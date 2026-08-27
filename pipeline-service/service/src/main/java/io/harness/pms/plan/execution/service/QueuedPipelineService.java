/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.plan.execution.beans.dto.QueuedPipelineBulkAbortResponseDTO;
import io.harness.pms.plan.execution.beans.dto.QueuedPipelineFilterDTO;
import io.harness.pms.plan.execution.beans.dto.QueuedPipelineListResponse;

import java.util.List;

@OwnedBy(PIPELINE)
public interface QueuedPipelineService {
  /**
   * Lists account-level executions. By default this returns both queued and running executions;
   * callers narrow to a specific group via the status filter on the {@link QueuedPipelineFilterDTO}.
   * Queued rows carry a global queue position; running rows do not.
   */
  QueuedPipelineListResponse listQueuedPipelines(
      String accountId, String filterIdentifier, QueuedPipelineFilterDTO filter, String searchTerm, int page, int size);

  QueuedPipelineBulkAbortResponseDTO bulkAbortQueuedPipelines(String accountId, List<String> planExecutionIds);
}
