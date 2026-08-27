/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.notification;

import io.harness.notification.PipelineEventType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;

public interface WebhookNotificationService {
  ModuleInfo getModuleInfo(
      Ambiance ambiance, PipelineExecutionSummaryEntity executionSummaryEntity, PipelineEventType eventType);
}
