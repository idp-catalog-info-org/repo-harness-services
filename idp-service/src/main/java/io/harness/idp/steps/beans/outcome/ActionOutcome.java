/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.idp.steps.beans.outcome;
import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.sdk.core.data.Outcome;

import java.util.Map;
import lombok.Builder;
import lombok.Value;

@OwnedBy(HarnessTeam.IDP)
@Value
@Builder
@RecasterAlias("io.harness.steps.idp.action.step.ActionOutcome")
public class ActionOutcome implements Outcome {
  int httpResponseCode;
  String httpResponseBody;
  Map<String, Object> outputVariables;
}
