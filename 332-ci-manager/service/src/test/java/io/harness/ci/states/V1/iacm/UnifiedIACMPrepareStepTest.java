/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.states.V1.iacm;

import static io.harness.rule.OwnerRule.NGONZALEZ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.entities.IACMUnifiedExecutionRequest;
import io.harness.beans.entities.IACMUnifiedExecutionResponse;
import io.harness.category.element.UnitTests;
import io.harness.ci.commonconstants.IACMExecutionConstants;
import io.harness.data.structure.UUIDGenerator;
import io.harness.helper.SerializedResponseDataHelper;
import io.harness.iacm.execution.IACMUnifiedUtils;
import io.harness.iacmserviceclient.IACMServiceUtils;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.plugin.CommonAbstractStepUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.runner.request.utils.RunnerSubmitTaskUtils;
import io.harness.utils.CDStepsExpressionResolver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(HarnessTeam.IACM)
public class UnifiedIACMPrepareStepTest extends CategoryTest {
  private static final String ACCOUNT_ID = "test-account";
  private static final String ORG_ID = "test-org";
  private static final String PROJECT_ID = "test-project";
  private static final String PIPELINE_ID = "test-pipeline";
  private static final String STAGE_EXECUTION_ID = "test-stage-id";
  private static final String PLAN_EXECUTION_ID = "plan-execution-id";

  @Mock private IACMServiceUtils iacmServiceUtils;
  @Mock private IACMUnifiedUtils iacmUnifiedUtils;
  @Mock private ExecutionSweepingOutputService sweepingOutputService;
  @Mock private CommonAbstractStepUtils commonAbstractStepUtils;
  @Mock private CDStepsExpressionResolver stepsExpressionResolver;
  @Mock private RunnerSubmitTaskUtils runnerSubmitTaskUtils;
  @Mock private SerializedResponseDataHelper serializedResponseDataHelper;

