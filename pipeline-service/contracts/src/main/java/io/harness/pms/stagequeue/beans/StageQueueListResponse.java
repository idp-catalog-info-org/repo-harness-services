/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.stagequeue.beans;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@OwnedBy(PIPELINE)
@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "StageQueueListResponse", description = "Paginated list of queued and running stages within the scope")
public class StageQueueListResponse {
  @Schema(description = "Stage rows for the current page, in display order") List<StageQueueRow> stages;
  @Schema(description = "Total QUEUED stages matching the filters (pre-pagination)") int totalQueued;
  @Schema(description = "Total RUNNING stages matching the filters (pre-pagination)") int totalRunning;
  @Schema(description = "0-based page index returned") int page;
  @Schema(description = "Page size returned") int limit;
  @Schema(description = "Total stages matching filters (totalQueued + totalRunning)") int totalItems;
}
