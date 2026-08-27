/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.changeadvisor;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.executions.steps.node.ExecutionNodeType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.changeadvisor.v1.ChangeAdvisorStepParameters;
import io.harness.utils.PmsFeatureFlagHelper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.CV)
@Slf4j
public class ChangeAdvisorEvaluationHelper {
  private static final String PIPELINE_ENGINE = "NG";
  private static final Set<String> CD_STAGE_STEP_TYPES =
      Set.of(ExecutionNodeType.DEPLOYMENT_STAGE_STEP.getName(), ExecutionNodeType.DEPLOYMENT_STAGE_STEP_V1.getName());
  // change-advisor-service API expects the YAML stage type label, not the plan-node StepType name.
  private static final String CD_STAGE_TYPE_FOR_API = "Deployment";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String UI_RENDER_SOURCE = "UI_RENDER";
  static final String COMING_SOON_MESSAGE =
      "This context is not yet evaluated in V1. This step is a no-op and does not block the pipeline.";

  @Inject private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Inject private ChangeAdvisorServiceClient changeAdvisorServiceClient;

  public enum EvaluationStatus { FEATURE_DISABLED, COMING_SOON, ADVISORY_RECEIVED, CALL_FAILED }

  @Value
  @Builder
  public static class EvaluationResponse {
    EvaluationStatus status;
    ChangeAdvisorOutcome advisorOutcome;
    ChangeAdvisorComingSoonOutcome comingSoonOutcome;
    Advisory advisory;
  }

  public EvaluationResponse evaluate(Ambiance ambiance, ChangeAdvisorStepSpecParameters params) {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectId = AmbianceUtils.getProjectIdentifier(ambiance);
    String pipelineId = ambiance.getMetadata() != null ? ambiance.getMetadata().getPipelineIdentifier() : null;
    String planExecutionId = ambiance.getPlanExecutionId();

    try {
      if (!pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.FF_CHANGEADVISOR_ENABLED)) {
        log.info("ChangeAdvisor step: FF_CHANGEADVISOR_ENABLED is OFF for account {} - skipping advisory call (no-op)",
            accountId);
        return EvaluationResponse.builder().status(EvaluationStatus.FEATURE_DISABLED).build();
      }
    } catch (Exception ffEx) {
      log.warn("ChangeAdvisor step: FF lookup failed for account {} - treating as disabled (no-op)", accountId, ffEx);
      return EvaluationResponse.builder().status(EvaluationStatus.FEATURE_DISABLED).build();
    }

    String stageType = resolveStageType(ambiance);

    if (!shouldEvaluate(ambiance, stageType)) {
      ChangeAdvisorComingSoonOutcome outcome = buildComingSoonOutcome(stageType);
      log.info("ChangeAdvisor step: non-CD stage (stageType={}, contextType={}) - short-circuiting as no-op "
              + "with Coming Soon outcome for account {} pipeline {} planExecutionId {}",
          stageType, outcome.getContextType(), accountId, pipelineId, planExecutionId);
      return EvaluationResponse.builder().status(EvaluationStatus.COMING_SOON).comingSoonOutcome(outcome).build();
    }

    try {
      CreateAdvisoryRequest request =
          buildRequest(ambiance, accountId, orgId, projectId, pipelineId, planExecutionId, stageType, params);

      log.info("ChangeAdvisor step: submitting advisory for account={} pipeline={} planExecutionId={}", accountId,
          pipelineId, planExecutionId);

      Call<Advisory> call = changeAdvisorServiceClient.createAdvisory(request);
      Response<Advisory> response = call.execute();

      if (response.isSuccessful() && response.body() != null) {
        Advisory advisory = response.body();
        log.info("ChangeAdvisor step: advisory received - id={} status={} decision={} score={}", advisory.getId(),
            advisory.getStatus(), advisory.getDecision(), advisory.getScore());
        return EvaluationResponse.builder()
            .status(EvaluationStatus.ADVISORY_RECEIVED)
            .advisorOutcome(buildAdvisorOutcome(advisory))
            .advisory(advisory)
            .build();
      }
      log.warn("ChangeAdvisor step: non-2xx response httpCode={} for account={} - degrading gracefully",
          response.code(), accountId);
    } catch (Exception e) {
      log.warn("ChangeAdvisor step: advisory call failed for account={} pipeline={} planExecutionId={} - degrading "
              + "gracefully",
          accountId, pipelineId, planExecutionId, e);
    }

