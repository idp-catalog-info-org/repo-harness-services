/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.validation;

import static io.harness.rule.OwnerRule.CHIRAG_S;
import static io.harness.rule.OwnerRule.DEV_MITTAL;
import static io.harness.rule.OwnerRule.HEN;
import static io.harness.rule.OwnerRule.TAPAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.ModuleType;
import io.harness.account.services.AccountClient;
import io.harness.beans.execution.license.CILicenseService;
import io.harness.category.element.UnitTests;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.config.ExecutionLimitSpec;
import io.harness.ci.config.ExecutionLimits;
import io.harness.ci.executionplan.base.CIExecutionTestBase;
import io.harness.creditcard.remote.CreditCardClient;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.licensing.Edition;
import io.harness.licensing.LicenseType;
import io.harness.licensing.beans.summary.dto.CILicenseSummaryDTO;
import io.harness.ng.core.account.AccountTrustLevel;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.user.UserInfo;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.subscription.responses.AccountCreditCardValidationResponse;
import io.harness.user.remote.UserClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import retrofit2.Call;
import retrofit2.Response;

public class CIAccountValidationServiceTest extends CIExecutionTestBase {
  @InjectMocks CIAccountValidationServiceImpl accountValidationService;
  @Mock UserClient userClient;
  @Mock AccountClient accountClient;
  @Mock CreditCardClient creditCardClient;
  @Mock CILicenseService ciLicenseService;
  @Mock CIExecutionServiceConfig ciExecutionServiceConfig;
  @Mock CIMiningPatternJob ciMiningPatternJob;
  @Mock ExecutionLimits executionLimits;
  static final String accountId = "ACCOUNT_ID";
  @Before
  public void setup() {
    initMocks(this);
    when(executionLimits.getFreeBasicUser())
        .thenReturn(ExecutionLimitSpec.builder().dailyMaxBuildsCount(25).monthlyMaxCreditsCount(2000).build());
    when(executionLimits.getFreeNewUser())
        .thenReturn(ExecutionLimitSpec.builder().dailyMaxBuildsCount(0).monthlyMaxCreditsCount(0).build());
  }

  @Test
  @Owner(developers = HEN)
  @Category(UnitTests.class)
  public void testAccountValidationForValidDomain() throws IOException {
    Call<RestResponse<List<UserInfo>>> userEmailsCall = mock(Call.class);
    Call<RestResponse<Integer>> accountTrustLevelCall = mock(Call.class);

    UserInfo userInfo = UserInfo.builder().email("test@harness.com").build();
    ArrayList<UserInfo> userInfos = new ArrayList<>();
    userInfos.add(userInfo);

    when(userEmailsCall.execute()).thenReturn(Response.success(new RestResponse<>(userInfos)));
    when(accountTrustLevelCall.execute())
        .thenReturn(Response.success(new RestResponse<>(AccountTrustLevel.BASIC_USER)));

    when(userClient.listUsersEmails(any(String.class))).thenReturn(userEmailsCall);
    when(accountClient.getAccountTrustLevel(any(String.class))).thenReturn(accountTrustLevelCall);

    assertThat(accountValidationService.isAccountValidForExecution(accountId, ModuleType.CI.name())).isTrue();
  }

