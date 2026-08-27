/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.opa.step;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.ng.core.infrastructure.InfrastructureKind.KUBERNETES_DIRECT;
import static io.harness.rule.OwnerRule.MAYANK_AGARWAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.network.SafeHttpCall;
import io.harness.opaclient.OpaServiceClient;
import io.harness.opaclient.model.EvaluationDetailsResponse;
import io.harness.opaclient.model.OpaEvaluationResponseHolder;
import io.harness.opaclient.model.OpaPolicyEvaluationResponse;
import io.harness.opaclient.model.OpaPolicySetEvaluationResponse;
import io.harness.opaclient.model.PolicyData;
import io.harness.opaclient.model.PolicySetData;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.steps.opa.OPAEvaluationAggregatorStepParameters;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import retrofit2.Call;

@OwnedBy(PIPELINE)
@RunWith(MockitoJUnitRunner.class)
public class OPAEvaluationAggregatorStepTest extends CategoryTest {
  @Mock private OpaServiceClient opaServiceClient;
  @Mock private OPAEvaluationStepHelper opaEvaluationStepHelper;
  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Mock private PmsExecutionSummaryService pmsExecutionSummaryService;
  @Mock private io.harness.metrics.service.api.MetricService metricService;
  @Mock
  private io.harness.pms.sdk.core.plugin.ContainerStepExecutionResponseHelper containerStepExecutionResponseHelper;

  @InjectMocks private OPAEvaluationAggregatorStep opaEvaluationAggregatorStep;

  private static final String ACCOUNT_ID = "account-id";
  private static final String PLAN_EXECUTION_ID = "plan-execution-id";
  private static final String EVALUATION_ID = "evaluation-id";

