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
import io.harness.cd.beans.outcomes.EnvironmentOutcome;
import io.harness.cd.beans.outcomes.InfraStepOutcome;
import io.harness.ci.config.CIExecutionServiceConfig;
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
import java.util.HashMap;
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

public class UnifiedInfraStepOpaHelperTest {
  private static final String ACCOUNT_ID = "test-account";
  private static final String ORG_ID = "test-org-id";
  private static final String PROJECT_ID = "test-project-id";
  private static final String PIPELINE_ID = "test-pipeline-id";
  private static final String STAGE_EXECUTION_ID = "test-stage-id";
  private static final String PLAN_EXECUTION_ID = "plan-execution-id";
  private static final String SETUP_ID = "test-id";
  private static final String INFRA_IDENTIFIER = "test-infra";
  private static final String PRINCIPAL = "test-user@harness.io";
  private static final String TRIGGERED_BY_USERNAME = "test-user";

  @Mock private OpaServiceClientHelper opaServiceClientHelper;
  @Mock private UnifiedServiceStepOpaHelper unifiedServiceStepOpaHelper;
  @Mock private ExecutionSweepingOutputService sweepingOutputService;
  @Mock private CIExecutionServiceConfig ciExecutionServiceConfig;

  @InjectMocks private UnifiedInfraStepOpaHelper unifiedInfraStepOpaHelper;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
    when(ciExecutionServiceConfig.isEnableOpaEvaluation()).thenReturn(true);
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
            .setIdentifier("unifiedInfraStep")
            .setStepType(StepType.newBuilder().setType("UnifiedCDInfraStep").setStepCategory(StepCategory.STEP).build())
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

