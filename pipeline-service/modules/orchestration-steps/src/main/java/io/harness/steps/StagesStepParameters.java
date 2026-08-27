/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps;

import io.harness.annotation.RecasterAlias;
import io.harness.pms.sdk.core.steps.io.StepParameters;

import java.util.List;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

/**
 * Step parameters for StagesStep that supports multiple children IDs for dependency-based execution.
 * This extends the functionality of NGSectionStepParameters to handle multiple stages simultaneously.
 */
@Value
@Builder
@TypeAlias("stagesStepParameters")
@RecasterAlias("io.harness.steps.StagesStepParameters")
public class StagesStepParameters implements StepParameters {
  // For dependency-based execution - multiple children IDs
  List<String> childrenIds;

  String logMessage;
  String name;
  String id;
  Boolean skip;
}
