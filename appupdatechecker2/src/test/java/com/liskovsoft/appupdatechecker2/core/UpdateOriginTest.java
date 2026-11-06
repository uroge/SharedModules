package com.liskovsoft.appupdatechecker2.core;

import static org.junit.Assert.*;

import org.junit.Test;

public class UpdateOriginTest {
    @Test
    public void acceptsOnlyOwnReleaseUrlsInitially() {
        assertTrue(
                SecureUpdateTransport.allowed(
                        "https://github.com/uroge/SmartTube/releases/download/latest/smarttube_beta2.json",
                        true));
        assertFalse(
                SecureUpdateTransport.allowed(
                        "https://github.com/GeorgeHarbor/SmartTube/releases/download/latest/app.apk",
                        true));
        assertFalse(
                SecureUpdateTransport.allowed(
                        "http://github.com/uroge/SmartTube/releases/download/latest/app.apk",
                        true));
        assertFalse(
                SecureUpdateTransport.allowed(
                        "https://github.com.evil.test/uroge/SmartTube/releases/download/latest/app.apk",
                        true));
        assertFalse(
                SecureUpdateTransport.allowed(
                        "https://evil@github.com/uroge/SmartTube/releases/download/latest/app.apk",
                        true));
    }

    @Test
    public void restrictsRedirectOrigins() {
        assertTrue(
                SecureUpdateTransport.allowed(
                        "https://release-assets.githubusercontent.com/asset", false));
        assertFalse(
                SecureUpdateTransport.allowed(
                        "https://release-assets.githubusercontent.com/asset", true));
        assertFalse(SecureUpdateTransport.allowed("https://example.com/app.apk", false));
    }
}
