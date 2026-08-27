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
import io.harness.spec.server.idp.v1.model.GithubCard;

import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@FieldNameConstants(innerTypeName = "GithubCardEntityKeys")
@StoreIn(DbAliases.IDP)
@Persistent
@OwnedBy(HarnessTeam.IDP)
@TypeAlias("io.harness.idp.homepage.entities.GithubCardEntity")
public class GithubCardEntity extends CardEntity {
  @NotNull private String size;

  @Override
  public Card.TypeEnum getType() {
    return Card.TypeEnum.GITHUB;
  }

  public static class GithubCardMapper implements CardMapper<GithubCard, GithubCardEntity> {
    @Override
    public GithubCardEntity fromDto(GithubCard githubCard, String accountIdentifier) {
      GithubCardEntity githubCardEntity = GithubCardEntity.builder().size(githubCard.getSize()).build();
      setCommonFieldsEntity(githubCard, githubCardEntity, accountIdentifier);
      return githubCardEntity;
    }

    @Override
    public GithubCard toDto(GithubCardEntity githubCardEntity) {
      GithubCard githubCard = new GithubCard();
      githubCard.setSize(githubCardEntity.getSize());
      setCommonFieldsDto(githubCardEntity, githubCard);
      return githubCard;
    }
  }
}
