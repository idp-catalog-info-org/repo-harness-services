/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.icons.service;

import static io.harness.annotations.dev.HarnessTeam.IDP;
import static io.harness.rule.OwnerRule.KOTA_KARTHIK;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.common.CloudStorageUtil;
import io.harness.idp.common.IconUploadType;
import io.harness.idp.homepage.config.HomePageCardIconConfig;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(IDP)
public class IconServiceImplTest extends CategoryTest {
  @Mock private CloudStorageUtil cloudStorageUtil;
  @Mock private HomePageCardIconConfig homePageCardIconConfig;

  private IconServiceImpl iconService;

  private static final String TEST_ACCOUNT_IDENTIFIER = "test-account-id";
  private static final String TEST_BUCKET_NAME = "test-bucket";
  private static final String TEST_ENV = "test";
  private static final String TEST_ICON_URL_1 =
      "https://storage.googleapis.com/test-bucket/icons/test/test-account-id/cards/icon1.png";
  private static final String TEST_ICON_URL_2 =
      "https://storage.googleapis.com/test-bucket/icons/test/test-account-id/cards/icon2.png";
  private static final String TEST_ICON_URL_3 =
      "https://storage.googleapis.com/test-bucket/icons/test/test-account-id/quick_links/icon3.png";
  private static final String TEST_ICON_URL_4 =
      "https://storage.googleapis.com/test-bucket/icons/test/test-account-id/banner/icon4.png";
  private static final String TEST_ICON_URL_5 =
      "https://storage.googleapis.com/test-bucket/icons/test/test-account-id/picker/icon5.png";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    iconService = new IconServiceImpl();
    iconService.cloudStorageUtil = cloudStorageUtil;
    iconService.homePageCardIconConfig = homePageCardIconConfig;
    iconService.env = TEST_ENV;
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetAllIcons_Success() {
    when(homePageCardIconConfig.getBucketName()).thenReturn(TEST_BUCKET_NAME);

    List<String> cardsIcons = Arrays.asList(TEST_ICON_URL_1, TEST_ICON_URL_2);
    List<String> quickLinksIcons = Arrays.asList(TEST_ICON_URL_3);
    List<String> bannerIcons = Arrays.asList(TEST_ICON_URL_4);
    List<String> pickerIcons = Arrays.asList(TEST_ICON_URL_5);

    when(cloudStorageUtil.fetchImageUrls(anyString(), anyString())).thenAnswer(invocation -> {
      String path = invocation.getArgument(1);
      if (path.endsWith("/cards")) {
        return cardsIcons;
      } else if (path.endsWith("/quick_links")) {
        return quickLinksIcons;
      } else if (path.endsWith("/banner")) {
        return bannerIcons;
      } else if (path.endsWith("/picker")) {
        return pickerIcons;
      }
      return new ArrayList<>();
    });

    List<String> result = iconService.getAllIcons(TEST_ACCOUNT_IDENTIFIER);

    assertNotNull(result);
    assertEquals(5, result.size());
    assertTrue(result.contains(TEST_ICON_URL_1));
    assertTrue(result.contains(TEST_ICON_URL_2));
    assertTrue(result.contains(TEST_ICON_URL_3));
    assertTrue(result.contains(TEST_ICON_URL_4));
    assertTrue(result.contains(TEST_ICON_URL_5));

    verify(cloudStorageUtil, times(4)).fetchImageUrls(eq(TEST_BUCKET_NAME), anyString());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetAllIcons_EmptyResults() {
    when(homePageCardIconConfig.getBucketName()).thenReturn(TEST_BUCKET_NAME);

    when(cloudStorageUtil.fetchImageUrls(eq(TEST_BUCKET_NAME), anyString())).thenReturn(new ArrayList<>());

    List<String> result = iconService.getAllIcons(TEST_ACCOUNT_IDENTIFIER);

    assertNotNull(result);
    assertEquals(0, result.size());

    verify(cloudStorageUtil, times(IconUploadType.values().length)).fetchImageUrls(eq(TEST_BUCKET_NAME), anyString());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetAllIcons_PartialResults() {
    when(homePageCardIconConfig.getBucketName()).thenReturn(TEST_BUCKET_NAME);

    List<String> cardsIcons = Arrays.asList(TEST_ICON_URL_1);

    when(cloudStorageUtil.fetchImageUrls(anyString(), anyString())).thenAnswer(invocation -> {
      String path = invocation.getArgument(1);
      if (path.endsWith("/cards")) {
        return cardsIcons;
      }
      return new ArrayList<>();
    });

    List<String> result = iconService.getAllIcons(TEST_ACCOUNT_IDENTIFIER);

    assertNotNull(result);
    assertEquals(1, result.size());
    assertTrue(result.contains(TEST_ICON_URL_1));

    verify(cloudStorageUtil, times(4)).fetchImageUrls(eq(TEST_BUCKET_NAME), anyString());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetAllIcons_AllIconUploadTypesProcessed() {
    when(homePageCardIconConfig.getBucketName()).thenReturn(TEST_BUCKET_NAME);

    when(cloudStorageUtil.fetchImageUrls(eq(TEST_BUCKET_NAME), anyString())).thenReturn(new ArrayList<>());

    iconService.getAllIcons(TEST_ACCOUNT_IDENTIFIER);

    verify(cloudStorageUtil).fetchImageUrls(eq(TEST_BUCKET_NAME), eq("static/test/test-account-id/cards"));
    verify(cloudStorageUtil).fetchImageUrls(eq(TEST_BUCKET_NAME), eq("static/test/test-account-id/quick_links"));
    verify(cloudStorageUtil).fetchImageUrls(eq(TEST_BUCKET_NAME), eq("static/test/test-account-id/banner"));
    verify(cloudStorageUtil).fetchImageUrls(eq(TEST_BUCKET_NAME), eq("static/test/test-account-id/picker"));
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetAllIcons_DifferentEnvironments() {
    iconService.env = "prod";
    when(homePageCardIconConfig.getBucketName()).thenReturn(TEST_BUCKET_NAME);

    List<String> cardsIcons =
        Arrays.asList("https://storage.googleapis.com/test-bucket/icons/prod/test-account-id/cards/icon1.png");

    when(cloudStorageUtil.fetchImageUrls(anyString(), anyString())).thenAnswer(invocation -> {
      String path = invocation.getArgument(1);
      if (path.endsWith("/cards")) {
        return cardsIcons;
      }
      return new ArrayList<>();
    });

    List<String> result = iconService.getAllIcons(TEST_ACCOUNT_IDENTIFIER);

    assertNotNull(result);
    assertEquals(1, result.size());

    verify(cloudStorageUtil, times(4)).fetchImageUrls(eq(TEST_BUCKET_NAME), anyString());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetAllIcons_MultipleIconsPerType() {
    when(homePageCardIconConfig.getBucketName()).thenReturn(TEST_BUCKET_NAME);

    List<String> cardsIcons =
        Arrays.asList("https://storage.googleapis.com/test-bucket/icons/test/test-account-id/cards/icon1.png",
            "https://storage.googleapis.com/test-bucket/icons/test/test-account-id/cards/icon2.png",
            "https://storage.googleapis.com/test-bucket/icons/test/test-account-id/cards/icon3.png");

    when(cloudStorageUtil.fetchImageUrls(anyString(), anyString())).thenAnswer(invocation -> {
      String path = invocation.getArgument(1);
      if (path.endsWith("/cards")) {
        return cardsIcons;
      }
      return new ArrayList<>();
    });

    List<String> result = iconService.getAllIcons(TEST_ACCOUNT_IDENTIFIER);

    assertNotNull(result);
    assertEquals(3, result.size());
  }
}
