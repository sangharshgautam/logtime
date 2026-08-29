/* ==========================================================
File:        ConfigFile.java
Description: Read and write settings from the INI config file.
Maintainer:  LogTime <support@logtime.com>
License:     BSD, see LICENSE for more details.
Website:     https://logtime.com/
===========================================================*/

package uk.co.sangharsh.logtime.plugin;

import java.io.*;

public class ConfigFile {
    private static final String fileName = "logtime.cfg";
    private static final String internalFileName = "logtime-internal.cfg";
    private static String cachedHomeFolder = null;
    private static String _jira_url = "";
    private static String _jira_username = "";
    private static String _jira_api_token = "";
    private static boolean _usingVaultCmd = false;
    private static String _instance_id = "";

    public static String getInstanceId() {
        if (!ConfigFile._instance_id.equals("")) {
            return ConfigFile._instance_id;
        }
        String id = get("settings", "instance_id", false);
        if (id == null || id.trim().equals("")) {
            id = java.util.UUID.randomUUID().toString();
            set("settings", "instance_id", false, id);
        }
        ConfigFile._instance_id = id;
        return id;
    }

    private static String getConfigFilePath(boolean internal) {
        if (ConfigFile.cachedHomeFolder == null) {
            String logtimeHome = System.getenv("LOGTIME_HOME");
            if (logtimeHome != null && !logtimeHome.trim().isEmpty()) {
                File folder = new File(logtimeHome.trim());
                ConfigFile.cachedHomeFolder = folder.getAbsolutePath();
                LogTime.log.debug("Using $LOGTIME_HOME for config folder: " + ConfigFile.cachedHomeFolder);
                if (internal) {
                    return new File(ConfigFile.cachedHomeFolder, internalFileName).getAbsolutePath();
                }
                return new File(ConfigFile.cachedHomeFolder, fileName).getAbsolutePath();
            }
            ConfigFile.cachedHomeFolder = new File(System.getProperty("user.home"), ".logtime").getAbsolutePath();
            LogTime.log.debug("Using $HOME for config folder: " + ConfigFile.cachedHomeFolder);
        }
        if (internal) {
            return new File(ConfigFile.cachedHomeFolder, internalFileName).getAbsolutePath();
        }
        return new File(ConfigFile.cachedHomeFolder, fileName).getAbsolutePath();
    }

    public static String get(String section, String key, boolean internal) {
        String file = ConfigFile.getConfigFilePath(internal);
        String val = null;
        try {
            BufferedReader br = new BufferedReader(new FileReader(file));
            String currentSection = "";
            try {
                String line = br.readLine();
                while (line != null) {
                    if (line.trim().startsWith("[") && line.trim().endsWith("]")) {
                        currentSection = line.trim().substring(1, line.trim().length() - 1).toLowerCase();
                    } else {
                        if (section.toLowerCase().equals(currentSection)) {
                            String[] parts = line.split("=");
                            if (parts.length == 2 && parts[0].trim().equals(key)) {
                                val = parts[1].trim();
                                br.close();
                                return removeNulls(val);
                            }
                        }
                    }
                    line = br.readLine();
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (FileNotFoundException e1) { /* ignored */ }
        return removeNulls(val);
    }

    public static void set(String section, String key, boolean internal, String val) {
        key = removeNulls(key);
        val = removeNulls(val);

        String file = ConfigFile.getConfigFilePath(internal);
        StringBuilder contents = new StringBuilder();
        try {
            BufferedReader br = new BufferedReader(new FileReader(file));
            try {
                String currentSection = "";
                String line = br.readLine();
                Boolean found = false;
                while (line != null) {
                    line = removeNulls(line);
                    if (line.trim().startsWith("[") && line.trim().endsWith("]")) {
                        if (section.toLowerCase().equals(currentSection) && !found) {
                            contents.append(key + " = " + val + "\n");
                            found = true;
                        }
                        currentSection = line.trim().substring(1, line.trim().length() - 1).toLowerCase();
                        contents.append(line + "\n");
                    } else {
                        if (section.toLowerCase().equals(currentSection)) {
                            String[] parts = line.split("=");
                            String currentKey = parts[0].trim();
                            if (currentKey.equals(key)) {
                                if (!found) {
                                    contents.append(key + " = " + val + "\n");
                                    found = true;
                                }
                            } else {
                                contents.append(line + "\n");
                            }
                        } else {
                            contents.append(line + "\n");
                        }
                    }
                    line = br.readLine();
                }
                if (!found) {
                    if (!section.toLowerCase().equals(currentSection)) {
                        contents.append("[" + section.toLowerCase() + "]\n");
                    }
                    contents.append(key + " = " + val + "\n");
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (FileNotFoundException e1) {

            // cannot read config file, so create it
            contents = new StringBuilder();
            contents.append("[" + section.toLowerCase() + "]\n");
            contents.append(key + " = " + val + "\n");
        }

        PrintWriter writer = null;
        File parent = new File(file).getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try {
            writer = new PrintWriter(file, "UTF-8");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        if (writer != null) {
            writer.print(contents.toString());
            writer.close();
        }
    }

    public static String getJiraUrl() {
        if (ConfigFile._usingVaultCmd) {
            return "";
        }
        if (!ConfigFile._jira_url.equals("")) {
            return ConfigFile._jira_url;
        }

        String jiraUrl = get("settings", "jira_url", false);
        if (jiraUrl == null) {
            String vaultCmd = get("settings", "jira_api_token_vault_cmd", false);
            if (vaultCmd != null && !vaultCmd.trim().equals("")) {
                ConfigFile._usingVaultCmd = true;
                return "";
            }
            jiraUrl = "";
        }

        ConfigFile._jira_url = jiraUrl;
        return jiraUrl;
    }

    public static boolean usingVaultCmd() {
        return ConfigFile._usingVaultCmd;
    }

    public static String getJiraUsername() {
        if (ConfigFile._usingVaultCmd) {
            return "";
        }
        if (!ConfigFile._jira_username.equals("")) {
            return ConfigFile._jira_username;
        }
        String username = get("settings", "jira_username", false);
        ConfigFile._jira_username = username == null ? "" : username;
        return ConfigFile._jira_username;
    }

    public static void setJiraUsername(String username) {
        set("settings", "jira_username", false, username);
        ConfigFile._jira_username = username;
    }

    public static String getJiraApiToken() {
        if (ConfigFile._usingVaultCmd) {
            return "";
        }
        if (!ConfigFile._jira_api_token.equals("")) {
            return ConfigFile._jira_api_token;
        }
        String token = get("settings", "jira_api_token", false);
        ConfigFile._jira_api_token = token == null ? "" : token;
        return ConfigFile._jira_api_token;
    }

    public static void setJiraApiToken(String token) {
        set("settings", "jira_api_token", false, token);
        ConfigFile._jira_api_token = token;
    }

    public static void setJiraUrl(String jiraUrl) {
        set("settings", "jira_url", false, jiraUrl);
        ConfigFile._jira_url = jiraUrl;
    }

    private static String removeNulls(String s) {
        if (s == null) return null;
        return s.replace("\0", "");
    }

}
