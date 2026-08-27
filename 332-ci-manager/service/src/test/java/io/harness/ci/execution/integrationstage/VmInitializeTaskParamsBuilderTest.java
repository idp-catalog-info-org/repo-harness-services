/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.integrationstage;

import static io.harness.rule.OwnerRule.ABHAY;
import static io.harness.rule.OwnerRule.ANURAG_MADNAWAT;
import static io.harness.rule.OwnerRule.DEV_MITTAL;
import static io.harness.rule.OwnerRule.JAMIE;
import static io.harness.rule.OwnerRule.MEENA;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;
import static io.harness.rule.OwnerRule.SAURABH;
import static io.harness.rule.OwnerRule.SHOBHIT_SINGH;
import static io.harness.rule.OwnerRule.SHUBHAM;
import static io.harness.rule.OwnerRule.SHUBHAM_AGARWAL;
import static io.harness.rule.OwnerRule.TAPAN;
import static io.harness.steps.StepUtils.PIE_SIMPLIFY_LOG_BASE_KEY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.ModuleType;
import io.harness.beans.FeatureName;
import io.harness.beans.execution.license.CILicenseService;
import io.harness.beans.steps.stepinfo.InitializeStepInfo;
import io.harness.beans.sweepingoutputs.ContextElement;
import io.harness.beans.sweepingoutputs.StageDetails;
import io.harness.beans.yaml.extended.CIResourceClass;
import io.harness.beans.yaml.extended.infrastrucutre.HostedVmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.beans.yaml.extended.infrastrucutre.Platform;
import io.harness.beans.yaml.extended.platform.ArchType;
import io.harness.beans.yaml.extended.runtime.CloudRuntime;
import io.harness.category.element.UnitTests;
import io.harness.ci.beans.entities.LogServiceConfig;
import io.harness.ci.beans.entities.TIServiceConfig;
import io.harness.ci.cacheserviceclient.CacheServiceUtils;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.config.HostedVmConfig;
import io.harness.ci.coverage.CoverageServiceHelper;
import io.harness.ci.execution.buildstate.ConnectorUtils;
import io.harness.ci.execution.integrationstage.vm.VmInitializeUtilsImpl;
import io.harness.ci.execution.states.IntegrationStageStepPMS;
import io.harness.ci.execution.utils.HostedVmSecretResolver;
import io.harness.ci.execution.utils.ci.CIStepInfoUtils;
import io.harness.ci.executionplan.base.CIExecutionTestBase;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.ci.logserviceclient.CILogServiceUtils;
import io.harness.ci.tiserviceclient.TIServiceUtils;
import io.harness.connector.SecretSpecBuilder;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.beans.ci.CIInitializeTaskParams;
import io.harness.delegate.beans.ci.pod.CICommonConstants;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.pod.SecretParams;
import io.harness.delegate.beans.ci.vm.dlite.DliteVmInitializeTaskParams;
import io.harness.delegate.beans.ci.vm.runner.SetupVmRequest;
import io.harness.delegate.beans.ci.vm.taskparams.CIVmInitializeTaskParams;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.git.GitClientHelper;
import io.harness.licensing.Edition;
import io.harness.licensing.LicenseStatus;
import io.harness.licensing.LicenseType;
import io.harness.licensing.beans.modules.AccountLicenseDTO;
import io.harness.licensing.beans.modules.ModuleLicenseDTO;
import io.harness.licensing.beans.modules.SSCAModuleLicenseDTO;
import io.harness.licensing.beans.summary.dto.CILicenseSummaryDTO;
import io.harness.licensing.remote.NgLicenseHttpClient;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngsettings.SettingValueType;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngsettings.dto.SettingValueResponseDTO;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.runner.request.utils.RunnerSubmitTaskUtils;
import io.harness.secrets.evaluator.CIVmSecretEvaluator;
import io.harness.ssca.client.SSCAServiceUtils;
import io.harness.ssca.execution.SSCALicenseHelper;
import io.harness.sto.beans.entities.STOServiceConfig;
import io.harness.stoserviceclient.STOServiceUtils;
import io.harness.utils.CILicenseUsageUtils;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.core.variables.NGVariableType;
import io.harness.yaml.core.variables.StringNGVariable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;
import org.powermock.reflect.Whitebox;
import retrofit2.Call;
import retrofit2.Response;

public class VmInitializeTaskParamsBuilderTest extends CIExecutionTestBase {
  @Mock private ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Mock CILogServiceUtils logServiceUtils;
  @Mock CILicenseService ciLicenseService;
  @Mock CoverageServiceHelper coverageServiceHelper;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock TIServiceUtils tiServiceUtils;
  @Mock CacheServiceUtils cacheServiceUtils;
  @Mock STOServiceUtils stoServiceUtils;
  @Mock CodebaseUtils codebaseUtils;
  @Mock ConnectorUtils connectorUtils;
  @Mock CIVmSecretEvaluator ciVmSecretEvaluator;
  @Mock CIExecutionServiceConfig ciExecutionServiceConfig;
  @Mock HostedVmSecretResolver hostedVmSecretResolver;
  @Mock private CIFeatureFlagService featureFlagService;
  @InjectMocks CILicenseUsageUtils ciLicenseUsageUtils;

  @Mock private VmInitializeUtilsImpl vmInitializeUtils;
  @InjectMocks VmInitializeTaskParamsBuilder vmInitializeTaskParamsBuilder;
  @Mock SSCAServiceUtils sscaServiceUtils;
  @Mock ConnectorDetails connectorDetails;
  @Mock SecretSpecBuilder secretSpecBuilder;
  private Ambiance ambiance;
  @InjectMocks SSCALicenseHelper sscaLicenseHelper;
  @Mock NgLicenseHttpClient ngLicenseHttpClient;
  @Mock NGSettingsClient settingsClient;
  private static final String accountId = "test";
  private static final String planExecutionId = "plan-exec-123";

