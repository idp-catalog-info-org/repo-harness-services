/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.dto;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.common.beans.PGPKeyIdentity;
import io.harness.ng.core.common.beans.PGPKeyUsage;
import io.harness.ng.core.common.beans.PGPPublicKey;

import java.util.List;
import javax.validation.constraints.NotEmpty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder(builderClassName = "Builder")
@OwnedBy(HarnessTeam.PL)
public class PGPPublicKeyDTOInternal {
  private String algorithm;
  @NotEmpty private String content;

  private String comment;
  private String fingerprint;
  private String keyId;

  private List<PGPKeyUsage> usage;
  private Integer bitLength;

  private Long timestamp;
  private Long validFrom;
  private Long validTo;

  private List<PGPKeyIdentity> identities;
  private PGPKeyIdentity primaryIdentity;
  private String parentKeyId;
  private Boolean isSubKey;
  private List<PGPPublicKeyDTOInternal> subKeys;

  public PGPPublicKey toPGPKey() {
    return PGPPublicKey.builder()
        .algorithm(algorithm)
        .pgpKeyContent(content)
        .comment(comment)
        .fingerprint(fingerprint)
        .keyId(keyId)
        .usage(usage)
        .bitLength(bitLength)
        .validFrom(validFrom)
        .validTo(validTo)
        .identities(identities)
        .primaryIdentity(primaryIdentity)
        .parentKeyId(parentKeyId)
        .isSubKey(isSubKey)
        .build();
  }
}