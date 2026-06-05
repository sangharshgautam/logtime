package com.github.sangharshgautam.logtime.services;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.github.sangharshgautam.logtime.MyBundle;

import java.util.Random;

@Service(Service.Level.PROJECT)
public final class MyProjectService {
    private final Project project;
    private final Random random = new Random();

    public MyProjectService(Project project) {
        this.project = project;
        Logger.getInstance(MyProjectService.class).info(MyBundle.message("projectService", project.getName()));
        Logger.getInstance(MyProjectService.class).warn("Don't forget to remove all non-needed sample code files with their corresponding registration entries in `plugin.xml`.");
    }

    public int getRandomNumber() {
        return random.nextInt(100) + 1;
    }
}
