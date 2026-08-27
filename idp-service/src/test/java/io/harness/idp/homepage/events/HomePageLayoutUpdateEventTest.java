/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.homepage.events;

import static io.harness.annotations.dev.HarnessTeam.IDP;
import static io.harness.audit.ResourceTypeConstants.IDP_HOMEPAGE_LAYOUT;
import static io.harness.idp.homepage.events.HomePageLayoutUpdateEvent.HOMEPAGE_LAYOUT_UPDATED;
import static io.harness.ng.core.ResourceConstants.LABEL_KEY_RESOURCE_NAME;
import static io.harness.rule.OwnerRule.NISARG;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.AccountScope;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceScope;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.BannerInfo;
import io.harness.spec.server.idp.v1.model.Card;
import io.harness.spec.server.idp.v1.model.HeaderInfo;
import io.harness.spec.server.idp.v1.model.HomePageLayoutInfo;
import io.harness.spec.server.idp.v1.model.HomePageLayoutResponse;

import java.util.List;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(IDP)
public class HomePageLayoutUpdateEventTest extends CategoryTest {
  static final String TEST_ACCOUNT_IDENTIFIER = "testAccount123";
  static final String TEST_OLD_HEADER_TEXT = "Old Welcome Text";
  static final String TEST_NEW_HEADER_TEXT = "New Welcome Text";
  static final String TEST_BANNER_TYPE = "image";
  static final String TEST_CARD_IDENTIFIER = "card-id-1";
  static final String TEST_OLD_CARD_TITLE = "Old Card Title";
  static final String TEST_NEW_CARD_TITLE = "New Card Title";

  HomePageLayoutResponse oldHomePageLayoutResponse;
  HomePageLayoutResponse newHomePageLayoutResponse;

  @Before
  public void setUp() {
    oldHomePageLayoutResponse = createHomePageLayoutResponse(TEST_OLD_HEADER_TEXT, TEST_OLD_CARD_TITLE);
    newHomePageLayoutResponse = createHomePageLayoutResponse(TEST_NEW_HEADER_TEXT, TEST_NEW_CARD_TITLE);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testHomePageLayoutUpdateEvent() {
    HomePageLayoutUpdateEvent event =
        new HomePageLayoutUpdateEvent(newHomePageLayoutResponse, oldHomePageLayoutResponse, TEST_ACCOUNT_IDENTIFIER);

    assertNotNull(event);
    assertEquals(newHomePageLayoutResponse, event.getNewHomePageLayout());
    assertEquals(oldHomePageLayoutResponse, event.getOldHomePageLayout());
    assertEquals(TEST_ACCOUNT_IDENTIFIER, event.getAccountIdentifier());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testGetEventType() {
    HomePageLayoutUpdateEvent event =
        new HomePageLayoutUpdateEvent(newHomePageLayoutResponse, oldHomePageLayoutResponse, TEST_ACCOUNT_IDENTIFIER);

    assertEquals(HOMEPAGE_LAYOUT_UPDATED, event.getEventType());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testGetResourceScope() {
    HomePageLayoutUpdateEvent event =
        new HomePageLayoutUpdateEvent(newHomePageLayoutResponse, oldHomePageLayoutResponse, TEST_ACCOUNT_IDENTIFIER);

    ResourceScope resourceScope = event.getResourceScope();
    assertNotNull(resourceScope);
    assertEquals(AccountScope.class, resourceScope.getClass());
    assertEquals(TEST_ACCOUNT_IDENTIFIER, ((AccountScope) resourceScope).getAccountIdentifier());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testGetResource() {
    HomePageLayoutUpdateEvent event =
        new HomePageLayoutUpdateEvent(newHomePageLayoutResponse, oldHomePageLayoutResponse, TEST_ACCOUNT_IDENTIFIER);

    Resource resource = event.getResource();
    assertNotNull(resource);
    assertEquals(IDP_HOMEPAGE_LAYOUT, resource.getType());
    assertEquals(TEST_ACCOUNT_IDENTIFIER + "idpHomePageLayout", resource.getIdentifier());
    assertNotNull(resource.getLabels());
    assertEquals("IDP Home Page Layout", resource.getLabels().get(LABEL_KEY_RESOURCE_NAME));
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testNoArgsConstructor() {
    HomePageLayoutUpdateEvent event = new HomePageLayoutUpdateEvent();
    assertNotNull(event);
  }

  private HomePageLayoutResponse createHomePageLayoutResponse(String headerText, String cardTitle) {
    HomePageLayoutResponse response = new HomePageLayoutResponse();
    HomePageLayoutInfo layoutInfo = new HomePageLayoutInfo();

    HeaderInfo headerInfo = new HeaderInfo();
    headerInfo.setHeaderText(headerText);
    layoutInfo.setHeader(headerInfo);

    BannerInfo bannerInfo = new BannerInfo();
    bannerInfo.setType(TEST_BANNER_TYPE);
    layoutInfo.setBanner(bannerInfo);

    Card card = new Card();
    card.setIdentifier(TEST_CARD_IDENTIFIER);
    card.setTitle(cardTitle);
    layoutInfo.setCards(List.of(card));

    response.setHomePageLayout(layoutInfo);
    return response;
  }
}
