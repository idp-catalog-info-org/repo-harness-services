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
import io.harness.connector.ManagerExecutable;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.pms.plan.execution.dryrun.semantic.SemanticConstants;
import io.harness.pms.plan.execution.dryrun.semantic.SemanticRule;
import io.harness.pms.plan.execution.dryrun.semantic.SemanticValidationContext;
import io.harness.pms.plan.execution.dryrun.semantic.SemanticYamlUtils;
import io.harness.spec.server.pipeline.v1.model.DryRunPipelineValidationResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rule 4: a connector routed through a delegate cannot back a step on a Harness Cloud CI stage --
 * Cloud runners have no delegate to execute against.
 *
 * <p>Scope: Integration (CI) stages whose {@code spec.runtime} resolves to Cloud. A CI stage
 * declares its environment as either {@code spec.infrastructure} (self-hosted: KubernetesDirect, VM)
 * or {@code spec.runtime} (Harness-managed: Cloud, Docker). A stage with an {@code infrastructure}
 * block runs on a delegate, so this rule skips it. Otherwise runtime type defaults to Cloud when the
 * block is absent (see {@code Runtime} {@code defaultImpl=CloudRuntime}), so a stage with no runtime
 * block is treated as Cloud and evaluated. A {@code Docker} runtime, or a {@code runtime.type} that
 * is a runtime expression, is skipped.
 *
 * <p>For each {@code spec.connectorRef} in the stage, and for
 * {@code pipeline.properties.ci.codebase.connectorRef} when the stage has {@code cloneCodebase: true},
 * the resolved {@link ConnectorInfoDTO} config is inspected only when it is a {@link
 * ManagerExecutable} (e.g. Docker, Github). {@code getExecuteOnDelegate()}:
 * <ul>
 *   <li>{@code TRUE} -> ERROR (CONNECTOR, ref)
 *   <li>{@code null} -> ERROR
 *   <li>{@code FALSE} -> no finding
 * </ul>
 *
 * <p>Checking the pipeline-level codebase connector matches hosted-infra execution: Cloud stages
 * reject delegate-routed git connectors used for clone. For V1, the effective clone connector is
 * resolved the same way as plan creation ({@code UnifiedStagePMSPlanCreator.getGitClone}): stage
 * {@code clone} overrides {@code pipeline.clone}, blanks inherit, and {@code enabled: false}
 * disables clone. That covers pipeline-only clone as well as stage-level {@code clone.connector}.
 *
 * <p>Treating {@code null} as delegate-routed is correct, not a heuristic: every {@link
 * ManagerExecutable} that can back a CI step defaults {@code executeOnDelegate} to {@code true}
 * (Docker/Artifactory/AWS/GCP/Azure via {@code @Builder.Default true}; Gitlab/Bitbucket/AzureRepo via
 * field default), so {@code null} resolves to the same delegate-routed default. The lone exception,
 * Github, coerces {@code null -> false} in its constructor and so never reaches the {@code null}
 * branch. This handling is also load-bearing: {@code @Builder.Default} is not applied on Jackson
 * field deserialization, so a Docker connector whose fetched JSON omits {@code executeOnDelegate}
 * arrives as {@code null}; treating it as delegate-routed recovers the intended default (dropping the
 * {@code null} branch would instead be a false negative -- a missed Cloud+delegate conflict).
 *
 * <p>Skipped when the ref is a runtime expression, is empty, is absent from {@code connectorsByRef}
 * (Rule 1 flags missing connectors), or its config is not a {@link ManagerExecutable} (e.g.
 * Kubernetes, which is never delegate-routed in this sense).
 */
@Singleton
@OwnedBy(PIPELINE)
public class CloudCiDelegateConnectorRule implements SemanticRule {
  private static final String ENTITY_TYPE_CONNECTOR = "CONNECTOR";
  private static final String RUNTIME_TYPE_DOCKER = "Docker";

  /** Stage types that run CI workloads on a runner. */
  private static final Set<String> CI_STAGE_TYPES = Set.of("CI", "Integration");

  @Override
  public List<DryRunPipelineValidationResult> apply(SemanticValidationContext ctx) {
    List<DryRunPipelineValidationResult> findings = new ArrayList<>();
    JsonNode root = ctx.getPipelineRoot();
    if (root == null) {
      return findings;
    }
    JsonNode pipeline = root.get("pipeline");
    JsonNode base = pipeline != null ? pipeline : root;

    // A connector referenced by several steps is one problem with one fix, so it is reported once.
    // Collect the referencing step identifiers per offending connector to keep the "where" in the message.
    Map<String, Set<String>> offendersToStepIds = new LinkedHashMap<>();
    if (ctx.isV1()) {
      JsonNode v1Stages = SemanticYamlUtils.stagesNode(root, true);
      JsonNode pipelineClone = asObject(base.get("clone"));
      SemanticYamlUtils.forEachStage(
          v1Stages, true, stage -> checkV1Stage(stage, pipelineClone, ctx, offendersToStepIds));
    } else {
      JsonNode stages = base.get("stages");
      String codebaseConnectorRef = text(at(base, "properties", "ci", "codebase"), "connectorRef");
      SemanticYamlUtils.forEachStage(stages, stage -> checkStage(stage, codebaseConnectorRef, ctx, offendersToStepIds));
    }

    for (Map.Entry<String, Set<String>> offender : offendersToStepIds.entrySet()) {
      findings.add(error(offender.getKey(), offender.getValue()));
    }
    return findings;
  }

