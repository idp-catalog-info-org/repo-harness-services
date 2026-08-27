/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.states.V1.cd.helpers;

import static io.harness.rule.OwnerRule.RITEK_ROUNAK;
import static io.harness.rule.OwnerRule.THRISHANK;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.cd.beans.outcomes.UnifiedServiceOutcome;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.execution.states.helpers.ServiceStepSweepingOutputHelper;
import io.harness.data.structure.UUIDGenerator;
import io.harness.engine.governance.PolicyEvaluationFailureException;
import io.harness.exception.UnexpectedException;
import io.harness.opaclient.OpaServiceClientHelper;
import io.harness.opaclient.model.OpaConstants;
import io.harness.opaclient.model.OpaEvaluationResponseHolder;
import io.harness.opaclient.model.OpaPolicyEvaluationResponse;
import io.harness.opaclient.model.OpaPolicySetEvaluationResponse;
import io.harness.opaclient.model.PolicyData;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.PrincipalType;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.rule.Owner;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.security.dto.ServicePrincipal;
import io.harness.security.dto.UserPrincipal;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class UnifiedServiceStepOpaHelperTest {
  private static final String ACCOUNT_ID = "test-account";
  private static final String ORG_ID = "test-org-id";
  private static final String PROJECT_ID = "test-project-id";
  private static final String PIPELINE_ID = "test-pipeline-id";
  private static final String STAGE_EXECUTION_ID = "test-stage-id";
  private static final String PLAN_EXECUTION_ID = "plan-execution-id";
  private static final String SETUP_ID = "test-id";
  private static final String SERVICE_IDENTIFIER = "test-service";
  private static final String PRINCIPAL = "test-user@harness.io";
  private static final String TRIGGERED_BY_USERNAME = "test-user";
  private static final String SERVICE_METADATA_SWEEPING_OUTPUT = "serviceMetadataSweepingOutput";

  @Mock private OpaServiceClientHelper opaServiceClientHelper;
  @Mock private ExecutionSweepingOutputService sweepingOutputService;
  @Mock private ServiceStepSweepingOutputHelper serviceStepSweepingOutputHelper;
  @Mock private CIExecutionServiceConfig ciExecutionServiceConfig;

  @InjectMocks private UnifiedServiceStepOpaHelper unifiedServiceStepOpaHelper;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
    when(ciExecutionServiceConfig.isEnableOpaEvaluation()).thenReturn(true);
    // Default mock for service variables lookup (called by resolveServiceVariables)
    when(sweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
  }

  @After
  public void tearDown() {
    SourcePrincipalContextBuilder.setSourcePrincipal(null);
    SecurityContextBuilder.unsetCompleteContext();
  }

  private Ambiance buildAmbiance() {
    List<Level> levels = new ArrayList<>();
    levels.add(
        Level.newBuilder()
            .setRuntimeId(UUIDGenerator.generateUuid())
            .setSetupId(SETUP_ID)
            .setIdentifier("unifiedServiceStep")
            .setStepType(StepType.newBuilder().setType("UnifiedServiceStep").setStepCategory(StepCategory.STEP).build())
            .setRetryIndex(0)
            .build());

    return Ambiance.newBuilder()
        .putAllSetupAbstractions(Map.of("accountId", ACCOUNT_ID, "orgIdentifier", ORG_ID, "projectIdentifier",
            PROJECT_ID, "pipelineIdentifier", PIPELINE_ID))
        .addAllLevels(levels)
        .setPlanExecutionId(PLAN_EXECUTION_ID)
        .setStageExecutionId(STAGE_EXECUTION_ID)
        .setMetadata(ExecutionMetadata.newBuilder()
                         .setPipelineIdentifier(PIPELINE_ID)
                         .setPrincipalInfo(ExecutionPrincipalInfo.newBuilder()
                                               .setPrincipal(PRINCIPAL)
                                               .setPrincipalType(PrincipalType.USER)
                                               .build())
                         .build())
        .build();
  }

  private Ambiance buildAmbianceWithRbacValidation() {
    return buildAmbiance()
        .toBuilder()
        .setMetadata(ExecutionMetadata.newBuilder()
                         .setPipelineIdentifier(PIPELINE_ID)
                         .setPrincipalInfo(ExecutionPrincipalInfo.newBuilder()
                                               .setPrincipal(PRINCIPAL)
                                               .setPrincipalType(PrincipalType.USER)
                                               .setShouldValidateRbac(true)
                                               .build())
                         .setTriggerInfo(ExecutionTriggerInfo.newBuilder()
                                             .setTriggeredBy(TriggeredBy.newBuilder()
                                                                 .setIdentifier(TRIGGERED_BY_USERNAME)
                                                                 .putExtraInfo("email", PRINCIPAL)
                                                                 .build())
                                             .build())
                         .build())
        .build();
  }

  private List<StepResponse.StepOutcome> buildServiceStepOutcomes() {
    UnifiedServiceOutcome serviceOutcome = UnifiedServiceOutcome.builder()
                                               .identifier(SERVICE_IDENTIFIER)
                                               .name("Test Service")
                                               .type("Kubernetes")
                                               .description("Test service for OPA evaluation")
                                               .build();

    return Collections.singletonList(
        StepResponse.StepOutcome.builder().name("service").outcome(serviceOutcome).build());
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testOpaEvaluationSkippedWhenStepFailed() {
    Ambiance ambiance = buildAmbiance();
    List<StepResponse.StepOutcome> stepOutcomes = buildServiceStepOutcomes();
    StepResponse stepResponse = StepResponse.builder().status(Status.FAILED).build();

    unifiedServiceStepOpaHelper.checkAndCallOpaForServiceRuntimeContext(ambiance, stepOutcomes, stepResponse);

    verify(opaServiceClientHelper, never())
        .shouldEvaluatePolicyWithRetry(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testOpaEvaluationSkippedWhenPolicyCheckNotRequired() {
    Ambiance ambiance = buildAmbiance();
    List<StepResponse.StepOutcome> stepOutcomes = buildServiceStepOutcomes();
    StepResponse stepResponse = StepResponse.builder().status(Status.SUCCEEDED).build();

    when(opaServiceClientHelper.shouldEvaluatePolicyWithRetry(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID),
             eq(OpaConstants.OPA_EVALUATION_TYPE_SERVICE), eq(OpaConstants.OPA_EVALUATION_ACTION_SERVICE_RUN),
             eq(HarnessYamlVersion.V1)))
        .thenReturn(false);

    unifiedServiceStepOpaHelper.checkAndCallOpaForServiceRuntimeContext(ambiance, stepOutcomes, stepResponse);

    verify(opaServiceClientHelper, times(1))
        .shouldEvaluatePolicyWithRetry(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID),
            eq(OpaConstants.OPA_EVALUATION_TYPE_SERVICE), eq(OpaConstants.OPA_EVALUATION_ACTION_SERVICE_RUN),
            eq(HarnessYamlVersion.V1));
    verify(opaServiceClientHelper, never())
        .evaluateWithCredentialsWithRetry(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
            anyString(), anyString(), anyString(), anyString(), any(JsonNode.class));
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testOpaEvaluationThrowsExceptionWhenServiceIdentifierIsEmpty() {
    Ambiance ambiance = buildAmbiance();
    UnifiedServiceOutcome serviceOutcome =
        UnifiedServiceOutcome.builder().identifier("").name("Test Service").build(); // Empty identifier

    List<StepResponse.StepOutcome> stepOutcomes =
        Collections.singletonList(StepResponse.StepOutcome.builder().name("service").outcome(serviceOutcome).build());
    StepResponse stepResponse = StepResponse.builder().status(Status.SUCCEEDED).build();

    when(opaServiceClientHelper.shouldEvaluatePolicyWithRetry(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID),
             eq(OpaConstants.OPA_EVALUATION_TYPE_SERVICE), eq(OpaConstants.OPA_EVALUATION_ACTION_SERVICE_RUN),
             eq(HarnessYamlVersion.V1)))
        .thenReturn(true);

    when(serviceStepSweepingOutputHelper.resolveServiceOutcomeFromNgOutcomes(any())).thenReturn(null);
    when(serviceStepSweepingOutputHelper.fetchServiceMetadataOutput(any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());

    assertThatThrownBy(
        () -> unifiedServiceStepOpaHelper.checkAndCallOpaForServiceRuntimeContext(ambiance, stepOutcomes, stepResponse))
        .isInstanceOf(UnexpectedException.class)
        .hasMessageContaining("Service identifier is empty");
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testSuccessfulOpaEvaluation() throws Exception {
    Ambiance ambiance = buildAmbiance();
    List<StepResponse.StepOutcome> stepOutcomes = buildServiceStepOutcomes();
    StepResponse stepResponse = StepResponse.builder().status(Status.SUCCEEDED).build();

    when(opaServiceClientHelper.shouldEvaluatePolicyWithRetry(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID),
             eq(OpaConstants.OPA_EVALUATION_TYPE_SERVICE), eq(OpaConstants.OPA_EVALUATION_ACTION_SERVICE_RUN),
             eq(HarnessYamlVersion.V1)))
        .thenReturn(true);

    when(serviceStepSweepingOutputHelper.resolveServiceOutcomeFromNgOutcomes(any())).thenReturn(null);
    when(serviceStepSweepingOutputHelper.fetchServiceMetadataOutput(any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());

    OpaEvaluationResponseHolder successResponse =
        OpaEvaluationResponseHolder.builder().status(OpaConstants.OPA_STATUS_PASS).id("policy-success").build();

    when(opaServiceClientHelper.evaluateWithCredentialsWithRetry(eq(OpaConstants.OPA_EVALUATION_TYPE_SERVICE),
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(OpaConstants.OPA_EVALUATION_ACTION_SERVICE_RUN),
             eq(SERVICE_IDENTIFIER), anyString(), eq(PRINCIPAL), eq(String.valueOf(PrincipalType.USER)),
             eq(HarnessYamlVersion.V1), any(JsonNode.class)))
        .thenReturn(successResponse);

    unifiedServiceStepOpaHelper.checkAndCallOpaForServiceRuntimeContext(ambiance, stepOutcomes, stepResponse);

    verify(opaServiceClientHelper, times(1))
        .evaluateWithCredentialsWithRetry(eq(OpaConstants.OPA_EVALUATION_TYPE_SERVICE), eq(ACCOUNT_ID), eq(ORG_ID),
            eq(PROJECT_ID), eq(OpaConstants.OPA_EVALUATION_ACTION_SERVICE_RUN), eq(SERVICE_IDENTIFIER), anyString(),
            eq(PRINCIPAL), eq(String.valueOf(PrincipalType.USER)), eq(HarnessYamlVersion.V1), any(JsonNode.class));
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testOpaEvaluationThrowsPolicyViolation() throws Exception {
    Ambiance ambiance = buildAmbiance();
    List<StepResponse.StepOutcome> stepOutcomes = buildServiceStepOutcomes();
    StepResponse stepResponse = StepResponse.builder().status(Status.SUCCEEDED).build();

    when(opaServiceClientHelper.shouldEvaluatePolicyWithRetry(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID),
             eq(OpaConstants.OPA_EVALUATION_TYPE_SERVICE), eq(OpaConstants.OPA_EVALUATION_ACTION_SERVICE_RUN),
             eq(HarnessYamlVersion.V1)))
        .thenReturn(true);

    when(serviceStepSweepingOutputHelper.resolveServiceOutcomeFromNgOutcomes(any())).thenReturn(null);
    when(serviceStepSweepingOutputHelper.fetchServiceMetadataOutput(any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());

    // Create a properly structured OPA error response with nested policy details
    PolicyData policyData = PolicyData.builder()
                                .identifier("service-policy")
                                .name("Service Policy")
                                .account_id(ACCOUNT_ID)
                                .org_id(ORG_ID)
                                .project_id(PROJECT_ID)
                                .created(System.currentTimeMillis())
                                .updated(System.currentTimeMillis())
                                .severity("high")
                                .build();

    OpaPolicyEvaluationResponse policyEvalResponse =
        OpaPolicyEvaluationResponse.builder()
            .status(OpaConstants.OPA_STATUS_ERROR)
            .policy(policyData)
            .deny_messages(List.of("Service must use approved container registry"))
            .build();

    OpaPolicySetEvaluationResponse policySetResponse = OpaPolicySetEvaluationResponse.builder()
                                                           .status(OpaConstants.OPA_STATUS_ERROR)
                                                           .identifier("service-policy-set")
                                                           .name("Service Policy Set")
                                                           .details(List.of(policyEvalResponse))
                                                           .build();

    OpaEvaluationResponseHolder errorResponse = OpaEvaluationResponseHolder.builder()
                                                    .status(OpaConstants.OPA_STATUS_ERROR)
                                                    .id("policy-violation")
                                                    .details(List.of(policySetResponse))
                                                    .build();

    when(opaServiceClientHelper.evaluateWithCredentialsWithRetry(eq(OpaConstants.OPA_EVALUATION_TYPE_SERVICE),
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(OpaConstants.OPA_EVALUATION_ACTION_SERVICE_RUN),
             eq(SERVICE_IDENTIFIER), anyString(), eq(PRINCIPAL), eq(String.valueOf(PrincipalType.USER)),
             eq(HarnessYamlVersion.V1), any(JsonNode.class)))
        .thenReturn(errorResponse);

    assertThatThrownBy(
        () -> unifiedServiceStepOpaHelper.checkAndCallOpaForServiceRuntimeContext(ambiance, stepOutcomes, stepResponse))
        .isInstanceOf(PolicyEvaluationFailureException.class);
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testOpaEvaluationHandlesJsonProcessingError() throws Exception {
    Ambiance ambiance = buildAmbiance();
    List<StepResponse.StepOutcome> stepOutcomes = buildServiceStepOutcomes();
    StepResponse stepResponse = StepResponse.builder().status(Status.SUCCEEDED).build();

    when(opaServiceClientHelper.shouldEvaluatePolicyWithRetry(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID),
             eq(OpaConstants.OPA_EVALUATION_TYPE_SERVICE), eq(OpaConstants.OPA_EVALUATION_ACTION_SERVICE_RUN),
             eq(HarnessYamlVersion.V1)))
        .thenReturn(true);

    when(serviceStepSweepingOutputHelper.resolveServiceOutcomeFromNgOutcomes(any())).thenReturn(null);
    when(serviceStepSweepingOutputHelper.fetchServiceMetadataOutput(any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());

    when(opaServiceClientHelper.evaluateWithCredentialsWithRetry(eq(OpaConstants.OPA_EVALUATION_TYPE_SERVICE),
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(OpaConstants.OPA_EVALUATION_ACTION_SERVICE_RUN),
             eq(SERVICE_IDENTIFIER), anyString(), eq(PRINCIPAL), eq(String.valueOf(PrincipalType.USER)),
             eq(HarnessYamlVersion.V1), any(JsonNode.class)))
        .thenThrow(new RuntimeException("JSON processing error"));

    assertThatThrownBy(
        () -> unifiedServiceStepOpaHelper.checkAndCallOpaForServiceRuntimeContext(ambiance, stepOutcomes, stepResponse))
        .isInstanceOf(UnexpectedException.class)
        .hasMessageContaining("Failed to process JSON for unified service OPA evaluation");
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testOpaEvaluationFallsBackToSweepingOutputWhenServiceNotInStepOutcomes() throws Exception {
    Ambiance ambiance = buildAmbiance();
    List<StepResponse.StepOutcome> stepOutcomes = new ArrayList<>();
    StepResponse stepResponse = StepResponse.builder().status(Status.SUCCEEDED).build();

    when(opaServiceClientHelper.shouldEvaluatePolicyWithRetry(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID),
             eq(OpaConstants.OPA_EVALUATION_TYPE_SERVICE), eq(OpaConstants.OPA_EVALUATION_ACTION_SERVICE_RUN),
             eq(HarnessYamlVersion.V1)))
        .thenReturn(true);

    UnifiedServiceOutcome serviceOutcomeFromSweepingOutput = UnifiedServiceOutcome.builder()
                                                                 .identifier(SERVICE_IDENTIFIER)
                                                                 .name("Service From Sweeping Output")
                                                                 .type("Kubernetes")
                                                                 .description("Service retrieved from sweeping output")
                                                                 .build();

    when(serviceStepSweepingOutputHelper.resolveServiceOutcomeFromNgOutcomes(any())).thenReturn(null);
    when(serviceStepSweepingOutputHelper.fetchServiceMetadataOutput(any()))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(serviceOutcomeFromSweepingOutput).build());

    OpaEvaluationResponseHolder successResponse =
        OpaEvaluationResponseHolder.builder().status(OpaConstants.OPA_STATUS_PASS).id("policy-success").build();

    when(opaServiceClientHelper.evaluateWithCredentialsWithRetry(eq(OpaConstants.OPA_EVALUATION_TYPE_SERVICE),
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(OpaConstants.OPA_EVALUATION_ACTION_SERVICE_RUN),
             eq(SERVICE_IDENTIFIER), anyString(), eq(PRINCIPAL), eq(String.valueOf(PrincipalType.USER)),
             eq(HarnessYamlVersion.V1), any(JsonNode.class)))
        .thenReturn(successResponse);

    unifiedServiceStepOpaHelper.checkAndCallOpaForServiceRuntimeContext(ambiance, stepOutcomes, stepResponse);

    verify(serviceStepSweepingOutputHelper, times(1)).resolveServiceOutcomeFromNgOutcomes(any());
    verify(serviceStepSweepingOutputHelper, times(1)).fetchServiceMetadataOutput(any());
    verify(opaServiceClientHelper, times(1))
        .evaluateWithCredentialsWithRetry(eq(OpaConstants.OPA_EVALUATION_TYPE_SERVICE), eq(ACCOUNT_ID), eq(ORG_ID),
            eq(PROJECT_ID), eq(OpaConstants.OPA_EVALUATION_ACTION_SERVICE_RUN), eq(SERVICE_IDENTIFIER), anyString(),
            eq(PRINCIPAL), eq(String.valueOf(PrincipalType.USER)), eq(HarnessYamlVersion.V1), any(JsonNode.class));
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testOpaEvaluationThrowsExceptionWhenServiceNotFoundInStepOutcomesOrSweepingOutput() {
    Ambiance ambiance = buildAmbiance();
    List<StepResponse.StepOutcome> stepOutcomes = new ArrayList<>();
    StepResponse stepResponse = StepResponse.builder().status(Status.SUCCEEDED).build();

    when(opaServiceClientHelper.shouldEvaluatePolicyWithRetry(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID),
             eq(OpaConstants.OPA_EVALUATION_TYPE_SERVICE), eq(OpaConstants.OPA_EVALUATION_ACTION_SERVICE_RUN),
             eq(HarnessYamlVersion.V1)))
        .thenReturn(true);

    when(serviceStepSweepingOutputHelper.resolveServiceOutcomeFromNgOutcomes(any())).thenReturn(null);
    when(serviceStepSweepingOutputHelper.fetchServiceMetadataOutput(any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());

    assertThatThrownBy(
        () -> unifiedServiceStepOpaHelper.checkAndCallOpaForServiceRuntimeContext(ambiance, stepOutcomes, stepResponse))
        .isInstanceOf(UnexpectedException.class)
        .hasMessageContaining("Service identifier is empty");

    verify(serviceStepSweepingOutputHelper, times(1)).resolveServiceOutcomeFromNgOutcomes(any());
    verify(serviceStepSweepingOutputHelper, times(1)).fetchServiceMetadataOutput(any());
    verify(opaServiceClientHelper, never())
        .evaluateWithCredentialsWithRetry(eq(OpaConstants.OPA_EVALUATION_TYPE_SERVICE), eq(ACCOUNT_ID), eq(ORG_ID),
            eq(PROJECT_ID), eq(OpaConstants.OPA_EVALUATION_ACTION_SERVICE_RUN), anyString(), anyString(), anyString(),
            anyString(), eq(HarnessYamlVersion.V1), any(JsonNode.class));
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testOpaEvaluationUsesStepOutcomeWhenAvailable_NoFallbackNeeded() throws Exception {
    Ambiance ambiance = buildAmbiance();
    List<StepResponse.StepOutcome> stepOutcomes = buildServiceStepOutcomes();
    StepResponse stepResponse = StepResponse.builder().status(Status.SUCCEEDED).build();

    when(opaServiceClientHelper.shouldEvaluatePolicyWithRetry(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID),
             eq(OpaConstants.OPA_EVALUATION_TYPE_SERVICE), eq(OpaConstants.OPA_EVALUATION_ACTION_SERVICE_RUN),
             eq(HarnessYamlVersion.V1)))
        .thenReturn(true);

    OpaEvaluationResponseHolder successResponse =
        OpaEvaluationResponseHolder.builder().status(OpaConstants.OPA_STATUS_PASS).id("policy-success").build();

    when(opaServiceClientHelper.evaluateWithCredentialsWithRetry(eq(OpaConstants.OPA_EVALUATION_TYPE_SERVICE),
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(OpaConstants.OPA_EVALUATION_ACTION_SERVICE_RUN),
             eq(SERVICE_IDENTIFIER), anyString(), eq(PRINCIPAL), eq(String.valueOf(PrincipalType.USER)),
             eq(HarnessYamlVersion.V1), any(JsonNode.class)))
        .thenReturn(successResponse);

    unifiedServiceStepOpaHelper.checkAndCallOpaForServiceRuntimeContext(ambiance, stepOutcomes, stepResponse);

    // When service is found in step outcomes, the helper methods should not be called for fallback
    verify(serviceStepSweepingOutputHelper, never()).resolveServiceOutcomeFromNgOutcomes(any());
    verify(serviceStepSweepingOutputHelper, never()).fetchServiceMetadataOutput(any());
    verify(opaServiceClientHelper, times(1))
        .evaluateWithCredentialsWithRetry(eq(OpaConstants.OPA_EVALUATION_TYPE_SERVICE), eq(ACCOUNT_ID), eq(ORG_ID),
            eq(PROJECT_ID), eq(OpaConstants.OPA_EVALUATION_ACTION_SERVICE_RUN), eq(SERVICE_IDENTIFIER), anyString(),
            eq(PRINCIPAL), eq(String.valueOf(PrincipalType.USER)), eq(HarnessYamlVersion.V1), any(JsonNode.class));
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testOpaEvaluationUsesNgOutcomesWhenStepOutcomeMissing() throws Exception {
    Ambiance ambiance = buildAmbiance();
    List<StepResponse.StepOutcome> stepOutcomes = new ArrayList<>();
    StepResponse stepResponse = StepResponse.builder().status(Status.SUCCEEDED).build();

    when(opaServiceClientHelper.shouldEvaluatePolicyWithRetry(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID),
             eq(OpaConstants.OPA_EVALUATION_TYPE_SERVICE), eq(OpaConstants.OPA_EVALUATION_ACTION_SERVICE_RUN),
             eq(HarnessYamlVersion.V1)))
        .thenReturn(true);

    UnifiedServiceOutcome serviceOutcomeFromNgOutcomes = UnifiedServiceOutcome.builder()
                                                             .identifier(SERVICE_IDENTIFIER)
                                                             .name("Test Service")
                                                             .type("Kubernetes")
                                                             .description("From NG")
                                                             .build();

    when(serviceStepSweepingOutputHelper.resolveServiceOutcomeFromNgOutcomes(any()))
        .thenReturn(serviceOutcomeFromNgOutcomes);

    OpaEvaluationResponseHolder successResponse =
        OpaEvaluationResponseHolder.builder().status(OpaConstants.OPA_STATUS_PASS).id("policy-success").build();

    when(opaServiceClientHelper.evaluateWithCredentialsWithRetry(eq(OpaConstants.OPA_EVALUATION_TYPE_SERVICE),
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(OpaConstants.OPA_EVALUATION_ACTION_SERVICE_RUN),
             eq(SERVICE_IDENTIFIER), anyString(), eq(PRINCIPAL), eq(String.valueOf(PrincipalType.USER)),
             eq(HarnessYamlVersion.V1), any(JsonNode.class)))
        .thenReturn(successResponse);

    unifiedServiceStepOpaHelper.checkAndCallOpaForServiceRuntimeContext(ambiance, stepOutcomes, stepResponse);

    verify(serviceStepSweepingOutputHelper, times(1)).resolveServiceOutcomeFromNgOutcomes(any());
    verify(serviceStepSweepingOutputHelper, never()).fetchServiceMetadataOutput(any());
    verify(opaServiceClientHelper, times(1))
        .evaluateWithCredentialsWithRetry(eq(OpaConstants.OPA_EVALUATION_TYPE_SERVICE), eq(ACCOUNT_ID), eq(ORG_ID),
            eq(PROJECT_ID), eq(OpaConstants.OPA_EVALUATION_ACTION_SERVICE_RUN), eq(SERVICE_IDENTIFIER), anyString(),
            eq(PRINCIPAL), eq(String.valueOf(PrincipalType.USER)), eq(HarnessYamlVersion.V1), any(JsonNode.class));
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testOpaEvaluationWithSweepingOutputFallback_PolicyViolation() throws Exception {
    Ambiance ambiance = buildAmbiance();
    List<StepResponse.StepOutcome> stepOutcomes = new ArrayList<>();
    StepResponse stepResponse = StepResponse.builder().status(Status.SUCCEEDED).build();

    when(opaServiceClientHelper.shouldEvaluatePolicyWithRetry(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID),
             eq(OpaConstants.OPA_EVALUATION_TYPE_SERVICE), eq(OpaConstants.OPA_EVALUATION_ACTION_SERVICE_RUN),
             eq(HarnessYamlVersion.V1)))
        .thenReturn(true);

    UnifiedServiceOutcome serviceOutcomeFromSweepingOutput = UnifiedServiceOutcome.builder()
                                                                 .identifier(SERVICE_IDENTIFIER)
                                                                 .name("Service From Sweeping Output")
                                                                 .type("Kubernetes")
                                                                 .build();

    when(serviceStepSweepingOutputHelper.resolveServiceOutcomeFromNgOutcomes(any())).thenReturn(null);
    when(serviceStepSweepingOutputHelper.fetchServiceMetadataOutput(any()))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(serviceOutcomeFromSweepingOutput).build());

    PolicyData policyData = PolicyData.builder()
                                .identifier("service-policy")
                                .name("Service Policy")
                                .account_id(ACCOUNT_ID)
                                .org_id(ORG_ID)
                                .project_id(PROJECT_ID)
                                .created(System.currentTimeMillis())
                                .updated(System.currentTimeMillis())
                                .severity("high")
                                .build();

    OpaPolicyEvaluationResponse policyEvalResponse =
        OpaPolicyEvaluationResponse.builder()
            .status(OpaConstants.OPA_STATUS_ERROR)
            .policy(policyData)
            .deny_messages(List.of("Service configuration violates security policy"))
            .build();

    OpaPolicySetEvaluationResponse policySetResponse = OpaPolicySetEvaluationResponse.builder()
                                                           .status(OpaConstants.OPA_STATUS_ERROR)
                                                           .identifier("service-policy-set")
                                                           .name("Service Policy Set")
                                                           .details(List.of(policyEvalResponse))
                                                           .build();

    OpaEvaluationResponseHolder errorResponse = OpaEvaluationResponseHolder.builder()
                                                    .status(OpaConstants.OPA_STATUS_ERROR)
                                                    .id("policy-violation")
                                                    .details(List.of(policySetResponse))
                                                    .build();

    when(opaServiceClientHelper.evaluateWithCredentialsWithRetry(eq(OpaConstants.OPA_EVALUATION_TYPE_SERVICE),
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(OpaConstants.OPA_EVALUATION_ACTION_SERVICE_RUN),
             eq(SERVICE_IDENTIFIER), anyString(), eq(PRINCIPAL), eq(String.valueOf(PrincipalType.USER)),
             eq(HarnessYamlVersion.V1), any(JsonNode.class)))
        .thenReturn(errorResponse);

    assertThatThrownBy(
        () -> unifiedServiceStepOpaHelper.checkAndCallOpaForServiceRuntimeContext(ambiance, stepOutcomes, stepResponse))
        .isInstanceOf(PolicyEvaluationFailureException.class);

    verify(serviceStepSweepingOutputHelper, times(1)).resolveServiceOutcomeFromNgOutcomes(any());
    verify(serviceStepSweepingOutputHelper, times(1)).fetchServiceMetadataOutput(any());
    verify(opaServiceClientHelper, times(1))
        .evaluateWithCredentialsWithRetry(eq(OpaConstants.OPA_EVALUATION_TYPE_SERVICE), eq(ACCOUNT_ID), eq(ORG_ID),
            eq(PROJECT_ID), eq(OpaConstants.OPA_EVALUATION_ACTION_SERVICE_RUN), eq(SERVICE_IDENTIFIER), anyString(),
            eq(PRINCIPAL), eq(String.valueOf(PrincipalType.USER)), eq(HarnessYamlVersion.V1), any(JsonNode.class));
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testOpaEvaluationWithNgOutcomesContainingTags() throws Exception {
    Ambiance ambiance = buildAmbiance();
    List<StepResponse.StepOutcome> stepOutcomes = new ArrayList<>();
    StepResponse stepResponse = StepResponse.builder().status(Status.SUCCEEDED).build();

    when(opaServiceClientHelper.shouldEvaluatePolicyWithRetry(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID),
             eq(OpaConstants.OPA_EVALUATION_TYPE_SERVICE), eq(OpaConstants.OPA_EVALUATION_ACTION_SERVICE_RUN),
             eq(HarnessYamlVersion.V1)))
        .thenReturn(true);

    UnifiedServiceOutcome serviceOutcomeFromNgOutcomes = UnifiedServiceOutcome.builder()
                                                             .identifier(SERVICE_IDENTIFIER)
                                                             .name("Test Service")
                                                             .type("Kubernetes")
                                                             .description("From NG with tags")
                                                             .tags(Map.of("env", "prod", "team", "platform"))
                                                             .build();

    when(serviceStepSweepingOutputHelper.resolveServiceOutcomeFromNgOutcomes(any()))
        .thenReturn(serviceOutcomeFromNgOutcomes);

    OpaEvaluationResponseHolder successResponse =
        OpaEvaluationResponseHolder.builder().status(OpaConstants.OPA_STATUS_PASS).id("policy-success").build();

    when(opaServiceClientHelper.evaluateWithCredentialsWithRetry(eq(OpaConstants.OPA_EVALUATION_TYPE_SERVICE),
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(OpaConstants.OPA_EVALUATION_ACTION_SERVICE_RUN),
             eq(SERVICE_IDENTIFIER), anyString(), eq(PRINCIPAL), eq(String.valueOf(PrincipalType.USER)),
             eq(HarnessYamlVersion.V1), any(JsonNode.class)))
        .thenReturn(successResponse);

    unifiedServiceStepOpaHelper.checkAndCallOpaForServiceRuntimeContext(ambiance, stepOutcomes, stepResponse);

    verify(opaServiceClientHelper, times(1))
        .evaluateWithCredentialsWithRetry(eq(OpaConstants.OPA_EVALUATION_TYPE_SERVICE), eq(ACCOUNT_ID), eq(ORG_ID),
            eq(PROJECT_ID), eq(OpaConstants.OPA_EVALUATION_ACTION_SERVICE_RUN), eq(SERVICE_IDENTIFIER), anyString(),
            eq(PRINCIPAL), eq(String.valueOf(PrincipalType.USER)), eq(HarnessYamlVersion.V1), any(JsonNode.class));
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testOpaEvaluationFallsBackToSweepingOutputWhenNgOutcomesServiceIsBlank() throws Exception {
    Ambiance ambiance = buildAmbiance();
    List<StepResponse.StepOutcome> stepOutcomes = new ArrayList<>();
    StepResponse stepResponse = StepResponse.builder().status(Status.SUCCEEDED).build();

    when(opaServiceClientHelper.shouldEvaluatePolicyWithRetry(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID),
             eq(OpaConstants.OPA_EVALUATION_TYPE_SERVICE), eq(OpaConstants.OPA_EVALUATION_ACTION_SERVICE_RUN),
             eq(HarnessYamlVersion.V1)))
        .thenReturn(true);

    UnifiedServiceOutcome serviceOutcomeFromSweepingOutput = UnifiedServiceOutcome.builder()
                                                                 .identifier(SERVICE_IDENTIFIER)
                                                                 .name("Service From Sweeping Output")
                                                                 .type("Kubernetes")
                                                                 .build();

    // When ngOutcomes has blank service, helper returns null
    when(serviceStepSweepingOutputHelper.resolveServiceOutcomeFromNgOutcomes(any())).thenReturn(null);
    when(serviceStepSweepingOutputHelper.fetchServiceMetadataOutput(any()))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(serviceOutcomeFromSweepingOutput).build());

    OpaEvaluationResponseHolder successResponse =
        OpaEvaluationResponseHolder.builder().status(OpaConstants.OPA_STATUS_PASS).id("policy-success").build();

    when(opaServiceClientHelper.evaluateWithCredentialsWithRetry(eq(OpaConstants.OPA_EVALUATION_TYPE_SERVICE),
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(OpaConstants.OPA_EVALUATION_ACTION_SERVICE_RUN),
             eq(SERVICE_IDENTIFIER), anyString(), eq(PRINCIPAL), eq(String.valueOf(PrincipalType.USER)),
             eq(HarnessYamlVersion.V1), any(JsonNode.class)))
        .thenReturn(successResponse);

    unifiedServiceStepOpaHelper.checkAndCallOpaForServiceRuntimeContext(ambiance, stepOutcomes, stepResponse);

    verify(serviceStepSweepingOutputHelper, times(1)).resolveServiceOutcomeFromNgOutcomes(any());
    verify(serviceStepSweepingOutputHelper, times(1)).fetchServiceMetadataOutput(any());
    verify(opaServiceClientHelper, times(1))
        .evaluateWithCredentialsWithRetry(eq(OpaConstants.OPA_EVALUATION_TYPE_SERVICE), eq(ACCOUNT_ID), eq(ORG_ID),
            eq(PROJECT_ID), eq(OpaConstants.OPA_EVALUATION_ACTION_SERVICE_RUN), eq(SERVICE_IDENTIFIER), anyString(),
            eq(PRINCIPAL), eq(String.valueOf(PrincipalType.USER)), eq(HarnessYamlVersion.V1), any(JsonNode.class));
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testOpaEvaluationFallsBackWhenNgOutcomesServiceIsNotString() throws Exception {
    Ambiance ambiance = buildAmbiance();
    List<StepResponse.StepOutcome> stepOutcomes = new ArrayList<>();
    StepResponse stepResponse = StepResponse.builder().status(Status.SUCCEEDED).build();

    when(opaServiceClientHelper.shouldEvaluatePolicyWithRetry(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID),
             eq(OpaConstants.OPA_EVALUATION_TYPE_SERVICE), eq(OpaConstants.OPA_EVALUATION_ACTION_SERVICE_RUN),
             eq(HarnessYamlVersion.V1)))
        .thenReturn(true);

    UnifiedServiceOutcome serviceOutcomeFromSweepingOutput = UnifiedServiceOutcome.builder()
                                                                 .identifier(SERVICE_IDENTIFIER)
                                                                 .name("Service From Sweeping Output")
                                                                 .type("Kubernetes")
                                                                 .build();

    // When ngOutcomes has non-string service, helper returns null
    when(serviceStepSweepingOutputHelper.resolveServiceOutcomeFromNgOutcomes(any())).thenReturn(null);
    when(serviceStepSweepingOutputHelper.fetchServiceMetadataOutput(any()))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(serviceOutcomeFromSweepingOutput).build());

    OpaEvaluationResponseHolder successResponse =
        OpaEvaluationResponseHolder.builder().status(OpaConstants.OPA_STATUS_PASS).id("policy-success").build();

    when(opaServiceClientHelper.evaluateWithCredentialsWithRetry(eq(OpaConstants.OPA_EVALUATION_TYPE_SERVICE),
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(OpaConstants.OPA_EVALUATION_ACTION_SERVICE_RUN),
             eq(SERVICE_IDENTIFIER), anyString(), eq(PRINCIPAL), eq(String.valueOf(PrincipalType.USER)),
             eq(HarnessYamlVersion.V1), any(JsonNode.class)))
        .thenReturn(successResponse);

    unifiedServiceStepOpaHelper.checkAndCallOpaForServiceRuntimeContext(ambiance, stepOutcomes, stepResponse);

    verify(serviceStepSweepingOutputHelper, times(1)).resolveServiceOutcomeFromNgOutcomes(any());
    verify(serviceStepSweepingOutputHelper, times(1)).fetchServiceMetadataOutput(any());
    verify(opaServiceClientHelper, times(1))
        .evaluateWithCredentialsWithRetry(eq(OpaConstants.OPA_EVALUATION_TYPE_SERVICE), eq(ACCOUNT_ID), eq(ORG_ID),
            eq(PROJECT_ID), eq(OpaConstants.OPA_EVALUATION_ACTION_SERVICE_RUN), eq(SERVICE_IDENTIFIER), anyString(),
            eq(PRINCIPAL), eq(String.valueOf(PrincipalType.USER)), eq(HarnessYamlVersion.V1), any(JsonNode.class));
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testOpaEvaluationFallsBackWhenNgOutcomesOutputIsNotVariablesSweepingOutput() throws Exception {
    Ambiance ambiance = buildAmbiance();
    List<StepResponse.StepOutcome> stepOutcomes = new ArrayList<>();
    StepResponse stepResponse = StepResponse.builder().status(Status.SUCCEEDED).build();

    when(opaServiceClientHelper.shouldEvaluatePolicyWithRetry(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID),
             eq(OpaConstants.OPA_EVALUATION_TYPE_SERVICE), eq(OpaConstants.OPA_EVALUATION_ACTION_SERVICE_RUN),
             eq(HarnessYamlVersion.V1)))
        .thenReturn(true);

    UnifiedServiceOutcome serviceOutcomeFromSweepingOutput = UnifiedServiceOutcome.builder()
                                                                 .identifier(SERVICE_IDENTIFIER)
                                                                 .name("Service From Sweeping Output")
                                                                 .type("Kubernetes")
                                                                 .build();

    // When ngOutcomes output is not VariablesSweepingOutput, helper returns null
    when(serviceStepSweepingOutputHelper.resolveServiceOutcomeFromNgOutcomes(any())).thenReturn(null);
    when(serviceStepSweepingOutputHelper.fetchServiceMetadataOutput(any()))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(serviceOutcomeFromSweepingOutput).build());

    OpaEvaluationResponseHolder successResponse =
        OpaEvaluationResponseHolder.builder().status(OpaConstants.OPA_STATUS_PASS).id("policy-success").build();

    when(opaServiceClientHelper.evaluateWithCredentialsWithRetry(eq(OpaConstants.OPA_EVALUATION_TYPE_SERVICE),
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(OpaConstants.OPA_EVALUATION_ACTION_SERVICE_RUN),
             eq(SERVICE_IDENTIFIER), anyString(), eq(PRINCIPAL), eq(String.valueOf(PrincipalType.USER)),
             eq(HarnessYamlVersion.V1), any(JsonNode.class)))
        .thenReturn(successResponse);

    unifiedServiceStepOpaHelper.checkAndCallOpaForServiceRuntimeContext(ambiance, stepOutcomes, stepResponse);

    verify(serviceStepSweepingOutputHelper, times(1)).resolveServiceOutcomeFromNgOutcomes(any());
    verify(serviceStepSweepingOutputHelper, times(1)).fetchServiceMetadataOutput(any());
    verify(opaServiceClientHelper, times(1))
        .evaluateWithCredentialsWithRetry(eq(OpaConstants.OPA_EVALUATION_TYPE_SERVICE), eq(ACCOUNT_ID), eq(ORG_ID),
            eq(PROJECT_ID), eq(OpaConstants.OPA_EVALUATION_ACTION_SERVICE_RUN), eq(SERVICE_IDENTIFIER), anyString(),
            eq(PRINCIPAL), eq(String.valueOf(PrincipalType.USER)), eq(HarnessYamlVersion.V1), any(JsonNode.class));
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testOpaEvaluationFallsBackWhenNgOutcomesJsonParsingFails() throws Exception {
    Ambiance ambiance = buildAmbiance();
    List<StepResponse.StepOutcome> stepOutcomes = new ArrayList<>();
    StepResponse stepResponse = StepResponse.builder().status(Status.SUCCEEDED).build();

    when(opaServiceClientHelper.shouldEvaluatePolicyWithRetry(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID),
             eq(OpaConstants.OPA_EVALUATION_TYPE_SERVICE), eq(OpaConstants.OPA_EVALUATION_ACTION_SERVICE_RUN),
             eq(HarnessYamlVersion.V1)))
        .thenReturn(true);

    UnifiedServiceOutcome serviceOutcomeFromSweepingOutput = UnifiedServiceOutcome.builder()
                                                                 .identifier(SERVICE_IDENTIFIER)
                                                                 .name("Service From Sweeping Output")
                                                                 .type("Kubernetes")
                                                                 .build();

    // When JSON parsing fails, helper returns null
    when(serviceStepSweepingOutputHelper.resolveServiceOutcomeFromNgOutcomes(any())).thenReturn(null);
    when(serviceStepSweepingOutputHelper.fetchServiceMetadataOutput(any()))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(serviceOutcomeFromSweepingOutput).build());

    OpaEvaluationResponseHolder successResponse =
        OpaEvaluationResponseHolder.builder().status(OpaConstants.OPA_STATUS_PASS).id("policy-success").build();

    when(opaServiceClientHelper.evaluateWithCredentialsWithRetry(eq(OpaConstants.OPA_EVALUATION_TYPE_SERVICE),
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(OpaConstants.OPA_EVALUATION_ACTION_SERVICE_RUN),
             eq(SERVICE_IDENTIFIER), anyString(), eq(PRINCIPAL), eq(String.valueOf(PrincipalType.USER)),
             eq(HarnessYamlVersion.V1), any(JsonNode.class)))
        .thenReturn(successResponse);

    unifiedServiceStepOpaHelper.checkAndCallOpaForServiceRuntimeContext(ambiance, stepOutcomes, stepResponse);

    verify(serviceStepSweepingOutputHelper, times(1)).resolveServiceOutcomeFromNgOutcomes(any());
    verify(serviceStepSweepingOutputHelper, times(1)).fetchServiceMetadataOutput(any());
    verify(opaServiceClientHelper, times(1))
        .evaluateWithCredentialsWithRetry(eq(OpaConstants.OPA_EVALUATION_TYPE_SERVICE), eq(ACCOUNT_ID), eq(ORG_ID),
            eq(PROJECT_ID), eq(OpaConstants.OPA_EVALUATION_ACTION_SERVICE_RUN), eq(SERVICE_IDENTIFIER), anyString(),
            eq(PRINCIPAL), eq(String.valueOf(PrincipalType.USER)), eq(HarnessYamlVersion.V1), any(JsonNode.class));
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testOpaEvaluationWithNgOutcomesHavingNullFields() throws Exception {
    Ambiance ambiance = buildAmbiance();
    List<StepResponse.StepOutcome> stepOutcomes = new ArrayList<>();
    StepResponse stepResponse = StepResponse.builder().status(Status.SUCCEEDED).build();

    when(opaServiceClientHelper.shouldEvaluatePolicyWithRetry(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID),
             eq(OpaConstants.OPA_EVALUATION_TYPE_SERVICE), eq(OpaConstants.OPA_EVALUATION_ACTION_SERVICE_RUN),
             eq(HarnessYamlVersion.V1)))
        .thenReturn(true);

    // Service outcome with null fields but valid identifier
    UnifiedServiceOutcome serviceOutcomeFromNgOutcomes =
        UnifiedServiceOutcome.builder().identifier(SERVICE_IDENTIFIER).name(null).type(null).description(null).build();

    when(serviceStepSweepingOutputHelper.resolveServiceOutcomeFromNgOutcomes(any()))
        .thenReturn(serviceOutcomeFromNgOutcomes);

    OpaEvaluationResponseHolder successResponse =
        OpaEvaluationResponseHolder.builder().status(OpaConstants.OPA_STATUS_PASS).id("policy-success").build();

    when(opaServiceClientHelper.evaluateWithCredentialsWithRetry(eq(OpaConstants.OPA_EVALUATION_TYPE_SERVICE),
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(OpaConstants.OPA_EVALUATION_ACTION_SERVICE_RUN),
             eq(SERVICE_IDENTIFIER), anyString(), eq(PRINCIPAL), eq(String.valueOf(PrincipalType.USER)),
             eq(HarnessYamlVersion.V1), any(JsonNode.class)))
        .thenReturn(successResponse);

    unifiedServiceStepOpaHelper.checkAndCallOpaForServiceRuntimeContext(ambiance, stepOutcomes, stepResponse);

    verify(opaServiceClientHelper, times(1))
        .evaluateWithCredentialsWithRetry(eq(OpaConstants.OPA_EVALUATION_TYPE_SERVICE), eq(ACCOUNT_ID), eq(ORG_ID),
            eq(PROJECT_ID), eq(OpaConstants.OPA_EVALUATION_ACTION_SERVICE_RUN), eq(SERVICE_IDENTIFIER), anyString(),
            eq(PRINCIPAL), eq(String.valueOf(PrincipalType.USER)), eq(HarnessYamlVersion.V1), any(JsonNode.class));
  }

  @Test
  @Owner(developers = THRISHANK)
  @Category(UnitTests.class)
  public void testOpaEvaluationSendsAmbiancePrincipalOnBothOpaCalls() throws Exception {
    Ambiance ambiance = buildAmbianceWithRbacValidation();
    List<StepResponse.StepOutcome> stepOutcomes = buildServiceStepOutcomes();
    StepResponse stepResponse = StepResponse.builder().status(Status.SUCCEEDED).build();

    // The step runs on an orchestration thread with no principal, so capture what each OPA call sees.
    SecurityContextBuilder.unsetCompleteContext();
    AtomicReference<Principal> principalOnShouldEvaluate = new AtomicReference<>();
    AtomicReference<Principal> principalOnEvaluate = new AtomicReference<>();

    when(opaServiceClientHelper.shouldEvaluatePolicyWithRetry(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID),
             eq(OpaConstants.OPA_EVALUATION_TYPE_SERVICE), eq(OpaConstants.OPA_EVALUATION_ACTION_SERVICE_RUN),
             eq(HarnessYamlVersion.V1)))
        .thenAnswer(invocation -> {
          principalOnShouldEvaluate.set(SecurityContextBuilder.getPrincipal());
          return true;
        });

    when(opaServiceClientHelper.evaluateWithCredentialsWithRetry(eq(OpaConstants.OPA_EVALUATION_TYPE_SERVICE),
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(OpaConstants.OPA_EVALUATION_ACTION_SERVICE_RUN),
             eq(SERVICE_IDENTIFIER), anyString(), eq(PRINCIPAL), eq(String.valueOf(PrincipalType.USER)),
             eq(HarnessYamlVersion.V1), any(JsonNode.class)))
        .thenAnswer(invocation -> {
          principalOnEvaluate.set(SecurityContextBuilder.getPrincipal());
          return OpaEvaluationResponseHolder.builder()
              .status(OpaConstants.OPA_STATUS_PASS)
              .id("policy-success")
              .build();
        });

    unifiedServiceStepOpaHelper.checkAndCallOpaForServiceRuntimeContext(ambiance, stepOutcomes, stepResponse);

    // Both calls go through the NON_PRIVILEGED OPA client, so both need the principal in context.
    assertThat(principalOnShouldEvaluate.get()).isInstanceOf(UserPrincipal.class);
    assertThat(principalOnShouldEvaluate.get().getName()).isEqualTo(PRINCIPAL);

    assertThat(principalOnEvaluate.get()).isInstanceOf(UserPrincipal.class);
    UserPrincipal userPrincipal = (UserPrincipal) principalOnEvaluate.get();
    assertThat(userPrincipal.getName()).isEqualTo(PRINCIPAL);
    assertThat(userPrincipal.getEmail()).isEqualTo(PRINCIPAL);
    assertThat(userPrincipal.getUsername()).isEqualTo(TRIGGERED_BY_USERNAME);
    assertThat(userPrincipal.getAccountId()).isEqualTo(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = THRISHANK)
  @Category(UnitTests.class)
  public void testOpaEvaluationRestoresPriorSecurityContext() throws Exception {
    Ambiance ambiance = buildAmbianceWithRbacValidation();
    List<StepResponse.StepOutcome> stepOutcomes = buildServiceStepOutcomes();
    StepResponse stepResponse = StepResponse.builder().status(Status.SUCCEEDED).build();

    Principal priorPrincipal = new ServicePrincipal("ci-manager");
    SecurityContextBuilder.setContext(priorPrincipal);
    SourcePrincipalContextBuilder.setSourcePrincipal(priorPrincipal);

    when(opaServiceClientHelper.shouldEvaluatePolicyWithRetry(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID),
             eq(OpaConstants.OPA_EVALUATION_TYPE_SERVICE), eq(OpaConstants.OPA_EVALUATION_ACTION_SERVICE_RUN),
             eq(HarnessYamlVersion.V1)))
        .thenReturn(true);

    when(opaServiceClientHelper.evaluateWithCredentialsWithRetry(eq(OpaConstants.OPA_EVALUATION_TYPE_SERVICE),
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(OpaConstants.OPA_EVALUATION_ACTION_SERVICE_RUN),
             eq(SERVICE_IDENTIFIER), anyString(), eq(PRINCIPAL), eq(String.valueOf(PrincipalType.USER)),
             eq(HarnessYamlVersion.V1), any(JsonNode.class)))
        .thenReturn(
            OpaEvaluationResponseHolder.builder().status(OpaConstants.OPA_STATUS_PASS).id("policy-success").build());

    unifiedServiceStepOpaHelper.checkAndCallOpaForServiceRuntimeContext(ambiance, stepOutcomes, stepResponse);

    assertThat(SecurityContextBuilder.getPrincipal()).isSameAs(priorPrincipal);
    assertThat(SourcePrincipalContextBuilder.getSourcePrincipal()).isSameAs(priorPrincipal);
  }

  @Test
  @Owner(developers = THRISHANK)
  @Category(UnitTests.class)
  public void testOpaEvaluationRestoresEmptyContextWhenPolicyEvaluationFails() throws Exception {
    Ambiance ambiance = buildAmbianceWithRbacValidation();
    List<StepResponse.StepOutcome> stepOutcomes = buildServiceStepOutcomes();
    StepResponse stepResponse = StepResponse.builder().status(Status.SUCCEEDED).build();

    SecurityContextBuilder.unsetCompleteContext();
    assertThat(SecurityContextBuilder.getPrincipal()).isNull();

    when(opaServiceClientHelper.shouldEvaluatePolicyWithRetry(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID),
             eq(OpaConstants.OPA_EVALUATION_TYPE_SERVICE), eq(OpaConstants.OPA_EVALUATION_ACTION_SERVICE_RUN),
             eq(HarnessYamlVersion.V1)))
        .thenReturn(true);

    OpaPolicyEvaluationResponse policyEvalResponse =
        OpaPolicyEvaluationResponse.builder()
            .status(OpaConstants.OPA_STATUS_ERROR)
            .policy(PolicyData.builder().identifier("service-policy").name("Service Policy").build())
            .deny_messages(List.of("Service must use approved container registry"))
            .build();
    OpaEvaluationResponseHolder errorResponse = OpaEvaluationResponseHolder.builder()
                                                    .status(OpaConstants.OPA_STATUS_ERROR)
                                                    .id("policy-violation")
                                                    .details(List.of(OpaPolicySetEvaluationResponse.builder()
                                                                         .status(OpaConstants.OPA_STATUS_ERROR)
                                                                         .identifier("service-policy-set")
                                                                         .name("Service Policy Set")
                                                                         .details(List.of(policyEvalResponse))
                                                                         .build()))
                                                    .build();

    AtomicReference<Principal> principalOnEvaluate = new AtomicReference<>();
    when(opaServiceClientHelper.evaluateWithCredentialsWithRetry(eq(OpaConstants.OPA_EVALUATION_TYPE_SERVICE),
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(OpaConstants.OPA_EVALUATION_ACTION_SERVICE_RUN),
             eq(SERVICE_IDENTIFIER), anyString(), eq(PRINCIPAL), eq(String.valueOf(PrincipalType.USER)),
             eq(HarnessYamlVersion.V1), any(JsonNode.class)))
        .thenAnswer(invocation -> {
          principalOnEvaluate.set(SecurityContextBuilder.getPrincipal());
          return errorResponse;
        });

    assertThatThrownBy(
        () -> unifiedServiceStepOpaHelper.checkAndCallOpaForServiceRuntimeContext(ambiance, stepOutcomes, stepResponse))
        .isInstanceOf(PolicyEvaluationFailureException.class);

    assertThat(principalOnEvaluate.get()).isInstanceOf(UserPrincipal.class);
    // The guard must not leak the execution principal onto the thread once the policy failure unwinds.
    assertThat(SecurityContextBuilder.getPrincipal()).isNull();
    assertThat(SourcePrincipalContextBuilder.getSourcePrincipal()).isNull();
  }
}
