/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.dryrun.semantic.rules;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.utils.ConnectorType;
import io.harness.pms.plan.execution.dryrun.semantic.SemanticConstants;
import io.harness.pms.plan.execution.dryrun.semantic.SemanticRule;
import io.harness.pms.plan.execution.dryrun.semantic.SemanticValidationContext;
import io.harness.spec.server.pipeline.v1.model.DryRunPipelineValidationResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Rule 2: a connector's actual type must match how the slot uses it.
 *
 * <p>Slots checked (see the demo pipelines):
 * <ul>
 *   <li>{@code pipeline.properties.ci.codebase.connectorRef} -> git family
 *   <li>docker push step {@code spec.connectorRef} (e.g. BuildAndPushDockerRegistry) -> DOCKER
 *   <li>CD Docker artifact source {@code spec.connectorRef} -> DOCKER
 *   <li>K8s deploy infrastructure {@code spec.connectorRef} -> KUBERNETES_CLUSTER
 *   <li>V0 Run step {@code spec.connectorRef} / V1 {@code container.connector} -> image-pull family
 *       (DockerRegistry, Gcp, Aws, Azure — matches UI connector picker filters)
 * </ul>
 *
 * <p>Skips a slot when the ref is a runtime expression, is empty (Harness Code codebase), or is
 * absent from {@code connectorsByRef} (Rule 1 flags missing connectors).
 */
@Singleton
@OwnedBy(PIPELINE)
public class ConnectorTypeRule implements SemanticRule {
  private static final String ENTITY_TYPE_CONNECTOR = "CONNECTOR";

  private static final Set<ConnectorType> GIT_FAMILY = EnumSet.of(ConnectorType.GIT, ConnectorType.GITHUB,
      ConnectorType.GITLAB, ConnectorType.BITBUCKET, ConnectorType.AZURE_REPO, ConnectorType.CODECOMMIT);
  private static final Set<ConnectorType> DOCKER_FAMILY = EnumSet.of(ConnectorType.DOCKER);
  /**
   * Image-pull / container registry connectors offered by the UI for Run step / container image
   * selection: {@code types: [Gcp, Aws, DockerRegistry, Azure]}.
   */
  private static final Set<ConnectorType> IMAGE_PULL_FAMILY =
      EnumSet.of(ConnectorType.DOCKER, ConnectorType.GCP, ConnectorType.AWS, ConnectorType.AZURE);
  private static final Set<ConnectorType> K8S_FAMILY = EnumSet.of(ConnectorType.KUBERNETES_CLUSTER);

  private static final String IMAGE_PULL_LABEL = "an image registry (DockerRegistry, Gcp, Aws, or Azure)";

  /** Node "type" values whose {@code spec.connectorRef} must be a Docker connector. */
  private static final Set<String> DOCKER_SLOT_TYPES = Set.of("BuildAndPushDockerRegistry", "Docker", "DockerRegistry");
  /** Node "type" values whose {@code spec.connectorRef} must be an image-pull registry connector. */
  private static final Set<String> IMAGE_PULL_SLOT_TYPES = Set.of("Run");
  /** Node "type" values whose {@code spec.connectorRef} must be a Kubernetes connector. */
  private static final Set<String> K8S_SLOT_TYPES = Set.of("KubernetesDirect");

  @Override
  public List<DryRunPipelineValidationResult> apply(SemanticValidationContext ctx) {
    List<DryRunPipelineValidationResult> findings = new ArrayList<>();
    JsonNode root = ctx.getPipelineRoot();
    if (root == null) {
      return findings;
    }

    if (ctx.isV1()) {
      // Pipeline-level repo shorthand is a git codebase source (see V1 connector inventory).
      checkSlot(ctx, text(at(root, "pipeline", "repo"), "connector"), GIT_FAMILY, "a Git", findings);
      walkV1Slots(root, ctx, findings);
      return findings;
    }

    // Slot 1: CI codebase -> git family.
    JsonNode codebase = at(root, "pipeline", "properties", "ci", "codebase");
    if (codebase != null) {
      checkSlot(ctx, text(codebase, "connectorRef"), GIT_FAMILY, "a Git", findings);
    }

    // Slots 2-4: recursively find nodes with a "type" + "spec.connectorRef".
    walkTypedSlots(root, ctx, findings);
    return findings;
  }

