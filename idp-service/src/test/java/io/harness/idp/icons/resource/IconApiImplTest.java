/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.idp.icons.resource;

import static io.harness.annotations.dev.HarnessTeam.IDP;
import static io.harness.rule.OwnerRule.KOTA_KARTHIK;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.icons.service.IconService;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.IconsResponse;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(IDP)
public class IconApiImplTest extends CategoryTest {
  @Mock private IconService iconService;

  private IconApiImpl iconApi;

  private static final String TEST_ACCOUNT_IDENTIFIER = "test-account-id";
  private static final String TEST_ICON_URL_1 =
      "https://storage.googleapis.com/test-bucket/icons/test/test-account-id/cards/icon1.png";
  private static final String TEST_ICON_URL_2 =
      "https://storage.googleapis.com/test-bucket/icons/test/test-account-id/cards/icon2.png";
  private static final String TEST_ICON_URL_3 =
      "https://storage.googleapis.com/test-bucket/icons/test/test-account-id/quick_links/icon3.png";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    iconApi = new IconApiImpl();
    iconApi.iconService = iconService;
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetIcons_Success() {
    List<String> expectedIcons = Arrays.asList(TEST_ICON_URL_1, TEST_ICON_URL_2, TEST_ICON_URL_3);
    when(iconService.getAllIcons(TEST_ACCOUNT_IDENTIFIER)).thenReturn(expectedIcons);

    Response response = iconApi.getIcons(TEST_ACCOUNT_IDENTIFIER);

    assertNotNull(response);
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

    IconsResponse iconResponse = (IconsResponse) response.getEntity();
    assertNotNull(iconResponse);
    assertNotNull(iconResponse.getIcons());
    assertEquals(3, iconResponse.getIcons().size());
    assertEquals(expectedIcons, iconResponse.getIcons());

    verify(iconService).getAllIcons(TEST_ACCOUNT_IDENTIFIER);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetIcons_EmptyList() {
    List<String> emptyIcons = Collections.emptyList();
    when(iconService.getAllIcons(TEST_ACCOUNT_IDENTIFIER)).thenReturn(emptyIcons);

    Response response = iconApi.getIcons(TEST_ACCOUNT_IDENTIFIER);

    assertNotNull(response);
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

    IconsResponse iconResponse = (IconsResponse) response.getEntity();
    assertNotNull(iconResponse);
    assertNotNull(iconResponse.getIcons());
    assertEquals(0, iconResponse.getIcons().size());

    verify(iconService).getAllIcons(TEST_ACCOUNT_IDENTIFIER);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetIcons_SingleIcon() {
    List<String> singleIcon = Collections.singletonList(TEST_ICON_URL_1);
    when(iconService.getAllIcons(TEST_ACCOUNT_IDENTIFIER)).thenReturn(singleIcon);

    Response response = iconApi.getIcons(TEST_ACCOUNT_IDENTIFIER);

    assertNotNull(response);
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

    IconsResponse iconResponse = (IconsResponse) response.getEntity();
    assertNotNull(iconResponse);
    assertNotNull(iconResponse.getIcons());
    assertEquals(1, iconResponse.getIcons().size());
    assertEquals(TEST_ICON_URL_1, iconResponse.getIcons().get(0));

    verify(iconService).getAllIcons(TEST_ACCOUNT_IDENTIFIER);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetIcons_ResponseStructure() {
    List<String> icons = Arrays.asList(TEST_ICON_URL_1, TEST_ICON_URL_2);
    when(iconService.getAllIcons(TEST_ACCOUNT_IDENTIFIER)).thenReturn(icons);

    Response response = iconApi.getIcons(TEST_ACCOUNT_IDENTIFIER);

    assertNotNull(response);
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

    Object entity = response.getEntity();
    assertNotNull(entity);
    assertEquals(IconsResponse.class, entity.getClass());

    IconsResponse iconResponse = (IconsResponse) entity;
    assertNotNull(iconResponse.getIcons());

    verify(iconService).getAllIcons(TEST_ACCOUNT_IDENTIFIER);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetIcons_ServiceInteraction() {
    List<String> icons = Arrays.asList(TEST_ICON_URL_1);
    when(iconService.getAllIcons(TEST_ACCOUNT_IDENTIFIER)).thenReturn(icons);

    iconApi.getIcons(TEST_ACCOUNT_IDENTIFIER);

    verify(iconService).getAllIcons(TEST_ACCOUNT_IDENTIFIER);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetIcons_MultipleAccounts() {
    String account1 = "account-1";
    String account2 = "account-2";

    List<String> icons1 = Arrays.asList(TEST_ICON_URL_1);
    List<String> icons2 = Arrays.asList(TEST_ICON_URL_2);

    when(iconService.getAllIcons(account1)).thenReturn(icons1);
    when(iconService.getAllIcons(account2)).thenReturn(icons2);

    Response response1 = iconApi.getIcons(account1);
    Response response2 = iconApi.getIcons(account2);

    assertEquals(Response.Status.OK.getStatusCode(), response1.getStatus());
    assertEquals(Response.Status.OK.getStatusCode(), response2.getStatus());

    IconsResponse iconResponse1 = (IconsResponse) response1.getEntity();
    IconsResponse iconResponse2 = (IconsResponse) response2.getEntity();

    assertEquals(icons1, iconResponse1.getIcons());
    assertEquals(icons2, iconResponse2.getIcons());

    verify(iconService).getAllIcons(account1);
    verify(iconService).getAllIcons(account2);
  }
}
