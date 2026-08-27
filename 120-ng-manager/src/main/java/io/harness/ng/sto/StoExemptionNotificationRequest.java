/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.sto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class StoExemptionNotificationRequest {
  String accountId;
  String orgId;
  String projectId;
  String correlationId;
  String eventIdentifier;
  String entityIdentifier;
  String issueTitle;
  String severityCode;
  String status;
  String scope;
  String requesterId;
  String approverId;
  String duration; // DEPRECATED - use expiration
  String expiration;
  String reason;
  String exemptionUrl;
  String scanTool;
}