  @Test
  @Owner(developers = HEN)
  @Category(UnitTests.class)
  public void testAccountValidationForInvalidDomain() throws IOException {
    Call<RestResponse<List<UserInfo>>> userEmailsCall = mock(Call.class);
    Call<RestResponse<Integer>> accountTrustLevelCall = mock(Call.class);

    UserInfo userInfo = UserInfo.builder().email("test@xyz.com").build();
    ArrayList<UserInfo> userInfos = new ArrayList<>();
    userInfos.add(userInfo);

    when(accountTrustLevelCall.execute()).thenReturn(Response.success(new RestResponse<>(AccountTrustLevel.NEW_USER)));
    when(ciLicenseService.getLicenseSummary(any(String.class), eq(ModuleType.CI.name())))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.TRIAL).edition(Edition.FREE).build());
    when(accountClient.getAccountTrustLevel(any(String.class))).thenReturn(accountTrustLevelCall);

    boolean isValid = false;
    try {
      isValid = accountValidationService.isAccountValidForExecution(accountId, ModuleType.CI.name());
    } catch (Exception e) {
    }
    assertThat(isValid).isTrue();
  }

  @Test
  @Owner(developers = HEN)
  @Category(UnitTests.class)
  public void testAccountValidationForInvalidDomainWithPayingStatus() throws IOException {
    Call<RestResponse<List<UserInfo>>> userEmailsCall = mock(Call.class);
    Call<RestResponse<Integer>> accountTrustLevelCall = mock(Call.class);

    UserInfo userInfo = UserInfo.builder().email("test@xyz.com").build();
    ArrayList<UserInfo> userInfos = new ArrayList<>();
    userInfos.add(userInfo);

    when(accountTrustLevelCall.execute()).thenReturn(Response.success(new RestResponse<>(AccountTrustLevel.NEW_USER)));
    when(ciLicenseService.getLicenseSummary(any(String.class), eq(ModuleType.CI.name())))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.PAID).edition(Edition.FREE).build());
    when(accountClient.getAccountTrustLevel(any(String.class))).thenReturn(accountTrustLevelCall);

    boolean isValid = false;
    try {
      isValid = accountValidationService.isAccountValidForExecution(accountId, ModuleType.CI.name());
    } catch (Exception e) {
    }
    assertThat(isValid).isTrue();
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testGetTrustLevel() throws IOException {
    Call<RestResponse<List<UserInfo>>> userEmailsCall = mock(Call.class);
    Call<RestResponse<Integer>> accountTrustLevelCall = mock(Call.class);

    UserInfo userInfo = UserInfo.builder().email("test@xyz.com").build();
    ArrayList<UserInfo> userInfos = new ArrayList<>();
    userInfos.add(userInfo);

    when(accountTrustLevelCall.execute()).thenReturn(Response.success(new RestResponse<>(AccountTrustLevel.NEW_USER)));
    when(userEmailsCall.execute()).thenReturn(Response.success(new RestResponse<>(userInfos)));
    when(accountClient.getAccountTrustLevel(any(String.class))).thenReturn(accountTrustLevelCall);
    when(userClient.listUsersEmails(any(String.class))).thenReturn(userEmailsCall);

    Integer trustLevel = accountValidationService.getTrustLevel(accountId);

    assertThat(trustLevel).isEqualTo(0);
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testGetMaxBuildPerDayOldAccount() throws IOException {
    Call<RestResponse<List<UserInfo>>> userEmailsCall = mock(Call.class);
    Call<RestResponse<Integer>> accountTrustLevelCall = mock(Call.class);
    Call<RestResponse<AccountDTO>> accountDTOCall = mock(Call.class);

    UserInfo userInfo = UserInfo.builder().email("test@xyz.com").build();
    ArrayList<UserInfo> userInfos = new ArrayList<>();
    userInfos.add(userInfo);
    AccountDTO dto = AccountDTO.builder().createdAt(CIAccountValidationServiceImpl.APPLY_DAY - 1000).build();

    when(accountTrustLevelCall.execute()).thenReturn(Response.success(new RestResponse<>(AccountTrustLevel.NEW_USER)));
    when(userEmailsCall.execute()).thenReturn(Response.success(new RestResponse<>(userInfos)));
    when(accountDTOCall.execute()).thenReturn(Response.success(new RestResponse<>(dto)));

    when(ciLicenseService.getLicenseSummary(any(String.class), eq(ModuleType.CI.name())))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.PAID).edition(Edition.FREE).build());
    when(accountClient.getAccountTrustLevel(any(String.class))).thenReturn(accountTrustLevelCall);
    when(accountClient.getAccountDTO(any(String.class))).thenReturn(accountDTOCall);
    when(userClient.listUsersEmails(any(String.class))).thenReturn(userEmailsCall);

    long buildsCount = accountValidationService.getMaxBuildPerDay(accountId, ModuleType.CI.name());

    assertThat(buildsCount).isEqualTo(25);
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testGetMaxBuildPerDayNewAccountValidCC() throws IOException {
    Call<RestResponse<List<UserInfo>>> userEmailsCall = mock(Call.class);
    Call<RestResponse<Integer>> accountTrustLevelCall = mock(Call.class);
    Call<RestResponse<AccountDTO>> accountDTOCall = mock(Call.class);
    Call<ResponseDTO<AccountCreditCardValidationResponse>> creditCardCall = mock(Call.class);

    UserInfo userInfo = UserInfo.builder().email("test@xyz.com").build();
    ArrayList<UserInfo> userInfos = new ArrayList<>();
    userInfos.add(userInfo);
    AccountDTO dto = AccountDTO.builder().createdAt(CIAccountValidationServiceImpl.APPLY_DAY + 1000).build();
    AccountCreditCardValidationResponse creditCardValidationResponse =
        AccountCreditCardValidationResponse.builder().hasAtleastOneValidCreditCard(true).build();

    when(accountTrustLevelCall.execute()).thenReturn(Response.success(new RestResponse<>(AccountTrustLevel.NEW_USER)));
    when(userEmailsCall.execute()).thenReturn(Response.success(new RestResponse<>(userInfos)));
    when(accountDTOCall.execute()).thenReturn(Response.success(new RestResponse<>(dto)));
    when(creditCardCall.execute()).thenReturn(Response.success(ResponseDTO.newResponse(creditCardValidationResponse)));

    when(ciLicenseService.getLicenseSummary(any(String.class), eq(ModuleType.CI.name())))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.PAID).edition(Edition.FREE).build());
    when(accountClient.getAccountTrustLevel(any(String.class))).thenReturn(accountTrustLevelCall);
    when(accountClient.getAccountDTO(any(String.class))).thenReturn(accountDTOCall);
    when(userClient.listUsersEmails(any(String.class))).thenReturn(userEmailsCall);
    when(creditCardClient.validateCreditCard(any(String.class))).thenReturn(creditCardCall);

    long buildsCount = accountValidationService.getMaxBuildPerDay(accountId, ModuleType.CI.name());

    assertThat(buildsCount).isEqualTo(25);
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testGetMaxCreditsPerMonthNewAccountValidCC() throws IOException {
    Call<RestResponse<List<UserInfo>>> userEmailsCall = mock(Call.class);
    Call<RestResponse<Integer>> accountTrustLevelCall = mock(Call.class);
    Call<RestResponse<AccountDTO>> accountDTOCall = mock(Call.class);
    Call<ResponseDTO<AccountCreditCardValidationResponse>> creditCardCall = mock(Call.class);

    UserInfo userInfo = UserInfo.builder().email("test@xyz.com").build();
    ArrayList<UserInfo> userInfos = new ArrayList<>();
    userInfos.add(userInfo);
    AccountDTO dto = AccountDTO.builder().createdAt(CIAccountValidationServiceImpl.APPLY_DAY + 1000).build();
    AccountCreditCardValidationResponse creditCardValidationResponse =
        AccountCreditCardValidationResponse.builder().hasAtleastOneValidCreditCard(true).build();

    when(accountTrustLevelCall.execute()).thenReturn(Response.success(new RestResponse<>(AccountTrustLevel.NEW_USER)));
    when(userEmailsCall.execute()).thenReturn(Response.success(new RestResponse<>(userInfos)));
    when(accountDTOCall.execute()).thenReturn(Response.success(new RestResponse<>(dto)));
    when(creditCardCall.execute()).thenReturn(Response.success(ResponseDTO.newResponse(creditCardValidationResponse)));

    when(ciLicenseService.getLicenseSummary(any(String.class), eq(ModuleType.CI.name())))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.PAID).edition(Edition.FREE).build());
    when(accountClient.getAccountTrustLevel(any(String.class))).thenReturn(accountTrustLevelCall);
    when(accountClient.getAccountDTO(any(String.class))).thenReturn(accountDTOCall);
    when(userClient.listUsersEmails(any(String.class))).thenReturn(userEmailsCall);
    when(creditCardClient.validateCreditCard(any(String.class))).thenReturn(creditCardCall);

    long creditsCount = accountValidationService.getMaxCreditsPerMonth(accountId, ModuleType.CI.name());

    assertThat(creditsCount).isEqualTo(2000);
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testGetMaxBuildPerDayNewAccountInvalidCC() throws IOException {
    Call<RestResponse<List<UserInfo>>> userEmailsCall = mock(Call.class);
    Call<RestResponse<Integer>> accountTrustLevelCall = mock(Call.class);
    Call<RestResponse<AccountDTO>> accountDTOCall = mock(Call.class);
    Call<ResponseDTO<AccountCreditCardValidationResponse>> creditCardCall = mock(Call.class);

    UserInfo userInfo = UserInfo.builder().email("test@xyz.com").build();
    ArrayList<UserInfo> userInfos = new ArrayList<>();
    userInfos.add(userInfo);
    AccountDTO dto = AccountDTO.builder().createdAt(CIAccountValidationServiceImpl.APPLY_DAY + 1000).build();
    AccountCreditCardValidationResponse creditCardValidationResponse =
        AccountCreditCardValidationResponse.builder().hasAtleastOneValidCreditCard(false).build();

    when(accountTrustLevelCall.execute()).thenReturn(Response.success(new RestResponse<>(AccountTrustLevel.NEW_USER)));
    when(userEmailsCall.execute()).thenReturn(Response.success(new RestResponse<>(userInfos)));
    when(accountDTOCall.execute()).thenReturn(Response.success(new RestResponse<>(dto)));
    when(creditCardCall.execute()).thenReturn(Response.success(ResponseDTO.newResponse(creditCardValidationResponse)));

    when(ciLicenseService.getLicenseSummary(any(String.class), eq(ModuleType.CI.name())))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.PAID).edition(Edition.FREE).build());
    when(accountClient.getAccountTrustLevel(any(String.class))).thenReturn(accountTrustLevelCall);
    when(accountClient.getAccountDTO(any(String.class))).thenReturn(accountDTOCall);
    when(userClient.listUsersEmails(any(String.class))).thenReturn(userEmailsCall);
    when(creditCardClient.validateCreditCard(any(String.class))).thenReturn(creditCardCall);

    long buildsCount = accountValidationService.getMaxBuildPerDay(accountId, ModuleType.CI.name());

    assertThat(buildsCount).isEqualTo(0);
  }

  @Test
  @Owner(developers = TAPAN)
  @Category(UnitTests.class)
  public void testAccountValidationBypassForEssentialsEdition() throws IOException {
    // ESSENTIALS edition should bypass account validation similar to ENTERPRISE and TEAM
    when(ciLicenseService.getLicenseSummary(any(String.class), eq(ModuleType.CI.name())))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.PAID).edition(Edition.ESSENTIALS).build());

    boolean isValid = accountValidationService.isAccountValidForExecution(accountId, ModuleType.CI.name());

    assertThat(isValid).isTrue();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetTrustLevel_whenUninitialized_shouldInitializeAndUpdate() throws IOException {
    Call<RestResponse<Integer>> accountTrustLevelCall = mock(Call.class);
    Call<RestResponse<Boolean>> updateTrustLevelCall = mock(Call.class);
    Call<RestResponse<List<UserInfo>>> userEmailsCall = mock(Call.class);

    when(accountTrustLevelCall.execute())
        .thenReturn(Response.success(new RestResponse<>(AccountTrustLevel.UNINITIALIZED)));
    when(updateTrustLevelCall.execute()).thenReturn(Response.success(new RestResponse<>(true)));
    when(accountClient.getAccountTrustLevel(any(String.class))).thenReturn(accountTrustLevelCall);
    when(accountClient.updateAccountTrustLevel(any(String.class), anyInt())).thenReturn(updateTrustLevelCall);

    // Setup initializeAccountTrustLevel dependencies - local config returns true for simple path
    when(ciExecutionServiceConfig.isLocal()).thenReturn(true);

    Integer trustLevel = accountValidationService.getTrustLevel(accountId);

    assertThat(trustLevel)
        .as("Should return BASIC_USER when local config is true and trust level was UNINITIALIZED")
        .isEqualTo(AccountTrustLevel.BASIC_USER);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetTrustLevel_whenExceptionOccurs_shouldReturnBasicUser() throws IOException {
    Call<RestResponse<Integer>> accountTrustLevelCall = mock(Call.class);
    when(accountTrustLevelCall.execute()).thenThrow(new RuntimeException("API failure"));
    when(accountClient.getAccountTrustLevel(any(String.class))).thenReturn(accountTrustLevelCall);

    Integer trustLevel = accountValidationService.getTrustLevel(accountId);

    assertThat(trustLevel)
        .as("Should fall back to BASIC_USER when exception occurs")
        .isEqualTo(AccountTrustLevel.BASIC_USER);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testIsAccountValidForExecution_whenFreeLicenseAndOldAccount_shouldReturnTrue() throws IOException {
    Call<RestResponse<AccountDTO>> accountDTOCall = mock(Call.class);
    AccountDTO dto = AccountDTO.builder().createdAt(CIAccountValidationServiceImpl.APPLY_DAY - 1000).build();

    when(ciLicenseService.getLicenseSummary(any(String.class), eq(ModuleType.CI.name())))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.TRIAL).edition(Edition.FREE).build());
    when(accountDTOCall.execute()).thenReturn(Response.success(new RestResponse<>(dto)));
    when(accountClient.getAccountDTO(any(String.class))).thenReturn(accountDTOCall);

    boolean isValid = accountValidationService.isAccountValidForExecution(accountId, ModuleType.CI.name());

    assertThat(isValid).as("Should return true for accounts created before APPLY_DAY with free license").isTrue();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testIsAccountValidForExecution_whenWhitelisted_shouldReturnTrue() throws IOException {
    Call<RestResponse<AccountDTO>> accountDTOCall = mock(Call.class);
    AccountDTO dto = AccountDTO.builder().createdAt(CIAccountValidationServiceImpl.APPLY_DAY + 1000).build();
    Set<String> whiteListed = new HashSet<>();
    whiteListed.add(accountId);

    when(ciLicenseService.getLicenseSummary(any(String.class), eq(ModuleType.CI.name())))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.TRIAL).edition(Edition.FREE).build());
    when(accountDTOCall.execute()).thenReturn(Response.success(new RestResponse<>(dto)));
    when(accountClient.getAccountDTO(any(String.class))).thenReturn(accountDTOCall);
    when(ciMiningPatternJob.getWhiteListed()).thenReturn(whiteListed);

    boolean isValid = accountValidationService.isAccountValidForExecution(accountId, ModuleType.CI.name());

    assertThat(isValid).as("Should return true for whitelisted accounts").isTrue();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testIsAccountValidForExecution_whenUninitializedTrustLevel_shouldInitialize() throws IOException {
    Call<RestResponse<AccountDTO>> accountDTOCall = mock(Call.class);
    Call<RestResponse<Integer>> accountTrustLevelCall = mock(Call.class);
    Call<RestResponse<Boolean>> updateTrustLevelCall = mock(Call.class);
    AccountDTO dto = AccountDTO.builder().createdAt(CIAccountValidationServiceImpl.APPLY_DAY + 1000).build();

    when(ciLicenseService.getLicenseSummary(any(String.class), eq(ModuleType.CI.name())))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.TRIAL).edition(Edition.FREE).build());
    when(accountDTOCall.execute()).thenReturn(Response.success(new RestResponse<>(dto)));
    when(accountClient.getAccountDTO(any(String.class))).thenReturn(accountDTOCall);
    when(ciMiningPatternJob.getWhiteListed()).thenReturn(Collections.emptySet());
    when(accountTrustLevelCall.execute())
        .thenReturn(Response.success(new RestResponse<>(AccountTrustLevel.UNINITIALIZED)));
    when(accountClient.getAccountTrustLevel(any(String.class))).thenReturn(accountTrustLevelCall);
    when(ciExecutionServiceConfig.isLocal()).thenReturn(true);
    when(updateTrustLevelCall.execute()).thenReturn(Response.success(new RestResponse<>(true)));
    when(accountClient.updateAccountTrustLevel(any(String.class), anyInt())).thenReturn(updateTrustLevelCall);

    boolean isValid = accountValidationService.isAccountValidForExecution(accountId, ModuleType.CI.name());

    assertThat(isValid).as("Should return true after initializing trust level for UNINITIALIZED account").isTrue();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testIsAccountValidForExecution_whenTrustLevelBelowBasic_shouldThrow() throws IOException {
    Call<RestResponse<AccountDTO>> accountDTOCall = mock(Call.class);
    Call<RestResponse<Integer>> accountTrustLevelCall = mock(Call.class);
    AccountDTO dto = AccountDTO.builder().createdAt(CIAccountValidationServiceImpl.APPLY_DAY + 1000).build();

    when(ciLicenseService.getLicenseSummary(any(String.class), eq(ModuleType.CI.name())))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.TRIAL).edition(Edition.FREE).build());
    when(accountDTOCall.execute()).thenReturn(Response.success(new RestResponse<>(dto)));
    when(accountClient.getAccountDTO(any(String.class))).thenReturn(accountDTOCall);
    when(ciMiningPatternJob.getWhiteListed()).thenReturn(Collections.emptySet());
    when(accountTrustLevelCall.execute()).thenReturn(Response.success(new RestResponse<>(AccountTrustLevel.NEW_USER)));
    when(accountClient.getAccountTrustLevel(any(String.class))).thenReturn(accountTrustLevelCall);

    assertThatThrownBy(() -> accountValidationService.isAccountValidForExecution(accountId, ModuleType.CI.name()))
        .as("Should throw CIStageExecutionException when trust level is below BASIC_USER")
        .isInstanceOf(CIStageExecutionException.class)
        .hasMessageContaining("not trusted");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testIsAccountValidForExecution_whenNullLicense_shouldCatchAndReturnTrue() {
    when(ciLicenseService.getLicenseSummary(any(String.class), eq(ModuleType.CI.name()))).thenReturn(null);

    // The CIStageExecutionException thrown on line 77 is caught by the outer catch block (line 108),
    // which sets trustLevel to BASIC_USER. Since BASIC_USER >= BASIC_USER, it returns true.
    boolean isValid = accountValidationService.isAccountValidForExecution(accountId, ModuleType.CI.name());

    assertThat(isValid)
        .as("Should return true because exception is caught and trustLevel defaults to BASIC_USER")
        .isTrue();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetMaxBuildPerDay_whenDefaultTrustLevel_shouldReturnNewUserLimits() throws IOException {
    Call<RestResponse<AccountDTO>> accountDTOCall = mock(Call.class);
    Call<RestResponse<Integer>> accountTrustLevelCall = mock(Call.class);
    Call<RestResponse<Boolean>> updateTrustLevelCall = mock(Call.class);
    Call<RestResponse<List<UserInfo>>> userEmailsCall = mock(Call.class);
    AccountDTO dto = AccountDTO.builder().createdAt(CIAccountValidationServiceImpl.APPLY_DAY + 1000).build();

    UserInfo userInfo = UserInfo.builder().email("test@unknown.com").build();
    ArrayList<UserInfo> userInfos = new ArrayList<>();
    userInfos.add(userInfo);

    when(ciLicenseService.getLicenseSummary(any(String.class), eq(ModuleType.CI.name())))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.TRIAL).edition(Edition.FREE).build());
    when(accountDTOCall.execute()).thenReturn(Response.success(new RestResponse<>(dto)));
    when(accountClient.getAccountDTO(any(String.class))).thenReturn(accountDTOCall);
    when(ciMiningPatternJob.getWhiteListed()).thenReturn(Collections.emptySet());
    // Return UNINITIALIZED so getTrustLevel is called, which will then re-init
    when(accountTrustLevelCall.execute())
        .thenReturn(Response.success(new RestResponse<>(AccountTrustLevel.UNINITIALIZED)));
    when(accountClient.getAccountTrustLevel(any(String.class))).thenReturn(accountTrustLevelCall);
    when(ciExecutionServiceConfig.isLocal()).thenReturn(false);
    when(ciMiningPatternJob.getValidDomains()).thenReturn(Collections.emptySet());
    when(userEmailsCall.execute()).thenReturn(Response.success(new RestResponse<>(userInfos)));
    when(userClient.listUsersEmails(any(String.class))).thenReturn(userEmailsCall);
    when(updateTrustLevelCall.execute()).thenReturn(Response.success(new RestResponse<>(true)));
    when(accountClient.updateAccountTrustLevel(any(String.class), anyInt())).thenReturn(updateTrustLevelCall);

    long buildsCount = accountValidationService.getMaxBuildPerDay(accountId, ModuleType.CI.name());

    assertThat(buildsCount).as("Should return freeNewUser daily max builds for NEW_USER trust level").isEqualTo(0);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetMaxCreditsPerMonth_whenBasicUser_shouldReturnBasicUserLimits() throws IOException {
    Call<RestResponse<AccountDTO>> accountDTOCall = mock(Call.class);
    AccountDTO dto = AccountDTO.builder().createdAt(CIAccountValidationServiceImpl.APPLY_DAY - 1000).build();

    when(ciLicenseService.getLicenseSummary(any(String.class), eq(ModuleType.CI.name())))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.TRIAL).edition(Edition.FREE).build());
    when(accountDTOCall.execute()).thenReturn(Response.success(new RestResponse<>(dto)));
    when(accountClient.getAccountDTO(any(String.class))).thenReturn(accountDTOCall);
    when(ciMiningPatternJob.getWhiteListed()).thenReturn(Collections.emptySet());

    long creditsCount = accountValidationService.getMaxCreditsPerMonth(accountId, ModuleType.CI.name());

    assertThat(creditsCount).as("Should return freeBasicUser monthly max credits for BASIC_USER").isEqualTo(2000);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetMaxCreditsPerMonth_whenNewUserNoValidCC_shouldReturnNewUserLimits() throws IOException {
    Call<RestResponse<AccountDTO>> accountDTOCall = mock(Call.class);
    Call<RestResponse<Integer>> accountTrustLevelCall = mock(Call.class);
    Call<RestResponse<Boolean>> updateTrustLevelCall = mock(Call.class);
    Call<RestResponse<List<UserInfo>>> userEmailsCall = mock(Call.class);
    Call<ResponseDTO<AccountCreditCardValidationResponse>> creditCardCall = mock(Call.class);
    AccountDTO dto = AccountDTO.builder().createdAt(CIAccountValidationServiceImpl.APPLY_DAY + 1000).build();

    UserInfo userInfo = UserInfo.builder().email("test@unknown.com").build();
    ArrayList<UserInfo> userInfos = new ArrayList<>();
    userInfos.add(userInfo);
    AccountCreditCardValidationResponse creditCardValidationResponse =
        AccountCreditCardValidationResponse.builder().hasAtleastOneValidCreditCard(false).build();

    when(ciLicenseService.getLicenseSummary(any(String.class), eq(ModuleType.CI.name())))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.TRIAL).edition(Edition.FREE).build());
    when(accountDTOCall.execute()).thenReturn(Response.success(new RestResponse<>(dto)));
    when(accountClient.getAccountDTO(any(String.class))).thenReturn(accountDTOCall);
    when(ciMiningPatternJob.getWhiteListed()).thenReturn(Collections.emptySet());
    when(accountTrustLevelCall.execute())
        .thenReturn(Response.success(new RestResponse<>(AccountTrustLevel.UNINITIALIZED)));
    when(accountClient.getAccountTrustLevel(any(String.class))).thenReturn(accountTrustLevelCall);
    when(ciExecutionServiceConfig.isLocal()).thenReturn(false);
    when(ciMiningPatternJob.getValidDomains()).thenReturn(Collections.emptySet());
    when(userEmailsCall.execute()).thenReturn(Response.success(new RestResponse<>(userInfos)));
    when(userClient.listUsersEmails(any(String.class))).thenReturn(userEmailsCall);
    when(updateTrustLevelCall.execute()).thenReturn(Response.success(new RestResponse<>(true)));
    when(accountClient.updateAccountTrustLevel(any(String.class), anyInt())).thenReturn(updateTrustLevelCall);
    when(creditCardCall.execute()).thenReturn(Response.success(ResponseDTO.newResponse(creditCardValidationResponse)));
    when(creditCardClient.validateCreditCard(any(String.class))).thenReturn(creditCardCall);

    long creditsCount = accountValidationService.getMaxCreditsPerMonth(accountId, ModuleType.CI.name());

    assertThat(creditsCount)
        .as("Should return freeNewUser monthly max credits for NEW_USER without valid CC")
        .isEqualTo(0);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetMaxBuildPerDay_whenNullLicense_shouldThrow() {
    when(ciLicenseService.getLicenseSummary(any(String.class), eq(ModuleType.CI.name()))).thenReturn(null);

    assertThatThrownBy(() -> accountValidationService.getMaxBuildPerDay(accountId, ModuleType.CI.name()))
        .as("Should throw when license summary is null")
        .isInstanceOf(CIStageExecutionException.class)
        .hasMessageContaining("enable CI free plan");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetMaxBuildPerDay_whenNonFreeLicense_shouldThrow() {
    when(ciLicenseService.getLicenseSummary(any(String.class), eq(ModuleType.CI.name())))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.PAID).edition(Edition.ENTERPRISE).build());

    assertThatThrownBy(() -> accountValidationService.getMaxBuildPerDay(accountId, ModuleType.CI.name()))
        .as("Should throw IllegalArgumentException for non-free license")
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("non free license");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetMaxBuildPerDay_whenCreditCardCheckFails_shouldReturnNewUserLimits() throws IOException {
    Call<RestResponse<AccountDTO>> accountDTOCall = mock(Call.class);
    Call<RestResponse<Integer>> accountTrustLevelCall = mock(Call.class);
    Call<RestResponse<Boolean>> updateTrustLevelCall = mock(Call.class);
    Call<RestResponse<List<UserInfo>>> userEmailsCall = mock(Call.class);
    Call<ResponseDTO<AccountCreditCardValidationResponse>> creditCardCall = mock(Call.class);
    AccountDTO dto = AccountDTO.builder().createdAt(CIAccountValidationServiceImpl.APPLY_DAY + 1000).build();

    UserInfo userInfo = UserInfo.builder().email("test@unknown.com").build();
    ArrayList<UserInfo> userInfos = new ArrayList<>();
    userInfos.add(userInfo);

    when(ciLicenseService.getLicenseSummary(any(String.class), eq(ModuleType.CI.name())))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.TRIAL).edition(Edition.FREE).build());
    when(accountDTOCall.execute()).thenReturn(Response.success(new RestResponse<>(dto)));
    when(accountClient.getAccountDTO(any(String.class))).thenReturn(accountDTOCall);
    when(ciMiningPatternJob.getWhiteListed()).thenReturn(Collections.emptySet());
    when(accountTrustLevelCall.execute())
        .thenReturn(Response.success(new RestResponse<>(AccountTrustLevel.UNINITIALIZED)));
    when(accountClient.getAccountTrustLevel(any(String.class))).thenReturn(accountTrustLevelCall);
    when(ciExecutionServiceConfig.isLocal()).thenReturn(false);
    when(ciMiningPatternJob.getValidDomains()).thenReturn(Collections.emptySet());
    when(userEmailsCall.execute()).thenReturn(Response.success(new RestResponse<>(userInfos)));
    when(userClient.listUsersEmails(any(String.class))).thenReturn(userEmailsCall);
    when(updateTrustLevelCall.execute()).thenReturn(Response.success(new RestResponse<>(true)));
    when(accountClient.updateAccountTrustLevel(any(String.class), anyInt())).thenReturn(updateTrustLevelCall);
    // Credit card call throws exception
    when(creditCardClient.validateCreditCard(any(String.class))).thenThrow(new RuntimeException("CC API failure"));

    long buildsCount = accountValidationService.getMaxBuildPerDay(accountId, ModuleType.CI.name());

    assertThat(buildsCount)
        .as("Should return freeNewUser daily max builds when credit card check throws exception")
        .isEqualTo(0);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetTrustLevel_whenUninitializedAndLocal_shouldReturnBasicUser() throws IOException {
    Call<RestResponse<Integer>> accountTrustLevelCall = mock(Call.class);
    Call<RestResponse<Boolean>> updateTrustLevelCall = mock(Call.class);

    when(accountTrustLevelCall.execute())
        .thenReturn(Response.success(new RestResponse<>(AccountTrustLevel.UNINITIALIZED)));
    when(updateTrustLevelCall.execute()).thenReturn(Response.success(new RestResponse<>(true)));
    when(accountClient.getAccountTrustLevel(any(String.class))).thenReturn(accountTrustLevelCall);
    when(accountClient.updateAccountTrustLevel(any(String.class), anyInt())).thenReturn(updateTrustLevelCall);
    when(ciExecutionServiceConfig.isLocal()).thenReturn(true);

    Integer trustLevel = accountValidationService.getTrustLevel(accountId);

    assertThat(trustLevel)
        .as("Should return BASIC_USER when local config and trust level was UNINITIALIZED")
        .isEqualTo(AccountTrustLevel.BASIC_USER);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetTrustLevel_whenUninitializedAndWhitelisted_shouldReturnBasicUser() throws IOException {
    Call<RestResponse<Integer>> accountTrustLevelCall = mock(Call.class);
    Call<RestResponse<Boolean>> updateTrustLevelCall = mock(Call.class);

    Set<String> whiteListed = new HashSet<>();
    whiteListed.add(accountId);

    when(accountTrustLevelCall.execute())
        .thenReturn(Response.success(new RestResponse<>(AccountTrustLevel.UNINITIALIZED)));
    when(updateTrustLevelCall.execute()).thenReturn(Response.success(new RestResponse<>(true)));
    when(accountClient.getAccountTrustLevel(any(String.class))).thenReturn(accountTrustLevelCall);
    when(accountClient.updateAccountTrustLevel(any(String.class), anyInt())).thenReturn(updateTrustLevelCall);
    when(ciExecutionServiceConfig.isLocal()).thenReturn(false);
    when(ciMiningPatternJob.getWhiteListed()).thenReturn(whiteListed);

    Integer trustLevel = accountValidationService.getTrustLevel(accountId);

    assertThat(trustLevel)
        .as("Should return BASIC_USER when account is whitelisted")
        .isEqualTo(AccountTrustLevel.BASIC_USER);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetTrustLevel_whenUninitializedAndValidDomain_shouldReturnBasicUser() throws IOException {
    Call<RestResponse<Integer>> accountTrustLevelCall = mock(Call.class);
    Call<RestResponse<Boolean>> updateTrustLevelCall = mock(Call.class);
    Call<RestResponse<List<UserInfo>>> userEmailsCall = mock(Call.class);

    UserInfo userInfo = UserInfo.builder().email("test@harness.io").build();
    ArrayList<UserInfo> userInfos = new ArrayList<>();
    userInfos.add(userInfo);

    Set<String> validDomains = new HashSet<>();
    validDomains.add("harness.io");

    when(accountTrustLevelCall.execute())
        .thenReturn(Response.success(new RestResponse<>(AccountTrustLevel.UNINITIALIZED)));
    when(updateTrustLevelCall.execute()).thenReturn(Response.success(new RestResponse<>(true)));
    when(accountClient.getAccountTrustLevel(any(String.class))).thenReturn(accountTrustLevelCall);
    when(accountClient.updateAccountTrustLevel(any(String.class), anyInt())).thenReturn(updateTrustLevelCall);
    when(ciExecutionServiceConfig.isLocal()).thenReturn(false);
    when(ciMiningPatternJob.getWhiteListed()).thenReturn(Collections.emptySet());
    when(ciMiningPatternJob.getValidDomains()).thenReturn(validDomains);
    when(userEmailsCall.execute()).thenReturn(Response.success(new RestResponse<>(userInfos)));
    when(userClient.listUsersEmails(any(String.class))).thenReturn(userEmailsCall);

    Integer trustLevel = accountValidationService.getTrustLevel(accountId);

    assertThat(trustLevel)
        .as("Should return BASIC_USER when user has email from valid domain")
        .isEqualTo(AccountTrustLevel.BASIC_USER);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetTrustLevel_whenUninitializedAndInvalidDomain_shouldReturnNewUser() throws IOException {
    Call<RestResponse<Integer>> accountTrustLevelCall = mock(Call.class);
    Call<RestResponse<Boolean>> updateTrustLevelCall = mock(Call.class);
    Call<RestResponse<List<UserInfo>>> userEmailsCall = mock(Call.class);

    UserInfo userInfo = UserInfo.builder().email("test@suspicious.com").build();
    ArrayList<UserInfo> userInfos = new ArrayList<>();
    userInfos.add(userInfo);

    when(accountTrustLevelCall.execute())
        .thenReturn(Response.success(new RestResponse<>(AccountTrustLevel.UNINITIALIZED)));
    when(updateTrustLevelCall.execute()).thenReturn(Response.success(new RestResponse<>(true)));
    when(accountClient.getAccountTrustLevel(any(String.class))).thenReturn(accountTrustLevelCall);
    when(accountClient.updateAccountTrustLevel(any(String.class), anyInt())).thenReturn(updateTrustLevelCall);
    when(ciExecutionServiceConfig.isLocal()).thenReturn(false);
    when(ciMiningPatternJob.getWhiteListed()).thenReturn(Collections.emptySet());
    when(ciMiningPatternJob.getValidDomains()).thenReturn(Collections.emptySet());
    when(userEmailsCall.execute()).thenReturn(Response.success(new RestResponse<>(userInfos)));
    when(userClient.listUsersEmails(any(String.class))).thenReturn(userEmailsCall);

    Integer trustLevel = accountValidationService.getTrustLevel(accountId);

    assertThat(trustLevel)
        .as("Should return NEW_USER when user has email from invalid domain")
        .isEqualTo(AccountTrustLevel.NEW_USER);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetTrustLevel_whenUpdateFails_shouldStillReturnTrustLevel() throws IOException {
    Call<RestResponse<Integer>> accountTrustLevelCall = mock(Call.class);
    Call<RestResponse<Boolean>> updateTrustLevelCall = mock(Call.class);

    when(accountTrustLevelCall.execute())
        .thenReturn(Response.success(new RestResponse<>(AccountTrustLevel.UNINITIALIZED)));
    when(updateTrustLevelCall.execute()).thenReturn(Response.success(new RestResponse<>(false)));
    when(accountClient.getAccountTrustLevel(any(String.class))).thenReturn(accountTrustLevelCall);
    when(accountClient.updateAccountTrustLevel(any(String.class), anyInt())).thenReturn(updateTrustLevelCall);
    when(ciExecutionServiceConfig.isLocal()).thenReturn(true);

    Integer trustLevel = accountValidationService.getTrustLevel(accountId);

    assertThat(trustLevel)
        .as("Should still return initialized trust level even when update fails")
        .isEqualTo(AccountTrustLevel.BASIC_USER);
  }
}
