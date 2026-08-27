/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.mappers.dto;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.Long.parseLong;
import static org.apache.commons.lang3.StringUtils.isNumeric;

import io.harness.EntityType;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.IdentifierRef;
import io.harness.beans.ScopeInfo;
import io.harness.common.NGExpressionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.validator.EntityIdentifierValidator;
import io.harness.exception.InvalidRequestException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.sdk.CacheResponse;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.gitsync.sdk.EntityGitDetailsMapper;
import io.harness.gitsync.sdk.EntityValidityDetails;
import io.harness.gitx.GitXUtils;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.pipeline.CacheResponseMetadataDTO;
import io.harness.pms.pipeline.ExecutionSummaryInfoDTO;
import io.harness.pms.pipeline.ExecutorInfoDTO;
import io.harness.pms.pipeline.PMSPipelineResponseDTO;
import io.harness.pms.pipeline.PMSPipelineSummaryResponseDTO;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.PipelineEntity.PipelineEntityBuilder;
import io.harness.pms.pipeline.PipelineEntityUtils;
import io.harness.pms.pipeline.PipelineMetadataV2;
import io.harness.pms.pipeline.PipelineValidationResponseDTO;
import io.harness.pms.pipeline.RecentExecutionInfo;
import io.harness.pms.pipeline.RecentExecutionInfoDTO;
import io.harness.pms.pipeline.api.dto.PipelineRequestInfoDTO;
import io.harness.pms.pipeline.mappers.ModuleInfoMapper;
import io.harness.pms.pipeline.validation.async.beans.PipelineValidationEvent;
import io.harness.pms.pipeline.yaml.BasicPipeline;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YamlUtils;
import io.harness.scope.ScopeHelper;

import com.google.common.hash.Hashing;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@UtilityClass
@Slf4j
public class PMSPipelineDtoMapper {
  public static final String BOOLEAN_TRUE_VALUE = "true";
  public PMSPipelineResponseDTO writePipelineDto(PipelineEntity pipelineEntity) {
    return PMSPipelineResponseDTO.builder()
        .yamlPipeline(pipelineEntity.getYaml())
        .version(pipelineEntity.getVersion())
        .modules(pipelineEntity.getFilters().keySet())
        .gitDetails(getEntityGitDetails(pipelineEntity))
        .entityValidityDetails(getEntityValidityDetails(pipelineEntity))
        .cacheResponse(getCacheResponse(pipelineEntity))
        .allowDynamicExecutions(pipelineEntity.getAllowDynamicExecutions())
        .storeType(StoreType.getExternalStoreTypeMapping(pipelineEntity.getStoreType()))
        .isInlineHCEntity(StoreType.INLINE_HC.equals(pipelineEntity.getStoreType()))
        .build();
  }

  public EntityGitDetails getEntityGitDetails(PipelineEntity pipelineEntity) {
    EntityGitDetails gitDetails = pipelineEntity.getStoreType() == null
        ? EntityGitDetailsMapper.mapEntityGitDetails(pipelineEntity)
        : GitXUtils.isYamlStoreBackedByGit(pipelineEntity.getStoreType())
        ? GitAwareContextHelper.getEntityGitDetailsFromScmGitMetadata()
        : null;
    if (null != gitDetails) {
      gitDetails.setRepoUrl(pipelineEntity.getRepoURL());
    }
    return gitDetails;
  }

  public CacheResponseMetadataDTO getCacheResponse(PipelineEntity pipelineEntity) {
    if (pipelineEntity.getStoreType() == StoreType.REMOTE) {
      return getCacheResponseFromGitContext();
    }
    return null;
  }

  private EntityGitDetails getEntityGitDetailsForMetadataResponse(PipelineEntity pipelineEntity) {
    EntityGitDetails entityGitDetails = pipelineEntity.getStoreType() == null
        ? EntityGitDetailsMapper.mapEntityGitDetails(pipelineEntity)
        : GitXUtils.isYamlStoreBackedByGit(pipelineEntity.getStoreType())
        ? GitAwareContextHelper.getEntityGitDetails(pipelineEntity)
        : null;
    if (entityGitDetails != null) {
      entityGitDetails.setRepoUrl(pipelineEntity.getRepoURL());
    }
    return entityGitDetails;
  }

