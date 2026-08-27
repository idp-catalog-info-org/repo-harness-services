/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.beans.unified;

import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonTypeName(YAMLFieldNameConstants.HARNESS)
@JsonIgnoreProperties(ignoreUnknown = true)
public class UnifiedHarnessFileStoreSource implements UnifiedShellScriptBaseSource {
  private ParameterField<String> file;

  @Override
  public UnifiedShellScriptSourceType getType() {
    return UnifiedShellScriptSourceType.HARNESS;
  }
}
