package uk.co.sangharsh.logtime.intellij.plugin.service;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.util.io.HttpRequests;
import com.wakatime.intellij.plugin.ConfigFile;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

@Service(Service.Level.APP)
public final class JiraService {
    public static JiraService getInstance() {
        return ApplicationManager.getApplication().getService(JiraService.class);
    }
    public String postWorklog(String issueKey, String jsonPayload) {
        String url = String.join("/",ConfigFile.getJiraUrl(), "rest/api/2/issue", issueKey, "worklog");
        try {
            return HttpRequests.post(url,"application/json")
//                    .tweakRequest(connection -> {
//                        // Add custom headers here if needed
//                        connection.setRequestProperty("Authorization", "Bearer your_token_here");
//                    })
                    .connect(request -> {
                        // 2. Write the JSON payload to the request body
                        try (OutputStreamWriter writer = new OutputStreamWriter(
                                request.getConnection().getOutputStream(), StandardCharsets.UTF_8)) {
                            writer.write(jsonPayload);
                            writer.flush();
                        }
                        // 3. Read and return the server's response
                        return request.readString();
                    });
        } catch (IOException e) {
            // Handle network errors gracefully
            e.printStackTrace();
            return null;
        }
    }
}
