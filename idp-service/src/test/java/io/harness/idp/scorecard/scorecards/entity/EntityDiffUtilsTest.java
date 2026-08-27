/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scorecards.entity;

import static io.harness.rule.OwnerRule.HARJAS;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.ScorecardDetails;
import io.harness.spec.server.idp.v1.model.ScorecardFilter;

import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class EntityDiffUtilsTest extends CategoryTest {
  private ScorecardEntity createScorecard(String name, String description, boolean published) {
    ScorecardEntity scorecard = new ScorecardEntity();
    scorecard.setName(name);
    scorecard.setDescription(description);
    scorecard.setWeightageStrategy(ScorecardDetails.WeightageStrategyEnum.EQUAL_WEIGHTS);
    scorecard.setPublished(published);
    scorecard.setFilter(createFilter());
    scorecard.setChecks(List.of(createCheck("check1", 10, false)));
    return scorecard;
  }

  private ScorecardEntity.Check createCheck(String id, int weight, boolean isCustom) {
    return new ScorecardEntity.Check(id, weight, isCustom);
  }

  private ScorecardFilter createFilter() {
    ScorecardFilter filter = new ScorecardFilter();
    filter.setKind("kind");
    filter.setType("type");
    filter.setLifecycle(List.of("lifecycle1"));
    filter.setOwners(List.of("owner1"));
    filter.setTags(List.of("tag1"));
    filter.setScopes(List.of("scope1"));
    return filter;
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testIsScorecardUpdated_noChange() {
    ScorecardEntity oldSc = createScorecard("score1", "desc", true);
    ScorecardEntity newSc = createScorecard("score1", "desc", true);
    assertFalse(EntityDiffUtils.isScorecardUpdated(oldSc, newSc));
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testIsScorecardUpdated_nameChanged() {
    ScorecardEntity oldSc = createScorecard("score1", "desc", true);
    ScorecardEntity newSc = createScorecard("score2", "desc", true);
    assertTrue(EntityDiffUtils.isScorecardUpdated(oldSc, newSc));
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testIsScorecardUpdated_descriptionChanged() {
    ScorecardEntity oldSc = createScorecard("score1", "desc", true);
    ScorecardEntity newSc = createScorecard("score1", "new desc", true);
    assertTrue(EntityDiffUtils.isScorecardUpdated(oldSc, newSc));
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testIsScorecardUpdated_weightageStrategyChanged() {
    ScorecardEntity oldSc = createScorecard("score1", "desc", true);
    ScorecardEntity newSc = createScorecard("score1", "desc", true);
    newSc.setWeightageStrategy(ScorecardDetails.WeightageStrategyEnum.CUSTOM);
    assertTrue(EntityDiffUtils.isScorecardUpdated(oldSc, newSc));
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testIsScorecardUpdated_publishedChanged() {
    ScorecardEntity oldSc = createScorecard("score1", "desc", false);
    ScorecardEntity newSc = createScorecard("score1", "desc", true);
    assertTrue(EntityDiffUtils.isScorecardUpdated(oldSc, newSc));
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testIsScorecardUpdated_filterChanged() {
    ScorecardEntity oldSc = createScorecard("score1", "desc", true);
    ScorecardEntity newSc = createScorecard("score1", "desc", true);
    ScorecardFilter newFilter = createFilter();
    newFilter.setKind("differentKind");
    newSc.setFilter(newFilter);
    assertTrue(EntityDiffUtils.isScorecardUpdated(oldSc, newSc));
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testIsScorecardUpdated_checkAdded() {
    ScorecardEntity oldSc = createScorecard("score1", "desc", true);
    ScorecardEntity newSc = createScorecard("score1", "desc", true);
    newSc.setChecks(List.of(createCheck("check1", 10, false), createCheck("check2", 20, true)));
    assertTrue(EntityDiffUtils.isScorecardUpdated(oldSc, newSc));
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testIsScorecardUpdated_checkRemoved() {
    ScorecardEntity oldSc = createScorecard("score1", "desc", true);
    oldSc.setChecks(List.of(createCheck("check1", 10, false), createCheck("check2", 20, true)));
    ScorecardEntity newSc = createScorecard("score1", "desc", true);
    newSc.setChecks(List.of(createCheck("check1", 10, false)));
    assertTrue(EntityDiffUtils.isScorecardUpdated(oldSc, newSc));
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testIsScorecardUpdated_checkModified() {
    ScorecardEntity oldSc = createScorecard("score1", "desc", true);
    ScorecardEntity newSc = createScorecard("score1", "desc", true);
    newSc.setChecks(List.of(createCheck("check1", 15, false))); // weight changed
    assertTrue(EntityDiffUtils.isScorecardUpdated(oldSc, newSc));
  }
}
