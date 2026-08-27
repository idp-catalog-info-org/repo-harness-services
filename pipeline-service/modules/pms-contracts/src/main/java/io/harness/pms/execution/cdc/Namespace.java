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
 * MongoDB namespace containing database and collection names.
 */
@OwnedBy(HarnessTeam.PIPELINE)
@Value
@Builder
public class Namespace {
  /** MongoDB database name (e.g., 'harness-pms') */
  String db;

  /** MongoDB collection name (e.g., 'nodeExecutionsStep', 'nodeExecutionsStage', 'nodeExecutionsPipeline') */
  String coll;
}
