/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.execution.consumers.flowgovernor;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.BRIJESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;

@OwnedBy(PIPELINE)
public class FlowGovernorStateStoreTest extends CategoryTest {
  private RedissonClient client;
  private RMap<String, FlowGovernorState> map;
  private FlowGovernorStateStore store;

  @Before
  public void setUp() {
    client = mock(RedissonClient.class);
    map = mock(RMap.class);
    when(client.<String, FlowGovernorState>getMap(FlowGovernorStateStore.MAP_NAME)).thenReturn(map);
    store = new FlowGovernorStateStore(client);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void get_missingEntry_returnsNormal() {
    when(map.get(FlowGovernorStateStore.STATE_KEY)).thenReturn(null);

    assertThat(store.get().getMode()).isEqualTo(FlowGovernorState.Mode.NORMAL);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void get_returnsStoredEntry() {
    FlowGovernorState halted = FlowGovernorState.builder()
                                   .mode(FlowGovernorState.Mode.HALTED)
                                   .version(7L)
                                   .updatedBy("oncall@harness.io")
                                   .build();
    when(map.get(FlowGovernorStateStore.STATE_KEY)).thenReturn(halted);

    assertThat(store.get()).isEqualTo(halted);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void get_redisException_returnsNormal() {
    when(map.get(FlowGovernorStateStore.STATE_KEY)).thenThrow(new RuntimeException("redis down"));

    assertThat(store.get().getMode()).isEqualTo(FlowGovernorState.Mode.NORMAL);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void put_writesStateToMap() {
    FlowGovernorState throttled =
        FlowGovernorState.builder().mode(FlowGovernorState.Mode.THROTTLED).targetRps(12).version(1L).build();

    store.put(throttled);

    verify(map).put(FlowGovernorStateStore.STATE_KEY, throttled);
  }

  // Documents the deliberately-asymmetric error contract: reads swallow Redis failures and fall
  // back to NORMAL, but writes propagate so the admin REST layer can surface a 5xx to the caller
  // rather than silently accepting a state change that never reached Redis.
  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void put_redisException_propagatesToCaller() {
    doThrow(new RuntimeException("redis down"))
        .when(map)
        .put(FlowGovernorStateStore.STATE_KEY, FlowGovernorState.normal());

    assertThatThrownBy(() -> store.put(FlowGovernorState.normal()))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("redis down");
  }
}
