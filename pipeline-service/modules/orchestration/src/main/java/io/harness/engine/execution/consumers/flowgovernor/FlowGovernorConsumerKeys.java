/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.engine.execution.consumers.flowgovernor;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

/**
 * Stable consumer identifiers for the engine-side orchestration Kafka consumers governed by the
 * flow governor. Used as keys in {@link FlowGovernorState#getTargetRpsByConsumer()} and as the
 * {@code topic} metric label. Kept as constants so operator input, Redis payloads, and consumer
 * wiring never drift.
 *
 * <p>SDK-side consumers are intentionally not governed: halting the engine stops writes to the
 * SDK topics upstream, so SDK-side throttling is redundant.
 *
 * <p>If an operator sets THROTTLED with a default RPS but no per-consumer override for a given
 * key, that consumer uses the default (see {@link FlowGovernorState#resolveRpsFor(String)}).
 */
@OwnedBy(PIPELINE)
@UtilityClass
public class FlowGovernorConsumerKeys {
  public static final String INITIATE_NODE = "initiateNode";
  public static final String SDK_STEP_RESPONSE = "sdkStepResponse";
}
