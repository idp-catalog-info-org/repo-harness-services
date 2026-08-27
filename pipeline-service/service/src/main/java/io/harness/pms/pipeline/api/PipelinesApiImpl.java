/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.api;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.Scope;
import io.harness.beans.ScopeInfo;
import io.harness.engine.governance.PolicyEvaluationFailureException;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ngexception.beans.yamlschema.YamlSchemaErrorWrapperDTO;
import io.harness.git.model.ChangeType;
import io.harness.gitaware.dto.InlineHCUpdateContextRequest;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitx.CrudAction;
import io.harness.gitx.InlineHCHelper;
import io.harness.governance.GovernanceMetadata;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.pms.opa.gitx.pipeline.PipelineOpaStatusHandler;
import io.harness.pms.pipeline.MoveConfigOperationDTO;
import io.harness.pms.pipeline.PMSPipelineSummaryResponseDTO;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.PipelineEntity.PipelineEntityKeys;
import io.harness.pms.pipeline.PipelineEntityUtils;
import io.harness.pms.pipeline.PipelineImportRequestDTO;
import io.harness.pms.pipeline.PipelineMetadataV2;
import io.harness.pms.pipeline.gitsync.PMSUpdateGitDetailsParams;
import io.harness.pms.pipeline.mappers.GitXCacheMapper;
import io.harness.pms.pipeline.mappers.dto.PMSPipelineDtoMapper;
import io.harness.pms.pipeline.service.enforcement.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.service.helper.PMSPipelineServiceHelper;
import io.harness.pms.pipeline.service.helper.PipelinePublicAccessHelper;
import io.harness.pms.pipeline.service.intfc.PMSPipelineService;
import io.harness.pms.pipeline.service.intfc.PipelineCRUDResult;
import io.harness.pms.pipeline.service.response.PipelineCRUDErrorResponse;
import io.harness.pms.pipeline.service.response.PipelineMetadataService;
import io.harness.pms.pipeline.validation.async.beans.Action;
import io.harness.pms.pipeline.validation.async.beans.PipelineValidationEvent;
import io.harness.pms.pipeline.validation.async.service.PipelineAsyncValidationService;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.pms.rbac.PipelineSplitPermissionsHelper;
import io.harness.pms.steps.BasicStepInfo;
import io.harness.pms.steps.StepNotificationSelectorHelper;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.NGYamlHelper;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.spec.server.pipeline.v1.PipelinesApi;
import io.harness.spec.server.pipeline.v1.model.GitMetadataUpdateRequestBody;
import io.harness.spec.server.pipeline.v1.model.GitMetadataUpdateResponseBody;
import io.harness.spec.server.pipeline.v1.model.PipelineCreateRequestBody;
import io.harness.spec.server.pipeline.v1.model.PipelineCreateResponseBody;
import io.harness.spec.server.pipeline.v1.model.PipelineGetResponseBody;
import io.harness.spec.server.pipeline.v1.model.PipelineImportRequestBody;
import io.harness.spec.server.pipeline.v1.model.PipelineInputSchemaDetailsResponseBody;
import io.harness.spec.server.pipeline.v1.model.PipelineMoveConfigRequestBody;
import io.harness.spec.server.pipeline.v1.model.PipelineMoveConfigResponseBody;
import io.harness.spec.server.pipeline.v1.model.PipelinePatchRequestBody;
import io.harness.spec.server.pipeline.v1.model.PipelineSaveResponseBody;
import io.harness.spec.server.pipeline.v1.model.PipelineUpdateRequestBody;
import io.harness.spec.server.pipeline.v1.model.PipelineValidationResponseBody;
import io.harness.spec.server.pipeline.v1.model.PipelineValidationUUIDResponseBody;
import io.harness.spec.server.pipeline.v1.model.ResolvedPipelineWithAllTemplatesRuntimeInputsResponseBody;
import io.harness.spec.server.pipeline.v1.model.StepFqnInfo;
import io.harness.spec.server.pipeline.v1.model.StepFqnRequestBody;
import io.harness.utils.ApiUtils;
import io.harness.utils.PageUtils;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.ScopeResolutionHelper;
import io.harness.yaml.core.variables.v1.NGVariableConstantsV1;
import io.harness.yaml.schema.inputs.beans.YamlInputDetails;
import io.harness.yaml.validator.InvalidYamlException;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@PipelineServiceAuth
@Slf4j
public class PipelinesApiImpl implements PipelinesApi {
  private static final Set<String> restrictedTypesAtRuntime =
      Set.of(NGVariableConstantsV1.STEP_TYPE, NGVariableConstantsV1.STAGE_TYPE);
  private static final Set<String> stringAllowedValueInputTypes =
      Set.of(NGVariableConstantsV1.STRING_TYPE, NGVariableConstantsV1.SECRET_TYPE);
  private final PMSPipelineService pmsPipelineService;
  private final PMSPipelineServiceHelper pipelineServiceHelper;
  private final PMSPipelineTemplateHelper pipelineTemplateHelper;
  private final PipelineMetadataService pipelineMetadataService;
  private final PipelineAsyncValidationService pipelineAsyncValidationService;
  private final PmsFeatureFlagService pmsFeatureFlagService;
  private final ScopeResolutionHelper scopeResolutionHelper;
  private final AccessControlClient accessControlClient;
  private final PipelineSplitPermissionsHelper pipelineSplitPermissionsHelper;
  private final PipelinePublicAccessHelper pipelinePublicAccessHelper;
  private final PipelineOpaStatusHandler pipelineOpaStatusHandler;

