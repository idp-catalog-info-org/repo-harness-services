/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.idp.homepage.entities;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.CreatedByAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UpdatedByAware;
import io.harness.spec.server.idp.v1.model.Card;
import io.harness.spec.server.idp.v1.model.CardResponse;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.util.ArrayList;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@FieldNameConstants(innerTypeName = "CardEntityKeys")
@StoreIn(DbAliases.IDP)
@Entity(value = "cards", noClassnameStored = true)
@Document("cards")
@Persistent
@OwnedBy(HarnessTeam.IDP)
public abstract class CardEntity
    implements PersistentEntity, CreatedAtAware, UpdatedAtAware, CreatedByAware, UpdatedByAware {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_account_identifier")
                 .unique(true)
                 .field(CardEntity.CardEntityKeys.accountIdentifier)
                 .field(CardEntityKeys.identifier)
                 .build())
        .build();
  }

  @Id @org.mongodb.morphia.annotations.Id private String id;
  @NotNull private String title;
  @NotNull private Boolean isDefault;
  @NotNull private String accountIdentifier;
  @NotNull private Boolean isDraft = false;
  @NotNull private Card.TypeEnum type;
  @NotNull private String identifier;
  @NotNull private String iconUrl;

  @NotNull @CreatedDate private long createdAt;
  @NotNull @CreatedBy private EmbeddedUser createdBy;
  @LastModifiedDate private long lastUpdatedAt;
  @LastModifiedBy private EmbeddedUser lastUpdatedBy;

  public abstract Card.TypeEnum getType();

  public interface CardMapper<S extends Card, T extends CardEntity> {
    T fromDto(S Card, String accountIdentifier);

    S toDto(T CardEntity);

    default void setCommonFieldsEntity(Card card, CardEntity cardEntity, String accountIdentifier) {
      cardEntity.setAccountIdentifier(accountIdentifier);
      cardEntity.setIsDefault(card.isDefaultCard());
      cardEntity.setTitle(card.getTitle());
      cardEntity.setIsDraft(card.isDraft());
      cardEntity.setType(card.getType());
      cardEntity.setIdentifier(card.getIdentifier());
      if (!isEmpty(card.getIconUrl())) {
        cardEntity.setIconUrl(card.getIconUrl());
      }
    }

    default void setCommonFieldsDto(CardEntity cardEntity, Card card) {
      card.setTitle(cardEntity.getTitle());
      card.setDefaultCard(cardEntity.isDefault);
      card.setDraft(cardEntity.isDraft);
      card.setType(Card.TypeEnum.valueOf(cardEntity.getType().name()));
      card.setIdentifier(cardEntity.getIdentifier());
      if (!isEmpty(cardEntity.getIconUrl())) {
        card.setIconUrl(cardEntity.getIconUrl());
      }
    }

    static List<CardResponse> toResponseList(List<Card> cards) {
      List<CardResponse> responses = new ArrayList<>();
      cards.forEach(card -> responses.add(new CardResponse().card(card)));
      return responses;
    }

    static CardResponse toResponse(Card card) {
      return new CardResponse().card(card);
    }
  }
}
