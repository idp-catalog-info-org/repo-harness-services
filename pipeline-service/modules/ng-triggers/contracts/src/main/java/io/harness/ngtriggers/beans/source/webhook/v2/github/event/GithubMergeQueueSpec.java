/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.source.webhook.v2.github.event;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.ngtriggers.beans.source.webhook.v2.github.event.GithubTriggerEvent.MERGE_QUEUE;

import static java.util.Collections.emptyList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ngtriggers.beans.source.webhook.v2.condition.TriggerEventDataCondition;
import io.harness.ngtriggers.beans.source.webhook.v2.git.GitAction;
import io.harness.ngtriggers.beans.source.webhook.v2.git.GitEvent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

/**
 * Deliberately has no `actions` field, mirroring HarnessMergeQueueSpec (see Plan 03). GitHub's
 * merge_group event has checks_requested and destroyed actions - go-scm maps destroyed to
 * ActionChecksCanceled - and both must reach the same trigger so a cancel can abort the run the
 * request started. Exposing an actions filter would let a user subscribe to half the lifecycle
 * and produce executions that are never aborted.
 */
@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(PIPELINE)
public class GithubMergeQueueSpec implements GithubEventSpec {
  String connectorRef;
  String repoName;
  List<TriggerEventDataCondition> headerConditions;
  List<TriggerEventDataCondition> payloadConditions;
  String jexlCondition;

  @Override
  public String fetchConnectorRef() {
    return connectorRef;
  }

  @Override
  public String fetchRepoName() {
    return repoName;
  }

  @Override
  public GitEvent fetchEvent() {
    return MERGE_QUEUE;
  }

  @Override
  public List<GitAction> fetchActions() {
    return emptyList();
  }

  @Override
  public List<TriggerEventDataCondition> fetchHeaderConditions() {
    return headerConditions;
  }

  @Override
  public List<TriggerEventDataCondition> fetchPayloadConditions() {
    return payloadConditions;
  }

  @Override
  public String fetchJexlCondition() {
    return jexlCondition;
  }
}
