/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.helper;

import io.harness.beans.FeatureName;
import io.harness.data.structure.EmptyPredicate;
import io.harness.pms.pipeline.PipelineSdkPrioritySupport;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;

@Singleton
public class PipelineSdkPrioritySupportImpl implements PipelineSdkPrioritySupport {
  @Inject private PmsSdkHelper pmsSdkHelper;
  @Inject private PmsFeatureFlagHelper pmsFeatureFlagHelper;

  @Override
  public boolean isHonorPipelineSdkPriorityEnabled(String accountIdentifier) {
    return EmptyPredicate.isNotEmpty(accountIdentifier)
        && pmsFeatureFlagHelper.isEnabled(
            accountIdentifier, FeatureName.PIPE_HONOR_PIPELINE_SDK_PRIORITY_IN_EXECUTION_URL);
  }

  @Override
  public Map<String, Integer> getPipelineSdkPriority() {
    return pmsSdkHelper.getPipelineSdkPriority();
  }
}
