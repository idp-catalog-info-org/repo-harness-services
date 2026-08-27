/*
 * Copyright 2026 Harness Inc. All rights reserved.
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
import io.harness.spec.server.idp.v1.model.EntityDistributionOwnershipCard;

import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@FieldNameConstants(innerTypeName = "EntityDistributionOwnershipCardEntityKeys")
@StoreIn(DbAliases.IDP)
@Persistent
@OwnedBy(HarnessTeam.IDP)
@TypeAlias("io.harness.idp.homepage.entities.EntityDistributionOwnershipCardEntity")
public class EntityDistributionOwnershipCardEntity extends CardEntity {
  @NotNull private String size;

  @Override
  public Card.TypeEnum getType() {
    return Card.TypeEnum.ENTITY_DISTRIBUTION_OWNERSHIP;
  }

  public static class EntityDistributionOwnershipCardMapper
      implements CardMapper<EntityDistributionOwnershipCard, EntityDistributionOwnershipCardEntity> {
    @Override
    public EntityDistributionOwnershipCardEntity fromDto(
        EntityDistributionOwnershipCard card, String accountIdentifier) {
      EntityDistributionOwnershipCardEntity entity =
          EntityDistributionOwnershipCardEntity.builder().size(card.getSize()).build();
      setCommonFieldsEntity(card, entity, accountIdentifier);
      return entity;
    }

    @Override
    public EntityDistributionOwnershipCard toDto(EntityDistributionOwnershipCardEntity entity) {
      EntityDistributionOwnershipCard card = new EntityDistributionOwnershipCard();
      card.setSize(entity.getSize());
      setCommonFieldsDto(entity, card);
      return card;
    }
  }
}
