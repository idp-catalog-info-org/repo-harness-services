/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.opa.step;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.MAYANK_AGARWAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.callback.DelegateCallbackToken;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.vm.VmTaskExecutionResponse;
import io.harness.delegate.beans.ci.vm.steps.VmPluginStep;
import io.harness.delegate.task.stepstatus.StepExecutionStatus;
import io.harness.delegate.task.stepstatus.StepMapOutput;
import io.harness.delegate.task.stepstatus.StepStatus;
import io.harness.delegate.task.stepstatus.StepStatusTaskResponseData;
import io.harness.helper.SerializedResponseDataHelper;
import io.harness.logging.CommandExecutionStatus;
import io.harness.opaclient.model.PolicySetData;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.plugin.ContainerPortHelper;
import io.harness.pms.sdk.core.plugin.ContainerStepExecutionResponseHelper;
import io.harness.pms.sdk.core.plugin.ContainerUnitStepUtils;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepOutcome;
import io.harness.pms.yaml.ParameterField;
import io.harness.product.ci.engine.proto.UnitStep;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.container.utils.ConnectorUtils;
import io.harness.steps.container.utils.ContainerStepImageUtils;
import io.harness.steps.opa.OPAEvaluationStepParameters;
import io.harness.tasks.ResponseData;
import io.harness.yaml.core.timeout.Timeout;

import com.google.inject.name.Named;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.apache.commons.lang3.tuple.Pair;
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

@OwnedBy(PIPELINE)
@RunWith(MockitoJUnitRunner.class)
public class OPAEvaluationStepTest extends CategoryTest {
  @Mock private Supplier<DelegateCallbackToken> delegateCallbackTokenSupplier;
  @Mock private ContainerStepExecutionResponseHelper containerStepExecutionResponseHelper;
  @Mock private ConnectorUtils connectorUtils;
  @Mock private ContainerStepImageUtils containerStepImageUtils;
  @Mock private OPAEvaluationStepHelper opaEvaluationStepHelper;
  @Mock private ContainerPortHelper containerPortHelper;
  @Mock private SerializedResponseDataHelper serializedResponseDataHelper;
  @Mock @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Mock private io.harness.metrics.service.api.MetricService metricService;

  @InjectMocks private OPAEvaluationStep opaEvaluationStep;

  private static final String ACCOUNT_ID = "account-id";
  private static final String ORG_ID = "org-id";
  private static final String PROJECT_ID = "project-id";
  private static final String PLAN_EXECUTION_ID = "plan-execution-id";
  private static final String STEP_IDENTIFIER = "opa_eval_step";
  private static final String IMAGE = "harness/opa-evaluation-plugin:latest";
  private static final String CONNECTOR_REF = "connector.ref";
  private static final String POLICY_SET_ID = "policy-set-id";
  private static final String EVALUATION_ID = "evaluation-id";

