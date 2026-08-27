/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngsubscriptions.resource;

import io.harness.ModuleType;
import io.harness.SecondaryEntitlement;
import io.harness.exception.InvalidArgumentsException;
import io.harness.licensing.beans.modules.AccountLicenseDTO;
import io.harness.licensing.beans.modules.CFModuleLicenseDTO;
import io.harness.licensing.beans.modules.CIModuleLicenseDTO;
import io.harness.licensing.beans.modules.CodeModuleLicenseDTO;
import io.harness.licensing.beans.modules.HARModuleLicenseDTO;
import io.harness.licensing.beans.modules.IDPModuleLicenseDTO;
import io.harness.licensing.beans.modules.ModuleLicenseDTO;
import io.harness.licensing.helpers.ModuleLicenseHelper;
import io.harness.licensing.services.LicenseService;
import io.harness.ngsubscriptions.entity.DailyAccountUsers;
import io.harness.repositories.ngsubscriptions.spring.AccountUsersUsageRepository;
import io.harness.spec.server.ng.v1.ModuleLicenseUtilizationApi;
import io.harness.spec.server.ng.v1.model.ModuleUtilizationDTO;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class ModuleLicenseUtilizationApiImpl implements ModuleLicenseUtilizationApi {
  @Inject LicenseService licenseService;
  @Inject AccountUsersUsageRepository accountUsersUsageRepository;
  final String DEVELOPERS = "Developers";
  final String COMMITTERS = "Committers";
  final String NUMBER_OF_DEVELOPERS = "Developers";
  final String STORAGE_SPACE = "storageSpace";

  final String MONTHLY_ACTIVE_USERS = "Monthly Active Users";

  final String NA = "NA";
  final Map<SecondaryEntitlement, String> secondaryEntitlementStringEnumMap =
      Map.of(SecondaryEntitlement.NUMBER_OF_DEVELOPERS, NUMBER_OF_DEVELOPERS, SecondaryEntitlement.NUMBER_OF_COMMITTERS,
          COMMITTERS, SecondaryEntitlement.NUMBER_OF_CLIENT_MAUS, MONTHLY_ACTIVE_USERS);
  private final Map<io.harness.ModuleType, io.harness.spec.server.ng.v1.model.ModuleType>
      nGModuleTypeModuleTypeEnumMap =
          Map.of(io.harness.ModuleType.IDP, io.harness.spec.server.ng.v1.model.ModuleType.IDP,
              io.harness.ModuleType.CODE, io.harness.spec.server.ng.v1.model.ModuleType.CODE, io.harness.ModuleType.CF,
              io.harness.spec.server.ng.v1.model.ModuleType.CF, io.harness.ModuleType.CI,
              io.harness.spec.server.ng.v1.model.ModuleType.CI);
  @Override
  public Response getV1ModuleLicenseUtilization(String harnessAccount) {
    if (harnessAccount.isEmpty()) {
      throw new InvalidArgumentsException("Missing account identifier");
    }

    AccountLicenseDTO accountLicenses = licenseService.getAccountLicenseV2(harnessAccount);
    Map<ModuleType, List<ModuleLicenseDTO>> moduleTypeListMap = accountLicenses.getAllModuleLicenses();

    List<ModuleUtilizationDTO> result = new ArrayList<>();
    Set<ModuleType> supportedModules =
        new HashSet<>(Arrays.asList(ModuleType.CI, ModuleType.CF, ModuleType.IDP, ModuleType.CODE));

    for (ModuleType moduleType : moduleTypeListMap.keySet()) {
      if (!supportedModules.contains(moduleType)) {
        continue;
      }

      List<ModuleLicenseDTO> moduleLicenseDTOList = moduleTypeListMap.get(moduleType);
      ModuleUtilizationDTO dto = new ModuleUtilizationDTO();
      ModuleLicenseDTO moduleLicenseDTO = ModuleLicenseHelper.getBaseLicense(moduleLicenseDTOList);

      // Add On license or expired license
      if (moduleLicenseDTO == null || moduleLicenseDTO.getIsAddOn()) {
        continue;
      }
      Calendar calendar = Calendar.getInstance();
      Optional<DailyAccountUsers> dailyAccountUser =
          accountUsersUsageRepository.findByAccountIdentifierAndModuleTypeAndYearAndMonthAndDay(harnessAccount,
              nGModuleTypeModuleTypeEnumMap.get(moduleType), calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH),
              calendar.get(Calendar.DAY_OF_MONTH));

      long totalModuleUtilization = 0;
      if (dailyAccountUser.isPresent()) {
        totalModuleUtilization = dailyAccountUser.get().getServiceAccounts() + dailyAccountUser.get().getUsers();
      } else {
        // try previous day just in case
        calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, -1);
        dailyAccountUser = accountUsersUsageRepository.findByAccountIdentifierAndModuleTypeAndYearAndMonthAndDay(
            harnessAccount, nGModuleTypeModuleTypeEnumMap.get(moduleType), calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
        if (dailyAccountUser.isPresent()) {
          totalModuleUtilization = dailyAccountUser.get().getServiceAccounts() + dailyAccountUser.get().getUsers();
        }
      }

      if (moduleType.equals(ModuleType.CI)) {
        CIModuleLicenseDTO ciModuleLicenseDTO = (CIModuleLicenseDTO) moduleLicenseDTO;
        // Not a developer license hence setting unit as NA
        if (moduleLicenseDTO.getDeveloperLicenseCount() == null) {
          dto.setLicenseUnit(NA);
          dto.setLicenses(0L);
          dto.setUtilization(0L);
        } else {
          dto.setLicenseUnit(DEVELOPERS);
          dto.setLicenses(ciModuleLicenseDTO.getDeveloperLicenseCount().longValue());
          dto.setUtilization(totalModuleUtilization);
        }
        // CI doesn't track code committers and just tracks secondary entitlement
        dto.setEntitlementUnit(DEVELOPERS);
        dto.setModuleType(ModuleType.CI.name());
        dto.setEntitlements(ciModuleLicenseDTO.getNumberOfCommitters().longValue());

      } else if (moduleType.equals(ModuleType.CF)) {
        CFModuleLicenseDTO cfModuleLicenseDTO = (CFModuleLicenseDTO) moduleLicenseDTO;
        if (moduleLicenseDTO.getDeveloperLicenseCount() == null) {
          dto.setLicenseUnit(NA);
          dto.setLicenses(0L);
          dto.setUtilization(0L);
        } else {
          dto.setLicenseUnit(DEVELOPERS);
          dto.setLicenses(cfModuleLicenseDTO.getDeveloperLicenseCount().longValue());
          dto.setUtilization(totalModuleUtilization);
        }

        dto.setEntitlementUnit(secondaryEntitlementStringEnumMap.get(SecondaryEntitlement.NUMBER_OF_CLIENT_MAUS));
        dto.setModuleType(ModuleType.CF.name());
        dto.setEntitlements(cfModuleLicenseDTO.getNumberOfClientMAUs());

      } else if (moduleType.equals(ModuleType.IDP)) {
        IDPModuleLicenseDTO idpModuleLicenseDTO = (IDPModuleLicenseDTO) moduleLicenseDTO;
        if (moduleLicenseDTO.getDeveloperLicenseCount() == null) {
          dto.setLicenseUnit(NA);
          dto.setLicenses(0L);
          dto.setUtilization(0L);
        } else {
          dto.setLicenseUnit(DEVELOPERS);
          dto.setLicenses(idpModuleLicenseDTO.getDeveloperLicenseCount().longValue());
          dto.setUtilization(totalModuleUtilization);
        }

        dto.setEntitlementUnit(secondaryEntitlementStringEnumMap.get(SecondaryEntitlement.NUMBER_OF_DEVELOPERS));
        dto.setModuleType(ModuleType.IDP.name());
        dto.setEntitlements(idpModuleLicenseDTO.getNumberOfDevelopers().longValue());

      } else if (moduleType.equals(ModuleType.CODE)) {
        CodeModuleLicenseDTO codeModuleLicenseDTO = (CodeModuleLicenseDTO) moduleLicenseDTO;

        if (moduleLicenseDTO.getDeveloperLicenseCount() == null) {
          dto.setLicenseUnit(NA);
          dto.setLicenses(0L);
          dto.setUtilization(0L);
        } else {
          dto.setLicenseUnit(DEVELOPERS);
          dto.setLicenses(codeModuleLicenseDTO.getDeveloperLicenseCount().longValue());
          dto.setUtilization(totalModuleUtilization);
        }

        dto.setEntitlementUnit(secondaryEntitlementStringEnumMap.get(SecondaryEntitlement.NUMBER_OF_DEVELOPERS));
        dto.setModuleType(ModuleType.CODE.name());
        dto.setEntitlements(codeModuleLicenseDTO.getNumberOfDevelopers().longValue());
      } else if (ModuleType.HAR.equals(moduleType)) {
        HARModuleLicenseDTO harModuleLicenseDTO = (HARModuleLicenseDTO) moduleLicenseDTO;

        if (moduleLicenseDTO.getDeveloperLicenseCount() == null) {
          dto.setLicenseUnit(NA);
          dto.setLicenses(0L);
          dto.setUtilization(0L);
        } else {
          dto.setLicenseUnit(DEVELOPERS);
          dto.setLicenses(harModuleLicenseDTO.getDeveloperLicenseCount().longValue());
          dto.setUtilization(totalModuleUtilization);
        }

        dto.setEntitlementUnit(STORAGE_SPACE);
        dto.setModuleType(ModuleType.HAR.name());
        dto.setEntitlements(harModuleLicenseDTO.getMaxStorageSizeInBytes());
      }
      result.add(dto);
    }

    return Response.status(Response.Status.OK).entity(result).build();
  }
}
