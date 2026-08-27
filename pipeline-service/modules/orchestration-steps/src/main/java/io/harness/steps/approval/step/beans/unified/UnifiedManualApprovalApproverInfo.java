/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.beans.unified;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.validation.OneOfField;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@OwnedBy(HarnessTeam.PIPELINE)
@OneOfField(fields = {"regex", "enumList"}, nullable = true)
public class UnifiedManualApprovalApproverInfo {
  @JsonProperty(YAMLFieldNameConstants.PATTERN) private ParameterField<String> regex;
  private String description;
  @JsonProperty(YAMLFieldNameConstants.MULTI_SELECT) private boolean multiSelect;
  @JsonProperty(YAMLFieldNameConstants.ENUM) private ParameterField<List<String>> enumList;
  private boolean required;
  @JsonProperty(YAMLFieldNameConstants.DEFAULT) private ParameterField<String> defaultValue;
}
