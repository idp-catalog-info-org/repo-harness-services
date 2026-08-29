/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.preprocess;

import static io.harness.annotations.dev.HarnessTeam.CV;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

/*
 * Pre-plan YAML transform for AIVerifyNG: derives strategy.matrix.hs from a concrete spec.healthSources list (each
 * element is an object carrying a healthSourceRef) so the UI does not need to author the matrix. A whole-field
 * expression is left untouched (the containerized step group init does not resolve an expression-valued matrix axis),
 * so plan creation rejects it with an actionable message guiding the author to a concrete list. Constants mirror
 * AIVerifyNGStepInfo.MULTI_HEALTH_SOURCE_MATRIX_AXIS/_EXPRESSION in the CD module.
 */
@Slf4j
@OwnedBy(CV)
public class AIVerifyMatrixPreprocessor implements PlanCreationYamlPreprocessor {
  public static final String AI_VERIFY_STEP_TYPE = "AIVerifyNG";
  static final String MATRIX_AXIS = "hs";
  static final String MATRIX_EXPRESSION = "<+matrix.hs>";
  static final String HEALTH_SOURCE_REF = "healthSourceRef";
  /**
   * Spec-level map keyed by authored {@link #HEALTH_SOURCE_REF} string. Populated when matrix injection replaces
   * {@code healthSources} with the {@link #MATRIX_EXPRESSION} carrier so per-ref {@code healthSourceInputs} are not
   * dropped. ng-manager resolves the active slice via {@code healthSourceInputsByRef[activeRef]}.
   */
  static final String HEALTH_SOURCE_INPUTS_BY_REF = "healthSourceInputsByRef";
  static final String HEALTH_SOURCE_FANOUT_SIZE = "healthSourceFanoutSize";
  static final String HEALTH_SOURCE_INPUTS = "healthSourceInputs";

  @Override
  public JsonNode preprocessPipelineYaml(JsonNode pipelineJsonNode, String accountId, String orgId, String projectId,
      String executionUuid, String pipelineId, ExecutionMode executionMode) {
    if (pipelineJsonNode == null) {
      return pipelineJsonNode;
    }

    boolean modified = expandStepsInPipeline(pipelineJsonNode);
    if (modified) {
      log.info("AIVerifyNG matrix injected in pipeline '{}' for account '{}'", pipelineId, accountId);
      return JsonPipelineUtils.readTree(JsonPipelineUtils.getJsonString(pipelineJsonNode));
    }
    return pipelineJsonNode;
  }

  private boolean expandStepsInPipeline(JsonNode pipelineNode) {
    JsonNode stagesNode = pipelineNode.path("pipeline").path("stages");
    if (stagesNode.isMissingNode() || !stagesNode.isArray()) {
      return false;
    }

    boolean modified = false;
    for (JsonNode stageWrapper : stagesNode) {
      JsonNode parallelNode = stageWrapper.path("parallel");
      if (!parallelNode.isMissingNode() && parallelNode.isArray()) {
        for (JsonNode parallelStageWrapper : parallelNode) {
          modified = expandStepsInStage(parallelStageWrapper) || modified;
        }
        continue;
      }
      modified = expandStepsInStage(stageWrapper) || modified;
    }
    return modified;
  }

  private boolean expandStepsInStage(JsonNode stageWrapper) {
    if (stageWrapper == null) {
      return false;
    }

    boolean modified = false;
    JsonNode stepsNode = stageWrapper.path("stage").path("spec").path("execution").path("steps");
    if (!stepsNode.isMissingNode() && stepsNode.isArray()) {
      modified = expandStepsInArray((ArrayNode) stepsNode) || modified;
    }

    JsonNode rollbackStepsNode = stageWrapper.path("stage").path("spec").path("execution").path("rollbackSteps");
    if (!rollbackStepsNode.isMissingNode() && rollbackStepsNode.isArray()) {
      modified = expandStepsInArray((ArrayNode) rollbackStepsNode) || modified;
    }

    return modified;
  }

