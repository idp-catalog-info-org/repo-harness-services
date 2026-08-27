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
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.pms.pipeline.ConsolidatedRollbackResponse;
import io.harness.pms.pipeline.ConsolidatedRollbackResponseDTO;
import io.harness.pms.pipeline.InlineHcMigrationEntityType;
import io.harness.pms.pipeline.InlineHcRollbackResource;
import io.harness.pms.pipeline.service.intfc.InlineHcRollbackService;
import io.harness.security.dto.UserPrincipal;
import io.harness.utils.UserHelperService;

import com.google.inject.Inject;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@PipelineServiceAuth
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class InlineHcRollbackResourceImpl implements InlineHcRollbackResource {
  private final InlineHcRollbackService inlineHcRollbackService;
  private final UserHelperService userHelperService;
  private static final String USER_ID_PLACEHOLDER = "{{USER}}";

  @Override
  public ResponseDTO<ConsolidatedRollbackResponseDTO> rollbackInlineHCToInline(@NotNull String accountIdentifier,
      @NotNull String targetAccountIdentifier, @NotNull InlineHcMigrationEntityType entityType) {
    checkPermissions(String.format(
        "User : %s not allowed to rollback entities for account %s", USER_ID_PLACEHOLDER, targetAccountIdentifier));
    log.info("Received request to rollback entities from INLINE_HC to INLINE for account: {}, entityType: {}",
        targetAccountIdentifier, entityType);

    ConsolidatedRollbackResponse response =
        inlineHcRollbackService.rollbackFromInlineHCToInline(targetAccountIdentifier, entityType);

    ConsolidatedRollbackResponseDTO responseDTO =
        ConsolidatedRollbackResponseDTO.builder()
            .pipelineMigratedCount(response.getPipelineMigratedCount())
            .inputSetMigratedCount(response.getInputSetMigratedCount())
            .templateMigratedCount(response.getTemplateMigratedCount())
            .totalMigratedCount(response.getPipelineMigratedCount() + response.getInputSetMigratedCount()
                + response.getTemplateMigratedCount())
            .errors(response.getErrors())
            .build();

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
