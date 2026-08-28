/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service.helper;

import static io.harness.NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS;
import static io.harness.NGResourceFilterConstants.EXACT_MATCH_REGEX;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.pms.instrumentaion.constants.PipelineInstrumentationConstants.IS_GITX;
import static io.harness.telemetry.Destination.AMPLITUDE;

import static com.google.common.base.Strings.nullToEmpty;
import static java.lang.String.format;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.ModuleType;
import io.harness.NGResourceFilterConstants;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.Scope;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.HarnessStringUtils;
import io.harness.engine.governance.PolicyEvaluationFailureException;
import io.harness.eraro.ErrorCode;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.DuplicateFileImportException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.exception.ngexception.InvalidFieldsDTO;
import io.harness.exception.ngexception.NGTemplateException;
import io.harness.exception.ngexception.beans.yamlschema.YamlSchemaErrorDTO;
import io.harness.exception.ngexception.beans.yamlschema.YamlSchemaErrorWrapperDTO;
import io.harness.filter.FilterType;
import io.harness.filter.dto.FilterDTO;
import io.harness.filter.service.FilterService;
import io.harness.gitaware.dto.GitContextRequestParams;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitaware.helper.GitAwareEntityHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.helpers.GitContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.governance.GovernanceMetadata;
import io.harness.governance.PolicySetMetadata;
import io.harness.licensing.enforcement.client.FlexEnforcementClient;
import io.harness.licensing.enforcement.client.FlexEnforcementContextUtils;
import io.harness.licensing.enforcement.client.FlexEnforcementException;
import io.harness.licensing.enforcement.client.model.DegradationLevel;
import io.harness.licensing.enforcement.client.model.EnforcementScope;
import io.harness.licensing.enforcement.client.model.FlexEnforcementRequest;
import io.harness.ng.core.common.beans.FilterWithOperator;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.common.beans.NGTag.NGTagKeys;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.ngtriggers.beans.entity.GitRepoDetails;
import io.harness.pms.filter.creation.FilterCreatorMergeServiceResponse;
import io.harness.pms.filter.creation.service.FilterCreatorMergeService;
import io.harness.pms.filter.utils.ModuleInfoFilterUtils;
import io.harness.pms.gitsync.PmsGitSyncBranchContextGuard;
import io.harness.pms.instrumentaion.PipelineInstrumentationUtils;
import io.harness.pms.instrumentaion.constants.PipelineInstrumentationConstants;
import io.harness.pms.pipeline.MoveConfigOperationDTO;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.PipelineEntity.PipelineEntityKeys;
import io.harness.pms.pipeline.PipelineEntityUtils;
import io.harness.pms.pipeline.PipelineFilterPropertiesDto;
import io.harness.pms.pipeline.PipelineImportRequestDTO;
import io.harness.pms.pipeline.PipelineMetadataV2.PipelineMetadataV2Keys;
import io.harness.pms.pipeline.governance.service.PipelineGovernanceService;
import io.harness.pms.pipeline.references.PipelineSetupUsageCreationHelper;
import io.harness.pms.pipeline.references.filter.FilterCreationGitMetadata;
import io.harness.pms.pipeline.references.filter.FilterCreationParams;
import io.harness.pms.pipeline.service.enforcement.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.service.intfc.PMSPipelineService;
import io.harness.pms.pipeline.service.response.PipelineCRUDErrorResponse;
import io.harness.pms.pipeline.validation.PipelineValidationResponse;
import io.harness.pms.pipeline.validation.service.intfc.PipelineValidationService;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YAMLMetadataFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.pms.yaml.preprocess.RuntimeInputIdValidatorV1;
import io.harness.pms.yaml.preprocess.YamlPreProcessor;
import io.harness.pms.yaml.preprocess.YamlPreProcessorFactory;
import io.harness.repositories.pipeline.PMSPipelineRepository;
import io.harness.serializer.JsonUtils;
import io.harness.telemetry.TelemetryReporter;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.ScopeResolutionHelper;
import io.harness.yaml.validator.InvalidYamlException;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.bson.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_PIPELINE, HarnessModuleComponent.CDS_TEMPLATE_LIBRARY})
@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PIPELINE)
public class PMSPipelineServiceHelper {
  @Inject private final FilterService filterService;
  @Inject private final FilterCreatorMergeService filterCreatorMergeService;
  @Inject private final PipelineValidationService pipelineValidationService;
  @Inject private final PipelineGovernanceService pipelineGovernanceService;
  @Inject private final PMSPipelineTemplateHelper pipelineTemplateHelper;
  @Inject private final PmsFeatureFlagService pmsFeatureFlagService;
  @Inject private final TelemetryReporter telemetryReporter;
  @Inject private final GitAwareEntityHelper gitAwareEntityHelper;
  @Inject private final PMSPipelineRepository pmsPipelineRepository;
  @Inject private final PipelineSetupUsageCreationHelper pipelineSetupUsageCreationHelper;
  @Inject private final PMSPipelineService pmsPipelineService;
  @Inject @Named("PipelineExecutorService") ExecutorService executorService;
  @Inject private final YamlPreProcessorFactory yamlPreProcessorFactory;
  @Inject private final ScopeResolutionHelper scopeResolutionHelper;
  @Inject private final PipelineEntityUtils pipelineEntityUtils;
  @Inject private FlexEnforcementClient flexEnforcementClient;

  public static String PIPELINE_SAVE = "pipeline_save";
  public static String PIPELINE_SAVE_ACTION_TYPE = "action";
  public static String PIPELINE_NAME = "pipelineName";
  public static String ACCOUNT_ID = "accountId";
  public static String ORG_ID = "orgId";
  public static String PROJECT_ID = "projectId";
  public static String PIPELINE_ID = "pipelineId";
  public static String PARENT_UNIQUE_ID = "parentUniqueId";
  public static String TEMPLATE_REF_PIPELINE = "template_ref_by_pipeline";
  public static String TEMPLATE_ID = "templateIdentifier";
  public static String MODULE_NAME = "moduleName";

