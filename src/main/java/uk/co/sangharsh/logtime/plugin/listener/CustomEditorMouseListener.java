/* ==========================================================
File:        CustomEditorMouseListener.java
Description: Logs time from mouse click events.
Maintainer:  LogTime <support@logtime.com>
License:     BSD, see LICENSE for more details.
Website:     https://logtime.com/
===========================================================*/

package uk.co.sangharsh.logtime.plugin.listener;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import uk.co.sangharsh.logtime.plugin.LineStats;
import uk.co.sangharsh.logtime.plugin.LogTime;

public class CustomEditorMouseListener implements EditorMouseListener {
    @Override
    public void mousePressed(EditorMouseEvent editorMouseEvent) {
        // LogTime.log.debug("mousePressed event");
        try {
            if (!LogTime.isAppActive()) return;
            Document document = editorMouseEvent.getEditor().getDocument();
            VirtualFile file = LogTime.getFile(document);
            if (file == null) return;
            Project project = editorMouseEvent.getEditor().getProject();
            if (!LogTime.isProjectInitialized(project)) return;
            ApplicationManager.getApplication().invokeLater(new Runnable() {
                public void run() {
                    LineStats lineStats = LogTime.getLineStats(document, editorMouseEvent.getEditor());
                    LogTime.appendHeartbeat(file, project, false, lineStats);
                }
            });
        } catch(Exception e) {
            LogTime.debugException(e);
        }
    }

    @Override
    public void mouseClicked(EditorMouseEvent editorMouseEvent) {
    }

    @Override
    public void mouseReleased(EditorMouseEvent editorMouseEvent) {
    }

    @Override
    public void mouseEntered(EditorMouseEvent editorMouseEvent) {
    }

    @Override
    public void mouseExited(EditorMouseEvent editorMouseEvent) {
    }
}
