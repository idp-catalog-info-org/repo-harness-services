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
import io.harness.spec.server.idp.v1.model.StarredEntitiesCard;

import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@FieldNameConstants(innerTypeName = "StarredEntitiesCardEntityKeys")
@StoreIn(DbAliases.IDP)
@Persistent
@OwnedBy(HarnessTeam.IDP)
@TypeAlias("io.harness.idp.homepage.entities.StarredEntitiesCardEntity")
public class StarredEntitiesCardEntity extends CardEntity {
  @NotNull private String description;
  @NotNull private String size;

  @Override
  public Card.TypeEnum getType() {
    return Card.TypeEnum.STARRED_ENTITIES;
  }

  public static class StarredEntitiesCardMapper implements CardMapper<StarredEntitiesCard, StarredEntitiesCardEntity> {
    @Override
    public StarredEntitiesCardEntity fromDto(StarredEntitiesCard starredEntitiesCard, String accountIdentifier) {
      StarredEntitiesCardEntity starredEntitiesCardEntity = StarredEntitiesCardEntity.builder()
                                                                .description(starredEntitiesCard.getDescription())
                                                                .size(starredEntitiesCard.getSize())
                                                                .build();
      setCommonFieldsEntity(starredEntitiesCard, starredEntitiesCardEntity, accountIdentifier);
      return starredEntitiesCardEntity;
    }

    @Override
    public StarredEntitiesCard toDto(StarredEntitiesCardEntity starredEntitiesCardEntity) {
      StarredEntitiesCard starredEntitiesCard = new StarredEntitiesCard();
      starredEntitiesCard.setDescription(starredEntitiesCardEntity.getDescription());
      starredEntitiesCard.setSize(starredEntitiesCardEntity.getSize());
      setCommonFieldsDto(starredEntitiesCardEntity, starredEntitiesCard);
      return starredEntitiesCard;
    }
  }
}
