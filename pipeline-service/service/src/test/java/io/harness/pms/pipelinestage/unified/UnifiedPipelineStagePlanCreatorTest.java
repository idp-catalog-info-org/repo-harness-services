/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipelinestage.unified;

import static io.harness.rule.OwnerRule.SOUMYO_PURKAYASTHA;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.IdentifierRef;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.plan.PlanCreationContextValue;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.enforcement.PipelineEnforcementService;
import io.harness.pms.pipeline.service.intfc.PMSPipelineService;
import io.harness.pms.pipelinestage.PipelineStageStepParameters;
import io.harness.pms.pipelinestage.v1.helper.PipelineStageHelperV1;
import io.harness.pms.plan.execution.helper.PipelineStageHelper;
import io.harness.pms.plan.execution.helper.PipelineStageStep;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.ScopeResolutionHelper;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class UnifiedPipelineStagePlanCreatorTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock PipelineStageHelper pipelineStageHelper;
  @Mock PMSPipelineService pmsPipelineService;
  @Mock KryoSerializer kryoSerializer;
  @Mock PmsFeatureFlagService pmsFeatureFlagService;
  @Mock PipelineEnforcementService pipelineEnforcementService;
  @Mock ScopeResolutionHelper scopeResolutionHelper;
  @Mock PipelineStageHelperV1 pipelineStageHelperV1;

  @InjectMocks UnifiedPipelineStagePlanCreator unifiedPipelineStagePlanCreator;

  private static final String ACC = "acc";
  private static final String ORG = "org";
  private static final String PROJ = "project";
  private static final String PIPELINE = "childPipeline";
  private ScopeInfo scopeInfo;

  @Before
  public void setup() {
    scopeInfo = ScopeInfo.builder().uniqueId("unique-id").build();
    when(scopeResolutionHelper.getScopeInfo(any(), any(), any())).thenReturn(scopeInfo);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testCreatePlanForField() throws IOException {
    // Setup YAML with new unified format
    String yamlField = "stage:\n"
        + "  id: parent_pipeline\n"
        + "  name: parent pipeline\n"
        + "  type: pipeline\n" // add just for testing
        + "  __uuid: uuid\n"
        + "  chain:\n"
        + "    uses: " + ORG + "/" + PROJ + "/" + PIPELINE + "\n"
        + "    with:\n"
        + "      input-sets: []\n"
        + "  on-failure:\n"
        + "    - action: success\n"
        + "      errors:\n"
        + "        - all\n";

    YamlField pipelineStageYamlField = YamlUtils.injectUuidInYamlField(yamlField);

    // Create context
    PlanCreationContextValue value = PlanCreationContextValue.newBuilder().setAccountIdentifier(ACC).build();
    PlanCreationContext ctx = PlanCreationContext.builder()
                                  .globalContext(Collections.singletonMap("metadata", value))
                                  .currentField(pipelineStageYamlField.getNode().getField("stage"))
                                  .build();

    // Setup mocks
    IdentifierRef identifierRef =
        IdentifierRef.builder().identifier(PIPELINE).orgIdentifier(ORG).projectIdentifier(PROJ).build();
    when(pipelineStageHelperV1.getIdentifierRef(anyString(), anyString())).thenReturn(identifierRef);

    doReturn(Optional.of(PipelineEntity.builder().yaml(yamlField).harnessVersion(HarnessYamlVersion.V1).build()))
        .when(pmsPipelineService)
        .getPipeline(ACC, ORG, PROJ, PIPELINE, false, false, false, true, scopeInfo, true);

    doNothing()
        .when(pipelineStageHelper)
        .validateNestedChainedPipeline(any(PipelineEntity.class), anyString(), anyString(), any(ScopeInfo.class));
    doNothing().when(pipelineStageHelperV1).validateFailureStrategy(any());
    doNothing().when(pipelineEnforcementService).validatePipelineChainingEnforcement(anyString());

    when(pipelineStageHelperV1.getChainedPipelineInputField(any())).thenReturn(null);
    when(kryoSerializer.asBytes(any())).thenReturn(new byte[0]);

    // Read stage node
    UnifiedPipelineStageNode stageNode = YamlUtils.read(
        pipelineStageYamlField.getNode().getField("stage").getNode().toString(), UnifiedPipelineStageNode.class);

    // Execute
    PlanCreationResponse response = unifiedPipelineStagePlanCreator.createPlanForField(ctx, stageNode);

    // Verify
    assertThat(response).isNotNull();
    assertThat(response.getPlanNode()).isNotNull();

    PlanNode planNode = response.getPlanNode();
    assertThat(planNode.getName()).isEqualTo(stageNode.getName());
    assertThat(planNode.getIdentifier()).isEqualTo(stageNode.getId());
    assertThat(planNode.getGroup()).isEqualTo(StepCategory.STAGE.name());
    assertThat(planNode.getStepType()).isEqualTo(PipelineStageStep.STEP_TYPE);
    assertThat(planNode.getFacilitatorObtainments().get(0).getType().getType())
        .isEqualTo(OrchestrationFacilitatorType.ASYNC);

    assertThat(planNode.getStepParameters()).isInstanceOf(PipelineStageStepParameters.class);
    PipelineStageStepParameters stepParameters = (PipelineStageStepParameters) planNode.getStepParameters();
    assertThat(stepParameters.getName()).isEqualTo("parent pipeline");
    assertThat(stepParameters.getIdentifier()).isEqualTo("parent_pipeline");

    verify(pipelineEnforcementService, times(1)).validatePipelineChainingEnforcement(ACC);
    verify(pipelineStageHelper, times(1))
        .validateNestedChainedPipeline(any(PipelineEntity.class), eq("parent pipeline"), eq(""), eq(scopeInfo));
    verify(pipelineStageHelperV1, times(1)).validateFailureStrategy(any());
  }

  @Test
  @Owner(developers = SOUMYO_PURKAYASTHA)
  @Category(UnitTests.class)
  public void testGetStageStepParametersPopulatesStageMetadata() throws IOException {
    String yamlField = "stage:\n"
        + "  id: chained_stage_1\n"
        + "  name: chained_stage\n"
        + "  desc: stage description\n"
        + "  type: pipeline\n"
        + "  __uuid: uuid\n"
        + "  chain:\n"
        + "    uses: " + ORG + "/" + PROJ + "/" + PIPELINE + "\n"
        + "    ref: main\n"
        + "    with:\n"
        + "      input-sets: []\n";

    YamlField stageYamlField = YamlUtils.injectUuidInYamlField(yamlField).getNode().getField("stage");
    UnifiedPipelineStageNode stageNode =
        YamlUtils.read(stageYamlField.getNode().toString(), UnifiedPipelineStageNode.class);

    IdentifierRef identifierRef =
        IdentifierRef.builder().identifier(PIPELINE).orgIdentifier(ORG).projectIdentifier(PROJ).build();
    when(pipelineStageHelperV1.getIdentifierRef(anyString(), anyString())).thenReturn(identifierRef);

    PipelineStageStepParameters stepParameters = unifiedPipelineStagePlanCreator.getStageStepParameters(
        stageNode.getUnifiedPipelineStageInfo(), null, "stageNodeId", HarnessYamlVersion.V1, stageNode);

    assertThat(stepParameters.getIdentifier()).isEqualTo("chained_stage_1");
    assertThat(stepParameters.getName()).isEqualTo("chained_stage");
    assertThat(stepParameters.getDescription()).isEqualTo("stage description");
    assertThat(stepParameters.getPipeline()).isEqualTo(PIPELINE);
    assertThat(stepParameters.getOrg()).isEqualTo(ORG);
    assertThat(stepParameters.getProject()).isEqualTo(PROJ);
    assertThat(stepParameters.getStageNodeId()).isEqualTo("stageNodeId");
    assertThat(stepParameters.getGitBranch()).isEqualTo("main");
    assertThat(stepParameters.getTags()).isNull();
  }
}
