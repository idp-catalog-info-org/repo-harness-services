/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.monitoring;

import static io.harness.NGCommonEntityConstants.MONGODB_ID;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.ProjectionOperation;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Value
@Builder
@AllArgsConstructor
@FieldNameConstants(innerTypeName = "ExecutionCountWithModuleAndStepTypeResultKeys")
public class ExecutionCountWithModuleAndStepTypeResult {
  private String module;
  private String type;
  private Integer count;

  public static ProjectionOperation getProjection() {
    return Aggregation.project()
        .andExpression(MONGODB_ID + "." + ExecutionCountWithModuleAndStepTypeResultKeys.module)
        .as(ExecutionCountWithModuleAndStepTypeResultKeys.module)
        .andExpression(MONGODB_ID + "." + ExecutionCountWithModuleAndStepTypeResultKeys.type)
        .as(ExecutionCountWithModuleAndStepTypeResultKeys.type)
        .andExpression(ExecutionCountWithModuleAndStepTypeResultKeys.count)
        .as(ExecutionCountWithModuleAndStepTypeResultKeys.count);
  }
}
