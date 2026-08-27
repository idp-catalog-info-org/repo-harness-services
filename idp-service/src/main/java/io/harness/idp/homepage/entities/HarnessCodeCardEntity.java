/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.idp.homepage.entities;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.DbAliases;
import io.harness.spec.server.idp.v1.model.Card;
import io.harness.spec.server.idp.v1.model.HarnessCodeCard;

import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@FieldNameConstants(innerTypeName = "HarnessCodeCardEntityKeys")
@StoreIn(DbAliases.IDP)
@Persistent
@OwnedBy(HarnessTeam.IDP)
@TypeAlias("io.harness.idp.homepage.entities.HarnessCodeCardEntity")
public class HarnessCodeCardEntity extends CardEntity {
  @NotNull private String size;

  @Override
  public Card.TypeEnum getType() {
    return Card.TypeEnum.HARNESS_CODE;
  }

  public static class HarnessCodeCardMapper implements CardMapper<HarnessCodeCard, HarnessCodeCardEntity> {
    @Override
    public HarnessCodeCardEntity fromDto(HarnessCodeCard harnessCodeCard, String accountIdentifier) {
      HarnessCodeCardEntity harnessCodeCardEntity =
          HarnessCodeCardEntity.builder().size(harnessCodeCard.getSize()).build();
      setCommonFieldsEntity(harnessCodeCard, harnessCodeCardEntity, accountIdentifier);
      return harnessCodeCardEntity;
    }

    @Override
    public HarnessCodeCard toDto(HarnessCodeCardEntity harnessCodeCardEntity) {
      HarnessCodeCard harnessCodeCard = new HarnessCodeCard();
      harnessCodeCard.setSize(harnessCodeCardEntity.getSize());
      setCommonFieldsDto(harnessCodeCardEntity, harnessCodeCard);
      return harnessCodeCard;
    }
  }
}