  private Ambiance ambiance;
  private StepBaseParameters stepBaseParameters;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);

    ambiance = Ambiance.newBuilder()
                   .setPlanExecutionId(PLAN_EXECUTION_ID)
                   .putSetupAbstractions("accountId", ACCOUNT_ID)
                   .build();

    OPAEvaluationAggregatorStepParameters spec = OPAEvaluationAggregatorStepParameters.infoBuilder()
                                                     .evaluationId(ParameterField.createValueField(EVALUATION_ID))
                                                     .build();

    stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(spec);
    when(stepBaseParameters.getIdentifier()).thenReturn("opa_aggregator_step");

    // Mock log streaming - NGLogCallback is created internally, so we just need to mock the factory
    io.harness.logstreaming.ILogStreamingStepClient logClient =
        mock(io.harness.logstreaming.ILogStreamingStepClient.class);
    when(logStreamingStepClientFactory.getLogStreamingStepClient(any(Ambiance.class))).thenReturn(logClient);
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testExecuteSyncSuccess() throws Exception {
    List<OpaPolicySetEvaluationResponse> policySets = new ArrayList<>();
    policySets.add(createPolicySetEvaluation("policy-set-1", "pass"));
    policySets.add(createPolicySetEvaluation("policy-set-2", "pass"));

    OpaEvaluationResponseHolder evaluationHolder =
        OpaEvaluationResponseHolder.builder().id(EVALUATION_ID).status("pass").details(policySets).build();

    EvaluationDetailsResponse response =
        EvaluationDetailsResponse.builder().evaluations(Collections.singletonList(evaluationHolder)).build();

    // Mock PmsExecutionSummaryService
    PipelineExecutionSummaryEntity summaryEntity = mock(PipelineExecutionSummaryEntity.class);
    when(pmsExecutionSummaryService.getPipelineExecutionSummaryWithProjections(
             anyString(), anyString(), any(java.util.Set.class)))
        .thenReturn(summaryEntity);
    when(summaryEntity.getGovernanceMetadata()).thenReturn(null);
    when(pmsExecutionSummaryService.update(anyString(), any(org.springframework.data.mongodb.core.query.Update.class)))
        .thenReturn(summaryEntity);

    // Mock StageInfraDetails for metrics (may be called even if no governance metadata)
    io.harness.beans.sweepingoutputs.StageInfraDetails stageInfraDetails =
        mock(io.harness.beans.sweepingoutputs.StageInfraDetails.class);
    when(stageInfraDetails.getType()).thenReturn(io.harness.beans.sweepingoutputs.StageInfraDetails.Type.K8);
    when(containerStepExecutionResponseHelper.getStageInfra(any(Ambiance.class))).thenReturn(stageInfraDetails);

    try (MockedStatic<SafeHttpCall> safeHttpCallMock = Mockito.mockStatic(SafeHttpCall.class);
         MockedStatic<AmbianceUtils> ambianceUtilsMock = Mockito.mockStatic(AmbianceUtils.class)) {
      Call<EvaluationDetailsResponse> call = Mockito.mock(Call.class);
      when(opaServiceClient.getEvaluationRecords(anyString(), anyString(), any(JsonNode.class))).thenReturn(call);
      safeHttpCallMock.when(() -> SafeHttpCall.executeWithErrorMessage(any(Call.class))).thenReturn(response);
      ambianceUtilsMock.when(() -> AmbianceUtils.getAccountId(any(Ambiance.class))).thenReturn(ACCOUNT_ID);

      StepResponse stepResponse = opaEvaluationAggregatorStep.executeSync(
          ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

      assertThat(stepResponse).isNotNull();
      assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    }
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testExecuteSyncWithFailedRecords() throws Exception {
    List<OpaPolicySetEvaluationResponse> policySets = new ArrayList<>();
    policySets.add(createPolicySetEvaluation("policy-set-1", "error"));
    policySets.add(createPolicySetEvaluation("policy-set-2", "pass"));

    OpaEvaluationResponseHolder evaluationHolder =
        OpaEvaluationResponseHolder.builder().id(EVALUATION_ID).status("error").details(policySets).build();

    EvaluationDetailsResponse response =
        EvaluationDetailsResponse.builder().evaluations(Collections.singletonList(evaluationHolder)).build();

    // Mock PmsExecutionSummaryService
    PipelineExecutionSummaryEntity summaryEntity = mock(PipelineExecutionSummaryEntity.class);
    when(pmsExecutionSummaryService.getPipelineExecutionSummaryWithProjections(
             anyString(), anyString(), any(java.util.Set.class)))
        .thenReturn(summaryEntity);
    when(summaryEntity.getGovernanceMetadata()).thenReturn(null);
    when(pmsExecutionSummaryService.update(anyString(), any(org.springframework.data.mongodb.core.query.Update.class)))
        .thenReturn(summaryEntity);

    // Mock StageInfraDetails for metrics (may be called even if no governance metadata)
    io.harness.beans.sweepingoutputs.StageInfraDetails stageInfraDetails =
        mock(io.harness.beans.sweepingoutputs.StageInfraDetails.class);
    when(stageInfraDetails.getType()).thenReturn(io.harness.beans.sweepingoutputs.StageInfraDetails.Type.K8);
    when(containerStepExecutionResponseHelper.getStageInfra(any(Ambiance.class))).thenReturn(stageInfraDetails);

    try (MockedStatic<SafeHttpCall> safeHttpCallMock = Mockito.mockStatic(SafeHttpCall.class);
         MockedStatic<AmbianceUtils> ambianceUtilsMock = Mockito.mockStatic(AmbianceUtils.class)) {
      Call<EvaluationDetailsResponse> call = Mockito.mock(Call.class);
      when(opaServiceClient.getEvaluationRecords(anyString(), anyString(), any(JsonNode.class))).thenReturn(call);
      safeHttpCallMock.when(() -> SafeHttpCall.executeWithErrorMessage(any(Call.class))).thenReturn(response);
      ambianceUtilsMock.when(() -> AmbianceUtils.getAccountId(any(Ambiance.class))).thenReturn(ACCOUNT_ID);

      StepResponse stepResponse = opaEvaluationAggregatorStep.executeSync(
          ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

      assertThat(stepResponse).isNotNull();
      assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
      assertThat(stepResponse.getFailureInfo()).isNotNull();
    }
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testExecuteSyncWithEmptyEvaluationId() throws Exception {
    OPAEvaluationAggregatorStepParameters spec =
        OPAEvaluationAggregatorStepParameters.infoBuilder().evaluationId(null).build();
    StepBaseParameters stepParams = mock(StepBaseParameters.class);
    when(stepParams.getSpec()).thenReturn(spec);
    when(stepParams.getIdentifier()).thenReturn("opa_aggregator_step");

    when(opaEvaluationStepHelper.fetchEvaluationIdFromPlanExecutionId(any(Ambiance.class), anyString()))
        .thenReturn(EVALUATION_ID);

    List<OpaPolicySetEvaluationResponse> policySets = new ArrayList<>();
    policySets.add(createPolicySetEvaluation("policy-set-1", "pass"));

    OpaEvaluationResponseHolder evaluationHolder =
        OpaEvaluationResponseHolder.builder().id(EVALUATION_ID).status("pass").details(policySets).build();

    EvaluationDetailsResponse response =
        EvaluationDetailsResponse.builder().evaluations(Collections.singletonList(evaluationHolder)).build();

    // Mock PmsExecutionSummaryService
    PipelineExecutionSummaryEntity summaryEntity = mock(PipelineExecutionSummaryEntity.class);
    when(pmsExecutionSummaryService.getPipelineExecutionSummaryWithProjections(
             anyString(), anyString(), any(java.util.Set.class)))
        .thenReturn(summaryEntity);
    when(summaryEntity.getGovernanceMetadata()).thenReturn(null);
    when(pmsExecutionSummaryService.update(anyString(), any(org.springframework.data.mongodb.core.query.Update.class)))
        .thenReturn(summaryEntity);

    // Mock StageInfraDetails for metrics (may be called even if no governance metadata)
    io.harness.beans.sweepingoutputs.StageInfraDetails stageInfraDetails =
        mock(io.harness.beans.sweepingoutputs.StageInfraDetails.class);
    when(stageInfraDetails.getType()).thenReturn(io.harness.beans.sweepingoutputs.StageInfraDetails.Type.K8);
    when(containerStepExecutionResponseHelper.getStageInfra(any(Ambiance.class))).thenReturn(stageInfraDetails);

    try (MockedStatic<SafeHttpCall> safeHttpCallMock = Mockito.mockStatic(SafeHttpCall.class);
         MockedStatic<AmbianceUtils> ambianceUtilsMock = Mockito.mockStatic(AmbianceUtils.class)) {
      Call<EvaluationDetailsResponse> call = Mockito.mock(Call.class);
      when(opaServiceClient.getEvaluationRecords(anyString(), anyString(), any(JsonNode.class))).thenReturn(call);
      safeHttpCallMock.when(() -> SafeHttpCall.executeWithErrorMessage(any(Call.class))).thenReturn(response);
      ambianceUtilsMock.when(() -> AmbianceUtils.getAccountId(any(Ambiance.class))).thenReturn(ACCOUNT_ID);

      StepResponse stepResponse =
          opaEvaluationAggregatorStep.executeSync(ambiance, stepParams, StepInputPackage.builder().build(), null);

      assertThat(stepResponse).isNotNull();
      assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    }
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testExecuteSyncWithNoRecords() throws Exception {
    EvaluationDetailsResponse response =
        EvaluationDetailsResponse.builder().evaluations(Collections.emptyList()).build();

    // Mock StageInfraDetails for metrics
    io.harness.beans.sweepingoutputs.StageInfraDetails stageInfraDetails =
        mock(io.harness.beans.sweepingoutputs.StageInfraDetails.class);
    when(stageInfraDetails.getType()).thenReturn(io.harness.beans.sweepingoutputs.StageInfraDetails.Type.K8);
    when(containerStepExecutionResponseHelper.getStageInfra(any(Ambiance.class))).thenReturn(stageInfraDetails);

    try (MockedStatic<SafeHttpCall> safeHttpCallMock = Mockito.mockStatic(SafeHttpCall.class);
         MockedStatic<AmbianceUtils> ambianceUtilsMock = Mockito.mockStatic(AmbianceUtils.class)) {
      Call<EvaluationDetailsResponse> call = Mockito.mock(Call.class);
      when(opaServiceClient.getEvaluationRecords(anyString(), anyString(), any(JsonNode.class))).thenReturn(call);
      safeHttpCallMock.when(() -> SafeHttpCall.executeWithErrorMessage(any(Call.class))).thenReturn(response);
      ambianceUtilsMock.when(() -> AmbianceUtils.getAccountId(any(Ambiance.class))).thenReturn(ACCOUNT_ID);

      StepResponse stepResponse = opaEvaluationAggregatorStep.executeSync(
          ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

      assertThat(stepResponse).isNotNull();
      assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    }
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testExecuteSyncWithErrorStatus() throws Exception {
    List<OpaPolicySetEvaluationResponse> policySets = new ArrayList<>();
    policySets.add(createPolicySetEvaluation("policy-set-1", "error"));

    OpaEvaluationResponseHolder evaluationHolder =
        OpaEvaluationResponseHolder.builder().id(EVALUATION_ID).status("error").details(policySets).build();

    EvaluationDetailsResponse response =
        EvaluationDetailsResponse.builder().evaluations(Collections.singletonList(evaluationHolder)).build();

    // Mock PmsExecutionSummaryService
    PipelineExecutionSummaryEntity summaryEntity = mock(PipelineExecutionSummaryEntity.class);
    when(pmsExecutionSummaryService.getPipelineExecutionSummaryWithProjections(
             anyString(), anyString(), any(java.util.Set.class)))
        .thenReturn(summaryEntity);
    when(summaryEntity.getGovernanceMetadata()).thenReturn(null);
    when(pmsExecutionSummaryService.update(anyString(), any(org.springframework.data.mongodb.core.query.Update.class)))
        .thenReturn(summaryEntity);

    // Mock StageInfraDetails for metrics (may be called even if no governance metadata)
    io.harness.beans.sweepingoutputs.StageInfraDetails stageInfraDetails =
        mock(io.harness.beans.sweepingoutputs.StageInfraDetails.class);
    when(stageInfraDetails.getType()).thenReturn(io.harness.beans.sweepingoutputs.StageInfraDetails.Type.K8);
    when(containerStepExecutionResponseHelper.getStageInfra(any(Ambiance.class))).thenReturn(stageInfraDetails);

    try (MockedStatic<SafeHttpCall> safeHttpCallMock = Mockito.mockStatic(SafeHttpCall.class);
         MockedStatic<AmbianceUtils> ambianceUtilsMock = Mockito.mockStatic(AmbianceUtils.class)) {
      Call<EvaluationDetailsResponse> call = Mockito.mock(Call.class);
      when(opaServiceClient.getEvaluationRecords(anyString(), anyString(), any(JsonNode.class))).thenReturn(call);
      safeHttpCallMock.when(() -> SafeHttpCall.executeWithErrorMessage(any(Call.class))).thenReturn(response);
      ambianceUtilsMock.when(() -> AmbianceUtils.getAccountId(any(Ambiance.class))).thenReturn(ACCOUNT_ID);

      StepResponse stepResponse = opaEvaluationAggregatorStep.executeSync(
          ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

      assertThat(stepResponse).isNotNull();
      assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    }
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testExecuteSyncWithPendingStatus() throws Exception {
    List<OpaPolicySetEvaluationResponse> policySets = new ArrayList<>();
    policySets.add(createPolicySetEvaluation("policy-set-1", "pending"));

    OpaEvaluationResponseHolder evaluationHolder =
        OpaEvaluationResponseHolder.builder().id(EVALUATION_ID).status("pending").details(policySets).build();

    EvaluationDetailsResponse response =
        EvaluationDetailsResponse.builder().evaluations(Collections.singletonList(evaluationHolder)).build();

    // Mock PmsExecutionSummaryService
    PipelineExecutionSummaryEntity summaryEntity = mock(PipelineExecutionSummaryEntity.class);
    when(pmsExecutionSummaryService.getPipelineExecutionSummaryWithProjections(
             anyString(), anyString(), any(java.util.Set.class)))
        .thenReturn(summaryEntity);
    when(summaryEntity.getGovernanceMetadata()).thenReturn(null);
    when(pmsExecutionSummaryService.update(anyString(), any(org.springframework.data.mongodb.core.query.Update.class)))
        .thenReturn(summaryEntity);

    // Mock StageInfraDetails for metrics (may be called even if no governance metadata)
    io.harness.beans.sweepingoutputs.StageInfraDetails stageInfraDetails =
        mock(io.harness.beans.sweepingoutputs.StageInfraDetails.class);
    when(stageInfraDetails.getType()).thenReturn(io.harness.beans.sweepingoutputs.StageInfraDetails.Type.K8);
    when(containerStepExecutionResponseHelper.getStageInfra(any(Ambiance.class))).thenReturn(stageInfraDetails);

    try (MockedStatic<SafeHttpCall> safeHttpCallMock = Mockito.mockStatic(SafeHttpCall.class);
         MockedStatic<AmbianceUtils> ambianceUtilsMock = Mockito.mockStatic(AmbianceUtils.class)) {
      Call<EvaluationDetailsResponse> call = Mockito.mock(Call.class);
      when(opaServiceClient.getEvaluationRecords(anyString(), anyString(), any(JsonNode.class))).thenReturn(call);
      safeHttpCallMock.when(() -> SafeHttpCall.executeWithErrorMessage(any(Call.class))).thenReturn(response);
      ambianceUtilsMock.when(() -> AmbianceUtils.getAccountId(any(Ambiance.class))).thenReturn(ACCOUNT_ID);

      StepResponse stepResponse = opaEvaluationAggregatorStep.executeSync(
          ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

      assertThat(stepResponse).isNotNull();
      assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    }
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetStepParametersClass() {
    assertThat(opaEvaluationAggregatorStep.getStepParametersClass()).isEqualTo(StepBaseParameters.class);
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetLogKeys() {
    List<String> logKeys = opaEvaluationAggregatorStep.getLogKeys(ambiance);
    assertThat(logKeys).isNotNull();
    assertThat(logKeys).isNotEmpty();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testExecuteSyncWithMergedGovernanceMetadata() throws Exception {
    List<OpaPolicySetEvaluationResponse> infraPolicySets = new ArrayList<>();
    infraPolicySets.add(createPolicySetEvaluation("infra-policy-set-1", "pass"));

    OpaEvaluationResponseHolder evaluationHolder =
        OpaEvaluationResponseHolder.builder().id(EVALUATION_ID).status("pass").details(infraPolicySets).build();

    EvaluationDetailsResponse response =
        EvaluationDetailsResponse.builder().evaluations(Collections.singletonList(evaluationHolder)).build();

    // Mock existing governance metadata with SAAS policy sets
    io.harness.governance.GovernanceMetadata existingMetadata =
        io.harness.governance.GovernanceMetadata.newBuilder()
            .addDetails(io.harness.governance.PolicySetMetadata.newBuilder()
                            .setIdentifier("saas-policy-set-1")
                            .setStatus("pass")
                            .build())
            .build();

    PipelineExecutionSummaryEntity summaryEntity = mock(PipelineExecutionSummaryEntity.class);
    when(pmsExecutionSummaryService.getPipelineExecutionSummaryWithProjections(
             anyString(), anyString(), any(java.util.Set.class)))
        .thenReturn(summaryEntity);
    when(summaryEntity.getGovernanceMetadata()).thenReturn(existingMetadata);
    when(pmsExecutionSummaryService.update(anyString(), any(org.springframework.data.mongodb.core.query.Update.class)))
        .thenReturn(summaryEntity);

    // Mock StageInfraDetails for metrics (may be called even if no governance metadata)
    io.harness.beans.sweepingoutputs.StageInfraDetails stageInfraDetails =
        mock(io.harness.beans.sweepingoutputs.StageInfraDetails.class);
    when(stageInfraDetails.getType()).thenReturn(io.harness.beans.sweepingoutputs.StageInfraDetails.Type.K8);
    when(containerStepExecutionResponseHelper.getStageInfra(any(Ambiance.class))).thenReturn(stageInfraDetails);

    try (MockedStatic<SafeHttpCall> safeHttpCallMock = Mockito.mockStatic(SafeHttpCall.class);
         MockedStatic<AmbianceUtils> ambianceUtilsMock = Mockito.mockStatic(AmbianceUtils.class)) {
      Call<EvaluationDetailsResponse> call = Mockito.mock(Call.class);
      when(opaServiceClient.getEvaluationRecords(anyString(), anyString(), any(JsonNode.class))).thenReturn(call);
      safeHttpCallMock.when(() -> SafeHttpCall.executeWithErrorMessage(any(Call.class))).thenReturn(response);
      ambianceUtilsMock.when(() -> AmbianceUtils.getAccountId(any(Ambiance.class))).thenReturn(ACCOUNT_ID);

      StepResponse stepResponse = opaEvaluationAggregatorStep.executeSync(
          ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

      assertThat(stepResponse).isNotNull();
      assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    }
  }

  private static final String ORG_ID = "org-id";
  private static final String PROJECT_ID = "project-id";

  private OpaPolicySetEvaluationResponse createPolicySetEvaluation(String identifier, String status) {
    return createPolicySetEvaluation(identifier, status, ACCOUNT_ID, null, null);
  }

  private OpaPolicySetEvaluationResponse createPolicySetEvaluation(
      String identifier, String status, String accountId, String orgId, String projectId) {
    PolicyData policyData = PolicyData.builder()
                                .identifier("policy-" + identifier)
                                .name("Policy " + identifier)
                                .severity("error")
                                .rego("package test\n\ndefault allow = false")
                                .build();

    return OpaPolicySetEvaluationResponse.builder()
        .identifier(identifier)
        .status(status)
        .name("Policy Set " + identifier)
        .account_id(accountId)
        .org_id(orgId)
        .project_id(projectId)
        .details(Collections.singletonList(OpaPolicyEvaluationResponse.builder()
                                               .status(status)
                                               .policy(policyData)
                                               .deny_messages(Collections.emptyList())
                                               .build()))
        .build();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testRecordPolicySetEvaluationMetricsWithInfraPolicySets() throws Exception {
    // Create infrastructure policy sets (with "pending" status in governance metadata)
    List<OpaPolicySetEvaluationResponse> policySets = new ArrayList<>();
    policySets.add(createPolicySetEvaluation("infra-policy-set-1", "pass", ACCOUNT_ID, ORG_ID, PROJECT_ID));
    policySets.add(createPolicySetEvaluation("infra-policy-set-2", "warning", ACCOUNT_ID, ORG_ID, PROJECT_ID));

    OpaEvaluationResponseHolder evaluationHolder = OpaEvaluationResponseHolder.builder()
                                                       .id(EVALUATION_ID)
                                                       .status("pass")
                                                       .account_id(ACCOUNT_ID)
                                                       .org_id(ORG_ID)
                                                       .project_id(PROJECT_ID)
                                                       .details(policySets)
                                                       .build();

    // Create governance metadata with "pending" status for infrastructure policy sets
    io.harness.governance.GovernanceMetadata governanceMetadata =
        io.harness.governance.GovernanceMetadata.newBuilder()
            .addDetails(io.harness.governance.PolicySetMetadata.newBuilder()
                            .setIdentifier("infra-policy-set-1")
                            .setStatus("pending")
                            .build())
            .addDetails(io.harness.governance.PolicySetMetadata.newBuilder()
                            .setIdentifier("infra-policy-set-2")
                            .setStatus("pending")
                            .build())
            .addDetails(io.harness.governance.PolicySetMetadata.newBuilder()
                            .setIdentifier("saas-policy-set-1")
                            .setStatus("pass")
                            .build())
            .build();

    PipelineExecutionSummaryEntity summaryEntity = mock(PipelineExecutionSummaryEntity.class);
    when(summaryEntity.getGovernanceMetadata()).thenReturn(governanceMetadata);
    when(pmsExecutionSummaryService.getPipelineExecutionSummaryWithProjections(
             anyString(), anyString(), any(java.util.Set.class)))
        .thenReturn(summaryEntity);

    // Mock PolicySetData with infra_type for each policy set
    PolicySetData policySetData1 = PolicySetData.builder().infra_type(KUBERNETES_DIRECT).build();
    PolicySetData policySetData2 = PolicySetData.builder().infra_type(KUBERNETES_DIRECT).build();
    when(opaEvaluationStepHelper.fetchPolicySet(
             any(Ambiance.class), eq("infra-policy-set-1"), eq(ORG_ID), eq(PROJECT_ID)))
        .thenReturn(policySetData1);
    when(opaEvaluationStepHelper.fetchPolicySet(
             any(Ambiance.class), eq("infra-policy-set-2"), eq(ORG_ID), eq(PROJECT_ID)))
        .thenReturn(policySetData2);

    try (MockedStatic<AmbianceUtils> ambianceUtilsMock = Mockito.mockStatic(AmbianceUtils.class)) {
      ambianceUtilsMock.when(() -> AmbianceUtils.getAccountId(any(Ambiance.class))).thenReturn(ACCOUNT_ID);

      // Use reflection to call the private method with updated signature
      java.lang.reflect.Method method = OPAEvaluationAggregatorStep.class.getDeclaredMethod(
          "recordMetricsForFilteredPolicySets", Ambiance.class, OpaEvaluationResponseHolder.class, Set.class);
      method.setAccessible(true);
      Set<String> infraPolicySetIdentifiers = new HashSet<>();
      infraPolicySetIdentifiers.add("infra-policy-set-1");
      infraPolicySetIdentifiers.add("infra-policy-set-2");
      method.invoke(opaEvaluationAggregatorStep, ambiance, evaluationHolder, infraPolicySetIdentifiers);

      // Verify metrics were recorded for infrastructure policy sets only (2 calls)
      Mockito.verify(metricService, Mockito.times(2)).incCounter(anyString());
    }
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testRecordPolicySetEvaluationMetricsSkipsWhenNoInfraPolicySets() throws Exception {
    // Create SAAS policy sets only (no "pending" status in governance metadata)
    List<OpaPolicySetEvaluationResponse> policySets = new ArrayList<>();
    policySets.add(createPolicySetEvaluation("saas-policy-set-1", "pass", ACCOUNT_ID, ORG_ID, PROJECT_ID));

    OpaEvaluationResponseHolder evaluationHolder = OpaEvaluationResponseHolder.builder()
                                                       .id(EVALUATION_ID)
                                                       .status("pass")
                                                       .account_id(ACCOUNT_ID)
                                                       .org_id(ORG_ID)
                                                       .project_id(PROJECT_ID)
                                                       .details(policySets)
                                                       .build();

    // Create governance metadata with only SAAS policy sets (no "pending" status)
    io.harness.governance.GovernanceMetadata governanceMetadata =
        io.harness.governance.GovernanceMetadata.newBuilder()
            .addDetails(io.harness.governance.PolicySetMetadata.newBuilder()
                            .setIdentifier("saas-policy-set-1")
                            .setStatus("pass")
                            .build())
            .build();

    PipelineExecutionSummaryEntity summaryEntity = mock(PipelineExecutionSummaryEntity.class);
    when(summaryEntity.getGovernanceMetadata()).thenReturn(governanceMetadata);
    when(pmsExecutionSummaryService.getPipelineExecutionSummaryWithProjections(
             anyString(), anyString(), any(java.util.Set.class)))
        .thenReturn(summaryEntity);

    try (MockedStatic<AmbianceUtils> ambianceUtilsMock = Mockito.mockStatic(AmbianceUtils.class)) {
      ambianceUtilsMock.when(() -> AmbianceUtils.getAccountId(any(Ambiance.class))).thenReturn(ACCOUNT_ID);

      // Use reflection to call the private method with updated signature
      java.lang.reflect.Method method = OPAEvaluationAggregatorStep.class.getDeclaredMethod(
          "recordMetricsForFilteredPolicySets", Ambiance.class, OpaEvaluationResponseHolder.class, Set.class);
      method.setAccessible(true);
      Set<String> infraPolicySetIdentifiers = new HashSet<>(); // Empty set - no infra policy sets
      method.invoke(opaEvaluationAggregatorStep, ambiance, evaluationHolder, infraPolicySetIdentifiers);

      // Verify no metrics were recorded (SAAS policy sets are skipped)
      Mockito.verify(metricService, Mockito.never()).incCounter(anyString());
    }
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testComputeOverallStatusFromAllPolicySetsWithError() throws Exception {
    // Test that ERROR has highest priority
    List<io.harness.governance.PolicySetMetadata> policySets = new ArrayList<>();
    policySets.add(
        io.harness.governance.PolicySetMetadata.newBuilder().setIdentifier("ps1").setStatus("error").build());
    policySets.add(
        io.harness.governance.PolicySetMetadata.newBuilder().setIdentifier("ps2").setStatus("warning").build());
    policySets.add(io.harness.governance.PolicySetMetadata.newBuilder().setIdentifier("ps3").setStatus("pass").build());

    java.lang.reflect.Method method =
        OPAEvaluationAggregatorStep.class.getDeclaredMethod("computeOverallStatusFromAllPolicySets", List.class);
    method.setAccessible(true);
    String result = (String) method.invoke(opaEvaluationAggregatorStep, policySets);

    assertThat(result).isEqualTo("error");
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testComputeOverallStatusFromAllPolicySetsWithWarning() throws Exception {
    // Test that WARNING has higher priority than PENDING and PASS
    List<io.harness.governance.PolicySetMetadata> policySets = new ArrayList<>();
    policySets.add(
        io.harness.governance.PolicySetMetadata.newBuilder().setIdentifier("ps1").setStatus("warning").build());
    policySets.add(
        io.harness.governance.PolicySetMetadata.newBuilder().setIdentifier("ps2").setStatus("pending").build());
    policySets.add(io.harness.governance.PolicySetMetadata.newBuilder().setIdentifier("ps3").setStatus("pass").build());

    java.lang.reflect.Method method =
        OPAEvaluationAggregatorStep.class.getDeclaredMethod("computeOverallStatusFromAllPolicySets", List.class);
    method.setAccessible(true);
    String result = (String) method.invoke(opaEvaluationAggregatorStep, policySets);

    assertThat(result).isEqualTo("warning");
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testComputeOverallStatusFromAllPolicySetsWithPending() throws Exception {
    // Test that PENDING has higher priority than PASS
    List<io.harness.governance.PolicySetMetadata> policySets = new ArrayList<>();
    policySets.add(
        io.harness.governance.PolicySetMetadata.newBuilder().setIdentifier("ps1").setStatus("pending").build());
    policySets.add(io.harness.governance.PolicySetMetadata.newBuilder().setIdentifier("ps2").setStatus("pass").build());

    java.lang.reflect.Method method =
        OPAEvaluationAggregatorStep.class.getDeclaredMethod("computeOverallStatusFromAllPolicySets", List.class);
    method.setAccessible(true);
    String result = (String) method.invoke(opaEvaluationAggregatorStep, policySets);

    assertThat(result).isEqualTo("pending");
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testComputeOverallStatusFromAllPolicySetsWithAllPass() throws Exception {
    // Test that all PASS returns PASS
    List<io.harness.governance.PolicySetMetadata> policySets = new ArrayList<>();
    policySets.add(io.harness.governance.PolicySetMetadata.newBuilder().setIdentifier("ps1").setStatus("pass").build());
    policySets.add(io.harness.governance.PolicySetMetadata.newBuilder().setIdentifier("ps2").setStatus("pass").build());

    java.lang.reflect.Method method =
        OPAEvaluationAggregatorStep.class.getDeclaredMethod("computeOverallStatusFromAllPolicySets", List.class);
    method.setAccessible(true);
    String result = (String) method.invoke(opaEvaluationAggregatorStep, policySets);

    assertThat(result).isEqualTo("pass");
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testComputeOverallStatusFromAllPolicySetsWithEmptyList() throws Exception {
    // Test that empty list returns PASS (default)
    List<io.harness.governance.PolicySetMetadata> policySets = new ArrayList<>();

    java.lang.reflect.Method method =
        OPAEvaluationAggregatorStep.class.getDeclaredMethod("computeOverallStatusFromAllPolicySets", List.class);
    method.setAccessible(true);
    String result = (String) method.invoke(opaEvaluationAggregatorStep, policySets);

    assertThat(result).isEqualTo("pass");
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testComputeOverallStatusFromAllPolicySetsWithNullAndEmptyStatus() throws Exception {
    // Test that null and empty statuses are ignored
    List<io.harness.governance.PolicySetMetadata> policySets = new ArrayList<>();
    policySets.add(io.harness.governance.PolicySetMetadata.newBuilder().setIdentifier("ps1").setStatus("").build());
    policySets.add(null);
    policySets.add(
        io.harness.governance.PolicySetMetadata.newBuilder().setIdentifier("ps2").setStatus("warning").build());

    java.lang.reflect.Method method =
        OPAEvaluationAggregatorStep.class.getDeclaredMethod("computeOverallStatusFromAllPolicySets", List.class);
    method.setAccessible(true);
    String result = (String) method.invoke(opaEvaluationAggregatorStep, policySets);

    assertThat(result).isEqualTo("warning");
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testComputeOverallStatusFromAllPolicySetsWithErrorStatus() throws Exception {
    // Test that "error" status is correctly identified
    List<io.harness.governance.PolicySetMetadata> policySets = new ArrayList<>();
    policySets.add(
        io.harness.governance.PolicySetMetadata.newBuilder().setIdentifier("ps1").setStatus("error").build());
    policySets.add(io.harness.governance.PolicySetMetadata.newBuilder().setIdentifier("ps2").setStatus("pass").build());

    java.lang.reflect.Method method =
        OPAEvaluationAggregatorStep.class.getDeclaredMethod("computeOverallStatusFromAllPolicySets", List.class);
    method.setAccessible(true);
    String result = (String) method.invoke(opaEvaluationAggregatorStep, policySets);

    assertThat(result).isEqualTo("error");
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testComputeOverallStatusFromAllPolicySetsWithMixedStatuses() throws Exception {
    // Test complex scenario with all statuses
    List<io.harness.governance.PolicySetMetadata> policySets = new ArrayList<>();
    policySets.add(io.harness.governance.PolicySetMetadata.newBuilder().setIdentifier("ps1").setStatus("pass").build());
    policySets.add(
        io.harness.governance.PolicySetMetadata.newBuilder().setIdentifier("ps2").setStatus("pending").build());
    policySets.add(
        io.harness.governance.PolicySetMetadata.newBuilder().setIdentifier("ps3").setStatus("warning").build());
    policySets.add(
        io.harness.governance.PolicySetMetadata.newBuilder().setIdentifier("ps4").setStatus("error").build());

    java.lang.reflect.Method method =
        OPAEvaluationAggregatorStep.class.getDeclaredMethod("computeOverallStatusFromAllPolicySets", List.class);
    method.setAccessible(true);
    String result = (String) method.invoke(opaEvaluationAggregatorStep, policySets);

    assertThat(result).isEqualTo("error");
  }
}
