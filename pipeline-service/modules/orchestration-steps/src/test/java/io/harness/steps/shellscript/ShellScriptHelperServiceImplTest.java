/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.shellscript;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.FERNANDOD;
import static io.harness.rule.OwnerRule.HINGER;
import static io.harness.rule.OwnerRule.NAMANG;
import static io.harness.rule.OwnerRule.SHALINI;
import static io.harness.rule.OwnerRule.VAIBHAV_SI;
import static io.harness.rule.OwnerRule.VITALIE;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.FileReference;
import io.harness.beans.common.VariablesSweepingOutput;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.k8s.DirectK8sInfraDelegateConfig;
import io.harness.delegate.task.shell.ShellScriptTaskNG;
import io.harness.delegate.task.shell.ShellScriptTaskParametersNG;
import io.harness.delegate.task.shell.ShellScriptTaskParametersNG.ShellScriptTaskParametersNGBuilder;
import io.harness.exception.GeneralException;
import io.harness.exception.InternalServerErrorException;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.EngineExpressionService;
import io.harness.filestore.remote.FileStoreClient;
import io.harness.harnessid.client.HarnessIdClientService;
import io.harness.network.SafeHttpCall;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.ngsettings.SettingValueType;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngsettings.dto.SettingValueResponseDTO;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.validation.InputSetValidatorFactory;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.SshKeySpecDTOHelper;
import io.harness.secrets.remote.SecretNGManagerClient;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.shell.ScriptType;
import io.harness.steps.OutputExpressionConstants;
import io.harness.steps.shellscript.v1.ShellTypeV1;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.utils.PmsFeatureFlagService;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;
import org.powermock.api.mockito.PowerMockito;
import retrofit2.Call;
import retrofit2.Response;

