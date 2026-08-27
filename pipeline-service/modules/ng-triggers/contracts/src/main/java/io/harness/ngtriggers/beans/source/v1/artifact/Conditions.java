/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.source.v1.artifact;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidRequestException;
import io.harness.ngtriggers.beans.source.v1.webhook.condition.TriggerEventDataCondition;
import io.harness.ngtriggers.conditionchecker.ConditionOperator;

import java.util.List;
import java.util.stream.Collectors;
import lombok.Value;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@Value
public class Conditions {
  List<TriggerEventDataCondition> event;
  List<TriggerEventDataCondition> metadata;
  String jexl;

  public List<io.harness.ngtriggers.beans.source.webhook.v2.condition.TriggerEventDataCondition> getEvent() {
    return event.stream().map(this::toTriggerEventDataCondition).collect(Collectors.toList());
  }

  public List<io.harness.ngtriggers.beans.source.webhook.v2.condition.TriggerEventDataCondition> getMetadata() {
    return metadata.stream().map(this::toTriggerEventDataCondition).collect(Collectors.toList());
  }

  private io.harness.ngtriggers.beans.source.webhook.v2.condition.TriggerEventDataCondition toTriggerEventDataCondition(
      TriggerEventDataCondition triggerEventDataCondition) {
    return io.harness.ngtriggers.beans.source.webhook.v2.condition.TriggerEventDataCondition.builder()
        .value(triggerEventDataCondition.getValue())
        .operator(toConditionOperator(triggerEventDataCondition.getOperator()))
        .key(triggerEventDataCondition.getKey())
        .build();
  }

  private ConditionOperator toConditionOperator(
      io.harness.ngtriggers.beans.source.v1.webhook.condition.ConditionOperator operatorEnum) {
    switch (operatorEnum) {
      case IN:
        return ConditionOperator.IN;
      case NOT_IN:
        return ConditionOperator.NOT_IN;
      case EQUALS:
        return ConditionOperator.EQUALS;
      case NOT_EQUALS:
        return ConditionOperator.NOT_EQUALS;
      case REGEX:
        return ConditionOperator.REGEX;
      case CONTAINS:
        return ConditionOperator.CONTAINS;
      case DOES_NOT_CONTAIN:
        return ConditionOperator.DOES_NOT_CONTAIN;
      case ENDS_WITH:
        return ConditionOperator.ENDS_WITH;
      case STARTS_WITH:
        return ConditionOperator.STARTS_WITH;
      default:
        throw new InvalidRequestException("Conditional Operator " + operatorEnum + " is invalid");
    }
  }
}
