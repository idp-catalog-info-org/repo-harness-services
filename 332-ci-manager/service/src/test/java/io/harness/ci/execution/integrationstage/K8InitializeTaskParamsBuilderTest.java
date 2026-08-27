/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.integrationstage;

import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_NETRC_SPARSE_CHECKOUT;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_REMOTE_URL;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_WORKSPACE;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.HARNESS_CI_EXPLICIT_GIT_CLONE_STEP;
import static io.harness.ci.commonconstants.CIExecutionConstants.STEP_MOUNT_PATH;
import static io.harness.ci.commonconstants.CIExecutionConstants.TI_SERVICE_ENDPOINT_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.TI_SERVICE_TOKEN_VARIABLE;
import static io.harness.ci.coverage.CoverageExecutionConstants.COVERAGE_SERVICE_ENDPOINT_VARIABLE;
import static io.harness.ci.coverage.CoverageExecutionConstants.COVERAGE_SERVICE_TOKEN_VARIABLE;
import static io.harness.ci.execution.integrationstage.K8InitializeTaskUtilsHelper.STAGE_ID;
import static io.harness.ci.execution.integrationstage.K8InitializeTaskUtilsHelper.getAddonContainer;
import static io.harness.ci.execution.integrationstage.K8InitializeTaskUtilsHelper.getLiteEngineContainer;
import static io.harness.pms.utils.NGPipelineSettingsConstant.DEFAULT_IMAGE_PULL_POLICY_ADD_ON_CONTAINER_SET_TO_EMPTY_BY_DEFAULT;
import static io.harness.rule.OwnerRule.AKSHAY_KHANDELWAL;
import static io.harness.rule.OwnerRule.DEV_MITTAL;
import static io.harness.rule.OwnerRule.DHIRAJ;
import static io.harness.rule.OwnerRule.GARGI;
import static io.harness.rule.OwnerRule.MARKO;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;
import static io.harness.rule.OwnerRule.SAHITHI;
import static io.harness.rule.OwnerRule.SAI_LAXMAN;
import static io.harness.rule.OwnerRule.SATYA;
import static io.harness.rule.OwnerRule.SHUBHAM;
import static io.harness.rule.OwnerRule.SOURABH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.ModuleType;
import io.harness.beans.FeatureName;
import io.harness.beans.environment.ConnectorConversionInfo;
import io.harness.beans.environment.pod.container.ContainerDefinitionInfo;
import io.harness.beans.environment.pod.container.ContainerImageDetails;
import io.harness.beans.steps.stepinfo.InitializeStepInfo;
import io.harness.beans.sweepingoutputs.ContextElement;
import io.harness.beans.sweepingoutputs.K8PodDetails;
import io.harness.beans.sweepingoutputs.StageDetails;
import io.harness.beans.yaml.extended.ImagePullPolicy;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.beans.yaml.extended.infrastrucutre.k8.SecurityContext;
import io.harness.category.element.UnitTests;
import io.harness.ci.buildstate.SecretUtils;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.config.HarnessRegistryConfig;
import io.harness.ci.execution.buildstate.ConnectorUtils;
import io.harness.ci.execution.buildstate.providers.InternalContainerParamsProvider;
import io.harness.ci.execution.integrationstage.k8s.K8InitializeStepUtils;
import io.harness.ci.execution.integrationstage.k8s.K8InitializeTaskUtils;
import io.harness.ci.execution.utils.ci.HarnessImageUtils;
import io.harness.ci.executionplan.base.CIExecutionTestBase;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.delegate.beans.ci.k8s.CIK8InitializeTaskParams;
import io.harness.delegate.beans.ci.pod.CIContainerType;
import io.harness.delegate.beans.ci.pod.CIK8ContainerParams;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.pod.ContainerCapabilities;
import io.harness.delegate.beans.ci.pod.ContainerSecurityContext;
import io.harness.delegate.beans.connector.DockerConnectorDTO;
import io.harness.delegate.beans.connector.utils.ConnectorType;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.k8s.model.ImageDetails;
import io.harness.licensing.LicenseStatus;
import io.harness.licensing.beans.modules.AccountLicenseDTO;
import io.harness.licensing.beans.modules.ModuleLicenseDTO;
import io.harness.licensing.beans.modules.SSCAModuleLicenseDTO;
import io.harness.licensing.remote.NgLicenseHttpClient;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.ParameterField;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.rule.Owner;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.UserPrincipal;
import io.harness.ssca.client.SSCAServiceUtils;
import io.harness.ssca.execution.SSCALicenseHelper;
import io.harness.yaml.core.timeout.Timeout;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.core.variables.StringNGVariable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.stubbing.Answer;
import retrofit2.Call;
import retrofit2.Response;

public class K8InitializeTaskParamsBuilderTest extends CIExecutionTestBase {
  @InjectMocks private K8InitializeTaskParamsBuilder k8InitializeTaskParamsBuilder;
  @Mock private ConnectorUtils connectorUtils;
  @Mock private K8InitializeStepUtils k8InitializeStepUtils;
  @Mock private K8InitializeServiceUtils k8InitializeServiceUtils;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Mock private InternalContainerParamsProvider internalContainerParamsProvider;
  @Mock private SecretUtils secretUtils;
  @Mock private CodebaseUtils codebaseUtils;
  @Mock private K8InitializeTaskUtils k8InitializeTaskUtils;
  @Spy private K8InitializeTaskUtils spyK8InitializeTaskUtils;
  @Mock private SSCAServiceUtils sscaServiceUtils;
  @Mock private CIFeatureFlagService featureFlagService;
  @InjectMocks SSCALicenseHelper sscaLicenseHelper;
  @Mock NgLicenseHttpClient ngLicenseHttpClient;
  @Mock private CIExecutionServiceConfig ciExecutionServiceConfig;
  @InjectMocks private HarnessImageUtils harnessImageUtils;

  private Ambiance ambiance;
  private static final String accountId = "test";
  private static final String podName = "test";

