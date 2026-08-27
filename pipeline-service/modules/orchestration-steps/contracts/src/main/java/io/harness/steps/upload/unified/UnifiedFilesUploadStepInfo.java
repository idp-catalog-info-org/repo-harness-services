/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.upload.unified;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.steps.upload.FilesUploadStepParameters;
import io.harness.yaml.core.variables.v1.NGVariableV1Wrapper;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Value;

@OwnedBy(PIPELINE)
@Value
@JsonIgnoreProperties(ignoreUnknown = true)
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
public class UnifiedFilesUploadStepInfo {
  NGVariableV1Wrapper inputs;

  public SpecParameters getSpecParameters() {
    // The planCreator will convert inputs to v0 inputVariables and outputVariables
    // For now, return empty parameters - the conversion happens in the planCreator
    return FilesUploadStepParameters.infoBuilder().build();
  }
}
