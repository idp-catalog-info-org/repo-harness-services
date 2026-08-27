/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.eventmapper.filters;

import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;

import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
public class TriggerFilterUtils {
  public List<TriggerDetails> mapToTriggerDetails(List<NGTriggerEntity> ngTriggerEntityList) {
    return ngTriggerEntityList.stream()
        .map(entity -> TriggerDetails.builder().ngTriggerEntity(entity).build())
        .toList();
  }
}