  private Ambiance ambiance;
  private StepElementParameters stepElementParameters;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);

    ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(PLAN_EXECUTION_ID)
            .putSetupAbstractions("accountId", ACCOUNT_ID)
            .putSetupAbstractions("orgIdentifier", ORG_ID)
            .putSetupAbstractions("projectIdentifier", PROJECT_ID)
            .addLevels(
                Level.newBuilder()
                    .setStepType(io.harness.pms.contracts.steps.StepType.newBuilder().setType("STEP_GROUP").build())
                    .setIdentifier("step-group-1")
                    .build())
            .build();

    OPAEvaluationStepParameters spec = OPAEvaluationStepParameters.infoBuilder()
                                           .image(ParameterField.createValueField(IMAGE))
                                           .connectorRef(ParameterField.createValueField(CONNECTOR_REF))
                                           .policySetId(ParameterField.createValueField(POLICY_SET_ID))
                                           .evaluationId(ParameterField.createValueField(EVALUATION_ID))
                                           .build();

    stepElementParameters = StepElementParameters.builder()
                                .identifier(STEP_IDENTIFIER)
                                .name("OPA Evaluation Step")
                                .spec(spec)
                                .timeout(ParameterField.createValueField("10m"))
                                .build();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetStepParametersClass() {
    assertThat(opaEvaluationStep.getStepParametersClass()).isEqualTo(StepElementParameters.class);
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetTimeout() {
    long timeout = opaEvaluationStep.getTimeout(ambiance, stepElementParameters);
    assertThat(timeout).isEqualTo(Timeout.fromString("10m").getTimeoutInMillis());
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetPort() {
    when(containerPortHelper.getPort(any(Ambiance.class), anyString(), anyBoolean())).thenReturn(8080);

    try (MockedStatic<AmbianceUtils> ambianceUtilsMock = Mockito.mockStatic(AmbianceUtils.class)) {
      ambianceUtilsMock.when(() -> AmbianceUtils.checkIfFeatureFlagEnabled(any(Ambiance.class), anyString()))
          .thenReturn(false);
      ambianceUtilsMock.when(() -> AmbianceUtils.obtainCurrentLevel(any(Ambiance.class)))
          .thenReturn(Level.newBuilder()
                          .setStepType(io.harness.pms.contracts.steps.StepType.newBuilder().setType("STEP").build())
                          .build());
      ambianceUtilsMock.when(() -> AmbianceUtils.obtainStepGroupIdentifier(any(Ambiance.class)))
          .thenReturn("step-group-1");

      Integer port = opaEvaluationStep.getPort(ambiance, STEP_IDENTIFIER);

      assertThat(port).isEqualTo(8080);
    }
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetVmPluginStepSuccess() {
    ConnectorDetails connectorDetails = ConnectorDetails.builder().build();
    PolicySetData policySetData = PolicySetData.builder().build();

    when(connectorUtils.getConnectorDetails(any(), anyString())).thenReturn(connectorDetails);
    when(opaEvaluationStepHelper.fetchPolicySet(
             any(Ambiance.class), anyString(), nullable(String.class), nullable(String.class)))
        .thenReturn(policySetData);
    when(opaEvaluationStepHelper.getPayloadGcsSignedUrl(any(Ambiance.class), anyString())).thenReturn("gcs-url");
    when(opaEvaluationStepHelper.convertPolicySetDataToJsonString(
             any(Ambiance.class), any(PolicySetData.class), anyBoolean()))
        .thenReturn(Pair.of("{}", Collections.emptySet()));
    when(opaEvaluationStepHelper.buildEnvironmentVariables(any(Ambiance.class), anyString(), anyString(), anyString(),
             anyString(), nullable(String.class), nullable(String.class)))
        .thenReturn(new HashMap<>());
    when(containerStepImageUtils.getFullyQualifiedImageName(anyString(), any(ConnectorDetails.class)))
        .thenReturn(IMAGE);

    VmPluginStep vmPluginStep = opaEvaluationStep.getVmPluginStep(ambiance, stepElementParameters, new HashMap<>());

    assertThat(vmPluginStep).isNotNull();
    assertThat(vmPluginStep.getImage()).isEqualTo(IMAGE);
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetVmPluginStepWithEmptyPolicySetId() {
    OPAEvaluationStepParameters spec = OPAEvaluationStepParameters.infoBuilder()
                                           .image(ParameterField.createValueField(IMAGE))
                                           .connectorRef(ParameterField.createValueField(CONNECTOR_REF))
                                           .policySetId(ParameterField.createValueField(""))
                                           .build();

    StepElementParameters stepParams = StepElementParameters.builder().spec(spec).build();

    assertThatThrownBy(() -> opaEvaluationStep.getVmPluginStep(ambiance, stepParams, new HashMap<>()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Policy Set ID is required");
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetSerialisedStep() {
    ConnectorDetails connectorDetails = ConnectorDetails.builder().build();
    PolicySetData policySetData = PolicySetData.builder().build();

    when(connectorUtils.getConnectorDetails(any(), anyString())).thenReturn(connectorDetails);
    when(opaEvaluationStepHelper.fetchPolicySet(
             any(Ambiance.class), anyString(), nullable(String.class), nullable(String.class)))
        .thenReturn(policySetData);
    when(opaEvaluationStepHelper.getPayloadGcsSignedUrl(any(Ambiance.class), anyString())).thenReturn("gcs-url");
    when(opaEvaluationStepHelper.convertPolicySetDataToJsonString(
             any(Ambiance.class), any(PolicySetData.class), anyBoolean()))
        .thenReturn(Pair.of("{}", Collections.emptySet()));
    when(opaEvaluationStepHelper.buildEnvironmentVariables(any(Ambiance.class), anyString(), anyString(), anyString(),
             anyString(), nullable(String.class), nullable(String.class)))
        .thenReturn(new HashMap<>());
    when(containerStepImageUtils.getFullyQualifiedImageName(anyString(), any(ConnectorDetails.class)))
        .thenReturn(IMAGE);
    when(containerPortHelper.getPort(any(Ambiance.class), anyString(), anyBoolean())).thenReturn(8080);

    try (MockedStatic<AmbianceUtils> ambianceUtilsMock = Mockito.mockStatic(AmbianceUtils.class);
         MockedStatic<ContainerUnitStepUtils> containerUnitStepUtilsMock =
             Mockito.mockStatic(ContainerUnitStepUtils.class)) {
      ambianceUtilsMock.when(() -> AmbianceUtils.checkIfFeatureFlagEnabled(any(Ambiance.class), anyString()))
          .thenReturn(false);
      ambianceUtilsMock.when(() -> AmbianceUtils.obtainCurrentLevel(any(Ambiance.class)))
          .thenReturn(Level.newBuilder()
                          .setStepType(io.harness.pms.contracts.steps.StepType.newBuilder().setType("STEP").build())
                          .build());
      ambianceUtilsMock.when(() -> AmbianceUtils.obtainStepGroupIdentifier(any(Ambiance.class)))
          .thenReturn("step-group-1");

      UnitStep unitStep = UnitStep.newBuilder().setId("unit-step-id").build();
      containerUnitStepUtilsMock
          .when(()
                    -> ContainerUnitStepUtils.serializeStepWithStepParameters(anyInt(), anyString(), anyString(),
                        anyString(), anyLong(), anyString(), anyString(), any(), any(Ambiance.class), any(Map.class),
                        anyString(), anyList()))
          .thenReturn(unitStep);

      UnitStep result = opaEvaluationStep.getSerialisedStep(
          ambiance, stepElementParameters, ACCOUNT_ID, "log-key", 600000L, "parked-task-id");

      assertThat(result).isNotNull();
    }
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetAnyOutComeForStepWithK8sSuccess() {
    StepStatusTaskResponseData stepStatusTaskResponseData =
        StepStatusTaskResponseData.builder()
            .stepStatus(StepStatus.builder()
                            .stepExecutionStatus(StepExecutionStatus.SUCCESS)
                            .output(StepMapOutput.builder().map(Map.of("key", "value")).build())
                            .build())
            .build();

    StageInfraDetails k8StageInfra = mock(StageInfraDetails.class);
    when(k8StageInfra.getType()).thenReturn(StageInfraDetails.Type.K8);
    when(containerStepExecutionResponseHelper.getStageInfra(any(Ambiance.class))).thenReturn(k8StageInfra);
    when(containerStepExecutionResponseHelper.filterK8StepResponse(any(Map.class)))
        .thenReturn(stepStatusTaskResponseData);

    Map<String, ResponseData> responseDataMap = new HashMap<>();
    StepOutcome outcome = opaEvaluationStep.getAnyOutComeForStep(ambiance, stepElementParameters, responseDataMap);

    assertThat(outcome).isNotNull();
    assertThat(outcome.getName()).isEqualTo("output");
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetAnyOutComeForStepWithVMSuccess() {
    VmTaskExecutionResponse vmResponse = VmTaskExecutionResponse.builder()
                                             .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                             .outputVars(Map.of("key", "value"))
                                             .build();

    StageInfraDetails vmStageInfra = mock(StageInfraDetails.class);
    when(vmStageInfra.getType()).thenReturn(StageInfraDetails.Type.VM);
    when(containerStepExecutionResponseHelper.getStageInfra(any(Ambiance.class))).thenReturn(vmStageInfra);
    when(containerStepExecutionResponseHelper.filterVMStepResponse(any(Map.class))).thenReturn(vmResponse);

    Map<String, ResponseData> responseDataMap = new HashMap<>();
    StepOutcome outcome = opaEvaluationStep.getAnyOutComeForStep(ambiance, stepElementParameters, responseDataMap);

    // For VM infrastructure, getAnyOutComeForStep returns null to let ContainerStepExecutionResponseHelper
    // handle outcome creation (to avoid duplicate outcome errors)
    assertThat(outcome).isNull();
  }

  // Note: getStepType() is protected, so we test it indirectly through other methods

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testValidateResources() {
    ConnectorDetails connectorDetails = ConnectorDetails.builder().build();
    when(connectorUtils.getConnectorDetails(any(), anyString())).thenReturn(connectorDetails);

    opaEvaluationStep.validateResources(ambiance, stepElementParameters);

    // Should not throw exception
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetStepType() {
    assertThat(OPAEvaluationStep.STEP_TYPE.getType()).isEqualTo(StepSpecTypeConstants.OPA_EVALUATION);
    assertThat(OPAEvaluationStep.STEP_TYPE.getStepCategory())
        .isEqualTo(io.harness.pms.contracts.steps.StepCategory.STEP);
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetVmPluginStepSetsHarnessWorkspace() {
    ConnectorDetails connectorDetails = ConnectorDetails.builder().build();
    PolicySetData policySetData = PolicySetData.builder().build();
    String stageExecutionId = "stage-execution-id-123";

    // Update ambiance to include stageExecutionId
    Ambiance ambianceWithStageId = Ambiance.newBuilder(ambiance).setStageExecutionId(stageExecutionId).build();

    when(connectorUtils.getConnectorDetails(any(), anyString())).thenReturn(connectorDetails);
    when(opaEvaluationStepHelper.fetchPolicySet(
             any(Ambiance.class), anyString(), nullable(String.class), nullable(String.class)))
        .thenReturn(policySetData);
    when(opaEvaluationStepHelper.getPayloadGcsSignedUrl(any(Ambiance.class), anyString())).thenReturn("gcs-url");
    when(opaEvaluationStepHelper.convertPolicySetDataToJsonString(
             any(Ambiance.class), any(PolicySetData.class), anyBoolean()))
        .thenReturn(Pair.of("{}", Collections.emptySet()));
    when(opaEvaluationStepHelper.buildEnvironmentVariables(any(Ambiance.class), anyString(), anyString(), anyString(),
             anyString(), nullable(String.class), nullable(String.class)))
        .thenReturn(new HashMap<>());
    when(containerStepImageUtils.getFullyQualifiedImageName(anyString(), any(ConnectorDetails.class)))
        .thenReturn(IMAGE);

    VmPluginStep vmPluginStep =
        opaEvaluationStep.getVmPluginStep(ambianceWithStageId, stepElementParameters, new HashMap<>());

    assertThat(vmPluginStep).isNotNull();
    assertThat(vmPluginStep.getEnvVariables()).isNotNull();
    assertThat(vmPluginStep.getEnvVariables()).containsKey("HARNESS_WORKSPACE");
    assertThat(vmPluginStep.getEnvVariables().get("HARNESS_WORKSPACE"))
        .isEqualTo(String.format("/tmp/harness/%s", stageExecutionId));
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetVmPluginStepAddsPerSecretEnvVarsForOpaPluginMasking() {
    ConnectorDetails connectorDetails = ConnectorDetails.builder().build();
    PolicySetData policySetData = PolicySetData.builder().build();
    java.util.Set<String> secretRefs = new java.util.HashSet<>();
    secretRefs.add("${ngSecretManager.obtain('secret-a', 12345)}");
    secretRefs.add("${ngSecretManager.obtain('secret-b', 12345)}");

    when(connectorUtils.getConnectorDetails(any(), anyString())).thenReturn(connectorDetails);
    when(opaEvaluationStepHelper.fetchPolicySet(
             any(Ambiance.class), anyString(), nullable(String.class), nullable(String.class)))
        .thenReturn(policySetData);
    when(opaEvaluationStepHelper.getPayloadGcsSignedUrl(any(Ambiance.class), anyString())).thenReturn("gcs-url");
    when(opaEvaluationStepHelper.convertPolicySetDataToJsonString(
             any(Ambiance.class), any(PolicySetData.class), anyBoolean()))
        .thenReturn(Pair.of("{}", secretRefs));
    when(opaEvaluationStepHelper.buildEnvironmentVariables(any(Ambiance.class), anyString(), anyString(), anyString(),
             anyString(), nullable(String.class), nullable(String.class)))
        .thenReturn(new HashMap<>());
    when(containerStepImageUtils.getFullyQualifiedImageName(anyString(), any(ConnectorDetails.class)))
        .thenReturn(IMAGE);

    VmPluginStep vmPluginStep = opaEvaluationStep.getVmPluginStep(ambiance, stepElementParameters, new HashMap<>());

    Map<String, String> envVars = vmPluginStep.getEnvVariables();
    long perSecretEnvVarCount =
        envVars.entrySet()
            .stream()
            .filter(e -> e.getKey().startsWith(OPAEvaluationStepHelper.PLUGIN_OPA_SECRET_PREFIX))
            .count();
    assertThat(perSecretEnvVarCount).isEqualTo(2);
    envVars.forEach((k, v) -> {
      if (k.startsWith(OPAEvaluationStepHelper.PLUGIN_OPA_SECRET_PREFIX)) {
        assertThat(v).startsWith("${ngSecretManager.obtain('");
      }
    });

    // HARNESS_SECRETS_LIST must list all PLUGIN_OPA_SECRET_<i> env var names so the OPA plugin's
    // masker (mask.go) can find them and replace decrypted values in the rendered policy output.
    String harnessSecretsList = envVars.get(OPAEvaluationStepHelper.HARNESS_SECRETS_LIST);
    assertThat(harnessSecretsList).isNotNull();
    String[] listedNames = harnessSecretsList.split(",");
    assertThat(listedNames).hasSize(2);
    for (String name : listedNames) {
      assertThat(name).startsWith(OPAEvaluationStepHelper.PLUGIN_OPA_SECRET_PREFIX);
      assertThat(envVars).containsKey(name);
    }
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testRecordCustomerInfraEvaluationDurationSuccess() throws Exception {
    // Setup response data with StepStatusTaskResponseData
    StepStatusTaskResponseData stepStatusTaskResponseData =
        StepStatusTaskResponseData.builder()
            .stepStatus(StepStatus.builder()
                            .stepExecutionStatus(StepExecutionStatus.SUCCESS)
                            .totalTimeTakenInMillis(5000L)
                            .output(StepMapOutput.builder().map(Map.of("key", "value")).build())
                            .build())
            .build();

    Map<String, ResponseData> responseDataMap = new HashMap<>();
    responseDataMap.put("callback-id", stepStatusTaskResponseData);

    // Mock StageInfraDetails for infra type extraction
    StageInfraDetails k8StageInfra = mock(StageInfraDetails.class);
    when(k8StageInfra.getType()).thenReturn(StageInfraDetails.Type.K8);
    when(containerStepExecutionResponseHelper.getStageInfra(any(Ambiance.class))).thenReturn(k8StageInfra);
    when(containerStepExecutionResponseHelper.filterK8StepResponse(any(Map.class)))
        .thenReturn(stepStatusTaskResponseData);

    StepResponse stepResponse = StepResponse.builder().status(Status.SUCCEEDED).build();

    try (MockedStatic<AmbianceUtils> ambianceUtilsMock = Mockito.mockStatic(AmbianceUtils.class)) {
      ambianceUtilsMock.when(() -> AmbianceUtils.getAccountId(any(Ambiance.class))).thenReturn(ACCOUNT_ID);
      ambianceUtilsMock.when(() -> AmbianceUtils.getOrgIdentifier(any(Ambiance.class))).thenReturn(ORG_ID);
      ambianceUtilsMock.when(() -> AmbianceUtils.getProjectIdentifier(any(Ambiance.class))).thenReturn(PROJECT_ID);

      // Use reflection to call the private method
      java.lang.reflect.Method method =
          OPAEvaluationStep.class.getDeclaredMethod("recordCustomerInfraEvaluationDuration", Ambiance.class,
              StepElementParameters.class, Map.class, StepResponse.class);
      method.setAccessible(true);
      method.invoke(opaEvaluationStep, ambiance, stepElementParameters, responseDataMap, stepResponse);

      // Verify metric was recorded (in milliseconds as per implementation)
      Mockito.verify(metricService, Mockito.times(1)).recordMetric(anyString(), Mockito.eq(5000.0));
    }
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testRecordCustomerInfraEvaluationDurationSkipsWhenNoExecutionTime() throws Exception {
    // Setup response data without execution time
    StepStatusTaskResponseData stepStatusTaskResponseData =
        StepStatusTaskResponseData.builder()
            .stepStatus(StepStatus.builder()
                            .stepExecutionStatus(StepExecutionStatus.SUCCESS)
                            .totalTimeTakenInMillis(0L) // No execution time
                            .output(StepMapOutput.builder().map(Map.of("key", "value")).build())
                            .build())
            .build();

    Map<String, ResponseData> responseDataMap = new HashMap<>();
    responseDataMap.put("callback-id", stepStatusTaskResponseData);

    when(containerStepExecutionResponseHelper.filterK8StepResponse(any(Map.class)))
        .thenReturn(stepStatusTaskResponseData);

    StepResponse stepResponse = StepResponse.builder().status(Status.SUCCEEDED).build();

    try (MockedStatic<AmbianceUtils> ambianceUtilsMock = Mockito.mockStatic(AmbianceUtils.class)) {
      ambianceUtilsMock.when(() -> AmbianceUtils.getAccountId(any(Ambiance.class))).thenReturn(ACCOUNT_ID);

      // Use reflection to call the private method
      java.lang.reflect.Method method =
          OPAEvaluationStep.class.getDeclaredMethod("recordCustomerInfraEvaluationDuration", Ambiance.class,
              StepElementParameters.class, Map.class, StepResponse.class);
      method.setAccessible(true);
      method.invoke(opaEvaluationStep, ambiance, stepElementParameters, responseDataMap, stepResponse);

      // Verify no metric was recorded (execution time is 0)
      Mockito.verify(metricService, Mockito.never()).recordMetric(anyString(), anyDouble());
    }
  }
}
