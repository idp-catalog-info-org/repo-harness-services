/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.branding.validation;

import static io.harness.rule.OwnerRule.YASH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.branding.enums.BrandingAssetType;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PL)
public class AssetValidatorTest {
  private AssetValidator assetValidator;

  @Before
  public void setUp() {
    assetValidator = new AssetValidator();
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testValidateAssetSuccess() {
    byte[] validFileData = createSamplePngData(100); // 100 bytes
    String validExtension = "png";
    BrandingAssetType assetType = BrandingAssetType.LARGE_LOGO_LIGHT;

    AssetValidationResult result = assetValidator.validateAsset(validFileData, validExtension, assetType);

    assertThat(result).isNotNull();
    assertThat(result.isValid()).isTrue();
    assertThat(result.getErrorMessage()).isNull();
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testValidateAssetWithNullFileDataAndNullExtension() {
    AssetValidationResult result = assetValidator.validateAsset(null, null, BrandingAssetType.LARGE_LOGO_LIGHT);

    assertThat(result).isNotNull();
    assertThat(result.isValid()).isTrue();
    assertThat(result.getErrorMessage()).isNull();
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testValidateAssetWithEmptyFileDataAndNullExtension() {
    byte[] emptyFileData = new byte[0];
    AssetValidationResult result =
        assetValidator.validateAsset(emptyFileData, null, BrandingAssetType.LARGE_LOGO_LIGHT);

    assertThat(result).isNotNull();
    assertThat(result.isValid()).isTrue();
    assertThat(result.getErrorMessage()).isNull();
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testValidateAssetWithNullFileDataButExtensionProvided() {
    String extension = "png";
    BrandingAssetType assetType = BrandingAssetType.LARGE_LOGO_LIGHT;

    AssetValidationResult result = assetValidator.validateAsset(null, extension, assetType);

    assertThat(result).isNotNull();
    assertThat(result.isValid()).isFalse();
    assertThat(result.getErrorMessage()).contains("Extension provided for largeLogoLight but no input stream found");
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testValidateAssetWithFileDataButNullExtension() {
    byte[] fileData = createSamplePngData(100);
    BrandingAssetType assetType = BrandingAssetType.LARGE_LOGO_LIGHT;

    AssetValidationResult result = assetValidator.validateAsset(fileData, null, assetType);

    assertThat(result).isNotNull();
    assertThat(result.isValid()).isFalse();
    assertThat(result.getErrorMessage()).contains("Input stream provided for largeLogoLight but no mimeType found");
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testValidateAssetWithInvalidExtension() {
    byte[] fileData = createSamplePngData(100);
    String invalidExtension = "jpg";
    BrandingAssetType assetType = BrandingAssetType.LARGE_LOGO_LIGHT;

    AssetValidationResult result = assetValidator.validateAsset(fileData, invalidExtension, assetType);

    assertThat(result).isNotNull();
    assertThat(result.isValid()).isFalse();
    assertThat(result.getErrorMessage()).contains("Invalid mimeType 'jpg' for largeLogoLight");
    assertThat(result.getErrorMessage()).contains("Allowed extensions:");
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testValidateAssetExceedsMaxSize() {
    // Create file data larger than 300KB (LARGE_LOGO_LIGHT max size)
    byte[] largeFileData = createSamplePngData(400 * 1024); // 400KB
    String validExtension = "png";
    BrandingAssetType assetType = BrandingAssetType.LARGE_LOGO_LIGHT;

    AssetValidationResult result = assetValidator.validateAsset(largeFileData, validExtension, assetType);

    assertThat(result).isNotNull();
    assertThat(result.isValid()).isFalse();
    assertThat(result.getErrorMessage()).contains("File size");
    assertThat(result.getErrorMessage()).contains("exceeds maximum allowed size");
    assertThat(result.getErrorMessage()).contains("largeLogoLight");
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testValidateAssetWithFaviconValidSize() {
    // Create file data within 50KB (FAVICON max size)
    byte[] validFileData = createSamplePngData(40 * 1024); // 40KB
    String validExtension = "png";
    BrandingAssetType assetType = BrandingAssetType.FAVICON;

    AssetValidationResult result = assetValidator.validateAsset(validFileData, validExtension, assetType);

    assertThat(result).isNotNull();
    assertThat(result.isValid()).isTrue();
    assertThat(result.getErrorMessage()).isNull();
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testValidateAssetExactlyAtMaxSize() {
    // Create file data exactly at 300KB (LARGE_LOGO_LIGHT max size)
    byte[] fileData = createSamplePngData(300 * 1024); // 300KB
    String validExtension = "png";
    BrandingAssetType assetType = BrandingAssetType.LARGE_LOGO_LIGHT;

    AssetValidationResult result = assetValidator.validateAsset(fileData, validExtension, assetType);

    assertThat(result).isNotNull();
    assertThat(result.isValid()).isTrue();
    assertThat(result.getErrorMessage()).isNull();
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testValidateAssetWithNullExtension() {
    byte[] fileData = createSamplePngData(100);
    BrandingAssetType assetType = BrandingAssetType.LARGE_LOGO_LIGHT;

    AssetValidationResult result = assetValidator.validateAsset(fileData, null, assetType);

    assertThat(result).isNotNull();
    assertThat(result.isValid()).isFalse();
    assertThat(result.getErrorMessage()).contains("Input stream provided for largeLogoLight but no mimeType found");
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testValidateAssetWithEmptyExtension() {
    byte[] fileData = createSamplePngData(100);
    String emptyExtension = "";
    BrandingAssetType assetType = BrandingAssetType.LARGE_LOGO_LIGHT;

    AssetValidationResult result = assetValidator.validateAsset(fileData, emptyExtension, assetType);

    assertThat(result).isNotNull();
    assertThat(result.isValid()).isFalse();
    assertThat(result.getErrorMessage()).contains("Invalid mimeType");
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testValidateAssetWithWhitespaceExtension() {
    byte[] fileData = createSamplePngData(100);
    String whitespaceExtension = "   ";
    BrandingAssetType assetType = BrandingAssetType.LARGE_LOGO_LIGHT;

    AssetValidationResult result = assetValidator.validateAsset(fileData, whitespaceExtension, assetType);

    assertThat(result).isNotNull();
    assertThat(result.isValid()).isFalse();
    assertThat(result.getErrorMessage()).contains("Invalid mimeType");
  }

  private byte[] createSamplePngData(int sizeInBytes) {
    return new byte[sizeInBytes];
  }
}