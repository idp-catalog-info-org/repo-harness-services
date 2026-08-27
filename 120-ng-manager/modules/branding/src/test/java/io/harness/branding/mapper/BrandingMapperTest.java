/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.branding.mapper;

import static io.harness.rule.OwnerRule.YASH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.branding.entities.Branding;
import io.harness.branding.entities.BrandingAsset;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.spec.server.ng.v1.model.BrandingAssetsDTO;
import io.harness.spec.server.ng.v1.model.BrandingSettingsDTO;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PL)
public class BrandingMapperTest extends CategoryTest {
  private BrandingMapper brandingMapper;

  @Before
  public void setUp() {
    brandingMapper = new BrandingMapper();
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testToBrandingSettingsDTO() {
    Branding branding = Branding.builder().accountIdentifier("test-account").brandingOnSignInPage(true).build();

    BrandingSettingsDTO result = brandingMapper.toBrandingSettingsDTO(branding);

    assertThat(result).isNotNull();
    assertThat(result.isBrandingOnSignInPage()).isTrue();
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testToBrandingSettingsDTOWithFalse() {
    Branding branding = Branding.builder().accountIdentifier("test-account").brandingOnSignInPage(false).build();

    BrandingSettingsDTO result = brandingMapper.toBrandingSettingsDTO(branding);

    assertThat(result).isNotNull();
    assertThat(result.isBrandingOnSignInPage()).isFalse();
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testToBrandingAssetsDTO() {
    BrandingAsset brandingAsset = BrandingAsset.builder()
                                      .accountIdentifier("test-account")
                                      .assetId("test-asset-id")
                                      .assetType("LARGE_LOGO_LIGHT")
                                      .mimeType("image/png")
                                      .build();

    BrandingAssetsDTO result = brandingMapper.toBrandingAssetsDTO(brandingAsset);

    assertThat(result).isNotNull();
    assertThat(result.getAssetId()).isEqualTo("test-asset-id");
    assertThat(result.getAssetType()).isEqualTo("LARGE_LOGO_LIGHT");
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testToBrandingAssetsDTOWithNullValues() {
    BrandingAsset brandingAsset =
        BrandingAsset.builder().accountIdentifier("test-account").assetId(null).assetType(null).build();

    BrandingAssetsDTO result = brandingMapper.toBrandingAssetsDTO(brandingAsset);

    assertThat(result).isNotNull();
    assertThat(result.getAssetId()).isNull();
    assertThat(result.getAssetType()).isNull();
  }
}