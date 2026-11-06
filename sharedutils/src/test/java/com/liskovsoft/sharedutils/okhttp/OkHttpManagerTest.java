package com.liskovsoft.sharedutils.okhttp;
import java.net.*;
import java.util.concurrent.TimeUnit;
import okhttp3.*;
import okhttp3.mockwebserver.*;
import org.junit.Test;
import static org.junit.Assert.*;
/** Bounded loopback proxy test. No public proxy, public credentials, or external network. */
public class OkHttpManagerTest {
    @Test public void configuredProxyReceivesRequestsAndAuthentication()throws Exception {
        try(MockWebServer proxy=new MockWebServer()) {
            proxy.enqueue(new MockResponse().setResponseCode(407).setHeader("Proxy-Authenticate","Basic realm=test"));
            proxy.enqueue(new MockResponse().setBody("proxied"));
            proxy.start();
            OkHttpClient client=new OkHttpClient.Builder()
                .proxy(new Proxy(Proxy.Type.HTTP,new InetSocketAddress("127.0.0.1",proxy.getPort())))
                .connectTimeout(2,TimeUnit.SECONDS).readTimeout(2,TimeUnit.SECONDS).callTimeout(5,TimeUnit.SECONDS)
                .proxyAuthenticator((route,response)->response.request().newBuilder().header("Proxy-Authorization",Credentials.basic("test-user","test-password")).build()).build();
            try(Response response=client.newCall(new Request.Builder().url("http://unit-test.invalid/test").build()).execute()) {
                assertEquals(200,response.code());assertEquals("proxied",response.body().string());
            }
            RecordedRequest first=proxy.takeRequest(2,TimeUnit.SECONDS),authenticated=proxy.takeRequest(2,TimeUnit.SECONDS);
            assertNotNull(first);assertNotNull(authenticated);
            assertTrue(first.getRequestLine().contains("http://unit-test.invalid/test"));
            assertEquals(Credentials.basic("test-user","test-password"),authenticated.getHeader("Proxy-Authorization"));
        }
    }
}
