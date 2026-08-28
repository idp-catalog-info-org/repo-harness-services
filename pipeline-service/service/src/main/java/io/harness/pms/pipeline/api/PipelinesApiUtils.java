/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.api;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.accesscontrol.publicaccess.dto.PublicAccessResponse;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.data.structure.EmptyPredicate;
import io.harness.dto.FailureInfoDTO;
import io.harness.dto.converter.FailureInfoDTOConverter;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ngexception.beans.yamlschema.NodeErrorInfo;
import io.harness.exception.ngexception.beans.yamlschema.YamlSchemaErrorDTO;
import io.harness.exception.ngexception.beans.yamlschema.YamlSchemaErrorWrapperDTO;
import io.harness.filter.FilterType;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.opa.gitx.OpaGitxStatus;
import io.harness.opa.gitx.OpaOnSaveStatusDTO;
import io.harness.pms.contracts.plan.PipelineStageInfo;
import io.harness.pms.contracts.plan.TriggerType;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.pipeline.ExecutorInfoDTO;
import io.harness.pms.pipeline.MoveConfigOperationDTO;
import io.harness.pms.pipeline.OpaOnSaveEvaluationStatus;
import io.harness.pms.pipeline.OpaOnSaveStatusResponseDTO;
import io.harness.pms.pipeline.PMSPipelineSummaryResponseDTO;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.PipelineEntity.PipelineEntityKeys;
import io.harness.pms.pipeline.PipelineFilterPropertiesDto;
import io.harness.pms.pipeline.PipelineMetadataV2;
import io.harness.pms.pipeline.api.dto.PipelineRequestInfoDTO;
import io.harness.pms.pipeline.mappers.GitXCacheMapper;
import io.harness.pms.pipeline.mappers.dto.PMSPipelineDtoMapper;
import io.harness.pms.pipeline.validation.async.beans.PipelineValidationEvent;
import io.harness.pms.plan.execution.PipelineExecutionDetailsApiUtils;
import io.harness.spec.server.pipeline.v1.model.CacheResponseMetadataDTO;
import io.harness.spec.server.pipeline.v1.model.ExecutorInfo;
import io.harness.spec.server.pipeline.v1.model.ExecutorInfo.TriggerTypeEnum;
import io.harness.spec.server.pipeline.v1.model.FixedValueFieldDependencyDetailsDTO;
import io.harness.spec.server.pipeline.v1.model.GitCreateDetails;
import io.harness.spec.server.pipeline.v1.model.GitDetails;
import io.harness.spec.server.pipeline.v1.model.GitImportInfo;
import io.harness.spec.server.pipeline.v1.model.GitMoveDetails;
import io.harness.spec.server.pipeline.v1.model.GitUpdateDetails;
import io.harness.spec.server.pipeline.v1.model.InputDetailsDTO;
import io.harness.spec.server.pipeline.v1.model.NodeInfo;
import io.harness.spec.server.pipeline.v1.model.OpaOnSaveStatus;
import io.harness.spec.server.pipeline.v1.model.ParentStageInfo;
import io.harness.spec.server.pipeline.v1.model.PipelineCreateRequestBody;
import io.harness.spec.server.pipeline.v1.model.PipelineGetResponseBody;
import io.harness.spec.server.pipeline.v1.model.PipelineInputSchemaDetailsResponseBody;
import io.harness.spec.server.pipeline.v1.model.PipelineListResponseBody;
import io.harness.spec.server.pipeline.v1.model.PipelineListResponseBody.StoreTypeEnum;
import io.harness.spec.server.pipeline.v1.model.PipelinePatchRequestBody;
import io.harness.spec.server.pipeline.v1.model.PipelineUpdateRequestBody;
import io.harness.spec.server.pipeline.v1.model.PipelineValidationResponseBody;
import io.harness.spec.server.pipeline.v1.model.PipelineValidationUUIDResponseBody;
import io.harness.spec.server.pipeline.v1.model.PipelineYamlInputDTO;
import io.harness.spec.server.pipeline.v1.model.PipelineYamlInputDetailsDTO;
import io.harness.spec.server.pipeline.v1.model.PipelineYamlInputMetadataDTO;
import io.harness.spec.server.pipeline.v1.model.RecentExecutionInfo;
import io.harness.spec.server.pipeline.v1.model.RecentExecutionInfo.ExecutionStatusEnum;
import io.harness.spec.server.pipeline.v1.model.RuntimeInputDependencyDetailsDTO;
import io.harness.spec.server.pipeline.v1.model.TemplateValidationResponseBody;
import io.harness.spec.server.pipeline.v1.model.YAMLSchemaErrorWrapper;
import io.harness.spec.server.pipeline.v1.model.YamlInputDependencyDetailsDTO;
import io.harness.spec.server.pipeline.v1.model.YamlInputType;
import io.harness.utils.ApiUtils;
import io.harness.yaml.schema.inputs.beans.DependencyDetails;
import io.harness.yaml.schema.inputs.beans.InputDetails;
import io.harness.yaml.schema.inputs.beans.InputMetadata;
import io.harness.yaml.schema.inputs.beans.SchemaInputType;
import io.harness.yaml.schema.inputs.beans.YamlInputDetails;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.bson.Document;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
public class PipelinesApiUtils {
  public static GitDetails getGitDetails(EntityGitDetails entityGitDetails) {
    if (entityGitDetails == null) {
      return null;
    }
    GitDetails gitDetails = new GitDetails();
    gitDetails.setBranchName(entityGitDetails.getBranch());
    gitDetails.setCommitId(entityGitDetails.getCommitId());
    gitDetails.setFilePath(entityGitDetails.getFilePath());
    gitDetails.setObjectId(entityGitDetails.getObjectId());
    gitDetails.setFileUrl(entityGitDetails.getFileUrl());
    gitDetails.setRepoUrl(entityGitDetails.getRepoUrl());
    gitDetails.setRepoName(entityGitDetails.getRepoName());
    gitDetails.setIsHarnessCodeRepo(entityGitDetails.getIsHarnessCodeRepo());
    return gitDetails;
  }