  private boolean expandStepsInArray(ArrayNode stepsArray) {
    boolean modified = false;

    for (int i = 0; i < stepsArray.size(); i++) {
      JsonNode wrapper = stepsArray.get(i);

      JsonNode stepNode = wrapper.path("step");
      if (!stepNode.isMissingNode() && stepNode.isObject() && isAIVerifyStep(stepNode)) {
        if (injectMatrixOnStep((ObjectNode) stepNode)) {
          modified = true;
          log.info("Injected strategy.matrix.hs on AIVerifyNG step '{}'", stepNode.path("identifier").asText());
        }
        continue;
      }

      JsonNode stepGroupNode = wrapper.path("stepGroup");
      if (!stepGroupNode.isMissingNode()) {
        JsonNode nestedSteps = stepGroupNode.path("steps");
        if (!nestedSteps.isMissingNode() && nestedSteps.isArray()) {
          modified = expandStepsInArray((ArrayNode) nestedSteps) || modified;
        }
        // A step group can also host an AIVerifyNG step in its rollbackSteps; recurse there too.
        JsonNode nestedRollbackSteps = stepGroupNode.path("rollbackSteps");
        if (!nestedRollbackSteps.isMissingNode() && nestedRollbackSteps.isArray()) {
          modified = expandStepsInArray((ArrayNode) nestedRollbackSteps) || modified;
        }
        continue;
      }

      JsonNode parallelNode = wrapper.path("parallel");
      if (!parallelNode.isMissingNode() && parallelNode.isArray()) {
        modified = expandStepsInArray((ArrayNode) parallelNode) || modified;
      }
    }

    return modified;
  }

  private boolean isAIVerifyStep(JsonNode stepNode) {
    JsonNode typeNode = stepNode.path("type");
    return !typeNode.isMissingNode() && AI_VERIFY_STEP_TYPE.equals(typeNode.asText());
  }

  // True when healthSources is the single-element matrix carrier [{healthSourceRef: <+matrix.hs>}] this preprocessor
  // injects, i.e. a repeat pass over already-processed YAML rather than a user-authored strategy.
  private boolean isAlreadyInjectedCarrier(JsonNode sourcesNode) {
    if (sourcesNode == null || !sourcesNode.isArray() || sourcesNode.size() != 1) {
      return false;
    }
    JsonNode ref = sourcesNode.get(0).path(HEALTH_SOURCE_REF);
    return ref.isTextual() && MATRIX_EXPRESSION.equals(ref.asText());
  }

  private boolean injectMatrixOnStep(ObjectNode stepNode) {
    JsonNode specNode = stepNode.path("spec");
    if (!specNode.isObject()) {
      return false;
    }

    String stepIdentifier = stepNode.path("identifier").asText();
    if (!stepNode.path("strategy").isMissingNode()) {
      // The backend injects strategy.matrix.hs from healthSources, so a user-authored strategy on an AIVerifyNG step
      // is not supported — fail fast regardless of the healthSources shape. The only strategy that may legitimately
      // coexist with healthSources is our own already-injected carrier (idempotency on a repeat pass), skipped quietly.
      if (isAlreadyInjectedCarrier(specNode.path("healthSources"))) {
        return false;
      }
      throw new InvalidRequestException("AI Verify NG step '" + stepIdentifier
          + "' must not author a strategy. The backend automatically injects strategy.matrix.hs from the "
          + "healthSources list at plan-creation time.");
    }

    JsonNode hsAxisNode = buildHsAxis(specNode.path("healthSources"), stepIdentifier);
    if (hsAxisNode == null) {
      return false;
    }

    ObjectNode strategyNode = YamlUtils.getMapper().createObjectNode();
    ObjectNode matrixNode = YamlUtils.getMapper().createObjectNode();
    matrixNode.set(MATRIX_AXIS, hsAxisNode);
    matrixNode.put("nodeName", MATRIX_EXPRESSION);
    strategyNode.set("matrix", matrixNode);
    YamlUtils.injectUuid(strategyNode);

    stepNode.set("strategy", strategyNode);

    preserveHealthSourceInputsByRef((ObjectNode) specNode, specNode.path("healthSources"));
    ((ObjectNode) specNode).put(HEALTH_SOURCE_FANOUT_SIZE, hsAxisNode.size());

    ObjectNode matrixCarrierEntry = YamlUtils.getMapper().createObjectNode();
    matrixCarrierEntry.put(HEALTH_SOURCE_REF, MATRIX_EXPRESSION);
    ArrayNode matrixCarrierSources = YamlUtils.getMapper().createArrayNode();
    matrixCarrierSources.add(matrixCarrierEntry);
    // Stamp UUIDs on the synthetic healthSources entry for FQN/log-key generation, like the strategy node above.
    YamlUtils.injectUuid(matrixCarrierSources);
    ((ObjectNode) specNode).set("healthSources", matrixCarrierSources);

    return true;
  }

