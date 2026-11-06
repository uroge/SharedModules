package com.liskovsoft.appupdatechecker2.core;

import static org.junit.Assert.*;

import org.junit.Test;

import java.io.*;
import java.util.*;

public class UpdatePolicyTest {
    private final Set<String> signer = Collections.singleton("known-signing-cert");

    @Test
    public void acceptsOnlyNewOwnPackageWithSameSignerAndManifestVersion() {
        assertTrue(UpdatePolicy.accepts("own.app", 10, signer, "own.app", 11, 11, signer));
        assertFalse(UpdatePolicy.accepts("own.app", 10, signer, "other.app", 11, 11, signer));
        assertFalse(UpdatePolicy.accepts("own.app", 10, signer, "own.app", 10, 10, signer));
        assertFalse(UpdatePolicy.accepts("own.app", 10, signer, "own.app", 9, 9, signer));
        assertFalse(UpdatePolicy.accepts("own.app", 10, signer, "own.app", 11, 12, signer));
        assertFalse(
                UpdatePolicy.accepts(
                        "own.app",
                        10,
                        signer,
                        "own.app",
                        11,
                        11,
                        Collections.singleton("attacker-cert")));
        assertFalse(
                UpdatePolicy.accepts(
                        "own.app",
                        10,
                        Collections.emptySet(),
                        "own.app",
                        11,
                        11,
                        Collections.emptySet()));
    }

    @Test
    public void tamperingChangesDigest() throws Exception {
        File file = File.createTempFile("update-", ".apk");
        try {
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write("original".getBytes("UTF-8"));
            }
            String first = UpdatePolicy.sha256(file);
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write("tampered".getBytes("UTF-8"));
            }
            assertNotEquals(first, UpdatePolicy.sha256(file));
        } finally {
            file.delete();
        }
    }
    @Test public void selectsCompatible32BitFallbackOn64BitX86() {
        assertEquals("x86",UpdatePolicy.selectAbi(new String[]{"x86_64","x86"},new HashSet<>(Arrays.asList("arm64-v8a","armeabi-v7a","x86"))));
    }
    @Test public void prefersSupported64BitArmBuild() {
        assertEquals("arm64-v8a",UpdatePolicy.selectAbi(new String[]{"arm64-v8a","armeabi-v7a"},new HashSet<>(Arrays.asList("arm64-v8a","armeabi-v7a"))));
    }
    @Test public void neverChoosesAnUnsupportedArchitecture() {
        assertNull(UpdatePolicy.selectAbi(new String[]{"x86_64"},Collections.singleton("armeabi-v7a")));
    }
}
