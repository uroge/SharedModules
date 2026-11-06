package com.liskovsoft.appupdatechecker2.other.downloadmanager;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/** Compatibility adapter; the secure updater itself uses its own strict HTTPS transport. */
public final class GoogleResolver {
    public List<InetAddress> resolve(String host) {
        try {
            return com.liskovsoft.sharedutils.okhttp.PublicDnsResolver.google().lookup(host);
        } catch (UnknownHostException failure) {
            throw new IllegalStateException("DNS lookup failed", failure);
        }
    }
}
