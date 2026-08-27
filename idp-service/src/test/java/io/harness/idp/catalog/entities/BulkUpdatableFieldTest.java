/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.entities;

import static io.harness.rule.OwnerRule.VIGNESWARA;

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
public class BulkUpdatableFieldTest extends CategoryTest {
  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testFromKeyOwner() {
    BulkUpdatableField field = BulkUpdatableField.fromKey("owner");
    assertThat(field).isEqualTo(BulkUpdatableField.OWNER);
    assertThat(field.getKey()).isEqualTo("owner");
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testFromKeyUnknownThrowsIllegalArgumentException() {
    assertThatThrownBy(() -> BulkUpdatableField.fromKey("unknownField"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown field key: unknownField");
  }
}
