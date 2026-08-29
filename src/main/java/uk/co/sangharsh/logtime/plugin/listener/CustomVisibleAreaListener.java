/* ==========================================================
File:        CustomEditorMouseMotionListener.java
Description: Logs time from mouse motion events.
Maintainer:  LogTime <support@logtime.com>
License:     BSD, see LICENSE for more details.
Website:     https://logtime.com/
===========================================================*/

package uk.co.sangharsh.logtime.plugin.listener;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.VisibleAreaEvent;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import uk.co.sangharsh.logtime.plugin.LineStats;
import uk.co.sangharsh.logtime.plugin.LogTime;

import java.awt.*;

public class CustomVisibleAreaListener implements VisibleAreaListener {
    @Override
    public void visibleAreaChanged(VisibleAreaEvent visibleAreaEvent) {
        // LogTime.log.debug("visibleAreaChanged event");
        try {
            if (!didChange(visibleAreaEvent)) return;
            if (!LogTime.isAppActive()) return;
            Document document = visibleAreaEvent.getEditor().getDocument();
            VirtualFile file = LogTime.getFile(document);
            if (file == null) return;
            Project project = visibleAreaEvent.getEditor().getProject();
            if (!LogTime.isProjectInitialized(project)) return;
            Editor editor = visibleAreaEvent.getEditor();
            LineStats lineStats = LogTime.getLineStats(document, editor);
            LogTime.appendHeartbeat(file, project, false, lineStats);
        } catch(Exception e) {
            LogTime.debugException(e);
        }
    }

    private boolean didChange(VisibleAreaEvent visibleAreaEvent) {
        Rectangle oldRect = visibleAreaEvent.getOldRectangle();
        if (oldRect == null) return true;
        Rectangle newRect = visibleAreaEvent.getNewRectangle();
        if (newRect == null) return false;
        return newRect.x != oldRect.x || newRect.y != oldRect.y;
    }
}
