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
import io.harness.ci.execution.execution.IPAllowlistServiceImpl;
import io.harness.ci.pipeline.executions.beans.IPAllowlistDTO;
import io.harness.ci.pipeline.executions.beans.IPAllowlistResource;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;

import com.google.inject.Inject;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CI)
@NextGenManagerAuth
public class IPAllowlistResourceImpl implements IPAllowlistResource {
  @Inject private IPAllowlistServiceImpl ipAllowlistService;

  @Override
  @NGAccessControlCheck(resourceType = ACCOUNT, permission = VIEW_ACCOUNT_PERMISSION)
  public ResponseDTO<IPAllowlistDTO> getIPAllowlist(@AccountIdentifier String accountIdentifier) {
    try {
      Set<String> ipAddresses = ipAllowlistService.getIPAllowlistForAccountAndModule(accountIdentifier);
      IPAllowlistDTO ipAllowlistDTO = IPAllowlistDTO.builder().ipAddresses(ipAddresses).build();
      return ResponseDTO.newResponse(ipAllowlistDTO);
    } catch (Exception e) {
      log.error("Error retrieving IP allowlist for account {}: {}", accountIdentifier, e.getMessage());
      return ResponseDTO.newResponse(IPAllowlistDTO.builder().build());
    }
  }
}
