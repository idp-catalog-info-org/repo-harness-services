/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.ro;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.pms.data.OutcomeException;
import io.harness.engine.pms.data.outcome.PmsOutcomeService;
import io.harness.execution.NodeExecution;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.ExecutionContext;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.sdk.core.execution.NodeExecutionUtils;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class ArtifactsResolver {
  /** Fallback when {@code releaseManagementMaxArtifactsPerType} is missing or non-positive. */
  public static final int DEFAULT_MAX_ITEMS_PER_TYPE = 15;

  /**
   * CI artifact-outcome field names. Mirrors {@code CIStepArtifactOutcome} and
   * {@code IntegrationStageOutcome} in 879-pipeline-ci-commons; duplicated here
   * because depending on ci-commons would invert the module direction.
   */
  private static final class CIOutcomeKeys {
    static final String ARTIFACT_OUTCOME_PREFIX = "artifact_";
    static final String INTEGRATION_STAGE_OUTCOME = "integrationStageOutcome";

    // STAGE mode: per-step CIStepArtifactOutcome.stepArtifacts.*
    static final String STEP_ARTIFACTS = "stepArtifacts";
    static final String PUBLISHED_IMAGE_ARTIFACTS = "publishedImageArtifacts";
    static final String PUBLISHED_FILE_ARTIFACTS = "publishedFileArtifacts";
    static final String PUBLISHED_SBOM_ARTIFACTS = "publishedSbomArtifacts";

    // PIPELINE mode: per-stage IntegrationStageOutcome.*
    static final String IMAGE_ARTIFACTS = "imageArtifacts";
    static final String FILE_ARTIFACTS = "fileArtifacts";
    static final String SBOM_ARTIFACTS = "sbomArtifacts";

    private CIOutcomeKeys() {}
  }

  public enum Scope { STAGE, PIPELINE }

  @Value
  public static class ResolvedArtifacts {
    List<Map<String, Object>> images;
    List<Map<String, Object>> files;
    List<Map<String, Object>> sbom;

    public boolean isEmpty() {
      return images.isEmpty() && files.isEmpty() && sbom.isEmpty();
    }
  }

  private final PmsOutcomeService outcomeService;
  private final NodeExecutionService nodeExecutionService;
  private final int maxItemsPerType;

  @Inject
  public ArtifactsResolver(PmsOutcomeService outcomeService, NodeExecutionService nodeExecutionService,
      @Named(ReleaseManagementClientModule.RELEASE_MANAGEMENT_MAX_ARTIFACTS_PER_TYPE) int maxItemsPerType) {
    this.outcomeService = outcomeService;
    this.nodeExecutionService = nodeExecutionService;
    this.maxItemsPerType = maxItemsPerType > 0 ? maxItemsPerType : DEFAULT_MAX_ITEMS_PER_TYPE;
  }

  public int getMaxItemsPerType() {
    return maxItemsPerType;
  }

  public ResolvedArtifacts resolve(Ambiance ambiance, Scope scope) {
    if (scope == Scope.STAGE) {
      return resolveStage(ambiance);
    }
    return resolvePipeline(ambiance);
  }

  private ResolvedArtifacts resolveStage(Ambiance ambiance) {
    Sinks sinks = new Sinks();
    List<String> outcomeNames;
    try {
      outcomeNames = outcomeService.findAllOutcomeNamesByPlanExecutionId(ambiance.getPlanExecutionId());
    } catch (Exception e) {
      // Surface bulk-fetch failures: caller can't distinguish "zero artifacts"
      // from "Mongo timed out" if we silently return empty.
      throw new OutcomeException("failed to list outcomes for plan " + ambiance.getPlanExecutionId(), e);
    }
    for (String name : outcomeNames) {
      if (!name.startsWith(CIOutcomeKeys.ARTIFACT_OUTCOME_PREFIX)) {
        continue;
      }
      try {
        Map<String, Object> obj = parseOutcome(ambiance, name);
        if (obj == null || !(obj.get(CIOutcomeKeys.STEP_ARTIFACTS) instanceof Map<?, ?> stepArtifacts)) {
          continue;
        }
        appendBag(stepArtifacts, CIOutcomeKeys.PUBLISHED_IMAGE_ARTIFACTS, CIOutcomeKeys.PUBLISHED_FILE_ARTIFACTS,
            CIOutcomeKeys.PUBLISHED_SBOM_ARTIFACTS, sinks);
      } catch (Exception e) {
        // Swallow per-outcome failures so one bad outcome doesn't drop the rest.
        log.warn("ArtifactsResolver: failed to read outcome {}", name, e);
      }
    }
    return sinks.build();
  }

  private ResolvedArtifacts resolvePipeline(Ambiance ambiance) {
    Sinks sinks = new Sinks();
    List<NodeExecution> stages;
    try {
      stages = nodeExecutionService.fetchStageExecutions(ambiance.getPlanExecutionId());
    } catch (Exception e) {
      // Surface bulk-fetch failures so caller can fail fast rather than ship empty.
      throw new OutcomeException("failed to fetch stage executions for plan " + ambiance.getPlanExecutionId(), e);
    }
    for (NodeExecution stage : stages) {
      if (!StatusUtils.isFinalStatus(stage.getStatus())) {
        continue;
      }
      try {
        Ambiance stageAmbiance = ambianceForResolution(stage);
        if (stageAmbiance == null) {
          log.warn("ArtifactsResolver: skipping stage {} — executionContext missing or has no levels", stage.getUuid());
          continue;
        }
        Map<String, Object> outcome = parseOutcome(stageAmbiance, CIOutcomeKeys.INTEGRATION_STAGE_OUTCOME);
        if (outcome == null) {
          continue;
        }
        appendBag(
            outcome, CIOutcomeKeys.IMAGE_ARTIFACTS, CIOutcomeKeys.FILE_ARTIFACTS, CIOutcomeKeys.SBOM_ARTIFACTS, sinks);
      } catch (Exception e) {
        // Swallow per-stage failures so one bad stage doesn't drop the rest.
        log.warn("ArtifactsResolver: failed to read integrationStageOutcome for stage {}", stage.getUuid(), e);
      }
    }
    return sinks.build();
  }

  // NodeExecution.ambiance is deprecated; executionContext is the authoritative source of levels.
  private static Ambiance ambianceForResolution(NodeExecution stage) {
    ExecutionContext ctx = stage.getExecutionContext();
    if (ctx == null || ctx.getLevelsList().isEmpty()) {
      return null;
    }
    return Ambiance.newBuilder().setPlanExecutionId(ctx.getPlanExecutionId()).addAllLevels(ctx.getLevelsList()).build();
  }

  private Map<String, Object> parseOutcome(Ambiance ambiance, String refName) {
    String json = outcomeService.resolve(ambiance, RefObjectUtils.getOutcomeRefObject(refName));
    if (json == null) {
      return null;
    }
    return NodeExecutionUtils.extractAndProcessObject(json);
  }

  private void appendBag(Map<?, ?> bag, String imageKey, String fileKey, String sbomKey, Sinks sinks) {
    appendList(bag.get(imageKey), sinks.images);
    appendList(bag.get(fileKey), sinks.files);
    appendList(bag.get(sbomKey), sinks.sbom);
  }

  @SuppressWarnings("unchecked")
  private void appendList(Object source, List<Map<String, Object>> sink) {
    if (!(source instanceof List)) {
      return;
    }
    for (Object item : (List<Object>) source) {
      if (!(item instanceof Map)) {
        continue;
      }
      if (sink.size() >= maxItemsPerType) {
        log.warn("ArtifactsResolver: per-type cap of {} reached; dropping remaining items", maxItemsPerType);
        return;
      }
      sink.add((Map<String, Object>) item);
    }
  }

  private static final class Sinks {
    final List<Map<String, Object>> images = new ArrayList<>();
    final List<Map<String, Object>> files = new ArrayList<>();
    final List<Map<String, Object>> sbom = new ArrayList<>();

    ResolvedArtifacts build() {
      return new ResolvedArtifacts(images, files, sbom);
    }
  }
}