  private void walkTypedSlots(
      JsonNode node, SemanticValidationContext ctx, List<DryRunPipelineValidationResult> findings) {
    if (node == null) {
      return;
    }
    if (node.isObject()) {
      String type = text(node, "type");
      JsonNode spec = node.get("spec");
      if (type != null && spec != null && spec.isObject()) {
        String connectorRef = text(spec, "connectorRef");
        if (connectorRef != null) {
          if (DOCKER_SLOT_TYPES.contains(type)) {
            checkSlot(ctx, connectorRef, DOCKER_FAMILY, "a Docker", findings);
          } else if (IMAGE_PULL_SLOT_TYPES.contains(type)) {
            checkSlot(ctx, connectorRef, IMAGE_PULL_FAMILY, IMAGE_PULL_LABEL, findings);
          } else if (K8S_SLOT_TYPES.contains(type)) {
            checkSlot(ctx, connectorRef, K8S_FAMILY, "a Kubernetes", findings);
          }
        }
      }
      node.fields().forEachRemaining(entry -> walkTypedSlots(entry.getValue(), ctx, findings));
    } else if (node.isArray()) {
      node.forEach(child -> walkTypedSlots(child, ctx, findings));
    }
  }

  /**
   * V1 connector slots by field name. Codebase/clone require git; {@code registryRef} /
   * harness-image / registry-cred require DockerRegistry only; bare {@code container.connector}
   * accepts the UI image-pull family (DockerRegistry, Gcp, Aws, Azure).
   */
  private void walkV1Slots(
      JsonNode node, SemanticValidationContext ctx, List<DryRunPipelineValidationResult> findings) {
    if (node == null) {
      return;
    }
    if (node.isObject()) {
      JsonNode clone = node.get("clone");
      if (clone != null && clone.isObject()) {
        checkSlot(ctx, text(clone, "connector"), GIT_FAMILY, "a Git", findings);
      }
      JsonNode repository = at(node, "options", "repository");
      if (repository != null) {
        checkSlot(ctx, text(repository, "connector"), GIT_FAMILY, "a Git", findings);
      }
      JsonNode container = node.get("container");
      if (container != null && container.isObject()) {
        checkSlot(ctx, text(container, "registryRef"), DOCKER_FAMILY, "a Docker", findings);
        checkSlot(ctx, text(container, "connector"), IMAGE_PULL_FAMILY, IMAGE_PULL_LABEL, findings);
      }
      checkSlot(ctx, text(node, "harness-image-connector"), DOCKER_FAMILY, "a Docker", findings);
      JsonNode credentials = at(node, "options", "registry", "credentials");
      if (credentials != null && credentials.isArray()) {
        credentials.forEach(cred -> checkSlot(ctx, text(cred, "name"), DOCKER_FAMILY, "a Docker", findings));
      }
      node.fields().forEachRemaining(entry -> walkV1Slots(entry.getValue(), ctx, findings));
    } else if (node.isArray()) {
      node.forEach(child -> walkV1Slots(child, ctx, findings));
    }
  }

  private void checkSlot(SemanticValidationContext ctx, String connectorRef, Set<ConnectorType> expected,
      String expectedLabel, List<DryRunPipelineValidationResult> findings) {
    if (connectorRef == null || connectorRef.isBlank()) {
      return;
    }
    connectorRef = connectorRef.trim();
    if (connectorRef.startsWith(SemanticConstants.RUNTIME_EXPRESSION_PREFIX)) {
      return;
    }
    ConnectorInfoDTO info = ctx.getConnectorsByRef().get(connectorRef);
    if (info == null || info.getConnectorType() == null) {
      return;
    }
    if (!expected.contains(info.getConnectorType())) {
      findings.add(error(connectorRef, expectedLabel, info.getConnectorType()));
    }
  }

  private DryRunPipelineValidationResult error(String connectorRef, String expectedLabel, ConnectorType actual) {
    DryRunPipelineValidationResult result = new DryRunPipelineValidationResult();
    result.setValidationType(SemanticConstants.VALIDATION_TYPE_SEMANTIC);
    result.setSeverity(SemanticConstants.SEVERITY_ERROR);
    result.setEntityType(ENTITY_TYPE_CONNECTOR);
    result.setEntityIdentifier(connectorRef);
    result.setErrorMessage("Connector '" + connectorRef + "' is of type " + actual + " but this slot requires "
        + expectedLabel + " connector.");
    result.setHint("Reference a connector of the expected type for this slot.");
    return result;
  }

  private JsonNode at(JsonNode node, String... path) {
    JsonNode current = node;
    for (String segment : path) {
      if (current == null) {
        return null;
      }
      current = current.get(segment);
    }
    return current;
  }

  private String text(JsonNode node, String field) {
    if (node == null) {
      return null;
    }
    JsonNode value = node.get(field);
    return value == null || value.isNull() ? null : value.asText();
  }
}
