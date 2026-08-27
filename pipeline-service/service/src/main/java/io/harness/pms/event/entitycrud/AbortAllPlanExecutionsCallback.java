/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event.entitycrud;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.tasks.ResponseData;
import io.harness.waiter.NotifyCallback;

import com.google.inject.Inject;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
@OwnedBy(PIPELINE)
@Slf4j
public class AbortAllPlanExecutionsCallback implements NotifyCallback {
  @Inject private PipelineEntityCRUDStreamListener pipelineEntityCRUDStreamListener;
  Set<String> planExecutionsToDelete;
  boolean retainPipelineExecutionDetailsAfterDelete;
  String accountId;
  @Builder
  public AbortAllPlanExecutionsCallback(PipelineEntityCRUDStreamListener pipelineEntityCRUDStreamListener,
      Set<String> planExecutionsToDelete, boolean retainPipelineExecutionDetailsAfterDelete, String accountId) {
    this.pipelineEntityCRUDStreamListener = pipelineEntityCRUDStreamListener;
    this.planExecutionsToDelete = planExecutionsToDelete;
    this.retainPipelineExecutionDetailsAfterDelete = retainPipelineExecutionDetailsAfterDelete;
    this.accountId = accountId;
  }

  @Override
  public void notifyTimeout(Map<String, ResponseData> responseMap) {
    pipelineEntityCRUDStreamListener.deleteAbortedPipelineExecutions(
        planExecutionsToDelete, retainPipelineExecutionDetailsAfterDelete, accountId);
  }
}
