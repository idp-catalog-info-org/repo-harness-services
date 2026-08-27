/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.utils;

import static io.harness.rule.OwnerRule.CHIRAG_S;
import static io.harness.rule.OwnerRule.DEV_MITTAL;
import static io.harness.rule.OwnerRule.SHUBHAM_AGARWAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.ModuleType;
import io.harness.beans.execution.license.CILicenseService;
import io.harness.beans.stages.IntegrationStageNode;
import io.harness.beans.yaml.extended.infrastrucutre.HostedVmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.category.element.UnitTests;
import io.harness.ci.execution.execution.metadata.CIAccountExecutionMetadata;
import io.harness.ci.execution.validation.CIAccountValidationService;
import io.harness.ci.executionplan.base.CIExecutionTestBase;
import io.harness.core.ci.dashboard.CIOverviewDashboardService;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.licensing.Edition;
import io.harness.licensing.beans.summary.dto.CILicenseSummaryDTO;
import io.harness.licensing.beans.summary.dto.LicensesWithSummaryDTO;
import io.harness.plancreator.steps.common.StageElementParameters;
import io.harness.plancreator.steps.common.StageElementParameters.StageElementParametersBuilder;
import io.harness.pms.plan.execution.AccountExecutionInfo;
import io.harness.pms.yaml.ParameterField;
import io.harness.repositories.CIAccountExecutionMetadataRepository;
import io.harness.rule.Owner;
import io.harness.yaml.core.failurestrategy.FailureStrategyConfig;

import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.powermock.reflect.Whitebox;

