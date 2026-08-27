/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.api.utils;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.common.beans.SSHPublicKey;
import io.harness.ng.core.dto.SSHPublicKeyDTOInternal;
import io.harness.ng.core.dto.SSHValidateDTO;

import com.hierynomus.utils.Strings;
import com.sun.jdi.request.InvalidRequestStateException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

@OwnedBy(HarnessTeam.CODE)
public class SSHKeyUtils {
  private static final String HASH_ALGORITHM = "SHA-256";
  private static final String FINGERPRINT_PREFIX = "SHA256";
  private static final List<String> ALLOWED_TYPES =
      Arrays.asList("ssh-rsa", "ecdsa-sha2-nistp256", "ecdsa-sha2-nistp384", "ecdsa-sha2-nistp521", "ssh-ed25519",
          "sk-ecdsa-sha2-nistp256@openssh.com", "sk-ssh-ed25519@openssh.com");
  private static final String allowedTypesString = Strings.join(ALLOWED_TYPES, ',');

  public static String calculateFingerprint(SSHPublicKeyDTOInternal publicKey) {
    MessageDigest messageDigest;
    try {
      messageDigest = MessageDigest.getInstance(HASH_ALGORITHM);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("Could not get fingerprint", e);
    }
    byte[] hash = messageDigest.digest(Base64.getDecoder().decode(publicKey.getSshKey().getBytes()));
    String fingerprint = Base64.getEncoder().encodeToString(hash).replaceAll("=+$", "");
    return FINGERPRINT_PREFIX + ":" + fingerprint;
  }

  public static SSHPublicKeyDTOInternal validateAndExtractKey(SSHValidateDTO sshValidateDTO) {
    if (sshValidateDTO == null) {
      throw new InvalidRequestException("Null ssh validate dto encountered");
    }

    return getSshPublicKeyDTOInternal(extractKeyFromContent(sshValidateDTO));
  }

  public static SSHPublicKeyDTOInternal validateAndExtractKey(String keyContent) {
    if (keyContent.isEmpty()) {
      throw new InvalidRequestStateException("SSH keyContent can not be empty");
    }
    return getSshPublicKeyDTOInternal(extractKeyFromContent(keyContent));
  }

  @NotNull
  private static SSHPublicKeyDTOInternal getSshPublicKeyDTOInternal(SSHPublicKeyDTOInternal sshPublicKeyDTOInternal) {
    if (!ALLOWED_TYPES.contains(sshPublicKeyDTOInternal.getAlgorithm())) {
      throw new InvalidRequestStateException(String.format("Allowed key types are [%s]", allowedTypesString));
    }
    String fingerprint;
    try {
      fingerprint = SSHKeyUtils.calculateFingerprint(sshPublicKeyDTOInternal);
    } catch (IllegalArgumentException e) {
      throw new InvalidRequestStateException("Parsing error, Provided key may not be valid base64 encoded");
    }
    sshPublicKeyDTOInternal.setFingerPrint(fingerprint);

    return sshPublicKeyDTOInternal;
  }

  private static SSHPublicKeyDTOInternal extractKeyFromContent(SSHValidateDTO sshValidateDTO) {
    if (sshValidateDTO.getSshKeyObject() == null) {
      throw new InvalidRequestStateException("SSH key type is empty");
    }
    if (StringUtils.isEmpty(sshValidateDTO.getSshKeyObject().getKey())) {
      throw new InvalidRequestStateException("SSH key is empty");
    }
    if (StringUtils.isEmpty(sshValidateDTO.getSshKeyObject().getAlgorithm())) {
      throw new InvalidRequestStateException("SSH key algorithm is empty");
    }

    return SSHPublicKeyDTOInternal.builder()
        .sshKey(sshValidateDTO.getSshKeyObject().getKey())
        .algorithm(sshValidateDTO.getSshKeyObject().getAlgorithm())
        .build();
  }

  private static SSHPublicKeyDTOInternal extractKeyFromContent(String key) {
    String[] publicKeyFormat = key.split(" ");

    if (publicKeyFormat.length < 2) {
      throw new InvalidRequestStateException("SSH key is not of the valid format, missing parts");
    }

    SSHPublicKeyDTOInternal.Builder builder =
        SSHPublicKeyDTOInternal.builder().algorithm(publicKeyFormat[0]).sshKey(publicKeyFormat[1]);
    if (publicKeyFormat.length > 2) {
      builder.comment(String.join(" ", Arrays.copyOfRange(publicKeyFormat, 2, publicKeyFormat.length)));
    }
    return builder.build();
  }

  public static String convertToKeyContent(SSHPublicKey sshPublicKey) {
    String key = sshPublicKey.getAlgorithm();
    key += " " + sshPublicKey.getSshKey();
    if (StringUtils.isNotEmpty(sshPublicKey.getComment())) {
      key += " " + sshPublicKey.getComment();
    }
    return key;
  }
}
