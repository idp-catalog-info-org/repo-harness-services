/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.dto.RetryExecutionInfoDTO;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
@Slf4j
@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ELASTIC_SEARCH})
public class PipelineExecutionSummaryEntityUtils {
  public RetryExecutionInfoDTO toRetryExecutionInfoDTO(PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity) {
    if (pipelineExecutionSummaryEntity == null) {
      return null;
    }
    return RetryExecutionInfoDTO.builder()
        .runSequence(pipelineExecutionSummaryEntity.getRunSequence())
        .startTs(pipelineExecutionSummaryEntity.getStartTs())
        .endTs(pipelineExecutionSummaryEntity.getEndTs())
        .status(pipelineExecutionSummaryEntity.getStatus())
        .planExecutionId(pipelineExecutionSummaryEntity.getPlanExecutionId())
        .build();
  }
}
