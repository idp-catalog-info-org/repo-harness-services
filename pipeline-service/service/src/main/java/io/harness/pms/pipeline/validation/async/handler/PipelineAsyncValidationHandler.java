/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.validation.async.handler;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ngexception.beans.yamlschema.YamlSchemaErrorDTO;
import io.harness.exception.ngexception.beans.yamlschema.YamlSchemaErrorWrapperDTO;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.governance.GovernanceMetadata;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.ng.core.template.refresh.ValidateTemplateInputsResponseDTO;
import io.harness.ng.core.template.refresh.v2.ValidateTemplateReconcileResponseDTO;
import io.harness.pms.opa.gitx.pipeline.PipelineOpaStatusHandler;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.TemplateValidationResponseDTO;
import io.harness.pms.pipeline.governance.service.PipelineGovernanceService;
import io.harness.pms.pipeline.service.enforcement.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.validation.async.beans.PipelineValidationEvent;
import io.harness.pms.pipeline.validation.async.beans.ValidationResult;
import io.harness.pms.pipeline.validation.async.beans.ValidationStatus;
import io.harness.pms.pipeline.validation.async.service.PipelineAsyncValidationService;
import io.harness.pms.pipeline.validation.service.intfc.PipelineValidationService;
import io.harness.pms.template.service.PipelineRefreshService;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.yaml.validator.InvalidYamlException;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class PipelineAsyncValidationHandler implements Runnable {
  private final PipelineValidationEvent validationEvent;
  private final boolean loadFromCache; // todo: see if this can be set to true always
  private final ScopeInfo scopeInfo;
  private final boolean isParentIdQueryingEnabled;
  private final PipelineAsyncValidationService validationService;
  private final PMSPipelineTemplateHelper pipelineTemplateHelper;
  private final PipelineGovernanceService pipelineGovernanceService;
  private final PipelineRefreshService pipelineRefreshService;
  private final PipelineValidationService pipelineValidationService;
  private final PmsFeatureFlagService pmsFeatureFlagService;
  private final PipelineOpaStatusHandler pipelineOpaStatusHandler;

  public PipelineAsyncValidationHandler(PipelineValidationEvent validationEvent, boolean loadFromCache,
      ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled, PipelineAsyncValidationService validationService,
      PMSPipelineTemplateHelper pipelineTemplateHelper, PipelineGovernanceService pipelineGovernanceService,
      PipelineRefreshService pipelineRefreshService, PipelineValidationService pipelineValidationService,
      PmsFeatureFlagService pmsFeatureFlagService, PipelineOpaStatusHandler pipelineOpaStatusHandler) {
    this.validationEvent = validationEvent;
    this.loadFromCache = loadFromCache;
    this.scopeInfo = scopeInfo;
    this.isParentIdQueryingEnabled = isParentIdQueryingEnabled;
    this.validationService = validationService;
    this.pipelineTemplateHelper = pipelineTemplateHelper;
    this.pipelineGovernanceService = pipelineGovernanceService;
    this.pipelineRefreshService = pipelineRefreshService;
    this.pipelineValidationService = pipelineValidationService;
    this.pmsFeatureFlagService = pmsFeatureFlagService;
    this.pipelineOpaStatusHandler = pipelineOpaStatusHandler;
  }

  @Override
  public void run() {
    // When the thread is created, the status to begin with is INITIATED because after creation, it is possible that the
    // thread is picked up after a while rather than immediately. That's why the status is changed to IN_PROGRESS only
    // once the thread has been picked up
    validationService.updateEvent(
        validationEvent.getUuid(), ValidationStatus.IN_PROGRESS, ValidationResult.builder().build());
    PipelineEntity pipelineEntity = validationEvent.getParams().getPipelineEntity();

    // Reconcile check
    ValidateTemplateReconcileResponseDTO validateTemplateReconcileResponseDTO = checkForReconcile(pipelineEntity);

    /* If reconcile is needed then we are setting the Yaml validation and Governance Policy check as default
        and setting the status success
     */
    if (validateTemplateReconcileResponseDTO.isReconcileNeeded()) {
      ValidationResult validationResult =
          ValidationResult.builder()
              .templateValidationResponse(TemplateValidationResponseDTO.builder().validYaml(true).build())
              .build();
      validationResult =
          validationResult.withValidateTemplateReconcileResponseDTO(validateTemplateReconcileResponseDTO);
      validationResult =
          validationResult.withGovernanceMetadata(GovernanceMetadata.newBuilder().setDeny(false).build());
      validationService.updateEvent(validationEvent.getUuid(), ValidationStatus.SUCCESS, validationResult);
      return;
    }

    // Validate templates
    Pair<ValidationResult, TemplateMergeResponseDTO> templateValidation =
        validateTemplatesAndUpdateResult(pipelineEntity);
    ValidationResult templateValidationResult =
        templateValidation.getKey().withValidateTemplateReconcileResponseDTO(validateTemplateReconcileResponseDTO);
    TemplateMergeResponseDTO templateMergeResponse = templateValidation.getValue();
    if (!templateValidationResult.getTemplateValidationResponse().isValidYaml()) {
      return;
    }

    // Evaluate Policies
    evaluatePoliciesAndUpdateResult(pipelineEntity, templateMergeResponse, templateValidationResult);
  }

  Pair<ValidationResult, TemplateMergeResponseDTO> validateTemplatesAndUpdateResult(PipelineEntity pipelineEntity) {
    ValidationResult templateValidationResult;

    TemplateMergeResponseDTO templateMergeResponse = null;
    try {
      templateMergeResponse =
          pipelineTemplateHelper.resolveTemplateRefsInPipeline(pipelineEntity, scopeInfo, true, loadFromCache);
      if (pmsFeatureFlagService.isEnabled(
              pipelineEntity.getAccountIdentifier(), FeatureName.PIE_VALIDATE_SCHEMA_IN_VALIDATE_API)) {
        // Validate the schema after resolving templates. Typically, schema validation is done in the GET pipeline call.
        // However, for pipelines with templates, we skip schema validation after resolving the templates because it's
        // an expensive operation. Therefore, it’s essential to include schema validation in the validate call to
        // identify any schema errors.
        pipelineValidationService.validateYaml(pipelineEntity.getAccountIdentifier(), pipelineEntity.getOrgIdentifier(),
            pipelineEntity.getProjectIdentifier(), templateMergeResponse.getMergedPipelineYaml(),
            pipelineEntity.getYaml(), pipelineEntity.getHarnessVersion());
      }
    } catch (InvalidYamlException ex) {
      log.error("Yaml Schema validation errors found after resolving template", ex);
      StringBuilder validationErrorMessage = getValidationErrorMessage(ex);
      templateValidationResult =
          ValidationResult.builder()
              .templateValidationResponse(TemplateValidationResponseDTO.builder()
                                              .validYaml(false)
                                              .exceptionMessage(String.valueOf(validationErrorMessage))
                                              .build())
              .build();
      validationService.updateEvent(validationEvent.getUuid(), ValidationStatus.FAILURE, templateValidationResult);
      return Pair.of(templateValidationResult, templateMergeResponse);
    } catch (Exception ex) {
      log.error("Error occurred while resolving template!", ex);

      templateValidationResult =
          ValidationResult.builder()
              .templateValidationResponse(
                  TemplateValidationResponseDTO.builder().validYaml(false).exceptionMessage(ex.getMessage()).build())
              .build();
      validationService.updateEvent(validationEvent.getUuid(), ValidationStatus.FAILURE, templateValidationResult);
      return Pair.of(templateValidationResult, templateMergeResponse);
    }

    templateValidationResult =
        ValidationResult.builder()
            .templateValidationResponse(TemplateValidationResponseDTO.builder().validYaml(true).build())
            .build();
    validationService.updateEvent(validationEvent.getUuid(), ValidationStatus.IN_PROGRESS, templateValidationResult);
    // Add Template Module Info temporarily to Pipeline Entity
    pipelineEntity.setTemplateModules(pipelineTemplateHelper.getTemplatesModuleInfo(templateMergeResponse));
    return Pair.of(templateValidationResult, templateMergeResponse);
  }

  private static StringBuilder getValidationErrorMessage(InvalidYamlException ex) {
    StringBuilder validationErrorMessage = new StringBuilder();
    if (ex.getMetadata() == null) {
      validationErrorMessage.append(ex.getMessage());
    } else {
      List<YamlSchemaErrorDTO> schemaErrors = ((YamlSchemaErrorWrapperDTO) ex.getMetadata()).getSchemaErrors();
      for (YamlSchemaErrorDTO yamlSchemaError : schemaErrors) {
        validationErrorMessage.append(yamlSchemaError.getMessageWithFQN());
        validationErrorMessage.append('\n');
      }
    }
    return validationErrorMessage;
  }

  void evaluatePoliciesAndUpdateResult(PipelineEntity pipelineEntity, TemplateMergeResponseDTO templateMergeResponse,
      ValidationResult templateValidationResult) {
    String mergedPipelineYamlWithTemplateRefs = templateMergeResponse.getMergedPipelineYamlWithTemplateRef();

    String branch = GitAwareContextHelper.getBranchInRequestOrFromSCMGitMetadata();

    String orgId = scopeInfo.getOrgIdentifier();
    String projectId = scopeInfo.getProjectIdentifier();

    GovernanceMetadata governanceMetadata = pipelineGovernanceService.validateGovernanceRules(
        pipelineEntity.getAccountId(), orgId, projectId, branch, pipelineEntity, mergedPipelineYamlWithTemplateRefs);
    ValidationResult governanceValidationResult = templateValidationResult.withGovernanceMetadata(governanceMetadata);

    governanceValidationResult = enrichWithOpaEvalData(pipelineEntity, governanceMetadata, governanceValidationResult);

    if (governanceMetadata.getDeny()) {
      validationService.updateEvent(validationEvent.getUuid(), ValidationStatus.FAILURE, governanceValidationResult);
    } else {
      validationService.updateEvent(validationEvent.getUuid(), ValidationStatus.SUCCESS, governanceValidationResult);
    }

    persistOpaOnSaveStatus(pipelineEntity, governanceMetadata);
  }

  ValidationResult enrichWithOpaEvalData(
      PipelineEntity pipelineEntity, GovernanceMetadata governanceMetadata, ValidationResult result) {
    return captureOpaEvalFields(pipelineEntity, governanceMetadata, result, validationEvent.getParams().getCommitId(),
        pipelineOpaStatusHandler);
  }

  public static ValidationResult captureOpaEvalFields(PipelineEntity entity, GovernanceMetadata gm,
      ValidationResult result, String commitId, PipelineOpaStatusHandler opaStatusHandler) {
    try {
      if (gm == null || !GitAwareContextHelper.isRemoteEntity(entity)) {
        return result;
      }
      long evaluatedAt = System.currentTimeMillis();
      boolean isClean = !gm.getDeny();
      String lastValidCommitId;
      if (isClean) {
        lastValidCommitId = commitId;
      } else {
        lastValidCommitId = resolveLastValidCommitIdFromDb(entity, commitId, opaStatusHandler);
      }
      return result.withOpaEvaluatedAt(evaluatedAt).withOpaLastValidCommitId(lastValidCommitId);
    } catch (Exception e) {
      log.warn("Failed to capture OPA eval data on ValidationResult, poll may degrade", e);
      return result;
    }
  }

  private static String resolveLastValidCommitIdFromDb(
      PipelineEntity entity, String commitId, PipelineOpaStatusHandler opaStatusHandler) {
    try {
      return opaStatusHandler.get(entity, entity.getAccountId(), commitId)
          .map(dto -> dto.getLastValidCommitId())
          .orElse(null);
    } catch (Exception e) {
      log.warn("Could not resolve lastValidCommitId from DB at eval-time", e);
      return null;
    }
  }

  void persistOpaOnSaveStatus(PipelineEntity pipelineEntity, GovernanceMetadata governanceMetadata) {
    try {
      if (governanceMetadata == null || !GitAwareContextHelper.isRemoteEntity(pipelineEntity)) {
        return;
      }
      String commitId = validationEvent.getParams().getCommitId();
      pipelineOpaStatusHandler.handleUiApiSave(
          pipelineEntity, pipelineEntity.getAccountId(), governanceMetadata, commitId);
    } catch (Exception e) {
      log.warn("Failed to persist OPA onSave status during validation, continuing without it", e);
    }
  }

  public ValidateTemplateReconcileResponseDTO checkForReconcile(PipelineEntity pipelineEntity) {
    try {
      String orgId = scopeInfo.getOrgIdentifier();
      String projectId = scopeInfo.getProjectIdentifier();

      ValidateTemplateInputsResponseDTO validateTemplateInputsResponseDTO =
          pipelineRefreshService.validateTemplateInputsInPipeline(pipelineEntity.getAccountId(), orgId, projectId,
              pipelineEntity.getIdentifier(), "false", scopeInfo, isParentIdQueryingEnabled);

      return ValidateTemplateReconcileResponseDTO.builder()
          .isReconcileNeeded(!validateTemplateInputsResponseDTO.isValidYaml())
          .build();
    } catch (InvalidRequestException ex) {
      log.info("Error occurred while checking reconcile!", ex);
    } catch (Exception ex) {
      log.error("Error occurred while checking reconcile!", ex);
    }
    return ValidateTemplateReconcileResponseDTO.builder().isReconcileNeeded(false).build();
  }
}