  public EntityValidityDetails getEntityValidityDetails(PipelineEntity pipelineEntity) {
    return pipelineEntity.getStoreType() != null || !pipelineEntity.isEntityInvalid()
        ? EntityValidityDetails.builder().valid(true).build()
        : EntityValidityDetails.builder().valid(false).invalidYaml(pipelineEntity.getYaml()).build();
  }

  public PipelineEntity toPipelineEntity(String accountId, String orgId, String projectId, String yaml) {
    return toPipelineEntity(accountId, orgId, projectId, yaml, null, false);
  }

  public PipelineEntity toPipelineEntity(String accountId, String orgId, String projectId, String yaml,
      ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    try {
      BasicPipeline basicPipeline = YamlUtils.read(yaml, BasicPipeline.class);

      validatePipelineData(basicPipeline.getIdentifier(), basicPipeline.getName(), false);
      PipelineEntityBuilder pipelineEntityBuilder =
          PipelineEntity.builder()
              .yaml(yaml)
              .accountId(accountId)
              .name(basicPipeline.getName())
              .identifier(basicPipeline.getIdentifier())
              .description(basicPipeline.getDescription())
              .tags(TagMapper.convertToList(basicPipeline.getTags()))
              .allowStageExecutions(basicPipeline.isAllowStageExecutions())
              .yamlHash(getYamlHash(yaml))
              .orgIdentifier(orgId)
              .projectIdentifier(projectId)
              .parentUniqueId(scopeInfo != null ? scopeInfo.getUniqueId() : null);
      return pipelineEntityBuilder.build();
    } catch (IOException e) {
      if (YamlUtils.isYamlSizeLimitExceeded(e)) {
        throw new InvalidRequestException(PipelineEntityUtils.PIPELINE_YAML_SIZE_LIMIT_EXCEEDED_MESSAGE, e);
      }
      throw new InvalidRequestException("Cannot create pipeline entity due to " + e.getMessage());
    }
  }

  public PipelineEntity toSimplifiedPipelineEntity(String accountId, String orgId, String projectId, String pipelineId,
      String pipelineName, String yaml, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    return toSimplifiedPipelineEntity(accountId, orgId, projectId, pipelineId, pipelineName, yaml, null, "", false,
        scopeInfo, isParentIdQueryingEnabled);
  }

  public PipelineEntity toSimplifiedPipelineEntity(String accountId, String orgId, String projectId, String pipelineId,
      String pipelineName, String yaml, Map<String, String> tags, String desc, boolean isPatch, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled) {
    validatePipelineData(pipelineId, pipelineName, isPatch);
    PipelineEntityBuilder pipelineEntityBuilder =
        PipelineEntity.builder()
            .yaml(yaml)
            .accountId(accountId)
            .name(pipelineName)
            .identifier(pipelineId)
            .tags(TagMapper.convertToListWithNull(tags))
            .harnessVersion(HarnessYamlVersion.V1)
            .yamlHash(getYamlHash(yaml))
            .description(desc)
            .allowStageExecutions(true)
            .orgIdentifier(orgId)
            .projectIdentifier(projectId)
            .parentUniqueId(scopeInfo != null ? scopeInfo.getUniqueId() : null);
    return pipelineEntityBuilder.build();
  }

