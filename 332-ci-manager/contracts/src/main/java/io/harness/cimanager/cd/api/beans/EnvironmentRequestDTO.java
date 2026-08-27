/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.cd.api.beans;

import static io.harness.annotations.dev.HarnessTeam.CI;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.EntityName;
import io.harness.ng.core.environment.beans.EnvironmentType;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@OwnedBy(CI)
@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(NON_NULL)
@Schema(name = "EnvironmentRequest", description = "Environment Request details defined in Harness.")
public class EnvironmentRequestDTO {
  @Schema(description = "Identifier of the Environment Request.") @NotEmpty @EntityIdentifier String identifier;
  @Schema(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) String orgIdentifier;
  @Schema(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) String projectIdentifier;

  @Schema(description = "Name of the Environment Request.") @EntityName String name;
  @Schema(description = NGCommonEntityConstants.DESCRIPTION) String description;
  @Schema(description = NGCommonEntityConstants.TAGS) Map<String, String> tags;

  @Schema(description = "Color of the Environment.") String color;
  @Schema(description = "Specify the environment type whether production or Preproduction. ")
  @NotNull
  @ApiModelProperty(required = true)
  EnvironmentType type;

  @Schema(description = "YAML version for the Environment Request") @NotEmpty String harnessVersion;
}
