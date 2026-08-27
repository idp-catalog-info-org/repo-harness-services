/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.helper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.pipeline.PipelineEntityUtils;
import io.harness.pms.pipeline.yaml.UnifiedPipelineYaml;
import io.harness.pms.yaml.YamlUtils;

import java.io.IOException;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.PIPELINE)
public class UnifiedPipelineExecutionUtils {
  public UnifiedPipelineYaml getUnifiedPipeline(String unifiedPipelineYaml) {
    try {
      return YamlUtils.read(unifiedPipelineYaml, UnifiedPipelineYaml.class);
    } catch (IOException e) {
      if (YamlUtils.isYamlSizeLimitExceeded(e)) {
        throw new InvalidRequestException(PipelineEntityUtils.PIPELINE_YAML_SIZE_LIMIT_EXCEEDED_MESSAGE, e);
      }
      throw new InvalidRequestException("Cannot create unified pipeline entity due to " + e.getMessage(), e);
    }
  }

  public boolean shouldAllowStageExecutions(String unifiedPipelineYaml) {
    return getUnifiedPipeline(unifiedPipelineYaml).isAllowStageExecutions();
  }

  public boolean isFixedInputsOnRerun(String unifiedPipelineYaml) {
    return getUnifiedPipeline(unifiedPipelineYaml).isFixedInputsOnRerun();
  }
}
