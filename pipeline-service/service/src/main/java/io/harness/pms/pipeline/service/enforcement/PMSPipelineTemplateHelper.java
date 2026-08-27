/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service.enforcement;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.gitcaching.GitCachingConstants.BOOLEAN_FALSE_VALUE;
import static io.harness.pms.pipeline.mappers.dto.PMSPipelineDtoMapper.BOOLEAN_TRUE_VALUE;
import static io.harness.pms.yaml.YAMLFieldNameConstants.NOTIFICATION_RULES_V0;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.GlobalTemplateConstants;
import io.harness.beans.IdentifierRef;
import io.harness.beans.ScopeInfo;
import io.harness.bulkReconciliation.ReferenceEntityType;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.engine.governance.OpaOnSaveStatusErrorDTO;
import io.harness.engine.governance.PolicyEvaluationFailureException;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.exception.HarnessRemoteServiceException;
import io.harness.exception.InternalServerErrorException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.exception.ngexception.ErrorMetadataDTO;
import io.harness.exception.ngexception.NGTemplateException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.helpers.GitContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.gitx.EntityGitDetailsGuard;
import io.harness.metrics.service.api.MetricService;
import io.harness.ng.core.template.RefreshRequestDTO;
import io.harness.ng.core.template.RefreshResponseDTO;
import io.harness.ng.core.template.TemplateApplyRequestDTO;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.ng.core.template.TemplateReferenceRequestDTO;
import io.harness.ng.core.template.TemplateReferenceSummary;
import io.harness.ng.core.template.TemplateResponseDTO;
import io.harness.ng.core.template.refresh.ValidateTemplateInputsResponseDTO;
import io.harness.ng.core.template.refresh.YamlFullRefreshResponseDTO;
import io.harness.pms.events.PmsEventMonitoringConstants;
import io.harness.pms.events.base.PmsMetricContextGuard;
import io.harness.pms.gitsync.PmsGitSyncBranchContextGuard;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlUtils;
import io.harness.pms.yaml.preprocess.YamlPreProcessor;
import io.harness.pms.yaml.preprocess.YamlPreProcessorFactory;
import io.harness.remote.client.NGRestUtils;
import io.harness.remote.client.PipelineRestUtils;
import io.harness.template.remote.TemplateResourceClient;
import io.harness.template.yaml.ref.PipelineTemplateRefInfo;
import io.harness.template.yaml.ref.TemplateRefHelper;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.NestedTemplateRuntimeInputsUtils;
import io.harness.utils.NestedTemplateRuntimeInputsUtils.NestedTemplateRuntimeInputsOptions;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.utils.YamlPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashSet;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PIPELINE)
public class PMSPipelineTemplateHelper {
  private static final String TEMPLATE_RESOLUTION_REQUEST_COUNT = "template_resolution_request_count";
  private static final String TEMPLATE_RESOLUTION_DURATION = "template_resolution_duration";
  private static final String STATUS_SUCCESS = "SUCCESS";
  private static final String STATUS_FAILURE = "FAILURE";

  private final PmsFeatureFlagHelper pmsFeatureFlagHelper;
  private final TemplateResourceClient templateResourceClient;
  private final PipelineEnforcementService pipelineEnforcementService;
  private final YamlPreProcessorFactory yamlPreProcessorFactory;
  private final MetricService metricService;

  public TemplateResponseDTO getTemplate(String templateIdentifier, String accountId, String orgId, String projectId,
      String versionLabel, String loadFromCache, String transientBranch) {
    return getTemplate(
        templateIdentifier, accountId, orgId, projectId, versionLabel, null, loadFromCache, transientBranch);
  }

  public TemplateResponseDTO getTemplate(String templateIdentifier, String accountId, String orgId, String projectId,
      String versionLabel, String label, String loadFromCache, String transientBranch) {
    TemplateResponseDTO response = null;
    GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();
    long start = System.currentTimeMillis();
    try {
      // transient branch gets the highest priority
      if (isNotEmpty(transientBranch)) {
        response = NGRestUtils.getResponse(templateResourceClient.get(templateIdentifier, accountId, orgId, projectId,
            label, versionLabel, false, transientBranch, null, null, null, null, null, null, null, loadFromCache));
      } else if (gitEntityInfo != null) {
        // if transient branch is not present, use the git details of the pipeline
        response = NGRestUtils.getResponse(templateResourceClient.get(templateIdentifier, accountId, orgId, projectId,
            label, versionLabel, false, gitEntityInfo.getBranch(), gitEntityInfo.getYamlGitConfigId(), true,
            getConnectorRef(), getRepoName(), accountId, orgId, projectId, loadFromCache));
      } else {
        response = NGRestUtils.getResponse(templateResourceClient.get(templateIdentifier, accountId, orgId, projectId,
            label, versionLabel, false, null, null, null, null, null, null, null, null, loadFromCache));
      }

    } catch (Exception e) {
      throw new InvalidRequestException(
          String.format("Unable to fetch template %s version %s, in account %s org %s and project %s",
              templateIdentifier, versionLabel, accountId, orgId, projectId),
          e);
    } finally {
      log.info("[PMS_Template] template get call took {}ms for projectId {}, orgId {}, accountId {}",
          System.currentTimeMillis() - start, projectId, orgId, accountId);
    }
    return response;
  }

