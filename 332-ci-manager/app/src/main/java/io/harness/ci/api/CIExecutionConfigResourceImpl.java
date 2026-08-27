/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.api;

import static io.harness.account.accesscontrol.AccountAccessControlPermissions.EDIT_ACCOUNT_PERMISSION;
import static io.harness.account.accesscontrol.AccountAccessControlPermissions.VIEW_ACCOUNT_PERMISSION;
import static io.harness.account.accesscontrol.ResourceTypes.ACCOUNT;
import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.sweepingoutputs.StageInfraDetails.Type;
import io.harness.ci.beans.entities.CIExecutionImages;
import io.harness.ci.config.Operation;
import io.harness.ci.execution.DeprecatedImageInfo;
import io.harness.ci.execution.execution.intfc.CIExecutionConfigService;
import io.harness.cimanager.executionconfig.api.CIExecutionConfigResource;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;

import com.google.inject.Inject;
import java.util.List;
import lombok.AllArgsConstructor;

@OwnedBy(CI)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@NextGenManagerAuth
public class CIExecutionConfigResourceImpl implements CIExecutionConfigResource {
  @Inject CIExecutionConfigService configService;

  @NGAccessControlCheck(resourceType = ACCOUNT, permission = EDIT_ACCOUNT_PERMISSION)
  public ResponseDTO<Boolean> updateExecutionConfig(
      Type infra, @AccountIdentifier String accountIdentifier, List<Operation> operations) {
    return ResponseDTO.newResponse(configService.updateCIContainerTags(accountIdentifier, operations, infra));
  }

  @NGAccessControlCheck(resourceType = ACCOUNT, permission = EDIT_ACCOUNT_PERMISSION)
  public ResponseDTO<Boolean> resetExecutionConfig(
      Type infra, @AccountIdentifier String accountIdentifier, List<Operation> operations) {
    return ResponseDTO.newResponse(configService.resetCIContainerTags(accountIdentifier, operations, infra));
  }

  @NGAccessControlCheck(resourceType = ACCOUNT, permission = EDIT_ACCOUNT_PERMISSION)
  public ResponseDTO<Boolean> deleteExecutionConfig(@AccountIdentifier String accountIdentifier) {
    return ResponseDTO.newResponse(configService.deleteCIExecutionConfig(accountIdentifier));
  }

  @NGAccessControlCheck(resourceType = ACCOUNT, permission = VIEW_ACCOUNT_PERMISSION)
  public ResponseDTO<List<DeprecatedImageInfo>> getExecutionConfig(@AccountIdentifier String accountIdentifier) {
    return ResponseDTO.newResponse(configService.getDeprecatedTags(accountIdentifier));
  }

  @NGAccessControlCheck(resourceType = ACCOUNT, permission = VIEW_ACCOUNT_PERMISSION)
  public ResponseDTO<CIExecutionImages> getDeprecatedConfig(@AccountIdentifier String accountIdentifier) {
    return ResponseDTO.newResponse(configService.getDeprecatedImages(accountIdentifier));
  }

  @NGAccessControlCheck(resourceType = ACCOUNT, permission = VIEW_ACCOUNT_PERMISSION)
  public ResponseDTO<CIExecutionImages> getCustomerConfig(
      Type infra, boolean overridesOnly, @AccountIdentifier String accountIdentifier) {
    CIExecutionImages ciExecutionImages = configService.getCustomerConfig(accountIdentifier, infra, overridesOnly);
    return ResponseDTO.newResponse(ciExecutionImages);
  }

  @NGAccessControlCheck(resourceType = ACCOUNT, permission = VIEW_ACCOUNT_PERMISSION)
  public ResponseDTO<CIExecutionImages> getDefaultConfig(@AccountIdentifier String accountIdentifier, Type infra) {
    return ResponseDTO.newResponse(configService.getDefaultConfig(infra));
  }
}
