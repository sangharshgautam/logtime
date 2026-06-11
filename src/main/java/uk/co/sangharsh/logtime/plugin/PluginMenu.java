/* ==========================================================
File:        PluginMenu.java
Description: Adds a LogTime item to the File menu.
Maintainer:  LogTime <support@wakatime.com>
License:     BSD, see LICENSE for more details.
Website:     https://wakatime.com/
===========================================================*/

package uk.co.sangharsh.logtime.plugin;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;

public class PluginMenu extends AnAction {
    public PluginMenu() {
        super("LogTime Settings");
        // super("LogTime Settings", "", IconLoader.getIcon("/Mypackage/icon.png"));
    }
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        Settings popup = new Settings(project);
        popup.show();
    }
}
