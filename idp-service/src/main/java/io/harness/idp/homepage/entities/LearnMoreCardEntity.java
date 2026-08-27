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
import io.harness.spec.server.idp.v1.model.LearnMoreCard;

import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@FieldNameConstants(innerTypeName = "LearnMoreCardEntityKeys")
@StoreIn(DbAliases.IDP)
@Persistent
@OwnedBy(HarnessTeam.IDP)
@TypeAlias("io.harness.idp.homepage.entities.LearnMoreCardEntity")
public class LearnMoreCardEntity extends CardEntity {
  @NotNull private String description;
  @NotNull private String size;

  @Override
  public Card.TypeEnum getType() {
    return Card.TypeEnum.LEARN_MORE;
  }

  public static class LearnMoreCardMapper implements CardMapper<LearnMoreCard, LearnMoreCardEntity> {
    @Override
    public LearnMoreCardEntity fromDto(LearnMoreCard learnMoreCard, String accountIdentifier) {
      LearnMoreCardEntity learnMoreCardEntity = LearnMoreCardEntity.builder()
                                                    .description(learnMoreCard.getDescription())
                                                    .size(learnMoreCard.getSize())
                                                    .build();
      setCommonFieldsEntity(learnMoreCard, learnMoreCardEntity, accountIdentifier);
      return learnMoreCardEntity;
    }

    @Override
    public LearnMoreCard toDto(LearnMoreCardEntity learnMoreCardEntity) {
      LearnMoreCard learnMoreCard = new LearnMoreCard();
      learnMoreCard.setDescription(learnMoreCardEntity.getDescription());
      learnMoreCard.setSize(learnMoreCardEntity.getSize());
      setCommonFieldsDto(learnMoreCardEntity, learnMoreCard);
      return learnMoreCard;
    }
  }
}
