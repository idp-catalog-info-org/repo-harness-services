/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer.spring.converters.triggers;

import io.harness.data.structure.EmptyPredicate;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.serializer.spring.ProtoWriteConverter;

import java.util.HashMap;
import java.util.Map;

public class ExecutionTriggerInfoWriteConverter extends ProtoWriteConverter<ExecutionTriggerInfo> {
  @Override
  public Map<String, Object> getFieldMetadata(ExecutionTriggerInfo entity) {
    Map<String, Object> m = new HashMap<>();
    m.put("triggerType", entity.getTriggerType().toString());
    if (entity.hasTriggeredBy()) {
      Map<String, Object> triggerByMap = new HashMap<>();
      triggerByMap.put("triggerIdentifier", entity.getTriggeredBy().getTriggerIdentifier());
      triggerByMap.put("identifier", entity.getTriggeredBy().getIdentifier());
      triggerByMap.put("uuid", entity.getTriggeredBy().getUuid());
      if (EmptyPredicate.isNotEmpty(entity.getTriggeredBy().getExtraInfoMap())) {
        Map<String, Object> extraInfoMap = new HashMap<>();
        extraInfoMap.put("email", entity.getTriggeredBy().getExtraInfoMap().get("email"));
        extraInfoMap.put("gitUser", entity.getTriggeredBy().getExtraInfoMap().get("gitUser"));
        triggerByMap.put("extraInfo", extraInfoMap);
      }
      m.put("triggeredBy", triggerByMap);
    }
    return m;
  }
}
