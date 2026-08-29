package uk.co.sangharsh.logtime.plugin.service;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import uk.co.sangharsh.logtime.plugin.ConfigFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service(Service.Level.APP)
public final class JiraService {
    public static JiraService getInstance() {
        return ApplicationManager.getApplication().getService(JiraService.class);
    }

    public String postWorklog(String issueKey, String jsonPayload) {
        String url = String.join("/", ConfigFile.getJiraUrl(), "rest/api/2/issue", issueKey, "worklog");
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");

            // Jira worklog API requires HTTP Basic authentication using the user's email
            // (or username) and an API token.
            String username = ConfigFile.getJiraUsername();
            String apiToken = ConfigFile.getJiraApiToken();
            if (username != null && !username.isEmpty() &&
                    apiToken != null && !apiToken.isEmpty()) {
                String credentials = username + ":" + apiToken;
                String token = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
                connection.setRequestProperty("Authorization", "Basic " + token);
            }

            connection.setDoOutput(true);
            connection.setConnectTimeout(30_000);
            connection.setReadTimeout(30_000);

            try (OutputStream os = connection.getOutputStream();
                 OutputStreamWriter writer = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
                writer.write(jsonPayload);
                writer.flush();
            }

            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            if (stream == null) return null;

            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line);
                }
            }
            return body.toString();
        } catch (IOException e) {
            // Handle network errors gracefully
            e.printStackTrace();
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
