/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.processor.api;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.common.SsrfDestinationValidator;

import com.google.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
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
      if (SsrfDestinationValidator.isBlockedAddress(addr)) {
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
      if (SsrfDestinationValidator.isBlockedAddress(addr)) {
        throw new SpecFetchException("Spec URL host is blocked (loopback / link-local / private address). "
            + "Only public URLs are supported.");
      }
    }
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
