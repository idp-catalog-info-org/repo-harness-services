/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.branding.service.impl;

import static io.harness.rule.OwnerRule.YASH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.branding.entities.Branding;
import io.harness.branding.entities.BrandingAsset;
import io.harness.branding.enums.BrandingAssetType;
import io.harness.branding.mapper.BrandingMapper;
import io.harness.branding.service.AccountBrandingAssetService;
import io.harness.category.element.UnitTests;
import io.harness.exception.EntityNotFoundException;
import io.harness.outbox.api.OutboxService;
import io.harness.repositories.spring.BrandingRepository;
import io.harness.rule.Owner;
import io.harness.spec.server.ng.v1.model.BrandingAssetsDTO;
import io.harness.spec.server.ng.v1.model.BrandingResponseDTO;
import io.harness.spec.server.ng.v1.model.BrandingSettingsDTO;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(HarnessTeam.PL)
public class AccountBrandingServiceImplTest extends CategoryTest {
  @Mock private AccountBrandingAssetService accountBrandingAssetService;
  @Mock private BrandingRepository brandingRepository;
  @Mock private BrandingMapper brandingMapper;
  @Mock private TransactionTemplate transactionTemplate;
  @Mock private OutboxService outboxService;

  @InjectMocks private AccountBrandingServiceImpl accountBrandingService;

