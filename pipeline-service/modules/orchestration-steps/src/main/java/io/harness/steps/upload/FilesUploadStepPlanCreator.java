/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.steps.upload;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidRequestException;
import io.harness.plancreator.steps.internal.PMSStepPlanCreatorV2;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.steps.StepSpecTypeConstants;

import com.google.common.collect.Sets;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@Slf4j
public class FilesUploadStepPlanCreator extends PMSStepPlanCreatorV2<FilesUploadStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.UPLOAD);
  }

  @Override
  public Class<FilesUploadStepNode> getFieldClass() {
    return FilesUploadStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, FilesUploadStepNode field) {
    // We want to restrict looping/parallelism on Upload step, since we are restricting the number of upload steps in a
    // pipeline.
    if (field.getStrategy() != null) {
      log.error(
          "Upload step restrictions: Looping or parallelism is not allowed. Please ensure that the upload steps in your pipeline are configured to execute sequentially.");
      throw new InvalidRequestException(
          "Upload step restrictions: Looping or parallelism is not allowed. Please ensure that the upload steps in your pipeline are configured to execute sequentially.");
    }
    return super.createPlanForField(ctx, field);
  }
  @Override
  public Set<String> getSupportedYamlVersions() {
    return Set.of(HarnessYamlVersion.V0);
  }
}
