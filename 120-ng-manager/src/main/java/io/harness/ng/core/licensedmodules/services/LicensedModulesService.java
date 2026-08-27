/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.licensedmodules.services;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.spec.server.ng.v1.model.LicensedModules;

@OwnedBy(HarnessTeam.PL)
public interface LicensedModulesService {
  /**
   * Given an accountIdentifier, provides the LicensedModules details
   * @param accountIdentifier   AccountIdentifier of the harness user
   * @return                    LicensedModules
   */
  LicensedModules getLicensedModulesForAccount(String accountIdentifier);
}
