/* ==========================================================
File:        ApiKey.java
Description: Prompts user for api key if it does not exist.
Maintainer:  WakaTime <support@wakatime.com>
License:     BSD, see LICENSE for more details.
Website:     https://wakatime.com/
===========================================================*/

package uk.co.sangharsh.logtime.plugin;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.Nullable;
import uk.co.sangharsh.logtime.plugin.service.UrlValidator;

import javax.swing.*;
import java.awt.*;

public class JiraUrl extends DialogWrapper {
    private final JPanel panel;
    private final JLabel label;
    private final JTextField input;
    private final LinkPane link;

    public static boolean isDialogOpened = false;

    public JiraUrl(@Nullable Project project) {
        super(project, true);
        setTitle("Jira Url");
        setOKButtonText("Save");
        panel = new JPanel();
        panel.setLayout(new GridLayout(0,1));
        label  = new JLabel("Enter your Jira Url:", JLabel.CENTER);
        panel.add(label);
        input = new JTextField(36);
        panel.add(input);
        link = new LinkPane("https://wakatime.com/api-key");
        panel.add(link);

        Disposer.register(getDisposable(), () -> isDialogOpened = false);

        init();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        return panel;
    }

    @Override
    protected ValidationInfo doValidate() {
        String jiraUrl = input.getText();
        boolean isValidUrl = UrlValidator.isValidURL(jiraUrl);
        if(!isValidUrl){
            return new ValidationInfo("Jira Url is not valid.");
        }
        return null;
    }

    @Override
    public void doOKAction() {
        ConfigFile.setJiraUrl(input.getText());
        super.doOKAction();
    }

    @Override
    public void doCancelAction() {
        WakaTime.cancelApiKey = true;
        super.doCancelAction();
    }

    @Override
    public void show() {
        isDialogOpened = true;
        super.show();
    }

    public String promptForJiraUrl() {
        input.setText(ConfigFile.getJiraUrl());
        this.show();
        return input.getText();
    }
}

