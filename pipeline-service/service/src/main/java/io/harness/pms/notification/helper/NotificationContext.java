/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.pms.notification.helper;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.notification.PipelineEventType;
import io.harness.notification.bean.NotificationRules;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@Data
@Builder
@AllArgsConstructor
public class NotificationContext {
  private String accountIdentifier;
  private String orgIdentifier;
  private String projectIdentifier;
  private String identifier;
  private String stepBaseFqn;
  private List<NotificationRules> notificationRulesList;
  private PipelineEventType pipelineEventType;
  private Map<String, String> notificationContent;
  private boolean notifyOnlyMe;
  private String yaml;
}
