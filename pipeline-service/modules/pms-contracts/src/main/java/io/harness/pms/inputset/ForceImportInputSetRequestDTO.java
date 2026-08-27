/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.inputset;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.autosync.ForceImportRequestDTO;
import io.harness.pms.yaml.HarnessYamlVersion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@OwnedBy(PIPELINE)
@Value
@SuperBuilder
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@ApiModel("ForceImportInputSetRequestDTO")
@Schema(name = "ForceImportInputSetRequestDTO", description = "")
public class ForceImportInputSetRequestDTO extends ForceImportRequestDTO {
  @Parameter(description = "Identifier of the pipeline of the input-set") String pipelineIdentifier;
  @Parameter(description = "Input set yaml version, should be one of '0' or '1'") String version;

  public String getVersion() {
    if (isEmpty(version)) {
      return HarnessYamlVersion.V0;
    }
    return version;
  }
}
