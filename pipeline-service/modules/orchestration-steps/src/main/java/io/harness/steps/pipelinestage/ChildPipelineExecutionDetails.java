/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.pipelinestage;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.pms.sdk.core.steps.executables.StepDetailsInfo;

import lombok.Builder;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Builder
@RecasterAlias("io.harness.steps.pipelinestage.ChildPipelineExecutionDetails")
public class ChildPipelineExecutionDetails implements StepDetailsInfo {
  String planExecutionId;
  String orgId;
  String projectId;
}