  /**
   * Validates pipeline inputs based on their type definitions
   * @param pipelineYaml The pipeline YAML to validate
   */
  private void validatePipelineInputsBasedOnType(String pipelineYaml, String pipelineYamlVersion) {
    if (!HarnessYamlVersion.isV1(pipelineYamlVersion)) {
      return;
    }
    try {
      JsonNode pipelineJsonNode = YamlUtils.readAsJsonNode(pipelineYaml);
      YamlNode pipelineNode = new YamlNode(pipelineJsonNode);

      YamlNode inputsYamlNode = pipelineNode.gotoPath("pipeline/inputs");
      if (inputsYamlNode == null) {
        inputsYamlNode = pipelineNode.gotoPath("inputs");
        if (inputsYamlNode == null) {
          return;
        }
      }

      for (YamlField field : inputsYamlNode.fields()) {
        validateSingleInputBasedOnType(inputsYamlNode, field.getName());
      }
    } catch (Exception e) {
      if (YamlUtils.isYamlSizeLimitExceeded(e)) {
        throw new InvalidRequestException(PipelineEntityUtils.PIPELINE_YAML_SIZE_LIMIT_EXCEEDED_MESSAGE, e);
      }
      throw new InvalidRequestException("Failed to validate pipeline inputs: " + e.getMessage(), e);
    }
  }

  /**
   * Validates a single input field based on its type
   * @param inputsYamlNode The inputs YAML node
   * @param key The input field key
   */
  private void validateSingleInputBasedOnType(YamlNode inputsYamlNode, String key) {
    YamlField inputField = inputsYamlNode.getField(key);
    if (inputField == null || inputField.getNode().getField(YAMLFieldNameConstants.TYPE) == null) {
      return; // No type definition, skip validation
    }

    String type = inputField.getNode().getField(YAMLFieldNameConstants.TYPE).getNode().getCurrJsonNode().asText();

    if (restrictedTypesAtRuntime.contains(type)) {
      throw new InvalidRequestException(String.format(
          "Input type step and stage can not be provided at runtime, Please provide input : [%s] in pipeline itself",
          key));
    }
  }

