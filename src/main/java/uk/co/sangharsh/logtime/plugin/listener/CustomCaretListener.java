/* ==========================================================
File:        CustomCaretListener.java
Description: Logs time from cursor movement events.
Maintainer:  LogTime <support@logtime.com>
License:     BSD, see LICENSE for more details.
Website:     https://logtime.com/
===========================================================*/

package uk.co.sangharsh.logtime.plugin.listener;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import uk.co.sangharsh.logtime.plugin.LineStats;
import uk.co.sangharsh.logtime.plugin.LogTime;

public class CustomCaretListener implements CaretListener {
    @Override
    public void caretPositionChanged(CaretEvent event) {
        // LogTime.log.debug("caret event");
        try {
            if (!LogTime.isAppActive()) return;
            Editor editor = event.getEditor();
            Document document = editor.getDocument();
            VirtualFile file = LogTime.getFile(document);
            if (file == null) return;
            Project project = editor.getProject();
            if (!LogTime.isProjectInitialized(project)) return;
            ApplicationManager.getApplication().invokeLater(new Runnable() {
                public void run() {
                    LineStats lineStats = LogTime.getLineStats(document, editor);
                    LogTime.appendHeartbeat(file, project, false, lineStats);
                }
            });
        } catch(Exception e) {
            LogTime.debugException(e);
        }
    }
}
