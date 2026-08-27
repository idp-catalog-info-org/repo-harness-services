/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.cd.api.beans;

import static io.harness.annotations.dev.HarnessTeam.CI;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityName;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import javax.validation.constraints.NotEmpty;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@OwnedBy(CI)
@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "InfrastructureRequest", description = "This is the InfrastructureRequest entity defined in Harness")
public class InfrastructureRequestDTO {
  @Schema(description = "identifier of the infrastructure") @NotEmpty String identifier;
  @Schema(description = "organisation identifier of the infrastructure") String orgIdentifier;
  @Schema(description = "project identifier of the infrastructure") String projectIdentifier;
  @Schema(description = "environment reference of the infrastructure") @NotEmpty String envIdentifier;

  @EntityName @Schema(description = "name of the infrastructure") String name;
  @Schema(description = "description of the infrastructure") String description;
  @Schema(description = "tags associated with the infrastructure") Map<String, String> tags;

  @Schema(description = "yaml spec of the infrastructure. Just yaml alone is sufficient to create an infrastructure.",
      required = true)
  @NotEmpty
  String yaml;

  @Schema(description = "YAML version for the infrastructure Request") @NotEmpty String harnessVersion;
}
