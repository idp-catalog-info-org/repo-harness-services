/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.homepage.mappers;

import static io.harness.annotations.dev.HarnessTeam.IDP;
import static io.harness.rule.OwnerRule.DEVESH;
import static io.harness.rule.OwnerRule.NISARG;

import static org.junit.Assert.assertTrue;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.homepage.entities.HomePageLayoutEntity;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.BannerInfo;
import io.harness.spec.server.idp.v1.model.Card;
import io.harness.spec.server.idp.v1.model.HeaderInfo;
import io.harness.spec.server.idp.v1.model.HomePageLayoutInfo;
import io.harness.spec.server.idp.v1.model.HomePageLayoutResponse;

import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

@OwnedBy(IDP)
public class HomePageLayoutMapperTest extends CategoryTest {
  private static final String TEST_CARD_IDENTIFIER = "test-card-id";
  private static final boolean TEST_DEFAULT_CARD_VALUE = false;
  private static final boolean TEST_DRAFT_VALUE = false;
  private static final String TEST_CARD_TITLE = "card-title";
  private static final String TEST_CARD_IMAGE_URL = "card-image-url";
  private static final Card.TypeEnum TEST_CARD_TYPE = Card.TypeEnum.CUSTOM_LINK;

  private static final String TEST_IMAGE_TYPE = "image-type";

  private static final String TEST_ACCOUNT_IDENTIFIER = "account-identifier";
  private static final String TEST_ID = "test-id";

