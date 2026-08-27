/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.container;

import static io.harness.beans.sweepingoutputs.CISweepingOutputNames.TASK_SELECTORS;
import static io.harness.beans.sweepingoutputs.StageInfraDetails.STAGE_INFRA_DETAILS;
import static io.harness.rule.OwnerRule.ABHISHEK;
import static io.harness.rule.OwnerRule.ANIL;
import static io.harness.rule.OwnerRule.FJUNIOR;
import static io.harness.rule.OwnerRule.KESHAV_GOEL;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;
import static io.harness.rule.OwnerRule.SOURABH;
import static io.harness.rule.OwnerRule.VINICIUS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.environment.pod.container.ContainerDefinitionInfo;
import io.harness.beans.sweepingoutputs.ContextElement;
import io.harness.beans.sweepingoutputs.K8StageInfraDetails;
import io.harness.beans.sweepingoutputs.StageDetails;
import io.harness.beans.sweepingoutputs.TaskSelectorSweepingOutput;
import io.harness.beans.yaml.extended.infrastrucutre.EcsDirectInfraYamlSpec;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.beans.yaml.extended.infrastrucutre.VmPoolYaml;
import io.harness.beans.yaml.extended.infrastrucutre.VmPoolYaml.VmPoolYamlSpec;
import io.harness.category.element.UnitTests;
import io.harness.ci.buildstate.SecretUtils;
import io.harness.ci.execution.integrationstage.utils.HarnessTokenUtils;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.ci.remote.CiServiceResourceClient;
import io.harness.ci.utils.PortFinder;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.beans.ci.CIInitializeTaskParams;
import io.harness.delegate.beans.ci.ecs.CIECSContainerParams;
import io.harness.delegate.beans.ci.k8s.CIK8InitializeTaskParams;
import io.harness.delegate.beans.ci.pod.CIK8ContainerParams;
import io.harness.delegate.beans.ci.pod.CIK8PodParams;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.pod.ContainerSecurityContext;
import io.harness.delegate.beans.ci.pod.PodVolume;
import io.harness.delegate.beans.ci.vm.taskparams.CIVmInitializeTaskParams;
import io.harness.delegate.task.citasks.cik8handler.params.CIConstants;
import io.harness.ff.FeatureFlagService;
import io.harness.k8s.model.ImageDetails;
import io.harness.logstreaming.LogStreamingServiceConfiguration;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.NGOidcAccess;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.plancreator.execution.StepsExecutionConfig;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.steps.container.beans.ServiceEnvironmentVars;
import io.harness.steps.container.exception.ContainerStepExecutionException;
import io.harness.steps.container.execution.output.ContainerDetailsSweepingOutput;
import io.harness.steps.container.utils.ConnectorUtils;
import io.harness.steps.container.utils.ContainerParamsProvider;
import io.harness.steps.container.utils.ContainerStepImageUtils;
import io.harness.steps.container.utils.K8sPodInitUtils;
import io.harness.steps.container.utils.VmInitializeUtils;
import io.harness.steps.plugin.ContainerStepInfo;
import io.harness.steps.plugin.ContainerStepSpec;
import io.harness.steps.plugin.InitContainerV2StepInfo;
import io.harness.steps.plugin.infrastructure.ContainerEcsInfra;
import io.harness.steps.plugin.infrastructure.ContainerInfraYamlSpec;
import io.harness.steps.plugin.infrastructure.ContainerK8sInfra;
import io.harness.steps.plugin.infrastructure.ContainerStepInfra;
import io.harness.steps.plugin.infrastructure.ContainerVMInfra;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.yaml.core.timeout.Timeout;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class ContainerStepInitHelperTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock private K8sPodInitUtils k8sPodInitUtils;
  @Mock private PmsFeatureFlagService pmsFeatureFlagService;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private ConnectorUtils connectorUtils;
  @Mock private ContainerStepImageUtils harnessImageUtils;
  @Mock private ContainerParamsProvider containerParamsProvider;

  @Mock LogStreamingServiceConfiguration logStreamingServiceConfiguration;

  @Mock VmInitializeUtils vmInitializeUtils;

  @Mock NGSettingsClient settingsClient;

  @Mock private CiServiceResourceClient ciServiceResourceClient;
  @Mock private CIFeatureFlagService ciFeatureFlagService;
  @Mock private HarnessTokenUtils harnessTokenUtils;
  @Mock private SecretUtils secretUtils;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Mock private OutcomeService outcomeService;
  @InjectMocks @Spy private ContainerStepInitHelper containerStepInitHelper;

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testSavingOfTaskSelectors() {
    String samContainerName = "sam-container";
    String liteEngineContainerName = "lite-engine-container";
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", "accountId").build();
    InitContainerV2StepInfo containerV2StepInfo =
        InitContainerV2StepInfo.builder()
            .sharedPaths(ParameterField.createValueField(new ArrayList<>(List.of("pathA", "pathB"))))
            .pluginsData(Collections.emptyMap())
            .infrastructure(ContainerK8sInfra.builder()
                                .spec(ContainerInfraYamlSpec.builder()
                                          .connectorRef(ParameterField.createValueField("k8sInfra"))
                                          .automountServiceAccountToken(ParameterField.createValueField(false))
                                          .priorityClassName(ParameterField.createValueField("className"))
                                          .build())
                                .build())
            .build();
    containerV2StepInfo.setIdentifier("identifier");

    List<String> samDeploySelectors = List.of("samDeploySelectorA", "samDeploySelectorB");
    List<TaskSelector> delegateSelectors =
        List.of(TaskSelector.newBuilder().setSelector(samDeploySelectors.get(0)).build(),
            TaskSelector.newBuilder().setSelector(samDeploySelectors.get(1)).build());

    doReturn(Pair.of(2, 500)).when(k8sPodInitUtils).getStepLimits(any(), any(), anyBoolean());
    doReturn(ContainerSecurityContext.builder().build())
        .when(k8sPodInitUtils)
        .getCtrSecurityContext(any(ContainerK8sInfra.class));
    doReturn(CIK8ContainerParams.builder().name(liteEngineContainerName).build())
        .when(containerParamsProvider)
        .getLiteEngineContainerParams(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());

    doReturn(CIK8ContainerParams.builder().name(samContainerName).build())
        .when(containerParamsProvider)
        .getSetupAddonContainerParams(any(), any(), any(), any(), any(), any(), any());

    doReturn("test-pod").when(k8sPodInitUtils).generatePodName(any());

    containerStepInitHelper.getK8InitializeTaskParams(containerV2StepInfo, ambiance, "logPrefix", delegateSelectors);

    ArgumentCaptor<ExecutionSweepingOutput> outputValueCaptor = ArgumentCaptor.forClass(ExecutionSweepingOutput.class);
    ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);

    verify(k8sPodInitUtils, times(5))
        .consumeSweepingOutput(eq(ambiance), outputValueCaptor.capture(), keyCaptor.capture());
    List<String> allValues = keyCaptor.getAllValues();
    assertThat(allValues.size()).isEqualTo(5);
    assertThat(allValues.contains(STAGE_INFRA_DETAILS)).isTrue();
    assertThat(allValues.contains(TASK_SELECTORS)).isTrue();

    List<ExecutionSweepingOutput> executionSweepingOutputs = outputValueCaptor.getAllValues();
    assertThat(allValues.size()).isEqualTo(5);
    assertThat(executionSweepingOutputs.stream().anyMatch(so -> so instanceof K8StageInfraDetails)).isTrue();
    assertThat(executionSweepingOutputs.stream().anyMatch(so -> so instanceof TaskSelectorSweepingOutput)).isTrue();

    executionSweepingOutputs.forEach(so -> {
      if (so instanceof K8StageInfraDetails stageInfraDetails) {
        List<String> containerNames = stageInfraDetails.getContainerNames();
        assertThat(containerNames.size()).isEqualTo(2);
        Stream.of(samContainerName, liteEngineContainerName)
            .forEach(containerName -> assertThat(containerNames.contains(containerName)).isTrue());

        assertThat(stageInfraDetails.getPodName().equals("test-pod")).isTrue();
        assertThat(stageInfraDetails.shouldRouteStageToRunner()).isFalse();

        List<String> stageInfraDetailsDelegateSelectors =
            stageInfraDetails.getDelegateSelectors().stream().map(TaskSelector::getSelector).toList();
        assertThat(stageInfraDetailsDelegateSelectors.size()).isEqualTo(2);

        List<String> stageInfraDelegateSelectorsForVerification =
            new ArrayList<>(List.of("samDeploySelectorA", "samDeploySelectorB"));
        stageInfraDetailsDelegateSelectors.forEach(ds -> {
          assertThat(stageInfraDelegateSelectorsForVerification.contains(ds)).isTrue();
          stageInfraDelegateSelectorsForVerification.remove(ds);
        });
      }

      if (so instanceof TaskSelectorSweepingOutput taskSelectorSweepingOutput) {
        List<String> taskSelectors =
            taskSelectorSweepingOutput.getTaskSelectors().stream().map(TaskSelector::getSelector).toList();
        assertThat(taskSelectors.size()).isEqualTo(2);

        List<String> taskSelectorsForVerification =
            new ArrayList<>(List.of("samDeploySelectorA", "samDeploySelectorB"));
        taskSelectors.forEach(ds -> {
          assertThat(taskSelectorsForVerification.contains(ds)).isTrue();
          taskSelectorsForVerification.remove(ds);
        });
      }
    });
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetK8InitializeTaskParams_withRouteToRunnerTrue_savesK8StageInfraDetailsWithRouteToRunnerTrue() {
    String samContainerName = "sam-container";
    String liteEngineContainerName = "lite-engine-container";
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", "accountId").build();
    InitContainerV2StepInfo containerV2StepInfo =
        InitContainerV2StepInfo.builder()
            .sharedPaths(ParameterField.createValueField(new ArrayList<>(List.of("pathA", "pathB"))))
            .pluginsData(Collections.emptyMap())
            .infrastructure(ContainerK8sInfra.builder()
                                .spec(ContainerInfraYamlSpec.builder()
                                          .connectorRef(ParameterField.createValueField("k8sInfra"))
                                          .automountServiceAccountToken(ParameterField.createValueField(false))
                                          .priorityClassName(ParameterField.createValueField("className"))
                                          .build())
                                .build())
            .build();
    containerV2StepInfo.setIdentifier("identifier");

    List<TaskSelector> delegateSelectors = List.of(
        TaskSelector.newBuilder().setSelector("sel1").build(), TaskSelector.newBuilder().setSelector("sel2").build());

    doReturn(Pair.of(2, 500)).when(k8sPodInitUtils).getStepLimits(any(), any(), anyBoolean());
    doReturn(ContainerSecurityContext.builder().build())
        .when(k8sPodInitUtils)
        .getCtrSecurityContext(any(ContainerK8sInfra.class));
    doReturn(CIK8ContainerParams.builder().name(liteEngineContainerName).build())
        .when(containerParamsProvider)
        .getLiteEngineContainerParams(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    doReturn(CIK8ContainerParams.builder().name(samContainerName).build())
        .when(containerParamsProvider)
        .getSetupAddonContainerParams(any(), any(), any(), any(), any(), any(), any());
    doReturn("test-pod").when(k8sPodInitUtils).generatePodName(any());

    containerStepInitHelper.getK8InitializeTaskParams(
        containerV2StepInfo, ambiance, "logPrefix", "stepId", delegateSelectors, true);

    ArgumentCaptor<ExecutionSweepingOutput> outputValueCaptor = ArgumentCaptor.forClass(ExecutionSweepingOutput.class);
    verify(k8sPodInitUtils, times(5)).consumeSweepingOutput(eq(ambiance), outputValueCaptor.capture(), any());
    K8StageInfraDetails k8StageInfraDetails = outputValueCaptor.getAllValues()
                                                  .stream()
                                                  .filter(K8StageInfraDetails.class ::isInstance)
                                                  .map(K8StageInfraDetails.class ::cast)
                                                  .findFirst()
                                                  .orElse(null);
    assertThat(k8StageInfraDetails).isNotNull();
    assertThat(k8StageInfraDetails.shouldRouteStageToRunner()).isTrue();
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetBuildSetupTaskParams_VM() {
    // Arrange
    ContainerStepInfra vmInfra = ContainerVMInfra.builder()
                                     .spec(VmPoolYaml.builder()
                                               .spec(VmPoolYamlSpec.builder()
                                                         .poolName(ParameterField.createValueField("poolName"))
                                                         .os(ParameterField.createValueField(OSType.Linux))
                                                         .build())
                                               .build())
                                     .build();
    ContainerStepSpec containerStepSpec = InitContainerV2StepInfo.builder().infrastructure(vmInfra).build();
    Ambiance ambiance = mock(Ambiance.class);
    String logPrefix = "logPrefix";
    String stepIdentifier = "stepIdentifier";
    List<TaskSelector> delegateSelectors = new ArrayList<>();
    CIVmInitializeTaskParams vmInitializeTaskParams = mock(CIVmInitializeTaskParams.class);
    doReturn(vmInitializeTaskParams)
        .when(containerStepInitHelper)
        .getDirectVmInitializeTaskParams(containerStepSpec, ambiance, logPrefix, stepIdentifier, delegateSelectors);
    // Act
    CIInitializeTaskParams result = containerStepInitHelper.getBuildSetupTaskParams(
        containerStepSpec, ambiance, logPrefix, stepIdentifier, delegateSelectors, false);

    // Assert
    assertThat(result).isEqualTo(vmInitializeTaskParams);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetVmInitializeParams_Success() {
    // Arrange
    ContainerStepInfra vmInfra = ContainerVMInfra.builder()
                                     .spec(VmPoolYaml.builder()
                                               .spec(VmPoolYamlSpec.builder()
                                                         .poolName(ParameterField.createValueField("poolName"))
                                                         .os(ParameterField.createValueField(OSType.Linux))
                                                         .build())
                                               .build())
                                     .build();
    ContainerStepSpec containerStepSpec = InitContainerV2StepInfo.builder().infrastructure(vmInfra).build();
    FeatureFlagService featureFlagService = mock(FeatureFlagService.class);

    List<String> fallbackPoolIds = new ArrayList<>();
    Optional<String> resourceClass = Optional.of("resourceClass");
    boolean shouldRouteStageToRunner = true;
    Long activeDeadlineSeconds = 100L;
    String logPrefix = "logPrefix";
    String stepIdentifier = "stepIdentifier";
    String poolId = "poolId";
    String accountID = "accountId";
    String orgID = "orgId";
    String projectID = "projectId";
    String pipelineID = "pipelineId";
    String stageID = "stageId";
    String stepGroupRuntimeId = "stepGroupRuntimeId";
    String workDir = "/workdir";
    String harnessImageConnectorRef = "harnessImageConnectorRef";
    int runSequence = 123;

    Level level = mock(Level.class);

    Ambiance ambiance =
        Ambiance.newBuilder()
            .putAllSetupAbstractions(
                Map.of("accountId", accountID, "orgIdentifier", orgID, "projectIdentifier", projectID))
            .setMetadata(
                ExecutionMetadata.newBuilder().setPipelineIdentifier(pipelineID).setRunSequence(runSequence).build())
            .addLevels(Level.newBuilder().setRuntimeId(stepGroupRuntimeId).setIdentifier(stageID).build())
            .build();

    doReturn(stepGroupRuntimeId).when(level).getRuntimeId();
    doReturn(Optional.of(ParameterField.createValueField(harnessImageConnectorRef)))
        .when(containerStepInitHelper)
        .getHarnessImageConnector(vmInfra);
    doReturn(new HashMap<>()).when(vmInitializeUtils).getVolumeToMountPath(any(), any(), any(), any());
    doReturn(workDir).when(vmInitializeUtils).getWorkDir(any(), any(), any());
    doReturn(new HashMap<>()).when(vmInitializeUtils).getCommonStepEnvVariables(any());
    doReturn(new HashMap<>()).when(k8sPodInitUtils).getLogServiceEnvVariables(any(), any());
    doReturn(new HashMap<>()).when(k8sPodInitUtils).getCommonStepEnvVariables(any(), any(), any(), any());
    doReturn(new HashMap<>()).when(vmInitializeUtils).getBuildTags(any(), any(), any());

    // Act
    CIInitializeTaskParams result = containerStepInitHelper.getVmInitializeParams(containerStepSpec, ambiance, poolId,
        fallbackPoolIds, resourceClass, shouldRouteStageToRunner, activeDeadlineSeconds, logPrefix, stepIdentifier);

    // Assert
    assertThat(result).isInstanceOf(CIVmInitializeTaskParams.class);
    CIVmInitializeTaskParams vmParams = (CIVmInitializeTaskParams) result;
    assertThat(vmParams.getPoolID()).isEqualTo(poolId);
    assertThat(vmParams.getFallbackPoolIDs()).isEqualTo(fallbackPoolIds);
    assertThat(vmParams.getWorkingDir()).isEqualTo(workDir);
    assertThat(vmParams.getAccountID()).isEqualTo(accountID);
    assertThat(vmParams.getOrgID()).isEqualTo(orgID);
    assertThat(vmParams.getProjectID()).isEqualTo(projectID);
    assertThat(vmParams.getPipelineID()).isEqualTo(pipelineID);
  }

  private void setupCommonK8Mocks() {
    doReturn(Pair.of(2, 500)).when(k8sPodInitUtils).getStepLimits(any(), any(), anyBoolean());
    doReturn(ContainerSecurityContext.builder().build())
        .when(k8sPodInitUtils)
        .getCtrSecurityContext(any(ContainerK8sInfra.class));
    doReturn(CIK8ContainerParams.builder().name("lite-engine").build())
        .when(containerParamsProvider)
        .getLiteEngineContainerParams(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    doReturn(CIK8ContainerParams.builder().name("addon").build())
        .when(containerParamsProvider)
        .getSetupAddonContainerParams(any(), any(), any(), any(), any(), any(), any());
    doReturn("test-pod").when(k8sPodInitUtils).generatePodName(any());
  }

  private InitContainerV2StepInfo buildK8StepInfo(ContainerK8sInfra infrastructure) {
    InitContainerV2StepInfo stepInfo = InitContainerV2StepInfo.builder()
                                           .sharedPaths(ParameterField.createValueField(new ArrayList<>()))
                                           .pluginsData(Collections.emptyMap())
                                           .infrastructure(infrastructure)
                                           .build();
    stepInfo.setIdentifier("identifier");
    return stepInfo;
  }

  @Test
  @Owner(developers = KESHAV_GOEL)
  @Category(UnitTests.class)
  public void testBuildK8DirectTaskParams_passesNGOidcAccessWithPipelineIdentifier() {
    String pipelineId = "myPipeline";
    String accountId = "accountId";
    String orgId = "orgId";
    String projectId = "projectId";

    Ambiance ambiance = Ambiance.newBuilder()
                            .putAllSetupAbstractions(
                                Map.of("accountId", accountId, "orgIdentifier", orgId, "projectIdentifier", projectId))
                            .setMetadata(ExecutionMetadata.newBuilder().setPipelineIdentifier(pipelineId).build())
                            .addLevels(Level.newBuilder().setRuntimeId("runtime1").setIdentifier("stage1").build())
                            .build();

    ContainerK8sInfra infrastructure =
        ContainerK8sInfra.builder()
            .spec(ContainerInfraYamlSpec.builder()
                      .connectorRef(ParameterField.createValueField("k8sConnector"))
                      .automountServiceAccountToken(ParameterField.createValueField(false))
                      .priorityClassName(ParameterField.createValueField("className"))
                      .build())
            .build();

    ConnectorDetails mockConnector = ConnectorDetails.builder().identifier("k8sConnector").build();
    when(connectorUtils.getConnectorDetails(any(NGAccess.class), eq("k8sConnector"))).thenReturn(mockConnector);
    setupCommonK8Mocks();

    List<TaskSelector> delegateSelectors = List.of(TaskSelector.newBuilder().setSelector("sel1").build());
    containerStepInitHelper.getK8InitializeTaskParams(
        buildK8StepInfo(infrastructure), ambiance, "logPrefix", delegateSelectors);

    ArgumentCaptor<NGAccess> ngAccessCaptor = ArgumentCaptor.forClass(NGAccess.class);
    verify(connectorUtils).getConnectorDetails(ngAccessCaptor.capture(), eq("k8sConnector"));

    NGAccess capturedAccess = ngAccessCaptor.getValue();
    assertThat(capturedAccess).isInstanceOf(NGOidcAccess.class);
    NGOidcAccess oidcAccess = (NGOidcAccess) capturedAccess;
    assertThat(oidcAccess.getPipelineIdentifier()).isEqualTo(pipelineId);
    assertThat(oidcAccess.getAccountIdentifier()).isEqualTo(accountId);
    assertThat(oidcAccess.getOrgIdentifier()).isEqualTo(orgId);
    assertThat(oidcAccess.getProjectIdentifier()).isEqualTo(projectId);
  }

  @Test
  @Owner(developers = KESHAV_GOEL)
  @Category(UnitTests.class)
  public void testBuildK8DirectTaskParams_connectorResolutionSucceedsWithAbsentOptionalOidcFields() {
    Ambiance ambiance =
        Ambiance.newBuilder()
            .putAllSetupAbstractions(Map.of("accountId", "acct1", "orgIdentifier", "org1", "projectIdentifier", "prj1"))
            .setMetadata(ExecutionMetadata.newBuilder().setPipelineIdentifier("pipe1").build())
            .addLevels(Level.newBuilder().setRuntimeId("runtime1").setIdentifier("stage1").build())
            .build();

    ContainerK8sInfra infrastructure =
        ContainerK8sInfra.builder()
            .spec(ContainerInfraYamlSpec.builder()
                      .connectorRef(ParameterField.createValueField("k8sConnector"))
                      .automountServiceAccountToken(ParameterField.createValueField(false))
                      .priorityClassName(ParameterField.createValueField("className"))
                      .build())
            .build();

    ConnectorDetails mockConnector = ConnectorDetails.builder().identifier("k8sConnector").build();
    when(connectorUtils.getConnectorDetails(any(NGAccess.class), eq("k8sConnector"))).thenReturn(mockConnector);
    setupCommonK8Mocks();

    List<TaskSelector> delegateSelectors = List.of(TaskSelector.newBuilder().setSelector("sel1").build());
    containerStepInitHelper.getK8InitializeTaskParams(
        buildK8StepInfo(infrastructure), ambiance, "logPrefix", delegateSelectors);

    ArgumentCaptor<NGAccess> ngAccessCaptor = ArgumentCaptor.forClass(NGAccess.class);
    verify(connectorUtils).getConnectorDetails(ngAccessCaptor.capture(), eq("k8sConnector"));

    NGOidcAccess oidcAccess = (NGOidcAccess) ngAccessCaptor.getValue();
    assertThat(oidcAccess.getTriggeredByName()).isNullOrEmpty();
    assertThat(oidcAccess.getTriggerByEmail()).isNullOrEmpty();
    assertThat(oidcAccess.getStepType()).isNullOrEmpty();
    assertThat(oidcAccess.getAccountIdentifier()).isEqualTo("acct1");
    assertThat(oidcAccess.getPipelineIdentifier()).isEqualTo("pipe1");
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testCreateStepContainerDefinitionWithRegistryRefOnly() throws Exception {
    ContainerStepInfo runStepInfo = ContainerStepInfo.infoBuilder()
                                        .identifier("stepId")
                                        .name("stepName")
                                        .image(ParameterField.createValueField("alpine"))
                                        .connectorRef(ParameterField.ofNull())
                                        .registryRef(ParameterField.createValueField("account.myRegistry"))
                                        .envVariables(ParameterField.createValueField(Collections.emptyMap()))
                                        .privileged(ParameterField.createValueField(false))
                                        .runAsUser(ParameterField.ofNull())
                                        .imagePullPolicy(ParameterField.ofNull())
                                        .build();
    PortFinder portFinder = PortFinder.builder().usedPorts(new java.util.HashSet<>()).startingPort(20002).build();

    doReturn(ImageDetails.builder().name("alpine").build()).when(k8sPodInitUtils).getImageInfo(any());
    doReturn(Pair.of(500, 256)).when(k8sPodInitUtils).getStepLimits(any(), any(), anyBoolean());

    Method method = ContainerStepInitHelper.class.getDeclaredMethod("createStepContainerDefinition",
        ContainerStepInfo.class, PortFinder.class, String.class, OSType.class, boolean.class);
    method.setAccessible(true);

    ContainerDefinitionInfo result = (ContainerDefinitionInfo) method.invoke(
        containerStepInitHelper, runStepInfo, portFinder, "accountId", OSType.Linux, false);

    assertThat(result).isNotNull();
    assertThat(result.getContainerImageDetails().getRegistryRef()).isEqualTo("account.myRegistry");
    assertThat(result.getContainerImageDetails().getConnectorIdentifier()).isNull();
  }

  @Test(expected = ContainerStepExecutionException.class)
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testCreateStepContainerDefinitionThrowsWhenBothConnectorRefAndRegistryRefNull() throws Exception {
    ContainerStepInfo runStepInfo = ContainerStepInfo.infoBuilder()
                                        .identifier("stepId")
                                        .name("stepName")
                                        .image(ParameterField.createValueField("alpine"))
                                        .connectorRef(ParameterField.ofNull())
                                        .registryRef(ParameterField.ofNull())
                                        .envVariables(ParameterField.createValueField(Collections.emptyMap()))
                                        .privileged(ParameterField.createValueField(false))
                                        .runAsUser(ParameterField.ofNull())
                                        .imagePullPolicy(ParameterField.ofNull())
                                        .build();
    PortFinder portFinder = PortFinder.builder().usedPorts(new java.util.HashSet<>()).startingPort(20002).build();

    Method method = ContainerStepInitHelper.class.getDeclaredMethod("createStepContainerDefinition",
        ContainerStepInfo.class, PortFinder.class, String.class, OSType.class, boolean.class);
    method.setAccessible(true);

    try {
      method.invoke(containerStepInitHelper, runStepInfo, portFinder, "accountId", OSType.Linux, false);
    } catch (java.lang.reflect.InvocationTargetException e) {
      if (e.getCause() instanceof ContainerStepExecutionException) {
        throw (ContainerStepExecutionException) e.getCause();
      }
      throw new RuntimeException(e);
    }
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testCreateStepContainerDefinitionWithConnectorRefOnly() throws Exception {
    ContainerStepInfo runStepInfo = ContainerStepInfo.infoBuilder()
                                        .identifier("stepId")
                                        .name("stepName")
                                        .image(ParameterField.createValueField("alpine"))
                                        .connectorRef(ParameterField.createValueField("account.harnessImage"))
                                        .registryRef(ParameterField.ofNull())
                                        .envVariables(ParameterField.createValueField(Collections.emptyMap()))
                                        .privileged(ParameterField.createValueField(false))
                                        .runAsUser(ParameterField.ofNull())
                                        .imagePullPolicy(ParameterField.ofNull())
                                        .build();
    PortFinder portFinder = PortFinder.builder().usedPorts(new java.util.HashSet<>()).startingPort(20002).build();

    doReturn(ImageDetails.builder().name("alpine").build()).when(k8sPodInitUtils).getImageInfo(any());
    doReturn(Pair.of(500, 256)).when(k8sPodInitUtils).getStepLimits(any(), any(), anyBoolean());

    Method method = ContainerStepInitHelper.class.getDeclaredMethod("createStepContainerDefinition",
        ContainerStepInfo.class, PortFinder.class, String.class, OSType.class, boolean.class);
    method.setAccessible(true);

    ContainerDefinitionInfo result = (ContainerDefinitionInfo) method.invoke(
        containerStepInitHelper, runStepInfo, portFinder, "accountId", OSType.Linux, false);

    assertThat(result).isNotNull();
    assertThat(result.getContainerImageDetails().getConnectorIdentifier()).isEqualTo("account.harnessImage");
    assertThat(result.getContainerImageDetails().getRegistryRef()).isNull();
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void testK8InitializeTaskParams_injectsHarnessTokenEnvVars() {
    Map<String, String> permissions = new HashMap<>(Map.of("pipelines", "execute"));
    Map<String, String> tokenEnvVars =
        new HashMap<>(Map.of("HARNESS_TOKEN", "token123", "HARNESS_BASE_URL", "https://app.harness.io"));
    String accountId = "accountId";

    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", accountId).build();
    InitContainerV2StepInfo containerV2StepInfo =
        InitContainerV2StepInfo.builder()
            .sharedPaths(ParameterField.createValueField(new ArrayList<>(List.of("pathA"))))
            .pluginsData(Collections.emptyMap())
            .permissions(permissions)
            .infrastructure(ContainerK8sInfra.builder()
                                .spec(ContainerInfraYamlSpec.builder()
                                          .connectorRef(ParameterField.createValueField("k8sInfra"))
                                          .automountServiceAccountToken(ParameterField.createValueField(false))
                                          .priorityClassName(ParameterField.createValueField("className"))
                                          .build())
                                .build())
            .build();
    containerV2StepInfo.setIdentifier("identifier");

    doReturn(tokenEnvVars).when(harnessTokenUtils).getPrincipalTokenEnvVariables(ambiance, accountId, permissions);
    setupCommonK8Mocks();

    containerStepInitHelper.getK8InitializeTaskParams(
        containerV2StepInfo, ambiance, "logPrefix", Collections.emptyList());

    verify(harnessTokenUtils).getPrincipalTokenEnvVariables(ambiance, accountId, permissions);
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void testK8InitializeTaskParams_nullPermissionsFallsBackToDefaults() {
    String accountId = "accountId";

    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", accountId).build();
    InitContainerV2StepInfo containerV2StepInfo =
        InitContainerV2StepInfo.builder()
            .sharedPaths(ParameterField.createValueField(new ArrayList<>(List.of("pathA"))))
            .pluginsData(Collections.emptyMap())
            .infrastructure(ContainerK8sInfra.builder()
                                .spec(ContainerInfraYamlSpec.builder()
                                          .connectorRef(ParameterField.createValueField("k8sInfra"))
                                          .automountServiceAccountToken(ParameterField.createValueField(false))
                                          .priorityClassName(ParameterField.createValueField("className"))
                                          .build())
                                .build())
            .build();
    containerV2StepInfo.setIdentifier("identifier");

    doReturn(new HashMap<>()).when(harnessTokenUtils).getPrincipalTokenEnvVariables(ambiance, accountId, null);
    setupCommonK8Mocks();

    containerStepInitHelper.getK8InitializeTaskParams(
        containerV2StepInfo, ambiance, "logPrefix", Collections.emptyList());

    verify(harnessTokenUtils).getPrincipalTokenEnvVariables(ambiance, accountId, null);
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void testVmInitializeParams_injectsHarnessTokenEnvVars() {
    Map<String, String> permissions = new HashMap<>(Map.of("pipelines", "execute"));
    Map<String, String> tokenEnvVars =
        new HashMap<>(Map.of("HARNESS_TOKEN", "token123", "HARNESS_BASE_URL", "https://app.harness.io"));
    String accountId = "accountId";
    String orgId = "orgId";
    String projectId = "projectId";

    ContainerStepInfra vmInfra = ContainerVMInfra.builder()
                                     .spec(VmPoolYaml.builder()
                                               .spec(VmPoolYamlSpec.builder()
                                                         .poolName(ParameterField.createValueField("poolName"))
                                                         .os(ParameterField.createValueField(OSType.Linux))
                                                         .build())
                                               .build())
                                     .build();
    InitContainerV2StepInfo containerStepSpec =
        InitContainerV2StepInfo.builder().infrastructure(vmInfra).permissions(permissions).build();

    Ambiance ambiance =
        Ambiance.newBuilder()
            .putAllSetupAbstractions(
                Map.of("accountId", accountId, "orgIdentifier", orgId, "projectIdentifier", projectId))
            .setMetadata(ExecutionMetadata.newBuilder().setPipelineIdentifier("pipeline1").setRunSequence(1).build())
            .addLevels(Level.newBuilder().setRuntimeId("runtimeId").setIdentifier("stageId").build())
            .build();

    doReturn(tokenEnvVars).when(harnessTokenUtils).getPrincipalTokenEnvVariables(ambiance, accountId, permissions);
    doReturn(Optional.empty()).when(containerStepInitHelper).getHarnessImageConnector(vmInfra);
    doReturn(new HashMap<>()).when(vmInitializeUtils).getVolumeToMountPath(any(), any(), any(), any());
    doReturn("/workdir").when(vmInitializeUtils).getWorkDir(any(), any(), any());
    doReturn(new HashMap<>()).when(vmInitializeUtils).getCommonStepEnvVariables(any());
    doReturn(new HashMap<>()).when(k8sPodInitUtils).getLogServiceEnvVariables(any(), any());
    doReturn(new HashMap<>()).when(k8sPodInitUtils).getCommonStepEnvVariables(any(), any(), any(), any());
    doReturn(new HashMap<>()).when(vmInitializeUtils).getBuildTags(any(), any(), any());

    CIVmInitializeTaskParams result = containerStepInitHelper.getVmInitializeParams(containerStepSpec, ambiance,
        "poolId", Collections.emptyList(), Optional.empty(), false, 600L, "logPrefix", "stepId");

    verify(harnessTokenUtils).getPrincipalTokenEnvVariables(ambiance, accountId, permissions);
    assertThat(result.getEnvironment()).containsEntry("HARNESS_TOKEN", "token123");
    assertThat(result.getEnvironment()).containsEntry("HARNESS_BASE_URL", "https://app.harness.io");
    assertThat(result.getSecrets()).contains("token123");
    assertThat(result.getSecrets()).contains("https://app.harness.io");
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetActiveDeadlineSecondsForK8s_ffDisabled_returnsDefault() throws Exception {
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", "testAccount").build();
    when(featureFlagService.isEnabled(FeatureName.CDS_CONTAINER_STEP_GROUP_STAGE_TIMEOUT, "testAccount"))
        .thenReturn(false);

    Method method =
        ContainerStepInitHelper.class.getDeclaredMethod("getActiveDeadlineSeconds", Ambiance.class, boolean.class);
    method.setAccessible(true);
    Long result = (Long) method.invoke(containerStepInitHelper, ambiance, true);

    assertThat(result).isEqualTo(CIConstants.POD_MAX_TTL_SECS);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetActiveDeadlineSecondsForK8s_ffEnabled_timeoutOver24h_returnsBuffered() throws Exception {
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", "testAccount").build();
    when(featureFlagService.isEnabled(FeatureName.CDS_CONTAINER_STEP_GROUP_STAGE_TIMEOUT, "testAccount"))
        .thenReturn(true);

    StageDetails stageDetails =
        StageDetails.builder().timeout(ParameterField.createValueField(Timeout.fromString("30h"))).build();
    OptionalSweepingOutput optionalSweepingOutput =
        OptionalSweepingOutput.builder().found(true).output(stageDetails).build();
    when(executionSweepingOutputResolver.resolveOptional(
             eq(ambiance), eq(RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails))))
        .thenReturn(optionalSweepingOutput);

    Method method =
        ContainerStepInitHelper.class.getDeclaredMethod("getActiveDeadlineSeconds", Ambiance.class, boolean.class);
    method.setAccessible(true);
    Long result = (Long) method.invoke(containerStepInitHelper, ambiance, true);

    long expectedMillis = 30 * 60 * 60 * 1000L;
    long expectedSeconds = (expectedMillis + CIConstants.TEN_MINUTES_IN_MILLI_SEC) / 1000;
    assertThat(result).isEqualTo(expectedSeconds);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetActiveDeadlineSecondsForK8s_ffEnabled_timeoutUnder24h_returnsDefault() throws Exception {
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", "testAccount").build();
    when(featureFlagService.isEnabled(FeatureName.CDS_CONTAINER_STEP_GROUP_STAGE_TIMEOUT, "testAccount"))
        .thenReturn(true);

    StageDetails stageDetails =
        StageDetails.builder().timeout(ParameterField.createValueField(Timeout.fromString("12h"))).build();
    OptionalSweepingOutput optionalSweepingOutput =
        OptionalSweepingOutput.builder().found(true).output(stageDetails).build();
    when(executionSweepingOutputResolver.resolveOptional(
             eq(ambiance), eq(RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails))))
        .thenReturn(optionalSweepingOutput);

    Method method =
        ContainerStepInitHelper.class.getDeclaredMethod("getActiveDeadlineSeconds", Ambiance.class, boolean.class);
    method.setAccessible(true);
    Long result = (Long) method.invoke(containerStepInitHelper, ambiance, true);

    assertThat(result).isEqualTo(CIConstants.POD_MAX_TTL_SECS);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetActiveDeadlineSecondsForK8s_ffEnabled_stageDetailsNotFound_returnsDefault() throws Exception {
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", "testAccount").build();
    when(featureFlagService.isEnabled(FeatureName.CDS_CONTAINER_STEP_GROUP_STAGE_TIMEOUT, "testAccount"))
        .thenReturn(true);

    OptionalSweepingOutput optionalSweepingOutput = OptionalSweepingOutput.builder().found(false).build();
    when(executionSweepingOutputResolver.resolveOptional(
             eq(ambiance), eq(RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails))))
        .thenReturn(optionalSweepingOutput);

    Method method =
        ContainerStepInitHelper.class.getDeclaredMethod("getActiveDeadlineSeconds", Ambiance.class, boolean.class);
    method.setAccessible(true);
    Long result = (Long) method.invoke(containerStepInitHelper, ambiance, true);

    assertThat(result).isEqualTo(CIConstants.POD_MAX_TTL_SECS);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetActiveDeadlineSecondsForK8s_ffEnabled_timeoutNull_returnsDefault() throws Exception {
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", "testAccount").build();
    when(featureFlagService.isEnabled(FeatureName.CDS_CONTAINER_STEP_GROUP_STAGE_TIMEOUT, "testAccount"))
        .thenReturn(true);

    StageDetails stageDetails = StageDetails.builder().build();
    OptionalSweepingOutput optionalSweepingOutput =
        OptionalSweepingOutput.builder().found(true).output(stageDetails).build();
    when(executionSweepingOutputResolver.resolveOptional(
             eq(ambiance), eq(RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails))))
        .thenReturn(optionalSweepingOutput);

    Method method =
        ContainerStepInitHelper.class.getDeclaredMethod("getActiveDeadlineSeconds", Ambiance.class, boolean.class);
    method.setAccessible(true);
    Long result = (Long) method.invoke(containerStepInitHelper, ambiance, true);

    assertThat(result).isEqualTo(CIConstants.POD_MAX_TTL_SECS);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetActiveDeadlineSeconds_ffEnabled_timeoutOver24h() throws Exception {
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", "testAccount").build();
    when(featureFlagService.isEnabled(FeatureName.CDS_CONTAINER_STEP_GROUP_STAGE_TIMEOUT, "testAccount"))
        .thenReturn(true);

    StageDetails stageDetails =
        StageDetails.builder().timeout(ParameterField.createValueField(Timeout.fromString("30h"))).build();
    OptionalSweepingOutput optionalSweepingOutput =
        OptionalSweepingOutput.builder().found(true).output(stageDetails).build();
    when(executionSweepingOutputResolver.resolveOptional(
             eq(ambiance), eq(RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails))))
        .thenReturn(optionalSweepingOutput);

    Method method =
        ContainerStepInitHelper.class.getDeclaredMethod("getActiveDeadlineSeconds", Ambiance.class, boolean.class);
    method.setAccessible(true);
    Long result = (Long) method.invoke(containerStepInitHelper, ambiance, false);

    long expectedMillis = 30 * 60 * 60 * 1000L;
    long expectedSeconds = expectedMillis / 1000;
    assertThat(result).isEqualTo(expectedSeconds);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetActiveDeadlineSeconds_ffDisabled_returnsDefault() throws Exception {
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", "testAccount").build();
    when(featureFlagService.isEnabled(FeatureName.CDS_CONTAINER_STEP_GROUP_STAGE_TIMEOUT, "testAccount"))
        .thenReturn(false);

    Method method =
        ContainerStepInitHelper.class.getDeclaredMethod("getActiveDeadlineSeconds", Ambiance.class, boolean.class);
    method.setAccessible(true);
    Long result = (Long) method.invoke(containerStepInitHelper, ambiance, false);

    assertThat(result).isEqualTo(CIConstants.POD_MAX_TTL_SECS);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetActiveDeadlineSecondsForK8s_ffEnabled_timeoutExactly24h_returnsDefault() throws Exception {
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", "testAccount").build();
    when(featureFlagService.isEnabled(FeatureName.CDS_CONTAINER_STEP_GROUP_STAGE_TIMEOUT, "testAccount"))
        .thenReturn(true);

    StageDetails stageDetails =
        StageDetails.builder().timeout(ParameterField.createValueField(Timeout.fromString("24h"))).build();
    OptionalSweepingOutput optionalSweepingOutput =
        OptionalSweepingOutput.builder().found(true).output(stageDetails).build();
    when(executionSweepingOutputResolver.resolveOptional(
             eq(ambiance), eq(RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails))))
        .thenReturn(optionalSweepingOutput);

    Method method =
        ContainerStepInitHelper.class.getDeclaredMethod("getActiveDeadlineSeconds", Ambiance.class, boolean.class);
    method.setAccessible(true);
    Long result = (Long) method.invoke(containerStepInitHelper, ambiance, true);

    assertThat(result).isEqualTo(CIConstants.POD_MAX_TTL_SECS);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetActiveDeadlineSeconds_vmPath_stageDetailsNotFound_returnsDefault() throws Exception {
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", "testAccount").build();
    when(featureFlagService.isEnabled(FeatureName.CDS_CONTAINER_STEP_GROUP_STAGE_TIMEOUT, "testAccount"))
        .thenReturn(true);

    OptionalSweepingOutput optionalSweepingOutput = OptionalSweepingOutput.builder().found(false).build();
    when(executionSweepingOutputResolver.resolveOptional(
             eq(ambiance), eq(RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails))))
        .thenReturn(optionalSweepingOutput);

    Method method =
        ContainerStepInitHelper.class.getDeclaredMethod("getActiveDeadlineSeconds", Ambiance.class, boolean.class);
    method.setAccessible(true);
    Long result = (Long) method.invoke(containerStepInitHelper, ambiance, false);

    assertThat(result).isEqualTo(CIConstants.POD_MAX_TTL_SECS);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetActiveDeadlineSeconds_vmPath_timeoutNull_returnsDefault() throws Exception {
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", "testAccount").build();
    when(featureFlagService.isEnabled(FeatureName.CDS_CONTAINER_STEP_GROUP_STAGE_TIMEOUT, "testAccount"))
        .thenReturn(true);

    StageDetails stageDetails = StageDetails.builder().build();
    OptionalSweepingOutput optionalSweepingOutput =
        OptionalSweepingOutput.builder().found(true).output(stageDetails).build();
    when(executionSweepingOutputResolver.resolveOptional(
             eq(ambiance), eq(RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails))))
        .thenReturn(optionalSweepingOutput);

    Method method =
        ContainerStepInitHelper.class.getDeclaredMethod("getActiveDeadlineSeconds", Ambiance.class, boolean.class);
    method.setAccessible(true);
    Long result = (Long) method.invoke(containerStepInitHelper, ambiance, false);

    assertThat(result).isEqualTo(CIConstants.POD_MAX_TTL_SECS);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetK8InitializeTaskParams_ephemeralDelegateMode_noInitContainerNoLiteEngineNoAddonVolume() {
    String accountId = "accountId";
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", accountId).build();

    ContainerK8sInfra infrastructure =
        ContainerK8sInfra.builder()
            .spec(ContainerInfraYamlSpec.builder()
                      .connectorRef(ParameterField.createValueField("k8sConnector"))
                      .automountServiceAccountToken(ParameterField.createValueField(false))
                      .priorityClassName(ParameterField.createValueField("className"))
                      .build())
            .build();
    InitContainerV2StepInfo stepInfo = buildK8StepInfo(infrastructure);

    when(featureFlagService.isEnabled(FeatureName.PIPE_ENABLE_EPHEMERAL_DELEGATE_MODE, accountId)).thenReturn(true);
    when(featureFlagService.isEnabled(FeatureName.CDS_CONTAINER_STEP_GROUP_STAGE_TIMEOUT, accountId)).thenReturn(false);

    ArgumentCaptor<Boolean> includeAddonCaptor = ArgumentCaptor.forClass(Boolean.class);
    doReturn(new HashMap<>()).when(k8sPodInitUtils).getVolumeToMountPath(any(), any(), includeAddonCaptor.capture());

    doReturn(Pair.of(2, 500)).when(k8sPodInitUtils).getStepLimits(any(), any(), anyBoolean());
    doReturn(ContainerSecurityContext.builder().build())
        .when(k8sPodInitUtils)
        .getCtrSecurityContext(any(ContainerK8sInfra.class));
    doReturn("test-pod").when(k8sPodInitUtils).generatePodName(any());

    CIK8InitializeTaskParams result =
        containerStepInitHelper.getK8InitializeTaskParams(stepInfo, ambiance, "logPrefix", Collections.emptyList());

    CIK8PodParams<CIK8ContainerParams> podParams = result.getCik8PodParams();

    assertThat(podParams.getInitContainerParamsList()).isEmpty();
    assertThat(podParams.getContainerParamsList().stream().noneMatch(c -> "lite-engine".equals(c.getName()))).isTrue();
    assertThat(includeAddonCaptor.getValue()).isFalse();
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetActiveDeadlineSeconds_vmPath_timeoutUnder24h_returnsDefault() throws Exception {
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", "testAccount").build();
    when(featureFlagService.isEnabled(FeatureName.CDS_CONTAINER_STEP_GROUP_STAGE_TIMEOUT, "testAccount"))
        .thenReturn(true);

    StageDetails stageDetails =
        StageDetails.builder().timeout(ParameterField.createValueField(Timeout.fromString("12h"))).build();
    OptionalSweepingOutput optionalSweepingOutput =
        OptionalSweepingOutput.builder().found(true).output(stageDetails).build();
    when(executionSweepingOutputResolver.resolveOptional(
             eq(ambiance), eq(RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails))))
        .thenReturn(optionalSweepingOutput);

    Method method =
        ContainerStepInitHelper.class.getDeclaredMethod("getActiveDeadlineSeconds", Ambiance.class, boolean.class);
    method.setAccessible(true);
    Long result = (Long) method.invoke(containerStepInitHelper, ambiance, false);

    assertThat(result).isEqualTo(CIConstants.POD_MAX_TTL_SECS);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void getStepContainersForEcs_ephemeralDelegateModeSkipsSetupAddonAndLiteEngine() throws Exception {
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", "accountId").build();
    InitContainerV2StepInfo containerStepInfo =
        InitContainerV2StepInfo.builder()
            .stepsExecutionConfig(StepsExecutionConfig.builder().steps(Collections.emptyList()).build())
            .sharedPaths(ParameterField.createValueField(new ArrayList<>()))
            .pluginsData(Collections.emptyMap())
            .build();
    containerStepInfo.setIdentifier("sg1");
    ContainerDetailsSweepingOutput podDetails = ContainerDetailsSweepingOutput.builder().build();
    ContainerEcsInfra ecsInfra = ContainerEcsInfra.builder()
                                     .type(ContainerStepInfra.Type.ECS_DIRECT)
                                     .spec(EcsDirectInfraYamlSpec.builder().build())
                                     .build();

    when(featureFlagService.isEnabled(eq(FeatureName.PIPE_ENABLE_EPHEMERAL_DELEGATE_MODE), eq("accountId")))
        .thenReturn(true);
    ArgumentCaptor<Boolean> includeAddonCaptor = ArgumentCaptor.forClass(Boolean.class);
    doReturn(new HashMap<>()).when(k8sPodInitUtils).getVolumeToMountPath(any(), any(), includeAddonCaptor.capture());
    when(k8sPodInitUtils.getOS(any())).thenReturn(OSType.Linux);
    when(k8sPodInitUtils.getCommonStepEnvVariables(any(), any(), any(), any())).thenReturn(new HashMap<>());
    when(harnessTokenUtils.getPrincipalTokenEnvVariables(any(), any(), any())).thenReturn(null);
    when(k8sPodInitUtils.getServiceEnvironmentVars(any(), any()))
        .thenReturn(ServiceEnvironmentVars.builder().logEnvVars(new HashMap<>()).stoEnvVars(new HashMap<>()).build());
    when(harnessImageUtils.getHarnessImageConnectorDetailsForEcs(any(), any()))
        .thenReturn(ConnectorDetails.builder().build());
    when(k8sPodInitUtils.getStepLimits(any(), any(), anyBoolean())).thenReturn(Pair.of(500, 256));
    when(k8sPodInitUtils.getWorkDir()).thenReturn("/harness");
    when(k8sPodInitUtils.getCtrSecurityContext(any(ContainerEcsInfra.class)))
        .thenReturn(ContainerSecurityContext.builder().build());
    doNothing().when(k8sPodInitUtils).checkSecretAccess(any(), any(), any(), any(), any());
    doNothing().when(k8sPodInitUtils).consumeSweepingOutput(any(), any(), any());
    when(pmsFeatureFlagService.isEnabled(anyString(), any(FeatureName.class))).thenReturn(false);
    when(featureFlagService.isEnabledReloadCache(any(), any())).thenReturn(false);

    Method method = ContainerStepInitHelper.class.getDeclaredMethod("getStepContainersForEcs", ContainerStepSpec.class,
        ContainerDetailsSweepingOutput.class, ContainerEcsInfra.class, Ambiance.class, List.class, String.class);
    method.setAccessible(true);
    @SuppressWarnings("unchecked")
    Pair<CIECSContainerParams, List<CIECSContainerParams>> result =
        (Pair<CIECSContainerParams, List<CIECSContainerParams>>) method.invoke(containerStepInitHelper,
            containerStepInfo, podDetails, ecsInfra, ambiance, Collections.<PodVolume>emptyList(), "log");

    assertThat(result.getLeft()).isNull();
    assertThat(result.getRight()).isEmpty();
    assertThat(includeAddonCaptor.getValue()).isFalse();
    verify(containerParamsProvider, never())
        .getSetupAddonEcsContainerParams(any(), any(), any(), any(), any(), any(), any());
    verify(containerParamsProvider, never())
        .getLiteEngineEcsContainerParams(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
  }
}
