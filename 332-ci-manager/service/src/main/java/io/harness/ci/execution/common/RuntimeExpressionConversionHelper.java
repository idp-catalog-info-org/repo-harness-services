/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.common;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.goconvert.GoConvertServiceClient;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.execution.PmsExecutionGrpcClient;
import io.harness.pms.yaml.HarnessYamlVersion;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles V0 to V1 expression conversion for converted pipelines using the go-convert gRPC service.
 */
@Singleton
@OwnedBy(HarnessTeam.CI)
@Slf4j
public class RuntimeExpressionConversionHelper {
  @Inject private CIFeatureFlagService featureFlagService;
  @Inject private GoConvertServiceClient goConvertServiceClient;
  @Inject private PmsExecutionGrpcClient pmsExecutionGrpcClient;

  public boolean isExpressionConversionEnabled(Ambiance ambiance) {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    return HarnessYamlVersion.isV1(ambiance.getMetadata().getHarnessVersion())
        && featureFlagService.isEnabled(FeatureName.PIPE_V0_TO_V1_CONVERSION, accountId)
        && ambiance.getMetadata().getIsPipelineConverted();
  }

  public String fetchPipelineYaml(Ambiance ambiance) {
    try {
      String accountId = AmbianceUtils.getAccountId(ambiance);
      return pmsExecutionGrpcClient.getPlanExecutionMetadataYaml(accountId, ambiance.getPlanExecutionId());
    } catch (Exception e) {
      log.warn("Failed to fetch pipeline yaml for expression conversion", e);
      return null;
    }
  }

  public String convertExpressions(String content, String pipelineYaml) {
    if (isEmpty(content) || isEmpty(pipelineYaml)) {
      return content;
    }
    try {
      String converted = goConvertServiceClient.convertRemoteFileExpressions(content, pipelineYaml);
      return isNotEmpty(converted) ? converted : content;
    } catch (Exception e) {
      log.warn("Failed to convert V0 expressions, proceeding with original", e);
      return content;
    }
  }
}