  @Before
  public void setUp() {
    on(sscaLicenseHelper).set("ngLicenseHttpClient", ngLicenseHttpClient);
    on(vmInitializeTaskParamsBuilder).set("sscaLicenseHelper", sscaLicenseHelper);
    on(vmInitializeTaskParamsBuilder).set("ciLicenseUsageUtils", ciLicenseUsageUtils);
    on(vmInitializeTaskParamsBuilder).set("settingsClient", settingsClient);
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put("accountId", accountId);

    List<Level> levels = new ArrayList<>();
    levels.add(Level.newBuilder()
                   .setRuntimeId(UUIDGenerator.generateUuid())
                   .setSetupId(UUIDGenerator.generateUuid())
                   .setStepType(IntegrationStageStepPMS.STEP_TYPE)
                   .setIdentifier(ModuleType.CI.name())
                   .build());

    ambiance = Ambiance.newBuilder()
                   .putSetupAbstractions("accountId", accountId)
                   .addAllLevels(levels)
                   .setPlanExecutionId(planExecutionId)
                   .setMetadata(ExecutionMetadata.newBuilder()
                                    .putFeatureFlagToValueMap(PIE_SIMPLIFY_LOG_BASE_KEY, false)
                                    .setModuleType(ModuleType.CI.name())
                                    .build())
                   .build();

    MockitoAnnotations.initMocks(this);

    when(ngLicenseHttpClient.getAccountLicensesDTO(Mockito.any()))
        .thenAnswer((Answer<Call<ResponseDTO<AccountLicenseDTO>>>) invocation -> {
          Call<ResponseDTO<AccountLicenseDTO>> call = Mockito.mock(Call.class);
          Map<ModuleType, ModuleLicenseDTO> testLicenses = new HashMap<>();
          ModuleLicenseDTO sscaModuleLicneseDTO = SSCAModuleLicenseDTO.builder()
                                                      .moduleType(ModuleType.SSCA)
                                                      .status(LicenseStatus.ACTIVE)
                                                      .startTime(1594684800000L) // 14 July 2020 00:00:00
                                                      .build();
          testLicenses.put(ModuleType.SSCA, sscaModuleLicneseDTO);
          when(call.execute())
              .thenReturn(Response.success(
                  ResponseDTO.newResponse(AccountLicenseDTO.builder().moduleLicenses(testLicenses).build())));
          when(call.clone()).thenReturn(null);
          return call;
        });

    when(settingsClient.getSetting(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
        .thenAnswer((Answer<Call<ResponseDTO<SettingValueResponseDTO>>>) invocation -> {
          Call<ResponseDTO<SettingValueResponseDTO>> call = Mockito.mock(Call.class);
          SettingValueType settingValueType = SettingValueType.BOOLEAN;
          String value = "true";
          when(call.execute())
              .thenReturn(Response.success(ResponseDTO.newResponse(
                  SettingValueResponseDTO.builder().valueType(settingValueType).value(value).build())));
          when(call.clone()).thenReturn(null);
          return call;
        });
  }

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void getHostedVmInitializeTaskParamsWithSmallResourceClassForLinuxArm64() {
    InitializeStepInfo initializeStepInfo = VmInitializeTaskParamsHelper.getHostedVmInitializeStep();
    HostedVmInfraYaml hostedVmInfraYaml = (HostedVmInfraYaml) initializeStepInfo.getInfrastructure();
    CloudRuntime cloudRuntime = (CloudRuntime) hostedVmInfraYaml.getSpec().getRuntime().obtainValue();
    hostedVmInfraYaml.getSpec().setPlatform(
        ParameterField.createValueField(Platform.builder()
                                            .os(ParameterField.createValueField(OSType.Linux))
                                            .arch(ParameterField.createValueField(ArchType.Arm64))
                                            .build()));
    cloudRuntime.getSpec().setSize(ParameterField.createValueField(CIResourceClass.SMALL));

    Map<String, String> volToMountPath = new HashMap<>();
    List<String> internalAccounts = new ArrayList<>();
    internalAccounts.add("random-account");
    volToMountPath.put("shared-0", "/tmp");
    volToMountPath.put("harness", "/harness");

    String stageRuntimeId = "test";
    when(ciLicenseService.getLicenseSummary(any(String.class), eq(ModuleType.CI.name()), any()))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.PAID).edition(Edition.ENTERPRISE).build());
    when(vmInitializeUtils.isCIFreeLicense(any(), eq(ModuleType.CI.name()))).thenReturn(false);
    doNothing().when(vmInitializeUtils).validateStageConfig(any(), any(), anyBoolean(), any());
    when(vmInitializeUtils.getVolumeToMountPath(any(), any(), any(), any())).thenReturn(volToMountPath);
    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(executionSweepingOutputResolver.consume(any(), any(), any(), any())).thenReturn("");
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageDetails.builder().stageRuntimeID(stageRuntimeId).build())
                        .build());

    Map<String, String> m = new HashMap<>();
    when(codebaseUtils.getGitConnector(AmbianceUtils.getNgAccess(ambiance), initializeStepInfo.getCiCodebase(),
             initializeStepInfo.isSkipGitClone(), null))
        .thenReturn(null);
    when(codebaseUtils.getCodebaseVars(any(), any(), any(), any())).thenReturn(m);
    when(
        codebaseUtils.getGitEnvVariables(null, initializeStepInfo.getCiCodebase(), initializeStepInfo.isSkipGitClone()))
        .thenReturn(m);

    when(logServiceUtils.getLogServiceConfig()).thenReturn(LogServiceConfig.builder().baseUrl("1.1.1.1").build());
    when(logServiceUtils.getLogServiceToken(any(), any())).thenReturn("test");
    when(tiServiceUtils.getTiServiceConfig()).thenReturn(TIServiceConfig.builder().baseUrl("1.1.1.2").build());
    when(tiServiceUtils.getTIServiceToken(any(), any())).thenReturn("test");
    when(stoServiceUtils.getStoServiceConfig()).thenReturn(STOServiceConfig.builder().baseUrl("1.1.1.3").build());
    when(stoServiceUtils.getSTOServiceToken(any(), eq(List.of("sto-plugin")))).thenReturn("test");
    when(featureFlagService.isEnabled(any(), any())).thenReturn(true);

    when(ciExecutionServiceConfig.getHostedVmConfig())
        .thenReturn(HostedVmConfig.builder().splitLinuxAmd64Pool(false).internalAccounts(internalAccounts).build());
    doNothing().when(hostedVmSecretResolver).resolve(any(), any(), eq(true), eq(false));

    DliteVmInitializeTaskParams response =
        vmInitializeTaskParamsBuilder.getHostedVmInitializeTaskParams(initializeStepInfo, ambiance, false);
    assertThat(response.getSetupVmRequest().getId()).isEqualTo(stageRuntimeId);
    assertThat(response.getSetupVmRequest().getPoolID()).isEqualTo("linux-arm64");
    assertThat(response.getSetupVmRequest().getFallbackPoolIDs()).contains("linux-arm64-fallback");
    assertThat(response.getSetupVmRequest().getFallbackPoolIDs()).contains("linux-arm64-europe-west4");
    assertThat(response.getSetupVmRequest().getFallbackPoolIDs()).contains("linux-arm64-asia-southeast1");
    assertThat(response.getSetupVmRequest().getResourceClass()).isEqualTo("small");
    assertThat(response.getSetupVmRequest().getContext().getPipelineExecutionID()).isEqualTo(planExecutionId);
  }

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void getHostedVmInitializeTaskParamsWithLargeResourceClassForLinuxArm64() {
    InitializeStepInfo initializeStepInfo = VmInitializeTaskParamsHelper.getHostedVmInitializeStep();
    HostedVmInfraYaml hostedVmInfraYaml = (HostedVmInfraYaml) initializeStepInfo.getInfrastructure();
    CloudRuntime cloudRuntime = (CloudRuntime) hostedVmInfraYaml.getSpec().getRuntime().obtainValue();
    hostedVmInfraYaml.getSpec().setPlatform(
        ParameterField.createValueField(Platform.builder()
                                            .os(ParameterField.createValueField(OSType.Linux))
                                            .arch(ParameterField.createValueField(ArchType.Arm64))
                                            .build()));
    cloudRuntime.getSpec().setSize(ParameterField.createValueField(CIResourceClass.LARGE));

    Map<String, String> volToMountPath = new HashMap<>();
    List<String> internalAccounts = new ArrayList<>();
    internalAccounts.add("random-account");
    volToMountPath.put("shared-0", "/tmp");
    volToMountPath.put("harness", "/harness");

    String stageRuntimeId = "test";
    when(ciLicenseService.getLicenseSummary(any(String.class), eq(ModuleType.CI.name()), any()))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.PAID).edition(Edition.ENTERPRISE).build());
    when(vmInitializeUtils.isCIFreeLicense(any(), eq(ModuleType.CI.name()))).thenReturn(false);
    doNothing().when(vmInitializeUtils).validateStageConfig(any(), any(), anyBoolean(), any());
    when(vmInitializeUtils.getVolumeToMountPath(any(), any(), any(), any())).thenReturn(volToMountPath);
    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(executionSweepingOutputResolver.consume(any(), any(), any(), any())).thenReturn("");
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageDetails.builder().stageRuntimeID(stageRuntimeId).build())
                        .build());

    Map<String, String> m = new HashMap<>();
    when(codebaseUtils.getGitConnector(AmbianceUtils.getNgAccess(ambiance), initializeStepInfo.getCiCodebase(),
             initializeStepInfo.isSkipGitClone(), null))
        .thenReturn(null);
    when(codebaseUtils.getCodebaseVars(any(), any(), any(), any())).thenReturn(m);
    when(
        codebaseUtils.getGitEnvVariables(null, initializeStepInfo.getCiCodebase(), initializeStepInfo.isSkipGitClone()))
        .thenReturn(m);

    when(logServiceUtils.getLogServiceConfig()).thenReturn(LogServiceConfig.builder().baseUrl("1.1.1.1").build());
    when(logServiceUtils.getLogServiceToken(any(), any())).thenReturn("test");
    when(tiServiceUtils.getTiServiceConfig()).thenReturn(TIServiceConfig.builder().baseUrl("1.1.1.2").build());
    when(tiServiceUtils.getTIServiceToken(any(), any())).thenReturn("test");
    when(stoServiceUtils.getStoServiceConfig()).thenReturn(STOServiceConfig.builder().baseUrl("1.1.1.3").build());
    when(stoServiceUtils.getSTOServiceToken(any(), eq(List.of("sto-plugin")))).thenReturn("test");
    when(featureFlagService.isEnabled(any(), any())).thenReturn(true);

    when(ciExecutionServiceConfig.getHostedVmConfig())
        .thenReturn(HostedVmConfig.builder().splitLinuxAmd64Pool(false).internalAccounts(internalAccounts).build());
    doNothing().when(hostedVmSecretResolver).resolve(any(), any(), eq(true), eq(false));

    DliteVmInitializeTaskParams response =
        vmInitializeTaskParamsBuilder.getHostedVmInitializeTaskParams(initializeStepInfo, ambiance, false);
    assertThat(response.getSetupVmRequest().getId()).isEqualTo(stageRuntimeId);
    assertThat(response.getSetupVmRequest().getPoolID()).isEqualTo("linux-arm64");
    assertThat(response.getSetupVmRequest().getFallbackPoolIDs().size()).isEqualTo(3);
    assertThat(response.getSetupVmRequest().getFallbackPoolIDs()).contains("linux-arm64-fallback");
    assertThat(response.getSetupVmRequest().getFallbackPoolIDs()).contains("linux-arm64-europe-west4");
    assertThat(response.getSetupVmRequest().getFallbackPoolIDs()).contains("linux-arm64-asia-southeast1");
    assertThat(response.getSetupVmRequest().getResourceClass()).isEqualTo("large");
  }

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void getHostedVmInitializeTaskParamsWithXXLargeResourceClassForLinuxArm64() {
    InitializeStepInfo initializeStepInfo = VmInitializeTaskParamsHelper.getHostedVmInitializeStep();
    HostedVmInfraYaml hostedVmInfraYaml = (HostedVmInfraYaml) initializeStepInfo.getInfrastructure();
    CloudRuntime cloudRuntime = (CloudRuntime) hostedVmInfraYaml.getSpec().getRuntime().obtainValue();
    hostedVmInfraYaml.getSpec().setPlatform(
        ParameterField.createValueField(Platform.builder()
                                            .os(ParameterField.createValueField(OSType.Linux))
                                            .arch(ParameterField.createValueField(ArchType.Arm64))
                                            .build()));
    cloudRuntime.getSpec().setSize(ParameterField.createValueField(CIResourceClass.XXLARGE));

    Map<String, String> volToMountPath = new HashMap<>();
    List<String> internalAccounts = new ArrayList<>();
    internalAccounts.add("random-account");
    volToMountPath.put("shared-0", "/tmp");
    volToMountPath.put("harness", "/harness");

    String stageRuntimeId = "test";
    when(ciLicenseService.getLicenseSummary(any(String.class), eq(ModuleType.CI.name()), any()))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.PAID).edition(Edition.ENTERPRISE).build());
    when(vmInitializeUtils.isCIFreeLicense(any(), eq(ModuleType.CI.name()))).thenReturn(false);
    doNothing().when(vmInitializeUtils).validateStageConfig(any(), any(), anyBoolean(), any());
    when(vmInitializeUtils.getVolumeToMountPath(any(), any(), any(), any())).thenReturn(volToMountPath);
    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(executionSweepingOutputResolver.consume(any(), any(), any(), any())).thenReturn("");
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageDetails.builder().stageRuntimeID(stageRuntimeId).build())
                        .build());

    Map<String, String> m = new HashMap<>();
    when(codebaseUtils.getGitConnector(AmbianceUtils.getNgAccess(ambiance), initializeStepInfo.getCiCodebase(),
             initializeStepInfo.isSkipGitClone(), null))
        .thenReturn(null);
    when(codebaseUtils.getCodebaseVars(any(), any(), any(), any())).thenReturn(m);
    when(
        codebaseUtils.getGitEnvVariables(null, initializeStepInfo.getCiCodebase(), initializeStepInfo.isSkipGitClone()))
        .thenReturn(m);

    when(logServiceUtils.getLogServiceConfig()).thenReturn(LogServiceConfig.builder().baseUrl("1.1.1.1").build());
    when(logServiceUtils.getLogServiceToken(any(), any())).thenReturn("test");
    when(tiServiceUtils.getTiServiceConfig()).thenReturn(TIServiceConfig.builder().baseUrl("1.1.1.2").build());
    when(tiServiceUtils.getTIServiceToken(any(), any())).thenReturn("test");
    when(stoServiceUtils.getStoServiceConfig()).thenReturn(STOServiceConfig.builder().baseUrl("1.1.1.3").build());
    when(stoServiceUtils.getSTOServiceToken(any(), eq(List.of("sto-plugin")))).thenReturn("test");
    when(featureFlagService.isEnabled(any(), any())).thenReturn(true);

    when(ciExecutionServiceConfig.getHostedVmConfig())
        .thenReturn(HostedVmConfig.builder().splitLinuxAmd64Pool(false).internalAccounts(internalAccounts).build());
    doNothing().when(hostedVmSecretResolver).resolve(any(), any(), eq(true), eq(false));

    DliteVmInitializeTaskParams response =
        vmInitializeTaskParamsBuilder.getHostedVmInitializeTaskParams(initializeStepInfo, ambiance, false);
    assertThat(response.getSetupVmRequest().getId()).isEqualTo(stageRuntimeId);
    assertThat(response.getSetupVmRequest().getPoolID()).isEqualTo("linux-arm64");
    assertThat(response.getSetupVmRequest().getFallbackPoolIDs().size()).isEqualTo(3);
    assertThat(response.getSetupVmRequest().getFallbackPoolIDs()).contains("linux-arm64-fallback");
    assertThat(response.getSetupVmRequest().getFallbackPoolIDs()).contains("linux-arm64-europe-west4");
    assertThat(response.getSetupVmRequest().getFallbackPoolIDs()).contains("linux-arm64-asia-southeast1");
    assertThat(response.getSetupVmRequest().getResourceClass()).isEqualTo("xxlarge");
  }

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void getHostedVmInitializeTaskParamsWithXXXLargeResourceClassForLinuxArm64() {
    InitializeStepInfo initializeStepInfo = VmInitializeTaskParamsHelper.getHostedVmInitializeStep();
    HostedVmInfraYaml hostedVmInfraYaml = (HostedVmInfraYaml) initializeStepInfo.getInfrastructure();
    CloudRuntime cloudRuntime = (CloudRuntime) hostedVmInfraYaml.getSpec().getRuntime().obtainValue();
    hostedVmInfraYaml.getSpec().setPlatform(
        ParameterField.createValueField(Platform.builder()
                                            .os(ParameterField.createValueField(OSType.Linux))
                                            .arch(ParameterField.createValueField(ArchType.Arm64))
                                            .build()));
    cloudRuntime.getSpec().setSize(ParameterField.createValueField(CIResourceClass.XXXLARGE));

    Map<String, String> volToMountPath = new HashMap<>();
    List<String> internalAccounts = new ArrayList<>();
    internalAccounts.add("random-account");
    volToMountPath.put("shared-0", "/tmp");
    volToMountPath.put("harness", "/harness");

    String stageRuntimeId = "test";
    when(ciLicenseService.getLicenseSummary(any(String.class), eq(ModuleType.CI.name()), any()))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.PAID).edition(Edition.ENTERPRISE).build());
    when(vmInitializeUtils.isCIFreeLicense(any(), eq(ModuleType.CI.name()))).thenReturn(false);
    doNothing().when(vmInitializeUtils).validateStageConfig(any(), any(), anyBoolean(), any());
    when(vmInitializeUtils.getVolumeToMountPath(any(), any(), any(), any())).thenReturn(volToMountPath);
    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(executionSweepingOutputResolver.consume(any(), any(), any(), any())).thenReturn("");
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageDetails.builder().stageRuntimeID(stageRuntimeId).build())
                        .build());

    Map<String, String> m = new HashMap<>();
    when(codebaseUtils.getGitConnector(AmbianceUtils.getNgAccess(ambiance), initializeStepInfo.getCiCodebase(),
             initializeStepInfo.isSkipGitClone(), null))
        .thenReturn(null);
    when(codebaseUtils.getCodebaseVars(any(), any(), any(), any())).thenReturn(m);
    when(
        codebaseUtils.getGitEnvVariables(null, initializeStepInfo.getCiCodebase(), initializeStepInfo.isSkipGitClone()))
        .thenReturn(m);

    when(logServiceUtils.getLogServiceConfig()).thenReturn(LogServiceConfig.builder().baseUrl("1.1.1.1").build());
    when(logServiceUtils.getLogServiceToken(any(), any())).thenReturn("test");
    when(tiServiceUtils.getTiServiceConfig()).thenReturn(TIServiceConfig.builder().baseUrl("1.1.1.2").build());
    when(tiServiceUtils.getTIServiceToken(any(), any())).thenReturn("test");
    when(stoServiceUtils.getStoServiceConfig()).thenReturn(STOServiceConfig.builder().baseUrl("1.1.1.3").build());
    when(stoServiceUtils.getSTOServiceToken(any(), eq(List.of("sto-plugin")))).thenReturn("test");
    when(featureFlagService.isEnabled(any(), any())).thenReturn(true);

    when(ciExecutionServiceConfig.getHostedVmConfig())
        .thenReturn(HostedVmConfig.builder().splitLinuxAmd64Pool(false).internalAccounts(internalAccounts).build());
    doNothing().when(hostedVmSecretResolver).resolve(any(), any(), eq(true), eq(false));

    DliteVmInitializeTaskParams response =
        vmInitializeTaskParamsBuilder.getHostedVmInitializeTaskParams(initializeStepInfo, ambiance, false);
    assertThat(response.getSetupVmRequest().getId()).isEqualTo(stageRuntimeId);
    assertThat(response.getSetupVmRequest().getPoolID()).isEqualTo("linux-arm64");
    assertThat(response.getSetupVmRequest().getFallbackPoolIDs().size()).isEqualTo(3);
    assertThat(response.getSetupVmRequest().getFallbackPoolIDs()).contains("linux-arm64-fallback");
    assertThat(response.getSetupVmRequest().getFallbackPoolIDs()).contains("linux-arm64-europe-west4");
    assertThat(response.getSetupVmRequest().getFallbackPoolIDs()).contains("linux-arm64-asia-southeast1");
    assertThat(response.getSetupVmRequest().getResourceClass()).isEqualTo("xxxlarge");
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void getVmInitializeTaskParams() {
    InitializeStepInfo initializeStepInfo = VmInitializeTaskParamsHelper.getInitializeStep();
    Map<String, String> volToMountPath = new HashMap<>();
    volToMountPath.put("shared-0", "/tmp");
    volToMountPath.put("harness", "/harness");

    String stageRuntimeId = "test";

    doNothing().when(vmInitializeUtils).validateStageConfig(any(), any(), anyBoolean(), any());
    when(ciLicenseService.getLicenseSummary(any(String.class), eq(ModuleType.CI.name()), any()))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.PAID).edition(Edition.FREE).build());
    when(vmInitializeUtils.getVolumeToMountPath(any(), any(), any(), any())).thenReturn(volToMountPath);
    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(executionSweepingOutputResolver.consume(any(), any(), any(), any())).thenReturn("");
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageDetails.builder().stageRuntimeID(stageRuntimeId).build())
                        .build());

    Map<String, String> m = new HashMap<>();
    when(codebaseUtils.getGitConnector(AmbianceUtils.getNgAccess(ambiance), initializeStepInfo.getCiCodebase(),
             initializeStepInfo.isSkipGitClone(), ambiance))
        .thenReturn(null);
    when(codebaseUtils.getCodebaseVars(any(), any(), any(), any())).thenReturn(m);
    when(
        codebaseUtils.getGitEnvVariables(null, initializeStepInfo.getCiCodebase(), initializeStepInfo.isSkipGitClone()))
        .thenReturn(m);

    when(logServiceUtils.getLogServiceConfig()).thenReturn(LogServiceConfig.builder().baseUrl("1.1.1.1").build());
    when(logServiceUtils.getLogServiceToken(any(), any())).thenReturn("test");
    when(tiServiceUtils.getTiServiceConfig()).thenReturn(TIServiceConfig.builder().baseUrl("1.1.1.2").build());
    when(tiServiceUtils.getTIServiceToken(any(), any())).thenReturn("test");
    when(stoServiceUtils.getStoServiceConfig()).thenReturn(STOServiceConfig.builder().baseUrl("1.1.1.3").build());
    when(stoServiceUtils.getSTOServiceToken(any(), eq(List.of("sto-plugin")))).thenReturn("test");
    when(featureFlagService.isEnabled(any(), any())).thenReturn(false);

    CIVmInitializeTaskParams response =
        vmInitializeTaskParamsBuilder.getDirectVmInitializeTaskParams(initializeStepInfo, ambiance, false);
    assertThat(response.getStageRuntimeId()).isEqualTo(stageRuntimeId);
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void getHostedVmInitializeTaskParams() {
    InitializeStepInfo initializeStepInfo = VmInitializeTaskParamsHelper.getHostedVmInitializeStep();
    Map<String, String> volToMountPath = new HashMap<>();
    volToMountPath.put("shared-0", "/tmp");
    volToMountPath.put("harness", "/harness");

    String stageRuntimeId = "test";
    when(ciLicenseService.getLicenseSummary(any(String.class), eq(ModuleType.CI.name()), any()))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.PAID).edition(Edition.FREE).build());
    doNothing().when(vmInitializeUtils).validateStageConfig(any(), any(), anyBoolean(), any());
    when(vmInitializeUtils.getVolumeToMountPath(any(), any(), any(), any())).thenReturn(volToMountPath);
    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(executionSweepingOutputResolver.consume(any(), any(), any(), any())).thenReturn("");
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageDetails.builder().stageRuntimeID(stageRuntimeId).build())
                        .build());

    Map<String, String> m = new HashMap<>();
    when(codebaseUtils.getGitConnector(AmbianceUtils.getNgAccess(ambiance), initializeStepInfo.getCiCodebase(),
             initializeStepInfo.isSkipGitClone(), null))
        .thenReturn(null);
    when(codebaseUtils.getCodebaseVars(any(), any(), any(), any())).thenReturn(m);
    when(
        codebaseUtils.getGitEnvVariables(null, initializeStepInfo.getCiCodebase(), initializeStepInfo.isSkipGitClone()))
        .thenReturn(m);

    when(logServiceUtils.getLogServiceConfig()).thenReturn(LogServiceConfig.builder().baseUrl("1.1.1.1").build());
    when(logServiceUtils.getLogServiceToken(any(), any())).thenReturn("test");
    when(tiServiceUtils.getTiServiceConfig()).thenReturn(TIServiceConfig.builder().baseUrl("1.1.1.2").build());
    when(tiServiceUtils.getTIServiceToken(any(), any())).thenReturn("test");
    when(stoServiceUtils.getStoServiceConfig()).thenReturn(STOServiceConfig.builder().baseUrl("1.1.1.3").build());
    when(stoServiceUtils.getSTOServiceToken(any(), eq(List.of("sto-plugin")))).thenReturn("test");
    when(featureFlagService.isEnabled(any(), any())).thenReturn(false);
    when(ciExecutionServiceConfig.getHostedVmConfig())
        .thenReturn(HostedVmConfig.builder().splitLinuxAmd64Pool(false).build());
    doNothing().when(hostedVmSecretResolver).resolve(any(), any(), eq(true), eq(false));

    DliteVmInitializeTaskParams response =
        vmInitializeTaskParamsBuilder.getHostedVmInitializeTaskParams(initializeStepInfo, ambiance, false);
    assertThat(response.getSetupVmRequest().getId()).isEqualTo(stageRuntimeId);
    assertThat(response.getSetupVmRequest().getFallbackPoolIDs().isEmpty());
    assertThat(response.isDistributed()).isTrue();
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void getHostedVmInitializeTaskParamsWithGCPFreeEnabled() {
    InitializeStepInfo initializeStepInfo = VmInitializeTaskParamsHelper.getHostedVmInitializeStep();
    HostedVmInfraYaml hostedVmInfraYaml = (HostedVmInfraYaml) initializeStepInfo.getInfrastructure();
    CloudRuntime cloudRuntime = (CloudRuntime) hostedVmInfraYaml.getSpec().getRuntime().obtainValue();
    cloudRuntime.getSpec().setSize(ParameterField.createValueField(CIResourceClass.MEDIUM));
    Map<String, String> volToMountPath = new HashMap<>();
    List<String> internalAccounts = new ArrayList<>();
    internalAccounts.add("random-account");
    volToMountPath.put("shared-0", "/tmp");
    volToMountPath.put("harness", "/harness");

    String stageRuntimeId = "test";
    when(ciLicenseService.getLicenseSummary(any(String.class), eq(ModuleType.CI.name()), any()))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.PAID).edition(Edition.FREE).build());
    when(vmInitializeUtils.isCIFreeLicense(any(), eq(ModuleType.CI.name()))).thenReturn(true);
    doNothing().when(vmInitializeUtils).validateStageConfig(any(), any(), anyBoolean(), any());
    when(vmInitializeUtils.getVolumeToMountPath(any(), any(), any(), any())).thenReturn(volToMountPath);
    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(executionSweepingOutputResolver.consume(any(), any(), any(), any())).thenReturn("");
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageDetails.builder().stageRuntimeID(stageRuntimeId).build())
                        .build());

    Map<String, String> m = new HashMap<>();
    when(codebaseUtils.getGitConnector(AmbianceUtils.getNgAccess(ambiance), initializeStepInfo.getCiCodebase(),
             initializeStepInfo.isSkipGitClone(), null))
        .thenReturn(null);
    when(codebaseUtils.getCodebaseVars(any(), any(), any(), any())).thenReturn(m);
    when(
        codebaseUtils.getGitEnvVariables(null, initializeStepInfo.getCiCodebase(), initializeStepInfo.isSkipGitClone()))
        .thenReturn(m);

    when(logServiceUtils.getLogServiceConfig()).thenReturn(LogServiceConfig.builder().baseUrl("1.1.1.1").build());
    when(logServiceUtils.getLogServiceToken(any(), any())).thenReturn("test");
    when(tiServiceUtils.getTiServiceConfig()).thenReturn(TIServiceConfig.builder().baseUrl("1.1.1.2").build());
    when(tiServiceUtils.getTIServiceToken(any(), any())).thenReturn("test");
    when(stoServiceUtils.getStoServiceConfig()).thenReturn(STOServiceConfig.builder().baseUrl("1.1.1.3").build());
    when(stoServiceUtils.getSTOServiceToken(any(), eq(List.of("sto-plugin")))).thenReturn("test");
    when(featureFlagService.isEnabled(FeatureName.CI_FREE_GCP_POOL, "test")).thenReturn(true);
    when(ciExecutionServiceConfig.getHostedVmConfig())
        .thenReturn(HostedVmConfig.builder().splitLinuxAmd64Pool(false).internalAccounts(internalAccounts).build());
    doNothing().when(hostedVmSecretResolver).resolve(any(), any(), eq(true), eq(false));

    DliteVmInitializeTaskParams response =
        vmInitializeTaskParamsBuilder.getHostedVmInitializeTaskParams(initializeStepInfo, ambiance, false);
    assertThat(response.getSetupVmRequest().getId()).isEqualTo(stageRuntimeId);
    assertThat(response.getSetupVmRequest().getPoolID()).isEqualTo("linux-amd64-free");
    assertThat(response.getSetupVmRequest().getFallbackPoolIDs().size()).isEqualTo(3);
    assertThat(response.getSetupVmRequest().getTags().get("freeCI")).isEqualTo("true");
    assertThat(response.isDistributed()).isTrue();
    assertThat(response.getSetupVmRequest().getResourceClass()).isEqualTo("flex");
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void getHostedVmInitializeTaskParamsWithLargeResourceClass() {
    InitializeStepInfo initializeStepInfo = VmInitializeTaskParamsHelper.getHostedVmInitializeStep();
    HostedVmInfraYaml hostedVmInfraYaml = (HostedVmInfraYaml) initializeStepInfo.getInfrastructure();
    CloudRuntime cloudRuntime = (CloudRuntime) hostedVmInfraYaml.getSpec().getRuntime().obtainValue();
    cloudRuntime.getSpec().setSize(ParameterField.createValueField(CIResourceClass.LARGE));
    Map<String, String> volToMountPath = new HashMap<>();
    List<String> internalAccounts = new ArrayList<>();
    internalAccounts.add("random-account");
    volToMountPath.put("shared-0", "/tmp");
    volToMountPath.put("harness", "/harness");

    String stageRuntimeId = "test";
    when(ciLicenseService.getLicenseSummary(any(String.class), eq(ModuleType.CI.name()), any()))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.PAID).edition(Edition.ENTERPRISE).build());
    when(vmInitializeUtils.isCIFreeLicense(any(), eq(ModuleType.CI.name()))).thenReturn(false);
    doNothing().when(vmInitializeUtils).validateStageConfig(any(), any(), anyBoolean(), any());
    when(vmInitializeUtils.getVolumeToMountPath(any(), any(), any(), any())).thenReturn(volToMountPath);
    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(executionSweepingOutputResolver.consume(any(), any(), any(), any())).thenReturn("");
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageDetails.builder().stageRuntimeID(stageRuntimeId).build())
                        .build());

    Map<String, String> m = new HashMap<>();
    when(codebaseUtils.getGitConnector(AmbianceUtils.getNgAccess(ambiance), initializeStepInfo.getCiCodebase(),
             initializeStepInfo.isSkipGitClone(), null))
        .thenReturn(null);
    when(codebaseUtils.getCodebaseVars(any(), any(), any(), any())).thenReturn(m);
    when(
        codebaseUtils.getGitEnvVariables(null, initializeStepInfo.getCiCodebase(), initializeStepInfo.isSkipGitClone()))
        .thenReturn(m);

    when(logServiceUtils.getLogServiceConfig()).thenReturn(LogServiceConfig.builder().baseUrl("1.1.1.1").build());
    when(logServiceUtils.getLogServiceToken(any(), any())).thenReturn("test");
    when(tiServiceUtils.getTiServiceConfig()).thenReturn(TIServiceConfig.builder().baseUrl("1.1.1.2").build());
    when(tiServiceUtils.getTIServiceToken(any(), any())).thenReturn("test");
    when(stoServiceUtils.getStoServiceConfig()).thenReturn(STOServiceConfig.builder().baseUrl("1.1.1.3").build());
    when(stoServiceUtils.getSTOServiceToken(any(), eq(List.of("sto-plugin")))).thenReturn("test");
    when(featureFlagService.isEnabled(any(), any())).thenReturn(true);

    when(ciExecutionServiceConfig.getHostedVmConfig())
        .thenReturn(HostedVmConfig.builder().splitLinuxAmd64Pool(false).internalAccounts(internalAccounts).build());
    doNothing().when(hostedVmSecretResolver).resolve(any(), any(), eq(true), eq(false));

    DliteVmInitializeTaskParams response =
        vmInitializeTaskParamsBuilder.getHostedVmInitializeTaskParams(initializeStepInfo, ambiance, false);
    assertThat(response.getSetupVmRequest().getId()).isEqualTo(stageRuntimeId);
    assertThat(response.getSetupVmRequest().getPoolID()).isEqualTo("linux-amd64");
    assertThat(response.getSetupVmRequest().getFallbackPoolIDs().contains("linux-amd64-fallback")).isTrue();
    assertThat(response.getSetupVmRequest().getResourceClass()).isEqualTo("large");
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void getHostedVmInitializeTaskParamsWithXLargeResourceClassForNonLinuxAMD() {
    InitializeStepInfo initializeStepInfo = VmInitializeTaskParamsHelper.getHostedVmInitializeStep();
    HostedVmInfraYaml hostedVmInfraYaml = (HostedVmInfraYaml) initializeStepInfo.getInfrastructure();
    CloudRuntime cloudRuntime = (CloudRuntime) hostedVmInfraYaml.getSpec().getRuntime().obtainValue();
    hostedVmInfraYaml.getSpec().setPlatform(
        ParameterField.createValueField(Platform.builder()
                                            .os(ParameterField.createValueField(OSType.MacOS))
                                            .arch(ParameterField.createValueField(ArchType.Arm64))
                                            .build()));
    cloudRuntime.getSpec().setSize(ParameterField.createValueField(CIResourceClass.XLARGE));
    Map<String, String> volToMountPath = new HashMap<>();
    List<String> internalAccounts = new ArrayList<>();
    internalAccounts.add("random-account");
    volToMountPath.put("shared-0", "/tmp");
    volToMountPath.put("harness", "/harness");

    String stageRuntimeId = "test";
    when(ciLicenseService.getLicenseSummary(any(String.class), eq(ModuleType.CI.name()), any()))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.PAID).edition(Edition.ENTERPRISE).build());
    when(vmInitializeUtils.isCIFreeLicense(any(), eq(ModuleType.CI.name()))).thenReturn(false);
    doNothing().when(vmInitializeUtils).validateStageConfig(any(), any(), anyBoolean(), any());
    when(vmInitializeUtils.getVolumeToMountPath(any(), any(), any(), any())).thenReturn(volToMountPath);
    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(executionSweepingOutputResolver.consume(any(), any(), any(), any())).thenReturn("");
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageDetails.builder().stageRuntimeID(stageRuntimeId).build())
                        .build());

    Map<String, String> m = new HashMap<>();
    when(codebaseUtils.getGitConnector(AmbianceUtils.getNgAccess(ambiance), initializeStepInfo.getCiCodebase(),
             initializeStepInfo.isSkipGitClone(), null))
        .thenReturn(null);
    when(codebaseUtils.getCodebaseVars(any(), any(), any(), any())).thenReturn(m);
    when(
        codebaseUtils.getGitEnvVariables(null, initializeStepInfo.getCiCodebase(), initializeStepInfo.isSkipGitClone()))
        .thenReturn(m);

    when(logServiceUtils.getLogServiceConfig()).thenReturn(LogServiceConfig.builder().baseUrl("1.1.1.1").build());
    when(logServiceUtils.getLogServiceToken(any(), any())).thenReturn("test");
    when(tiServiceUtils.getTiServiceConfig()).thenReturn(TIServiceConfig.builder().baseUrl("1.1.1.2").build());
    when(tiServiceUtils.getTIServiceToken(any(), any())).thenReturn("test");
    when(stoServiceUtils.getStoServiceConfig()).thenReturn(STOServiceConfig.builder().baseUrl("1.1.1.3").build());
    when(stoServiceUtils.getSTOServiceToken(any(), eq(List.of("sto-plugin")))).thenReturn("test");
    when(featureFlagService.isEnabled(FeatureName.CIE_HOSTED_VMS_MAC, accountId)).thenReturn(true);
    when(ciExecutionServiceConfig.getHostedVmConfig())
        .thenReturn(HostedVmConfig.builder().splitLinuxAmd64Pool(false).internalAccounts(internalAccounts).build());
    doNothing().when(hostedVmSecretResolver).resolve(any(), any(), eq(true), eq(false));

    DliteVmInitializeTaskParams response =
        vmInitializeTaskParamsBuilder.getHostedVmInitializeTaskParams(initializeStepInfo, ambiance, false);
    assertThat(response.getSetupVmRequest().getId()).isEqualTo(stageRuntimeId);
    assertThat(response.getSetupVmRequest().getPoolID()).isEqualTo("macos-arm64-tart");
    assertThat(response.getSetupVmRequest().getFallbackPoolIDs().size()).isEqualTo(1);
    assertThat(response.getSetupVmRequest().getResourceClass()).isEqualTo("flex");
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void getHostedVmInitializeTaskParamsWithTart() {
    InitializeStepInfo initializeStepInfo = VmInitializeTaskParamsHelper.getHostedVmInitializeStep();
    HostedVmInfraYaml hostedVmInfraYaml = (HostedVmInfraYaml) initializeStepInfo.getInfrastructure();
    hostedVmInfraYaml.getSpec().setPlatform(
        ParameterField.createValueField(Platform.builder()
                                            .os(ParameterField.createValueField(OSType.MacOS))
                                            .arch(ParameterField.createValueField(ArchType.Arm64))
                                            .build()));
    Map<String, String> volToMountPath = new HashMap<>();
    List<String> internalAccounts = new ArrayList<>();
    internalAccounts.add("random-account");
    volToMountPath.put("shared-0", "/tmp");
    volToMountPath.put("harness", "/harness");

    String stageRuntimeId = "test";
    when(ciLicenseService.getLicenseSummary(any(String.class), eq(ModuleType.CI.name()), any()))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.PAID).edition(Edition.ENTERPRISE).build());
    when(vmInitializeUtils.isCIFreeLicense(any(), eq(ModuleType.CI.name()))).thenReturn(false);
    doNothing().when(vmInitializeUtils).validateStageConfig(any(), any(), anyBoolean(), any());
    when(vmInitializeUtils.getVolumeToMountPath(any(), any(), any(), any())).thenReturn(volToMountPath);
    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(executionSweepingOutputResolver.consume(any(), any(), any(), any())).thenReturn("");
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageDetails.builder().stageRuntimeID(stageRuntimeId).build())
                        .build());

    Map<String, String> m = new HashMap<>();
    when(codebaseUtils.getGitConnector(AmbianceUtils.getNgAccess(ambiance), initializeStepInfo.getCiCodebase(),
             initializeStepInfo.isSkipGitClone(), null))
        .thenReturn(null);
    when(codebaseUtils.getCodebaseVars(any(), any(), any(), any())).thenReturn(m);
    when(
        codebaseUtils.getGitEnvVariables(null, initializeStepInfo.getCiCodebase(), initializeStepInfo.isSkipGitClone()))
        .thenReturn(m);

    when(logServiceUtils.getLogServiceConfig()).thenReturn(LogServiceConfig.builder().baseUrl("1.1.1.1").build());
    when(logServiceUtils.getLogServiceToken(any(), any())).thenReturn("test");
    when(tiServiceUtils.getTiServiceConfig()).thenReturn(TIServiceConfig.builder().baseUrl("1.1.1.2").build());
    when(tiServiceUtils.getTIServiceToken(any(), any())).thenReturn("test");
    when(stoServiceUtils.getStoServiceConfig()).thenReturn(STOServiceConfig.builder().baseUrl("1.1.1.3").build());
    when(stoServiceUtils.getSTOServiceToken(any(), eq(List.of("sto-plugin")))).thenReturn("test");
    when(featureFlagService.isEnabled(FeatureName.CIE_HOSTED_VMS_MAC, accountId)).thenReturn(true);
    when(ciExecutionServiceConfig.getHostedVmConfig())
        .thenReturn(HostedVmConfig.builder().splitLinuxAmd64Pool(false).internalAccounts(internalAccounts).build());
    doNothing().when(hostedVmSecretResolver).resolve(any(), any(), eq(true), eq(false));

    DliteVmInitializeTaskParams response =
        vmInitializeTaskParamsBuilder.getHostedVmInitializeTaskParams(initializeStepInfo, ambiance, false);
    assertThat(response.getSetupVmRequest().getId()).isEqualTo(stageRuntimeId);
    assertThat(response.getSetupVmRequest().getPoolID()).isEqualTo("macos-arm64-tart");
    assertThat(response.getSetupVmRequest().getFallbackPoolIDs().size()).isEqualTo(1);
    assertThat(response.getSetupVmRequest().getFallbackPoolIDs().contains("macos-arm64")).isTrue();
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void getHostedVmInitializeTaskParamsWithXLargeResourceClassForNonLinuxAMDAndStageVarSet() {
    InitializeStepInfo initializeStepInfo = VmInitializeTaskParamsHelper.getHostedVmInitializeStep();
    HostedVmInfraYaml hostedVmInfraYaml = (HostedVmInfraYaml) initializeStepInfo.getInfrastructure();
    CloudRuntime cloudRuntime = (CloudRuntime) hostedVmInfraYaml.getSpec().getRuntime().obtainValue();
    hostedVmInfraYaml.getSpec().setPlatform(
        ParameterField.createValueField(Platform.builder()
                                            .os(ParameterField.createValueField(OSType.Linux))
                                            .arch(ParameterField.createValueField(ArchType.Arm64))
                                            .build()));
    cloudRuntime.getSpec().setSize(ParameterField.createValueField(CIResourceClass.XLARGE));
    Map<String, String> volToMountPath = new HashMap<>();
    List<String> internalAccounts = new ArrayList<>();
    internalAccounts.add("random-account");
    volToMountPath.put("shared-0", "/tmp");
    volToMountPath.put("harness", "/harness");

    String stageRuntimeId = "test";
    when(ciLicenseService.getLicenseSummary(any(String.class), eq(ModuleType.CI.name()), any()))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.PAID).edition(Edition.ENTERPRISE).build());
    when(vmInitializeUtils.isCIFreeLicense(any(), eq(ModuleType.CI.name()))).thenReturn(false);
    doNothing().when(vmInitializeUtils).validateStageConfig(any(), any(), anyBoolean(), any());
    when(vmInitializeUtils.getVolumeToMountPath(any(), any(), any(), any())).thenReturn(volToMountPath);
    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(executionSweepingOutputResolver.consume(any(), any(), any(), any())).thenReturn("");
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageDetails.builder().stageRuntimeID(stageRuntimeId).build())
                        .build());

    Map<String, String> m = new HashMap<>();
    when(codebaseUtils.getGitConnector(AmbianceUtils.getNgAccess(ambiance), initializeStepInfo.getCiCodebase(),
             initializeStepInfo.isSkipGitClone(), null))
        .thenReturn(null);
    when(codebaseUtils.getCodebaseVars(any(), any(), any(), any())).thenReturn(m);
    when(
        codebaseUtils.getGitEnvVariables(null, initializeStepInfo.getCiCodebase(), initializeStepInfo.isSkipGitClone()))
        .thenReturn(m);

    when(logServiceUtils.getLogServiceConfig()).thenReturn(LogServiceConfig.builder().baseUrl("1.1.1.1").build());
    when(logServiceUtils.getLogServiceToken(any(), any())).thenReturn("test");
    when(tiServiceUtils.getTiServiceConfig()).thenReturn(TIServiceConfig.builder().baseUrl("1.1.1.2").build());
    when(tiServiceUtils.getTIServiceToken(any(), any())).thenReturn("test");
    when(stoServiceUtils.getStoServiceConfig()).thenReturn(STOServiceConfig.builder().baseUrl("1.1.1.3").build());
    when(stoServiceUtils.getSTOServiceToken(any(), eq(List.of("sto-plugin")))).thenReturn("test");
    when(featureFlagService.isEnabled(any(), any())).thenReturn(true);

    when(ciExecutionServiceConfig.getHostedVmConfig())
        .thenReturn(HostedVmConfig.builder().splitLinuxAmd64Pool(false).internalAccounts(internalAccounts).build());
    doNothing().when(hostedVmSecretResolver).resolve(any(), any(), eq(true), eq(false));

    DliteVmInitializeTaskParams response =
        vmInitializeTaskParamsBuilder.getHostedVmInitializeTaskParams(initializeStepInfo, ambiance, false);
    assertThat(response.getSetupVmRequest().getId()).isEqualTo(stageRuntimeId);
    assertThat(response.getSetupVmRequest().getPoolID()).isEqualTo("linux-arm64");
    assertThat(response.getSetupVmRequest().getFallbackPoolIDs().size()).isEqualTo(3);
    assertThat(response.getSetupVmRequest().getFallbackPoolIDs()).contains("linux-arm64-fallback");
    assertThat(response.getSetupVmRequest().getFallbackPoolIDs()).contains("linux-arm64-europe-west4");
    assertThat(response.getSetupVmRequest().getFallbackPoolIDs()).contains("linux-arm64-asia-southeast1");
    assertThat(response.getSetupVmRequest().getResourceClass()).isEqualTo("xlarge");
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void getHostedVmInitializeTaskParamsWithXLargeResourceClass() {
    InitializeStepInfo initializeStepInfo = VmInitializeTaskParamsHelper.getHostedVmInitializeStep();
    HostedVmInfraYaml hostedVmInfraYaml = (HostedVmInfraYaml) initializeStepInfo.getInfrastructure();
    CloudRuntime cloudRuntime = (CloudRuntime) hostedVmInfraYaml.getSpec().getRuntime().obtainValue();
    cloudRuntime.getSpec().setSize(ParameterField.createValueField(CIResourceClass.XLARGE));
    Map<String, String> volToMountPath = new HashMap<>();
    List<String> internalAccounts = new ArrayList<>();
    internalAccounts.add("random-account");
    volToMountPath.put("shared-0", "/tmp");
    volToMountPath.put("harness", "/harness");

    String stageRuntimeId = "test";
    when(ciLicenseService.getLicenseSummary(any(String.class), eq(ModuleType.CI.name()), any()))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.PAID).edition(Edition.ENTERPRISE).build());
    when(vmInitializeUtils.isCIFreeLicense(any(), eq(ModuleType.CI.name()))).thenReturn(false);
    doNothing().when(vmInitializeUtils).validateStageConfig(any(), any(), anyBoolean(), any());
    when(vmInitializeUtils.getVolumeToMountPath(any(), any(), any(), any())).thenReturn(volToMountPath);
    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(executionSweepingOutputResolver.consume(any(), any(), any(), any())).thenReturn("");
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageDetails.builder().stageRuntimeID(stageRuntimeId).build())
                        .build());

    Map<String, String> m = new HashMap<>();
    when(codebaseUtils.getGitConnector(AmbianceUtils.getNgAccess(ambiance), initializeStepInfo.getCiCodebase(),
             initializeStepInfo.isSkipGitClone(), null))
        .thenReturn(null);
    when(codebaseUtils.getCodebaseVars(any(), any(), any(), any())).thenReturn(m);
    when(
        codebaseUtils.getGitEnvVariables(null, initializeStepInfo.getCiCodebase(), initializeStepInfo.isSkipGitClone()))
        .thenReturn(m);

    when(logServiceUtils.getLogServiceConfig()).thenReturn(LogServiceConfig.builder().baseUrl("1.1.1.1").build());
    when(logServiceUtils.getLogServiceToken(any(), any())).thenReturn("test");
    when(tiServiceUtils.getTiServiceConfig()).thenReturn(TIServiceConfig.builder().baseUrl("1.1.1.2").build());
    when(tiServiceUtils.getTIServiceToken(any(), any())).thenReturn("test");
    when(stoServiceUtils.getStoServiceConfig()).thenReturn(STOServiceConfig.builder().baseUrl("1.1.1.3").build());
    when(stoServiceUtils.getSTOServiceToken(any(), eq(List.of("sto-plugin")))).thenReturn("test");
    when(featureFlagService.isEnabled(any(), any())).thenReturn(true);

    when(ciExecutionServiceConfig.getHostedVmConfig())
        .thenReturn(HostedVmConfig.builder().splitLinuxAmd64Pool(false).internalAccounts(internalAccounts).build());
    doNothing().when(hostedVmSecretResolver).resolve(any(), any(), eq(true), eq(false));

    DliteVmInitializeTaskParams response =
        vmInitializeTaskParamsBuilder.getHostedVmInitializeTaskParams(initializeStepInfo, ambiance, false);
    assertThat(response.getSetupVmRequest().getId()).isEqualTo(stageRuntimeId);
    assertThat(response.getSetupVmRequest().getPoolID()).isEqualTo("linux-amd64");
    assertThat(response.getSetupVmRequest().getFallbackPoolIDs().contains("linux-amd64-fallback")).isTrue();
    assertThat(response.getSetupVmRequest().getResourceClass()).isEqualTo("xlarge");
  }

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void getHostedVmInitializeTaskParamsWithXXLargeResourceClass() {
    InitializeStepInfo initializeStepInfo = VmInitializeTaskParamsHelper.getHostedVmInitializeStep();
    HostedVmInfraYaml hostedVmInfraYaml = (HostedVmInfraYaml) initializeStepInfo.getInfrastructure();
    CloudRuntime cloudRuntime = (CloudRuntime) hostedVmInfraYaml.getSpec().getRuntime().obtainValue();
    cloudRuntime.getSpec().setSize(ParameterField.createValueField(CIResourceClass.XXLARGE));
    Map<String, String> volToMountPath = new HashMap<>();
    List<String> internalAccounts = new ArrayList<>();
    internalAccounts.add("random-account");
    volToMountPath.put("shared-0", "/tmp");
    volToMountPath.put("harness", "/harness");

    String stageRuntimeId = "test";
    when(ciLicenseService.getLicenseSummary(any(String.class), eq(ModuleType.CI.name()), any()))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.PAID).edition(Edition.ENTERPRISE).build());
    when(vmInitializeUtils.isCIFreeLicense(any(), eq(ModuleType.CI.name()))).thenReturn(false);
    doNothing().when(vmInitializeUtils).validateStageConfig(any(), any(), anyBoolean(), any());
    when(vmInitializeUtils.getVolumeToMountPath(any(), any(), any(), any())).thenReturn(volToMountPath);
    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(executionSweepingOutputResolver.consume(any(), any(), any(), any())).thenReturn("");
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageDetails.builder().stageRuntimeID(stageRuntimeId).build())
                        .build());

    Map<String, String> m = new HashMap<>();
    when(codebaseUtils.getGitConnector(AmbianceUtils.getNgAccess(ambiance), initializeStepInfo.getCiCodebase(),
             initializeStepInfo.isSkipGitClone(), null))
        .thenReturn(null);
    when(codebaseUtils.getCodebaseVars(any(), any(), any(), any())).thenReturn(m);
    when(
        codebaseUtils.getGitEnvVariables(null, initializeStepInfo.getCiCodebase(), initializeStepInfo.isSkipGitClone()))
        .thenReturn(m);

    when(logServiceUtils.getLogServiceConfig()).thenReturn(LogServiceConfig.builder().baseUrl("1.1.1.1").build());
    when(logServiceUtils.getLogServiceToken(any(), any())).thenReturn("test");
    when(tiServiceUtils.getTiServiceConfig()).thenReturn(TIServiceConfig.builder().baseUrl("1.1.1.2").build());
    when(tiServiceUtils.getTIServiceToken(any(), any())).thenReturn("test");
    when(stoServiceUtils.getStoServiceConfig()).thenReturn(STOServiceConfig.builder().baseUrl("1.1.1.3").build());
    when(stoServiceUtils.getSTOServiceToken(any(), eq(List.of("sto-plugin")))).thenReturn("test");
    when(featureFlagService.isEnabled(any(), any())).thenReturn(true);

    when(ciExecutionServiceConfig.getHostedVmConfig())
        .thenReturn(HostedVmConfig.builder().splitLinuxAmd64Pool(false).internalAccounts(internalAccounts).build());
    doNothing().when(hostedVmSecretResolver).resolve(any(), any(), eq(true), eq(false));

    DliteVmInitializeTaskParams response =
        vmInitializeTaskParamsBuilder.getHostedVmInitializeTaskParams(initializeStepInfo, ambiance, false);
    assertThat(response.getSetupVmRequest().getId()).isEqualTo(stageRuntimeId);
    assertThat(response.getSetupVmRequest().getPoolID()).isEqualTo("linux-amd64");
    assertThat(response.getSetupVmRequest().getFallbackPoolIDs().size()).isEqualTo(3);
    assertThat(response.getSetupVmRequest().getFallbackPoolIDs().contains("linux-amd64-fallback")).isTrue();
    assertThat(response.getSetupVmRequest().getFallbackPoolIDs().contains("linux-amd64-east5")).isTrue();
    assertThat(response.getSetupVmRequest().getFallbackPoolIDs().contains("linux-amd64-west4")).isTrue();
    assertThat(response.getSetupVmRequest().getResourceClass()).isEqualTo("xxlarge");
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testSettingUpVMParametersWithPrivateRepo() throws Exception {
    Map<String, String> env = new HashMap<>();
    env.put("DRONE_REPO_LINK", "PRIVATE_REPO");
    CIVmInitializeTaskParams params =
        CIVmInitializeTaskParams.builder().gitConnector(connectorDetails).environment(env).build();
    when(secretSpecBuilder.decryptGitSecretVariables(any()))
        .thenReturn(Map.of("DRONE_NETRC_PASSWORD", SecretParams.builder().value("cGFzc3dvcmQ=").build()));

    try (MockedStatic<GitClientHelper> mockedStatic = mockStatic(GitClientHelper.class)) {
      mockedStatic.when(() -> GitClientHelper.isRepoPrivate(any())).thenReturn(true);
      mockedStatic.when(() -> GitClientHelper.shouldHideSecret(any())).thenCallRealMethod();
      InitializeStepInfo initializeStepInfo = VmInitializeTaskParamsHelper.getHostedVmInitializeStep();

      SetupVmRequest setupVmRequest = Whitebox.invokeMethod(vmInitializeTaskParamsBuilder, "convertHostedSetupParams",
          initializeStepInfo.getInfrastructure(), params, ambiance, Optional.empty(), 10000L, false);
      assertThat(setupVmRequest).isNotNull();
      assertThat(setupVmRequest.getConfig().getEnvs().containsKey("DRONE_NETRC_PASSWORD")).isEqualTo(true);
    }
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testSettingUpVMParametersWithPublicRepo() throws Exception {
    Map<String, String> env = new HashMap<>();
    env.put("DRONE_REPO_LINK", "PUBLIC_REPO");
    CIVmInitializeTaskParams params =
        CIVmInitializeTaskParams.builder().gitConnector(connectorDetails).environment(env).build();
    when(secretSpecBuilder.decryptGitSecretVariables(any()))
        .thenReturn(Map.of("DRONE_NETRC_PASSWORD", SecretParams.builder().value("cGFzc3dvcmQ=").build()));

    try (MockedStatic<GitClientHelper> mockedStatic = mockStatic(GitClientHelper.class)) {
      mockedStatic.when(() -> GitClientHelper.isRepoPrivate(any())).thenReturn(false);
      mockedStatic.when(() -> GitClientHelper.shouldHideSecret(any())).thenCallRealMethod();
      InitializeStepInfo initializeStepInfo = VmInitializeTaskParamsHelper.getHostedVmInitializeStep();
      SetupVmRequest setupVmRequest = Whitebox.invokeMethod(vmInitializeTaskParamsBuilder, "convertHostedSetupParams",
          initializeStepInfo.getInfrastructure(), params, ambiance, Optional.empty(), 10000L, false);
      assertThat(setupVmRequest).isNotNull();
      assertThat(setupVmRequest.getConfig().getEnvs().containsKey("DRONE_NETRC_PASSWORD")).isEqualTo(false);
    }
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testSettingUpVMParametersWithDroneRepoLinkMissing() throws Exception {
    CIVmInitializeTaskParams params =
        CIVmInitializeTaskParams.builder().gitConnector(connectorDetails).environment(new HashMap<>()).build();
    InitializeStepInfo initializeStepInfo = VmInitializeTaskParamsHelper.getHostedVmInitializeStep();
    when(secretSpecBuilder.decryptGitSecretVariables(any()))
        .thenReturn(Map.of("DRONE_NETRC_PASSWORD", SecretParams.builder().value("cGFzc3dvcmQ=").build()));
    SetupVmRequest setupVmRequest = Whitebox.invokeMethod(vmInitializeTaskParamsBuilder, "convertHostedSetupParams",
        initializeStepInfo.getInfrastructure(), params, ambiance, Optional.empty(), 10000L, false);
    assertThat(setupVmRequest).isNotNull();
    assertThat(setupVmRequest.getConfig().getEnvs().containsKey("DRONE_NETRC_PASSWORD")).isEqualTo(true);
  }

  @Test
  @Owner(developers = JAMIE)
  @Category(UnitTests.class)
  public void testSettingUpVMParametersWithTimeout() throws Exception {
    CIVmInitializeTaskParams params =
        CIVmInitializeTaskParams.builder().gitConnector(connectorDetails).environment(new HashMap<>()).build();
    InitializeStepInfo initializeStepInfo = VmInitializeTaskParamsHelper.getHostedVmInitializeStep();
    when(secretSpecBuilder.decryptGitSecretVariables(any()))
        .thenReturn(Map.of("DRONE_NETRC_PASSWORD", SecretParams.builder().value("cGFzc3dvcmQ=").build()));
    SetupVmRequest setupVmRequest = Whitebox.invokeMethod(vmInitializeTaskParamsBuilder, "convertHostedSetupParams",
        initializeStepInfo.getInfrastructure(), params, ambiance, Optional.empty(), 10000L, false);
    assertThat(setupVmRequest).isNotNull();
    assertThat(setupVmRequest.getTimeout()).isEqualTo(10000L);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void getHostedVmInitializeTaskParamsWithNestedVirtualizationFlagEnabledWithMediumResourceClass() {
    InitializeStepInfo initializeStepInfo = VmInitializeTaskParamsHelper.getHostedVmInitializeStep();
    HostedVmInfraYaml hostedVmInfraYaml = (HostedVmInfraYaml) initializeStepInfo.getInfrastructure();
    CloudRuntime cloudRuntime = (CloudRuntime) hostedVmInfraYaml.getSpec().getRuntime().obtainValue();
    cloudRuntime.getSpec().setSize(ParameterField.createValueField(CIResourceClass.MEDIUM));
    cloudRuntime.getSpec().setNestedVirtualization(ParameterField.createValueField(true));

    Map<String, String> volToMountPath = new HashMap<>();
    List<String> internalAccounts = new ArrayList<>();
    internalAccounts.add("random-account");
    volToMountPath.put("shared-0", "/tmp");
    volToMountPath.put("harness", "/harness");

    String stageRuntimeId = "test";
    when(ciLicenseService.getLicenseSummary(any(String.class), eq(ModuleType.CI.name()), any()))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.PAID).edition(Edition.ENTERPRISE).build());
    when(vmInitializeUtils.isCIFreeLicense(any(), eq(ModuleType.CI.name()))).thenReturn(false);
    doNothing().when(vmInitializeUtils).validateStageConfig(any(), any(), anyBoolean(), any());
    when(vmInitializeUtils.getVolumeToMountPath(any(), any(), any(), any())).thenReturn(volToMountPath);
    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(executionSweepingOutputResolver.consume(any(), any(), any(), any())).thenReturn("");
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageDetails.builder().stageRuntimeID(stageRuntimeId).build())
                        .build());

    Map<String, String> m = new HashMap<>();
    when(codebaseUtils.getGitConnector(AmbianceUtils.getNgAccess(ambiance), initializeStepInfo.getCiCodebase(),
             initializeStepInfo.isSkipGitClone(), null))
        .thenReturn(null);
    when(codebaseUtils.getCodebaseVars(any(), any(), any(), any())).thenReturn(m);
    when(
        codebaseUtils.getGitEnvVariables(null, initializeStepInfo.getCiCodebase(), initializeStepInfo.isSkipGitClone()))
        .thenReturn(m);

    when(logServiceUtils.getLogServiceConfig()).thenReturn(LogServiceConfig.builder().baseUrl("1.1.1.1").build());
    when(logServiceUtils.getLogServiceToken(any(), any())).thenReturn("test");
    when(tiServiceUtils.getTiServiceConfig()).thenReturn(TIServiceConfig.builder().baseUrl("1.1.1.2").build());
    when(tiServiceUtils.getTIServiceToken(any(), any())).thenReturn("test");
    when(stoServiceUtils.getStoServiceConfig()).thenReturn(STOServiceConfig.builder().baseUrl("1.1.1.3").build());
    when(stoServiceUtils.getSTOServiceToken(any(), eq(List.of("sto-plugin")))).thenReturn("test");
    when(featureFlagService.isEnabled(any(), any())).thenReturn(true);

    when(ciExecutionServiceConfig.getHostedVmConfig())
        .thenReturn(HostedVmConfig.builder().splitLinuxAmd64Pool(false).internalAccounts(internalAccounts).build());
    doNothing().when(hostedVmSecretResolver).resolve(any(), any(), eq(true), eq(false));

    DliteVmInitializeTaskParams response =
        vmInitializeTaskParamsBuilder.getHostedVmInitializeTaskParams(initializeStepInfo, ambiance, false);
    assertThat(response.getSetupVmRequest().getId()).isEqualTo(stageRuntimeId);
    assertThat(response.getSetupVmRequest().getPoolID()).isEqualTo("linux-amd64");
    assertThat(response.getSetupVmRequest().getFallbackPoolIDs().size()).isEqualTo(3);
    assertThat(response.getSetupVmRequest().getFallbackPoolIDs().contains("linux-amd64-west4")).isTrue();
    assertThat(response.getSetupVmRequest().getFallbackPoolIDs().contains("linux-amd64-east5")).isTrue();
    assertThat(response.getSetupVmRequest().getFallbackPoolIDs().contains("linux-amd64-fallback")).isTrue();
    assertThat(response.getSetupVmRequest().getResourceClass()).isEqualTo("medium");
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void getHostedVmInitializeTaskParamsWithNestedVirtualizationFlagEnabledWithXLargeResourceClass() {
    InitializeStepInfo initializeStepInfo = VmInitializeTaskParamsHelper.getHostedVmInitializeStep();
    HostedVmInfraYaml hostedVmInfraYaml = (HostedVmInfraYaml) initializeStepInfo.getInfrastructure();
    CloudRuntime cloudRuntime = (CloudRuntime) hostedVmInfraYaml.getSpec().getRuntime().obtainValue();
    cloudRuntime.getSpec().setSize(ParameterField.createValueField(CIResourceClass.XLARGE));
    cloudRuntime.getSpec().setNestedVirtualization(ParameterField.createValueField(true));

    Map<String, String> volToMountPath = new HashMap<>();
    List<String> internalAccounts = new ArrayList<>();
    internalAccounts.add("random-account");
    volToMountPath.put("shared-0", "/tmp");
    volToMountPath.put("harness", "/harness");

    String stageRuntimeId = "test";
    when(ciLicenseService.getLicenseSummary(any(String.class), eq(ModuleType.CI.name()), any()))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.PAID).edition(Edition.ENTERPRISE).build());
    when(vmInitializeUtils.isCIFreeLicense(any(), eq(ModuleType.CI.name()))).thenReturn(false);
    doNothing().when(vmInitializeUtils).validateStageConfig(any(), any(), anyBoolean(), any());
    when(vmInitializeUtils.getVolumeToMountPath(any(), any(), any(), any())).thenReturn(volToMountPath);
    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(executionSweepingOutputResolver.consume(any(), any(), any(), any())).thenReturn("");
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageDetails.builder().stageRuntimeID(stageRuntimeId).build())
                        .build());

    Map<String, String> m = new HashMap<>();
    when(codebaseUtils.getGitConnector(AmbianceUtils.getNgAccess(ambiance), initializeStepInfo.getCiCodebase(),
             initializeStepInfo.isSkipGitClone(), null))
        .thenReturn(null);
    when(codebaseUtils.getCodebaseVars(any(), any(), any(), any())).thenReturn(m);
    when(
        codebaseUtils.getGitEnvVariables(null, initializeStepInfo.getCiCodebase(), initializeStepInfo.isSkipGitClone()))
        .thenReturn(m);

    when(logServiceUtils.getLogServiceConfig()).thenReturn(LogServiceConfig.builder().baseUrl("1.1.1.1").build());
    when(logServiceUtils.getLogServiceToken(any(), any())).thenReturn("test");
    when(tiServiceUtils.getTiServiceConfig()).thenReturn(TIServiceConfig.builder().baseUrl("1.1.1.2").build());
    when(tiServiceUtils.getTIServiceToken(any(), any())).thenReturn("test");
    when(stoServiceUtils.getStoServiceConfig()).thenReturn(STOServiceConfig.builder().baseUrl("1.1.1.3").build());
    when(stoServiceUtils.getSTOServiceToken(any(), eq(List.of("sto-plugin")))).thenReturn("test");
    when(featureFlagService.isEnabled(any(), any())).thenReturn(true);

    when(ciExecutionServiceConfig.getHostedVmConfig())
        .thenReturn(HostedVmConfig.builder().splitLinuxAmd64Pool(false).internalAccounts(internalAccounts).build());
    doNothing().when(hostedVmSecretResolver).resolve(any(), any(), eq(true), eq(false));

    DliteVmInitializeTaskParams response =
        vmInitializeTaskParamsBuilder.getHostedVmInitializeTaskParams(initializeStepInfo, ambiance, false);
    assertThat(response.getSetupVmRequest().getId()).isEqualTo(stageRuntimeId);
    assertThat(response.getSetupVmRequest().getPoolID()).isEqualTo("linux-amd64");
    assertThat(response.getSetupVmRequest().getFallbackPoolIDs().size()).isEqualTo(3);
    assertThat(response.getSetupVmRequest().getFallbackPoolIDs().contains("linux-amd64-fallback")).isTrue();
    assertThat(response.getSetupVmRequest().getFallbackPoolIDs().contains("linux-amd64-east5")).isTrue();
    assertThat(response.getSetupVmRequest().getFallbackPoolIDs().contains("linux-amd64-west4")).isTrue();
    assertThat(response.getSetupVmRequest().getResourceClass()).isEqualTo("xlarge");
  }

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void getHostedVmInitializeTaskParamForArmFallback() {
    InitializeStepInfo initializeStepInfo = VmInitializeTaskParamsHelper.getHostedVmInitializeStep();
    HostedVmInfraYaml hostedVmInfraYaml = (HostedVmInfraYaml) initializeStepInfo.getInfrastructure();
    hostedVmInfraYaml.getSpec().setPlatform(
        ParameterField.createValueField(Platform.builder()
                                            .os(ParameterField.createValueField(OSType.Linux))
                                            .arch(ParameterField.createValueField(ArchType.Arm64))
                                            .build()));
    Map<String, String> volToMountPath = new HashMap<>();
    List<String> internalAccounts = new ArrayList<>();
    internalAccounts.add("random-account");
    volToMountPath.put("shared-0", "/tmp");
    volToMountPath.put("harness", "/harness");

    String stageRuntimeId = "test";
    when(ciLicenseService.getLicenseSummary(any(String.class), any(), any()))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.PAID).edition(Edition.ENTERPRISE).build());
    when(vmInitializeUtils.isCIFreeLicense(any(), any())).thenReturn(false);
    doNothing().when(vmInitializeUtils).validateStageConfig(any(), any(), anyBoolean(), any());
    when(vmInitializeUtils.getVolumeToMountPath(any(), any(), any(), any())).thenReturn(volToMountPath);
    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(executionSweepingOutputResolver.consume(any(), any(), any(), any())).thenReturn("");
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageDetails.builder().stageRuntimeID(stageRuntimeId).build())
                        .build());

    Map<String, String> m = new HashMap<>();
    when(codebaseUtils.getGitConnector(AmbianceUtils.getNgAccess(ambiance), initializeStepInfo.getCiCodebase(),
             initializeStepInfo.isSkipGitClone(), null))
        .thenReturn(null);
    when(codebaseUtils.getCodebaseVars(any(), any(), any(), any())).thenReturn(m);
    when(
        codebaseUtils.getGitEnvVariables(null, initializeStepInfo.getCiCodebase(), initializeStepInfo.isSkipGitClone()))
        .thenReturn(m);

    when(logServiceUtils.getLogServiceConfig()).thenReturn(LogServiceConfig.builder().baseUrl("1.1.1.1").build());
    when(logServiceUtils.getLogServiceToken(any(), any())).thenReturn("test");
    when(tiServiceUtils.getTiServiceConfig()).thenReturn(TIServiceConfig.builder().baseUrl("1.1.1.2").build());
    when(tiServiceUtils.getTIServiceToken(any(), any())).thenReturn("test");
    when(stoServiceUtils.getStoServiceConfig()).thenReturn(STOServiceConfig.builder().baseUrl("1.1.1.3").build());
    when(stoServiceUtils.getSTOServiceToken(any(), eq(List.of("sto-plugin")))).thenReturn("test");
    when(featureFlagService.isEnabled(any(), any())).thenReturn(true);

    when(ciExecutionServiceConfig.getHostedVmConfig())
        .thenReturn(HostedVmConfig.builder().splitLinuxAmd64Pool(false).internalAccounts(internalAccounts).build());
    doNothing().when(hostedVmSecretResolver).resolve(any(), any(), eq(true), eq(false));

    DliteVmInitializeTaskParams response =
        vmInitializeTaskParamsBuilder.getHostedVmInitializeTaskParams(initializeStepInfo, ambiance, false);
    assertThat(response.getSetupVmRequest().getId()).isEqualTo(stageRuntimeId);
    assertThat(response.getSetupVmRequest().getFallbackPoolIDs().contains("linux-arm64-fallback")).isTrue();
    assertThat(response.getSetupVmRequest().getFallbackPoolIDs().contains("linux-arm64-europe-west4")).isTrue();
    assertThat(response.getSetupVmRequest().getFallbackPoolIDs().contains("linux-arm64-asia-southeast1")).isTrue();
  }

  @Test
  @Owner(developers = TAPAN)
  @Category(UnitTests.class)
  public void testFreeUserBlockedFromUsingNestedVM() {
    InitializeStepInfo initializeStepInfo = VmInitializeTaskParamsHelper.getHostedVmInitializeStep();
    HostedVmInfraYaml hostedVmInfraYaml = (HostedVmInfraYaml) initializeStepInfo.getInfrastructure();
    CloudRuntime cloudRuntime = (CloudRuntime) hostedVmInfraYaml.getSpec().getRuntime().obtainValue();

    cloudRuntime.getSpec().setNestedVirtualization(ParameterField.createValueField(true));
    when(ciLicenseService.getLicenseSummary(any(String.class), eq(ModuleType.CI.name()), any()))
        .thenReturn(CILicenseSummaryDTO.builder().edition(Edition.FREE).build());
    when(vmInitializeUtils.isCIFreeLicense(any(), eq(ModuleType.CI.name()))).thenReturn(true);

    assertThatThrownBy(
        () -> vmInitializeTaskParamsBuilder.getHostedVmInitializeTaskParams(initializeStepInfo, ambiance, false))
        .isInstanceOf(CIStageExecutionException.class)
        .hasMessage("Nested virtualization is not available for free users, please upgrade your license");
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testIsStageContainerLessWithRunner() {
    Function<String, NGVariable> varFn = (String val)
        -> StringNGVariable.builder()
               .name(CIStepInfoUtils.HARNESS_CI_INTERNAL_CONTAINERLESS)
               .type(NGVariableType.STRING)
               .value(ParameterField.createValueField(val))
               .build();

    // Case 1: shouldRouteStageToRunner is false -> expect false
    assertThat(vmInitializeTaskParamsBuilder.isStageContainerLessWithRunner(
                   accountId, false, CIInitializeTaskParams.Type.DOCKER, Collections.emptyList()))
        .isFalse();

    // Case 2: infra not DOCKER -> expect false
    assertThat(vmInitializeTaskParamsBuilder.isStageContainerLessWithRunner(
                   accountId, true, CIInitializeTaskParams.Type.VM, Collections.emptyList()))
        .isFalse();

    // Case 3: stage var explicitly disabled -> false
    List<NGVariable> varsDisabled = List.of(varFn.apply("false"));
    assertThat(vmInitializeTaskParamsBuilder.isStageContainerLessWithRunner(
                   accountId, true, CIInitializeTaskParams.Type.DOCKER, varsDisabled))
        .isFalse();

    // Case 4: stage var enabled -> true
    List<NGVariable> varsEnabled = List.of(varFn.apply("true"));
    when(featureFlagService.isEnabled(FeatureName.CI_LOCAL_CONTAINERLESS_OOTB_STEP_ENABLED, accountId))
        .thenReturn(false);
    assertThat(vmInitializeTaskParamsBuilder.isStageContainerLessWithRunner(
                   accountId, true, CIInitializeTaskParams.Type.DOCKER, varsEnabled))
        .isTrue();

    // Case 5: no stage var but FF enabled -> true
    when(featureFlagService.isEnabled(FeatureName.CI_LOCAL_CONTAINERLESS_OOTB_STEP_ENABLED, accountId))
        .thenReturn(true);
    assertThat(vmInitializeTaskParamsBuilder.isStageContainerLessWithRunner(
                   accountId, true, CIInitializeTaskParams.Type.DOCKER, Collections.emptyList()))
        .isTrue();
  }

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void getHostedVmInitializeTaskParamsWindowsFlex() {
    InitializeStepInfo initializeStepInfo = VmInitializeTaskParamsHelper.getHostedVmInitializeStep();
    HostedVmInfraYaml hostedVmInfraYaml = (HostedVmInfraYaml) initializeStepInfo.getInfrastructure();
    hostedVmInfraYaml.getSpec().setPlatform(
        ParameterField.createValueField(Platform.builder()
                                            .os(ParameterField.createValueField(OSType.Windows))
                                            .arch(ParameterField.createValueField(ArchType.Amd64))
                                            .build()));

    Map<String, String> volToMountPath = new HashMap<>();
    volToMountPath.put("shared-0", "/tmp");
    volToMountPath.put("harness", "/harness");

    String stageRuntimeId = "test";
    when(ciLicenseService.getLicenseSummary(any(String.class), eq(ModuleType.CI.name()), any()))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.PAID).edition(Edition.ENTERPRISE).build());
    when(vmInitializeUtils.isCIFreeLicense(any(), eq(ModuleType.CI.name()))).thenReturn(false);
    doNothing().when(vmInitializeUtils).validateStageConfig(any(), any(), anyBoolean(), any());
    when(vmInitializeUtils.getVolumeToMountPath(any(), any(), any(), any())).thenReturn(volToMountPath);
    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(executionSweepingOutputResolver.consume(any(), any(), any(), any())).thenReturn("");
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageDetails.builder().stageRuntimeID(stageRuntimeId).build())
                        .build());

    Map<String, String> m = new HashMap<>();
    when(codebaseUtils.getGitConnector(AmbianceUtils.getNgAccess(ambiance), initializeStepInfo.getCiCodebase(),
             initializeStepInfo.isSkipGitClone(), null))
        .thenReturn(null);
    when(codebaseUtils.getCodebaseVars(any(), any(), any(), any())).thenReturn(m);
    when(
        codebaseUtils.getGitEnvVariables(null, initializeStepInfo.getCiCodebase(), initializeStepInfo.isSkipGitClone()))
        .thenReturn(m);

    when(logServiceUtils.getLogServiceConfig()).thenReturn(LogServiceConfig.builder().baseUrl("1.1.1.1").build());
    when(logServiceUtils.getLogServiceToken(any(), any())).thenReturn("test");
    when(tiServiceUtils.getTiServiceConfig()).thenReturn(TIServiceConfig.builder().baseUrl("1.1.1.2").build());
    when(tiServiceUtils.getTIServiceToken(any(), any())).thenReturn("test");
    when(stoServiceUtils.getStoServiceConfig()).thenReturn(STOServiceConfig.builder().baseUrl("1.1.1.3").build());
    when(stoServiceUtils.getSTOServiceToken(any(), eq(List.of("sto-plugin")))).thenReturn("test");
    when(featureFlagService.isEnabled(any(), any())).thenReturn(false);

    DliteVmInitializeTaskParams response =
        vmInitializeTaskParamsBuilder.getHostedVmInitializeTaskParams(initializeStepInfo, ambiance, false);
    assertThat(response.getSetupVmRequest().getId()).isEqualTo(stageRuntimeId);
    assertThat(response.getSetupVmRequest().getPoolID()).isEqualTo("windows-amd64");
    assertThat(response.getSetupVmRequest().getFallbackPoolIDs().contains("windows-amd64-fallback")).isTrue();
    assertThat(response.getSetupVmRequest().getFallbackPoolIDs().contains("windows-amd64-west4")).isFalse();
    assertThat(response.getSetupVmRequest().getFallbackPoolIDs().contains("windows-amd64-east5")).isFalse();
    assertThat(response.getSetupVmRequest().getResourceClass()).isEqualTo("small");
  }

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void getHostedVmInitializeTaskParamsWindowsSmall() {
    InitializeStepInfo initializeStepInfo = VmInitializeTaskParamsHelper.getHostedVmInitializeStep();
    HostedVmInfraYaml hostedVmInfraYaml = (HostedVmInfraYaml) initializeStepInfo.getInfrastructure();
    hostedVmInfraYaml.getSpec().setPlatform(
        ParameterField.createValueField(Platform.builder()
                                            .os(ParameterField.createValueField(OSType.Windows))
                                            .arch(ParameterField.createValueField(ArchType.Amd64))
                                            .build()));
    CloudRuntime cloudRuntime = (CloudRuntime) hostedVmInfraYaml.getSpec().getRuntime().obtainValue();
    cloudRuntime.getSpec().setSize(ParameterField.createValueField(CIResourceClass.SMALL));

    Map<String, String> volToMountPath = new HashMap<>();
    volToMountPath.put("shared-0", "/tmp");
    volToMountPath.put("harness", "/harness");

    String stageRuntimeId = "test";
    when(ciLicenseService.getLicenseSummary(any(String.class), eq(ModuleType.CI.name()), any()))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.PAID).edition(Edition.ENTERPRISE).build());
    when(vmInitializeUtils.isCIFreeLicense(any(), eq(ModuleType.CI.name()))).thenReturn(false);
    doNothing().when(vmInitializeUtils).validateStageConfig(any(), any(), anyBoolean(), any());
    when(vmInitializeUtils.getVolumeToMountPath(any(), any(), any(), any())).thenReturn(volToMountPath);
    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(executionSweepingOutputResolver.consume(any(), any(), any(), any())).thenReturn("");
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageDetails.builder().stageRuntimeID(stageRuntimeId).build())
                        .build());

    Map<String, String> m = new HashMap<>();
    when(codebaseUtils.getGitConnector(AmbianceUtils.getNgAccess(ambiance), initializeStepInfo.getCiCodebase(),
             initializeStepInfo.isSkipGitClone(), null))
        .thenReturn(null);
    when(codebaseUtils.getCodebaseVars(any(), any(), any(), any())).thenReturn(m);
    when(
        codebaseUtils.getGitEnvVariables(null, initializeStepInfo.getCiCodebase(), initializeStepInfo.isSkipGitClone()))
        .thenReturn(m);

    when(logServiceUtils.getLogServiceConfig()).thenReturn(LogServiceConfig.builder().baseUrl("1.1.1.1").build());
    when(logServiceUtils.getLogServiceToken(any(), any())).thenReturn("test");
    when(tiServiceUtils.getTiServiceConfig()).thenReturn(TIServiceConfig.builder().baseUrl("1.1.1.2").build());
    when(tiServiceUtils.getTIServiceToken(any(), any())).thenReturn("test");
    when(stoServiceUtils.getStoServiceConfig()).thenReturn(STOServiceConfig.builder().baseUrl("1.1.1.3").build());
    when(stoServiceUtils.getSTOServiceToken(any(), eq(List.of("sto-plugin")))).thenReturn("test");
    when(featureFlagService.isEnabled(any(), any())).thenReturn(false);

    DliteVmInitializeTaskParams response =
        vmInitializeTaskParamsBuilder.getHostedVmInitializeTaskParams(initializeStepInfo, ambiance, false);
    assertThat(response.getSetupVmRequest().getId()).isEqualTo(stageRuntimeId);
    assertThat(response.getSetupVmRequest().getPoolID()).isEqualTo("windows-amd64");
    assertThat(response.getSetupVmRequest().getFallbackPoolIDs().contains("windows-amd64-fallback")).isTrue();
    assertThat(response.getSetupVmRequest().getFallbackPoolIDs().contains("windows-amd64-west4")).isFalse();
    assertThat(response.getSetupVmRequest().getFallbackPoolIDs().contains("windows-amd64-east5")).isFalse();
    assertThat(response.getSetupVmRequest().getResourceClass()).isEqualTo("small");
  }

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void getHostedVmInitializeTaskParamsWindowsMedium() {
    InitializeStepInfo initializeStepInfo = VmInitializeTaskParamsHelper.getHostedVmInitializeStep();
    HostedVmInfraYaml hostedVmInfraYaml = (HostedVmInfraYaml) initializeStepInfo.getInfrastructure();
    hostedVmInfraYaml.getSpec().setPlatform(
        ParameterField.createValueField(Platform.builder()
                                            .os(ParameterField.createValueField(OSType.Windows))
                                            .arch(ParameterField.createValueField(ArchType.Amd64))
                                            .build()));
    CloudRuntime cloudRuntime = (CloudRuntime) hostedVmInfraYaml.getSpec().getRuntime().obtainValue();
    cloudRuntime.getSpec().setSize(ParameterField.createValueField(CIResourceClass.MEDIUM));

    Map<String, String> volToMountPath = new HashMap<>();
    volToMountPath.put("shared-0", "/tmp");
    volToMountPath.put("harness", "/harness");

    String stageRuntimeId = "test";
    when(ciLicenseService.getLicenseSummary(any(String.class), eq(ModuleType.CI.name()), any()))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.PAID).edition(Edition.ENTERPRISE).build());
    when(vmInitializeUtils.isCIFreeLicense(any(), eq(ModuleType.CI.name()))).thenReturn(false);
    doNothing().when(vmInitializeUtils).validateStageConfig(any(), any(), anyBoolean(), any());
    when(vmInitializeUtils.getVolumeToMountPath(any(), any(), any(), any())).thenReturn(volToMountPath);
    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(executionSweepingOutputResolver.consume(any(), any(), any(), any())).thenReturn("");
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageDetails.builder().stageRuntimeID(stageRuntimeId).build())
                        .build());

    Map<String, String> m = new HashMap<>();
    when(codebaseUtils.getGitConnector(AmbianceUtils.getNgAccess(ambiance), initializeStepInfo.getCiCodebase(),
             initializeStepInfo.isSkipGitClone(), null))
        .thenReturn(null);
    when(codebaseUtils.getCodebaseVars(any(), any(), any(), any())).thenReturn(m);
    when(
        codebaseUtils.getGitEnvVariables(null, initializeStepInfo.getCiCodebase(), initializeStepInfo.isSkipGitClone()))
        .thenReturn(m);

    when(logServiceUtils.getLogServiceConfig()).thenReturn(LogServiceConfig.builder().baseUrl("1.1.1.1").build());
    when(logServiceUtils.getLogServiceToken(any(), any())).thenReturn("test");
    when(tiServiceUtils.getTiServiceConfig()).thenReturn(TIServiceConfig.builder().baseUrl("1.1.1.2").build());
    when(tiServiceUtils.getTIServiceToken(any(), any())).thenReturn("test");
    when(stoServiceUtils.getStoServiceConfig()).thenReturn(STOServiceConfig.builder().baseUrl("1.1.1.3").build());
    when(stoServiceUtils.getSTOServiceToken(any(), eq(List.of("sto-plugin")))).thenReturn("test");
    when(featureFlagService.isEnabled(any(), any())).thenReturn(false);

    DliteVmInitializeTaskParams response =
        vmInitializeTaskParamsBuilder.getHostedVmInitializeTaskParams(initializeStepInfo, ambiance, false);
    assertThat(response.getSetupVmRequest().getId()).isEqualTo(stageRuntimeId);
    assertThat(response.getSetupVmRequest().getPoolID()).isEqualTo("windows-amd64");
    assertThat(response.getSetupVmRequest().getFallbackPoolIDs().contains("windows-amd64-fallback")).isTrue();
    assertThat(response.getSetupVmRequest().getFallbackPoolIDs().contains("windows-amd64-west4")).isFalse();
    assertThat(response.getSetupVmRequest().getFallbackPoolIDs().contains("windows-amd64-east5")).isFalse();
    assertThat(response.getSetupVmRequest().getResourceClass()).isEqualTo("medium");
  }

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void getHostedVmInitializeTaskParamsWindowsLarge() {
    InitializeStepInfo initializeStepInfo = VmInitializeTaskParamsHelper.getHostedVmInitializeStep();
    HostedVmInfraYaml hostedVmInfraYaml = (HostedVmInfraYaml) initializeStepInfo.getInfrastructure();
    hostedVmInfraYaml.getSpec().setPlatform(
        ParameterField.createValueField(Platform.builder()
                                            .os(ParameterField.createValueField(OSType.Windows))
                                            .arch(ParameterField.createValueField(ArchType.Amd64))
                                            .build()));
    CloudRuntime cloudRuntime = (CloudRuntime) hostedVmInfraYaml.getSpec().getRuntime().obtainValue();
    cloudRuntime.getSpec().setSize(ParameterField.createValueField(CIResourceClass.LARGE));

    Map<String, String> volToMountPath = new HashMap<>();
    volToMountPath.put("shared-0", "/tmp");
    volToMountPath.put("harness", "/harness");

    String stageRuntimeId = "test";
    when(ciLicenseService.getLicenseSummary(any(String.class), eq(ModuleType.CI.name()), any()))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.PAID).edition(Edition.ENTERPRISE).build());
    when(vmInitializeUtils.isCIFreeLicense(any(), eq(ModuleType.CI.name()))).thenReturn(false);
    doNothing().when(vmInitializeUtils).validateStageConfig(any(), any(), anyBoolean(), any());
    when(vmInitializeUtils.getVolumeToMountPath(any(), any(), any(), any())).thenReturn(volToMountPath);
    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(executionSweepingOutputResolver.consume(any(), any(), any(), any())).thenReturn("");
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageDetails.builder().stageRuntimeID(stageRuntimeId).build())
                        .build());

    Map<String, String> m = new HashMap<>();
    when(codebaseUtils.getGitConnector(AmbianceUtils.getNgAccess(ambiance), initializeStepInfo.getCiCodebase(),
             initializeStepInfo.isSkipGitClone(), null))
        .thenReturn(null);
    when(codebaseUtils.getCodebaseVars(any(), any(), any(), any())).thenReturn(m);
    when(
        codebaseUtils.getGitEnvVariables(null, initializeStepInfo.getCiCodebase(), initializeStepInfo.isSkipGitClone()))
        .thenReturn(m);

    when(logServiceUtils.getLogServiceConfig()).thenReturn(LogServiceConfig.builder().baseUrl("1.1.1.1").build());
    when(logServiceUtils.getLogServiceToken(any(), any())).thenReturn("test");
    when(tiServiceUtils.getTiServiceConfig()).thenReturn(TIServiceConfig.builder().baseUrl("1.1.1.2").build());
    when(tiServiceUtils.getTIServiceToken(any(), any())).thenReturn("test");
    when(stoServiceUtils.getStoServiceConfig()).thenReturn(STOServiceConfig.builder().baseUrl("1.1.1.3").build());
    when(stoServiceUtils.getSTOServiceToken(any(), eq(List.of("sto-plugin")))).thenReturn("test");
    when(featureFlagService.isEnabled(any(), any())).thenReturn(false);

    DliteVmInitializeTaskParams response =
        vmInitializeTaskParamsBuilder.getHostedVmInitializeTaskParams(initializeStepInfo, ambiance, false);
    assertThat(response.getSetupVmRequest().getId()).isEqualTo(stageRuntimeId);
    assertThat(response.getSetupVmRequest().getPoolID()).isEqualTo("windows-amd64");
    assertThat(response.getSetupVmRequest().getFallbackPoolIDs().contains("windows-amd64-fallback")).isTrue();
    assertThat(response.getSetupVmRequest().getFallbackPoolIDs().contains("windows-amd64-west4")).isFalse();
    assertThat(response.getSetupVmRequest().getFallbackPoolIDs().contains("windows-amd64-east5")).isFalse();
    assertThat(response.getSetupVmRequest().getResourceClass()).isEqualTo("large");
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testRunAsUserFromRuntimeIsSavedInStageInfra() {
    InitializeStepInfo initializeStepInfo = VmInitializeTaskParamsHelper.getHostedVmInitializeStep();
    HostedVmInfraYaml hostedVmInfraYaml = (HostedVmInfraYaml) initializeStepInfo.getInfrastructure();
    CloudRuntime cloudRuntime = (CloudRuntime) hostedVmInfraYaml.getSpec().getRuntime().obtainValue();
    cloudRuntime.getSpec().setRunAsUser(ParameterField.createValueField(1500));

    Map<String, String> volToMountPath = new HashMap<>();
    volToMountPath.put("shared-0", "/tmp");
    volToMountPath.put("harness", "/harness");

    String stageRuntimeId = "test";
    when(ciLicenseService.getLicenseSummary(any(String.class), eq(ModuleType.CI.name()), any()))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.PAID).edition(Edition.FREE).build());
    doNothing().when(vmInitializeUtils).validateStageConfig(any(), any(), anyBoolean(), any());
    when(vmInitializeUtils.getVolumeToMountPath(any(), any(), any(), any())).thenReturn(volToMountPath);
    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(executionSweepingOutputResolver.consume(any(), any(), any(), any())).thenReturn("");
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageDetails.builder().stageRuntimeID(stageRuntimeId).build())
                        .build());

    Map<String, String> m = new HashMap<>();
    when(codebaseUtils.getGitConnector(AmbianceUtils.getNgAccess(ambiance), initializeStepInfo.getCiCodebase(),
             initializeStepInfo.isSkipGitClone(), null))
        .thenReturn(null);
    when(codebaseUtils.getCodebaseVars(any(), any(), any(), any())).thenReturn(m);
    when(
        codebaseUtils.getGitEnvVariables(null, initializeStepInfo.getCiCodebase(), initializeStepInfo.isSkipGitClone()))
        .thenReturn(m);

    when(logServiceUtils.getLogServiceConfig()).thenReturn(LogServiceConfig.builder().baseUrl("1.1.1.1").build());
    when(logServiceUtils.getLogServiceToken(any(), any())).thenReturn("test");
    when(tiServiceUtils.getTiServiceConfig()).thenReturn(TIServiceConfig.builder().baseUrl("1.1.1.2").build());
    when(tiServiceUtils.getTIServiceToken(any(), any())).thenReturn("test");
    when(stoServiceUtils.getStoServiceConfig()).thenReturn(STOServiceConfig.builder().baseUrl("1.1.1.3").build());
    when(stoServiceUtils.getSTOServiceToken(any(), eq(List.of("sto-plugin")))).thenReturn("test");
    when(featureFlagService.isEnabled(any(), any())).thenReturn(false);
    when(ciExecutionServiceConfig.getHostedVmConfig())
        .thenReturn(HostedVmConfig.builder().splitLinuxAmd64Pool(false).build());
    doNothing().when(hostedVmSecretResolver).resolve(any(), any(), eq(true), eq(false));

    DliteVmInitializeTaskParams response =
        vmInitializeTaskParamsBuilder.getHostedVmInitializeTaskParams(initializeStepInfo, ambiance, false);

    // Verify that the runAsUser from runtime is captured
    assertThat(response.getSetupVmRequest().getId()).isEqualTo(stageRuntimeId);
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetPoolIdsAndFallbacks_withStageVariables() {
    // Given
    HostedVmInfraYaml hostedVmInfraYaml =
        HostedVmInfraYaml.builder()
            .type(Infrastructure.Type.HOSTED_VM)
            .spec(HostedVmInfraYaml.HostedVmInfraSpec.builder()
                      .platform(ParameterField.createValueField(
                          Platform.builder().os(ParameterField.createValueField(OSType.Linux)).build()))
                      .build())
            .build();

    Map<String, Object> variables = new HashMap<>();
    variables.put("testVar", "testValue");

    when(ciLicenseService.getLicenseSummary(any(String.class), eq(ModuleType.CI.name()), any()))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.PAID).edition(Edition.FREE).build());
    when(vmInitializeUtils.isCIFreeLicense(any(), eq(ModuleType.CI.name()))).thenReturn(true);
    when(featureFlagService.isEnabled(any(), any())).thenReturn(false);
    when(ciExecutionServiceConfig.getHostedVmConfig())
        .thenReturn(HostedVmConfig.builder().splitLinuxAmd64Pool(false).build());

    // When
    Pair<String, List<String>> result =
        vmInitializeTaskParamsBuilder.getPoolIdsAndFallbacks(hostedVmInfraYaml, variables, ambiance);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getLeft()).isNotNull(); // Pool ID
    assertThat(result.getRight()).isNotNull(); // Fallback pool IDs
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetPoolIdsAndFallbacks_withNullVariables() {
    // Given
    HostedVmInfraYaml hostedVmInfraYaml =
        HostedVmInfraYaml.builder()
            .type(Infrastructure.Type.HOSTED_VM)
            .spec(HostedVmInfraYaml.HostedVmInfraSpec.builder()
                      .platform(ParameterField.createValueField(
                          Platform.builder().os(ParameterField.createValueField(OSType.Linux)).build()))
                      .build())
            .build();

    when(ciLicenseService.getLicenseSummary(any(String.class), eq(ModuleType.CI.name()), any()))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.PAID).edition(Edition.FREE).build());
    when(vmInitializeUtils.isCIFreeLicense(any(), eq(ModuleType.CI.name()))).thenReturn(true);
    when(featureFlagService.isEnabled(any(), any())).thenReturn(false);
    when(ciExecutionServiceConfig.getHostedVmConfig())
        .thenReturn(HostedVmConfig.builder().splitLinuxAmd64Pool(false).build());

    // When
    Pair<String, List<String>> result =
        vmInitializeTaskParamsBuilder.getPoolIdsAndFallbacks(hostedVmInfraYaml, null, ambiance);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getLeft()).isNotNull();
    assertThat(result.getRight()).isNotNull();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testShouldUseGCP32_withVariablePresent() {
    // Given
    Map<String, Object> variables = new HashMap<>();

    HostedVmInfraYaml hostedVmInfraYaml =
        HostedVmInfraYaml.builder()
            .type(Infrastructure.Type.HOSTED_VM)
            .spec(HostedVmInfraYaml.HostedVmInfraSpec.builder()
                      .platform(ParameterField.createValueField(
                          Platform.builder().os(ParameterField.createValueField(OSType.Linux)).build()))
                      .build())
            .build();

    when(ciLicenseService.getLicenseSummary(any(String.class), eq(ModuleType.CI.name()), any()))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.PAID).edition(Edition.FREE).build());
    when(vmInitializeUtils.isCIFreeLicense(any(), eq(ModuleType.CI.name()))).thenReturn(true);
    when(featureFlagService.isEnabled(any(), any())).thenReturn(false);
    when(ciExecutionServiceConfig.getHostedVmConfig())
        .thenReturn(HostedVmConfig.builder().splitLinuxAmd64Pool(false).build());

    // When
    Pair<String, List<String>> result =
        vmInitializeTaskParamsBuilder.getPoolIdsAndFallbacks(hostedVmInfraYaml, variables, ambiance);

    // Then - Should use GCP32 pool
    assertThat(result).isNotNull();
    assertThat(result.getLeft()).isNotNull();
  }

  @Test
  @Owner(developers = TAPAN)
  @Category(UnitTests.class)
  public void testGetPoolIdsAndFallbacks_withSimplifyPoolYamlFF_LinuxAmd64() {
    // Given - Linux AMD64 with simplified pool IDs
    HostedVmInfraYaml hostedVmInfraYaml =
        HostedVmInfraYaml.builder()
            .type(Infrastructure.Type.HOSTED_VM)
            .spec(
                HostedVmInfraYaml.HostedVmInfraSpec.builder()
                    .platform(ParameterField.createValueField(Platform.builder()
                                                                  .os(ParameterField.createValueField(OSType.Linux))
                                                                  .arch(ParameterField.createValueField(ArchType.Amd64))
                                                                  .build()))
                    .build())
            .build();

    when(ciLicenseService.getLicenseSummary(any(String.class), eq(ModuleType.CI.name()), any()))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.PAID).edition(Edition.ENTERPRISE).build());
    when(vmInitializeUtils.isCIFreeLicense(any(), eq(ModuleType.CI.name()))).thenReturn(false);

    when(ciExecutionServiceConfig.getHostedVmConfig())
        .thenReturn(HostedVmConfig.builder().splitLinuxAmd64Pool(false).build());

    // When
    Pair<String, List<String>> result =
        vmInitializeTaskParamsBuilder.getPoolIdsAndFallbacks(hostedVmInfraYaml, null, ambiance);

    // Then - Pool ID should be linux-amd64 and fallbacks should be present
    assertThat(result).isNotNull();
    assertThat(result.getLeft()).isEqualTo("linux-amd64");
    // Fallbacks should contain standard fallback pools without -hw suffix
    assertThat(result.getRight()).isNotEmpty();
    assertThat(result.getRight()).contains("linux-amd64-fallback");
  }

  @Test
  @Owner(developers = TAPAN)
  @Category(UnitTests.class)
  public void testGetPoolIdsAndFallbacks_withSimplifyPoolYamlFF_LinuxArm64() {
    // Given - Linux ARM64 with simplified pool IDs
    HostedVmInfraYaml hostedVmInfraYaml =
        HostedVmInfraYaml.builder()
            .type(Infrastructure.Type.HOSTED_VM)
            .spec(
                HostedVmInfraYaml.HostedVmInfraSpec.builder()
                    .platform(ParameterField.createValueField(Platform.builder()
                                                                  .os(ParameterField.createValueField(OSType.Linux))
                                                                  .arch(ParameterField.createValueField(ArchType.Arm64))
                                                                  .build()))
                    .build())
            .build();

    when(ciLicenseService.getLicenseSummary(any(String.class), eq(ModuleType.CI.name()), any()))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.PAID).edition(Edition.ENTERPRISE).build());
    when(vmInitializeUtils.isCIFreeLicense(any(), eq(ModuleType.CI.name()))).thenReturn(false);

    when(ciExecutionServiceConfig.getHostedVmConfig())
        .thenReturn(HostedVmConfig.builder().splitLinuxArm64Pool(false).build());

    // When
    Pair<String, List<String>> result =
        vmInitializeTaskParamsBuilder.getPoolIdsAndFallbacks(hostedVmInfraYaml, null, ambiance);

    // Then - Pool ID should be linux-arm64 and fallbacks should be present
    assertThat(result).isNotNull();
    assertThat(result.getLeft()).isEqualTo("linux-arm64");
    assertThat(result.getRight()).isNotEmpty();
  }

  @Test
  @Owner(developers = TAPAN)
  @Category(UnitTests.class)
  public void testGetPoolIdsAndFallbacks_withSimplifyPoolYamlFF_WindowsAmd64() {
    // Given - Windows AMD64 with simplified pool IDs
    HostedVmInfraYaml hostedVmInfraYaml =
        HostedVmInfraYaml.builder()
            .type(Infrastructure.Type.HOSTED_VM)
            .spec(
                HostedVmInfraYaml.HostedVmInfraSpec.builder()
                    .platform(ParameterField.createValueField(Platform.builder()
                                                                  .os(ParameterField.createValueField(OSType.Windows))
                                                                  .arch(ParameterField.createValueField(ArchType.Amd64))
                                                                  .build()))
                    .build())
            .build();

    when(ciLicenseService.getLicenseSummary(any(String.class), eq(ModuleType.CI.name()), any()))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.PAID).edition(Edition.ENTERPRISE).build());
    when(vmInitializeUtils.isCIFreeLicense(any(), eq(ModuleType.CI.name()))).thenReturn(false);

    when(ciExecutionServiceConfig.getHostedVmConfig())
        .thenReturn(HostedVmConfig.builder().splitWindowsAmd64Pool(false).build());

    // When
    Pair<String, List<String>> result =
        vmInitializeTaskParamsBuilder.getPoolIdsAndFallbacks(hostedVmInfraYaml, null, ambiance);

    // Then - should return simplified Windows pool IDs
    assertThat(result).isNotNull();
    assertThat(result.getLeft()).isEqualTo("windows-amd64");
    assertThat(result.getRight()).contains("windows-amd64-fallback");
  }

  @Test
  @Owner(developers = TAPAN)
  @Category(UnitTests.class)
  public void testGetPoolIdsAndFallbacks_withSimplifyPoolYamlFF_GcpPool() {
    // Given - Linux AMD64 with GCP pools (post-OVH migration)
    HostedVmInfraYaml hostedVmInfraYaml =
        HostedVmInfraYaml.builder()
            .type(Infrastructure.Type.HOSTED_VM)
            .spec(
                HostedVmInfraYaml.HostedVmInfraSpec.builder()
                    .platform(ParameterField.createValueField(Platform.builder()
                                                                  .os(ParameterField.createValueField(OSType.Linux))
                                                                  .arch(ParameterField.createValueField(ArchType.Amd64))
                                                                  .build()))
                    .build())
            .build();

    when(ciLicenseService.getLicenseSummary(any(String.class), eq(ModuleType.CI.name()), any()))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.PAID).edition(Edition.ENTERPRISE).build());
    when(vmInitializeUtils.isCIFreeLicense(any(), eq(ModuleType.CI.name()))).thenReturn(false);

    when(ciExecutionServiceConfig.getHostedVmConfig())
        .thenReturn(HostedVmConfig.builder().splitLinuxAmd64Pool(false).build());

    // When
    Pair<String, List<String>> result =
        vmInitializeTaskParamsBuilder.getPoolIdsAndFallbacks(hostedVmInfraYaml, null, ambiance);

    // Then - Post-OVH migration: should return GCP pool with appropriate fallbacks
    assertThat(result).isNotNull();
    assertThat(result.getLeft()).contains("linux-amd64");
    assertThat(result.getRight()).isNotEmpty();
  }

  @Test
  @Owner(developers = TAPAN)
  @Category(UnitTests.class)
  public void testGetHostedPoolId_withSimplifyPoolYamlFF_NoHwSuffix_WhenNestedVirtualizationEnabled() {
    // Given - Linux AMD64 with nested virtualization enabled
    ParameterField<Platform> platform =
        ParameterField.createValueField(Platform.builder()
                                            .os(ParameterField.createValueField(OSType.Linux))
                                            .arch(ParameterField.createValueField(ArchType.Amd64))
                                            .build());

    when(ciLicenseService.getLicenseSummary(any(String.class), eq(ModuleType.CI.name()), any()))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.PAID).edition(Edition.ENTERPRISE).build());
    when(ciExecutionServiceConfig.getHostedVmConfig())
        .thenReturn(HostedVmConfig.builder().splitLinuxAmd64Pool(false).build());

    // When - nested virtualization is enabled
    String poolId = vmInitializeTaskParamsBuilder.getHostedPoolId(
        platform, "testAccountId", false, "west-1", true, ModuleType.CI.name());

    // Then - Pool ID should NOT contain "-hw" suffix
    assertThat(poolId).isNotNull();
    assertThat(poolId).doesNotContain("-hw");
    assertThat(poolId).isEqualTo("linux-amd64");
  }

  @Test
  @Owner(developers = SAURABH)
  @Category(UnitTests.class)
  public void testValidateHostedPlatform_whenMacArmFeatureFlagDisabled_thenThrow() {
    Infrastructure infrastructure = getHostedInfra(OSType.MacOS, ArchType.Arm64);
    when(featureFlagService.isEnabled(FeatureName.CIE_HOSTED_VMS_MAC, accountId)).thenReturn(false);

    assertThatThrownBy(() -> vmInitializeTaskParamsBuilder.validateHostedPlatform(infrastructure, accountId))
        .isInstanceOf(CIStageExecutionException.class)
        .hasMessageContaining("Mac Arm64 platform is not enabled for accountId " + accountId);
  }

  @Test
  @Owner(developers = SAURABH)
  @Category(UnitTests.class)
  public void testValidateHostedPlatform_whenMacArmFeatureFlagEnabled_thenNoException() {
    when(featureFlagService.isEnabled(FeatureName.CIE_HOSTED_VMS_MAC, accountId)).thenReturn(true);

    vmInitializeTaskParamsBuilder.validateHostedPlatform(getHostedInfra(OSType.MacOS, ArchType.Arm64), accountId);
  }

  @Test
  @Owner(developers = SAURABH)
  @Category(UnitTests.class)
  public void testValidateHostedPlatform_whenPlatformUnsupported_thenThrow() {
    Infrastructure infrastructure = getHostedInfra(OSType.MacOS, ArchType.Amd64);

    assertThatThrownBy(() -> vmInitializeTaskParamsBuilder.validateHostedPlatform(infrastructure, accountId))
        .isInstanceOf(CIStageExecutionException.class)
        .hasMessageContaining("platform is not supported for hosted builds");
  }

  @Test
  @Owner(developers = SAURABH)
  @Category(UnitTests.class)
  public void testValidateHostedPlatform_whenInfraIsNotHosted_thenSkipValidation() {
    Infrastructure infrastructure = Mockito.mock(Infrastructure.class);
    when(infrastructure.getType()).thenReturn(Infrastructure.Type.KUBERNETES_DIRECT);

    vmInitializeTaskParamsBuilder.validateHostedPlatform(infrastructure, accountId);

    verify(featureFlagService, Mockito.never()).isEnabled(any(), any());
  }

  private Infrastructure getHostedInfra(OSType os, ArchType arch) {
    return HostedVmInfraYaml.builder()
        .type(Infrastructure.Type.HOSTED_VM)
        .spec(HostedVmInfraYaml.HostedVmInfraSpec.builder()
                  .platform(ParameterField.createValueField(Platform.builder()
                                                                .os(ParameterField.createValueField(os))
                                                                .arch(ParameterField.createValueField(arch))
                                                                .build()))
                  .build())
        .build();
  }

  /**
   * Integration test that verifies all *_TOKEN environment variables created during VM init
   * for local/Docker infrastructure are covered by the masking list in RunnerSubmitTaskUtils.
   *
   * This test creates a real CIVmInitializeTaskParams using getDirectVmInitializeTaskParams()
   * (which is used for Docker/local runner infrastructure), extracts the environment variables,
   * and verifies that any env var ending with _TOKEN is registered in the masking list.
   *
   * If this test fails, it means a new token env var was added to the init flow but not
   * added to API_TOKEN_ENV_VARS in RunnerSubmitTaskUtils for masking.
   */
  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testAllTokenEnvVarsInInitAreInMaskingList() {
    // Setup - create a local VM init params (Docker/local runner infrastructure)
    InitializeStepInfo initializeStepInfo = VmInitializeTaskParamsHelper.getInitializeStep();

    Map<String, String> volToMountPath = new HashMap<>();
    volToMountPath.put("shared-0", "/tmp");
    volToMountPath.put("harness", "/harness");

    String stageRuntimeId = "test";

    doNothing().when(vmInitializeUtils).validateStageConfig(any(), any(), anyBoolean(), any());
    when(ciLicenseService.getLicenseSummary(any(String.class), eq(ModuleType.CI.name()), any()))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.PAID).edition(Edition.FREE).build());
    when(vmInitializeUtils.getVolumeToMountPath(any(), any(), any(), any())).thenReturn(volToMountPath);
    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(executionSweepingOutputResolver.consume(any(), any(), any(), any())).thenReturn("");
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageDetails.builder().stageRuntimeID(stageRuntimeId).build())
                        .build());

    Map<String, String> m = new HashMap<>();
    when(codebaseUtils.getGitConnector(AmbianceUtils.getNgAccess(ambiance), initializeStepInfo.getCiCodebase(),
             initializeStepInfo.isSkipGitClone(), ambiance))
        .thenReturn(null);
    when(codebaseUtils.getCodebaseVars(any(), any(), any(), any())).thenReturn(m);
    when(
        codebaseUtils.getGitEnvVariables(null, initializeStepInfo.getCiCodebase(), initializeStepInfo.isSkipGitClone()))
        .thenReturn(m);

    when(logServiceUtils.getLogServiceConfig()).thenReturn(LogServiceConfig.builder().baseUrl("1.1.1.1").build());
    when(logServiceUtils.getLogServiceToken(any(), any())).thenReturn("log-token-value");
    when(tiServiceUtils.getTiServiceConfig()).thenReturn(TIServiceConfig.builder().baseUrl("1.1.1.2").build());
    when(tiServiceUtils.getTIServiceToken(any(), any())).thenReturn("ti-token-value");
    when(stoServiceUtils.getStoServiceConfig()).thenReturn(STOServiceConfig.builder().baseUrl("1.1.1.3").build());
    when(stoServiceUtils.getSTOServiceToken(any(), eq(List.of("sto-plugin")))).thenReturn("sto-token-value");
    when(featureFlagService.isEnabled(any(), any())).thenReturn(false);

    // Act - build the init task params for local/Docker infrastructure
    CIVmInitializeTaskParams response =
        vmInitializeTaskParamsBuilder.getDirectVmInitializeTaskParams(initializeStepInfo, ambiance, false);

    // Extract environment variables from the response
    Map<String, String> initEnvVars = response.getEnvironment();

    // Get the registered masking list
    List<String> registeredTokenEnvVars = RunnerSubmitTaskUtils.getApiTokenEnvVarNames();

    // Find all env var names ending with _TOKEN
    List<String> tokenEnvVarNames = initEnvVars.keySet()
                                        .stream()
                                        .filter(key -> key.toUpperCase().endsWith("_TOKEN"))
                                        .collect(java.util.stream.Collectors.toList());

    // Verify that every _TOKEN env var is in the registered masking list
    for (String tokenEnvVar : tokenEnvVarNames) {
      assertThat(registeredTokenEnvVars)
          .as("Token env var '%s' found in init env vars but not in API_TOKEN_ENV_VARS. "
                  + "Add it to RunnerSubmitTaskUtils.API_TOKEN_ENV_VARS to ensure it gets masked in logs.",
              tokenEnvVar)
          .contains(tokenEnvVar);
    }
  }
  @Test
  @Owner(developers = SHUBHAM_AGARWAL)
  @Category(UnitTests.class)
  public void testGetPoolIdsAndFallbacks_withAitBypass_ciFreeLicenseIsFalse() {
    // Given - AIT bypass returns ENTERPRISE, so ciFreeLicense should be false
    HostedVmInfraYaml hostedVmInfraYaml =
        HostedVmInfraYaml.builder()
            .type(Infrastructure.Type.HOSTED_VM)
            .spec(HostedVmInfraYaml.HostedVmInfraSpec.builder()
                      .platform(ParameterField.createValueField(
                          Platform.builder().os(ParameterField.createValueField(OSType.Linux)).build()))
                      .build())
            .build();

    when(featureFlagService.isEnabled(any(), any())).thenReturn(false);
    when(ciExecutionServiceConfig.getHostedVmConfig())
        .thenReturn(HostedVmConfig.builder().splitLinuxAmd64Pool(false).build());
    CILicenseSummaryDTO license = CILicenseSummaryDTO.builder().edition(Edition.ENTERPRISE).build();
    when(ciLicenseService.getLicenseSummary(any(String.class), any(), any())).thenReturn(license);

    // When
    Pair<String, List<String>> result =
        vmInitializeTaskParamsBuilder.getPoolIdsAndFallbacks(hostedVmInfraYaml, null, ambiance);

    // Then - ENTERPRISE license means ciFreeLicense is false
    assertThat(result).isNotNull();
    verify(ciLicenseService, Mockito.atLeastOnce()).getLicenseSummary(any(String.class), any(), any());
  }

  @Test
  @Owner(developers = SHUBHAM_AGARWAL)
  @Category(UnitTests.class)
  public void testGetPoolIdsAndFallbacks_withoutAitBypass_ciFreeLicenseChecked() {
    // Given - FREE license, so ciFreeLicense should be true
    HostedVmInfraYaml hostedVmInfraYaml =
        HostedVmInfraYaml.builder()
            .type(Infrastructure.Type.HOSTED_VM)
            .spec(HostedVmInfraYaml.HostedVmInfraSpec.builder()
                      .platform(ParameterField.createValueField(
                          Platform.builder().os(ParameterField.createValueField(OSType.Linux)).build()))
                      .build())
            .build();

    when(featureFlagService.isEnabled(any(), any())).thenReturn(false);
    when(ciExecutionServiceConfig.getHostedVmConfig())
        .thenReturn(HostedVmConfig.builder().splitLinuxAmd64Pool(false).build());
    CILicenseSummaryDTO freeLicense = CILicenseSummaryDTO.builder().edition(Edition.FREE).build();
    when(ciLicenseService.getLicenseSummary(any(String.class), any(), any())).thenReturn(freeLicense);

    // When
    Pair<String, List<String>> result =
        vmInitializeTaskParamsBuilder.getPoolIdsAndFallbacks(hostedVmInfraYaml, null, ambiance);

    // Then - FREE license means ciFreeLicense is true
    assertThat(result).isNotNull();
    verify(ciLicenseService, Mockito.atLeastOnce()).getLicenseSummary(any(String.class), any(), any());
  }

  @Test
  @Owner(developers = SHUBHAM_AGARWAL)
  @Category(UnitTests.class)
  public void testGetHostedVmInitializeTaskParams_withAitBypass_skipsCiFreeLicenseCheck() {
    InitializeStepInfo initializeStepInfo = VmInitializeTaskParamsHelper.getHostedVmInitializeStep();
    CILicenseUsageUtils mockedLicenseUsageUtils = Mockito.mock(CILicenseUsageUtils.class);
    Whitebox.setInternalState(vmInitializeTaskParamsBuilder, "ciLicenseUsageUtils", mockedLicenseUsageUtils);

    // AIT bypass returns ENTERPRISE — ciFreeLicense will be false
    CILicenseSummaryDTO license = CILicenseSummaryDTO.builder().edition(Edition.ENTERPRISE).build();
    when(ciLicenseService.getLicenseSummary(any(String.class), any(), any())).thenReturn(license);
    when(mockedLicenseUsageUtils.getResourceClass(any(), any(), anyBoolean()))
        .thenThrow(new RuntimeException("stop-after-bypass-check"));

    assertThatThrownBy(
        () -> vmInitializeTaskParamsBuilder.getHostedVmInitializeTaskParams(initializeStepInfo, ambiance, false))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("stop-after-bypass-check");

    verify(ciLicenseService).getLicenseSummary(any(String.class), any(), any());
  }

  @Test
  @Owner(developers = SHUBHAM_AGARWAL)
  @Category(UnitTests.class)
  public void testGetHostedVmInitializeTaskParams_withoutAitBypass_checksCiFreeLicense() {
    InitializeStepInfo initializeStepInfo = VmInitializeTaskParamsHelper.getHostedVmInitializeStep();
    CILicenseUsageUtils mockedLicenseUsageUtils = Mockito.mock(CILicenseUsageUtils.class);
    Whitebox.setInternalState(vmInitializeTaskParamsBuilder, "ciLicenseUsageUtils", mockedLicenseUsageUtils);

    // Regular user — FREE license
    CILicenseSummaryDTO freeLicense = CILicenseSummaryDTO.builder().edition(Edition.FREE).build();
    when(ciLicenseService.getLicenseSummary(any(String.class), any(), any())).thenReturn(freeLicense);
    when(mockedLicenseUsageUtils.getResourceClass(any(), any(), anyBoolean()))
        .thenThrow(new RuntimeException("stop-after-free-license-check"));

    assertThatThrownBy(
        () -> vmInitializeTaskParamsBuilder.getHostedVmInitializeTaskParams(initializeStepInfo, ambiance, false))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("stop-after-free-license-check");

    verify(ciLicenseService).getLicenseSummary(any(String.class), any(), any());
  }

  @Test
  @Owner(developers = SAURABH)
  @Category(UnitTests.class)
  public void testGetPoolIdsAndFallbacks_noResourceClass_LinuxAmd64_addsFallbackPools() {
    HostedVmInfraYaml hostedVmInfraYaml =
        HostedVmInfraYaml.builder()
            .type(Infrastructure.Type.HOSTED_VM)
            .spec(
                HostedVmInfraYaml.HostedVmInfraSpec.builder()
                    .platform(ParameterField.createValueField(Platform.builder()
                                                                  .os(ParameterField.createValueField(OSType.Linux))
                                                                  .arch(ParameterField.createValueField(ArchType.Amd64))
                                                                  .build()))
                    .build())
            .build();

    CILicenseUsageUtils mockedLicenseUsageUtils = Mockito.mock(CILicenseUsageUtils.class);
    Whitebox.setInternalState(vmInitializeTaskParamsBuilder, "ciLicenseUsageUtils", mockedLicenseUsageUtils);

    when(ciLicenseService.getLicenseSummary(any(String.class), eq(ModuleType.CI.name()), any()))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.PAID).edition(Edition.ENTERPRISE).build());
    when(mockedLicenseUsageUtils.getResourceClass(any(), any(), anyBoolean())).thenReturn(Optional.empty());
    when(featureFlagService.isEnabled(eq(FeatureName.CI_FREE_GCP_POOL), any())).thenReturn(false);
    when(ciExecutionServiceConfig.getHostedVmConfig())
        .thenReturn(HostedVmConfig.builder().splitLinuxAmd64Pool(false).build());

    // When
    Pair<String, List<String>> result =
        vmInitializeTaskParamsBuilder.getPoolIdsAndFallbacks(hostedVmInfraYaml, null, ambiance);

    // Then - Should use west-1 pool and have AMD64 regional fallback pools
    assertThat(result).isNotNull();
    assertThat(result.getLeft()).isEqualTo("linux-amd64");
    assertThat(result.getRight()).isNotEmpty();
    assertThat(result.getRight().size()).isEqualTo(3);
    assertThat(result.getRight()).contains("linux-amd64-fallback");
    assertThat(result.getRight()).contains("linux-amd64-east5");
    assertThat(result.getRight()).contains("linux-amd64-west4");
  }

  @Test
  @Owner(developers = SAURABH)
  @Category(UnitTests.class)
  public void testGetPoolIdsAndFallbacks_noResourceClass_LinuxArm64_addsFallbackPools() {
    HostedVmInfraYaml hostedVmInfraYaml =
        HostedVmInfraYaml.builder()
            .type(Infrastructure.Type.HOSTED_VM)
            .spec(
                HostedVmInfraYaml.HostedVmInfraSpec.builder()
                    .platform(ParameterField.createValueField(Platform.builder()
                                                                  .os(ParameterField.createValueField(OSType.Linux))
                                                                  .arch(ParameterField.createValueField(ArchType.Arm64))
                                                                  .build()))
                    .build())
            .build();

    CILicenseUsageUtils mockedLicenseUsageUtils = Mockito.mock(CILicenseUsageUtils.class);
    Whitebox.setInternalState(vmInitializeTaskParamsBuilder, "ciLicenseUsageUtils", mockedLicenseUsageUtils);

    when(ciLicenseService.getLicenseSummary(any(String.class), eq(ModuleType.CI.name()), any()))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.PAID).edition(Edition.ENTERPRISE).build());
    when(mockedLicenseUsageUtils.getResourceClass(any(), any(), anyBoolean())).thenReturn(Optional.empty());
    when(featureFlagService.isEnabled(eq(FeatureName.CI_FREE_GCP_POOL), any())).thenReturn(false);
    when(ciExecutionServiceConfig.getHostedVmConfig())
        .thenReturn(HostedVmConfig.builder().splitLinuxArm64Pool(false).build());

    // When
    Pair<String, List<String>> result =
        vmInitializeTaskParamsBuilder.getPoolIdsAndFallbacks(hostedVmInfraYaml, null, ambiance);

    // Then - Should use west-1 pool and have ARM64 regional fallback pools
    assertThat(result).isNotNull();
    assertThat(result.getLeft()).isEqualTo("linux-arm64");
    assertThat(result.getRight()).isNotEmpty();
    assertThat(result.getRight().size()).isEqualTo(2);
    assertThat(result.getRight()).contains("linux-arm64-europe-west4");
    assertThat(result.getRight()).contains("linux-arm64-asia-southeast1");
  }

  @Test
  @Owner(developers = SAURABH)
  @Category(UnitTests.class)
  public void getDirectVmInitializeTaskParamsSetsMemoryMetricsLogKey() {
    InitializeStepInfo initializeStepInfo = VmInitializeTaskParamsHelper.getInitializeStep();
    Map<String, String> volToMountPath = new HashMap<>();
    volToMountPath.put("shared-0", "/tmp");
    volToMountPath.put("harness", "/harness");

    String stageRuntimeId = "test";

    doNothing().when(vmInitializeUtils).validateStageConfig(any(), any(), anyBoolean(), any());
    when(ciLicenseService.getLicenseSummary(any(String.class), eq(ModuleType.CI.name()), any()))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.PAID).edition(Edition.FREE).build());
    when(vmInitializeUtils.getVolumeToMountPath(any(), any(), any(), any())).thenReturn(volToMountPath);
    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(executionSweepingOutputResolver.consume(any(), any(), any(), any())).thenReturn("");
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageDetails.builder().stageRuntimeID(stageRuntimeId).build())
                        .build());

    Map<String, String> m = new HashMap<>();
    when(codebaseUtils.getGitConnector(AmbianceUtils.getNgAccess(ambiance), initializeStepInfo.getCiCodebase(),
             initializeStepInfo.isSkipGitClone(), ambiance))
        .thenReturn(null);
    when(codebaseUtils.getCodebaseVars(any(), any(), any(), any())).thenReturn(m);
    when(
        codebaseUtils.getGitEnvVariables(null, initializeStepInfo.getCiCodebase(), initializeStepInfo.isSkipGitClone()))
        .thenReturn(m);

    when(logServiceUtils.getLogServiceConfig()).thenReturn(LogServiceConfig.builder().baseUrl("1.1.1.1").build());
    when(logServiceUtils.getLogServiceToken(any(), any())).thenReturn("test");
    when(tiServiceUtils.getTiServiceConfig()).thenReturn(TIServiceConfig.builder().baseUrl("1.1.1.2").build());
    when(tiServiceUtils.getTIServiceToken(any(), any())).thenReturn("test");
    when(stoServiceUtils.getStoServiceConfig()).thenReturn(STOServiceConfig.builder().baseUrl("1.1.1.3").build());
    when(stoServiceUtils.getSTOServiceToken(any(), eq(List.of("sto-plugin")))).thenReturn("test");
    when(featureFlagService.isEnabled(any(), any())).thenReturn(false);

    CIVmInitializeTaskParams response =
        vmInitializeTaskParamsBuilder.getDirectVmInitializeTaskParams(initializeStepInfo, ambiance, false);
    assertThat(response.getMemoryMetricsLogKey()).isNotNull();
    assertThat(response.getMemoryMetricsLogKey()).endsWith("/" + CICommonConstants.MEMORY_METRICS_LOG_KEY_SUFFIX);
  }

  @Test
  @Owner(developers = SAURABH)
  @Category(UnitTests.class)
  public void getHostedVmInitializeTaskParamsSetsMemoryMetricsLogKey() {
    InitializeStepInfo initializeStepInfo = VmInitializeTaskParamsHelper.getHostedVmInitializeStep();
    Map<String, String> volToMountPath = new HashMap<>();
    volToMountPath.put("shared-0", "/tmp");
    volToMountPath.put("harness", "/harness");

    String stageRuntimeId = "test";
    when(ciLicenseService.getLicenseSummary(any(String.class), eq(ModuleType.CI.name()), any()))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.PAID).edition(Edition.FREE).build());
    doNothing().when(vmInitializeUtils).validateStageConfig(any(), any(), anyBoolean(), any());
    when(vmInitializeUtils.getVolumeToMountPath(any(), any(), any(), any())).thenReturn(volToMountPath);
    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(executionSweepingOutputResolver.consume(any(), any(), any(), any())).thenReturn("");
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageDetails.builder().stageRuntimeID(stageRuntimeId).build())
                        .build());

    Map<String, String> m = new HashMap<>();
    when(codebaseUtils.getGitConnector(AmbianceUtils.getNgAccess(ambiance), initializeStepInfo.getCiCodebase(),
             initializeStepInfo.isSkipGitClone(), null))
        .thenReturn(null);
    when(codebaseUtils.getCodebaseVars(any(), any(), any(), any())).thenReturn(m);
    when(
        codebaseUtils.getGitEnvVariables(null, initializeStepInfo.getCiCodebase(), initializeStepInfo.isSkipGitClone()))
        .thenReturn(m);

    when(logServiceUtils.getLogServiceConfig()).thenReturn(LogServiceConfig.builder().baseUrl("1.1.1.1").build());
    when(logServiceUtils.getLogServiceToken(any(), any())).thenReturn("test");
    when(tiServiceUtils.getTiServiceConfig()).thenReturn(TIServiceConfig.builder().baseUrl("1.1.1.2").build());
    when(tiServiceUtils.getTIServiceToken(any(), any())).thenReturn("test");
    when(stoServiceUtils.getStoServiceConfig()).thenReturn(STOServiceConfig.builder().baseUrl("1.1.1.3").build());
    when(stoServiceUtils.getSTOServiceToken(any(), eq(List.of("sto-plugin")))).thenReturn("test");
    when(featureFlagService.isEnabled(any(), any())).thenReturn(false);
    when(ciExecutionServiceConfig.getHostedVmConfig())
        .thenReturn(HostedVmConfig.builder().splitLinuxAmd64Pool(false).build());
    doNothing().when(hostedVmSecretResolver).resolve(any(), any(), eq(true), eq(false));

    DliteVmInitializeTaskParams response =
        vmInitializeTaskParamsBuilder.getHostedVmInitializeTaskParams(initializeStepInfo, ambiance, false);
    assertThat(response.getSetupVmRequest().getConfig().getMemoryMetricsLogKey()).isNotNull();
    assertThat(response.getSetupVmRequest().getConfig().getMemoryMetricsLogKey())
        .endsWith("/" + CICommonConstants.MEMORY_METRICS_LOG_KEY_SUFFIX);
  }
}