/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.api;

import static io.harness.ci.api.AnnotationUtils.assertParameterCounts;
import static io.harness.rule.OwnerRule.ANURAG_MADNAWAT;
import static io.harness.rule.OwnerRule.TAPAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

import io.harness.ModuleType;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.app.beans.entities.ExecutionQueueLimit;
import io.harness.category.element.UnitTests;
import io.harness.ci.api.AdminExecutionQueueLimitResourceImpl;
import io.harness.ci.config.ExecutionLimitSpec;
import io.harness.ci.execution.execution.QueueExecutionUtils;
import io.harness.ci.pipeline.executions.beans.ExecutionQueueLimitDTO;
import io.harness.exception.WingsException;
import io.harness.repositories.ExecutionQueueLimitRepository;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;

import java.lang.reflect.Method;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CI)
public class AdminExecutionQueueLimitResourceImplTest {
  @Mock private ExecutionQueueLimitRepository executionQueueLimitRepository;
  @Mock private QueueExecutionUtils queueExecutionUtils;

  @InjectMocks private AdminExecutionQueueLimitResourceImpl adminExecutionQueueLimitResource;
  @Before
  public void setUp() {
    openMocks(this);
  }

  private static final String ACCOUNT_IDENTIFIER = "testAccount";
  private static final String MAC_LIMIT = "5";
  private static final String TOTAL_LIMIT = "10";

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testUpdateExecutionLimitsAnnotations() throws NoSuchMethodException {
    // Check method level annotations for updateExecutionLimits method
    Method updateExecutionLimitsMethod = AdminExecutionQueueLimitResourceImpl.class.getDeclaredMethod(
        "updateExecutionLimits", String.class, ExecutionQueueLimitDTO.class);
    assertParameterCounts(updateExecutionLimitsMethod, 1, AccountIdentifier.class);
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetExecutionLimitsAnnotations() throws NoSuchMethodException {
    // Check method level annotations for getExecutionLimits method
    Method getExecutionLimitsMethod =
        AdminExecutionQueueLimitResourceImpl.class.getDeclaredMethod("getExecutionLimits", String.class);
    assertParameterCounts(getExecutionLimitsMethod, 1, AccountIdentifier.class);
  }
  @Test
  @Owner(developers = TAPAN)
  @Category(UnitTests.class)
  public void testGetExecutionLimitsWhenDbEntryExists() {
    ExecutionQueueLimit existingLimit = ExecutionQueueLimit.builder()
                                            .macExecLimit(MAC_LIMIT)
                                            .totalExecLimit(TOTAL_LIMIT)
                                            .accountIdentifier(ACCOUNT_IDENTIFIER)
                                            .build();

    when(executionQueueLimitRepository.findFirstByAccountIdentifier(ACCOUNT_IDENTIFIER))
        .thenReturn(Optional.of(existingLimit));

    RestResponse<ExecutionQueueLimitDTO> response =
        adminExecutionQueueLimitResource.getExecutionLimits(ACCOUNT_IDENTIFIER);

    assertThat(response).isNotNull();
    assertThat(response.getResource()).isNotNull();
    assertThat(response.getResource().getMacExecutionLimits()).isEqualTo(MAC_LIMIT);
    assertThat(response.getResource().getTotalExecutionLimits()).isEqualTo(TOTAL_LIMIT);
  }

  @Test
  @Owner(developers = TAPAN)
  @Category(UnitTests.class)
  public void testGetExecutionLimitsFallbackToFreeEditionLimits() {
    ExecutionLimitSpec freeLimits =
        ExecutionLimitSpec.builder().defaultMacExecutionCount(2).defaultTotalExecutionCount(5).build();

    when(executionQueueLimitRepository.findFirstByAccountIdentifier(ACCOUNT_IDENTIFIER)).thenReturn(Optional.empty());
    when(queueExecutionUtils.getDefaultLimitsBasedOnLicenseAndModuleType(ACCOUNT_IDENTIFIER, ModuleType.CI.name()))
        .thenReturn(freeLimits);

    RestResponse<ExecutionQueueLimitDTO> response =
        adminExecutionQueueLimitResource.getExecutionLimits(ACCOUNT_IDENTIFIER);

    assertThat(response).isNotNull();
    assertThat(response.getResource()).isNotNull();
    assertThat(response.getResource().getMacExecutionLimits()).isEqualTo("2");
    assertThat(response.getResource().getTotalExecutionLimits()).isEqualTo("5");
  }

  @Test
  @Owner(developers = TAPAN)
  @Category(UnitTests.class)
  public void testGetExecutionLimitsFallbackToTeamEditionLimits() {
    ExecutionLimitSpec teamLimits =
        ExecutionLimitSpec.builder().defaultMacExecutionCount(10).defaultTotalExecutionCount(25).build();

    when(executionQueueLimitRepository.findFirstByAccountIdentifier(ACCOUNT_IDENTIFIER)).thenReturn(Optional.empty());
    when(queueExecutionUtils.getDefaultLimitsBasedOnLicenseAndModuleType(ACCOUNT_IDENTIFIER, ModuleType.CI.name()))
        .thenReturn(teamLimits);

    RestResponse<ExecutionQueueLimitDTO> response =
        adminExecutionQueueLimitResource.getExecutionLimits(ACCOUNT_IDENTIFIER);

    assertThat(response).isNotNull();
    assertThat(response.getResource()).isNotNull();
    assertThat(response.getResource().getMacExecutionLimits()).isEqualTo("10");
    assertThat(response.getResource().getTotalExecutionLimits()).isEqualTo("25");
  }

  @Test
  @Owner(developers = TAPAN)
  @Category(UnitTests.class)
  public void testGetExecutionLimitsFallbackToEnterpriseLimits() {
    ExecutionLimitSpec enterpriseLimits =
        ExecutionLimitSpec.builder().defaultMacExecutionCount(50).defaultTotalExecutionCount(100).build();

    when(executionQueueLimitRepository.findFirstByAccountIdentifier(ACCOUNT_IDENTIFIER)).thenReturn(Optional.empty());
    when(queueExecutionUtils.getDefaultLimitsBasedOnLicenseAndModuleType(ACCOUNT_IDENTIFIER, ModuleType.CI.name()))
        .thenReturn(enterpriseLimits);

    RestResponse<ExecutionQueueLimitDTO> response =
        adminExecutionQueueLimitResource.getExecutionLimits(ACCOUNT_IDENTIFIER);

    assertThat(response).isNotNull();
    assertThat(response.getResource()).isNotNull();
    assertThat(response.getResource().getMacExecutionLimits()).isEqualTo("50");
    assertThat(response.getResource().getTotalExecutionLimits()).isEqualTo("100");
  }

  @Test
  @Owner(developers = TAPAN)
  @Category(UnitTests.class)
  public void testGetExecutionLimitsFallbackToDevopsEssentialsLimits() {
    ExecutionLimitSpec devopsEssentialsLimits =
        ExecutionLimitSpec.builder().defaultMacExecutionCount(20).defaultTotalExecutionCount(40).build();

    when(executionQueueLimitRepository.findFirstByAccountIdentifier(ACCOUNT_IDENTIFIER)).thenReturn(Optional.empty());
    when(queueExecutionUtils.getDefaultLimitsBasedOnLicenseAndModuleType(ACCOUNT_IDENTIFIER, ModuleType.CI.name()))
        .thenReturn(devopsEssentialsLimits);

    RestResponse<ExecutionQueueLimitDTO> response =
        adminExecutionQueueLimitResource.getExecutionLimits(ACCOUNT_IDENTIFIER);

    assertThat(response).isNotNull();
    assertThat(response.getResource()).isNotNull();
    assertThat(response.getResource().getMacExecutionLimits()).isEqualTo("20");
    assertThat(response.getResource().getTotalExecutionLimits()).isEqualTo("40");
  }

  @Test
  @Owner(developers = TAPAN)
  @Category(UnitTests.class)
  public void testGetExecutionLimitsFallbackToEssentialsLimits() {
    ExecutionLimitSpec essentialsLimits =
        ExecutionLimitSpec.builder().defaultMacExecutionCount(5).defaultTotalExecutionCount(65).build();

    when(executionQueueLimitRepository.findFirstByAccountIdentifier(ACCOUNT_IDENTIFIER)).thenReturn(Optional.empty());
    when(queueExecutionUtils.getDefaultLimitsBasedOnLicenseAndModuleType(ACCOUNT_IDENTIFIER, ModuleType.CI.name()))
        .thenReturn(essentialsLimits);

    RestResponse<ExecutionQueueLimitDTO> response =
        adminExecutionQueueLimitResource.getExecutionLimits(ACCOUNT_IDENTIFIER);

    assertThat(response).isNotNull();
    assertThat(response.getResource()).isNotNull();
    assertThat(response.getResource().getMacExecutionLimits()).isEqualTo("5");
    assertThat(response.getResource().getTotalExecutionLimits()).isEqualTo("65");
  }

  @Test
  @Owner(developers = TAPAN)
  @Category(UnitTests.class)
  public void testGetExecutionLimitsWhenCiLicenseServiceIsNull() {
    when(executionQueueLimitRepository.findFirstByAccountIdentifier(ACCOUNT_IDENTIFIER)).thenReturn(Optional.empty());
    when(queueExecutionUtils.getDefaultLimitsBasedOnLicenseAndModuleType(ACCOUNT_IDENTIFIER, ModuleType.CI.name()))
        .thenThrow(new WingsException("Please enable CI free plan or reach out to support."));

    assertThatThrownBy(() -> adminExecutionQueueLimitResource.getExecutionLimits(ACCOUNT_IDENTIFIER))
        .isInstanceOf(WingsException.class)
        .hasMessage("Please enable CI free plan or reach out to support.");
  }

  @Test
  @Owner(developers = TAPAN)
  @Category(UnitTests.class)
  public void testGetExecutionLimitsWhenDbEntryExistsAndMissingFieldsUseDefaults() {
    ExecutionQueueLimit existingLimit = ExecutionQueueLimit.builder()
                                            .macExecLimit(null)
                                            .totalExecLimit(TOTAL_LIMIT)
                                            .accountIdentifier(ACCOUNT_IDENTIFIER)
                                            .build();

    ExecutionLimitSpec defaults =
        ExecutionLimitSpec.builder().defaultMacExecutionCount(7).defaultTotalExecutionCount(99).build();

    when(executionQueueLimitRepository.findFirstByAccountIdentifier(ACCOUNT_IDENTIFIER))
        .thenReturn(Optional.of(existingLimit));
    when(queueExecutionUtils.getDefaultLimitsBasedOnLicenseAndModuleType(ACCOUNT_IDENTIFIER, ModuleType.CI.name()))
        .thenReturn(defaults);

    RestResponse<ExecutionQueueLimitDTO> response =
        adminExecutionQueueLimitResource.getExecutionLimits(ACCOUNT_IDENTIFIER);

    assertThat(response).isNotNull();
    assertThat(response.getResource()).isNotNull();
    assertThat(response.getResource().getMacExecutionLimits()).isEqualTo("7");
    assertThat(response.getResource().getTotalExecutionLimits()).isEqualTo(TOTAL_LIMIT);
  }
}
