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
import io.harness.spec.server.idp.v1.model.SecurityFindingsCard;

import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@FieldNameConstants(innerTypeName = "SecurityFindingsCardEntityKeys")
@StoreIn(DbAliases.IDP)
@Persistent
@OwnedBy(HarnessTeam.IDP)
@TypeAlias("io.harness.idp.homepage.entities.SecurityFindingsCardEntity")
public class SecurityFindingsCardEntity extends CardEntity {
  @NotNull private String size;

  @Override
  public Card.TypeEnum getType() {
    return Card.TypeEnum.SECURITY_FINDINGS;
  }

  public static class SecurityFindingsCardMapper
      implements CardMapper<SecurityFindingsCard, SecurityFindingsCardEntity> {
    @Override
    public SecurityFindingsCardEntity fromDto(SecurityFindingsCard card, String accountIdentifier) {
      SecurityFindingsCardEntity entity = SecurityFindingsCardEntity.builder().size(card.getSize()).build();
      setCommonFieldsEntity(card, entity, accountIdentifier);
      return entity;
    }

    @Override
    public SecurityFindingsCard toDto(SecurityFindingsCardEntity entity) {
      SecurityFindingsCard card = new SecurityFindingsCard();
      card.setSize(entity.getSize());
      setCommonFieldsDto(entity, card);
      return card;
    }
  }
}
