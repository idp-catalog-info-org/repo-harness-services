/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.iterators.mapper;

import static io.harness.idp.common.DateUtils.midnightInMilliseconds;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.idp.iterators.bean.Iterator;
import io.harness.idp.iterators.entity.IteratorEntity;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
public class IteratorMapper {
  public IteratorEntity fromDTO(Iterator iterator) {
    return IteratorEntity.builder()
        .id(iterator.getId())
        .name(iterator.getName())
        .nextIteration(iterator.isAfterMidnight()
                ? midnightInMilliseconds() + iterator.getInitialDelay()
                : (iterator.getInitialDelay() > 0 ? System.currentTimeMillis() + iterator.getInitialDelay() : -1))
        .build();
  }

  public List<IteratorEntity> toEntityList(List<Iterator> iterators) {
    List<IteratorEntity> iteratorEntities = new ArrayList<>();
    iterators.forEach(iterator -> iteratorEntities.add(fromDTO(iterator)));
    return iteratorEntities;
  }
}
