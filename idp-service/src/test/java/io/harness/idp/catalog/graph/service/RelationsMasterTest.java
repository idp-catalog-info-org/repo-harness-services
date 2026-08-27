/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.graph.service;

import static io.harness.rule.OwnerRule.ANKUR;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class RelationsMasterTest extends CategoryTest {
  private RelationsMaster relationsMaster;

  @Before
  public void setUp() {
    relationsMaster = new RelationsMaster();
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testGetReverseRelationForAllKnownPairs() {
    assertThat(relationsMaster.getReverseRelation("ownedBy")).isEqualTo("ownerOf");
    assertThat(relationsMaster.getReverseRelation("ownerOf")).isEqualTo("ownedBy");
    assertThat(relationsMaster.getReverseRelation("consumesApi")).isEqualTo("apiConsumedBy");
    assertThat(relationsMaster.getReverseRelation("apiConsumedBy")).isEqualTo("consumesApi");
    assertThat(relationsMaster.getReverseRelation("providesApi")).isEqualTo("apiProvidedBy");
    assertThat(relationsMaster.getReverseRelation("apiProvidedBy")).isEqualTo("providesApi");
    assertThat(relationsMaster.getReverseRelation("dependsOn")).isEqualTo("dependencyOf");
    assertThat(relationsMaster.getReverseRelation("dependencyOf")).isEqualTo("dependsOn");
    assertThat(relationsMaster.getReverseRelation("parentOf")).isEqualTo("childOf");
    assertThat(relationsMaster.getReverseRelation("childOf")).isEqualTo("parentOf");
    assertThat(relationsMaster.getReverseRelation("memberOf")).isEqualTo("hasMember");
    assertThat(relationsMaster.getReverseRelation("hasMember")).isEqualTo("memberOf");
    assertThat(relationsMaster.getReverseRelation("partOf")).isEqualTo("hasPart");
    assertThat(relationsMaster.getReverseRelation("hasPart")).isEqualTo("partOf");
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testGetReverseRelationIsCaseInsensitive() {
    assertThat(relationsMaster.getReverseRelation("OwnedBy")).isEqualTo("ownerOf");
    assertThat(relationsMaster.getReverseRelation("DEPENDSON")).isEqualTo("dependencyOf");
    assertThat(relationsMaster.getReverseRelation("HasPart")).isEqualTo("partOf");
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testGetReverseRelationReturnsNullForUnknown() {
    assertThat(relationsMaster.getReverseRelation("customRelation")).isNull();
    assertThat(relationsMaster.getReverseRelation("unknownType")).isNull();
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testGetReverseRelationReturnsNullForNull() {
    assertThat(relationsMaster.getReverseRelation(null)).isNull();
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testGetReverseRelationReturnsNullForEmpty() {
    assertThat(relationsMaster.getReverseRelation("")).isNull();
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testBidirectionalConsistency() {
    String[] relations = {"ownedBy", "consumesApi", "providesApi", "dependsOn", "parentOf", "memberOf", "partOf"};
    for (String relation : relations) {
      String reverse = relationsMaster.getReverseRelation(relation);
      assertThat(reverse).isNotNull();
      assertThat(relationsMaster.getReverseRelation(reverse)).isEqualTo(relation);
    }
  }
}