  public TemplateMergeResponseDTO resolveTemplateRefsInPipeline(PipelineEntity pipelineEntity, String loadFromCache) {
    return resolveTemplateRefsInPipeline(pipelineEntity.getAccountId(), pipelineEntity.getOrgIdentifier(),
        pipelineEntity.getProjectIdentifier(), pipelineEntity.getYaml(), false, false, loadFromCache, false,
        pipelineEntity.getHarnessVersion(), false, pipelineEntity.getRepo(), false);
  }

  public TemplateMergeResponseDTO resolveTemplateRefsInPipeline(
      PipelineEntity pipelineEntity, ScopeInfo scopeInfo, String loadFromCache) {
    return resolveTemplateRefsInPipeline(pipelineEntity.getAccountId(), scopeInfo.getOrgIdentifier(),
        scopeInfo.getProjectIdentifier(), pipelineEntity.getYaml(), false, false, loadFromCache, false,
        pipelineEntity.getHarnessVersion(), false, pipelineEntity.getRepo(), false);
  }

  public TemplateMergeResponseDTO resolveTemplateRefsInPipeline(
      PipelineEntity pipelineEntity, boolean getMergedTemplateWithTemplateReferences, boolean loadFromCache) {
    return resolveTemplateRefsInPipeline(pipelineEntity.getAccountId(), pipelineEntity.getOrgIdentifier(),
        pipelineEntity.getProjectIdentifier(), pipelineEntity.getYaml(), false, getMergedTemplateWithTemplateReferences,
        parseLoadFromCache(loadFromCache), false, pipelineEntity.getHarnessVersion(), false, pipelineEntity.getRepo(),
        false);
  }

  public TemplateMergeResponseDTO resolveTemplateRefsInPipeline(PipelineEntity pipelineEntity, ScopeInfo scopeInfo,
      boolean getMergedTemplateWithTemplateReferences, boolean loadFromCache) {
    return resolveTemplateRefsInPipeline(pipelineEntity.getAccountId(), scopeInfo.getOrgIdentifier(),
        scopeInfo.getProjectIdentifier(), pipelineEntity.getYaml(), false, getMergedTemplateWithTemplateReferences,
        parseLoadFromCache(loadFromCache), false, pipelineEntity.getHarnessVersion(), false, pipelineEntity.getRepo(),
        false);
  }

  public TemplateMergeResponseDTO resolveTemplateRefsInPipeline(
      String accountId, String orgId, String projectId, String yaml, String loadFromCache, String yamlVersion) {
    return resolveTemplateRefsInPipeline(
        accountId, orgId, projectId, yaml, false, false, loadFromCache, false, yamlVersion, false, null, false);
  }

  public TemplateMergeResponseDTO resolveTemplateRefsInPipeline(String accountId, String orgId, String projectId,
      String yaml, boolean checkForTemplateAccess, boolean getMergedTemplateWithTemplateReferences,
      String loadFromCache, String yamlVersion, boolean resolveNotificationTemplate) {
    return resolveTemplateRefsInPipeline(accountId, orgId, projectId, yaml, checkForTemplateAccess,
        getMergedTemplateWithTemplateReferences, loadFromCache, false, yamlVersion, resolveNotificationTemplate, null,
        false);
  }

  public TemplateMergeResponseDTO resolveTemplateRefsInPipelineAndAppendInputSetValidators(String accountId,
      String orgId, String projectId, String yaml, boolean checkForTemplateAccess,
      boolean getMergedTemplateWithTemplateReferences, String loadFromCache, String yamlVersion) {
    return resolveTemplateRefsInPipelineAndAppendInputSetValidators(accountId, orgId, projectId, yaml,
        checkForTemplateAccess, getMergedTemplateWithTemplateReferences, loadFromCache, yamlVersion, null);
  }

  public TemplateMergeResponseDTO resolveTemplateRefsInPipelineAndAppendInputSetValidators(String accountId,
      String orgId, String projectId, String yaml, boolean checkForTemplateAccess,
      boolean getMergedTemplateWithTemplateReferences, String loadFromCache, String yamlVersion, String pipelineRepo) {
    return resolveTemplateRefsInPipeline(accountId, orgId, projectId, yaml, checkForTemplateAccess,
        getMergedTemplateWithTemplateReferences, loadFromCache, true, yamlVersion, false, pipelineRepo, false);
  }