public class CIStagePlanCreationUtilsTest extends CIExecutionTestBase {
  @Mock private CIOverviewDashboardService ciOverviewDashboardService;
  @Mock private CIAccountValidationService ciAccountValidationService;
  @Mock private CILicenseService ciLicenseService;
  @Mock private CIAccountExecutionMetadataRepository accountExecutionMetadataRepository;
  @InjectMocks private CIStagePlanCreationUtils ciStagePlanCreationUtils;

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testEnforceCreditsCount() throws Exception {
    when(ciOverviewDashboardService.getHostedCreditUsage(any(), anyLong(), anyLong())).thenReturn(10L);
    when(ciAccountValidationService.getMaxCreditsPerMonth(any(), eq(ModuleType.CI.name()))).thenReturn(2000L);
    Whitebox.invokeMethod(ciStagePlanCreationUtils, "enforceCreditsCount", "acc", ModuleType.CI.name());
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testEnforceCreditsCount_Exception() throws Exception {
    when(ciOverviewDashboardService.getHostedCreditUsage(any(), anyLong(), anyLong())).thenThrow(new SQLException());
    when(ciAccountValidationService.getMaxCreditsPerMonth(any(), eq(ModuleType.CI.name()))).thenReturn(2000L);
    Whitebox.invokeMethod(ciStagePlanCreationUtils, "enforceCreditsCount", "acc", ModuleType.CI.name());
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testEnforceCreditsCount_NoCreditCard() throws Exception {
    when(ciOverviewDashboardService.getHostedCreditUsage(any(), anyLong(), anyLong())).thenReturn(10L);
    when(ciAccountValidationService.getMaxCreditsPerMonth(any(), eq(ModuleType.CI.name()))).thenReturn(0L);
    assertThatThrownBy(
        () -> Whitebox.invokeMethod(ciStagePlanCreationUtils, "enforceCreditsCount", "acc", ModuleType.CI.name()))
        .isExactlyInstanceOf(CIStageExecutionException.class);
    assertThatThrownBy(
        () -> Whitebox.invokeMethod(ciStagePlanCreationUtils, "enforceCreditsCount", "acc", ModuleType.CI.name()))
        .hasMessage("To use Harness Cloud, you must provide a credit card to validate your account");
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testEnforceCreditsCount_LimitCrossed() throws Exception {
    when(ciOverviewDashboardService.getHostedCreditUsage(any(), anyLong(), anyLong())).thenReturn(2005L);
    when(ciAccountValidationService.getMaxCreditsPerMonth(any(), eq(ModuleType.CI.name()))).thenReturn(2000L);
    assertThatThrownBy(
        () -> Whitebox.invokeMethod(ciStagePlanCreationUtils, "enforceCreditsCount", "acc", ModuleType.CI.name()))
        .isExactlyInstanceOf(CIStageExecutionException.class);
    assertThatThrownBy(
        () -> Whitebox.invokeMethod(ciStagePlanCreationUtils, "enforceCreditsCount", "acc", ModuleType.CI.name()))
        .hasMessage("You have reached the account build limit. Please contact support: support@harness.io");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetStageParameters_shouldPopulateAllFields() {
    IntegrationStageNode stageNode =
        IntegrationStageNode.builder().uuid("test-uuid").identifier("stage-id").name("stage-name").build();
    stageNode.setDescription(ParameterField.createValueField("stage-description"));
    stageNode.setTags(Collections.singletonMap("key", "value"));

    StageElementParametersBuilder result = ciStagePlanCreationUtils.getStageParameters(stageNode);

    assertThat(result).as("StageElementParametersBuilder should not be null").isNotNull();
    StageElementParameters built = result.build();
    assertThat(built.getName()).as("Stage name should be set").isEqualTo("stage-name");
    assertThat(built.getIdentifier()).as("Stage identifier should be set").isEqualTo("stage-id");
    assertThat(built.getUuid()).as("Stage uuid should be set").isEqualTo("test-uuid");
    assertThat(built.getTags()).as("Tags should be populated").containsEntry("key", "value");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetStageParameters_whenNullableFieldsAreNull() {
    IntegrationStageNode stageNode =
        IntegrationStageNode.builder().uuid("uuid-1").identifier("id-1").name("name-1").build();

    StageElementParametersBuilder result = ciStagePlanCreationUtils.getStageParameters(stageNode);

    assertThat(result).as("Builder should not be null even with nullable fields").isNotNull();
    assertThat(result.build().getFailureStrategies()).as("Failure strategies should be null when not set").isNull();
    assertThat(result.build().getWhen()).as("When condition should be null when not set").isNull();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetStageParameters_whenFailureStrategiesAndWhenAreSet() {
    ParameterField<List<FailureStrategyConfig>> failureStrategies =
        ParameterField.createValueField(Collections.emptyList());
    IntegrationStageNode stageNode = IntegrationStageNode.builder()
                                         .uuid("uuid-2")
                                         .identifier("id-2")
                                         .name("name-2")
                                         .failureStrategies(failureStrategies)
                                         .build();

    StageElementParametersBuilder result = ciStagePlanCreationUtils.getStageParameters(stageNode);

    assertThat(result.build().getFailureStrategies()).as("Failure strategies should be set when provided").isNotNull();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testIsHostedInfra_whenHostedVm_shouldReturnTrue() {
    Infrastructure infrastructure = buildInfrastructure(Infrastructure.Type.HOSTED_VM);

    boolean result = CIStagePlanCreationUtils.isHostedInfra(infrastructure);

    assertThat(result).as("Should return true for HOSTED_VM infrastructure").isTrue();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testIsHostedInfra_whenNotHostedVm_shouldReturnFalse() {
    Infrastructure infrastructure = buildInfrastructure(Infrastructure.Type.KUBERNETES_DIRECT);

    boolean result = CIStagePlanCreationUtils.isHostedInfra(infrastructure);

    assertThat(result).as("Should return false for non-HOSTED_VM infrastructure").isFalse();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidateFreeAccountStageExecutionLimit_whenNotHostedInfra_shouldNotThrow() {
    Infrastructure infrastructure = buildInfrastructure(Infrastructure.Type.KUBERNETES_DIRECT);

    assertThatCode(()
                       -> ciStagePlanCreationUtils.validateFreeAccountStageExecutionLimit(
                           "acc", infrastructure, ModuleType.CI.name()))
        .as("Should not throw when infrastructure is not hosted")
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidateFreeAccountStageExecutionLimit_whenLicenseIsNull_shouldThrow() {
    Infrastructure infrastructure = buildInfrastructure(Infrastructure.Type.HOSTED_VM);
    when(ciLicenseService.getLicenseSummary(eq("acc"), eq(ModuleType.CI.name()))).thenReturn(null);

    assertThatThrownBy(()
                           -> ciStagePlanCreationUtils.validateFreeAccountStageExecutionLimit(
                               "acc", infrastructure, ModuleType.CI.name()))
        .as("Should throw when license summary is null")
        .isInstanceOf(CIStageExecutionException.class)
        .hasMessage("Please enable CI free plan or reach out to support.");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidateFreeAccountStageExecutionLimit_whenFreeEditionAndMetadataPresent_shouldEnforceCredits()
      throws Exception {
    Infrastructure infrastructure = buildInfrastructure(Infrastructure.Type.HOSTED_VM);
    LicensesWithSummaryDTO licenseDto = CILicenseSummaryDTO.builder().edition(Edition.FREE).build();
    when(ciLicenseService.getLicenseSummary(eq("acc"), eq(ModuleType.CI.name()))).thenReturn(licenseDto);
    CIAccountExecutionMetadata metadata = CIAccountExecutionMetadata.builder().accountId("acc").build();
    when(accountExecutionMetadataRepository.findByAccountId("acc")).thenReturn(Optional.of(metadata));
    when(ciOverviewDashboardService.getHostedCreditUsage(eq("acc"), anyLong(), anyLong())).thenReturn(10L);
    when(ciAccountValidationService.getMaxCreditsPerMonth(eq("acc"), eq(ModuleType.CI.name()))).thenReturn(2000L);

    assertThatCode(()
                       -> ciStagePlanCreationUtils.validateFreeAccountStageExecutionLimit(
                           "acc", infrastructure, ModuleType.CI.name()))
        .as("Should not throw when credits are within limit")
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidateFreeAccountStageExecutionLimit_whenFreeEditionAndMetadataAbsent_shouldNotEnforceCredits()
      throws Exception {
    Infrastructure infrastructure = buildInfrastructure(Infrastructure.Type.HOSTED_VM);
    LicensesWithSummaryDTO licenseDto = CILicenseSummaryDTO.builder().edition(Edition.FREE).build();
    when(ciLicenseService.getLicenseSummary(eq("acc"), eq(ModuleType.CI.name()))).thenReturn(licenseDto);
    when(accountExecutionMetadataRepository.findByAccountId("acc")).thenReturn(Optional.empty());

    assertThatCode(()
                       -> ciStagePlanCreationUtils.validateFreeAccountStageExecutionLimit(
                           "acc", infrastructure, ModuleType.CI.name()))
        .as("Should not throw when metadata is absent")
        .doesNotThrowAnyException();

    verify(ciOverviewDashboardService, never()).getHostedCreditUsage(any(), anyLong(), anyLong());
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidateFreeAccountStageExecutionLimit_whenNonFreeEdition_shouldNotEnforceCredits() throws Exception {
    Infrastructure infrastructure = buildInfrastructure(Infrastructure.Type.HOSTED_VM);
    LicensesWithSummaryDTO licenseDto = CILicenseSummaryDTO.builder().edition(Edition.ENTERPRISE).build();
    when(ciLicenseService.getLicenseSummary(eq("acc"), eq(ModuleType.CI.name()))).thenReturn(licenseDto);

    assertThatCode(()
                       -> ciStagePlanCreationUtils.validateFreeAccountStageExecutionLimit(
                           "acc", infrastructure, ModuleType.CI.name()))
        .as("Should not throw for non-free edition")
        .doesNotThrowAnyException();

    verify(accountExecutionMetadataRepository, never()).findByAccountId(any());
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidateFreeAccountStageExecutionLimit_overload_whenNotFreePlan_shouldReturnEarly() {
    Infrastructure infrastructure = buildInfrastructure(Infrastructure.Type.HOSTED_VM);

    assertThatCode(()
                       -> ciStagePlanCreationUtils.validateFreeAccountStageExecutionLimit(
                           false, "acc", infrastructure, ModuleType.CI.name()))
        .as("Should return early when not free plan")
        .doesNotThrowAnyException();

    verify(accountExecutionMetadataRepository, never()).findByAccountId(any());
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidateFreeAccountStageExecutionLimit_overload_whenFreePlanAndNotHosted_shouldNotEnforce() {
    Infrastructure infrastructure = buildInfrastructure(Infrastructure.Type.KUBERNETES_DIRECT);

    assertThatCode(()
                       -> ciStagePlanCreationUtils.validateFreeAccountStageExecutionLimit(
                           true, "acc", infrastructure, ModuleType.CI.name()))
        .as("Should not enforce for non-hosted infra even when free plan")
        .doesNotThrowAnyException();

    verify(accountExecutionMetadataRepository, never()).findByAccountId(any());
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void
  testValidateFreeAccountStageExecutionLimit_overload_whenFreePlanHostedAndMetadataPresent_shouldEnforceCredits()
      throws Exception {
    Infrastructure infrastructure = buildInfrastructure(Infrastructure.Type.HOSTED_VM);
    CIAccountExecutionMetadata metadata = CIAccountExecutionMetadata.builder().accountId("acc").build();
    when(accountExecutionMetadataRepository.findByAccountId("acc")).thenReturn(Optional.of(metadata));
    when(ciOverviewDashboardService.getHostedCreditUsage(eq("acc"), anyLong(), anyLong())).thenReturn(10L);
    when(ciAccountValidationService.getMaxCreditsPerMonth(eq("acc"), eq(ModuleType.CI.name()))).thenReturn(2000L);

    assertThatCode(()
                       -> ciStagePlanCreationUtils.validateFreeAccountStageExecutionLimit(
                           true, "acc", infrastructure, ModuleType.CI.name()))
        .as("Should not throw when credits within limit")
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidateFreeAccountStageExecutionLimit_overload_whenFreePlanHostedAndMetadataAbsent_shouldNotEnforce()
      throws Exception {
    Infrastructure infrastructure = buildInfrastructure(Infrastructure.Type.HOSTED_VM);
    when(accountExecutionMetadataRepository.findByAccountId("acc")).thenReturn(Optional.empty());

    assertThatCode(()
                       -> ciStagePlanCreationUtils.validateFreeAccountStageExecutionLimit(
                           true, "acc", infrastructure, ModuleType.CI.name()))
        .as("Should not enforce when metadata absent")
        .doesNotThrowAnyException();

    verify(ciOverviewDashboardService, never()).getHostedCreditUsage(any(), anyLong(), anyLong());
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testEnforceBuildsCount_whenBelowLimit_shouldNotThrow() throws Exception {
    Map<String, Long> countPerDay = new HashMap<>();
    java.time.LocalDate today = java.time.Instant.now().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
    java.time.YearMonth yearMonth = java.time.YearMonth.of(today.getYear(), today.getMonth());
    String dayKey = yearMonth + "-" + today.getDayOfMonth();
    countPerDay.put(dayKey, 5L);
    AccountExecutionInfo executionInfo = AccountExecutionInfo.builder().countPerDay(countPerDay).build();
    CIAccountExecutionMetadata metadata =
        CIAccountExecutionMetadata.builder().accountId("acc").accountExecutionInfo(executionInfo).build();

    when(ciAccountValidationService.getMaxBuildPerDay(eq("acc"), eq(ModuleType.CI.name()))).thenReturn(100L);

    Whitebox.invokeMethod(
        ciStagePlanCreationUtils, "enforceBuildsCount", "acc", Optional.of(metadata), ModuleType.CI.name());
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testEnforceBuildsCount_whenCountPerDayIsNull_shouldNotThrow() throws Exception {
    AccountExecutionInfo executionInfo = AccountExecutionInfo.builder().build();
    executionInfo.setCountPerDay(null);
    CIAccountExecutionMetadata metadata =
        CIAccountExecutionMetadata.builder().accountId("acc").accountExecutionInfo(executionInfo).build();

    Whitebox.invokeMethod(
        ciStagePlanCreationUtils, "enforceBuildsCount", "acc", Optional.of(metadata), ModuleType.CI.name());
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testEnforceBuildsCount_whenLimitExceeded_shouldThrowAccountLimitError() throws Exception {
    Map<String, Long> countPerDay = new HashMap<>();
    java.time.LocalDate today = java.time.Instant.now().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
    java.time.YearMonth yearMonth = java.time.YearMonth.of(today.getYear(), today.getMonth());
    String dayKey = yearMonth + "-" + today.getDayOfMonth();
    countPerDay.put(dayKey, 200L);
    AccountExecutionInfo executionInfo = AccountExecutionInfo.builder().countPerDay(countPerDay).build();
    CIAccountExecutionMetadata metadata =
        CIAccountExecutionMetadata.builder().accountId("acc").accountExecutionInfo(executionInfo).build();

    when(ciAccountValidationService.getMaxBuildPerDay(eq("acc"), eq(ModuleType.CI.name()))).thenReturn(100L);

    assertThatThrownBy(()
                           -> Whitebox.invokeMethod(ciStagePlanCreationUtils, "enforceBuildsCount", "acc",
                               Optional.of(metadata), ModuleType.CI.name()))
        .as("Should throw when build limit exceeded")
        .isInstanceOf(CIStageExecutionException.class)
        .hasMessage("You have reached the account build limit. Please contact support: support@harness.io");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testEnforceBuildsCount_whenMaxBuildsIsZero_shouldThrowNotVerifiedError() throws Exception {
    Map<String, Long> countPerDay = new HashMap<>();
    java.time.LocalDate today = java.time.Instant.now().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
    java.time.YearMonth yearMonth = java.time.YearMonth.of(today.getYear(), today.getMonth());
    String dayKey = yearMonth + "-" + today.getDayOfMonth();
    countPerDay.put(dayKey, 1L);
    AccountExecutionInfo executionInfo = AccountExecutionInfo.builder().countPerDay(countPerDay).build();
    CIAccountExecutionMetadata metadata =
        CIAccountExecutionMetadata.builder().accountId("acc").accountExecutionInfo(executionInfo).build();

    when(ciAccountValidationService.getMaxBuildPerDay(eq("acc"), eq(ModuleType.CI.name()))).thenReturn(0L);

    assertThatThrownBy(()
                           -> Whitebox.invokeMethod(ciStagePlanCreationUtils, "enforceBuildsCount", "acc",
                               Optional.of(metadata), ModuleType.CI.name()))
        .as("Should throw not verified error when max builds is zero")
        .isInstanceOf(CIStageExecutionException.class)
        .hasMessage("We apologize, but your account is not verified for Harness Cloud. To resolve this issue, please "
            + "use your work email or contact support to request account verification: support@harness.io");
  }

  @Test
  @Owner(developers = SHUBHAM_AGARWAL)
  @Category(UnitTests.class)
  public void testValidateFreeAccountStageExecutionLimit_FreeLicenseHostedVm() {
    Infrastructure infrastructure = HostedVmInfraYaml.builder().type(Infrastructure.Type.HOSTED_VM).build();
    CILicenseSummaryDTO license = CILicenseSummaryDTO.builder().edition(Edition.FREE).build();
    when(ciLicenseService.getLicenseSummary("acc", ModuleType.CI.name())).thenReturn(license);
    when(accountExecutionMetadataRepository.findByAccountId("acc")).thenReturn(Optional.empty());

    assertThatCode(()
                       -> ciStagePlanCreationUtils.validateFreeAccountStageExecutionLimit(
                           "acc", infrastructure, ModuleType.CI.name()))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = SHUBHAM_AGARWAL)
  @Category(UnitTests.class)
  public void testValidateFreeAccountStageExecutionLimit_PaidLicenseHostedVm() {
    Infrastructure infrastructure = HostedVmInfraYaml.builder().type(Infrastructure.Type.HOSTED_VM).build();
    CILicenseSummaryDTO license = CILicenseSummaryDTO.builder().edition(Edition.ENTERPRISE).build();
    when(ciLicenseService.getLicenseSummary("acc", ModuleType.CI.name())).thenReturn(license);

    assertThatCode(()
                       -> ciStagePlanCreationUtils.validateFreeAccountStageExecutionLimit(
                           "acc", infrastructure, ModuleType.CI.name()))
        .doesNotThrowAnyException();
    verify(accountExecutionMetadataRepository, never()).findByAccountId(any());
  }

  @Test
  @Owner(developers = SHUBHAM_AGARWAL)
  @Category(UnitTests.class)
  public void testValidateFreeAccountStageExecutionLimit_NullLicenseThrows() {
    Infrastructure infrastructure = HostedVmInfraYaml.builder().type(Infrastructure.Type.HOSTED_VM).build();
    when(ciLicenseService.getLicenseSummary("acc", ModuleType.CI.name())).thenReturn(null);

    assertThatThrownBy(()
                           -> ciStagePlanCreationUtils.validateFreeAccountStageExecutionLimit(
                               "acc", infrastructure, ModuleType.CI.name()))
        .isInstanceOf(CIStageExecutionException.class);
  }

  @Test
  @Owner(developers = SHUBHAM_AGARWAL)
  @Category(UnitTests.class)
  public void testValidateFreeAccountStageExecutionLimit_NonHostedInfraSkips() {
    Infrastructure infrastructure = K8sDirectInfraYaml.builder().type(Infrastructure.Type.KUBERNETES_DIRECT).build();

    assertThatCode(()
                       -> ciStagePlanCreationUtils.validateFreeAccountStageExecutionLimit(
                           "acc", infrastructure, ModuleType.CI.name()))
        .doesNotThrowAnyException();
    verify(ciLicenseService, never()).getLicenseSummary(any(), any());
  }

  private Infrastructure buildInfrastructure(Infrastructure.Type type) {
    return new Infrastructure() {
      @Override
      public Type getType() {
        return type;
      }
    };
  }
}
