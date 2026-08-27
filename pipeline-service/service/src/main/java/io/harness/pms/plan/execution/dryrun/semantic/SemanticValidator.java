/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.dryrun.semantic;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.remote.client.NGRestUtils;
import io.harness.spec.server.pipeline.v1.model.DryRunPipelineValidationResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * In-process semantic validator for the dry-run API. Reads the resolved YAML and referred entities
 * that {@code DryRunHelper} already has, batch-fetches connectors once, and dispatches the
 * registered {@link SemanticRule}s. Fail-open at every layer: a parse failure or a per-rule throw
 * becomes a WARNING, never an exception that fails the dry run.
 */
@Singleton
@Slf4j
@OwnedBy(PIPELINE)
public class SemanticValidator {
  // ObjectMapper is expensive to construct and thread-safe once built; the validator is a singleton
  // whose validate() runs many times, so parse with a single shared instance.
  private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

  private final ConnectorResourceClient connectorClient;
  private final Set<SemanticRule> rules;

  @Inject
  public SemanticValidator(ConnectorResourceClient connectorClient, Set<SemanticRule> rules) {
    this.connectorClient = connectorClient;
    this.rules = rules;
  }

  public List<DryRunPipelineValidationResult> validate(String resolvedYaml, List<EntityDetailProtoDTO> referredEntities,
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String harnessVersion) {
    if (resolvedYaml == null || resolvedYaml.isBlank()) {
      return Collections.emptyList();
    }

    JsonNode root;
    try {
      root = YAML_MAPPER.readTree(resolvedYaml);
    } catch (Exception e) {
      log.warn("Semantic validation could not parse resolved YAML; skipping rules", e);
      return Collections.singletonList(
          warning("SemanticValidator", "Could not parse resolved YAML for semantic validation."));
    }
    if (root == null) {
      return Collections.emptyList();
    }

    List<EntityDetailProtoDTO> effectiveReferredEntities =
        referredEntities == null ? new ArrayList<>() : new ArrayList<>(referredEntities);
    if (io.harness.pms.yaml.HarnessYamlVersion.isV1(harnessVersion)) {
      effectiveReferredEntities.addAll(
          V1ConnectorExtractor.extractReferredConnectors(root, accountIdentifier, orgIdentifier, projectIdentifier));
    }

    List<DryRunPipelineValidationResult> results = new ArrayList<>();

    // Holder for the fetch-failed signal so an empty connector map from a thrown fetch is not
    // mistaken for "no connectors exist" (which would make Rule 1 flag every reference).
    boolean[] connectorFetchFailed = {false};
    Map<String, ConnectorInfoDTO> connectorsByRef =
        fetchConnectors(effectiveReferredEntities, accountIdentifier, results, connectorFetchFailed);

    SemanticValidationContext ctx = SemanticValidationContext.builder()
                                        .pipelineRoot(root)
                                        .referredEntities(effectiveReferredEntities)
                                        .connectorsByRef(connectorsByRef)
                                        .connectorFetchFailed(connectorFetchFailed[0])
                                        .accountIdentifier(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .harnessVersion(harnessVersion)
                                        .build();

    for (SemanticRule rule : rules) {
      try {
        List<DryRunPipelineValidationResult> ruleResults = rule.apply(ctx);
        if (isNotEmpty(ruleResults)) {
          results.addAll(ruleResults);
        }
      } catch (Exception e) {
        log.warn("Semantic rule {} threw; skipping (dry run continues)", rule.getClass().getSimpleName(), e);
        results.add(warning(
            "SemanticValidator", "Semantic rule " + rule.getClass().getSimpleName() + " could not be evaluated."));
      }
    }
    return results;
  }

  /**
   * Batch-fetch connectors referenced by the pipeline, keyed by the scoped YAML ref (e.g.
   * {@code account.harnessImage}). Harness Code codebases carry no connector referred entity (an
   * empty codebase connectorRef is never emitted by filter creation), so nothing to exclude here. On
   * failure a single WARNING is emitted, {@code connectorFetchFailed[0]} is set, and an empty map is
   * returned; the flag lets Rule 1 tell "fetch failed" apart from "no connectors exist" and skip
   * instead of flagging every reference as missing.
   */
  private Map<String, ConnectorInfoDTO> fetchConnectors(List<EntityDetailProtoDTO> referredEntities,
      String accountIdentifier, List<DryRunPipelineValidationResult> results, boolean[] connectorFetchFailed) {
    if (isEmpty(referredEntities)) {
      return Collections.emptyMap();
    }

    // scoped ref -> FQN, preserving insertion order so the request list is deterministic.
    Map<String, String> scopedRefToFqn = new LinkedHashMap<>();
    for (EntityDetailProtoDTO entity : referredEntities) {
      if (entity.getType() != EntityTypeProtoEnum.CONNECTORS) {
        continue;
      }
      String scopedRef = SemanticRefUtils.scopedRef(entity.getIdentifierRef());
      if (scopedRef == null) {
        continue;
      }
      scopedRefToFqn.putIfAbsent(scopedRef, fqnOf(entity.getIdentifierRef()));
    }

    if (scopedRefToFqn.isEmpty()) {
      return Collections.emptyMap();
    }

    List<ConnectorResponseDTO> responses;
    try {
      responses = NGRestUtils.getResponse(
          connectorClient.listConnectorByFQN(accountIdentifier, new ArrayList<>(scopedRefToFqn.values())));
    } catch (Exception e) {
      log.warn("Semantic validation could not fetch connectors; rules will skip connector checks", e);
      results.add(warning("SemanticValidator", "Could not fetch referenced connectors for semantic validation."));
      connectorFetchFailed[0] = true;
      return Collections.emptyMap();
    }
    if (isEmpty(responses)) {
      return Collections.emptyMap();
    }

    Map<String, ConnectorInfoDTO> connectorsByRef = new HashMap<>();
    for (ConnectorResponseDTO response : responses) {
      if (response == null || response.getConnector() == null) {
        continue;
      }
      ConnectorInfoDTO info = response.getConnector();
      String scopedRef =
          SemanticRefUtils.scopedRef(info.getOrgIdentifier(), info.getProjectIdentifier(), info.getIdentifier());
      if (scopedRef != null) {
        connectorsByRef.put(scopedRef, info);
      }
    }
    return connectorsByRef;
  }

  private String fqnOf(IdentifierRefProtoDTO proto) {
    return IdentifierRef.builder()
        .accountIdentifier(proto.getAccountIdentifier().getValue())
        .orgIdentifier(SemanticRefUtils.emptyToNull(proto.getOrgIdentifier().getValue()))
        .projectIdentifier(SemanticRefUtils.emptyToNull(proto.getProjectIdentifier().getValue()))
        .identifier(proto.getIdentifier().getValue())
        .build()
        .getFullyQualifiedName();
  }

  private DryRunPipelineValidationResult warning(String entityIdentifier, String message) {
    DryRunPipelineValidationResult result = new DryRunPipelineValidationResult();
    result.setValidationType(SemanticConstants.VALIDATION_TYPE_SEMANTIC);
    result.setSeverity(SemanticConstants.SEVERITY_WARNING);
    result.setEntityIdentifier(entityIdentifier);
    result.setErrorMessage(message);
    return result;
  }
}
