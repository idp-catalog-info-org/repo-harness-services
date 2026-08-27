/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.aisre;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.yaml.ParameterField;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CHAOS)
@UtilityClass
public class AisreStepUtils {
  public ParameterField<Map<String, String>> processFieldsList(List<AisreField> fields) {
    if (fields == null) {
      return null;
    }

    Map<String, String> fieldsMap = new HashMap<>();
    Set<String> duplicateFields = new HashSet<>();
    fields.forEach(field -> {
      if (field == null || EmptyPredicate.isEmpty(field.getName()) || ParameterField.isNull(field.getValue())) {
        return;
      }
      if (fieldsMap.containsKey(field.getName())) {
        duplicateFields.add(field.getName());
        return;
      }
      if (field.getValue().getValue() != null) {
        fieldsMap.put(field.getName(), field.getValue().getValue());
      }
    });

    if (EmptyPredicate.isNotEmpty(duplicateFields)) {
      throw new InvalidRequestException(
          String.format("Duplicate incident fields: [%s]", String.join(", ", duplicateFields)));
    }
    return fieldsMap.isEmpty() ? null : ParameterField.createValueField(fieldsMap);
  }
}
