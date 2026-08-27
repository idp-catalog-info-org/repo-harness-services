/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.config;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TokenExpiryAlertIteratorConfig {
  private boolean enabled;
  @Builder.Default private String iteratorMode = "REDIS_BATCH";
  @Builder.Default private int threadPoolSize = 1;
  @Builder.Default private int threadPoolIntervalInSeconds = 3600;
  @Builder.Default private long targetIntervalInSeconds = 86400;
  // REDIS_BATCH specific fields
  @Builder.Default private int batchSize = 5;
  @Builder.Default private int redisLockTimeoutSeconds = 5;
}
