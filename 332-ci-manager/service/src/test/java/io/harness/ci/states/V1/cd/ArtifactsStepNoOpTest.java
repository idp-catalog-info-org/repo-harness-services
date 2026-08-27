/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.states.V1.cd;

import static io.harness.rule.OwnerRule.SIDDHARTHA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.harness.category.element.UnitTests;
import io.harness.cd.beans.ArtifactMetadata;
import io.harness.cd.beans.ArtifactsSweepingOutput;
import io.harness.ci.execution.common.MapBasedReferenceExtractor;
import io.harness.ci.execution.states.helpers.ServiceStepSweepingOutputHelper;
import io.harness.data.structure.UUIDGenerator;
import io.harness.ng.core.entitydetail.EntityDetailProtoToRestMapper;
import io.harness.plancreator.stages.v1.EmptyStepParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.rule.Owner;
import io.harness.utils.CDStepsExpressionResolver;
import io.harness.utils.TemplateYamlGenerator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ArtifactsStepNoOpTest {
  private static final String ACCOUNT_ID = "test-account";
  private static final String ORG_ID = "test-org";
  private static final String PROJECT_ID = "test-project";
  private static final String PIPELINE_ID = "test-pipeline";
  private static final String STAGE_EXECUTION_ID = "test-stage-id";
  private static final String PLAN_EXECUTION_ID = "plan-execution-id";

  @Mock private ServiceStepSweepingOutputHelper serviceStepSweepingOutputHelper;
  @Mock private TemplateYamlGenerator templateYamlGenerator;
  @Mock private MapBasedReferenceExtractor mapBasedReferenceExtractor;
  @Mock private EntityDetailProtoToRestMapper entityDetailProtoToRestMapper;
  @Mock private PipelineRbacHelper pipelineRbacHelper;
  @Mock private CDStepsExpressionResolver cdStepsExpressionResolver;

  @InjectMocks private ArtifactsStep artifactsStep;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
  }

  private Ambiance buildAmbiance() {
    List<Level> levels = new ArrayList<>();
    levels.add(
        Level.newBuilder()
            .setRuntimeId(UUIDGenerator.generateUuid())
            .setSetupId("setup-id")
            .setIdentifier("artifactsStep")
            .setStepType(
                StepType.newBuilder().setType("UNIFIED_ARTIFACTS_STEP").setStepCategory(StepCategory.STEP).build())
            .build());
    return Ambiance.newBuilder()
        .putAllSetupAbstractions(Map.of("accountId", ACCOUNT_ID, "orgIdentifier", ORG_ID, "projectIdentifier",
            PROJECT_ID, "pipelineIdentifier", PIPELINE_ID))
        .addAllLevels(levels)
        .setPlanExecutionId(PLAN_EXECUTION_ID)
        .setStageExecutionId(STAGE_EXECUTION_ID)
        .setMetadata(ExecutionMetadata.newBuilder().setPipelineIdentifier(PIPELINE_ID).build())
        .build();
  }

  private String buildNoOpArtifactYaml() {
    return "id: har-artifact\n"
        + "uses: har\n"
        + "action: \"no-op\"\n"
        + "sidecar: false\n";
  }

  @Test
  @Owner(developers = SIDDHARTHA)
  @Category(UnitTests.class)
  public void testExecuteAsyncAfterRbac_allNoOpArtifacts_returnsEmptyCallbackIds() {
    Ambiance ambiance = buildAmbiance();

    ArtifactMetadata harMetadata =
        ArtifactMetadata.builder().yaml(buildNoOpArtifactYaml()).logKey("har-artifact-log").templatized(false).build();

    Map<String, ArtifactMetadata> metadataMap = new HashMap<>();
    metadataMap.put("har-artifact", harMetadata);

    ArtifactsSweepingOutput sweepingOutput =
        ArtifactsSweepingOutput.builder().artifactsMetadataMap(metadataMap).primaryArtifactId("har-artifact").build();

    doReturn(OptionalSweepingOutput.builder().found(true).output(sweepingOutput).build())
        .when(serviceStepSweepingOutputHelper)
        .fetchServiceArtifactsSweepingOutput(any(Ambiance.class));

    AsyncExecutableResponse response =
        artifactsStep.executeAsyncAfterRbac(ambiance, new EmptyStepParameters(), StepInputPackage.builder().build());

    assertThat(response.getCallbackIdsList()).isEmpty();
    assertThat(response.getLogKeysList()).isEmpty();
    assertThat(response.getUnitsList()).isEmpty();

    verify(templateYamlGenerator, never()).generateYamlWithMergedDefaults(any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = SIDDHARTHA)
  @Category(UnitTests.class)
  public void testExecuteAsyncAfterRbac_noArtifacts_returnsEmptyCallbackIds() {
    Ambiance ambiance = buildAmbiance();

    doReturn(OptionalSweepingOutput.builder().found(false).build())
        .when(serviceStepSweepingOutputHelper)
        .fetchServiceArtifactsSweepingOutput(any(Ambiance.class));

    AsyncExecutableResponse response =
        artifactsStep.executeAsyncAfterRbac(ambiance, new EmptyStepParameters(), StepInputPackage.builder().build());

    assertThat(response.getCallbackIdsList()).isEmpty();
    verify(templateYamlGenerator, never()).generateYamlWithMergedDefaults(any(), any(), any(), any(), any(), any());
  }
}
