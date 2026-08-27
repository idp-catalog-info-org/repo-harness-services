/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.dto;

import javax.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class SSHValidateDTO {
  // old way of verifying where ssh key and type are both in single string - example ssh-rsa aklsdlkas
  String sshKey;
  @NotEmpty String accountIdentifier;
  SSHKey sshKeyObject;
  Long verified;

  @Data
  public class SSHKey {
    String key;
    String algorithm;
  }
}
