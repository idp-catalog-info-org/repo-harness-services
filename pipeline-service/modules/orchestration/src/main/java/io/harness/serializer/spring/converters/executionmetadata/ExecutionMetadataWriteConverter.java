/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer.spring.converters.executionmetadata;

import io.harness.data.structure.EmptyPredicate;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.serializer.spring.ProtoWriteConverter;

import java.util.HashMap;
import java.util.Map;

public class ExecutionMetadataWriteConverter extends ProtoWriteConverter<ExecutionMetadata> {
  @Override
  public Map<String, Object> getFieldMetadata(ExecutionMetadata entity) {
    Map<String, Object> m = new HashMap<>();
    m.put("pipelineIdentifier", entity.getPipelineIdentifier());
    if (entity.hasTriggerInfo()) {
      Map<String, Object> triggerInfoMap =
          new HashMap<>(Map.of("triggerType", entity.getTriggerInfo().getTriggerType()));
      // TODO: Improve better index in PlanExecution
      if (entity.getTriggerInfo().hasTriggeredBy()
          && EmptyPredicate.isNotEmpty(entity.getTriggerInfo().getTriggeredBy().getExtraInfoMap())
          && entity.getTriggerInfo().getTriggeredBy().getExtraInfoMap().containsKey(
              "execution_trigger_tag_needed_for_abort")) {
        triggerInfoMap.put("triggeredBy",
            Map.of("extraInfo",
                Map.of("execution_trigger_tag_needed_for_abort",
                    entity.getTriggerInfo().getTriggeredBy().getExtraInfoMap().get(
                        "execution_trigger_tag_needed_for_abort"))));
      }
      m.put("triggerInfo", triggerInfoMap);
    }
    return m;
  }
}
