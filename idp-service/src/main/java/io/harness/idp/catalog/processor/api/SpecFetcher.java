/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.processor.api;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.google.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Hardened HTTP fetcher for customer-controlled spec URLs. Public URLs only.
 * Protections: connect/read/write + overall call timeouts; 5 MiB body cap; SSRF block-list
 * (loopback, link-local incl. metadata, RFC1918/RFC4193, plus IPv4 tunnelled inside IPv6 —
 * NAT64/6to4/IPv4-mapped/compatible); 3-redirect cap re-checking SSRF each hop; HTTP/HTTPS only.
 */
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class SpecFetcher {
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration WRITE_TIMEOUT = Duration.ofSeconds(10);
  // Wall-clock cap on the ENTIRE call — readTimeout alone doesn't bound total duration against a
  // slow-drip server. Without this, a malicious URL could pin the fetch thread.
  private static final Duration CALL_TIMEOUT = Duration.ofSeconds(30);
  private static final int MAX_REDIRECTS = 3;
  private static final long MAX_BODY_BYTES = 5L * 1024 * 1024; // 5 MiB
  private static final String USER_AGENT = "HarnessIDP-EndpointExtractor/1.0";
  private static final String ACCEPT =
      "application/json, application/yaml, application/x-yaml, text/yaml, text/plain, */*";

  private final OkHttpClient client;

  public SpecFetcher() {
    // Custom Dns closes the DNS-rebinding TOCTOU window; redirects disabled so we re-check SSRF per hop.
    this.client = new OkHttpClient.Builder()
                      .connectTimeout(CONNECT_TIMEOUT)
                      .readTimeout(READ_TIMEOUT)
                      .writeTimeout(WRITE_TIMEOUT)
                      .callTimeout(CALL_TIMEOUT)
                      .followRedirects(false)
                      .followSslRedirects(false)
                      .retryOnConnectionFailure(false)
                      .dns(SpecFetcher::resolveAndValidate)
                      .build();
  }

  int callTimeoutMillis() {
    return client.callTimeoutMillis();
  }

  private static List<InetAddress> resolveAndValidate(String hostname) throws UnknownHostException {
    InetAddress[] addresses = InetAddress.getAllByName(hostname);
    for (InetAddress addr : addresses) {
      if (isBlockedAddress(addr)) {
        // OkHttp's Dns contract only permits UnknownHostException.
        throw new UnknownHostException("Spec URL host resolves to a blocked address (loopback / link-local / private). "
            + "Only public URLs are supported.");
      }
    }
    return Arrays.asList(addresses);
  }

  public String fetch(String url) {
    if (url == null || url.isBlank()) {
      throw new SpecFetchException("Spec URL must not be empty");
    }

    String current = url;
    for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
      HttpUrl parsed = parseAndValidate(current);
      Request request =
          new Request.Builder().url(parsed).header("User-Agent", USER_AGENT).header("Accept", ACCEPT).get().build();

      try (Response response = client.newCall(request).execute()) {
        if (response.isRedirect()) {
          String location = response.header("Location");
          if (location == null || location.isBlank()) {
            throw new SpecFetchException("Spec URL returned a redirect with no Location header: " + redact(current));
          }
          HttpUrl next = parsed.resolve(location); // resolves relative redirects
          if (next == null) {
            throw new SpecFetchException(
                "Spec URL returned a redirect to an unparseable location: " + redact(location));
          }
          current = next.toString();
          continue;
        }

        if (!response.isSuccessful()) {
          throw new SpecFetchException(String.format(
              "Spec URL returned HTTP %d: verify the URL is correct and publicly accessible.", response.code()));
        }

        return readBoundedBody(response);
      } catch (IOException ex) {
        throw new SpecFetchException(
            "Spec URL could not be reached: " + ex.getMessage() + " (URL: " + redact(current) + ")", ex);
      }
    }

    throw new SpecFetchException("Spec URL exceeded the redirect limit of " + MAX_REDIRECTS + " hops: " + redact(url));
  }

  private HttpUrl parseAndValidate(String url) {
    HttpUrl parsed = HttpUrl.parse(url);
    if (parsed == null) {
      throw new SpecFetchException("Spec URL is not a valid HTTP(S) URL: " + redact(url));
    }
    String scheme = parsed.scheme();
    if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
      throw new SpecFetchException("Spec URL must use http or https. Got: " + scheme);
    }
    rejectIfPrivateHost(parsed.host(), url);
    return parsed;
  }

  private void rejectIfPrivateHost(String host, String originalUrl) {
    if (host == null) {
      throw new SpecFetchException("Spec URL has no host component: " + redact(originalUrl));
    }
    InetAddress[] resolved;
    try {
      resolved = InetAddress.getAllByName(host);
    } catch (UnknownHostException ex) {
      throw new SpecFetchException(
          "Spec URL host could not be resolved: " + host + " (URL: " + redact(originalUrl) + ")", ex);
    }
    for (InetAddress addr : resolved) {
      if (isBlockedAddress(addr)) {
        throw new SpecFetchException("Spec URL host is blocked (loopback / link-local / private address). "
            + "Only public URLs are supported.");
      }
    }
  }

  private static boolean isBlockedAddress(InetAddress addr) {
    if (addr.isLoopbackAddress() || addr.isLinkLocalAddress() || addr.isAnyLocalAddress()
        || addr.isMulticastAddress()) {
      return true;
    }
    if (addr instanceof Inet4Address) {
      return isPrivateIpv4(addr.getAddress());
    }
    if (addr instanceof Inet6Address) {
      byte[] bytes = addr.getAddress();
      // fc00::/7 (RFC 4193 unique local).
      if ((bytes[0] & (byte) 0xFE) == (byte) 0xFC) {
        return true;
      }
      // IPv4 tunnelled inside IPv6 (NAT64/6to4/IPv4-mapped/compatible) — check the embedded v4.
      byte[] embeddedV4 = extractEmbeddedIpv4(bytes);
      if (embeddedV4 != null) {
        try {
          return isBlockedAddress(InetAddress.getByAddress(embeddedV4));
        } catch (UnknownHostException ex) {
          return true; // fail closed
        }
      }
    }
    return false;
  }

  /** Extracts the embedded IPv4 from IPv4-in-IPv6 forms (6to4, NAT64, IPv4-mapped/compatible). */
  private static byte[] extractEmbeddedIpv4(byte[] v6) {
    if (v6 == null || v6.length != 16) {
      return null;
    }
    // 6to4: 2002::/16 — IPv4 in bytes 2..5.
    if ((v6[0] & 0xFF) == 0x20 && (v6[1] & 0xFF) == 0x02) {
      return new byte[] {v6[2], v6[3], v6[4], v6[5]};
    }
    // NAT64: 64:ff9b::/96 — IPv4 in bytes 12..15.
    if ((v6[0] & 0xFF) == 0x00 && (v6[1] & 0xFF) == 0x64 && (v6[2] & 0xFF) == 0xFF && (v6[3] & 0xFF) == 0x9B
        && allZero(v6, 4, 12)) {
      return new byte[] {v6[12], v6[13], v6[14], v6[15]};
    }
    // IPv4-mapped (::ffff:0:0/96) and IPv4-compatible (::/96).
    if (allZero(v6, 0, 10)) {
      boolean mapped = (v6[10] & 0xFF) == 0xFF && (v6[11] & 0xFF) == 0xFF;
      boolean compatible = v6[10] == 0 && v6[11] == 0;
      if (mapped || compatible) {
        return new byte[] {v6[12], v6[13], v6[14], v6[15]};
      }
    }
    return null;
  }

  private static boolean allZero(byte[] bytes, int fromInclusive, int toExclusive) {
    for (int i = fromInclusive; i < toExclusive; i++) {
      if (bytes[i] != 0) {
        return false;
      }
    }
    return true;
  }

  private static boolean isPrivateIpv4(byte[] bytes) {
    int first = bytes[0] & 0xFF;
    int second = bytes[1] & 0xFF;
    if (first == 10) {
      return true; // 10.0.0.0/8
    }
    if (first == 172 && second >= 16 && second <= 31) {
      return true; // 172.16.0.0/12
    }
    if (first == 192 && second == 168) {
      return true; // 192.168.0.0/16
    }
    if (first == 100 && second >= 64 && second <= 127) {
      return true; // 100.64.0.0/10 (CGN)
    }
    return false;
  }

  /** Streams the body with a hard byte cap so a huge URL can't exhaust memory. */
  private String readBoundedBody(Response response) throws IOException {
    ResponseBody body = response.body();
    if (body == null) {
      throw new SpecFetchException("Spec URL returned an empty body.");
    }
    // Reject early if the declared Content-Length already exceeds the cap.
    long declared = body.contentLength();
    if (declared > MAX_BODY_BYTES) {
      throw new SpecFetchException("Spec exceeds the " + (MAX_BODY_BYTES / (1024 * 1024)) + " MiB size limit (declared "
          + declared + " bytes). Consider splitting the API into multiple entities.");
    }
    byte[] buffer = new byte[8192];
    try (InputStream in = body.byteStream(); java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
      long total = 0;
      int n;
      while ((n = in.read(buffer)) != -1) {
        total += n;
        if (total > MAX_BODY_BYTES) {
          throw new SpecFetchException("Spec exceeds the " + (MAX_BODY_BYTES / (1024 * 1024))
              + " MiB size limit. Consider splitting the API into multiple entities.");
        }
        out.write(buffer, 0, n);
      }
      return out.toString(StandardCharsets.UTF_8);
    }
  }

  /** Reduces a URL to scheme+host+path for logs/errors, dropping query params. */
  private static String redact(String url) {
    if (url == null) {
      return "<null>";
    }
    try {
      URI uri = URI.create(url);
      StringBuilder sb = new StringBuilder();
      if (uri.getScheme() != null) {
        sb.append(uri.getScheme()).append("://");
      }
      if (uri.getHost() != null) {
        sb.append(uri.getHost());
      }
      if (uri.getPath() != null) {
        sb.append(uri.getPath());
      }
      return sb.toString();
    } catch (Exception ex) {
      return url.length() <= 256 ? url : url.substring(0, 256) + "…";
    }
  }
}
