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
import io.harness.beans.EmbeddedUser;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.BannerInfo;
import io.harness.spec.server.idp.v1.model.HeaderInfo;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(IDP)
public class HomePageLayoutEntityTest extends CategoryTest {
  public static final String TEST_ACCOUNT_IDENTIFIER = "testAccount123";
  public static final String TEST_HEADER_TEXT = "Welcome to IDP";
  public static final String TEST_BANNER_TYPE = "image";
  public static final String TEST_BANNER_URL = "https://example.com/banner.png";
  public static final String TEST_CARD_ID_1 = "card-id-1";
  public static final String TEST_CARD_ID_2 = "card-id-2";
  public static final String TEST_ENTITY_ID = "test-entity-id";
  public static final String TEST_USER_NAME = "test-user";
  public static final String TEST_USER_EMAIL = "test@example.com";

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testHomePageLayoutEntityBuilder() {
    HeaderInfo headerInfo = new HeaderInfo();
    headerInfo.setHeaderText(TEST_HEADER_TEXT);

    BannerInfo bannerInfo = new BannerInfo();
    bannerInfo.setType(TEST_BANNER_TYPE);
    bannerInfo.setBannerEnabled(true);

    List<String> cards = Arrays.asList(TEST_CARD_ID_1, TEST_CARD_ID_2);

    HomePageLayoutEntity entity = HomePageLayoutEntity.builder()
                                      .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                      .header(headerInfo)
                                      .banner(bannerInfo)
                                      .cards(cards)
                                      .createdAt(System.currentTimeMillis())
                                      .build();

    assertThat(entity).isNotNull();
    assertThat(entity.getAccountIdentifier()).isEqualTo(TEST_ACCOUNT_IDENTIFIER);
    assertThat(entity.getHeader()).isNotNull();
    assertThat(entity.getHeader().getHeaderText()).isEqualTo(TEST_HEADER_TEXT);
    assertThat(entity.getBanner()).isNotNull();
    assertThat(entity.getBanner().getType()).isEqualTo(TEST_BANNER_TYPE);
    assertThat(entity.getBanner().isBannerEnabled()).isTrue();
    assertThat(entity.getCards()).hasSize(2);
    assertThat(entity.getCards()).containsExactly(TEST_CARD_ID_1, TEST_CARD_ID_2);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testHomePageLayoutEntityFields() {
    HeaderInfo headerInfo = new HeaderInfo();
    headerInfo.setHeaderText(TEST_HEADER_TEXT);

    BannerInfo bannerInfo = new BannerInfo();
    bannerInfo.setType(TEST_BANNER_TYPE);

    List<String> cards = Arrays.asList(TEST_CARD_ID_1);
    long currentTime = System.currentTimeMillis();

    EmbeddedUser user = EmbeddedUser.builder().name(TEST_USER_NAME).email(TEST_USER_EMAIL).build();

    HomePageLayoutEntity entity = HomePageLayoutEntity.builder()
                                      .id(TEST_ENTITY_ID)
                                      .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                      .header(headerInfo)
                                      .banner(bannerInfo)
                                      .cards(cards)
                                      .createdAt(currentTime)
                                      .createdBy(user)
                                      .lastUpdatedAt(currentTime)
                                      .lastUpdatedBy(user)
                                      .build();

    assertThat(entity.getId()).isEqualTo(TEST_ENTITY_ID);
    assertThat(entity.getAccountIdentifier()).isEqualTo(TEST_ACCOUNT_IDENTIFIER);
    assertThat(entity.getHeader().getHeaderText()).isEqualTo(TEST_HEADER_TEXT);
    assertThat(entity.getBanner()).isEqualTo(bannerInfo);
    assertThat(entity.getCards()).hasSize(1);
    assertThat(entity.getLastUpdatedAt()).isEqualTo(currentTime);
    assertThat(entity.getCreatedBy()).isEqualTo(user);
    assertThat(entity.getLastUpdatedBy()).isEqualTo(user);
    assertThat(entity.getCreatedBy().getName()).isEqualTo(TEST_USER_NAME);
    assertThat(entity.getLastUpdatedBy()).isNotNull();
    assertThat(entity.getLastUpdatedBy().getName()).isEqualTo(TEST_USER_NAME);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testHomePageLayoutEntityWithEmptyCards() {
    HomePageLayoutEntity entity = HomePageLayoutEntity.builder()
                                      .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                      .header(new HeaderInfo())
                                      .banner(new BannerInfo())
                                      .cards(List.of())
                                      .createdAt(System.currentTimeMillis())
                                      .build();

    assertThat(entity.getCards()).isEmpty();
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testHomePageLayoutEntityWithMultipleCards() {
    List<String> multipleCards = Arrays.asList("card1", "card2", "card3", "card4", "card5");

    HomePageLayoutEntity entity = HomePageLayoutEntity.builder()
                                      .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                      .header(new HeaderInfo())
                                      .banner(new BannerInfo())
                                      .cards(multipleCards)
                                      .createdAt(System.currentTimeMillis())
                                      .build();

    assertThat(entity.getCards()).hasSize(5);
    assertThat(entity.getCards()).containsExactlyElementsOf(multipleCards);
  }
}
