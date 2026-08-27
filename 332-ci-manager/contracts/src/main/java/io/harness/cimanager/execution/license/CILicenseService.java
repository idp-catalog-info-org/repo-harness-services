/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.execution.license;

import io.harness.licensing.beans.summary.dto.LicensesWithSummaryDTO;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;

import javax.validation.constraints.NotNull;

public interface CILicenseService {
  LicensesWithSummaryDTO getLicenseSummary(
      @NotNull String accountId, @NotNull String moduleType, ExecutionPrincipalInfo principalInfo);

  default LicensesWithSummaryDTO getLicenseSummary(@NotNull String accountId, @NotNull String moduleType) {
    return getLicenseSummary(accountId, moduleType, null);
  }

  Boolean hasActiveModuleLicense(@NotNull String accountId, @NotNull String moduleType);
}
