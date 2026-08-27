/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.branding.outbox.brandingasset.events;

import static io.harness.audit.ResourceTypeConstants.BRANDING_ASSET;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.branding.entities.BrandingAsset;
import io.harness.event.Event;
import io.harness.ng.core.AccountScope;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceScope;

import lombok.Getter;
import lombok.NoArgsConstructor;

@OwnedBy(HarnessTeam.PL)
@Getter
@NoArgsConstructor
public abstract class BrandingAssetEvent implements Event {
  public static final String BRANDING_ASSET_UPLOADED = "branding_asset_uploaded";
  public static final String BRANDING_ASSET_DELETED = "branding_asset_deleted";

  String accountIdentifier;
  BrandingAsset brandingAsset;

  public BrandingAssetEvent(String accountIdentifier, BrandingAsset brandingAsset) {
    this.accountIdentifier = accountIdentifier;
    this.brandingAsset = brandingAsset;
  }

  @Override
  public ResourceScope getResourceScope() {
    return new AccountScope(accountIdentifier);
  }

  @Override
  public Resource getResource() {
    return Resource.builder()
        .identifier(brandingAsset.getId())
        .uniqueId(brandingAsset.getUniqueId())
        .type(BRANDING_ASSET)
        .build();
  }

  @Override public abstract String getEventType();
}
