/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.ci.api;

import static io.harness.account.accesscontrol.AccountAccessControlPermissions.VIEW_ACCOUNT_PERMISSION;
import static io.harness.account.accesscontrol.ResourceTypes.ACCOUNT;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ci.execution.execution.intfc.CIBuildImageVmConfigService;
import io.harness.ci.pipeline.executions.beans.BuildImageConfigDTO;
import io.harness.ci.pipeline.executions.beans.CIBuildImageVmConfigResource;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;

import com.google.inject.Inject;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CI)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@NextGenManagerAuth
public class CIVmImageConfigResourceImpl implements CIBuildImageVmConfigResource {
  @Inject CIBuildImageVmConfigService ciBuildImageVmConfigService;

  @Override
  @NGAccessControlCheck(resourceType = ACCOUNT, permission = VIEW_ACCOUNT_PERMISSION)
  public ResponseDTO<BuildImageConfigDTO> getBuildVmImageConfig(@AccountIdentifier String accountIdentifier) {
    try {
      return ResponseDTO.newResponse(ciBuildImageVmConfigService.getBuildImageConfigOrDefault(accountIdentifier));
    } catch (Exception e) {
      log.error("Failed to get build image config for account: {}", accountIdentifier, e);
      return ResponseDTO.newResponse(null);
    }
  }
}
