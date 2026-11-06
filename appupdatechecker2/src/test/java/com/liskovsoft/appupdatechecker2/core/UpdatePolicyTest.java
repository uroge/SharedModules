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
}
