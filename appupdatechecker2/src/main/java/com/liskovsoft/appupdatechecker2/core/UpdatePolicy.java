package com.liskovsoft.appupdatechecker2.core;

import java.io.*;
import java.security.MessageDigest;
import java.util.*;

public final class UpdatePolicy {
    private UpdatePolicy() {}

    public static boolean accepts(
            String installedPackage,
            long installedVersion,
            Set<String> installedSigners,
            String candidatePackage,
            long candidateVersion,
            long manifestVersion,
            Set<String> candidateSigners) {
        return installedPackage != null
                && installedPackage.equals(candidatePackage)
                && candidateVersion > installedVersion
                && candidateVersion == manifestVersion
                && installedSigners != null
                && !installedSigners.isEmpty()
                && installedSigners.equals(candidateSigners);
    }

    public static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = input.read(buffer)) != -1) digest.update(buffer, 0, n);
        }
        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest())
            result.append(String.format(Locale.ROOT, "%02x", value & 255));
        return result.toString();
    }
}
