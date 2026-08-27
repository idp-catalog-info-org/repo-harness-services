/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.creation;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.plancreator.PipelineServiceFilter;
import io.harness.pms.filter.creation.FilterCreationResponse;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import java.util.HashSet;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class PipelineServiceFilterCreationResponseMergerTest extends CategoryTest {
  private PipelineServiceFilterCreationResponseMerger merger;

  @Before
  public void setUp() {
    merger = new PipelineServiceFilterCreationResponseMerger();
  }

  @Test
  @Owner(developers = OwnerRule.SHUBHAM_CHAUDHARY)
  @Category(UnitTests.class)
  public void mergeFilterCreationResponse_whenCurrentFilterIsNull_doesNotModifyFinalResponse() {
    FilterCreationResponse finalResponse = FilterCreationResponse.builder().build();
    FilterCreationResponse current = FilterCreationResponse.builder().pipelineFilter(null).build();

    merger.mergeFilterCreationResponse(finalResponse, current);

    assertThat(finalResponse.getPipelineFilter()).isNull();
  }

  @Test
  @Owner(developers = OwnerRule.SHUBHAM_CHAUDHARY)
  @Category(UnitTests.class)
  public void mergeFilterCreationResponse_whenCurrentFilterIsNull_preservesExistingFinalFilter() {
    Set<String> existingStageTypes = new HashSet<>();
    existingStageTypes.add("Custom");
    existingStageTypes.add("DRTest");
    PipelineServiceFilter existingFilter =
        PipelineServiceFilter.builder().stageTypes(existingStageTypes).featureFlagStepCount(2).build();
    FilterCreationResponse finalResponse = FilterCreationResponse.builder().pipelineFilter(existingFilter).build();
    FilterCreationResponse current = FilterCreationResponse.builder().pipelineFilter(null).build();

    merger.mergeFilterCreationResponse(finalResponse, current);

    PipelineServiceFilter result = (PipelineServiceFilter) finalResponse.getPipelineFilter();
    assertThat(result).isNotNull();
    assertThat(result.getStageTypes()).containsExactlyInAnyOrder("Custom", "DRTest");
    assertThat(result.getFeatureFlagStepCount()).isEqualTo(2);
  }

  @Test
  @Owner(developers = OwnerRule.SHUBHAM_CHAUDHARY)
  @Category(UnitTests.class)
  public void mergeFilterCreationResponse_whenFinalFilterIsNull_initializesAndMerges() {
    Set<String> currentStageTypes = new HashSet<>();
    currentStageTypes.add("Custom");
    PipelineServiceFilter currentFilter =
        PipelineServiceFilter.builder().stageTypes(currentStageTypes).featureFlagStepCount(2).build();
    FilterCreationResponse finalResponse = FilterCreationResponse.builder().build();
    FilterCreationResponse current = FilterCreationResponse.builder().pipelineFilter(currentFilter).build();

    merger.mergeFilterCreationResponse(finalResponse, current);

    PipelineServiceFilter result = (PipelineServiceFilter) finalResponse.getPipelineFilter();
    assertThat(result).isNotNull();
    assertThat(result.getFeatureFlagStepCount()).isEqualTo(2);
    assertThat(result.getStageTypes()).containsExactly("Custom");
  }

  @Test
  @Owner(developers = OwnerRule.SHUBHAM_CHAUDHARY)
  @Category(UnitTests.class)
  public void mergeFilterCreationResponse_whenBothFiltersNonNull_accumulatesCountsAndUnionsStageTypes() {
    Set<String> existingStageTypes = new HashSet<>();
    existingStageTypes.add("Custom");
    PipelineServiceFilter existingFilter =
        PipelineServiceFilter.builder().stageTypes(existingStageTypes).featureFlagStepCount(1).build();
    FilterCreationResponse finalResponse = FilterCreationResponse.builder().pipelineFilter(existingFilter).build();

    Set<String> currentStageTypes = new HashSet<>();
    currentStageTypes.add("DRTest");
    PipelineServiceFilter currentFilter =
        PipelineServiceFilter.builder().stageTypes(currentStageTypes).featureFlagStepCount(3).build();
    FilterCreationResponse current = FilterCreationResponse.builder().pipelineFilter(currentFilter).build();

    merger.mergeFilterCreationResponse(finalResponse, current);

    PipelineServiceFilter result = (PipelineServiceFilter) finalResponse.getPipelineFilter();
    assertThat(result).isNotNull();
    assertThat(result.getFeatureFlagStepCount()).isEqualTo(4);
    assertThat(result.getStageTypes()).containsExactlyInAnyOrder("Custom", "DRTest");
  }
}