  @InjectMocks private UnifiedIACMPrepareStep unifiedIACMPrepareStep;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testExecuteAsyncPassesPlaybooksAndInventories() {
    List<String> playbooks = Arrays.asList("playbook-1", "playbook-2");
    List<String> inventories = Arrays.asList("inventory-1");

    UnifiedIACMPrepareParameters stepParameters = UnifiedIACMPrepareParameters.builder()
                                                      .playbooks(ParameterField.createValueField(playbooks))
                                                      .inventories(ParameterField.createValueField(inventories))
                                                      .build();

    IACMUnifiedExecutionResponse mockResponse = IACMUnifiedExecutionResponse.builder()
                                                    .outputs(new HashMap<>())
                                                    .envVariables(new HashMap<>())
                                                    .steps(new ArrayList<>())
                                                    .build();

    when(iacmServiceUtils.createIACMUnifiedExecution(
             any(Ambiance.class), anyBoolean(), isNull(), isNull(), isNull(), isNull(), any(), any()))
        .thenReturn(mockResponse);

    Ambiance ambiance = buildAmbiance();
    AsyncExecutableResponse response =
        unifiedIACMPrepareStep.executeAsyncAfterRbac(ambiance, stepParameters, StepInputPackage.builder().build());

    verify(iacmServiceUtils)
        .createIACMUnifiedExecution(
            eq(ambiance), eq(false), isNull(), isNull(), isNull(), isNull(), eq(playbooks), eq(inventories));
    assertThat(response).isNotNull();
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testExecuteAsyncWithWorkspaceAndNoPlaybooks() {
    UnifiedIACMPrepareParameters stepParameters =
        UnifiedIACMPrepareParameters.builder().workspaceId(ParameterField.createValueField("ws-1")).build();

    IACMUnifiedExecutionResponse mockResponse = IACMUnifiedExecutionResponse.builder()
                                                    .outputs(new HashMap<>())
                                                    .envVariables(new HashMap<>())
                                                    .steps(new ArrayList<>())
                                                    .build();

    when(iacmServiceUtils.createIACMUnifiedExecution(
             any(Ambiance.class), anyBoolean(), anyString(), isNull(), isNull(), isNull(), isNull(), isNull()))
        .thenReturn(mockResponse);

    Ambiance ambiance = buildAmbiance();
    AsyncExecutableResponse response =
        unifiedIACMPrepareStep.executeAsyncAfterRbac(ambiance, stepParameters, StepInputPackage.builder().build());

    verify(iacmServiceUtils)
        .createIACMUnifiedExecution(
            eq(ambiance), eq(false), eq("ws-1"), isNull(), isNull(), isNull(), isNull(), isNull());
    assertThat(response).isNotNull();
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testExecuteAsyncWithWebhookInfo() {
    List<String> playbooks = Arrays.asList("playbook-1");
    List<String> inventories = Arrays.asList("inventory-1");

    UnifiedIACMPrepareParameters stepParameters = UnifiedIACMPrepareParameters.builder()
                                                      .playbooks(ParameterField.createValueField(playbooks))
                                                      .inventories(ParameterField.createValueField(inventories))
                                                      .webhookEventType("push")
                                                      .webhookConnector("connector-1")
                                                      .webhookRepo("org/repo")
                                                      .webhookLink("https://github.com/org/repo/commit/abc")
                                                      .build();

    IACMUnifiedExecutionResponse mockResponse = IACMUnifiedExecutionResponse.builder()
                                                    .outputs(new HashMap<>())
                                                    .envVariables(new HashMap<>())
                                                    .steps(new ArrayList<>())
                                                    .build();

    when(iacmServiceUtils.createIACMUnifiedExecution(any(Ambiance.class), anyBoolean(), isNull(),
             any(IACMUnifiedExecutionRequest.WebhookInfo.class), isNull(), isNull(), any(), any()))
        .thenReturn(mockResponse);

    Ambiance ambiance = buildAmbiance();
    AsyncExecutableResponse response =
        unifiedIACMPrepareStep.executeAsyncAfterRbac(ambiance, stepParameters, StepInputPackage.builder().build());

    ArgumentCaptor<IACMUnifiedExecutionRequest.WebhookInfo> webhookCaptor =
        ArgumentCaptor.forClass(IACMUnifiedExecutionRequest.WebhookInfo.class);
    verify(iacmServiceUtils)
        .createIACMUnifiedExecution(eq(ambiance), eq(false), isNull(), webhookCaptor.capture(), isNull(), isNull(),
            eq(playbooks), eq(inventories));

    IACMUnifiedExecutionRequest.WebhookInfo capturedWebhook = webhookCaptor.getValue();
    assertThat(capturedWebhook.getType()).isEqualTo("push");
    assertThat(capturedWebhook.getConnector()).isEqualTo("connector-1");
    assertThat(capturedWebhook.getRepo()).isEqualTo("org/repo");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testGetStepParametersClass() {
    assertThat(unifiedIACMPrepareStep.getStepParametersClass()).isEqualTo(UnifiedIACMPrepareParameters.class);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testAnsibleFlowEnrichesEndpointVariables() {
    List<String> playbooks = Arrays.asList("playbook-1");
    List<String> inventories = Arrays.asList("inventory-1");

    UnifiedIACMPrepareParameters stepParameters = UnifiedIACMPrepareParameters.builder()
                                                      .playbooks(ParameterField.createValueField(playbooks))
                                                      .inventories(ParameterField.createValueField(inventories))
                                                      .build();

    Map<String, String> serverEnvVars = new HashMap<>();
    serverEnvVars.put("ANSIBLE_PLAYBOOK", "playbook-1");
    serverEnvVars.put("ANSIBLE_INVENTORY", "inventory-1");
    serverEnvVars.put("PLUGIN_ROOT_DIR", "/harness/");

    IACMUnifiedExecutionResponse mockResponse = IACMUnifiedExecutionResponse.builder()
                                                    .outputs(new HashMap<>())
                                                    .envVariables(serverEnvVars)
                                                    .steps(new ArrayList<>())
                                                    .build();

    when(iacmServiceUtils.createIACMUnifiedExecution(
             any(Ambiance.class), anyBoolean(), isNull(), isNull(), isNull(), isNull(), any(), any()))
        .thenReturn(mockResponse);
    when(iacmServiceUtils.getIacmServiceUrl(ACCOUNT_ID)).thenReturn("https://iacm.harness.io");
    when(iacmServiceUtils.generateJWTTokenWithCache(ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn("mock-jwt-token");
    when(iacmServiceUtils.getGGProviderDomain(ACCOUNT_ID)).thenReturn("app.harness.io");

    Ambiance ambiance = buildAmbiance();
    unifiedIACMPrepareStep.executeAsyncAfterRbac(ambiance, stepParameters, StepInputPackage.builder().build());

    ArgumentCaptor<IACMRelatedEnvVars> envVarsCaptor = ArgumentCaptor.forClass(IACMRelatedEnvVars.class);
    verify(sweepingOutputService)
        .consume(eq(ambiance), eq(IACMExecutionConstants.IACM_RELATED_ENV_VARS), envVarsCaptor.capture(), anyString());

    Map<String, String> capturedEnvVars = envVarsCaptor.getValue();
    assertThat(capturedEnvVars.get(IACMExecutionConstants.PLUGIN_ENDPOINT_VARIABLES)).isNotNull();
    assertThat(capturedEnvVars.get(IACMExecutionConstants.PLUGIN_ENDPOINT_VARIABLES)).contains("\"base_url\"");
    assertThat(capturedEnvVars.get(IACMExecutionConstants.PLUGIN_ENDPOINT_VARIABLES))
        .contains("https://iacm.harness.io");
    assertThat(capturedEnvVars.get(IACMExecutionConstants.PLUGIN_ENDPOINT_VARIABLES)).contains("\"org_id\"");
    assertThat(capturedEnvVars.get(IACMExecutionConstants.PLUGIN_ENDPOINT_VARIABLES)).contains(ORG_ID);
    assertThat(capturedEnvVars.get(IACMExecutionConstants.PLUGIN_ENDPOINT_VARIABLES)).contains("\"project_id\"");
    assertThat(capturedEnvVars.get(IACMExecutionConstants.PLUGIN_ENDPOINT_VARIABLES)).contains(PROJECT_ID);
    assertThat(capturedEnvVars.get(IACMExecutionConstants.PLUGIN_ENDPOINT_VARIABLES)).contains("mock-jwt-token");
    assertThat(capturedEnvVars.get(IACMExecutionConstants.HARNESS_IACM_SERVICE_ENDPOINT))
        .isEqualTo("https://iacm.harness.io");
    assertThat(capturedEnvVars.get(IACMExecutionConstants.HARNESS_IACM_SERVICE_TOKEN)).isEqualTo("mock-jwt-token");
    assertThat(capturedEnvVars.get(IACMExecutionConstants.PLUGIN_STAGE_EXECUTION_ID)).isEqualTo(STAGE_EXECUTION_ID);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testEndpointVariablesNotOverwrittenWhenPresent() {
    String existingEndpointVars = "{\"base_url\":\"https://existing.io\",\"org_id\":\"existing-org\"}";

    Map<String, String> serverEnvVars = new HashMap<>();
    serverEnvVars.put("ANSIBLE_PLAYBOOK", "playbook-1");
    serverEnvVars.put(IACMExecutionConstants.PLUGIN_ENDPOINT_VARIABLES, existingEndpointVars);

    IACMUnifiedExecutionResponse mockResponse = IACMUnifiedExecutionResponse.builder()
                                                    .outputs(new HashMap<>())
                                                    .envVariables(serverEnvVars)
                                                    .steps(new ArrayList<>())
                                                    .build();

    UnifiedIACMPrepareParameters stepParameters = UnifiedIACMPrepareParameters.builder()
                                                      .playbooks(ParameterField.createValueField(List.of("playbook-1")))
                                                      .build();

    when(iacmServiceUtils.createIACMUnifiedExecution(
             any(Ambiance.class), anyBoolean(), isNull(), isNull(), isNull(), isNull(), any(), any()))
        .thenReturn(mockResponse);

    Ambiance ambiance = buildAmbiance();
    unifiedIACMPrepareStep.executeAsyncAfterRbac(ambiance, stepParameters, StepInputPackage.builder().build());

    ArgumentCaptor<IACMRelatedEnvVars> envVarsCaptor = ArgumentCaptor.forClass(IACMRelatedEnvVars.class);
    verify(sweepingOutputService)
        .consume(eq(ambiance), eq(IACMExecutionConstants.IACM_RELATED_ENV_VARS), envVarsCaptor.capture(), anyString());

    Map<String, String> capturedEnvVars = envVarsCaptor.getValue();
    assertThat(capturedEnvVars.get(IACMExecutionConstants.PLUGIN_ENDPOINT_VARIABLES)).isEqualTo(existingEndpointVars);
    verify(iacmServiceUtils, never()).getIacmServiceUrl(anyString());
  }

  private Ambiance buildAmbiance() {
    Level level =
        Level.newBuilder()
            .setRuntimeId(UUIDGenerator.generateUuid())
            .setSetupId("setup-id")
            .setIdentifier("iacmPrepareStep")
            .setStepType(
                StepType.newBuilder().setType("UnifiedIACMPrepareExecution").setStepCategory(StepCategory.STEP).build())
            .setRetryIndex(0)
            .build();
    return Ambiance.newBuilder()
        .putAllSetupAbstractions(Map.of("accountId", ACCOUNT_ID, "orgIdentifier", ORG_ID, "projectIdentifier",
            PROJECT_ID, "pipelineIdentifier", PIPELINE_ID))
        .addAllLevels(List.of(level))
        .setPlanExecutionId(PLAN_EXECUTION_ID)
        .setStageExecutionId(STAGE_EXECUTION_ID)
        .setMetadata(ExecutionMetadata.newBuilder().setPipelineIdentifier(PIPELINE_ID).build())
        .build();
  }
}
