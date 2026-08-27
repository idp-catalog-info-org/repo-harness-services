/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.orchestrationgovernor;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.exception.WingsException.USER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.execution.consumers.flowgovernor.FlowGovernorConsumerKeys;
import io.harness.engine.execution.consumers.flowgovernor.FlowGovernorState;
import io.harness.engine.execution.consumers.flowgovernor.FlowGovernorState.Mode;
import io.harness.engine.execution.consumers.flowgovernor.FlowGovernorStateStore;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.security.dto.PrincipalType;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * SERVICE-principal-gated admin endpoints that mutate the Redis-backed {@link FlowGovernorState}.
 * Every mutation bumps {@code version}, stamps {@code updatedBy} + {@code updatedAt}, and returns
 * the resulting state so the caller can verify the write without a separate GET.
 */
@OwnedBy(PIPELINE)
@PipelineServiceAuth
@Slf4j
public class OrchestrationGovernorResourceImpl implements OrchestrationGovernorResource {
  private static final int MIN_RPS = 1;
  private static final int MAX_RPS = 10_000;

  /**
   * Allow-listed consumer keys for the {@code consumer} query param. Mirrors the constants in
   * {@link FlowGovernorConsumerKeys}; kept as an explicit set so REST validation fails fast on
   * typos rather than accumulating stale entries in Redis after a consumer is renamed or retired.
   */
  private static final Set<String> ALLOWED_CONSUMER_KEYS =
      ImmutableSet.of(FlowGovernorConsumerKeys.INITIATE_NODE, FlowGovernorConsumerKeys.SDK_STEP_RESPONSE);

  private final FlowGovernorStateStore stateStore;

  @Inject
  public OrchestrationGovernorResourceImpl(FlowGovernorStateStore stateStore) {
    this.stateStore = stateStore;
  }

  @Override
  public ResponseDTO<FlowGovernorStateDTO> halt() {
    String principal = requireServicePrincipal();
    FlowGovernorState existing = stateStore.get();
    FlowGovernorState next = FlowGovernorState.builder()
                                 .mode(Mode.HALTED)
                                 .version(existing.getVersion() + 1)
                                 .updatedBy(principal)
                                 .updatedAt(System.currentTimeMillis())
                                 .build();
    stateStore.put(next);
    log.info("Flow governor: HALT by {} (version {} -> {})", principal, existing.getVersion(), next.getVersion());
    return ResponseDTO.newResponse(toDto(next));
  }

  @Override
  public ResponseDTO<FlowGovernorStateDTO> resumeThrottled(Integer rps, String consumer) {
    if (rps == null) {
      throw new InvalidRequestException("Query parameter 'rps' is required for throttled resume.");
    }
    if (rps < MIN_RPS || rps > MAX_RPS) {
      throw new InvalidRequestException(
          String.format("rps must be between %d and %d (got %d).", MIN_RPS, MAX_RPS, rps));
    }
    if (consumer != null && !ALLOWED_CONSUMER_KEYS.contains(consumer)) {
      throw new InvalidRequestException(
          String.format("Unknown consumer key '%s'. Allowed: %s", consumer, ALLOWED_CONSUMER_KEYS));
    }
    String principal = requireServicePrincipal();
    FlowGovernorState existing = stateStore.get();

    Integer defaultRps = existing.getTargetRps();
    Map<String, Integer> overrides =
        existing.getTargetRpsByConsumer() == null ? null : new HashMap<>(existing.getTargetRpsByConsumer());
    if (consumer == null) {
      defaultRps = rps;
    } else {
      if (overrides == null) {
        overrides = new HashMap<>();
      }
      overrides.put(consumer, rps);
    }

    FlowGovernorState next = FlowGovernorState.builder()
                                 .mode(Mode.THROTTLED)
                                 .targetRps(defaultRps)
                                 .targetRpsByConsumer(overrides)
                                 .version(existing.getVersion() + 1)
                                 .updatedBy(principal)
                                 .updatedAt(System.currentTimeMillis())
                                 .build();
    stateStore.put(next);
    log.info("Flow governor: THROTTLED rps={} consumer={} by {} (version {} -> {})", rps,
        consumer == null ? "<default>" : consumer, principal, existing.getVersion(), next.getVersion());
    return ResponseDTO.newResponse(toDto(next));
  }

  @Override
  public ResponseDTO<FlowGovernorStateDTO> resumeFull() {
    String principal = requireServicePrincipal();
    FlowGovernorState existing = stateStore.get();
    FlowGovernorState next = FlowGovernorState.builder()
                                 .mode(Mode.NORMAL)
                                 .version(existing.getVersion() + 1)
                                 .updatedBy(principal)
                                 .updatedAt(System.currentTimeMillis())
                                 .build();
    stateStore.put(next);
    log.info("Flow governor: NORMAL by {} (version {} -> {})", principal, existing.getVersion(), next.getVersion());
    return ResponseDTO.newResponse(toDto(next));
  }

  @Override
  public ResponseDTO<FlowGovernorStateDTO> getState() {
    requireServicePrincipal();
    return ResponseDTO.newResponse(toDto(stateStore.get()));
  }

  /**
   * The caller today is {@code AdminAccountResource} in 400-rest, which forwards the admin-portal
   * action over a {@code ClientMode.PRIVILEGED} client — that client mints the
   * {@link PrincipalType#SERVICE} principal this gate requires.
   */
  private static String requireServicePrincipal() {
    Principal principal = SecurityContextBuilder.getPrincipal();
    if (principal == null || principal.getType() != PrincipalType.SERVICE) {
      throw new AccessDeniedException("[ORCHESTRATION GOVERNOR]: SERVICE principal required.", USER);
    }
    return principal.getName();
  }

  private static FlowGovernorStateDTO toDto(FlowGovernorState state) {
    return FlowGovernorStateDTO.builder()
        .mode(state.getMode() == null ? null : state.getMode().name())
        .targetRps(state.getTargetRps())
        .targetRpsByConsumer(state.getTargetRpsByConsumer())
        .version(state.getVersion())
        .updatedBy(state.getUpdatedBy())
        .updatedAt(state.getUpdatedAt())
        .build();
  }
}
