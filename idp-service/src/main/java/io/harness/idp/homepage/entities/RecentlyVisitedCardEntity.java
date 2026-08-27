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
import io.harness.spec.server.idp.v1.model.RecentlyVisitedCard;

import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@FieldNameConstants(innerTypeName = "RecentlyVisitedCardEntityKeys")
@StoreIn(DbAliases.IDP)
@Persistent
@OwnedBy(HarnessTeam.IDP)
@TypeAlias("io.harness.idp.homepage.entities.RecentlyVisitedCardEntity")
public class RecentlyVisitedCardEntity extends CardEntity {
  @NotNull private String description;
  @NotNull private String size;

  @Override
  public Card.TypeEnum getType() {
    return Card.TypeEnum.RECENTLY_VISITED;
  }

  public static class RecentlyVisitedCardMapper implements CardMapper<RecentlyVisitedCard, RecentlyVisitedCardEntity> {
    @Override
    public RecentlyVisitedCardEntity fromDto(RecentlyVisitedCard recentlyVisitedCard, String accountIdentifier) {
      RecentlyVisitedCardEntity recentlyVisitedCardEntity = RecentlyVisitedCardEntity.builder()
                                                                .description(recentlyVisitedCard.getDescription())
                                                                .size(recentlyVisitedCard.getSize())
                                                                .build();
      setCommonFieldsEntity(recentlyVisitedCard, recentlyVisitedCardEntity, accountIdentifier);
      return recentlyVisitedCardEntity;
    }

    @Override
    public RecentlyVisitedCard toDto(RecentlyVisitedCardEntity recentlyVisitedCardEntity) {
      RecentlyVisitedCard recentlyVisitedCard = new RecentlyVisitedCard();
      recentlyVisitedCard.setDescription(recentlyVisitedCardEntity.getDescription());
      recentlyVisitedCard.setSize(recentlyVisitedCardEntity.getSize());
      setCommonFieldsDto(recentlyVisitedCardEntity, recentlyVisitedCard);
      return recentlyVisitedCard;
    }
  }
}