  public TemplateMergeResponseDTO resolveTemplateRefsInPipelineAndAppendInputSetValidatorsForExecution(String accountId,
      String orgId, String projectId, String yaml, boolean checkForTemplateAccess,
      boolean getMergedTemplateWithTemplateReferences, String loadFromCache, String yamlVersion, String pipelineRepo) {
    return resolveTemplateRefsInPipeline(accountId, orgId, projectId, yaml, checkForTemplateAccess,
        getMergedTemplateWithTemplateReferences, loadFromCache, true, yamlVersion, false, pipelineRepo, true);
  }

  public String resolveOnlyPipelineTemplateRefAndMerge(String accountId, String orgIdentifier, String projectIdentifier,
      String yaml, StoreType storeType, String loadFromCache, String yamlVersion) {
    // If storeType is inline then resolve the templates in empty git-context guard.
    if (StoreType.INLINE == storeType) {
      try (EntityGitDetailsGuard guard = new EntityGitDetailsGuard(GitEntityInfo.builder().build())) {
        return resolveOnlyPipelineTemplateRefAndMerge(
            yaml, loadFromCache, accountId, orgIdentifier, projectIdentifier, yamlVersion);
      }
    }
    return resolveOnlyPipelineTemplateRefAndMerge(
        yaml, loadFromCache, accountId, orgIdentifier, projectIdentifier, yamlVersion);
  }

