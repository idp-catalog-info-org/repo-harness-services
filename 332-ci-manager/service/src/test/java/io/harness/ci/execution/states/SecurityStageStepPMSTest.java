/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.states;

import static io.harness.beans.sweepingoutputs.CISweepingOutputNames.STAGE_EXECUTION;
import static io.harness.delegate.beans.ci.pod.CICommonConstants.MEMORY_METRICS_LOG_KEY_SUFFIX;
import static io.harness.rule.OwnerRule.CHIRAG_S;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.execution.PublishedImageArtifact;
import io.harness.beans.stages.parameters.IntegrationStageStepParametersPMS;
import io.harness.beans.steps.outcome.CIStepArtifactOutcome;
import io.harness.beans.steps.outcome.IntegrationStageOutcome;
import io.harness.beans.steps.outcome.StepArtifacts;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.category.element.UnitTests;
import io.harness.ci.executionplan.base.CIExecutionTestBase;
import io.harness.plancreator.steps.common.StageElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ChildExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.TriggerType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepOutcome;
import io.harness.pms.sdk.core.steps.io.StepResponseNotifyData;
import io.harness.repositories.StepExecutionParametersRepository;
import io.harness.rule.Owner;
import io.harness.tasks.ResponseData;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CI)
public class SecurityStageStepPMSTest extends CIExecutionTestBase {
  @Mock private ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Mock private OutcomeService outcomeService;
  @Mock private StepExecutionParametersRepository stepExecutionParametersRepository;
  @InjectMocks private SecurityStageStepPMS securityStageStepPMS;

  private Ambiance ambiance;
  private StageElementParameters stageElementParameters;
  private StepInputPackage inputPackage;

