/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.expressions.functors;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.expression.LateBindingValue;
import io.harness.ngtriggers.beans.dto.BasicPipelineInfo;

import java.util.HashMap;
import java.util.Map;

@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineBasicInfoFunctor implements LateBindingValue {
  BasicPipelineInfo basicPipelineInfo;

  public PipelineBasicInfoFunctor(BasicPipelineInfo basicPipelineInfo) {
    this.basicPipelineInfo = basicPipelineInfo;
  }

  @Override
  public Object bind() {
    Map<String, Object> jsonObject = new HashMap<>();
    if (basicPipelineInfo == null) {
      return jsonObject;
    }
    jsonObject.put("name", basicPipelineInfo.getPipelineName());
    jsonObject.put("identifier", basicPipelineInfo.getPipelineIdentifier());
    return jsonObject;
  }
}