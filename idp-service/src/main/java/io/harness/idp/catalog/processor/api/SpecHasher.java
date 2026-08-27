/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.processor.api;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import lombok.experimental.UtilityClass;

/**
 * Computes a stable content hash of a resolved OpenAPI spec.
 *
 * Invariant: the hash is a function of the fetched spec bytes only. Nothing in {@code metadata.apis}
 * (especially {@code lastCheckedAt}) feeds it — otherwise every cursor stamp would look like drift
 * and defeat the hash-skip loop-breaker.
 */
@UtilityClass
@OwnedBy(HarnessTeam.IDP)
public class SpecHasher {
  private static final String SHA_256 = "SHA-256";

  /**
   * Hex-encoded SHA-256 of the resolved spec content. Input must be the post-{@code
   * SpecSourceResolver} content (what the parser sees), not raw {@code spec.definition} — else a
   * {@code $yaml} Git update wouldn't change the hash and drift would go undetected.
   */
  public static String hash(String resolvedContent) {
    if (resolvedContent == null) {
      throw new IllegalArgumentException("resolvedContent must not be null");
    }
    try {
      MessageDigest digest = MessageDigest.getInstance(SHA_256);
      byte[] bytes = digest.digest(resolvedContent.getBytes(StandardCharsets.UTF_8));
      return toHex(bytes);
    } catch (NoSuchAlgorithmException ex) {
      // SHA-256 is mandated by every standard JRE; unreachable.
      throw new IllegalStateException("SHA-256 not available in JRE", ex);
    }
  }

  private static String toHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      sb.append(Character.forDigit((b >> 4) & 0xF, 16));
      sb.append(Character.forDigit(b & 0xF, 16));
    }
    return sb.toString();
  }
}
