/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

/**
 * Provider interface for log service base URL.
 * Implementations should return the base URL for the log service
 * that can be used to construct log download URLs.
 */
@OwnedBy(HarnessTeam.PIPELINE)
public interface LogServiceUrlProvider {
  /**
   * Gets the base URL for the log service.
   * @param accountId The account identifier
   * @return The log service base URL (e.g., "http://localhost:8079" or "https://app.harness.io/gateway/log-service")
   */
  String getLogServiceBaseUrl(String accountId);
}
