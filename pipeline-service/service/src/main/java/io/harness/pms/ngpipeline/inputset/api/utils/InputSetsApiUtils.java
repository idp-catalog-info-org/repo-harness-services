/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.api.utils;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.pms.pipeline.api.PipelinesApiUtils.getMoveConfigType;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.exception.InvalidRequestException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncConstants;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.gitx.GitXUtils;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.pms.inputset.InputSetErrorDTOPMS;
import io.harness.pms.inputset.InputSetErrorResponseDTOPMS;
import io.harness.pms.inputset.InputSetErrorWrapperDTOPMS;
import io.harness.pms.inputset.InputSetMoveConfigOperationDTO;
import io.harness.pms.ngpipeline.inputset.api.dto.InputSetRequestInfoDTO;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.overlayinputset.beans.resource.OverlayInputSetResponseDTOPMS;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.spec.server.pipeline.v1.model.FQNtoError;
import io.harness.spec.server.pipeline.v1.model.GitCreateDetails;
import io.harness.spec.server.pipeline.v1.model.GitDetails;
import io.harness.spec.server.pipeline.v1.model.GitMoveDetails;
import io.harness.spec.server.pipeline.v1.model.InputSetCreateRequestBody;
import io.harness.spec.server.pipeline.v1.model.InputSetError;
import io.harness.spec.server.pipeline.v1.model.InputSetErrorDetails;
import io.harness.spec.server.pipeline.v1.model.InputSetErrorWrapperDTO;
import io.harness.spec.server.pipeline.v1.model.InputSetGitUpdateDetails;
import io.harness.spec.server.pipeline.v1.model.InputSetResponseBody;
import io.harness.spec.server.pipeline.v1.model.InputSetResponseBody.StoreTypeEnum;
import io.harness.spec.server.pipeline.v1.model.InputSetUpdateRequestBody;
import io.harness.spec.server.pipeline.v1.model.OverlayInputSetResponseBody;
import io.harness.utils.ApiUtils;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class InputSetsApiUtils {
  @Inject private final PmsFeatureFlagHelper pmsFeatureFlagHelper;

  @Inject private final NGSettingsClient ngSettingsClient;

  public InputSetResponseBody getInputSetResponse(InputSetEntity inputSetEntity,
      boolean shouldFetchGitDetailsFromEntity, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    InputSetResponseBody responseBody = new InputSetResponseBody();
    responseBody.setInputSetYaml(inputSetEntity.getYaml());
    responseBody.setIdentifier(inputSetEntity.getIdentifier());
    responseBody.setName(inputSetEntity.getName());
    responseBody.setVersion(inputSetEntity.getHarnessVersion());
    responseBody.setOrg(isParentIdQueryingEnabled ? scopeInfo.getOrgIdentifier() : inputSetEntity.getOrgIdentifier());
    responseBody.setProject(
        isParentIdQueryingEnabled ? scopeInfo.getProjectIdentifier() : inputSetEntity.getProjectIdentifier());
    responseBody.pipeline(inputSetEntity.getPipelineIdentifier());
    responseBody.setDescription(inputSetEntity.getDescription());
    responseBody.setTags(ApiUtils.getTags(inputSetEntity.getTags()));
    if (shouldFetchGitDetailsFromEntity) {
      responseBody.setGitDetails(getGitDetailsForListInputSets(inputSetEntity));
    } else {
      responseBody.setGitDetails(getGitDetails(inputSetEntity));
    }
    responseBody.setCreated(inputSetEntity.getCreatedAt());
    responseBody.setUpdated(inputSetEntity.getLastUpdatedAt());
    responseBody.storeType(getStoreType(inputSetEntity.getStoreType()));
    responseBody.connectorRef(inputSetEntity.getConnectorRef());
    responseBody.setErrorDetails(new InputSetErrorDetails().valid(true));
    return responseBody;
  }

  public InputSetErrorWrapperDTO getInputSetErrorWrapper(InputSetErrorWrapperDTOPMS errorWrapperDTO) {
    InputSetErrorWrapperDTO inputSetErrorWrapperDTO = new InputSetErrorWrapperDTO();
    inputSetErrorWrapperDTO.setErrorPipelineYaml(errorWrapperDTO.getErrorPipelineYaml());
    inputSetErrorWrapperDTO.setUuidToErrorResponseMap(errorWrapperDTO.getUuidToErrorResponseMap());
    inputSetErrorWrapperDTO.setInvalidInputsetReferences(errorWrapperDTO.getInvalidInputSetReferences());
    return inputSetErrorWrapperDTO;
  }

  public InputSetResponseBody getInputSetResponseWithError(InputSetEntity inputSetEntity,
      InputSetErrorWrapperDTOPMS errorWrapperDTO, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    InputSetResponseBody responseBody = new InputSetResponseBody();
    responseBody.setInputSetYaml(inputSetEntity.getYaml());
    responseBody.setIdentifier(inputSetEntity.getIdentifier());
    responseBody.setName(inputSetEntity.getName());
    responseBody.setOrg(inputSetEntity.getOrgIdentifier());
    responseBody.setProject(inputSetEntity.getProjectIdentifier());
    responseBody.setPipeline(inputSetEntity.getPipelineIdentifier());
    responseBody.setDescription(inputSetEntity.getDescription());
    responseBody.setTags(ApiUtils.getTags(inputSetEntity.getTags()));
    responseBody.storeType(getStoreType(inputSetEntity.getStoreType()));
    responseBody.connectorRef(inputSetEntity.getConnectorRef());
    responseBody.setGitDetails(getGitDetails(inputSetEntity));
    responseBody.setCreated(inputSetEntity.getCreatedAt());
    responseBody.setUpdated(inputSetEntity.getLastUpdatedAt());
    InputSetErrorDetails errorDetails = new InputSetErrorDetails();
    errorDetails.setValid(false);
    errorDetails.setMessage("Some fields in the Input Set are invalid.");
    errorDetails.setOutdated(inputSetEntity.getIsInvalid());
    errorDetails.setErrorPipelineYaml(errorWrapperDTO.getErrorPipelineYaml());
    errorDetails.setInvalidRefs(errorWrapperDTO.getInvalidInputSetReferences());
    errorDetails.setFqnErrors(getFQNErrors(errorWrapperDTO));
    responseBody.setErrorDetails(errorDetails);
    return responseBody;
  }

  public static StoreTypeEnum getStoreType(StoreType storeType) {
    if (storeType == null || StoreType.getExternalStoreTypeMapping(storeType).equals(StoreType.INLINE)) {
      return StoreTypeEnum.INLINE;
    }
    if (storeType.equals(StoreType.REMOTE)) {
      return StoreTypeEnum.REMOTE;
    }
    return null;
  }

  public GitDetails getGitDetailsForListInputSets(InputSetEntity inputSetEntity) {
    // For List View, GitAwareContextHelper.getEntityGitDetailsFromScmGitMetadata() cannot be used, because there won't
    // be any SCM Context set in the List call.
    return (inputSetEntity.getStoreType() == null || GitXUtils.isYamlStoreBackedByGit(inputSetEntity.getStoreType()))
        ? mapEntityToGitDetails(inputSetEntity)
        : null;
  }

  public GitDetails getGitDetails(InputSetEntity inputSetEntity) {
    return inputSetEntity.getStoreType() == null ? mapEntityToGitDetails(inputSetEntity)
        : GitXUtils.isYamlStoreBackedByGit(inputSetEntity.getStoreType())
        ? mapEntityGitDetailsToGitDetails(GitAwareContextHelper.getEntityGitDetailsFromScmGitMetadata())
        : null;
  }

  private GitDetails mapEntityToGitDetails(InputSetEntity inputSetEntity) {
    GitDetails gitDetails = new GitDetails();
    gitDetails.setBranchName(inputSetEntity.getBranch());
    gitDetails.setObjectId(inputSetEntity.getObjectIdOfYaml());
    gitDetails.setRepoName(inputSetEntity.getRepo());
    gitDetails.setRepoUrl(inputSetEntity.getRepoURL());
    gitDetails.setFilePath(inputSetEntity.getFilePath());
    return gitDetails;
  }

  public GitDetails mapEntityGitDetailsToGitDetails(EntityGitDetails entityGitDetails) {
    if (entityGitDetails == null) {
      return null;
    }
    GitDetails gitDetails = new GitDetails();
    gitDetails.setBranchName(entityGitDetails.getBranch());
    gitDetails.setObjectId(entityGitDetails.getObjectId());
    gitDetails.setRepoName(entityGitDetails.getRepoName());
    gitDetails.setRepoUrl(entityGitDetails.getRepoUrl());
    gitDetails.setFilePath(entityGitDetails.getFilePath());
    gitDetails.setFileUrl(entityGitDetails.getFileUrl());
    gitDetails.setCommitId(entityGitDetails.getCommitId());
    gitDetails.setIsHarnessCodeRepo(entityGitDetails.getIsHarnessCodeRepo());
    return gitDetails;
  }

  public List<FQNtoError> getFQNErrors(InputSetErrorWrapperDTOPMS errorWrapperDTO) {
    List<FQNtoError> fqNtoErrors = new ArrayList<>();
    Set<String> keys = errorWrapperDTO.getUuidToErrorResponseMap().keySet();
    for (String key : keys) {
      InputSetErrorResponseDTOPMS value = errorWrapperDTO.getUuidToErrorResponseMap().get(key);
      FQNtoError fqNtoError = new FQNtoError();
      fqNtoError.fqn(key);
      fqNtoError.errors(getErrors(value.getErrors()));
      fqNtoErrors.add(fqNtoError);
    }
    return fqNtoErrors;
  }

  public List<InputSetError> getErrors(List<InputSetErrorDTOPMS> errorDTOPMS) {
    List<InputSetError> errors = new ArrayList<>();
    for (InputSetErrorDTOPMS errorDTO : errorDTOPMS) {
      InputSetError inputSetError = new InputSetError();
      inputSetError.setMessage(errorDTO.getMessage());
      inputSetError.setIdentifierOfErrorSource(errorDTO.getIdentifierOfErrorSource());
      inputSetError.setFieldName(errorDTO.getFieldName());
      errors.add(inputSetError);
    }
    return errors;
  }

  public static GitEntityInfo populateGitCreateDetails(GitCreateDetails gitDetails) {
    if (gitDetails == null) {
      return GitEntityInfo.builder().build();
    }
    return GitEntityInfo.builder()
        .branch(gitDetails.getBranchName())
        .filePath(gitDetails.getFilePath())
        .commitMsg(gitDetails.getCommitMessage())
        .isNewBranch(isNotEmpty(gitDetails.getBranchName()) && isNotEmpty(gitDetails.getBaseBranch()))
        .baseBranch(gitDetails.getBaseBranch())
        .connectorRef(gitDetails.getConnectorRef())
        .storeType(StoreType.getFromStringOrNull(gitDetails.getStoreType().toString()))
        .repoName(gitDetails.getRepoName())
        .isHarnessCodeRepo(gitDetails.isIsHarnessCodeRepo())
        .build();
  }

  public static GitEntityInfo populateGitUpdateDetails(InputSetGitUpdateDetails gitDetails) {
    if (gitDetails == null) {
      return GitEntityInfo.builder().build();
    }
    return GitEntityInfo.builder()
        .branch(gitDetails.getBranchName())
        .commitMsg(gitDetails.getCommitMessage())
        .isNewBranch(isNotEmpty(gitDetails.getBranchName()) && isNotEmpty(gitDetails.getBaseBranch()))
        .baseBranch(gitDetails.getBaseBranch())
        .lastCommitId(gitDetails.getLastCommitId())
        .lastObjectId(gitDetails.getLastObjectId())
        .parentEntityConnectorRef(gitDetails.getParentEntityConnectorRef())
        .parentEntityRepoName(gitDetails.getParentEntityRepoName())
        .isHarnessCodeRepo(gitDetails.isIsHarnessCodeRepo())
        .build();
  }

  public static InputSetRequestInfoDTO mapCreateToRequestInfoDTO(InputSetCreateRequestBody createRequestBody) {
    if (createRequestBody == null) {
      throw new InvalidRequestException("Create Request Body cannot be null.");
    }
    return InputSetRequestInfoDTO.builder()
        .identifier(createRequestBody.getIdentifier())
        .name(createRequestBody.getName())
        .yaml(createRequestBody.getInputSetYaml())
        .description(createRequestBody.getDescription())
        .tags(createRequestBody.getTags())
        .build();
  }

  public static InputSetRequestInfoDTO mapUpdateToRequestInfoDTO(InputSetUpdateRequestBody updateRequestBody) {
    if (updateRequestBody == null) {
      throw new InvalidRequestException("Update Request Body cannot be null.");
    }
    return InputSetRequestInfoDTO.builder()
        .identifier(updateRequestBody.getIdentifier())
        .name(updateRequestBody.getName())
        .yaml(updateRequestBody.getInputSetYaml())
        .description(updateRequestBody.getDescription())
        .tags(updateRequestBody.getTags())
        .build();
  }

  public boolean isDifferentRepoForPipelineAndInputSetsAccountSettingEnabled(String accountId) {
    String isGitClientEnabledString =
        NGRestUtils
            .getResponse(ngSettingsClient.getSetting(
                GitSyncConstants.ALLOW_DIFFERENT_REPO_FOR_PIPELINE_AND_INPUT_SETS, accountId, null, null))
            .getValue();
    return GitSyncConstants.TRUE_VALUE.equals(isGitClientEnabledString);
  }

  public static InputSetMoveConfigOperationDTO buildMoveConfigOperationDTO(GitMoveDetails gitDetails,
      io.harness.spec.server.pipeline.v1.model.MoveConfigOperationType moveConfigOperationType,
      String pipelineIdentifier) {
    return InputSetMoveConfigOperationDTO.builder()
        .repoName(gitDetails.getRepoName())
        .branch(gitDetails.getBranchName())
        .moveConfigOperationType(getMoveConfigType(moveConfigOperationType))
        .connectorRef(gitDetails.getConnectorRef())
        .baseBranch(gitDetails.getBaseBranch())
        .commitMessage(gitDetails.getCommitMessage())
        .isNewBranch(isNotEmpty(gitDetails.getBranchName()) && isNotEmpty(gitDetails.getBaseBranch()))
        .filePath(gitDetails.getFilePath())
        .pipelineIdentifier(pipelineIdentifier)
        .build();
  }

  public static Map<String, String> getNameAndIdentifierFromYaml(String yaml) {
    Map<String, String> metadata = new HashMap<>();

    YamlField inputSetYamlField;
    try {
      inputSetYamlField = YamlUtils.readTree(yaml);
    } catch (IOException e) {
      log.warn("Error when reading YAML to extract name and identifier. {}", "Invalid YAML found.");
      return null;
    }

    YamlField inputSetInnerField = inputSetYamlField.getNode().getField(YAMLFieldNameConstants.INPUT_SETS);
    if (inputSetInnerField != null) {
      metadata.put(NGCommonEntityConstants.IDENTIFIER_KEY, inputSetInnerField.getNode().getIdentifier());
      metadata.put(NGCommonEntityConstants.NAME_KEY, inputSetInnerField.getNode().getName());
      return metadata;
    }
    return null;
  }

  public OverlayInputSetResponseBody toOverlayInputSetResponseBody(OverlayInputSetResponseDTOPMS dto) {
    OverlayInputSetResponseBody body = new OverlayInputSetResponseBody();
    if (dto == null) {
      return body;
    }
    body.setAccountId(dto.getAccountId());
    body.setOrg(dto.getOrgIdentifier());
    body.setProject(dto.getProjectIdentifier());
    body.setPipeline(dto.getPipelineIdentifier());
    body.setIdentifier(dto.getIdentifier());
    body.setName(dto.getName());
    body.setDescription(dto.getDescription());
    body.setInputSetReferences(dto.getInputSetReferences());
    body.setOverlayInputSetYaml(dto.getOverlayInputSetYaml());
    body.setTags(dto.getTags());
    body.setIsOutdated(dto.isOutdated());
    body.setIsErrorResponse(dto.isErrorResponse());
    body.setInvalidInputSetReferences(dto.getInvalidInputSetReferences());
    body.setStoreType(toOverlayResponseStoreType(dto.getStoreType()));
    body.setConnectorRef(dto.getConnectorRef());
    body.setGitDetails(mapEntityGitDetailsToGitDetails(dto.getGitDetails()));
    return body;
  }

  private static OverlayInputSetResponseBody.StoreTypeEnum toOverlayResponseStoreType(StoreType storeType) {
    StoreTypeEnum mapped = getStoreType(storeType);
    if (mapped == null) {
      return null;
    }
    return OverlayInputSetResponseBody.StoreTypeEnum.fromValue(mapped.value());
  }
}
