/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.savings;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.beans.FeatureName;
import io.harness.beans.savings.api.SavingsInfo;
import io.harness.beans.steps.CIPipelineBaseline;
import io.harness.beans.steps.CIStageSavingsInfo;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.repositories.CIPipelineBaselineRespository;
import io.harness.repositories.CIStageSavingsInfoRepository;
import io.harness.utils.CIScopeInfoHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class CISavingsServiceImpl implements CISavingsService {
  @Inject private CIFeatureFlagService featureFlagService;
  @Inject CIPipelineBaselineRespository ciPipelineBaselineRespository;
  @Inject CIStageSavingsInfoRepository ciStageSavingsInfoRepository;
  @Inject(optional = true) private CIScopeInfoHelper ciScopeInfoHelper;

  public SavingsInfo getStageSavings(String accountId, String stageExecutionId) {
    CIStageSavingsInfo ciStageSavingsInfo =
        ciStageSavingsInfoRepository.findByAccountIdAndStageExecutionId(accountId, stageExecutionId);

    if (ciStageSavingsInfo == null) {
      return null;
    }

    return SavingsInfo.builder()
        .optimizationState(ciStageSavingsInfo.getOptimizationState())
        .timeSaved(ciStageSavingsInfo.getTimeSaved())
        .build();
  }

  public String getFirstFullRun(String accountId, String orgId, String projectId, String pipelineId) {
    CIPipelineBaseline ciPipelineBaseline;

    // Try using parentUniqueId if feature flag is enabled
    if (featureFlagService.isEnabled(FeatureName.CI_USE_UNIQUE_PARENT_ID_FOR_QUERY, accountId)
        && ciScopeInfoHelper != null) {
      String parentUniqueId = ciScopeInfoHelper.getParentUniqueId(accountId, orgId, projectId);
      if (isNotEmpty(parentUniqueId)) {
        ciPipelineBaseline =
            ciPipelineBaselineRespository.findByParentUniqueIdAndPipelineId(parentUniqueId, pipelineId);
      } else {
        ciPipelineBaseline = ciPipelineBaselineRespository.findByAccountIdAndOrgIdAndProjectIdAndPipelineId(
            accountId, orgId, projectId, pipelineId);
      }
    } else {
      ciPipelineBaseline = ciPipelineBaselineRespository.findByAccountIdAndOrgIdAndProjectIdAndPipelineId(
          accountId, orgId, projectId, pipelineId);
    }

    if (ciPipelineBaseline == null) {
      return null;
    }

    return ciPipelineBaseline.getFirstFullRunPlanExecutionId();
  }
}
