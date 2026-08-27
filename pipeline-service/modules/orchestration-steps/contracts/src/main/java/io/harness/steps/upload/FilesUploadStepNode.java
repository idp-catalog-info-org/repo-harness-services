/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.upload;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.internal.PmsAbstractStepNode;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.yaml.core.StepSpecType;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName(StepSpecTypeConstants.UPLOAD)
@TypeAlias("filesUploadStepNode")
@OwnedBy(PIPELINE)
@RecasterAlias("io.harness.steps.upload.FilesUploadStepNode")
public class FilesUploadStepNode extends PmsAbstractStepNode {
  @JsonProperty("type") @NotNull FilesUploadStepNode.StepType type = StepType.FilesUpload;
  @NotNull
  @JsonProperty("spec")
  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
  FilesUploadStepInfo uploadStepInfo;

  @JsonIgnore
  public String getType() {
    return StepSpecTypeConstants.UPLOAD;
  }
  @JsonIgnore
  public StepSpecType getStepSpecType() {
    return uploadStepInfo;
  }

  @Getter
  enum StepType {
    FilesUpload(StepSpecTypeConstants.UPLOAD);
    String name;
    StepType(String name) {
      this.name = name;
    }
  }
}