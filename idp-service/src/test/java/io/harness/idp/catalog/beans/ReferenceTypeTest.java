/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.beans;

import static io.harness.rule.OwnerRule.KOTA_KARTHIK;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class ReferenceTypeTest extends CategoryTest {
  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testReferenceTypeEnumValues() {
    assertThat(ReferenceType.values()).hasSize(2);
    assertThat(ReferenceType.values()).containsExactly(ReferenceType.INLINE, ReferenceType.GIT);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testReferenceTypeValueOf() {
    assertThat(ReferenceType.valueOf("INLINE")).isEqualTo(ReferenceType.INLINE);
    assertThat(ReferenceType.valueOf("GIT")).isEqualTo(ReferenceType.GIT);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testReferenceTypeName() {
    assertThat(ReferenceType.INLINE.name()).isEqualTo("INLINE");
    assertThat(ReferenceType.GIT.name()).isEqualTo("GIT");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testReferenceTypeOrdinal() {
    assertThat(ReferenceType.INLINE.ordinal()).isEqualTo(0);
    assertThat(ReferenceType.GIT.ordinal()).isEqualTo(1);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testReferenceTypeEquality() {
    ReferenceType ref1 = ReferenceType.INLINE;
    ReferenceType ref2 = ReferenceType.INLINE;
    ReferenceType ref3 = ReferenceType.GIT;

    assertThat(ref1).isEqualTo(ref2);
    assertThat(ref1).isNotEqualTo(ref3);
    assertThat(ref1 == ref2).isTrue();
    assertThat(ref1 == ref3).isFalse();
  }
}
