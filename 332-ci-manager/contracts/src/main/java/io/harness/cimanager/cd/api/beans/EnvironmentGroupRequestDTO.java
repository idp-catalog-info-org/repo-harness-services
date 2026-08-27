/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.cd.api.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.EntityName;
import io.harness.validator.NGRegexValidatorConstants;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@OwnedBy(CDC)
@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "EnvironmentGroupRequest", description = "This is the EnvironmentGroupRequest defined in Harness")
public class EnvironmentGroupRequestDTO {
  @Schema(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) String orgIdentifier;
  @Schema(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) String projectIdentifier;
  @Pattern(regexp = NGRegexValidatorConstants.IDENTIFIER_PATTERN)
  @Schema(description = "Identifier of the Environment Group.")
  @EntityIdentifier
  @NotNull
  String identifier;

  @Schema(description = "Name of the Environment Group Request.") @EntityName String name;
  @Schema(description = "List of scoped identifiers of environments referred") @NotNull List<String> environments;

  @Schema(description = "Color of the Environment Group.") String color;
  @Schema(description = NGCommonEntityConstants.DESCRIPTION) String description;
  @Schema(description = NGCommonEntityConstants.TAGS) Map<String, String> tags;

  @Schema(description = "YAML version for the Environment Group Request") @NotEmpty String harnessVersion;
}
