/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.helper;

import static io.harness.rule.OwnerRule.KUSHAL_DASARI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.plan.service.PlanExecutionMetadataService;
import io.harness.engine.pms.data.expression.PmsEngineExpressionService;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.execution.dynamic.DynamicExecutionService;
import io.harness.execution.dynamic.dtos.DynamicExecutionInstanceRequestDTO;
import io.harness.execution.dynamic.dtos.DynamicExecutionInstanceResponseDTO;
import io.harness.gitaware.helper.GitAwareEntityHelper;
import io.harness.plancreator.stages.dynamic.v1.DynamicStageStepParametersV1;
import io.harness.plancreator.steps.common.v1.StageElementParametersV1;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ChildExecutableResponse;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.plan.RetryExecutionInfo;
import io.harness.pms.pipeline.service.enforcement.PMSPipelineTemplateHelper;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.rule.Owner;
import io.harness.utils.ScopeResolutionHelper;

import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class DynamicStageStepV1Test extends CategoryTest {
  @Mock private PlanCreationQueueRequestHelper planCreationQueueRequestHelper;
  @Mock private DynamicExecutionService dynamicExecutionService;
  @Mock private PlanExecutionMetadataService planExecutionMetadataService;
  @Mock private PMSPipelineTemplateHelper pmsPipelineTemplateHelper;
  @Mock private ScopeResolutionHelper scopeResolutionHelper;
  @Mock private PmsEngineExpressionService pmsEngineExpressionService;
  @Mock private GitAwareEntityHelper gitAwareEntityHelper;
  @Mock private io.harness.pms.pipeline.service.helper.PMSPipelineServiceHelper pmsPipelineServiceHelper;
  @InjectMocks private DynamicStageStepV1 dynamicStageStepV1;

  private static final String ACCOUNT_ID = "accountId";
  private static final String PLAN_EXECUTION_ID = "planExecutionId";
  private static final String CHILD_NODE_ID = "childNodeId123";
  private static final String STAGE_IDENTIFIER = "myDynamicStage";
  private static final String RUNTIME_ID = "runtimeId";
  private static final String ORIGINAL_PLAN_EXECUTION_ID = "originalPlanExecutionId";

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  private Ambiance buildAmbiance(ExecutionMode executionMode, String originalPlanExecutionId) {
    ExecutionMetadata.Builder metadataBuilder = ExecutionMetadata.newBuilder().setExecutionMode(executionMode);
    if (originalPlanExecutionId != null) {
      metadataBuilder.setOriginalPlanExecutionIdForRollbackMode(originalPlanExecutionId);
    }
    return Ambiance.newBuilder()
        .setPlanExecutionId(PLAN_EXECUTION_ID)
        .putSetupAbstractions("accountId", ACCOUNT_ID)
        .addLevels(Level.newBuilder().setIdentifier(STAGE_IDENTIFIER).setRuntimeId(RUNTIME_ID).build())
        .setMetadata(metadataBuilder.build())
        .build();
  }

  private StageElementParametersV1 buildStageParams(String childNodeId) {
    DynamicStageStepParametersV1 stepParams = DynamicStageStepParametersV1.builder().childNodeId(childNodeId).build();
    return StageElementParametersV1.builder().spec(stepParams).build();
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testObtainChild_WithChildNodeId_ReturnsEarlyWithoutFetchingYaml() {
    // Arrange: normal (non-rollback, non-retry) execution with childNodeId set
    Ambiance ambiance = buildAmbiance(ExecutionMode.NORMAL, null);
    StageElementParametersV1 stageParams = buildStageParams(CHILD_NODE_ID);

    doReturn(Optional.of(PlanExecutionMetadata.builder().build()))
        .when(planExecutionMetadataService)
        .findByPlanExecutionId(ACCOUNT_ID, PLAN_EXECUTION_ID);

    // Act
    ChildExecutableResponse response =
        dynamicStageStepV1.obtainChild(ambiance, stageParams, StepInputPackage.builder().build());

    // Assert: response contains the correct childNodeId
    assertThat(response).isNotNull();
    assertThat(response.getChildNodeId()).isEqualTo(CHILD_NODE_ID);

    // Verify no YAML fetching or plan creation happened (early return path)
    verify(planCreationQueueRequestHelper, never()).createAndAppendToExistingPlan(any(), any(), any());
    verify(gitAwareEntityHelper, never()).fetchYAMLFromRemote(any(), any(), any());
    verify(pmsPipelineTemplateHelper, never()).resolveTemplateRefsInPipeline(any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testCopyDynamicExecutionInstanceForRollback_InRollbackMode() {
    // Arrange: PIPELINE_ROLLBACK execution mode with childNodeId set
    Ambiance ambiance = buildAmbiance(ExecutionMode.PIPELINE_ROLLBACK, ORIGINAL_PLAN_EXECUTION_ID);
    StageElementParametersV1 stageParams = buildStageParams(CHILD_NODE_ID);

    // Mock: the original execution has a DynamicExecutionInstance
    DynamicExecutionInstanceResponseDTO originalInstance = DynamicExecutionInstanceResponseDTO.builder()
                                                               .nodeExecutionId("originalNodeExecId")
                                                               .planExecutionId(ORIGINAL_PLAN_EXECUTION_ID)
                                                               .yaml("original-yaml-content")
                                                               .processedYaml("original-processed-yaml")
                                                               .build();
    doReturn(Optional.of(originalInstance))
        .when(dynamicExecutionService)
        .getByPlanExecutionIdAndIdentifier(ORIGINAL_PLAN_EXECUTION_ID, STAGE_IDENTIFIER);

    // Act
    ChildExecutableResponse response =
        dynamicStageStepV1.obtainChild(ambiance, stageParams, StepInputPackage.builder().build());

    // Assert: response has the correct childNodeId
    assertThat(response).isNotNull();
    assertThat(response.getChildNodeId()).isEqualTo(CHILD_NODE_ID);

    // Verify: dynamicExecutionService.create was called with a new instance copied from the original
    ArgumentCaptor<DynamicExecutionInstanceRequestDTO> captor =
        ArgumentCaptor.forClass(DynamicExecutionInstanceRequestDTO.class);
    verify(dynamicExecutionService, times(1)).create(captor.capture());

    DynamicExecutionInstanceRequestDTO createdDTO = captor.getValue();
    assertThat(createdDTO.getNodeExecutionId()).isEqualTo(RUNTIME_ID);
    assertThat(createdDTO.getPlanExecutionId()).isEqualTo(PLAN_EXECUTION_ID);
    assertThat(createdDTO.getYaml()).isEqualTo("original-yaml-content");
    assertThat(createdDTO.getIdentifier()).isEqualTo(STAGE_IDENTIFIER);
    assertThat(createdDTO.getProcessedYaml()).isEqualTo("original-processed-yaml");
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testCopyDynamicExecutionInstance_NotInRollbackOrRetryMode() {
    // Arrange: NORMAL execution mode with childNodeId set, no retry info
    Ambiance ambiance = buildAmbiance(ExecutionMode.NORMAL, null);
    StageElementParametersV1 stageParams = buildStageParams(CHILD_NODE_ID);

    doReturn(Optional.of(PlanExecutionMetadata.builder().build()))
        .when(planExecutionMetadataService)
        .findByPlanExecutionId(ACCOUNT_ID, PLAN_EXECUTION_ID);

    // Act
    ChildExecutableResponse response =
        dynamicStageStepV1.obtainChild(ambiance, stageParams, StepInputPackage.builder().build());

    // Assert
    assertThat(response).isNotNull();
    assertThat(response.getChildNodeId()).isEqualTo(CHILD_NODE_ID);

    // Verify: no instance copy happened (no rollback, no retry)
    verify(dynamicExecutionService, never()).getByPlanExecutionIdAndIdentifier(any(), any());
    verify(dynamicExecutionService, never()).create(any(DynamicExecutionInstanceRequestDTO.class));
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testCopyDynamicExecutionInstance_InRetryMode() {
    // Arrange: NORMAL execution mode but with retry info pointing to original execution
    Ambiance ambiance = buildAmbiance(ExecutionMode.NORMAL, null);
    StageElementParametersV1 stageParams = buildStageParams(CHILD_NODE_ID);

    doReturn(Optional.of(PlanExecutionMetadata.builder()
                             .retryExecutionInfo(
                                 RetryExecutionInfo.newBuilder().setParentRetryId(ORIGINAL_PLAN_EXECUTION_ID).build())
                             .build()))
        .when(planExecutionMetadataService)
        .findByPlanExecutionId(ACCOUNT_ID, PLAN_EXECUTION_ID);

    DynamicExecutionInstanceResponseDTO originalInstance = DynamicExecutionInstanceResponseDTO.builder()
                                                               .nodeExecutionId("originalNodeExecId")
                                                               .planExecutionId(ORIGINAL_PLAN_EXECUTION_ID)
                                                               .yaml("original-yaml-content")
                                                               .processedYaml("original-processed-yaml")
                                                               .build();
    doReturn(Optional.of(originalInstance))
        .when(dynamicExecutionService)
        .getByPlanExecutionIdAndIdentifier(ORIGINAL_PLAN_EXECUTION_ID, STAGE_IDENTIFIER);

    // Act
    ChildExecutableResponse response =
        dynamicStageStepV1.obtainChild(ambiance, stageParams, StepInputPackage.builder().build());

    // Assert
    assertThat(response).isNotNull();
    assertThat(response.getChildNodeId()).isEqualTo(CHILD_NODE_ID);

    // Verify: instance was copied from original execution to retry execution
    ArgumentCaptor<DynamicExecutionInstanceRequestDTO> captor =
        ArgumentCaptor.forClass(DynamicExecutionInstanceRequestDTO.class);
    verify(dynamicExecutionService, times(1)).create(captor.capture());

    DynamicExecutionInstanceRequestDTO createdDTO = captor.getValue();
    assertThat(createdDTO.getPlanExecutionId()).isEqualTo(PLAN_EXECUTION_ID);
    assertThat(createdDTO.getYaml()).isEqualTo("original-yaml-content");
    assertThat(createdDTO.getIdentifier()).isEqualTo(STAGE_IDENTIFIER);
    assertThat(createdDTO.getProcessedYaml()).isEqualTo("original-processed-yaml");
  }
}
