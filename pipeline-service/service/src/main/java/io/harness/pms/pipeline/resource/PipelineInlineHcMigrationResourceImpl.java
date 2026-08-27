/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.resource;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.WingsException;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.pipeline.PipelineInlineHcMigrationResource;
import io.harness.pms.pipeline.RollbackResponse;
import io.harness.pms.pipeline.RollbackResponseDTO;
import io.harness.pms.pipeline.service.intfc.PMSPipelineInlineHcMigrationService;
import io.harness.security.dto.UserPrincipal;
import io.harness.utils.UserHelperService;

import com.google.inject.Inject;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class PipelineInlineHcMigrationResourceImpl implements PipelineInlineHcMigrationResource {
  private final PMSPipelineInlineHcMigrationService pmsPipelineInlineHcMigrationService;
  private final UserHelperService userHelperService;
  private static final String USER_ID_PLACEHOLDER = "{{USER}}";

  @Override
  public ResponseDTO<RollbackResponseDTO> rollbackInlineHCToInline(@NotNull String accountId) {
    checkPermissions(String.format(
        "User : %s not allowed to rollback pipeline entities for account %s", USER_ID_PLACEHOLDER, accountId));
    log.info("Received request to rollback pipelines from INLINE_HC to INLINE for account: {}", accountId);

    RollbackResponse response = pmsPipelineInlineHcMigrationService.rollbackPipelinesFromInlineHCToInline(accountId);

    RollbackResponseDTO responseDTO = RollbackResponseDTO.builder().migratedCount(response.getMigratedCount()).build();

    return ResponseDTO.newResponse(responseDTO);
  }

  private void checkPermissions(String errorMessageIfAuthorizationFailed) {
    UserPrincipal userPrincipal = userHelperService.getUserPrincipalOrThrow();
    String userId = userPrincipal.getName();
    if (!userHelperService.isHarnessSupportUser(userId)) {
      log.error(errorMessageIfAuthorizationFailed.replace(USER_ID_PLACEHOLDER, userId));
      throw new AccessDeniedException("Not Authorized", WingsException.USER);
    }
  }
}