  public static List<YAMLSchemaErrorWrapper> getListYAMLErrorWrapper(YamlSchemaErrorWrapperDTO errorWrapperDTO) {
    if (errorWrapperDTO == null) {
      return null;
    }
    return errorWrapperDTO.getSchemaErrors()
        .stream()
        .map(PipelinesApiUtils::getYAMLErrorWrapper)
        .collect(Collectors.toList());
  }

  public static YAMLSchemaErrorWrapper getYAMLErrorWrapper(YamlSchemaErrorDTO yamlSchemaErrorDTO) {
    YAMLSchemaErrorWrapper yamlSchemaErrorWrapper = new YAMLSchemaErrorWrapper();
    yamlSchemaErrorWrapper.setFqn(yamlSchemaErrorDTO.getFqn());
    yamlSchemaErrorWrapper.setMessage(yamlSchemaErrorDTO.getMessage());
    yamlSchemaErrorWrapper.setHintMessage(yamlSchemaErrorDTO.getHintMessage());
    yamlSchemaErrorWrapper.setMessageFqn(yamlSchemaErrorDTO.getMessageWithFQN());
    yamlSchemaErrorWrapper.setStageInfo(getNodeInfo(yamlSchemaErrorDTO.getStageInfo()));
    yamlSchemaErrorWrapper.setStepInfo(getNodeInfo(yamlSchemaErrorDTO.getStepInfo()));
    return yamlSchemaErrorWrapper;
  }

  public static NodeInfo getNodeInfo(NodeErrorInfo errorInfo) {
    if (errorInfo == null) {
      return null;
    }
    NodeInfo nodeInfo = new NodeInfo();
    nodeInfo.setFqn(errorInfo.getFqn());
    nodeInfo.setName(errorInfo.getName());
    nodeInfo.setIdentifier(errorInfo.getIdentifier());
    nodeInfo.setType(errorInfo.getType());
    return nodeInfo;
  }

  public static PipelineGetResponseBody getGetResponseBody(
      PipelineEntity pipelineEntity, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    PipelineGetResponseBody pipelineGetResponseBody = new PipelineGetResponseBody();
    pipelineGetResponseBody.setPipelineYaml(pipelineEntity.getYaml());
    pipelineGetResponseBody.setIdentifier(pipelineEntity.getIdentifier());
    pipelineGetResponseBody.setName(pipelineEntity.getName());

    pipelineGetResponseBody.setOrg(scopeInfo.getOrgIdentifier());
    pipelineGetResponseBody.setProject(scopeInfo.getProjectIdentifier());
    pipelineGetResponseBody.setDescription(pipelineEntity.getDescription());
    pipelineGetResponseBody.setTags(ApiUtils.getTags(pipelineEntity.getTags()));
    pipelineGetResponseBody.setGitDetails(getGitDetails(PMSPipelineDtoMapper.getEntityGitDetails(pipelineEntity)));
    pipelineGetResponseBody.setModules(getModules(pipelineEntity));
    pipelineGetResponseBody.setCreated(pipelineEntity.getCreatedAt());
    pipelineGetResponseBody.setUpdated(pipelineEntity.getLastUpdatedAt());
    pipelineGetResponseBody.setValid(true);
    pipelineGetResponseBody.setCacheResponseMetadata(
        getCacheResponseMetadataDTO(PMSPipelineDtoMapper.getCacheResponse(pipelineEntity)));
    pipelineGetResponseBody.setVersion(pipelineEntity.getHarnessVersion());
    return pipelineGetResponseBody;
  }

