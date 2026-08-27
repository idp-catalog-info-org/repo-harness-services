/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityIdentifier;

import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@OwnedBy(CDC)
@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(name = "ApprovalServiceAccount", description = "Service Account details used in Approvals.")
public class ApprovalServiceAccountDTO {
  @Schema(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) String accountIdentifier;
  @Schema(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE)
  @EntityIdentifier(allowBlank = true)
  String orgIdentifier;
  @Schema(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE)
  @EntityIdentifier(allowBlank = true)
  String projectIdentifier;
  @Schema(description = "Identifier of the ServiceAccount.") @NotNull @EntityIdentifier String identifier;
}