  @Override
  @Timed
  @ResponseMetered
  public Response createPipeline(PipelineCreateRequestBody requestBody, @OrgIdentifier String org,
      @ProjectIdentifier String project, @AccountIdentifier String account, Boolean isPublic, Boolean enableDAG) {
    boolean isParentIdQueryingEnabled = true;
    ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(account, org, project);
    pipelineSplitPermissionsHelper.checkForPipelineRBACSplitAccessPermissions(account, org, project, null,
        pmsFeatureFlagService.isEnabled(account, FeatureName.PIPE_CREATE_EDIT_PERMISSION_SPLIT),
        PipelineRbacPermissions.PIPELINE_EDIT, Arrays.asList(PipelineRbacPermissions.PIPELINE_CREATE));
    if (requestBody == null) {
      throw new InvalidRequestException("Pipeline Create request body must not be null.");
    }
    String pipelineVersion = requestBody.getVersion();
    if (isEmpty(pipelineVersion)) {
      pipelineVersion = NGYamlHelper.detectVersionFromYamlStructure(requestBody.getPipelineYaml());
    }
    GitAwareContextHelper.populateGitDetails(PipelinesApiUtils.populateGitCreateDetails(requestBody.getGitDetails()));
    PipelineEntity pipelineEntity = PMSPipelineDtoMapper.validateAndConvertToPipelineEntity(
        PipelinesApiUtils.mapCreateToRequestInfoDTO(requestBody), account, org, project, null, pipelineVersion, false,
        scopeInfo, isParentIdQueryingEnabled, requestBody.isAllowDynamicExecutions());
    pipelineEntity = pipelineEntity.withEnableDAG(enableDAG);
    InlineHCHelper.checkAndUpdateContextForInlineHC(pipelineEntity, CrudAction.CREATE,
        InlineHCUpdateContextRequest.builder()
            .scope(Scope.of(account, org, project))
            .entityIdentifier(pipelineEntity.getIdentifier())
            .build(),
        pmsFeatureFlagService::isEnabled);
    log.info(String.format("Creating a Pipeline with identifier %s in project %s, org %s, account %s",
        pipelineEntity.getIdentifier(), project, org, account));
    validatePipelineInputsBasedOnType(pipelineEntity.getYaml(), pipelineVersion);
    PipelineCRUDResult pipelineCRUDResult =
        pmsPipelineService.validateAndCreatePipeline(pipelineEntity, false, scopeInfo, isParentIdQueryingEnabled);
    GovernanceMetadata governanceMetadata = pipelineCRUDResult.getGovernanceMetadata();
    PipelineCreateResponseBody responseBody = new PipelineCreateResponseBody();
    if (governanceMetadata != null) {
      responseBody.setGovernanceMetadata(PipelineApiOpaUtils.buildGovernanceMetadataFromProto(governanceMetadata));
      if (governanceMetadata.getDeny()) {
        return Response.status(Response.Status.OK).entity(responseBody).build();
      }
    }

    PipelineEntity createdEntity = pipelineCRUDResult.getPipelineEntity();
    responseBody.setIdentifier(createdEntity.getIdentifier());
    responseBody.setPublicAccessResponse(PipelinesApiUtils.toPublicAccessResponse(
        pipelinePublicAccessHelper.markPipelinePublic(account, org, project, createdEntity.getIdentifier(), isPublic)));
    return Response.status(201).entity(responseBody).build();
  }

