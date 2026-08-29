package uk.co.sangharsh.logtime.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.intellij.openapi.diagnostic.Logger;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Durable, crash-safe queue of {@link Heartbeat}s persisted as one JSON object per line (JSONL).
 * The file is the single source of truth for pending heartbeats: entries are only removed after a
 * successful Jira worklog POST.
 *
 * <p>Crash safety: appends are newline-terminated and a partial trailing line left by a crash is
 * truncated on the next append. Compaction/removal rewrites to a temp file and atomically renames
 * it, so a crash never leaves a corrupt queue.</p>
 *
 * <p>Multi-instance safety: mutating operations serialise through a dedicated {@code .lock} file
 * using an OS file lock, so two IDE processes cannot interleave writes to (or corrupt) the queue.
 * Heartbeats are {@link #claim(Collection, String, long) claimed} before posting and released on
 * failure, preventing duplicate Jira worklogs between concurrent instances.</p>
 */
public final class HeartbeatStore {

    private static final Logger log = Logger.getInstance(HeartbeatStore.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /** How long (ms) to attempt acquiring the cross-process file lock before giving up. */
    private static final int LOCK_RETRY_MS = 2000;
    private static final int LOCK_RETRY_STEP_MS = 50;

    private final Path queueFile;
    private final Path lockFile;
    private final Object lock = new Object();
    private FileChannel lockChannel;
    private int pendingCountCache = -1;

    public HeartbeatStore(Path queueFile) {
        this.queueFile = queueFile;
        this.lockFile = queueFile.resolveSibling(queueFile.getFileName() + ".lock");
    }

    /**
     * Releases the persistent lock channel. This store must not be used afterwards. Called on plugin
     * shutdown and by tests for clean teardown of the temp directory.
     */
    public void close() {
        synchronized (lock) {
            if (lockChannel != null) {
                try {
                    lockChannel.close();
                } catch (IOException e) {
                    log.warn("Failed to close queue lock channel", e);
                } finally {
                    lockChannel = null;
                }
            }
        }
    }

    public static Path getLogtimeHome() {
        String env = System.getenv("LOGTIME_HOME");
        Path base;
        if (env != null && !env.trim().isEmpty()) {
            base = new File(env.trim()).toPath();
        } else if (isWindows()) {
            String userProfile = System.getenv("USERPROFILE");
            base = new File(userProfile != null ? userProfile : System.getProperty("user.home")).toPath();
        } else {
            base = new File(System.getProperty("user.home")).toPath();
        }
        return base.resolve(".logtime");
    }

    public static HeartbeatStore getDefault() {
        return new HeartbeatStore(getLogtimeHome().resolve("heartbeats.jsonl"));
    }

    // ---------------------------------------------------------------------------------------------
    // Writes
    // ---------------------------------------------------------------------------------------------

    /**
     * Durably appends a heartbeat to the queue. Safe to call from any thread and from multiple
     * processes (serialised by the lock file). If another process holds the lock beyond the retry
     * window, the write is skipped (logged once) to avoid interleaving corruption.
     */
    public void append(Heartbeat heartbeat) {
        if (heartbeat == null) return;
        synchronized (lock) {
            try (FileLockHandle ignored = acquireFileLock()) {
                if (ignored == null) {
                    log.warn("Could not acquire queue lock; dropping heartbeat for " + heartbeat.entity);
                    return;
                }
                Path parent = queueFile.getParent();
                if (parent != null) Files.createDirectories(parent);
                ensureTerminatedLastLine();

                String json = MAPPER.writeValueAsString(heartbeat);
                Files.write(queueFile, (json + "\n").getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                if (pendingCountCache >= 0) pendingCountCache++;
            } catch (IOException e) {
                log.warn("Failed to persist heartbeat to " + queueFile, e);
            }
        }
    }

    /**
     * Marks the given heartbeats as owned by this instance before posting, under the lock. Returns
     * true if the claims were persisted.
     */
    public boolean claim(Collection<String> ids, String owner, long now) {
        return rewriteMatching(ids, h -> {
            h.owner = owner;
            h.claimedAt = now;
        });
    }

    /**
     * Releases the claim (clears owner/claimedAt) on the given heartbeats so they can be retried
     * later, without deleting them. Returns true if persisted.
     */
    public boolean releaseClaim(Collection<String> ids) {
        return rewriteMatching(ids, h -> {
            h.owner = null;
            h.claimedAt = null;
        });
    }

    /**
     * Removes the heartbeats whose unique {@code id}s are in {@code ids}. Only these lines are
     * deleted, regardless of their position, so failed sessions are never accidentally evicted.
     */
    public boolean removeIds(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) return false;
        synchronized (lock) {
            try {
                if (!Files.exists(queueFile)) return true;
                List<String> lines = Files.readAllLines(queueFile, StandardCharsets.UTF_8);
                Set<String> toRemove = new HashSet<>(ids);
                List<String> remaining = new ArrayList<>(lines.size());
                int removed = 0;
                for (String line : lines) {
                    if (line == null || line.trim().isEmpty()) continue;
                    Heartbeat h = parseLine(line);
                    if (h != null && h.id != null && toRemove.contains(h.id)) {
                        removed++;
                        continue;
                    }
                    remaining.add(line);
                }
                boolean ok = doRewriteAtomically(remaining);
                if (ok && removed > 0 && pendingCountCache >= 0) {
                    pendingCountCache = Math.max(0, pendingCountCache - removed);
                }
                return ok;
            } catch (IOException e) {
                log.warn("Failed to remove heartbeats from " + queueFile, e);
                return false;
            }
        }
    }

    /**
     * Increments the retry counter (failCount) of the heartbeat with the given id, persisting it so
     * it survives a crash or restart. Returns the new failCount, or -1 if not found/write failed.
     */
    public int incrementFailCount(String id) {
        if (id == null) return -1;
        synchronized (lock) {
            try {
                if (!Files.exists(queueFile)) return -1;
                List<String> lines = Files.readAllLines(queueFile, StandardCharsets.UTF_8);
                List<String> rewritten = new ArrayList<>(lines.size());
                int newCount = -1;
                for (String line : lines) {
                    if (line == null || line.trim().isEmpty()) continue;
                    Heartbeat h = parseLine(line);
                    if (h != null && id.equals(h.id)) {
                        int current = h.failCount == null ? 0 : h.failCount;
                        h.failCount = current + 1;
                        newCount = h.failCount;
                        rewritten.add(MAPPER.writeValueAsString(h));
                    } else {
                        rewritten.add(line);
                    }
                }
                if (newCount >= 0) {
                    doRewriteAtomically(rewritten);
                }
                return newCount;
            } catch (IOException e) {
                log.warn("Failed to increment failCount in " + queueFile, e);
                return -1;
            }
        }
    }

    /** Removes all pending heartbeats. */
    public boolean clear() {
        synchronized (lock) {
            try (FileLockHandle ignored = acquireFileLock()) {
                if (ignored == null) return false;
                Files.deleteIfExists(queueFile);
                pendingCountCache = 0;
                return true;
            } catch (IOException e) {
                log.warn("Failed to clear heartbeat queue " + queueFile, e);
                return false;
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Reads
    // ---------------------------------------------------------------------------------------------

    /** Reads all pending heartbeats without removing them, skipping malformed/partial lines. */
    public List<Heartbeat> load() {
        synchronized (lock) {
            List<Heartbeat> result = new ArrayList<>();
            if (!Files.exists(queueFile)) return result;
            try {
                List<String> lines = Files.readAllLines(queueFile, StandardCharsets.UTF_8);
                for (String line : lines) {
                    if (line == null || line.trim().isEmpty()) continue;
                    Heartbeat h = parseLine(line);
                    if (h != null) result.add(h);
                }
                trimTrailingPartialLine(lines);
                return result;
            } catch (IOException e) {
                log.warn("Failed to read heartbeat queue " + queueFile, e);
                return result;
            }
        }
    }

    /**
     * Returns heartbeats eligible to be worked on: those with no owner, or whose claim is stale
     * (owner crashed). Fresh claims by any owner (including this instance) are excluded so a live
     * instance's in-flight post is never double-processed.
     *
     * @param staleOlderThanMs cutoff; lines with {@code claimedAt < staleOlderThanMs} are stale.
     */
    public List<Heartbeat> loadUnclaimed(long staleOlderThanMs) {
        synchronized (lock) {
            List<Heartbeat> result = new ArrayList<>();
            if (!Files.exists(queueFile)) return result;
            try {
                List<String> lines = Files.readAllLines(queueFile, StandardCharsets.UTF_8);
                for (String line : lines) {
                    if (line == null || line.trim().isEmpty()) continue;
                    Heartbeat h = parseLine(line);
                    if (h == null) continue;
                    if (h.owner == null) {
                        result.add(h);
                    } else if (h.claimedAt != null && h.claimedAt < staleOlderThanMs) {
                        result.add(h);
                    }
                }
                return result;
            } catch (IOException e) {
                log.warn("Failed to read heartbeat queue " + queueFile, e);
                return result;
            }
        }
    }

    /** Number of pending heartbeats in the file (cached in-memory; recomputed lazily). */
    public int pendingCount() {
        synchronized (lock) {
            if (pendingCountCache >= 0) return pendingCountCache;
            pendingCountCache = countLinesOnDisk();
            return pendingCountCache;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Locking + helpers
    // ---------------------------------------------------------------------------------------------

    /**
     * Rewrites lines whose id is in {@code ids}, applying {@code transform} to each matched
     * heartbeat before serialising. Returns true if persisted (and at least one matched).
     */
    private boolean rewriteMatching(Collection<String> ids, java.util.function.Consumer<Heartbeat> transform) {
        if (ids == null || ids.isEmpty()) return false;
        boolean[] any = {false};
        synchronized (lock) {
            try (FileLockHandle ignored = acquireFileLock()) {
                if (ignored == null) return false;
                if (!Files.exists(queueFile)) return false;
                List<String> lines = Files.readAllLines(queueFile, StandardCharsets.UTF_8);
                List<String> rewritten = new ArrayList<>(lines.size());
                for (String line : lines) {
                    if (line == null || line.trim().isEmpty()) continue;
                    Heartbeat h = parseLine(line);
                    if (h != null && h.id != null && ids.contains(h.id)) {
                        transform.accept(h);
                        rewritten.add(MAPPER.writeValueAsString(h));
                        any[0] = true;
                    } else {
                        rewritten.add(line);
                    }
                }
                if (any[0]) {
                    doRewriteAtomically(rewritten);
                }
                return any[0];
            } catch (IOException e) {
                log.warn("Failed to rewrite heartbeats in " + queueFile, e);
                return false;
            }
        }
    }

    /**
     * Acquires an exclusive OS lock on the lock file, retrying briefly. The lock channel is opened
     * once and reused, so repeated appends don't pay a file-open cost on every heartbeat. Returns a
     * handle owning the lock (which must be closed to release it), or null if it could not be
     * acquired within the retry window (another process owns it).
     *
     * <p>Callers must already hold the {@code lock} monitor; this makes it safe to touch the shared
     * {@link #lockChannel} and guarantees no same-JVM {@code OverlappingFileLockException}.</p>
     */
    private FileLockHandle acquireFileLock() {
        synchronized (lock) {
            try {
                Path parent = lockFile.getParent();
                if (parent != null) Files.createDirectories(parent);
                if (lockChannel == null || !lockChannel.isOpen()) {
                    lockChannel = FileChannel.open(lockFile,
                            StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                }
            } catch (IOException e) {
                log.warn("Failed to open queue lock file " + lockFile, e);
                return null;
            }
            long deadline = System.currentTimeMillis() + LOCK_RETRY_MS;
            while (true) {
                try {
                    FileLock fileLock = lockChannel.tryLock();
                    if (fileLock != null) {
                        return new FileLockHandle(fileLock);
                    }
                } catch (OverlappingFileLockException | IOException e) {
                    return null; // same-JVM overlap is impossible under the monitor; treat as busy
                }
                if (System.currentTimeMillis() >= deadline) return null;
                try {
                    Thread.sleep(LOCK_RETRY_STEP_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
    }

    /** The channel is owned by the store; closing the handle only releases the lock. */
    private static final class FileLockHandle implements AutoCloseable {
        private final FileLock fileLock;

        FileLockHandle(FileLock fileLock) {
            this.fileLock = fileLock;
        }

        @Override
        public void close() {
            try {
                if (fileLock != null && fileLock.isValid()) {
                    fileLock.release();
                }
            } catch (IOException e) {
                // ignore; releasing a lock on close is best-effort
            }
        }
    }

    private Heartbeat parseLine(String line) {
        try {
            return MAPPER.readValue(line, Heartbeat.class);
        } catch (IOException e) {
            return null;
        }
    }

    private int countLinesOnDisk() {
        try {
            if (!Files.exists(queueFile)) return 0;
            int count = 0;
            try (java.io.BufferedReader reader = Files.newBufferedReader(queueFile, StandardCharsets.UTF_8)) {
                while (reader.readLine() != null) count++;
            }
            return count;
        } catch (IOException e) {
            log.warn("Failed to count heartbeats in " + queueFile, e);
            return 0;
        }
    }

    /**
     * Checks only the tail of the file for a trailing partial line, truncating it if found. This is
     * O(1) in the normal (properly terminated) case.
     */
    private void ensureTerminatedLastLine() throws IOException {
        if (!Files.exists(queueFile)) return;
        long size = Files.size(queueFile);
        if (size == 0) return;
        byte[] tail = readTail(queueFile, (int) Math.min(size, 1024));
        if (tail.length == 0) return;
        char last = (char) tail[tail.length - 1];
        if (last == '\n' || last == '\r') return; // fast path: already terminated

        // Partial trailing line (crash mid-append). Truncate after the previous newline.
        long truncateTo = findTruncatePoint(queueFile, size, tail);
        try (FileChannel channel = FileChannel.open(queueFile, StandardOpenOption.WRITE)) {
            channel.truncate(truncateTo);
        }
    }

    private long findTruncatePoint(Path file, long size, byte[] tail) throws IOException {
        int lastNewline = -1;
        for (int i = tail.length - 1; i >= 0; i--) {
            if (tail[i] == '\n') { lastNewline = i; break; }
        }
        if (lastNewline >= 0) {
            return (size - tail.length) + lastNewline + 1;
        }
        // No newline in the tail chunk; scan backwards in chunks.
        long pos = size - tail.length;
        int chunk = 1024;
        while (pos > 0) {
            int len = (int) Math.min(chunk, pos);
            byte[] buf = readTail(file, len);
            for (int i = buf.length - 1; i >= 0; i--) {
                if (buf[i] == '\n') return (pos - len) + i + 1;
            }
            pos -= len;
        }
        return 0;
    }

    private byte[] readTail(Path file, int len) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            long size = raf.length();
            long start = Math.max(0, size - len);
            int toRead = (int) (size - start);
            byte[] buf = new byte[toRead];
            raf.seek(start);
            raf.readFully(buf);
            return buf;
        }
    }

    private void trimTrailingPartialLine(List<String> lines) {
        if (lines.isEmpty()) return;
        try {
            if (!Files.exists(queueFile)) return;
            long size = Files.size(queueFile);
            if (size == 0) return;
            byte[] tail = readTail(queueFile, (int) Math.min(size, 1024));
            if (tail.length == 0) return;
            char last = (char) tail[tail.length - 1];
            if (last != '\n' && last != '\r') {
                // The last parsed line was partial; drop it from the file.
                List<String> remaining = new ArrayList<>(lines.subList(0, lines.size() - 1));
                doRewriteAtomically(remaining);
                pendingCountCache = remaining.size();
            }
        } catch (IOException e) {
            log.warn("Failed to trim partial heartbeat line in " + queueFile, e);
        }
    }

    private boolean doRewriteAtomically(List<String> lines) {
        try {
            Path parent = queueFile.getParent();
            if (parent != null) Files.createDirectories(parent);
            StringBuilder sb = new StringBuilder();
            for (String line : lines) {
                if (line == null || line.trim().isEmpty()) continue;
                sb.append(line);
                if (!line.endsWith("\n")) sb.append('\n');
            }
            if (sb.length() == 0) {
                Files.deleteIfExists(queueFile);
                return true;
            }
            Path tmp = parent != null ? parent.resolve(queueFile.getFileName() + ".tmp")
                    : queueFile.resolveSibling(queueFile.getFileName() + ".tmp");
            Files.write(tmp, sb.toString().getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(tmp, queueFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (IOException e) {
            log.warn("Failed to atomically rewrite heartbeat queue " + queueFile, e);
            return false;
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
