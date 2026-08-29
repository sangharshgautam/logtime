/* ==========================================================
File:        LogTime.java
Description: Automatic time tracking for JetBrains IDEs, logging
             consolidated coding sessions to Jira worklogs.
Maintainer:  LogTime <support@logtime.com>
License:     BSD, see LICENSE for more details.
Website:     https://logtime.com/
===========================================================*/

package uk.co.sangharsh.logtime.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.AppTopics;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.DataManager;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.LogLevel;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import uk.co.sangharsh.logtime.plugin.listener.*;
import uk.co.sangharsh.logtime.plugin.service.JiraService;
import uk.co.sangharsh.logtime.plugin.service.TimeSpent;

import java.awt.*;
import java.io.*;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

public class LogTime implements ApplicationComponent {

    public static final BigDecimal FREQUENCY = new BigDecimal(2 * 60); // max secs between heartbeats for continuous coding
    public static final Logger log = Logger.getInstance("LogTime");

    public static String VERSION;
    public static String IDE_NAME;
    public static Boolean DEBUG = false;
    public static Boolean DEBUG_CHECKED = false;
    public static Boolean STATUS_BAR = false;
    public static Boolean READY = false;
    public static String lastFile = null;
    public static BigDecimal lastTime = new BigDecimal(0);
    public static Boolean isBuilding = false;
    public static Map<String, LineStats> lineStatsCache = new ConcurrentHashMap<String, LineStats>();
    public static Map<String, Integer> humanLineChanges = new ConcurrentHashMap<String, Integer>();
    public static Map<String, Boolean> filesWithHumanTyping = new ConcurrentHashMap<String, Boolean>();
    public static Boolean cancelApiKey = false;

    // Maximum gap (in SECONDS, since heartbeats are epoch seconds) between heartbeats of the
    // same project before they are considered a separate worklog block (5 minutes).
    private static final BigDecimal MAX_ALLOWED_GAP_SECONDS = new BigDecimal("300");

    // How many consecutive failed POST attempts before a session is dropped to avoid an
    // unbounded queue while Jira is unreachable.
    private static final int MAX_RETRY_ATTEMPTS = 3;

    // A claim older than this (ms) is considered stale (its owner crashed); another instance may
    // reclaim it. Must comfortably exceed the Jira POST timeout window (~60s) to avoid stealing an
    // in-flight post.
    private static final long CLAIM_TIMEOUT_MS = 300_000L; // 5 minutes

    private final int queueTimeoutSeconds = 30;
    private static final HeartbeatStore heartbeatStore = HeartbeatStore.getDefault();
    private static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static ScheduledFuture<?> scheduledFixture;
    private static MessageBusConnection connection;

    private static final java.util.regex.Pattern JIRA_ISSUE_KEY_PATTERN =
            java.util.regex.Pattern.compile("[A-Z]+-\\d+");

    public LogTime() {
    }

    public void initComponent() {
        try {
            VERSION = PluginManager.getPlugin(PluginId.getId("uk.co.sangharsh.logtime.plugin")).getVersion();
        } catch (Exception e) {
            VERSION = PluginManagerCore.getPlugin(PluginId.getId("uk.co.sangharsh.logtime.plugin")).getVersion();
        }
        log.info("Initializing LogTime plugin v" + VERSION + " (https://logtime.com/)");

        IDE_NAME = ApplicationNamesInfo.getInstance().getFullProductName().replaceAll(" ", "").toLowerCase();

        setupConfigs();
        setLoggingLevel();
        setupStatusBar();
        setupEventListeners();
        setupQueueProcessor();

        // The durable queue means logging no longer depends on an external binary download, so the
        // plugin is ready as soon as listeners and the scheduler are running.
        READY = true;

        // Recover and flush any heartbeats left over from a previously crashed IDE session.
        recoverPendingHeartbeats();
    }

