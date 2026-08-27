/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.observers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.observers.beans.DynamicOrchestrationStartInfo;
import io.harness.engine.observers.beans.OrchestrationQueueInfo;
import io.harness.engine.observers.beans.OrchestrationStartInfo;
import io.harness.pms.execution.utils.AmbianceUtils;

@OwnedBy(HarnessTeam.PIPELINE)
public interface OrchestrationStartObserver {
  default void onStartWrapper(OrchestrationStartInfo orchestrationStartInfo) {
    // If workflow type is set, we don't want to call the onStart method as observers are registered mainly for pipeline
    if (shouldIgnore(orchestrationStartInfo)) {
      return;
    }
    onStart(orchestrationStartInfo);
  }

  void onStart(OrchestrationStartInfo orchestrationStartInfo);
  void onQueue(OrchestrationQueueInfo orchestrationQueueInfo);

  default boolean shouldIgnore(OrchestrationStartInfo orchestrationStartInfo) {
    return orchestrationStartInfo != null && AmbianceUtils.hasWorkflowType(orchestrationStartInfo.getAmbiance());
  }

  // This should be called when some new nodes are being added dynamically during the execution.
  default void onDynamicStart(DynamicOrchestrationStartInfo orchestrationStartInfo) {
    // no-op
  }
}
