/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.states.V1.cd;

import static io.harness.beans.steps.CIStepInfoType.UNIFIED_SERVICE_STEP;
import static io.harness.beans.steps.constants.ServiceStepConstants.OVERRIDES_COMMAND_UNIT;
import static io.harness.beans.steps.constants.ServiceStepConstants.SERVICE_STEP_COMMAND_UNIT;
import static io.harness.rule.OwnerRule.CHIRAG_S;
import static io.harness.rule.OwnerRule.FERNANDOD;
import static io.harness.rule.OwnerRule.SHOBHIT_SINGH;
import static io.harness.steps.StepUtils.PIE_SIMPLIFY_LOG_BASE_KEY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.cd.beans.outcomes.ManifestMetadata;
import io.harness.cd.beans.outcomes.ManifestsSweepingOutput;
import io.harness.cd.beans.outcomes.UnifiedServiceOutcome;
import io.harness.ci.execution.common.ProcessedServiceResult;
import io.harness.ci.execution.common.ServiceEntityMetadata;
import io.harness.ci.execution.common.ServiceEntityProcessor;
import io.harness.ci.execution.common.ServiceStepOutcomeHelper;
import io.harness.ci.execution.states.helpers.ServiceStepSweepingOutputHelper;
import io.harness.ci.states.V1.cd.ManifestsStep;
import io.harness.ci.states.V1.cd.ServiceHookTaskHelper;
import io.harness.ci.states.V1.cd.UnifiedServiceStep;
import io.harness.ci.states.V1.cd.UnifiedServiceStepParameters;
import io.harness.ci.states.V1.cd.helpers.UnifiedServiceStepOpaHelper;
import io.harness.data.structure.UUIDGenerator;
import io.harness.engine.governance.PolicyEvaluationFailureException;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.UnitProgress;
import io.harness.logging.UnitStatus;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponseNotifyData;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.tasks.ResponseData;
import io.harness.transientData.service.TransientExecutionDataService;
import io.harness.unified.cd.service.spec.ServiceConfig;
import io.harness.unified.cd.service.spec.ServiceInfoConfig;
import io.harness.unified.cd.service.spec.ServiceType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.powermock.reflect.Whitebox;

