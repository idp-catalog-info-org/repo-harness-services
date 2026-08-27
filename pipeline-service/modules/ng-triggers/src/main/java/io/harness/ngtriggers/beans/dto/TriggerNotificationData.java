/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.dto;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.HeaderConfig;
import io.harness.ngtriggers.beans.source.NGTriggerType;
import io.harness.pms.contracts.triggers.TriggerPayload;

import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(PIPELINE)
public class TriggerNotificationData {
  String triggerFailureNotificationEntityUuid;
  String accountIdentifier;
  String orgIdentifier;
  String projectIdentifier;
  String parentUniqueId;
  String triggerIdentifier;
  String triggerName;
  String pipelineIdentifier;
  String pipelineName;
  NGTriggerType ngTriggerType;
  String triggerSubType;
  TriggerPayload triggerPayload;
  List<HeaderConfig> headerConfigs;
  String payload;
  String errorMessage;
  String eventCorrelationId;
  Long triggerEventCreatedAt;
}
