/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.engine.expressions.usages.service;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.engine.expressions.usages.beans.ExecutionExpressionUsagesEntity;

import java.util.List;
import java.util.Set;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
public interface ExecutionExpressionUsageService {
  void saveExpressions(List<ExecutionExpressionUsagesEntity> expressions);

  List<ExecutionExpressionUsagesEntity> getExpressions(String planExecutionId, String nodeExecutionId);
  List<ExecutionExpressionUsagesEntity> getExpressionsWithProjection(
      String planExecutionId, String nodeExecutionId, Set<String> fieldsToInclude);
}
