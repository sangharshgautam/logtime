package com.github.sangharshgautam.logtime.startup;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import kotlin.coroutines.Continuation;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;

public class MyProjectActivity implements ProjectActivity {

    @Override
    public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        Logger.getInstance(MyProjectActivity.class).warn("Don't forget to remove all non-needed sample code files with their corresponding registration entries in `plugin.xml`.");
        return Unit.INSTANCE;
    }
}