  public static CacheResponseMetadataDTO getCacheResponseMetadataDTO(
      io.harness.pms.pipeline.CacheResponseMetadataDTO cacheResponseMetadata) {
    if (cacheResponseMetadata == null) {
      return null;
    }
    CacheResponseMetadataDTO cacheResponseMetadataDTO = new CacheResponseMetadataDTO();
    cacheResponseMetadataDTO.setCacheState(GitXCacheMapper.getCacheStateEnum(cacheResponseMetadata.getCacheState()));
    cacheResponseMetadataDTO.setTtlLeft(cacheResponseMetadata.getTtlLeft());
    cacheResponseMetadataDTO.setLastUpdatedAt(cacheResponseMetadata.getLastUpdatedAt());
    cacheResponseMetadataDTO.setIsSyncEnabled(cacheResponseMetadata.getIsSyncEnabled());
    return cacheResponseMetadataDTO;
  }

  public static PipelineInputSchemaDetailsResponseBody getPipelineInputSchemaDetailsResponseBody(
      List<YamlInputDetails> yamlInputDetailsList) {
    PipelineInputSchemaDetailsResponseBody responseBody = new PipelineInputSchemaDetailsResponseBody();
    List<PipelineYamlInputDetailsDTO> pipelineYamlInputDetailsDTOList = new ArrayList<>();
    yamlInputDetailsList.forEach(
        yamlInputDetails -> pipelineYamlInputDetailsDTOList.add(toPipelineYamlInputDetailsDTO(yamlInputDetails)));

    responseBody.setInputs(pipelineYamlInputDetailsDTOList);
    return responseBody;
  }

  public static List<String> getModules(PipelineEntity pipelineEntity) {
    Set<String> modules = pipelineEntity.getFilters().keySet();

    if (modules == null) {
      return null;
    }
    return new ArrayList<>(modules);
  }

  public static PipelineFilterPropertiesDto getFilterProperties(List<String> pipelineIds, String name,
      String description, List<String> tags, List<String> services, List<String> envs, String deploymentType,
      String repoName) {
    if (EmptyPredicate.isEmpty(pipelineIds) && EmptyPredicate.isEmpty(name) && EmptyPredicate.isEmpty(description)
        && EmptyPredicate.isEmpty(tags) && EmptyPredicate.isEmpty(services) && EmptyPredicate.isEmpty(envs)
        && EmptyPredicate.isEmpty(deploymentType) && EmptyPredicate.isEmpty(repoName)) {
      return null;
    }
    Document moduleProperties = getModuleProperties(services, envs, deploymentType, repoName);
    PipelineFilterPropertiesDto propertiesDto = PipelineFilterPropertiesDto.builder()
                                                    .pipelineTags(getPipelineTags(tags))
                                                    .pipelineIdentifiers(pipelineIds)
                                                    .name(name)
                                                    .description(description)
                                                    .moduleProperties(moduleProperties)
                                                    .build();
    propertiesDto.setTags(getTags(tags));
    propertiesDto.setFilterType(FilterType.PIPELINESETUP);
    return propertiesDto;
  }

  public static List<NGTag> getPipelineTags(List<String> tags) {
    if (isEmpty(tags)) {
      return null;
    }
    return tags.stream().map(PipelinesApiUtils::getNGTags).collect(Collectors.toList());
  }

  public static NGTag getNGTags(String tag) {
    String[] tagComps = tag.split(":");
    if (tagComps.length == 1) {
      return NGTag.builder().key(tagComps[0]).value("").build();
    }
    return NGTag.builder().key(tagComps[0]).value(tagComps[1]).build();
  }

  public static Map<String, String> getTags(List<String> tags) {
    if (isEmpty(tags)) {
      return null;
    }
    Map<String, String> map = new HashMap<>();
    for (String tag : tags) {
      String[] tagComps = tag.split(":");
      if (tagComps.length == 1) {
        map.put(tagComps[0], null);
      } else {
        map.put(tagComps[0], tagComps[1]);
      }
    }
    return map;
  }

  public static Document getModuleProperties(
      List<String> services, List<String> envs, String deploymentType, String repoName) {
    Map<String, Object> ci = new HashMap<>();
    Map<String, Object> cd = new HashMap<>();
    if (repoName != null) {
      ci.put("repoName", repoName);
    }
    if (deploymentType != null) {
      cd.put("deploymentTypes", deploymentType);
    }
    if (isNotEmpty(services)) {
      cd.put("serviceNames", services);
    }
    if (isNotEmpty(envs)) {
      cd.put("environmentNames", envs);
    }
    Map<String, Object> map = new HashMap<>();
    if (!ci.isEmpty()) {
      map.put("ci", ci);
    }
    if (!cd.isEmpty()) {
      map.put("cd", cd);
    }
    return (map.isEmpty()) ? null : new Document(map);
  }

