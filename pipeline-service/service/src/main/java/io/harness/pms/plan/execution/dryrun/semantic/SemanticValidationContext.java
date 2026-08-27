/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.dryrun.semantic;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

/**
 * Immutable inputs shared by every {@link SemanticRule} for one dry-run invocation. Built once by
 * the {@code SemanticValidator} from data {@code DryRunHelper} already has.
 */
@Value
@Builder
@OwnedBy(PIPELINE)
public class SemanticValidationContext {
  /** Parsed resolved-YAML DOM (root of the pipeline document). */
  JsonNode pipelineRoot;

  /** Referred entities discovered during filter creation. */
  List<EntityDetailProtoDTO> referredEntities;

  /**
   * Batch-fetched connectors keyed by the scoped YAML ref string (e.g. {@code account.harnessImage}).
   * Always non-null; rules treat a missing key as "skip". When {@code connectorFetchFailed} is true
   * this map is empty for a different reason (the fetch threw) and absence must NOT be read as
   * "connector missing" -- see {@code connectorFetchFailed}.
   */
  Map<String, ConnectorInfoDTO> connectorsByRef;

  /**
   * True when the batch connector fetch threw (e.g. a transient ng-manager outage). An empty
   * {@code connectorsByRef} then means "could not resolve", not "does not exist", so the
   * existence check (Rule 1) must skip to stay fail-open instead of flagging every referenced
   * connector as missing.
   */
  boolean connectorFetchFailed;

  String accountIdentifier;
  String orgIdentifier;
  String projectIdentifier;

  /** Stored pipeline YAML version ("0"/"1"); drives version-aware rule + extraction paths. */
  String harnessVersion;

  public boolean isV1() {
    return io.harness.pms.yaml.HarnessYamlVersion.isV1(harnessVersion);
  }
}
