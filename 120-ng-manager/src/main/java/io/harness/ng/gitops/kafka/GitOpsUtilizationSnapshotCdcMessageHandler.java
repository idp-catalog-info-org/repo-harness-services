/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.gitops.kafka;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventHandler.DebeziumAbstractRedisEventHandler;
import io.harness.ff.FeatureFlagService;
import io.harness.ng.gitops.changestreams.GitOpsUtilizationSnapshotRedisEventHandler;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * CDC Kafka message handler for GitOps utilization snapshot events.
 *
 * <p>Delegates to {@link GitOpsUtilizationSnapshotRedisEventHandler} which handles
 * CREATE/UPDATE/DELETE operations and writes to the {@code gitops_instance_stats} table.
 *
 * @see AbstractGitopsCdcMessageHandler for common CDC handling logic
 */
@OwnedBy(GITOPS)
@Singleton
@Slf4j
public class GitOpsUtilizationSnapshotCdcMessageHandler extends AbstractGitopsCdcMessageHandler {
  private final GitOpsUtilizationSnapshotRedisEventHandler eventHandler;

  @Inject
  public GitOpsUtilizationSnapshotCdcMessageHandler(
      GitOpsUtilizationSnapshotRedisEventHandler eventHandler, FeatureFlagService featureFlagService) {
    super(featureFlagService);
    this.eventHandler = eventHandler;
  }

  @Override
  protected DebeziumAbstractRedisEventHandler getEventHandler() {
    return eventHandler;
  }

  @Override
  protected String getTopicName() {
    return "utilization_snapshot";
  }
}