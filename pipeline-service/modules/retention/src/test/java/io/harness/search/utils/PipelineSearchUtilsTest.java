/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.search.utils;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.RISHABH;
import static io.harness.rule.OwnerRule.RISHIKESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.rule.Owner;
import io.harness.search.entity.beans.PipelineSearchExecutionSummaryDTO.PipelineSearchExecutionSummaryDTOKeys;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class PipelineSearchUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetSearchSortFieldMapping() {
    assertThat(
        PipelineSearchUtils.getSearchSortFieldMapping(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.name))
        .isEqualTo(PipelineSearchExecutionSummaryDTOKeys.name);
    assertThat(
        PipelineSearchUtils.getSearchSortFieldMapping(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.status))
        .isEqualTo(PipelineSearchExecutionSummaryDTOKeys.status);
    assertThat(
        PipelineSearchUtils.getSearchSortFieldMapping(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.startTs))
        .isEqualTo(PipelineSearchExecutionSummaryDTOKeys.startTs);
    assertThatThrownBy(()
                           -> PipelineSearchUtils.getSearchSortFieldMapping(
                               PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.labels))
        .hasMessage("The provided field is not supported for sorting in Elastic: labels");
    assertThatThrownBy(() -> PipelineSearchUtils.getSearchSortFieldMapping(null))
        .hasMessage("The provided field for sorting results in Elastic cannot be null");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetModuleInfoFieldPath() {
    assertThat(PipelineSearchUtils.getModuleInfoFieldPath("moduleInfo.cd.serviceIdentifiers"))
        .isEqualTo("cdModuleInfo.serviceIdentifiers");
    assertThat(PipelineSearchUtils.getModuleInfoFieldPath("moduleInfo.cd.envIdentifiers"))
        .isEqualTo("cdModuleInfo.envIdentifiers");
    assertThat(PipelineSearchUtils.getModuleInfoFieldPath("moduleInfo.cd.serviceDefinitionTypes"))
        .isEqualTo("cdModuleInfo.serviceDefinitionTypes");
    assertThat(PipelineSearchUtils.getModuleInfoFieldPath("moduleInfo.cd.artifactDisplayNames"))
        .isEqualTo("cdModuleInfo.artifactDisplayNames");
    assertThat(PipelineSearchUtils.getModuleInfoFieldPath("moduleInfo.cd.helmChartVersions"))
        .isEqualTo("cdModuleInfo.helmChartVersions");
    assertThat(PipelineSearchUtils.getModuleInfoFieldPath("moduleInfo.ci.ciExecutionInfoDTO.event"))
        .isEqualTo("ciModuleInfo.ciExecutionInfoDTO.event");
    assertThat(PipelineSearchUtils.getModuleInfoFieldPath("moduleInfo.ci.ciExecutionInfoDTO.pullRequest.sourceBranch"))
        .isEqualTo("ciModuleInfo.ciExecutionInfoDTO.pullRequest.sourceBranch");
    assertThat(PipelineSearchUtils.getModuleInfoFieldPath("moduleInfo.ci.ciExecutionInfoDTO.pullRequest.targetBranch"))
        .isEqualTo("ciModuleInfo.ciExecutionInfoDTO.pullRequest.targetBranch");
    assertThat(PipelineSearchUtils.getModuleInfoFieldPath("moduleInfo.ci.branch")).isEqualTo("ciModuleInfo.branch");
    assertThat(PipelineSearchUtils.getModuleInfoFieldPath("moduleInfo.ci.tag")).isEqualTo("ciModuleInfo.tag");
    assertThat(PipelineSearchUtils.getModuleInfoFieldPath("moduleInfo.ci.buildType"))
        .isEqualTo("ciModuleInfo.buildType");
    assertThat(PipelineSearchUtils.getModuleInfoFieldPath("moduleInfo.iacm.ciExecutionInfoDTO"))
        .isEqualTo("moduleInfo.iacm.ciExecutionInfoDTO");
    assertThat(PipelineSearchUtils.getModuleInfoFieldPath("xyzPath")).isEqualTo("xyzPath");
    assertThatThrownBy(() -> PipelineSearchUtils.getModuleInfoFieldPath(null))
        .hasMessage("The provided path to get Module Info field in Elastic cannot be null");
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testGetSortOptions() {
    LinkedHashMap<String, SortOrder> sortingFields = null;
    assertThat(PipelineSearchUtils.getSortOptions(sortingFields)).isEqualTo(null);

    sortingFields = new LinkedHashMap<>();
    sortingFields.put(PipelineSearchExecutionSummaryDTOKeys.createdAt, SortOrder.Desc);
    sortingFields.put(PipelineSearchExecutionSummaryDTOKeys.planExecutionId, SortOrder.Asc);
    List<SortOptions> expectedSortOptions =
        List.of(new SortOptions.Builder()
                    .field(f -> f.field(PipelineSearchExecutionSummaryDTOKeys.createdAt).order(SortOrder.Desc))
                    .build(),
            new SortOptions.Builder()
                .field(f -> f.field(PipelineSearchExecutionSummaryDTOKeys.planExecutionId).order(SortOrder.Asc))
                .build());
    List<SortOptions> actualSortOptions = PipelineSearchUtils.getSortOptions(sortingFields);
    assertThat(expectedSortOptions.size()).isEqualTo(actualSortOptions.size());

    // Compare each element of the lists
    for (int i = 0; i < expectedSortOptions.size(); i++) {
      SortOptions expectedSortOption = expectedSortOptions.get(i);
      SortOptions actualSortOption = actualSortOptions.get(i);
      assertThat(expectedSortOption.toString()).isEqualTo(actualSortOption.toString());
    }
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testCheckFieldExistsInElastic() {
    String field = PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.planExecutionId;
    assertThat(PipelineSearchUtils.checkFieldExistsInElastic(field)).isTrue();
    String nonExistingField = PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.layoutNodeMap;
    assertThat(PipelineSearchUtils.checkFieldExistsInElastic(nonExistingField)).isFalse();
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testGetSummaryEntitiesOrderedByExecutionIds() {
    // Both empty executionIds and pipelineSummaryExecutionEntities as empty
    assertThat(
        PipelineSearchUtils.getSummaryEntitiesOrderedByExecutionIds(Collections.emptyList(), Collections.emptyList()))
        .isEmpty();

    // One of executionIds or pipelineSummaryExecutionEntities is empty
    List<String> planExecutionIds = List.of("first", "second", "third", "fourth");
    List<PipelineExecutionSummaryEntity> pipelineSummaryExecutionEntities = new ArrayList<>();
    assertThatThrownBy(()
                           -> PipelineSearchUtils.getSummaryEntitiesOrderedByExecutionIds(
                               planExecutionIds, pipelineSummaryExecutionEntities))
        .hasMessage("[ELASTIC_SEARCH] Provided planExecutionIds or pipelineExecutionSummaryEntities can not be empty");

    // executionIds or pipelineSummaryExecutionEntities with different size
    pipelineSummaryExecutionEntities.add(PipelineExecutionSummaryEntity.builder().planExecutionId("third").build());
    pipelineSummaryExecutionEntities.add(PipelineExecutionSummaryEntity.builder().planExecutionId("second").build());
    pipelineSummaryExecutionEntities.add(PipelineExecutionSummaryEntity.builder().planExecutionId("fourth").build());
    assertThatThrownBy(()
                           -> PipelineSearchUtils.getSummaryEntitiesOrderedByExecutionIds(
                               planExecutionIds, pipelineSummaryExecutionEntities))
        .hasMessage(
            "[ELASTIC_SEARCH] Provided planExecutionIds and pipelineExecutionSummaryEntities can not have different size");

    pipelineSummaryExecutionEntities.add(PipelineExecutionSummaryEntity.builder().planExecutionId("first").build());

    assertThat(
        PipelineSearchUtils.getSummaryEntitiesOrderedByExecutionIds(planExecutionIds, pipelineSummaryExecutionEntities)
            .stream()
            .map(PipelineExecutionSummaryEntity::getPlanExecutionId)
            .collect(Collectors.toList()))
        .isEqualTo(planExecutionIds);
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testGetFieldValueList() {
    // Empty list
    assertThat(PipelineSearchUtils.getFieldValueList(Collections.emptyList())).isEmpty();

    List<Object> objects = List.of(123L, "Test", true, 123.0);
    List<FieldValue> expectedValues =
        List.of(FieldValue.of(123L), FieldValue.of("Test"), FieldValue.of(true), FieldValue.of(123.0));
    assertThat(PipelineSearchUtils.getFieldValueList(objects))
        .usingRecursiveFieldByFieldElementComparator()
        .isEqualTo(expectedValues);

    // exception class
    assertThatThrownBy(() -> PipelineSearchUtils.getFieldValueList(Collections.singletonList(null)))
        .hasMessage("[ELASTIC_SEARCH]: provided object null has a type which is not supported");
  }
}
