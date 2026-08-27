/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.branding.remote.v1.api;

import static io.harness.branding.dtos.BrandingPermissions.BRANDING_EDIT_PERMISSION;
import static io.harness.branding.dtos.BrandingPermissions.BRANDING_RESOURCE_TYPE;
import static io.harness.delegate.beans.FileBucket.BRANDING_ASSETS;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.branding.entities.BrandingAsset;
import io.harness.branding.service.AccountBrandingAssetService;
import io.harness.branding.service.AccountBrandingService;
import io.harness.security.annotations.PublicApi;
import io.harness.spec.server.ng.v1.AccountBrandingApi;
import io.harness.spec.server.ng.v1.model.BrandingResponseDTO;
import io.harness.spec.server.ng.v1.model.BrandingSettingsDTO;

import software.wings.service.intfc.FileService;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Timed
@ResponseMetered
public class AccountBrandingApiImpl implements AccountBrandingApi {
  private final AccountBrandingService accountBrandingService;
  private final AccountBrandingAssetService accountBrandingAssetService;
  private final FileService fileService;

  private static final int MAX_CACHE_CONTROL_AGE = 24 * 60 * 60; // 1 day

  @Inject
  public AccountBrandingApiImpl(AccountBrandingService accountBrandingService,
      AccountBrandingAssetService accountBrandingAssetService, FileService fileService) {
    this.accountBrandingService = accountBrandingService;
    this.accountBrandingAssetService = accountBrandingAssetService;
    this.fileService = fileService;
  }

  @Override
  @NGAccessControlCheck(resourceType = BRANDING_RESOURCE_TYPE, permission = BRANDING_EDIT_PERMISSION)
  public Response deleteBrandingAsset(String type, @AccountIdentifier String harnessAccount) {
    accountBrandingAssetService.deleteBrandingAsset(harnessAccount, type);
    return Response.noContent().build();
  }

  @Override
  @PublicApi
  public Response getBrandingAsset(String type, String harnessAccount) {
    BrandingAsset brandingAsset = accountBrandingAssetService.getBrandingAsset(harnessAccount, type);

    ByteArrayOutputStream os = new ByteArrayOutputStream();
    fileService.downloadToStream(brandingAsset.getAssetId(), os, BRANDING_ASSETS);

    byte[] imageBytes = os.toByteArray();

    CacheControl cacheControl = new CacheControl();
    cacheControl.setMaxAge(MAX_CACHE_CONTROL_AGE);
    cacheControl.setPrivate(true);

    return Response.ok(imageBytes, brandingAsset.getMimeType()).cacheControl(cacheControl).build();
  }

  @Override
  @PublicApi
  public Response getBrandingSettings(String harnessAccount) {
    BrandingSettingsDTO settingsDTO = accountBrandingService.getBrandingSettings(harnessAccount);
    return Response.ok(settingsDTO).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = BRANDING_RESOURCE_TYPE, permission = BRANDING_EDIT_PERMISSION)
  public Response uploadBrandingAssets(InputStream largeLogoLightInputStream, String largeLogoLightExtension,
      InputStream smallLogoLightInputStream, String smallLogoLightExtension, InputStream faviconInputStream,
      String faviconExtension, InputStream largeLogoDarkInputStream, String largeLogoDarkExtension,
      Boolean brandingOnSignInPage, @AccountIdentifier String harnessAccount) {
    BrandingResponseDTO brandingResponseDTO = accountBrandingService.saveBrandingInfo(harnessAccount,
        largeLogoLightInputStream, largeLogoLightExtension, smallLogoLightInputStream, smallLogoLightExtension,
        faviconInputStream, faviconExtension, largeLogoDarkInputStream, largeLogoDarkExtension, brandingOnSignInPage);

    return Response.status(Response.Status.OK).entity(brandingResponseDTO).build();
  }
}