  @Override
  @Timed
  @ResponseMetered
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_DELETE)
  public Response deletePipeline(@OrgIdentifier String org, @ProjectIdentifier String project,
      @ResourceIdentifier String pipeline, @AccountIdentifier String account) {
    boolean isParentIdQueryingEnabled = true;
    ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(account, org, project);
    log.info(String.format(
        "Deleting Pipeline with identifier %s in project %s, org %s, account %s", pipeline, project, org, account));
    pmsPipelineService.delete(account, org, project, pipeline, null, scopeInfo, isParentIdQueryingEnabled);
    return Response.status(204).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public Response getInputsSchemaDetails(@OrgIdentifier String org, @ProjectIdentifier String project,
      @ResourceIdentifier String pipeline, @AccountIdentifier String harnessAccount, String branchName,
      String connectorRef, Boolean isHarnessCodeRepo, String repoName) {
    GitAwareContextHelper.populateGitDetails(
        GitEntityInfo.builder().branch(branchName).connectorRef(connectorRef).repoName(repoName).build());
    boolean isParentIdQueryingEnabled = true;
    ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(harnessAccount, org, project);
    List<YamlInputDetails> yamlInputDetails = pmsPipelineService.getInputSchemaDetails(
        harnessAccount, org, project, pipeline, scopeInfo, isParentIdQueryingEnabled);
    PipelineInputSchemaDetailsResponseBody responseBody =
        PipelinesApiUtils.getPipelineInputSchemaDetailsResponseBody(yamlInputDetails);
    return Response.ok().entity(responseBody).build();
  }

  @Override
  @Timed
  @ResponseMetered
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public Response getPipeline(@OrgIdentifier String org, @ProjectIdentifier String project,
      @ResourceIdentifier String pipeline, @AccountIdentifier String account, String branch, Boolean templatesApplied,
      String connectorRef, String repoName, String loadFromCache, Boolean loadFromFallbackBranch,
      Boolean validateAsync) {
    GitAwareContextHelper.populateGitDetails(
        GitEntityInfo.builder().branch(branch).connectorRef(connectorRef).repoName(repoName).build());
    log.info(String.format(
        "Retrieving Pipeline with identifier %s in project %s, org %s, account %s", pipeline, project, org, account));
    Optional<PipelineEntity> optionalPipelineEntity;
    PipelineGetResponseBody pipelineGetResponseBody = new PipelineGetResponseBody();
    // if validateAsync is true, then this ID wil be of the event started for the async validation process, which can be
    // queried on using another API to get the result of the async validation. If validateAsync is false, then this ID
    // is not needed and will be null
    String validationUUID;
    boolean isParentIdQueryingEnabled = true;
    ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(account, org, project);
    try {
      optionalPipelineEntity = pmsPipelineService.getPipeline(account, org, project, pipeline, false, false,
          Boolean.TRUE.equals(loadFromFallbackBranch), GitXCacheMapper.parseLoadFromCacheHeaderParam(loadFromCache),
          scopeInfo, isParentIdQueryingEnabled);
      if (optionalPipelineEntity.isEmpty()) {
        throw new EntityNotFoundException(
            PipelineCRUDErrorResponse.errorMessageForPipelineNotFound(org, project, pipeline));
      }
      PipelineEntity pipelineEntity = optionalPipelineEntity.get();
      pipelineGetResponseBody =
          PipelinesApiUtils.getGetResponseBody(pipelineEntity, scopeInfo, isParentIdQueryingEnabled);
      pipelineGetResponseBody.setPublicAccessResponse(PipelinesApiUtils.toPublicAccessResponse(
          pipelinePublicAccessHelper.getPublicContextResponse(account, org, project, pipeline)));

      validationUUID = pmsPipelineService.validatePipeline(account, org, project, pipeline,
          Boolean.TRUE.equals(loadFromFallbackBranch), GitXCacheMapper.parseLoadFromCacheHeaderParam(loadFromCache),
          validateAsync, pipelineEntity, scopeInfo, isParentIdQueryingEnabled);
    } catch (PolicyEvaluationFailureException pe) {
      pipelineGetResponseBody.setPipelineYaml(pe.getYaml());
      pipelineGetResponseBody.setGitDetails(
          PipelinesApiUtils.getGitDetails(GitAwareContextHelper.getEntityGitDetailsFromScmGitMetadata()));
      pipelineGetResponseBody.setValid(false);
      // GovMetaData needed here after redoing structure
      return Response.status(200).entity(pipelineGetResponseBody).build();
    } catch (InvalidYamlException e) {
      pipelineGetResponseBody.setPipelineYaml(e.getYaml());
      pipelineGetResponseBody.setGitDetails(
          PipelinesApiUtils.getGitDetails(GitAwareContextHelper.getEntityGitDetailsFromScmGitMetadata()));
      pipelineGetResponseBody.setYamlErrorWrapper(
          PipelinesApiUtils.getListYAMLErrorWrapper((YamlSchemaErrorWrapperDTO) e.getMetadata()));
      pipelineGetResponseBody.setValid(false);
      return Response.status(200).entity(pipelineGetResponseBody).build();
    }
    if (Boolean.TRUE.equals(templatesApplied)) {
      try {
        String templateResolvedPipelineYaml = "";
        TemplateMergeResponseDTO templateMergeResponseDTO = pipelineTemplateHelper.resolveTemplateRefsInPipeline(
            optionalPipelineEntity.get(), scopeInfo, loadFromCache);
        templateResolvedPipelineYaml = templateMergeResponseDTO.getMergedPipelineYaml();
        pipelineGetResponseBody.setTemplateAppliedPipelineYaml(templateResolvedPipelineYaml);
      } catch (Exception e) {
        log.info("Cannot get resolved templates pipeline YAML");
      }
    }
    if (validateAsync) {
      pipelineGetResponseBody.setValidationUuid(validationUUID);
    }
    return Response.ok().entity(pipelineGetResponseBody).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public Response startPipelineValidationEvent(@OrgIdentifier String org, @ProjectIdentifier String project,
      String pipeline, @AccountIdentifier String account, String branch, String connectorRef, String repoName,
      Boolean loadFromCache, Boolean loadFromFallbackBranch) {
    GitAwareContextHelper.populateGitDetails(
        GitEntityInfo.builder().branch(branch).connectorRef(connectorRef).repoName(repoName).build());
    boolean isParentIdQueryingEnabled = true;
    ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(account, org, project);
    Optional<PipelineEntity> pipelineEntity = pmsPipelineService.getPipeline(account, org, project, pipeline, false,
        false, Boolean.TRUE.equals(loadFromFallbackBranch), Boolean.TRUE.equals(loadFromCache), scopeInfo,
        isParentIdQueryingEnabled);
    if (pipelineEntity.isEmpty()) {
      throw new EntityNotFoundException(
          String.format("Pipeline with the given ID: %s does not exist or has been deleted.", pipeline));
    }
    PipelineValidationEvent pipelineValidationEvent = pipelineAsyncValidationService.startEvent(
        pipelineEntity.get(), branch, Action.CRUD, loadFromCache, scopeInfo, isParentIdQueryingEnabled);
    PipelineValidationUUIDResponseBody pipelineValidationUUIDResponseBody =
        PipelinesApiUtils.buildPipelineValidationUUIDResponseBody(pipelineValidationEvent);
    return Response.ok().entity(pipelineValidationUUIDResponseBody).build();
  }

  @Override
  public Response updatePipelineGitMetadata(@OrgIdentifier String org, @ProjectIdentifier String project,
      @ResourceIdentifier String pipeline, @Valid GitMetadataUpdateRequestBody body,
      @AccountIdentifier String harnessAccount) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(harnessAccount, org, project),
        Resource.of("PIPELINE", pipeline), PipelineRbacPermissions.PIPELINE_EDIT);

    boolean isParentIdQueryingEnabled = pipelineServiceHelper.isParentIdQueryingEnabled(harnessAccount);
    ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(harnessAccount, org, project);

    String pipelineAfterUpdate = pmsPipelineService.updateGitMetadata(harnessAccount, org, project, pipeline,
        PMSUpdateGitDetailsParams.builder()
            .connectorRef(body.getConnectorRef())
            .filePath(body.getFilePath())
            .repoName(body.getRepoName())
            .build(),
        scopeInfo, isParentIdQueryingEnabled);

    GitMetadataUpdateResponseBody gitMetadataUpdateResponseBody = new GitMetadataUpdateResponseBody();
    gitMetadataUpdateResponseBody.setEntityIdentifier(pipelineAfterUpdate);
    return Response.ok().entity(gitMetadataUpdateResponseBody).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public Response getPipelineValidateResult(
      @OrgIdentifier String org, @ProjectIdentifier String project, String uuid, @AccountIdentifier String account) {
    Optional<PipelineValidationEvent> eventByUuid = pipelineAsyncValidationService.getEventByUuid(uuid);
    PipelineValidationEvent pipelineValidationEvent = eventByUuid.get();
    PipelineValidationResponseBody pipelineValidationResponseBody =
        PipelinesApiUtils.buildPipelineValidationResponseBody(pipelineValidationEvent);

    try {
      PipelineEntity entity = pipelineValidationEvent.getParams().getPipelineEntity();
      PipelinesApiUtils
          .resolveValidateOpaEnrichment(entity, account, pipelineValidationEvent.getResult().getGovernanceMetadata(),
              pipelineOpaStatusHandler, pipelineValidationEvent.getParams().getCommitId(),
              pipelineValidationEvent.getResult().getOpaEvaluatedAt(),
              pipelineValidationEvent.getResult().getOpaLastValidCommitId())
          .ifPresent(enrichment -> {
            pipelineValidationResponseBody.setOpaOnSaveStatus(
                PipelinesApiUtils.toV1OpaOnSaveStatus(enrichment.getOpaStatus(), enrichment.getCurrentCommitId()));
          });
    } catch (Exception e) {
      log.warn("Failed to enrich v1 validate result with OPA onSave status, continuing without it", e);
    }

    return Response.ok().entity(pipelineValidationResponseBody).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public Response getResolvedPipelineWithAllTemplatesRuntimeInputs(@OrgIdentifier String orgId,
      @ProjectIdentifier String projectId, @ResourceIdentifier String pipelineId, @AccountIdentifier String accountId,
      String branchName, String loadFromCache) {
    boolean isParentIdQueryingEnabled = pipelineServiceHelper.isParentIdQueryingEnabled(accountId);
    if (isNotEmpty(branchName)) {
      pipelineTemplateHelper.setupGitContext(branchName);
    }

    ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(accountId, orgId, projectId);
    Optional<PipelineEntity> optionalPipelineEntity =
        pmsPipelineService.getPipeline(accountId, orgId, projectId, pipelineId, false, false, false,
            GitXCacheMapper.parseLoadFromCacheHeaderParam(loadFromCache), scopeInfo, isParentIdQueryingEnabled);
    if (optionalPipelineEntity.isPresent()
        && HarnessYamlVersion.isV1(optionalPipelineEntity.get().getHarnessVersion())) {
      String pipelineYaml = optionalPipelineEntity.get().getYaml();
      String resolvedPipelineYaml = pipelineTemplateHelper.resolvePipelineWithAllTemplatesRuntimeInputs(
          pipelineYaml, accountId, orgId, projectId, loadFromCache);
      ResolvedPipelineWithAllTemplatesRuntimeInputsResponseBody
          resolvedPipelineWithAllTemplatesRuntimeInputsResponseBody =
              new ResolvedPipelineWithAllTemplatesRuntimeInputsResponseBody();
      optionalPipelineEntity.ifPresent(pipelineEntity
          -> resolvedPipelineWithAllTemplatesRuntimeInputsResponseBody.identifier(pipelineEntity.getIdentifier()));
      resolvedPipelineWithAllTemplatesRuntimeInputsResponseBody.yaml(resolvedPipelineYaml);
      return Response.ok().entity(resolvedPipelineWithAllTemplatesRuntimeInputsResponseBody).build();
    }
    return Response.noContent().build(); // return no content if not a V1 pipeline or pipeline is not present.
  }

  @Override
  public Response importPipelineFromGit(@OrgIdentifier String org, @ProjectIdentifier String project,
      @ResourceIdentifier String pipeline, @Valid PipelineImportRequestBody body,
      @AccountIdentifier String harnessAccount) {
    pipelineSplitPermissionsHelper.checkForPipelineRBACSplitAccessPermissions(harnessAccount, org, project, pipeline,
        pmsFeatureFlagService.isEnabled(harnessAccount, FeatureName.PIPE_CREATE_EDIT_PERMISSION_SPLIT),
        PipelineRbacPermissions.PIPELINE_EDIT, Arrays.asList(PipelineRbacPermissions.PIPELINE_CREATE));
    GitAwareContextHelper.populateGitDetails(PipelinesApiUtils.populateGitImportDetails(body.getGitImportInfo()));
    PipelineImportRequestDTO pipelineImportRequestDTO =
        PipelineImportRequestDTO.builder()
            .pipelineName(body.getPipelineImportRequest().getPipelineName())
            .pipelineDescription(body.getPipelineImportRequest().getPipelineDescription())
            .version(body.getPipelineImportRequest().getVersion())
            .build();
    log.info(String.format("Importing Pipeline with identifier %s in project %s, org %s, account %s", pipeline, project,
        org, harnessAccount));
    boolean isParentIdQueryingEnabled = pipelineServiceHelper.isParentIdQueryingEnabled(harnessAccount);
    ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(harnessAccount, org, project);
    PipelineEntity savedPipelineEntity =
        pmsPipelineService.importPipelineFromRemote(harnessAccount, org, project, pipeline, pipelineImportRequestDTO,
            Boolean.TRUE.equals(body.getGitImportInfo().isIsForceImport()), scopeInfo, isParentIdQueryingEnabled);
    PipelineSaveResponseBody responseBody = new PipelineSaveResponseBody();
    responseBody.setIdentifier(savedPipelineEntity.getIdentifier());
    return Response.ok().entity(responseBody).build();
  }

  @Override
  @Timed
  @ResponseMetered
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public Response listPipelines(@OrgIdentifier String org, @ProjectIdentifier String project,
      @AccountIdentifier String account, Integer page, Integer limit, String searchTerm, String sort, String order,
      String module, String filterId, List<String> pipelineIds, String name, String description, List<String> tags,
      List<String> services, List<String> envs, String deploymentType, String repoName) {
    log.info(String.format("Get List of Pipelines in project %s, org %s, account %s", project, org, account));
    Criteria criteria;
    boolean isParentIdQueryingEnabled = pipelineServiceHelper.isParentIdQueryingEnabled(account);
    ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(account, org, project);
    try {
      criteria = pipelineServiceHelper.formCriteria(account, org, project, filterId,
          PipelinesApiUtils.getFilterProperties(
              pipelineIds, name, description, tags, services, envs, deploymentType, repoName),
          false, module, searchTerm, scopeInfo, isParentIdQueryingEnabled);
    } catch (PatternSyntaxException exception) {
      return Response.ok().build();
    }
    List<String> sortingList = PipelinesApiUtils.getPipelineSorting(sort, order);
    Pageable pageRequest = PageUtils.getPageRequest(
        page, limit, sortingList, Sort.by(Sort.Direction.DESC, PipelineEntityKeys.lastUpdatedAt));
    Page<PipelineEntity> pipelineEntities = pmsPipelineService.list(
        criteria, pageRequest, account, org, project, false, scopeInfo, isParentIdQueryingEnabled);

    List<String> pipelineIdentifiers =
        pipelineEntities.stream().map(PipelineEntity::getIdentifier).collect(Collectors.toList());
    Map<String, PipelineMetadataV2> pipelineMetadataMap = pipelineMetadataService.getMetadataForGivenPipelineIds(
        account, org, project, pipelineIdentifiers, scopeInfo, isParentIdQueryingEnabled);

    Page<PMSPipelineSummaryResponseDTO> pipelines =
        pipelineEntities.map(e -> PMSPipelineDtoMapper.preparePipelineSummaryForListView(e, pipelineMetadataMap));

    ResponseBuilder responseBuilder = Response.ok();
    ResponseBuilder responseBuilderWithLinks =
        ApiUtils.addLinksHeader(responseBuilder, pipelines.getTotalElements(), page, limit);
    return responseBuilderWithLinks
        .entity(pipelines.getContent()
                    .stream()
                    .map(pipeline
                        -> PipelinesApiUtils.getPipelines(pipeline, pipelineMetadataMap.get(pipeline.getIdentifier())))
                    .collect(Collectors.toList()))
        .build();
  }

  @Override
  @Timed
  @ResponseMetered
  public Response updatePipeline(PipelineUpdateRequestBody requestBody, @OrgIdentifier String org,
      @ProjectIdentifier String project, @ResourceIdentifier String pipeline, @AccountIdentifier String account,
      Boolean isPublic) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(account, org, project),
        Resource.of("PIPELINE", pipeline), PipelineRbacPermissions.PIPELINE_EDIT);
    if (requestBody == null) {
      throw new InvalidRequestException("Pipeline Update request body must not be null.");
    }
    if (!Objects.equals(pipeline, requestBody.getIdentifier())) {
      throw new InvalidRequestException(
          String.format("Expected Pipeline identifier in Request Body to be [%s], but was [%s]", pipeline,
              requestBody.getIdentifier()));
    }
    String pipelineVersion = requestBody.getVersion();
    if (isEmpty(pipelineVersion)) {
      pipelineVersion = NGYamlHelper.detectVersionFromYamlStructure(requestBody.getPipelineYaml());
    }
    GitAwareContextHelper.populateGitDetails(PipelinesApiUtils.populateGitUpdateDetails(requestBody.getGitDetails()));
    boolean isParentIdQueryingEnabled = true;
    ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(account, org, project);
    log.info(String.format(
        "Updating Pipeline with identifier %s in project %s, org %s, account %s", pipeline, project, org, account));
    PipelineEntity pipelineEntity = PMSPipelineDtoMapper.validateAndConvertToPipelineEntity(
        PipelinesApiUtils.mapUpdateToRequestInfoDTO(requestBody), account, org, project, null, pipelineVersion, false,
        scopeInfo, isParentIdQueryingEnabled, requestBody.isAllowDynamicExecutions());

    // Validate pipeline inputs based on type
    validatePipelineInputsBasedOnType(pipelineEntity.getYaml(), pipelineVersion);

    PipelineCRUDResult pipelineCRUDResult = pmsPipelineService.validateAndUpdatePipeline(
        pipelineEntity, ChangeType.MODIFY, false, false, scopeInfo, isParentIdQueryingEnabled);
    GovernanceMetadata governanceMetadata = pipelineCRUDResult.getGovernanceMetadata();
    PipelineCreateResponseBody responseBody = new PipelineCreateResponseBody();
    if (governanceMetadata != null) {
      responseBody.setGovernanceMetadata(PipelineApiOpaUtils.buildGovernanceMetadataFromProto(governanceMetadata));
      if (governanceMetadata.getDeny()) {
        return Response.ok().entity(responseBody).build();
      }
    }
    PipelineEntity updatedEntity = pipelineCRUDResult.getPipelineEntity();
    responseBody.setIdentifier(updatedEntity.getIdentifier());
    responseBody.setPublicAccessResponse(PipelinesApiUtils.toPublicAccessResponse(
        pipelinePublicAccessHelper.markPipelinePublic(account, org, project, updatedEntity.getIdentifier(), isPublic)));
    return Response.ok().entity(responseBody).build();
  }

  @Override
  public Response patchPipeline(@OrgIdentifier String org, @ProjectIdentifier String project,
      @ResourceIdentifier String pipeline, PipelinePatchRequestBody requestBody, @AccountIdentifier String account) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(account, org, project),
        Resource.of("PIPELINE", pipeline), PipelineRbacPermissions.PIPELINE_EDIT);
    if (requestBody == null) {
      throw new InvalidRequestException("Pipeline Update request body must not be null.");
    }
    boolean isParentIdQueryingEnabled = true;
    ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(account, org, project);
    String pipelineVersion = requestBody.getVersion();
    GitAwareContextHelper.populateGitDetails(PipelinesApiUtils.populateGitUpdateDetails(requestBody.getGitDetails()));
    log.info(String.format(
        "Patching Pipeline with identifier %s in project %s, org %s, account %s", pipeline, project, org, account));
    PipelineEntity pipelineEntity = PMSPipelineDtoMapper.validateAndConvertToPipelineEntity(
        PipelinesApiUtils.mapPatchToRequestInfoDTO(requestBody, pipeline), account, org, project, null, pipelineVersion,
        true, scopeInfo, isParentIdQueryingEnabled, requestBody.isAllowDynamicExecutions());
    PipelineCRUDResult pipelineCRUDResult = pmsPipelineService.validateAndUpdatePipeline(
        pipelineEntity, ChangeType.MODIFY, false, true, scopeInfo, isParentIdQueryingEnabled);
    GovernanceMetadata governanceMetadata = pipelineCRUDResult.getGovernanceMetadata();
    PipelineCreateResponseBody responseBody = new PipelineCreateResponseBody();
    if (governanceMetadata != null) {
      responseBody.setGovernanceMetadata(PipelineApiOpaUtils.buildGovernanceMetadataFromProto(governanceMetadata));
      if (governanceMetadata.getDeny()) {
        return Response.ok().entity(responseBody).build();
      }
    }
    PipelineEntity updatedEntity = pipelineCRUDResult.getPipelineEntity();
    responseBody.setIdentifier(updatedEntity.getIdentifier());
    return Response.ok().entity(responseBody).build();
  }

  @Override
  public Response moveConfig(@OrgIdentifier String org, @ProjectIdentifier String project,
      @ResourceIdentifier String pipeline, PipelineMoveConfigRequestBody requestBody,
      @AccountIdentifier String account) {
    if (requestBody == null) {
      throw new InvalidRequestException("Pipeline move config request body must not be null.");
    }
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(account, org, project),
        Resource.of("PIPELINE", pipeline), PipelineRbacPermissions.PIPELINE_EDIT);
    if (!Objects.equals(pipeline, requestBody.getPipelineIdentifier())) {
      throw new InvalidRequestException(
          String.format("Expected Pipeline identifier in Request Body to be [%s], but was [%s]", pipeline,
              requestBody.getPipelineIdentifier()));
    }
    log.info(
        String.format("Move Config for Pipeline of move type %s with identifier %s in project %s, org %s, account %s",
            requestBody.getMoveConfigOperationType().toString(), pipeline, project, org, account));
    GitAwareContextHelper.populateGitDetails(PipelinesApiUtils.populateGitMoveDetails(requestBody.getGitDetails()));
    MoveConfigOperationDTO moveConfigOperation = PipelinesApiUtils.buildMoveConfigOperationDTO(
        requestBody.getGitDetails(), requestBody.getMoveConfigOperationType());
    boolean isParentIdQueryingEnabled = pipelineServiceHelper.isParentIdQueryingEnabled(account);
    ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(account, org, project);
    PipelineCRUDResult pipelineCRUDResult = pmsPipelineService.moveConfig(
        account, org, project, pipeline, moveConfigOperation, scopeInfo, isParentIdQueryingEnabled);
    PipelineEntity movedPipelineEntity = pipelineCRUDResult.getPipelineEntity();
    PipelineMoveConfigResponseBody responseBody = new PipelineMoveConfigResponseBody();
    responseBody.setPipelineIdentifier(movedPipelineEntity.getIdentifier());
    return Response.ok().entity(responseBody).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public Response getStepFqnList(StepFqnRequestBody requestBody, @OrgIdentifier String org,
      @ProjectIdentifier String project, @AccountIdentifier String account) {
    if (requestBody == null || isEmpty(requestBody.getPipelineYaml())) {
      throw new InvalidRequestException("Pipeline YAML must not be empty.");
    }
    List<BasicStepInfo> basicStepInfoList =
        StepNotificationSelectorHelper.getStepInfoList(requestBody.getPipelineYaml());
    List<StepFqnInfo> stepFqnInfoList = basicStepInfoList.stream()
                                            .map(info -> {
                                              StepFqnInfo stepFqnInfo = new StepFqnInfo();
                                              stepFqnInfo.setLabel(info.getLabel());
                                              stepFqnInfo.setStepFqn(info.getStepFqn());
                                              return stepFqnInfo;
                                            })
                                            .collect(Collectors.toList());
    return Response.ok().entity(stepFqnInfoList).build();
  }
}
