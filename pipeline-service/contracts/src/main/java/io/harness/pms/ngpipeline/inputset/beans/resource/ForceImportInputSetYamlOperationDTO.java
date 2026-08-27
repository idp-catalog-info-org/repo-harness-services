/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.beans.resource;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.autosync.ForceImportYamlOperationDTO;
import io.harness.pms.yaml.HarnessYamlVersion;

import lombok.Data;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
public class ForceImportInputSetYamlOperationDTO extends ForceImportYamlOperationDTO {
  String pipelineIdentifier;
  String version;

  public String getVersion() {
    if (isEmpty(version)) {
      return HarnessYamlVersion.V0;
    }
    return version;
  }
}
