/* ==========================================================
File:        CustomStatusBar.java
Description: Shows today's total code time in the status bar.
Maintainer:  LogTime <support@logtime.com>
License:     BSD, see LICENSE for more details.
Website:     https://logtime.com/
===========================================================*/

package uk.co.sangharsh.logtime.plugin;

import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.laf.UIThemeLookAndFeelInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.StatusBarWidgetFactory;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.event.MouseEvent;

public class CustomStatusBar implements StatusBarWidgetFactory {

    @NotNull
    @Override
    public String getId() {
        return "LogTime";
    }

    @Nls
    @NotNull
    @Override
    public String getDisplayName() {
        return "LogTime";
    }

    @Override
    public boolean isAvailable(@NotNull Project project) { return true; }

    @NotNull
    @Override
    public StatusBarWidget createWidget(@NotNull Project project) {
        return new LogTimeStatusBarWidget(project);
    }

    @Override
    public void disposeWidget(@NotNull StatusBarWidget widget) { }

    @Override
    public boolean canBeEnabledOn(@NotNull StatusBar statusBar) {
        return true;
    }

    public class LogTimeStatusBarWidget implements StatusBarWidget {
        public final Project project;
        public final StatusBar statusBar;

        @Contract(pure = true)
        public LogTimeStatusBarWidget(Project project) {
            this.project = project;
            this.statusBar = WindowManager.getInstance().getStatusBar(project);
        }

        private static boolean isDarkTheme() {
            LafManager lafManager = LafManager.getInstance();
            if (lafManager == null) return false;
            UIThemeLookAndFeelInfo current = lafManager.getCurrentUIThemeLookAndFeel();
            return current != null && current.isDark();
        }

        @NotNull
        @Override
        public String ID() {
            return "LogTime";
        }

        @Nullable
        @Override
        public WidgetPresentation getPresentation() {
            return new StatusBarPresenter(this);
        }

        @Override
        public void install(@NotNull StatusBar statusBar) { }

        @Override
        public void dispose() { }

        private class StatusBarPresenter implements MultipleTextValuesPresentation, Multiframe {
            private final LogTimeStatusBarWidget widget;

            public StatusBarPresenter(LogTimeStatusBarWidget widget) {
                this.widget = widget;
            }

            @Nullable
            @Override
            public ListPopup getPopupStep() {
                return null;
            }

            @Nullable
            @Override
            public String getSelectedValue() { return LogTime.getStatusBarText(); }

            @Override
            public @Nullable
            Icon getIcon() {
                String theme = isDarkTheme() ? "dark" : "light";
                return IconLoader.getIcon("status-bar-icon-" + theme + "-theme.svg", LogTime.class);
            }

            @Nullable
            @Override
            public String getTooltipText() {
                return null;
            }

            @Nullable
            @Override
            public Consumer<MouseEvent> getClickConsumer() {
                return (MouseEvent e) -> LogTime.openDashboardWebsite();
            }

            @Override
            public StatusBarWidget copy() {
                return new LogTimeStatusBarWidget(this.widget.project);
            }

            @Override
            public @NonNls
            @NotNull String ID() {
                return "LogTime";
            }

            @Override
            public void install(@NotNull StatusBar statusBar) {

            }

            @Override
            public void dispose() {
                Disposer.dispose(widget);
            }
        }
    }
}
