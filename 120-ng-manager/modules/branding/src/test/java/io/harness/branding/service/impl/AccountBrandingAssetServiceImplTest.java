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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.branding.entities.BrandingAsset;
import io.harness.branding.enums.BrandingAssetType;
import io.harness.branding.enums.MimeType;
import io.harness.branding.validation.AssetValidationResult;
import io.harness.branding.validation.AssetValidator;
import io.harness.category.element.UnitTests;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InternalServerErrorException;
import io.harness.file.beans.NGBaseFile;
import io.harness.outbox.api.OutboxService;
import io.harness.repositories.spring.BrandingAssetsRepository;
import io.harness.rule.Owner;

import software.wings.service.intfc.FileService;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(HarnessTeam.PL)
public class AccountBrandingAssetServiceImplTest extends CategoryTest {
  @Mock private FileService fileService;
  @Mock private AssetValidator assetValidator;
  @Mock private BrandingAssetsRepository assetsRepository;
  @Mock private TransactionTemplate transactionTemplate;
  @Mock private OutboxService outboxService;

  @InjectMocks private AccountBrandingAssetServiceImpl accountBrandingAssetService;

  private static final String ACCOUNT_ID = "test-account";
  private static final String ASSET_ID = "test-asset-id";
  private static final String ASSET_TYPE = "LARGE_LOGO_LIGHT";

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testGetBrandingAsset() {
    BrandingAsset mockAsset =
        BrandingAsset.builder().accountIdentifier(ACCOUNT_ID).assetId(ASSET_ID).assetType(ASSET_TYPE).build();

    when(assetsRepository.findByAccountIdentifierAndAssetType(ACCOUNT_ID, ASSET_TYPE))
        .thenReturn(Optional.of(mockAsset));

    BrandingAsset result = accountBrandingAssetService.getBrandingAsset(ACCOUNT_ID, ASSET_TYPE);

    assertThat(result).isNotNull();
    assertThat(result.getAccountIdentifier()).isEqualTo(ACCOUNT_ID);
    assertThat(result.getAssetId()).isEqualTo(ASSET_ID);
    assertThat(result.getAssetType()).isEqualTo(ASSET_TYPE);

    verify(assetsRepository, times(1)).findByAccountIdentifierAndAssetType(ACCOUNT_ID, ASSET_TYPE);
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testGetBrandingAssetNotFound() {
    when(assetsRepository.findByAccountIdentifierAndAssetType(ACCOUNT_ID, ASSET_TYPE)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> accountBrandingAssetService.getBrandingAsset(ACCOUNT_ID, ASSET_TYPE))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessageContaining("Branding Asset with type [LARGE_LOGO_LIGHT] does not exist");

    verify(assetsRepository, times(1)).findByAccountIdentifierAndAssetType(ACCOUNT_ID, ASSET_TYPE);
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testDeleteBrandingAsset() {
    BrandingAsset mockAsset =
        BrandingAsset.builder().accountIdentifier(ACCOUNT_ID).assetId(ASSET_ID).assetType(ASSET_TYPE).build();

    when(assetsRepository.findByAccountIdentifierAndAssetType(ACCOUNT_ID, ASSET_TYPE))
        .thenReturn(Optional.of(mockAsset));
    when(transactionTemplate.execute(any())).thenReturn(null);
    doNothing().when(fileService).deleteFile(eq(ASSET_ID), any());
    doNothing().when(assetsRepository).delete(mockAsset);

    accountBrandingAssetService.deleteBrandingAsset(ACCOUNT_ID, ASSET_TYPE);

    verify(assetsRepository, times(1)).findByAccountIdentifierAndAssetType(ACCOUNT_ID, ASSET_TYPE);
    verify(transactionTemplate, times(1)).execute(any());
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testDeleteBrandingAssetNotFound() {
    when(assetsRepository.findByAccountIdentifierAndAssetType(ACCOUNT_ID, ASSET_TYPE)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> accountBrandingAssetService.deleteBrandingAsset(ACCOUNT_ID, ASSET_TYPE))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessageContaining("Branding Asset with type [LARGE_LOGO_LIGHT] does not exist");

    verify(assetsRepository, times(1)).findByAccountIdentifierAndAssetType(ACCOUNT_ID, ASSET_TYPE);
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testDeleteBrandingAssetWithException() {
    BrandingAsset mockAsset =
        BrandingAsset.builder().accountIdentifier(ACCOUNT_ID).assetId(ASSET_ID).assetType(ASSET_TYPE).build();

    when(assetsRepository.findByAccountIdentifierAndAssetType(ACCOUNT_ID, ASSET_TYPE))
        .thenReturn(Optional.of(mockAsset));
    when(transactionTemplate.execute(any())).thenThrow(new RuntimeException("Database error"));

    assertThatThrownBy(() -> accountBrandingAssetService.deleteBrandingAsset(ACCOUNT_ID, ASSET_TYPE))
        .isInstanceOf(InternalServerErrorException.class)
        .hasMessageContaining("Error occurred while deleting asset");

    verify(assetsRepository, times(1)).findByAccountIdentifierAndAssetType(ACCOUNT_ID, ASSET_TYPE);
    verify(transactionTemplate, times(1)).execute(any());
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testPrepareAndUploadBrandingAsset() {
    InputStream inputStream = new ByteArrayInputStream("test-image-data".getBytes());
    String extension = "png";
    BrandingAssetType assetType = BrandingAssetType.LARGE_LOGO_LIGHT;
    Map<String, String> errorMap = new HashMap<>();

    AssetValidationResult validationResult = AssetValidationResult.builder().valid(true).build();

    when(assetValidator.validateAsset(any(byte[].class), eq(extension), eq(assetType))).thenReturn(validationResult);
    when(assetsRepository.findByAccountIdentifierAndAssetType(ACCOUNT_ID, assetType.getAssetName()))
        .thenReturn(Optional.empty());
    when(fileService.saveFile(any(NGBaseFile.class), any(InputStream.class), any())).thenReturn("new-file-id");

    Optional<BrandingAsset> result = accountBrandingAssetService.prepareAndUploadBrandingAsset(
        ACCOUNT_ID, inputStream, extension, assetType, errorMap);

    assertThat(result).isPresent();
    assertThat(result.get().getAccountIdentifier()).isEqualTo(ACCOUNT_ID);
    assertThat(result.get().getAssetType()).isEqualTo(assetType.getAssetName());
    assertThat(result.get().getAssetId()).isEqualTo("new-file-id");
    assertThat(result.get().getMimeType()).isEqualTo(MimeType.fromExtension(extension).getType());
    assertThat(errorMap).isEmpty();

    verify(assetValidator, times(1)).validateAsset(any(byte[].class), eq(extension), eq(assetType));
    verify(fileService, times(1)).saveFile(any(NGBaseFile.class), any(InputStream.class), any());
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testPrepareAndUploadBrandingAssetWithExistingAsset() {
    InputStream inputStream = new ByteArrayInputStream("test-image-data".getBytes());
    String extension = "png";
    BrandingAssetType assetType = BrandingAssetType.LARGE_LOGO_LIGHT;
    Map<String, String> errorMap = new HashMap<>();

    BrandingAsset existingAsset = BrandingAsset.builder()
                                      .accountIdentifier(ACCOUNT_ID)
                                      .assetId("old-file-id")
                                      .assetType(assetType.getAssetName())
                                      .build();

    AssetValidationResult validationResult = AssetValidationResult.builder().valid(true).build();

    when(assetValidator.validateAsset(any(byte[].class), eq(extension), eq(assetType))).thenReturn(validationResult);
    when(assetsRepository.findByAccountIdentifierAndAssetType(ACCOUNT_ID, assetType.getAssetName()))
        .thenReturn(Optional.of(existingAsset));
    when(fileService.saveFile(any(NGBaseFile.class), any(InputStream.class), any())).thenReturn("new-file-id");
    doNothing().when(fileService).deleteFile(eq("old-file-id"), any());

    Optional<BrandingAsset> result = accountBrandingAssetService.prepareAndUploadBrandingAsset(
        ACCOUNT_ID, inputStream, extension, assetType, errorMap);

    assertThat(result).isPresent();
    assertThat(result.get().getAssetId()).isEqualTo("new-file-id");
    assertThat(errorMap).isEmpty();

    verify(fileService, times(1)).deleteFile(eq("old-file-id"), any());
    verify(fileService, times(1)).saveFile(any(NGBaseFile.class), any(InputStream.class), any());
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testPrepareAndUploadBrandingAssetWithValidationError() {
    InputStream inputStream = new ByteArrayInputStream("invalid-data".getBytes());
    String extension = "invalid";
    BrandingAssetType assetType = BrandingAssetType.LARGE_LOGO_LIGHT;
    Map<String, String> errorMap = new HashMap<>();

    AssetValidationResult validationResult =
        AssetValidationResult.builder().valid(false).errorMessage("Invalid file format").build();

    when(assetValidator.validateAsset(any(byte[].class), eq(extension), eq(assetType))).thenReturn(validationResult);

    Optional<BrandingAsset> result = accountBrandingAssetService.prepareAndUploadBrandingAsset(
        ACCOUNT_ID, inputStream, extension, assetType, errorMap);

    assertThat(result).isEmpty();
    assertThat(errorMap).containsEntry(assetType.getAssetName(), "Invalid file format");

    verify(assetValidator, times(1)).validateAsset(any(byte[].class), eq(extension), eq(assetType));
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testPrepareAndUploadBrandingAssetWithNullInputStream() {
    BrandingAssetType assetType = BrandingAssetType.LARGE_LOGO_LIGHT;
    Map<String, String> errorMap = new HashMap<>();

    AssetValidationResult successResult = AssetValidationResult.builder().valid(true).build();

    when(assetValidator.validateAsset(null, null, assetType)).thenReturn(successResult);
    Optional<BrandingAsset> result =
        accountBrandingAssetService.prepareAndUploadBrandingAsset(ACCOUNT_ID, null, null, assetType, errorMap);

    assertThat(result).isEmpty();
    assertThat(errorMap).isEmpty();
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testSaveAllAssets() {
    BrandingAsset asset1 = BrandingAsset.builder().accountIdentifier(ACCOUNT_ID).assetId("asset1").build();
    BrandingAsset asset2 = BrandingAsset.builder().accountIdentifier(ACCOUNT_ID).assetId("asset2").build();
    List<BrandingAsset> assetList = Arrays.asList(asset1, asset2);

    when(transactionTemplate.execute(any())).thenReturn(assetList);
    when(assetsRepository.saveAll(assetList)).thenReturn(assetList);

    Iterable<BrandingAsset> result = accountBrandingAssetService.saveAllAssets(ACCOUNT_ID, assetList);

    assertThat(result).isNotNull();
    assertThat(result).containsExactlyElementsOf(assetList);

    verify(transactionTemplate, times(1)).execute(any());
  }
}