    private void setupEventListeners() {
        ApplicationManager.getApplication().invokeLater(new Runnable(){
            public void run() {
                Disposable disposable = Disposer.newDisposable("LogTimeListener");
                connection = ApplicationManager.getApplication().getMessageBus().connect();

                // save file
                connection.subscribe(AppTopics.FILE_DOCUMENT_SYNC, new CustomSaveListener());

                // edit document
                EditorFactory.getInstance().getEventMulticaster().addDocumentListener(new CustomDocumentListener(), disposable);

                // mouse press
                EditorFactory.getInstance().getEventMulticaster().addEditorMouseListener(new CustomEditorMouseListener(), disposable);

                // scroll document
                EditorFactory.getInstance().getEventMulticaster().addVisibleAreaListener(new CustomVisibleAreaListener(), disposable);

                // caret moved
                EditorFactory.getInstance().getEventMulticaster().addCaretListener(new CustomCaretListener(), disposable);
            }
        });
    }

    private void setupQueueProcessor() {
        final Runnable handler = new Runnable() {
            public void run() {
                processHeartbeatQueue();
            }
        };
        long delay = queueTimeoutSeconds;
        scheduledFixture = scheduler.scheduleAtFixedRate(handler, delay, delay, TimeUnit.SECONDS);
    }