  public static PipelineListResponseBody getPipelines(PMSPipelineSummaryResponseDTO pipelineDTO) {
    PipelineListResponseBody responseBody = new PipelineListResponseBody();
    responseBody.setIdentifier(pipelineDTO.getIdentifier());
    responseBody.setName(pipelineDTO.getName());
    responseBody.setDescription(pipelineDTO.getDescription());
    responseBody.setTags(pipelineDTO.getTags());
    responseBody.setCreated(pipelineDTO.getCreatedAt());
    responseBody.setUpdated(pipelineDTO.getLastUpdatedAt());
    if (pipelineDTO.getModules() != null) {
      responseBody.setModules(new ArrayList<>(pipelineDTO.getModules()));
    }
    responseBody.setStoreType(getStoreType(pipelineDTO.getStoreType()));
    responseBody.setConnectorRef(pipelineDTO.getConnectorRef());
    responseBody.setValid((pipelineDTO.getIsDraft() == null) ? null : !pipelineDTO.getIsDraft());
    responseBody.setGitDetails(getGitDetails(pipelineDTO.getGitDetails()));
    responseBody.setYamlVersion(pipelineDTO.getYamlVersion());
    if (pipelineDTO.getRecentExecutionsInfo() != null) {
      responseBody.setRecentExecutionInfo(pipelineDTO.getRecentExecutionsInfo()
                                              .stream()
                                              .map(PipelinesApiUtils::getRecentExecutionInfo)
                                              .collect(Collectors.toList()));
    }
    return responseBody;
  }

  public static PipelineListResponseBody getPipelines(
      PMSPipelineSummaryResponseDTO pipelineDTO, PipelineMetadataV2 pipelineMetadataV2) {
    PipelineListResponseBody responseBody = getPipelines(pipelineDTO);
    if (pipelineMetadataV2 == null || EmptyPredicate.isEmpty(pipelineMetadataV2.getRecentExecutionInfoList())
        || responseBody.getRecentExecutionInfo() == null) {
      return responseBody;
    }
    Map<String, FailureInfoDTO> failureInfoByExecutionId =
        pipelineMetadataV2.getRecentExecutionInfoList()
            .stream()
            .filter(info -> isNotEmpty(info.getPlanExecutionId()) && info.getFailureInfo() != null)
            .collect(Collectors.toMap(io.harness.pms.pipeline.RecentExecutionInfo::getPlanExecutionId,
                info -> FailureInfoDTOConverter.toFailureInfoDTO(info.getFailureInfo()), (first, second) -> first));
    responseBody.getRecentExecutionInfo().forEach(recentExecutionInfo
        -> recentExecutionInfo.setFailureInfo(PipelineExecutionDetailsApiUtils.toFailureInfoDTOV1(
            failureInfoByExecutionId.get(recentExecutionInfo.getExecutionId()))));
    return responseBody;
  }

  public static StoreTypeEnum getStoreType(StoreType storeType) {
    if (storeType == null) {
      return null;
    }
    if (storeType.equals(StoreType.INLINE)) {
      return StoreTypeEnum.INLINE;
    }
    if (storeType.equals(StoreType.REMOTE)) {
      return StoreTypeEnum.REMOTE;
    }
    return null;
  }

  public static RecentExecutionInfo getRecentExecutionInfo(
      io.harness.pms.pipeline.RecentExecutionInfoDTO executionInfo) {
    RecentExecutionInfo recentExecutionInfo = new RecentExecutionInfo();
    recentExecutionInfo.setRunNumber(executionInfo.getRunSequence());
    recentExecutionInfo.setExecutionId(executionInfo.getPlanExecutionId());
    recentExecutionInfo.setStarted(executionInfo.getStartTs());
    recentExecutionInfo.setEnded(executionInfo.getEndTs());
    recentExecutionInfo.setExecutionStatus(getExecutionStatus(executionInfo.getStatus()));
    recentExecutionInfo.setExecutorInfo(getExecutorInfo(executionInfo.getExecutorInfo()));
    recentExecutionInfo.setParentStageInfo(getParentStageInfo(executionInfo.getParentStageInfo()));
    return recentExecutionInfo;
  }

  public static ExecutionStatusEnum getExecutionStatus(ExecutionStatus executionStatus) {
    if (executionStatus == null) {
      return null;
    }
    return ExecutionStatusEnum.fromValue(executionStatus.getDisplayName());
  }

  public static ExecutorInfo getExecutorInfo(ExecutorInfoDTO infoDTO) {
    if (infoDTO == null) {
      return null;
    }
    ExecutorInfo executorInfo = new ExecutorInfo();
    executorInfo.setUsername(infoDTO.getUsername());
    executorInfo.setEmail(infoDTO.getEmail());
    executorInfo.setTriggerType(getTrigger(infoDTO.getTriggerType()));
    return executorInfo;
  }

  public static ParentStageInfo getParentStageInfo(PipelineStageInfo pipelineStageInfo) {
    if (pipelineStageInfo == null) {
      return null;
    }
    ParentStageInfo parentStageInfo = new ParentStageInfo();
    parentStageInfo.setHasParentPipeline(pipelineStageInfo.getHasParentPipeline());
    if (!pipelineStageInfo.getHasParentPipeline()) {
      return parentStageInfo;
    }
    parentStageInfo.setExecutionId(pipelineStageInfo.getExecutionId());
    parentStageInfo.setName(pipelineStageInfo.getPipelineName());
    parentStageInfo.setIdentifier(pipelineStageInfo.getIdentifier());
    parentStageInfo.setStageNodeId(pipelineStageInfo.getStageNodeId());
    parentStageInfo.setRunSequence(pipelineStageInfo.getRunSequence());
    parentStageInfo.setProjectId(pipelineStageInfo.getProjectId());
    parentStageInfo.setOrgId(pipelineStageInfo.getOrgId());
    return parentStageInfo;
  }

