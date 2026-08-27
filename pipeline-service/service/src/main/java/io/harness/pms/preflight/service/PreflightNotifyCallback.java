/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.preflight.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.preflight.PreFlightEntityErrorInfo;
import io.harness.pms.preflight.PreFlightStatus;
import io.harness.pms.preflight.service.intfc.PreflightService;
import io.harness.tasks.ResponseData;
import io.harness.waiter.NotifyCallbackWithErrorHandling;

import com.google.inject.Inject;
import java.util.Map;
import java.util.function.Supplier;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@Builder
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class PreflightNotifyCallback implements NotifyCallbackWithErrorHandling {
  @Inject private PreflightService preflightService;

  private String accountId;
  private String orgId;
  private String projectId;
  private String preflightId;
  @Override
  public void notifyTimeout(Map<String, ResponseData> responseMap) {
    final String errMessage = "Timed out waiting for pre flight response";
    preflightService.updateStatus(preflightId, PreFlightStatus.FAILURE,
        PreFlightEntityErrorInfo.builder().summary(errMessage).build(), PreFlightStatus.FAILURE);
    log.error(errMessage);
  }

  @Override
  public void notify(Map<String, Supplier<ResponseData>> response) {
    preflightService.schedulePreflightCheck(accountId, orgId, projectId, preflightId);
  }
}
