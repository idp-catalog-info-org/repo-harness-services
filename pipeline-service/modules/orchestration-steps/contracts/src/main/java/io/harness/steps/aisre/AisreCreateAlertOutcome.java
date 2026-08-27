/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.aisre;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.sdk.core.data.Outcome;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CHAOS)
@Value
@Builder
@TypeAlias("aisreCreateAlertOutcome")
@JsonTypeName("aisreCreateAlertOutcome")
@RecasterAlias("io.harness.steps.aisre.AisreCreateAlertOutcome")
public class AisreCreateAlertOutcome implements Outcome {
  // Human-facing alert id, e.g. "ALERT-123".
  String alertId;
  // Deep link to the alert in AI SRE.
  String alertUrl;
}
