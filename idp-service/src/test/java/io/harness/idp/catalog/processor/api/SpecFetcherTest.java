/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.processor.api;

import static io.harness.rule.OwnerRule.ROUNAK;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class SpecFetcherTest extends CategoryTest {
  private SpecFetcher fetcher;

  @Before
  public void setUp() {
    fetcher = new SpecFetcher();
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void blocksLoopbackUrl() {
    assertThatThrownBy(() -> fetcher.fetch("http://127.0.0.1:8080/spec.json"))
        .isInstanceOf(SpecFetchException.class)
        .hasMessageContaining("blocked");
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void blocksLocalhost() {
    assertThatThrownBy(() -> fetcher.fetch("http://localhost/spec.json"))
        .isInstanceOf(SpecFetchException.class)
        .hasMessageContaining("blocked");
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void blocksCloudMetadataAddress() {
    // 169.254.169.254 is the AWS/GCP/Azure cloud metadata endpoint — a classic SSRF target.
    assertThatThrownBy(() -> fetcher.fetch("http://169.254.169.254/latest/meta-data/"))
        .isInstanceOf(SpecFetchException.class)
        .hasMessageContaining("blocked");
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void blocksRfc1918PrivateRange() {
    assertThatThrownBy(() -> fetcher.fetch("http://10.0.0.1/spec.json"))
        .isInstanceOf(SpecFetchException.class)
        .hasMessageContaining("blocked");
    assertThatThrownBy(() -> fetcher.fetch("http://192.168.1.1/spec.json"))
        .isInstanceOf(SpecFetchException.class)
        .hasMessageContaining("blocked");
    assertThatThrownBy(() -> fetcher.fetch("http://172.16.5.5/spec.json"))
        .isInstanceOf(SpecFetchException.class)
        .hasMessageContaining("blocked");
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void blocksCloudMetadataViaNat64() {
    // NAT64 (64:ff9b::/96) embeds 169.254.169.254 in its last 4 bytes. The JDK keeps this as an
    // Inet6Address (it does NOT collapse it), so without embedded-IPv4 decoding it would slip past
    // the fc00::/7 check and a NAT64 gateway would translate it back to the cloud metadata IP.
    assertThatThrownBy(() -> fetcher.fetch("http://[64:ff9b::a9fe:a9fe]/latest/meta-data/"))
        .isInstanceOf(SpecFetchException.class)
        .hasMessageContaining("blocked");
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void blocksCloudMetadataVia6to4() {
    // 6to4 (2002::/16) embeds the IPv4 in bytes 2..5 → 2002:a9fe:a9fe:: carries 169.254.169.254.
    assertThatThrownBy(() -> fetcher.fetch("http://[2002:a9fe:a9fe::]/latest/meta-data/"))
        .isInstanceOf(SpecFetchException.class)
        .hasMessageContaining("blocked");
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void blocksRfc1918ViaNat64() {
    // 10.0.0.1 tunnelled through NAT64 must be blocked just like the bare IPv4 form.
    assertThatThrownBy(() -> fetcher.fetch("http://[64:ff9b::0a00:0001]/spec.json"))
        .isInstanceOf(SpecFetchException.class)
        .hasMessageContaining("blocked");
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void rejectsNonHttpScheme() {
    assertThatThrownBy(() -> fetcher.fetch("file:///etc/passwd")).isInstanceOf(SpecFetchException.class);
    assertThatThrownBy(() -> fetcher.fetch("ftp://example.com/spec.json")).isInstanceOf(SpecFetchException.class);
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void rejectsMalformedUrl() {
    assertThatThrownBy(() -> fetcher.fetch("not a url")).isInstanceOf(SpecFetchException.class);
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void rejectsNullAndEmpty() {
    assertThatThrownBy(() -> fetcher.fetch(null)).isInstanceOf(SpecFetchException.class).hasMessageContaining("empty");
    assertThatThrownBy(() -> fetcher.fetch("")).isInstanceOf(SpecFetchException.class);
    assertThatThrownBy(() -> fetcher.fetch("   ")).isInstanceOf(SpecFetchException.class);
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void clientHasOverallCallTimeout() {
    // A non-zero overall call timeout is the guard against a slow-drip server (1 byte just inside
    // each readTimeout window) pinning the fetch thread until the 5 MiB cap — millions of reads,
    // effectively forever. readTimeout alone does NOT bound total duration. Without callTimeout a
    // single malicious/slow URL can exhaust the shared consumer/iterator thread pools.
    assertThat(fetcher.callTimeoutMillis()).isGreaterThan(0);
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void exceptionMessageDoesNotLeakLongQueryStrings() {
    // The redact() helper truncates URLs in messages; this protects against accidentally logging
    // query strings carrying sensitive identifiers. Verify the loopback rejection path does not
    // surface the raw query.
    String spy = "http://127.0.0.1/path?token=verysecret123";
    try {
      fetcher.fetch(spy);
    } catch (SpecFetchException ex) {
      // The blocked-host message is generic and shouldn't include the token. (The redact() helper
      // is used in error paths that echo the URL; the SSRF block message intentionally omits it.)
      assertThat(ex.getMessage()).doesNotContain("verysecret123");
    }
  }
}
