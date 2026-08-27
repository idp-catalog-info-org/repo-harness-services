/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.execution.cdc;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.Builder;
import lombok.Value;

/**
 * BSON timestamp structure with seconds and increment.
 */
@OwnedBy(HarnessTeam.PIPELINE)
@Value
@Builder
public class BSONTimestamp {
  /** Seconds since Unix epoch */
  long t;

  /** Increment/ordinal for operations in the same second */
  int i;
}