  public static TriggerTypeEnum getTrigger(TriggerType triggerType) {
    if (triggerType == null) {
      return null;
    }
    switch (triggerType.getNumber()) {
      case 0:
        return TriggerTypeEnum.NOOP;
      case 1:
        return TriggerTypeEnum.MANUAL;
      case 2:
        return TriggerTypeEnum.WEBHOOK;
      case 3:
        return TriggerTypeEnum.WEBHOOK_CUSTOM;
      case 4:
        return TriggerTypeEnum.SCHEDULER_CRON;
      default:
        return null;
    }
  }

  public static List<String> getSorting(String field, String order) {
    if (field == null) {
      if (order != null) {
        throw new InvalidRequestException("Order of sorting provided without Sort field.");
      }
      return null;
    }
    switch (field) {
      case "name":
        break;
      case "updated":
        field = "lastUpdatedAt";
        break;
      default:
        throw new InvalidRequestException("Field provided for sorting unidentified. Accepted values: name / updated");
    }
    return buildSortingList(field, order);
  }

  // Pipeline list sorting accepts an additional `last_executed` key (mapped to the
  // PipelineEntity field `executionSummaryInfo.lastExecutionTs`). Kept separate from
  // getSorting() so other entities (e.g. input sets) that reuse getSorting() do not
  // start accepting a key that maps to a field they do not have.
  public static List<String> getPipelineSorting(String field, String order) {
    if (field == null) {
      if (order != null) {
        throw new InvalidRequestException("Order of sorting provided without Sort field.");
      }
      return null;
    }
    switch (field) {
      case "name":
        break;
      case "updated":
        field = "lastUpdatedAt";
        break;
      case "last_executed":
        field = PipelineEntityKeys.lastExecutedAt;
        break;
      default:
        throw new InvalidRequestException(
            "Field provided for sorting unidentified. Accepted values: name / updated / last_executed");
    }
    return buildSortingList(field, order);
  }

