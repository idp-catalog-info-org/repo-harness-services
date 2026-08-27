/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.common.beans.PublicKeyScheme;
import io.harness.ng.core.dto.PublicKeyDTO;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class TokenServiceTest extends CategoryTest {
  @Test
  @Owner(developers = OwnerRule.ATEFEH)
  @Category(UnitTests.class)
  public void testListByFingerprintReturnsResults() {
    TokenService tokenService = mock(TokenService.class);
    List<PublicKeyDTO> expectedKeys = new ArrayList<>();

    when(tokenService.listByFingerprint("account", "fingerprint", null, null, List.of(PublicKeyScheme.PGP)))
        .thenReturn(expectedKeys);

    List<PublicKeyDTO> result =
        tokenService.listByFingerprint("account", "fingerprint", null, null, List.of(PublicKeyScheme.PGP));

    assertThat(result).isEqualTo(expectedKeys);
  }

  @Test
  @Owner(developers = OwnerRule.ATEFEH)
  @Category(UnitTests.class)
  public void testListBySubKeyIdReturnsResults() {
    TokenService tokenService = mock(TokenService.class);
    List<PublicKeyDTO> expectedKeys = new ArrayList<>();

    when(tokenService.listBySubKeyId("account", "keyId", null, null, List.of(PublicKeyScheme.PGP)))
        .thenReturn(expectedKeys);

    List<PublicKeyDTO> result =
        tokenService.listBySubKeyId("account", "keyId", null, null, List.of(PublicKeyScheme.PGP));

    assertThat(result).isEqualTo(expectedKeys);
  }
}
