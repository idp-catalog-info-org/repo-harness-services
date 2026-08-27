/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.common.encryption;

import static io.harness.rule.OwnerRule.DEVESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.google.crypto.tink.Aead;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class IdpContentEncryptionServiceTest extends CategoryTest {
  private static final String ACCOUNT_ID = "test-account-id";
  private static final byte[] PLAINTEXT = "# My Skill\n\nThis skill does...".getBytes(StandardCharsets.UTF_8);
  private static final byte[] CIPHERTEXT = "encrypted-blob-bytes".getBytes(StandardCharsets.UTF_8);

  @Mock private Aead aead;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testEncryptWhenDisabled() throws Exception {
    IdpContentEncryptionConfig config = IdpContentEncryptionConfig.builder().enabled(false).kmsKeyUri("").build();
    IdpContentEncryptionService service = new IdpContentEncryptionService(config);

    byte[] result = service.encrypt(PLAINTEXT, ACCOUNT_ID);

    assertThat(result).isEqualTo(PLAINTEXT);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testDecryptWhenDisabled() throws Exception {
    IdpContentEncryptionConfig config = IdpContentEncryptionConfig.builder().enabled(false).kmsKeyUri("").build();
    IdpContentEncryptionService service = new IdpContentEncryptionService(config);

    byte[] result = service.decrypt(CIPHERTEXT, ACCOUNT_ID);

    assertThat(result).isEqualTo(CIPHERTEXT);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testDecryptWhenDisabledReturnsRawBytes() throws Exception {
    IdpContentEncryptionConfig config = IdpContentEncryptionConfig.builder().enabled(false).kmsKeyUri("").build();
    IdpContentEncryptionService service = new IdpContentEncryptionService(config);

    // Even with random bytes that aren't valid ciphertext, disabled mode returns as-is
    byte[] randomBytes = new byte[] {0x01, 0x02, 0x03, 0x04};
    byte[] result = service.decrypt(randomBytes, ACCOUNT_ID);

    assertThat(result).isEqualTo(randomBytes);
  }
}
