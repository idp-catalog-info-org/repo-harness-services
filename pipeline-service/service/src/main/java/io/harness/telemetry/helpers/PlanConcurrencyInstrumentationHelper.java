/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.telemetry.helpers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.google.inject.Singleton;
import java.util.HashMap;
import lombok.extern.slf4j.Slf4j;

/**
 * Segment/Amplitude telemetry for the per-project pipeline execution concurrency feature (PIPE-35674).
 * Extends {@link InstrumentationHelper} so the event flows through the shared {@code TelemetryReporter}
 * as a {@code track} event; every send is swallowed by the base {@code sendEvent} so telemetry never
 * breaks the caller's path.
 *
 * <p>Emits {@link #sendConcurrencyConfigChangeEvent} when an account/project edits one of the
 * concurrency settings (mode / default / per-project override) — a pure feature-usage signal fired
 * from the settings-change listener, so it costs nothing when nobody is changing settings.
 */
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class PlanConcurrencyInstrumentationHelper extends InstrumentationHelper {
  public static final String CONCURRENCY_CONFIG_CHANGED_EVENT = "pipeline_concurrency_config_changed";

  // Property keys (kept local to this feature rather than polluting the shared InstrumentationConstants).
  public static final String SETTING_IDENTIFIER = "setting_identifier";
  public static final String NEW_VALUE = "new_value";
  public static final String SCOPE = "scope";

  /**
   * Fired when a concurrency setting changes. The cross-service settings-change event only carries the
   * new value (not the old one), so we report the identifier + new value + scope.
   */
  public void sendConcurrencyConfigChangeEvent(
      String accountId, String orgId, String projectId, String settingIdentifier, String newValue) {
    HashMap<String, Object> properties = new HashMap<>();
    properties.put(InstrumentationConstants.ACCOUNT, accountId);
    properties.put(InstrumentationConstants.ORG, orgId);
    properties.put(InstrumentationConstants.PROJECT, projectId);
    properties.put(SETTING_IDENTIFIER, settingIdentifier);
    properties.put(NEW_VALUE, newValue);
    properties.put(SCOPE, scopeOf(orgId, projectId));
    sendEvent(CONCURRENCY_CONFIG_CHANGED_EVENT, accountId, properties);
  }

  private static String scopeOf(String orgId, String projectId) {
    if (projectId != null && !projectId.isEmpty()) {
      return "project";
    }
    if (orgId != null && !orgId.isEmpty()) {
      return "org";
    }
    return "account";
  }
}
