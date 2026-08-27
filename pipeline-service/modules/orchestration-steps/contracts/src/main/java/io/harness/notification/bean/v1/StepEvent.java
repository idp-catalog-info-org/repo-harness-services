/*
 * Copyright 2026 Harness Inc. All rights reserved.
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
import io.harness.notification.v1.StepEventConfig;
import io.harness.notification.v1.StepEventType;
import io.harness.pms.yaml.YAMLFieldNameConstants;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Value;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Value
public class StepEvent {
  List<StepEventConfig> configs;

  @JsonCreator
  public StepEvent(Object stepEventObj) {
    this.configs = new ArrayList<>();
    if (stepEventObj instanceof String) {
      // - step: failed
      this.configs.add(StepEventConfig.builder().type(toStepEventType((String) stepEventObj)).build());
    } else if (stepEventObj instanceof List) {
      parseStepEventList((List<?>) stepEventObj);
    }
  }

  private void parseStepEventList(List<?> stepEventList) {
    for (Object stepEventItem : stepEventList) {
      if (stepEventItem instanceof String) {
        // - step:
        //   - failed
        configs.add(StepEventConfig.builder().type(toStepEventType((String) stepEventItem)).build());
      } else if (stepEventItem instanceof Map) {
        // - step:
        //   - failed: all
        //   - failed:
        //     - stage1.step1
        //     - stage2.group1.step2
        parseStepEvent((Map<String, Object>) stepEventItem);
      }
    }
  }

  private void parseStepEvent(Map<String, Object> stepEvent) {
    for (Map.Entry<String, Object> entry : stepEvent.entrySet()) {
      StepEventType eventType = toStepEventType(entry.getKey());
      if (entry.getValue() instanceof String) {
        // failed: all
        if (YAMLFieldNameConstants.ALL.equals(entry.getValue())) {
          configs.add(StepEventConfig.builder().type(eventType).build());
        } else {
          configs.add(StepEventConfig.builder().type(eventType).refs(List.of((String) entry.getValue())).build());
        }
      } else if (entry.getValue() instanceof List) {
        configs.add(StepEventConfig.builder().type(eventType).refs((List<String>) entry.getValue()).build());
      } else {
        configs.add(StepEventConfig.builder().type(eventType).build());
      }
    }
  }

  private StepEventType toStepEventType(String value) {
    if (YAMLFieldNameConstants.ALL.equals(value)) {
      return StepEventType.FAILED;
    }
    return StepEventType.valueOf(value.toUpperCase());
  }
}