  public PipelineEntity toPipelineEntity(String accountId, String orgId, String projectId, String pipelineIdentifier,
      String pipelineName, String yaml, Boolean isDraft, String pipelineVersion, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled, Boolean allowDynamicExecutions) {
    PipelineEntity pipelineEntity;
    // Use the pipeline name from api request only for V1 yaml
    if (HarnessYamlVersion.isV1(pipelineVersion)) {
      pipelineEntity = toSimplifiedPipelineEntity(
          accountId, orgId, projectId, pipelineIdentifier, pipelineName, yaml, scopeInfo, isParentIdQueryingEnabled);
    } else {
      pipelineEntity = toPipelineEntity(accountId, orgId, projectId, yaml, scopeInfo, isParentIdQueryingEnabled);
    }
    if (isDraft == null) {
      isDraft = false;
    }
    pipelineEntity.setIsDraft(isDraft);
    pipelineEntity.setHarnessVersion(pipelineVersion);
    if (allowDynamicExecutions != null) {
      pipelineEntity.setAllowDynamicExecutions(allowDynamicExecutions);
    }
    return pipelineEntity;
  }

  public PipelineEntity toMinimalPipelineEntity(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, String pipelineName, String yaml, Boolean isDraft,
      String pipelineVersion, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    PipelineEntityBuilder pipelineEntityBuilder =
        PipelineEntity.builder()
            .yaml(yaml)
            .accountId(accountIdentifier)
            .name(pipelineName)
            .identifier(pipelineIdentifier)
            .yamlHash(getYamlHash(yaml))
            .orgIdentifier(orgIdentifier)
            .projectIdentifier(projectIdentifier)
            .allowStageExecutions(false)
            .isDraft(isDraft)
            .tags(Collections.emptyList())
            .parentUniqueId(scopeInfo != null ? scopeInfo.getUniqueId() : null);
    return pipelineEntityBuilder.build();
  }

  public Integer getYamlHash(String yaml) {
    if (isNotEmpty(yaml)) {
      return Hashing.murmur3_32_fixed().hashString(yaml, StandardCharsets.UTF_8).asInt();
    }
    return null;
  }

