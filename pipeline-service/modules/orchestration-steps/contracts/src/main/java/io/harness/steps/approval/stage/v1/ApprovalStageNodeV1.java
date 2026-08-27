/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.stage.v1;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.stages.stage.v1.AbstractStageNodeV1;
import io.harness.pms.yaml.YAMLFieldNameConstants;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Value;

@Value
@JsonTypeName(YAMLFieldNameConstants.APPROVAL_V1)
@OwnedBy(PIPELINE)
public class ApprovalStageNodeV1 extends AbstractStageNodeV1 {
  String type = YAMLFieldNameConstants.APPROVAL_V1;
  ApprovalStageConfigV1 spec;
}
