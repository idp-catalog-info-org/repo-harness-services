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

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class SpecHasherTest extends CategoryTest {
  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void hashIsDeterministic() {
    String content = "openapi: 3.0.1\ninfo:\n  title: Test\n  version: 1.0.0\n";
    assertThat(SpecHasher.hash(content)).isEqualTo(SpecHasher.hash(content));
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void differentContentProducesDifferentHash() {
    assertThat(SpecHasher.hash("openapi: 3.0.1")).isNotEqualTo(SpecHasher.hash("openapi: 3.0.2"));
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void hashIsHex64() {
    String hash = SpecHasher.hash("openapi: 3.0.1");
    assertThat(hash).hasSize(64).matches("^[0-9a-f]+$");
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void nullInputThrows() {
    assertThatThrownBy(() -> SpecHasher.hash(null)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void emptyStringIsAcceptedAndDeterministic() {
    // Empty input is not the same as null; producing a stable hash for it is fine.
    assertThat(SpecHasher.hash("")).isEqualTo(SpecHasher.hash(""));
  }
}
