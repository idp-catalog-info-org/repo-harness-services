/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.branding.validation;

import io.harness.branding.enums.BrandingAssetType;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AssetValidator {
  public AssetValidationResult validateAsset(byte[] fileData, String extension, BrandingAssetType assetType) {
    boolean isFileNull = fileData == null || fileData.length == 0;
    if (isFileNull && extension != null) {
      return AssetValidationResult.failure(
          String.format("Extension provided for %s but no input stream found", assetType.getAssetName()));
    }

    if (!isFileNull && extension == null) {
      return AssetValidationResult.failure(
          String.format("Input stream provided for %s but no mimeType found", assetType.getAssetName()));
    }

    if (isFileNull) {
      return AssetValidationResult.success(); // Optional asset
    }

    AssetValidationResult extensionValidation = validateExtension(assetType, extension);
    if (!extensionValidation.isValid()) {
      return extensionValidation;
    }

    AssetValidationResult sizeValidation = validateSize(fileData, assetType);
    if (!sizeValidation.isValid()) {
      return sizeValidation;
    }

    return AssetValidationResult.success();
  }

  private AssetValidationResult validateExtension(BrandingAssetType assetType, String extension) {
    if (!assetType.isValidExtension(extension)) {
      return AssetValidationResult.failure(String.format("Invalid mimeType '%s' for %s. Allowed extensions: %s",
          extension, assetType.getAssetName(), assetType.getAllowedExtensions()));
    }
    return AssetValidationResult.success();
  }

  private AssetValidationResult validateSize(byte[] fileData, BrandingAssetType assetType) {
    long size = fileData.length;

    if (size > assetType.getMaxSizeBytes()) {
      return AssetValidationResult.failure(String.format("File size %d KB exceeds maximum allowed size %d KB for %s",
          size / 1024, assetType.getMaxSizeBytes() / 1024, assetType.getAssetName()));
    }

    return AssetValidationResult.success();
  }
}