  private void checkV1Stage(
      JsonNode stage, JsonNode pipelineClone, SemanticValidationContext ctx, Map<String, Set<String>> offenders) {
    if (!isV1CloudRuntime(stage.get("runtime"))) {
      return;
    }
    // Effective clone connector (pipeline + stage merge) — same inheritance as plan creation.
    // Checked explicitly because pipeline.clone sits outside the stage tree walk below.
    checkEffectiveV1CloneConnector(stage, pipelineClone, ctx, offenders);
    checkConnectorRefsV1(stage, null, ctx, offenders);
  }

  /**
   * Mirrors {@code UnifiedStagePMSPlanCreator.getGitClone} / Rule 3: stage clone overrides pipeline
   * clone; blank connector inherits; {@code enabled: false} disables.
   */
  private void checkEffectiveV1CloneConnector(
      JsonNode stage, JsonNode pipelineClone, SemanticValidationContext ctx, Map<String, Set<String>> offenders) {
    JsonNode stageClone = asObject(stage.get("clone"));
    JsonNode effectiveClone = stageClone != null ? stageClone : pipelineClone;
    if (effectiveClone == null) {
      return;
    }
    String enabled = text(effectiveClone, "enabled");
    if (isRuntimeExpression(enabled)) {
      return;
    }
    if (enabled != null && !Boolean.parseBoolean(enabled.trim())) {
      return;
    }
    String connectorRef = firstNonBlank(text(stageClone, "connector"), text(pipelineClone, "connector"));
    if (connectorRef != null) {
      checkConnector(connectorRef.trim(), null, ctx, offenders);
    }
  }

  private JsonNode asObject(JsonNode node) {
    return node != null && node.isObject() ? node : null;
  }

  private String firstNonBlank(String primary, String fallback) {
    if (primary != null && !primary.isBlank()) {
      return primary;
    }
    return fallback;
  }

  private boolean isV1CloudRuntime(JsonNode runtime) {
    if (runtime == null || runtime.isNull()) {
      return true;
    }
    if (runtime.isTextual()) {
      String value = runtime.asText();
      if (value == null || isRuntimeExpression(value)) {
        return false;
      }
      return "cloud".equalsIgnoreCase(value.trim());
    }
    return runtime.isObject() && runtime.get("cloud") != null;
  }

  private void checkConnectorRefsV1(
      JsonNode node, String enclosingStepId, SemanticValidationContext ctx, Map<String, Set<String>> offenders) {
    if (node == null) {
      return;
    }
    if (node.isObject()) {
      String stepId = text(node, "id");
      String currentStepId = stepId != null ? stepId : enclosingStepId;
      checkV1Ref(text(node, "connector"), currentStepId, ctx, offenders);
      checkV1Ref(text(node, "registryRef"), currentStepId, ctx, offenders);
      checkV1Ref(text(node, "harness-image-connector"), currentStepId, ctx, offenders);
      JsonNode credentials = at(node, "options", "registry", "credentials");
      if (credentials != null && credentials.isArray()) {
        final String scoped = currentStepId;
        credentials.forEach(cred -> checkV1Ref(text(cred, "name"), scoped, ctx, offenders));
      }
      final String scopedStepId = currentStepId;
      node.fields().forEachRemaining(entry -> checkConnectorRefsV1(entry.getValue(), scopedStepId, ctx, offenders));
    } else if (node.isArray()) {
      node.forEach(child -> checkConnectorRefsV1(child, enclosingStepId, ctx, offenders));
    }
  }

  private void checkV1Ref(
      String ref, String stepId, SemanticValidationContext ctx, Map<String, Set<String>> offenders) {
    if (ref != null) {
      checkConnector(ref.trim(), stepId, ctx, offenders);
    }
  }

