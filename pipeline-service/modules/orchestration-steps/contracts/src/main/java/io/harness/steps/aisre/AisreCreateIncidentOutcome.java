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
import java.util.List;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CHAOS)
@Value
@Builder
@TypeAlias("aisreCreateIncidentOutcome")
@JsonTypeName("aisreCreateIncidentOutcome")
@RecasterAlias("io.harness.steps.aisre.AisreCreateIncidentOutcome")
public class AisreCreateIncidentOutcome implements Outcome {
  // Human-facing incident id, e.g. "INC-123".
  String incidentId;
  // Deep link to the incident in AI SRE.
  String incidentUrl;
  // On-call users resolved when pageOnCall was requested at create time.
  List<AssignedResponder> assignedResponders;

  @Value
  @Builder
  public static class AssignedResponder {
    String userId;
    String displayName;
    String email;
  }
}
