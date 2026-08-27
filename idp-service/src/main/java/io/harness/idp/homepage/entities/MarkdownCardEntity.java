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
import io.harness.spec.server.idp.v1.model.MarkdownCard;

import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@FieldNameConstants(innerTypeName = "MarkdownCardEntityKeys")
@StoreIn(DbAliases.IDP)
@Persistent
@OwnedBy(HarnessTeam.IDP)
@TypeAlias("io.harness.idp.homepage.entities.MarkdownCardEntity")
public class MarkdownCardEntity extends CardEntity {
  @NotNull private String markdown;
  @NotNull private String size;

  @Override
  public Card.TypeEnum getType() {
    return Card.TypeEnum.MARKDOWN;
  }

  public static class MarkdownCardMapper implements CardMapper<MarkdownCard, MarkdownCardEntity> {
    @Override
    public MarkdownCardEntity fromDto(MarkdownCard markdownCard, String accountIdentifier) {
      MarkdownCardEntity markdownCardEntity =
          MarkdownCardEntity.builder().markdown(markdownCard.getMarkdown()).size(markdownCard.getSize()).build();
      setCommonFieldsEntity(markdownCard, markdownCardEntity, accountIdentifier);
      return markdownCardEntity;
    }

    @Override
    public MarkdownCard toDto(MarkdownCardEntity markdownCardEntity) {
      MarkdownCard markdownCard = new MarkdownCard();
      markdownCard.markdown(markdownCardEntity.getMarkdown());
      markdownCard.setSize(markdownCardEntity.getSize());
      setCommonFieldsDto(markdownCardEntity, markdownCard);
      return markdownCard;
    }
  }
}
