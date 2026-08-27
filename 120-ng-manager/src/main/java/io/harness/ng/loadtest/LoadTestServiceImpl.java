/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.loadtest;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.loadtest.LoadTestStepNotifyData;
import io.harness.waiter.WaitNotifyEngine;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CHAOS)
@Singleton
@Slf4j
public class LoadTestServiceImpl implements LoadTestService {
  private final WaitNotifyEngine waitNotifyEngine;

  @Inject
  public LoadTestServiceImpl(WaitNotifyEngine waitNotifyEngine) {
    this.waitNotifyEngine = waitNotifyEngine;
  }

  @Override
  public void notifyStep(String notifyId, LoadTestStepNotifyData data) {
    waitNotifyEngine.doneWith(notifyId, data);
  }
}
