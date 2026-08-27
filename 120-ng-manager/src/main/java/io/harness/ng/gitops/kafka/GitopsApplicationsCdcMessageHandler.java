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
import io.harness.ng.gitops.changestreams.GitopsApplicationsRedisEventHandler;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * CDC Kafka message handler for GitOps applications events.
 *
 * <p>Delegates to {@link GitopsApplicationsRedisEventHandler} which handles
 * CREATE/UPDATE/DELETE operations and writes to the {@code gitops_app_info} table.
 *
 * <p><strong>Note:</strong> Unlike other GitOps CDC handlers, this uses JSON serialization
 * instead of Avro to handle MongoDB's dot replacement character (~) in label keys, which
 * is illegal in Avro field names.
 *
 * @see AbstractGitopsJsonCdcMessageHandler for common JSON CDC handling logic
 */
@OwnedBy(GITOPS)
@Singleton
@Slf4j
public class GitopsApplicationsCdcMessageHandler extends AbstractGitopsJsonCdcMessageHandler {
  private final GitopsApplicationsRedisEventHandler eventHandler;

  @Inject
  public GitopsApplicationsCdcMessageHandler(
      GitopsApplicationsRedisEventHandler eventHandler, FeatureFlagService featureFlagService) {
    super(featureFlagService);
    this.eventHandler = eventHandler;
  }

  @Override
  protected DebeziumAbstractRedisEventHandler getEventHandler() {
    return eventHandler;
  }

  @Override
  protected String getTopicName() {
    return "applications";
  }
}