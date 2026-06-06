package uk.co.sangharsh.logtime.plugin;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

public class LogTimeStartupActivity implements StartupActivity.Background {

    @Override
    public void runActivity(@NotNull Project project) {
        LogTime.checkApiKey();
    }
}
