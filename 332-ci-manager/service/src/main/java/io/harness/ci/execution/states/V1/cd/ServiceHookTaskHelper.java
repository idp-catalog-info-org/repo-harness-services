/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.states.V1.cd;

import io.harness.beans.FeatureName;
import io.harness.beans.sweepingoutputs.K8StageInfraDetails;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.cd.beans.outcomes.ServiceHookMetadata;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.expression.common.ExpressionMode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.plugin.CommonAbstractStepUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.runner.request.utils.RunnerSubmitTaskUtils;
import io.harness.unified.cd.service.spec.ServiceType;
import io.harness.utils.CDStepsExpressionResolver;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Map;

@Singleton
public class ServiceHookTaskHelper {
  @Inject private RunnerSubmitTaskUtils runnerSubmitTaskUtils;
  @Inject private CommonAbstractStepUtils commonAbstractStepUtils;
  @Inject private CDStepsExpressionResolver cdStepsExpressionResolver;
  @Inject private CIFeatureFlagService ciFeatureFlagService;

  public boolean isServiceHooksEnabled(String accountId) {
    return ciFeatureFlagService.isEnabled(FeatureName.CDS_UNIFIED_SERVICE_HOOKS, accountId);
  }

  public boolean isServiceHooksEnabled(Ambiance ambiance) {
    return isServiceHooksEnabled(AmbianceUtils.getAccountId(ambiance));
  }

  public boolean isNativeHelmWithSopsEnabled(Ambiance ambiance, String serviceType) {
    return ServiceType.HELM.getDisplayName().equalsIgnoreCase(serviceType)
        && ciFeatureFlagService.isEnabled(
            FeatureName.CDS_HELM_IMPROVED_SOPS_SUPPORT_FOR_SERVICE_HOOKS, AmbianceUtils.getAccountId(ambiance));
  }

  public String submitHookTask(Ambiance ambiance, ServiceHookMetadata hookMetadata,
      ParameterField<Map<String, ParameterField<JsonNode>>> envVars, Map<String, String> runnerFiles) {
    return submitHookTask(ambiance, hookMetadata, envVars, runnerFiles, null);
  }

  public String submitHookTask(Ambiance ambiance, ServiceHookMetadata hookMetadata,
      ParameterField<Map<String, ParameterField<JsonNode>>> envVars, Map<String, String> runnerFiles, String logKey) {
    String hookYaml = hookMetadata.getHookYaml();
    hookYaml = (String) cdStepsExpressionResolver.updateExpressions(
        ambiance, hookYaml, ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
    String stepId = hookMetadata.getStepId();
    String resolvedLogKey = logKey != null ? logKey : hookMetadata.getLogKey();

    StageInfraDetails stageInfraDetails = commonAbstractStepUtils.getStageInfra(ambiance);
    StageInfraDetails.Type stageInfraType = stageInfraDetails.getType();

    if (stageInfraType == StageInfraDetails.Type.K8) {
      return runnerSubmitTaskUtils.submitK8sTask(ambiance, stepId, envVars, hookYaml,
          (K8StageInfraDetails) stageInfraDetails, resolvedLogKey, runnerFiles, new ArrayList<>());
    }

    return runnerSubmitTaskUtils.submitTaskByTemplate(
        ambiance, stepId, envVars, hookYaml, new ArrayList<>(), runnerFiles);
  }
}
