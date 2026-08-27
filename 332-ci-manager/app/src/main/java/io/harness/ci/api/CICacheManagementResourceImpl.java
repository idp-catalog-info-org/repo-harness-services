/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.api;

import static io.harness.account.accesscontrol.AccountAccessControlPermissions.EDIT_ACCOUNT_PERMISSION;
import static io.harness.account.accesscontrol.ResourceTypes.ACCOUNT;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.cache.api.CICacheManagementResource;
import io.harness.beans.cache.api.CacheMetadataInfo;
import io.harness.beans.cache.api.DeleteCacheResponse;
import io.harness.ci.cache.CICacheManagementService;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;

import com.google.inject.Inject;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CI)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@NextGenManagerAuth
public class CICacheManagementResourceImpl implements CICacheManagementResource {
  private final CICacheManagementService ciCacheManagementService;

  @NGAccessControlCheck(resourceType = ACCOUNT, permission = EDIT_ACCOUNT_PERMISSION)
  public ResponseDTO<CacheMetadataInfo> getCacheInfo(@AccountIdentifier String accountIdentifier) {
    log.info("Getting cache information");

    return ResponseDTO.newResponse(ciCacheManagementService.getCacheMetadata(accountIdentifier));
  }

  @NGAccessControlCheck(resourceType = ACCOUNT, permission = EDIT_ACCOUNT_PERMISSION)
  public ResponseDTO<DeleteCacheResponse> deleteCache(
      @AccountIdentifier String accountIdentifier, String path, String cacheType) {
    log.info("Deleting cache");

    return ResponseDTO.newResponse(ciCacheManagementService.deleteCache(accountIdentifier, path, cacheType));
  }
}
