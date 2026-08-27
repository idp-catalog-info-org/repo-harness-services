/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.dryrun.semantic.rules;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.plan.execution.dryrun.semantic.SemanticConstants;
import io.harness.pms.plan.execution.dryrun.semantic.SemanticRule;
import io.harness.pms.plan.execution.dryrun.semantic.SemanticValidationContext;
import io.harness.pms.plan.execution.dryrun.semantic.SemanticYamlUtils;
import io.harness.spec.server.pipeline.v1.model.DryRunPipelineValidationResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Rule 3: when a stage enables {@code cloneCodebase}, the pipeline must configure a usable codebase.
 *
 * <p>Scope: any stage type that clones via the {@code ci} codebase path -- Integration (CI),
 * SecurityTests, IDP. IACM is excluded in v1 (it reads {@code properties.iacm.codebase}).
 *
 * <p>{@code cloneCodebase} default is FALSE (platform default); this rule fires only when it
 * resolves to {@code true}. Findings:
 * <ul>
 *   <li>codebase block absent/empty -> ERROR (CODEBASE, stage id)
 *   <li>Harness Code source (harness/empty ref) without {@code repoName} -> ERROR (CODEBASE)
 *   <li>Harness Code source with {@code repoName} -> valid (the demo case)
 *   <li>real git {@code connectorRef} present -> defer type/existence to Rules 1-2
 * </ul>
 *
 * <p>Validates the clone <em>source</em> (connectorRef / repoName), which always lives in the
 * pipeline YAML -- never {@code codebase.build}, which is the clone <em>target</em> a webhook may
 * supply at runtime. Skipped when {@code cloneCodebase}, {@code connectorRef}, or {@code repoName}
 * is a runtime expression (the source may still resolve to something valid).
 */
@Singleton
@OwnedBy(PIPELINE)
public class CloneCodebaseSanityRule implements SemanticRule {
  private static final String ENTITY_TYPE_CODEBASE = "CODEBASE";

  /** Stage types that clone via the {@code properties.ci.codebase} path. */
  private static final Set<String> CLONE_STAGE_TYPES = Set.of("CI", "Integration", "SecurityTests", "IDP");

  @Override
  public List<DryRunPipelineValidationResult> apply(SemanticValidationContext ctx) {
    List<DryRunPipelineValidationResult> findings = new ArrayList<>();
    JsonNode root = ctx.getPipelineRoot();
    if (root == null) {
      return findings;
    }
    if (ctx.isV1()) {
      JsonNode v1Stages = SemanticYamlUtils.stagesNode(root, true);
      if (v1Stages != null) {
        JsonNode pipelineNode = root.get("pipeline");
        JsonNode pipelineRepo = pipelineNode == null ? null : pipelineNode.get("repo");
        JsonNode pipelineClone = pipelineNode == null ? null : pipelineNode.get("clone");
        if (pipelineClone != null && !pipelineClone.isObject()) {
          pipelineClone = null;
        }
        JsonNode finalPipelineClone = pipelineClone;
        SemanticYamlUtils.forEachStage(
            v1Stages, true, stage -> checkV1Stage(stage, finalPipelineClone, pipelineRepo, findings));
      }
      return findings;
    }
    JsonNode pipeline = root.get("pipeline");
    JsonNode base = pipeline != null ? pipeline : root;

    JsonNode codebase = at(base, "properties", "ci", "codebase");
    // Note: codebase.build (which ref/commit to clone) is deliberately ignored here. build is the
    // clone *target* -- a webhook may supply it at runtime (<+input>) -- but this rule validates the
    // clone *source* (connectorRef / repoName), which always lives in the pipeline YAML. A runtime
    // build says nothing about whether the source is configured, so it must not suppress the check.

    JsonNode stages = base.get("stages");
    if (stages == null) {
      return findings;
    }
    SemanticYamlUtils.forEachStage(stages, stage -> checkStage(stage, codebase, findings));
    return findings;
  }

  /**
   * Mirrors V1 plan creation ({@code UnifiedStagePMSPlanCreator.getGitClone}): stage clone overrides
   * pipeline clone; a present clone is enabled unless {@code enabled: false} is explicit.
   */
  private void checkV1Stage(
      JsonNode stage, JsonNode pipelineClone, JsonNode pipelineRepo, List<DryRunPipelineValidationResult> findings) {
    JsonNode stageClone = stage.get("clone");
    if (stageClone != null && !stageClone.isObject()) {
      stageClone = null;
    }
    JsonNode effectiveClone = stageClone != null ? stageClone : pipelineClone;
    if (effectiveClone == null) {
      return;
    }
    String enabled = text(effectiveClone, "enabled");
    if (isRuntimeExpression(enabled)) {
      return;
    }
    // Explicit false disables; omitted/null/true means clone is active (plan-creation parity).
    if (enabled != null && !Boolean.parseBoolean(enabled.trim())) {
      return;
    }
    if (hasV1CodebaseSource(stage, stageClone, pipelineClone, pipelineRepo)) {
      return;
    }
    String stageId = SemanticYamlUtils.stageId(stage, true);
    findings.add(error(stageId, "Clone is enabled but no connector or repo is configured.",
        "Add clone.connector/clone.repo, options.repository, or pipeline.repo."));
  }

