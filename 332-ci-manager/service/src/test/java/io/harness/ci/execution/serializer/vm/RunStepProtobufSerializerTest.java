/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.serializer.vm;

import static io.harness.beans.steps.stepinfo.RunStepInfo.RunStepInfoBuilder;
import static io.harness.ci.execution.serializer.SerializerUtils.AUTO_INJECTION_BINARIES_K8;
import static io.harness.rule.OwnerRule.DEV_MITTAL;
import static io.harness.rule.OwnerRule.DHIRAJ;
import static io.harness.rule.OwnerRule.SAHITHI;
import static io.harness.rule.OwnerRule.SOUMYAJIT;
import static io.harness.steps.StepUtils.PIE_SIMPLIFY_LOG_BASE_KEY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.steps.stepinfo.RunStepInfo;
import io.harness.beans.sweepingoutputs.StageDetails;
import io.harness.beans.yaml.extended.CIShellType;
import io.harness.beans.yaml.extended.buildIntelligence.BuildIntelligence;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.beans.yaml.extended.reports.UnitTestReport;
import io.harness.callback.DelegateCallbackToken;
import io.harness.category.element.UnitTests;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.execution.serializer.RunStepProtobufSerializer;
import io.harness.ci.execution.serializer.SerializerUtils;
import io.harness.ci.execution.utils.ci.CIInitStripStageVarHelper;
import io.harness.ci.execution.workloadidentity.WorkloadIdentitySerializerHelper;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.ci.utils.CISweepingOutputEvaluator;
import io.harness.encryption.SecretRefHelper;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.yaml.ParameterField;
import io.harness.product.ci.engine.proto.OutputVariable;
import io.harness.product.ci.engine.proto.ShellType;
import io.harness.product.ci.engine.proto.UnitStep;
import io.harness.rule.Owner;
import io.harness.yaml.core.timeout.Timeout;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.core.variables.NGVariableType;
import io.harness.yaml.core.variables.SecretNGVariable;
import io.harness.yaml.core.variables.StringNGVariable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(HarnessTeam.CI)
public class RunStepProtobufSerializerTest {
  @InjectMocks private RunStepProtobufSerializer runStepProtobufSerializer;

  @Mock private Supplier<DelegateCallbackToken> delegateCallbackTokenSupplier;
  @Mock private CIFeatureFlagService featureFlagService;
  @Mock CIExecutionServiceConfig ciExecutionServiceConfig;
  @Mock private SerializerUtils serializerUtils;
  @Mock private CISweepingOutputEvaluator ciSweepingOutputEvaluator;
  @Mock private CIInitStripStageVarHelper ciInitStripStageVarHelper;
  @Mock private WorkloadIdentitySerializerHelper workloadIdentitySerializerHelper;

  private Ambiance ambiance;
  private RunStepInfoBuilder stepInfo;
  private final String callbackId = UUID.randomUUID().toString();
  public static final String STEP_ID = "runStepId";
  private StageDetails stageDetails;

  @Before
  public void setUp() {
    HashMap<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, "accountId");
    setupAbstractions.put(SetupAbstractionKeys.projectIdentifier, "projectId");
    setupAbstractions.put(SetupAbstractionKeys.orgIdentifier, "orgId");

