package com.liskovsoft.sharedutils.okhttp;

import static org.junit.Assert.*;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import okhttp3.*;
import okhttp3.mockwebserver.*;
import okhttp3.tls.*;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class TlsRejectionTest {
    @Test
    public void productionClientRejectsUntrustedCertificate() throws Exception {
        HeldCertificate cert =
                new HeldCertificate.Builder()
                        .commonName("localhost")
                        .addSubjectAlternativeName("localhost")
                        .build();
        HandshakeCertificates serverKeys =
                new HandshakeCertificates.Builder().heldCertificate(cert).build();
        try (MockWebServer server = new MockWebServer()) {
            server.useHttps(serverKeys.sslSocketFactory(), false);
            server.enqueue(new MockResponse().setBody("must not be read"));
            server.start();
            OkHttpClient client = OkHttpCommons.setupBuilder(new OkHttpClient.Builder()).build();
            try (Response ignored =
                    client.newCall(new Request.Builder().url(server.url("/")).build()).execute()) {
                fail("Untrusted certificate accepted");
            } catch (javax.net.ssl.SSLHandshakeException expected) {
            }
        }
    }

    @Test
    public void productionClientRejectsTrustedCertificateForWrongHostname() throws Exception {
        HeldCertificate cert =
                new HeldCertificate.Builder()
                        .commonName("wrong.invalid")
                        .addSubjectAlternativeName("wrong.invalid")
                        .build();
        HandshakeCertificates serverKeys =
                new HandshakeCertificates.Builder().heldCertificate(cert).build();
        HandshakeCertificates clientKeys =
                new HandshakeCertificates.Builder()
                        .addTrustedCertificate(cert.certificate())
                        .build();
        try (MockWebServer server = new MockWebServer()) {
            server.useHttps(serverKeys.sslSocketFactory(), false);
            server.enqueue(new MockResponse().setBody("must not be read"));
            server.start();
            OkHttpClient client =
                    OkHttpCommons.setupBuilder(new OkHttpClient.Builder())
                            .sslSocketFactory(
                                    clientKeys.sslSocketFactory(), clientKeys.trustManager())
                            .build();
            try (Response ignored =
                    client.newCall(new Request.Builder().url(server.url("/")).build()).execute()) {
                fail("Wrong hostname accepted");
            } catch (javax.net.ssl.SSLPeerUnverifiedException expected) {
            }
        }
    }
}
