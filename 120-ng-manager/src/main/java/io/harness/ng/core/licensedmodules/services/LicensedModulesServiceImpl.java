/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.licensedmodules.services;

import io.harness.licensing.beans.modules.ModuleLicenseCondensedDTO;
import io.harness.licensing.services.LicenseService;
import io.harness.spec.server.ng.v1.model.LicensedModules;
import io.harness.spec.server.ng.v1.model.ModuleType;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
public class LicensedModulesServiceImpl implements LicensedModulesService {
  @Inject private LicenseService licenseService;

  @Override
  public LicensedModules getLicensedModulesForAccount(final String accountIdentifier) {
    final List<ModuleLicenseCondensedDTO> licensedModulesDto =
        licenseService.getLicensedModulesForAccount(accountIdentifier);
    final List<ModuleType> moduleTypes = new ArrayList<>();

    for (ModuleLicenseCondensedDTO dto : licensedModulesDto) {
      String moduleCode = dto.getModuleType().name();
      ModuleType moduleType = ModuleType.fromValue(moduleCode);
      if (moduleType != null) {
        moduleTypes.add(moduleType);
      }
    }
    final LicensedModules licensedModules = new LicensedModules();
    licensedModules.setLicensedModules(moduleTypes);
    return licensedModules;
  }
}
