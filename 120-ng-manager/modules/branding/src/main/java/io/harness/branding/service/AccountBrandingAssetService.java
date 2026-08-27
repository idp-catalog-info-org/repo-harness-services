/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.branding.service;

import io.harness.branding.entities.BrandingAsset;
import io.harness.branding.enums.BrandingAssetType;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface AccountBrandingAssetService {
  Optional<BrandingAsset> prepareAndUploadBrandingAsset(String accountIdentifier, InputStream inputStream,
      String extension, BrandingAssetType assetType, Map<String, String> errorMap);
  Iterable<BrandingAsset> saveAllAssets(String accountIdentifier, List<BrandingAsset> brandingAssetList);
  BrandingAsset getBrandingAsset(String accountIdentifier, String type);
  void deleteBrandingAsset(String accountIdentifier, String type);
}
