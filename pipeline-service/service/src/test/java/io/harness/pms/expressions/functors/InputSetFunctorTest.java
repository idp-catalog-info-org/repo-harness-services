/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import static io.harness.rule.OwnerRule.SAKSHI;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.plan.service.PlanExecutionMetadataService;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.metrics.service.api.MetricService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntityType;
import io.harness.pms.ngpipeline.inputset.beans.resource.BulkInputSetsRequestDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.BulkInputSetsResponseDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetSummaryResponseDTOPMS;
import io.harness.pms.ngpipeline.inputset.service.PMSInputSetService;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.service.intfc.PMSExecutionService;
import io.harness.rule.Owner;
import io.harness.utils.ScopeResolutionHelper;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class InputSetFunctorTest extends CategoryTest {
  @Mock private PlanExecutionMetadataService planExecutionMetadataService;
  @Mock private PMSExecutionService pmsExecutionService;
  @Mock private PMSInputSetService pmsInputSetService;
  @Mock private ScopeResolutionHelper scopeResolutionHelper;
  @Mock private MetricService metricService;

  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  private static final String PLAN_EXECUTION_ID = "planExecutionId";
  private static final String PIPELINE_IDENTIFIER = "myPipeline";

  private static final Ambiance AMBIANCE = Ambiance.newBuilder()
                                               .setPlanExecutionId(PLAN_EXECUTION_ID)
                                               .putSetupAbstractions("accountId", ACCOUNT_ID)
                                               .putSetupAbstractions("orgIdentifier", ORG_ID)
                                               .putSetupAbstractions("projectIdentifier", PROJECT_ID)
                                               .build();

  private static final String INPUT_SET_YAML = "pipeline:\n"
      + "  identifier: trialselective\n"
      + "  stages:\n"
      + "    - stage:\n"
      + "        identifier: Test1\n"
      + "        type: Custom\n";

  private InputSetFunctor inputSetFunctor;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    inputSetFunctor = new InputSetFunctor(planExecutionMetadataService, pmsExecutionService, pmsInputSetService,
        scopeResolutionHelper, AMBIANCE, metricService);
  }

  @Test
  @Owner(developers = SAKSHI)
  @Category(UnitTests.class)
  public void testBind_returnsInputSetYamlValues() {
    stubMetadata(INPUT_SET_YAML);
    stubExecutionSummary(Collections.emptyList());

    Map<String, Object> result = (Map<String, Object>) inputSetFunctor.bind();

    Map<String, Object> pipeline = (Map<String, Object>) result.get("pipeline");
    assertEquals("trialselective", pipeline.get("identifier"));
  }

  @Test
  @Owner(developers = SAKSHI)
  @Category(UnitTests.class)
  public void testBind_noInputSets_detailsEmpty() {
    stubMetadata(INPUT_SET_YAML);
    stubExecutionSummary(Collections.emptyList());

    Map<String, Object> result = (Map<String, Object>) inputSetFunctor.bind();

    List<Map<String, Object>> details = (List<Map<String, Object>>) result.get("details");
    assertThat(details).isEmpty();
    verify(pmsInputSetService, never()).getBulkInputSets(any(), any(), any());
  }

  @Test
  @Owner(developers = SAKSHI)
  @Category(UnitTests.class)
  public void testBind_withInputSets_detailsPopulated() {
    stubMetadata(INPUT_SET_YAML);
    stubExecutionSummary(Arrays.asList("is1", "is2"));

    ScopeInfo scopeInfo = ScopeInfo.builder().accountIdentifier(ACCOUNT_ID).uniqueId("test-unique-id").build();
    doReturn(scopeInfo).when(scopeResolutionHelper).getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID);

    InputSetSummaryResponseDTOPMS is1 = InputSetSummaryResponseDTOPMS.builder()
                                            .identifier("is1")
                                            .name("Input Set One")
                                            .description("desc1")
                                            .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                            .inputSetType(InputSetEntityType.INPUT_SET)
                                            .build();
    InputSetSummaryResponseDTOPMS is2 = InputSetSummaryResponseDTOPMS.builder()
                                            .identifier("is2")
                                            .name("Input Set Two")
                                            .description("desc2")
                                            .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                            .inputSetType(InputSetEntityType.INPUT_SET)
                                            .build();

    doReturn(BulkInputSetsResponseDTO.builder().inputSets(Arrays.asList(is1, is2)).build())
        .when(pmsInputSetService)
        .getBulkInputSets(eq(scopeInfo), eq(PIPELINE_IDENTIFIER), any(BulkInputSetsRequestDTO.class));

    Map<String, Object> result = (Map<String, Object>) inputSetFunctor.bind();
    List<Map<String, Object>> details = (List<Map<String, Object>>) result.get("details");

    assertThat(details).hasSize(2);

    Map<String, Object> detail1 = details.get(0);
    assertEquals("is1", detail1.get("identifier"));
    assertEquals("Input Set One", detail1.get("name"));
    assertEquals("desc1", detail1.get("description"));
    assertEquals(ORG_ID, detail1.get("orgIdentifier"));
    assertEquals(PROJECT_ID, detail1.get("projectIdentifier"));
    assertEquals(PIPELINE_IDENTIFIER, detail1.get("pipelineIdentifier"));
    assertEquals("INPUT_SET", detail1.get("inputSetType"));

    Map<String, Object> detail2 = details.get(1);
    assertEquals("is2", detail2.get("identifier"));
    assertEquals("Input Set Two", detail2.get("name"));
  }

  @Test
  @Owner(developers = SAKSHI)
  @Category(UnitTests.class)
  public void testBind_inputSetDeletedAfterExecution_identifierPreservedNameNull() {
    stubMetadata(INPUT_SET_YAML);
    stubExecutionSummary(Arrays.asList("deletedIs"));

    ScopeInfo scopeInfo = ScopeInfo.builder().accountIdentifier(ACCOUNT_ID).uniqueId("test-unique-id").build();
    doReturn(scopeInfo).when(scopeResolutionHelper).getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID);

    doReturn(BulkInputSetsResponseDTO.builder().inputSets(Collections.emptyList()).build())
        .when(pmsInputSetService)
        .getBulkInputSets(any(), any(), any());

    Map<String, Object> result = (Map<String, Object>) inputSetFunctor.bind();
    List<Map<String, Object>> details = (List<Map<String, Object>>) result.get("details");

    assertThat(details).hasSize(1);
    assertEquals("deletedIs", details.get(0).get("identifier"));
    assertNull(details.get(0).get("name"));
  }

  @Test
  @Owner(developers = SAKSHI)
  @Category(UnitTests.class)
  public void testBind_bulkResponseNull_identifierPreserved() {
    stubMetadata(INPUT_SET_YAML);
    stubExecutionSummary(Arrays.asList("is1"));

    ScopeInfo scopeInfo = ScopeInfo.builder().accountIdentifier(ACCOUNT_ID).uniqueId("test-unique-id").build();
    doReturn(scopeInfo).when(scopeResolutionHelper).getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID);
    doReturn(null).when(pmsInputSetService).getBulkInputSets(any(), any(), any());

    Map<String, Object> result = (Map<String, Object>) inputSetFunctor.bind();
    List<Map<String, Object>> details = (List<Map<String, Object>>) result.get("details");

    assertThat(details).hasSize(1);
    assertEquals("is1", details.get(0).get("identifier"));
    assertNull(details.get(0).get("name"));
  }

  private void stubMetadata(String yaml) {
    doReturn(PlanExecutionMetadata.builder().inputSetYaml(yaml).build())
        .when(planExecutionMetadataService)
        .getWithFieldsIncludedFromSecondary(eq(ACCOUNT_ID), eq(PLAN_EXECUTION_ID), any());
  }

  private void stubExecutionSummary(List<String> inputSetIds) {
    doReturn(PipelineExecutionSummaryEntity.builder()
                 .pipelineIdentifier(PIPELINE_IDENTIFIER)
                 .inputSetIdentifiers(inputSetIds)
                 .build())
        .when(pmsExecutionService)
        .fetchExecutionSummaryFromDb(eq(PLAN_EXECUTION_ID), any());
  }
}
