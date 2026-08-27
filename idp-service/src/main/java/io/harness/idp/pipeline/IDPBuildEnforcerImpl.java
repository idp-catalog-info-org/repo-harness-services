/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.pipeline;

import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.ci.enforcement.CIBuildEnforcer;
import io.harness.ci.execution.execution.QueueExecutionUtils;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class IDPBuildEnforcerImpl implements CIBuildEnforcer {
  private QueueExecutionUtils queueExecutionUtils;
  private Boolean enableQueue;

  @Inject
  public IDPBuildEnforcerImpl(QueueExecutionUtils queueExecutionUtils, @Named("enableQueue") Boolean enableQueue) {
    this.queueExecutionUtils = queueExecutionUtils;
    this.enableQueue = enableQueue;
  }

  @Override
  public boolean shouldQueue(String accountID, Infrastructure infrastructure, String moduleType) {
    return queueExecutionUtils.shouldQueue(accountID, infrastructure, enableQueue, ModuleType.IDP.name());
  }

  @Override
  public boolean shouldRun(String accountID, Infrastructure infrastructure, String moduleType) {
    return queueExecutionUtils.shouldRun(accountID, infrastructure, ModuleType.IDP.name());
  }
}