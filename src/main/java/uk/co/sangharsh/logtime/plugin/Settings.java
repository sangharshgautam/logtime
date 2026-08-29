/* ==========================================================
File:        Settings.java
Description: LogTime settings dialog (Jira connection, display, debug).
Maintainer:  LogTime <support@logtime.com>
License:     BSD, see LICENSE for more details.
Website:     https://logtime.com/
===========================================================*/

package uk.co.sangharsh.logtime.plugin;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import org.jetbrains.annotations.Nullable;
import uk.co.sangharsh.logtime.plugin.service.UrlValidator;

import javax.swing.*;
import java.awt.*;

public class Settings extends DialogWrapper {
    private final JPanel panel;
    private final JLabel jiraUrlLabel;
    private final JTextField jiraUrl;
    private final JLabel jiraUsernameLabel;
    private final JTextField jiraUsername;
    private final JLabel jiraApiTokenLabel;
    private final JPasswordField jiraApiToken;
    private final JLabel proxyLabel;
    private final JTextField proxy;
    private final JLabel debugLabel;
    private final JCheckBox debug;
    private final JLabel statusBarLabel;
    private final JCheckBox statusBar;

    public Settings(@Nullable Project project) {
        super(project, true);
        setTitle("LogTime Settings");
        setOKButtonText("Save");
        panel = new JPanel();
        panel.setLayout(new GridLayout(0,2));

        jiraUrlLabel = new JLabel("Jira Url:", JLabel.CENTER);
        panel.add(jiraUrlLabel);
        jiraUrl = new JTextField(36);
        jiraUrl.setText(ConfigFile.getJiraUrl());
        panel.add(jiraUrl);

        jiraUsernameLabel = new JLabel("Jira username:", JLabel.CENTER);
        panel.add(jiraUsernameLabel);
        jiraUsername = new JTextField(36);
        jiraUsername.setText(ConfigFile.getJiraUsername());
        panel.add(jiraUsername);

        jiraApiTokenLabel = new JLabel("Jira API token:", JLabel.CENTER);
        panel.add(jiraApiTokenLabel);
        jiraApiToken = new JPasswordField(36);
        jiraApiToken.setText(ConfigFile.getJiraApiToken());
        panel.add(jiraApiToken);

        proxyLabel = new JLabel("Proxy:", JLabel.CENTER);
        panel.add(proxyLabel);
        proxy = new JTextField();
        proxy.setToolTipText("HTTP proxy in the form host:port (e.g. 127.0.0.1:8080). Leave empty for none.");
        String p = ConfigFile.get("settings", "proxy", false);
        if (p == null) p = "";
        proxy.setText(p);
        panel.add(proxy);

        statusBarLabel = new JLabel("Show LogTime in status bar:", JLabel.CENTER);
        panel.add(statusBarLabel);
        String statusBarValue = ConfigFile.get("settings", "status_bar_enabled", false);
        statusBar = new JCheckBox();
        statusBar.setSelected(statusBarValue == null || !statusBarValue.trim().toLowerCase().equals("false"));
        panel.add(statusBar);

        debugLabel = new JLabel("Debug:", JLabel.CENTER);
        panel.add(debugLabel);
        String debugValue = ConfigFile.get("settings", "debug", false);
        debug = new JCheckBox();
        debug.setSelected(debugValue != null && debugValue.trim().toLowerCase().equals("true"));
        panel.add(debug);

        init();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        return panel;
    }

    @Override
    protected ValidationInfo doValidate() {
        boolean isValidUrl = UrlValidator.isValidURL(jiraUrl.getText());
        if (!isValidUrl) {
            return new ValidationInfo("Jira Url is not valid.");
        }
        String proxyText = proxy.getText() == null ? "" : proxy.getText().trim();
        if (!proxyText.isEmpty() && !proxyText.matches("^(https?://)?[A-Za-z0-9._-]+(:\\d{1,5})?$")) {
            return new ValidationInfo("Proxy must be in the form host:port.");
        }
        return null;
    }

    @Override
    public void doOKAction() {
        ConfigFile.setJiraUrl(jiraUrl.getText());
        ConfigFile.setJiraUsername(jiraUsername.getText());
        ConfigFile.setJiraApiToken(new String(jiraApiToken.getPassword()));
        ConfigFile.set("settings", "proxy", false, proxy.getText());
        ConfigFile.set("settings", "debug", false, debug.isSelected() ? "true" : "false");
        ConfigFile.set("settings", "status_bar_enabled", false, statusBar.isSelected() ? "true" : "false");
        LogTime.setupConfigs();
        LogTime.setupStatusBar();
        LogTime.setLoggingLevel();
        super.doOKAction();
    }

}