@Slf4j
@OwnedBy(CDC)
@RunWith(MockitoJUnitRunner.class)
public class ShellScriptHelperServiceImplTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock private SecretNGManagerClient secretManagerClient;
  @Mock private SshKeySpecDTOHelper sshKeySpecDTOHelper;
  @Mock private ShellScriptHelperService shellScriptHelperService;
  @Mock private HarnessIdClientService workloadIdentityService;
  @Mock private NGSettingsClient settingsClient;
  @Mock private Call<ResponseDTO<SettingValueResponseDTO>> response;
  @Mock private EngineExpressionService engineExpressionService;
  @Mock FileStoreClient fileStoreClient;
  @Mock private InputSetValidatorFactory inputSetValidatorFactory;
  @Mock private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Mock private PmsFeatureFlagService pmsFeatureFlagService;
  @Mock Ambiance ambiance;
  @InjectMocks private ShellScriptHelperServiceImpl shellScriptHelperServiceImpl;

  @Before
  public void beforeRun() {
    PowerMockito.when(settingsClient.getSetting(any(), any(), any(), any())).thenReturn(response);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testGetEnvironmentVariables() throws IOException {
    SettingValueResponseDTO settingValueResponseDTO =
        SettingValueResponseDTO.builder().value("false").valueType(SettingValueType.BOOLEAN).build();
    PowerMockito.when(response.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(settingValueResponseDTO)));

    assertThat(shellScriptHelperServiceImpl.getEnvironmentVariables(null, Ambiance.newBuilder().build())).isEmpty();
    assertThat(shellScriptHelperServiceImpl.getEnvironmentVariables(new HashMap<>(), Ambiance.newBuilder().build()))
        .isEmpty();

    Map<String, Object> envVariables = new HashMap<>();
    envVariables.put("var1", Arrays.asList(1));
    envVariables.put("var2", "val2");
    envVariables.put("var3", ParameterField.createValueField("val3"));
    envVariables.put("var4", ParameterField.createExpressionField(true, "<+unresolved>", null, true));

    assertThatThrownBy(
        () -> shellScriptHelperServiceImpl.getEnvironmentVariables(envVariables, Ambiance.newBuilder().build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Env. variables: [var4] found to be unresolved");

    envVariables.remove("var4");
    Map<String, String> environmentVariables =
        shellScriptHelperServiceImpl.getEnvironmentVariables(envVariables, Ambiance.newBuilder().build());
    assertThat(environmentVariables).hasSize(2);
    assertThat(environmentVariables.get("var2")).isEqualTo("val2");
    assertThat(environmentVariables.get("var3")).isEqualTo("val3");
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testGetEnvironmentVariablesWithServiceVariables() throws IOException {
    // export service variable setting enabled
    SettingValueResponseDTO settingValueResponseDTO =
        SettingValueResponseDTO.builder().value("true").valueType(SettingValueType.BOOLEAN).build();
    PowerMockito.when(response.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(settingValueResponseDTO)));

    VariablesSweepingOutput serviceVariableOutput = new VariablesSweepingOutput();
    serviceVariableOutput.put("svar1", ParameterField.createValueField("sval1"));

    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(serviceVariableOutput).build());

    // without any input variables. only service vars should be present
    assertThat(shellScriptHelperServiceImpl.getEnvironmentVariables(null, Ambiance.newBuilder().build())).hasSize(1);
    assertThat(shellScriptHelperServiceImpl.getEnvironmentVariables(new HashMap<>(), Ambiance.newBuilder().build()))
        .hasSize(1);

    Map<String, Object> envVariables = new HashMap<>();
    envVariables.put("var1", Arrays.asList(1));
    envVariables.put("var2", "val2");
    envVariables.put("var3", ParameterField.createValueField("val3"));

    Map<String, String> environmentVariables =
        shellScriptHelperServiceImpl.getEnvironmentVariables(envVariables, Ambiance.newBuilder().build());
    assertThat(environmentVariables).hasSize(3);
    assertThat(environmentVariables.get("svar1")).isEqualTo("sval1");
    assertThat(environmentVariables.get("var2")).isEqualTo("val2");
    assertThat(environmentVariables.get("var3")).isEqualTo("val3");
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testGetOutputVars() {
    assertThat(shellScriptHelperServiceImpl.getOutputVars(null, new HashSet<>())).isEmpty();
    assertThat(shellScriptHelperServiceImpl.getOutputVars(new HashMap<>(), new HashSet<>())).isEmpty();

    Map<String, Object> outputVariables = new LinkedHashMap<>();
    outputVariables.put("var1", Arrays.asList(1));
    outputVariables.put("var2", "val2");
    outputVariables.put("var3", ParameterField.createValueField("val3"));
    outputVariables.put("var4", ParameterField.createExpressionField(true, "<+unresolved>", null, true));

    assertThatThrownBy(() -> shellScriptHelperServiceImpl.getOutputVars(outputVariables, new HashSet<>()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Output variable [var4] value found to be empty");

    outputVariables.remove("var4");
    List<String> outputVariablesValues = shellScriptHelperServiceImpl.getOutputVars(outputVariables, new HashSet<>());
    assertThat(outputVariablesValues).hasSize(2);
    assertThat(outputVariablesValues.get(0)).isEqualTo("val2");
    assertThat(outputVariablesValues.get(1)).isEqualTo("val3");

    Set<String> secretOutputVars = new HashSet<>();
    secretOutputVars.add("var2");
    outputVariablesValues = shellScriptHelperServiceImpl.getOutputVars(outputVariables, secretOutputVars);
    assertThat(outputVariablesValues).hasSize(1);
    assertThat(outputVariablesValues.get(0)).isEqualTo("val3");
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testGetSecretOutputVars() {
    assertThat(shellScriptHelperServiceImpl.getSecretOutputVars(null, new HashSet<>())).isEmpty();
    assertThat(shellScriptHelperServiceImpl.getSecretOutputVars(new HashMap<>(), new HashSet<>())).isEmpty();

    Map<String, Object> outputVariables = new LinkedHashMap<>();
    outputVariables.put("var1", Arrays.asList(1));
    outputVariables.put("var2", "val2");
    outputVariables.put("var3", ParameterField.createValueField("val3"));
    outputVariables.put("var4", ParameterField.createExpressionField(true, "<+unresolved>", null, true));

    Set<String> secretOutputVars = new HashSet<>();
    secretOutputVars.add("var4");

    assertThatThrownBy(() -> shellScriptHelperServiceImpl.getSecretOutputVars(outputVariables, secretOutputVars))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Output variable [var4] value found to be empty");

    secretOutputVars.remove("var4");
    secretOutputVars.add("var2");
    List<String> outputVariablesValues =
        shellScriptHelperServiceImpl.getSecretOutputVars(outputVariables, secretOutputVars);
    assertThat(outputVariablesValues).hasSize(1);
    assertThat(outputVariablesValues.get(0)).isEqualTo("val2");
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testGetK8sInfraDelegateConfig() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    String script = "echo hey";
    ShellScriptTaskParametersNGBuilder taskParamsBuilder = ShellScriptTaskParametersNG.builder();

    assertThat(shellScriptHelperServiceImpl.getK8sInfraDelegateConfig(ambiance, script, false)).isNull();

    script = "export KUBE_CONFIG=${HARNESS_KUBE_CONFIG_PATH}";
    OptionalSweepingOutput optionalSweepingOutput = OptionalSweepingOutput.builder().found(false).build();
    doReturn(optionalSweepingOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(OutputExpressionConstants.K8S_INFRA_DELEGATE_CONFIG_OUTPUT_NAME));
    assertThat(shellScriptHelperServiceImpl.getK8sInfraDelegateConfig(ambiance, script, false)).isNull();

    K8sInfraDelegateConfigOutput k8sInfraDelegateConfigOutput = K8sInfraDelegateConfigOutput.builder().build();
    optionalSweepingOutput = OptionalSweepingOutput.builder().found(true).output(k8sInfraDelegateConfigOutput).build();
    doReturn(optionalSweepingOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(OutputExpressionConstants.K8S_INFRA_DELEGATE_CONFIG_OUTPUT_NAME));
    assertThat(shellScriptHelperServiceImpl.getK8sInfraDelegateConfig(ambiance, script, false)).isNull();

    DirectK8sInfraDelegateConfig k8sInfraDelegateConfig = DirectK8sInfraDelegateConfig.builder().build();
    k8sInfraDelegateConfigOutput =
        K8sInfraDelegateConfigOutput.builder().k8sInfraDelegateConfig(k8sInfraDelegateConfig).build();
    optionalSweepingOutput = OptionalSweepingOutput.builder().found(true).output(k8sInfraDelegateConfigOutput).build();
    doReturn(optionalSweepingOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(OutputExpressionConstants.K8S_INFRA_DELEGATE_CONFIG_OUTPUT_NAME));
    assertThat(shellScriptHelperServiceImpl.getK8sInfraDelegateConfig(ambiance, script, false))
        .isEqualTo(k8sInfraDelegateConfig);

    script = "echo hey";
    assertThat(shellScriptHelperServiceImpl.getK8sInfraDelegateConfig(ambiance, script, true))
        .isEqualTo(k8sInfraDelegateConfig);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testPrepareTaskParametersForIncorrectExecutionTarget() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    ShellScriptStepParametersV0 stepParameters =
        ShellScriptStepParametersV0.infoBuilder().onDelegate(ParameterField.createValueField(true)).build();
    ShellScriptTaskParametersNGBuilder taskParamsBuilder = ShellScriptTaskParametersNG.builder();

    ParameterField<ExecutionTarget> executionTarget =
        ParameterField.createValueField(ExecutionTarget.builder().build());
    stepParameters.setExecutionTarget(executionTarget);

    shellScriptHelperServiceImpl.prepareTaskParametersForExecutionTarget(
        ambiance, stepParameters, taskParamsBuilder, true);
    assertThat(taskParamsBuilder.build().getHost()).isNull();

    stepParameters.setOnDelegate(ParameterField.createValueField(false));

    assertThatThrownBy(()
                           -> shellScriptHelperServiceImpl.prepareTaskParametersForExecutionTarget(
                               ambiance, stepParameters, taskParamsBuilder, false))
        .hasMessageContaining("Connector Ref in Execution Target can't be empty");

    executionTarget.getValue().setConnectorRef(ParameterField.createValueField("cRef"));
    assertThatThrownBy(()
                           -> shellScriptHelperServiceImpl.prepareTaskParametersForExecutionTarget(
                               ambiance, stepParameters, taskParamsBuilder, false))
        .hasMessageContaining("Host in Execution Target can't be empty");
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testPrepareTaskParametersForExecutionTarget() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, "accId")
                            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "orgId")
                            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "projId")
                            .build();
    ParameterField<ExecutionTarget> executionTarget =
        ParameterField.createValueField(ExecutionTarget.builder()
                                            .connectorRef(ParameterField.createValueField("cref"))
                                            .host(ParameterField.createValueField("host"))
                                            .build());
    ShellScriptStepParametersV0 stepParameters = ShellScriptStepParametersV0.infoBuilder()
                                                     .onDelegate(ParameterField.createValueField(false))
                                                     .executionTarget(executionTarget)
                                                     .build();
    ShellScriptTaskParametersNGBuilder taskParamsBuilder = ShellScriptTaskParametersNG.builder();

    try (MockedStatic<NGRestUtils> aStatic = Mockito.mockStatic(NGRestUtils.class)) {
      aStatic.when(() -> NGRestUtils.getResponse(any(), any())).thenReturn(null);
      assertThatThrownBy(()
                             -> shellScriptHelperServiceImpl.prepareTaskParametersForExecutionTarget(
                                 ambiance, stepParameters, taskParamsBuilder, false))
          .hasMessageContaining("No secret configured with identifier: cref");

      SSHKeySpecDTO sshKeySpecDTO = SSHKeySpecDTO.builder().build();
      Optional<SecretResponseWrapper> secretResponseWrapperOptional = Optional.of(
          SecretResponseWrapper.builder().secret(SecretDTOV2.builder().spec(sshKeySpecDTO).build()).build());
      when(NGRestUtils.getResponse(any(), any())).thenReturn(secretResponseWrapperOptional.get());

      List<EncryptedDataDetail> encryptedDataDetails = Collections.singletonList(EncryptedDataDetail.builder().build());
      doReturn(encryptedDataDetails)
          .when(sshKeySpecDTOHelper)
          .getSSHKeyEncryptionDetails(sshKeySpecDTO, AmbianceUtils.getNgAccess(ambiance));
      shellScriptHelperServiceImpl.prepareTaskParametersForExecutionTarget(
          ambiance, stepParameters, taskParamsBuilder, false);
      assertThat(taskParamsBuilder.build().getHost()).isEqualTo("host");
      assertThat(taskParamsBuilder.build().getEncryptionDetails()).hasSize(1);
      assertThat(taskParamsBuilder.build().getSshKeySpecDTO()).isEqualTo(sshKeySpecDTO);
    }
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testGetShellScript() {
    String script = "echo <+unresolved1>\necho <+unresolved2>";
    Ambiance ambiance = Ambiance.newBuilder().putAllSetupAbstractions(Map.of("accountId", "testAcc")).build();

    ParameterField<String> parameterFieldScript = ParameterField.createExpressionField(true, script, null, true);
    ShellScriptInlineSource source = ShellScriptInlineSource.builder().script(parameterFieldScript).build();
    ShellScriptStepParametersV0 stepParameters =
        ShellScriptStepParametersV0.infoBuilder()
            .source(ShellScriptSourceWrapper.builder().spec(source).type("Inline").build())
            .build();
    assertThat(shellScriptHelperServiceImpl.getShellScript(stepParameters, ambiance))
        .isEqualTo("echo <+unresolved1>\necho <+unresolved2>");
    source.setScript(ParameterField.createValueField("echo hi"));
    assertThat(shellScriptHelperServiceImpl.getShellScript(stepParameters, ambiance)).isEqualTo("echo hi");
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testGetShellScriptWithFileScript() {
    final String fileContent = "echo hi";
    Ambiance ambiance = Ambiance.newBuilder().putAllSetupAbstractions(Map.of("accountId", "testAcc")).build();

    ParameterField<String> parameterFieldFile = ParameterField.createValueField("account:/test");
    HarnessFileStoreSource source = HarnessFileStoreSource.builder().file(parameterFieldFile).build();
    ShellScriptStepParametersV0 stepParameters =
        ShellScriptStepParametersV0.infoBuilder()
            .source(ShellScriptSourceWrapper.builder().spec(source).type("Harness").build())
            .build();

    Call call = mock(Call.class);

    try (MockedStatic<SafeHttpCall> safeHttpCallMockedStatic = mockStatic(SafeHttpCall.class)) {
      doReturn(call).when(fileStoreClient).getContent(anyString(), anyString(), anyString(), anyString());
      doReturn(fileContent).when(engineExpressionService).renderExpression(any(), anyString());
      doReturn(true).when(pmsFeatureFlagHelper).isEnabled(anyString(), any(FeatureName.class));

      safeHttpCallMockedStatic.when(() -> SafeHttpCall.executeWithExceptions(any()))
          .thenReturn(ResponseDTO.newResponse(fileContent));
      String ret = shellScriptHelperServiceImpl.getShellScript(stepParameters, ambiance);
      assertThat(ret).isEqualTo(fileContent);
    }
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testGetShellScriptWithFileScriptFFNotEnabled() {
    Ambiance ambiance = Ambiance.newBuilder().putAllSetupAbstractions(Map.of("accountId", "testAcc")).build();

    ParameterField<String> parameterFieldFile = ParameterField.createValueField("account:/test");
    HarnessFileStoreSource source = HarnessFileStoreSource.builder().file(parameterFieldFile).build();
    ShellScriptStepParametersV0 stepParameters =
        ShellScriptStepParametersV0.infoBuilder()
            .source(ShellScriptSourceWrapper.builder().spec(source).type("Harness").build())
            .build();

    assertThatThrownBy(() -> shellScriptHelperServiceImpl.getShellScript(stepParameters, ambiance))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testGetShellScriptThrowsException() {
    Ambiance ambiance = Ambiance.newBuilder().putAllSetupAbstractions(Map.of("accountId", "testAcc")).build();

    ParameterField<String> parameterFieldFile = ParameterField.createValueField("account:/test");
    HarnessFileStoreSource source = HarnessFileStoreSource.builder().file(parameterFieldFile).build();
    ShellScriptStepParametersV0 stepParameters =
        ShellScriptStepParametersV0.infoBuilder()
            .source(ShellScriptSourceWrapper.builder().spec(source).type("Unknown").build())
            .build();

    assertThatThrownBy(() -> shellScriptHelperServiceImpl.getShellScript(stepParameters, ambiance))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testGetWorkingDirectory() {
    ShellScriptStepParametersV0 stepParameters =
        ShellScriptStepParametersV0.infoBuilder().onDelegate(ParameterField.createValueField(true)).build();
    assertThat(shellScriptHelperServiceImpl.getWorkingDirectory(ParameterField.ofNull(), ScriptType.BASH, true))
        .isEqualTo("/tmp");
    assertThat(shellScriptHelperServiceImpl.getWorkingDirectory(ParameterField.ofNull(), ScriptType.POWERSHELL, true))
        .isEqualTo("/tmp");
    assertThat(shellScriptHelperServiceImpl.getWorkingDirectory(
                   ParameterField.createValueField(ExecutionTarget.builder().build()), ScriptType.POWERSHELL, false))
        .isEqualTo("%TEMP%");

    stepParameters.setExecutionTarget(ParameterField.createValueField(
        ExecutionTarget.builder().workingDirectory(ParameterField.createValueField("dir")).build()));
    assertThat(
        shellScriptHelperServiceImpl.getWorkingDirectory(stepParameters.getExecutionTarget(), ScriptType.BASH, true))
        .isEqualTo("dir");
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testBuildShellScriptTaskParametersNG() {
    Ambiance ambiance = buildAmbiance();
    Map<String, Object> inputVars = new LinkedHashMap<>();
    inputVars.put("key1", "val1");
    Map<String, Object> outputVars = new LinkedHashMap<>();
    outputVars.put("key1", "val1");
    outputVars.put("key2", "val2");

    ShellScriptStepParametersV0 stepParameters =
        ShellScriptStepParametersV0.infoBuilder()
            .shellType(ShellType.Bash)
            .onDelegate(ParameterField.createValueField(true))
            .environmentVariables(inputVars)
            .outputVariables(outputVars)
            .secretOutputVariables(new HashSet<>())
            .outputAlias(
                OutputAlias.builder().key(ParameterField.createValueField("abc")).scope(ExportScope.PIPELINE).build())
            .build();
    String script = "echo hey";
    DirectK8sInfraDelegateConfig k8sInfraDelegateConfig = DirectK8sInfraDelegateConfig.builder().build();
    Map<String, String> taskEnvVariables = new LinkedHashMap<>();
    inputVars.put("key1", "val1");
    List<String> taskOutputVars = Arrays.asList("key1", "key2");

    doReturn(script).when(shellScriptHelperService).getShellScript(stepParameters, ambiance);
    doNothing()
        .when(shellScriptHelperService)
        .prepareTaskParametersForExecutionTarget(eq(ambiance), eq(stepParameters), any(), anyBoolean());
    doReturn(false).when(pmsFeatureFlagService).isEnabled(anyString(), eq(FeatureName.PIPE_HARNESS_ANNOTATIONS));
    doReturn(k8sInfraDelegateConfig).when(shellScriptHelperService).getK8sInfraDelegateConfig(ambiance, script, false);
    doReturn(taskEnvVariables)
        .when(shellScriptHelperService)
        .getEnvironmentVariables(inputVars, Ambiance.newBuilder().build());
    doReturn(taskOutputVars).when(shellScriptHelperService).getOutputVars(outputVars, new HashSet<>());
    doReturn("/tmp").when(shellScriptHelperService).getWorkingDirectory(null, ScriptType.BASH, true);

    ShellScriptTaskParametersNG taskParams =
        (ShellScriptTaskParametersNG) shellScriptHelperServiceImpl.buildShellScriptTaskParametersNG(
            ambiance, stepParameters, null, ShellScriptTaskNG.COMMAND_UNIT);
    assertThat(taskParams.getScript()).isEqualTo(script);
    assertThat(taskParams.getK8sInfraDelegateConfig()).isEqualTo(k8sInfraDelegateConfig);
    assertThat(taskParams.getWorkingDirectory()).isEqualTo("/tmp");
    assertThat(taskParams.getOutputVars()).isEqualTo(taskOutputVars);
    assertThat(taskParams.getEnvironmentVariables()).isEqualTo(taskEnvVariables);

    // onDelegate parameter field null/empty cases
    stepParameters.setOnDelegate(ParameterField.createValueField(null));
    stepParameters.setExecutionTarget(null);
    taskParams = (ShellScriptTaskParametersNG) shellScriptHelperServiceImpl.buildShellScriptTaskParametersNG(
        ambiance, stepParameters, null, ShellScriptTaskNG.COMMAND_UNIT);
    assertThat(taskParams.isExecuteOnDelegate()).isTrue();

    stepParameters.setOnDelegate(ParameterField.createValueField(null));
    stepParameters.setExecutionTarget(
        ParameterField.createValueField(ExecutionTarget.builder()
                                            .workingDirectory(ParameterField.createValueField("dir"))
                                            .host(ParameterField.createValueField("host"))
                                            .connectorRef(ParameterField.createValueField("connectorRef"))
                                            .build()));
    taskParams = (ShellScriptTaskParametersNG) shellScriptHelperServiceImpl.buildShellScriptTaskParametersNG(
        ambiance, stepParameters, null, ShellScriptTaskNG.COMMAND_UNIT);
    assertThat(taskParams.isExecuteOnDelegate()).isFalse();
    stepParameters.setOnDelegate(ParameterField.createValueField(true));

    // negative cases for output alias configuration
    stepParameters.setOutputAlias(OutputAlias.builder().build());
    assertThatThrownBy(()
                           -> shellScriptHelperServiceImpl.buildShellScriptTaskParametersNG(
                               ambiance, stepParameters, null, ShellScriptTaskNG.COMMAND_UNIT))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Empty value for key is not allowed in output alias configuration");
    stepParameters.setOutputAlias(
        OutputAlias.builder().key(ParameterField.createValueField("")).scope(ExportScope.PIPELINE).build());
    assertThatThrownBy(()
                           -> shellScriptHelperServiceImpl.buildShellScriptTaskParametersNG(
                               ambiance, stepParameters, null, ShellScriptTaskNG.COMMAND_UNIT))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Empty value for key is not allowed in output alias configuration");
    stepParameters.setOutputAlias(
        OutputAlias.builder().key(ParameterField.createValueField("   ")).scope(ExportScope.PIPELINE).build());
    assertThatThrownBy(()
                           -> shellScriptHelperServiceImpl.buildShellScriptTaskParametersNG(
                               ambiance, stepParameters, null, ShellScriptTaskNG.COMMAND_UNIT))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Empty value for key is not allowed in output alias configuration");
    stepParameters.setOutputAlias(
        OutputAlias.builder().key(ParameterField.createValueField("null")).scope(ExportScope.PIPELINE).build());
    assertThatThrownBy(()
                           -> shellScriptHelperServiceImpl.buildShellScriptTaskParametersNG(
                               ambiance, stepParameters, null, ShellScriptTaskNG.COMMAND_UNIT))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Expression provided for key in output alias configuration was not resolved");
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testBuildBashTaskParametersInjectsWorkloadIdentityEnvVars() {
    Map<String, Object> inputVars = new LinkedHashMap<>();
    inputVars.put("key1", "val1");
    ShellScriptStepParametersV0 stepParameters = ShellScriptStepParametersV0.infoBuilder()
                                                     .shellType(ShellType.Bash)
                                                     .onDelegate(ParameterField.createValueField(true))
                                                     .environmentVariables(inputVars)
                                                     .outputVariables(new LinkedHashMap<>())
                                                     .secretOutputVariables(new HashSet<>())
                                                     .build();
    String script = "echo hey";
    Map<String, String> envVariables = new LinkedHashMap<>();

    doReturn(script).when(shellScriptHelperService).getShellScript(stepParameters, ambiance);
    doNothing()
        .when(shellScriptHelperService)
        .prepareTaskParametersForExecutionTarget(eq(ambiance), eq(stepParameters), any(), anyBoolean());
    doReturn(DirectK8sInfraDelegateConfig.builder().build())
        .when(shellScriptHelperService)
        .getK8sInfraDelegateConfig(ambiance, script, false);
    doReturn(envVariables).when(shellScriptHelperService).getEnvironmentVariables(any(), any());
    doReturn(Collections.emptyList()).when(shellScriptHelperService).getOutputVars(any(), any());
    doReturn(Collections.emptyList()).when(shellScriptHelperService).getSecretOutputVars(any(), any());
    doReturn("/tmp").when(shellScriptHelperService).getWorkingDirectory(any(), eq(ScriptType.BASH), anyBoolean());

    // null token => no injection
    Map<String, String> envVariables2 = new LinkedHashMap<>();
    doReturn(envVariables2).when(shellScriptHelperService).getEnvironmentVariables(any(), any());
    ShellScriptTaskParametersNG withoutToken =
        (ShellScriptTaskParametersNG) shellScriptHelperServiceImpl.buildShellScriptTaskParametersNG(
            ambiance, stepParameters, null, ShellScriptTaskNG.COMMAND_UNIT, null);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testBuildBashTaskParametersInjectsIdentityTokens() {
    ShellScriptStepParametersV0 stepParameters = ShellScriptStepParametersV0.infoBuilder()
                                                     .shellType(ShellType.Bash)
                                                     .onDelegate(ParameterField.createValueField(true))
                                                     .environmentVariables(new LinkedHashMap<>())
                                                     .outputVariables(new LinkedHashMap<>())
                                                     .secretOutputVariables(new HashSet<>())
                                                     .build();
    String script = "echo hey";
    doReturn(script).when(shellScriptHelperService).getShellScript(stepParameters, ambiance);
    doNothing()
        .when(shellScriptHelperService)
        .prepareTaskParametersForExecutionTarget(eq(ambiance), eq(stepParameters), any(), anyBoolean());
    doReturn(DirectK8sInfraDelegateConfig.builder().build())
        .when(shellScriptHelperService)
        .getK8sInfraDelegateConfig(ambiance, script, false);
    doReturn(new LinkedHashMap<>()).when(shellScriptHelperService).getEnvironmentVariables(any(), any());
    doReturn(Collections.emptyList()).when(shellScriptHelperService).getOutputVars(any(), any());
    doReturn(Collections.emptyList()).when(shellScriptHelperService).getSecretOutputVars(any(), any());
    doReturn("/tmp").when(shellScriptHelperService).getWorkingDirectory(any(), eq(ScriptType.BASH), anyBoolean());

    Map<String, String> identityTokens = new LinkedHashMap<>();
    identityTokens.put("AWS_ID_TOKEN", "aws-token-value");
    identityTokens.put("GCP_ID_TOKEN", "gcp-token-value");

    ShellScriptTaskParametersNG params =
        (ShellScriptTaskParametersNG) shellScriptHelperServiceImpl.buildShellScriptTaskParametersNG(
            ambiance, stepParameters, null, ShellScriptTaskNG.COMMAND_UNIT, identityTokens);

    assertThat(params.getEnvironmentVariables())
        .containsEntry("AWS_ID_TOKEN", "aws-token-value")
        .containsEntry("GCP_ID_TOKEN", "gcp-token-value");
  }

  private Ambiance buildAmbiance() {
    return Ambiance.newBuilder()
        .putSetupAbstractions(SetupAbstractionKeys.accountId, "accId")
        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "orgId")
        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "projId")
        .build();
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testPrepareShellScriptOutcome() {
    ShellScriptBaseOutcome shellScriptOutcome =
        shellScriptHelperServiceImpl.prepareShellScriptOutcome(null, new HashMap<>());
    assertThat(shellScriptOutcome).isNull();

    shellScriptOutcome = shellScriptHelperServiceImpl.prepareShellScriptOutcome(new HashMap<>(), null);
    assertThat(shellScriptOutcome).isNull();

    Map<String, Object> outputVariables = new HashMap<>();
    outputVariables.put("output1", ParameterField.createValueField("var1"));
    outputVariables.put("output2", ParameterField.createValueField("var2"));

    shellScriptOutcome = shellScriptHelperServiceImpl.prepareShellScriptOutcome(new HashMap<>(), outputVariables);
    assertThat(shellScriptOutcome.getOutputVariables()).hasSize(2);
    assertThat(shellScriptOutcome.getOutputVariables().get("output1")).isNull();

    Map<String, String> envVariables = new HashMap<>();
    envVariables.put("var1", "val1");
    envVariables.put("var2", "val2");

    shellScriptOutcome = shellScriptHelperServiceImpl.prepareShellScriptOutcome(envVariables, outputVariables);
    assertThat(shellScriptOutcome.getOutputVariables()).hasSize(2);
    assertThat(shellScriptOutcome.getOutputVariables().get("output1")).isEqualTo("val1");
    assertThat(shellScriptOutcome.getOutputVariables().get("output2")).isEqualTo("val2");
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testPrepareShellScriptOutcomeWithSecretVars() {
    ShellScriptBaseOutcome shellScriptOutcome =
        ShellScriptHelperService.prepareShellScriptOutcome(null, new HashMap<>(), new HashSet<>());
    assertThat(shellScriptOutcome).isNull();

    shellScriptOutcome = ShellScriptHelperService.prepareShellScriptOutcome(new HashMap<>(), null, null);
    assertThat(shellScriptOutcome).isNull();

    Map<String, Object> outputVariables = new HashMap<>();
    outputVariables.put("output1", ParameterField.createValueField("var1"));
    outputVariables.put("output2", ParameterField.createValueField("var2"));

    // FF OFF (default): var not exported → captured value (null) is stored — matches pre-fallback prod behavior.
    shellScriptOutcome = ShellScriptHelperService.prepareShellScriptOutcome(new HashMap<>(), outputVariables, null);
    assertThat(shellScriptOutcome).isNotNull();
    assertThat(shellScriptOutcome.getOutputVariables()).hasSize(2);
    assertThat(shellScriptOutcome.getOutputVariables().get("output1")).isNull();

    // FF ON: var not exported → post-processor falls back to resolved YAML value ("var1").
    shellScriptOutcome = ShellScriptHelperService.prepareShellScriptOutcome(new HashMap<>(), outputVariables, null);
    ShellScriptHelperService.applyResolutionFallback(shellScriptOutcome, new HashMap<>(), outputVariables, null);
    assertThat(shellScriptOutcome).isNotNull();
    assertThat(shellScriptOutcome.getOutputVariables().get("output1")).isEqualTo("var1");

    // FF ON: var exported as empty string → post-processor preserves "" (do NOT fall back to var name).
    Map<String, String> emptyEnv = new HashMap<>();
    emptyEnv.put("var1", "");
    shellScriptOutcome = ShellScriptHelperService.prepareShellScriptOutcome(emptyEnv, outputVariables, null);
    ShellScriptHelperService.applyResolutionFallback(shellScriptOutcome, emptyEnv, outputVariables, null);
    assertThat(shellScriptOutcome).isNotNull();
    assertThat(shellScriptOutcome.getOutputVariables().get("output1")).isEqualTo("");

    Map<String, String> envVariables = new HashMap<>();
    envVariables.put("var1", "val1");
    envVariables.put("var2", "val2");

    shellScriptOutcome = ShellScriptHelperService.prepareShellScriptOutcome(envVariables, outputVariables, null);
    assertThat(shellScriptOutcome).isNotNull();
    assertThat(shellScriptOutcome.getOutputVariables()).hasSize(2);
    assertThat(shellScriptOutcome.getOutputVariables().get("output1")).isEqualTo("val1");
    assertThat(shellScriptOutcome.getOutputVariables().get("output2")).isEqualTo("val2");

    Set<String> secretOutputVars = new HashSet<>();
    secretOutputVars.add("output1");

    // "${sweepingOutputSecrets.obtain("output1","sa32zupgqijF2be+H2lEAw7yfMwGDFtC5zciKbzQGEtm5Vq+cjo7RclAhPVLTig7")}">
    String SECRET_REGEX = "\\$\\{sweepingOutputSecrets\\.obtain\\(\"[\\S|.]+\",\"[\\S|.]+\"\\)}";

    shellScriptOutcome =
        ShellScriptHelperService.prepareShellScriptOutcome(envVariables, outputVariables, secretOutputVars);
    assertThat(shellScriptOutcome).isNotNull();
    assertThat(shellScriptOutcome.getOutputVariables()).hasSize(2);
    assertThat(shellScriptOutcome.getOutputVariables().get("output1")).containsPattern(Pattern.compile(SECRET_REGEX));
    assertThat(shellScriptOutcome.getOutputVariables().get("output2")).isEqualTo("val2");

    // Secret output not exported by the script — must not leak the shell var name; preserved as null so UI renders
    // masked.
    shellScriptOutcome =
        ShellScriptHelperService.prepareShellScriptOutcome(new HashMap<>(), outputVariables, secretOutputVars);
    assertThat(shellScriptOutcome).isNotNull();
    assertThat(shellScriptOutcome.getOutputVariables()).containsKey("output1");
    assertThat(shellScriptOutcome.getOutputVariables().get("output1")).isNull();
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testExportOutputVariablesUsingAlias() {
    Map<String, String> outputVars = new LinkedHashMap<>();
    outputVars.put("key1", "val1");
    outputVars.put("key2", "val2");
    ShellScriptStepParametersV0 stepParameters = ShellScriptStepParametersV0.infoBuilder()
                                                     .shellType(ShellType.Bash)
                                                     .onDelegate(ParameterField.createValueField(true))
                                                     .secretOutputVariables(new HashSet<>())
                                                     .build();
    when(executionSweepingOutputService.consume(any(), any(), any(), any())).thenReturn("");
    when(ambiance.getExpressionFunctorToken()).thenReturn(1234L);
    // invalid cases
    shellScriptHelperServiceImpl.exportOutputVariablesUsingAlias(
        ambiance, stepParameters, ShellScriptOutcome.builder().outputVariables(outputVars).build());
    stepParameters.setOutputAlias(
        OutputAlias.builder().key(ParameterField.createValueField("abc")).scope(ExportScope.PIPELINE).build());
    shellScriptHelperServiceImpl.exportOutputVariablesUsingAlias(
        ambiance, stepParameters, ShellScriptOutcome.builder().outputVariables(new HashMap<>()).build());
    // normal case
    shellScriptHelperServiceImpl.exportOutputVariablesUsingAlias(
        ambiance, stepParameters, ShellScriptOutcome.builder().outputVariables(outputVars).build());
    verify(executionSweepingOutputService, times(1))
        .consume(ambiance, OutputAliasUtils.generateSweepingOutputKeyUsingUserAlias("abc", ambiance),
            OutputAliasSweepingOutput.builder().outputVariables(outputVars).build(), StepOutcomeGroup.PIPELINE.name());
    // negative cases
    when(executionSweepingOutputService.consume(any(), any(), any(), any()))
        .thenThrow(new GeneralException("Sweeping output with name 2507ee91 is already saved"))
        .thenThrow(new GeneralException("random"));
    assertThatThrownBy(()
                           -> shellScriptHelperServiceImpl.exportOutputVariablesUsingAlias(ambiance, stepParameters,
                               ShellScriptOutcome.builder().outputVariables(outputVars).build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Output alias with key abc, already saved in Pipeline scope. Please ensure that there are no "
            + "duplicate output alias keys within the same scope");

    assertThatThrownBy(()
                           -> shellScriptHelperServiceImpl.exportOutputVariablesUsingAlias(ambiance, stepParameters,
                               ShellScriptOutcome.builder().outputVariables(outputVars).build()))
        .isInstanceOf(InternalServerErrorException.class)
        .hasMessage("Error while publishing outputAlias for the key abc for scope Pipeline: GENERAL_ERROR");
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetShellScriptStepParameters() {
    assertEquals(ShellScriptHelperService.getShellScriptStepParameters(
                     StepElementParameters.builder()
                         .spec(ShellScriptStepParametersV0.infoBuilder().shellType(ShellType.PowerShell).build())
                         .build()),
        ShellScriptStepParametersV0.infoBuilder().shellType(ShellType.PowerShell).build());
    assertEquals(ShellScriptHelperService.getShellScriptStepParameters(
                     StepElementParameters.builder()
                         .spec(io.harness.steps.shellscript.v1.ShellScriptStepParameters.infoBuilder()
                                   .shell(ShellTypeV1.PowerShell)
                                   .build())
                         .build()),
        ShellScriptStepParametersV0.infoBuilder().shellType(ShellType.PowerShell).build());
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetShellScriptOutcome() {
    assertEquals(ShellScriptHelperService.getShellScriptOutcome(new HashMap<>(), HarnessYamlVersion.V0),
        io.harness.steps.shellscript.ShellScriptOutcome.builder().outputVariables(new HashMap<>()).build());
    assertEquals(ShellScriptHelperService.getShellScriptOutcome(new HashMap<>(), HarnessYamlVersion.V1),
        io.harness.steps.shellscript.v1.ShellScriptOutcome.builder().output_vars(new HashMap<>()).build());
    assertThatThrownBy(() -> ShellScriptHelperService.getShellScriptOutcome(new HashMap<>(), "v1"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Version v1 not supported");
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void testGetContentSuccess() {
    String scopedFilePath = "account:/test/script.sh";
    String fileContent = "echo 'Hello World'";
    FileReference fileReference = FileReference.builder()
                                      .accountIdentifier("accId")
                                      .orgIdentifier("orgId")
                                      .projectIdentifier("projId")
                                      .path(scopedFilePath)
                                      .build();
    @SuppressWarnings("unchecked") Call<ResponseDTO<String>> call = mock(Call.class);

    try (MockedStatic<SafeHttpCall> safeHttpCallMockedStatic = mockStatic(SafeHttpCall.class)) {
      doReturn(call).when(fileStoreClient).getContent(anyString(), anyString(), anyString(), anyString());
      safeHttpCallMockedStatic.when(() -> SafeHttpCall.executeWithExceptions(any()))
          .thenReturn(ResponseDTO.newResponse(fileContent));

      String result = shellScriptHelperServiceImpl.getContent(fileReference, scopedFilePath);

      assertThat(result).isEqualTo(fileContent);
      verify(fileStoreClient, times(1)).getContent(anyString(), eq("accId"), eq("orgId"), eq("projId"));
    }
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void testGetContentRetryOnSocketTimeoutExceptionWithEventualSuccess() {
    String scopedFilePath = "account:/test/script.sh";
    String fileContent = "echo 'Hello World'";
    FileReference fileReference = FileReference.builder()
                                      .accountIdentifier("accId")
                                      .orgIdentifier("orgId")
                                      .projectIdentifier("projId")
                                      .path(scopedFilePath)
                                      .build();
    @SuppressWarnings("unchecked") Call<ResponseDTO<String>> call = mock(Call.class);

    try (MockedStatic<SafeHttpCall> safeHttpCallMockedStatic = mockStatic(SafeHttpCall.class)) {
      doReturn(call).when(fileStoreClient).getContent(anyString(), anyString(), anyString(), anyString());

      // First call throws SocketTimeoutException, second call succeeds
      safeHttpCallMockedStatic.when(() -> SafeHttpCall.executeWithExceptions(any()))
          .thenThrow(new SocketTimeoutException("Connection timeout"))
          .thenReturn(ResponseDTO.newResponse(fileContent));

      String result = shellScriptHelperServiceImpl.getContent(fileReference, scopedFilePath);

      assertThat(result).isEqualTo(fileContent);
      // Verify that the method was called twice (1 failure + 1 success)
      verify(fileStoreClient, times(2)).getContent(anyString(), eq("accId"), eq("orgId"), eq("projId"));
    }
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void testGetContentRetryExhaustedAfterMaxAttempts() {
    String scopedFilePath = "account:/test/script.sh";
    FileReference fileReference = FileReference.builder()
                                      .accountIdentifier("accId")
                                      .orgIdentifier("orgId")
                                      .projectIdentifier("projId")
                                      .path(scopedFilePath)
                                      .build();
    @SuppressWarnings("unchecked") Call<ResponseDTO<String>> call = mock(Call.class);

    try (MockedStatic<SafeHttpCall> safeHttpCallMockedStatic = mockStatic(SafeHttpCall.class)) {
      doReturn(call).when(fileStoreClient).getContent(anyString(), anyString(), anyString(), anyString());

      // All attempts throw SocketTimeoutException
      safeHttpCallMockedStatic.when(() -> SafeHttpCall.executeWithExceptions(any()))
          .thenThrow(new SocketTimeoutException("Connection timeout"));

      assertThatThrownBy(() -> shellScriptHelperServiceImpl.getContent(fileReference, scopedFilePath))
          .isInstanceOf(InvalidRequestException.class)
          .hasMessageContaining("Failed to get File content from");

      // Verify that the method was called 3 times (max attempts)
      verify(fileStoreClient, times(3)).getContent(anyString(), eq("accId"), eq("orgId"), eq("projId"));
    }
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void testGetContentNoRetryOnOtherExceptions() {
    String scopedFilePath = "account:/test/script.sh";
    FileReference fileReference = FileReference.builder()
                                      .accountIdentifier("accId")
                                      .orgIdentifier("orgId")
                                      .projectIdentifier("projId")
                                      .path(scopedFilePath)
                                      .build();
    @SuppressWarnings("unchecked") Call<ResponseDTO<String>> call = mock(Call.class);

    try (MockedStatic<SafeHttpCall> safeHttpCallMockedStatic = mockStatic(SafeHttpCall.class)) {
      doReturn(call).when(fileStoreClient).getContent(anyString(), anyString(), anyString(), anyString());

      // Throw a different exception (not SocketTimeoutException)
      safeHttpCallMockedStatic.when(() -> SafeHttpCall.executeWithExceptions(any()))
          .thenThrow(new IOException("File not found"));

      assertThatThrownBy(() -> shellScriptHelperServiceImpl.getContent(fileReference, scopedFilePath))
          .isInstanceOf(InvalidRequestException.class)
          .hasMessageContaining("Failed to get File content from");

      // Verify that the method was called only once (no retry for non-SocketTimeoutException)
      verify(fileStoreClient, times(1)).getContent(anyString(), eq("accId"), eq("orgId"), eq("projId"));
    }
  }
}
