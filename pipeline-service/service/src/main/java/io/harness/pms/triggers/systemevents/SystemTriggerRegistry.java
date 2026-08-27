/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.triggers.systemevents;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Registry of all in-code {@link SystemTrigger} instances.
 * Populated via Guice Multibinder — an empty set by default.
 */
@OwnedBy(PIPELINE)
@Singleton
public class SystemTriggerRegistry {
  private final Set<SystemTrigger> triggers;

  @Inject
  public SystemTriggerRegistry(Set<SystemTrigger> triggers) {
    this.triggers = triggers;
  }

  /**
   * Returns all registered system triggers whose {@link SystemTrigger#matches(String)}
   * returns true for the given event type.
   */
  public List<SystemTrigger> getMatching(String eventType) {
    return triggers.stream().filter(t -> t.matches(eventType)).collect(Collectors.toList());
  }
}
