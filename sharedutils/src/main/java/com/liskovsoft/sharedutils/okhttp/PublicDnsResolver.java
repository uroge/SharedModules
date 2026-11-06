package com.liskovsoft.sharedutils.okhttp;

import okhttp3.*;
import okhttp3.dnsoverhttps.DnsOverHttps;

import java.net.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Encrypted DNS with normal TLS hostname validation and a literal bootstrap address. */
public final class PublicDnsResolver {
    private PublicDnsResolver() {}

    private static Dns create(String url, String bootstrap) {
        try {
            OkHttpClient client =
                    new OkHttpClient.Builder()
                            .dns(Dns.SYSTEM)
                            .connectTimeout(5, TimeUnit.SECONDS)
                            .readTimeout(5, TimeUnit.SECONDS)
                            .build();
            Dns secure =
                    new DnsOverHttps.Builder()
                            .client(client)
                            .url(HttpUrl.get(url))
                            .includeIPv6(true)
                            .bootstrapDnsHosts(InetAddress.getByName(bootstrap))
                            .build();
            return hostname -> {
                List<InetAddress> addresses;
                try {
                    addresses = new ArrayList<>(secure.lookup(hostname));
                } catch (UnknownHostException failure) {
                    addresses = new ArrayList<>(Dns.SYSTEM.lookup(hostname));
                }
                addresses.sort(
                        (a, b) ->
                                Boolean.compare(
                                        a instanceof Inet4Address, b instanceof Inet4Address));
                return addresses;
            };
        } catch (UnknownHostException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static Dns google() {
        return create("https://dns.google/dns-query", "8.8.8.8");
    }

    public static Dns cloudflare() {
        return create("https://cloudflare-dns.com/dns-query", "1.1.1.1");
    }
}
