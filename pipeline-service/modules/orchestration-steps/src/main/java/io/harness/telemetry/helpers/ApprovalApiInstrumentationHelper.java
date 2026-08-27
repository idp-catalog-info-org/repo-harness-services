/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.telemetry.helpers;

import static io.harness.telemetry.helpers.InstrumentationConstants.ACCOUNT;
import static io.harness.telemetry.helpers.InstrumentationConstants.FAILURE_TYPE;
import static io.harness.telemetry.helpers.InstrumentationConstants.ORG;
import static io.harness.telemetry.helpers.InstrumentationConstants.PIPELINE_EXECUTION_ID;
import static io.harness.telemetry.helpers.InstrumentationConstants.PROJECT;
import static io.harness.telemetry.helpers.InstrumentationConstants.STATUS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.google.inject.Singleton;
import java.util.HashMap;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CDC)
public class ApprovalApiInstrumentationHelper extends InstrumentationHelper {
  public static final String FAILURE = "failed";
  public static final String SUCCESS = "success";
  public static final String APPROVAL_API = "approval_api";
  public static final String MULTIPLE_APPROVALS_FOUND = "multiple_approvals_found";
  public static final String NO_APPROVALS_FOUND = "no_approvals_found";
  public static final String EXECUTION_ID_NOT_FOUND = "execution_id_not_found";

  public void sendApprovalApiEvent(
      String accountId, String orgId, String projectId, String executionId, String status, String failureType) {
    HashMap<String, Object> eventPropertiesMap = new HashMap<>();
    eventPropertiesMap.put(ACCOUNT, accountId);
    eventPropertiesMap.put(ORG, orgId);
    eventPropertiesMap.put(PROJECT, projectId);
    eventPropertiesMap.put(STATUS, status);
    eventPropertiesMap.put(PIPELINE_EXECUTION_ID, executionId);
    eventPropertiesMap.put(FAILURE_TYPE, failureType);
    sendEvent(APPROVAL_API, accountId, eventPropertiesMap);
  }
}
