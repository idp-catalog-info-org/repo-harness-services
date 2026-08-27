/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.search.entity.beans.cd;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_ELASTIC_SEARCH})
@Value
@Builder
@OwnedBy(HarnessTeam.PIPELINE)
public class CDPipelineSearchModuleInfo {
  List<String> serviceIdentifiers;
  List<String> envIdentifiers;
  List<String> serviceDefinitionTypes;
  List<String> artifactDisplayNames;
  List<String> helmChartVersions;
  List<String> gitOpsAppIdentifiers;
}