  private static final String ACCOUNT_ID = "test-account";
  private static final String ASSET_ID = "test-asset-id";

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testSaveBrandingInfo() {
    InputStream largeLogoLightStream = new ByteArrayInputStream("test-logo".getBytes());
    BrandingAsset mockAsset =
        BrandingAsset.builder().accountIdentifier(ACCOUNT_ID).assetId(ASSET_ID).assetType("LARGE_LOGO_LIGHT").build();

    Branding mockBranding = Branding.builder().accountIdentifier(ACCOUNT_ID).brandingOnSignInPage(true).build();

    BrandingAssetsDTO mockAssetDTO = new BrandingAssetsDTO().assetId(ASSET_ID).assetType("LARGE_LOGO_LIGHT");
    BrandingSettingsDTO mockSettingsDTO = new BrandingSettingsDTO().brandingOnSignInPage(true);

    when(accountBrandingAssetService.prepareAndUploadBrandingAsset(
             eq(ACCOUNT_ID), eq(largeLogoLightStream), eq("png"), eq(BrandingAssetType.LARGE_LOGO_LIGHT), any()))
        .thenReturn(Optional.of(mockAsset));
    when(accountBrandingAssetService.prepareAndUploadBrandingAsset(
             eq(ACCOUNT_ID), eq(null), eq(null), eq(BrandingAssetType.SMALL_LOGO_LIGHT), any()))
        .thenReturn(Optional.empty());
    when(accountBrandingAssetService.prepareAndUploadBrandingAsset(
             eq(ACCOUNT_ID), eq(null), eq(null), eq(BrandingAssetType.FAVICON), any()))
        .thenReturn(Optional.empty());
    when(accountBrandingAssetService.prepareAndUploadBrandingAsset(
             eq(ACCOUNT_ID), eq(null), eq(null), eq(BrandingAssetType.LARGE_LOGO_DARK), any()))
        .thenReturn(Optional.empty());

    when(brandingRepository.findByAccountIdentifier(ACCOUNT_ID)).thenReturn(Optional.empty());
    when(transactionTemplate.execute(any())).thenReturn(mockBranding);
    when(accountBrandingAssetService.saveAllAssets(eq(ACCOUNT_ID), any())).thenReturn(Arrays.asList(mockAsset));
    when(brandingMapper.toBrandingAssetsDTO(mockAsset)).thenReturn(mockAssetDTO);
    when(brandingMapper.toBrandingSettingsDTO(mockBranding)).thenReturn(mockSettingsDTO);

    BrandingResponseDTO result = accountBrandingService.saveBrandingInfo(
        ACCOUNT_ID, largeLogoLightStream, "png", null, null, null, null, null, null, true);

    assertThat(result).isNotNull();
    assertThat(result.getSettings()).isEqualTo(mockSettingsDTO);
    assertThat(result.getSavedAssets()).hasSize(1);
    assertThat(result.getSavedAssets().get(0)).isEqualTo(mockAssetDTO);

    verify(accountBrandingAssetService, times(4)).prepareAndUploadBrandingAsset(any(), any(), any(), any(), any());
    verify(accountBrandingAssetService, times(1)).saveAllAssets(eq(ACCOUNT_ID), any());
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testGetBrandingSettings() {
    Branding mockBranding = Branding.builder().accountIdentifier(ACCOUNT_ID).brandingOnSignInPage(true).build();

    when(brandingRepository.findByAccountIdentifier(ACCOUNT_ID)).thenReturn(Optional.of(mockBranding));

    BrandingSettingsDTO result = accountBrandingService.getBrandingSettings(ACCOUNT_ID);

    assertThat(result).isNotNull();
    assertThat(result.isBrandingOnSignInPage()).isTrue();

    verify(brandingRepository, times(1)).findByAccountIdentifier(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testGetBrandingSettingsNotFound() {
    when(brandingRepository.findByAccountIdentifier(ACCOUNT_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> accountBrandingService.getBrandingSettings(ACCOUNT_ID))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessageContaining("Branding Settings does not exist for the account");

    verify(brandingRepository, times(1)).findByAccountIdentifier(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testSaveBrandingInfoWithExistingBranding() {
    Branding existingBranding = Branding.builder().accountIdentifier(ACCOUNT_ID).brandingOnSignInPage(false).build();

    Branding updatedBranding = Branding.builder().accountIdentifier(ACCOUNT_ID).brandingOnSignInPage(true).build();

    when(brandingRepository.findByAccountIdentifier(ACCOUNT_ID)).thenReturn(Optional.of(existingBranding));
    when(transactionTemplate.execute(any())).thenReturn(updatedBranding);
    when(accountBrandingAssetService.prepareAndUploadBrandingAsset(any(), any(), any(), any(), any()))
        .thenReturn(Optional.empty());
    when(accountBrandingAssetService.saveAllAssets(eq(ACCOUNT_ID), any())).thenReturn(new ArrayList<>());
    when(brandingMapper.toBrandingSettingsDTO(updatedBranding))
        .thenReturn(new BrandingSettingsDTO().brandingOnSignInPage(true));

    BrandingResponseDTO result =
        accountBrandingService.saveBrandingInfo(ACCOUNT_ID, null, null, null, null, null, null, null, null, true);

    assertThat(result).isNotNull();
    assertThat(result.getSettings().isBrandingOnSignInPage()).isTrue();

    verify(brandingRepository, times(1)).findByAccountIdentifier(ACCOUNT_ID);
    verify(transactionTemplate, times(1)).execute(any());
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testSaveBrandingInfoWithErrors() {
    when(accountBrandingAssetService.prepareAndUploadBrandingAsset(any(), any(), any(), any(), any()))
        .thenAnswer(invocation -> {
          java.util.Map<String, String> errorMap = invocation.getArgument(4);
          errorMap.put("LARGE_LOGO_LIGHT", "Invalid file format");
          return Optional.empty();
        });

    when(brandingRepository.findByAccountIdentifier(ACCOUNT_ID)).thenReturn(Optional.empty());
    when(transactionTemplate.execute(any()))
        .thenReturn(Branding.builder().accountIdentifier(ACCOUNT_ID).brandingOnSignInPage(true).build());
    when(accountBrandingAssetService.saveAllAssets(eq(ACCOUNT_ID), any())).thenReturn(new ArrayList<>());
    when(brandingMapper.toBrandingSettingsDTO(any())).thenReturn(new BrandingSettingsDTO().brandingOnSignInPage(true));

    BrandingResponseDTO result = accountBrandingService.saveBrandingInfo(
        ACCOUNT_ID, new ByteArrayInputStream("test".getBytes()), "invalid", null, null, null, null, null, null, true);

    assertThat(result).isNotNull();
    assertThat(result.getErrors()).hasSize(1);
    assertThat(result.getErrors().get(0).getAssetType()).isEqualTo("LARGE_LOGO_LIGHT");
    assertThat(result.getErrors().get(0).getError()).isEqualTo("Invalid file format");
  }
}