  public PipelineEntity validateAndConvertToPipelineEntity(PipelineRequestInfoDTO requestInfoDTO, String accountId,
      String orgId, String projectId, Boolean isDraft, String pipelineVersion, boolean isPatch, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled, Boolean allowDynamicExecutions) {
    try {
      validatePipelineData(requestInfoDTO.getIdentifier(), requestInfoDTO.getName(), isPatch);
      BasicPipeline basicPipeline = null;
      if (pipelineVersion != null && !pipelineVersion.equals(HarnessYamlVersion.V0)) {
        return toSimplifiedPipelineEntity(accountId, orgId, projectId, requestInfoDTO.getIdentifier(),
            requestInfoDTO.getName(), requestInfoDTO.getYaml(), requestInfoDTO.getTags(),
            requestInfoDTO.getDescription(), isPatch, scopeInfo, isParentIdQueryingEnabled);
      }
      basicPipeline = YamlUtils.read(requestInfoDTO.getYaml(), BasicPipeline.class);
      if (isEmpty(basicPipeline.getIdentifier())) {
        throw new InvalidRequestException("Required field [pipelineId] is either null or empty in the pipeline yaml");
      }

      if (isNotEmpty(basicPipeline.getIdentifier())
          && !basicPipeline.getIdentifier().equals(requestInfoDTO.getIdentifier())) {
        throw new InvalidRequestException(String.format("Expected Pipeline identifier in YAML to be [%s], but was [%s]",
            requestInfoDTO.getIdentifier(), basicPipeline.getIdentifier()));
      }
      if (isEmpty(basicPipeline.getName())) {
        throw new InvalidRequestException("Required field [pipelineName] is either null or empty in the pipeline yaml");
      }
      if (isNotEmpty(basicPipeline.getName()) && !basicPipeline.getName().equals(requestInfoDTO.getName())) {
        throw new InvalidRequestException(String.format("Expected Pipeline name in YAML to be [%s], but was [%s]",
            requestInfoDTO.getName(), basicPipeline.getName()));
      }
      if (isEmpty(basicPipeline.getOrgIdentifier())) {
        throw new InvalidRequestException("Required field [orgId] is either null or empty in the pipeline yaml");
      }
      if (isNotEmpty(basicPipeline.getOrgIdentifier()) && !basicPipeline.getOrgIdentifier().equals(orgId)) {
        throw new InvalidRequestException(
            String.format("Expected Pipeline Organization identifier in YAML to be [%s], but was [%s]", orgId,
                basicPipeline.getOrgIdentifier()));
      }
      if (isEmpty(basicPipeline.getProjectIdentifier())) {
        throw new InvalidRequestException("Required field [projectId] is either null or empty in the pipeline yaml");
      }
      if (isNotEmpty(basicPipeline.getProjectIdentifier()) && !basicPipeline.getProjectIdentifier().equals(projectId)) {
        throw new InvalidRequestException(
            String.format("Expected Pipeline Project identifier in YAML to be [%s], but was [%s]", projectId,
                basicPipeline.getProjectIdentifier()));
      }
      if (isNotEmpty(basicPipeline.getDescription()) && isNotEmpty(requestInfoDTO.getDescription())
          && !basicPipeline.getDescription().equals(requestInfoDTO.getDescription())) {
        throw new InvalidRequestException(
            String.format("Expected Pipeline description in YAML to be [%s], but was [%s]",
                requestInfoDTO.getDescription(), basicPipeline.getDescription()));
      }
      if (!(isEmpty(basicPipeline.getTags()) && isEmpty(requestInfoDTO.getTags())
              || (isNotEmpty(basicPipeline.getTags()) && isNotEmpty(requestInfoDTO.getTags())
                  && basicPipeline.getTags().equals(requestInfoDTO.getTags())))) {
        throw new InvalidRequestException(
            String.format("The pipeline tags in the YAML must match the tags passed in the request params. The tags "
                    + "passed in the pipeline YAML were: [%s], but the tags in the request params were: [%s]",
                requestInfoDTO.getTags(), basicPipeline.getTags()));
      }
      PipelineEntity pipelineEntity = PipelineEntity.builder()
                                          .yaml(requestInfoDTO.getYaml())
                                          .accountId(accountId)
                                          .orgIdentifier(orgId)
                                          .projectIdentifier(projectId)
                                          .parentUniqueId(scopeInfo != null ? scopeInfo.getUniqueId() : null)
                                          .name(requestInfoDTO.getName())
                                          .identifier(requestInfoDTO.getIdentifier())
                                          .description(requestInfoDTO.getDescription())
                                          .tags(TagMapper.convertToList(requestInfoDTO.getTags()))
                                          // allowStageExecutions will still be extracted from Yaml
                                          .allowStageExecutions(basicPipeline.isAllowStageExecutions())
                                          .build();

      if (isDraft == null) {
        isDraft = false;
      }
      if (allowDynamicExecutions != null) {
        pipelineEntity.setAllowDynamicExecutions(allowDynamicExecutions);
      }
      pipelineEntity.setYamlHash(getYamlHash(requestInfoDTO.getYaml()));
      pipelineEntity.setIsDraft(isDraft);
      return pipelineEntity;
    } catch (IOException e) {
      if (YamlUtils.isYamlSizeLimitExceeded(e)) {
        throw new InvalidRequestException(PipelineEntityUtils.PIPELINE_YAML_SIZE_LIMIT_EXCEEDED_MESSAGE, e);
      }
      throw new InvalidRequestException("Cannot create pipeline entity due to " + e.getMessage());
    }
  }

  public PipelineEntity toPipelineEntityWithVersion(String accountId, String orgId, String projectId, String pipelineId,
      String pipelineName, String yaml, String ifMatch, Boolean isDraft, String pipelineVersion, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled, Boolean allowDynamicExecutions) {
    PipelineEntity pipelineEntity = toPipelineEntity(accountId, orgId, projectId, pipelineId, pipelineName, yaml,
        isDraft, pipelineVersion, scopeInfo, isParentIdQueryingEnabled, allowDynamicExecutions);
    PipelineEntity withVersion = pipelineEntity.withVersion(isNumeric(ifMatch) ? parseLong(ifMatch) : null);
    if (!Objects.equals(pipelineId, withVersion.getIdentifier())) {
      throw new InvalidRequestException(String.format(
          "Expected Pipeline identifier in YAML to be [%s], but was [%s]", pipelineId, pipelineEntity.getIdentifier()));
    }
    return withVersion;
  }

