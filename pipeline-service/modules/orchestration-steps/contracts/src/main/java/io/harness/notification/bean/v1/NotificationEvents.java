/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.bean.v1;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.notification.v1.PipelineEventType;
import io.harness.notification.v1.TriggerEventType;
import io.harness.pms.yaml.YAMLFieldNameConstants;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Data;

@OwnedBy(HarnessTeam.CI)
@Data
public class NotificationEvents {
  List<PipelineEventType> pipelineEvents;
  StageEvent stageEvent;
  StepEvent stepEvent;
  List<TriggerEventType> triggerEvents;

  @JsonCreator
  public NotificationEvents(List<Map<String, Object>> eventObjs) {
    if (EmptyPredicate.isNotEmpty(eventObjs)) {
      for (Map<String, Object> eventObj : eventObjs) {
        for (Map.Entry<String, Object> entry : eventObj.entrySet()) {
          switch (entry.getKey()) {
            case YAMLFieldNameConstants.PIPELINE:
              this.pipelineEvents = parsePipelineEvents(entry.getValue());
              break;
            case YAMLFieldNameConstants.STAGE:
              this.stageEvent = new StageEvent(entry.getValue());
              break;
            case YAMLFieldNameConstants.STEP:
              this.stepEvent = new StepEvent(entry.getValue());
              break;
            case YAMLFieldNameConstants.TRIGGER:
              this.triggerEvents = parseTriggerEvents(entry.getValue());
              break;
            default:
              break;
          }
        }
      }
    }
  }

  private PipelineEventType toPipelineEventType(String value) {
    if (YAMLFieldNameConstants.ALL.equals(value)) {
      return PipelineEventType.ALL;
    }
    return PipelineEventType.valueOf(value.toUpperCase());
  }
  private List<PipelineEventType> parsePipelineEvents(Object value) {
    List<PipelineEventType> events = new ArrayList<>();
    if (value instanceof String) {
      events.add(toPipelineEventType((String) value));
    } else if (value instanceof List) {
      for (Object type : (List<?>) value) {
        events.add(toPipelineEventType((String) type));
      }
    }
    return events;
  }

  private List<TriggerEventType> parseTriggerEvents(Object value) {
    List<TriggerEventType> events = new ArrayList<>();
    if (value instanceof String) {
      events.add(toTriggerEventType((String) value));
    } else if (value instanceof List) {
      for (Object type : (List<?>) value) {
        events.add(toTriggerEventType((String) type));
      }
    }
    return events;
  }

  private TriggerEventType toTriggerEventType(String value) {
    if (YAMLFieldNameConstants.ALL.equals(value)) {
      return TriggerEventType.FAILED;
    }
    return TriggerEventType.valueOf(value.toUpperCase());
  }
}
