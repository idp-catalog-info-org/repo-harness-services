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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.concurrent.Executor;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class FlowGovernorStateCacheTest extends CategoryTest {
  private static final Executor DIRECT_EXECUTOR = Runnable::run;

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void getState_loadsFromStore() {
    FlowGovernorStateStore store = mock(FlowGovernorStateStore.class);
    FlowGovernorState throttled =
        FlowGovernorState.builder().mode(FlowGovernorState.Mode.THROTTLED).targetRps(10).version(2L).build();
    when(store.get()).thenReturn(throttled);
    FlowGovernorStateCache cache = new FlowGovernorStateCache(store, DIRECT_EXECUTOR);

    FlowGovernorState state = cache.getState();

    assertThat(state.getMode()).isEqualTo(FlowGovernorState.Mode.THROTTLED);
    assertThat(state.getTargetRps()).isEqualTo(10);
    assertThat(state.getVersion()).isEqualTo(2L);
    verify(store, times(1)).get();
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void getState_repeatedReads_hitStoreOnce() {
    FlowGovernorStateStore store = mock(FlowGovernorStateStore.class);
    when(store.get()).thenReturn(FlowGovernorState.normal());
    FlowGovernorStateCache cache = new FlowGovernorStateCache(store, DIRECT_EXECUTOR);

    cache.getState();
    cache.getState();
    cache.getState();

    verify(store, times(1)).get();
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void getState_storeThrows_returnsNormal() {
    FlowGovernorStateStore store = mock(FlowGovernorStateStore.class);
    when(store.get()).thenThrow(new RuntimeException("redis down"));
    FlowGovernorStateCache cache = new FlowGovernorStateCache(store, DIRECT_EXECUTOR);

    FlowGovernorState state = cache.getState();

    assertThat(state.getMode()).isEqualTo(FlowGovernorState.Mode.NORMAL);
  }
}
