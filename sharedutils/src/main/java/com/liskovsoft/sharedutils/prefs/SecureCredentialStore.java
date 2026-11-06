package com.liskovsoft.sharedutils.prefs;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Account records only; never change the storage format of ordinary settings. */
final class SecureCredentialStore {
    private final Context context;

    SecureCredentialStore(Context context) {
        this.context = context.getApplicationContext();
    }

    private File file(String name) {
        return new File(context.getNoBackupFilesDir(), "credentials-" + name);
    }

    boolean contains(String name) {
        return file(name).exists() || new File(file(name).getPath() + ".bak").exists();
    }

    private String alias(String name) {
        return context.getPackageName() + ".credentials." + name;
    }

    private SecretKey key(String name, boolean create) throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(alias(name))) return (SecretKey) store.getKey(alias(name), null);
        if (!create) throw new java.security.KeyStoreException("Credential key unavailable");
        KeyGenerator generator =
                KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(
                new KeyGenParameterSpec.Builder(
                                alias(name),
                                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setRandomizedEncryptionRequired(true)
                        .build());
        return generator.generateKey();
    }

    synchronized String read(String name) throws Exception {
        byte[] payload = new AtomicFile(file(name)).readFully();
        if (payload.length < 30 || payload[0] != 1)
            throw new java.io.IOException("Invalid credential record");
        byte[] iv = java.util.Arrays.copyOfRange(payload, 1, 13);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(name, false), new GCMParameterSpec(128, iv));
        cipher.updateAAD((context.getPackageName() + ":" + name).getBytes(StandardCharsets.UTF_8));
        return new String(cipher.doFinal(payload, 13, payload.length - 13), StandardCharsets.UTF_8);
    }

    synchronized void write(String name, String value) throws Exception {
        if (value == null || value.trim().isEmpty()) {
            delete(name);
            return;
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key(name, true));
        cipher.updateAAD((context.getPackageName() + ":" + name).getBytes(StandardCharsets.UTF_8));
        byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        byte[] payload =
                ByteBuffer.allocate(1 + 12 + encrypted.length)
                        .put((byte) 1)
                        .put(cipher.getIV())
                        .put(encrypted)
                        .array();
        AtomicFile target = new AtomicFile(file(name));
        FileOutputStream out = null;
        try {
            out = target.startWrite();
            out.write(payload);
            target.finishWrite(out);
        } catch (Exception failure) {
            if (out != null) target.failWrite(out);
            throw failure;
        }
        if (!value.equals(read(name)))
            throw new java.io.IOException("Credential verification failed");
    }

    synchronized void delete(String name) throws Exception {
        new AtomicFile(file(name)).delete();
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        store.deleteEntry(alias(name));
    }
}
