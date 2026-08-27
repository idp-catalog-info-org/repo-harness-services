/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service.yamlConversion;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.pms.pipeline.yamlConversion.PipelineYamlConversionEntity;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
public interface PipelineYamlConversionEntityService {
  void createOrUpdateEntity(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String v0Yaml, String v1Yaml);

  PipelineYamlConversionEntity get(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String pipelineIdentifier);

  String convertV0PipelineYamlToV1(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String pipelineVersion, String v0_pipeline_yaml, boolean shouldRunAsV1);
}