  public static void validatePresenceOfRequiredFields(
      PipelineEntity pipelineEntity, boolean isParentIdQueryingEnabled) {
    HashMap<String, String> requiredFieldMap = new HashMap<>();
    requiredFieldMap.put(ACCOUNT_ID, pipelineEntity.getAccountId());
    requiredFieldMap.put(PARENT_UNIQUE_ID, pipelineEntity.getParentUniqueId());
    requiredFieldMap.put(PIPELINE_ID, pipelineEntity.getIdentifier());

    requiredFieldMap.forEach((requiredField, value) -> {
      if (EmptyPredicate.isEmpty(value)) {
        throw new InvalidRequestException(String.format("Required field [%s] is either null or empty.", requiredField));
      }
    });
  }

  public static Criteria getPipelineEqualityCriteria(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, boolean deleted, Long version) {
    Criteria criteria = Criteria.where(PipelineEntityKeys.accountId)
                            .is(accountId)
                            .and(PipelineEntityKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(PipelineEntityKeys.projectIdentifier)
                            .is(projectIdentifier)
                            .and(PipelineEntityKeys.identifier)
                            .is(pipelineIdentifier)
                            .and(PipelineEntityKeys.deleted)
                            .is(deleted);

    if (version != null) {
      criteria.and(PipelineEntityKeys.version).is(version);
    }

    return criteria;
  }

  public static Criteria getPipelineEqualityCriteria(
      String accountId, String parentUniqueId, String pipelineIdentifier, boolean deleted, Long version) {
    Criteria criteria = Criteria.where(PipelineEntityKeys.accountId)
                            .is(accountId)
                            .and(PipelineEntityKeys.parentUniqueId)
                            .is(parentUniqueId)
                            .and(PipelineEntityKeys.identifier)
                            .is(pipelineIdentifier)
                            .and(PipelineEntityKeys.deleted)
                            .is(deleted);

    if (version != null) {
      criteria.and(PipelineEntityKeys.version).is(version);
    }

    return criteria;
  }

  public String fetchYamlFromRemote(
      boolean applyRepoAllowListFilter, PipelineEntity pipeline, GitRepoDetails gitRepoDetails) {
    ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(pipeline.getAccountId(), pipeline.getParentUniqueId());
    Scope scope = scopeInfo != null
        ? Scope.of(scopeInfo)
        : Scope.of(pipeline.getAccountId(), pipeline.getOrgIdentifier(), pipeline.getProjectIdentifier());
    GitContextRequestParams gitContextRequestParams = GitContextRequestParams.builder()
                                                          .branchName(gitRepoDetails.getBranch())
                                                          .connectorRef(pipeline.getConnectorRef())
                                                          .filePath(pipeline.getFilePath())
                                                          .repoName(gitRepoDetails.getRepoName())
                                                          .applyRepoAllowListFilter(applyRepoAllowListFilter)
                                                          .build();
    return gitAwareEntityHelper.fetchYAMLFromRemote(scope, gitContextRequestParams, Collections.emptyMap());
  }

  public static Criteria buildCriteriaForRepoListing(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    Criteria criteria = new Criteria();
    criteria.and(PipelineEntityKeys.accountId).is(accountIdentifier);
    if (scopeInfo != null) {
      criteria.and(PipelineEntityKeys.parentUniqueId).is(scopeInfo.getUniqueId());
    } else {
      criteria.and(PipelineEntityKeys.orgIdentifier).is(orgIdentifier);
      criteria.and(PipelineEntityKeys.projectIdentifier).is(projectIdentifier);
    }
    return criteria;
  }

  public PipelineEntity updatePipelineInfo(PipelineEntity pipelineEntity, String pipelineVersion) throws IOException {
    return updatePipelineInfo(pipelineEntity, pipelineVersion, null, false);
  }

  public PipelineEntity updatePipelineInfo(PipelineEntity pipelineEntity, String pipelineVersion, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled) throws IOException {
    switch (pipelineVersion) {
      case HarnessYamlVersion.V1:
      case HarnessYamlVersion.V0:
        return updatePipelineInfoInternal(pipelineEntity, scopeInfo, isParentIdQueryingEnabled);
      default:
        throw new IllegalStateException("version not supported");
    }
  }

  public void populateFilterUsingIdentifier(List<Criteria> criteriaList, Criteria criteria, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, @NotNull String filterIdentifier, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled) {
    FilterDTO pipelineFilterDTO = scopeInfo != null
        ? filterService.get(scopeInfo, filterIdentifier, FilterType.PIPELINESETUP)
        : filterService.get(
              accountIdentifier, orgIdentifier, projectIdentifier, filterIdentifier, FilterType.PIPELINESETUP);
    if (pipelineFilterDTO == null) {
      throw new InvalidRequestException("Could not find a pipeline filter with the identifier ");
    } else {
      populateFilter(criteriaList, criteria, (PipelineFilterPropertiesDto) pipelineFilterDTO.getFilterProperties());
    }
  }

  public static void populateFilter(
      List<Criteria> criteriaList, Criteria criteria, @NotNull PipelineFilterPropertiesDto pipelineFilter) {
    if (EmptyPredicate.isNotEmpty(pipelineFilter.getName())) {
      String regex = pipelineFilter.getName();
      Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
      criteria.and(PipelineEntityKeys.name).regex(pattern);
    }
    if (EmptyPredicate.isNotEmpty(pipelineFilter.getDescription())) {
      String regex = escapeRegexSpecialCharacters(pipelineFilter.getDescription());
      Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
      criteria.and(PipelineEntityKeys.description).regex(pattern);
    }
    if (EmptyPredicate.isNotEmpty(pipelineFilter.getPipelineTags())) {
      addPipelineTagsCriteria(criteriaList, pipelineFilter.getPipelineTags());
    }
    if (EmptyPredicate.isNotEmpty(pipelineFilter.getPipelineIdentifiers())) {
      criteria.and(PipelineEntityKeys.identifier).in(pipelineFilter.getPipelineIdentifiers());
    }
    if (pipelineFilter.getModuleProperties() != null) {
      ModuleInfoFilterUtils.processNode(
          JsonUtils.readTree(pipelineFilter.getModuleProperties().toJson()), "filters", criteria);
    }
    if (EmptyPredicate.isNotEmpty(pipelineFilter.getRepoName())) {
      criteria.and(PipelineEntityKeys.repo).is(pipelineFilter.getRepoName());
    }
  }

  public static void addPipelineTagsCriteria(List<Criteria> criteriaList, List<NGTag> pipelineTags) {
    Criteria tagsCriteria = TagMapper.addTagFiltering(pipelineTags, PlanExecutionSummaryKeys.tags, "Pipeline");
    criteriaList.add(tagsCriteria);
  }

  public static void addPipelineTagsCriteriaV2(List<Criteria> criteriaList, FilterWithOperator<NGTag> pipelineTagsV2) {
    Criteria tagsCriteria = TagMapper.addTagFilteringV2(pipelineTagsV2, PlanExecutionSummaryKeys.tags, "Pipeline");
    criteriaList.add(tagsCriteria);
  }

  public void resolveTemplatesAndValidatePipelineEntity(PipelineEntity pipelineEntity, boolean loadFromCache,
      boolean shouldIgnoreOpaOnSaveCheck, ScopeInfo scopeInfo, boolean isParentUniqueIdQueryingEnabled) {
    long start = System.currentTimeMillis();
    GovernanceMetadata governanceMetadata = resolveTemplatesAndValidatePipeline(
        pipelineEntity, false, loadFromCache, scopeInfo, isParentUniqueIdQueryingEnabled, shouldIgnoreOpaOnSaveCheck);
    log.info("[PMS_PipelineService] validating pipeline took {}ms for projectId {}, orgId {}, accountId {}",
        System.currentTimeMillis() - start, pipelineEntity.getProjectIdentifier(), pipelineEntity.getOrgIdentifier(),
        pipelineEntity.getAccountIdentifier());
    if (governanceMetadata.getDeny()) {
      List<String> denyingRuleSetIds = governanceMetadata.getDetailsList()
                                           .stream()
                                           .filter(PolicySetMetadata::getDeny)
                                           .map(PolicySetMetadata::getIdentifier)
                                           .collect(Collectors.toList());
      throw new PolicyEvaluationFailureException(
          "Pipeline does not follow the Policies in these Policy Sets: " + denyingRuleSetIds.toString(),
          governanceMetadata, pipelineEntity.getYaml());
    }
  }

  public PipelineEntity updatePipelineFilters(PipelineEntity pipelineToUpdate, String uuid, Integer yamlHash) {
    return pmsPipelineRepository.updatePipelineFilters(pipelineToUpdate, uuid, yamlHash);
  }

  @VisibleForTesting
  static void checkAndThrowMismatchInImportedPipelineMetadataInternal(String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, PipelineImportRequestDTO pipelineImportRequest, String importedPipeline) {
    YamlField pipelineYamlField;
    try {
      pipelineYamlField = YamlUtils.readTree(importedPipeline);
    } catch (IOException e) {
      String errorMessage = PipelineCRUDErrorResponse.errorMessageForNotAYAMLFile(
          GitAwareContextHelper.getBranchInRequest(), GitAwareContextHelper.getFilepathInRequest());
      throw PMSPipelineServiceHelper.buildInvalidYamlException(errorMessage, importedPipeline);
    }
    YamlField pipelineInnerField = pipelineYamlField.getNode().getField(YAMLFieldNameConstants.PIPELINE);
    if (pipelineInnerField == null) {
      String errorMessage = PipelineCRUDErrorResponse.errorMessageForNotAPipelineYAML(
          GitAwareContextHelper.getBranchInRequest(), GitAwareContextHelper.getFilepathInRequest());
      throw PMSPipelineServiceHelper.buildInvalidYamlException(errorMessage, importedPipeline);
    }

    Map<String, String> changedFields = new HashMap<>();

    String identifierFromGit = pipelineInnerField.getNode().getIdentifier();
    if (!pipelineIdentifier.equals(identifierFromGit)) {
      changedFields.put(YAMLMetadataFieldNameConstants.IDENTIFIER, identifierFromGit);
    }

    String nameFromGit = pipelineInnerField.getNode().getName();
    if (!pipelineImportRequest.getPipelineName().equals(nameFromGit)) {
      changedFields.put(YAMLMetadataFieldNameConstants.NAME, nameFromGit);
    }

    String orgIdentifierFromGit = pipelineInnerField.getNode().getStringValue(YAMLFieldNameConstants.ORG_IDENTIFIER);
    if (!orgIdentifier.equals(orgIdentifierFromGit)) {
      changedFields.put(YAMLMetadataFieldNameConstants.ORG_IDENTIFIER, orgIdentifierFromGit);
    }

    String projectIdentifierFromGit =
        pipelineInnerField.getNode().getStringValue(YAMLFieldNameConstants.PROJECT_IDENTIFIER);
    if (!projectIdentifier.equals(projectIdentifierFromGit)) {
      changedFields.put(YAMLMetadataFieldNameConstants.PROJECT_IDENTIFIER, projectIdentifierFromGit);
    }

    if (!changedFields.isEmpty()) {
      InvalidFieldsDTO invalidFields = InvalidFieldsDTO.builder().expectedValues(changedFields).build();
      throw new InvalidRequestException(
          "Requested metadata params do not match the values found in the YAML on Git for these fields: "
              + changedFields.keySet(),
          invalidFields);
    }
  }

  public GovernanceMetadata resolveTemplatesAndValidatePipeline(PipelineEntity pipelineEntity,
      boolean throwExceptionIfGovernanceRulesFails, boolean loadFromCache, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled, boolean shouldIgnoreOpaOnSaveCheck) {
    try {
      GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();
      if (HarnessYamlVersion.isV1(pipelineEntity.getHarnessVersion())) {
        // Validate that entities with runtime inputs have user-provided ids.
        // Must run on original YAML before auto-ID generation so we can detect missing ids.
        RuntimeInputIdValidatorV1.validateIdsForEntitiesWithRuntimeInputs(pipelineEntity.getYaml());

        // preprocessedYaml with ids is needed for template resolution
        String yaml = preProcessPipelineYaml(pipelineEntity.getYaml(), false);
        // withYaml() will return a new object of pipelineEntity and will not modify yaml in existing pipelineEntity
        // so while saving/updating, yaml will be saved without preprocessing only.
        pipelineEntity = pipelineEntity.withYaml(yaml);
      }
      if (gitEntityInfo != null && gitEntityInfo.isNewBranch()) {
        GitSyncBranchContext gitSyncBranchContext =
            GitSyncBranchContext.builder()
                .gitBranchInfo(GitEntityInfo.builder()
                                   .branch(gitEntityInfo.getBaseBranch())
                                   .connectorRef(gitEntityInfo.getConnectorRef())
                                   .repoName(gitEntityInfo.getRepoName())
                                   .yamlGitConfigId(gitEntityInfo.getYamlGitConfigId())
                                   .build())
                .build();
        try (PmsGitSyncBranchContextGuard ignored = new PmsGitSyncBranchContextGuard(gitSyncBranchContext, true)) {
          return resolveTemplatesAndValidatePipelineYaml(pipelineEntity, throwExceptionIfGovernanceRulesFails,
              loadFromCache, scopeInfo, isParentIdQueryingEnabled, false, gitEntityInfo);
        }
      } else {
        return resolveTemplatesAndValidatePipelineYaml(pipelineEntity, throwExceptionIfGovernanceRulesFails,
            loadFromCache, scopeInfo, isParentIdQueryingEnabled, shouldIgnoreOpaOnSaveCheck, null);
      }
    } catch (io.harness.yaml.validator.InvalidYamlException ex) {
      ex.setYaml(pipelineEntity.getData());
      throw ex;
    } catch (NGTemplateException ex) {
      throw ex;
    } catch (PolicyEvaluationFailureException ex) {
      throw ex;
    } catch (Exception ex) {
      if (YamlUtils.isYamlSizeLimitExceeded(ex)) {
        throw new InvalidRequestException(PipelineEntityUtils.PIPELINE_YAML_SIZE_LIMIT_EXCEEDED_MESSAGE, ex);
      }
      YamlSchemaErrorWrapperDTO errorWrapperDTO =
          YamlSchemaErrorWrapperDTO.builder()
              .schemaErrors(Collections.singletonList(
                  YamlSchemaErrorDTO.builder().message(ex.getMessage()).fqn("$.pipeline").build()))
              .build();
      throw new io.harness.yaml.validator.InvalidYamlException(
          HarnessStringUtils.emptyIfNull(ex.getMessage()), ex, errorWrapperDTO, pipelineEntity.getData());
    }
  }

  GovernanceMetadata resolveTemplatesAndValidatePipelineYaml(PipelineEntity pipelineEntity,
      boolean throwExceptionIfGovernanceRulesFails, boolean loadFromCache, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled, boolean shouldIgnoreOpaOnSaveCheck, GitEntityInfo originalGitEntityInfo) {
    boolean getMergedTemplateWithTemplateReferences =
        pmsFeatureFlagService.isEnabled(pipelineEntity.getAccountId(), FeatureName.OPA_PIPELINE_GOVERNANCE);
    // Apply all the templateRefs(if any) then check for schema validation.
    TemplateMergeResponseDTO templateMergeResponseDTO = scopeInfo != null
        ? pipelineTemplateHelper.resolveTemplateRefsInPipeline(
              pipelineEntity, scopeInfo, getMergedTemplateWithTemplateReferences, loadFromCache)
        : pipelineTemplateHelper.resolveTemplateRefsInPipeline(
              pipelineEntity, getMergedTemplateWithTemplateReferences, loadFromCache);
    // Add Template Module Info temporarily to Pipeline Entity
    pipelineEntity.setTemplateModules(pipelineTemplateHelper.getTemplatesModuleInfo(templateMergeResponseDTO));
    // If this is a new-branch save and the fix is enabled, OPA must see the actual new branch + storeType=REMOTE.
    // Template resolution above used baseBranch context (new branch doesn't exist in git yet).
    if (originalGitEntityInfo != null
        && !pmsFeatureFlagService.isEnabled(
            pipelineEntity.getAccountId(), FeatureName.PIPE_DISABLE_OPA_GITCONFIG_NEW_BRANCH_FIX)) {
      GitSyncBranchContext newBranchContext =
          GitSyncBranchContext.builder()
              .gitBranchInfo(GitEntityInfo.builder()
                                 .branch(originalGitEntityInfo.getBranch())
                                 .isNewBranch(true)
                                 .baseBranch(originalGitEntityInfo.getBaseBranch())
                                 .connectorRef(originalGitEntityInfo.getConnectorRef())
                                 .repoName(originalGitEntityInfo.getRepoName())
                                 .yamlGitConfigId(originalGitEntityInfo.getYamlGitConfigId())
                                 .storeType(originalGitEntityInfo.getStoreType())
                                 .build())
              .build();
      try (PmsGitSyncBranchContextGuard ignored = new PmsGitSyncBranchContextGuard(newBranchContext, true)) {
        return validateYaml(pipelineEntity, templateMergeResponseDTO, throwExceptionIfGovernanceRulesFails, scopeInfo,
            isParentIdQueryingEnabled, shouldIgnoreOpaOnSaveCheck)
            .getGovernanceMetadata();
      }
    }
    return validateYaml(pipelineEntity, templateMergeResponseDTO, throwExceptionIfGovernanceRulesFails, scopeInfo,
        isParentIdQueryingEnabled, shouldIgnoreOpaOnSaveCheck)
        .getGovernanceMetadata();
  }

  PipelineValidationResponse validateYaml(PipelineEntity pipelineEntity,
      TemplateMergeResponseDTO templateMergeResponseDTO, boolean throwExceptionIfGovernanceRulesFails,
      ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled, boolean shouldIgnoreOpaOnSaveCheck) {
    String accountId = pipelineEntity.getAccountId();
    String orgIdentifier = scopeInfo.getOrgIdentifier();
    String projectIdentifier = scopeInfo.getProjectIdentifier();
    String resolveTemplateRefsInPipeline = templateMergeResponseDTO.getMergedPipelineYaml();

    if (shouldIgnoreOpaOnSaveCheck) {
      return PipelineValidationResponse.builder()
          .governanceMetadata(GovernanceMetadata.newBuilder().setDeny(false).build())
          .build();
    }

    if (throwExceptionIfGovernanceRulesFails) {
      return pipelineValidationService.validateYamlAndGovernanceRules(accountId, orgIdentifier, projectIdentifier,
          resolveTemplateRefsInPipeline, templateMergeResponseDTO.getMergedPipelineYamlWithTemplateRef(),
          pipelineEntity);
    }
    return pipelineValidationService.validateYamlAndGetGovernanceMetadata(accountId, orgIdentifier, projectIdentifier,
        resolveTemplateRefsInPipeline, templateMergeResponseDTO.getMergedPipelineYamlWithTemplateRef(), pipelineEntity);
  }

  public Criteria formCriteria(String accountId, String orgId, String projectId, String filterIdentifier,
      PipelineFilterPropertiesDto filterProperties, boolean deleted, String module, String searchTerm,
      ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    Criteria criteria = new Criteria();
    if (isNotEmpty(accountId)) {
      criteria.and(PipelineEntityKeys.accountId).is(accountId);
    }

    if (scopeInfo != null && scopeInfo.getScopeType() == ScopeLevel.PROJECT) {
      criteria.and(PipelineEntityKeys.parentUniqueId).is(scopeInfo.getUniqueId());
    }

    criteria.and(PipelineEntityKeys.deleted).is(deleted);
    List<Criteria> criteriaList = new ArrayList<>();
    if (EmptyPredicate.isNotEmpty(filterIdentifier) && filterProperties != null) {
      throw new InvalidRequestException("Can not apply both filter properties and saved filter together");
    } else if (EmptyPredicate.isNotEmpty(filterIdentifier) && filterProperties == null) {
      populateFilterUsingIdentifier(
          criteriaList, criteria, accountId, orgId, projectId, filterIdentifier, scopeInfo, isParentIdQueryingEnabled);
    } else if (EmptyPredicate.isEmpty(filterIdentifier) && filterProperties != null) {
      populateFilter(criteriaList, criteria, filterProperties);
    }

    Criteria moduleCriteria = new Criteria();
    if (EmptyPredicate.isNotEmpty(module)) {
      // Check if the provided module type is valid
      checkThatTheModuleExists(module);
      // Add approval stage criteria to check for the pipelines containing the given module and the approval stage.
      Criteria approvalStageCriteria =
          Criteria.where(format("%s.%s.stageTypes", PipelineEntityKeys.filters, ModuleType.PMS.name().toLowerCase()))
              .exists(true);
      for (ModuleType moduleType : ModuleType.values()) {
        if (moduleType.isInternal()) {
          continue;
        }
        // This query ensures that only pipelines containing approval stage are visible.
        approvalStageCriteria.and(format("%s.%s", PipelineEntityKeys.filters, moduleType.name().toLowerCase()))
            .exists(false);
      }
      // Check for pipeline with no filters also - empty pipeline or pipelines with only approval stage
      // criteria = { "$or": [ { "filters": {} } , { "filters.MODULE": { $exists: true } } ] }
      moduleCriteria.orOperator(where(PipelineEntityKeys.filters).is(new Document()),
          where(format("%s.%s", PipelineEntityKeys.filters, module.toLowerCase())).exists(true), approvalStageCriteria);
    }

    Criteria searchCriteria = new Criteria();
    if (EmptyPredicate.isNotEmpty(searchTerm)) {
      String escapedSearchTerm = escapeRegexSpecialCharacters(searchTerm);
      searchCriteria.orOperator(
          where(PipelineEntityKeys.identifier).regex(escapedSearchTerm, CASE_INSENSITIVE_MONGO_OPTIONS),
          where(PipelineEntityKeys.name).regex(escapedSearchTerm, CASE_INSENSITIVE_MONGO_OPTIONS),
          where(PipelineEntityKeys.tags + "." + NGTagKeys.key).regex(escapedSearchTerm, CASE_INSENSITIVE_MONGO_OPTIONS),
          where(PipelineEntityKeys.tags + "." + NGTagKeys.value)
              .regex(escapedSearchTerm, CASE_INSENSITIVE_MONGO_OPTIONS));
    }

    criteriaList.add(moduleCriteria);
    criteriaList.add(searchCriteria);
    criteria.andOperator(criteriaList.toArray(new Criteria[criteriaList.size()]));

    return criteria;
  }
  public void sendPipelineSaveTelemetryEvent(
      PipelineEntity entity, String actionType, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    String orgId = scopeInfo.getOrgIdentifier();
    String projectId = scopeInfo.getProjectIdentifier();

    executorService.submit(() -> {
      try {
        HashMap<String, Object> properties = new HashMap<>();
        properties.put(PIPELINE_NAME, entity.getName());

        properties.put(ORG_ID, orgId);
        properties.put(PROJECT_ID, projectId);
        properties.put(PIPELINE_SAVE_ACTION_TYPE, actionType);
        properties.put(PipelineInstrumentationConstants.MODULE_NAME,
            pipelineEntityUtils.getModuleNameFromPipelineEntity(entity.getFilters().keySet(), entity.getAccountId()));
        properties.put(
            PipelineInstrumentationConstants.STAGE_TYPES, PipelineInstrumentationUtils.getStageTypes(entity));
        telemetryReporter.sendTrackEvent(PIPELINE_SAVE, null, entity.getAccountId(), properties,
            Collections.singletonMap(AMPLITUDE, true), io.harness.telemetry.Category.GLOBAL);
      } catch (Exception ex) {
        log.error(format("Exception while sending telemetry event for pipeline save. accountId: %s, orgId: %s, "
                          + "projectId: %s, pipelineId: %s",
                      entity.getAccountIdentifier(), orgId, projectId, entity.getIdentifier()),
            ex);
      }
    });
  }

  public void sendTemplatesUsedInPipelinesTelemetryEvent(
      PipelineEntity pipelineEntity, String actionType, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    String accountId = pipelineEntity.getAccountId();
    String orgId = scopeInfo.getOrgIdentifier();
    String projectId = scopeInfo.getProjectIdentifier();
    executorService.submit(() -> {
      try {
        List<EntityDetailProtoDTO> templateReferences = pipelineTemplateHelper.getTemplateReferencesForGivenYaml(
            accountId, orgId, projectId, pipelineEntity.getYaml(), pipelineEntity.getHarnessVersion());
        for (EntityDetailProtoDTO reference : templateReferences) {
          if (reference.hasTemplateRef()) {
            HashMap<String, Object> properties = new HashMap<>();
            properties.put(TEMPLATE_ID, reference.getTemplateRef().getIdentifier().getValue());
            properties.put(PIPELINE_ID, pipelineEntity.getIdentifier());
            properties.put(ACCOUNT_ID, accountId);
            properties.put(ORG_ID, orgId);
            properties.put(PROJECT_ID, projectId);
            properties.put(PIPELINE_SAVE_ACTION_TYPE, actionType);
            properties.put(IS_GITX,
                StringUtils.isNotBlank(reference.getEntityGitMetadata().getBranch())
                    && StringUtils.isNotBlank(reference.getEntityGitMetadata().getRepo()));
            properties.put(MODULE_NAME, "cd");
            telemetryReporter.sendTrackEvent(TEMPLATE_REF_PIPELINE, null, accountId, properties,
                Collections.singletonMap(AMPLITUDE, true), io.harness.telemetry.Category.GLOBAL);
          }
        }
      } catch (Exception ex) {
        log.error(format("Exception while sending telemetry event for template ref by pipeline. accountId: %s, orgId: "
                          + "%s, projectId: %s, pipelineId: %s",
                      accountId, orgId, projectId, pipelineEntity.getIdentifier()),
            ex);
      }
    });
  }

  public static InvalidYamlException buildInvalidYamlException(String errorMessage, String pipelineYaml) {
    YamlSchemaErrorWrapperDTO errorWrapperDTO =
        YamlSchemaErrorWrapperDTO.builder()
            .schemaErrors(
                Collections.singletonList(YamlSchemaErrorDTO.builder().message(errorMessage).fqn("$.pipeline").build()))
            .build();
    return new InvalidYamlException(errorMessage, errorWrapperDTO, pipelineYaml);
  }

  public String importPipelineFromRemote(String accountId, String orgIdentifier, String projectIdentifier,
      boolean applyRepoAllowListFilter, ScopeInfo scopeInfo) {
    Scope scope = scopeInfo != null ? Scope.of(scopeInfo) : Scope.of(accountId, orgIdentifier, projectIdentifier);
    return gitAwareEntityHelper.importFile(scope, applyRepoAllowListFilter);
  }

  public String fetchYAMLFromRemote(String accountId, String orgIdentifier, String projectIdentifier,
      boolean applyRepoAllowListFilter, ScopeInfo scopeInfo) {
    GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    Scope scope = scopeInfo != null ? Scope.of(scopeInfo) : Scope.of(accountId, orgIdentifier, projectIdentifier);
    GitContextRequestParams gitContextRequestParams = GitContextRequestParams.builder()
                                                          .branchName(gitEntityInfo.getBranch())
                                                          .connectorRef(gitEntityInfo.getConnectorRef())
                                                          .filePath(gitEntityInfo.getFilePath())
                                                          .repoName(gitEntityInfo.getRepoName())
                                                          .applyRepoAllowListFilter(applyRepoAllowListFilter)
                                                          .shouldSetupGitXWebhook(false)
                                                          .build();
    return gitAwareEntityHelper.fetchYAMLFromRemote(scope, gitContextRequestParams, Collections.emptyMap());
  }

  public static void checkAndThrowMismatchInImportedPipelineMetadata(String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, PipelineImportRequestDTO pipelineImportRequest, String importedPipeline,
      String pipelineVersion) {
    // TODO (prashant) : Check with the team
    switch (pipelineVersion) {
      case HarnessYamlVersion.V1:
        return;
      default:
        checkAndThrowMismatchInImportedPipelineMetadataInternal(
            orgIdentifier, projectIdentifier, pipelineIdentifier, pipelineImportRequest, importedPipeline);
    }
  }

  public static Pair<String, String> getPipelineNameAndIdentifierFromYaml(String yaml, String version) {
    if (HarnessYamlVersion.isV1(version)) {
      return null;
    }

    return getPipelineNameAndIdentifierFromYamlInternal(yaml);
  }

  static Pair<String, String> getPipelineNameAndIdentifierFromYamlInternal(String yaml) {
    YamlField pipelineYamlField;
    try {
      pipelineYamlField = YamlUtils.readTree(yaml);
    } catch (IOException e) {
      String errorMessage = PipelineCRUDErrorResponse.errorMessageForNotAYAMLFile(
          GitAwareContextHelper.getBranchInRequest(), GitAwareContextHelper.getFilepathInRequest());
      log.warn("Error when reading pipeline YAML to extract name and identifier. {}", errorMessage);
      return null;
    }
    YamlField pipelineInnerField = pipelineYamlField.getNode().getField(YAMLFieldNameConstants.PIPELINE);
    if (pipelineInnerField != null) {
      String identifier = pipelineInnerField.getNode().getIdentifier();
      String name = pipelineInnerField.getNode().getName();
      return ImmutablePair.of(identifier, name);
    }
    return null;
  }

  public String getRepoUrlAndCheckForFileUniqueness(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, Boolean isForceImport, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled) {
    GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    String repoURL = null;
    if (scopeInfo != null) {
      repoURL = gitAwareEntityHelper.getRepoUrl(scopeInfo);
    } else {
      repoURL = gitAwareEntityHelper.getRepoUrl(accountIdentifier, orgIdentifier, projectIdentifier);
    }
    if (Boolean.TRUE.equals(isForceImport)) {
      log.info("Importing YAML forcefully with Pipeline Id: {}, RepoURl: {}, FilePath: {}", pipelineIdentifier, repoURL,
          gitEntityInfo.getFilePath());
    } else if (isAlreadyImported(accountIdentifier, repoURL, gitEntityInfo.getFilePath())) {
      String error = "The Requested YAML with Pipeline Id: " + pipelineIdentifier + ", RepoURl: " + repoURL
          + ", FilePath: " + gitEntityInfo.getFilePath() + " has already been imported.";
      throw new DuplicateFileImportException(error);
    }
    return repoURL;
  }

  private boolean isAlreadyImported(String accountIdentifier, String repoURL, String filePath) {
    Long totalInstancesOfYAML = pmsPipelineRepository.countFileInstances(accountIdentifier, repoURL, filePath);
    return totalInstancesOfYAML > 0;
  }

  private PipelineEntity updatePipelineInfoInternal(
      PipelineEntity pipelineEntity, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) throws IOException {
    // V1 pipeline metadata (name/description/tags) lives outside the YAML, so V1 PATCH flows
    // can legitimately omit the YAML body. Filters/stage info and pipeline references are
    // derived from the YAML; if the YAML didn't change, nothing here needs to be recomputed and
    // the existing derived values in DB should be left untouched. getUpdateOperationsForPatch
    // only writes eligible patch fields, including explicit description/tags clear operations.
    // V0 callers always carry the metadata inside the YAML and therefore never reach here with
    // an empty YAML in normal flows; V0 behavior is preserved.
    if (HarnessYamlVersion.isV1(pipelineEntity.getHarnessVersion()) && isEmpty(pipelineEntity.getYaml())) {
      return pipelineEntity;
    }
    FilterCreatorMergeServiceResponse filtersAndStageCount = filterCreatorMergeService.getPipelineInfo(
        FilterCreationParams.builder()
            .pipelineEntity(HarnessYamlVersion.isV1(pipelineEntity.getHarnessVersion())
                    ? pipelineEntity.withYaml(injectTypeField(pipelineEntity.getYaml()))
                    : pipelineEntity)
            .scopeInfo(scopeInfo)
            .isParentIdQueryingEnabled(isParentIdQueryingEnabled)
            .build());
    PipelineEntity newEntity = pipelineEntity.withStageCount(filtersAndStageCount.getStageCount())
                                   .withStageNames(filtersAndStageCount.getStageNames());
    newEntity.getFilters().clear();
    try {
      if (isNotEmpty(filtersAndStageCount.getFilters())) {
        filtersAndStageCount.getFilters().forEach(
            (key, value)
                -> newEntity.getFilters().put(key, isNotEmpty(value) ? Document.parse(value) : Document.parse("{}")));
      }

      if (isNotEmpty(pipelineEntity.getTemplateModules())) {
        for (String module : pipelineEntity.getTemplateModules()) {
          if (!newEntity.getFilters().containsKey(module)) {
            newEntity.getFilters().put(module, Document.parse("{}"));
          }
        }
      }
    } catch (Exception e) {
      log.error("Unable to parse the Filter value", e);
    }
    return newEntity;
  }

  public Criteria getPipelineMetadataV2Criteria(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String pipelineIdentifier) {
    return Criteria.where(PipelineMetadataV2Keys.accountIdentifier)
        .is(accountIdentifier)
        .and(PipelineMetadataV2Keys.orgIdentifier)
        .is(orgIdentifier)
        .and(PipelineMetadataV2Keys.projectIdentifier)
        .is(projectIdentifier)
        .and(PipelineMetadataV2Keys.identifier)
        .is(pipelineIdentifier);
  }

  public Criteria getPipelineMetadataV2Criteria(
      String accountIdentifier, String parentUniqueId, String pipelineIdentifier) {
    return Criteria.where(PipelineMetadataV2Keys.accountIdentifier)
        .is(accountIdentifier)
        .and(PipelineMetadataV2Keys.parentUniqueId)
        .is(parentUniqueId)
        .and(PipelineMetadataV2Keys.identifier)
        .is(pipelineIdentifier);
  }

  public Update getPipelineUpdateForInlineToRemote(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, MoveConfigOperationDTO moveConfigDTO, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled) {
    Update update = new Update();
    update.set(PipelineEntityKeys.repo, moveConfigDTO.getRepoName());
    update.set(PipelineEntityKeys.storeType, StoreType.REMOTE);
    update.set(PipelineEntityKeys.filePath, moveConfigDTO.getFilePath());
    update.set(PipelineEntityKeys.connectorRef, nullToEmpty(moveConfigDTO.getConnectorRef()));
    update.set(PipelineEntityKeys.lastUpdatedAt, System.currentTimeMillis());
    update.set(PipelineEntityKeys.repoURL, gitAwareEntityHelper.getRepoUrl(scopeInfo));
    return update;
  }

  public Update getPipelineUpdateForRemoteToInline() {
    Update update = new Update();
    update.unset(PipelineEntityKeys.repo);
    update.unset(PipelineEntityKeys.filePath);
    update.unset(PipelineEntityKeys.connectorRef);
    update.unset(PipelineEntityKeys.repoURL);
    update.set(PipelineEntityKeys.storeType, StoreType.INLINE);
    update.set(PipelineEntityKeys.lastUpdatedAt, System.currentTimeMillis());
    return update;
  }

  public void checkThatTheModuleExists(String module) {
    if (isNotEmpty(module)
        && isEmpty(ModuleType.getPublicModules()
                       .stream()
                       .filter(moduleType -> moduleType.name().equalsIgnoreCase(module))
                       .collect(Collectors.toList()))
        // special handling for PMS (internal module) but should be supported for internal API
        && !module.equalsIgnoreCase(ModuleType.PMS.name())) {
      throw NestedExceptionUtils.hintWithExplanationException(format("Invalid module type [%s]", module),
          format("Please select the correct module type %s", ModuleType.getPublicModules()),
          new InvalidRequestException(format("Invalid module type [%s]", module)));
    }
  }

  public void computePipelineReferences(PipelineEntity pipelineEntity, ScopeInfo scopeInfo) {
    computePipelineReferences(pipelineEntity, GitAwareContextHelper.getBranchInSCMGitMetadata(), scopeInfo);
  }

  public void computePipelineReferences(PipelineEntity pipelineEntity, String branch, ScopeInfo scopeInfo) {
    pipelineSetupUsageCreationHelper.submitTask(FilterCreationParams.builder()
                                                    .pipelineEntity(pipelineEntity)
                                                    .scopeInfo(scopeInfo)
                                                    .filterCreationGitMetadata(FilterCreationGitMetadata.builder()
                                                                                   .branch(branch)
                                                                                   .repo(pipelineEntity.getRepo())
                                                                                   .isGitDefaultBranch(true)
                                                                                   .build())
                                                    .isParentIdQueryingEnabled(true)
                                                    .build());
  }

  public void deletePipelineReferences(PipelineEntity pipelineEntity, ScopeInfo scopeInfo) {
    filterCreatorMergeService.deleteSetupReferences(pipelineEntity, scopeInfo);
  }

  public void setCriteriaForPermittedPipelines(
      String accountId, String orgId, String projectId, Criteria criteria, String pipelineIdentifierKey) {
    /*
    If user is having all pipeline view permission, we do not need to check for individual pipeline view permission
     */
    if (!pmsPipelineService.validateViewPermission(accountId, orgId, projectId)) {
      List<String> allPipelineIdentifiers = pmsPipelineService.listAllIdentifiers(criteria);

      List<String> permittedPipelineIdentifiers =
          pmsPipelineService.getPermittedToViewPipelineIdentifiers(accountId, orgId, projectId, allPipelineIdentifiers);

      criteria.and(pipelineIdentifierKey).in(permittedPipelineIdentifiers);
    }
  }

  // TODO this method invokes accessControlClient.checkForAccessOrThrow(permissionChecks) which checks for the resources
  // in the request and If no items in the list have permission (i.e., isPermitted returns false for all items), throws
  // an exception. Check the expected behavior and change it use method setCriteriaForPermittedPipelines() to get all
  // permitted pipelines and set the criteria
  public void setPermittedPipelines(
      String accountId, String orgId, String projectId, Criteria criteria, String pipelineIdentifierKey) {
    List<String> permittedPipelineIdentifiers = getPermittedPipelines(criteria, accountId, orgId, projectId);
    if (permittedPipelineIdentifiers != null) {
      criteria.and(pipelineIdentifierKey).in(permittedPipelineIdentifiers);
    }
  }

  public List<String> getPermittedPipelines(Criteria criteria, String accountId, String orgId, String projectId) {
    /*
    If user is having all pipeline view permission, we do not need to check for individual pipeline view permission
     */
    if (!pmsPipelineService.validateViewPermission(accountId, orgId, projectId)) {
      List<String> allPipelineIdentifiers = pmsPipelineService.listAllIdentifiers(criteria);

      return pmsPipelineService.getPermittedPipelineIdentifier(accountId, orgId, projectId, allPipelineIdentifiers);
    }
    return null;
  }

  public String preProcessPipelineYaml(String yaml, boolean isPreFinal) {
    YamlPreProcessor preProcessor = yamlPreProcessorFactory.getProcessorInstance(HarnessYamlVersion.V1);
    if (preProcessor != null) {
      yaml = YamlUtils.writeYamlString(preProcessor.preProcess(yaml, isPreFinal).getPreprocessedJsonNode());
    }
    return yaml;
  }

  public String injectTypeField(String yaml) {
    YamlPreProcessor preProcessor = yamlPreProcessorFactory.getProcessorInstance(HarnessYamlVersion.V1);
    if (preProcessor != null) {
      yaml = preProcessor.injectTypeField("", YamlUtils.readAsJsonNode(yaml));
    }
    return yaml;
  }

  public Stream<PipelineEntity> fetchAllPipelinesByFilePathAndRepo(
      String accountIdentifier, String filePath, String repoName) {
    Criteria criteria = buildCriteriaWithFilePathAndRepoName(accountIdentifier, filePath, repoName);
    List<String> fieldsToBeExcluded = List.of(PipelineEntityKeys.yaml);

    return pmsPipelineRepository.findAllFromSecondaryDb(criteria, fieldsToBeExcluded);
  }

  public boolean isParentIdQueryingEnabled(String accountId) {
    return true;
  }

  public boolean isParentIdQueryingEnabledForInputSet(String accountId) {
    return true;
  }

  private Criteria buildCriteriaWithFilePathAndRepoName(String accountIdentifier, String filePath, String repoName) {
    Criteria criteria = new Criteria();
    criteria.and(PipelineEntityKeys.accountId).is(accountIdentifier);
    criteria.and(PipelineEntityKeys.filePath).is(filePath);
    criteria.and(PipelineEntityKeys.repo)
        .regex(String.format(EXACT_MATCH_REGEX, repoName), CASE_INSENSITIVE_MONGO_OPTIONS);
    return criteria;
  }

  public static Criteria buildCriteriaWithRepoUrlAndHarnessVersionAndStoreType(
      String repoUrl, String harnessVersion, StoreType storeType) {
    Criteria criteria = new Criteria();
    criteria.and(PipelineEntityKeys.repoURL)
        .regex("^" + repoUrl + "$", NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS);
    criteria.and(PipelineEntityKeys.harnessVersion).is(harnessVersion);
    criteria.and(PipelineEntityKeys.storeType).is(storeType);
    return criteria;
  }

  public ScopeInfo getScopeInfo(String accountId, String orgId, String projectId, ScopeInfo scopeInfo) {
    if (scopeInfo != null && isNotEmpty(scopeInfo.getUniqueId())) {
      return scopeInfo;
    }
    return scopeResolutionHelper.getScopeInfo(accountId, orgId, projectId);
  }

  public static String escapeRegexSpecialCharacters(String input) {
    if (input == null || input.isEmpty()) {
      return input;
    }
    return input.replaceAll("([.*+?{}\\[\\]\\\\()])", "\\\\$1");
  }

  public void validateAndThrowFlexEnforcementRules(String operationId, ScopeInfo scopeInfo) {
    try {
      FlexEnforcementRequest flexEnforcementRequest =
          FlexEnforcementRequest.builder()
              .scope(EnforcementScope.builder()
                         .accountIdentifier(scopeInfo.getAccountIdentifier())
                         .orgIdentifier(scopeInfo.getOrgIdentifier())
                         .projectIdentifier(scopeInfo.getProjectIdentifier())
                         .build())
              .operationId(operationId)
              .principal(FlexEnforcementContextUtils.getPrincipalFromSecurityContext(operationId))
              .build();
      flexEnforcementClient.check(flexEnforcementRequest, response -> {
        if (response.getDegradationLevel() != null && response.getDegradationLevel() == DegradationLevel.D0) {
          throw new FlexEnforcementException(
              "Operation blocked by license enforcement: " + response.getDegradationDetail());
        }
      });
    } catch (FlexEnforcementException ex) {
      throw new AccessDeniedException(ex.getMessage(), ErrorCode.ACCESS_DENIED, WingsException.USER);
    } catch (Exception ex) {
      log.error("Unable to run flex enforcement rules for operation: {}", operationId, ex);
    }
  }
}