  private void checkStage(JsonNode stage, String codebaseConnectorRef, SemanticValidationContext ctx,
      Map<String, Set<String>> offendersToStepIds) {
    String type = text(stage, "type");
    if (type == null || !CI_STAGE_TYPES.contains(type)) {
      return;
    }
    JsonNode spec = stage.get("spec");
    if (spec == null) {
      return;
    }
    // A CI stage declares its environment as either `infrastructure` (self-hosted: KubernetesDirect,
    // VM, etc.) or `runtime` (Harness-managed: Cloud, Docker). An `infrastructure` block means the
    // stage runs on a delegate, so delegate-routed connectors are valid -- skip the stage entirely.
    if (spec.get("infrastructure") != null) {
      return;
    }
    // Runtime absent => Cloud (platform default). Docker runtime => has a delegate/runner, skip.
    // Runtime expression => cannot resolve, skip.
    String runtimeType = text(at(spec, "runtime"), "type");
    if (runtimeType != null) {
      if (isRuntimeExpression(runtimeType)) {
        return;
      }
      if (RUNTIME_TYPE_DOCKER.equals(runtimeType.trim())) {
        return;
      }
    }
    // Pipeline-level codebase is used for clone on Cloud the same way step connectorRefs are: a
    // delegate-routed git connector fails hosted-infra execution. Only when this stage clones.
    if (isCloneCodebaseEnabled(spec) && codebaseConnectorRef != null) {
      checkConnector(codebaseConnectorRef.trim(), null, ctx, offendersToStepIds);
    }
    // Cloud stage: inspect every connectorRef referenced under this stage, tagging each with the
    // nearest enclosing step identifier so a finding can name where the connector is used.
    checkConnectorRefs(spec, null, ctx, offendersToStepIds);
  }

  /** Matches CloneCodebaseSanityRule: absent / expression => not treated as cloning. */
  private boolean isCloneCodebaseEnabled(JsonNode spec) {
    String cloneCodebase = text(spec, "cloneCodebase");
    if (cloneCodebase == null || isRuntimeExpression(cloneCodebase)) {
      return false;
    }
    return Boolean.parseBoolean(cloneCodebase.trim());
  }

  private void checkConnectorRefs(JsonNode node, String enclosingStepId, SemanticValidationContext ctx,
      Map<String, Set<String>> offendersToStepIds) {
    if (node == null) {
      return;
    }
    if (node.isObject()) {
      // A step/stepGroup node carries its own identifier; use it for connectorRefs nested beneath it.
      String stepId = text(node, "identifier");
      String currentStepId = stepId != null ? stepId : enclosingStepId;

      String connectorRef = text(node, "connectorRef");
      if (connectorRef != null) {
        checkConnector(connectorRef.trim(), currentStepId, ctx, offendersToStepIds);
      }
      final String scopedStepId = currentStepId;
      node.fields().forEachRemaining(
          entry -> checkConnectorRefs(entry.getValue(), scopedStepId, ctx, offendersToStepIds));
    } else if (node.isArray()) {
      node.forEach(child -> checkConnectorRefs(child, enclosingStepId, ctx, offendersToStepIds));
    }
  }

  private void checkConnector(
      String connectorRef, String stepId, SemanticValidationContext ctx, Map<String, Set<String>> offendersToStepIds) {
    if (connectorRef.isEmpty() || isRuntimeExpression(connectorRef)) {
      return;
    }
    ConnectorInfoDTO info = ctx.getConnectorsByRef().get(connectorRef);
    if (info == null) {
      return; // Rule 1 flags missing connectors.
    }
    ConnectorConfigDTO config = info.getConnectorConfig();
    if (!(config instanceof ManagerExecutable)) {
      return; // Connector type has no delegate-vs-cloud routing (e.g. Kubernetes).
    }
    // executeOnDelegate == TRUE, or null defaulting to delegate (Docker's @Builder.Default true) => delegate-routed.
    Boolean executeOnDelegate = ((ManagerExecutable) config).getExecuteOnDelegate();
    if (executeOnDelegate == null || Boolean.TRUE.equals(executeOnDelegate)) {
      Set<String> stepIds = offendersToStepIds.computeIfAbsent(connectorRef, k -> new LinkedHashSet<>());
      if (stepId != null) {
        stepIds.add(stepId);
      }
    }
  }

  private boolean isRuntimeExpression(String value) {
    return value != null && value.trim().startsWith(SemanticConstants.RUNTIME_EXPRESSION_PREFIX);
  }

  private DryRunPipelineValidationResult error(String connectorRef, Set<String> stepIds) {
    DryRunPipelineValidationResult result = new DryRunPipelineValidationResult();
    result.setValidationType(SemanticConstants.VALIDATION_TYPE_SEMANTIC);
    result.setSeverity(SemanticConstants.SEVERITY_ERROR);
    result.setEntityType(ENTITY_TYPE_CONNECTOR);
    result.setEntityIdentifier(connectorRef);
    StringBuilder message = new StringBuilder(128)
                                .append("Connector '")
                                .append(connectorRef)
                                .append("' runs on a delegate but the CI stage runs on Harness Cloud (no delegate).");
    if (stepIds != null && !stepIds.isEmpty()) {
      message.append(" Referenced by ")
          .append(stepIds.size())
          .append(stepIds.size() == 1 ? " step: " : " steps: ")
          .append(String.join(", ", stepIds))
          .append('.');
    }
    result.setErrorMessage(message.toString());
    result.setHint("Set executeOnDelegate: false on the connector, or run the stage on a delegate/self-hosted runner.");
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
