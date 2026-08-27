/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service;

import static io.harness.NGCommonEntityConstants.ACCOUNT_KEY;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_NESTS;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.logging.AutoLogContext;
import io.harness.pms.ngpipeline.inputset.service.PMSInputSetInlineHcMigrationService;
import io.harness.pms.pipeline.ConsolidatedRollbackResponse;
import io.harness.pms.pipeline.InlineHcMigrationEntityType;
import io.harness.pms.pipeline.RollbackResponse;
import io.harness.pms.pipeline.service.intfc.InlineHcRollbackService;
import io.harness.pms.pipeline.service.intfc.PMSPipelineInlineHcMigrationService;
import io.harness.remote.client.NGRestUtils;
import io.harness.template.remote.TemplateResourceClient;
import io.harness.template.resources.beans.RollbackResponseDTO;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class InlineHcRollbackServiceImpl implements InlineHcRollbackService {
  private final PMSPipelineInlineHcMigrationService pmsPipelineInlineHcMigrationService;
  private final PMSInputSetInlineHcMigrationService pmsInputSetInlineHcMigrationService;
  private final TemplateResourceClient templateResourceClient;

  @Override
  public ConsolidatedRollbackResponse rollbackFromInlineHCToInline(
      String accountIdentifier, InlineHcMigrationEntityType entityType) {
    try (AutoLogContext ignore = new AutoLogContext(Map.of(ACCOUNT_KEY, accountIdentifier), OVERRIDE_NESTS)) {
      log.info("Starting rollback from INLINE_HC to INLINE, entityType: {}", entityType);

      Long pipelineMigratedCount = 0L;
      Long inputSetMigratedCount = 0L;
      Long templateMigratedCount = 0L;
      List<String> errors = new ArrayList<>();

      if (entityType == InlineHcMigrationEntityType.PIPELINE || entityType == InlineHcMigrationEntityType.ALL) {
        try {
          RollbackResponse pipelineResponse =
              pmsPipelineInlineHcMigrationService.rollbackPipelinesFromInlineHCToInline(accountIdentifier);
          pipelineMigratedCount = pipelineResponse.getMigratedCount();
        } catch (Exception e) {
          String error = String.format("Error while rolling back pipelines. Error: %s", e.getMessage());
          log.error(error, e);
          errors.add(error);
        }
      }

      if (entityType == InlineHcMigrationEntityType.INPUT_SET || entityType == InlineHcMigrationEntityType.ALL) {
        try {
          io.harness.pms.ngpipeline.inputset.beans.resource.RollbackResponse inputSetResponse =
              pmsInputSetInlineHcMigrationService.rollbackInputSetsFromInlineHCToInline(accountIdentifier);
          inputSetMigratedCount = inputSetResponse.getMigratedCount();
        } catch (Exception e) {
          String error = String.format("Error while rolling back input sets. Error: %s", e.getMessage());
          log.error(error, e);
          errors.add(error);
        }
      }

      if (entityType == InlineHcMigrationEntityType.TEMPLATE || entityType == InlineHcMigrationEntityType.ALL) {
        try {
          RollbackResponseDTO templateResponse =
              NGRestUtils.getResponse(templateResourceClient.rollbackInlineHCToInline(accountIdentifier));
          if (templateResponse != null) {
            templateMigratedCount = templateResponse.getMigratedCount();
          }
        } catch (Exception e) {
          String error = String.format("Error while rolling back templates. Error: %s", e.getMessage());
          log.error(error, e);
          errors.add(error);
        }
      }

      return ConsolidatedRollbackResponse.builder()
          .pipelineMigratedCount(pipelineMigratedCount)
          .inputSetMigratedCount(inputSetMigratedCount)
          .templateMigratedCount(templateMigratedCount)
          .errors(errors)
          .build();
    }
  }
}
