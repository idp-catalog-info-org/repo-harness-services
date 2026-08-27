/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.homepage.entities;

import static io.harness.annotations.dev.HarnessTeam.IDP;
import static io.harness.rule.OwnerRule.NISARG;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.rule.Owner;

import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(IDP)
public class CardEntityTest extends CategoryTest {
  public static final String TEST_ACCOUNT_IDENTIFIER = "testAccount123";
  public static final String TEST_TITLE = "Test Card";
  public static final String TEST_IDENTIFIER = "test-card-id";
  public static final String TEST_ICON_URL = "https://example.com/icon.png";

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testCardEntityMongoIndexes() {
    List<MongoIndex> cardEntityMongoIndexes = CardEntity.mongoIndexes();

    assertThat(cardEntityMongoIndexes).isNotNull();
    assertThat(cardEntityMongoIndexes).hasSize(1);

    MongoIndex index = cardEntityMongoIndexes.get(0);
    assertThat(index).isInstanceOf(CompoundMongoIndex.class);

    CompoundMongoIndex compoundIndex = (CompoundMongoIndex) index;
    assertThat(compoundIndex.getName()).isEqualTo("unique_account_identifier");
    assertThat(compoundIndex.isUnique()).isTrue();
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testCustomLinkCardEntity() {
    CustomLinkCardEntity entity = CustomLinkCardEntity.builder().links(List.of()).size("medium").build();
    entity.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    entity.setTitle(TEST_TITLE);
    entity.setIsDefault(false);
    entity.setIsDraft(false);
    entity.setIdentifier(TEST_IDENTIFIER);
    entity.setIconUrl(TEST_ICON_URL);

    assertThat(entity).isNotNull();
    assertThat(entity.getSize()).isEqualTo("medium");
    assertThat(entity.getLinks()).isEmpty();
    assertThat(entity.getAccountIdentifier()).isEqualTo(TEST_ACCOUNT_IDENTIFIER);
    assertThat(entity.getType()).isEqualTo(io.harness.spec.server.idp.v1.model.Card.TypeEnum.CUSTOM_LINK);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testGithubCardEntity() {
    GithubCardEntity entity = GithubCardEntity.builder().size("small").build();
    entity.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    entity.setTitle(TEST_TITLE);
    entity.setIsDefault(false);
    entity.setIsDraft(false);
    entity.setIdentifier(TEST_IDENTIFIER);

    assertThat(entity).isNotNull();
    assertThat(entity.getSize()).isEqualTo("small");
    assertThat(entity.getAccountIdentifier()).isEqualTo(TEST_ACCOUNT_IDENTIFIER);
    assertThat(entity.getType()).isEqualTo(io.harness.spec.server.idp.v1.model.Card.TypeEnum.GITHUB);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testHarnessCodeCardEntity() {
    HarnessCodeCardEntity entity = HarnessCodeCardEntity.builder().size("large").build();
    entity.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    entity.setTitle(TEST_TITLE);
    entity.setIsDefault(false);
    entity.setIsDraft(false);
    entity.setIdentifier(TEST_IDENTIFIER);

    assertThat(entity).isNotNull();
    assertThat(entity.getSize()).isEqualTo("large");
    assertThat(entity.getAccountIdentifier()).isEqualTo(TEST_ACCOUNT_IDENTIFIER);
    assertThat(entity.getType()).isEqualTo(io.harness.spec.server.idp.v1.model.Card.TypeEnum.HARNESS_CODE);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testJiraCardEntity() {
    JiraCardEntity entity = JiraCardEntity.builder().size("medium").build();
    entity.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    entity.setTitle(TEST_TITLE);
    entity.setIsDefault(false);
    entity.setIsDraft(false);
    entity.setIdentifier(TEST_IDENTIFIER);

    assertThat(entity).isNotNull();
    assertThat(entity.getSize()).isEqualTo("medium");
    assertThat(entity.getAccountIdentifier()).isEqualTo(TEST_ACCOUNT_IDENTIFIER);
    assertThat(entity.getType()).isEqualTo(io.harness.spec.server.idp.v1.model.Card.TypeEnum.JIRA);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testMarkdownCardEntity() {
    MarkdownCardEntity entity = MarkdownCardEntity.builder().size("large").build();
    entity.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    entity.setTitle(TEST_TITLE);
    entity.setIsDefault(false);
    entity.setIsDraft(false);
    entity.setIdentifier(TEST_IDENTIFIER);

    assertThat(entity).isNotNull();
    assertThat(entity.getSize()).isEqualTo("large");
    assertThat(entity.getAccountIdentifier()).isEqualTo(TEST_ACCOUNT_IDENTIFIER);
    assertThat(entity.getType()).isEqualTo(io.harness.spec.server.idp.v1.model.Card.TypeEnum.MARKDOWN);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testVideoCardEntity() {
    VideoCardEntity entity = VideoCardEntity.builder().size("medium").build();
    entity.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    entity.setTitle(TEST_TITLE);
    entity.setIsDefault(false);
    entity.setIsDraft(false);
    entity.setIdentifier(TEST_IDENTIFIER);

    assertThat(entity).isNotNull();
    assertThat(entity.getSize()).isEqualTo("medium");
    assertThat(entity.getAccountIdentifier()).isEqualTo(TEST_ACCOUNT_IDENTIFIER);
    assertThat(entity.getType()).isEqualTo(io.harness.spec.server.idp.v1.model.Card.TypeEnum.VIDEO);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testLearnMoreCardEntity() {
    LearnMoreCardEntity entity = LearnMoreCardEntity.builder().size("small").build();
    entity.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    entity.setTitle(TEST_TITLE);
    entity.setIsDefault(false);
    entity.setIsDraft(false);
    entity.setIdentifier(TEST_IDENTIFIER);

    assertThat(entity).isNotNull();
    assertThat(entity.getSize()).isEqualTo("small");
    assertThat(entity.getType()).isEqualTo(io.harness.spec.server.idp.v1.model.Card.TypeEnum.LEARN_MORE);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testRecentlyVisitedCardEntity() {
    RecentlyVisitedCardEntity entity = RecentlyVisitedCardEntity.builder().size("medium").build();
    entity.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    entity.setTitle(TEST_TITLE);
    entity.setIsDefault(false);
    entity.setIsDraft(false);
    entity.setIdentifier(TEST_IDENTIFIER);

    assertThat(entity).isNotNull();
    assertThat(entity.getSize()).isEqualTo("medium");
    assertThat(entity.getAccountIdentifier()).isEqualTo(TEST_ACCOUNT_IDENTIFIER);
    assertThat(entity.getType()).isEqualTo(io.harness.spec.server.idp.v1.model.Card.TypeEnum.RECENTLY_VISITED);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testTopVisitedCardEntity() {
    TopVisitedCardEntity entity = TopVisitedCardEntity.builder().size("large").build();
    entity.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    entity.setTitle(TEST_TITLE);
    entity.setIsDefault(false);
    entity.setIsDraft(false);
    entity.setIdentifier(TEST_IDENTIFIER);

    assertThat(entity).isNotNull();
    assertThat(entity.getSize()).isEqualTo("large");
    assertThat(entity.getAccountIdentifier()).isEqualTo(TEST_ACCOUNT_IDENTIFIER);
    assertThat(entity.getType()).isEqualTo(io.harness.spec.server.idp.v1.model.Card.TypeEnum.TOP_VISITED);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testStarredEntitiesCardEntity() {
    StarredEntitiesCardEntity entity = StarredEntitiesCardEntity.builder().size("medium").build();
    entity.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    entity.setTitle(TEST_TITLE);
    entity.setIsDefault(false);
    entity.setIsDraft(false);
    entity.setIdentifier(TEST_IDENTIFIER);

    assertThat(entity).isNotNull();
    assertThat(entity.getSize()).isEqualTo("medium");
    assertThat(entity.getAccountIdentifier()).isEqualTo(TEST_ACCOUNT_IDENTIFIER);
    assertThat(entity.getType()).isEqualTo(io.harness.spec.server.idp.v1.model.Card.TypeEnum.STARRED_ENTITIES);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testSelfServiceCardEntity() {
    SelfServiceCardEntity entity = SelfServiceCardEntity.builder().size("small").build();
    entity.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    entity.setTitle(TEST_TITLE);
    entity.setIsDefault(false);
    entity.setIsDraft(false);
    entity.setIdentifier(TEST_IDENTIFIER);

    assertThat(entity).isNotNull();
    assertThat(entity.getSize()).isEqualTo("small");
    assertThat(entity.getAccountIdentifier()).isEqualTo(TEST_ACCOUNT_IDENTIFIER);
    assertThat(entity.getType()).isEqualTo(io.harness.spec.server.idp.v1.model.Card.TypeEnum.SELF_SERVICE);
  }
}