    private void recoverPendingHeartbeats() {
        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
            public void run() {
                processHeartbeatQueue();
            }
        });
    }

    private static void checkDebug() {
        if (DEBUG_CHECKED) return;
        DEBUG_CHECKED = true;
        if (!DEBUG) return;
        ApplicationManager.getApplication().invokeLater(new Runnable(){
            public void run() {
                Messages.showWarningDialog("Your IDE may respond slower. Disable debug mode from Tools -> LogTime Settings.", "LogTime Debug Mode Enabled");
            }
        });
    }

    public void disposeComponent() {
        try {
            if (connection != null) connection.disconnect();
        } catch(Exception e) { }
        try {
            if (scheduledFixture != null) scheduledFixture.cancel(true);
        } catch (Exception e) { }

        // Durable data is already on disk; flush whatever is pending before exiting.
        processHeartbeatQueue();
    }

    public static void checkApiKey() {
        if (cancelApiKey || JiraUrl.isDialogOpened) return;
        ApplicationManager.getApplication().invokeLater(new Runnable(){
            public void run() {
                Project project = getCurrentProject();
                if (project == null) return;
                if (ConfigFile.getJiraUrl().equals("") && !ConfigFile.usingVaultCmd()) {
                    Application app = ApplicationManager.getApplication();
                    if (app.isUnitTestMode() || !app.isDispatchThread()) return;
                    try {
                        JiraUrl jiraUrl = new JiraUrl(project);
                        jiraUrl.promptForJiraUrl();
                    } catch(Exception e) {
                        warnException(e);
                    } catch (Throwable throwable) {
                        log.warn("Unable to prompt for Jira URL because UI not ready.");
                    }
                }
            }
        });
    }

    public static BigDecimal getCurrentTimestamp() {
        return new BigDecimal(String.valueOf(System.currentTimeMillis() / 1000.0)).setScale(4, BigDecimal.ROUND_HALF_UP);
    }

    public static void appendHeartbeat(final VirtualFile file, final Project project, final boolean isWrite, @Nullable final LineStats lineStats) {
        checkDebug();

        if (!shouldLogFile(file)) return;

        String filePath = file.getPath();

        if (filePath.contains("/var/cache/")) {
            return;
        }

        final BigDecimal time = LogTime.getCurrentTimestamp();
        if (!isWrite && filePath.equals(LogTime.lastFile) && !enoughTimePassed(time)) {
            return;
        }
        LogTime.lastFile = filePath;
        LogTime.lastTime = time;

        final String projectName = project != null ? project.getName() : null;
        final String language = LogTime.getLanguage(file);

        String localFile = null;
        if (file.getFileSystem().getProtocol().equals("cwm")) {
            try {
                byte[] content = file.contentsToByteArray(true);
                File tempFile = FileUtil.createTempFile("logtime.", file.getName());
                FileUtil.writeToFile(tempFile, content);
                localFile = tempFile.getAbsolutePath();
            } catch (IOException e) {
                warnException(e);
                return;
            }
        }
        final String localFilePath = localFile;
        final Integer humanLineChanges = popHumanLineChanges(filePath);

        Heartbeat h = new Heartbeat();
        h.id = java.util.UUID.randomUUID().toString();
        h.failCount = 0;
        h.entity = filePath;
        h.timestamp = time;
        h.isWrite = isWrite;
        h.isUnsavedFile = !file.exists();
        h.project = projectName;
        h.language = language;
        h.isBuilding = LogTime.isBuilding;
        if (lineStats != null) {
            h.lineCount = lineStats.lineCount;
            h.lineNumber = lineStats.lineNumber;
            h.cursorPosition = lineStats.cursorPosition;
        }
        h.humanLineChanges = humanLineChanges;

        if (localFilePath != null) {
            h.localFile = localFilePath;
        }

        // Durable before anything else can lose it: write to disk first so a crash right after
        // this point still preserves the heartbeat.
        heartbeatStore.append(h);
        if (LogTime.READY && project != null) {
            updateStatusBar();
        }

        if (LogTime.isBuilding) setBuildTimeout();
    }

    private static void updateStatusBar() {
        try {
            for (Project project : ProjectManager.getInstance().getOpenProjects()) {
                StatusBar statusbar = WindowManager.getInstance().getStatusBar(project);
                if (statusbar != null) statusbar.updateWidget("LogTime");
            }
        } catch (Exception e) {
            warnException(e);
        }
    }

    private static void setBuildTimeout() {
        AppExecutorUtil.getAppScheduledExecutorService().schedule(new Runnable() {
            @Override
            public void run() {
                if (!LogTime.isBuilding) return;
                Project project = getCurrentProject();
                if (project == null) return;
                if (!LogTime.isProjectInitialized(project)) return;
                VirtualFile file = LogTime.getCurrentFile(project);
                if (file == null) return;
                LogTime.appendHeartbeat(file, project, false, null);
            }
        }, 10, TimeUnit.SECONDS);
    }

    private static List<MergedSession> drainAndMergeHeartbeats(List<Heartbeat> rawHeartbeats) {
        if (rawHeartbeats == null || rawHeartbeats.isEmpty()) {
            return new java.util.ArrayList<>();
        }

        // Sort chronologically using BigDecimal's built-in comparator
        rawHeartbeats.sort((h1, h2) -> h1.timestamp.compareTo(h2.timestamp));

        List<MergedSession> mergedSessions = new java.util.ArrayList<>();

        MergedSession activeSession = new MergedSession(rawHeartbeats.get(0));

        for (int i = 1; i < rawHeartbeats.size(); i++) {
            Heartbeat nextHb = rawHeartbeats.get(i);
            BigDecimal gapSeconds = nextHb.timestamp.subtract(activeSession.getEndTime());
            boolean sameProject = projectEquals(activeSession.getProject(), nextHb.project);

            if (gapSeconds.compareTo(MAX_ALLOWED_GAP_SECONDS) <= 0 && sameProject) {
                activeSession.updateEndTime(nextHb.timestamp);
                activeSession.addHeartbeat(nextHb);
            } else {
                mergedSessions.add(activeSession);
                activeSession = new MergedSession(nextHb);
            }
        }
        mergedSessions.add(activeSession);

        return mergedSessions;
    }

    private static boolean projectEquals(String a, String b) {
        if (a == null) return b == null;
        return a.equals(b);
    }

    private static void processHeartbeatQueue() {
        if (!LogTime.READY) return;

        checkApiKey();

        // Multi-instance safe: only work on heartbeats that are unowned or whose claim is stale
        // (owner crashed). Fresh claims owned by another live instance are left alone so we never
        // double-post a session another process is already sending.
        String owner = ConfigFile.getInstanceId();
        long now = System.currentTimeMillis();
        List<Heartbeat> unclaimed = heartbeatStore.loadUnclaimed(now - CLAIM_TIMEOUT_MS);
        if (unclaimed.isEmpty()) {
            return;
        }

        // Claim these heartbeats for this instance before posting, so no other instance touches
        // them while our (potentially slow) network calls run.
        Set<String> claimIds = new HashSet<>();
        for (Heartbeat hb : unclaimed) {
            if (hb.id != null) claimIds.add(hb.id);
        }
        heartbeatStore.claim(claimIds, owner, now);

        List<MergedSession> consolidatedSessions = drainAndMergeHeartbeats(unclaimed);

        Set<String> removeIds = new HashSet<>();
        Set<String> releaseIds = new HashSet<>();
        for (MergedSession session : consolidatedSessions) {
            SendOutcome outcome = sendToJira(session);
            for (Heartbeat hb : session.getHeartbeats()) {
                if (hb.id == null) continue;
                if (outcome == SendOutcome.POSTED || outcome == SendOutcome.DROP) {
                    // Successfully posted, or unretryable (no issue key / no elapsed time / retries
                    // exhausted): permanently evict from the queue.
                    removeIds.add(hb.id);
                } else {
                    // FAILED_RETRY: keep it but release the claim so it is eligible to be retried
                    // (and so a crashed instance isn't left owning it forever).
                    releaseIds.add(hb.id);
                }
            }
        }

        if (!removeIds.isEmpty()) {
            heartbeatStore.removeIds(removeIds);
        }
        if (!releaseIds.isEmpty()) {
            heartbeatStore.releaseClaim(releaseIds);
        }
    }

    private enum SendOutcome { POSTED, FAILED_RETRY, DROP }

    private static SendOutcome sendToJira(MergedSession session) {
        // Skip sessions with no measurable elapsed time (not retryable).
        if (!session.hasElapsed()) return SendOutcome.DROP;

        String issueKey = extractJiraIssueKey(session);
        if (issueKey == null) return SendOutcome.DROP; // not retryable; drop

        // Durable retry cap: failCount is persisted on the session's last heartbeat so it survives
        // crashes and restarts. Once it exceeds MAX_RETRY_ATTEMPTS we give up and drop the session.
        if (readFailCount(session) >= MAX_RETRY_ATTEMPTS) {
            log.warn("Giving up on Jira worklog for issue " + issueKey + " after " + MAX_RETRY_ATTEMPTS + " attempts.");
            return SendOutcome.DROP;
        }

        JiraService jiraService = JiraService.getInstance();
        try {
            TimeSpent timeSpent = buildJiraWorklogPayload(session);
            if (timeSpent == null || timeSpent.timeSpentSeconds <= 0) return SendOutcome.DROP;
            String jsonPayload = new ObjectMapper().writeValueAsString(timeSpent);
            boolean posted = jiraService.postWorklog(issueKey, jsonPayload);
            if (posted) {
                log.debug("Jira worklog sent for issue: " + issueKey);
                return SendOutcome.POSTED;
            }
            log.warn("Failed to send Jira worklog for issue: " + issueKey);
            return recordFailure(session) ? SendOutcome.FAILED_RETRY : SendOutcome.DROP;
        } catch (Exception e) {
            // Handle serialization exceptions safely within the IDE
            e.printStackTrace();
            return recordFailure(session) ? SendOutcome.FAILED_RETRY : SendOutcome.DROP;
        }
    }

    private static int readFailCount(MergedSession session) {
        Heartbeat last = session.getLastHeartbeat();
        if (last == null || last.id == null) return 0;
        Integer fc = last.failCount;
        return fc == null ? 0 : fc;
    }

    /** Persists one failed attempt on the session's last heartbeat. True = keep retrying. */
    private static boolean recordFailure(MergedSession session) {
        Heartbeat last = session.getLastHeartbeat();
        if (last == null || last.id == null) return true; // cannot track; keep retrying
        int newCount = heartbeatStore.incrementFailCount(last.id);
        if (newCount < 0) return true; // could not persist; keep retrying
        return newCount < MAX_RETRY_ATTEMPTS;
    }

    private static String extractJiraIssueKey(MergedSession session) {
        // Look for a Jira issue key (e.g. PROJECT-123) in the project name. Returns null when there
        // is no key so the session is dropped rather than logged to a hardcoded placeholder issue.
        String source = session.getProject();
        if (source == null) return null;
        java.util.regex.Matcher matcher = JIRA_ISSUE_KEY_PATTERN.matcher(source);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

    private static TimeSpent buildJiraWorklogPayload(MergedSession session) {
        String startTimeISO = session.getStartTimeISO();
        long secondsSpent = session.getDurationInSeconds();
        try {
            TimeSpent payload = new TimeSpent();
            payload.comment = String.format("Time tracking consolidated log for project [%s]", session.getProject());
            payload.started = startTimeISO;
            payload.timeSpentSeconds = secondsSpent;
            return payload;
        } catch (Exception e) {
            log.error("Failed to build Jira worklog payload", e);
            return null;
        }
    }

    public static boolean enoughTimePassed(BigDecimal currentTime) {
        return LogTime.lastTime.add(FREQUENCY).compareTo(currentTime) < 0;
    }

    public static boolean shouldLogFile(VirtualFile file) {
        if (file == null || file.getUrl().startsWith("mock://")) {
            return false;
        }
        String filePath = file.getPath();
        if (filePath.equals("atlassian-ide-plugin.xml") || filePath.contains("/.idea/workspace.xml")) {
            return false;
        }
        return true;
    }

    public static boolean isAppActive() {
        return KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow() != null;
    }

    public static boolean isProjectInitialized(Project project) {
        if (project == null) return true;
        return project.isInitialized();
    }

    public static void setupConfigs() {
        String debug = ConfigFile.get("settings", "debug", false);
        LogTime.DEBUG = debug != null && debug.trim().equals("true");
    }

    public static void setupStatusBar() {
        String statusBarVal = ConfigFile.get("settings", "status_bar_enabled", false);
        LogTime.STATUS_BAR = statusBarVal == null || !statusBarVal.trim().equals("false");
        if (LogTime.READY) {
            try {
                updateStatusBar();
            } catch (Exception e) {
                warnException(e);
            }
        }
    }

    public static void setLoggingLevel() {
        try {
            if (LogTime.DEBUG) {
                log.setLevel(LogLevel.DEBUG);
                log.debug("Logging level set to DEBUG");
            } else {
                log.setLevel(LogLevel.INFO);
            }
        } catch(Throwable e) {
            System.out.println(e.getStackTrace());
        }
    }

    private static String getLanguage(final VirtualFile file) {
        FileType type = file.getFileType();
        if (type != null)
            return type.getName();
        return null;
    }

    @Nullable
    public static VirtualFile getFile(Document document) {
        if (document == null) return null;
        FileDocumentManager instance = FileDocumentManager.getInstance();
        if (instance == null) return null;
        VirtualFile file = instance.getFile(document);
        return file;
    }

    @Nullable
    public static VirtualFile getCurrentFile(Project project) {
        if (project == null) return null;
        Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        if (editor == null) return null;
        Document document = editor.getDocument();
        return LogTime.getFile(document);
    }

    public static Project getProject(Document document) {
        Editor[] editors = EditorFactory.getInstance().getEditors(document);
        if (editors.length > 0) {
            return editors[0].getProject();
        }
        return null;
    }

    @Nullable
    public static Project getCurrentProject() {
        ProjectManager pm = ProjectManager.getInstance();
        try {
            // Prefer the project whose window currently has focus.
            for (Project p : pm.getOpenProjects()) {
                Window w = WindowManager.getInstance().suggestParentWindow(p);
                if (w != null && w.isActive()) return p;
            }
            // Fall back to the first initialized open project, then the default project.
            for (Project p : pm.getOpenProjects()) {
                if (p.isInitialized()) return p;
            }
            return pm.getDefaultProject();
        } catch (Exception e) {
            return pm.getDefaultProject();
        }
    }

    public static LineStats getLineStats(@Nullable Document document, @Nullable Editor editor) {
        if (editor == null && document != null) {
            Project project = LogTime.getProject(document);
            if (project != null && project.isInitialized()) {
                editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
            }
        }

        if (editor != null) {
            if (document == null) {
                document = editor.getDocument();
            }
            for (Caret caret : editor.getCaretModel().getAllCarets()) {
                LineStats lineStats = new LineStats();
                if (document != null) {
                    lineStats.lineCount = document.getLineCount();
                }
                LogicalPosition position = caret.getLogicalPosition();
                lineStats.lineNumber = position.line + 1;
                lineStats.cursorPosition = position.column + 1;
                if (lineStats.isOK()) {
                    saveLineStats(document, lineStats, true);
                    return lineStats;
                }
            }
        }

        return LogTime.getLineStats(document);
    }

    public static LineStats getLineStats(@Nullable Document document) {
        if (document != null) {
            LineStats lineStats = new LineStats();
            lineStats.lineCount = document.getLineCount();
            Caret caret = CommonDataKeys.CARET.getData(DataManager.getInstance().getDataContext());
            if (caret != null) {
                LogicalPosition position = caret.getLogicalPosition();
                lineStats.lineNumber = position.line + 1;
                lineStats.cursorPosition = position.column + 1;
            }
            saveLineStats(document, lineStats, true);
            return lineStats;
        }
        return new LineStats();
    }

    public static void saveLineStats(Document document, LineStats lineStats) {
        VirtualFile file = LogTime.getFile(document);
        saveLineStats(file, lineStats);
    }

    public static void saveLineStats(@Nullable VirtualFile file, LineStats lineStats) {
        saveLineStats(file, lineStats, false);
    }

    public static void saveLineStats(Document document, LineStats lineStats, boolean updateLineChanges) {
        VirtualFile file = LogTime.getFile(document);
        saveLineStats(file, lineStats, updateLineChanges);
    }

    public static synchronized void saveLineStats(@Nullable VirtualFile file, LineStats lineStats, boolean updateLineChanges) {
        if (file == null) return;
        if (lineStats == null || !lineStats.hasLineCount()) return;
        lineStats.updatedAt = System.currentTimeMillis();
        if (updateLineChanges) {
            updateLineChanges(file, lineStats);
        }
        LogTime.lineStatsCache.put(file.getPath(), lineStats);
    }

    private static synchronized void updateLineChanges(@NotNull VirtualFile file, @NotNull LineStats lineStats) {
        String filePath = file.getPath();
        long now = lineStats.updatedAt != null ? lineStats.updatedAt : System.currentTimeMillis();
        LineStats previous = LogTime.lineStatsCache.get(filePath);
        if (previous == null || previous.lineCount == null) {
            return;
        }

        int delta = lineStats.lineCount - previous.lineCount;

        // prevent counting large copy/paste as human typed lines of code
        if (delta > 50 && previous.updatedAt != null && Math.abs(now - previous.updatedAt) < 60000) {
            delta = 0;
        }

        if (delta == 0) return;

        Integer current = LogTime.humanLineChanges.get(filePath);
        LogTime.humanLineChanges.put(filePath, (current != null ? current : 0) + delta);
    }

    private static synchronized Integer popHumanLineChanges(@NotNull String filePath) {
        Integer lineChanges = LogTime.humanLineChanges.remove(filePath);
        Boolean hasHumanTyping = LogTime.filesWithHumanTyping.remove(filePath);
        if (!Boolean.TRUE.equals(hasHumanTyping)) return 0;
        return lineChanges;
    }

    public static synchronized void markFileWithHumanTyping(@NotNull VirtualFile file) {
        LogTime.filesWithHumanTyping.put(file.getPath(), true);
    }

    public static String getStatusBarText() {
        if (!LogTime.READY) return "";
        if (!LogTime.STATUS_BAR) return "";
        return "LogTime";
    }

    public static void openDashboardWebsite() {
        BrowserUtil.browse("https://logtime.com");
    }

    public static void debugException(Exception e) {
        if (!log.isDebugEnabled()) return;
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        String str = e.getMessage() + "\n" + sw.toString();
        log.debug(str);
    }

    public static void warnException(Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        String str = e.getMessage() + "\n" + sw.toString();
        log.warn(str);
    }

    public static void errorException(Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        String str = e.getMessage() + "\n" + sw.toString();
        log.error(str);
    }

    @NotNull
    public String getComponentName() {
        return "LogTime";
    }
}
