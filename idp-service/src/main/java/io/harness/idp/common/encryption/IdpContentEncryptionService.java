/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.common.encryption;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.google.crypto.tink.Aead;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.aead.KmsEnvelopeAead;
import com.google.crypto.tink.aead.PredefinedAeadParameters;
import com.google.crypto.tink.integration.gcpkms.GcpKmsClient;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class IdpContentEncryptionService {
  private final Aead aead;
  private final boolean enabled;

  @Inject
  public IdpContentEncryptionService(@Named("contentEncryption") IdpContentEncryptionConfig config)
      throws GeneralSecurityException {
    this.enabled = config.isEnabled();

    if (enabled) {
      log.info("Enabling IDP content encryption KMS key: {}", config.getKmsKeyUri());
      AeadConfig.register();
      Aead remoteAead = new GcpKmsClient().withDefaultCredentials().getAead(config.getKmsKeyUri());
      this.aead = KmsEnvelopeAead.create(PredefinedAeadParameters.AES256_GCM, remoteAead);
      log.info("IDP content encryption enabled with KMS key: {}", config.getKmsKeyUri());
    } else {
      this.aead = null;
      log.info("IDP content encryption is disabled");
    }
  }

  public byte[] encrypt(byte[] plaintext, String accountId) throws GeneralSecurityException {
    if (!enabled) {
      return plaintext;
    }
    return aead.encrypt(plaintext, accountId.getBytes(StandardCharsets.UTF_8));
  }

  public byte[] decrypt(byte[] ciphertext, String accountId) throws GeneralSecurityException {
    if (!enabled) {
      return ciphertext;
    }
    try {
      return aead.decrypt(ciphertext, accountId.getBytes(StandardCharsets.UTF_8));
    } catch (GeneralSecurityException e) {
      log.warn("Decryption failed, returning raw content (likely pre-encryption file): {}", e.getMessage());
      return ciphertext;
    }
  }
}
