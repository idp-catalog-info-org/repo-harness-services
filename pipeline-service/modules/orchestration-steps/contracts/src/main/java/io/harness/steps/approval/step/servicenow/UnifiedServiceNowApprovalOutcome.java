/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.servicenow;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.sdk.core.data.Outcome;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.HashMap;
import java.util.Map;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDC)
@JsonTypeName("unifiedServiceNowApprovalOutcome")
@TypeAlias("unifiedServiceNowApprovalOutcome")
@RecasterAlias("io.harness.steps.approval.step.servicenow.UnifiedServiceNowApprovalOutcome")
public class UnifiedServiceNowApprovalOutcome extends HashMap<String, Object> implements Outcome {
  public UnifiedServiceNowApprovalOutcome(Map<String, ?> m) {
    super(m);
  }
}
