package io.harness.branding.mapper;
/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.branding.entities.Branding;
import io.harness.branding.entities.BrandingAsset;
import io.harness.spec.server.ng.v1.model.BrandingAssetsDTO;
import io.harness.spec.server.ng.v1.model.BrandingSettingsDTO;

@OwnedBy(PL)
public class BrandingMapper {
  public BrandingSettingsDTO toBrandingSettingsDTO(Branding branding) {
    return new BrandingSettingsDTO().brandingOnSignInPage(branding.isBrandingOnSignInPage());
  }
  public BrandingAssetsDTO toBrandingAssetsDTO(BrandingAsset brandingAsset) {
    return new BrandingAssetsDTO().assetId(brandingAsset.getAssetId()).assetType(brandingAsset.getAssetType());
  }
}
