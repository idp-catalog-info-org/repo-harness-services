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
import io.harness.spec.server.idp.v1.model.TopVisitedCard;

import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@FieldNameConstants(innerTypeName = "TopVisitedCardEntityKeys")
@StoreIn(DbAliases.IDP)
@Persistent
@OwnedBy(HarnessTeam.IDP)
@TypeAlias("io.harness.idp.homepage.entities.TopVisitedCardEntity")
public class TopVisitedCardEntity extends CardEntity {
  @NotNull private String description;
  @NotNull private String size;

  @Override
  public Card.TypeEnum getType() {
    return Card.TypeEnum.TOP_VISITED;
  }

  public static class TopVisitedCardMapper implements CardMapper<TopVisitedCard, TopVisitedCardEntity> {
    @Override
    public TopVisitedCardEntity fromDto(TopVisitedCard topVisitedCard, String accountIdentifier) {
      TopVisitedCardEntity topVisitedCardEntity = TopVisitedCardEntity.builder()
                                                      .description(topVisitedCard.getDescription())
                                                      .size(topVisitedCard.getSize())
                                                      .build();
      setCommonFieldsEntity(topVisitedCard, topVisitedCardEntity, accountIdentifier);
      return topVisitedCardEntity;
    }

    @Override
    public TopVisitedCard toDto(TopVisitedCardEntity topVisitedCardEntity) {
      TopVisitedCard topVisitedCard = new TopVisitedCard();
      topVisitedCard.setDescription(topVisitedCardEntity.getDescription());
      topVisitedCard.setSize(topVisitedCardEntity.getSize());
      setCommonFieldsDto(topVisitedCardEntity, topVisitedCard);
      return topVisitedCard;
    }
  }
}
