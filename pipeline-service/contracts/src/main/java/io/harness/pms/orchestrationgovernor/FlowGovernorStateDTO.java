/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.orchestrationgovernor;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Data;

@OwnedBy(PIPELINE)
@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Current pipeline-execution-events flow-governor state, as persisted in Redis.")
public class FlowGovernorStateDTO {
  @Schema(description = "Governor mode: NORMAL, HALTED, or THROTTLED.") private String mode;

  @Schema(description = "Default per-pod RPS applied in THROTTLED mode when a consumer has no override. Null in"
          + " NORMAL / HALTED modes.")
  @Nullable
  private Integer targetRps;

  @Schema(description = "Per-consumer RPS overrides keyed by FlowGovernorConsumerKeys constant. Null when no overrides"
          + " are set.")
  @Nullable
  private Map<String, Integer> targetRpsByConsumer;

  @Schema(description = "Monotonically incremented on each mutation; useful for detecting concurrent writes.")
  private long version;

  @Schema(description = "Principal that last mutated the state.") private String updatedBy;

  @Schema(description = "Epoch millis of the last mutation.") private long updatedAt;
}