  /**
   * It only resolves pipeline template refs present in the given
   * yaml and merge into the yaml
   * @param yaml
   * @param loadFromCache
   * @param accountId
   * @param orgIdentifier
   * @param projectIdentifier
   * @return
   */
  public String resolveOnlyPipelineTemplateRefAndMerge(String yaml, String loadFromCache, String accountId,
      String orgIdentifier, String projectIdentifier, String yamlVersion) {
    PipelineTemplateRefInfo hasPipelineTemplateWithRef = TemplateRefHelper.hasPipelineTemplateRef(yaml, yamlVersion);
    boolean hasPipelineTemplatePresent = hasPipelineTemplateWithRef.isHasPipelineTemplate();
    if (hasPipelineTemplatePresent) {
      String templateIdentifier = hasPipelineTemplateWithRef.getPipelineTemplateIdentifier();
      String versionLabel = hasPipelineTemplateWithRef.getPipelineTemplateVersionLabel();
      String label = hasPipelineTemplateWithRef.getPipelineTemplateLabel();
      String transientBranch = hasPipelineTemplateWithRef.getTransientBranch();
      IdentifierRef templateIdentifierRef = IdentifierRefHelper.getIdentifierRefOrThrowException(
          templateIdentifier, accountId, orgIdentifier, projectIdentifier, YAMLFieldNameConstants.TEMPLATE);
      TemplateResponseDTO templateResponseDTO = getTemplate(templateIdentifierRef.getIdentifier(),
          templateIdentifierRef.getAccountIdentifier(), templateIdentifierRef.getOrgIdentifier(),
          templateIdentifierRef.getProjectIdentifier(), versionLabel, label, loadFromCache, transientBranch);
      String templateYaml = templateResponseDTO.getYaml();
      boolean templateOverridesEnabled = pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIPE_TEMPLATE_OVERRIDES);
      yaml = HarnessYamlVersion.isV1(yamlVersion)
          ? TemplateRefHelper.mergePipelineTemplateYamlIntoPipelineYamlV1(
                hasPipelineTemplateWithRef.getPipelineYamlJsonNode(), templateYaml, yaml)
          : TemplateRefHelper.mergePipelineTemplateYamlIntoPipelineYaml(
                hasPipelineTemplateWithRef.getPipelineYamlJsonNode(), templateYaml, yaml,
                hasPipelineTemplateWithRef.getTemplateInputsJsonNode(),
                hasPipelineTemplateWithRef.getTemplateOverridesJsonNode(), templateOverridesEnabled);
    }
    return preProcessPipelineYaml(yaml, yamlVersion);
  }

  private String preProcessPipelineYaml(String yaml, String yamlVersion) {
    if (HarnessYamlVersion.isV1(yamlVersion)) {
      YamlPreProcessor preProcessor = yamlPreProcessorFactory.getProcessorInstance(HarnessYamlVersion.V1);
      if (preProcessor != null) {
        yaml = YamlUtils.writeYamlString(preProcessor.preProcess(yaml, false).getPreprocessedJsonNode());
      }
    }
    return yaml;
  }

  public String resolvePipelineWithAllTemplatesRuntimeInputs(
      String pipelineYaml, String accountId, String orgIdentifier, String projectIdentifier, String loadFromCache) {
    return NestedTemplateRuntimeInputsUtils.resolvePipelineRuntimeInputs(pipelineYaml,
        templateIdentifier
        -> getTemplateEntityNode(templateIdentifier, accountId, orgIdentifier, projectIdentifier, loadFromCache),
        NestedTemplateRuntimeInputsOptions.builder().build());
  }

  private JsonNode getTemplateEntityNode(
      String templateIdentifier, String accountId, String orgId, String projectId, String loadFromCache) {
    TemplateResponseDTO templateResponseDTO;
    Pair<String, String> getTemplateIdAndVersionLabel =
        YamlPipelineUtils.extractIdAndVersionLabelFromCompleteTemplateIdentifier(templateIdentifier);
    String templateId = getTemplateIdAndVersionLabel.getLeft();
    String versionLabel = getTemplateIdAndVersionLabel.getRight();

    // The template ref may carry a scope prefix (e.g. "org.Foo", "account.Foo"). Resolve it so the scope query params
    // match the ref's scope. Otherwise a scoped ref is queried at project scope (org and project are both non-empty
    // here) and never matches the org/account level template it points to.
    IdentifierRef templateRef = resolveScopedTemplateRef(templateId, accountId, orgId, projectId);
    try {
      // Global templates will get high priority.
      templateResponseDTO = getTemplate(templateRef.getIdentifier(),
          GlobalTemplateConstants.GLOBAL_TEMPLATES_ACCOUNT_ID, null, null, versionLabel, loadFromCache, null);
    } catch (Exception e) {
      log.info(String.format("Unable to fetch template %s from global template account. Fetching template from account "
                       + "%s, organization %s and project %s",
                   templateIdentifier, templateRef.getAccountIdentifier(), templateRef.getOrgIdentifier(),
                   templateRef.getProjectIdentifier()),
          e);
      templateResponseDTO = getTemplate(templateRef.getIdentifier(), templateRef.getAccountIdentifier(),
          templateRef.getOrgIdentifier(), templateRef.getProjectIdentifier(), versionLabel, loadFromCache, null);
    }
    validateIfTemplateIsV1(templateResponseDTO);
    return YamlUtils.readAsJsonNode(templateResponseDTO.getYaml());
  }

  /**
   * Resolves a (possibly scoped) V1 template reference to an {@link IdentifierRef} carrying the correct scope.
   *
   * <p>This is intentionally keyword-agnostic. In V1 a template can be linked through any template-type keyword
   * ({@code template}, {@code action}, {@code build}, {@code test}, {@code deploy}, {@code chaos}, {@code idp},
   * {@code iacm}, {@code sto}); every one of them references the template via the {@code uses:} value that is extracted
   * upstream. The scope is therefore derived solely from the {@code org.}/{@code account.} prefix on the ref, so the
   * same resolution applies regardless of which keyword linked the template.
   */
  private IdentifierRef resolveScopedTemplateRef(
      String scopedTemplateRef, String accountId, String orgId, String projectId) {
    return IdentifierRefHelper.getIdentifierRef(scopedTemplateRef, accountId, orgId, projectId);
  }

  private void validateIfTemplateIsV1(TemplateResponseDTO templateResponseDTO) {
    if (!HarnessYamlVersion.isV1(templateResponseDTO.getYamlVersion())) {
      throw new NGTemplateException("V0 templates cannot be linked with V1 pipelines/templates");
    }
  }

  private TemplateMergeResponseDTO resolveTemplateRefsInPipeline(String accountId, String orgId, String projectId,
      String yaml, boolean checkForTemplateAccess, boolean getMergedTemplateWithTemplateReferences,
      String loadFromCache, boolean appendInputSetValidator, String yamlVersion, boolean resolveNotificationTemplate,
      String pipelineRepo, boolean enforceOpaOnExecution) {
    // validating the duplicate fields in yaml field
    if (TemplateRefHelper.hasTemplateRefWithCheckDuplicate(yamlVersion, yaml)
        && pipelineEnforcementService.isFeatureRestricted(accountId, FeatureRestrictionName.TEMPLATE_SERVICE.name())) {
      String TEMPLATE_RESOLVE_EXCEPTION_MSG = "Exception in resolving template refs in given pipeline yaml.";
      long start = System.currentTimeMillis();
      boolean success = false;
      String updatedYaml = null;
      JsonNode notificationNode = null;
      boolean shouldProcessNotificationTemplateSeparately =
          shouldProcessNotificationTemplateSeparately(accountId, resolveNotificationTemplate);
      // Remove notification rules from the pipeline YAML.
      if (shouldProcessNotificationTemplateSeparately) {
        JsonNode rootNode = YamlPipelineUtils.readAsJsonNode(yaml);
        notificationNode = getNotificationRulesNode(getPipelineNode(rootNode));
        updatedYaml = removeNotificationRulesFromYaml(rootNode);
      }

      TemplateApplyRequestDTO templateApplyRequestDTO =
          TemplateApplyRequestDTO.builder()
              .originalEntityYaml((updatedYaml == null) ? yaml : updatedYaml)
              .checkForAccess(checkForTemplateAccess)
              .getMergedYamlWithTemplateField(getMergedTemplateWithTemplateReferences)
              .yamlVersion(yamlVersion)
              .enforceOpaOnExecution(enforceOpaOnExecution)
              .build();
      try {
        GitEntityInfo gitEntityInfo =
            pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIPE_DISABLE_GIT_ENTITY_INFO_V2_FLOW)
            ? GitContextHelper.getGitEntityInfo()
            : GitContextHelper.getGitEntityInfoV2();
        if (gitEntityInfo != null) {
          // Set parent repo to pipeline's repo when missing so cross-repo template fetches resolve correctly.
          boolean shouldPopulateRepoName = pipelineRepo != null
              && !pmsFeatureFlagHelper.isEnabled(
                  accountId, FeatureName.PIPE_DISABLE_PARENT_REPO_NAME_CONTEXT_POPULATION_FOR_TEMPLATE);
          GitEntityInfo gitEntityInfoForApply =
              shouldPopulateRepoName ? gitEntityInfo.toBuilder().repoName(pipelineRepo).build() : gitEntityInfo;
          try (EntityGitDetailsGuard ignored =
                   shouldPopulateRepoName ? new EntityGitDetailsGuard(gitEntityInfoForApply) : null) {
            if (pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIE_ERROR_ENHANCEMENTS)) {
              TemplateMergeResponseDTO mergedTemplateResponseDTO =
                  PipelineRestUtils.getResponse(templateResourceClient.applyTemplatesOnGivenYamlV2(accountId, orgId,
                      projectId, gitEntityInfoForApply.getBranch(), gitEntityInfoForApply.getYamlGitConfigId(), true,
                      getConnectorRef(gitEntityInfoForApply), getRepoName(gitEntityInfoForApply), accountId, orgId,
                      projectId, loadFromCache, templateApplyRequestDTO, appendInputSetValidator));
              success = true;
              return processNotificationTemplateMerging(mergedTemplateResponseDTO,
                  shouldProcessNotificationTemplateSeparately, getMergedTemplateWithTemplateReferences,
                  notificationNode);
            } else {
              TemplateMergeResponseDTO mergedTemplateResponseDTO =
                  NGRestUtils.getResponse(templateResourceClient.applyTemplatesOnGivenYamlV2(accountId, orgId,
                      projectId, gitEntityInfoForApply.getBranch(), gitEntityInfoForApply.getYamlGitConfigId(), true,
                      getConnectorRef(gitEntityInfoForApply), getRepoName(gitEntityInfoForApply), accountId, orgId,
                      projectId, loadFromCache, templateApplyRequestDTO, appendInputSetValidator));
              success = true;
              return processNotificationTemplateMerging(mergedTemplateResponseDTO,
                  shouldProcessNotificationTemplateSeparately, getMergedTemplateWithTemplateReferences,
                  notificationNode);
            }
          }
        }
        GitSyncBranchContext gitSyncBranchContext =
            GitSyncBranchContext.builder().gitBranchInfo(GitEntityInfo.builder().build()).build();
        try (PmsGitSyncBranchContextGuard ignored = new PmsGitSyncBranchContextGuard(gitSyncBranchContext, true)) {
          if (pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIE_ERROR_ENHANCEMENTS)) {
            TemplateMergeResponseDTO mergedTemplateResponseDTO = PipelineRestUtils.getResponse(
                templateResourceClient.applyTemplatesOnGivenYamlV2(accountId, orgId, projectId, null, null, null, null,
                    null, null, null, null, loadFromCache, templateApplyRequestDTO, appendInputSetValidator));
            success = true;
            return processNotificationTemplateMerging(mergedTemplateResponseDTO,
                shouldProcessNotificationTemplateSeparately, getMergedTemplateWithTemplateReferences, notificationNode);
          } else {
            TemplateMergeResponseDTO mergedTemplateResponseDTO = NGRestUtils.getResponse(
                templateResourceClient.applyTemplatesOnGivenYamlV2(accountId, orgId, projectId, null, null, null, null,
                    null, null, null, null, loadFromCache, templateApplyRequestDTO, appendInputSetValidator));
            success = true;
            return processNotificationTemplateMerging(mergedTemplateResponseDTO,
                shouldProcessNotificationTemplateSeparately, getMergedTemplateWithTemplateReferences, notificationNode);
          }
        }
      } catch (InvalidRequestException ex) {
        rethrowIfOpaViolation(ex.getMessage(), ex.getMetadata());
        throw ex;
      } catch (HarnessRemoteServiceException e) {
        rethrowIfOpaViolation(e.getMessage(), e.getMetadata());
        throw new NGTemplateException("Failed to apply templates on pipeline", e.getResponseMessages());

      } catch (UnexpectedException e) {
        log.error("Error connecting to Template Service", e);
        throw new InternalServerErrorException(
            TEMPLATE_RESOLVE_EXCEPTION_MSG + ": Error while connecting to Template Service", e);
      } catch (Exception e) {
        log.error("Unknown exception in resolving templates", e);
        throw new NGTemplateException(TEMPLATE_RESOLVE_EXCEPTION_MSG, e);
      } finally {
        long duration = System.currentTimeMillis() - start;
        log.info("[PMS_Template] template resolution took {}ms for projectId {}, orgId {}, accountId {}", duration,
            projectId, orgId, accountId);
        String status = success ? STATUS_SUCCESS : STATUS_FAILURE;
        try (PmsMetricContextGuard ignore = new PmsMetricContextGuard(ImmutableMap.of(
                 PmsEventMonitoringConstants.ACCOUNT_ID, accountId, PmsEventMonitoringConstants.STATUS, status))) {
          metricService.incCounter(TEMPLATE_RESOLUTION_REQUEST_COUNT);
          metricService.recordMetric(TEMPLATE_RESOLUTION_DURATION, duration);
        }
      }
    }
    return TemplateMergeResponseDTO.builder().mergedPipelineYaml(yaml).mergedPipelineYamlWithTemplateRef(yaml).build();
  }

  public List<EntityDetailProtoDTO> getTemplateReferencesForGivenYaml(
      String accountId, String orgId, String projectId, String yaml) {
    return getTemplateReferencesForGivenYaml(accountId, orgId, projectId, yaml, null);
  }

  /**
   * Returns template entity references for the given pipeline YAML.
   *
   * <p>The {@code harnessVersion} must be supplied so the template service can apply the correct
   * extraction strategy: V1 YAML uses {@code uses:} syntax under template-type nodes and requires
   * a JSON-tree walk ({@code getV1TemplateReferences}), while V0 YAML uses FQN-based traversal.
   * Without the version, the template service defaults to V0 and silently misses all V1 refs.
   */
  public List<EntityDetailProtoDTO> getTemplateReferencesForGivenYaml(
      String accountId, String orgId, String projectId, String yaml, String harnessVersion) {
    TemplateReferenceRequestDTO requestDTO =
        TemplateReferenceRequestDTO.builder().yaml(yaml).yamlVersion(harnessVersion).build();
    GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();
    if (gitEntityInfo != null) {
      return NGRestUtils.getResponse(templateResourceClient.getTemplateReferenceForGivenYaml(accountId, orgId,
          projectId, gitEntityInfo.isNewBranch() ? gitEntityInfo.getBaseBranch() : gitEntityInfo.getBranch(),
          gitEntityInfo.getRepoName(), true, requestDTO));
    }
    return NGRestUtils.getResponse(templateResourceClient.getTemplateReferenceForGivenYaml(
        accountId, orgId, projectId, null, null, null, requestDTO));
  }

  public RefreshResponseDTO getRefreshedYaml(String accountId, String orgId, String projectId, String yaml,
      PipelineEntity pipelineEntity, String loadFromCache) {
    GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();
    RefreshRequestDTO refreshRequest = RefreshRequestDTO.builder().yaml(yaml).build();
    if (gitEntityInfo != null) {
      log.info("Fetch refreshed yaml for pipeline {} from git branch {} and repo {}", pipelineEntity.getIdentifier(),
          gitEntityInfo.getBranch(), gitEntityInfo.getRepoName());
      return NGRestUtils.getResponse(templateResourceClient.getRefreshedYaml(accountId, orgId, projectId,
          gitEntityInfo.isNewBranch() ? gitEntityInfo.getBaseBranch() : gitEntityInfo.getBranch(),
          gitEntityInfo.getYamlGitConfigId(), true, pipelineEntity.getConnectorRef(), pipelineEntity.getRepo(),
          pipelineEntity.getAccountIdentifier(), pipelineEntity.getOrgIdentifier(),
          pipelineEntity.getProjectIdentifier(), pipelineEntity.getHarnessVersion(), loadFromCache, refreshRequest));
    }

    return NGRestUtils.getResponse(templateResourceClient.getRefreshedYaml(accountId, orgId, projectId, null, null,
        null, null, null, null, null, null, pipelineEntity.getHarnessVersion(), loadFromCache, refreshRequest));
  }

  public ValidateTemplateInputsResponseDTO validateTemplateInputsForGivenYaml(String accountId, String orgId,
      String projectId, String yaml, PipelineEntity pipelineEntity, String loadFromCache) {
    GitEntityInfo gitEntityInfo =
        pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIPE_DISABLE_GIT_ENTITY_INFO_V2_FLOW)
        ? GitContextHelper.getGitEntityInfo()
        : GitContextHelper.getGitEntityInfoV2();
    RefreshRequestDTO refreshRequest = RefreshRequestDTO.builder().yaml(yaml).build();
    long start = System.currentTimeMillis();
    try {
      if (gitEntityInfo != null) {
        return NGRestUtils.getResponse(templateResourceClient.validateTemplateInputsForGivenYaml(accountId, orgId,
            projectId, gitEntityInfo.isNewBranch() ? gitEntityInfo.getBaseBranch() : gitEntityInfo.getBranch(),
            gitEntityInfo.getYamlGitConfigId(), true, pipelineEntity.getConnectorRef(), pipelineEntity.getRepo(),
            accountId, orgId, projectId, pipelineEntity.getHarnessVersion(), loadFromCache, refreshRequest));
      }
      return NGRestUtils.getResponse(
          templateResourceClient.validateTemplateInputsForGivenYaml(accountId, orgId, projectId, null, null, null, null,
              null, null, null, null, pipelineEntity.getHarnessVersion(), loadFromCache, refreshRequest));
    } finally {
      log.info("[PMS_PipelineTemplate] validating template inputs for given yaml took {}ms for projectId {}, orgId {}, "
              + "accountId {}",
          System.currentTimeMillis() - start, projectId, orgId, accountId);
    }
  }

  public YamlFullRefreshResponseDTO refreshAllTemplatesForYaml(String accountId, String orgId, String projectId,
      String yaml, PipelineEntity pipelineEntity, String loadFromCache) {
    GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();
    RefreshRequestDTO refreshRequest = RefreshRequestDTO.builder().yaml(yaml).build();
    if (gitEntityInfo != null) {
      return NGRestUtils.getResponse(templateResourceClient.refreshAllTemplatesForYaml(accountId, orgId, projectId,
          gitEntityInfo.isNewBranch() ? gitEntityInfo.getBaseBranch() : gitEntityInfo.getBranch(),
          gitEntityInfo.getYamlGitConfigId(), true, pipelineEntity.getConnectorRef(), pipelineEntity.getRepo(),
          pipelineEntity.getAccountIdentifier(), pipelineEntity.getOrgIdentifier(),
          pipelineEntity.getProjectIdentifier(), pipelineEntity.getHarnessVersion(), loadFromCache, refreshRequest));
    }

    return NGRestUtils.getResponse(templateResourceClient.refreshAllTemplatesForYaml(accountId, orgId, projectId, null,
        null, null, null, null, null, null, null, pipelineEntity.getHarnessVersion(), loadFromCache, refreshRequest));
  }

  public YamlFullRefreshResponseDTO refreshAllTemplatesForYaml(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String yaml, PipelineEntity pipelineEntity, String bulkReconcileUUID,
      String loadFromCache) {
    GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();
    RefreshRequestDTO refreshRequest = RefreshRequestDTO.builder().yaml(yaml).build();
    if (gitEntityInfo != null) {
      return NGRestUtils.getResponse(templateResourceClient.bulkRefreshAllTemplatesForYaml(accountIdentifier,
          orgIdentifier, projectIdentifier, pipelineEntity.getIdentifier(), ReferenceEntityType.PIPELINE.name(),
          bulkReconcileUUID, gitEntityInfo.isNewBranch() ? gitEntityInfo.getBaseBranch() : gitEntityInfo.getBranch(),
          gitEntityInfo.getYamlGitConfigId(), true, pipelineEntity.getConnectorRef(), pipelineEntity.getRepo(),
          pipelineEntity.getAccountIdentifier(), pipelineEntity.getOrgIdentifier(),
          pipelineEntity.getProjectIdentifier(), loadFromCache, refreshRequest));
    }

    return NGRestUtils.getResponse(templateResourceClient.bulkRefreshAllTemplatesForYaml(accountIdentifier,
        orgIdentifier, projectIdentifier, pipelineEntity.getIdentifier(), ReferenceEntityType.PIPELINE.name(),
        bulkReconcileUUID, null, null, null, null, null, null, null, null, loadFromCache, refreshRequest));
  }

  public HashSet<String> getTemplatesModuleInfo(TemplateMergeResponseDTO templateMergeResponseDTO) {
    HashSet<String> templateModuleInfo = new HashSet<>();
    if (isNotEmpty(templateMergeResponseDTO.getTemplateReferenceSummaries())) {
      for (TemplateReferenceSummary templateReferenceSummary :
          templateMergeResponseDTO.getTemplateReferenceSummaries()) {
        templateModuleInfo.addAll(templateReferenceSummary.getModuleInfo());
      }
    }
    return templateModuleInfo;
  }

  private String getConnectorRef() {
    return getConnectorRef(GitContextHelper.getGitEntityInfo());
  }

  private String getConnectorRef(GitEntityInfo gitEntityInfo) {
    if (gitEntityInfo == null) {
      return null;
    }
    if (GitAwareContextHelper.isNullOrDefault(gitEntityInfo.getParentEntityConnectorRef())) {
      return gitEntityInfo.getConnectorRef();
    }
    return gitEntityInfo.getParentEntityConnectorRef();
  }

  private String getRepoName() {
    return getRepoName(GitContextHelper.getGitEntityInfo());
  }

  private String getRepoName(GitEntityInfo gitEntityInfo) {
    if (gitEntityInfo == null) {
      return null;
    }
    if (GitAwareContextHelper.isNullOrDefault(gitEntityInfo.getParentEntityRepoName())) {
      return gitEntityInfo.getRepoName();
    }
    return gitEntityInfo.getParentEntityRepoName();
  }

  private String parseLoadFromCache(boolean loadFromCache) {
    if (loadFromCache) {
      return BOOLEAN_TRUE_VALUE;
    }
    return BOOLEAN_FALSE_VALUE;
  }

  private static String mergeNotificationRulesFromYaml(String mergedPipelineYaml, JsonNode notificationNode) {
    if (notificationNode != null) {
      JsonNode mergedYamlRootNode = YamlPipelineUtils.readAsJsonNode(mergedPipelineYaml);
      if (mergedYamlRootNode != null && mergedYamlRootNode.get(YAMLFieldNameConstants.PIPELINE) != null) {
        ((ObjectNode) mergedYamlRootNode.get(YAMLFieldNameConstants.PIPELINE))
            .set(NOTIFICATION_RULES_V0, notificationNode);
        return YamlPipelineUtils.writeYamlString(mergedYamlRootNode);
      }
    }
    return mergedPipelineYaml;
  }

  private String removeNotificationRulesFromYaml(JsonNode rootNode) {
    /*
     * This is necessary because when users use custom templates in notification rules,
     * we don't want the template resolved initially to avoid increasing the pipeline YAML size.
     * Instead, templates will be resolved dynamically when the notification is sent.
     * We remove the templates from the YAML before calling the template-service.
     * We also don't check for the template's presence in the notification rule, as it won't affect the flow.
     */
    JsonNode applicationNode = getPipelineNode(rootNode);
    if (getNotificationRulesNode(applicationNode) != null) {
      ((ObjectNode) applicationNode).remove(NOTIFICATION_RULES_V0);
    }
    return YamlPipelineUtils.writeYamlString(rootNode);
  }

  private JsonNode getPipelineNode(JsonNode rootNode) {
    return rootNode.get(YAMLFieldNameConstants.PIPELINE);
  }

  private JsonNode getNotificationRulesNode(JsonNode applicationNode) {
    JsonNode notificationNode = null;
    if (applicationNode.has(NOTIFICATION_RULES_V0)) {
      notificationNode = applicationNode.get(NOTIFICATION_RULES_V0);
    }
    return notificationNode;
  }

  private boolean shouldProcessNotificationTemplateSeparately(String accountId, boolean resolveNotificationTemplate) {
    return !resolveNotificationTemplate;
  }

  /**
   * Processes notification template merging for the given TemplateMergeResponseDTO.
   * This method handles the common logic of merging notification rules back into the pipeline YAML
   * after template resolution.
   *
   * @param mergedTemplateResponseDTO The response DTO from template resolution
   * @param shouldProcessNotificationTemplateSeparately Flag indicating if notification templates should be processed
   *     separately
   * @param getMergedTemplateWithTemplateReferences Flag indicating if merged template with template references is
   *     needed
   * @param notificationNode The notification rules JSON node to merge back
   * @return The processed TemplateMergeResponseDTO with notification rules merged
   */
  private TemplateMergeResponseDTO processNotificationTemplateMerging(
      TemplateMergeResponseDTO mergedTemplateResponseDTO, boolean shouldProcessNotificationTemplateSeparately,
      boolean getMergedTemplateWithTemplateReferences, JsonNode notificationNode) {
    if (mergedTemplateResponseDTO != null && shouldProcessNotificationTemplateSeparately) {
      mergedTemplateResponseDTO.setMergedPipelineYaml(
          mergeNotificationRulesFromYaml(mergedTemplateResponseDTO.getMergedPipelineYaml(), notificationNode));
      if (getMergedTemplateWithTemplateReferences
          && mergedTemplateResponseDTO.getMergedPipelineYamlWithTemplateRef() != null) {
        String mergedTemplateRefYaml = mergeNotificationRulesFromYaml(
            mergedTemplateResponseDTO.getMergedPipelineYamlWithTemplateRef(), notificationNode);
        mergedTemplateResponseDTO.setMergedPipelineYamlWithTemplateRef(mergedTemplateRefYaml);
      }
    }
    return mergedTemplateResponseDTO;
  }

  public void setupGitContext(String branch) {
    GitAwareContextHelper.populateGitDetails(
        io.harness.gitsync.interceptor.GitEntityInfo.builder().branch(branch).build());
  }

  private void rethrowIfOpaViolation(String message, ErrorMetadataDTO metadata) {
    if (metadata instanceof OpaOnSaveStatusErrorDTO) {
      log.error("Policy evaluation failed while resolving templates on pipeline: {}", message);
      throw new PolicyEvaluationFailureException(message, ((OpaOnSaveStatusErrorDTO) metadata).getOpaOnSaveStatusDTO());
    }
  }
}
