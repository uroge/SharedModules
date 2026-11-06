package com.liskovsoft.sharedutils.prefs;

import static org.junit.Assert.*;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SecureCredentialStoreTest {
    @Test
    public void encryptsRoundTripsAndDeletesCredentials() throws Exception {
        android.content.Context context =
                InstrumentationRegistry.getInstrumentation().getTargetContext();
        SecureCredentialStore store = new SecureCredentialStore(context);
        String name = "security_test";
        try {
            store.write(name, "refresh-token-test-value");
            assertEquals("refresh-token-test-value", store.read(name));
            byte[] first =
                    new android.util.AtomicFile(
                                    new java.io.File(
                                            context.getNoBackupFilesDir(), "credentials-" + name))
                            .readFully();
            assertFalse(
                    new String(first, java.nio.charset.StandardCharsets.UTF_8)
                            .contains("refresh-token-test-value"));
            store.write(name, "refresh-token-test-value");
            byte[] second =
                    new android.util.AtomicFile(
                                    new java.io.File(
                                            context.getNoBackupFilesDir(), "credentials-" + name))
                            .readFully();
            assertFalse(java.util.Arrays.equals(first, second));
        } finally {
            store.delete(name);
        }
        assertFalse(store.contains(name));
    }

    @Test
    public void invalidatedKeyDoesNotReturnPlaintext() throws Exception {
        android.content.Context context =
                InstrumentationRegistry.getInstrumentation().getTargetContext();
        SecureCredentialStore store = new SecureCredentialStore(context);
        String name = "lost_key_test";
        try {
            store.write(name, "secret");
            java.security.KeyStore keyStore = java.security.KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            keyStore.deleteEntry(context.getPackageName() + ".credentials." + name);
            try {
                store.read(name);
                fail("Missing key accepted");
            } catch (java.security.KeyStoreException expected) {
            }
        } finally {
            store.delete(name);
        }
    }
    @Test public void migratesAndRemovesLegacyCredentialOnlyAfterEncryptedRoundTrip()throws Exception{
        android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        GlobalPreferences preferences=GlobalPreferences.instance(context);preferences.resetAccountCredentials();
        java.io.File legacy=new java.io.File(context.getFilesDir(),"global_prefs/media_service_account_data");legacy.getParentFile().mkdirs();
        try{
            try(java.io.FileOutputStream out=new java.io.FileOutputStream(legacy)){out.write("legacy-test-token".getBytes(java.nio.charset.StandardCharsets.UTF_8));}
            assertEquals("legacy-test-token",preferences.getMediaServiceAccountData());
            assertFalse(legacy.exists());
            assertEquals("legacy-test-token",new SecureCredentialStore(context).read("media_service_account_data"));
            assertEquals("legacy-test-token",preferences.getMediaServiceAccountData());
        }finally{preferences.resetAccountCredentials();}
    }
}
