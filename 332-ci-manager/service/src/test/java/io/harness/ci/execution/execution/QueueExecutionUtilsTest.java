/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.execution;

import static io.harness.rule.OwnerRule.ANURAG_MADNAWAT;
import static io.harness.rule.OwnerRule.DEVESH;
import static io.harness.rule.OwnerRule.SAURABH;
import static io.harness.rule.OwnerRule.SOUMYAJIT;
import static io.harness.rule.OwnerRule.TAPAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.ModuleType;
import io.harness.app.beans.entities.ExecutionQueueLimit;
import io.harness.beans.FeatureName;
import io.harness.beans.execution.license.CILicenseService;
import io.harness.beans.stages.parameters.IntegrationStageStepParametersPMS;
import io.harness.beans.yaml.extended.CIResourceClass;
import io.harness.beans.yaml.extended.infrastrucutre.HostedVmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml.K8sDirectInfraYamlSpec;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.beans.yaml.extended.infrastrucutre.Platform;
import io.harness.category.element.UnitTests;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.config.ExecutionLimitSpec;
import io.harness.ci.config.ExecutionLimits;
import io.harness.ci.config.GlobalQueueingConfig;
import io.harness.ci.execution.execution.metadata.CIExecutionMetadata;
import io.harness.ci.execution.integrationstage.vm.intfc.VmInitializeUtils;
import io.harness.ci.executionplan.base.CIExecutionTestBase;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.ci.metrics.ExecutionMetricsService;
import io.harness.licensing.Edition;
import io.harness.licensing.beans.summary.dto.CILicenseSummaryDTO;
import io.harness.licensing.beans.summary.dto.IDPLicenseSummaryDTO;
import io.harness.plancreator.steps.common.StageElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.yaml.ParameterField;
import io.harness.repositories.CIExecutionRepository;
import io.harness.repositories.ExecutionQueueLimitRepository;
import io.harness.rule.Owner;
import io.harness.utils.CILicenseUsageUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class QueueExecutionUtilsTest extends CIExecutionTestBase {
  @InjectMocks private QueueExecutionUtils queueExecutionUtils;
  @Mock private ExecutionQueueLimitRepository executionQueueLimitRepository;
  @Mock CIExecutionRepository ciExecutionRepository;
  @Mock CILicenseService ciLicenseService;
  private static final String accountID = "accountID";
  @Mock private ExecutionLimits executionLimits;
  @Mock private ExecutionMetricsService executionMetricsService;
  @Mock private VmInitializeUtils vmInitializeUtils;
  @Mock private CILicenseUsageUtils ciLicenseUsageUtils;
  @Mock private CIFeatureFlagService featureFlagService;
  @Mock private CIExecutionServiceConfig ciExecutionServiceConfig;
  @Mock private GlobalQueueingConfig globalQueueingConfig;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    when(executionLimits.getTeam())
        .thenReturn(ExecutionLimitSpec.builder().defaultTotalExecutionCount(50).defaultMacExecutionCount(10).build());
    when(executionLimits.getEnterprise())
        .thenReturn(ExecutionLimitSpec.builder().defaultTotalExecutionCount(100).defaultMacExecutionCount(10).build());
    when(executionLimits.getDevopsEssentials())
        .thenReturn(ExecutionLimitSpec.builder().defaultTotalExecutionCount(65).defaultMacExecutionCount(5).build());
    when(executionLimits.getEssentials())
        .thenReturn(ExecutionLimitSpec.builder().defaultTotalExecutionCount(65).defaultMacExecutionCount(5).build());
    when(executionLimits.getFree())
        .thenReturn(ExecutionLimitSpec.builder().defaultTotalExecutionCount(20).defaultMacExecutionCount(5).build());
  }

  @Captor ArgumentCaptor<CIExecutionMetadata> executionMetadataArgumentCaptor;

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void testAddActiveExecutionBuild() {
    K8sDirectInfraYamlSpec k8sDirectInfraYaml =
        K8sDirectInfraYamlSpec.builder().os(ParameterField.createValueField(OSType.Linux)).build();
    Infrastructure infrastructure =
        K8sDirectInfraYaml.builder().type(Infrastructure.Type.KUBERNETES_DIRECT).spec(k8sDirectInfraYaml).build();

    String accountID = "abcd";
    String stageExecutionID = "efgh";
    CIExecutionMetadata ciExecutionMetadata = CIExecutionMetadata.builder()
                                                  .accountId(accountID)
                                                  .buildType(OSType.Linux)
                                                  .stageExecutionId(stageExecutionID)
                                                  .infraType(Infrastructure.Type.KUBERNETES_DIRECT)
                                                  .build();

    queueExecutionUtils.addExecutionRecord(infrastructure, accountID, stageExecutionID, null);
    //    verify(ciExecutionRepository,times(1)).save(any());
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testAddExecutionRecord_withNullTimeout() {
    // Given
    K8sDirectInfraYamlSpec k8sDirectInfraYaml =
        K8sDirectInfraYamlSpec.builder().os(ParameterField.createValueField(OSType.Linux)).build();
    Infrastructure infrastructure =
        K8sDirectInfraYaml.builder().type(Infrastructure.Type.KUBERNETES_DIRECT).spec(k8sDirectInfraYaml).build();

    String accountId = "test-account";
    String stageExecutionId = "test-stage-exec-id";

    when(ciExecutionRepository.findByStageExecutionId(stageExecutionId)).thenReturn(null);

    // When
    queueExecutionUtils.addExecutionRecord(infrastructure, accountId, stageExecutionId, null);

    // Then
    verify(ciExecutionRepository).save(executionMetadataArgumentCaptor.capture());
    CIExecutionMetadata savedMetadata = executionMetadataArgumentCaptor.getValue();

    assertThat(savedMetadata.getAccountId()).isEqualTo(accountId);
    assertThat(savedMetadata.getStageExecutionId()).isEqualTo(stageExecutionId);
    assertThat(savedMetadata.getBuildType()).isEqualTo(OSType.Linux);
    // expireAfter should use builder default (24 hours) when timeout is null or <= 24 hours
    assertThat(savedMetadata.getExpireAfter()).isNotNull();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testAddExecutionRecord_withShortTimeout() {
    // Given
    Infrastructure infrastructure = getHostedVMInfrastructure(OSType.Linux);
    String accountId = "test-account";
    String stageExecutionId = "test-stage-exec-id";
    Long timeoutInSeconds = 12L * 60 * 60; // 12 hours in seconds (43200)

    when(ciExecutionRepository.findByStageExecutionId(stageExecutionId)).thenReturn(null);

    // When
    queueExecutionUtils.addExecutionRecord(infrastructure, accountId, stageExecutionId, timeoutInSeconds);

    // Then
    verify(ciExecutionRepository).save(executionMetadataArgumentCaptor.capture());
    CIExecutionMetadata savedMetadata = executionMetadataArgumentCaptor.getValue();

    assertThat(savedMetadata.getAccountId()).isEqualTo(accountId);
    assertThat(savedMetadata.getStageExecutionId()).isEqualTo(stageExecutionId);
    // expireAfter should use builder default (24 hours) since timeout is <= 24 hours
    assertThat(savedMetadata.getExpireAfter()).isNotNull();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testAddExecutionRecord_withExactly24HoursTimeout() {
    // Given - boundary test at exactly 24 hours
    Infrastructure infrastructure = getHostedVMInfrastructure(OSType.Linux);
    String accountId = "test-account";
    String stageExecutionId = "test-stage-exec-id";
    Long timeoutInSeconds = 24L * 60 * 60; // exactly 24 hours = 86400 seconds

    when(ciExecutionRepository.findByStageExecutionId(stageExecutionId)).thenReturn(null);

    // When
    queueExecutionUtils.addExecutionRecord(infrastructure, accountId, stageExecutionId, timeoutInSeconds);

    // Then
    verify(ciExecutionRepository).save(executionMetadataArgumentCaptor.capture());
    CIExecutionMetadata savedMetadata = executionMetadataArgumentCaptor.getValue();

    assertThat(savedMetadata.getAccountId()).isEqualTo(accountId);
    assertThat(savedMetadata.getStageExecutionId()).isEqualTo(stageExecutionId);
    // expireAfter should use builder default (24 hours) since timeout is not > 24 hours (boundary case)
    assertThat(savedMetadata.getExpireAfter()).isNotNull();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testAddExecutionRecord_withLongTimeout() {
    // Given
    Infrastructure infrastructure = getHostedVMInfrastructure(OSType.Linux);
    String accountId = "test-account";
    String stageExecutionId = "test-stage-exec-id";
    Long timeoutInSeconds = 2L * 24 * 60 * 60; // 2 days in seconds (172800)

    when(ciExecutionRepository.findByStageExecutionId(stageExecutionId)).thenReturn(null);

    long beforeCall = System.currentTimeMillis();

    // When
    queueExecutionUtils.addExecutionRecord(infrastructure, accountId, stageExecutionId, timeoutInSeconds);

    long afterCall = System.currentTimeMillis();

    // Then
    verify(ciExecutionRepository).save(executionMetadataArgumentCaptor.capture());
    CIExecutionMetadata savedMetadata = executionMetadataArgumentCaptor.getValue();

    assertThat(savedMetadata.getAccountId()).isEqualTo(accountId);
    assertThat(savedMetadata.getStageExecutionId()).isEqualTo(stageExecutionId);
    // expireAfter should be overridden with timeout + 10 minutes buffer
    assertThat(savedMetadata.getExpireAfter()).isNotNull();

    // Verify expireAfter is in the future (timeout + 10 min buffer)
    long minExpectedExpire = beforeCall + (timeoutInSeconds * 1000) + (10 * 60 * 1000); // timeout + 10 min buffer
    long maxExpectedExpire = afterCall + (timeoutInSeconds * 1000) + (10 * 60 * 1000);

    assertThat(savedMetadata.getExpireAfter().getTime()).isBetween(minExpectedExpire, maxExpectedExpire);
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testAddExecutionRecord_withMaxValidTimeout() {
    // Given
    Infrastructure infrastructure = getHostedVMInfrastructure(OSType.Linux);
    String accountId = "test-account";
    String stageExecutionId = "test-stage-exec-id";
    Long timeoutInSeconds = 35L * 24 * 60 * 60; // 35 days in seconds (3024000)

    when(ciExecutionRepository.findByStageExecutionId(stageExecutionId)).thenReturn(null);

    long beforeCall = System.currentTimeMillis();

    // When
    queueExecutionUtils.addExecutionRecord(infrastructure, accountId, stageExecutionId, timeoutInSeconds);

    long afterCall = System.currentTimeMillis();

    // Then
    verify(ciExecutionRepository).save(executionMetadataArgumentCaptor.capture());
    CIExecutionMetadata savedMetadata = executionMetadataArgumentCaptor.getValue();

    assertThat(savedMetadata.getAccountId()).isEqualTo(accountId);
    assertThat(savedMetadata.getStageExecutionId()).isEqualTo(stageExecutionId);
    // expireAfter should be overridden with timeout + 10 minutes buffer
    assertThat(savedMetadata.getExpireAfter()).isNotNull();

    long minExpectedExpire = beforeCall + (timeoutInSeconds * 1000) + (10 * 60 * 1000);
    long maxExpectedExpire = afterCall + (timeoutInSeconds * 1000) + (10 * 60 * 1000);

    assertThat(savedMetadata.getExpireAfter().getTime()).isBetween(minExpectedExpire, maxExpectedExpire);
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testAddExecutionRecord_withVeryLongTimeout() {
    // Given
    Infrastructure infrastructure = getHostedVMInfrastructure(OSType.Linux);
    String accountId = "test-account";
    String stageExecutionId = "test-stage-exec-id";
    Long timeoutInSeconds = 40L * 24 * 60 * 60; // 40 days in seconds (3456000)

    when(ciExecutionRepository.findByStageExecutionId(stageExecutionId)).thenReturn(null);

    long beforeCall = System.currentTimeMillis();

    // When
    queueExecutionUtils.addExecutionRecord(infrastructure, accountId, stageExecutionId, timeoutInSeconds);

    long afterCall = System.currentTimeMillis();

    // Then
    verify(ciExecutionRepository).save(executionMetadataArgumentCaptor.capture());
    CIExecutionMetadata savedMetadata = executionMetadataArgumentCaptor.getValue();

    assertThat(savedMetadata.getAccountId()).isEqualTo(accountId);
    assertThat(savedMetadata.getStageExecutionId()).isEqualTo(stageExecutionId);
    // expireAfter should be set with timeout + 10 minutes buffer (any timeout > 24 hours gets this)
    assertThat(savedMetadata.getExpireAfter()).isNotNull();

    long minExpectedExpire = beforeCall + (timeoutInSeconds * 1000) + (10 * 60 * 1000);
    long maxExpectedExpire = afterCall + (timeoutInSeconds * 1000) + (10 * 60 * 1000);

    assertThat(savedMetadata.getExpireAfter().getTime()).isBetween(minExpectedExpire, maxExpectedExpire);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testShouldQueueWithEnterpriseLicense101Execution() {
    Infrastructure hostedVMInfrastructure = getHostedVMInfrastructure(OSType.Linux);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Linux),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(101L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.MacOS),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(0L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Windows),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(0L);
    when(ciLicenseService.getLicenseSummary(eq(accountID), eq(ModuleType.CI.name()), any()))
        .thenReturn(IDPLicenseSummaryDTO.builder().edition(Edition.ENTERPRISE).build());

    doReturn(
        Optional.of(
            ExecutionQueueLimit.builder().accountIdentifier(accountID).totalExecLimit("100").macExecLimit("2").build()))
        .when(executionQueueLimitRepository)
        .findFirstByAccountIdentifier(accountID);

    assertThat(queueExecutionUtils.shouldQueue(accountID, hostedVMInfrastructure, true, ModuleType.CI.name())).isTrue();
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testShouldNotQueueWithEnterpriseLicense100Executions() {
    Infrastructure hostedVMInfrastructure = getHostedVMInfrastructure(OSType.Linux);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Linux),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(100L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.MacOS),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(0L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Windows),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(0L);
    when(ciLicenseService.getLicenseSummary(eq(accountID), eq(ModuleType.CI.name()), any()))
        .thenReturn(IDPLicenseSummaryDTO.builder().edition(Edition.ENTERPRISE).build());

    assertThat(queueExecutionUtils.shouldQueue(accountID, hostedVMInfrastructure, true, ModuleType.CI.name()))
        .isFalse();
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testShouldQueueWithTeamLicense51Executions() {
    Infrastructure hostedVMInfrastructure = getHostedVMInfrastructure(OSType.Linux);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Linux),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(51L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.MacOS),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(0L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Windows),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(0L);
    when(ciLicenseService.getLicenseSummary(eq(accountID), eq(ModuleType.CI.name()), any()))
        .thenReturn(IDPLicenseSummaryDTO.builder().edition(Edition.TEAM).build());

    assertThat(queueExecutionUtils.shouldQueue(accountID, hostedVMInfrastructure, true, ModuleType.CI.name())).isTrue();
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testShouldNotQueueWithTeamLicense50Executions() {
    Infrastructure hostedVMInfrastructure = getHostedVMInfrastructure(OSType.Linux);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Linux),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(50L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.MacOS),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(0L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Windows),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(0L);
    when(ciLicenseService.getLicenseSummary(eq(accountID), eq(ModuleType.CI.name()), any()))
        .thenReturn(IDPLicenseSummaryDTO.builder().edition(Edition.TEAM).build());

    assertThat(queueExecutionUtils.shouldQueue(accountID, hostedVMInfrastructure, true, ModuleType.CI.name()))
        .isFalse();
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testShouldQueueWithFreeLicense21Executions() {
    Infrastructure hostedVMInfrastructure = getHostedVMInfrastructure(OSType.Linux);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Linux),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(21L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.MacOS),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(0L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Windows),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(0L);
    when(ciLicenseService.getLicenseSummary(eq(accountID), eq(ModuleType.CI.name()), any()))
        .thenReturn(IDPLicenseSummaryDTO.builder().edition(Edition.FREE).build());

    assertThat(queueExecutionUtils.shouldQueue(accountID, hostedVMInfrastructure, true, ModuleType.CI.name())).isTrue();
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testShouldNotQueueWithFreeLicense20Executions() {
    Infrastructure hostedVMInfrastructure = getHostedVMInfrastructure(OSType.Linux);

    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Linux),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(20L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.MacOS),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(0L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Windows),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(0L);
    when(ciLicenseService.getLicenseSummary(eq(accountID), eq(ModuleType.CI.name()), any()))
        .thenReturn(IDPLicenseSummaryDTO.builder().edition(Edition.FREE).build());

    assertThat(queueExecutionUtils.shouldQueue(accountID, hostedVMInfrastructure, true, ModuleType.CI.name()))
        .isFalse();
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testShouldQueueWithEnterpriseLicense21MacExecution() {
    Infrastructure hostedVMInfrastructure = getHostedVMInfrastructure(OSType.MacOS);

    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Linux),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(11L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.MacOS),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(11L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Windows),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(0L);
    when(ciLicenseService.getLicenseSummary(eq(accountID), eq(ModuleType.CI.name()), any()))
        .thenReturn(IDPLicenseSummaryDTO.builder().edition(Edition.ENTERPRISE).build());

    assertThat(queueExecutionUtils.shouldQueue(accountID, hostedVMInfrastructure, true, ModuleType.CI.name())).isTrue();
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testShouldNotQueueWithEnterpriseLicense10MacExecutions() {
    Infrastructure hostedVMInfrastructure = getHostedVMInfrastructure(OSType.MacOS);

    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Linux),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(10L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.MacOS),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(10L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Windows),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(0L);
    when(ciLicenseService.getLicenseSummary(eq(accountID), eq(ModuleType.CI.name()), any()))
        .thenReturn(IDPLicenseSummaryDTO.builder().edition(Edition.ENTERPRISE).build());

    assertThat(queueExecutionUtils.shouldQueue(accountID, hostedVMInfrastructure, true, ModuleType.CI.name()))
        .isFalse();
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testShouldQueueWithTeamLicense11MacExecutions() {
    Infrastructure hostedVMInfrastructure = getHostedVMInfrastructure(OSType.MacOS);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Linux),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(11L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.MacOS),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(11L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Windows),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(0L);
    when(ciLicenseService.getLicenseSummary(eq(accountID), eq(ModuleType.CI.name()), any()))
        .thenReturn(IDPLicenseSummaryDTO.builder().edition(Edition.TEAM).build());

    assertThat(queueExecutionUtils.shouldQueue(accountID, hostedVMInfrastructure, true, ModuleType.CI.name())).isTrue();
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testShouldNotQueueWithTeamLicense10MacExecutions() {
    Infrastructure hostedVMInfrastructure = getHostedVMInfrastructure(OSType.MacOS);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Linux),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(10L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.MacOS),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(10L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Windows),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(0L);
    when(ciLicenseService.getLicenseSummary(eq(accountID), eq(ModuleType.CI.name()), any()))
        .thenReturn(IDPLicenseSummaryDTO.builder().edition(Edition.TEAM).build());

    assertThat(queueExecutionUtils.shouldQueue(accountID, hostedVMInfrastructure, true, ModuleType.CI.name()))
        .isFalse();
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testShouldQueueWithFreeLicense6MacExecutions() {
    Infrastructure hostedVMInfrastructure = getHostedVMInfrastructure(OSType.MacOS);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Linux),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(6L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.MacOS),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(6L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Windows),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(0L);
    when(ciLicenseService.getLicenseSummary(eq(accountID), eq(ModuleType.CI.name()), any()))
        .thenReturn(IDPLicenseSummaryDTO.builder().edition(Edition.FREE).build());

    assertThat(queueExecutionUtils.shouldQueue(accountID, hostedVMInfrastructure, true, ModuleType.CI.name())).isTrue();
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testShouldNotQueueWithFreeLicense5MacExecutions() {
    Infrastructure hostedVMInfrastructure = getHostedVMInfrastructure(OSType.MacOS);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Linux),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(5L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.MacOS),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(5L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Windows),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(0L);
    when(ciLicenseService.getLicenseSummary(eq(accountID), eq(ModuleType.CI.name()), any()))
        .thenReturn(IDPLicenseSummaryDTO.builder().edition(Edition.FREE).build());

    assertThat(queueExecutionUtils.shouldQueue(accountID, hostedVMInfrastructure, true, ModuleType.CI.name()))
        .isFalse();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testShouldQueueWithEnterpriseLicenseWindowsExecutionsExceedingTotalLimit() {
    Infrastructure hostedVMInfrastructure = getHostedVMInfrastructure(OSType.Windows);

    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Linux),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(50L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.MacOS),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(0L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Windows),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(51L); // Linux(50) + Windows(51) = 101 > 100 (totalLimit for enterprise)
    when(ciLicenseService.getLicenseSummary(eq(accountID), eq(ModuleType.CI.name()), any()))
        .thenReturn(CILicenseSummaryDTO.builder().edition(Edition.ENTERPRISE).build());

    assertThat(queueExecutionUtils.shouldQueue(accountID, hostedVMInfrastructure, true, ModuleType.CI.name())).isTrue();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testShouldNotQueueWithEnterpriseLicenseWindowsExecutionsBelowTotalLimit() {
    Infrastructure hostedVMInfrastructure = getHostedVMInfrastructure(OSType.Windows);

    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Linux),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(50L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.MacOS),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(0L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Windows),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(49L); // Linux(50) + Windows(49) = 99 < 100 (totalLimit for enterprise)
    when(ciLicenseService.getLicenseSummary(eq(accountID), eq(ModuleType.CI.name()), any()))
        .thenReturn(CILicenseSummaryDTO.builder().edition(Edition.ENTERPRISE).build());

    assertThat(queueExecutionUtils.shouldQueue(accountID, hostedVMInfrastructure, true, ModuleType.CI.name()))
        .isFalse();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testShouldQueueWithDevopsEssentialLicenseWindowsExecutionsExceedingTotalLimit() {
    Infrastructure hostedVMInfrastructure = getHostedVMInfrastructure(OSType.Windows);

    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Linux),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(30L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.MacOS),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(0L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Windows),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(36L); // Linux(30) + Windows(36) = 66 > 65 (totalLimit for devopsEssentials)
    when(ciLicenseService.getLicenseSummary(eq(accountID), eq(ModuleType.CI.name()), any()))
        .thenReturn(CILicenseSummaryDTO.builder().edition(Edition.DEVOPS_ESSENTIALS).build());

    assertThat(queueExecutionUtils.shouldQueue(accountID, hostedVMInfrastructure, true, ModuleType.CI.name())).isTrue();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testShouldNotQueueWithDevopsEssentialLicenseWindowsExecutionsBelowTotalLimit() {
    Infrastructure hostedVMInfrastructure = getHostedVMInfrastructure(OSType.Windows);

    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Linux),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(30L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.MacOS),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(0L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Windows),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(34L); // Linux(30) + Windows(34) = 64 < 65 (totalLimit for devopsEssentials)
    when(ciLicenseService.getLicenseSummary(eq(accountID), eq(ModuleType.CI.name()), any()))
        .thenReturn(CILicenseSummaryDTO.builder().edition(Edition.DEVOPS_ESSENTIALS).build());

    assertThat(queueExecutionUtils.shouldQueue(accountID, hostedVMInfrastructure, true, ModuleType.CI.name()))
        .isFalse();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testShouldQueueWithTeamLicenseWindowsExecutionsExceedingTotalLimit() {
    Infrastructure hostedVMInfrastructure = getHostedVMInfrastructure(OSType.Windows);

    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Linux),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(25L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.MacOS),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(0L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Windows),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(26L); // Linux(25) + Windows(26) = 51 > 50 (totalLimit for team)
    when(ciLicenseService.getLicenseSummary(eq(accountID), eq(ModuleType.CI.name()), any()))
        .thenReturn(CILicenseSummaryDTO.builder().edition(Edition.TEAM).build());

    assertThat(queueExecutionUtils.shouldQueue(accountID, hostedVMInfrastructure, true, ModuleType.CI.name())).isTrue();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testShouldNotQueueWithTeamLicenseWindowsExecutionsBelowTotalLimit() {
    Infrastructure hostedVMInfrastructure = getHostedVMInfrastructure(OSType.Windows);

    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Linux),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(25L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.MacOS),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(0L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Windows),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(24L); // Linux(25) + Windows(24) = 49 < 50 (totalLimit for team)
    when(ciLicenseService.getLicenseSummary(eq(accountID), eq(ModuleType.CI.name()), any()))
        .thenReturn(CILicenseSummaryDTO.builder().edition(Edition.TEAM).build());

    assertThat(queueExecutionUtils.shouldQueue(accountID, hostedVMInfrastructure, true, ModuleType.CI.name()))
        .isFalse();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testShouldQueueWithFreeLicenseWindowsExecutionsExceedingTotalLimit() {
    Infrastructure hostedVMInfrastructure = getHostedVMInfrastructure(OSType.Windows);

    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Linux),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(10L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.MacOS),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(0L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Windows),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(11L); // Linux(10) + Windows(11) = 21 > 20 (totalLimit for free)
    when(ciLicenseService.getLicenseSummary(eq(accountID), eq(ModuleType.CI.name()), any()))
        .thenReturn(CILicenseSummaryDTO.builder().edition(Edition.FREE).build());

    assertThat(queueExecutionUtils.shouldQueue(accountID, hostedVMInfrastructure, true, ModuleType.CI.name())).isTrue();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testShouldNotQueueWithFreeLicenseWindowsExecutionsBelowTotalLimit() {
    Infrastructure hostedVMInfrastructure = getHostedVMInfrastructure(OSType.Windows);

    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Linux),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(10L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.MacOS),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(0L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Windows),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(9L); // Linux(10) + Windows(9) = 19 < 20 (totalLimit for free)
    when(ciLicenseService.getLicenseSummary(eq(accountID), eq(ModuleType.CI.name()), any()))
        .thenReturn(CILicenseSummaryDTO.builder().edition(Edition.FREE).build());

    assertThat(queueExecutionUtils.shouldQueue(accountID, hostedVMInfrastructure, true, ModuleType.CI.name()))
        .isFalse();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testShouldQueueBehaviourWithEnterpriseLicenseOverrideExecution() {
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Linux),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(101L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.MacOS),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(0L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Windows),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(3L);
    when(ciLicenseService.getLicenseSummary(eq(accountID), eq(ModuleType.CI.name()), any()))
        .thenReturn(CILicenseSummaryDTO.builder().edition(Edition.ENTERPRISE).build());

    doReturn(
        Optional.of(
            ExecutionQueueLimit.builder().accountIdentifier(accountID).totalExecLimit("100").macExecLimit("2").build()))
        .when(executionQueueLimitRepository)
        .findFirstByAccountIdentifier(accountID);

    assertThat(
        queueExecutionUtils.shouldQueue(accountID, getHostedVMInfrastructure(OSType.Linux), true, ModuleType.CI.name()))
        .isTrue();
    assertThat(
        queueExecutionUtils.shouldQueue(accountID, getHostedVMInfrastructure(OSType.MacOS), true, ModuleType.CI.name()))
        .isFalse();
    assertThat(queueExecutionUtils.shouldQueue(
                   accountID, getHostedVMInfrastructure(OSType.Windows), true, ModuleType.CI.name()))
        .isTrue();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testShouldQueueBehaviourWithEnterpriseLicenseOverrideExecutionNoWindows() {
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Linux),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(101L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.MacOS),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(0L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Windows),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(3L);
    when(ciLicenseService.getLicenseSummary(eq(accountID), eq(ModuleType.CI.name()), any()))
        .thenReturn(CILicenseSummaryDTO.builder().edition(Edition.ENTERPRISE).build());

    doReturn(
        Optional.of(
            ExecutionQueueLimit.builder().accountIdentifier(accountID).totalExecLimit("100").macExecLimit("2").build()))
        .when(executionQueueLimitRepository)
        .findFirstByAccountIdentifier(accountID);

    assertThat(
        queueExecutionUtils.shouldQueue(accountID, getHostedVMInfrastructure(OSType.Linux), true, ModuleType.CI.name()))
        .isTrue();
    assertThat(
        queueExecutionUtils.shouldQueue(accountID, getHostedVMInfrastructure(OSType.MacOS), true, ModuleType.CI.name()))
        .isFalse();
    // Windows also queues because Linux(101) + Windows(3) = 104 > 100 (totalLimit)
    assertThat(queueExecutionUtils.shouldQueue(
                   accountID, getHostedVMInfrastructure(OSType.Windows), true, ModuleType.CI.name()))
        .isTrue();
  }

  @Test(expected = Exception.class)
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testShouldThrowAnExceptionWhenLicenseIsMissing() {
    Infrastructure hostedVMInfrastructure = getHostedVMInfrastructure(OSType.MacOS);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Linux),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(5L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.MacOS),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(5L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Windows),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(0L);
    when(ciLicenseService.getLicenseSummary(eq(accountID), eq(ModuleType.CI.name()), any())).thenReturn(null);

    queueExecutionUtils.shouldQueue(accountID, hostedVMInfrastructure, true, ModuleType.CI.name());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testShouldRunWithEnterpriseLicense99Executions() {
    Infrastructure hostedVMInfrastructure = getHostedVMInfrastructure(OSType.Linux);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Linux),
             eq(List.of(Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(), Status.RUNNING.toString())), any()))
        .thenReturn(99L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.MacOS),
             eq(List.of(Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(), Status.RUNNING.toString())), any()))
        .thenReturn(0L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Windows),
             eq(List.of(Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(), Status.RUNNING.toString())), any()))
        .thenReturn(0L);
    when(ciLicenseService.getLicenseSummary(eq(accountID), eq(ModuleType.IDP.name()), any()))
        .thenReturn(IDPLicenseSummaryDTO.builder().edition(Edition.ENTERPRISE).build());

    assertThat(queueExecutionUtils.shouldRun(accountID, hostedVMInfrastructure, ModuleType.IDP.name())).isTrue();
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testShouldNotRunWithEnterpriseLicense100Executions() {
    Infrastructure hostedVMInfrastructure = getHostedVMInfrastructure(OSType.Linux);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Linux),
             eq(List.of(Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(), Status.RUNNING.toString())), any()))
        .thenReturn(100L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.MacOS),
             eq(List.of(Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(), Status.RUNNING.toString())), any()))
        .thenReturn(0L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Windows),
             eq(List.of(Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(), Status.RUNNING.toString())), any()))
        .thenReturn(0L);
    when(ciLicenseService.getLicenseSummary(eq(accountID), eq(ModuleType.IDP.name()), any()))
        .thenReturn(IDPLicenseSummaryDTO.builder().edition(Edition.ENTERPRISE).build());

    assertThat(queueExecutionUtils.shouldRun(accountID, hostedVMInfrastructure, ModuleType.IDP.name())).isFalse();
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testShouldRunWithEnterpriseLicense9MacExecutions() {
    Infrastructure hostedVMInfrastructure = getHostedVMInfrastructure(OSType.MacOS);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Linux),
             eq(List.of(Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(), Status.RUNNING.toString())), any()))
        .thenReturn(99L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.MacOS),
             eq(List.of(Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(), Status.RUNNING.toString())), any()))
        .thenReturn(9L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Windows),
             eq(List.of(Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(), Status.RUNNING.toString())), any()))
        .thenReturn(0L);
    when(ciLicenseService.getLicenseSummary(eq(accountID), eq(ModuleType.IDP.name()), any()))
        .thenReturn(IDPLicenseSummaryDTO.builder().edition(Edition.ENTERPRISE).build());

    assertThat(queueExecutionUtils.shouldRun(accountID, hostedVMInfrastructure, ModuleType.IDP.name())).isTrue();
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testShouldNotRunWithEnterpriseLicense10MacExecutions() {
    Infrastructure hostedVMInfrastructure = getHostedVMInfrastructure(OSType.MacOS);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Linux),
             eq(List.of(Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(), Status.RUNNING.toString())), any()))
        .thenReturn(10L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.MacOS),
             eq(List.of(Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(), Status.RUNNING.toString())), any()))
        .thenReturn(10L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Windows),
             eq(List.of(Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(), Status.RUNNING.toString())), any()))
        .thenReturn(0L);
    when(ciLicenseService.getLicenseSummary(eq(accountID), eq(ModuleType.IDP.name()), any()))
        .thenReturn(IDPLicenseSummaryDTO.builder().edition(Edition.ENTERPRISE).build());

    assertThat(queueExecutionUtils.shouldRun(accountID, hostedVMInfrastructure, ModuleType.IDP.name())).isFalse();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testShouldRunWithFreeLicense0WindowsExecutions() {
    Infrastructure hostedVMInfrastructure = getHostedVMInfrastructure(OSType.Windows);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Linux),
             eq(List.of(Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(), Status.RUNNING.toString())), any()))
        .thenReturn(0L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.MacOS),
             eq(List.of(Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(), Status.RUNNING.toString())), any()))
        .thenReturn(0L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Windows),
             eq(List.of(Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(), Status.RUNNING.toString())), any()))
        .thenReturn(0L);
    when(ciLicenseService.getLicenseSummary(eq(accountID), eq(ModuleType.CI.name()), any()))
        .thenReturn(CILicenseSummaryDTO.builder().edition(Edition.FREE).build());

    assertThat(queueExecutionUtils.shouldRun(accountID, hostedVMInfrastructure, ModuleType.CI.name())).isTrue();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testShouldNotRunWithFreeLicenseWindowsExecutionsAtTotalLimit() {
    Infrastructure hostedVMInfrastructure = getHostedVMInfrastructure(OSType.Windows);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Linux),
             eq(List.of(Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(), Status.RUNNING.toString())), any()))
        .thenReturn(10L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.MacOS),
             eq(List.of(Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(), Status.RUNNING.toString())), any()))
        .thenReturn(0L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Windows),
             eq(List.of(Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(), Status.RUNNING.toString())), any()))
        .thenReturn(10L); // Linux(10) + Windows(10) = 20 >= 20 (totalLimit for free)
    when(ciLicenseService.getLicenseSummary(eq(accountID), eq(ModuleType.CI.name()), any()))
        .thenReturn(CILicenseSummaryDTO.builder().edition(Edition.FREE).build());

    assertThat(queueExecutionUtils.shouldRun(accountID, hostedVMInfrastructure, ModuleType.CI.name())).isFalse();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testShouldRunWithTeamLicenseWindowsExecutionsBelowTotalLimit() {
    Infrastructure hostedVMInfrastructure = getHostedVMInfrastructure(OSType.Windows);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Linux),
             eq(List.of(Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(), Status.RUNNING.toString())), any()))
        .thenReturn(25L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.MacOS),
             eq(List.of(Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(), Status.RUNNING.toString())), any()))
        .thenReturn(0L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Windows),
             eq(List.of(Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(), Status.RUNNING.toString())), any()))
        .thenReturn(24L); // Linux(25) + Windows(24) = 49 < 50 (totalLimit for team)
    when(ciLicenseService.getLicenseSummary(eq(accountID), eq(ModuleType.CI.name()), any()))
        .thenReturn(CILicenseSummaryDTO.builder().edition(Edition.TEAM).build());

    assertThat(queueExecutionUtils.shouldRun(accountID, hostedVMInfrastructure, ModuleType.CI.name())).isTrue();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testShouldNotRunWithTeamLicenseWindowsExecutionsAtTotalLimit() {
    Infrastructure hostedVMInfrastructure = getHostedVMInfrastructure(OSType.Windows);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Linux),
             eq(List.of(Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(), Status.RUNNING.toString())), any()))
        .thenReturn(25L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.MacOS),
             eq(List.of(Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(), Status.RUNNING.toString())), any()))
        .thenReturn(0L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Windows),
             eq(List.of(Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(), Status.RUNNING.toString())), any()))
        .thenReturn(25L); // Linux(25) + Windows(25) = 50 >= 50 (totalLimit for team)
    when(ciLicenseService.getLicenseSummary(eq(accountID), eq(ModuleType.CI.name()), any()))
        .thenReturn(CILicenseSummaryDTO.builder().edition(Edition.TEAM).build());

    assertThat(queueExecutionUtils.shouldRun(accountID, hostedVMInfrastructure, ModuleType.CI.name())).isFalse();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testShouldRunWithEnterpriseLicenseWindowsExecutionsBelowTotalLimit() {
    Infrastructure hostedVMInfrastructure = getHostedVMInfrastructure(OSType.Windows);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Linux),
             eq(List.of(Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(), Status.RUNNING.toString())), any()))
        .thenReturn(50L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.MacOS),
             eq(List.of(Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(), Status.RUNNING.toString())), any()))
        .thenReturn(0L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Windows),
             eq(List.of(Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(), Status.RUNNING.toString())), any()))
        .thenReturn(49L); // Linux(50) + Windows(49) = 99 < 100 (totalLimit for enterprise)
    when(ciLicenseService.getLicenseSummary(eq(accountID), eq(ModuleType.CI.name()), any()))
        .thenReturn(CILicenseSummaryDTO.builder().edition(Edition.ENTERPRISE).build());

    assertThat(queueExecutionUtils.shouldRun(accountID, hostedVMInfrastructure, ModuleType.CI.name())).isTrue();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testShouldNotRunWithEnterpriseLicenseWindowsExecutionsAtTotalLimit() {
    Infrastructure hostedVMInfrastructure = getHostedVMInfrastructure(OSType.Windows);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Linux),
             eq(List.of(Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(), Status.RUNNING.toString())), any()))
        .thenReturn(50L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.MacOS),
             eq(List.of(Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(), Status.RUNNING.toString())), any()))
        .thenReturn(0L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Windows),
             eq(List.of(Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(), Status.RUNNING.toString())), any()))
        .thenReturn(50L); // Linux(50) + Windows(50) = 100 >= 100 (totalLimit for enterprise)
    when(ciLicenseService.getLicenseSummary(eq(accountID), eq(ModuleType.CI.name()), any()))
        .thenReturn(CILicenseSummaryDTO.builder().edition(Edition.ENTERPRISE).build());

    assertThat(queueExecutionUtils.shouldRun(accountID, hostedVMInfrastructure, ModuleType.CI.name())).isFalse();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testShouldRunWithDevopsEssentialLicenseWindowsExecutionsBelowTotalLimit() {
    Infrastructure hostedVMInfrastructure = getHostedVMInfrastructure(OSType.Windows);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Linux),
             eq(List.of(Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(), Status.RUNNING.toString())), any()))
        .thenReturn(30L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.MacOS),
             eq(List.of(Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(), Status.RUNNING.toString())), any()))
        .thenReturn(0L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Windows),
             eq(List.of(Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(), Status.RUNNING.toString())), any()))
        .thenReturn(34L); // Linux(30) + Windows(34) = 64 < 65 (totalLimit for devopsEssentials)
    when(ciLicenseService.getLicenseSummary(eq(accountID), eq(ModuleType.CI.name()), any()))
        .thenReturn(CILicenseSummaryDTO.builder().edition(Edition.DEVOPS_ESSENTIALS).build());

    assertThat(queueExecutionUtils.shouldRun(accountID, hostedVMInfrastructure, ModuleType.CI.name())).isTrue();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testShouldNotRunWithDevopsEssentialLicenseWindowsExecutionsAtTotalLimit() {
    Infrastructure hostedVMInfrastructure = getHostedVMInfrastructure(OSType.Windows);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Linux),
             eq(List.of(Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(), Status.RUNNING.toString())), any()))
        .thenReturn(30L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.MacOS),
             eq(List.of(Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(), Status.RUNNING.toString())), any()))
        .thenReturn(0L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Windows),
             eq(List.of(Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(), Status.RUNNING.toString())), any()))
        .thenReturn(35L); // Linux(30) + Windows(35) = 65 >= 65 (totalLimit for devopsEssentials)
    when(ciLicenseService.getLicenseSummary(eq(accountID), eq(ModuleType.CI.name()), any()))
        .thenReturn(CILicenseSummaryDTO.builder().edition(Edition.DEVOPS_ESSENTIALS).build());

    assertThat(queueExecutionUtils.shouldRun(accountID, hostedVMInfrastructure, ModuleType.CI.name())).isFalse();
  }

  @Test
  @Owner(developers = TAPAN)
  @Category(UnitTests.class)
  public void testShouldQueueWithEssentialsLicenseWindowsExecutionsExceedingTotalLimit() {
    Infrastructure hostedVMInfrastructure = getHostedVMInfrastructure(OSType.Windows);

    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Linux),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(30L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.MacOS),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(0L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Windows),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(36L); // Linux(30) + Windows(36) = 66 > 65 (totalLimit for essentials)
    when(ciLicenseService.getLicenseSummary(eq(accountID), eq(ModuleType.CI.name()), any()))
        .thenReturn(CILicenseSummaryDTO.builder().edition(Edition.ESSENTIALS).build());

    assertThat(queueExecutionUtils.shouldQueue(accountID, hostedVMInfrastructure, true, ModuleType.CI.name())).isTrue();
  }

  @Test
  @Owner(developers = TAPAN)
  @Category(UnitTests.class)
  public void testShouldNotQueueWithEssentialsLicenseWindowsExecutionsBelowTotalLimit() {
    Infrastructure hostedVMInfrastructure = getHostedVMInfrastructure(OSType.Windows);

    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Linux),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(30L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.MacOS),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(0L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Windows),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(34L); // Linux(30) + Windows(34) = 64 < 65 (totalLimit for essentials)
    when(ciLicenseService.getLicenseSummary(eq(accountID), eq(ModuleType.CI.name()), any()))
        .thenReturn(CILicenseSummaryDTO.builder().edition(Edition.ESSENTIALS).build());

    assertThat(queueExecutionUtils.shouldQueue(accountID, hostedVMInfrastructure, true, ModuleType.CI.name()))
        .isFalse();
  }

  @Test
  @Owner(developers = TAPAN)
  @Category(UnitTests.class)
  public void testShouldRunWithEssentialsLicenseWindowsExecutionsBelowTotalLimit() {
    Infrastructure hostedVMInfrastructure = getHostedVMInfrastructure(OSType.Windows);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Linux),
             eq(List.of(Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(), Status.RUNNING.toString())), any()))
        .thenReturn(30L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.MacOS),
             eq(List.of(Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(), Status.RUNNING.toString())), any()))
        .thenReturn(0L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Windows),
             eq(List.of(Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(), Status.RUNNING.toString())), any()))
        .thenReturn(34L); // Linux(30) + Windows(34) = 64 < 65 (totalLimit for essentials)
    when(ciLicenseService.getLicenseSummary(eq(accountID), eq(ModuleType.CI.name()), any()))
        .thenReturn(CILicenseSummaryDTO.builder().edition(Edition.ESSENTIALS).build());

    assertThat(queueExecutionUtils.shouldRun(accountID, hostedVMInfrastructure, ModuleType.CI.name())).isTrue();
  }

  @Test
  @Owner(developers = TAPAN)
  @Category(UnitTests.class)
  public void testShouldNotRunWithEssentialsLicenseWindowsExecutionsAtTotalLimit() {
    Infrastructure hostedVMInfrastructure = getHostedVMInfrastructure(OSType.Windows);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Linux),
             eq(List.of(Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(), Status.RUNNING.toString())), any()))
        .thenReturn(30L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.MacOS),
             eq(List.of(Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(), Status.RUNNING.toString())), any()))
        .thenReturn(0L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Windows),
             eq(List.of(Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(), Status.RUNNING.toString())), any()))
        .thenReturn(35L); // Linux(30) + Windows(35) = 65 >= 65 (totalLimit for essentials)
    when(ciLicenseService.getLicenseSummary(eq(accountID), eq(ModuleType.CI.name()), any()))
        .thenReturn(CILicenseSummaryDTO.builder().edition(Edition.ESSENTIALS).build());

    assertThat(queueExecutionUtils.shouldRun(accountID, hostedVMInfrastructure, ModuleType.CI.name())).isFalse();
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testShouldQueueWhenGetEnableQueueIsFalse() {
    Infrastructure hostedVMInfrastructure = getHostedVMInfrastructure(OSType.Linux);
    assertThat(queueExecutionUtils.shouldQueue(accountID, hostedVMInfrastructure, false, ModuleType.CI.name()))
        .isFalse();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testComputeQueueTimeInMillis_validQueueId() {
    long now = System.currentTimeMillis() - 5000; // 5 seconds ago
    String queueId = now + "-0";

    Double queueTimeMs = queueExecutionUtils.computeQueueTimeInMillis(queueId);

    assertThat(queueTimeMs).isNotNull();
    assertThat(queueTimeMs).isGreaterThan(4000.0); // allow for slight timing differences (5000ms - buffer)
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testComputeQueueTimeInMillis_nullQueueId() {
    Double queueTimeMs = queueExecutionUtils.computeQueueTimeInMillis(null);
    assertThat(queueTimeMs).isNull();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testComputeQueueTimeInMillis_emptyQueueId() {
    Double queueTimeMs = queueExecutionUtils.computeQueueTimeInMillis("");
    assertThat(queueTimeMs).isNull();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testComputeQueueTimeInMillis_invalidFormatQueueId() {
    String queueId = "not-valid";
    Double queueTimeMs = queueExecutionUtils.computeQueueTimeInMillis(queueId);
    assertThat(queueTimeMs).isNull();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testComputeQueueTimeInMillis_nonNumericTimestamp() {
    String queueId = "abc123-def";
    Double queueTimeMs = queueExecutionUtils.computeQueueTimeInMillis(queueId);
    assertThat(queueTimeMs).isNull();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testPublishQueueCountMetrics() {
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, accountID);
    Ambiance ambiance = Ambiance.newBuilder().putAllSetupAbstractions(setupAbstractions).build();
    Infrastructure infrastructure = getHostedVMInfrastructure(OSType.Linux);

    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(
             eq(accountID), eq(OSType.Linux), eq(List.of(Status.QUEUED.toString())), any()))
        .thenReturn(5L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(
             eq(accountID), eq(OSType.MacOS), eq(List.of(Status.QUEUED.toString())), any()))
        .thenReturn(2L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(
             eq(accountID), eq(OSType.Windows), eq(List.of(Status.QUEUED.toString())), any()))
        .thenReturn(1L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Linux),
             eq(List.of(Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString())), any()))
        .thenReturn(3L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.MacOS),
             eq(List.of(Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString())), any()))
        .thenReturn(1L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Windows),
             eq(List.of(Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString())), any()))
        .thenReturn(0L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(
             eq(accountID), eq(OSType.Linux), eq(List.of(Status.QUEUED.toString(), Status.RUNNING.toString())), any()))
        .thenReturn(10L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(
             eq(accountID), eq(OSType.MacOS), eq(List.of(Status.QUEUED.toString(), Status.RUNNING.toString())), any()))
        .thenReturn(4L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Windows),
             eq(List.of(Status.QUEUED.toString(), Status.RUNNING.toString())), any()))
        .thenReturn(2L);

    queueExecutionUtils.publishQueueCountMetrics(ambiance, infrastructure);

    verify(executionMetricsService, times(6))
        .recordQueuedExecutionCount(eq(accountID), anyString(), anyString(), anyString(), any(Double.class));
    verify(executionMetricsService, times(3))
        .recordActiveExecutionCount(eq(accountID), anyString(), anyString(), anyString(), any(Double.class));
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testPublishQueueTimeMetrics() {
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, accountID);
    Ambiance ambiance =
        Ambiance.newBuilder().putAllSetupAbstractions(setupAbstractions).setStageExecutionId("stage123").build();
    Infrastructure infrastructure = getHostedVMInfrastructure(OSType.Linux);

    long now = System.currentTimeMillis() - 10000; // 10 seconds ago
    String queueId = now + "-0";

    queueExecutionUtils.publishQueueTimeMetrics(ambiance, infrastructure, queueId);

    verify(executionMetricsService, times(1))
        .recordQueuedExecutionTime(
            eq(accountID), eq("stage123"), anyString(), anyString(), anyString(), any(Double.class));
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testPublishQueueTimeMetrics_nullQueueId() {
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, accountID);
    Ambiance ambiance =
        Ambiance.newBuilder().putAllSetupAbstractions(setupAbstractions).setStageExecutionId("stage123").build();
    Infrastructure infrastructure = getHostedVMInfrastructure(OSType.Linux);

    queueExecutionUtils.publishQueueTimeMetrics(ambiance, infrastructure, null);

    verify(executionMetricsService, times(0))
        .recordQueuedExecutionTime(anyString(), anyString(), anyString(), anyString(), anyString(), any(Double.class));
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testPublishGlobalQueueTimeMetrics() {
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, accountID);
    Ambiance ambiance =
        Ambiance.newBuilder().putAllSetupAbstractions(setupAbstractions).setStageExecutionId("stage123").build();
    Infrastructure infrastructure = getHostedVMInfrastructure(OSType.Linux);

    long now = System.currentTimeMillis() - 15000; // 15 seconds ago
    String queueId = now + "-0";

    queueExecutionUtils.publishGlobalQueueTimeMetrics(ambiance, infrastructure, queueId);

    verify(executionMetricsService, times(1))
        .recordQueuedExecutionTime(
            eq(accountID), eq("stage123"), anyString(), anyString(), anyString(), any(Double.class));
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testPublishGlobalQueueTimeMetrics_invalidQueueId() {
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, accountID);
    Ambiance ambiance =
        Ambiance.newBuilder().putAllSetupAbstractions(setupAbstractions).setStageExecutionId("stage123").build();
    Infrastructure infrastructure = getHostedVMInfrastructure(OSType.Linux);

    queueExecutionUtils.publishGlobalQueueTimeMetrics(ambiance, infrastructure, "invalid");

    verify(executionMetricsService, times(0))
        .recordQueuedExecutionTime(anyString(), anyString(), anyString(), anyString(), anyString(), any(Double.class));
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetGlobalQueueTopic() {
    String topic = QueueExecutionUtils.getGlobalQueueTopic("ci");
    assertThat(topic).isEqualTo("global_capacity_queue_ci");

    String idpTopic = QueueExecutionUtils.getGlobalQueueTopic("idp");
    assertThat(idpTopic).isEqualTo("global_capacity_queue_idp");
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetGlobalQueueSubTopic_freeLicense() {
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, accountID);
    Ambiance ambiance = Ambiance.newBuilder()
                            .putAllSetupAbstractions(setupAbstractions)
                            .addLevels(Level.newBuilder()
                                           .setStepType(StepType.newBuilder()
                                                            .setType("IntegrationStageStepPMS")
                                                            .setStepCategory(StepCategory.STAGE)
                                                            .build())
                                           .build())
                            .build();

    Infrastructure infrastructure = getHostedVMInfrastructure(OSType.Linux);
    IntegrationStageStepParametersPMS integrationStageConfig =
        IntegrationStageStepParametersPMS.builder().infrastructure(infrastructure).build();
    StageElementParameters stepParameters = StageElementParameters.builder().specConfig(integrationStageConfig).build();

    when(ciLicenseService.getLicenseSummary(any(), any(), any()))
        .thenReturn(CILicenseSummaryDTO.builder().edition(Edition.FREE).build());
    when(ciLicenseUsageUtils.getResourceClass(eq(accountID), any(HostedVmInfraYaml.class), eq(true)))
        .thenReturn(Optional.of(CIResourceClass.FLEX.toString()));

    String subTopic = queueExecutionUtils.getGlobalQueueSubTopic(ambiance, stepParameters);

    assertThat(subTopic).isEqualTo("Linux-Amd64-free-flex");
    verify(ciLicenseUsageUtils, times(1)).getResourceClass(eq(accountID), any(HostedVmInfraYaml.class), eq(true));
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetGlobalQueueSubTopic_paidLicense() {
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, accountID);
    Ambiance ambiance = Ambiance.newBuilder()
                            .putAllSetupAbstractions(setupAbstractions)
                            .addLevels(Level.newBuilder()
                                           .setStepType(StepType.newBuilder()
                                                            .setType("IntegrationStageStepPMS")
                                                            .setStepCategory(StepCategory.STAGE)
                                                            .build())
                                           .build())
                            .build();

    Infrastructure infrastructure = getHostedVMInfrastructure(OSType.MacOS);
    IntegrationStageStepParametersPMS integrationStageConfig =
        IntegrationStageStepParametersPMS.builder().infrastructure(infrastructure).build();
    StageElementParameters stepParameters = StageElementParameters.builder().specConfig(integrationStageConfig).build();

    when(ciLicenseService.getLicenseSummary(any(), any(), any()))
        .thenReturn(CILicenseSummaryDTO.builder().edition(Edition.ENTERPRISE).build());
    when(ciLicenseUsageUtils.getResourceClass(eq(accountID), any(HostedVmInfraYaml.class), eq(false)))
        .thenReturn(Optional.of(CIResourceClass.LARGE.toString()));

    String subTopic = queueExecutionUtils.getGlobalQueueSubTopic(ambiance, stepParameters);

    assertThat(subTopic).isEqualTo("MacOS-Amd64-paid-large");
    verify(ciLicenseUsageUtils, times(1)).getResourceClass(eq(accountID), any(HostedVmInfraYaml.class), eq(false));
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetGlobalQueueSubTopic_defaultResourceClass() {
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, accountID);
    Ambiance ambiance = Ambiance.newBuilder()
                            .putAllSetupAbstractions(setupAbstractions)
                            .addLevels(Level.newBuilder()
                                           .setStepType(StepType.newBuilder()
                                                            .setType("IntegrationStageStepPMS")
                                                            .setStepCategory(StepCategory.STAGE)
                                                            .build())
                                           .build())
                            .build();

    Infrastructure infrastructure = getHostedVMInfrastructure(OSType.Windows);
    IntegrationStageStepParametersPMS integrationStageConfig =
        IntegrationStageStepParametersPMS.builder().infrastructure(infrastructure).build();
    StageElementParameters stepParameters = StageElementParameters.builder().specConfig(integrationStageConfig).build();

    when(ciLicenseService.getLicenseSummary(any(), any(), any()))
        .thenReturn(CILicenseSummaryDTO.builder().edition(Edition.FREE).build());
    when(ciLicenseUsageUtils.getResourceClass(eq(accountID), any(HostedVmInfraYaml.class), eq(true)))
        .thenReturn(Optional.empty());

    String subTopic = queueExecutionUtils.getGlobalQueueSubTopic(ambiance, stepParameters);

    assertThat(subTopic).isEqualTo("Windows-Amd64-free-flex");
    verify(ciLicenseUsageUtils, times(1)).getResourceClass(eq(accountID), any(HostedVmInfraYaml.class), eq(true));
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetGlobalQueueSubTopic_windowsPaid() {
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, accountID);
    Ambiance ambiance = Ambiance.newBuilder()
                            .putAllSetupAbstractions(setupAbstractions)
                            .addLevels(Level.newBuilder()
                                           .setStepType(StepType.newBuilder()
                                                            .setType("IntegrationStageStepPMS")
                                                            .setStepCategory(StepCategory.STAGE)
                                                            .build())
                                           .build())
                            .build();

    Infrastructure infrastructure = getHostedVMInfrastructure(OSType.Windows);
    IntegrationStageStepParametersPMS integrationStageConfig =
        IntegrationStageStepParametersPMS.builder().infrastructure(infrastructure).build();
    StageElementParameters stepParameters = StageElementParameters.builder().specConfig(integrationStageConfig).build();

    when(ciLicenseService.getLicenseSummary(any(), any(), any()))
        .thenReturn(CILicenseSummaryDTO.builder().edition(Edition.ENTERPRISE).build());
    when(ciLicenseUsageUtils.getResourceClass(eq(accountID), any(HostedVmInfraYaml.class), eq(false)))
        .thenReturn(Optional.of(CIResourceClass.XLARGE.toString()));

    String subTopic = queueExecutionUtils.getGlobalQueueSubTopic(ambiance, stepParameters);

    assertThat(subTopic).isEqualTo("Windows-Amd64-paid-xlarge");
    verify(ciLicenseUsageUtils, times(1)).getResourceClass(eq(accountID), any(HostedVmInfraYaml.class), eq(false));
  }

  private Infrastructure getHostedVMInfrastructure(OSType osType) {
    return HostedVmInfraYaml.builder()
        .spec(HostedVmInfraYaml.HostedVmInfraSpec.builder()
                  .platform(ParameterField.createValueField(
                      Platform.builder().os(ParameterField.createValueField(osType)).build()))
                  .build())
        .build();
  }

  // ==================== Tests for isGlobalQueueEnabled ====================

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testIsGlobalQueueEnabled_whenAllConditionsMet_thenReturnTrue() {
    // Given - HOSTED_VM infrastructure, feature flag enabled, global queue config enabled
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, accountID);
    Ambiance ambiance = Ambiance.newBuilder().putAllSetupAbstractions(setupAbstractions).build();

    Infrastructure infrastructure = getHostedVMInfrastructure(OSType.Linux);

    when(featureFlagService.isEnabled(FeatureName.CI_GLOBAL_QUEUEING_ENABLED, accountID)).thenReturn(true);
    when(ciExecutionServiceConfig.getGlobalQueueingConfig()).thenReturn(globalQueueingConfig);
    when(globalQueueingConfig.getEnableGlobalQueue()).thenReturn(true);

    // When
    boolean result = queueExecutionUtils.isGlobalQueueEnabled(ambiance, infrastructure);

    // Then
    assertThat(result).isTrue();
    verify(featureFlagService).isEnabled(FeatureName.CI_GLOBAL_QUEUEING_ENABLED, accountID);
    verify(ciExecutionServiceConfig).getGlobalQueueingConfig();
    verify(globalQueueingConfig).getEnableGlobalQueue();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testIsGlobalQueueEnabled_whenFeatureFlagDisabled_thenReturnFalse() {
    // Given - HOSTED_VM infrastructure but feature flag disabled
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, accountID);
    Ambiance ambiance = Ambiance.newBuilder().putAllSetupAbstractions(setupAbstractions).build();

    Infrastructure infrastructure = getHostedVMInfrastructure(OSType.Linux);

    when(featureFlagService.isEnabled(FeatureName.CI_GLOBAL_QUEUEING_ENABLED, accountID)).thenReturn(false);

    // When
    boolean result = queueExecutionUtils.isGlobalQueueEnabled(ambiance, infrastructure);

    // Then
    assertThat(result).isFalse();
    verify(featureFlagService).isEnabled(FeatureName.CI_GLOBAL_QUEUEING_ENABLED, accountID);
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testIsGlobalQueueEnabled_whenGlobalQueueConfigDisabled_thenReturnFalse() {
    // Given - HOSTED_VM infrastructure, feature flag enabled, but global queue config disabled
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, accountID);
    Ambiance ambiance = Ambiance.newBuilder().putAllSetupAbstractions(setupAbstractions).build();

    Infrastructure infrastructure = getHostedVMInfrastructure(OSType.Linux);

    when(featureFlagService.isEnabled(FeatureName.CI_GLOBAL_QUEUEING_ENABLED, accountID)).thenReturn(true);
    when(ciExecutionServiceConfig.getGlobalQueueingConfig()).thenReturn(globalQueueingConfig);
    when(globalQueueingConfig.getEnableGlobalQueue()).thenReturn(false);

    // When
    boolean result = queueExecutionUtils.isGlobalQueueEnabled(ambiance, infrastructure);

    // Then
    assertThat(result).isFalse();
    verify(featureFlagService).isEnabled(FeatureName.CI_GLOBAL_QUEUEING_ENABLED, accountID);
    verify(ciExecutionServiceConfig).getGlobalQueueingConfig();
    verify(globalQueueingConfig).getEnableGlobalQueue();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testIsGlobalQueueEnabled_whenNotHostedVmInfrastructure_thenReturnFalse() {
    // Given - K8s Direct infrastructure (not HOSTED_VM)
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, accountID);
    Ambiance ambiance = Ambiance.newBuilder().putAllSetupAbstractions(setupAbstractions).build();

    Infrastructure infrastructure = K8sDirectInfraYaml.builder().spec(K8sDirectInfraYamlSpec.builder().build()).build();

    when(featureFlagService.isEnabled(FeatureName.CI_GLOBAL_QUEUEING_ENABLED, accountID)).thenReturn(true);
    when(ciExecutionServiceConfig.getGlobalQueueingConfig()).thenReturn(globalQueueingConfig);
    when(globalQueueingConfig.getEnableGlobalQueue()).thenReturn(true);

    // When
    boolean result = queueExecutionUtils.isGlobalQueueEnabled(ambiance, infrastructure);

    // Then
    assertThat(result).isFalse();
    // Feature flag and config should not be checked if infrastructure type check fails
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testIsGlobalQueueEnabled_whenHostedVmWindows_thenReturnTrue() {
    // Given - HOSTED_VM Windows infrastructure with all conditions met
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, accountID);
    Ambiance ambiance = Ambiance.newBuilder().putAllSetupAbstractions(setupAbstractions).build();

    Infrastructure infrastructure = getHostedVMInfrastructure(OSType.Windows);

    when(featureFlagService.isEnabled(FeatureName.CI_GLOBAL_QUEUEING_ENABLED, accountID)).thenReturn(true);
    when(ciExecutionServiceConfig.getGlobalQueueingConfig()).thenReturn(globalQueueingConfig);
    when(globalQueueingConfig.getEnableGlobalQueue()).thenReturn(true);

    // When
    boolean result = queueExecutionUtils.isGlobalQueueEnabled(ambiance, infrastructure);

    // Then
    assertThat(result).isTrue();
    verify(featureFlagService).isEnabled(FeatureName.CI_GLOBAL_QUEUEING_ENABLED, accountID);
    verify(ciExecutionServiceConfig).getGlobalQueueingConfig();
    verify(globalQueueingConfig).getEnableGlobalQueue();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testIsGlobalQueueEnabled_whenHostedVmMacOS_thenReturnTrue() {
    // Given - HOSTED_VM MacOS infrastructure with all conditions met
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, accountID);
    Ambiance ambiance = Ambiance.newBuilder().putAllSetupAbstractions(setupAbstractions).build();

    Infrastructure infrastructure = getHostedVMInfrastructure(OSType.MacOS);

    when(featureFlagService.isEnabled(FeatureName.CI_MAC_GLOBAL_QUEUEING_ENABLED, accountID)).thenReturn(true);
    when(ciExecutionServiceConfig.getGlobalQueueingConfig()).thenReturn(globalQueueingConfig);
    when(globalQueueingConfig.getEnableGlobalQueue()).thenReturn(true);

    // When
    boolean result = queueExecutionUtils.isGlobalQueueEnabled(ambiance, infrastructure);

    // Then
    assertThat(result).isTrue();
    verify(featureFlagService).isEnabled(FeatureName.CI_MAC_GLOBAL_QUEUEING_ENABLED, accountID);
    verify(featureFlagService, never()).isEnabled(FeatureName.CI_GLOBAL_QUEUEING_ENABLED, accountID);
    verify(ciExecutionServiceConfig).getGlobalQueueingConfig();
    verify(globalQueueingConfig).getEnableGlobalQueue();
  }

  @Test
  @Owner(developers = SAURABH)
  @Category(UnitTests.class)
  public void testIsGlobalQueueEnabled_whenHostedVmMacOSAndMacFlagDisabled_thenReturnFalse() {
    // Given - HOSTED_VM MacOS infrastructure but the Mac global queueing feature flag is disabled
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, accountID);
    Ambiance ambiance = Ambiance.newBuilder().putAllSetupAbstractions(setupAbstractions).build();

    Infrastructure infrastructure = getHostedVMInfrastructure(OSType.MacOS);

    when(featureFlagService.isEnabled(FeatureName.CI_MAC_GLOBAL_QUEUEING_ENABLED, accountID)).thenReturn(false);
    when(ciExecutionServiceConfig.getGlobalQueueingConfig()).thenReturn(globalQueueingConfig);
    when(globalQueueingConfig.getEnableGlobalQueue()).thenReturn(true);

    // When
    boolean result = queueExecutionUtils.isGlobalQueueEnabled(ambiance, infrastructure);

    // Then
    assertThat(result).isFalse();
    verify(featureFlagService).isEnabled(FeatureName.CI_MAC_GLOBAL_QUEUEING_ENABLED, accountID);
  }

  @Test
  @Owner(developers = SAURABH)
  @Category(UnitTests.class)
  public void testIsGlobalQueueEnabled_whenHostedVmMacOSAndOnlyLinuxFlagEnabled_thenReturnFalse() {
    // Given - HOSTED_VM MacOS infrastructure, only the Linux/default flag (CI_GLOBAL_QUEUEING_ENABLED) is enabled.
    // MacOS should gate on CI_MAC_GLOBAL_QUEUEING_ENABLED which is disabled, so the result must be false.
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, accountID);
    Ambiance ambiance = Ambiance.newBuilder().putAllSetupAbstractions(setupAbstractions).build();

    Infrastructure infrastructure = getHostedVMInfrastructure(OSType.MacOS);

    when(featureFlagService.isEnabled(FeatureName.CI_GLOBAL_QUEUEING_ENABLED, accountID)).thenReturn(true);
    when(featureFlagService.isEnabled(FeatureName.CI_MAC_GLOBAL_QUEUEING_ENABLED, accountID)).thenReturn(false);
    when(ciExecutionServiceConfig.getGlobalQueueingConfig()).thenReturn(globalQueueingConfig);
    when(globalQueueingConfig.getEnableGlobalQueue()).thenReturn(true);

    // When
    boolean result = queueExecutionUtils.isGlobalQueueEnabled(ambiance, infrastructure);

    // Then
    assertThat(result).isFalse();
    verify(featureFlagService).isEnabled(FeatureName.CI_MAC_GLOBAL_QUEUEING_ENABLED, accountID);
    verify(featureFlagService, never()).isEnabled(FeatureName.CI_GLOBAL_QUEUEING_ENABLED, accountID);
  }

  @Test
  @Owner(developers = SAURABH)
  @Category(UnitTests.class)
  public void testIsGlobalQueueEnabled_whenHostedVmLinuxAndLinuxFlagEnabled_thenReturnTrue() {
    // Given - HOSTED_VM Linux infrastructure, CI_GLOBAL_QUEUEING_ENABLED enabled and global queue config enabled.
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, accountID);
    Ambiance ambiance = Ambiance.newBuilder().putAllSetupAbstractions(setupAbstractions).build();

    Infrastructure infrastructure = getHostedVMInfrastructure(OSType.Linux);

    when(featureFlagService.isEnabled(FeatureName.CI_GLOBAL_QUEUEING_ENABLED, accountID)).thenReturn(true);
    when(ciExecutionServiceConfig.getGlobalQueueingConfig()).thenReturn(globalQueueingConfig);
    when(globalQueueingConfig.getEnableGlobalQueue()).thenReturn(true);

    // When
    boolean result = queueExecutionUtils.isGlobalQueueEnabled(ambiance, infrastructure);

    // Then
    assertThat(result).isTrue();
    verify(featureFlagService).isEnabled(FeatureName.CI_GLOBAL_QUEUEING_ENABLED, accountID);
    verify(featureFlagService, never()).isEnabled(FeatureName.CI_MAC_GLOBAL_QUEUEING_ENABLED, accountID);
  }

  @Test
  @Owner(developers = SAURABH)
  @Category(UnitTests.class)
  public void testIsGlobalQueueEnabled_whenHostedVmLinuxAndLinuxFlagDisabled_thenReturnFalse() {
    // Given - HOSTED_VM Linux infrastructure but CI_GLOBAL_QUEUEING_ENABLED disabled.
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, accountID);
    Ambiance ambiance = Ambiance.newBuilder().putAllSetupAbstractions(setupAbstractions).build();

    Infrastructure infrastructure = getHostedVMInfrastructure(OSType.Linux);

    when(featureFlagService.isEnabled(FeatureName.CI_GLOBAL_QUEUEING_ENABLED, accountID)).thenReturn(false);
    when(ciExecutionServiceConfig.getGlobalQueueingConfig()).thenReturn(globalQueueingConfig);
    when(globalQueueingConfig.getEnableGlobalQueue()).thenReturn(true);

    // When
    boolean result = queueExecutionUtils.isGlobalQueueEnabled(ambiance, infrastructure);

    // Then
    assertThat(result).isFalse();
    verify(featureFlagService).isEnabled(FeatureName.CI_GLOBAL_QUEUEING_ENABLED, accountID);
    verify(featureFlagService, never()).isEnabled(FeatureName.CI_MAC_GLOBAL_QUEUEING_ENABLED, accountID);
  }

  @Test
  @Owner(developers = SAURABH)
  @Category(UnitTests.class)
  public void testIsGlobalQueueEnabled_whenHostedVmLinuxAndOnlyMacFlagEnabled_thenReturnFalse() {
    // Given - HOSTED_VM Linux infrastructure, only the Mac flag (CI_MAC_GLOBAL_QUEUEING_ENABLED) is enabled.
    // Linux should gate on CI_GLOBAL_QUEUEING_ENABLED which is disabled, so the result must be false.
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, accountID);
    Ambiance ambiance = Ambiance.newBuilder().putAllSetupAbstractions(setupAbstractions).build();

    Infrastructure infrastructure = getHostedVMInfrastructure(OSType.Linux);

    when(featureFlagService.isEnabled(FeatureName.CI_MAC_GLOBAL_QUEUEING_ENABLED, accountID)).thenReturn(true);
    when(featureFlagService.isEnabled(FeatureName.CI_GLOBAL_QUEUEING_ENABLED, accountID)).thenReturn(false);
    when(ciExecutionServiceConfig.getGlobalQueueingConfig()).thenReturn(globalQueueingConfig);
    when(globalQueueingConfig.getEnableGlobalQueue()).thenReturn(true);

    // When
    boolean result = queueExecutionUtils.isGlobalQueueEnabled(ambiance, infrastructure);

    // Then
    assertThat(result).isFalse();
    verify(featureFlagService).isEnabled(FeatureName.CI_GLOBAL_QUEUEING_ENABLED, accountID);
    verify(featureFlagService, never()).isEnabled(FeatureName.CI_MAC_GLOBAL_QUEUEING_ENABLED, accountID);
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetStageVariables_withValidVariables() {
    // Given
    Map<String, Object> expectedVariables = new HashMap<>();
    expectedVariables.put("var1", "value1");
    expectedVariables.put("var2", "value2");

    IntegrationStageStepParametersPMS integrationStageConfig =
        IntegrationStageStepParametersPMS.builder().infrastructure(getHostedVMInfrastructure(OSType.Linux)).build();
    StageElementParameters stageElementParameters = StageElementParameters.builder()
                                                        .specConfig(integrationStageConfig)
                                                        .variables(ParameterField.createValueField(expectedVariables))
                                                        .build();

    // When
    Map<String, Object> result = QueueExecutionUtils.getStageVariables(stageElementParameters);

    // Then
    assertThat(result).isNotNull();
    assertThat(result).isEqualTo(expectedVariables);
    assertThat(result.get("var1")).isEqualTo("value1");
    assertThat(result.get("var2")).isEqualTo("value2");
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetStageVariables_withNullVariables() {
    // Given
    IntegrationStageStepParametersPMS integrationStageConfig =
        IntegrationStageStepParametersPMS.builder().infrastructure(getHostedVMInfrastructure(OSType.Linux)).build();
    StageElementParameters stageElementParameters =
        StageElementParameters.builder().specConfig(integrationStageConfig).variables(null).build();

    // When
    Map<String, Object> result = QueueExecutionUtils.getStageVariables(stageElementParameters);

    // Then
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetStageVariables_withEmptyVariables() {
    // Given
    Map<String, Object> emptyVariables = new HashMap<>();
    IntegrationStageStepParametersPMS integrationStageConfig =
        IntegrationStageStepParametersPMS.builder().infrastructure(getHostedVMInfrastructure(OSType.Linux)).build();
    StageElementParameters stageElementParameters = StageElementParameters.builder()
                                                        .specConfig(integrationStageConfig)
                                                        .variables(ParameterField.createValueField(emptyVariables))
                                                        .build();

    // When
    Map<String, Object> result = QueueExecutionUtils.getStageVariables(stageElementParameters);

    // Then
    assertThat(result).isNotNull();
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testIsStaleQueueMessage_withNullItemId() {
    // Given
    String itemId = null;

    // When
    boolean result = queueExecutionUtils.isStaleQueueMessage(itemId);

    // Then
    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testIsStaleQueueMessage_withInvalidItemId() {
    // Given - invalid format, not parseable
    String itemId = "invalid-id";

    // When
    boolean result = queueExecutionUtils.isStaleQueueMessage(itemId);

    // Then
    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testIsStaleQueueMessage_withRecentMessage() {
    // Given - message from 1 hour ago
    long oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000);
    String itemId = oneHourAgo + "-0";

    // When
    boolean result = queueExecutionUtils.isStaleQueueMessage(itemId);

    // Then
    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testIsStaleQueueMessage_withStaleMessage() {
    // Given - message from 36 days ago (older than 35 days)
    long thirtySixDaysAgo = System.currentTimeMillis() - (36L * 24 * 60 * 60 * 1000);
    String itemId = thirtySixDaysAgo + "-0";

    // When
    boolean result = queueExecutionUtils.isStaleQueueMessage(itemId);

    // Then
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testIsStaleQueueMessage_withVeryOldMessage() {
    // Given - message from 100 days ago
    long oneHundredDaysAgo = System.currentTimeMillis() - (100L * 24 * 60 * 60 * 1000);
    String itemId = oneHundredDaysAgo + "-0";

    // When
    boolean result = queueExecutionUtils.isStaleQueueMessage(itemId);

    // Then
    assertThat(result).isTrue();
  }

  // ==================== Tests for deleteActiveExecutionRecord ====================

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testDeleteActiveExecutionRecord_withExistingRecord() {
    // Given
    String stageExecutionId = "test-stage-exec-id";
    CIExecutionMetadata existingMetadata = CIExecutionMetadata.builder()
                                               .accountId(accountID)
                                               .stageExecutionId(stageExecutionId)
                                               .buildType(OSType.Linux)
                                               .status(Status.RUNNING.toString())
                                               .build();

    when(ciExecutionRepository.findByStageExecutionId(stageExecutionId)).thenReturn(existingMetadata);

    // When
    CIExecutionMetadata result = queueExecutionUtils.deleteActiveExecutionRecord(stageExecutionId);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getStageExecutionId()).isEqualTo(stageExecutionId);
    assertThat(result.getAccountId()).isEqualTo(accountID);
    verify(ciExecutionRepository).findByStageExecutionId(stageExecutionId);
    verify(ciExecutionRepository).deleteByStageExecutionId(stageExecutionId);
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testDeleteActiveExecutionRecord_withNonExistingRecord() {
    // Given
    String stageExecutionId = "non-existing-stage-exec-id";

    when(ciExecutionRepository.findByStageExecutionId(stageExecutionId)).thenReturn(null);

    // When
    CIExecutionMetadata result = queueExecutionUtils.deleteActiveExecutionRecord(stageExecutionId);

    // Then
    assertThat(result).isNull();
    verify(ciExecutionRepository).findByStageExecutionId(stageExecutionId);
    verify(ciExecutionRepository).deleteByStageExecutionId(stageExecutionId);
  }

  // ==================== Tests for getInfrastructure ====================

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetInfrastructure_withHostedVmInfra() {
    // Given
    Infrastructure expectedInfra = getHostedVMInfrastructure(OSType.Linux);
    IntegrationStageStepParametersPMS integrationStageConfig =
        IntegrationStageStepParametersPMS.builder().infrastructure(expectedInfra).build();
    StageElementParameters stepParameters = StageElementParameters.builder().specConfig(integrationStageConfig).build();

    // When
    Infrastructure result = QueueExecutionUtils.getInfrastructure(stepParameters);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getType()).isEqualTo(Infrastructure.Type.HOSTED_VM);
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetInfrastructure_withK8sInfra() {
    // Given
    K8sDirectInfraYamlSpec k8sSpec =
        K8sDirectInfraYamlSpec.builder().os(ParameterField.createValueField(OSType.Linux)).build();
    Infrastructure expectedInfra =
        K8sDirectInfraYaml.builder().type(Infrastructure.Type.KUBERNETES_DIRECT).spec(k8sSpec).build();
    IntegrationStageStepParametersPMS integrationStageConfig =
        IntegrationStageStepParametersPMS.builder().infrastructure(expectedInfra).build();
    StageElementParameters stepParameters = StageElementParameters.builder().specConfig(integrationStageConfig).build();

    // When
    Infrastructure result = QueueExecutionUtils.getInfrastructure(stepParameters);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getType()).isEqualTo(Infrastructure.Type.KUBERNETES_DIRECT);
  }

  // ==================== Tests for getStageTimeout ====================

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetStageTimeout_withValidTimeout() {
    // Given
    IntegrationStageStepParametersPMS integrationStageConfig =
        IntegrationStageStepParametersPMS.builder().infrastructure(getHostedVMInfrastructure(OSType.Linux)).build();
    StageElementParameters stepParameters = StageElementParameters.builder()
                                                .specConfig(integrationStageConfig)
                                                .stageTimeout(ParameterField.createValueField("2h"))
                                                .build();

    // When
    ParameterField<io.harness.yaml.core.timeout.Timeout> result = QueueExecutionUtils.getStageTimeout(stepParameters);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getValue()).isNotNull();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetStageTimeout_withNullStageTimeout() {
    // Given
    IntegrationStageStepParametersPMS integrationStageConfig =
        IntegrationStageStepParametersPMS.builder().infrastructure(getHostedVMInfrastructure(OSType.Linux)).build();
    StageElementParameters stepParameters =
        StageElementParameters.builder().specConfig(integrationStageConfig).stageTimeout(null).build();

    // When
    ParameterField<io.harness.yaml.core.timeout.Timeout> result = QueueExecutionUtils.getStageTimeout(stepParameters);

    // Then
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetStageTimeout_withNullTimeoutValue() {
    // Given
    IntegrationStageStepParametersPMS integrationStageConfig =
        IntegrationStageStepParametersPMS.builder().infrastructure(getHostedVMInfrastructure(OSType.Linux)).build();
    StageElementParameters stepParameters = StageElementParameters.builder()
                                                .specConfig(integrationStageConfig)
                                                .stageTimeout(ParameterField.createValueField(null))
                                                .build();

    // When
    ParameterField<io.harness.yaml.core.timeout.Timeout> result = QueueExecutionUtils.getStageTimeout(stepParameters);

    // Then
    assertThat(result).isNull();
  }

  // ==================== Tests for addExecutionRecord edge cases ====================

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testAddExecutionRecord_whenRecordAlreadyExists() {
    // Given
    Infrastructure infrastructure = getHostedVMInfrastructure(OSType.Linux);
    String accountId = "test-account";
    String stageExecutionId = "existing-stage-exec-id";
    CIExecutionMetadata existingMetadata =
        CIExecutionMetadata.builder().accountId(accountId).stageExecutionId(stageExecutionId).build();

    when(ciExecutionRepository.findByStageExecutionId(stageExecutionId)).thenReturn(existingMetadata);

    // When
    queueExecutionUtils.addExecutionRecord(infrastructure, accountId, stageExecutionId, null);

    // Then - save should NOT be called since record already exists
    verify(ciExecutionRepository).findByStageExecutionId(stageExecutionId);
    verify(ciExecutionRepository, times(0)).save(any(CIExecutionMetadata.class));
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testAddExecutionRecord_whenExceptionOccurs() {
    // Given
    Infrastructure infrastructure = getHostedVMInfrastructure(OSType.Linux);
    String accountId = "test-account";
    String stageExecutionId = "test-stage-exec-id";

    when(ciExecutionRepository.findByStageExecutionId(stageExecutionId))
        .thenThrow(new RuntimeException("Database error"));

    // When - should not throw exception, just log it
    queueExecutionUtils.addExecutionRecord(infrastructure, accountId, stageExecutionId, null);

    // Then - verify method completes without throwing
    verify(ciExecutionRepository).findByStageExecutionId(stageExecutionId);
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testAddExecutionRecord_withMacOSInfrastructure() {
    // Given
    Infrastructure infrastructure = getHostedVMInfrastructure(OSType.MacOS);
    String accountId = "test-account";
    String stageExecutionId = "test-stage-exec-id";

    when(ciExecutionRepository.findByStageExecutionId(stageExecutionId)).thenReturn(null);

    // When
    queueExecutionUtils.addExecutionRecord(infrastructure, accountId, stageExecutionId, null);

    // Then
    verify(ciExecutionRepository).save(executionMetadataArgumentCaptor.capture());
    CIExecutionMetadata savedMetadata = executionMetadataArgumentCaptor.getValue();

    assertThat(savedMetadata.getBuildType()).isEqualTo(OSType.MacOS);
    assertThat(savedMetadata.getInfraType()).isEqualTo(Infrastructure.Type.HOSTED_VM);
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testAddExecutionRecord_withWindowsInfrastructure() {
    // Given
    Infrastructure infrastructure = getHostedVMInfrastructure(OSType.Windows);
    String accountId = "test-account";
    String stageExecutionId = "test-stage-exec-id";

    when(ciExecutionRepository.findByStageExecutionId(stageExecutionId)).thenReturn(null);

    // When
    queueExecutionUtils.addExecutionRecord(infrastructure, accountId, stageExecutionId, null);

    // Then
    verify(ciExecutionRepository).save(executionMetadataArgumentCaptor.capture());
    CIExecutionMetadata savedMetadata = executionMetadataArgumentCaptor.getValue();

    assertThat(savedMetadata.getBuildType()).isEqualTo(OSType.Windows);
    assertThat(savedMetadata.getInfraType()).isEqualTo(Infrastructure.Type.HOSTED_VM);
  }

  // ==================== Tests for getExecutionLimit with partial overrides ====================

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testShouldQueue_withOnlyMacOverride() {
    // Given - override has only mac limit, should use default for total
    Infrastructure hostedVMInfrastructure = getHostedVMInfrastructure(OSType.MacOS);

    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Linux),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(0L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.MacOS),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(6L); // 6 > 5 (overridden mac limit)
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Windows),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(0L);
    when(ciLicenseService.getLicenseSummary(eq(accountID), eq(ModuleType.CI.name()), any()))
        .thenReturn(CILicenseSummaryDTO.builder().edition(Edition.ENTERPRISE).build());

    // Override with only mac limit (totalExecLimit is empty)
    doReturn(Optional.of(ExecutionQueueLimit.builder().accountIdentifier(accountID).macExecLimit("5").build()))
        .when(executionQueueLimitRepository)
        .findFirstByAccountIdentifier(accountID);

    // When & Then
    assertThat(queueExecutionUtils.shouldQueue(accountID, hostedVMInfrastructure, true, ModuleType.CI.name())).isTrue();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testShouldQueue_withOnlyTotalOverride() {
    // Given - override has only total limit, should use default for mac
    Infrastructure hostedVMInfrastructure = getHostedVMInfrastructure(OSType.Linux);

    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Linux),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(51L); // 51 > 50 (overridden total limit)
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.MacOS),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(0L);
    when(ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(eq(accountID), eq(OSType.Windows),
             eq(List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
                 Status.RUNNING.toString())),
             any()))
        .thenReturn(0L);
    when(ciLicenseService.getLicenseSummary(eq(accountID), eq(ModuleType.CI.name()), any()))
        .thenReturn(CILicenseSummaryDTO.builder().edition(Edition.ENTERPRISE).build());

    // Override with only total limit (macExecLimit is empty)
    doReturn(Optional.of(ExecutionQueueLimit.builder().accountIdentifier(accountID).totalExecLimit("50").build()))
        .when(executionQueueLimitRepository)
        .findFirstByAccountIdentifier(accountID);

    // When & Then
    assertThat(queueExecutionUtils.shouldQueue(accountID, hostedVMInfrastructure, true, ModuleType.CI.name())).isTrue();
  }
}