  private boolean hasV1CodebaseSource(
      JsonNode stage, JsonNode stageClone, JsonNode pipelineClone, JsonNode pipelineRepo) {
    // Stage clone fields inherit blanks from pipeline clone (same merge as plan creation).
    if (hasUsableConnectorOrRepo(firstNonBlank(text(stageClone, "connector"), text(pipelineClone, "connector")),
            firstNonBlank(text(stageClone, "repo"), text(pipelineClone, "repo")))) {
      return true;
    }
    JsonNode repository = at(stage, "options", "repository");
    if (repository != null && hasUsableConnectorOrRepo(text(repository, "connector"), text(repository, "name"))) {
      return true;
    }
    return pipelineRepo != null
        && hasUsableConnectorOrRepo(text(pipelineRepo, "connector"), text(pipelineRepo, "name"));
  }

  private String firstNonBlank(String primary, String fallback) {
    if (primary != null && !primary.isBlank()) {
      return primary;
    }
    return fallback;
  }

  private boolean hasUsableConnectorOrRepo(String connectorRef, String repo) {
    if (isRuntimeExpression(connectorRef) || isRealConnectorRef(connectorRef)) {
      return true;
    }
    return repo != null && !repo.isBlank() && !isRuntimeExpression(repo);
  }

  private void checkStage(JsonNode stage, JsonNode codebase, List<DryRunPipelineValidationResult> findings) {
    String type = text(stage, "type");
    if (type == null || !CLONE_STAGE_TYPES.contains(type)) {
      return;
    }
    JsonNode spec = stage.get("spec");
    if (spec == null) {
      return;
    }
    String cloneCodebase = text(spec, "cloneCodebase");
    if (cloneCodebase == null || isRuntimeExpression(cloneCodebase)) {
      return; // absent => default false; runtime expression => cannot resolve.
    }
    if (!Boolean.parseBoolean(cloneCodebase.trim())) {
      return;
    }

    String stageId = text(stage, "identifier");
    if (codebase == null || codebase.isMissingNode()) {
      findings.add(error(stageId, "cloneCodebase is enabled but no codebase is configured.",
          "Configure pipeline.properties.ci.codebase with a connectorRef or Harness Code repoName."));
      return;
    }

    String connectorRef = text(codebase, "connectorRef");
    if (isRuntimeExpression(connectorRef)) {
      return; // May resolve to a real connector at runtime; cannot demand repoName here.
    }
    if (isRealConnectorRef(connectorRef)) {
      return; // Rules 1-2 validate existence/type; the codebase block is present.
    }
    // Harness Code path (empty/sentinel ref): production resolves this to Harness Code and needs a
    // repoName to build the connector (CodebaseUtils.getGitConnector). Missing repoName is broken.
    String repoName = text(codebase, "repoName");
    if (repoName == null || repoName.isBlank()) {
      findings.add(error(
          stageId, "Clone from Harness Code requires codebase.repoName.", "Add codebase.connectorRef or repoName."));
    }
  }

  /**
   * True when the ref denotes a real git connector. An empty/absent ref is the Harness Code codebase
   * (production {@code CodebaseUtils.getGitConnector} treats only an empty ref as Harness Code).
   */
  private boolean isRealConnectorRef(String connectorRef) {
    if (connectorRef == null || connectorRef.isBlank()) {
      return false;
    }
    return !isRuntimeExpression(connectorRef.trim());
  }

  private boolean isRuntimeExpression(String value) {
    return value != null && value.trim().startsWith(SemanticConstants.RUNTIME_EXPRESSION_PREFIX);
  }

  private DryRunPipelineValidationResult error(String stageId, String message, String hint) {
    DryRunPipelineValidationResult result = new DryRunPipelineValidationResult();
    result.setValidationType(SemanticConstants.VALIDATION_TYPE_SEMANTIC);
    result.setSeverity(SemanticConstants.SEVERITY_ERROR);
    result.setEntityType(ENTITY_TYPE_CODEBASE);
    result.setEntityIdentifier(stageId);
    result.setErrorMessage(message);
    result.setHint(hint);
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
