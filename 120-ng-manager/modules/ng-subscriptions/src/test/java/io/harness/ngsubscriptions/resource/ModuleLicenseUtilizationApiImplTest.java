/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngsubscriptions.resource;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.PRAVEEN_SOLANKI;

import static javax.ws.rs.core.Response.Status.OK;
import static junit.framework.TestCase.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.ModuleType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cd.CDLicenseType;
import io.harness.licensing.LicenseStatus;
import io.harness.licensing.beans.modules.AccountLicenseDTO;
import io.harness.licensing.beans.modules.CDModuleLicenseDTO;
import io.harness.licensing.beans.modules.CEModuleLicenseDTO;
import io.harness.licensing.beans.modules.CETModuleLicenseDTO;
import io.harness.licensing.beans.modules.CFModuleLicenseDTO;
import io.harness.licensing.beans.modules.CIModuleLicenseDTO;
import io.harness.licensing.beans.modules.ChaosModuleLicenseDTO;
import io.harness.licensing.beans.modules.CodeModuleLicenseDTO;
import io.harness.licensing.beans.modules.IACMModuleLicenseDTO;
import io.harness.licensing.beans.modules.IDPModuleLicenseDTO;
import io.harness.licensing.beans.modules.ModuleLicenseDTO;
import io.harness.licensing.beans.modules.SEIModuleLicenseDTO;
import io.harness.licensing.beans.modules.SRMModuleLicenseDTO;
import io.harness.licensing.beans.modules.SSCAModuleLicenseDTO;
import io.harness.licensing.beans.modules.STOModuleLicenseDTO;
import io.harness.licensing.services.LicenseService;
import io.harness.ngsubscriptions.entity.DailyAccountUsers;
import io.harness.repositories.ngsubscriptions.spring.AccountUsersUsageRepository;
import io.harness.rule.Owner;
import io.harness.spec.server.ng.v1.ModuleLicenseUtilizationApi;

import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PL)
public class ModuleLicenseUtilizationApiImplTest extends CategoryTest {
  @Mock private LicenseService licenseService;
  @Mock private AccountUsersUsageRepository accountUsersUsageRepository;
  ModuleLicenseUtilizationApi moduleLicenseUtilizationApi;

  private static final String ACCOUNT_ID = "testAccount";

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    moduleLicenseUtilizationApi = new ModuleLicenseUtilizationApiImpl(licenseService, accountUsersUsageRepository);
  }

  private AccountLicenseDTO createAccountLicensesDTO() {
    Map<ModuleType, List<ModuleLicenseDTO>> allModuleLicenses = new HashMap<>();
    allModuleLicenses.put(
        ModuleType.CE, List.of(CEModuleLicenseDTO.builder().spendLimit(25000L).status(LicenseStatus.ACTIVE).build()));
    allModuleLicenses.put(ModuleType.CET, List.of(CETModuleLicenseDTO.builder().status(LicenseStatus.ACTIVE).build()));
    allModuleLicenses.put(ModuleType.SEI,
        List.of(SEIModuleLicenseDTO.builder().numberOfContributors(1000).status(LicenseStatus.ACTIVE).build()));
    allModuleLicenses.put(ModuleType.SRM, List.of());
    allModuleLicenses.put(ModuleType.CI,
        List.of(CIModuleLicenseDTO.builder().numberOfCommitters(200).status(LicenseStatus.ACTIVE).build()));
    allModuleLicenses.put(ModuleType.CODE,
        List.of(CodeModuleLicenseDTO.builder()
                    .numberOfDevelopers(1000)
                    .numberOfRepositories(5000)
                    .maxRepoSizeString("15GiB")
                    .maxRepoSizeInBytes(16106127360L)
                    .status(LicenseStatus.ACTIVE)
                    .build()));
    allModuleLicenses.put(ModuleType.CD,
        List.of(CDModuleLicenseDTO.builder()
                    .cdLicenseType(CDLicenseType.DEVELOPER_360)
                    .workloads(100)
                    .serviceInstances(0)
                    .status(LicenseStatus.ACTIVE)
                    .build(),
            CDModuleLicenseDTO.builder()
                .cdLicenseType(CDLicenseType.DEVELOPER_360)
                .workloads(139)
                .serviceInstances(0)
                .status(LicenseStatus.ACTIVE)
                .build()));
    allModuleLicenses.put(
        ModuleType.CHAOS, List.of(ChaosModuleLicenseDTO.builder().status(LicenseStatus.ACTIVE).build()));
    allModuleLicenses.put(ModuleType.CF,
        List.of(CFModuleLicenseDTO.builder()
                    .numberOfUsers(2)
                    .numberOfClientMAUs(5000L)
                    .status(LicenseStatus.ACTIVE)
                    .build()));
    allModuleLicenses.put(ModuleType.SSCA, List.of(SSCAModuleLicenseDTO.builder().numberOfExecutions(20000).build()));
    allModuleLicenses.put(ModuleType.IACM, List.of(IACMModuleLicenseDTO.builder().numberOfDevelopers(-1).build()));
    allModuleLicenses.put(ModuleType.IDP,
        List.of(IDPModuleLicenseDTO.builder().numberOfDevelopers(200).status(LicenseStatus.ACTIVE).build()));
    allModuleLicenses.put(ModuleType.CV, List.of(SRMModuleLicenseDTO.builder().numberOfServices(6).build()));
    allModuleLicenses.put(ModuleType.STO, List.of(STOModuleLicenseDTO.builder().numberOfDevelopers(200).build()));

    AccountLicenseDTO accountLicenseDTO = AccountLicenseDTO.builder()
                                              .accountId(ACCOUNT_ID)
                                              .allModuleLicenses(allModuleLicenses)
                                              .moduleLicenses(null)
                                              .build();
    return accountLicenseDTO;
  }

  @Test
  @Owner(developers = PRAVEEN_SOLANKI)
  @Category(UnitTests.class)
  public void validateResponseIsNotEmpty() {
    AccountLicenseDTO accountLicenses = createAccountLicensesDTO();
    Calendar calendar = Calendar.getInstance();
    Optional<DailyAccountUsers> dailyAccountUsers = Optional.empty();
    when(licenseService.getAccountLicenseV2(ACCOUNT_ID)).thenReturn(accountLicenses);
    when(accountUsersUsageRepository.findByAccountIdentifierAndModuleTypeAndYearAndMonthAndDay(eq(ACCOUNT_ID),
             any(io.harness.spec.server.ng.v1.model.ModuleType.class), eq(calendar.get(Calendar.YEAR)),
             eq(calendar.get(Calendar.MONTH)), eq(calendar.get(Calendar.DAY_OF_MONTH))))
        .thenReturn(dailyAccountUsers);

    calendar.add(Calendar.DAY_OF_YEAR, -1);
    dailyAccountUsers = Optional.of(DailyAccountUsers.builder().serviceAccounts(5).users(100).build());
    when(accountUsersUsageRepository.findByAccountIdentifierAndModuleTypeAndYearAndMonthAndDay(eq(ACCOUNT_ID),
             any(io.harness.spec.server.ng.v1.model.ModuleType.class), eq(calendar.get(Calendar.YEAR)),
             eq(calendar.get(Calendar.MONTH)), eq(calendar.get(Calendar.DAY_OF_MONTH))))
        .thenReturn(dailyAccountUsers);
    Response response = moduleLicenseUtilizationApi.getV1ModuleLicenseUtilization(ACCOUNT_ID);
    assertEquals(OK.getStatusCode(), response.getStatus());
    System.out.println(response.getEntity().toString());
  }
}
