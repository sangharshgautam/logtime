package uk.co.sangharsh.logtime.plugin.service;

import java.net.URI;
import java.net.URISyntaxException;

public class UrlValidator {
    public static boolean isValidURL(String urlString) {
        if (urlString == null || urlString.isBlank()) {
            return false;
        }
        try {
            // 1. Check syntax by parsing into a URI instance
            URI uri = new URI(urlString);

            // 2. Enforce absolute URLs containing an explicit protocol scheme (like http/https)
            if (uri.getScheme() == null) {
                return false;
            }

            // 3. Ensure a valid host/domain target exists
            if (uri.getHost() == null) {
                return false;
            }

            return true;
        } catch (URISyntaxException e) {
            // Malformed syntax string intercepted
            return false;
        }
    }
}

