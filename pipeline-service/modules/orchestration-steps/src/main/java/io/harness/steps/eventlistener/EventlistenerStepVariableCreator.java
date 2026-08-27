/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.eventlistener;

import io.harness.pms.contracts.plan.YamlExtraProperties;
import io.harness.pms.contracts.plan.YamlProperties;
import io.harness.pms.sdk.core.pipeline.variables.GenericStepVariableCreator;
import io.harness.pms.sdk.core.variables.helper.VariableCreatorHelper;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.yaml.core.variables.NGVariable;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class EventlistenerStepVariableCreator extends GenericStepVariableCreator<EventListenerStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Collections.singleton(StepSpecTypeConstants.EVENT_LISTENER);
  }

  @Override
  public Class<EventListenerStepNode> getFieldClass() {
    return EventListenerStepNode.class;
  }

  @Override
  public YamlExtraProperties getStepExtraProperties(
      String fqnPrefix, String localNamePrefix, EventListenerStepNode config) {
    YamlExtraProperties stepExtraProperties = super.getStepExtraProperties(fqnPrefix, localNamePrefix, config);

    // empty map so that expressions are added for this even if no variables are added
    Map<String, String> outputVariablesMap = new HashMap<>();

    if (config.getEventListenerStepInfo().getOutputVariables() != null) {
      for (NGVariable outputVariable : config.getEventListenerStepInfo().getOutputVariables()) {
        outputVariablesMap.put(outputVariable.getName(), "variable");
      }
    }

    EventListenerStepOutcome eventListenerStepOutcome =
        EventListenerStepOutcome.builder().outputVariables(outputVariablesMap).build();

    List<String> outputExpressions = VariableCreatorHelper.getExpressionsInObject(eventListenerStepOutcome, "output");
    List<YamlProperties> outputProperties = new LinkedList<>();
    for (String outputExpression : outputExpressions) {
      outputProperties.add(YamlProperties.newBuilder()
                               .setFqn(fqnPrefix + "." + outputExpression)
                               .setLocalName(localNamePrefix + "." + outputExpression)
                               .setVisible(true)
                               .build());
    }

    return YamlExtraProperties.newBuilder()
        .addAllProperties(stepExtraProperties.getPropertiesList())
        .addAllOutputProperties(outputProperties)
        .build();
  }
}
