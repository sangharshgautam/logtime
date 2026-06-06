/* ==========================================================
File:        CustomDocumentListener.java
Description: Logs time from document change events.
Maintainer:  LogTime <support@logtime.com>
License:     BSD, see LICENSE for more details.
Website:     https://logtime.com/
===========================================================*/

package uk.co.sangharsh.logtime.plugin.listener;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.BulkAwareDocumentListener;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import uk.co.sangharsh.logtime.plugin.LineStats;
import uk.co.sangharsh.logtime.plugin.LogTime;

public class CustomDocumentListener implements BulkAwareDocumentListener.Simple {
    @Override
    public void documentChangedNonBulk(DocumentEvent documentEvent) {
        // LogTime.log.debug("documentChangedNonBulk event");
        try {
            if (!LogTime.isAppActive()) return;
            Document document = documentEvent.getDocument();
            VirtualFile file = LogTime.getFile(document);
            if (file == null) return;
            if (documentEvent.getNewFragment().length() == 1) {
                LogTime.markFileWithHumanTyping(file);
            }
            Project project = LogTime.getProject(document);
            if (!LogTime.isProjectInitialized(project)) return;
            LineStats lineStats = LogTime.getLineStats(document);
            LogTime.appendHeartbeat(file, project, false, lineStats);
        } catch(Exception e) {
            LogTime.debugException(e);
        }
    }
}