  public PMSPipelineSummaryResponseDTO preparePipelineSummary(PipelineEntity pipelineEntity, Boolean getMetadataOnly) {
    if (Boolean.TRUE.equals(getMetadataOnly)) {
      return preparePipelineSummary(pipelineEntity, getEntityGitDetailsForMetadataResponse(pipelineEntity));
    }
    return preparePipelineSummary(pipelineEntity, getEntityGitDetails(pipelineEntity));
  }

  public PMSPipelineSummaryResponseDTO preparePipelineSummaryForListView(
      PipelineEntity pipelineEntity, Map<String, PipelineMetadataV2> pipelineMetadataMap) {
    // For List View, getEntityGitDetails(...) method cant be used because for REMOTE pipelines. That is because
    // GitAwareContextHelper.getEntityGitDetailsFromScmGitMetadata() cannot be used, because there won't be any
    // SCM Context set in the List call.
    EntityGitDetails entityGitDetails = getEntityGitDetailsForMetadataResponse(pipelineEntity);
    PMSPipelineSummaryResponseDTO pmsPipelineSummaryResponseDTO =
        preparePipelineSummary(pipelineEntity, entityGitDetails);
    List<RecentExecutionInfoDTO> recentExecutionsInfo =
        prepareRecentExecutionsInfo(pipelineMetadataMap.get(pipelineEntity.getIdentifier()));
    pmsPipelineSummaryResponseDTO.setRecentExecutionsInfo(recentExecutionsInfo);
    return pmsPipelineSummaryResponseDTO;
  }

  private PMSPipelineSummaryResponseDTO preparePipelineSummary(
      PipelineEntity pipelineEntity, EntityGitDetails entityGitDetails) {
    if (pipelineEntity.getIsDraft() == null) {
      pipelineEntity.setIsDraft(false);
    }
    if (pipelineEntity.getEnableDAG() == null) {
      pipelineEntity.setEnableDAG(false);
    }
    if (entityGitDetails != null) {
      entityGitDetails.setRepoUrl(pipelineEntity.getRepoURL());
    }
    return PMSPipelineSummaryResponseDTO.builder()
        .identifier(pipelineEntity.getIdentifier())
        .description(pipelineEntity.getDescription())
        .name(pipelineEntity.getName())
        .tags(TagMapper.convertToMap(pipelineEntity.getTags()))
        .version(pipelineEntity.getVersion())
        .numOfStages(pipelineEntity.getStageCount())
        .executionSummaryInfo(getExecutionSummaryInfoDTO(pipelineEntity))
        .lastUpdatedAt(pipelineEntity.getLastUpdatedAt())
        .createdAt(pipelineEntity.getCreatedAt())
        .modules(pipelineEntity.getFilters().keySet())
        .filters(ModuleInfoMapper.getModuleInfo(pipelineEntity.getFilters()))
        .stageNames(pipelineEntity.getStageNames())
        .storeType(StoreType.getExternalStoreTypeMapping(pipelineEntity.getStoreType()))
        .connectorRef(pipelineEntity.getConnectorRef())
        .gitDetails(entityGitDetails)
        .entityValidityDetails(getEntityValidityDetails(pipelineEntity))
        .isDraft(pipelineEntity.getIsDraft())
        .yamlVersion(pipelineEntity.getHarnessVersion())
        .isInlineHCEntity(StoreType.INLINE_HC.equals(pipelineEntity.getStoreType()))
        .enableDAG(pipelineEntity.getEnableDAG())
        .build();
  }

