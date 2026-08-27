/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.helper;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.exception.FilterCreatorException;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.ExecutionPlan;
import io.harness.governance.GovernanceMetadata;
import io.harness.governance.PolicySetMetadata;
import io.harness.metrics.ThreadAutoLogContext;
import io.harness.metrics.service.api.MetricService;
import io.harness.opaclient.model.OpaConstants;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.filter.creation.service.FilterCreatorMergeService;
import io.harness.pms.helpers.PrincipalInfoHelper;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.governance.service.PipelineGovernanceService;
import io.harness.pms.pipeline.references.filter.FilterCreationGitMetadata;
import io.harness.pms.pipeline.references.filter.FilterCreationParams;
import io.harness.pms.pipeline.service.helper.PMSPipelineServiceHelper;
import io.harness.pms.plan.execution.dryrun.semantic.SemanticValidator;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.spec.server.pipeline.v1.model.DryRunPipelineRequestBody;
import io.harness.spec.server.pipeline.v1.model.DryRunPipelineResponseBody;
import io.harness.spec.server.pipeline.v1.model.DryRunPipelineValidationResult;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.yaml.validator.InvalidYamlException;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class DryRunHelper {
  private static final String METRIC_DRY_RUN_OUTCOME = "pms_dry_run_outcome";
  private static final String LABEL_ACCOUNT_ID = "accountId";
  private static final String LABEL_ORG_ID = "orgId";
  private static final String LABEL_PROJECT_ID = "projectId";
  private static final String LABEL_STATUS = "status";

  // Validation type constants
  private static final String VALIDATION_TYPE_SYSTEM = "SYSTEM";
  private static final String VALIDATION_TYPE_REFERRED_ENTITIES = "REFERRED_ENTITIES";
  private static final String VALIDATION_TYPE_SCHEMA = "SCHEMA";
  private static final String VALIDATION_TYPE_PERMISSIONS = "PERMISSIONS";
  private static final String VALIDATION_TYPE_POLICY = "POLICY";
  private static final String VALIDATION_TYPE_SEMANTIC = "SEMANTIC";
  private static final String ENTITY_TYPE_PIPELINE = "PIPELINE";

  // Severity constants. Absent severity is treated as ERROR (blocks); only WARNING is non-blocking.
  private static final String SEVERITY_ERROR = "ERROR";
  private static final String SEVERITY_WARNING = "WARNING";

  @Inject ExecutionHelper executionHelper;
  @Inject PipelineExecutor pipelineExecutor;
  @Inject PmsFeatureFlagService pmsFeatureFlagService;
  @Inject FilterCreatorMergeService filterCreatorMergeService;
  @Inject PipelineGovernanceService pipelineGovernanceService;
  @Inject PipelineRbacHelper pipelineRbacHelper;
  @Inject PrincipalInfoHelper principalInfoHelper;
  @Inject MetricService metricService;
  @Inject SemanticValidator semanticValidator;
  @Inject PMSPipelineServiceHelper pmsPipelineServiceHelper;

  /**
   * Status enum for dry run execution.
   * SUCCESS: Dry run completed without system errors (may have validation errors).
   * ERROR: System error or exception occurred during dry run execution.
   */
  public enum DryRunStatus { SUCCESS, ERROR }

  /**
   * Wrapper class to track dry run execution status for metrics.
   * Defaults to ERROR as a fail-safe - status must be explicitly set to SUCCESS.
   */
  @Getter
  @Setter
  public static class DryRunOperationStatus {
    private DryRunStatus status = DryRunStatus.ERROR;
  }

  public DryRunPipelineResponseBody startDryRun(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, ScopeInfo scopeInfo, DryRunPipelineRequestBody body,
      boolean isParentIdQueryingEnabled) {
    DryRunOperationStatus operationStatus = new DryRunOperationStatus();

    try {
      log.info("Starting dry run for pipeline: account={}, org={}, project={}, pipeline={}, branch={}",
          accountIdentifier, orgIdentifier, projectIdentifier, body.getPipelineIdentifier(), body.getBranch());

      DryRunPipelineResponseBody missingFieldsResponse = validateRequiredFields(body);
      if (missingFieldsResponse != null) {
        operationStatus.setStatus(DryRunStatus.SUCCESS);
        log.warn("Dry run request missing required field(s) for pipeline: {}", body.getPipelineIdentifier());
        return missingFieldsResponse;
      }

      DryRunPipelineResponseBody responseBody;
      boolean hadSystemError = false;

      try {
        responseBody = executeDryRunValidation(
            accountIdentifier, orgIdentifier, projectIdentifier, scopeInfo, body, isParentIdQueryingEnabled);
      } catch (FilterCreatorException exception) {
        log.warn(
            "Filter creator error during dry run for pipeline: account={}, org={}, project={}, pipeline={}, error={}",
            accountIdentifier, orgIdentifier, projectIdentifier, body.getPipelineIdentifier(), exception.getMessage(),
            exception);
        responseBody = buildErrorResponse(body.getPipelineIdentifier(), VALIDATION_TYPE_REFERRED_ENTITIES,
            ENTITY_TYPE_PIPELINE, "Failed to resolve pipeline references: " + exception.getMessage(),
            "Ensure all templates, connectors, and other referenced entities are valid and accessible");
        hadSystemError = Boolean.TRUE; // System error, not a validation warning
      } catch (IOException exception) {
        log.error("IO error during dry run for pipeline: account={}, org={}, project={}, pipeline={}, error={}",
            accountIdentifier, orgIdentifier, projectIdentifier, body.getPipelineIdentifier(), exception.getMessage(),
            exception);
        responseBody = buildErrorResponse(body.getPipelineIdentifier(), VALIDATION_TYPE_SYSTEM, null,
            "Failed to process pipeline references: " + exception.getMessage(),
            "Please verify that all referenced entities are accessible. Contact support if this issue persists.");
        hadSystemError = Boolean.TRUE; // System error, not a validation warning
      } catch (Exception exception) {
        log.error(
            "Unexpected error occurred while performing dry run: account={}, org={}, project={}, pipeline={}, error={}",
            accountIdentifier, orgIdentifier, projectIdentifier, body.getPipelineIdentifier(), exception.getMessage(),
            exception);
        responseBody = buildErrorResponse(body.getPipelineIdentifier(), VALIDATION_TYPE_SYSTEM, null,
            "Unexpected error during dry run: " + exception.getMessage(),
            "Please contact support if this issue persists. Check logs for more details.");
        hadSystemError = Boolean.TRUE; // System error, not a validation warning
      }

      // Determine status based on validation results
      if (hadSystemError) {
        operationStatus.setStatus(DryRunStatus.ERROR);
        log.error("Dry run failed with system error for pipeline: {}", body.getPipelineIdentifier());
      } else {
        operationStatus.setStatus(DryRunStatus.SUCCESS);
        if (responseBody.isIsValid()) {
          log.info("Dry run completed successfully for pipeline: {}", body.getPipelineIdentifier());
        } else {
          log.info("Dry run completed successfully with {} validation errors for pipeline: {}",
              responseBody.getValidation() != null ? responseBody.getValidation().size() : 0,
              body.getPipelineIdentifier());
        }
      }

      return responseBody;
    } catch (Exception exception) {
      log.error("Unexpected error occurred while performing dry run for pipeline {}: {}", body.getPipelineIdentifier(),
          exception.getMessage(), exception);

      operationStatus.setStatus(DryRunStatus.ERROR);

      DryRunPipelineResponseBody errorResponse = new DryRunPipelineResponseBody();
      errorResponse.setIsValid(false);

      DryRunPipelineValidationResult validationResult = new DryRunPipelineValidationResult();
      validationResult.setValidationType(VALIDATION_TYPE_SYSTEM);
      validationResult.setEntityIdentifier(
          body.getPipelineIdentifier() != null ? body.getPipelineIdentifier() : "UNKNOWN");
      validationResult.setErrorMessage("Unexpected error during dry run: " + exception.getMessage());
      validationResult.setHint("Please contact support if this issue persists. Check logs for more details.");

      errorResponse.setValidation(List.of(validationResult));
      return errorResponse;
    } finally {
      recordDryRunOutcome(accountIdentifier, orgIdentifier, projectIdentifier, operationStatus.getStatus());
    }
  }

  private DryRunPipelineResponseBody executeDryRunValidation(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, ScopeInfo scopeInfo, DryRunPipelineRequestBody body, boolean isParentIdQueryingEnabled)
      throws IOException, FilterCreatorException {
    List<DryRunPipelineValidationResult> validationResults = new ArrayList<>();

    // Step 1: Get pipeline entity
    PipelineEntity pipelineEntity = getPipelineEntity(
        accountIdentifier, orgIdentifier, projectIdentifier, scopeInfo, body, isParentIdQueryingEnabled);
    log.info("Successfully fetched pipeline entity for dry run: pipeline={}", pipelineEntity.getIdentifier());

    // Step 2: Get referred entities
    List<EntityDetailProtoDTO> referredEntities = getFilters(scopeInfo, pipelineEntity, body.getBranch());
    if (isNotEmpty(referredEntities)) {
      log.info("Found {} referred entities for pipeline: {}", referredEntities.size(), pipelineEntity.getIdentifier());
    }

    // Step 3: Plan creation
    ExecutionPlan executionPlan = performPlanCreationForDryRun(accountIdentifier, orgIdentifier, projectIdentifier,
        scopeInfo, body, isParentIdQueryingEnabled, pipelineEntity, validationResults);
    if (executionPlan != null) {
      log.info("Successfully created execution plan for dry run: pipeline={}", pipelineEntity.getIdentifier());
    } else {
      log.warn("Plan creation failed or returned null for pipeline: {}", pipelineEntity.getIdentifier());
    }

    // Step 3.5: Semantic validation (FF-gated, fail-open). Reads the resolved YAML and referred entities;
    // never fails the dry run.
    runSemanticValidation(accountIdentifier, orgIdentifier, projectIdentifier, executionPlan, referredEntities,
        validationResults, pipelineEntity);

    // Step 4: RBAC checks
    performRBACChecks(accountIdentifier, referredEntities, validationResults);
    log.info("Completed RBAC checks for dry run: pipeline={}", pipelineEntity.getIdentifier());

    // Step 5: Policy checks
    performPolicyChecks(pipelineEntity, executionPlan, body.getBranch(), validationResults);
    log.info("Completed policy checks for dry run: pipeline={}", pipelineEntity.getIdentifier());

    // Build and return response
    return buildValidationResponse(
        pipelineEntity.getIdentifier(), accountIdentifier, orgIdentifier, projectIdentifier, validationResults);
  }

  @VisibleForTesting
  void runSemanticValidation(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      ExecutionPlan executionPlan, List<EntityDetailProtoDTO> referredEntities,
      List<DryRunPipelineValidationResult> validationResults, PipelineEntity pipelineEntity) {
    if (executionPlan == null) {
      return;
    }
    if (!pmsFeatureFlagService.isEnabled(accountIdentifier, FeatureName.PIPE_DRY_RUN_SEMANTIC_VALIDATION)) {
      return;
    }
    try {
      String resolvedYaml = extractResolvedYaml(executionPlan);
      if (resolvedYaml == null) {
        return;
      }
      validationResults.addAll(semanticValidator.validate(resolvedYaml, referredEntities, accountIdentifier,
          orgIdentifier, projectIdentifier, pipelineEntity.getHarnessVersion()));
    } catch (Exception e) {
      log.warn("Semantic validation failed; skipping (dry run continues)", e);
    }
  }

  private String extractResolvedYaml(ExecutionPlan executionPlan) {
    if (executionPlan != null && executionPlan.getPlanExecutionMetadataWithContext() != null) {
      return executionPlan.getPlanExecutionMetadataWithContext().getPipelineYamlWithTemplateRef();
    }
    return null;
  }

  private DryRunPipelineResponseBody buildValidationResponse(String pipelineIdentifier, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, List<DryRunPipelineValidationResult> validationResults) {
    DryRunPipelineResponseBody responseBody = new DryRunPipelineResponseBody();

    // A result blocks (flips isValid to false) unless it is explicitly a WARNING. A missing severity is treated as
    // ERROR to preserve legacy behavior for non-SEMANTIC results.
    boolean anyBlocking = validationResults.stream().anyMatch(r -> !SEVERITY_WARNING.equals(r.getSeverity()));
    if (isEmpty(validationResults) || !anyBlocking) {
      responseBody.setIsValid(Boolean.TRUE);
      if (isNotEmpty(validationResults)) {
        responseBody.setValidation(validationResults);
      }
      log.info("Dry run validation successful: pipeline={}, account={}, org={}, project={}", pipelineIdentifier,
          accountIdentifier, orgIdentifier, projectIdentifier);
    } else {
      responseBody.setIsValid(Boolean.FALSE);
      responseBody.setValidation(validationResults);
      log.warn("Dry run validation failed with {} errors: pipeline={}, account={}, org={}, project={}",
          validationResults.size(), pipelineIdentifier, accountIdentifier, orgIdentifier, projectIdentifier);
    }

    return responseBody;
  }

  @VisibleForTesting
  DryRunPipelineResponseBody buildValidationResponseForTest(String pipelineIdentifier, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, List<DryRunPipelineValidationResult> validationResults) {
    return buildValidationResponse(
        pipelineIdentifier, accountIdentifier, orgIdentifier, projectIdentifier, validationResults);
  }

  @VisibleForTesting
  DryRunPipelineResponseBody validateRequiredFields(DryRunPipelineRequestBody body) {
    List<String> missingFields = new ArrayList<>();
    if (isEmpty(body.getBranch())) {
      missingFields.add("branch");
    }
    if (isEmpty(body.getPipelineIdentifier())) {
      missingFields.add("pipeline_identifier");
    }
    if (isEmpty(body.getPipelineYaml())) {
      missingFields.add("pipeline_yaml");
    }

    if (isEmpty(missingFields)) {
      return null;
    }

    String errorMessage = "Missing required field(s): " + String.join(", ", missingFields);
    String hint = "Provide branch, pipeline_identifier and pipeline_yaml in the request body. "
        + "Note property names use snake_case.";
    return buildErrorResponse(
        body.getPipelineIdentifier(), VALIDATION_TYPE_SCHEMA, ENTITY_TYPE_PIPELINE, errorMessage, hint);
  }

  private DryRunPipelineResponseBody buildErrorResponse(
      String pipelineIdentifier, String validationType, String entityType, String errorMessage, String hint) {
    DryRunPipelineResponseBody errorResponse = new DryRunPipelineResponseBody();
    errorResponse.setIsValid(false);

    DryRunPipelineValidationResult validationResult =
        createValidationResult(validationType, entityType, pipelineIdentifier, errorMessage, hint);

    errorResponse.setValidation(List.of(validationResult));
    return errorResponse;
  }

  private DryRunPipelineValidationResult createValidationResult(
      String validationType, String entityType, String entityIdentifier, String errorMessage, String hint) {
    DryRunPipelineValidationResult validationResult = new DryRunPipelineValidationResult();
    validationResult.setValidationType(validationType);
    if (entityType != null) {
      validationResult.setEntityType(entityType);
    }
    validationResult.setEntityIdentifier(entityIdentifier != null ? entityIdentifier : "UNKNOWN");
    validationResult.setErrorMessage(errorMessage);
    validationResult.setHint(hint);
    return validationResult;
  }

  private ExecutionPlan performPlanCreationForDryRun(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, ScopeInfo scopeInfo, DryRunPipelineRequestBody body, boolean isParentIdQueryingEnabled,
      PipelineEntity pipelineEntity, List<DryRunPipelineValidationResult> dryRunPipelineValidationResults) {
    try {
      String inputSetPipelineYaml = PipelineInputsUtils.getInputsForPipeline(null, body.getInputsetMap());
      List<String> inputSetIdentifiers = isEmpty(body.getInputsetRef()) ? null : List.of(body.getInputsetRef());
      return pipelineExecutor.dryRunPipeline(accountIdentifier, orgIdentifier, projectIdentifier, null,
          inputSetPipelineYaml, false, null, scopeInfo, inputSetIdentifiers, false, false, false, pipelineEntity,
          isParentIdQueryingEnabled);
    } catch (InvalidYamlException e) {
      log.warn(
          "Invalid YAML during plan creation for pipeline {}: {}", pipelineEntity.getIdentifier(), e.getMessage(), e);
      DryRunPipelineValidationResult validationResult =
          createValidationResult(VALIDATION_TYPE_SCHEMA, ENTITY_TYPE_PIPELINE, pipelineEntity.getIdentifier(),
              "Invalid YAML: " + e.getMessage(), "Check your pipeline YAML syntax and ensure all fields are valid");
      dryRunPipelineValidationResults.add(validationResult);
      return null;
    } catch (InvalidRequestException e) {
      log.warn("Invalid request during plan creation for pipeline {}: {}", pipelineEntity.getIdentifier(),
          e.getMessage(), e);
      DryRunPipelineValidationResult validationResult = createValidationResult("REFERENCES", ENTITY_TYPE_PIPELINE,
          pipelineEntity.getIdentifier(), "Invalid pipeline configuration: " + e.getMessage(),
          "Verify that all pipeline parameters are correct and resources are accessible");
      dryRunPipelineValidationResults.add(validationResult);
      return null;
    } catch (Exception e) {
      log.error("Unexpected error during dry run plan creation for pipeline {}: {}", pipelineEntity.getIdentifier(),
          e.getMessage(), e);
      DryRunPipelineValidationResult validationResult = createValidationResult(VALIDATION_TYPE_SCHEMA,
          ENTITY_TYPE_PIPELINE, pipelineEntity.getIdentifier(), "Failed to create execution plan: " + e.getMessage(),
          "Ensure your pipeline is properly configured. Contact support if this issue persists");
      dryRunPipelineValidationResults.add(validationResult);
      return null;
    }
  }

  private PipelineEntity getPipelineEntity(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      ScopeInfo scopeInfo, DryRunPipelineRequestBody body, boolean isParentIdQueryingEnabled) {
    PipelineEntity pipelineEntity = executionHelper.fetchPipelineEntity(
        accountIdentifier, orgIdentifier, projectIdentifier, body.getPipelineIdentifier(), scopeInfo);
    // Only override pipeline YAML if provided in request body
    // If not provided, use the existing YAML from database
    if (body.getPipelineYaml() != null) {
      pipelineEntity.setData(body.getPipelineYaml());
    }
    return pipelineEntity;
  }

  @VisibleForTesting
  void performRBACChecks(String accountIdentifier, List<EntityDetailProtoDTO> entityDetailProtoDTOList,
      List<DryRunPipelineValidationResult> dryRunPipelineValidationResults) {
    // Construct Ambiance with principal information from security context
    try {
      Ambiance ambiance =
          Ambiance.newBuilder()
              .putSetupAbstractions("accountId", accountIdentifier)
              .setMetadata(ExecutionMetadata.newBuilder()
                               .setPrincipalInfo(principalInfoHelper.getPrincipalInfoFromSecurityContext())
                               .build())
              .build();

      pipelineRbacHelper.checkRuntimePermissions(ambiance, new HashSet<>(entityDetailProtoDTOList));
    } catch (NGAccessDeniedException e) {
      log.warn("RBAC check failed for dry run: {}", e.getMessage(), e);
      // Extract failed permission checks and add to validation results
      if (e.getFailedPermissionChecks() != null) {
        for (PermissionCheckDTO failedCheck : e.getFailedPermissionChecks()) {
          DryRunPipelineValidationResult validationResult = createValidationResult(VALIDATION_TYPE_PERMISSIONS,
              failedCheck.getResourceType(), failedCheck.getResourceIdentifier(),
              String.format("Missing permission '%s' on %s '%s'", failedCheck.getPermission(),
                  failedCheck.getResourceType(), failedCheck.getResourceIdentifier()),
              String.format("Ensure you have the '%s' permission on the %s resource", failedCheck.getPermission(),
                  failedCheck.getResourceType()));

          dryRunPipelineValidationResults.add(validationResult);
        }
      }
    }
  }

  /**
   * V1 filter creation routes stages by their {@code type} discriminator, but a V1 pipeline's stages
   * are type-less in the YAML. The plan path injects a synthetic type first (ExecutionHelper); the
   * filter path does not, so a raw V1 pipeline fails filter creation with
   * "yaml paths could not be parsed: pipeline/stages/[0]". This mirrors that injection for dry-run
   * only (a non-mutating copy) so referred-entity extraction can run. V0 / blank YAML pass through.
   */
  @VisibleForTesting
  PipelineEntity preProcessV1YamlForFilters(PipelineEntity pipelineEntity) {
    if (!HarnessYamlVersion.isV1(pipelineEntity.getHarnessVersion())) {
      return pipelineEntity;
    }
    String yaml = pipelineEntity.getYaml();
    if (yaml == null || yaml.isBlank()) {
      return pipelineEntity;
    }
    try {
      return pipelineEntity.withYaml(pmsPipelineServiceHelper.injectTypeField(yaml));
    } catch (Exception e) {
      // Best-effort: if type injection fails, continue with the original entity. Filter creation may
      // still fail on the existing referred-entities error path, exactly as it does today.
      log.warn("V1 type-field injection for dry-run filters failed; using original YAML", e);
      return pipelineEntity;
    }
  }

  private List<EntityDetailProtoDTO> getFilters(ScopeInfo scopeInfo, PipelineEntity pipelineEntity, String branch)
      throws IOException, FilterCreatorException {
    try {
      PipelineEntity entityForFilters = preProcessV1YamlForFilters(pipelineEntity);
      FilterCreationParams filterCreationParams = FilterCreationParams.builder()
                                                      .pipelineEntity(entityForFilters)
                                                      .scopeInfo(scopeInfo)
                                                      .filterCreationGitMetadata(FilterCreationGitMetadata.builder()
                                                                                     .branch(branch)
                                                                                     .repo(entityForFilters.getRepo())
                                                                                     .isGitDefaultBranch(Boolean.TRUE)
                                                                                     .build())
                                                      .isParentIdQueryingEnabled(true)
                                                      .build();
      return filterCreatorMergeService.getReferredEntities(filterCreationParams);
    } catch (FilterCreatorException e) {
      log.error(
          "Failed to get referred entities for pipeline {}: {}", pipelineEntity.getIdentifier(), e.getMessage(), e);
      throw e;
    }
  }

  @VisibleForTesting
  void performPolicyChecks(PipelineEntity pipelineEntity, ExecutionPlan executionPlan, String branch,
      List<DryRunPipelineValidationResult> dryRunPipelineValidationResults) {
    // Extract resolved YAML from execution plan
    String resolvedYaml = extractResolvedYaml(executionPlan);
    if (resolvedYaml == null) {
      log.warn("Resolved YAML is null for pipeline {}, skipping governance checks", pipelineEntity.getIdentifier());
      DryRunPipelineValidationResult validationResult = createValidationResult(VALIDATION_TYPE_POLICY, "PIPELINE",
          pipelineEntity.getIdentifier(), "Policy checks skipped: Resolved pipeline YAML is not available",
          "Ensure the pipeline has valid YAML and all templates are properly resolved.");
      dryRunPipelineValidationResults.add(validationResult);
      return;
    }

    // Evaluate OnSave policies
    evaluatePolicies(pipelineEntity, branch, resolvedYaml, OpaConstants.OPA_EVALUATION_ACTION_SAVE,
        OpaConstants.OPA_EVALUATION_ACTION_SAVE, dryRunPipelineValidationResults);

    // Evaluate OnRun policies
    evaluatePolicies(pipelineEntity, branch, resolvedYaml, OpaConstants.OPA_EVALUATION_ACTION_PIPELINE_RUN,
        OpaConstants.OPA_EVALUATION_ACTION_PIPELINE_RUN, dryRunPipelineValidationResults);
  }

  private void evaluatePolicies(PipelineEntity pipelineEntity, String branch, String resolvedYaml, String action,
      String actionLabel, List<DryRunPipelineValidationResult> dryRunPipelineValidationResults) {
    try {
      GovernanceMetadata governanceMetadata = pipelineGovernanceService.validateGovernanceRules(
          pipelineEntity.getAccountId(), pipelineEntity.getOrgIdentifier(), pipelineEntity.getProjectIdentifier(),
          branch, pipelineEntity, resolvedYaml, action);

      // Check if policy evaluation failed
      if (governanceMetadata != null && governanceMetadata.getDeny()) {
        log.warn("Pipeline {} failed {} governance policy evaluation", pipelineEntity.getIdentifier(), actionLabel);
        // Extract policy failures from GovernanceMetadata
        for (PolicySetMetadata policySetMetadata : governanceMetadata.getDetailsList()) {
          if (policySetMetadata.getDeny()) {
            // Build error message from policy set metadata
            StringBuilder errorMessage = new StringBuilder();
            errorMessage.append("Pipeline violates ")
                .append(actionLabel)
                .append(" policies in Policy Set '")
                .append(policySetMetadata.getPolicySetName())
                .append("' (")
                .append(policySetMetadata.getIdentifier())
                .append(')');

            if (policySetMetadata.getPolicyMetadataCount() > 0) {
              errorMessage.append(": ");
              errorMessage.append(policySetMetadata.getPolicyMetadataList()
                                      .stream()
                                      .filter(pm -> isNotEmpty(pm.getDenyMessagesList()))
                                      .flatMap(pm -> pm.getDenyMessagesList().stream())
                                      .collect(java.util.stream.Collectors.joining("; ")));
            }

            DryRunPipelineValidationResult validationResult = createValidationResult(VALIDATION_TYPE_POLICY,
                ENTITY_TYPE_PIPELINE, pipelineEntity.getIdentifier(), errorMessage.toString(),
                String.format("Review the %s policy configuration and ensure the pipeline meets policy requirements",
                    actionLabel));

            dryRunPipelineValidationResults.add(validationResult);
          }
        }
      }
    } catch (InvalidRequestException e) {
      log.warn("Invalid request during {} policy evaluation for pipeline {}: {}", actionLabel,
          pipelineEntity.getIdentifier(), e.getMessage(), e);

      DryRunPipelineValidationResult validationResult =
          createValidationResult(VALIDATION_TYPE_POLICY, ENTITY_TYPE_PIPELINE, pipelineEntity.getIdentifier(),
              String.format("Invalid %s policy configuration: %s", actionLabel, e.getMessage()),
              String.format("Verify that %s policy sets are properly configured and accessible", actionLabel));

      dryRunPipelineValidationResults.add(validationResult);
    }
  }

  /**
   * Records dry run outcome metric with status label for tracking success/error rates.
   */
  private void recordDryRunOutcome(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, DryRunStatus status) {
    try {
      Map<String, String> metricLabels = new HashMap<>();
      metricLabels.put(LABEL_ACCOUNT_ID, accountIdentifier);
      metricLabels.put(LABEL_ORG_ID, orgIdentifier != null ? orgIdentifier : "");
      metricLabels.put(LABEL_PROJECT_ID, projectIdentifier != null ? projectIdentifier : "");
      metricLabels.put(LABEL_STATUS, status.name());

      try (ThreadAutoLogContext ignore = new ThreadAutoLogContext(metricLabels)) {
        metricService.recordMetric(METRIC_DRY_RUN_OUTCOME, 1);
      }
      log.debug("Recorded dry run outcome: status={}, account={}, org={}, project={}", status, accountIdentifier,
          orgIdentifier, projectIdentifier);
    } catch (Exception e) {
      log.warn("Failed to record dry run outcome for account {}", accountIdentifier, e);
    }
  }
}
