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
@Schema(name = "UpdateStagePriorityResponse",
    description = "Per-selector outcome of PUT /v2/stages/queue/priority. Always 200 even if every entry fails.")
public class UpdatePriorityResponse {
  @Schema(description = "Selectors whose priority was updated") List<UpdatePrioritySuccess> updated;
  @Schema(description = "Selectors that were not updated, with reason") List<UpdatePriorityFailure> failed;
}