  public List<RecentExecutionInfoDTO> prepareRecentExecutionsInfo(PipelineMetadataV2 pipelineMetadataV2) {
    if (pipelineMetadataV2 == null) {
      return Collections.emptyList();
    }
    List<RecentExecutionInfo> recentExecutionInfoFromMetadata = pipelineMetadataV2.getRecentExecutionInfoList();
    if (EmptyPredicate.isEmpty(recentExecutionInfoFromMetadata)) {
      return Collections.emptyList();
    }
    return recentExecutionInfoFromMetadata.stream()
        .map(PMSPipelineDtoMapper::prepareRecentExecutionInfo)
        .collect(Collectors.toList());
  }

  public RecentExecutionInfoDTO prepareRecentExecutionInfo(RecentExecutionInfo recentExecutionInfo) {
    ExecutionTriggerInfo triggerInfo = recentExecutionInfo.getExecutionTriggerInfo();
    ExecutorInfoDTO executorInfo = ExecutorInfoDTO.builder()
                                       .triggerType(triggerInfo.getTriggerType())
                                       .username(triggerInfo.getTriggeredBy().getIdentifier())
                                       .email(triggerInfo.getTriggeredBy().getExtraInfoOrDefault("email", null))
                                       .build();
    return RecentExecutionInfoDTO.builder()
        .planExecutionId(recentExecutionInfo.getPlanExecutionId())
        .status(ExecutionStatus.getExecutionStatus(recentExecutionInfo.getStatus()))
        .startTs(recentExecutionInfo.getStartTs())
        .endTs(recentExecutionInfo.getEndTs())
        .executorInfo(executorInfo)
        .parentStageInfo(recentExecutionInfo.getParentStageInfo())
        .runSequence(recentExecutionInfo.getRunSequence())
        .build();
  }

  public PipelineEntity toPipelineEntity(String accountId, String yaml) {
    try {
      BasicPipeline basicPipeline = YamlUtils.read(yaml, BasicPipeline.class);

      validatePipelineData(basicPipeline.getIdentifier(), basicPipeline.getName(), false);

      return PipelineEntity.builder()
          .yaml(yaml)
          .accountId(accountId)
          .orgIdentifier(basicPipeline.getOrgIdentifier())
          .projectIdentifier(basicPipeline.getProjectIdentifier())
          .name(basicPipeline.getName())
          .identifier(basicPipeline.getIdentifier())
          .description(basicPipeline.getDescription())
          .tags(TagMapper.convertToList(basicPipeline.getTags()))
          .allowStageExecutions(basicPipeline.isAllowStageExecutions())
          .build();
    } catch (IOException e) {
      if (YamlUtils.isYamlSizeLimitExceeded(e)) {
        throw new InvalidRequestException(PipelineEntityUtils.PIPELINE_YAML_SIZE_LIMIT_EXCEEDED_MESSAGE, e);
      }
      throw new InvalidRequestException("Cannot create pipeline entity due to " + e.getMessage());
    }
  }

  private ExecutionSummaryInfoDTO getExecutionSummaryInfoDTO(PipelineEntity pipelineEntity) {
    return ExecutionSummaryInfoDTO.builder()
        .deployments(getNumberOfDeployments(pipelineEntity))
        .numOfErrors(getNumberOfErrorsLast7Days(pipelineEntity))
        .lastExecutionStatus(pipelineEntity.getExecutionSummaryInfo() != null
                ? pipelineEntity.getExecutionSummaryInfo().getLastExecutionStatus()
                : null)
        .lastExecutionTs(pipelineEntity.getExecutionSummaryInfo() != null
                ? pipelineEntity.getExecutionSummaryInfo().getLastExecutionTs()
                : null)
        .lastExecutionId(pipelineEntity.getExecutionSummaryInfo() != null
                ? pipelineEntity.getExecutionSummaryInfo().getLastExecutionId()
                : null)
        .build();
  }

