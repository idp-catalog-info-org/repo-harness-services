/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.custom;

import io.harness.annotation.RecasterAlias;
import io.harness.beans.steps.stepinfo.RunStepInfoV1;
import io.harness.plancreator.steps.common.SpecParameters;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.TypeAlias;

@Data
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
@TypeAlias("BaseCustomApprovalSpecParameters")
@RecasterAlias("io.harness.steps.approval.step.custom.BaseCustomApprovalSpecParameters")
public class BaseCustomApprovalSpecParameters implements SpecParameters {
  // This class is the base class for Custom Approval Spec Parameters.
  // Since we want to use params of CustomApprovalSpecParameters and want it untouched since V0 flow use that.
  // Hence this class which is the base class for CustomApprovalSpecParameters. Base class is currently used for V1.
  RunStepInfoV1 runStepInfo;
}
