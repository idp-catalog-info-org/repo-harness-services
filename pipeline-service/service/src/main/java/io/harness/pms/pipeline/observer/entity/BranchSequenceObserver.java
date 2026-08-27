/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.observer.entity;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.events.delete.PipelineDeleteEvent;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.observer.PipelineActionObserver;
import io.harness.pms.pipeline.service.BranchSequenceService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Observer that cleans up branch sequence data when a pipeline is deleted.
 *
 * <p>This ensures that branch sequence counters are removed when their parent
 * pipeline is deleted, preventing orphaned data in the database.
 *
 * @see <a href="https://harness.atlassian.net/browse/CI-19987">CI-19987</a>
 */
@Slf4j
@Singleton
@OwnedBy(CI)
public class BranchSequenceObserver implements PipelineActionObserver {
  @Inject BranchSequenceService branchSequenceService;

  @Override
  public void onDelete(PipelineDeleteEvent pipelineDeleteEvent) {
    PipelineEntity pipelineEntity = pipelineDeleteEvent.getPipeline();

    String accountId = pipelineEntity.getAccountId();
    String orgId = pipelineEntity.getOrgIdentifier();
    String projectId = pipelineEntity.getProjectIdentifier();
    String pipelineId = pipelineEntity.getIdentifier();

    try {
      long deletedCount = branchSequenceService.deleteAllForPipeline(accountId, orgId, projectId, pipelineId);

      if (deletedCount > 0) {
        log.info("[BranchSeqId] Deleted {} branch sequence records for pipeline={} in account={}, org={}, project={}",
            deletedCount, pipelineId, accountId, orgId, projectId);
      }
    } catch (Exception e) {
      // Log but don't fail the pipeline deletion due to cleanup failure
      log.error(
          "[BranchSeqId] Failed to delete branch sequence records for pipeline={} in account={}, org={}, project={}",
          pipelineId, accountId, orgId, projectId, e);
    }
  }
}
