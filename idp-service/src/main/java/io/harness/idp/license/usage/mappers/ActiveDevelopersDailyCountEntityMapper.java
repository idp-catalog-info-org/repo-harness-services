/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.license.usage.mappers;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.idp.license.usage.dto.ActiveDevelopersTrendCountDTO;
import io.harness.idp.license.usage.dto.ActiveDevelopersTrendCountDTOV2;
import io.harness.idp.license.usage.entities.ActiveDevelopersDailyCountEntity;

import java.util.Collections;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
public class ActiveDevelopersDailyCountEntityMapper {
  public ActiveDevelopersTrendCountDTO toDto(ActiveDevelopersDailyCountEntity activeDevelopersDailyCountEntity) {
    return ActiveDevelopersTrendCountDTO.builder()
        .date(activeDevelopersDailyCountEntity.getDateInStringFormat())
        .count(activeDevelopersDailyCountEntity.getCount())
        .build();
  }

  public ActiveDevelopersTrendCountDTOV2 toDtoV2(ActiveDevelopersDailyCountEntity activeDevelopersDailyCountEntity) {
    return ActiveDevelopersTrendCountDTOV2.builder()
        .date(activeDevelopersDailyCountEntity.getDateInStringFormat())
        .count(activeDevelopersDailyCountEntity.getCount())
        .uniqueUserIdentifiers(activeDevelopersDailyCountEntity.getUniqueUserIdentifiers() != null
                ? activeDevelopersDailyCountEntity.getUniqueUserIdentifiers()
                : Collections.emptySet())
        .build();
  }
}
