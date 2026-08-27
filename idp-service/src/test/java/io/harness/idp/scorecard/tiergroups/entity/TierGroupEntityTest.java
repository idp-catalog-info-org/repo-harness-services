/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.tiergroups.entity;

import static io.harness.rule.OwnerRule.NITESH_GAHLOT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class TierGroupEntityTest extends CategoryTest {
  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void uniqueIdentifierIndexAppliesToAllTierGroupsIncludingDeleted() {
    CompoundMongoIndex index = (CompoundMongoIndex) TierGroupEntity.mongoIndexes().get(0);

    assertThat(index.getName()).isEqualTo("unique_account_identifier");
    assertThat(index.isUnique()).isTrue();
    assertThat(index.getFields()).containsExactly("accountIdentifier", "identifier");
    assertThat(index.getPartialFilterExpression()).isNull();
  }
}
