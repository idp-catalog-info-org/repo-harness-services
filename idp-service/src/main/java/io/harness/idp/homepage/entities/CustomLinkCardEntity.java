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
import io.harness.spec.server.idp.v1.model.CustomLinkCard;
import io.harness.spec.server.idp.v1.model.LinksInfo;

import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@FieldNameConstants(innerTypeName = "CustomLinkCardEntityKeys")
@StoreIn(DbAliases.IDP)
@Persistent
@OwnedBy(HarnessTeam.IDP)
@TypeAlias("io.harness.idp.homepage.entities.CustomLinkCardEntity")
public class CustomLinkCardEntity extends CardEntity {
  @NotNull private List<LinksInfo> links;
  @NotNull private String size;

  @Override
  public Card.TypeEnum getType() {
    return Card.TypeEnum.CUSTOM_LINK;
  }

  public static class CustomLinkCardMapper implements CardMapper<CustomLinkCard, CustomLinkCardEntity> {
    @Override
    public CustomLinkCardEntity fromDto(CustomLinkCard customLinkCard, String accountIdentifier) {
      CustomLinkCardEntity customLinkCardEntity =
          CustomLinkCardEntity.builder().links(customLinkCard.getLinks()).size(customLinkCard.getSize()).build();
      setCommonFieldsEntity(customLinkCard, customLinkCardEntity, accountIdentifier);
      return customLinkCardEntity;
    }

    @Override
    public CustomLinkCard toDto(CustomLinkCardEntity customLinkCardEntity) {
      CustomLinkCard customLinkCard = new CustomLinkCard();
      customLinkCard.setLinks(customLinkCardEntity.getLinks());
      customLinkCard.setSize(customLinkCardEntity.getSize());
      setCommonFieldsDto(customLinkCardEntity, customLinkCard);
      return customLinkCard;
    }
  }
}