  private InfraStepOutcome buildInfraStepOutcome() {
    EnvironmentOutcome environmentOutcome =
        EnvironmentOutcome.builder().identifier("test-env").name("Test Environment").build();

    InfraStepOutcome infraStepOutcome = InfraStepOutcome.builder()
                                            .identifier(INFRA_IDENTIFIER)
                                            .name("Test Infrastructure")
                                            .kind("KubernetesDirect")
                                            .description("Test infrastructure for OPA evaluation")
                                            .infrastructureKey("test-infra-key")
                                            .environment(environmentOutcome)
                                            .build();
    infraStepOutcome.populateMap();
    return infraStepOutcome;
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testOpaEvaluationSkippedWhenInfraOutcomeIsNull() {
    Ambiance ambiance = buildAmbiance();
    StepResponse stepResponse = StepResponse.builder().status(Status.SUCCEEDED).build();

    when(sweepingOutputService.resolveOptional(any(Ambiance.class), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());

    unifiedInfraStepOpaHelper.checkAndCallOpaForInfrastructureRuntimeContext(ambiance, null, stepResponse);

    verify(sweepingOutputService, times(1)).resolveOptional(any(Ambiance.class), any());
    verify(opaServiceClientHelper, never())
        .shouldEvaluatePolicyWithRetry(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testOpaEvaluationSkippedWhenStepFailed() {
    Ambiance ambiance = buildAmbiance();
    InfraStepOutcome infraStepOutcome = buildInfraStepOutcome();
    StepResponse stepResponse = StepResponse.builder().status(Status.FAILED).build();

    unifiedInfraStepOpaHelper.checkAndCallOpaForInfrastructureRuntimeContext(ambiance, infraStepOutcome, stepResponse);

    verify(opaServiceClientHelper, never())
        .shouldEvaluatePolicyWithRetry(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testOpaEvaluationSkippedWhenPolicyCheckNotRequired() {
    Ambiance ambiance = buildAmbiance();
    InfraStepOutcome infraStepOutcome = buildInfraStepOutcome();
    StepResponse stepResponse = StepResponse.builder().status(Status.SUCCEEDED).build();

    when(opaServiceClientHelper.shouldEvaluatePolicyWithRetry(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID),
             eq(OpaConstants.OPA_EVALUATION_TYPE_INFRASTRUCTURE), eq(OpaConstants.OPA_EVALUATION_ACTION_INFRA_ENV_RUN),
             eq(HarnessYamlVersion.V1)))
        .thenReturn(false);

    unifiedInfraStepOpaHelper.checkAndCallOpaForInfrastructureRuntimeContext(ambiance, infraStepOutcome, stepResponse);

    verify(opaServiceClientHelper, times(1))
        .shouldEvaluatePolicyWithRetry(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID),
            eq(OpaConstants.OPA_EVALUATION_TYPE_INFRASTRUCTURE), eq(OpaConstants.OPA_EVALUATION_ACTION_INFRA_ENV_RUN),
            eq(HarnessYamlVersion.V1));
    verify(opaServiceClientHelper, never())
        .evaluateWithCredentialsWithRetry(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
            anyString(), anyString(), anyString(), any(JsonNode.class));
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testOpaEvaluationThrowsExceptionWhenInfraIdentifierIsEmpty() {
    Ambiance ambiance = buildAmbiance();
    InfraStepOutcome infraStepOutcome = InfraStepOutcome.builder()
                                            .identifier("") // Empty identifier
                                            .name("Test Infrastructure")
                                            .build();
    StepResponse stepResponse = StepResponse.builder().status(Status.SUCCEEDED).build();

    when(opaServiceClientHelper.shouldEvaluatePolicyWithRetry(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID),
             eq(OpaConstants.OPA_EVALUATION_TYPE_INFRASTRUCTURE), eq(OpaConstants.OPA_EVALUATION_ACTION_INFRA_ENV_RUN),
             eq(HarnessYamlVersion.V1)))
        .thenReturn(true);

    assertThatThrownBy(()
                           -> unifiedInfraStepOpaHelper.checkAndCallOpaForInfrastructureRuntimeContext(
                               ambiance, infraStepOutcome, stepResponse))
        .isInstanceOf(UnexpectedException.class)
        .hasMessageContaining("Infrastructure identifier is empty");
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testSuccessfulOpaEvaluation() throws Exception {
    Ambiance ambiance = buildAmbiance();
    InfraStepOutcome infraStepOutcome = buildInfraStepOutcome();
    StepResponse stepResponse = StepResponse.builder().status(Status.SUCCEEDED).build();

    when(opaServiceClientHelper.shouldEvaluatePolicyWithRetry(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID),
             eq(OpaConstants.OPA_EVALUATION_TYPE_INFRASTRUCTURE), eq(OpaConstants.OPA_EVALUATION_ACTION_INFRA_ENV_RUN),
             eq(HarnessYamlVersion.V1)))
        .thenReturn(true);

    when(unifiedServiceStepOpaHelper.resolveServiceVariables(any(Ambiance.class))).thenReturn(new HashMap<>());

    OpaEvaluationResponseHolder successResponse =
        OpaEvaluationResponseHolder.builder().status(OpaConstants.OPA_STATUS_PASS).id("policy-success").build();

    when(opaServiceClientHelper.evaluateWithCredentialsWithRetry(eq(OpaConstants.OPA_EVALUATION_TYPE_INFRASTRUCTURE),
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(OpaConstants.OPA_EVALUATION_ACTION_INFRA_ENV_RUN),
             eq(INFRA_IDENTIFIER), anyString(), eq(PRINCIPAL), eq(String.valueOf(PrincipalType.USER)),
             eq(HarnessYamlVersion.V1), any(JsonNode.class)))
        .thenReturn(successResponse);

    unifiedInfraStepOpaHelper.checkAndCallOpaForInfrastructureRuntimeContext(ambiance, infraStepOutcome, stepResponse);

    verify(opaServiceClientHelper, times(1))
        .evaluateWithCredentialsWithRetry(eq(OpaConstants.OPA_EVALUATION_TYPE_INFRASTRUCTURE), eq(ACCOUNT_ID),
            eq(ORG_ID), eq(PROJECT_ID), eq(OpaConstants.OPA_EVALUATION_ACTION_INFRA_ENV_RUN), eq(INFRA_IDENTIFIER),
            anyString(), eq(PRINCIPAL), eq(String.valueOf(PrincipalType.USER)), eq(HarnessYamlVersion.V1),
            any(JsonNode.class));
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testOpaEvaluationThrowsPolicyViolation() throws Exception {
    Ambiance ambiance = buildAmbiance();
    InfraStepOutcome infraStepOutcome = buildInfraStepOutcome();
    StepResponse stepResponse = StepResponse.builder().status(Status.SUCCEEDED).build();

    when(opaServiceClientHelper.shouldEvaluatePolicyWithRetry(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID),
             eq(OpaConstants.OPA_EVALUATION_TYPE_INFRASTRUCTURE), eq(OpaConstants.OPA_EVALUATION_ACTION_INFRA_ENV_RUN),
             eq(HarnessYamlVersion.V1)))
        .thenReturn(true);

    when(unifiedServiceStepOpaHelper.resolveServiceVariables(any(Ambiance.class))).thenReturn(new HashMap<>());

    // Create a properly structured OPA error response with nested policy details
    PolicyData policyData = PolicyData.builder()
                                .identifier("infra-policy")
                                .name("Infrastructure Policy")
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
            .deny_messages(List.of("Infrastructure must use approved namespaces"))
            .build();

    OpaPolicySetEvaluationResponse policySetResponse = OpaPolicySetEvaluationResponse.builder()
                                                           .status(OpaConstants.OPA_STATUS_ERROR)
                                                           .identifier("infra-policy-set")
                                                           .name("Infrastructure Policy Set")
                                                           .details(List.of(policyEvalResponse))
                                                           .build();

    OpaEvaluationResponseHolder errorResponse = OpaEvaluationResponseHolder.builder()
                                                    .status(OpaConstants.OPA_STATUS_ERROR)
                                                    .id("policy-violation")
                                                    .details(List.of(policySetResponse))
                                                    .build();

    when(opaServiceClientHelper.evaluateWithCredentialsWithRetry(eq(OpaConstants.OPA_EVALUATION_TYPE_INFRASTRUCTURE),
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(OpaConstants.OPA_EVALUATION_ACTION_INFRA_ENV_RUN),
             eq(INFRA_IDENTIFIER), anyString(), eq(PRINCIPAL), eq(String.valueOf(PrincipalType.USER)),
             eq(HarnessYamlVersion.V1), any(JsonNode.class)))
        .thenReturn(errorResponse);

    assertThatThrownBy(()
                           -> unifiedInfraStepOpaHelper.checkAndCallOpaForInfrastructureRuntimeContext(
                               ambiance, infraStepOutcome, stepResponse))
        .isInstanceOf(PolicyEvaluationFailureException.class);
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testOpaEvaluationHandlesJsonProcessingError() throws Exception {
    Ambiance ambiance = buildAmbiance();
    InfraStepOutcome infraStepOutcome = buildInfraStepOutcome();
    StepResponse stepResponse = StepResponse.builder().status(Status.SUCCEEDED).build();

    when(opaServiceClientHelper.shouldEvaluatePolicyWithRetry(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID),
             eq(OpaConstants.OPA_EVALUATION_TYPE_INFRASTRUCTURE), eq(OpaConstants.OPA_EVALUATION_ACTION_INFRA_ENV_RUN),
             eq(HarnessYamlVersion.V1)))
        .thenReturn(true);

    when(unifiedServiceStepOpaHelper.resolveServiceVariables(any(Ambiance.class))).thenReturn(new HashMap<>());

    when(opaServiceClientHelper.evaluateWithCredentialsWithRetry(eq(OpaConstants.OPA_EVALUATION_TYPE_INFRASTRUCTURE),
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(OpaConstants.OPA_EVALUATION_ACTION_INFRA_ENV_RUN),
             eq(INFRA_IDENTIFIER), anyString(), eq(PRINCIPAL), eq(String.valueOf(PrincipalType.USER)),
             eq(HarnessYamlVersion.V1), any(JsonNode.class)))
        .thenThrow(new RuntimeException("JSON processing error"));

    assertThatThrownBy(()
                           -> unifiedInfraStepOpaHelper.checkAndCallOpaForInfrastructureRuntimeContext(
                               ambiance, infraStepOutcome, stepResponse))
        .isInstanceOf(UnexpectedException.class)
        .hasMessageContaining("Failed to process JSON for unified infrastructure OPA evaluation");
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testOpaEvaluationWithNullInfraIdentifier() {
    Ambiance ambiance = buildAmbiance();
    InfraStepOutcome infraStepOutcome = InfraStepOutcome.builder().name("Test Infrastructure").build();
    StepResponse stepResponse = StepResponse.builder().status(Status.SUCCEEDED).build();

    when(opaServiceClientHelper.shouldEvaluatePolicyWithRetry(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID),
             eq(OpaConstants.OPA_EVALUATION_TYPE_INFRASTRUCTURE), eq(OpaConstants.OPA_EVALUATION_ACTION_INFRA_ENV_RUN),
             eq(HarnessYamlVersion.V1)))
        .thenReturn(true);

    assertThatThrownBy(()
                           -> unifiedInfraStepOpaHelper.checkAndCallOpaForInfrastructureRuntimeContext(
                               ambiance, infraStepOutcome, stepResponse))
        .isInstanceOf(UnexpectedException.class)
        .hasMessageContaining("Infrastructure identifier is empty");
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testSuccessfulOpaEvaluationWithVariables() throws Exception {
    Ambiance ambiance = buildAmbiance();
    InfraStepOutcome infraStepOutcome = buildInfraStepOutcome();
    StepResponse stepResponse = StepResponse.builder().status(Status.SUCCEEDED).build();

    when(opaServiceClientHelper.shouldEvaluatePolicyWithRetry(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID),
             eq(OpaConstants.OPA_EVALUATION_TYPE_INFRASTRUCTURE), eq(OpaConstants.OPA_EVALUATION_ACTION_INFRA_ENV_RUN),
             eq(HarnessYamlVersion.V1)))
        .thenReturn(true);

    Map<String, String> serviceVariables = new HashMap<>();
    serviceVariables.put("region", "us-east-1");
    serviceVariables.put("namespace", "production");
    when(unifiedServiceStepOpaHelper.resolveServiceVariables(any(Ambiance.class))).thenReturn(serviceVariables);

    OpaEvaluationResponseHolder successResponse =
        OpaEvaluationResponseHolder.builder().status(OpaConstants.OPA_STATUS_PASS).id("policy-success").build();

    when(opaServiceClientHelper.evaluateWithCredentialsWithRetry(eq(OpaConstants.OPA_EVALUATION_TYPE_INFRASTRUCTURE),
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(OpaConstants.OPA_EVALUATION_ACTION_INFRA_ENV_RUN),
             eq(INFRA_IDENTIFIER), anyString(), eq(PRINCIPAL), eq(String.valueOf(PrincipalType.USER)),
             eq(HarnessYamlVersion.V1), any(JsonNode.class)))
        .thenReturn(successResponse);

    unifiedInfraStepOpaHelper.checkAndCallOpaForInfrastructureRuntimeContext(ambiance, infraStepOutcome, stepResponse);

    verify(unifiedServiceStepOpaHelper, times(1)).resolveServiceVariables(ambiance);
    verify(opaServiceClientHelper, times(1))
        .evaluateWithCredentialsWithRetry(eq(OpaConstants.OPA_EVALUATION_TYPE_INFRASTRUCTURE), eq(ACCOUNT_ID),
            eq(ORG_ID), eq(PROJECT_ID), eq(OpaConstants.OPA_EVALUATION_ACTION_INFRA_ENV_RUN), eq(INFRA_IDENTIFIER),
            anyString(), eq(PRINCIPAL), eq(String.valueOf(PrincipalType.USER)), eq(HarnessYamlVersion.V1),
            any(JsonNode.class));
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testOpaEvaluationWithPolicyViolationAndDetailedMessage() throws Exception {
    Ambiance ambiance = buildAmbiance();
    InfraStepOutcome infraStepOutcome = buildInfraStepOutcome();
    StepResponse stepResponse = StepResponse.builder().status(Status.SUCCEEDED).build();

    when(opaServiceClientHelper.shouldEvaluatePolicyWithRetry(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID),
             eq(OpaConstants.OPA_EVALUATION_TYPE_INFRASTRUCTURE), eq(OpaConstants.OPA_EVALUATION_ACTION_INFRA_ENV_RUN),
             eq(HarnessYamlVersion.V1)))
        .thenReturn(true);

    when(unifiedServiceStepOpaHelper.resolveServiceVariables(any(Ambiance.class))).thenReturn(new HashMap<>());

    PolicyData policyData1 =
        PolicyData.builder().identifier("namespace-policy").name("Namespace Policy").severity("high").build();

    PolicyData policyData2 =
        PolicyData.builder().identifier("resource-policy").name("Resource Limits Policy").severity("medium").build();

    OpaPolicyEvaluationResponse policyEval1 =
        OpaPolicyEvaluationResponse.builder()
            .status(OpaConstants.OPA_STATUS_ERROR)
            .policy(policyData1)
            .deny_messages(List.of("Namespace must be in approved list", "Namespace cannot be 'default'"))
            .build();

    OpaPolicyEvaluationResponse policyEval2 = OpaPolicyEvaluationResponse.builder()
                                                  .status(OpaConstants.OPA_STATUS_ERROR)
                                                  .policy(policyData2)
                                                  .deny_messages(List.of("CPU limits must be defined"))
                                                  .build();

    OpaPolicySetEvaluationResponse policySetResponse = OpaPolicySetEvaluationResponse.builder()
                                                           .status(OpaConstants.OPA_STATUS_ERROR)
                                                           .identifier("infra-policy-set")
                                                           .name("Infrastructure Policy Set")
                                                           .details(List.of(policyEval1, policyEval2))
                                                           .build();

    OpaEvaluationResponseHolder errorResponse = OpaEvaluationResponseHolder.builder()
                                                    .status(OpaConstants.OPA_STATUS_ERROR)
                                                    .id("policy-violation")
                                                    .details(List.of(policySetResponse))
                                                    .build();

    when(opaServiceClientHelper.evaluateWithCredentialsWithRetry(eq(OpaConstants.OPA_EVALUATION_TYPE_INFRASTRUCTURE),
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(OpaConstants.OPA_EVALUATION_ACTION_INFRA_ENV_RUN),
             eq(INFRA_IDENTIFIER), anyString(), eq(PRINCIPAL), eq(String.valueOf(PrincipalType.USER)),
             eq(HarnessYamlVersion.V1), any(JsonNode.class)))
        .thenReturn(errorResponse);

    assertThatThrownBy(()
                           -> unifiedInfraStepOpaHelper.checkAndCallOpaForInfrastructureRuntimeContext(
                               ambiance, infraStepOutcome, stepResponse))
        .isInstanceOf(PolicyEvaluationFailureException.class)
        .hasMessageContaining("Policy evaluation failed for 1 policy set");
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testOpaEvaluationSkippedForNonSuccessStatuses() {
    Ambiance ambiance = buildAmbiance();
    InfraStepOutcome infraStepOutcome = buildInfraStepOutcome();

    Status[] nonSuccessStatuses = {Status.FAILED, Status.ABORTED, Status.ERRORED, Status.EXPIRED, Status.SKIPPED};

    for (Status status : nonSuccessStatuses) {
      StepResponse stepResponse = StepResponse.builder().status(status).build();
      unifiedInfraStepOpaHelper.checkAndCallOpaForInfrastructureRuntimeContext(
          ambiance, infraStepOutcome, stepResponse);
    }

    verify(opaServiceClientHelper, never())
        .shouldEvaluatePolicyWithRetry(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testOpaEvaluationPassesPrincipalTypeCorrectly() throws Exception {
    Ambiance ambiance = buildAmbiance();
    InfraStepOutcome infraStepOutcome = buildInfraStepOutcome();
    StepResponse stepResponse = StepResponse.builder().status(Status.SUCCEEDED).build();

    when(opaServiceClientHelper.shouldEvaluatePolicyWithRetry(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID),
             eq(OpaConstants.OPA_EVALUATION_TYPE_INFRASTRUCTURE), eq(OpaConstants.OPA_EVALUATION_ACTION_INFRA_ENV_RUN),
             eq(HarnessYamlVersion.V1)))
        .thenReturn(true);

    when(unifiedServiceStepOpaHelper.resolveServiceVariables(any(Ambiance.class))).thenReturn(new HashMap<>());

    OpaEvaluationResponseHolder successResponse =
        OpaEvaluationResponseHolder.builder().status(OpaConstants.OPA_STATUS_PASS).id("policy-success").build();

    when(opaServiceClientHelper.evaluateWithCredentialsWithRetry(eq(OpaConstants.OPA_EVALUATION_TYPE_INFRASTRUCTURE),
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(OpaConstants.OPA_EVALUATION_ACTION_INFRA_ENV_RUN),
             eq(INFRA_IDENTIFIER), anyString(), eq(PRINCIPAL), eq(String.valueOf(PrincipalType.USER)),
             eq(HarnessYamlVersion.V1), any(JsonNode.class)))
        .thenReturn(successResponse);

    unifiedInfraStepOpaHelper.checkAndCallOpaForInfrastructureRuntimeContext(ambiance, infraStepOutcome, stepResponse);

    verify(opaServiceClientHelper, times(1))
        .evaluateWithCredentialsWithRetry(eq(OpaConstants.OPA_EVALUATION_TYPE_INFRASTRUCTURE), eq(ACCOUNT_ID),
            eq(ORG_ID), eq(PROJECT_ID), eq(OpaConstants.OPA_EVALUATION_ACTION_INFRA_ENV_RUN), eq(INFRA_IDENTIFIER),
            anyString(), eq(PRINCIPAL), eq("USER"), eq(HarnessYamlVersion.V1), any(JsonNode.class));
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testOpaEvaluationBuildsDetailedErrorMessage() throws Exception {
    Ambiance ambiance = buildAmbiance();
    InfraStepOutcome infraStepOutcome = buildInfraStepOutcome();
    StepResponse stepResponse = StepResponse.builder().status(Status.SUCCEEDED).build();

    when(opaServiceClientHelper.shouldEvaluatePolicyWithRetry(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID),
             eq(OpaConstants.OPA_EVALUATION_TYPE_INFRASTRUCTURE), eq(OpaConstants.OPA_EVALUATION_ACTION_INFRA_ENV_RUN),
             eq(HarnessYamlVersion.V1)))
        .thenReturn(true);

    when(unifiedServiceStepOpaHelper.resolveServiceVariables(any(Ambiance.class))).thenReturn(new HashMap<>());

    PolicyData policyData =
        PolicyData.builder().identifier("test-policy").name("Test Policy").severity("critical").build();

    OpaPolicyEvaluationResponse policyEvalResponse =
        OpaPolicyEvaluationResponse.builder()
            .status(OpaConstants.OPA_STATUS_ERROR)
            .policy(policyData)
            .deny_messages(List.of("Detailed error message 1", "Detailed error message 2"))
            .build();

    OpaPolicySetEvaluationResponse policySetResponse = OpaPolicySetEvaluationResponse.builder()
                                                           .status(OpaConstants.OPA_STATUS_ERROR)
                                                           .identifier("test-policy-set")
                                                           .name("Test Policy Set")
                                                           .details(List.of(policyEvalResponse))
                                                           .build();

    OpaEvaluationResponseHolder errorResponse = OpaEvaluationResponseHolder.builder()
                                                    .status(OpaConstants.OPA_STATUS_ERROR)
                                                    .id("error-id")
                                                    .details(List.of(policySetResponse))
                                                    .build();

    when(opaServiceClientHelper.evaluateWithCredentialsWithRetry(eq(OpaConstants.OPA_EVALUATION_TYPE_INFRASTRUCTURE),
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(OpaConstants.OPA_EVALUATION_ACTION_INFRA_ENV_RUN),
             eq(INFRA_IDENTIFIER), anyString(), eq(PRINCIPAL), eq(String.valueOf(PrincipalType.USER)),
             eq(HarnessYamlVersion.V1), any(JsonNode.class)))
        .thenReturn(errorResponse);

    assertThatThrownBy(()
                           -> unifiedInfraStepOpaHelper.checkAndCallOpaForInfrastructureRuntimeContext(
                               ambiance, infraStepOutcome, stepResponse))
        .isInstanceOf(PolicyEvaluationFailureException.class);

    verify(opaServiceClientHelper, times(1))
        .evaluateWithCredentialsWithRetry(eq(OpaConstants.OPA_EVALUATION_TYPE_INFRASTRUCTURE), eq(ACCOUNT_ID),
            eq(ORG_ID), eq(PROJECT_ID), eq(OpaConstants.OPA_EVALUATION_ACTION_INFRA_ENV_RUN), eq(INFRA_IDENTIFIER),
            anyString(), eq(PRINCIPAL), eq(String.valueOf(PrincipalType.USER)), eq(HarnessYamlVersion.V1),
            any(JsonNode.class));
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testOpaEvaluationWithServicePrincipalType() throws Exception {
    List<Level> levels = new ArrayList<>();
    levels.add(
        Level.newBuilder()
            .setRuntimeId(UUIDGenerator.generateUuid())
            .setSetupId(SETUP_ID)
            .setIdentifier("unifiedInfraStep")
            .setStepType(StepType.newBuilder().setType("UnifiedCDInfraStep").setStepCategory(StepCategory.STEP).build())
            .setRetryIndex(0)
            .build());

    Ambiance ambiance = Ambiance.newBuilder()
                            .putAllSetupAbstractions(Map.of("accountId", ACCOUNT_ID, "orgIdentifier", ORG_ID,
                                "projectIdentifier", PROJECT_ID, "pipelineIdentifier", PIPELINE_ID))
                            .addAllLevels(levels)
                            .setPlanExecutionId(PLAN_EXECUTION_ID)
                            .setStageExecutionId(STAGE_EXECUTION_ID)
                            .setMetadata(ExecutionMetadata.newBuilder()
                                             .setPipelineIdentifier(PIPELINE_ID)
                                             .setPrincipalInfo(ExecutionPrincipalInfo.newBuilder()
                                                                   .setPrincipal("service-account")
                                                                   .setPrincipalType(PrincipalType.SERVICE)
                                                                   .build())
                                             .build())
                            .build();

    InfraStepOutcome infraStepOutcome = buildInfraStepOutcome();
    StepResponse stepResponse = StepResponse.builder().status(Status.SUCCEEDED).build();

    when(opaServiceClientHelper.shouldEvaluatePolicyWithRetry(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID),
             eq(OpaConstants.OPA_EVALUATION_TYPE_INFRASTRUCTURE), eq(OpaConstants.OPA_EVALUATION_ACTION_INFRA_ENV_RUN),
             eq(HarnessYamlVersion.V1)))
        .thenReturn(true);

    when(unifiedServiceStepOpaHelper.resolveServiceVariables(any(Ambiance.class))).thenReturn(new HashMap<>());

    OpaEvaluationResponseHolder successResponse =
        OpaEvaluationResponseHolder.builder().status(OpaConstants.OPA_STATUS_PASS).id("policy-success").build();

    when(opaServiceClientHelper.evaluateWithCredentialsWithRetry(eq(OpaConstants.OPA_EVALUATION_TYPE_INFRASTRUCTURE),
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(OpaConstants.OPA_EVALUATION_ACTION_INFRA_ENV_RUN),
             eq(INFRA_IDENTIFIER), anyString(), eq("service-account"), eq(String.valueOf(PrincipalType.SERVICE)),
             eq(HarnessYamlVersion.V1), any(JsonNode.class)))
        .thenReturn(successResponse);

    unifiedInfraStepOpaHelper.checkAndCallOpaForInfrastructureRuntimeContext(ambiance, infraStepOutcome, stepResponse);

    verify(opaServiceClientHelper, times(1))
        .evaluateWithCredentialsWithRetry(eq(OpaConstants.OPA_EVALUATION_TYPE_INFRASTRUCTURE), eq(ACCOUNT_ID),
            eq(ORG_ID), eq(PROJECT_ID), eq(OpaConstants.OPA_EVALUATION_ACTION_INFRA_ENV_RUN), eq(INFRA_IDENTIFIER),
            anyString(), eq("service-account"), eq("SERVICE"), eq(HarnessYamlVersion.V1), any(JsonNode.class));
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testOpaEvaluationErrorStatusTriggersDetailedMessage() throws Exception {
    Ambiance ambiance = buildAmbiance();
    InfraStepOutcome infraStepOutcome = buildInfraStepOutcome();
    StepResponse stepResponse = StepResponse.builder().status(Status.SUCCEEDED).build();

    when(opaServiceClientHelper.shouldEvaluatePolicyWithRetry(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID),
             eq(OpaConstants.OPA_EVALUATION_TYPE_INFRASTRUCTURE), eq(OpaConstants.OPA_EVALUATION_ACTION_INFRA_ENV_RUN),
             eq(HarnessYamlVersion.V1)))
        .thenReturn(true);

    when(unifiedServiceStepOpaHelper.resolveServiceVariables(any(Ambiance.class))).thenReturn(new HashMap<>());

    OpaEvaluationResponseHolder errorResponse =
        OpaEvaluationResponseHolder.builder().status(OpaConstants.OPA_STATUS_ERROR).id("error-response").build();

    when(opaServiceClientHelper.evaluateWithCredentialsWithRetry(eq(OpaConstants.OPA_EVALUATION_TYPE_INFRASTRUCTURE),
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(OpaConstants.OPA_EVALUATION_ACTION_INFRA_ENV_RUN),
             eq(INFRA_IDENTIFIER), anyString(), eq(PRINCIPAL), eq(String.valueOf(PrincipalType.USER)),
             eq(HarnessYamlVersion.V1), any(JsonNode.class)))
        .thenReturn(errorResponse);

    assertThatThrownBy(()
                           -> unifiedInfraStepOpaHelper.checkAndCallOpaForInfrastructureRuntimeContext(
                               ambiance, infraStepOutcome, stepResponse))
        .isInstanceOf(PolicyEvaluationFailureException.class);
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testOpaEvaluationFallsBackToSweepingOutputWhenInfraOutcomeIsNull() throws Exception {
    Ambiance ambiance = buildAmbiance();
    StepResponse stepResponse = StepResponse.builder().status(Status.SUCCEEDED).build();

    when(opaServiceClientHelper.shouldEvaluatePolicyWithRetry(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID),
             eq(OpaConstants.OPA_EVALUATION_TYPE_INFRASTRUCTURE), eq(OpaConstants.OPA_EVALUATION_ACTION_INFRA_ENV_RUN),
             eq(HarnessYamlVersion.V1)))
        .thenReturn(true);

    InfraStepOutcome infraStepOutcomeFromSweepingOutput = buildInfraStepOutcome();
    when(sweepingOutputService.resolveOptional(any(Ambiance.class), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(infraStepOutcomeFromSweepingOutput).build());

    when(unifiedServiceStepOpaHelper.resolveServiceVariables(any(Ambiance.class))).thenReturn(new HashMap<>());

    OpaEvaluationResponseHolder successResponse =
        OpaEvaluationResponseHolder.builder().status(OpaConstants.OPA_STATUS_PASS).id("policy-success").build();

    when(opaServiceClientHelper.evaluateWithCredentialsWithRetry(eq(OpaConstants.OPA_EVALUATION_TYPE_INFRASTRUCTURE),
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(OpaConstants.OPA_EVALUATION_ACTION_INFRA_ENV_RUN),
             eq(INFRA_IDENTIFIER), anyString(), eq(PRINCIPAL), eq(String.valueOf(PrincipalType.USER)),
             eq(HarnessYamlVersion.V1), any(JsonNode.class)))
        .thenReturn(successResponse);

    unifiedInfraStepOpaHelper.checkAndCallOpaForInfrastructureRuntimeContext(ambiance, null, stepResponse);

    verify(sweepingOutputService, times(1)).resolveOptional(any(Ambiance.class), any());
    verify(opaServiceClientHelper, times(1))
        .evaluateWithCredentialsWithRetry(eq(OpaConstants.OPA_EVALUATION_TYPE_INFRASTRUCTURE), eq(ACCOUNT_ID),
            eq(ORG_ID), eq(PROJECT_ID), eq(OpaConstants.OPA_EVALUATION_ACTION_INFRA_ENV_RUN), eq(INFRA_IDENTIFIER),
            anyString(), eq(PRINCIPAL), eq(String.valueOf(PrincipalType.USER)), eq(HarnessYamlVersion.V1),
            any(JsonNode.class));
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testOpaEvaluationSkipsWhenInfraOutcomeNullAndNotFoundInSweepingOutput() {
    Ambiance ambiance = buildAmbiance();
    StepResponse stepResponse = StepResponse.builder().status(Status.SUCCEEDED).build();

    when(sweepingOutputService.resolveOptional(any(Ambiance.class), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());

    unifiedInfraStepOpaHelper.checkAndCallOpaForInfrastructureRuntimeContext(ambiance, null, stepResponse);

    verify(sweepingOutputService, times(1)).resolveOptional(any(Ambiance.class), any());
    verify(opaServiceClientHelper, never())
        .shouldEvaluatePolicyWithRetry(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testOpaEvaluationSkipsWhenInfraOutcomeFoundButOutputIsNull() {
    Ambiance ambiance = buildAmbiance();
    StepResponse stepResponse = StepResponse.builder().status(Status.SUCCEEDED).build();

    when(sweepingOutputService.resolveOptional(any(Ambiance.class), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(null).build());

    unifiedInfraStepOpaHelper.checkAndCallOpaForInfrastructureRuntimeContext(ambiance, null, stepResponse);

    verify(sweepingOutputService, times(1)).resolveOptional(any(Ambiance.class), any());
    verify(opaServiceClientHelper, never())
        .shouldEvaluatePolicyWithRetry(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testOpaEvaluationWithSweepingOutputFallback_PolicyViolation() throws Exception {
    Ambiance ambiance = buildAmbiance();
    StepResponse stepResponse = StepResponse.builder().status(Status.SUCCEEDED).build();

    when(opaServiceClientHelper.shouldEvaluatePolicyWithRetry(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID),
             eq(OpaConstants.OPA_EVALUATION_TYPE_INFRASTRUCTURE), eq(OpaConstants.OPA_EVALUATION_ACTION_INFRA_ENV_RUN),
             eq(HarnessYamlVersion.V1)))
        .thenReturn(true);

    InfraStepOutcome infraStepOutcomeFromSweepingOutput = buildInfraStepOutcome();
    when(sweepingOutputService.resolveOptional(any(Ambiance.class), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(infraStepOutcomeFromSweepingOutput).build());

    when(unifiedServiceStepOpaHelper.resolveServiceVariables(any(Ambiance.class))).thenReturn(new HashMap<>());

    PolicyData policyData = PolicyData.builder()
                                .identifier("infra-policy")
                                .name("Infrastructure Policy")
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
            .deny_messages(List.of("Infrastructure configuration violates security policy"))
            .build();

    OpaPolicySetEvaluationResponse policySetResponse = OpaPolicySetEvaluationResponse.builder()
                                                           .status(OpaConstants.OPA_STATUS_ERROR)
                                                           .identifier("infra-policy-set")
                                                           .name("Infrastructure Policy Set")
                                                           .details(List.of(policyEvalResponse))
                                                           .build();

    OpaEvaluationResponseHolder errorResponse = OpaEvaluationResponseHolder.builder()
                                                    .status(OpaConstants.OPA_STATUS_ERROR)
                                                    .id("policy-violation")
                                                    .details(List.of(policySetResponse))
                                                    .build();

    when(opaServiceClientHelper.evaluateWithCredentialsWithRetry(eq(OpaConstants.OPA_EVALUATION_TYPE_INFRASTRUCTURE),
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(OpaConstants.OPA_EVALUATION_ACTION_INFRA_ENV_RUN),
             eq(INFRA_IDENTIFIER), anyString(), eq(PRINCIPAL), eq(String.valueOf(PrincipalType.USER)),
             eq(HarnessYamlVersion.V1), any(JsonNode.class)))
        .thenReturn(errorResponse);

    assertThatThrownBy(
        () -> unifiedInfraStepOpaHelper.checkAndCallOpaForInfrastructureRuntimeContext(ambiance, null, stepResponse))
        .isInstanceOf(PolicyEvaluationFailureException.class);

    verify(sweepingOutputService, times(1)).resolveOptional(any(Ambiance.class), any());
    verify(opaServiceClientHelper, times(1))
        .evaluateWithCredentialsWithRetry(eq(OpaConstants.OPA_EVALUATION_TYPE_INFRASTRUCTURE), eq(ACCOUNT_ID),
            eq(ORG_ID), eq(PROJECT_ID), eq(OpaConstants.OPA_EVALUATION_ACTION_INFRA_ENV_RUN), eq(INFRA_IDENTIFIER),
            anyString(), eq(PRINCIPAL), eq(String.valueOf(PrincipalType.USER)), eq(HarnessYamlVersion.V1),
            any(JsonNode.class));
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testOpaEvaluationUsesProvidedInfraOutcome_NoFallbackNeeded() throws Exception {
    Ambiance ambiance = buildAmbiance();
    InfraStepOutcome infraStepOutcome = buildInfraStepOutcome();
    StepResponse stepResponse = StepResponse.builder().status(Status.SUCCEEDED).build();

    when(opaServiceClientHelper.shouldEvaluatePolicyWithRetry(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID),
             eq(OpaConstants.OPA_EVALUATION_TYPE_INFRASTRUCTURE), eq(OpaConstants.OPA_EVALUATION_ACTION_INFRA_ENV_RUN),
             eq(HarnessYamlVersion.V1)))
        .thenReturn(true);

    when(unifiedServiceStepOpaHelper.resolveServiceVariables(any(Ambiance.class))).thenReturn(new HashMap<>());

    OpaEvaluationResponseHolder successResponse =
        OpaEvaluationResponseHolder.builder().status(OpaConstants.OPA_STATUS_PASS).id("policy-success").build();

    when(opaServiceClientHelper.evaluateWithCredentialsWithRetry(eq(OpaConstants.OPA_EVALUATION_TYPE_INFRASTRUCTURE),
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(OpaConstants.OPA_EVALUATION_ACTION_INFRA_ENV_RUN),
             eq(INFRA_IDENTIFIER), anyString(), eq(PRINCIPAL), eq(String.valueOf(PrincipalType.USER)),
             eq(HarnessYamlVersion.V1), any(JsonNode.class)))
        .thenReturn(successResponse);

    unifiedInfraStepOpaHelper.checkAndCallOpaForInfrastructureRuntimeContext(ambiance, infraStepOutcome, stepResponse);

    verify(sweepingOutputService, never()).resolveOptional(any(Ambiance.class), any());
    verify(opaServiceClientHelper, times(1))
        .evaluateWithCredentialsWithRetry(eq(OpaConstants.OPA_EVALUATION_TYPE_INFRASTRUCTURE), eq(ACCOUNT_ID),
            eq(ORG_ID), eq(PROJECT_ID), eq(OpaConstants.OPA_EVALUATION_ACTION_INFRA_ENV_RUN), eq(INFRA_IDENTIFIER),
            anyString(), eq(PRINCIPAL), eq(String.valueOf(PrincipalType.USER)), eq(HarnessYamlVersion.V1),
            any(JsonNode.class));
  }

  @Test
  @Owner(developers = THRISHANK)
  @Category(UnitTests.class)
  public void testOpaEvaluationSendsAmbiancePrincipalOnBothOpaCalls() throws Exception {
    Ambiance ambiance = buildAmbianceWithRbacValidation();
    InfraStepOutcome infraStepOutcome = buildInfraStepOutcome();
    StepResponse stepResponse = StepResponse.builder().status(Status.SUCCEEDED).build();

    // The step runs on an orchestration thread with no principal, so capture what each OPA call sees.
    SecurityContextBuilder.unsetCompleteContext();
    AtomicReference<Principal> principalOnShouldEvaluate = new AtomicReference<>();
    AtomicReference<Principal> principalOnEvaluate = new AtomicReference<>();

    when(opaServiceClientHelper.shouldEvaluatePolicyWithRetry(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID),
             eq(OpaConstants.OPA_EVALUATION_TYPE_INFRASTRUCTURE), eq(OpaConstants.OPA_EVALUATION_ACTION_INFRA_ENV_RUN),
             eq(HarnessYamlVersion.V1)))
        .thenAnswer(invocation -> {
          principalOnShouldEvaluate.set(SecurityContextBuilder.getPrincipal());
          return true;
        });

    when(unifiedServiceStepOpaHelper.resolveServiceVariables(any(Ambiance.class))).thenReturn(new HashMap<>());

    when(opaServiceClientHelper.evaluateWithCredentialsWithRetry(eq(OpaConstants.OPA_EVALUATION_TYPE_INFRASTRUCTURE),
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(OpaConstants.OPA_EVALUATION_ACTION_INFRA_ENV_RUN),
             eq(INFRA_IDENTIFIER), anyString(), eq(PRINCIPAL), eq(String.valueOf(PrincipalType.USER)),
             eq(HarnessYamlVersion.V1), any(JsonNode.class)))
        .thenAnswer(invocation -> {
          principalOnEvaluate.set(SecurityContextBuilder.getPrincipal());
          return OpaEvaluationResponseHolder.builder()
              .status(OpaConstants.OPA_STATUS_PASS)
              .id("policy-success")
              .build();
        });

    unifiedInfraStepOpaHelper.checkAndCallOpaForInfrastructureRuntimeContext(ambiance, infraStepOutcome, stepResponse);

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
    InfraStepOutcome infraStepOutcome = buildInfraStepOutcome();
    StepResponse stepResponse = StepResponse.builder().status(Status.SUCCEEDED).build();

    Principal priorPrincipal = new ServicePrincipal("ci-manager");
    SecurityContextBuilder.setContext(priorPrincipal);
    SourcePrincipalContextBuilder.setSourcePrincipal(priorPrincipal);

    when(opaServiceClientHelper.shouldEvaluatePolicyWithRetry(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID),
             eq(OpaConstants.OPA_EVALUATION_TYPE_INFRASTRUCTURE), eq(OpaConstants.OPA_EVALUATION_ACTION_INFRA_ENV_RUN),
             eq(HarnessYamlVersion.V1)))
        .thenReturn(true);

    when(unifiedServiceStepOpaHelper.resolveServiceVariables(any(Ambiance.class))).thenReturn(new HashMap<>());

    when(opaServiceClientHelper.evaluateWithCredentialsWithRetry(eq(OpaConstants.OPA_EVALUATION_TYPE_INFRASTRUCTURE),
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(OpaConstants.OPA_EVALUATION_ACTION_INFRA_ENV_RUN),
             eq(INFRA_IDENTIFIER), anyString(), eq(PRINCIPAL), eq(String.valueOf(PrincipalType.USER)),
             eq(HarnessYamlVersion.V1), any(JsonNode.class)))
        .thenReturn(
            OpaEvaluationResponseHolder.builder().status(OpaConstants.OPA_STATUS_PASS).id("policy-success").build());

    unifiedInfraStepOpaHelper.checkAndCallOpaForInfrastructureRuntimeContext(ambiance, infraStepOutcome, stepResponse);

    assertThat(SecurityContextBuilder.getPrincipal()).isSameAs(priorPrincipal);
    assertThat(SourcePrincipalContextBuilder.getSourcePrincipal()).isSameAs(priorPrincipal);
  }

  @Test
  @Owner(developers = THRISHANK)
  @Category(UnitTests.class)
  public void testOpaEvaluationRestoresEmptyContextWhenPolicyEvaluationFails() throws Exception {
    Ambiance ambiance = buildAmbianceWithRbacValidation();
    InfraStepOutcome infraStepOutcome = buildInfraStepOutcome();
    StepResponse stepResponse = StepResponse.builder().status(Status.SUCCEEDED).build();

    SecurityContextBuilder.unsetCompleteContext();
    assertThat(SecurityContextBuilder.getPrincipal()).isNull();

    when(opaServiceClientHelper.shouldEvaluatePolicyWithRetry(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID),
             eq(OpaConstants.OPA_EVALUATION_TYPE_INFRASTRUCTURE), eq(OpaConstants.OPA_EVALUATION_ACTION_INFRA_ENV_RUN),
             eq(HarnessYamlVersion.V1)))
        .thenReturn(true);

    when(unifiedServiceStepOpaHelper.resolveServiceVariables(any(Ambiance.class))).thenReturn(new HashMap<>());

    OpaPolicyEvaluationResponse policyEvalResponse =
        OpaPolicyEvaluationResponse.builder()
            .status(OpaConstants.OPA_STATUS_ERROR)
            .policy(PolicyData.builder().identifier("infra-policy").name("Infra Policy").build())
            .deny_messages(List.of("Infrastructure must use approved cluster"))
            .build();
    OpaEvaluationResponseHolder errorResponse = OpaEvaluationResponseHolder.builder()
                                                    .status(OpaConstants.OPA_STATUS_ERROR)
                                                    .id("policy-violation")
                                                    .details(List.of(OpaPolicySetEvaluationResponse.builder()
                                                                         .status(OpaConstants.OPA_STATUS_ERROR)
                                                                         .identifier("infra-policy-set")
                                                                         .name("Infra Policy Set")
                                                                         .details(List.of(policyEvalResponse))
                                                                         .build()))
                                                    .build();

    AtomicReference<Principal> principalOnEvaluate = new AtomicReference<>();
    when(opaServiceClientHelper.evaluateWithCredentialsWithRetry(eq(OpaConstants.OPA_EVALUATION_TYPE_INFRASTRUCTURE),
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(OpaConstants.OPA_EVALUATION_ACTION_INFRA_ENV_RUN),
             eq(INFRA_IDENTIFIER), anyString(), eq(PRINCIPAL), eq(String.valueOf(PrincipalType.USER)),
             eq(HarnessYamlVersion.V1), any(JsonNode.class)))
        .thenAnswer(invocation -> {
          principalOnEvaluate.set(SecurityContextBuilder.getPrincipal());
          return errorResponse;
        });

    assertThatThrownBy(()
                           -> unifiedInfraStepOpaHelper.checkAndCallOpaForInfrastructureRuntimeContext(
                               ambiance, infraStepOutcome, stepResponse))
        .isInstanceOf(PolicyEvaluationFailureException.class);

    assertThat(principalOnEvaluate.get()).isInstanceOf(UserPrincipal.class);
    // The guard must not leak the execution principal onto the thread once the policy failure unwinds.
    assertThat(SecurityContextBuilder.getPrincipal()).isNull();
    assertThat(SourcePrincipalContextBuilder.getSourcePrincipal()).isNull();
  }
}
