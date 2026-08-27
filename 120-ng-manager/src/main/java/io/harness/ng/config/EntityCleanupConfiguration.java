/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.config;

import io.harness.threading.ThreadPoolConfig;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@Value
@Builder
@FieldDefaults(makeFinal = false)
public class EntityCleanupConfiguration {
  ThreadPoolConfig threadPoolConfig;
  @JsonProperty(defaultValue = "1000") @Builder.Default int batchSize = 1000;
}
