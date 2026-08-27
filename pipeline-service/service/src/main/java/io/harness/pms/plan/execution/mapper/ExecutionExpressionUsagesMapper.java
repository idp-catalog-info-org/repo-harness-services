/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.mapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.expressions.usages.beans.ExecutionExpressionUsagesEntity;
import io.harness.engine.expressions.usages.dto.ResolvedExpressionDTO;

import java.util.List;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
public class ExecutionExpressionUsagesMapper {
  public List<ResolvedExpressionDTO> toResolvedExpressionDTO(
      List<ExecutionExpressionUsagesEntity> resolvedExpressionsResponse) {
    List<ExecutionExpressionUsagesEntity> filteredResolvedExpressions =
        resolvedExpressionsResponse.stream().filter(entity -> !entity.isError()).toList();
    return filteredResolvedExpressions.stream()
        .map(entity
            -> ResolvedExpressionDTO.builder()
                   .expression(entity.getExpression())
                   .expressionValue(entity.getExpressionValue())
                   .build())
        .toList();
  }

  public List<String> toFailedExpressions(List<ExecutionExpressionUsagesEntity> resolvedExpressionsResponse) {
    List<ExecutionExpressionUsagesEntity> filteredFailedExpression =
        resolvedExpressionsResponse.stream().filter(ExecutionExpressionUsagesEntity::isError).toList();
    return filteredFailedExpression.stream().map(ExecutionExpressionUsagesEntity::getExpression).toList();
  }
}