    return EvaluationResponse.builder().status(EvaluationStatus.CALL_FAILED).build();
  }

  public static ChangeAdvisorStepSpecParameters extractParams(StepBaseParameters stepParameters) {
    if (stepParameters == null || stepParameters.getSpec() == null) {
      return null;
    }
    if (stepParameters.getSpec() instanceof ChangeAdvisorStepSpecParameters) {
      return (ChangeAdvisorStepSpecParameters) stepParameters.getSpec();
    }
    if (stepParameters.getSpec() instanceof ChangeAdvisorStepParameters) {
      return ((ChangeAdvisorStepParameters) stepParameters.getSpec()).toChangeAdvisorStepSpecParameters();
    }
    return null;
  }

  public static boolean requiresApproval(Advisory advisory) {
    if (advisory == null || advisory.getDecision() == null) {
      return false;
    }
    String decision = advisory.getDecision();
    return "GATE".equalsIgnoreCase(decision) || "BLOCK".equalsIgnoreCase(decision);
  }

  static String resolveStageType(Ambiance ambiance) {
    return AmbianceUtils.getStageLevelFromAmbiance(ambiance)
        .map(level -> level.getStepType() != null ? level.getStepType().getType() : null)
        .orElse(null);
  }

  private static boolean isCdStage(String stageStepType) {
    return stageStepType != null && CD_STAGE_STEP_TYPES.stream().anyMatch(type -> type.equalsIgnoreCase(stageStepType));
  }

  private static boolean shouldEvaluate(Ambiance ambiance, String stageType) {
    if (isCdStage(stageType)) {
      return true;
    }
    // TEMP dev bypass: allow v1 unified CI stages (IntegrationStageStepPMS) for E2E testing.
    return HarnessYamlVersion.isV1(AmbianceUtils.getPipelineVersion(ambiance))
        && "IntegrationStageStepPMS".equals(stageType);
  }

  static ChangeAdvisorComingSoonOutcome buildComingSoonOutcome(String stageType) {
    String contextType;
    String title;
    if (stageType == null || stageType.isEmpty()) {
      contextType = "unknown";
      title = "ChangeAdvisor — Coming Soon";
    } else {
      String lower = stageType.toLowerCase();
      if (lower.contains("integrationstage") || "ci".equals(lower)) {
        contextType = "ci";
        title = "ChangeAdvisor for CI — Artifact Promotion Gating";
      } else if (lower.contains("iacm")) {
        contextType = "iacm";
        title = "ChangeAdvisor for IACM — Infrastructure Change Evaluation";
      } else if ("approval".equals(lower)) {
        contextType = "approval";
        title = "ChangeAdvisor for Approval — Coming Soon";
      } else {
        contextType = "custom";
        title = "ChangeAdvisor — Coming Soon";
      }
    }
    return ChangeAdvisorComingSoonOutcome.builder()
        .comingSoon(true)
        .contextType(contextType)
        .title(title)
        .message(COMING_SOON_MESSAGE)
        .build();
  }

  static ChangeAdvisorOutcome buildAdvisorOutcome(Advisory advisory) {
    return ChangeAdvisorOutcome.builder()
        .advisoryId(advisory.getId())
        .status(advisory.getStatus())
        .decision(advisory.getDecision())
        .score(advisory.getScore())
        .uiRender(extractUiRender(advisory))
        .build();
  }

  @SuppressWarnings("unchecked")
  static String extractUiRender(Advisory advisory) {
    if (advisory == null || advisory.getEvidence() == null) {
      return null;
    }
    Object signalsObj = advisory.getEvidence().get("signals");
    if (!(signalsObj instanceof List)) {
      return null;
    }
    for (Object element : (List<Object>) signalsObj) {
      if (!(element instanceof Map)) {
        continue;
      }
      Map<String, Object> signal = (Map<String, Object>) element;
      if (UI_RENDER_SOURCE.equals(signal.get("source"))) {
        Object data = signal.get("data");
        if (data == null) {
          return null;
        }
        try {
          return OBJECT_MAPPER.writeValueAsString(data);
        } catch (JsonProcessingException e) {
          log.warn("ChangeAdvisor step: failed to serialize UI_RENDER data", e);
          return null;
        }
      }
    }
    return null;
  }

  private CreateAdvisoryRequest buildRequest(Ambiance ambiance, String accountId, String orgId, String projectId,
      String pipelineId, String planExecutionId, String stageType, ChangeAdvisorStepSpecParameters params) {
    String stageExecutionId = ambiance.getStageExecutionId();
    String triggerType = ambiance.getMetadata() != null && ambiance.getMetadata().getTriggerInfo() != null
            && ambiance.getMetadata().getTriggerInfo().getTriggerType() != null
        ? ambiance.getMetadata().getTriggerInfo().getTriggerType().name()
        : null;
    long runSequence = ambiance.getMetadata() != null ? ambiance.getMetadata().getRunSequence() : 0L;

    String harnessYamlVersion = AmbianceUtils.getPipelineVersion(ambiance);
    CreateAdvisoryRequest.PipelineContext pipelineContext =
        CreateAdvisoryRequest.PipelineContext.builder()
            .accountId(accountId)
            .orgId(orgId)
            .projectId(projectId)
            .pipelineId(pipelineId)
            .triggerType(triggerType)
            .metadata(Map.of("harnessYamlVersion", harnessYamlVersion))
            .build();

    CreateAdvisoryRequest.ExecutionRef execution = CreateAdvisoryRequest.ExecutionRef.builder()
                                                       .pipelineExecutionId(planExecutionId)
                                                       .stageExecutionId(stageExecutionId)
                                                       .planExecutionId(planExecutionId)
                                                       .runSequence(runSequence > 0 ? runSequence : null)
                                                       .build();

    CreateAdvisoryRequest.EnvironmentRef environment = null;
    if (params != null) {
      String envValue = resolveString(params.getEnv());
      if (envValue != null) {
        environment = CreateAdvisoryRequest.EnvironmentRef.builder().id(envValue).name(envValue).build();
      }
    }

    Integer timeoutSeconds = null;
    List<String> presets = null;
    String mode = null;
    if (params != null) {
      Integer timeoutMinutes = resolveInt(params.getTimeoutMinutes());
      if (timeoutMinutes != null) {
        timeoutSeconds = timeoutMinutes * 60;
      }
      presets = resolveList(params.getPresets());
      mode = resolveString(params.getMode());
    }

    CreateAdvisoryRequest.EvaluationOptions options =
        CreateAdvisoryRequest.EvaluationOptions.builder()
            .dryRun("ADVISORY".equalsIgnoreCase(mode) ? Boolean.TRUE : null)
            .timeoutSeconds(timeoutSeconds)
            .build();

    return CreateAdvisoryRequest.builder()
        .pipelineEngine(PIPELINE_ENGINE)
        .stageType(CD_STAGE_TYPE_FOR_API)
        .pipelineContext(pipelineContext)
        .execution(execution)
        .environment(environment)
        .options(options)
        .presets(presets)
        .build();
  }

  private static String resolveString(ParameterField<String> field) {
    if (field == null || field.isExpression()) {
      return null;
    }
    String v = field.getValue();
    return (v == null || v.isEmpty()) ? null : v;
  }

  private static Integer resolveInt(ParameterField<Integer> field) {
    if (field == null || field.isExpression()) {
      return null;
    }
    return field.getValue();
  }

  private static List<String> resolveList(ParameterField<List<String>> field) {
    if (field == null || field.isExpression()) {
      return null;
    }
    List<String> v = field.getValue();
    return (v == null || v.isEmpty()) ? null : v;
  }
}
