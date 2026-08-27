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
import io.harness.spec.server.idp.v1.model.PagerDutyCard;

import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@FieldNameConstants(innerTypeName = "PagerDutyCardEntityKeys")
@StoreIn(DbAliases.IDP)
@Persistent
@OwnedBy(HarnessTeam.IDP)
@TypeAlias("io.harness.idp.homepage.entities.PagerDutyCardEntity")
public class PagerDutyCardEntity extends CardEntity {
  @NotNull private String size;

  @Override
  public Card.TypeEnum getType() {
    return Card.TypeEnum.PAGER_DUTY;
  }

  public static class PagerDutyCardMapper implements CardMapper<PagerDutyCard, PagerDutyCardEntity> {
    @Override
    public PagerDutyCardEntity fromDto(PagerDutyCard pagerDutyCard, String accountIdentifier) {
      PagerDutyCardEntity pagerDutyCardEntity = PagerDutyCardEntity.builder().size(pagerDutyCard.getSize()).build();
      setCommonFieldsEntity(pagerDutyCard, pagerDutyCardEntity, accountIdentifier);
      return pagerDutyCardEntity;
    }

    @Override
    public PagerDutyCard toDto(PagerDutyCardEntity pagerDutyCardEntity) {
      PagerDutyCard pagerDutyCard = new PagerDutyCard();
      pagerDutyCard.setSize(pagerDutyCardEntity.getSize());
      setCommonFieldsDto(pagerDutyCardEntity, pagerDutyCard);
      return pagerDutyCard;
    }
  }
}
