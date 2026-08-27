/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
public class PipelineEventUtils {
  public static String getResourceName(
      String identifier, String name, String accountIdentifier, PmsFeatureFlagHelper pmsFeatureFlagHelper) {
    if (pmsFeatureFlagHelper != null
        && pmsFeatureFlagHelper.isEnabled(accountIdentifier, FeatureName.PIPE_USE_PIPELINE_IDENTIFIER_IN_AUDIT_LOGS)) {
      return identifier;
    }
    return name;
  }
}
