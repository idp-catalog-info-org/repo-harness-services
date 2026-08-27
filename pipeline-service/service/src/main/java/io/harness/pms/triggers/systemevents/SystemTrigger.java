/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.triggers.systemevents;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;

/**
 * A system trigger is a trigger defined in code (not persisted in MongoDB) that
 * fires when a matching platform event arrives on the TRIGGER_EXECUTION_EVENTS_STREAM.
 * Register implementations via Guice Multibinder.
 */
@OwnedBy(PIPELINE)
public interface SystemTrigger {
  /**
   * Returns true if this trigger should fire for the given event type string
   * (e.g. "harness.pipeline.completed", "harness.deployment.failed").
   */
  boolean matches(String eventType);

  /**
   * Execute the trigger action for the given event.
   * Called after {@link #matches(String)} returns true.
   *
   * @param eventType     the platform event type string
   * @param accountId     account that generated the event
   * @param orgId         org identifier of the originating scope
   * @param projectId     project identifier of the originating scope
   * @param correlationId correlation / execution ID from the originating event
   */
  void execute(String eventType, String accountId, String orgId, String projectId, String correlationId);
}
