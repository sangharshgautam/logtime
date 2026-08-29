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
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service(Service.Level.APP)
public final class JiraService {
    public static JiraService getInstance() {
        return ApplicationManager.getApplication().getService(JiraService.class);
    }

    public boolean postWorklog(String issueKey, String jsonPayload) {
        String url = String.join("/", ConfigFile.getJiraUrl(), "rest/api/2/issue", issueKey, "worklog");
        HttpURLConnection connection = null;
        try {
            Proxy proxy = buildProxy(ConfigFile.get("settings", "proxy", false));
            connection = (HttpURLConnection) new URL(url).openConnection(proxy == null ? Proxy.NO_PROXY : proxy);
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
            if (status >= 200 && status < 300) {
                return true;
            }
            // Drain the error stream so the connection can be reused/closed cleanly.
            InputStream stream = connection.getErrorStream();
            if (stream != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    while (reader.readLine() != null) {
                        // discard
                    }
                }
            }
            uk.co.sangharsh.logtime.plugin.LogTime.log.warn("Jira worklog POST failed with status " + status + " for " + url);
            return false;
        } catch (IOException e) {
            // Handle network errors gracefully
            e.printStackTrace();
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Parses the user-configured proxy setting (expected {@code host:port}) into a {@link Proxy}.
     * Returns null when the setting is empty, malformed, or not a valid host, in which case the
     * connection uses the default (no) proxy.
     *
     * @param raw the raw proxy string from config, or null
     */
    private Proxy buildProxy(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;

        // Allow an optional scheme prefix (e.g. "http://host:8080").
        if (s.startsWith("http://")) {
            s = s.substring("http://".length());
        } else if (s.startsWith("https://")) {
            s = s.substring("https://".length());
        }

        String host;
        int port = 80;
        int colon = s.lastIndexOf(':');
        if (colon >= 0) {
            host = s.substring(0, colon).trim();
            String portStr = s.substring(colon + 1).trim();
            try {
                port = Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                uk.co.sangharsh.logtime.plugin.LogTime.log.warn("Invalid proxy port in '" + raw + "'");
                return null;
            }
        } else {
            host = s;
        }

        if (host.isEmpty() || port < 1 || port > 65535) {
            uk.co.sangharsh.logtime.plugin.LogTime.log.warn("Invalid proxy setting '" + raw + "'");
            return null;
        }
        return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port));
    }
}