public class UnifiedServiceStepTest {
  @InjectMocks private UnifiedServiceStep serviceStep;
  @Mock private UnifiedServiceStepOpaHelper unifiedServiceStepOpaHelper;
  @Mock private ServiceStepSweepingOutputHelper serviceStepSweepingOutputHelper;
  @Mock private ServiceStepOutcomeHelper serviceStepOutcomeHelper;
  @Mock private ExecutionSweepingOutputService sweepingOutputService;
  @Mock private TransientExecutionDataService transientExecutionDataService;
  @Mock private ServiceEntityProcessor serviceEntityProcessor;
  @Mock private ServiceHookTaskHelper serviceHookTaskHelper;
  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Mock private ILogStreamingStepClient logStreamingClient;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
    when(serviceHookTaskHelper.isServiceHooksEnabled(any(String.class))).thenReturn(false);
    when(serviceHookTaskHelper.isServiceHooksEnabled(any(Ambiance.class))).thenReturn(false);
    when(logStreamingStepClientFactory.getLogStreamingStepClient(any())).thenReturn(logStreamingClient);
  }

  private Ambiance buildAmbiance(boolean isPIESimplifyLogBaseEnabled) {
    List<Level> levels = new ArrayList<>();
    levels.add(Level.newBuilder()
                   .setRuntimeId(UUIDGenerator.generateUuid())
                   .setSetupId("test-id")
                   .setIdentifier("unifiedServiceStep")
                   .setStepType(StepType.newBuilder()
                                    .setType(UNIFIED_SERVICE_STEP.getDisplayName())
                                    .setStepCategory(StepCategory.STEP)
                                    .build())
                   .setRetryIndex(0)
                   .build());
    return Ambiance.newBuilder()
        .putAllSetupAbstractions(Map.of("accountId", "test-account", "orgIdentifier", "test-org", "projectIdentifier",
            "test-project", "pipelineIdentifier", "test-pipeline"))
        .addAllLevels(levels)
        .setPlanExecutionId("plan-execution-id")
        .setStageExecutionId("test-stage-id")
        .setMetadata(ExecutionMetadata.newBuilder()
                         .setPipelineIdentifier("test-pipeline")
                         .putFeatureFlagToValueMap(PIE_SIMPLIFY_LOG_BASE_KEY, isPIESimplifyLogBaseEnabled)
                         .build())
        .build();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetStepParametersClass() {
    assertThat(serviceStep.getStepParametersClass()).isEqualTo(UnifiedServiceStepParameters.class);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidateResources_DoesNotThrow() {
    Ambiance ambiance = buildAmbiance(true);
    UnifiedServiceStepParameters stepParameters = UnifiedServiceStepParameters.builder().build();
    serviceStep.validateResources(ambiance, stepParameters);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetLogKeyWithPIESimplifyLogBaseEnabled() throws Exception {
    Ambiance ambiance = buildAmbiance(true);
    String logBaseKey = LogStreamingStepClientFactory.getLogBaseKey(ambiance);
    String manifestLogKey =
        Whitebox.invokeMethod(serviceStep, "appendChildStepLevel", logBaseKey, "manifests", ambiance);
    String expectedManifestLogKey =
        "test-account/pipeline/test-pipeline/0/-plan-execution-id/unifiedServiceStep/manifests";
    assertThat(manifestLogKey).isEqualTo(expectedManifestLogKey);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetLogKeyWithPIESimplifyLogBaseDisabled() throws Exception {
    Ambiance ambiance = buildAmbiance(false);
    String logBaseKey = LogStreamingStepClientFactory.getLogBaseKey(ambiance);
    String manifestLogKey =
        Whitebox.invokeMethod(serviceStep, "appendChildStepLevel", logBaseKey, "manifests", ambiance);
    String expectedManifestLogKey =
        "accountId:test-account/orgId:test-org/projectId:test-project/pipelineId:test-pipeline/runSequence:0/"
        + "level0:unifiedServiceStep/level1:manifests";
    assertThat(manifestLogKey).isEqualTo(expectedManifestLogKey);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testOpaEvaluationSuccess() {
    Ambiance ambiance = buildAmbiance(true);
    UnifiedServiceOutcome serviceOutcome =
        UnifiedServiceOutcome.builder().identifier("svc1").name("Test Service").type("Kubernetes").build();
    List<StepResponse.StepOutcome> stepOutcomes =
        Collections.singletonList(StepResponse.StepOutcome.builder().name("service").outcome(serviceOutcome).build());
    StepResponse stepResponse = StepResponse.builder().status(Status.SUCCEEDED).stepOutcomes(stepOutcomes).build();

    doNothing()
        .when(unifiedServiceStepOpaHelper)
        .checkAndCallOpaForServiceRuntimeContext(eq(ambiance), eq(stepOutcomes), any(StepResponse.class));

    serviceStep.callOpaForServiceRuntimeContext(ambiance, stepOutcomes, stepResponse);

    verify(unifiedServiceStepOpaHelper, times(1))
        .checkAndCallOpaForServiceRuntimeContext(eq(ambiance), eq(stepOutcomes), any(StepResponse.class));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testOpaEvaluationPolicyViolation() {
    Ambiance ambiance = buildAmbiance(true);
    List<StepResponse.StepOutcome> stepOutcomes = Collections.emptyList();
    StepResponse stepResponse = StepResponse.builder().status(Status.SUCCEEDED).stepOutcomes(stepOutcomes).build();

    doThrow(new PolicyEvaluationFailureException("Policy violation"))
        .when(unifiedServiceStepOpaHelper)
        .checkAndCallOpaForServiceRuntimeContext(eq(ambiance), eq(stepOutcomes), any(StepResponse.class));

    assertThatThrownBy(() -> serviceStep.callOpaForServiceRuntimeContext(ambiance, stepOutcomes, stepResponse))
        .isInstanceOf(PolicyEvaluationFailureException.class);
  }

  private void setupCommonMocksForHandleChildrenResponse() {
    when(transientExecutionDataService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(sweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(serviceStepSweepingOutputHelper.fetchServiceMetadataOutput(any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(serviceStepSweepingOutputHelper.fetchManifestUnitStatusesSweepingOutput(any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(serviceStepSweepingOutputHelper.fetchArtifactUnitStatusesSweepingOutput(any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(serviceStepSweepingOutputHelper.fetchConfigFileUnitStatusesSweepingOutput(any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(serviceStepSweepingOutputHelper.fetchServiceArtifactsSweepingOutput(any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(serviceStepSweepingOutputHelper.fetchConfigFilesSweepingOutput(any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(serviceStepSweepingOutputHelper.fetchServiceConfigMetadataOutput(any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    doNothing().when(unifiedServiceStepOpaHelper).checkAndCallOpaForServiceRuntimeContext(any(), any(), any());
  }

  private OptionalSweepingOutput buildManifestSweepingOutput(String manifestId, String manifestType) {
    LinkedHashMap<String, ManifestMetadata> manifestMetadataMap = new LinkedHashMap<>();
    manifestMetadataMap.put(manifestId,
        ManifestMetadata.builder()
            .logKey("logKey-" + manifestId)
            .manifestYaml("id: \"" + manifestId + "\"\nuses: \"" + manifestType + "\"\n")
            .build());
    return OptionalSweepingOutput.builder()
        .found(true)
        .output(ManifestsSweepingOutput.builder().manifestMetadataMap(manifestMetadataMap).build())
        .build();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void testHandleChildrenResponse_withInvalidManifestId_validationUnitIsFirst() {
    setupCommonMocksForHandleChildrenResponse();
    when(serviceStepSweepingOutputHelper.fetchServiceManifestsSweepingOutput(any()))
        .thenReturn(buildManifestSweepingOutput("1invalid", "k8s"));

    Ambiance ambiance = buildAmbiance(true);
    StepResponse response = serviceStep.handleChildrenResponse(
        ambiance, UnifiedServiceStepParameters.builder().build(), Collections.emptyMap());

    List<UnitProgress> unitProgressList = response.getUnitProgressList();
    assertThat(unitProgressList).isNotEmpty();
    assertThat(unitProgressList.get(0).getUnitName()).isEqualTo(SERVICE_STEP_COMMAND_UNIT);
    assertThat(unitProgressList.get(1).getUnitName()).isEqualTo(OVERRIDES_COMMAND_UNIT);
    assertThat(unitProgressList.get(2).getUnitName()).isEqualTo(ManifestsStep.MANIFESTS_VALIDATION_UNIT);
    assertThat(unitProgressList.get(2).getStatus()).isEqualTo(UnitStatus.SUCCESS);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void testHandleChildrenResponse_withValidManifestId_noValidationUnit() {
    setupCommonMocksForHandleChildrenResponse();
    when(serviceStepSweepingOutputHelper.fetchServiceManifestsSweepingOutput(any()))
        .thenReturn(buildManifestSweepingOutput("myManifest", "k8s"));

    Ambiance ambiance = buildAmbiance(true);
    StepResponse response = serviceStep.handleChildrenResponse(
        ambiance, UnifiedServiceStepParameters.builder().build(), Collections.emptyMap());

    boolean hasValidationUnit = response.getUnitProgressList().stream().anyMatch(
        up -> ManifestsStep.MANIFESTS_VALIDATION_UNIT.equals(up.getUnitName()));
    assertThat(hasValidationUnit).isFalse();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void testHandleChildrenResponse_withBrokeChildStatus_serviceUnitIsFailureOverridesIsSuccess() {
    setupCommonMocksForHandleChildrenResponse();
    when(serviceStepSweepingOutputHelper.fetchServiceManifestsSweepingOutput(any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());

    Map<String, ResponseData> responseDataMap = new HashMap<>();
    responseDataMap.put("childNode",
        StepResponseNotifyData.builder()
            .nodeUuid("childNodeId")
            .status(Status.FAILED)
            .failureInfo(FailureInfo.newBuilder().setErrorMessage("child failed").build())
            .build());

    Ambiance ambiance = buildAmbiance(true);
    StepResponse response =
        serviceStep.handleChildrenResponse(ambiance, UnifiedServiceStepParameters.builder().build(), responseDataMap);

    List<UnitProgress> unitProgressList = response.getUnitProgressList();
    assertThat(unitProgressList).hasSizeGreaterThanOrEqualTo(2);
    assertThat(unitProgressList.get(0).getUnitName()).isEqualTo(SERVICE_STEP_COMMAND_UNIT);
    assertThat(unitProgressList.get(0).getStatus()).isEqualTo(UnitStatus.FAILURE);
    assertThat(unitProgressList.get(1).getUnitName()).isEqualTo(OVERRIDES_COMMAND_UNIT);
    assertThat(unitProgressList.get(1).getStatus()).isEqualTo(UnitStatus.SUCCESS);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleFreezeResponse_NoFreezeOutput() {
    Ambiance ambiance = buildAmbiance(true);

    when(transientExecutionDataService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());

    StepResponse response = serviceStep.handleFreezeResponse(ambiance);

    assertThat(response).isNull();
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testObtainChildrenAfterRbac_throwsWhenDeclaredTypeMismatchesResolvedType() {
    Ambiance ambiance = buildAmbiance(true);
    // Service resolves to HELM, but the stage declared 'kubernetes' -> mismatch must fail before any downstream work.
    ProcessedServiceResult resolvedAsHelm =
        ProcessedServiceResult.builder()
            .serviceEntityMetadata(ServiceEntityMetadata.builder().name("my-svc").identifier("my-svc").build())
            .serviceConfig(ServiceConfig.builder()
                               .serviceInfoConfig(ServiceInfoConfig.builder().uses(ServiceType.HELM).build())
                               .build())
            .build();
    when(serviceEntityProcessor.getMergedServiceYamlAndSaveOutput(
             any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(resolvedAsHelm);

    UnifiedServiceStepParameters stepParameters = UnifiedServiceStepParameters.builder()
                                                      .serviceRef(ParameterField.createValueField("my-svc"))
                                                      .environmentRef(ParameterField.createValueField("my-env"))
                                                      .infraId(ParameterField.createValueField("my-infra"))
                                                      .serviceType("kubernetes")
                                                      .build();

    assertThatThrownBy(() -> serviceStep.obtainChildrenAfterRbac(ambiance, stepParameters, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("my-svc")
        .hasMessageContaining("does not match the declared type");
  }
}
