/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.aisre;

import io.harness.aisre.AiSrePipelineContextData;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.helpers.PipelineExpressionHelper;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;

import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;

/**
 * Builds the incident summary text with optional Harness pipeline execution context (Option A: summary-only).
 */
@OwnedBy(HarnessTeam.CHAOS)
@Singleton
@Slf4j
public class AisrePipelineContextFormatter {
  private static final String PIPELINE_CONTEXT_HEADER = "Created from Harness pipeline execution.";
  private static final String CD_MODULE = "cd";
  private static final String ARTIFACT_DISPLAY_NAMES = "artifactDisplayNames";
  private static final int MAX_ARTIFACTS = 5;

  @Inject private PipelineExpressionHelper pipelineExpressionHelper;
  @Inject private PmsExecutionSummaryService pmsExecutionSummaryService;

  public String formatIncidentSummary(String userDescription, Ambiance ambiance, boolean attachPipelineContext) {
    if (!attachPipelineContext) {
      return userDescription;
    }

    StringBuilder sb = new StringBuilder();
    if (!Strings.isNullOrEmpty(userDescription)) {
      sb.append(userDescription).append("\n\n");
    }
    sb.append(formatPipelineContextBlock(ambiance));
    return sb.toString();
  }

  public String formatPipelineContextBlock(Ambiance ambiance) {
    StringBuilder sb = new StringBuilder();
    sb.append(PIPELINE_CONTEXT_HEADER);

    appendTriggeredBy(sb);
    appendIfPresent(sb, "Pipeline", AmbianceUtils.getPipelineIdentifier(ambiance));
    appendIfPresent(sb, "Stage", AmbianceUtils.getStageIdentifierFromAmbiance(ambiance));

    Optional<PipelineExecutionSummaryEntity> executionSummary = fetchExecutionSummary(ambiance);
    appendExecutionUrl(sb, ambiance, executionSummary.orElse(null));
    appendArtifacts(sb, executionSummary.orElse(null));

    return sb.toString();
  }

  private void appendTriggeredBy(StringBuilder sb) {
    AiSrePipelineContextData context = AiSrePipelineContextData.get();
    if (context == null) {
      return;
    }
    appendIfPresent(sb, "Triggered by", formatTriggeredBy(context));
  }

  private static String formatTriggeredBy(AiSrePipelineContextData context) {
    if (!Strings.isNullOrEmpty(context.getTriggeredByEmail())) {
      if (!Strings.isNullOrEmpty(context.getTriggerType())) {
        return context.getTriggeredByEmail() + " (" + context.getTriggerType() + ")";
      }
      return context.getTriggeredByEmail();
    }
    if (!Strings.isNullOrEmpty(context.getTriggeredByName())) {
      if (!Strings.isNullOrEmpty(context.getTriggerType())) {
        return context.getTriggeredByName() + " (" + context.getTriggerType() + ")";
      }
      return context.getTriggeredByName();
    }
    return context.getTriggerType();
  }

  /**
   * The execution URL on its own, for callers that store it in a link field rather than in prose.
   * Returns null when it cannot be built, so a missing link never fails the caller.
   */
  public String resolveExecutionUrl(Ambiance ambiance) {
    try {
      String executionUrl =
          pipelineExpressionHelper.generateUrl(ambiance, fetchExecutionSummary(ambiance).orElse(null));
      return Strings.isNullOrEmpty(executionUrl) ? null : executionUrl;
    } catch (Exception e) {
      log.warn("Failed to generate pipeline execution URL", e);
      return null;
    }
  }

  private void appendExecutionUrl(
      StringBuilder sb, Ambiance ambiance, PipelineExecutionSummaryEntity executionSummary) {
    try {
      String executionUrl = pipelineExpressionHelper.generateUrl(ambiance, executionSummary);
      if (!Strings.isNullOrEmpty(executionUrl)) {
        appendIfPresent(sb, "Execution URL", executionUrl);
        return;
      }
    } catch (Exception e) {
      log.warn("Failed to generate pipeline execution URL for incident context", e);
    }
    appendIfPresent(sb, "Execution", ambiance.getPlanExecutionId());
  }

  private void appendArtifacts(StringBuilder sb, PipelineExecutionSummaryEntity executionSummary) {
    List<String> artifacts = resolveArtifactDisplayNames(executionSummary);
    if (!artifacts.isEmpty()) {
      appendIfPresent(sb, "Artifact", String.join(", ", artifacts));
    }
  }

  private Optional<PipelineExecutionSummaryEntity> fetchExecutionSummary(Ambiance ambiance) {
    if (Strings.isNullOrEmpty(ambiance.getPlanExecutionId())) {
      return Optional.empty();
    }
    try {
      return Optional.ofNullable(pmsExecutionSummaryService.getPipelineExecutionSummaryWithProjections(
          AmbianceUtils.getAccountId(ambiance), ambiance.getPlanExecutionId(),
          Sets.newHashSet(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.modules,
              PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.moduleInfo)));
    } catch (Exception e) {
      log.warn("Failed to fetch pipeline execution summary for incident context", e);
      return Optional.empty();
    }
  }

  private static List<String> resolveArtifactDisplayNames(PipelineExecutionSummaryEntity executionSummary) {
    if (executionSummary == null || executionSummary.getModuleInfo() == null) {
      return Collections.emptyList();
    }
    Document cdModule = executionSummary.getModuleInfo().get(CD_MODULE);
    if (cdModule == null) {
      return Collections.emptyList();
    }
    List<String> artifactDisplayNames = cdModule.getList(ARTIFACT_DISPLAY_NAMES, String.class);
    if (artifactDisplayNames == null || artifactDisplayNames.isEmpty()) {
      return Collections.emptyList();
    }
    return artifactDisplayNames.stream()
        .filter(name -> !Strings.isNullOrEmpty(name))
        .limit(MAX_ARTIFACTS)
        .collect(Collectors.toList());
  }

  private static void appendIfPresent(StringBuilder sb, String label, String value) {
    if (!Strings.isNullOrEmpty(value)) {
      sb.append('\n').append(label).append(": ").append(value);
    }
  }
}
