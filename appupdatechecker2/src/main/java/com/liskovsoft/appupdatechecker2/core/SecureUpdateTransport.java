package com.liskovsoft.appupdatechecker2.core;

import java.io.*;
import java.net.*;

import javax.net.ssl.HttpsURLConnection;

/** Update-only transport. Every redirect is checked before making the next request. */
public final class SecureUpdateTransport {
    private SecureUpdateTransport() {}

    public static boolean allowed(String value, boolean initial) {
        try {
            URI uri = new URI(value);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getUserInfo() != null
                    || uri.getFragment() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)) return false;
            String host = uri.getHost(), path = uri.getPath();
            if (path == null) return false;
            for (String segment : path.split("/"))
                if (segment.equals(".") || segment.equals("..")) return false;
            if ("github.com".equalsIgnoreCase(host))
                return path != null && path.startsWith("/uroge/SmartTube/releases/download/");
            return !initial
                    && ("release-assets.githubusercontent.com".equalsIgnoreCase(host)
                            || "objects.githubusercontent.com".equalsIgnoreCase(host));
        } catch (Exception failure) {
            return false;
        }
    }

    public static void fetch(String value, OutputStream output, long limit) throws IOException {
        String current = value;
        for (int redirect = 0; redirect <= 5; redirect++) {
            if (!allowed(current, redirect == 0)) throw new IOException("Untrusted update URL");
            HttpsURLConnection connection = (HttpsURLConnection) new URL(current).openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            try {
                int status = connection.getResponseCode();
                if (status == 301
                        || status == 302
                        || status == 303
                        || status == 307
                        || status == 308) {
                    String location = connection.getHeaderField("Location");
                    if (location == null) throw new IOException("Missing redirect");
                    current = new URL(new URL(current), location).toString();
                    continue;
                }
                if (status != 200) throw new IOException("Update HTTP status " + status);
                long total = 0;
                byte[] buffer = new byte[8192];
                try (InputStream input = connection.getInputStream()) {
                    int size;
                    while ((size = input.read(buffer)) != -1) {
                        total += size;
                        if (total > limit) throw new IOException("Update size limit");
                        output.write(buffer, 0, size);
                    }
                }
                return;
            } finally {
                connection.disconnect();
            }
        }
        throw new IOException("Too many update redirects");
    }
}
