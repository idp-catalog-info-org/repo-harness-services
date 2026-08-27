/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.bean.v1;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.notification.v1.StageEventConfig;
import io.harness.notification.v1.StageEventConfig.StageEventConfigBuilder;
import io.harness.notification.v1.StageEventType;
import io.harness.pms.yaml.YAMLFieldNameConstants;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Value;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Value
public class StageEvent {
  List<StageEventConfig> configs;

  @JsonCreator
  public StageEvent(Object stageEventObj) {
    this.configs = new ArrayList<>();

    if (stageEventObj instanceof String) {
      //  - stage: all
      this.configs.add(
          StageEventConfig.builder().type(StageEventType.ALL).refs(List.of(YAMLFieldNameConstants.ALL)).build());
    } else if (stageEventObj instanceof List) {
      parseStageEventList((List<?>) stageEventObj);
    }
  }

  private void parseStageEventList(List<?> stageEventList) {
    for (Object stageEventItem : stageEventList) {
      if (stageEventItem instanceof String) {
        //    - stage:
        //      - failed
        configs.add(StageEventConfig.builder()
                        .type(StageEventType.valueOf(((String) stageEventItem).toUpperCase()))
                        .refs(List.of(YAMLFieldNameConstants.ALL))
                        .build());
      } else if (stageEventItem instanceof Map) {
        //    - stage:
        //       - failed: all
        //       - failed:
        //         - ref1
        //         - ref2
        parseStageEvent((Map<String, Object>) stageEventItem);
      }
    }
  }

  private void parseStageEvent(Map<String, Object> stageEvent) {
    for (Map.Entry<String, Object> entry : stageEvent.entrySet()) {
      StageEventType eventType = StageEventType.valueOf(entry.getKey().toUpperCase());
      StageEventConfigBuilder builder = StageEventConfig.builder().type(eventType);
      if (entry.getValue() instanceof String) {
        //    - stage:
        //       - failed: all
        this.configs.add(YAMLFieldNameConstants.ALL.equals(entry.getValue())
                ? builder.build()
                : builder.refs(List.of(YAMLFieldNameConstants.ALL)).build());
      } else if (entry.getValue() instanceof List) {
        // List of refs
        this.configs.add(StageEventConfig.builder().type(eventType).refs((List<String>) entry.getValue()).build());
      }
    }
  }
}
