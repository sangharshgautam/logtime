/* ==========================================================
File:        CustomSaveListener.java
Description: Sends a heartbeat when a file is saved.
Maintainer:  LogTime <support@logtime.com>
License:     BSD, see LICENSE for more details.
Website:     https://logtime.com/
===========================================================*/

package uk.co.sangharsh.logtime.plugin.listener;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import uk.co.sangharsh.logtime.plugin.LineStats;
import uk.co.sangharsh.logtime.plugin.LogTime;
import org.jetbrains.annotations.NotNull;

public class CustomSaveListener implements FileDocumentManagerListener {
    @Override
    public void beforeDocumentSaving(Document document) {
        // LogTime.log.debug("beforeDocumentSaving event");
        try {
            if (!LogTime.isAppActive()) return;
            VirtualFile file = LogTime.getFile(document);
            if (file == null) return;
            LogTime.markFileWithHumanTyping(file);
            Project project = LogTime.getProject(document);
            if (!LogTime.isProjectInitialized(project)) return;
            LineStats lineStats = new LineStats();
            if (project != null) {
                Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
                lineStats = LogTime.getLineStats(document, editor);
            }
            LogTime.appendHeartbeat(file, project, true, lineStats);
        } catch(Exception e) {
            LogTime.debugException(e);
        }
    }

    @Override
    public void beforeAllDocumentsSaving() {
    }

    @Override
    public void beforeFileContentReload(@NotNull VirtualFile file, @NotNull Document document) {
    }

    @Override
    public void fileWithNoDocumentChanged(@NotNull VirtualFile file) {
    }

    @Override
    public void fileContentReloaded(@NotNull VirtualFile file, @NotNull Document document) {
    }

    @Override
    public void fileContentLoaded(@NotNull VirtualFile file, @NotNull Document document) {
    }

    @Override
    public void unsavedDocumentsDropped() {
    }
}
