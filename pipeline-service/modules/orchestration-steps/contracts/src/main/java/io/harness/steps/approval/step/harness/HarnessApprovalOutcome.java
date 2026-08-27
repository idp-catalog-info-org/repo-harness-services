/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.harness;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.steps.approval.step.harness.beans.HarnessApprovalActivityDTO;
import io.harness.steps.approval.step.harness.beans.HarnessApprovalBaseActivityDTO;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDC)
@Value
@Builder
@JsonTypeName("HarnessApprovalOutcome")
@TypeAlias("harnessApprovalOutcome")
@RecasterAlias("io.harness.steps.approval.step.harness.HarnessApprovalOutcome")
public class HarnessApprovalOutcome extends HarnessApprovalBaseOutcome {
  List<HarnessApprovalActivityDTO> approvalActivities;
  Map<String, String> approverInputs;

  @Override
  public List<HarnessApprovalBaseActivityDTO> getApprovalActivities() {
    return approvalActivities.stream().map(HarnessApprovalBaseActivityDTO.class ::cast).collect(Collectors.toList());
  }
}