  @Before
  public void setUp() {
    on(sscaLicenseHelper).set("ngLicenseHttpClient", ngLicenseHttpClient);
    on(k8InitializeTaskParamsBuilder).set("sscaLicenseHelper", sscaLicenseHelper);

    on(harnessImageUtils).set("connectorUtils", connectorUtils);
    on(harnessImageUtils).set("ciExecutionServiceConfig", ciExecutionServiceConfig);
    on(harnessImageUtils).set("featureFlagService", featureFlagService);
    on(k8InitializeTaskParamsBuilder).set("harnessImageUtils", harnessImageUtils);

    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put("accountId", accountId);
    ambiance = Ambiance.newBuilder().putAllSetupAbstractions(setupAbstractions).build();
    MockitoAnnotations.initMocks(this);
    MockitoAnnotations.openMocks(this);

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
    when(k8InitializeTaskUtils.getTopologySpreadConstraintsList(any())).thenCallRealMethod();
    when(k8InitializeTaskUtils.checkStageVarState(any(), any(), any())).thenCallRealMethod();
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void testGetK8InitializeTaskParams() {
    InitializeStepInfo initializeStepInfo = K8InitializeTaskUtilsHelper.getDirectK8Step(false);
    K8PodDetails k8PodDetails = K8PodDetails.builder().accountId(accountId).stageID(STAGE_ID).build();
    when(featureFlagService.isEnabled(eq(FeatureName.CI_PR_MERGE_STRATEGY_BRANCH), any())).thenReturn(true);
    when(featureFlagService.isEnabled(eq(FeatureName.CI_K8S_OVERLAY_YAML), any())).thenReturn(false);
    when(executionSweepingOutputResolver.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(executionSweepingOutputResolver.resolve(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.podDetails)))
        .thenReturn(k8PodDetails);
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageDetails.builder().stageRuntimeID("test").build())
                        .build());
    when(k8InitializeTaskUtils.generatePodName(STAGE_ID)).thenReturn(podName);
    when(k8InitializeTaskUtils.getBuildLabels(ambiance, k8PodDetails)).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getSharedPaths(any())).thenReturn(new ArrayList<>());
    when(k8InitializeTaskUtils.getVolumeToMountPath(any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getVolumeV2ToMountPath(any(), any(), any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getOS(any())).thenReturn(OSType.Linux);
    when(k8InitializeTaskUtils.getLogServiceEnvVariables(any(), any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getTIServiceEnvVariables(any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getSTOServiceEnvVariables(any())).thenReturn(new HashMap<>());
    when(codebaseUtils.getGitEnvVariables(any(), any(), eq(false))).thenReturn(new HashMap<>());
    Map<String, String> map = new HashMap<>();
    map.put("DRONE_REMOTE_URL", "https://github.com/harness/harness-core");
    when(k8InitializeTaskUtils.getCommonStepEnvVariables(any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(map);
    Map<String, String> advancedVarMap = new HashMap<>();
    advancedVarMap.put("DRONE_NETRC_DEBUG", "true");
    advancedVarMap.put("DRONE_NETRC_LFS_ENABLED", "true");
    advancedVarMap.put("DRONE_NETRC_PRE_FETCH", "echo something \ngit config lfs.url https://blah.com");
    when(codebaseUtils.getGitAdvancedVariables(any(), eq(false), any(), any(), any(), anyBoolean()))
        .thenReturn(advancedVarMap);
    when(k8InitializeTaskUtils.getCacheEnvironmentVariable(OSType.Linux)).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getWorkDir()).thenReturn("/harness");
    when(k8InitializeTaskUtils.getCtrSecurityContext(any(), any()))
        .thenReturn(ContainerSecurityContext.builder().build());
    when(internalContainerParamsProvider.getSetupAddonContainerParams(
             any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(getAddonContainer());
    when(internalContainerParamsProvider.getLiteEngineContainerParams(any(), any(), any(), any(), any(), any(), any(),
             any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(),
             any(Map.class)))
        .thenReturn(getLiteEngineContainer());
    when(k8InitializeStepUtils.getStageRequest(any(), any(), anyBoolean())).thenReturn(Pair.of(1024, 1024));
    when(k8InitializeServiceUtils.createServiceContainerDefinitions(any(), any(), any(), any()))
        .thenReturn(new ArrayList<>());
    when(
        k8InitializeStepUtils.createStepContainerDefinitions(any(), any(), any(), any(), any(), any(), any(), anyInt()))
        .thenReturn(Arrays.asList(K8InitializeTaskUtilsHelper.getRunStepContainer(0)));
    doNothing().when(k8InitializeTaskUtils).consumeSweepingOutput(any(), any(), any());
    doNothing().when(k8InitializeTaskUtils).consumeSweepingOutput(any(), any(), any());

    CIK8InitializeTaskParams response =
        k8InitializeTaskParamsBuilder.getK8InitializeTaskParams(initializeStepInfo, ambiance, "", false);
    assertThat(response.getCik8PodParams().getName()).isEqualTo(podName);
    verify(k8InitializeStepUtils, times(1))
        .createStepContainerDefinitions(any(), any(), any(), any(), any(), any(), any(), anyInt());
    // Step container is now at index 0, lite engine is at the end
    assertThat(response.getCik8PodParams().getContainerParamsList().get(0).getEnvVars().get("DRONE_NETRC_DEBUG"))
        .isEqualTo("true");
    assertThat(response.getCik8PodParams().getContainerParamsList().get(0).getEnvVars().get("DRONE_NETRC_LFS_ENABLED"))
        .isEqualTo("true");
    assertThat(response.getCik8PodParams().getContainerParamsList().get(0).getEnvVars().get("DRONE_NETRC_PRE_FETCH"))
        .isEqualTo("echo something \ngit config lfs.url https://blah.com");
    assertThat(response.getCik8PodParams().getContainerParamsList().get(0).getEnvVars().get("DRONE_REMOTE_URL"))
        .isEqualTo("https://github.com/harness/harness-core");
    assertThat(
        response.getCik8PodParams().getContainerParamsList().get(0).getEnvVars().get("DRONE_PR_MERGE_STRATEGY_BRANCH"))
        .isEqualTo("true");
    assertThat(response.getCik8PodParams().getTopologySpreadConstraints().size()).isEqualTo(2);
    assertThat(response.getCik8PodParams().getOverlayYaml()).isNull();
  }

  @Test(expected = CIStageExecutionException.class)
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetK8InitializeTaskParamsWithEmptyInfraConnector() {
    InitializeStepInfo initializeStepInfo = K8InitializeTaskUtilsHelper.getDirectK8Step(false);
    K8sDirectInfraYaml k8sDirectInfraYaml = (K8sDirectInfraYaml) initializeStepInfo.getInfrastructure();
    k8sDirectInfraYaml.getSpec().setConnectorRef(ParameterField.ofNull());
    K8PodDetails k8PodDetails = K8PodDetails.builder().accountId(accountId).stageID(STAGE_ID).build();
    when(executionSweepingOutputResolver.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(executionSweepingOutputResolver.resolve(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.podDetails)))
        .thenReturn(k8PodDetails);
    k8InitializeTaskParamsBuilder.getK8InitializeTaskParams(initializeStepInfo, ambiance, "", false);
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void testGetK8InitializeTaskParamsWithImagePullPolicy() {
    InitializeStepInfo initializeStepInfo = K8InitializeTaskUtilsHelper.getDirectK8Step(true);
    K8sDirectInfraYaml k8sDirectInfraYaml = (K8sDirectInfraYaml) initializeStepInfo.getInfrastructure();
    K8PodDetails k8PodDetails = K8PodDetails.builder().accountId(accountId).stageID(STAGE_ID).build();
    ImagePullPolicy imagePullPolicyValueUsed = ImagePullPolicy.IFNOTPRESENT;

    assertThat(k8sDirectInfraYaml.getSpec().getImagePullPolicy()).isNull();
    ambiance = Ambiance.newBuilder()
                   .setMetadata(ExecutionMetadata.newBuilder()
                                    .putSettingToValueMap(
                                        DEFAULT_IMAGE_PULL_POLICY_ADD_ON_CONTAINER_SET_TO_EMPTY_BY_DEFAULT.getName(),
                                        imagePullPolicyValueUsed.getYamlName())
                                    .build())
                   .build();

    //    k8sDirectInfraYaml.getSpec().setImagePullPolicy(ParameterField.createValueField(ImagePullPolicy.NEVER));

    when(featureFlagService.isEnabled(eq(FeatureName.CI_PR_MERGE_STRATEGY_BRANCH), any())).thenReturn(true);
    when(featureFlagService.isEnabled(eq(FeatureName.CI_K8S_OVERLAY_YAML), any())).thenReturn(false);
    List<NGVariable> variables = new ArrayList<>();
    variables.add(
        StringNGVariable.builder().name("CI_K8S_OVERLAY_YAML").value(ParameterField.createValueField("true")).build());
    initializeStepInfo.setVariables(variables);

    when(executionSweepingOutputResolver.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(executionSweepingOutputResolver.resolve(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.podDetails)))
        .thenReturn(k8PodDetails);
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageDetails.builder().stageRuntimeID("test").build())
                        .build());

    when(k8InitializeTaskUtils.generatePodName(STAGE_ID)).thenReturn(podName);
    when(k8InitializeTaskUtils.getBuildLabels(ambiance, k8PodDetails)).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getSharedPaths(any())).thenReturn(new ArrayList<>());
    when(k8InitializeTaskUtils.getVolumeToMountPath(any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getVolumeV2ToMountPath(any(), any(), any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getOS(any())).thenReturn(OSType.Linux);
    when(k8InitializeTaskUtils.getWorkDir()).thenReturn("/harness");
    when(k8InitializeTaskUtils.getCtrSecurityContext(any(), any()))
        .thenReturn(ContainerSecurityContext.builder().build());
    when(k8InitializeTaskUtils.getLogServiceEnvVariables(any(), any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getTIServiceEnvVariables(any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getSTOServiceEnvVariables(any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getCacheEnvironmentVariable(OSType.Linux)).thenReturn(new HashMap<>());

    when(featureFlagService.isEnabled(
             FeatureName.CI_ENABLE_IMAGE_PULL_POLICY_OVERRIDE_FROM_SETTINGS, AmbianceUtils.getAccountId(ambiance)))
        .thenReturn(true);

    when(codebaseUtils.getGitEnvVariables(any(), any(), eq(false))).thenReturn(new HashMap<>());
    when(codebaseUtils.getGitAdvancedVariables(any(), eq(false), any(), any(), any(), anyBoolean()))
        .thenReturn(new HashMap<>());

    ArgumentCaptor<String> imagePullPolicyCaptor = ArgumentCaptor.forClass(String.class);

    when(internalContainerParamsProvider.getSetupAddonContainerParams(
             any(), any(), any(), any(), any(), any(), any(), imagePullPolicyCaptor.capture()))
        .thenAnswer(invocation -> {
          String capturedPolicy = imagePullPolicyCaptor.getValue();
          return CIK8ContainerParams.builder().name("addon").imagePullPolicy(capturedPolicy).build();
        });

    when(internalContainerParamsProvider.getLiteEngineContainerParams(any(), any(), any(), any(), any(), any(), any(),
             any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), imagePullPolicyCaptor.capture(),
             any(), anyBoolean(), any(Map.class)))
        .thenAnswer(invocation -> {
          String capturedPolicy = imagePullPolicyCaptor.getValue();
          return CIK8ContainerParams.builder().name("engine").imagePullPolicy(capturedPolicy).build();
        });

    when(k8InitializeStepUtils.getStageRequest(any(), any(), anyBoolean())).thenReturn(Pair.of(1024, 1024));
    when(
        k8InitializeStepUtils.createStepContainerDefinitions(any(), any(), any(), any(), any(), any(), any(), anyInt()))
        .thenReturn(Arrays.asList(K8InitializeTaskUtilsHelper.getRunStepContainer(0)));
    when(k8InitializeServiceUtils.createServiceContainerDefinitions(any(), any(), any(), any()))
        .thenReturn(new ArrayList<>());

    CIK8InitializeTaskParams response =
        k8InitializeTaskParamsBuilder.getK8InitializeTaskParams(initializeStepInfo, ambiance, "", false);

    assertThat(response).isNotNull();
    assertThat(response.getCik8PodParams()).isNotNull();

    CIK8ContainerParams initContainerParams = response.getCik8PodParams().getInitContainerParamsList().get(0);
    // Lite engine is now at the end of the list
    List<CIK8ContainerParams> containerParamsList = response.getCik8PodParams().getContainerParamsList();
    CIK8ContainerParams liteEngineContainerParams = containerParamsList.get(containerParamsList.size() - 1);

    assertThat(initContainerParams.getImagePullPolicy()).isEqualTo(imagePullPolicyValueUsed.getYamlName());
    assertThat(liteEngineContainerParams.getImagePullPolicy()).isEqualTo(imagePullPolicyValueUsed.getYamlName());
    assertThat(response.getCik8PodParams().getTopologySpreadConstraints()).isNull();
    assertThat(response.getCik8PodParams().getOverlayYaml()).isNotNull();
    assertThat(response.getCik8PodParams().getOverlayYaml().equals(K8InitializeTaskUtilsHelper.overlaySpecYaml))
        .isTrue();
  }

  @Test
  @Owner(developers = SATYA)
  @Category(UnitTests.class)
  public void testGetK8InitializeTaskParamsWithPrivilegedTrueAndAllowPrivilegedEscalationFalse() {
    InitializeStepInfo initializeStepInfo = K8InitializeTaskUtilsHelper.getDirectK8Step(false);
    K8PodDetails k8PodDetails = K8PodDetails.builder().accountId(accountId).stageID(STAGE_ID).build();
    when(featureFlagService.isEnabled(eq(FeatureName.CI_PR_MERGE_STRATEGY_BRANCH), any())).thenReturn(true);

    when(executionSweepingOutputResolver.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(executionSweepingOutputResolver.resolve(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.podDetails)))
        .thenReturn(k8PodDetails);
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageDetails.builder().stageRuntimeID("test").build())
                        .build());
    when(k8InitializeTaskUtils.generatePodName(STAGE_ID)).thenReturn(podName);
    when(k8InitializeTaskUtils.getBuildLabels(ambiance, k8PodDetails)).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getSharedPaths(any())).thenReturn(new ArrayList<>());
    when(k8InitializeTaskUtils.getVolumeToMountPath(any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getVolumeV2ToMountPath(any(), any(), any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getOS(any())).thenReturn(OSType.Linux);
    when(k8InitializeTaskUtils.getLogServiceEnvVariables(any(), any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getTIServiceEnvVariables(any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getSTOServiceEnvVariables(any())).thenReturn(new HashMap<>());
    when(codebaseUtils.getGitEnvVariables(any(), any(), eq(false))).thenReturn(new HashMap<>());
    Map<String, String> map = new HashMap<>();
    map.put("DRONE_REMOTE_URL", "https://github.com/harness/harness-core");
    when(k8InitializeTaskUtils.getCommonStepEnvVariables(any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(map);
    Map<String, String> advancedVarMap = new HashMap<>();
    advancedVarMap.put("DRONE_NETRC_DEBUG", "true");
    advancedVarMap.put("DRONE_NETRC_LFS_ENABLED", "true");
    advancedVarMap.put("DRONE_NETRC_PRE_FETCH", "echo something \ngit config lfs.url https://blah.com");
    when(codebaseUtils.getGitAdvancedVariables(any(), eq(false), any(), any(), any(), anyBoolean()))
        .thenReturn(advancedVarMap);
    when(k8InitializeTaskUtils.getCacheEnvironmentVariable(OSType.Linux)).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getWorkDir()).thenReturn("/harness");
    when(k8InitializeTaskUtils.getCtrSecurityContext(any(), any()))
        .thenReturn(ContainerSecurityContext.builder().allowPrivilegeEscalation(false).privileged(true).build());
    when(internalContainerParamsProvider.getSetupAddonContainerParams(
             any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(getAddonContainer());
    when(internalContainerParamsProvider.getLiteEngineContainerParams(any(), any(), any(), any(), any(), any(), any(),
             any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(),
             any(Map.class)))
        .thenReturn(getLiteEngineContainer());
    when(k8InitializeStepUtils.getStageRequest(any(), any(), anyBoolean())).thenReturn(Pair.of(1024, 1024));
    when(k8InitializeServiceUtils.createServiceContainerDefinitions(any(), any(), any(), any()))
        .thenReturn(new ArrayList<>());
    when(
        k8InitializeStepUtils.createStepContainerDefinitions(any(), any(), any(), any(), any(), any(), any(), anyInt()))
        .thenReturn(Arrays.asList(K8InitializeTaskUtilsHelper.getRunStepContainer(0)));
    doNothing().when(k8InitializeTaskUtils).consumeSweepingOutput(any(), any(), any());
    doNothing().when(k8InitializeTaskUtils).consumeSweepingOutput(any(), any(), any());
    assertThatExceptionOfType(CIStageExecutionException.class)
        .isThrownBy(
            () -> k8InitializeTaskParamsBuilder.getK8InitializeTaskParams(initializeStepInfo, ambiance, "", false))
        .withMessageContaining(K8InitializeTaskParamsBuilder.privilegeFieldExceptionMessage);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testGetConnectorDetailsWithConversionInfo() {
    NGAccess ngAccess =
        BaseNGAccess.builder().accountIdentifier("account").orgIdentifier("org").projectIdentifier("projectt").build();
    ConnectorConversionInfo connectorConversionInfo =
        ConnectorConversionInfo.builder().registryRef("registry").envToSecretsMap(new HashMap<>()).build();

    when(featureFlagService.isEnabled(any(), any())).thenReturn(true);
    ConnectorDetails connectorDetailsFoMock =
        ConnectorDetails.builder()
            .connectorConfig(DockerConnectorDTO.builder().dockerRegistryUrl("RegistryURL").build())
            .connectorType(ConnectorType.DOCKER)
            .executeOnDelegate(false)
            .build();

    when(connectorUtils.getConnectorDetailsForHarnessArtifactRegistry(any())).thenReturn(connectorDetailsFoMock);
    HarnessRegistryConfig harnessRegistryConfig =
        HarnessRegistryConfig.builder()
            .httpClientConfig(ServiceHttpClientConfig.builder().baseUrl("RegistryURL").build())
            .jwtSecret("secret")
            .build();
    when(ciExecutionServiceConfig.getHarnessRegistryConfig()).thenReturn(harnessRegistryConfig);
    SourcePrincipalContextBuilder.setSourcePrincipal(new UserPrincipal("user", "user", "user", "user", "user"));

    ConnectorDetails connectorDetails = k8InitializeTaskParamsBuilder.getConnectorDetailsWithConversionInfo(
        ngAccess, connectorConversionInfo, ambiance, "");
    assertThat(connectorDetails.getConnectorConfig()).isInstanceOf(DockerConnectorDTO.class);
    assertThat(connectorDetails.getExecuteOnDelegate()).isFalse();
    DockerConnectorDTO dockerConnectorDTO = (DockerConnectorDTO) connectorDetails.getConnectorConfig();
    assertThat(dockerConnectorDTO.getDockerRegistryUrl()).isEqualTo("RegistryURL");
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testGetConnectorDetailsWithConversionInfoForNullRegistryRef() {
    NGAccess ngAccess =
        BaseNGAccess.builder().accountIdentifier("account").orgIdentifier("org").projectIdentifier("projectt").build();
    ConnectorConversionInfo connectorConversionInfo =
        ConnectorConversionInfo.builder().connectorRef("connector").envToSecretsMap(new HashMap<>()).build();
    ConnectorDetails connectorDetailsFoMock =
        ConnectorDetails.builder()
            .connectorConfig(DockerConnectorDTO.builder().dockerRegistryUrl("RegistryURL").build())
            .connectorType(ConnectorType.DOCKER)
            .executeOnDelegate(false)
            .build();

    when(connectorUtils.getConnectorDetailsForHarnessArtifactRegistry(any())).thenReturn(connectorDetailsFoMock);

    when(codebaseUtils.getGitConnector(any(), any(), any(), any())).thenReturn(ConnectorDetails.builder().build());
    ConnectorDetails connectorDetails = k8InitializeTaskParamsBuilder.getConnectorDetailsWithConversionInfo(
        ngAccess, connectorConversionInfo, ambiance, "");
    verify(codebaseUtils, times(1)).getGitConnector(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testGetK8InitializeTaskParamsWithHARRunStep() {
    InitializeStepInfo initializeStepInfo = K8InitializeTaskUtilsHelper.getDirectK8StepWithHARRunStep();
    K8PodDetails k8PodDetails = K8PodDetails.builder().accountId(accountId).stageID(STAGE_ID).build();
    when(featureFlagService.isEnabled(eq(FeatureName.CI_PR_MERGE_STRATEGY_BRANCH), any())).thenReturn(true);
    when(featureFlagService.isEnabled(eq(FeatureName.HAR_ENABLED), any())).thenReturn(true);

    ConnectorDetails connectorDetailsFoMock =
        ConnectorDetails.builder()
            .connectorConfig(DockerConnectorDTO.builder().dockerRegistryUrl("https://pkg.harness.io").build())
            .connectorType(ConnectorType.DOCKER)
            .executeOnDelegate(false)
            .build();

    when(connectorUtils.getConnectorDetailsForHarnessArtifactRegistry(any())).thenReturn(connectorDetailsFoMock);

    when(executionSweepingOutputResolver.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(executionSweepingOutputResolver.resolve(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.podDetails)))
        .thenReturn(k8PodDetails);
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageDetails.builder().stageRuntimeID("test").build())
                        .build());
    when(k8InitializeTaskUtils.generatePodName(STAGE_ID)).thenReturn(podName);
    when(k8InitializeTaskUtils.getBuildLabels(ambiance, k8PodDetails)).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getSharedPaths(any())).thenReturn(new ArrayList<>());
    when(k8InitializeTaskUtils.getVolumeToMountPath(any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getVolumeV2ToMountPath(any(), any(), any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getOS(any())).thenReturn(OSType.Linux);
    when(k8InitializeTaskUtils.getLogServiceEnvVariables(any(), any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getTIServiceEnvVariables(any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getSTOServiceEnvVariables(any())).thenReturn(new HashMap<>());
    when(codebaseUtils.getGitEnvVariables(any(), any(), eq(false))).thenReturn(new HashMap<>());
    Map<String, String> map = new HashMap<>();
    map.put("DRONE_REMOTE_URL", "https://github.com/harness/harness-core");
    when(k8InitializeTaskUtils.getCommonStepEnvVariables(any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(map);
    Map<String, String> advancedVarMap = new HashMap<>();
    advancedVarMap.put("DRONE_NETRC_DEBUG", "true");
    advancedVarMap.put("DRONE_NETRC_LFS_ENABLED", "true");
    advancedVarMap.put("DRONE_NETRC_PRE_FETCH", "echo something \ngit config lfs.url https://blah.com");
    when(codebaseUtils.getGitAdvancedVariables(any(), eq(false), any(), any(), any(), anyBoolean()))
        .thenReturn(advancedVarMap);
    when(k8InitializeTaskUtils.getCacheEnvironmentVariable(OSType.Linux)).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getWorkDir()).thenReturn("/harness");
    when(k8InitializeTaskUtils.getCtrSecurityContext(any(), any()))
        .thenReturn(ContainerSecurityContext.builder().build());
    when(internalContainerParamsProvider.getSetupAddonContainerParams(
             any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(getAddonContainer());
    when(internalContainerParamsProvider.getLiteEngineContainerParams(any(), any(), any(), any(), any(), any(), any(),
             any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(),
             any(Map.class)))
        .thenReturn(getLiteEngineContainer());
    when(k8InitializeStepUtils.getStageRequest(any(), any(), anyBoolean())).thenReturn(Pair.of(1024, 1024));
    when(k8InitializeServiceUtils.createServiceContainerDefinitions(any(), any(), any(), any()))
        .thenReturn(new ArrayList<>());
    when(
        k8InitializeStepUtils.createStepContainerDefinitions(any(), any(), any(), any(), any(), any(), any(), anyInt()))
        .thenReturn(Arrays.asList(K8InitializeTaskUtilsHelper.getRunStepContainerForHAR(0)));
    doNothing().when(k8InitializeTaskUtils).consumeSweepingOutput(any(), any(), any());
    doNothing().when(k8InitializeTaskUtils).consumeSweepingOutput(any(), any(), any());

    CIK8InitializeTaskParams response =
        k8InitializeTaskParamsBuilder.getK8InitializeTaskParams(initializeStepInfo, ambiance, "", false);
    assertThat(response.getCik8PodParams().getName()).isEqualTo(podName);
    verify(k8InitializeStepUtils, times(1))
        .createStepContainerDefinitions(any(), any(), any(), any(), any(), any(), any(), anyInt());
    // Step container is now at index 0, lite engine is at the end
    assertThat(response.getCik8PodParams().getContainerParamsList().get(0).getEnvVars().get("DRONE_NETRC_DEBUG"))
        .isEqualTo("true");
    assertThat(response.getCik8PodParams().getContainerParamsList().get(0).getEnvVars().get("DRONE_NETRC_LFS_ENABLED"))
        .isEqualTo("true");
    assertThat(response.getCik8PodParams().getContainerParamsList().get(0).getEnvVars().get("DRONE_NETRC_PRE_FETCH"))
        .isEqualTo("echo something \ngit config lfs.url https://blah.com");
    assertThat(response.getCik8PodParams().getContainerParamsList().get(0).getEnvVars().get("DRONE_REMOTE_URL"))
        .isEqualTo("https://github.com/harness/harness-core");
    assertThat(
        response.getCik8PodParams().getContainerParamsList().get(0).getEnvVars().get("DRONE_PR_MERGE_STRATEGY_BRANCH"))
        .isEqualTo("true");
    assertThat(response.getCik8PodParams()
                   .getContainerParamsList()
                   .get(0)
                   .getImageDetailsWithConnector()
                   .getImageDetails()
                   .getName())
        .isEqualTo("pkg.harness.io/test/registry/maven");
    assertThat(response.getCik8PodParams()
                   .getContainerParamsList()
                   .get(0)
                   .getImageDetailsWithConnector()
                   .getImageConnectorDetails()
                   .getConnectorConfig())
        .isInstanceOf(DockerConnectorDTO.class);
    assertThat(((DockerConnectorDTO) response.getCik8PodParams()
                       .getContainerParamsList()
                       .get(0)
                       .getImageDetailsWithConnector()
                       .getImageConnectorDetails()
                       .getConnectorConfig())
                   .getDockerRegistryUrl())
        .isEqualTo("https://pkg.harness.io");
  }

  @Test
  @Owner(developers = SATYA)
  @Category(UnitTests.class)
  public void testSecurityContextValidationMultiStep() {
    InitializeStepInfo initializeStepInfo = K8InitializeTaskUtilsHelper.getDirectK8Step(false);
    K8PodDetails k8PodDetails = K8PodDetails.builder().accountId(accountId).stageID(STAGE_ID).build();
    when(featureFlagService.isEnabled(eq(FeatureName.CI_PR_MERGE_STRATEGY_BRANCH), any())).thenReturn(true);

    when(executionSweepingOutputResolver.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(executionSweepingOutputResolver.resolve(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.podDetails)))
        .thenReturn(k8PodDetails);
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageDetails.builder().stageRuntimeID("test").build())
                        .build());
    when(k8InitializeTaskUtils.generatePodName(STAGE_ID)).thenReturn(podName);
    when(k8InitializeTaskUtils.getBuildLabels(ambiance, k8PodDetails)).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getSharedPaths(any())).thenReturn(new ArrayList<>());
    when(k8InitializeTaskUtils.getVolumeToMountPath(any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getOS(any())).thenReturn(OSType.Linux);
    when(k8InitializeTaskUtils.getLogServiceEnvVariables(any(), any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getTIServiceEnvVariables(any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getSTOServiceEnvVariables(any())).thenReturn(new HashMap<>());
    when(codebaseUtils.getGitEnvVariables(any(), any(), eq(false))).thenReturn(new HashMap<>());
    Map<String, String> map = new HashMap<>();
    map.put("DRONE_REMOTE_URL", "https://github.com/harness/harness-core");
    when(k8InitializeTaskUtils.getCommonStepEnvVariables(any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(map);
    Map<String, String> advancedVarMap = new HashMap<>();
    advancedVarMap.put("DRONE_NETRC_DEBUG", "true");
    advancedVarMap.put("DRONE_NETRC_LFS_ENABLED", "true");
    advancedVarMap.put("DRONE_NETRC_PRE_FETCH", "echo something \ngit config lfs.url https://blah.com");
    when(codebaseUtils.getGitAdvancedVariables(any(), eq(false), any(), any(), any(), anyBoolean()))
        .thenReturn(advancedVarMap);
    when(k8InitializeTaskUtils.getCacheEnvironmentVariable(OSType.Linux)).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getWorkDir()).thenReturn("/harness");

    ContainerSecurityContext securityContext = ContainerSecurityContext.builder()
                                                   .runAsUser(1000)
                                                   .privileged(false)
                                                   .allowPrivilegeEscalation(true)
                                                   .procMount("default")
                                                   .runAsNonRoot(false)
                                                   .readOnlyRootFilesystem(false)
                                                   .runAsGroup(0)
                                                   .capabilities(ContainerCapabilities.builder().build())
                                                   .build();

    ContainerSecurityContext privilegedStepContext = ContainerSecurityContext.builder()
                                                         .runAsUser(0)
                                                         .privileged(true)
                                                         .allowPrivilegeEscalation(true)
                                                         .procMount("default")
                                                         .runAsNonRoot(false)
                                                         .readOnlyRootFilesystem(false)
                                                         .runAsGroup(0)
                                                         .capabilities(ContainerCapabilities.builder().build())
                                                         .build();

    K8sDirectInfraYaml infrastructure = (K8sDirectInfraYaml) initializeStepInfo.getInfrastructure();

    SecurityContext sc = new SecurityContext();
    sc.setRunAsUser(ParameterField.createValueField(securityContext.getRunAsUser()));
    sc.setAllowPrivilegeEscalation(ParameterField.createValueField(securityContext.getAllowPrivilegeEscalation()));
    sc.setPrivileged(ParameterField.createValueField(securityContext.getPrivileged()));
    sc.setProcMount(ParameterField.createValueField(securityContext.getProcMount()));
    sc.setRunAsNonRoot(ParameterField.createValueField(securityContext.getRunAsNonRoot()));
    sc.setCapabilities(ParameterField.createValueField(null));
    sc.setReadOnlyRootFilesystem(ParameterField.createValueField(securityContext.getReadOnlyRootFilesystem()));
    sc.setRunAsGroup(ParameterField.createValueField(securityContext.getRunAsGroup()));
    infrastructure.getSpec().setContainerSecurityContext(ParameterField.createValueField(sc));
    when(k8InitializeTaskUtils.getCtrSecurityContext(any(), any()))
        .thenAnswer((Answer<ContainerSecurityContext>) invocation
            -> spyK8InitializeTaskUtils.getCtrSecurityContext(infrastructure, Collections.emptyMap()));

    when(internalContainerParamsProvider.getSetupAddonContainerParams(
             any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(getAddonContainer());
    when(internalContainerParamsProvider.getLiteEngineContainerParams(any(), any(), any(), any(), any(), any(), any(),
             any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(),
             any(Map.class)))
        .thenReturn(getLiteEngineContainer());
    when(k8InitializeStepUtils.getStageRequest(any(), any(), anyBoolean())).thenReturn(Pair.of(1024, 1024));
    when(k8InitializeServiceUtils.createServiceContainerDefinitions(any(), any(), any(), any()))
        .thenReturn(new ArrayList<>());
    when(
        k8InitializeStepUtils.createStepContainerDefinitions(any(), any(), any(), any(), any(), any(), any(), anyInt()))
        .thenReturn(Arrays.asList(K8InitializeTaskUtilsHelper.getRunStepContainerWithPrivilegedAndRunAsRoot(0),
            K8InitializeTaskUtilsHelper.getRunStepContainer(1)));

    doNothing().when(k8InitializeTaskUtils).consumeSweepingOutput(any(), any(), any());
    doNothing().when(k8InitializeTaskUtils).consumeSweepingOutput(any(), any(), any());

    CIK8InitializeTaskParams response =
        k8InitializeTaskParamsBuilder.getK8InitializeTaskParams(initializeStepInfo, ambiance, "", false);
    // Step containers are now at index 0 and 1, lite engine is at the end
    assertThat(response.getCik8PodParams().getContainerParamsList().get(0).getSecurityContext())
        .isEqualTo(privilegedStepContext);
    assertThat(response.getCik8PodParams().getContainerParamsList().get(1).getSecurityContext())
        .isEqualTo(securityContext);
  }

  @Test
  @Owner(developers = SAHITHI)
  @Category(UnitTests.class)
  public void testGetPODTTLWhenStageTimeoutIsLessThan24Hrs() {
    InitializeStepInfo initializeStepInfo = K8InitializeTaskUtilsHelper.getDirectK8Step(false);
    K8PodDetails k8PodDetails =
        K8PodDetails.builder()
            .accountId(accountId)
            .stageID(STAGE_ID)
            .timeout(ParameterField.<Timeout>builder()
                         .value(Timeout.builder().timeoutString("20m").timeoutInMillis(20 * 60 * 1000).build())
                         .build())
            .build();
    when(featureFlagService.isEnabled(eq(FeatureName.CI_PR_MERGE_STRATEGY_BRANCH), any())).thenReturn(true);
    when(featureFlagService.isEnabled(eq(FeatureName.CI_ENABLE_LONG_TIMEOUTS), any())).thenReturn(true);

    when(executionSweepingOutputResolver.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(executionSweepingOutputResolver.resolve(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.podDetails)))
        .thenReturn(k8PodDetails);
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageDetails.builder().stageRuntimeID("test").build())
                        .build());
    when(k8InitializeTaskUtils.generatePodName(STAGE_ID)).thenReturn(podName);
    when(k8InitializeTaskUtils.getBuildLabels(ambiance, k8PodDetails)).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getSharedPaths(any())).thenReturn(new ArrayList<>());
    when(k8InitializeTaskUtils.getVolumeToMountPath(any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getVolumeV2ToMountPath(any(), any(), any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getOS(any())).thenReturn(OSType.Linux);
    when(k8InitializeTaskUtils.getLogServiceEnvVariables(any(), any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getTIServiceEnvVariables(any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getSTOServiceEnvVariables(any())).thenReturn(new HashMap<>());
    when(codebaseUtils.getGitEnvVariables(any(), any(), eq(false))).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getCtrSecurityContext(any(), any()))
        .thenReturn(ContainerSecurityContext.builder().build());
    when(internalContainerParamsProvider.getSetupAddonContainerParams(
             any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(getAddonContainer());
    when(internalContainerParamsProvider.getLiteEngineContainerParams(any(), any(), any(), any(), any(), any(), any(),
             any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(),
             any(Map.class)))
        .thenReturn(getLiteEngineContainer());
    when(k8InitializeStepUtils.getStageRequest(any(), any(), anyBoolean())).thenReturn(Pair.of(1024, 1024));
    when(k8InitializeServiceUtils.createServiceContainerDefinitions(any(), any(), any(), any()))
        .thenReturn(new ArrayList<>());
    when(
        k8InitializeStepUtils.createStepContainerDefinitions(any(), any(), any(), any(), any(), any(), any(), anyInt()))
        .thenReturn(Arrays.asList(K8InitializeTaskUtilsHelper.getRunStepContainer(0)));
    doNothing().when(k8InitializeTaskUtils).consumeSweepingOutput(any(), any(), any());
    doNothing().when(k8InitializeTaskUtils).consumeSweepingOutput(any(), any(), any());

    CIK8InitializeTaskParams response =
        k8InitializeTaskParamsBuilder.getK8InitializeTaskParams(initializeStepInfo, ambiance, "", false);
    assertThat(response.getCik8PodParams().getName()).isEqualTo(podName);
    assertThat(response.getCik8PodParams().getActiveDeadLineSeconds()).isEqualTo(86400);
  }

  @Test
  @Owner(developers = SAHITHI)
  @Category(UnitTests.class)
  public void testGetPODTTLWhenStageTimeoutIsMoreThan24Hrs() {
    InitializeStepInfo initializeStepInfo = K8InitializeTaskUtilsHelper.getDirectK8Step(false);
    K8PodDetails k8PodDetails =
        K8PodDetails.builder()
            .accountId(accountId)
            .stageID(STAGE_ID)
            .timeout(ParameterField.<Timeout>builder()
                         .value(Timeout.builder().timeoutString("25h").timeoutInMillis(25 * 60 * 60 * 1000).build())
                         .build())
            .build();
    when(featureFlagService.isEnabled(eq(FeatureName.CI_PR_MERGE_STRATEGY_BRANCH), any())).thenReturn(true);
    when(featureFlagService.isEnabled(eq(FeatureName.CI_ENABLE_LONG_TIMEOUTS), any())).thenReturn(true);

    when(executionSweepingOutputResolver.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(executionSweepingOutputResolver.resolve(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.podDetails)))
        .thenReturn(k8PodDetails);
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageDetails.builder().stageRuntimeID("test").build())
                        .build());
    when(k8InitializeTaskUtils.generatePodName(STAGE_ID)).thenReturn(podName);
    when(k8InitializeTaskUtils.getBuildLabels(ambiance, k8PodDetails)).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getSharedPaths(any())).thenReturn(new ArrayList<>());
    when(k8InitializeTaskUtils.getVolumeToMountPath(any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getVolumeV2ToMountPath(any(), any(), any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getOS(any())).thenReturn(OSType.Linux);
    when(k8InitializeTaskUtils.getLogServiceEnvVariables(any(), any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getTIServiceEnvVariables(any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getSTOServiceEnvVariables(any())).thenReturn(new HashMap<>());
    when(codebaseUtils.getGitEnvVariables(any(), any(), eq(false))).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getCtrSecurityContext(any(), any()))
        .thenReturn(ContainerSecurityContext.builder().build());
    when(internalContainerParamsProvider.getSetupAddonContainerParams(
             any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(getAddonContainer());
    when(internalContainerParamsProvider.getLiteEngineContainerParams(any(), any(), any(), any(), any(), any(), any(),
             any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(),
             any(Map.class)))
        .thenReturn(getLiteEngineContainer());
    when(k8InitializeStepUtils.getStageRequest(any(), any(), anyBoolean())).thenReturn(Pair.of(1024, 1024));
    when(k8InitializeServiceUtils.createServiceContainerDefinitions(any(), any(), any(), any()))
        .thenReturn(new ArrayList<>());
    when(
        k8InitializeStepUtils.createStepContainerDefinitions(any(), any(), any(), any(), any(), any(), any(), anyInt()))
        .thenReturn(Arrays.asList(K8InitializeTaskUtilsHelper.getRunStepContainer(0)));
    doNothing().when(k8InitializeTaskUtils).consumeSweepingOutput(any(), any(), any());
    doNothing().when(k8InitializeTaskUtils).consumeSweepingOutput(any(), any(), any());

    CIK8InitializeTaskParams response =
        k8InitializeTaskParamsBuilder.getK8InitializeTaskParams(initializeStepInfo, ambiance, "", false);
    assertThat(response.getCik8PodParams().getName()).isEqualTo(podName);
    assertThat(response.getCik8PodParams().getActiveDeadLineSeconds()).isEqualTo(90600);
  }

  @Test
  @Owner(developers = SAHITHI)
  @Category(UnitTests.class)
  public void testGetPODTTLWhenStageTimeoutIsNull() {
    InitializeStepInfo initializeStepInfo = K8InitializeTaskUtilsHelper.getDirectK8Step(false);
    K8PodDetails k8PodDetails = K8PodDetails.builder().accountId(accountId).stageID(STAGE_ID).build();
    when(featureFlagService.isEnabled(eq(FeatureName.CI_PR_MERGE_STRATEGY_BRANCH), any())).thenReturn(true);
    when(featureFlagService.isEnabled(eq(FeatureName.CI_ENABLE_LONG_TIMEOUTS), any())).thenReturn(true);

    when(executionSweepingOutputResolver.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(executionSweepingOutputResolver.resolve(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.podDetails)))
        .thenReturn(k8PodDetails);
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageDetails.builder().stageRuntimeID("test").build())
                        .build());
    when(k8InitializeTaskUtils.generatePodName(STAGE_ID)).thenReturn(podName);
    when(k8InitializeTaskUtils.getBuildLabels(ambiance, k8PodDetails)).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getSharedPaths(any())).thenReturn(new ArrayList<>());
    when(k8InitializeTaskUtils.getVolumeToMountPath(any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getVolumeV2ToMountPath(any(), any(), any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getOS(any())).thenReturn(OSType.Linux);
    when(k8InitializeTaskUtils.getLogServiceEnvVariables(any(), any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getTIServiceEnvVariables(any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getSTOServiceEnvVariables(any())).thenReturn(new HashMap<>());
    when(codebaseUtils.getGitEnvVariables(any(), any(), eq(false))).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getCtrSecurityContext(any(), any()))
        .thenReturn(ContainerSecurityContext.builder().build());
    when(internalContainerParamsProvider.getSetupAddonContainerParams(
             any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(getAddonContainer());
    when(internalContainerParamsProvider.getLiteEngineContainerParams(any(), any(), any(), any(), any(), any(), any(),
             any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(),
             any(Map.class)))
        .thenReturn(getLiteEngineContainer());
    when(k8InitializeStepUtils.getStageRequest(any(), any(), anyBoolean())).thenReturn(Pair.of(1024, 1024));
    when(k8InitializeServiceUtils.createServiceContainerDefinitions(any(), any(), any(), any()))
        .thenReturn(new ArrayList<>());
    when(
        k8InitializeStepUtils.createStepContainerDefinitions(any(), any(), any(), any(), any(), any(), any(), anyInt()))
        .thenReturn(Arrays.asList(K8InitializeTaskUtilsHelper.getRunStepContainer(0)));
    doNothing().when(k8InitializeTaskUtils).consumeSweepingOutput(any(), any(), any());
    doNothing().when(k8InitializeTaskUtils).consumeSweepingOutput(any(), any(), any());

    CIK8InitializeTaskParams response =
        k8InitializeTaskParamsBuilder.getK8InitializeTaskParams(initializeStepInfo, ambiance, "", false);
    assertThat(response.getCik8PodParams().getName()).isEqualTo(podName);
    assertThat(response.getCik8PodParams().getActiveDeadLineSeconds()).isEqualTo(86400);
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testIsCommonEnvPodEnabledWithStageVariable() {
    String accountId = "test-account";
    NGVariable stageVar =
        StringNGVariable.builder().name("CI_COMMON_ENV_POD").value(ParameterField.createValueField("true")).build();
    List<NGVariable> variables = Collections.singletonList(stageVar);

    when(featureFlagService.isEnabled(FeatureName.CI_COMMON_ENV_POD, accountId)).thenReturn(false);

    boolean result = k8InitializeTaskParamsBuilder.isCommonEnvPodEnabled(accountId, variables);

    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testIsCommonEnvPodEnabledWithFeatureFlag() {
    String accountId = "test-account";
    List<NGVariable> variables = Collections.emptyList();

    when(featureFlagService.isEnabled(FeatureName.CI_COMMON_ENV_POD, accountId)).thenReturn(true);

    boolean result = k8InitializeTaskParamsBuilder.isCommonEnvPodEnabled(accountId, variables);

    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testIsCommonEnvPodDisabledWithStageVariable() {
    String accountId = "test-account";
    NGVariable stageVar =
        StringNGVariable.builder().name("CI_COMMON_ENV_POD").value(ParameterField.createValueField("false")).build();
    List<NGVariable> variables = Collections.singletonList(stageVar);

    when(featureFlagService.isEnabled(FeatureName.CI_COMMON_ENV_POD, accountId)).thenReturn(true);

    boolean result = k8InitializeTaskParamsBuilder.isCommonEnvPodEnabled(accountId, variables);

    // Stage variable set to false should override feature flag
    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testIsCommonEnvPodDisabledByDefault() {
    String accountId = "test-account";
    List<NGVariable> variables = Collections.emptyList();

    when(featureFlagService.isEnabled(FeatureName.CI_COMMON_ENV_POD, accountId)).thenReturn(false);

    boolean result = k8InitializeTaskParamsBuilder.isCommonEnvPodEnabled(accountId, variables);

    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testGetK8InitializeTaskParamsWithCommonEnvPodEnabled() {
    InitializeStepInfo initializeStepInfo = K8InitializeTaskUtilsHelper.getDirectK8Step(false);

    // Add CI_COMMON_ENV_POD stage variable
    List<NGVariable> variables = new ArrayList<>();
    variables.add(
        StringNGVariable.builder().name("CI_COMMON_ENV_POD").value(ParameterField.createValueField("true")).build());
    initializeStepInfo.setVariables(variables);

    K8PodDetails k8PodDetails = K8PodDetails.builder().accountId(accountId).stageID(STAGE_ID).build();

    when(featureFlagService.isEnabled(eq(FeatureName.CI_PR_MERGE_STRATEGY_BRANCH), any())).thenReturn(true);
    when(featureFlagService.isEnabled(eq(FeatureName.CI_K8S_OVERLAY_YAML), any())).thenReturn(false);
    when(featureFlagService.isEnabled(eq(FeatureName.CI_COMMON_ENV_POD), any())).thenReturn(false);

    when(executionSweepingOutputResolver.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(executionSweepingOutputResolver.resolve(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.podDetails)))
        .thenReturn(k8PodDetails);
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageDetails.builder().stageRuntimeID("test").build())
                        .build());

    when(k8InitializeTaskUtils.generatePodName(STAGE_ID)).thenReturn(podName);
    when(k8InitializeTaskUtils.getBuildLabels(ambiance, k8PodDetails)).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getSharedPaths(any())).thenReturn(new ArrayList<>());
    when(k8InitializeTaskUtils.getVolumeToMountPath(any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getVolumeV2ToMountPath(any(), any(), any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getOS(any())).thenReturn(OSType.Linux);
    when(k8InitializeTaskUtils.getLogServiceEnvVariables(any(), any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getTIServiceEnvVariables(any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getSTOServiceEnvVariables(any())).thenReturn(new HashMap<>());
    when(codebaseUtils.getGitEnvVariables(any(), any(), eq(false))).thenReturn(new HashMap<>());

    // Create common env vars that will be shared across all containers
    Map<String, String> commonEnvVarsMap = new HashMap<>();
    commonEnvVarsMap.put("DRONE_REMOTE_URL", "https://github.com/harness/harness-core");
    commonEnvVarsMap.put("DRONE_COMMIT_SHA", "abc123");
    commonEnvVarsMap.put("DRONE_BUILD_NUMBER", "42");

    when(k8InitializeTaskUtils.getCommonStepEnvVariables(any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(commonEnvVarsMap);

    Map<String, String> advancedVarMap = new HashMap<>();
    advancedVarMap.put("DRONE_NETRC_DEBUG", "true");
    when(codebaseUtils.getGitAdvancedVariables(any(), eq(false), any(), any(), any(), anyBoolean()))
        .thenReturn(advancedVarMap);

    when(k8InitializeTaskUtils.getCacheEnvironmentVariable(OSType.Linux)).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getWorkDir()).thenReturn("/harness");
    when(k8InitializeTaskUtils.getCtrSecurityContext(any(), any()))
        .thenReturn(ContainerSecurityContext.builder().build());

    when(internalContainerParamsProvider.getSetupAddonContainerParams(
             any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(getAddonContainer());
    when(internalContainerParamsProvider.getLiteEngineContainerParams(any(), any(), any(), any(), any(), any(), any(),
             any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(),
             any(Map.class)))
        .thenReturn(getLiteEngineContainer());

    when(k8InitializeStepUtils.getStageRequest(any(), any(), anyBoolean())).thenReturn(Pair.of(1024, 1024));
    when(k8InitializeServiceUtils.createServiceContainerDefinitions(any(), any(), any(), any()))
        .thenReturn(new ArrayList<>());

    // Create one step container that will have common env vars
    when(
        k8InitializeStepUtils.createStepContainerDefinitions(any(), any(), any(), any(), any(), any(), any(), anyInt()))
        .thenReturn(Arrays.asList(K8InitializeTaskUtilsHelper.getRunStepContainer(0)));

    doNothing().when(k8InitializeTaskUtils).consumeSweepingOutput(any(), any(), any());

    CIK8InitializeTaskParams response =
        k8InitializeTaskParamsBuilder.getK8InitializeTaskParams(initializeStepInfo, ambiance, "", false);

    // Verify the pod was created
    assertThat(response.getCik8PodParams().getName()).isEqualTo(podName);

    // Verify that common env vars are extracted and placed at pod level
    Map<String, String> podCommonEnvVars = response.getCik8PodParams().getCommonEnvVars();
    assertThat(podCommonEnvVars).isNotNull();
    assertThat(podCommonEnvVars).isNotEmpty();
    assertThat(podCommonEnvVars).containsKey("DRONE_REMOTE_URL");
    assertThat(podCommonEnvVars.get("DRONE_REMOTE_URL")).isEqualTo("https://github.com/harness/harness-core");
    assertThat(podCommonEnvVars).containsKey("DRONE_COMMIT_SHA");
    assertThat(podCommonEnvVars.get("DRONE_COMMIT_SHA")).isEqualTo("abc123");
    assertThat(podCommonEnvVars).containsKey("DRONE_BUILD_NUMBER");
    assertThat(podCommonEnvVars.get("DRONE_BUILD_NUMBER")).isEqualTo("42");
    assertThat(podCommonEnvVars).containsKey("DRONE_NETRC_DEBUG");
    assertThat(podCommonEnvVars.get("DRONE_NETRC_DEBUG")).isEqualTo("true");
    assertThat(podCommonEnvVars).containsKey("DRONE_PR_MERGE_STRATEGY_BRANCH");
    assertThat(podCommonEnvVars.get("DRONE_PR_MERGE_STRATEGY_BRANCH")).isEqualTo("true");

    // applyCommonEnvOptimization must be true on the task params (stage var path sets it too)
    assertThat(response.isApplyCommonEnvOptimization()).isTrue();

    // Verify that step container doesn't have the common env vars anymore (they were removed during extraction)
    // Step container is now at index 0, lite engine is at the end
    CIK8ContainerParams stepContainer = response.getCik8PodParams().getContainerParamsList().get(0);

    // With a single container, all its env vars become "common" and get extracted
    // So the step container should NOT have the env vars (they were all extracted to commonEnvVars)
    assertThat(stepContainer.getEnvVars()).doesNotContainKey("DRONE_REMOTE_URL");
    assertThat(stepContainer.getEnvVars()).doesNotContainKey("DRONE_COMMIT_SHA");
    assertThat(stepContainer.getEnvVars()).doesNotContainKey("DRONE_BUILD_NUMBER");
    assertThat(stepContainer.getEnvVars()).doesNotContainKey("DRONE_NETRC_DEBUG");
    assertThat(stepContainer.getEnvVars()).doesNotContainKey("DRONE_PR_MERGE_STRATEGY_BRANCH");

    // Note: With a single container, even DRONE_STEP_NAME gets extracted as "common"
    // This is expected behavior - all env vars from a single container are considered common

    // Verify lite engine container (last index) - it's added after extraction, so it keeps all vars
    List<CIK8ContainerParams> containerParamsList = response.getCik8PodParams().getContainerParamsList();
    CIK8ContainerParams liteEngineContainer = containerParamsList.get(containerParamsList.size() - 1);
    assertThat(liteEngineContainer.getName()).contains("engine");

    // Verify total containers: 1 step + 1 lite engine = 2
    assertThat(containerParamsList.size()).isEqualTo(2);
  }

  /**
   * When CI_COMMON_ENV_POD is enabled via the feature flag only (no stage variable), the manager must:
   * - leave commonEnvVars EMPTY (delegate 89300+ computes them itself)
   * - set applyCommonEnvOptimization=true so 89300+ delegates know to apply the optimization
   * - keep full env vars in every container so delegates below 89300 can start the pod without regression
   */
  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testGetK8InitializeTaskParamsWithCommonEnvPodEnabledViaFFOnly() {
    InitializeStepInfo initializeStepInfo = K8InitializeTaskUtilsHelper.getDirectK8Step(false);
    // No stage variable set — feature is enabled via FF only
    initializeStepInfo.setVariables(Collections.emptyList());

    K8PodDetails k8PodDetails = K8PodDetails.builder().accountId(accountId).stageID(STAGE_ID).build();

    when(featureFlagService.isEnabled(eq(FeatureName.CI_PR_MERGE_STRATEGY_BRANCH), any())).thenReturn(true);
    when(featureFlagService.isEnabled(eq(FeatureName.CI_K8S_OVERLAY_YAML), any())).thenReturn(false);
    when(featureFlagService.isEnabled(eq(FeatureName.CI_COMMON_ENV_POD), any())).thenReturn(true);

    when(executionSweepingOutputResolver.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(executionSweepingOutputResolver.resolve(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.podDetails)))
        .thenReturn(k8PodDetails);
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageDetails.builder().stageRuntimeID("test").build())
                        .build());

    when(k8InitializeTaskUtils.generatePodName(STAGE_ID)).thenReturn(podName);
    when(k8InitializeTaskUtils.getBuildLabels(ambiance, k8PodDetails)).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getSharedPaths(any())).thenReturn(new ArrayList<>());
    when(k8InitializeTaskUtils.getVolumeToMountPath(any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getVolumeV2ToMountPath(any(), any(), any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getOS(any())).thenReturn(OSType.Linux);
    when(k8InitializeTaskUtils.getLogServiceEnvVariables(any(), any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getTIServiceEnvVariables(any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getSTOServiceEnvVariables(any())).thenReturn(new HashMap<>());
    when(codebaseUtils.getGitEnvVariables(any(), any(), eq(false))).thenReturn(new HashMap<>());

    Map<String, String> commonEnvVarsMap = new HashMap<>();
    commonEnvVarsMap.put("DRONE_REMOTE_URL", "https://github.com/harness/harness-core");
    commonEnvVarsMap.put("DRONE_COMMIT_SHA", "abc123");

    when(k8InitializeTaskUtils.getCommonStepEnvVariables(any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(commonEnvVarsMap);

    when(codebaseUtils.getGitAdvancedVariables(any(), eq(false), any(), any(), any(), anyBoolean()))
        .thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getCacheEnvironmentVariable(OSType.Linux)).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getWorkDir()).thenReturn("/harness");
    when(k8InitializeTaskUtils.getCtrSecurityContext(any(), any()))
        .thenReturn(ContainerSecurityContext.builder().build());

    when(internalContainerParamsProvider.getSetupAddonContainerParams(
             any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(getAddonContainer());
    when(internalContainerParamsProvider.getLiteEngineContainerParams(any(), any(), any(), any(), any(), any(), any(),
             any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(),
             any(Map.class)))
        .thenReturn(getLiteEngineContainer());

    when(k8InitializeStepUtils.getStageRequest(any(), any(), anyBoolean())).thenReturn(Pair.of(1024, 1024));
    when(k8InitializeServiceUtils.createServiceContainerDefinitions(any(), any(), any(), any()))
        .thenReturn(new ArrayList<>());
    when(
        k8InitializeStepUtils.createStepContainerDefinitions(any(), any(), any(), any(), any(), any(), any(), anyInt()))
        .thenReturn(Arrays.asList(K8InitializeTaskUtilsHelper.getRunStepContainer(0)));

    doNothing().when(k8InitializeTaskUtils).consumeSweepingOutput(any(), any(), any());

    CIK8InitializeTaskParams response =
        k8InitializeTaskParamsBuilder.getK8InitializeTaskParams(initializeStepInfo, ambiance, "", false);

    assertThat(response.getCik8PodParams().getName()).isEqualTo(podName);

    // commonEnvVars must be EMPTY — the manager does not compute or strip for the FF-only path.
    // Delegate 89300+ will compute these itself using the applyCommonEnvOptimization flag.
    Map<String, String> podCommonEnvVars = response.getCik8PodParams().getCommonEnvVars();
    assertThat(podCommonEnvVars).isNotNull();
    assertThat(podCommonEnvVars).isEmpty();

    // applyCommonEnvOptimization must be true on the task params so 89300+ delegates know to apply the optimization
    assertThat(response.isApplyCommonEnvOptimization()).isTrue();

    // Step containers must carry the full env vars — delegates below 89300 rely on these.
    // Keeping them guarantees no YAML regression for any delegate version.
    CIK8ContainerParams stepContainer = response.getCik8PodParams().getContainerParamsList().get(0);
    assertThat(stepContainer.getEnvVars()).containsKey("DRONE_REMOTE_URL");
    assertThat(stepContainer.getEnvVars()).containsKey("DRONE_COMMIT_SHA");
  }

  @Test(expected = CIStageExecutionException.class)
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testOverlayYamlWithSecretRef_secretRestrictionFFEnabled_throws() {
    InitializeStepInfo initializeStepInfo = K8InitializeTaskUtilsHelper.getDirectK8Step(false);
    K8sDirectInfraYaml k8sDirectInfraYaml = (K8sDirectInfraYaml) initializeStepInfo.getInfrastructure();
    k8sDirectInfraYaml.getSpec().setPodSpecOverlay(
        ParameterField.createValueField(K8InitializeTaskUtilsHelper.overlaySpecYamlWithSecretKeyRef));

    K8PodDetails k8PodDetails = K8PodDetails.builder().accountId(accountId).stageID(STAGE_ID).build();

    when(featureFlagService.isEnabled(eq(FeatureName.CI_PR_MERGE_STRATEGY_BRANCH), any())).thenReturn(false);
    when(featureFlagService.isEnabled(eq(FeatureName.CI_K8S_OVERLAY_YAML), any())).thenReturn(true);
    when(featureFlagService.isEnabled(eq(FeatureName.CI_K8S_OVERLAY_YAML_SECRET_RESTRICTION), any())).thenReturn(true);

    when(executionSweepingOutputResolver.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(executionSweepingOutputResolver.resolve(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.podDetails)))
        .thenReturn(k8PodDetails);
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageDetails.builder().stageRuntimeID("test").build())
                        .build());

    when(k8InitializeTaskUtils.generatePodName(STAGE_ID)).thenReturn(podName);
    when(k8InitializeTaskUtils.getBuildLabels(ambiance, k8PodDetails)).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getSharedPaths(any())).thenReturn(new ArrayList<>());
    when(k8InitializeTaskUtils.getVolumeToMountPath(any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getVolumeV2ToMountPath(any(), any(), any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getOS(any())).thenReturn(OSType.Linux);
    when(k8InitializeTaskUtils.getWorkDir()).thenReturn("/harness");
    when(k8InitializeTaskUtils.getCtrSecurityContext(any(), any()))
        .thenReturn(ContainerSecurityContext.builder().build());
    when(k8InitializeTaskUtils.getLogServiceEnvVariables(any(), any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getTIServiceEnvVariables(any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getSTOServiceEnvVariables(any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getCacheEnvironmentVariable(OSType.Linux)).thenReturn(new HashMap<>());
    when(codebaseUtils.getGitEnvVariables(any(), any(), eq(false))).thenReturn(new HashMap<>());
    when(codebaseUtils.getGitAdvancedVariables(any(), eq(false), any(), any(), any(), anyBoolean()))
        .thenReturn(new HashMap<>());
    when(k8InitializeStepUtils.getStageRequest(any(), any(), anyBoolean())).thenReturn(Pair.of(1024, 1024));
    when(
        k8InitializeStepUtils.createStepContainerDefinitions(any(), any(), any(), any(), any(), any(), any(), anyInt()))
        .thenReturn(Arrays.asList(K8InitializeTaskUtilsHelper.getRunStepContainer(0)));
    when(k8InitializeServiceUtils.createServiceContainerDefinitions(any(), any(), any(), any()))
        .thenReturn(new ArrayList<>());
    when(internalContainerParamsProvider.getSetupAddonContainerParams(
             any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(CIK8ContainerParams.builder().name("addon").build());
    when(internalContainerParamsProvider.getLiteEngineContainerParams(any(), any(), any(), any(), any(), any(), any(),
             any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(),
             any(Map.class)))
        .thenReturn(CIK8ContainerParams.builder().name("engine").build());

    k8InitializeTaskParamsBuilder.getK8InitializeTaskParams(initializeStepInfo, ambiance, "", false);
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testOverlayYamlWithSecretRef_secretRestrictionFFDisabled_passes() {
    InitializeStepInfo initializeStepInfo = K8InitializeTaskUtilsHelper.getDirectK8Step(false);
    K8sDirectInfraYaml k8sDirectInfraYaml = (K8sDirectInfraYaml) initializeStepInfo.getInfrastructure();
    k8sDirectInfraYaml.getSpec().setPodSpecOverlay(
        ParameterField.createValueField(K8InitializeTaskUtilsHelper.overlaySpecYamlWithSecretKeyRef));

    K8PodDetails k8PodDetails = K8PodDetails.builder().accountId(accountId).stageID(STAGE_ID).build();

    when(featureFlagService.isEnabled(eq(FeatureName.CI_PR_MERGE_STRATEGY_BRANCH), any())).thenReturn(false);
    when(featureFlagService.isEnabled(eq(FeatureName.CI_K8S_OVERLAY_YAML), any())).thenReturn(true);
    when(featureFlagService.isEnabled(eq(FeatureName.CI_K8S_OVERLAY_YAML_SECRET_RESTRICTION), any())).thenReturn(false);

    when(executionSweepingOutputResolver.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(executionSweepingOutputResolver.resolve(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.podDetails)))
        .thenReturn(k8PodDetails);
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageDetails.builder().stageRuntimeID("test").build())
                        .build());

    when(k8InitializeTaskUtils.generatePodName(STAGE_ID)).thenReturn(podName);
    when(k8InitializeTaskUtils.getBuildLabels(ambiance, k8PodDetails)).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getSharedPaths(any())).thenReturn(new ArrayList<>());
    when(k8InitializeTaskUtils.getVolumeToMountPath(any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getVolumeV2ToMountPath(any(), any(), any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getOS(any())).thenReturn(OSType.Linux);
    when(k8InitializeTaskUtils.getWorkDir()).thenReturn("/harness");
    when(k8InitializeTaskUtils.getCtrSecurityContext(any(), any()))
        .thenReturn(ContainerSecurityContext.builder().build());
    when(k8InitializeTaskUtils.getLogServiceEnvVariables(any(), any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getTIServiceEnvVariables(any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getSTOServiceEnvVariables(any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getCacheEnvironmentVariable(OSType.Linux)).thenReturn(new HashMap<>());
    when(codebaseUtils.getGitEnvVariables(any(), any(), eq(false))).thenReturn(new HashMap<>());
    when(codebaseUtils.getGitAdvancedVariables(any(), eq(false), any(), any(), any(), anyBoolean()))
        .thenReturn(new HashMap<>());
    when(k8InitializeStepUtils.getStageRequest(any(), any(), anyBoolean())).thenReturn(Pair.of(1024, 1024));
    when(
        k8InitializeStepUtils.createStepContainerDefinitions(any(), any(), any(), any(), any(), any(), any(), anyInt()))
        .thenReturn(Arrays.asList(K8InitializeTaskUtilsHelper.getRunStepContainer(0)));
    when(k8InitializeServiceUtils.createServiceContainerDefinitions(any(), any(), any(), any()))
        .thenReturn(new ArrayList<>());
    when(internalContainerParamsProvider.getSetupAddonContainerParams(
             any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(CIK8ContainerParams.builder().name("addon").build());
    when(internalContainerParamsProvider.getLiteEngineContainerParams(any(), any(), any(), any(), any(), any(), any(),
             any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(),
             any(Map.class)))
        .thenReturn(CIK8ContainerParams.builder().name("engine").build());

    CIK8InitializeTaskParams response =
        k8InitializeTaskParamsBuilder.getK8InitializeTaskParams(initializeStepInfo, ambiance, "", false);

    assertThat(response).isNotNull();
    assertThat(response.getCik8PodParams().getOverlayYaml())
        .isEqualTo(K8InitializeTaskUtilsHelper.overlaySpecYamlWithSecretKeyRef);
  }

  @Test
  @Owner(developers = AKSHAY_KHANDELWAL)
  @Category(UnitTests.class)
  public void testStepContainersReceiveServiceEndpointsNotTokens() {
    InitializeStepInfo initializeStepInfo = K8InitializeTaskUtilsHelper.getDirectK8Step(false);
    K8PodDetails k8PodDetails = K8PodDetails.builder().accountId(accountId).stageID(STAGE_ID).build();

    Map<String, String> tiEnvVars = new HashMap<>();
    tiEnvVars.put(TI_SERVICE_ENDPOINT_VARIABLE, "https://ti.example");
    tiEnvVars.put(TI_SERVICE_TOKEN_VARIABLE, "ti-token");
    Map<String, String> coverageEnvVars = new HashMap<>();
    coverageEnvVars.put(COVERAGE_SERVICE_ENDPOINT_VARIABLE, "https://coverage.example");
    coverageEnvVars.put(COVERAGE_SERVICE_TOKEN_VARIABLE, "coverage-token");

    when(featureFlagService.isEnabled(eq(FeatureName.CI_PR_MERGE_STRATEGY_BRANCH), any())).thenReturn(false);
    when(featureFlagService.isEnabled(eq(FeatureName.CI_K8S_OVERLAY_YAML), any())).thenReturn(false);
    when(featureFlagService.isEnabled(eq(FeatureName.CODE_COVERAGE_ENABLED), any())).thenReturn(true);
    when(executionSweepingOutputResolver.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(executionSweepingOutputResolver.resolve(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.podDetails)))
        .thenReturn(k8PodDetails);
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageDetails.builder().stageRuntimeID("test").build())
                        .build());
    when(k8InitializeTaskUtils.generatePodName(STAGE_ID)).thenReturn(podName);
    when(k8InitializeTaskUtils.getBuildLabels(ambiance, k8PodDetails)).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getSharedPaths(any())).thenReturn(new ArrayList<>());
    when(k8InitializeTaskUtils.getVolumeToMountPath(any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getVolumeV2ToMountPath(any(), any(), any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getOS(any())).thenReturn(OSType.Linux);
    when(k8InitializeTaskUtils.getLogServiceEnvVariables(any(), any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getTIServiceEnvVariables(any(), any())).thenReturn(tiEnvVars);
    when(k8InitializeTaskUtils.getCoverageEnvVariables(any(), any())).thenReturn(coverageEnvVars);
    when(k8InitializeTaskUtils.getSTOServiceEnvVariables(any())).thenReturn(new HashMap<>());
    when(codebaseUtils.getGitEnvVariables(any(), any(), eq(false))).thenReturn(new HashMap<>());
    Map<String, String> map = new HashMap<>();
    map.put("DRONE_REMOTE_URL", "https://github.com/harness/harness-core");
    when(k8InitializeTaskUtils.getCommonStepEnvVariables(any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(map);
    when(codebaseUtils.getGitAdvancedVariables(any(), eq(false), any(), any(), any(), anyBoolean()))
        .thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getCacheEnvironmentVariable(OSType.Linux)).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getWorkDir()).thenReturn("/harness");
    when(k8InitializeTaskUtils.getCtrSecurityContext(any(), any()))
        .thenReturn(ContainerSecurityContext.builder().build());
    when(internalContainerParamsProvider.getSetupAddonContainerParams(
             any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(getAddonContainer());
    when(internalContainerParamsProvider.getLiteEngineContainerParams(any(), any(), any(), any(), any(), any(), any(),
             any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(),
             any(Map.class)))
        .thenReturn(getLiteEngineContainer());
    when(k8InitializeStepUtils.getStageRequest(any(), any(), anyBoolean())).thenReturn(Pair.of(1024, 1024));
    when(k8InitializeServiceUtils.createServiceContainerDefinitions(any(), any(), any(), any()))
        .thenReturn(new ArrayList<>());
    when(
        k8InitializeStepUtils.createStepContainerDefinitions(any(), any(), any(), any(), any(), any(), any(), anyInt()))
        .thenReturn(Arrays.asList(K8InitializeTaskUtilsHelper.getRunStepContainer(0)));
    doNothing().when(k8InitializeTaskUtils).consumeSweepingOutput(any(), any(), any());

    CIK8InitializeTaskParams response =
        k8InitializeTaskParamsBuilder.getK8InitializeTaskParams(initializeStepInfo, ambiance, "", false);

    Map<String, String> stepEnvVars = response.getCik8PodParams().getContainerParamsList().get(0).getEnvVars();
    assertThat(stepEnvVars).containsEntry(TI_SERVICE_ENDPOINT_VARIABLE, "https://ti.example");
    assertThat(stepEnvVars).containsEntry(COVERAGE_SERVICE_ENDPOINT_VARIABLE, "https://coverage.example");
    assertThat(stepEnvVars).doesNotContainKey(TI_SERVICE_TOKEN_VARIABLE);
    assertThat(stepEnvVars).doesNotContainKey(COVERAGE_SERVICE_TOKEN_VARIABLE);
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testOverlayYamlFallbackToPlatformSetting() {
    InitializeStepInfo initializeStepInfo = K8InitializeTaskUtilsHelper.getDirectK8Step(false);
    K8sDirectInfraYaml k8sDirectInfraYaml = (K8sDirectInfraYaml) initializeStepInfo.getInfrastructure();
    k8sDirectInfraYaml.getSpec().setPodSpecOverlay(ParameterField.createValueField(null));

    K8PodDetails k8PodDetails = K8PodDetails.builder().accountId(accountId).stageID(STAGE_ID).build();

    String platformOverlayYaml = "topologySpreadConstraints:\n  - maxSkew: 1\n    topologyKey: zone";
    Map<String, String> settingsMap = new HashMap<>();
    settingsMap.put("ci_k8_pod_spec_overlay", platformOverlayYaml);

    when(featureFlagService.isEnabled(eq(FeatureName.CI_PR_MERGE_STRATEGY_BRANCH), any())).thenReturn(false);
    when(featureFlagService.isEnabled(eq(FeatureName.CI_K8S_OVERLAY_YAML), any())).thenReturn(true);
    when(featureFlagService.isEnabled(eq(FeatureName.CI_K8S_OVERLAY_YAML_SECRET_RESTRICTION), any())).thenReturn(false);

    when(executionSweepingOutputResolver.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(executionSweepingOutputResolver.resolve(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.podDetails)))
        .thenReturn(k8PodDetails);
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageDetails.builder().stageRuntimeID("test").build())
                        .build());

    when(k8InitializeTaskUtils.generatePodName(STAGE_ID)).thenReturn(podName);
    when(k8InitializeTaskUtils.getBuildLabels(ambiance, k8PodDetails)).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getSharedPaths(any())).thenReturn(new ArrayList<>());
    when(k8InitializeTaskUtils.getVolumeToMountPath(any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getVolumeV2ToMountPath(any(), any(), any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getOS(any())).thenReturn(OSType.Linux);
    when(k8InitializeTaskUtils.getWorkDir()).thenReturn("/harness");
    when(k8InitializeTaskUtils.getCtrSecurityContext(any(), any()))
        .thenReturn(ContainerSecurityContext.builder().build());
    when(k8InitializeTaskUtils.fetchK8InfraAdvancedSettings(any(), any(), any())).thenReturn(settingsMap);
    when(k8InitializeTaskUtils.getLogServiceEnvVariables(any(), any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getTIServiceEnvVariables(any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getSTOServiceEnvVariables(any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getCacheEnvironmentVariable(OSType.Linux)).thenReturn(new HashMap<>());
    when(codebaseUtils.getGitEnvVariables(any(), any(), eq(false))).thenReturn(new HashMap<>());
    when(codebaseUtils.getGitAdvancedVariables(any(), eq(false), any(), any(), any(), anyBoolean()))
        .thenReturn(new HashMap<>());
    when(k8InitializeStepUtils.getStageRequest(any(), any(), anyBoolean())).thenReturn(Pair.of(1024, 1024));
    when(
        k8InitializeStepUtils.createStepContainerDefinitions(any(), any(), any(), any(), any(), any(), any(), anyInt()))
        .thenReturn(Arrays.asList(K8InitializeTaskUtilsHelper.getRunStepContainer(0)));
    when(k8InitializeServiceUtils.createServiceContainerDefinitions(any(), any(), any(), any()))
        .thenReturn(new ArrayList<>());
    when(internalContainerParamsProvider.getSetupAddonContainerParams(
             any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(CIK8ContainerParams.builder().name("addon").build());
    when(internalContainerParamsProvider.getLiteEngineContainerParams(any(), any(), any(), any(), any(), any(), any(),
             any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(),
             any(Map.class)))
        .thenReturn(CIK8ContainerParams.builder().name("engine").build());

    CIK8InitializeTaskParams response =
        k8InitializeTaskParamsBuilder.getK8InitializeTaskParams(initializeStepInfo, ambiance, "", false);

    assertThat(response).isNotNull();
    assertThat(response.getCik8PodParams().getOverlayYaml()).isEqualTo(platformOverlayYaml);
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testOverlayYamlStageOverridesPlatformSetting() {
    InitializeStepInfo initializeStepInfo = K8InitializeTaskUtilsHelper.getDirectK8Step(true);
    K8PodDetails k8PodDetails = K8PodDetails.builder().accountId(accountId).stageID(STAGE_ID).build();

    Map<String, String> settingsMap = new HashMap<>();
    settingsMap.put("ci_k8_pod_spec_overlay", "should-not-be-used");

    when(featureFlagService.isEnabled(eq(FeatureName.CI_PR_MERGE_STRATEGY_BRANCH), any())).thenReturn(false);
    when(featureFlagService.isEnabled(eq(FeatureName.CI_K8S_OVERLAY_YAML), any())).thenReturn(true);
    when(featureFlagService.isEnabled(eq(FeatureName.CI_K8S_OVERLAY_YAML_SECRET_RESTRICTION), any())).thenReturn(false);

    when(executionSweepingOutputResolver.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(executionSweepingOutputResolver.resolve(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.podDetails)))
        .thenReturn(k8PodDetails);
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageDetails.builder().stageRuntimeID("test").build())
                        .build());

    when(k8InitializeTaskUtils.generatePodName(STAGE_ID)).thenReturn(podName);
    when(k8InitializeTaskUtils.getBuildLabels(ambiance, k8PodDetails)).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getSharedPaths(any())).thenReturn(new ArrayList<>());
    when(k8InitializeTaskUtils.getVolumeToMountPath(any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getVolumeV2ToMountPath(any(), any(), any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getOS(any())).thenReturn(OSType.Linux);
    when(k8InitializeTaskUtils.getWorkDir()).thenReturn("/harness");
    when(k8InitializeTaskUtils.getCtrSecurityContext(any(), any()))
        .thenReturn(ContainerSecurityContext.builder().build());
    when(k8InitializeTaskUtils.fetchK8InfraAdvancedSettings(any(), any(), any())).thenReturn(settingsMap);
    when(k8InitializeTaskUtils.getLogServiceEnvVariables(any(), any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getTIServiceEnvVariables(any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getSTOServiceEnvVariables(any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getCacheEnvironmentVariable(OSType.Linux)).thenReturn(new HashMap<>());
    when(codebaseUtils.getGitEnvVariables(any(), any(), eq(false))).thenReturn(new HashMap<>());
    when(codebaseUtils.getGitAdvancedVariables(any(), eq(false), any(), any(), any(), anyBoolean()))
        .thenReturn(new HashMap<>());
    when(k8InitializeStepUtils.getStageRequest(any(), any(), anyBoolean())).thenReturn(Pair.of(1024, 1024));
    when(
        k8InitializeStepUtils.createStepContainerDefinitions(any(), any(), any(), any(), any(), any(), any(), anyInt()))
        .thenReturn(Arrays.asList(K8InitializeTaskUtilsHelper.getRunStepContainer(0)));
    when(k8InitializeServiceUtils.createServiceContainerDefinitions(any(), any(), any(), any()))
        .thenReturn(new ArrayList<>());
    when(internalContainerParamsProvider.getSetupAddonContainerParams(
             any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(CIK8ContainerParams.builder().name("addon").build());
    when(internalContainerParamsProvider.getLiteEngineContainerParams(any(), any(), any(), any(), any(), any(), any(),
             any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(),
             any(Map.class)))
        .thenReturn(CIK8ContainerParams.builder().name("engine").build());

    CIK8InitializeTaskParams response =
        k8InitializeTaskParamsBuilder.getK8InitializeTaskParams(initializeStepInfo, ambiance, "", false);

    assertThat(response).isNotNull();
    assertThat(response.getCik8PodParams().getOverlayYaml()).isEqualTo(K8InitializeTaskUtilsHelper.overlaySpecYaml);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testGetK8InitializeTaskParamsEphemeralDelegateModeSkipsSetupAndLiteEngineContainers() {
    InitializeStepInfo initializeStepInfo = K8InitializeTaskUtilsHelper.getDirectK8Step(false);
    K8PodDetails k8PodDetails = K8PodDetails.builder().accountId(accountId).stageID(STAGE_ID).build();

    // Ephemeral delegate mode ON: the runner executes steps in-process, so neither the setup-addon init
    // container nor the lite-engine sidecar should be added to the pod.
    when(featureFlagService.isEnabled(eq(FeatureName.PIPE_ENABLE_EPHEMERAL_DELEGATE_MODE), any())).thenReturn(true);
    when(featureFlagService.isEnabled(eq(FeatureName.CI_PR_MERGE_STRATEGY_BRANCH), any())).thenReturn(true);
    when(featureFlagService.isEnabled(eq(FeatureName.CI_K8S_OVERLAY_YAML), any())).thenReturn(false);

    when(executionSweepingOutputResolver.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(executionSweepingOutputResolver.resolve(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.podDetails)))
        .thenReturn(k8PodDetails);
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageDetails.builder().stageRuntimeID("test").build())
                        .build());
    when(k8InitializeTaskUtils.generatePodName(STAGE_ID)).thenReturn(podName);
    when(k8InitializeTaskUtils.getBuildLabels(ambiance, k8PodDetails)).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getSharedPaths(any())).thenReturn(new ArrayList<>());
    when(k8InitializeTaskUtils.getVolumeToMountPath(any(), any(), anyBoolean())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getVolumeV2ToMountPath(any(), any(), any(), any(), anyBoolean()))
        .thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getOS(any())).thenReturn(OSType.Linux);
    when(k8InitializeTaskUtils.getLogServiceEnvVariables(any(), any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getTIServiceEnvVariables(any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getSTOServiceEnvVariables(any())).thenReturn(new HashMap<>());
    when(codebaseUtils.getGitEnvVariables(any(), any(), eq(false))).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getCommonStepEnvVariables(any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new HashMap<>());
    when(codebaseUtils.getGitAdvancedVariables(any(), eq(false), any(), any(), any(), anyBoolean()))
        .thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getCacheEnvironmentVariable(OSType.Linux)).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getWorkDir()).thenReturn("/harness");
    when(k8InitializeTaskUtils.getCtrSecurityContext(any(), any()))
        .thenReturn(ContainerSecurityContext.builder().build());
    // Stage-level CPU/memory requests are surfaced onto the pod params for downstream consumers.
    when(k8InitializeStepUtils.getStageRequest(any(), any(), anyBoolean())).thenReturn(Pair.of(2048, 4096));
    when(k8InitializeServiceUtils.createServiceContainerDefinitions(any(), any(), any(), any()))
        .thenReturn(new ArrayList<>());
    when(
        k8InitializeStepUtils.createStepContainerDefinitions(any(), any(), any(), any(), any(), any(), any(), anyInt()))
        .thenReturn(Arrays.asList(K8InitializeTaskUtilsHelper.getRunStepContainer(0)));
    doNothing().when(k8InitializeTaskUtils).consumeSweepingOutput(any(), any(), any());

    CIK8InitializeTaskParams response =
        k8InitializeTaskParamsBuilder.getK8InitializeTaskParams(initializeStepInfo, ambiance, "", false);

    assertThat(response.getCik8PodParams().getName()).isEqualTo(podName);
    // No setup-addon init container in ephemeral mode.
    assertThat(response.getCik8PodParams().getInitContainerParamsList()).isEmpty();
    // Step containers remain; the lite-engine container is not appended.
    List<CIK8ContainerParams> containers = response.getCik8PodParams().getContainerParamsList();
    assertThat(containers).isNotEmpty();
    assertThat(containers.stream().map(CIK8ContainerParams::getName)).doesNotContain("engine", "addon");
    // Stage-level resource requests are propagated to the pod params.
    assertThat(response.getCik8PodParams().getStageCpuMilli()).isEqualTo(2048);
    assertThat(response.getCik8PodParams().getStageMemoryMiB()).isEqualTo(4096);
    // The internal container providers are never invoked in ephemeral mode.
    verify(internalContainerParamsProvider, Mockito.never())
        .getSetupAddonContainerParams(any(), any(), any(), any(), any(), any(), any(), any());
    verify(internalContainerParamsProvider, Mockito.never())
        .getLiteEngineContainerParams(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
            any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any(Map.class));
  }

  @Test
  @Owner(developers = GARGI)
  @Category(UnitTests.class)
  public void testCreateCIK8ContainerParams_GitCloneWithoutCloneDir_DefaultsWorkspaceToHarnessRepoName() {
    Map<String, String> stepEnv = explicitCloneStepEnv();
    stepEnv.put(DRONE_REMOTE_URL, "https://github.com/Gargi-thakur01/nginx.git");

    Map<String, String> commonEnv = new HashMap<>();
    commonEnv.put(DRONE_WORKSPACE, STEP_MOUNT_PATH);

    CIK8ContainerParams result = invokeCreateCIK8ContainerParams(stepEnv, commonEnv, true, OSType.Linux);

    assertThat(result.getEnvVars().get(DRONE_WORKSPACE)).isEqualTo(STEP_MOUNT_PATH + "/nginx");
  }

  @Test
  @Owner(developers = GARGI)
  @Category(UnitTests.class)
  public void testCreateCIK8ContainerParams_GitCloneWithEmptyCloneDir_DefaultsWorkspaceToHarnessRepoName() {
    Map<String, String> stepEnv = explicitCloneStepEnv();
    stepEnv.put(DRONE_REMOTE_URL, "https://github.com/Gargi-thakur01/nginx.git");
    stepEnv.put(DRONE_WORKSPACE, "");

    Map<String, String> commonEnv = new HashMap<>();
    commonEnv.put(DRONE_WORKSPACE, STEP_MOUNT_PATH);

    CIK8ContainerParams result = invokeCreateCIK8ContainerParams(stepEnv, commonEnv, true, OSType.Linux);

    assertThat(result.getEnvVars().get(DRONE_WORKSPACE)).isEqualTo(STEP_MOUNT_PATH + "/nginx");
  }

  @Test
  @Owner(developers = GARGI)
  @Category(UnitTests.class)
  public void testCreateCIK8ContainerParams_GitCloneWithExplicitCloneDir_PreservesUserWorkspace() {
    Map<String, String> stepEnv = explicitCloneStepEnv();
    stepEnv.put(DRONE_REMOTE_URL, "https://github.com/Gargi-thakur01/nginx.git");
    stepEnv.put(DRONE_WORKSPACE, "/harness/repo_branch");

    Map<String, String> commonEnv = new HashMap<>();
    commonEnv.put(DRONE_WORKSPACE, STEP_MOUNT_PATH); // stage default that must not win

    CIK8ContainerParams result = invokeCreateCIK8ContainerParams(stepEnv, commonEnv, true, OSType.Linux);

    assertThat(result.getEnvVars().get(DRONE_WORKSPACE)).isEqualTo("/harness/repo_branch");
  }

  @Test
  @Owner(developers = GARGI)
  @Category(UnitTests.class)
  public void testCreateCIK8ContainerParams_NonV1Yaml_DoesNotApplyDefaultWorkspace() {
    Map<String, String> stepEnv = explicitCloneStepEnv();
    stepEnv.put(DRONE_REMOTE_URL, "https://github.com/Gargi-thakur01/nginx.git");

    Map<String, String> commonEnv = new HashMap<>();
    commonEnv.put(DRONE_WORKSPACE, STEP_MOUNT_PATH);

    CIK8ContainerParams result = invokeCreateCIK8ContainerParams(stepEnv, commonEnv, false, OSType.Linux);

    // V0 path: no defaulting — stage commonEnvVars value remains.
    assertThat(result.getEnvVars().get(DRONE_WORKSPACE)).isEqualTo(STEP_MOUNT_PATH);
  }

  @Test
  @Owner(developers = GARGI)
  @Category(UnitTests.class)
  public void testCreateCIK8ContainerParams_GitCloneWithoutCloneDir_OnMacOs_LeavesStageWorkspace() {
    Map<String, String> stepEnv = explicitCloneStepEnv();
    stepEnv.put(DRONE_REMOTE_URL, "https://github.com/Gargi-thakur01/nginx.git");

    Map<String, String> commonEnv = new HashMap<>();
    commonEnv.put(DRONE_WORKSPACE, STEP_MOUNT_PATH);

    CIK8ContainerParams result = invokeCreateCIK8ContainerParams(stepEnv, commonEnv, true, OSType.MacOS);

    // V0 getCloneDirEnvVars skips MacOS when clone dir is empty; stage workspace remains.
    assertThat(result.getEnvVars().get(DRONE_WORKSPACE)).isEqualTo(STEP_MOUNT_PATH);
  }

  @Test
  @Owner(developers = GARGI)
  @Category(UnitTests.class)
  public void testCreateCIK8ContainerParams_NonExplicitCloneStepWithRemoteUrl_DoesNotDefaultWorkspace() {
    // No HARNESS_CI_EXPLICIT_GIT_CLONE_STEP marker — the workdir defaulting must not apply to
    // generic V1 steps that happen to carry DRONE_REMOTE_URL.
    Map<String, String> stepEnv = new HashMap<>();
    stepEnv.put(DRONE_REMOTE_URL, "https://github.com/Gargi-thakur01/nginx.git");

    Map<String, String> commonEnv = new HashMap<>();
    commonEnv.put(DRONE_WORKSPACE, STEP_MOUNT_PATH);

    CIK8ContainerParams result = invokeCreateCIK8ContainerParams(stepEnv, commonEnv, true, OSType.Linux);

    assertThat(result.getEnvVars().get(DRONE_WORKSPACE)).isEqualTo(STEP_MOUNT_PATH);
  }

  @Test
  @Owner(developers = SAI_LAXMAN)
  @Category(UnitTests.class)
  public void testCreateCIK8ContainerParams_ExplicitGitCloneDoesNotInheritSparseCheckout() {
    Map<String, String> stepEnv = explicitCloneStepEnv();
    stepEnv.put(DRONE_NETRC_SPARSE_CHECKOUT, "");

    Map<String, String> commonEnv = new HashMap<>();
    commonEnv.put(DRONE_NETRC_SPARSE_CHECKOUT, "apps");

    CIK8ContainerParams result = invokeCreateCIK8ContainerParams(stepEnv, commonEnv, true, OSType.Linux);

    assertThat(result.getEnvVars()).containsEntry(DRONE_NETRC_SPARSE_CHECKOUT, "");
  }

  private static Map<String, String> explicitCloneStepEnv() {
    Map<String, String> env = new HashMap<>();
    env.put(HARNESS_CI_EXPLICIT_GIT_CLONE_STEP, "true");
    return env;
  }

  private CIK8ContainerParams invokeCreateCIK8ContainerParams(
      Map<String, String> stepEnv, Map<String, String> commonEnv, boolean isV1Yaml, OSType os) {
    when(k8InitializeTaskUtils.removeEnvVarsWithSecretRef(any())).thenReturn(Collections.emptyMap());
    when(k8InitializeTaskUtils.getSecretVariableDetails(any(), any(), any())).thenReturn(Collections.emptyList());
    when(featureFlagService.isEnabled(any(), any())).thenReturn(false);
    when(internalContainerParamsProvider.getLiteEngineSecretVars(any(), any(), any(), any(), any(), any()))
        .thenReturn(Collections.emptyMap());

    ContainerDefinitionInfo containerDefinitionInfo =
        ContainerDefinitionInfo.builder()
            .name("step-2")
            .stepName("gitClone")
            .stepIdentifier("gitClone")
            .envVars(stepEnv)
            .containerType(CIContainerType.RUN)
            .containerImageDetails(ContainerImageDetails.builder()
                                       .imageDetails(ImageDetails.builder().name("harness/drone-git").build())
                                       .build())
            .build();

    NGAccess ngAccess = BaseNGAccess.builder().accountIdentifier(accountId).build();
    ContainerSecurityContext securityContext = ContainerSecurityContext.builder().build();

    return on(k8InitializeTaskParamsBuilder)
        .call("createCIK8ContainerParams", ngAccess, containerDefinitionInfo, null /* harnessInternalImageConnector */,
            commonEnv, Collections.emptyMap() /* stoEnvVars */, Collections.emptyMap() /* principalTokenEnvVars */,
            Collections.emptyMap() /* stepTiSecretEnvVars */, Collections.emptyMap() /* stepCoverageSecretEnvVars */,
            Collections.emptyMap() /* connectorRefs */, Collections.emptyMap() /* volumeToMountPath */,
            Collections.emptyMap() /* volumeMountInfoV2 */, STEP_MOUNT_PATH /* workDirPath */, securityContext,
            "log-prefix", Collections.emptyList() /* secretVariableDetails */,
            Collections.emptyMap() /* githubApiTokenFunctorConnectors */, os, null /* secretEnvVars */, ambiance,
            Collections.emptyList() /* variables */, isV1Yaml)
        .get();
  }
}
