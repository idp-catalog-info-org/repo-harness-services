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
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.common.IconUploadType;
import io.harness.idp.homepage.service.HomePageLayoutService;
import io.harness.rule.Owner;

import java.io.ByteArrayInputStream;
import javax.ws.rs.core.Response;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(IDP)
public class IconUploadApiImplTest extends CategoryTest {
  @Mock private HomePageLayoutService homePageLayoutService;
  @InjectMocks IconUploadApiImpl iconUploadApiImpl;

  private static final String TEST_STRING_VALUE = "testStringValue";
  private static final String TEST_IDENTIFIER = "testIdentifier";
  private static final String TEST_FILE_TYPE = "ICON";
  private static final String TEST_ACCOUNT_IDENTIFIER = "testAccountIdentifier";

  private static final String TEST_FILE_NAME = "testFileName";
  private static final int TEST_FILE_SIZE = 1024;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testUploadIcon() {
    when(homePageLayoutService.uploadIcon(any(), any(), any(), any(), any(), any())).thenReturn(TEST_STRING_VALUE);
    FormDataContentDisposition formDataContentDisposition =
        FormDataContentDisposition.name(TEST_FILE_NAME).fileName(TEST_FILE_NAME).size(TEST_FILE_SIZE).build();
    Response response = iconUploadApiImpl.uploadIcon(IconUploadType.banner, TEST_IDENTIFIER, TEST_FILE_TYPE,
        new ByteArrayInputStream(TEST_STRING_VALUE.getBytes()), formDataContentDisposition, TEST_ACCOUNT_IDENTIFIER);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(response.getEntity()).isNotNull();
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testUploadIconForCards() {
    when(homePageLayoutService.uploadIcon(any(), any(), any(), any(), any(), any())).thenReturn(TEST_STRING_VALUE);
    FormDataContentDisposition formDataContentDisposition =
        FormDataContentDisposition.name(TEST_FILE_NAME).fileName(TEST_FILE_NAME).size(TEST_FILE_SIZE).build();
    Response response = iconUploadApiImpl.uploadIcon(IconUploadType.cards, TEST_IDENTIFIER, TEST_FILE_TYPE,
        new ByteArrayInputStream(TEST_STRING_VALUE.getBytes()), formDataContentDisposition, TEST_ACCOUNT_IDENTIFIER);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(response.getEntity()).isNotNull();
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testUploadIconForQuickLinks() {
    when(homePageLayoutService.uploadIcon(any(), any(), any(), any(), any(), any())).thenReturn(TEST_STRING_VALUE);
    FormDataContentDisposition formDataContentDisposition =
        FormDataContentDisposition.name(TEST_FILE_NAME).fileName(TEST_FILE_NAME).size(TEST_FILE_SIZE).build();
    Response response = iconUploadApiImpl.uploadIcon(IconUploadType.quick_links, TEST_IDENTIFIER, TEST_FILE_TYPE,
        new ByteArrayInputStream(TEST_STRING_VALUE.getBytes()), formDataContentDisposition, TEST_ACCOUNT_IDENTIFIER);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(response.getEntity()).isNotNull();
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testUploadIconWithDifferentFileType() {
    String testIconUrl = "https://example.com/icon.png";
    when(homePageLayoutService.uploadIcon(any(), any(), any(), any(), any(), any())).thenReturn(testIconUrl);
    FormDataContentDisposition formDataContentDisposition =
        FormDataContentDisposition.name("iconFile").fileName("test-icon.png").size(2048).build();
    Response response = iconUploadApiImpl.uploadIcon(IconUploadType.banner, "banner-id", "PNG",
        new ByteArrayInputStream("test-data".getBytes()), formDataContentDisposition, TEST_ACCOUNT_IDENTIFIER);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(response.getEntity()).isNotNull();
  }
}