  /**
   * Copies {@code healthSourceInputs} from each authored list element into {@link #HEALTH_SOURCE_INPUTS_BY_REF}, keyed
   * by that element's {@link #HEALTH_SOURCE_REF}. Must run before {@code healthSources} is replaced with the matrix
   * carrier entry.
   */
  private void preserveHealthSourceInputsByRef(ObjectNode specNode, JsonNode sourcesNode) {
    if (!sourcesNode.isArray()) {
      return;
    }
    ObjectNode byRef = YamlUtils.getMapper().createObjectNode();
    for (JsonNode element : sourcesNode) {
      if (!element.isObject()) {
        continue;
      }
      JsonNode refNode = element.path(HEALTH_SOURCE_REF);
      if (!refNode.isTextual()) {
        continue;
      }
      String ref = refNode.asText();
      if (ref.isEmpty()) {
        continue;
      }
      JsonNode inputs = element.path(HEALTH_SOURCE_INPUTS);
      if (inputs.isMissingNode() || inputs.isNull()) {
        continue;
      }
      byRef.set(ref, inputs);
    }
    if (!byRef.isEmpty()) {
      specNode.set(HEALTH_SOURCE_INPUTS_BY_REF, byRef);
      log.info("Preserved healthSourceInputs for {} health source ref(s) on AIVerifyNG step spec", byRef.size());
    }
  }

  /*
   * Builds the strategy.matrix.hs array axis from a concrete healthSources list, or null when the step should be left
   * untouched. Accepts an array of objects, each carrying a non-empty healthSourceRef string (literal id and/or
   * per-element expression); each ref resolves per matrix child at runtime.
   *
   * A whole-field expression (e.g. healthSources: <+pipeline.variables.list>) is intentionally skipped: its element
   * count is unknown at plan-creation time, and the containerized step group init does not resolve an expression-valued
   * matrix axis. Skipping lets plan creation reject it with an actionable message guiding the author to a concrete
   * list. Also skipped: missing/null, non-array, element missing a textual healthSourceRef, empty array/ref, or an
   * already-injected [{healthSourceRef: <+matrix.hs>}].
   */
  private JsonNode buildHsAxis(JsonNode sourcesNode, String stepIdentifier) {
    // Whole-field expression (missing/null/non-array) is intentionally left untouched: a quiet skip, not a mistake.
    if (sourcesNode.isMissingNode() || sourcesNode.isNull() || !sourcesNode.isArray()) {
      return null;
    }

    ArrayNode hsAxis = YamlUtils.getMapper().createArrayNode();
    for (JsonNode element : sourcesNode) {
      if (!element.isObject()) {
        log.warn(
            "AIVerifyNG step '{}': skipping matrix injection because a healthSources element is not an object: {}. "
                + "Each entry must be an object carrying a non-empty '{}'.",
            stepIdentifier, element, HEALTH_SOURCE_REF);
        return null;
      }
      JsonNode refNode = element.path(HEALTH_SOURCE_REF);
      if (!refNode.isTextual()) {
        log.warn("AIVerifyNG step '{}': skipping matrix injection because a healthSources element is missing a textual "
                + "'{}': "
                + "{}.",
            stepIdentifier, HEALTH_SOURCE_REF, element);
        return null;
      }
      String ref = refNode.asText();
      if (ref.isEmpty()) {
        log.warn("AIVerifyNG step '{}': skipping matrix injection because a healthSources element has an empty '{}'.",
            stepIdentifier, HEALTH_SOURCE_REF);
        return null;
      }
      hsAxis.add(ref);
    }

    if (hsAxis.isEmpty()) {
      log.warn(
          "AIVerifyNG step '{}': skipping matrix injection because healthSources is an empty list.", stepIdentifier);
      return null;
    }
    // Already-injected [{healthSourceRef: <+matrix.hs>}] is a normal idempotency skip on a second pass, not a problem.
    if (hsAxis.size() == 1 && MATRIX_EXPRESSION.equals(hsAxis.get(0).asText())) {
      return null;
    }
    return hsAxis;
  }
}
