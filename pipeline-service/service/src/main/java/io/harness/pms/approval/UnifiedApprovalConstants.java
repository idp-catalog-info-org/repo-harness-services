/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.approval;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

@OwnedBy(CDP)
public class UnifiedApprovalConstants {
  // ServiceNow const
  public static final String SERVICENOW_URL_PLUGIN_ENV = "PLUGIN_SERVICENOW_URL";
  public static final String SERVICENOW_TICKET_PLUGIN_ENV = "TICKET";

  // Jira constants
  public static final String JIRA_URL_PLUGIN_ENV = "PLUGIN_JIRA_URL";
  public static final String JIRA_TICKET_PLUGIN_ENV = "ISSUE";

  // Generic Plugin const
  public static final String PLUGIN_EXECUTION_STATUS_ENV = "PLUGIN_EXECUTION_STATUS";
  public static final String PLUGIN_EXECUTION_ERROR_ENV = "PLUGIN_EXECUTION_ERROR";
  public static final String PLUGIN_EXECUTION_ERROR_NO_DETAILS_MSG = "No error details available";
  public static final String PLUGIN_EXECUTION_FAILURE_TYPE_ENV = "PLUGIN_EXECUTION_FAILURE_TYPE";
  public static final String PLUGIN_EXECUTION_FAILURE_TYPE_UNKNOWN = "UNKNOWN";
  public static final String PLUGIN_EXECUTION_STATUS_SUCCESS = "SUCCESS";
  public static final String PLUGIN_EXECUTION_STATUS_FAILURE = "FAILURE";
}
