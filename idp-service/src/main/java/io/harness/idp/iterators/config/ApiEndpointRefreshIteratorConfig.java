/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.iterators.config;

import static io.harness.annotations.dev.HarnessTeam.IDP;

import io.harness.annotations.dev.OwnedBy;

import lombok.Builder;
import lombok.Value;

@OwnedBy(IDP)
@Value
@Builder
public class ApiEndpointRefreshIteratorConfig {
  boolean enabled;
  long targetIntervalInSeconds;
  /** Entities with lastCheckedAt newer than this window are skipped. Default 6h when unset/<=0. */
  long recencyWindowInSeconds;
  /** Max real processEntity calls per fire. Default {@code DEFAULT_MAX_PROCESS_CALLS_PER_FIRE} when unset/<=0. */
  int maxEntitiesPerFire;
  /** Mongo page size fetched per fire. Default {@code DEFAULT_PAGE_SIZE} when unset/<=0. */
  int pageSize;
  /** Number of entities processed in parallel within a fire. Default {@code DEFAULT_PARALLELISM} when unset/<=0. */
  int parallelism;
}
