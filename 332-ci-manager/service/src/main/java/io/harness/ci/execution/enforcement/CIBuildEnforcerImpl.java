/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.enforcement;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.ci.execution.execution.QueueExecutionUtils;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CI)
@Slf4j
public class CIBuildEnforcerImpl implements CIBuildEnforcer {
  @Inject(optional = true) private QueueExecutionUtils queueExecutionUtils;

  @Override
  public boolean shouldQueue(
      String accountID, Infrastructure infrastructure, String moduleType, ExecutionPrincipalInfo principalInfo) {
    /* Cyclic dependency is coming on reading the enable queue config looks this was default
    behaviour for CI for same reason*/
    return queueExecutionUtils.shouldQueue(accountID, infrastructure, true, moduleType, principalInfo);
  }

  @Override
  public boolean shouldRun(
      String accountID, Infrastructure infrastructure, String moduleType, ExecutionPrincipalInfo principalInfo) {
    return queueExecutionUtils.shouldRun(accountID, infrastructure, moduleType, principalInfo);
  }
}
