/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.homepage.resource;

import static io.harness.annotations.dev.HarnessTeam.IDP;
import static io.harness.rule.OwnerRule.DEVESH;
import static io.harness.rule.OwnerRule.NISARG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.homepage.service.HomePageLayoutService;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.HomePageLayoutRequest;
import io.harness.spec.server.idp.v1.model.HomePageLayoutResponse;
import io.harness.spec.server.idp.v1.model.HomePageLayoutYamlResponse;

import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(IDP)
public class HomePageLayoutApiImplTest extends CategoryTest {
  @Mock private HomePageLayoutService homePageLayoutService;
  @InjectMocks private HomePageLayoutApiImpl homePageLayoutApiImpl;

  private static final String TEST_CARD_IDENTIFIER = "test-card-id";
  private static final String TEST_ACCOUNT_ID = "test-account-id";
  private static final String TEST_QUICK_LINK_ID = "test-quick-link-id";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetHomePageLayoutInfo() {
    when(homePageLayoutService.getHomePageLayout(TEST_ACCOUNT_ID)).thenReturn(new HomePageLayoutResponse());
    Response response = homePageLayoutApiImpl.getHomePageLayoutInfo(TEST_ACCOUNT_ID);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(response.getEntity()).isNotNull();
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testSaveHomePageLayoutInfo() {
    when(homePageLayoutService.saveHomePageLayout(any(), any())).thenReturn(new HomePageLayoutResponse());
    Response response = homePageLayoutApiImpl.saveHomePageLayoutInfo(new HomePageLayoutRequest(), TEST_ACCOUNT_ID);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(response.getEntity()).isNotNull();
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetYamlForHomePageLayout() {
    when(homePageLayoutService.getHomePageLayoutYaml(TEST_ACCOUNT_ID)).thenReturn(new HomePageLayoutYamlResponse());
    Response response = homePageLayoutApiImpl.getYamlForHomePageLayout(TEST_ACCOUNT_ID);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(response.getEntity()).isNotNull();
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testDeleteCustomLinkCardQuickLinks() {
    doNothing()
        .when(homePageLayoutService)
        .deleteCustomCardQuickLinksIcon(TEST_ACCOUNT_ID, TEST_CARD_IDENTIFIER, TEST_QUICK_LINK_ID);
    Response response =
        homePageLayoutApiImpl.deleteCustomLinkCardQuickLinks(TEST_CARD_IDENTIFIER, TEST_QUICK_LINK_ID, TEST_ACCOUNT_ID);
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testDeleteHeadersQuickLinksIcon() {
    doNothing().when(homePageLayoutService).deleteHeaderQuickLinksIcon(TEST_ACCOUNT_ID, TEST_QUICK_LINK_ID);
    Response response = homePageLayoutApiImpl.deleteHeadersQuickLinksIcon(TEST_QUICK_LINK_ID, TEST_ACCOUNT_ID);
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testDeleteHomePageLayoutCardsIcon() {
    doNothing().when(homePageLayoutService).deleteCardIcon(TEST_ACCOUNT_ID, TEST_CARD_IDENTIFIER);
    Response response = homePageLayoutApiImpl.deleteHomePageLayoutCardsIcon(TEST_CARD_IDENTIFIER, TEST_ACCOUNT_ID);
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testGetHomePageLayoutInfoWithEmptyResponse() {
    HomePageLayoutResponse emptyResponse = new HomePageLayoutResponse();
    when(homePageLayoutService.getHomePageLayout(TEST_ACCOUNT_ID)).thenReturn(emptyResponse);
    Response response = homePageLayoutApiImpl.getHomePageLayoutInfo(TEST_ACCOUNT_ID);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(response.getEntity()).isNotNull();
    assertThat(response.getEntity()).isEqualTo(emptyResponse);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testSaveHomePageLayoutInfoWithEmptyRequest() {
    HomePageLayoutRequest emptyRequest = new HomePageLayoutRequest();
    HomePageLayoutResponse response = new HomePageLayoutResponse();
    when(homePageLayoutService.saveHomePageLayout(any(), any())).thenReturn(response);
    Response apiResponse = homePageLayoutApiImpl.saveHomePageLayoutInfo(emptyRequest, TEST_ACCOUNT_ID);
    assertThat(apiResponse.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(apiResponse.getEntity()).isNotNull();
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testGetYamlForHomePageLayoutWithEmptyYaml() {
    HomePageLayoutYamlResponse yamlResponse = new HomePageLayoutYamlResponse();
    yamlResponse.setYaml("");
    when(homePageLayoutService.getHomePageLayoutYaml(TEST_ACCOUNT_ID)).thenReturn(yamlResponse);
    Response response = homePageLayoutApiImpl.getYamlForHomePageLayout(TEST_ACCOUNT_ID);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(response.getEntity()).isNotNull();
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testDeleteCustomLinkCardQuickLinksWithDifferentIdentifiers() {
    String cardId = "custom-card-123";
    String quickLinkId = "quick-link-456";
    doNothing().when(homePageLayoutService).deleteCustomCardQuickLinksIcon(TEST_ACCOUNT_ID, cardId, quickLinkId);
    Response response = homePageLayoutApiImpl.deleteCustomLinkCardQuickLinks(cardId, quickLinkId, TEST_ACCOUNT_ID);
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testDeleteHeadersQuickLinksIconWithDifferentIdentifier() {
    String quickLinkId = "header-quick-link-789";
    doNothing().when(homePageLayoutService).deleteHeaderQuickLinksIcon(TEST_ACCOUNT_ID, quickLinkId);
    Response response = homePageLayoutApiImpl.deleteHeadersQuickLinksIcon(quickLinkId, TEST_ACCOUNT_ID);
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }
}