  private static final String TEST_HEADER_TEXT = "test-header-text";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void toDtoTest() {
    HomePageLayoutInfo homePageLayoutInfo = HomePageLayoutMapper.toDTO(getHomePageLayoutEntity(), getListOfCards());
    assertTrue(homePageLayoutInfo.getCards().get(0).getIdentifier().equals(TEST_CARD_IDENTIFIER));
    assertTrue(homePageLayoutInfo.getHeader().getHeaderText().equals(TEST_HEADER_TEXT));
    assertTrue(homePageLayoutInfo.getBanner().getType().equals(TEST_IMAGE_TYPE));
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void fromDTOTest() {
    HomePageLayoutInfo homePageLayoutInfo = HomePageLayoutMapper.toDTO(getHomePageLayoutEntity(), getListOfCards());
    HomePageLayoutEntity homePageLayoutEntity =
        HomePageLayoutMapper.fromDTO(homePageLayoutInfo, TEST_ACCOUNT_IDENTIFIER, List.of(TEST_CARD_IDENTIFIER));

    assertTrue(homePageLayoutEntity.getAccountIdentifier().equals(TEST_ACCOUNT_IDENTIFIER));
    assertTrue(homePageLayoutEntity.getCards().get(0).equals(TEST_CARD_IDENTIFIER));
    assertTrue(homePageLayoutInfo.getBanner().getType().equals(TEST_IMAGE_TYPE));
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void toResponseTest() {
    HomePageLayoutResponse homePageLayoutResponse =
        HomePageLayoutMapper.toResponse(getHomePageLayoutEntity(), getListOfCards());
    assertTrue(
        homePageLayoutResponse.getHomePageLayout().getCards().get(0).getIdentifier().equals(TEST_CARD_IDENTIFIER));
    assertTrue(homePageLayoutResponse.getHomePageLayout().getHeader().getHeaderText().equals(TEST_HEADER_TEXT));
    assertTrue(homePageLayoutResponse.getHomePageLayout().getBanner().getType().equals(TEST_IMAGE_TYPE));
  }

  List<Card> getListOfCards() {
    Card card = new Card();
    card.setDefaultCard(TEST_DEFAULT_CARD_VALUE);
    card.setDraft(TEST_DRAFT_VALUE);
    card.setIdentifier(TEST_CARD_IDENTIFIER);
    card.setIdentifier(TEST_CARD_IDENTIFIER);
    card.setType(TEST_CARD_TYPE);
    card.setTitle(TEST_CARD_TITLE);
    card.setIconUrl(TEST_CARD_IMAGE_URL);
    return List.of(card);
  }

  HomePageLayoutEntity getHomePageLayoutEntity() {
    BannerInfo bannerInfo = new BannerInfo();
    bannerInfo.setType(TEST_IMAGE_TYPE);

    HeaderInfo headerInfo = new HeaderInfo();
    headerInfo.setHeaderText(TEST_HEADER_TEXT);
    return HomePageLayoutEntity.builder()
        .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
        .id(TEST_ID)
        .cards(List.of(TEST_CARD_IDENTIFIER))
        .header(headerInfo)
        .banner(bannerInfo)
        .build();
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void toDtoTestWithEmptyCards() {
    HomePageLayoutEntity entity = getHomePageLayoutEntity();
    entity.setCards(List.of());
    HomePageLayoutInfo homePageLayoutInfo = HomePageLayoutMapper.toDTO(entity, List.of());
    assertTrue(homePageLayoutInfo.getCards().isEmpty());
    assertTrue(homePageLayoutInfo.getHeader().getHeaderText().equals(TEST_HEADER_TEXT));
    assertTrue(homePageLayoutInfo.getBanner().getType().equals(TEST_IMAGE_TYPE));
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void fromDTOTestWithEmptyCards() {
    HomePageLayoutInfo homePageLayoutInfo = new HomePageLayoutInfo();
    homePageLayoutInfo.setHeader(new HeaderInfo());
    homePageLayoutInfo.setBanner(new BannerInfo());
    homePageLayoutInfo.setCards(List.of());

    HomePageLayoutEntity homePageLayoutEntity =
        HomePageLayoutMapper.fromDTO(homePageLayoutInfo, TEST_ACCOUNT_IDENTIFIER, List.of());

    assertTrue(homePageLayoutEntity.getAccountIdentifier().equals(TEST_ACCOUNT_IDENTIFIER));
    assertTrue(homePageLayoutEntity.getCards().isEmpty());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void toResponseTestWithEmptyCards() {
    HomePageLayoutEntity entity = getHomePageLayoutEntity();
    entity.setCards(List.of());
    HomePageLayoutResponse homePageLayoutResponse = HomePageLayoutMapper.toResponse(entity, List.of());
    assertTrue(homePageLayoutResponse.getHomePageLayout().getCards().isEmpty());
    assertTrue(homePageLayoutResponse.getHomePageLayout().getHeader().getHeaderText().equals(TEST_HEADER_TEXT));
    assertTrue(homePageLayoutResponse.getHomePageLayout().getBanner().getType().equals(TEST_IMAGE_TYPE));
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void toDtoTestWithMultipleCards() {
    List<Card> multipleCards =
        List.of(createCard("card-1", "Card 1"), createCard("card-2", "Card 2"), createCard("card-3", "Card 3"));

    HomePageLayoutEntity entity = getHomePageLayoutEntity();
    entity.setCards(List.of("card-1", "card-2", "card-3"));

    HomePageLayoutInfo homePageLayoutInfo = HomePageLayoutMapper.toDTO(entity, multipleCards);
    assertTrue(homePageLayoutInfo.getCards().size() == 3);
    assertTrue(homePageLayoutInfo.getCards().get(0).getIdentifier().equals("card-1"));
    assertTrue(homePageLayoutInfo.getCards().get(1).getIdentifier().equals("card-2"));
    assertTrue(homePageLayoutInfo.getCards().get(2).getIdentifier().equals("card-3"));
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void fromDTOTestWithMultipleCards() {
    HomePageLayoutInfo homePageLayoutInfo = HomePageLayoutMapper.toDTO(getHomePageLayoutEntity(), getListOfCards());
    List<String> cardIdentifiers = List.of("card-1", "card-2", "card-3");

    HomePageLayoutEntity homePageLayoutEntity =
        HomePageLayoutMapper.fromDTO(homePageLayoutInfo, TEST_ACCOUNT_IDENTIFIER, cardIdentifiers);

    assertTrue(homePageLayoutEntity.getCards().size() == 3);
    assertTrue(homePageLayoutEntity.getCards().containsAll(cardIdentifiers));
  }

  private Card createCard(String identifier, String title) {
    Card card = new Card();
    card.setIdentifier(identifier);
    card.setTitle(title);
    card.setDefaultCard(false);
    card.setDraft(false);
    card.setType(TEST_CARD_TYPE);
    card.setIconUrl(TEST_CARD_IMAGE_URL);
    return card;
  }
}
