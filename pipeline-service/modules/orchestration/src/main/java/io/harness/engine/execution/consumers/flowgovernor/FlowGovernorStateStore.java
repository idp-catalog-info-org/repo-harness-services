/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.engine.execution.consumers.flowgovernor;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;

/**
 * Persists the shared flow-governor mode in Redis. All throttled orchestration consumers share the
 * single key {@link #STATE_KEY}. Backed by a plain {@link RMap} because the entry is intentionally
 * long-lived — an operator's HALT / THROTTLED decision must survive pod restarts and rolling
 * deploys until a subsequent REST call overwrites it.
 */
@OwnedBy(PIPELINE)
@Singleton
@Slf4j
public class FlowGovernorStateStore {
  public static final String MAP_NAME = "pms-flow-governor-state";
  public static final String STATE_KEY = "orchestration";

  private final RMap<String, FlowGovernorState> map;

  @Inject
  public FlowGovernorStateStore(@Named("cacheRedissonClient") RedissonClient redissonClient) {
    this.map = redissonClient.getMap(MAP_NAME);
  }

  public FlowGovernorState get() {
    try {
      FlowGovernorState state = map.get(STATE_KEY);
      return state == null ? FlowGovernorState.normal() : state;
    } catch (Exception ex) {
      log.warn("Failed to read flow-governor state from Redis; defaulting to NORMAL.", ex);
      return FlowGovernorState.normal();
    }
  }

  public void put(FlowGovernorState state) {
    map.put(STATE_KEY, state);
  }
}
