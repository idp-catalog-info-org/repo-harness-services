/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.harness.beans;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;

import java.util.Date;
import java.util.List;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_APPROVALS})
public abstract class HarnessApprovalBaseActivityDTO {
  public abstract EmbeddedUserDTO getUser();
  public abstract HarnessApprovalAction getAction();
  public abstract List<ApproverInput> getApproverInputs();
  public abstract String getComments();
  public abstract Date getApprovedAt();
}
