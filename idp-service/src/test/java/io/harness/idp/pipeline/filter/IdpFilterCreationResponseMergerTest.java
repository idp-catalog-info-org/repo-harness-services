/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.pipeline.filter;

import static io.harness.rule.OwnerRule.KOTA_KARTHIK;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.filter.creation.FilterCreationResponse;
import io.harness.rule.Owner;

import java.util.HashSet;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class IdpFilterCreationResponseMergerTest extends CategoryTest {
  private IdpFilterCreationResponseMerger merger;

  @Before
  public void setUp() {
    merger = new IdpFilterCreationResponseMerger();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testMergeFilterCreationResponse_NullCurrent() {
    FilterCreationResponse finalResponse = FilterCreationResponse.builder().build();

    merger.mergeFilterCreationResponse(finalResponse, null);

    assertNull(finalResponse.getPipelineFilter());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testMergeFilterCreationResponse_NullCurrentPipelineFilter() {
    FilterCreationResponse finalResponse = FilterCreationResponse.builder().build();
    FilterCreationResponse current = FilterCreationResponse.builder().build();

    merger.mergeFilterCreationResponse(finalResponse, current);

    assertNull(finalResponse.getPipelineFilter());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testMergeFilterCreationResponse_NullFinalPipelineFilter() {
    FilterCreationResponse finalResponse = FilterCreationResponse.builder().build();

    Set<String> repoNames = new HashSet<>();
    repoNames.add("repo1");
    IDPFilter currentFilter = IDPFilter.builder().repoNames(repoNames).build();
    FilterCreationResponse current = FilterCreationResponse.builder().pipelineFilter(currentFilter).build();

    merger.mergeFilterCreationResponse(finalResponse, current);

    assertNotNull(finalResponse.getPipelineFilter());
    IDPFilter finalFilter = (IDPFilter) finalResponse.getPipelineFilter();
    assertEquals(1, finalFilter.getRepoNames().size());
    assertTrue(finalFilter.getRepoNames().contains("repo1"));
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testMergeFilterCreationResponse_BothFiltersPresent() {
    Set<String> finalRepoNames = new HashSet<>();
    finalRepoNames.add("repo1");
    IDPFilter finalFilter = IDPFilter.builder().repoNames(finalRepoNames).build();
    FilterCreationResponse finalResponse = FilterCreationResponse.builder().pipelineFilter(finalFilter).build();

    Set<String> currentRepoNames = new HashSet<>();
    currentRepoNames.add("repo2");
    currentRepoNames.add("repo3");
    IDPFilter currentFilter = IDPFilter.builder().repoNames(currentRepoNames).build();
    FilterCreationResponse current = FilterCreationResponse.builder().pipelineFilter(currentFilter).build();

    merger.mergeFilterCreationResponse(finalResponse, current);

    assertNotNull(finalResponse.getPipelineFilter());
    IDPFilter mergedFilter = (IDPFilter) finalResponse.getPipelineFilter();
    assertEquals(3, mergedFilter.getRepoNames().size());
    assertTrue(mergedFilter.getRepoNames().contains("repo1"));
    assertTrue(mergedFilter.getRepoNames().contains("repo2"));
    assertTrue(mergedFilter.getRepoNames().contains("repo3"));
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testMergeFilterCreationResponse_EmptyCurrentRepoNames() {
    Set<String> finalRepoNames = new HashSet<>();
    finalRepoNames.add("repo1");
    IDPFilter finalFilter = IDPFilter.builder().repoNames(finalRepoNames).build();
    FilterCreationResponse finalResponse = FilterCreationResponse.builder().pipelineFilter(finalFilter).build();

    IDPFilter currentFilter = IDPFilter.builder().build();
    FilterCreationResponse current = FilterCreationResponse.builder().pipelineFilter(currentFilter).build();

    merger.mergeFilterCreationResponse(finalResponse, current);

    assertNotNull(finalResponse.getPipelineFilter());
    IDPFilter mergedFilter = (IDPFilter) finalResponse.getPipelineFilter();
    assertEquals(1, mergedFilter.getRepoNames().size());
    assertTrue(mergedFilter.getRepoNames().contains("repo1"));
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testMergeFilterCreationResponse_DuplicateRepoNames() {
    Set<String> finalRepoNames = new HashSet<>();
    finalRepoNames.add("repo1");
    IDPFilter finalFilter = IDPFilter.builder().repoNames(finalRepoNames).build();
    FilterCreationResponse finalResponse = FilterCreationResponse.builder().pipelineFilter(finalFilter).build();

    Set<String> currentRepoNames = new HashSet<>();
    currentRepoNames.add("repo1");
    currentRepoNames.add("repo2");
    IDPFilter currentFilter = IDPFilter.builder().repoNames(currentRepoNames).build();
    FilterCreationResponse current = FilterCreationResponse.builder().pipelineFilter(currentFilter).build();

    merger.mergeFilterCreationResponse(finalResponse, current);

    assertNotNull(finalResponse.getPipelineFilter());
    IDPFilter mergedFilter = (IDPFilter) finalResponse.getPipelineFilter();
    assertEquals(2, mergedFilter.getRepoNames().size());
    assertTrue(mergedFilter.getRepoNames().contains("repo1"));
    assertTrue(mergedFilter.getRepoNames().contains("repo2"));
  }
}