  private static List<String> buildSortingList(String field, String order) {
    if (order == null) {
      order = "DESC";
    }
    if (!order.equalsIgnoreCase("asc") && !order.equalsIgnoreCase("desc")) {
      throw new InvalidRequestException("Order of sorting unidentified. Accepted values: ASC / DESC");
    }
    return new ArrayList<>(Collections.singleton(field + "," + order));
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
  public static GitEntityInfo populateGitImportDetails(GitImportInfo gitDetails) {
    if (gitDetails == null) {
      return GitEntityInfo.builder().build();
    }
    return GitEntityInfo.builder()
        .branch(gitDetails.getBranchName())
        .filePath(gitDetails.getFilePath())
        .connectorRef(gitDetails.getConnectorRef())
        .repoName(gitDetails.getRepoName())
        .build();
  }

  public static GitEntityInfo populateGitMoveDetails(GitMoveDetails gitDetails) {
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
        .repoName(gitDetails.getRepoName())
        .isHarnessCodeRepo(gitDetails.isIsHarnessCodeRepo())
        .build();
  }

  public static GitEntityInfo populateGitUpdateDetails(GitUpdateDetails gitDetails) {
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
        .repoName(gitDetails.getRepoName())
        .storeType(StoreType.getFromStringOrNull(
            (gitDetails.getStoreType() == null) ? null : gitDetails.getStoreType().value()))
        .connectorRef(gitDetails.getConnectorRef())
        .filePath(gitDetails.getFilePath())
        .build();
  }

  public static PipelineRequestInfoDTO mapCreateToRequestInfoDTO(PipelineCreateRequestBody createRequestBody) {
    if (createRequestBody == null) {
      throw new InvalidRequestException("Create Request Body cannot be null.");
    }
    return PipelineRequestInfoDTO.builder()
        .identifier(createRequestBody.getIdentifier())
        .name(createRequestBody.getName())
        .yaml(createRequestBody.getPipelineYaml())
        .description(createRequestBody.getDescription())
        .tags(createRequestBody.getTags())
        .build();
  }

  public static PipelineRequestInfoDTO mapUpdateToRequestInfoDTO(PipelineUpdateRequestBody updateRequestBody) {
    if (updateRequestBody == null) {
      throw new InvalidRequestException("Update Request Body cannot be null.");
    }
    return PipelineRequestInfoDTO.builder()
        .identifier(updateRequestBody.getIdentifier())
        .name(updateRequestBody.getName())
        .yaml(updateRequestBody.getPipelineYaml())
        .description(updateRequestBody.getDescription())
        .tags(updateRequestBody.getTags())
        .build();
  }

  public static PipelineRequestInfoDTO mapPatchToRequestInfoDTO(
      PipelinePatchRequestBody patchRequestBody, String identifier) {
    if (patchRequestBody == null) {
      throw new InvalidRequestException("Update Request Body cannot be null.");
    }
    return PipelineRequestInfoDTO.builder()
        .identifier(identifier)
        .name(patchRequestBody.getName())
        .yaml(patchRequestBody.getPipelineYaml())
        .description(patchRequestBody.getDesc())
        .tags(patchRequestBody.getTags())
        .build();
  }

  public static PipelineValidationUUIDResponseBody buildPipelineValidationUUIDResponseBody(
      PipelineValidationEvent event) {
    return new PipelineValidationUUIDResponseBody().uuid(event.getUuid());
  }

  public static PipelineValidationResponseBody buildPipelineValidationResponseBody(PipelineValidationEvent event) {
    return new PipelineValidationResponseBody()
        .status(event.getStatus().name())
        .policyEval(PipelineApiOpaUtils.buildGovernanceMetadataFromProto(event.getResult().getGovernanceMetadata()))
        .startTs(event.getStartTs())
        .templateValidationResponse(
            new TemplateValidationResponseBody()
                .validYaml(event.getResult().getTemplateValidationResponse().isValidYaml())
                .exceptionMessage(event.getResult().getTemplateValidationResponse().getExceptionMessage()))
        .endTs(event.getEndTs());
  }

  public static MoveConfigOperationDTO buildMoveConfigOperationDTO(GitMoveDetails gitDetails,
      io.harness.spec.server.pipeline.v1.model.MoveConfigOperationType moveConfigOperationType) {
    return MoveConfigOperationDTO.builder()
        .repoName(gitDetails.getRepoName())
        .branch(gitDetails.getBranchName())
        .moveConfigOperationType(getMoveConfigType(moveConfigOperationType))
        .connectorRef(gitDetails.getConnectorRef())
        .baseBranch(gitDetails.getBaseBranch())
        .commitMessage(gitDetails.getCommitMessage())
        .isNewBranch(isNotEmpty(gitDetails.getBranchName()) && isNotEmpty(gitDetails.getBaseBranch()))
        .filePath(gitDetails.getFilePath())
        .isHarnessCodeRepo(gitDetails.isIsHarnessCodeRepo())
        .build();
  }

  public static io.harness.pms.pipeline.MoveConfigOperationType getMoveConfigType(
      io.harness.spec.server.pipeline.v1.model.MoveConfigOperationType moveConfigOperationType) {
    switch (moveConfigOperationType) {
      case INLINE_TO_REMOTE:
        return io.harness.pms.pipeline.MoveConfigOperationType.INLINE_TO_REMOTE;
      default:
        throw new InvalidRequestException("Invalid move config type provided.");
    }
  }

  private static PipelineYamlInputDetailsDTO toPipelineYamlInputDetailsDTO(YamlInputDetails yamlInputDetails) {
    PipelineYamlInputDetailsDTO pipelineYamlInputDetailsDTO = new PipelineYamlInputDetailsDTO();
    pipelineYamlInputDetailsDTO.setDetails(toPipelineYamlInputDTO(yamlInputDetails.getInputDetails()));
    if (yamlInputDetails.getInputMetadataWrapper() != null
        && isNotEmpty(yamlInputDetails.getInputMetadataWrapper().getInputMetadataList())) {
      pipelineYamlInputDetailsDTO.setMetadata(yamlInputDetails.getInputMetadataWrapper()
                                                  .getInputMetadataList()
                                                  .stream()
                                                  .map(PipelinesApiUtils::toPipelineYamlInputMetadataDTO)
                                                  .collect(Collectors.toList()));
    }
    return pipelineYamlInputDetailsDTO;
  }

  private static PipelineYamlInputDTO toPipelineYamlInputDTO(InputDetails inputDetails) {
    PipelineYamlInputDTO pipelineYamlInputDTO = new PipelineYamlInputDTO();
    pipelineYamlInputDTO.setName(inputDetails.getName());
    pipelineYamlInputDTO.setDesc(inputDetails.getDescription());
    pipelineYamlInputDTO.setType(getYamlInputType(inputDetails.getType()));
    pipelineYamlInputDTO.setRequired(inputDetails.isRequired());
    pipelineYamlInputDTO.setAllowedValues(inputDetails.getAllowedValues());
    pipelineYamlInputDTO.setRegex(inputDetails.getRegex());
    pipelineYamlInputDTO.execution(inputDetails.getExecution());
    pipelineYamlInputDTO.setDefault(inputDetails.getDefaultValue());
    return pipelineYamlInputDTO;
  }

  private static PipelineYamlInputMetadataDTO toPipelineYamlInputMetadataDTO(InputMetadata inputMetadata) {
    PipelineYamlInputMetadataDTO pipelineYamlInputMetadataDTO = new PipelineYamlInputMetadataDTO();
    if (inputMetadata != null) {
      pipelineYamlInputMetadataDTO.setFieldProperties(toInputDetailsDTO(inputMetadata.getInputDetails()));
      pipelineYamlInputMetadataDTO.setDependencies(
          toYamlInputDependencyDetailsDTO(inputMetadata.getDependencyDetails()));
    }
    return pipelineYamlInputMetadataDTO;
  }

  private static InputDetailsDTO toInputDetailsDTO(InputMetadata.InputDetails inputDetails) {
    InputDetailsDTO inputDetailsDTO = new InputDetailsDTO();
    inputDetailsDTO.setInputType(inputDetails.getInputType());
    inputDetailsDTO.setEntityGroup(inputDetails.getEntityGroup());
    inputDetailsDTO.entityType(inputDetails.getEntityType());
    inputDetailsDTO.setPath(inputDetails.getFqnFromEntityRoot());

    return inputDetailsDTO;
  }

  private static YamlInputDependencyDetailsDTO toYamlInputDependencyDetailsDTO(DependencyDetails dependencyDetails) {
    YamlInputDependencyDetailsDTO yamlInputDependencyDetailsDTO = new YamlInputDependencyDetailsDTO();
    List<FixedValueFieldDependencyDetailsDTO> fixedValueFieldDependencyDetailsDTOList = new ArrayList<>();
    List<RuntimeInputDependencyDetailsDTO> runtimeInputDependencyDetailsDTOList = new ArrayList<>();

    if (dependencyDetails != null) {
      if (dependencyDetails.getRuntimeInputDependencyDetailsList() != null) {
        dependencyDetails.getRuntimeInputDependencyDetailsList().forEach(inputDependencyDetails -> {
          RuntimeInputDependencyDetailsDTO runtimeInputDependencyDetailsDTO = new RuntimeInputDependencyDetailsDTO();
          runtimeInputDependencyDetailsDTO.setInputName(inputDependencyDetails.getInputName());
          runtimeInputDependencyDetailsDTO.setFieldName(inputDependencyDetails.getFieldName());
          runtimeInputDependencyDetailsDTO.setEntityGroup(inputDependencyDetails.getEntityGroup());
          runtimeInputDependencyDetailsDTO.setEntityType(inputDependencyDetails.getEntityType());
          runtimeInputDependencyDetailsDTO.setPath(inputDependencyDetails.getFqnFromEntityRoot());
          runtimeInputDependencyDetailsDTOList.add(runtimeInputDependencyDetailsDTO);
        });
      }
      if (dependencyDetails.getFixedValueDependencyDetailsList() != null) {
        dependencyDetails.getFixedValueDependencyDetailsList().forEach(constantDependencyDetails -> {
          FixedValueFieldDependencyDetailsDTO fixedValueFieldDependencyDetailsDTO =
              new FixedValueFieldDependencyDetailsDTO();
          fixedValueFieldDependencyDetailsDTO.setPath(constantDependencyDetails.getFqnFromRootEntity());
          fixedValueFieldDependencyDetailsDTO.setFieldValue(constantDependencyDetails.getFieldValue());
          fixedValueFieldDependencyDetailsDTO.setFieldInputType(constantDependencyDetails.getPropertyType());
          fixedValueFieldDependencyDetailsDTOList.add(fixedValueFieldDependencyDetailsDTO);
        });
      }
    }

    yamlInputDependencyDetailsDTO.setRequiredFixedValues(fixedValueFieldDependencyDetailsDTOList);
    yamlInputDependencyDetailsDTO.setRequiredRuntimeInputs(runtimeInputDependencyDetailsDTOList);
    return yamlInputDependencyDetailsDTO;
  }

  private static YamlInputType getYamlInputType(SchemaInputType schemaInputType) {
    switch (schemaInputType) {
      case STRING:
        return YamlInputType.STRING;
      case BOOLEAN:
        return YamlInputType.BOOLEAN;
      case INTEGER:
        return YamlInputType.INTEGER;
      default:
        return YamlInputType.OBJECT;
    }
  }

  public static io.harness.spec.server.pipeline.v1.model.PublicAccessResponse toPublicAccessResponse(
      PublicAccessResponse publicAccessResponse) {
    io.harness.spec.server.pipeline.v1.model.PublicAccessResponse response =
        new io.harness.spec.server.pipeline.v1.model.PublicAccessResponse();
    response.isPublic(publicAccessResponse.isPublic());
    response.setError(publicAccessResponse.getErrorMessage());
    return response;
  }

  public static OpaOnSaveStatusResponseDTO toOpaOnSaveStatusResponse(OpaOnSaveStatusDTO dto, String currentCommitId) {
    return OpaOnSaveStatusResponseDTO.builder()
        .status(mapToEvaluationStatus(dto.getStatus()))
        .repoURL(dto.getRepoURL())
        .filePath(dto.getFilePath())
        .evaluatedAtCommitId(dto.getEvaluatedAtCommitId())
        .lastValidCommitId(dto.getLastValidCommitId())
        .evaluatedAt(dto.getEvaluatedAt())
        .message(dto.getMessage())
        .currentCommitId(currentCommitId)
        .governanceMetadata(dto.getGovernanceMetadata())
        .build();
  }

  public static OpaOnSaveStatus toV1OpaOnSaveStatus(OpaOnSaveStatusDTO dto, String currentCommitId) {
    OpaOnSaveStatus v1 = new OpaOnSaveStatus();
    v1.setStatus(mapToV1StatusEnum(dto.getStatus()));
    v1.setRepoUrl(dto.getRepoURL());
    v1.setFilePath(dto.getFilePath());
    v1.setEvaluatedAtCommitId(dto.getEvaluatedAtCommitId());
    v1.setLastValidCommitId(dto.getLastValidCommitId());
    if (dto.getEvaluatedAt() != null) {
      v1.setEvaluatedAt(dto.getEvaluatedAt());
    }
    v1.setMessage(dto.getMessage());
    v1.setCurrentCommitId(currentCommitId);
    if (dto.getGovernanceMetadata() != null) {
      v1.setGovernanceMetadata(PipelineApiOpaUtils.buildGovernanceMetadataFromProto(dto.getGovernanceMetadata()));
    }
    return v1;
  }

  private static OpaOnSaveEvaluationStatus mapToEvaluationStatus(OpaGitxStatus status) {
    if (status == null) {
      return null;
    }
    try {
      return OpaOnSaveEvaluationStatus.valueOf(status.name());
    } catch (IllegalArgumentException e) {
      return OpaOnSaveEvaluationStatus.UNKNOWN;
    }
  }

  private static OpaOnSaveStatus.StatusEnum mapToV1StatusEnum(OpaGitxStatus status) {
    if (status == null) {
      return null;
    }
    OpaOnSaveStatus.StatusEnum mapped = OpaOnSaveStatus.StatusEnum.fromValue(status.name());
    return mapped != null ? mapped : OpaOnSaveStatus.StatusEnum.UNKNOWN;
  }

  static OpaGitxStatus gmToStatus(io.harness.governance.GovernanceMetadata gm) {
    if (gm == null) {
      return OpaGitxStatus.NOT_EVALUATED;
    }
    if (gm.getDeny()) {
      return OpaGitxStatus.ERROR;
    }
    if ("warning".equalsIgnoreCase(gm.getStatus())) {
      return OpaGitxStatus.WARNING;
    }
    return OpaGitxStatus.SUCCESS;
  }

  private static boolean isCleanStatus(OpaGitxStatus s) {
    return s == OpaGitxStatus.SUCCESS || s == OpaGitxStatus.WARNING;
  }

  @lombok.Value
  @lombok.Builder
  public static class OpaOnSaveEnrichmentResult {
    OpaOnSaveStatusDTO opaStatus;
    String currentCommitId;
  }

  public static Optional<OpaOnSaveEnrichmentResult> resolveValidateOpaEnrichment(
      io.harness.pms.pipeline.PipelineEntity entity, String accountId, io.harness.governance.GovernanceMetadata freshGm,
      io.harness.pms.opa.gitx.pipeline.PipelineOpaStatusHandler opaStatusHandler, String evaluatedCommitId,
      Long opaEvaluatedAt, String opaLastValidCommitId) {
    if (freshGm == null || !io.harness.gitaware.helper.GitAwareContextHelper.isRemoteEntity(entity)) {
      return Optional.empty();
    }

    OpaGitxStatus status = gmToStatus(freshGm);

    Long resolvedEvaluatedAt;
    String resolvedLastValid;
    if (opaEvaluatedAt != null) {
      resolvedEvaluatedAt = opaEvaluatedAt;
      resolvedLastValid = isCleanStatus(status) ? evaluatedCommitId : opaLastValidCommitId;
    } else {
      Optional<OpaOnSaveStatusDTO> existingRecord = opaStatusHandler.get(entity, accountId, evaluatedCommitId);
      resolvedEvaluatedAt = existingRecord.map(OpaOnSaveStatusDTO::getEvaluatedAt).orElse(null);
      resolvedLastValid = isCleanStatus(status)
          ? evaluatedCommitId
          : existingRecord.map(OpaOnSaveStatusDTO::getLastValidCommitId).orElse(null);
    }

    OpaOnSaveStatusDTO dto = OpaOnSaveStatusDTO.builder()
                                 .status(status)
                                 .evaluatedAtCommitId(evaluatedCommitId)
                                 .lastValidCommitId(resolvedLastValid)
                                 .evaluatedAt(resolvedEvaluatedAt)
                                 .message(io.harness.opa.gitx.OpaGitxUtils.aggregateDenyMessages(freshGm))
                                 .governanceMetadata(freshGm)
                                 .build();
    return Optional.of(OpaOnSaveEnrichmentResult.builder().opaStatus(dto).currentCommitId(evaluatedCommitId).build());
  }
}
