/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.engine.expressions.usages.beans;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;

import javax.validation.constraints.NotEmpty;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@FieldNameConstants(innerTypeName = "ExpressionValueMetadataKeys")
@Value
@Builder
public class ExpressionValueMetadata {
  /**
   * We are storing accountId, org,project and pipeline and runSequence to send alerts if any expression fails and check
   * if same expression passed in previous executions
   */
  @NotEmpty String accountIdentifier;
  @NotEmpty String orgIdentifier;
  @NotEmpty String projectIdentifier;
  @NotEmpty String parentUniqueId;
  @NotEmpty String pipelineIdentifier;

  // node FQN of the expression in yaml
  String nodeFQN;
  Integer nodeFQNHash;
  Integer expressionHash;
}
