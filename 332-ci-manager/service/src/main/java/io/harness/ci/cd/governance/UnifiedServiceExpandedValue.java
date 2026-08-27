/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.cd.governance;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.pms.yaml.YAMLFieldNameConstants.ITEMS;
import static io.harness.pms.yaml.YAMLFieldNameConstants.SERVICE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.app.beans.entities.ServiceBasicInfo;
import io.harness.pms.sdk.core.governance.handler.ExpandedValue;
import io.harness.yaml.utils.JsonPipelineUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.SneakyThrows;

@OwnedBy(CI)
@Data
@Builder
public class UnifiedServiceExpandedValue implements ExpandedValue {
  private List<ServiceBasicInfo> servicesInfo;
  @Override
  public String getKey() {
    return SERVICE;
  }

  @SneakyThrows
  @Override
  public String toJson() {
    if (servicesInfo.size() == 1) {
      return JsonPipelineUtils.writeJsonString(servicesInfo.get(0));
    }
    Map<String, Object> multiSvcMap = new HashMap<>();
    multiSvcMap.put(ITEMS, servicesInfo);
    return JsonPipelineUtils.writeJsonString(multiSvcMap);
  }
}