  private List<Integer> getNumberOfErrorsLast7Days(PipelineEntity pipeline) {
    if (pipeline.getExecutionSummaryInfo() == null || pipeline.getExecutionSummaryInfo().getNumOfErrors() == null) {
      return new ArrayList<>();
    }
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.DAY_OF_YEAR, -7);
    SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
    List<Integer> errors = new ArrayList<>();
    for (int i = 0; i < 7; i++) {
      cal.add(Calendar.DAY_OF_YEAR, 1);
      errors.add(pipeline.getExecutionSummaryInfo().getNumOfErrors().getOrDefault(sdf.format(cal.getTime()), 0));
    }
    return errors;
  }

  private List<Integer> getNumberOfDeployments(PipelineEntity pipeline) {
    if (pipeline.getExecutionSummaryInfo() == null || pipeline.getExecutionSummaryInfo().getDeployments() == null) {
      return new ArrayList<>();
    }
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.DAY_OF_YEAR, -7);
    SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
    List<Integer> numberOfDeployments = new ArrayList<>();
    for (int i = 0; i < 7; i++) {
      cal.add(Calendar.DAY_OF_YEAR, 1);
      numberOfDeployments.add(
          pipeline.getExecutionSummaryInfo().getDeployments().getOrDefault(sdf.format(cal.getTime()), 0));
    }
    return numberOfDeployments;
  }

  public EntityDetail toEntityDetail(PipelineEntity entity) {
    return EntityDetail.builder()
        .name(entity.getName())
        .type(EntityType.PIPELINES)
        .entityRef(IdentifierRef.builder()
                       .accountIdentifier(entity.getAccountIdentifier())
                       .orgIdentifier(entity.getOrgIdentifier())
                       .projectIdentifier(entity.getProjectIdentifier())
                       .scope(ScopeHelper.getScope(
                           entity.getAccountIdentifier(), entity.getOrgIdentifier(), entity.getProjectIdentifier()))
                       .identifier(entity.getIdentifier())
                       .build())
        .build();
  }

  public boolean parseLoadFromCacheHeaderParam(String loadFromCache) {
    if (isEmpty(loadFromCache)) {
      return false;
    } else {
      return BOOLEAN_TRUE_VALUE.equalsIgnoreCase(loadFromCache);
    }
  }

  public PipelineValidationResponseDTO buildPipelineValidationResponseDTO(PipelineValidationEvent event) {
    return PipelineValidationResponseDTO.builder()
        .status(event.getStatus().name())
        .policyEval(event.getResult().getGovernanceMetadata())
        .startTs(event.getStartTs())
        .endTs(event.getEndTs())
        .templateValidationResponse(event.getResult().getTemplateValidationResponse())
        .validateTemplateReconcileResponseDTO(event.getResult().getValidateTemplateReconcileResponseDTO())
        .build();
  }

  public CacheResponseMetadataDTO getCacheResponseFromGitContext() {
    CacheResponse cacheResponse = GitAwareContextHelper.getCacheResponseFromScmGitMetadata();
    if (cacheResponse != null) {
      return CacheResponseMetadataDTO.builder()
          .cacheState(cacheResponse.getCacheState())
          .ttlLeft(cacheResponse.getTtlLeft())
          .lastUpdatedAt(cacheResponse.getLastUpdatedAt())
          .isSyncEnabled(cacheResponse.isSyncEnabled())
          .build();
    }
    return null;
  }

  private void validatePipelineData(String identifier, String name, boolean skipNameValidation) {
    if (isEmpty(identifier)) {
      throw new InvalidRequestException("Pipeline identifier cannot be empty");
    }
    if (NGExpressionUtils.matchesInputSetPattern(identifier)) {
      throw new InvalidRequestException("Pipeline identifier cannot be runtime input");
    }
    if (!EntityIdentifierValidator.IDENTIFIER_PATTERN.matcher(identifier).matches()) {
      throw new InvalidRequestException(
          "Pipeline Identifier must be up to 128 characters, start with a letter, and contain only alphanumeric "
          + "characters, underscores (_), or dollar signs ($). It cannot start with a number or $.");
    }
    if (!skipNameValidation && isEmpty(name)) {
      throw new InvalidRequestException("Pipeline name cannot be empty");
    }
  }
}
