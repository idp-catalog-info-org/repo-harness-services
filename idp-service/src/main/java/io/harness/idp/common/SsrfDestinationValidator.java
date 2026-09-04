/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.common;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import lombok.experimental.UtilityClass;

/**
 * Shared SSRF address policy (IDP-10919). Loopback, link-local (incl. cloud metadata),
 * RFC1918/RFC4193, and IPv4 tunnelled inside IPv6 (NAT64/6to4/IPv4-mapped/compatible).
 *
 * <p>Extracted out of {@code SpecFetcher} (catalog/processor/api) so {@code idp-service}'s other SSRF checks (e.g.
 * backend-proxy target validation in configmanager/utils) do not each reimplement the same address classification.
 * Lives here rather than in catalog/processor/api because both consumers already depend on {@code idp.common}, and
 * a general-purpose address policy should not force a config-management package to depend on a catalog-processing
 * one.
 */
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
@UtilityClass
public class SsrfDestinationValidator {
  public boolean isBlockedAddress(InetAddress address) {
    if (address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isAnyLocalAddress()
        || address.isMulticastAddress()) {
      return true;
    }
    if (address instanceof Inet4Address) {
      return isPrivateIpv4(address.getAddress());
    }
    if (address instanceof Inet6Address) {
      byte[] bytes = address.getAddress();
      // fc00::/7 (RFC 4193 unique local).
      if ((bytes[0] & (byte) 0xFE) == (byte) 0xFC) {
        return true;
      }
      // IPv4 tunnelled inside IPv6 (NAT64/6to4/IPv4-mapped/compatible) — check the embedded v4.
      byte[] embeddedV4 = extractEmbeddedIpv4(bytes);
      if (embeddedV4 != null) {
        try {
          return isBlockedAddress(InetAddress.getByAddress(embeddedV4));
        } catch (UnknownHostException e) {
          return true; // fail closed
        }
      }
    }
    return false;
  }

  private boolean isPrivateIpv4(byte[] bytes) {
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

  /** Extracts the embedded IPv4 from IPv4-in-IPv6 forms (6to4, NAT64, IPv4-mapped/compatible). */
  private byte[] extractEmbeddedIpv4(byte[] v6) {
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

  private boolean allZero(byte[] bytes, int fromInclusive, int toExclusive) {
    for (int i = fromInclusive; i < toExclusive; i++) {
      if (bytes[i] != 0) {
        return false;
      }
    }
    return true;
  }
}
