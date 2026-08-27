/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.notification.gitstatus;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.execution.NodeExecution;
import io.harness.pms.contracts.ambiance.Ambiance;

@OwnedBy(HarnessTeam.PIPELINE)
public interface GitStatusUpdateNotifier {
  void onNodeStatusUpdate(NodeExecution nodeExecution, Ambiance ambiance);

  void onGitOpsNodeStatusUpdate(NodeExecution nodeExecution, Ambiance ambiance);
}