    ambiance = Ambiance.newBuilder()
                   .setMetadata(ExecutionMetadata.newBuilder()
                                    .setPipelineIdentifier("pipelineId")
                                    .setRunSequence(1)
                                    .putFeatureFlagToValueMap(PIE_SIMPLIFY_LOG_BASE_KEY, false)
                                    .build())
                   .putAllSetupAbstractions(setupAbstractions)
                   .addLevels(Level.newBuilder()
                                  .setRuntimeId("runtimeId")
                                  .setIdentifier("runStepId")
                                  .setOriginalIdentifier("runStepId")
                                  .setRetryIndex(1)
                                  .build())
                   .build();
    lenient()
        .when(workloadIdentitySerializerHelper.buildProtoWorkloadIdentities(any(), any(), any(), anyLong()))
        .thenReturn(java.util.Collections.emptyList());
    stepInfo = RunStepInfo.builder()
                   .connectorRef(ParameterField.<String>builder().value("docker").build())
                   .identifier(STEP_ID)
                   .command(ParameterField.<String>builder().value("ls").build())
                   .image(ParameterField.<String>builder().value("alpine").build())
                   .reports(ParameterField.<UnitTestReport>builder().build());
    when(serializerUtils.checkAndGetFullyQualifiedName(any(), any(), any(), any(), anyBoolean(), eq(false)))
        .thenReturn("alpine");
    stageDetails =
        StageDetails.builder()
            .buildIntelligence(
                BuildIntelligence.builder().enabled(ParameterField.<Boolean>builder().value(true).build()).build())
            .build();
  }

  @Test
  @Owner(developers = SAHITHI)
  @Category(UnitTests.class)
  public void testRunStepPhotoBuffer() {
    NGVariable outputVariableWithoutType =
        StringNGVariable.builder()
            .name("variableWithoutType")
            .type(NGVariableType.STRING)
            .value(ParameterField.<String>builder().value("variableWithoutType").build())
            .build();
    NGVariable outputVariableWithTypeString =
        StringNGVariable.builder()
            .name("variableWithTypeString")
            .type(NGVariableType.STRING)
            .value(ParameterField.<String>builder().value("variableWithTypeString").build())
            .build();

    NGVariable outputVariableWithTypeSecret =
        SecretNGVariable.builder()
            .name("variableWithTypeSecret")
            .type(NGVariableType.SECRET)
            .value(ParameterField.createValueField(SecretRefHelper.createSecretRef("variableWithTypeSecret")))
            .build();
    List<NGVariable> ngVariableList = new ArrayList<>();
    ngVariableList.add(outputVariableWithoutType);
    ngVariableList.add(outputVariableWithTypeString);
    ngVariableList.add(outputVariableWithTypeSecret);

    stepInfo.outputVariables(ParameterField.createValueField(ngVariableList));

    when(delegateCallbackTokenSupplier.get()).thenReturn(DelegateCallbackToken.newBuilder().build());
    when(featureFlagService.isEnabled(FeatureName.CI_ENABLE_BUILD_CACHE_K8, "accountId")).thenReturn(true);
    Map<String, String> envMap = new HashMap<>();
    envMap.put("PLUGIN_CACHE_SERVICE_BEARER_TOKEN", "token");
    envMap.put("HARNESS_CACHE_SERVICE_BASE_URL", "url");
    envMap.put("PLUGIN_ACCOUNT_ID", "accountId");
    when(serializerUtils.getEnvVarsForBuildIntelligence(any(), any())).thenReturn(envMap);
    when(serializerUtils.injectAutoInjectionBinariesToCommand(any())).thenReturn("autoinjectionCommand");

    UnitStep unitStep = runStepProtobufSerializer.serializeStepWithStepParameters(stepInfo.build(), 123456, callbackId,
        "logKey", STEP_ID, ParameterField.<Timeout>builder().build(), "accountId", "stepName", ambiance, "podName",
        stageDetails, OSType.Linux);

    OutputVariable protoOutputVariableWithoutType =
        OutputVariable.newBuilder().setValue("variableWithoutType").setKey("variableWithoutType").build();
    OutputVariable protoOutputVariableWithTypeString =
        OutputVariable.newBuilder().setValue("variableWithTypeString").setKey("variableWithTypeString").build();
    OutputVariable protoOutputVariableWithTypeSecret = OutputVariable.newBuilder()
                                                           .setType(OutputVariable.OutputType.SECRET)
                                                           .setValue("variableWithTypeSecret")
                                                           .setKey("variableWithTypeSecret")
                                                           .build();

    List<OutputVariable> outputVariables = new ArrayList<>();
    outputVariables.add(protoOutputVariableWithoutType);
    outputVariables.add(protoOutputVariableWithTypeString);
    outputVariables.add(protoOutputVariableWithTypeSecret);

    List<String> output = new ArrayList<>();
    output.add("variableWithoutType");
    output.add("variableWithTypeString");
    output.add("variableWithTypeSecret");

    assertThat(unitStep.getRun().getOutputsList()).isEqualTo(outputVariables);
    assertThat(unitStep.getRun().getEnvVarOutputsList()).isEqualTo(output);
    assertThat(unitStep.getRun().getCommand())
        .isEqualTo("set +x\n"
            + "if [ -x \"$(command -v git)\" ]; then\n"
            + "  git config --global --add safe.directory '*' || true \n"
            + "fi\n"
            + "autoinjectionCommandls");
    assertThat(unitStep.getRun().getShellType()).isEqualTo(ShellType.SH);
    Map<String, String> resultEnvmap = new HashMap<>();
    resultEnvmap = unitStep.getRun().getEnvironmentMap();
    assertThat(resultEnvmap.get("PLUGIN_CACHE_SERVICE_BEARER_TOKEN")).isEqualTo("token");
    assertThat(resultEnvmap.get("HARNESS_CACHE_SERVICE_BASE_URL")).isEqualTo("url");
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testRunStepProtoBuffer() {
    stepInfo.command(null);
    when(delegateCallbackTokenSupplier.get()).thenReturn(DelegateCallbackToken.newBuilder().build());
    UnitStep unitStep = runStepProtobufSerializer.serializeStepWithStepParameters(stepInfo.build(), 123456, callbackId,
        "logKey", STEP_ID, ParameterField.<Timeout>builder().build(), "accountId", "stepName", ambiance, "podName",
        stageDetails, OSType.Linux);
    assertThat(unitStep.getRun().getImage()).isEqualTo("alpine");
    assertThat(unitStep.getRun().getCommand()).isEqualTo("");
  }

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void testRunStepPhotoBufferBuildCacheK8PWSH() {
    NGVariable outputVariableWithoutType =
        StringNGVariable.builder()
            .name("variableWithoutType")
            .type(NGVariableType.STRING)
            .value(ParameterField.<String>builder().value("variableWithoutType").build())
            .build();
    NGVariable outputVariableWithTypeString =
        StringNGVariable.builder()
            .name("variableWithTypeString")
            .type(NGVariableType.STRING)
            .value(ParameterField.<String>builder().value("variableWithTypeString").build())
            .build();

    NGVariable outputVariableWithTypeSecret =
        SecretNGVariable.builder()
            .name("variableWithTypeSecret")
            .type(NGVariableType.SECRET)
            .value(ParameterField.createValueField(SecretRefHelper.createSecretRef("variableWithTypeSecret")))
            .build();
    List<NGVariable> ngVariableList = new ArrayList<>();
    ngVariableList.add(outputVariableWithoutType);
    ngVariableList.add(outputVariableWithTypeString);
    ngVariableList.add(outputVariableWithTypeSecret);

    stepInfo.outputVariables(ParameterField.createValueField(ngVariableList));
    stepInfo.shell(ParameterField.createValueField(CIShellType.PWSH));

    when(delegateCallbackTokenSupplier.get()).thenReturn(DelegateCallbackToken.newBuilder().build());
    when(featureFlagService.isEnabled(FeatureName.CI_ENABLE_BUILD_CACHE_K8, "accountId")).thenReturn(true);
    Map<String, String> envMap = new HashMap<>();
    envMap.put("PLUGIN_CACHE_SERVICE_BEARER_TOKEN", "token");
    envMap.put("HARNESS_CACHE_SERVICE_BASE_URL", "url");
    envMap.put("PLUGIN_ACCOUNT_ID", "accountId");
    when(serializerUtils.getEnvVarsForBuildIntelligence(any(), any())).thenReturn(envMap);
    when(serializerUtils.injectAutoInjectionBinariesToCommand(any()))
        .thenReturn("\n    " + AUTO_INJECTION_BINARIES_K8 + " | Out-Null\n");

    UnitStep unitStep = runStepProtobufSerializer.serializeStepWithStepParameters(stepInfo.build(), 123456, callbackId,
        "logKey", STEP_ID, ParameterField.<Timeout>builder().build(), "accountId", "stepName", ambiance, "podName",
        stageDetails, OSType.Linux);

    OutputVariable protoOutputVariableWithoutType =
        OutputVariable.newBuilder().setValue("variableWithoutType").setKey("variableWithoutType").build();
    OutputVariable protoOutputVariableWithTypeString =
        OutputVariable.newBuilder().setValue("variableWithTypeString").setKey("variableWithTypeString").build();
    OutputVariable protoOutputVariableWithTypeSecret = OutputVariable.newBuilder()
                                                           .setType(OutputVariable.OutputType.SECRET)
                                                           .setValue("variableWithTypeSecret")
                                                           .setKey("variableWithTypeSecret")
                                                           .build();

    List<OutputVariable> outputVariables = new ArrayList<>();
    outputVariables.add(protoOutputVariableWithoutType);
    outputVariables.add(protoOutputVariableWithTypeString);
    outputVariables.add(protoOutputVariableWithTypeSecret);

    List<String> output = new ArrayList<>();
    output.add("variableWithoutType");
    output.add("variableWithTypeString");
    output.add("variableWithTypeSecret");

    assertThat(unitStep.getRun().getOutputsList()).isEqualTo(outputVariables);
    assertThat(unitStep.getRun().getEnvVarOutputsList()).isEqualTo(output);
    assertThat(unitStep.getRun().getCommand())
        .isEqualTo("try\n"
            + "{\n"
            + "\n"
            + "    /addon/bin/auto-injection | Out-Null\n"
            + "    git config --global --add safe.directory '*' | Out-Null\n"
            + "}\n"
            + "catch [System.Management.Automation.CommandNotFoundException]\n"
            + "{\n"
            + " }\n"
            + "ls");
    assertThat(unitStep.getRun().getShellType()).isEqualTo(ShellType.PWSH);
    Map<String, String> resultEnvmap = new HashMap<>();
    resultEnvmap = unitStep.getRun().getEnvironmentMap();
    assertThat(resultEnvmap.get("PLUGIN_CACHE_SERVICE_BEARER_TOKEN")).isEqualTo("token");
    assertThat(resultEnvmap.get("HARNESS_CACHE_SERVICE_BASE_URL")).isEqualTo("url");
  }

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void testRunStepPhotoBufferBuildCacheK8Python() {
    NGVariable outputVariableWithoutType =
        StringNGVariable.builder()
            .name("variableWithoutType")
            .type(NGVariableType.STRING)
            .value(ParameterField.<String>builder().value("variableWithoutType").build())
            .build();
    NGVariable outputVariableWithTypeString =
        StringNGVariable.builder()
            .name("variableWithTypeString")
            .type(NGVariableType.STRING)
            .value(ParameterField.<String>builder().value("variableWithTypeString").build())
            .build();

    NGVariable outputVariableWithTypeSecret =
        SecretNGVariable.builder()
            .name("variableWithTypeSecret")
            .type(NGVariableType.SECRET)
            .value(ParameterField.createValueField(SecretRefHelper.createSecretRef("variableWithTypeSecret")))
            .build();
    List<NGVariable> ngVariableList = new ArrayList<>();
    ngVariableList.add(outputVariableWithoutType);
    ngVariableList.add(outputVariableWithTypeString);
    ngVariableList.add(outputVariableWithTypeSecret);

    stepInfo.outputVariables(ParameterField.createValueField(ngVariableList));
    stepInfo.shell(ParameterField.createValueField(CIShellType.PYTHON));

    when(delegateCallbackTokenSupplier.get()).thenReturn(DelegateCallbackToken.newBuilder().build());
    when(featureFlagService.isEnabled(FeatureName.CI_ENABLE_BUILD_CACHE_K8, "accountId")).thenReturn(true);
    Map<String, String> envMap = new HashMap<>();
    envMap.put("PLUGIN_CACHE_SERVICE_BEARER_TOKEN", "token");
    envMap.put("HARNESS_CACHE_SERVICE_BASE_URL", "url");
    envMap.put("PLUGIN_ACCOUNT_ID", "accountId");
    when(serializerUtils.getEnvVarsForBuildIntelligence(any(), any())).thenReturn(envMap);
    when(serializerUtils.injectAutoInjectionBinariesToCommand(any()))
        .thenReturn("\tsubprocess.call([\"/addon/bin/auto-injection\"])\n");

    UnitStep unitStep = runStepProtobufSerializer.serializeStepWithStepParameters(stepInfo.build(), 123456, callbackId,
        "logKey", STEP_ID, ParameterField.<Timeout>builder().build(), "accountId", "stepName", ambiance, "podName",
        stageDetails, OSType.Linux);

    OutputVariable protoOutputVariableWithoutType =
        OutputVariable.newBuilder().setValue("variableWithoutType").setKey("variableWithoutType").build();
    OutputVariable protoOutputVariableWithTypeString =
        OutputVariable.newBuilder().setValue("variableWithTypeString").setKey("variableWithTypeString").build();
    OutputVariable protoOutputVariableWithTypeSecret = OutputVariable.newBuilder()
                                                           .setType(OutputVariable.OutputType.SECRET)
                                                           .setValue("variableWithTypeSecret")
                                                           .setKey("variableWithTypeSecret")
                                                           .build();

    List<OutputVariable> outputVariables = new ArrayList<>();
    outputVariables.add(protoOutputVariableWithoutType);
    outputVariables.add(protoOutputVariableWithTypeString);
    outputVariables.add(protoOutputVariableWithTypeSecret);

    List<String> output = new ArrayList<>();
    output.add("variableWithoutType");
    output.add("variableWithTypeString");
    output.add("variableWithTypeSecret");

    assertThat(unitStep.getRun().getOutputsList()).isEqualTo(outputVariables);
    assertThat(unitStep.getRun().getEnvVarOutputsList()).isEqualTo(output);
    assertThat(unitStep.getRun().getCommand())
        .isEqualTo("import subprocess\n"
            + "try:\n"
            + "\tsubprocess.call([\"/addon/bin/auto-injection\"])\n"
            + "\tsubprocess.run(['git', 'config', '--global', '--add', 'safe.directory', '*'])\n"
            + "except:\n"
            + "\tpass\n"
            + "ls");
    assertThat(unitStep.getRun().getShellType()).isEqualTo(ShellType.PYTHON);
    Map<String, String> resultEnvmap = new HashMap<>();
    resultEnvmap = unitStep.getRun().getEnvironmentMap();
    assertThat(resultEnvmap.get("PLUGIN_CACHE_SERVICE_BEARER_TOKEN")).isEqualTo("token");
    assertThat(resultEnvmap.get("HARNESS_CACHE_SERVICE_BASE_URL")).isEqualTo("url");
  }

  /**
   * Serializer e2e for stage-var opt-in: when the shared helper reports required-fields-only stripping is on (FF off
   * + stage var true, as covered by CIInitStripStageVarHelperTest), RunStepProtobufSerializer must set
   * scriptSecretsRuntime so the addon resolves stripped fields at runtime.
   */
  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void testScriptSecretsRuntimeSetWhenHelperEnablesRequiredFieldsOnlyStrip() {
    when(delegateCallbackTokenSupplier.get()).thenReturn(DelegateCallbackToken.newBuilder().build());
    when(ciInitStripStageVarHelper.isRequiredFieldsOnlyInitStripEnabled(ambiance, "accountId")).thenReturn(true);
    when(featureFlagService.isEnabled(FeatureName.CI_REMOVE_COMMAND_INIT_PARAMS, "accountId")).thenReturn(false);

    UnitStep unitStep = runStepProtobufSerializer.serializeStepWithStepParameters(stepInfo.build(), 123456, callbackId,
        "logKey", STEP_ID, ParameterField.<Timeout>builder().build(), "accountId", "stepName", ambiance, "podName",
        stageDetails, OSType.Linux);

    assertThat(unitStep.getRun().getScriptSecretsRuntime()).isTrue();
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void testScriptSecretsRuntimeUnsetWhenHelperAndV1CommandStripDisabled() {
    when(delegateCallbackTokenSupplier.get()).thenReturn(DelegateCallbackToken.newBuilder().build());
    when(ciInitStripStageVarHelper.isRequiredFieldsOnlyInitStripEnabled(ambiance, "accountId")).thenReturn(false);
    when(featureFlagService.isEnabled(FeatureName.CI_REMOVE_COMMAND_INIT_PARAMS, "accountId")).thenReturn(false);

    UnitStep unitStep = runStepProtobufSerializer.serializeStepWithStepParameters(stepInfo.build(), 123456, callbackId,
        "logKey", STEP_ID, ParameterField.<Timeout>builder().build(), "accountId", "stepName", ambiance, "podName",
        stageDetails, OSType.Linux);

    assertThat(unitStep.getRun().getScriptSecretsRuntime()).isFalse();
  }
}
