/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.branding.remote.v1.api;

import static io.harness.rule.OwnerRule.YASH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.branding.entities.BrandingAsset;
import io.harness.branding.service.AccountBrandingAssetService;
import io.harness.branding.service.AccountBrandingService;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.spec.server.ng.v1.model.BrandingResponseDTO;
import io.harness.spec.server.ng.v1.model.BrandingSettingsDTO;

import software.wings.service.intfc.FileService;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PL)
public class AccountBrandingApiImplTest {
  @Mock private AccountBrandingService accountBrandingService;
  @Mock private AccountBrandingAssetService accountBrandingAssetService;
  @Mock private FileService fileService;

  @InjectMocks private AccountBrandingApiImpl accountBrandingApi;

  private static final String ACCOUNT_ID = "test-account";
  private static final String ASSET_TYPE = "LARGE_LOGO_LIGHT";
  private static final String ASSET_ID = "test-asset-id";

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testDeleteBrandingAsset() {
    doNothing().when(accountBrandingAssetService).deleteBrandingAsset(ACCOUNT_ID, ASSET_TYPE);

    Response response = accountBrandingApi.deleteBrandingAsset(ASSET_TYPE, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    verify(accountBrandingAssetService, times(1)).deleteBrandingAsset(ACCOUNT_ID, ASSET_TYPE);
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testGetBrandingAsset() {
    BrandingAsset mockAsset = BrandingAsset.builder()
                                  .accountIdentifier(ACCOUNT_ID)
                                  .assetId(ASSET_ID)
                                  .assetType(ASSET_TYPE)
                                  .mimeType("image/png")
                                  .build();

    byte[] imageData = "test-image-data".getBytes();
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    outputStream.writeBytes(imageData);

    when(accountBrandingAssetService.getBrandingAsset(ACCOUNT_ID, ASSET_TYPE)).thenReturn(mockAsset);
    doNothing().when(fileService).downloadToStream(eq(ASSET_ID), any(ByteArrayOutputStream.class), any());

    Response response = accountBrandingApi.getBrandingAsset(ASSET_TYPE, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(response.getMediaType().toString()).isEqualTo("image/png");
    verify(accountBrandingAssetService, times(1)).getBrandingAsset(ACCOUNT_ID, ASSET_TYPE);
    verify(fileService, times(1)).downloadToStream(eq(ASSET_ID), any(ByteArrayOutputStream.class), any());
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testGetBrandingSettings() {
    BrandingSettingsDTO mockSettingsDTO = new BrandingSettingsDTO().brandingOnSignInPage(true);

    when(accountBrandingService.getBrandingSettings(ACCOUNT_ID)).thenReturn(mockSettingsDTO);

    Response response = accountBrandingApi.getBrandingSettings(ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(response.getEntity()).isEqualTo(mockSettingsDTO);
    verify(accountBrandingService, times(1)).getBrandingSettings(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testUploadBrandingAssets() {
    InputStream largeLogoLightStream = new ByteArrayInputStream("logo-data".getBytes());
    InputStream smallLogoLightStream = new ByteArrayInputStream("small-logo-data".getBytes());
    InputStream faviconStream = new ByteArrayInputStream("favicon-data".getBytes());
    InputStream largeLogoDarkStream = new ByteArrayInputStream("dark-logo-data".getBytes());

    BrandingResponseDTO mockResponseDTO =
        new BrandingResponseDTO().settings(new BrandingSettingsDTO().brandingOnSignInPage(true));

    when(accountBrandingService.saveBrandingInfo(eq(ACCOUNT_ID), eq(largeLogoLightStream), eq("png"),
             eq(smallLogoLightStream), eq("png"), eq(faviconStream), eq("ico"), eq(largeLogoDarkStream), eq("png"),
             eq(true)))
        .thenReturn(mockResponseDTO);

    Response response = accountBrandingApi.uploadBrandingAssets(largeLogoLightStream, "png", smallLogoLightStream,
        "png", faviconStream, "ico", largeLogoDarkStream, "png", true, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(response.getEntity()).isEqualTo(mockResponseDTO);
    verify(accountBrandingService, times(1))
        .saveBrandingInfo(eq(ACCOUNT_ID), eq(largeLogoLightStream), eq("png"), eq(smallLogoLightStream), eq("png"),
            eq(faviconStream), eq("ico"), eq(largeLogoDarkStream), eq("png"), eq(true));
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testUploadBrandingAssetsWithNullInputs() {
    BrandingResponseDTO mockResponseDTO =
        new BrandingResponseDTO().settings(new BrandingSettingsDTO().brandingOnSignInPage(false));

    when(accountBrandingService.saveBrandingInfo(
             eq(ACCOUNT_ID), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null), eq(false)))
        .thenReturn(mockResponseDTO);

    Response response =
        accountBrandingApi.uploadBrandingAssets(null, null, null, null, null, null, null, null, false, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(response.getEntity()).isEqualTo(mockResponseDTO);
    verify(accountBrandingService, times(1))
        .saveBrandingInfo(
            eq(ACCOUNT_ID), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null), eq(false));
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testUploadBrandingAssetsPartialInputs() {
    InputStream largeLogoLightStream = new ByteArrayInputStream("logo-data".getBytes());

    BrandingResponseDTO mockResponseDTO =
        new BrandingResponseDTO().settings(new BrandingSettingsDTO().brandingOnSignInPage(true));

    when(accountBrandingService.saveBrandingInfo(eq(ACCOUNT_ID), eq(largeLogoLightStream), eq("png"), eq(null),
             eq(null), eq(null), eq(null), eq(null), eq(null), eq(true)))
        .thenReturn(mockResponseDTO);

    Response response = accountBrandingApi.uploadBrandingAssets(
        largeLogoLightStream, "png", null, null, null, null, null, null, true, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(response.getEntity()).isEqualTo(mockResponseDTO);
    verify(accountBrandingService, times(1))
        .saveBrandingInfo(eq(ACCOUNT_ID), eq(largeLogoLightStream), eq("png"), eq(null), eq(null), eq(null), eq(null),
            eq(null), eq(null), eq(true));
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testGetBrandingAssetWithDifferentMimeType() {
    BrandingAsset mockAsset = BrandingAsset.builder()
                                  .accountIdentifier(ACCOUNT_ID)
                                  .assetId(ASSET_ID)
                                  .assetType("FAVICON")
                                  .mimeType("image/x-icon")
                                  .build();

    when(accountBrandingAssetService.getBrandingAsset(ACCOUNT_ID, "FAVICON")).thenReturn(mockAsset);
    doNothing().when(fileService).downloadToStream(eq(ASSET_ID), any(ByteArrayOutputStream.class), any());

    Response response = accountBrandingApi.getBrandingAsset("FAVICON", ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(response.getMediaType().toString()).isEqualTo("image/x-icon");
    verify(accountBrandingAssetService, times(1)).getBrandingAsset(ACCOUNT_ID, "FAVICON");
  }
}