  @Before
  public void setUp() {
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put("accountId", "accountId");
    setupAbstractions.put("projectIdentifier", "projectId");
    setupAbstractions.put("orgIdentifier", "orgId");

    Level stageLevel =
        Level.newBuilder()
            .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).setType("STAGE").build())
            .setRuntimeId("stageRuntimeId")
            .setSetupId("stageSetupId")
            .build();

    Level stepLevel =
        Level.newBuilder()
            .setStepType(
                StepType.newBuilder().setStepCategory(StepCategory.STEP).setType("SecurityStageStepPMS").build())
            .setRuntimeId("runtimeId")
            .setSetupId("setupId")
            .setStartTs(System.currentTimeMillis() - 1000)
            .build();

    ambiance = Ambiance.newBuilder()
                   .putAllSetupAbstractions(setupAbstractions)
                   .addLevels(stageLevel)
                   .addLevels(stepLevel)
                   .setMetadata(
                       ExecutionMetadata.newBuilder()
                           .setTriggerInfo(ExecutionTriggerInfo.newBuilder().setTriggerType(TriggerType.MANUAL).build())
                           .build())
                   .setPlanExecutionId("planExeId")
                   .build();

    Infrastructure infrastructure = new K8sDirectInfraYaml();
    IntegrationStageStepParametersPMS specConfig =
        IntegrationStageStepParametersPMS.builder().childNodeID("childNode123").infrastructure(infrastructure).build();

    stageElementParameters =
        StageElementParameters.builder().identifier("stageId").name("stageName").specConfig(specConfig).build();
    inputPackage = StepInputPackage.builder().build();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testStepType() {
    StepType stepType = SecurityStageStepPMS.STEP_TYPE;
    assertThat(stepType.getType()).isEqualTo("SecurityStageStepPMS");
    assertThat(stepType.getStepCategory()).isEqualTo(StepCategory.STAGE);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetStepParametersClass() {
    assertThat(securityStageStepPMS.getStepParametersClass()).isEqualTo(StageElementParameters.class);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testObtainChildReturnsChildNodeId() {
    when(stepExecutionParametersRepository.save(any())).thenReturn(null);

    ChildExecutableResponse response = securityStageStepPMS.obtainChild(ambiance, stageElementParameters, inputPackage);

    assertThat(response).isNotNull();
    assertThat(response.getChildNodeId()).isEqualTo("childNode123");
    assertThat(response.getUnitsList()).contains(MEMORY_METRICS_LOG_KEY_SUFFIX);
    assertThat(response.getLogKeysList()).hasSize(1);
    assertThat(response.getLogKeys(0)).endsWith("/" + MEMORY_METRICS_LOG_KEY_SUFFIX);
    verify(stepExecutionParametersRepository).save(any());
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleChildResponseWithSuccessNoArtifacts() {
    IntegrationStageStepParametersPMS specConfig =
        IntegrationStageStepParametersPMS.builder().childNodeID("childNode123").build();
    StageElementParameters params =
        StageElementParameters.builder().identifier("stageId").name("stageName").specConfig(specConfig).build();

    Map<String, ResponseData> responseDataMap =
        ImmutableMap.<String, ResponseData>builder()
            .put("id", StepResponseNotifyData.builder().status(Status.SUCCEEDED).build())
            .build();

    when(executionSweepingOutputResolver.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(STAGE_EXECUTION)))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());

    StepResponse stepResponse = securityStageStepPMS.handleChildResponse(ambiance, params, responseDataMap);

    assertThat(stepResponse).isNotNull();
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleChildResponseWithFailure() {
    IntegrationStageStepParametersPMS specConfig =
        IntegrationStageStepParametersPMS.builder().childNodeID("childNode123").build();
    StageElementParameters params =
        StageElementParameters.builder().identifier("stageId").name("stageName").specConfig(specConfig).build();

    Map<String, ResponseData> responseDataMap =
        ImmutableMap.<String, ResponseData>builder()
            .put("id", StepResponseNotifyData.builder().status(Status.FAILED).build())
            .build();

    when(executionSweepingOutputResolver.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(STAGE_EXECUTION)))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());

    StepResponse stepResponse = securityStageStepPMS.handleChildResponse(ambiance, params, responseDataMap);

    assertThat(stepResponse).isNotNull();
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleChildResponseWithArtifacts() {
    IntegrationStageStepParametersPMS specConfig = IntegrationStageStepParametersPMS.builder()
                                                       .childNodeID("childNode123")
                                                       .stepIdentifiers(Arrays.asList("step1", "step2"))
                                                       .build();
    StageElementParameters params =
        StageElementParameters.builder().identifier("stageId").name("stageName").specConfig(specConfig).build();

    Map<String, ResponseData> responseDataMap =
        ImmutableMap.<String, ResponseData>builder()
            .put("id", StepResponseNotifyData.builder().status(Status.SUCCEEDED).build())
            .build();

    when(executionSweepingOutputResolver.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(STAGE_EXECUTION)))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());

    when(outcomeService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject("artifact-step1")))
        .thenReturn(OptionalOutcome.builder()
                        .found(true)
                        .outcome(CIStepArtifactOutcome.builder()
                                     .stepArtifacts(StepArtifacts.builder()
                                                        .publishedImageArtifacts(
                                                            Collections.singletonList(PublishedImageArtifact.builder()
                                                                                          .imageName("image1")
                                                                                          .tag("tag1")
                                                                                          .digest("digest1")
                                                                                          .build()))
                                                        .build())
                                     .build())
                        .build());

    when(outcomeService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject("artifact-step2")))
        .thenReturn(OptionalOutcome.builder().found(false).build());

    StepResponse stepResponse = securityStageStepPMS.handleChildResponse(ambiance, params, responseDataMap);

    assertThat(stepResponse).isNotNull();
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getStepOutcomes()).isNotNull();
    assertThat(stepResponse.getStepOutcomes()).hasSize(1);

    StepOutcome stepOutcome = new ArrayList<>(stepResponse.getStepOutcomes()).get(0);
    IntegrationStageOutcome integrationStageOutcome = (IntegrationStageOutcome) stepOutcome.getOutcome();
    assertThat(integrationStageOutcome.getImageArtifacts()).hasSize(1);
    assertThat(integrationStageOutcome.getImageArtifacts())
        .contains(PublishedImageArtifact.builder().imageName("image1").tag("tag1").digest("digest1").build());
  }
}
