/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.dto;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.common.beans.SSHKeyUsage;
import io.harness.ng.core.common.beans.SSHPublicKey;

import java.util.List;
import javax.validation.constraints.NotEmpty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder(builderClassName = "Builder")
@OwnedBy(HarnessTeam.CODE)
public class SSHPublicKeyDTOInternal {
  String algorithm;
  @NotEmpty String sshKey;

  String comment;
  String fingerPrint;

  List<SSHKeyUsage> keyUsage;

  Long timestamp;

  public SSHPublicKey toSSHKey() {
    return SSHPublicKey.builder()
        .algorithm(algorithm)
        .sshKey(sshKey)
        .comment(comment)
        .fingerPrint(fingerPrint)
        .keyUsage(keyUsage)
        .build();
  }
}
