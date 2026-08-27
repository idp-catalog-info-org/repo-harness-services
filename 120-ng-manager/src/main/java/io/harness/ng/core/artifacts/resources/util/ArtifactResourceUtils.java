/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.artifacts.resources.util;

import static io.harness.connector.accesscontrol.ConnectorsAccessControlPermissions.ACCESS_CONNECTOR_PERMISSION;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.gitcaching.GitCachingConstants.BOOLEAN_TRUE_VALUE;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;
import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.NexusConstant;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.audit.ResourceTypeConstants;
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
import io.harness.beans.InputSetValidatorType;
import io.harness.beans.ScopeInfo;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.AcrArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.AmazonS3ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.ArtifactoryRegistryArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.DockerHubArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.EcrArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.GcrArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.GoogleArtifactRegistryConfig;
import io.harness.cdng.artifact.bean.yaml.JenkinsArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.NexusRegistryArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.customartifact.CustomArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.nexusartifact.Nexus2RegistryArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.nexusartifact.NexusRegistryDockerConfig;
import io.harness.cdng.artifact.bean.yaml.nexusartifact.NexusRegistryMavenConfig;
import io.harness.cdng.artifact.bean.yaml.nexusartifact.NexusRegistryNpmConfig;
import io.harness.cdng.artifact.bean.yaml.nexusartifact.NexusRegistryNugetConfig;
import io.harness.cdng.artifact.bean.yaml.nexusartifact.NexusRegistryRawConfig;
import io.harness.cdng.artifact.resources.acr.dtos.AcrRegistriesDTO;
import io.harness.cdng.artifact.resources.acr.dtos.AcrRepositoriesDTO;
import io.harness.cdng.artifact.resources.acr.dtos.AcrRequestDTO;
import io.harness.cdng.artifact.resources.acr.service.AcrResourceService;
import io.harness.cdng.artifact.resources.artifactory.dtos.ArtifactoryBuildDetailsDTO;
import io.harness.cdng.artifact.resources.artifactory.dtos.ArtifactoryImagePathsDTO;
import io.harness.cdng.artifact.resources.artifactory.dtos.ArtifactoryRequestDTO;
import io.harness.cdng.artifact.resources.artifactory.service.ArtifactoryResourceService;
import io.harness.cdng.artifact.resources.custom.CustomResourceService;
import io.harness.cdng.artifact.resources.docker.dtos.DockerBuildDetailsDTO;
import io.harness.cdng.artifact.resources.docker.dtos.DockerRequestDTO;
import io.harness.cdng.artifact.resources.docker.service.DockerResourceService;
import io.harness.cdng.artifact.resources.ecr.dtos.EcrBuildDetailsDTO;
import io.harness.cdng.artifact.resources.ecr.dtos.EcrRequestDTO;
import io.harness.cdng.artifact.resources.ecr.service.EcrResourceService;
import io.harness.cdng.artifact.resources.gcr.dtos.GcrBuildDetailsDTO;
import io.harness.cdng.artifact.resources.gcr.dtos.GcrRequestDTO;
import io.harness.cdng.artifact.resources.gcr.dtos.GcrResponseDTO;
import io.harness.cdng.artifact.resources.gcr.service.GcrResourceService;
import io.harness.cdng.artifact.resources.googleartifactregistry.dtos.GARBuildDetailsDTO;
import io.harness.cdng.artifact.resources.googleartifactregistry.dtos.GARPackageDTO;
import io.harness.cdng.artifact.resources.googleartifactregistry.dtos.GARPackageDTOList;
import io.harness.cdng.artifact.resources.googleartifactregistry.dtos.GARRepositoryDTOList;
import io.harness.cdng.artifact.resources.googleartifactregistry.dtos.GARResponseDTO;
import io.harness.cdng.artifact.resources.googleartifactregistry.dtos.GarRepositoryDTO;
import io.harness.cdng.artifact.resources.googleartifactregistry.dtos.GarRequestDTO;
import io.harness.cdng.artifact.resources.googleartifactregistry.service.GARResourceService;
import io.harness.cdng.artifact.resources.jenkins.dtos.JenkinsJobDetailsDTO;
import io.harness.cdng.artifact.resources.jenkins.service.JenkinsResourceService;
import io.harness.cdng.artifact.resources.nexus.dtos.Nexus2MavenDTO;
import io.harness.cdng.artifact.resources.nexus.dtos.NexusBuildDetailsDTO;
import io.harness.cdng.artifact.resources.nexus.dtos.NexusRequestDTO;
import io.harness.cdng.artifact.resources.nexus.dtos.NexusResponseDTO;
import io.harness.cdng.artifact.resources.nexus.service.NexusResourceService;
import io.harness.cdng.buckets.resources.s3.S3ResourceService;
import io.harness.cdng.k8s.resources.azure.dtos.AzureSubscriptionsDTO;
import io.harness.cdng.k8s.resources.azure.service.AzureResourceService;
import io.harness.cdng.manifest.yaml.S3StoreConfig;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.common.NGExpressionUtils;
import io.harness.connector.accesscontrol.ResourceTypes;
import io.harness.data.algorithm.HashGenerator;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.azure.AcrBuildDetailsDTO;
import io.harness.delegate.beans.azure.AcrResponseDTO;
import io.harness.delegate.task.artifacts.source.ArtifactSourceType;
import io.harness.encryption.Scope;
import io.harness.evaluators.CDExpressionEvaluator;
import io.harness.evaluators.CDYamlExpressionEvaluator;
import io.harness.exception.InvalidIdentifierRefException;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.expression.common.ExpressionMode;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.gitx.GitXTransientBranchGuard;
import io.harness.ng.core.artifacts.resources.custom.script.CustomScriptInfo;
import io.harness.ng.core.buckets.resources.BucketsResourceUtils;
import io.harness.ng.core.buckets.resources.s3.dtos.BucketResponseDTO;
import io.harness.ng.core.buckets.resources.s3.dtos.FilePathDTO;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.service.services.impl.InputSetMergeUtility;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesSpec;
import io.harness.ng.core.serviceoverridev2.service.ServiceOverridesServiceV2;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.template.TemplateApplyRequestDTO;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.ng.core.template.TemplateResponseDTO;
import io.harness.ng.core.utils.ServiceOverrideV2ValidationHelper;
import io.harness.ng.core.utils.URLDecoderUtility;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.inputset.MergeInputSetResponseDTOPMS;
import io.harness.pms.inputset.MergeInputSetTemplateRequestDTO;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.merger.yaml.YamlConfig;
import io.harness.pms.pipeline.PMSPipelineResponseDTO;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.pms.yaml.validation.RuntimeInputValuesValidator;
import io.harness.remote.client.NGRestUtils;
import io.harness.telemetry.helpers.ArtifactSourceInstrumentationHelper;
import io.harness.template.remote.TemplateResourceClient;
import io.harness.template.resources.beans.NGTemplateConstants;
import io.harness.template.yaml.ref.PipelineTemplateRefInfo;
import io.harness.template.yaml.ref.TemplateRefHelper;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.NGFeatureFlagHelperService;
import io.harness.utils.ScopeResolutionHelper;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.utils.NGVariablesUtils;

import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.helpers.ext.jenkins.JobDetails;
import software.wings.helpers.ext.nexus.NexusRepositories;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import javax.ws.rs.NotFoundException;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_ARTIFACTS, HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class ArtifactResourceUtils {
  @Inject PipelineServiceClient pipelineServiceClient;
  @Inject TemplateResourceClient templateResourceClient;
  @Inject ServiceEntityService serviceEntityService;
  @Inject EnvironmentService environmentService;
  @Inject NexusResourceService nexusResourceService;
  @Inject DockerResourceService dockerResourceService;
  @Inject GARResourceService garResourceService;
  @Inject GcrResourceService gcrResourceService;
  @Inject EcrResourceService ecrResourceService;
  @Inject AcrResourceService acrResourceService;
  @Inject AzureResourceService azureResourceService;
  @Inject ArtifactoryResourceService artifactoryResourceService;
  @Inject AccessControlClient accessControlClient;
  @Inject CustomResourceService customResourceService;
  @Inject S3ResourceService s3ResourceService;
  @Inject BucketsResourceUtils bucketsResourceUtils;
  @Inject ArtifactSourceInstrumentationHelper artifactSourceInstrumentationHelper;
  @Inject JenkinsResourceService jenkinsResourceService;
  @Inject ServiceOverridesServiceV2 serviceOverridesServiceV2;
  @Inject ServiceOverrideV2ValidationHelper overrideV2ValidationHelper;
  @Inject NGFeatureFlagHelperService featureFlagHelperService;
  @Inject ScopeResolutionHelper scopeResolutionHelper;
  @Inject ScopeInfoService scopeInfoService;

  public final String SERVICE_GIT_BRANCH = "serviceGitBranch";
  public final String ENV_GIT_BRANCH = "envGitBranch";
  public final String DOCKER = "DOCKER";
  public final String FETCH_PACKAGES = "fetch_packages";
  public final String FETCH_REPOSITORIES = "fetch_repositories";
  public final String VARIABLES = "variables";
  public final String VAR_NAME = "name";
  public final String VAR_VALUE = "value";
  public final String VAR_TYPE = "type";
  public final String VAR_DESCRIPTION = "description";
  private static ObjectMapper objectMapper = new ObjectMapper();

  // Checks whether field is fixed value or not, if empty then also we return false for fixed value.
  public static boolean isFieldFixedValue(String fieldValue) {
    return !isEmpty(fieldValue) && !NGExpressionUtils.isRuntimeOrExpressionField(fieldValue);
  }

  private String getMergedCompleteYaml(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String runtimeInputYaml, GitEntityFindInfoDTO gitEntityBasicInfo) {
    if (isEmpty(pipelineIdentifier)) {
      return runtimeInputYaml;
    }

    if (gitEntityBasicInfo == null) {
      gitEntityBasicInfo = new GitEntityFindInfoDTO();
    }

    MergeInputSetResponseDTOPMS response =
        NGRestUtils.getResponse(pipelineServiceClient.getMergeInputSetFromPipelineTemplate(accountId, orgIdentifier,
            projectIdentifier, pipelineIdentifier, gitEntityBasicInfo.getBranch(),
            gitEntityBasicInfo.getYamlGitConfigId(), gitEntityBasicInfo.getBranch(),
            gitEntityBasicInfo.getYamlGitConfigId(), gitEntityBasicInfo.getDefaultFromOtherRepo(),
            MergeInputSetTemplateRequestDTO.builder().runtimeInputYaml(runtimeInputYaml).build()));
    if (response.isErrorResponse()) {
      log.error("Failed to get Merged Pipeline Yaml with error yaml - \n "
          + response.getInputSetErrorWrapper().getErrorPipelineYaml());
      log.error("Error map to identify the errors - \n"
          + response.getInputSetErrorWrapper().getUuidToErrorResponseMap().toString());
      throw new InvalidRequestException("Failed to get Merged Pipeline yaml.");
    }
    return response.getCompletePipelineYaml();
  }

  private String applyTemplatesOnGivenYaml(String accountId, String orgIdentifier, String projectIdentifier,
      String yaml, GitEntityFindInfoDTO gitEntityBasicInfo) {
    return applyTemplatesOnGivenYaml(accountId, orgIdentifier, projectIdentifier, yaml, gitEntityBasicInfo, null);
  }

  private String applyTemplatesOnGivenYaml(String accountId, String orgIdentifier, String projectIdentifier,
      String yaml, GitEntityFindInfoDTO gitEntityBasicInfo, String pipelineIdentifier) {
    GitEntityFindInfoDTO gitInfoForApply = populateParentEntityRepoNameIfMissing(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, gitEntityBasicInfo);
    TemplateMergeResponseDTO response = NGRestUtils.getResponse(templateResourceClient.applyTemplatesOnGivenYamlV2(
        accountId, orgIdentifier, projectIdentifier, gitInfoForApply.getBranch(), gitInfoForApply.getYamlGitConfigId(),
        gitInfoForApply.getDefaultFromOtherRepo(), null, gitInfoForApply.getParentEntityRepoName(), null, null, null,
        BOOLEAN_TRUE_VALUE, TemplateApplyRequestDTO.builder().originalEntityYaml(yaml).build(), false));
    return response.getMergedPipelineYaml();
  }

  /**
   * When the parent entity repo is not supplied by the caller, template resolution falls back to the
   * request branch even for templates that live in a different repo, which fails for cross-repo templates whose branch
   * doesn't exist in their own repo. Populate {@code parentEntityRepoName} with the pipeline's own repo so
   * {@code GitAwareEntityHelper.getWorkingBranch} correctly resolves cross-repo templates from their default branch.
   */
  @VisibleForTesting
  GitEntityFindInfoDTO populateParentEntityRepoNameIfMissing(String accountId, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, GitEntityFindInfoDTO gitEntityBasicInfo) {
    if (gitEntityBasicInfo == null || isEmpty(pipelineIdentifier) || "-1".equals(pipelineIdentifier)
        || isNotEmpty(gitEntityBasicInfo.getParentEntityRepoName())
        || featureFlagHelperService.isEnabled(
            accountId, FeatureName.PIPE_DISABLE_PARENT_REPO_NAME_CONTEXT_POPULATION_FOR_TEMPLATE)) {
      return gitEntityBasicInfo;
    }
    String pipelineRepo =
        getPipelineRepoName(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, gitEntityBasicInfo);
    if (isEmpty(pipelineRepo)) {
      return gitEntityBasicInfo;
    }
    return GitEntityFindInfoDTO.builder()
        .branch(gitEntityBasicInfo.getBranch())
        .yamlGitConfigId(gitEntityBasicInfo.getYamlGitConfigId())
        .defaultFromOtherRepo(gitEntityBasicInfo.getDefaultFromOtherRepo())
        .parentEntityConnectorRef(gitEntityBasicInfo.getParentEntityConnectorRef())
        .parentEntityRepoName(pipelineRepo)
        .parentEntityAccountIdentifier(gitEntityBasicInfo.getParentEntityAccountIdentifier())
        .parentEntityOrgIdentifier(gitEntityBasicInfo.getParentEntityOrgIdentifier())
        .parentEntityProjectIdentifier(gitEntityBasicInfo.getParentEntityProjectIdentifier())
        .repoName(gitEntityBasicInfo.getRepoName())
        .build();
  }

  private String getPipelineRepoName(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, GitEntityFindInfoDTO gitEntityBasicInfo) {
    try {
      PMSPipelineResponseDTO pipelineResponse = NGRestUtils.getResponse(pipelineServiceClient.getPipelineByIdentifier(
          pipelineIdentifier, accountId, orgIdentifier, projectIdentifier, gitEntityBasicInfo.getBranch(),
          gitEntityBasicInfo.getYamlGitConfigId(), gitEntityBasicInfo.getDefaultFromOtherRepo()));
      if (pipelineResponse != null && pipelineResponse.getGitDetails() != null) {
        return pipelineResponse.getGitDetails().getRepoName();
      }
    } catch (Exception ex) {
      log.warn("Failed to fetch pipeline repo for pipeline [{}] while populating parentEntityRepoName for template "
              + "resolution. Proceeding without it.",
          pipelineIdentifier, ex);
    }
    return null;
  }

  public String resolveTemplatesInYaml(String accountId, String orgIdentifier, String projectIdentifier, String yaml,
      GitEntityFindInfoDTO gitEntityBasicInfo) {
    return resolveTemplatesInYaml(accountId, orgIdentifier, projectIdentifier, yaml, gitEntityBasicInfo, null);
  }

  public String resolveTemplatesInYaml(String accountId, String orgIdentifier, String projectIdentifier, String yaml,
      GitEntityFindInfoDTO gitEntityBasicInfo, String pipelineIdentifier) {
    boolean hasTemplateRef = isNotEmpty(yaml) && TemplateRefHelper.hasTemplateRef(yaml);
    if (!hasTemplateRef) {
      return yaml;
    }

    Exception primaryException = null;
    try {
      return applyTemplatesOnGivenYaml(accountId, orgIdentifier, projectIdentifier, yaml,
          gitEntityBasicInfo != null ? gitEntityBasicInfo : new GitEntityFindInfoDTO(), pipelineIdentifier);
    } catch (Exception e) {
      primaryException = e;
      log.warn("applyTemplatesOnGivenYaml failed, trying fallback", e);
    }
    try {
      return fetchAndMergePipelineTemplate(accountId, orgIdentifier, projectIdentifier, yaml);
    } catch (Exception e) {
      log.warn("fetchAndMergePipelineTemplate fallback also failed", e);
    }
    log.error("All template resolution strategies failed for YAML with templateRef", primaryException);
    return yaml;
  }

  private String fetchAndMergePipelineTemplate(
      String accountId, String orgIdentifier, String projectIdentifier, String yaml) {
    PipelineTemplateRefInfo refInfo = TemplateRefHelper.hasPipelineTemplateRef(yaml, HarnessYamlVersion.V0);
    if (!refInfo.isHasPipelineTemplate()) {
      return yaml;
    }

    String templateRef = refInfo.getPipelineTemplateIdentifier();
    String versionLabel = refInfo.getPipelineTemplateVersionLabel();
    String label = refInfo.getPipelineTemplateLabel();

    IdentifierRef templateIdentifier =
        IdentifierRefHelper.getIdentifierRef(templateRef, accountId, orgIdentifier, projectIdentifier);
    TemplateResponseDTO response = NGRestUtils.getResponse(templateResourceClient.get(
        templateIdentifier.getIdentifier(), templateIdentifier.getAccountIdentifier(),
        templateIdentifier.getOrgIdentifier(), templateIdentifier.getProjectIdentifier(), label, versionLabel, false));

    if (response == null || isEmpty(response.getYaml())) {
      return yaml;
    }

    boolean templateOverridesEnabled =
        featureFlagHelperService.isEnabled(accountId, FeatureName.PIPE_TEMPLATE_OVERRIDES);

    return TemplateRefHelper.mergePipelineTemplateYamlIntoPipelineYaml(refInfo.getPipelineYamlJsonNode(),
        response.getYaml(), yaml, refInfo.getTemplateInputsJsonNode(), refInfo.getTemplateOverridesJsonNode(),
        templateOverridesEnabled);
  }

  public boolean checkValidRegexType(ParameterField<String> artifactConfig) {
    return artifactConfig.getExpressionValue() != null && artifactConfig.getInputSetValidator() != null
        && artifactConfig.getInputSetValidator().getValidatorType() == InputSetValidatorType.REGEX;
  }

  public void checkConnectorAccess(IdentifierRef connectorRef) {
    if (connectorRef == null) {
      return;
    }
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(connectorRef.getAccountIdentifier(),
                                                  connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier()),
        Resource.of(ResourceTypes.CONNECTOR, connectorRef.getIdentifier()), ACCESS_CONNECTOR_PERMISSION);
  }

  public boolean isRemoteService(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceRef) {
    Optional<ServiceEntity> optionalService;
    ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);
    optionalService = serviceEntityService.getMetadata(scopeInfo, serviceRef, false);

    return optionalService.filter(serviceEntity -> StoreType.REMOTE.equals(serviceEntity.getStoreType())).isPresent();
  }

  public ResolvedFieldValueWithYamlExpressionEvaluator getResolvedFieldValueWithYamlExpressionEvaluator(
      String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier,
      String runtimeInputYaml, String fieldValuePath, String fqnPath, GitEntityFindInfoDTO gitEntityBasicInfo,
      String serviceId, CDYamlExpressionEvaluator yamlExpressionEvaluator) {
    final ParameterField<String> fieldValueParameterField =
        RuntimeInputValuesValidator.getInputSetParameterField(fieldValuePath);
    if (fieldValueParameterField == null) {
      return ResolvedFieldValueWithYamlExpressionEvaluator.builder()
          .yamlExpressionEvaluator(yamlExpressionEvaluator)
          .value(fieldValuePath)
          .build();
    } else if (!fieldValueParameterField.isExpression()) {
      return ResolvedFieldValueWithYamlExpressionEvaluator.builder()
          .yamlExpressionEvaluator(yamlExpressionEvaluator)
          .value(fieldValueParameterField.getValue())
          .build();
    } else {
      // this check assumes ui sends -1 as pipeline identifier when pipeline is under construction
      if ("-1".equals(pipelineIdentifier)) {
        throw new InvalidRequestException(String.format(
            "Couldn't resolve artifact image path expression %s, as pipeline has not been saved yet.", fieldValuePath));
      }
      if (yamlExpressionEvaluator == null) {
        YamlExpressionEvaluatorWithContext evaluatorWithContext =
            getYamlExpressionEvaluatorWithContext(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
                runtimeInputYaml, fqnPath, gitEntityBasicInfo, serviceId);
        yamlExpressionEvaluator = evaluatorWithContext.getYamlExpressionEvaluator();
      }
      String resolvedFieldValuePath = yamlExpressionEvaluator.renderExpression(
          fieldValuePath, ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
      final ParameterField<String> fieldValueParameter =
          RuntimeInputValuesValidator.getInputSetParameterField(resolvedFieldValuePath);
      if (fieldValueParameter == null || fieldValueParameter.isExpression()) {
        return ResolvedFieldValueWithYamlExpressionEvaluator.builder()
            .yamlExpressionEvaluator(yamlExpressionEvaluator)
            .value(null)
            .build();
      } else {
        return ResolvedFieldValueWithYamlExpressionEvaluator.builder()
            .yamlExpressionEvaluator(yamlExpressionEvaluator)
            .value(fieldValueParameter.getValue())
            .build();
      }
    }
  }

  public YamlExpressionEvaluatorWithContext getYamlExpressionEvaluatorWithContext(String accountId,
      String orgIdentifier, String projectIdentifier, String pipelineIdentifier, String runtimeInputYaml,
      String fqnPath, GitEntityFindInfoDTO gitEntityBasicInfo, String serviceId) {
    String mergedCompleteYaml = getMergedCompleteYaml(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, runtimeInputYaml, gitEntityBasicInfo);
    if (isNotEmpty(mergedCompleteYaml) && TemplateRefHelper.hasTemplateRef(mergedCompleteYaml)) {
      mergedCompleteYaml = applyTemplatesOnGivenYaml(
          accountId, orgIdentifier, projectIdentifier, mergedCompleteYaml, gitEntityBasicInfo, pipelineIdentifier);
    }
    String[] split = fqnPath.split("\\.");
    String stageIdentifier = split[2];
    YamlConfig yamlConfig = new YamlConfig(mergedCompleteYaml);
    Map<FQN, Object> fqnObjectMap = yamlConfig.getFqnToValueMap();

    EntityRefAndFQN serviceRefAndFQN = getEntityRefAndFQN(fqnObjectMap, stageIdentifier, YamlTypes.SERVICE_REF);
    if (isEmpty(serviceId)) {
      // pipelines with inline service definitions
      serviceId = serviceRefAndFQN.getEntityRef();
    }
    serviceId = resolveEntityIdIfExpression(serviceId, mergedCompleteYaml, serviceRefAndFQN);

    // get environment ref
    String environmentId = getResolvedEnvironmentId(mergedCompleteYaml, stageIdentifier, fqnObjectMap);

    // add context needed for fetching git entities
    Map<String, String> contextMap = buildContextMap(fqnObjectMap, stageIdentifier, serviceId, environmentId);

    List<YamlField> aliasYamlField =
        getAliasYamlFields(accountId, orgIdentifier, projectIdentifier, serviceId, environmentId, contextMap);
    return YamlExpressionEvaluatorWithContext.builder()
        .yamlExpressionEvaluator(new CDYamlExpressionEvaluator(mergedCompleteYaml, fqnPath, aliasYamlField))
        .contextMap(contextMap)
        .build();
  }

  public YamlExpressionEvaluatorWithContext getYamlExpressionEvaluatorWithContext(ScopeInfo scopeInfo,
      String pipelineIdentifier, String runtimeInputYaml, String fqnPath, GitEntityFindInfoDTO gitEntityBasicInfo,
      String serviceId) {
    String mergedCompleteYaml = getMergedCompleteYaml(scopeInfo.getAccountIdentifier(), scopeInfo.getOrgIdentifier(),
        scopeInfo.getProjectIdentifier(), pipelineIdentifier, runtimeInputYaml, gitEntityBasicInfo);
    if (isNotEmpty(mergedCompleteYaml) && TemplateRefHelper.hasTemplateRef(mergedCompleteYaml)) {
      mergedCompleteYaml = applyTemplatesOnGivenYaml(scopeInfo.getAccountIdentifier(), scopeInfo.getOrgIdentifier(),
          scopeInfo.getProjectIdentifier(), mergedCompleteYaml, gitEntityBasicInfo, pipelineIdentifier);
    }
    String[] split = fqnPath.split("\\.");
    String stageIdentifier = split[2];
    YamlConfig yamlConfig = new YamlConfig(mergedCompleteYaml);
    Map<FQN, Object> fqnObjectMap = yamlConfig.getFqnToValueMap();

    EntityRefAndFQN serviceRefAndFQN = getEntityRefAndFQN(fqnObjectMap, stageIdentifier, YamlTypes.SERVICE_REF);
    if (isEmpty(serviceId)) {
      // pipelines with inline service definitions
      serviceId = serviceRefAndFQN.getEntityRef();
    }
    serviceId = resolveEntityIdIfExpression(serviceId, mergedCompleteYaml, serviceRefAndFQN);

    // get environment ref
    String environmentId = getResolvedEnvironmentId(mergedCompleteYaml, stageIdentifier, fqnObjectMap);

    // add context needed for fetching git entities
    Map<String, String> contextMap = buildContextMap(fqnObjectMap, stageIdentifier, serviceId, environmentId);

    List<YamlField> aliasYamlField = getAliasYamlFields(scopeInfo, serviceId, environmentId, contextMap);
    return YamlExpressionEvaluatorWithContext.builder()
        .yamlExpressionEvaluator(new CDYamlExpressionEvaluator(mergedCompleteYaml, fqnPath, aliasYamlField))
        .contextMap(contextMap)
        .build();
  }

  public List<String> getNexusArtifactIds(String nexusConnectorIdentifier, String accountId, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, String repositoryFormat, String repository, String groupId,
      String sourceType, String fqnPath, GitEntityFindInfoDTO gitEntityBasicInfo, String runtimeInputYaml,
      String serviceRef) {
    ArtifactSourceType artifactSourceType = null;
    YamlExpressionEvaluatorWithContext baseEvaluatorWithContext = null;

    // remote services can be linked with a specific branch, so we parse the YAML in one go and store the context data
    //  has env git branch and service git branch
    if (isNotEmpty(serviceRef) && isRemoteService(accountId, orgIdentifier, projectIdentifier, serviceRef)) {
      baseEvaluatorWithContext = getYamlExpressionEvaluatorWithContext(accountId, orgIdentifier, projectIdentifier,
          pipelineIdentifier, runtimeInputYaml, fqnPath, gitEntityBasicInfo, serviceRef);
    }

    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = locateArtifactInService(accountId, orgIdentifier,
          projectIdentifier, serviceRef, fqnPath,
          baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getContextMap().get(SERVICE_GIT_BRANCH));

      if (artifactSpecFromService.getSourceType().equals(ArtifactSourceType.NEXUS3_REGISTRY)) {
        NexusRegistryArtifactConfig nexusRegistryArtifactConfig = (NexusRegistryArtifactConfig) artifactSpecFromService;

        artifactSourceType = ArtifactSourceType.NEXUS3_REGISTRY;

        if (isEmpty(nexusConnectorIdentifier)) {
          nexusConnectorIdentifier = (String) nexusRegistryArtifactConfig.getConnectorRef().fetchFinalValue();
        }

        if (isEmpty(repositoryFormat)) {
          repositoryFormat = (String) nexusRegistryArtifactConfig.getRepositoryFormat().fetchFinalValue();
        }

        if (isEmpty(repository)) {
          repository = (String) nexusRegistryArtifactConfig.getRepository().fetchFinalValue();
        }

        if (isEmpty(groupId)) {
          if (repositoryFormat.equals("maven")) {
            NexusRegistryMavenConfig mavenConfig =
                (NexusRegistryMavenConfig) nexusRegistryArtifactConfig.getNexusRegistryConfigSpec();
            if (mavenConfig != null) {
              groupId = (String) mavenConfig.getGroupId().fetchFinalValue();
            }
          }
        }

      } else if (artifactSpecFromService.getSourceType().equals(ArtifactSourceType.NEXUS2_REGISTRY)) {
        Nexus2RegistryArtifactConfig nexus2RegistryArtifactConfig =
            (Nexus2RegistryArtifactConfig) artifactSpecFromService;

        artifactSourceType = ArtifactSourceType.NEXUS2_REGISTRY;

        if (isEmpty(nexusConnectorIdentifier)) {
          nexusConnectorIdentifier = (String) nexus2RegistryArtifactConfig.getConnectorRef().fetchFinalValue();
        }

        if (isEmpty(repositoryFormat)) {
          repositoryFormat = (String) nexus2RegistryArtifactConfig.getRepositoryFormat().fetchFinalValue();
        }

        if (isEmpty(repository)) {
          repository = (String) nexus2RegistryArtifactConfig.getRepository().fetchFinalValue();
        }

        if (isEmpty(groupId)) {
          if (repositoryFormat.equals("maven")) {
            NexusRegistryMavenConfig mavenConfig =
                (NexusRegistryMavenConfig) nexus2RegistryArtifactConfig.getNexusRegistryConfigSpec();
            groupId = (String) mavenConfig.getGroupId().fetchFinalValue();
          }
        }
      } else {
        throw new InvalidRequestException("Invalid artifact source type.");
      }
    }
    CDYamlExpressionEvaluator yamlExpressionEvaluator =
        baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getYamlExpressionEvaluator();
    nexusConnectorIdentifier = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, runtimeInputYaml, nexusConnectorIdentifier, fqnPath, gitEntityBasicInfo,
        serviceRef, yamlExpressionEvaluator)
                                   .getValue();

    repositoryFormat = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, repositoryFormat, fqnPath, gitEntityBasicInfo, serviceRef,
        yamlExpressionEvaluator)
                           .getValue();

    repository = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, repository, fqnPath, gitEntityBasicInfo, serviceRef,
        yamlExpressionEvaluator)
                     .getValue();

    groupId = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, groupId, fqnPath, gitEntityBasicInfo, serviceRef, yamlExpressionEvaluator)
                  .getValue();

    checkConnectorIdentifier(nexusConnectorIdentifier);

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(nexusConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    checkConnectorAccess(connectorRef);

    String nexus3Registry = "Nexus3Registry";
    String nexus2Registry = "Nexus2Registry";

    if (nexus3Registry.equals(sourceType)) {
      artifactSourceType = ArtifactSourceType.NEXUS3_REGISTRY;
    } else if (nexus2Registry.equals(sourceType)) {
      artifactSourceType = ArtifactSourceType.NEXUS2_REGISTRY;
    } else {
      throw new InvalidRequestException("Invalid artifact source type.");
    }

    if (artifactSourceType.equals(ArtifactSourceType.NEXUS3_REGISTRY) && repositoryFormat.equals("maven")
        && EmptyPredicate.isEmpty(groupId)) {
      throw new InvalidRequestException(
          "GroupId field cannot be empty. Please provide a valid groupId to fetch the artifactIds.");
    }
    ScopeInfo scopeInfo =
        featureFlagHelperService.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;
    return nexusResourceService.getArtifactIds(accountId, orgIdentifier, projectIdentifier, connectorRef,
        repositoryFormat, repository, groupId, artifactSourceType, scopeInfo);
  }

  public List<String> getNexusGroupIds(String nexusConnectorIdentifier, String accountId, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, String repositoryFormat, String repository, String fqnPath,
      GitEntityFindInfoDTO gitEntityBasicInfo, String runtimeInputYaml, String serviceRef) {
    ArtifactSourceType artifactSourceType = null;
    YamlExpressionEvaluatorWithContext baseEvaluatorWithContext = null;

    // remote services can be linked with a specific branch, so we parse the YAML in one go and store the context data
    //  has env git branch and service git branch
    if (isNotEmpty(serviceRef) && isRemoteService(accountId, orgIdentifier, projectIdentifier, serviceRef)) {
      baseEvaluatorWithContext = getYamlExpressionEvaluatorWithContext(accountId, orgIdentifier, projectIdentifier,
          pipelineIdentifier, runtimeInputYaml, fqnPath, gitEntityBasicInfo, serviceRef);
    }

    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = locateArtifactInService(accountId, orgIdentifier,
          projectIdentifier, serviceRef, fqnPath,
          baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getContextMap().get(SERVICE_GIT_BRANCH));

      if (artifactSpecFromService.getSourceType().equals(ArtifactSourceType.NEXUS3_REGISTRY)) {
        NexusRegistryArtifactConfig nexusRegistryArtifactConfig = (NexusRegistryArtifactConfig) artifactSpecFromService;

        artifactSourceType = ArtifactSourceType.NEXUS3_REGISTRY;

        if (isEmpty(nexusConnectorIdentifier)) {
          nexusConnectorIdentifier = (String) nexusRegistryArtifactConfig.getConnectorRef().fetchFinalValue();
        }

        if (isEmpty(repositoryFormat)) {
          repositoryFormat = (String) nexusRegistryArtifactConfig.getRepositoryFormat().fetchFinalValue();
        }

        if (isEmpty(repository)) {
          repository = (String) nexusRegistryArtifactConfig.getRepository().fetchFinalValue();
        }

      } else if (artifactSpecFromService.getSourceType().equals(ArtifactSourceType.NEXUS2_REGISTRY)) {
        Nexus2RegistryArtifactConfig nexus2RegistryArtifactConfig =
            (Nexus2RegistryArtifactConfig) artifactSpecFromService;

        artifactSourceType = ArtifactSourceType.NEXUS2_REGISTRY;

        if (isEmpty(nexusConnectorIdentifier)) {
          nexusConnectorIdentifier = (String) nexus2RegistryArtifactConfig.getConnectorRef().fetchFinalValue();
        }

        if (isEmpty(repositoryFormat)) {
          repositoryFormat = (String) nexus2RegistryArtifactConfig.getRepositoryFormat().fetchFinalValue();
        }

        if (isEmpty(repository)) {
          repository = (String) nexus2RegistryArtifactConfig.getRepository().fetchFinalValue();
        }
      }
    }

    CDYamlExpressionEvaluator yamlExpressionEvaluator =
        baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getYamlExpressionEvaluator();

    nexusConnectorIdentifier =

        getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
            pipelineIdentifier, runtimeInputYaml, nexusConnectorIdentifier, fqnPath, gitEntityBasicInfo, serviceRef,
            yamlExpressionEvaluator)
            .getValue();

    repositoryFormat = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, repositoryFormat, fqnPath, gitEntityBasicInfo, serviceRef,
        yamlExpressionEvaluator)
                           .getValue();

    repository = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, repository, fqnPath, gitEntityBasicInfo, serviceRef,
        yamlExpressionEvaluator)
                     .getValue();

    checkConnectorIdentifier(nexusConnectorIdentifier);

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(nexusConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo =
        featureFlagHelperService.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;
    return nexusResourceService.getGroupIds(accountId, orgIdentifier, projectIdentifier, connectorRef, repositoryFormat,
        repository, artifactSourceType, scopeInfo);
  }

  private Map<String, String> buildContextMap(
      Map<FQN, Object> fqnObjectMap, String stageIdentifier, String serviceId, String environmentId) {
    Map<String, String> contextMap = new HashMap<>();

    for (Map.Entry<FQN, Object> mapEntry : fqnObjectMap.entrySet()) {
      String nodeStageIdentifier = mapEntry.getKey().getStageIdentifier();

      String fieldName = mapEntry.getKey().getFieldName();

      if (stageIdentifier.equals(nodeStageIdentifier) && NGTemplateConstants.GIT_BRANCH.equals(fieldName)
          && mapEntry.getValue() instanceof TextNode) {
        String fqn = mapEntry.getKey().display();

        if (isGitBranchForServiceEnvironment(fqn, YamlTypes.SERVICE_ENTITY, serviceId)) {
          contextMap.put(SERVICE_GIT_BRANCH, ((TextNode) mapEntry.getValue()).asText());
          continue;
        }

        if (isGitBranchForServiceEnvironment(fqn, YamlTypes.ENVIRONMENT_YAML, environmentId)) {
          contextMap.put(ENV_GIT_BRANCH, ((TextNode) mapEntry.getValue()).asText());
          continue;
        }
      }
    }

    return contextMap;
  }

  private boolean isGitBranchForServiceEnvironment(String fqn, String entityType, String entityId) {
    if (YamlTypes.SERVICE_ENTITY.equals(entityType)) {
      // Case1[single-service] -> pipeline.stages.stage[identifier:s1].spec.service.gitBranch.
      if (fqn.contains("service.gitBranch")) {
        return true;
      }

      // Case2[multi-service] ->
      // pipeline.stages.stage[identifier:s1].spec.services.values.[serviceRef:docker].gitBranch.
      if (fqn.contains("services.values") && fqn.contains("serviceRef:" + entityId)) {
        return true;
      }
    }

    if (YamlTypes.ENVIRONMENT_YAML.equals(entityType)) {
      // Case3[single-env] -> pipeline.stages.stage[identifier:s1].spec.environment.gitBranch.
      if (fqn.contains("environment.gitBranch")) {
        return true;
      }

      // Case4[multi-env] ->
      // pipeline.stages.stage[identifier:s1].spec.environments.values.[environmentRef:env].gitBranch.
      if (fqn.contains("environments.values") && fqn.contains("environmentRef:" + entityId)) {
        return true;
      }
    }

    return false;
  }

  @Nullable
  private String resolveEntityIdIfExpression(
      String entityId, String mergedCompleteYaml, EntityRefAndFQN entityRefAndFQN) {
    if (isNotEmpty(entityId) && EngineExpressionEvaluator.hasExpressions(entityId)) {
      CDYamlExpressionEvaluator CDYamlExpressionEvaluator =
          new CDYamlExpressionEvaluator(mergedCompleteYaml, entityRefAndFQN.getEntityFQN(), new ArrayList<>());
      return CDYamlExpressionEvaluator.renderExpression(entityRefAndFQN.getEntityRef());
    }
    return entityId;
  }

  @Nullable
  private String getResolvedEnvironmentId(
      String mergedCompleteYaml, String stageIdentifier, Map<FQN, Object> fqnObjectMap) {
    EntityRefAndFQN environmentRefAndFQN = getEntityRefAndFQN(fqnObjectMap, stageIdentifier, YamlTypes.ENVIRONMENT_REF);
    return resolveEntityIdIfExpression(environmentRefAndFQN.getEntityRef(), mergedCompleteYaml, environmentRefAndFQN);
  }

  public void resolveParameterFieldValues(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String runtimeInputYaml, List<ParameterField<String>> parameterFields, String fqnPath,
      GitEntityFindInfoDTO gitEntityBasicInfo, String serviceId) {
    boolean shouldResolveExpression = false;
    for (ParameterField<String> param : parameterFields) {
      if (isResolvableParameterField(param)) {
        shouldResolveExpression = true;
        break;
      }
    }
    if (!shouldResolveExpression) {
      return;
    }
    String mergedCompleteYaml = "";

    try {
      mergedCompleteYaml = getMergedCompleteYaml(
          accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, runtimeInputYaml, gitEntityBasicInfo);
    } catch (InvalidRequestException invalidRequestException) {
      if (invalidRequestException.getMessage().contains("doesn't exist or has been deleted")) {
        return;
      }
      throw invalidRequestException;
    }
    if (isNotEmpty(mergedCompleteYaml) && TemplateRefHelper.hasTemplateRef(mergedCompleteYaml)) {
      mergedCompleteYaml = applyTemplatesOnGivenYaml(
          accountId, orgIdentifier, projectIdentifier, mergedCompleteYaml, gitEntityBasicInfo, pipelineIdentifier);
    }
    String[] split = fqnPath.split("\\.");
    String stageIdentifier = split[2];
    YamlConfig yamlConfig = new YamlConfig(mergedCompleteYaml);
    Map<FQN, Object> fqnObjectMap = yamlConfig.getFqnToValueMap();

    EntityRefAndFQN serviceRefAndFQN = getEntityRefAndFQN(fqnObjectMap, stageIdentifier, YamlTypes.SERVICE_REF);
    if (isEmpty(serviceId)) {
      // pipelines with inline service definitions
      serviceId = serviceRefAndFQN.getEntityRef();
    }
    serviceId = resolveEntityIdIfExpression(serviceId, mergedCompleteYaml, serviceRefAndFQN);
    // get environment ref
    String environmentId = getResolvedEnvironmentId(mergedCompleteYaml, stageIdentifier, fqnObjectMap);
    List<YamlField> aliasYamlField =
        getAliasYamlFields(accountId, orgIdentifier, projectIdentifier, serviceId, environmentId, new HashMap<>());
    CDYamlExpressionEvaluator CDYamlExpressionEvaluator =
        new CDYamlExpressionEvaluator(mergedCompleteYaml, fqnPath, aliasYamlField);
    for (ParameterField<String> param : parameterFields) {
      String paramValue = (String) param.fetchFinalValue();
      if (isResolvableParameterField(param) && EngineExpressionEvaluator.hasExpressions(paramValue)) {
        param.updateWithValue(CDYamlExpressionEvaluator.renderExpression(paramValue));
      }
    }
  }

  private boolean isResolvableParameterField(ParameterField<String> parameterField) {
    return parameterField.isExpression() && !parameterField.isExecutionInput();
  }

  public ResolvedFieldValueWithYamlExpressionEvaluator getResolvedExpression(String accountId, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, String runtimeInputYaml, String param, String fqnPath,
      GitEntityFindInfoDTO gitEntityBasicInfo, String serviceId, int secretFunctor,
      CDExpressionEvaluator cdExpressionEvaluator) {
    if (EngineExpressionEvaluator.hasExpressions(param)) {
      if (cdExpressionEvaluator == null) {
        cdExpressionEvaluator = getCDExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
            pipelineIdentifier, runtimeInputYaml, fqnPath, gitEntityBasicInfo, serviceId, secretFunctor);
      }
      param = cdExpressionEvaluator.renderExpression(param);
    }
    return ResolvedFieldValueWithYamlExpressionEvaluator.builder()
        .cdExpressionEvaluator(cdExpressionEvaluator)
        .value(param)
        .build();
  }

  public ResolvedFieldValueWithYamlExpressionEvaluator getResolvedFieldValueWithCDExpressionEvaluator(String accountId,
      String orgIdentifier, String projectIdentifier, String pipelineIdentifier, String runtimeInputYaml, String param,
      String fqnPath, GitEntityFindInfoDTO gitEntityBasicInfo, String serviceId, int secretFunctor,
      CDExpressionEvaluator cdExpressionEvaluator) {
    final ParameterField<String> fieldValueParameterField =
        RuntimeInputValuesValidator.getInputSetParameterField(param);
    if (fieldValueParameterField == null) {
      return ResolvedFieldValueWithYamlExpressionEvaluator.builder()
          .cdExpressionEvaluator(cdExpressionEvaluator)
          .value(param)
          .build();
    } else if (!fieldValueParameterField.isExpression()) {
      return ResolvedFieldValueWithYamlExpressionEvaluator.builder()
          .cdExpressionEvaluator(cdExpressionEvaluator)
          .value(fieldValueParameterField.getValue())
          .build();
    } else {
      // this check assumes ui sends -1 as pipeline identifier when pipeline is under construction
      if ("-1".equals(pipelineIdentifier)) {
        throw new InvalidRequestException(String.format(
            "Couldn't resolve artifact image path expression %s, as pipeline has not been saved yet.", param));
      }
      if (cdExpressionEvaluator == null) {
        cdExpressionEvaluator = getCDExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
            pipelineIdentifier, runtimeInputYaml, fqnPath, gitEntityBasicInfo, serviceId, secretFunctor);
      }
      String resolvedFieldValuePath =
          cdExpressionEvaluator.renderExpression(param, ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
      final ParameterField<String> fieldValueParameter =
          RuntimeInputValuesValidator.getInputSetParameterField(resolvedFieldValuePath);
      if (fieldValueParameter == null || fieldValueParameter.isExpression()) {
        return ResolvedFieldValueWithYamlExpressionEvaluator.builder()
            .cdExpressionEvaluator(cdExpressionEvaluator)
            .value(null)
            .build();
      } else {
        return ResolvedFieldValueWithYamlExpressionEvaluator.builder()
            .cdExpressionEvaluator(cdExpressionEvaluator)
            .value(fieldValueParameter.getValue())
            .build();
      }
    }
  }

  CDExpressionEvaluator getCDExpressionEvaluator(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String runtimeInputYaml, String fqnPath, GitEntityFindInfoDTO gitEntityBasicInfo,
      String serviceId, int secretFunctor) {
    String mergedCompleteYaml = getMergedCompleteYaml(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, runtimeInputYaml, gitEntityBasicInfo);
    if (isNotEmpty(mergedCompleteYaml) && TemplateRefHelper.hasTemplateRef(mergedCompleteYaml)) {
      mergedCompleteYaml = applyTemplatesOnGivenYaml(
          accountId, orgIdentifier, projectIdentifier, mergedCompleteYaml, gitEntityBasicInfo, pipelineIdentifier);
    }
    String[] split = fqnPath.split("\\.");
    String stageIdentifier = split[2];
    YamlConfig yamlConfig = new YamlConfig(mergedCompleteYaml);
    Map<FQN, Object> fqnObjectMap = yamlConfig.getFqnToValueMap();
    EntityRefAndFQN serviceRefAndFQN = getEntityRefAndFQN(fqnObjectMap, stageIdentifier, YamlTypes.SERVICE_REF);
    if (isEmpty(serviceId)) {
      // pipelines with inline service definitions
      serviceId = serviceRefAndFQN.getEntityRef();
    }
    serviceId = resolveEntityIdIfExpression(serviceId, mergedCompleteYaml, serviceRefAndFQN);
    // get environment ref
    String environmentId = getResolvedEnvironmentId(mergedCompleteYaml, stageIdentifier, fqnObjectMap);

    // add context needed for fetching git entities
    Map<String, String> contextMap = buildContextMap(fqnObjectMap, stageIdentifier, serviceId, environmentId);

    List<YamlField> aliasYamlField =
        getAliasYamlFields(accountId, orgIdentifier, projectIdentifier, serviceId, environmentId, contextMap);
    return new CDExpressionEvaluator(mergedCompleteYaml, fqnPath, aliasYamlField, secretFunctor,
        featureFlagHelperService.isEnabled(accountId, FeatureName.CDS_USE_SINGLE_QUOTES_IN_SECRET_FUNCTOR));
  }

  /**
   * Returns the serviceRef using stage identifier and fqnToObjectMap.
   * Field name should be serviceRef and fqn should have stage identifier to get the value of serviceRef
   *
   * @param fqnToObjectMap fqn to object map depicting yaml tree as key value pair
   * @param stageIdentifier stage identifier to fetch serviceRef from
   * @return String
   */
  private String getServiceRef(Map<FQN, Object> fqnToObjectMap, String stageIdentifier) {
    for (Map.Entry<FQN, Object> mapEntry : fqnToObjectMap.entrySet()) {
      String nodeStageIdentifier = mapEntry.getKey().getStageIdentifier();
      String fieldName = mapEntry.getKey().getFieldName();
      if (stageIdentifier.equals(nodeStageIdentifier) && YamlTypes.SERVICE_REF.equals(fieldName)
          && mapEntry.getValue() instanceof TextNode) {
        return ((TextNode) mapEntry.getValue()).asText();
      }
    }
    return null;
  }

  private EntityRefAndFQN getEntityRefAndFQN(
      Map<FQN, Object> fqnToObjectMap, String stageIdentifier, String yamlTypes) {
    for (Map.Entry<FQN, Object> mapEntry : fqnToObjectMap.entrySet()) {
      String nodeStageIdentifier = mapEntry.getKey().getStageIdentifier();
      String fieldName = mapEntry.getKey().getFieldName();
      if (stageIdentifier.equals(nodeStageIdentifier) && yamlTypes.equals(fieldName)
          && mapEntry.getValue() instanceof TextNode) {
        return EntityRefAndFQN.builder()
            .entityRef(((TextNode) mapEntry.getValue()).asText())
            .entityFQN(mapEntry.getKey().getExpressionFqn())
            .build();
      }
    }
    return EntityRefAndFQN.builder().build();
  }

  /**
   * Returns the environmentRef using stage identifier and fqnToObjectMap.
   * Field name should be environmentRef and fqn should have stage identifier to get the value of environmentRef
   *
   * @param fqnToObjectMap fqn to object map depicting yaml tree as key value pair
   * @param stageIdentifier stage identifier to fetch serviceRef from
   * @return String
   */
  private String getEnvironmentRef(Map<FQN, Object> fqnToObjectMap, String stageIdentifier) {
    for (Map.Entry<FQN, Object> mapEntry : fqnToObjectMap.entrySet()) {
      String nodeStageIdentifier = mapEntry.getKey().getStageIdentifier();
      String fieldName = mapEntry.getKey().getFieldName();
      if (stageIdentifier.equals(nodeStageIdentifier) && YamlTypes.ENVIRONMENT_REF.equals(fieldName)
          && mapEntry.getValue() instanceof TextNode) {
        return ((TextNode) mapEntry.getValue()).asText();
      }
    }
    return null;
  }

  List<YamlField> getAliasYamlFields(String accountId, String orgIdentifier, String projectIdentifier, String serviceId,
      String environmentId, Map<String, String> contextMap) {
    List<YamlField> yamlFields = new ArrayList<>();
    String serviceGitBranch = contextMap.get(SERVICE_GIT_BRANCH);
    if (isNotEmpty(serviceId)) {
      try (GitXTransientBranchGuard ignore = new GitXTransientBranchGuard(serviceGitBranch)) {
        Optional<ServiceEntity> optionalService;
        ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(accountId, orgIdentifier, projectIdentifier);

        optionalService = serviceEntityService.get(scopeInfo, serviceId, false);
        optionalService.ifPresent(service
            -> yamlFields.add(getYamlField(service.fetchNonEmptyYaml(scopeInfo), YAMLFieldNameConstants.SERVICE)));
      }
    }
    String envGitBranch = contextMap.get(ENV_GIT_BRANCH);
    ScopeInfo scopeInfo = null;
    if (isNotEmpty(environmentId)) {
      try (GitXTransientBranchGuard ignore = new GitXTransientBranchGuard(envGitBranch)) {
        Optional<Environment> optionalEnvironment;
        // Assumes the updated values account for any changes resulting from the project movement
        scopeInfo = scopeInfoService.getScopeInfo(accountId, orgIdentifier, projectIdentifier);
        optionalEnvironment = environmentService.get(scopeInfo, environmentId, false);

        if (optionalEnvironment.isPresent()) {
          // use envGitBranch to fetch env global overrides
          YamlField envYamlField =
              getYamlField(optionalEnvironment.get().fetchNonEmptyYaml(scopeInfo), YAMLFieldNameConstants.ENVIRONMENT);
          addGlobalEnvOverrideYamlField(envYamlField, accountId, orgIdentifier, projectIdentifier, environmentId);
          yamlFields.add(envYamlField);
        }
      }
    }
    return yamlFields;
  }

  List<YamlField> getAliasYamlFields(
      ScopeInfo scopeInfo, String serviceId, String environmentId, Map<String, String> contextMap) {
    List<YamlField> yamlFields = new ArrayList<>();
    String serviceGitBranch = contextMap.get(SERVICE_GIT_BRANCH);
    if (isNotEmpty(serviceId)) {
      try (GitXTransientBranchGuard ignore = new GitXTransientBranchGuard(serviceGitBranch)) {
        Optional<ServiceEntity> optionalService = serviceEntityService.get(scopeInfo, serviceId, false);
        optionalService.ifPresent(service
            -> yamlFields.add(getYamlField(service.fetchNonEmptyYaml(scopeInfo), YAMLFieldNameConstants.SERVICE)));
      }
    }
    String envGitBranch = contextMap.get(ENV_GIT_BRANCH);
    if (isNotEmpty(environmentId)) {
      try (GitXTransientBranchGuard ignore = new GitXTransientBranchGuard(envGitBranch)) {
        Optional<Environment> optionalEnvironment = environmentService.get(scopeInfo, environmentId, false);
        if (optionalEnvironment.isPresent()) {
          // use envGitBranch to fetch env global overrides
          YamlField envYamlField =
              getYamlField(optionalEnvironment.get().fetchNonEmptyYaml(scopeInfo), YAMLFieldNameConstants.ENVIRONMENT);
          addGlobalEnvOverrideYamlField(envYamlField, scopeInfo.getAccountIdentifier(), scopeInfo.getOrgIdentifier(),
              scopeInfo.getProjectIdentifier(), environmentId);
          yamlFields.add(envYamlField);
        }
      }
    }
    return yamlFields;
  }

  private void addGlobalEnvOverrideYamlField(
      YamlField envYamlField, String accountId, String orgIdentifier, String projectIdentifier, String environmentId) {
    try {
      if (!overrideV2ValidationHelper.isOverridesV2Enabled(accountId)) {
        return;
      }
      String envRef =
          IdentifierRefHelper.getRefFromIdentifierOrRef(accountId, orgIdentifier, projectIdentifier, environmentId);
      Map<Scope, NGServiceOverridesEntity> envOverrideMap =
          serviceOverridesServiceV2.getEnvOverride(accountId, orgIdentifier, projectIdentifier, envRef, null, true);
      List<Scope> scopeList = Arrays.asList(Scope.ACCOUNT, Scope.ORG, Scope.PROJECT);
      Map<String, NGVariable> finalVariableMap = new HashMap<>();

      scopeList.forEach(scope -> {
        if (envOverrideMap.containsKey(scope)) {
          ServiceOverridesSpec serviceOverridesSpec =
              serviceOverridesServiceV2.getSpecFromYamlV2(envOverrideMap.get(scope));
          if (isNull(serviceOverridesSpec) || isNull(serviceOverridesSpec.getVariables())) {
            return;
          }
          serviceOverridesSpec.getVariables().forEach(variable -> finalVariableMap.put(variable.getName(), variable));
        }
      });

      List<NGVariable> finalVariablesList = new ArrayList<>(finalVariableMap.values());
      ArrayNode arrayNode = objectMapper.createArrayNode();
      finalVariablesList.forEach(variable -> arrayNode.add(createNodeFromNGVariable(variable)));
      JsonNode jsonNode = envYamlField.getNode().getCurrJsonNode();
      ((ObjectNode) jsonNode).set(VARIABLES, arrayNode);

    } catch (Exception e) {
      log.error("Failed to add global environment overrides for expression resolution", e);
    }
  }

  private JsonNode createNodeFromNGVariable(NGVariable variable) {
    ObjectNode jsonNode = objectMapper.createObjectNode();
    jsonNode.set(VAR_NAME, objectMapper.valueToTree(variable.getName()));
    jsonNode.set(VAR_VALUE, objectMapper.valueToTree(variable.fetchValue().getValue()));
    jsonNode.set(VAR_TYPE, objectMapper.valueToTree(variable.getType()));
    jsonNode.set(VAR_DESCRIPTION, objectMapper.valueToTree(variable.getDescription()));
    return jsonNode;
  }

  private YamlField getYamlField(String yaml, String fieldName) {
    try {
      YamlField yamlField = YamlUtils.readTree(yaml);
      return yamlField.getNode().getField(fieldName);
    } catch (IOException e) {
      throw new InvalidRequestException("Invalid service yaml passed.");
    }
  }
  /**
   * Locates ArtifactConfig in a service entity for a given FQN of type
   * pipeline.stages.s1.spec.service.serviceInputs.serviceDefinition.spec.artifacts.primary.spec.tag
   * pipeline.stages.s1.spec.service.serviceInputs.serviceDefinition.spec.artifacts.sidecars[0].sidecar.spec.tag
   * @return ArtifactConfig
   */
  @NotNull
  public ArtifactConfig locateArtifactInService(
      String accountId, String orgId, String projectId, String serviceRef, String imageTagFqn) {
    return locateArtifactInService(accountId, orgId, projectId, serviceRef, imageTagFqn, null);
  }

  /**
   * Locates ArtifactConfig in a service entity picked from a git branch for a given FQN of type
   * pipeline.stages.s1.spec.service.serviceInputs.serviceDefinition.spec.artifacts.primary.spec.tag
   * pipeline.stages.s1.spec.service.serviceInputs.serviceDefinition.spec.artifacts.sidecars[0].sidecar.spec.tag
   * @return ArtifactConfig
   */
  @NotNull
  public ArtifactConfig locateArtifactInService(
      String accountId, String orgId, String projectId, String serviceRef, String imageTagFqn, String gitBranch) {
    String TEMPLATE_ACCESS_PERMISSION = "core_template_access";
    YamlNode artifactTagLeafNode;
    try (GitXTransientBranchGuard ignore = new GitXTransientBranchGuard(gitBranch)) {
      ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(accountId, orgId, projectId);
      artifactTagLeafNode = serviceEntityService.getYamlNodeForFqn(scopeInfo, serviceRef, imageTagFqn);
    }

    // node from service will have updated details
    YamlNode artifactSpecNode = artifactTagLeafNode.getParentNode().getParentNode();

    // In case of Nexus2&3 configs, coz they have one more spec in their ArtifactConfig like no other artifact source.
    if (artifactSpecNode.getFieldName().equals("spec")) {
      artifactSpecNode = artifactSpecNode.getParentNode();
    }

    if (artifactSpecNode.getParentNode() != null
        && "template".equals(artifactSpecNode.getParentNode().getFieldName())) {
      YamlNode templateNode = artifactSpecNode.getParentNode();
      String templateRef = templateNode.getField("templateRef").getNode().getCurrJsonNode().asText();
      String versionLabel = templateNode.getField("versionLabel") != null
          ? templateNode.getField("versionLabel").getNode().getCurrJsonNode().asText()
          : null;
      String label = templateNode.getField("label") != null
          ? templateNode.getField("label").getNode().getCurrJsonNode().asText()
          : null;

      if (isNotEmpty(templateRef)) {
        IdentifierRef templateIdentifier =
            IdentifierRefHelper.getIdentifierRef(templateRef, accountId, orgId, projectId);
        accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, templateIdentifier.getOrgIdentifier(),
                                                      templateIdentifier.getProjectIdentifier()),
            Resource.of(ResourceTypeConstants.TEMPLATE, templateIdentifier.getIdentifier()),
            TEMPLATE_ACCESS_PERMISSION);
        TemplateResponseDTO response =
            NGRestUtils.getResponse(templateResourceClient.get(templateIdentifier.getIdentifier(),
                templateIdentifier.getAccountIdentifier(), templateIdentifier.getOrgIdentifier(),
                templateIdentifier.getProjectIdentifier(), label, versionLabel, false));
        if (!response.getTemplateEntityType().equals(TemplateEntityType.ARTIFACT_SOURCE_TEMPLATE)) {
          throw new InvalidRequestException(
              String.format("Provided template ref: [%s], version: [%s] is not an artifact source template",
                  templateRef, versionLabel));
        }
        if (isEmpty(response.getYaml())) {
          throw new InvalidRequestException(
              String.format("Received empty artifact source template yaml for template ref: %s, version label: %s",
                  templateRef, versionLabel));
        }
        YamlNode artifactTemplateSpecNode;
        try {
          artifactTemplateSpecNode = YamlNode.fromYamlPath(response.getYaml(), "template/spec");

          String inputSetYaml = YamlUtils.writeYamlString(new YamlField(artifactSpecNode));
          String originalYaml = YamlUtils.writeYamlString(new YamlField(artifactTemplateSpecNode));

          boolean processAdditionalBaseKeys =
              featureFlagHelperService.isEnabled(accountId, FeatureName.PIE_PROCESS_ADDITIONAL_BASE_KEYS);
          String mergedArtifactYamlConfig = InputSetMergeUtility.mergeRuntimeInputValuesIntoOriginalYamlForArrayNode(
              originalYaml, inputSetYaml, processAdditionalBaseKeys);

          artifactSpecNode = YamlUtils.readTree(mergedArtifactYamlConfig).getNode();
        } catch (IOException e) {
          throw new InvalidRequestException("Cannot read spec from the artifact source template");
        }
      }
    }
    final ArtifactInternalDTO artifactDTO;
    try {
      artifactDTO = YamlUtils.read(artifactSpecNode.toString(), ArtifactInternalDTO.class);
    } catch (IOException e) {
      throw new InvalidRequestException("Unable to read artifact spec in service yaml", e);
    }

    return artifactDTO.spec;
  }

  public static class ArtifactInternalDTO {
    @JsonProperty("type") public ArtifactSourceType sourceType;
    @JsonProperty("spec")
    @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
    public ArtifactConfig spec;
  }

  public NexusResponseDTO getBuildDetails(String nexusConnectorIdentifier, String repositoryName, String repositoryPort,
      String artifactPath, String repositoryFormat, String artifactRepositoryUrl, String orgIdentifier,
      String projectIdentifier, String groupId, String artifactId, String extension, String classifier,
      String packageName, String pipelineIdentifier, String fqnPath, GitEntityFindInfoDTO gitEntityBasicInfo,
      String runtimeInputYaml, String serviceRef, String accountId, String group,
      YamlExpressionEvaluatorWithContext baseEvaluatorWithContext) {
    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = locateArtifactInService(accountId, orgIdentifier,
          projectIdentifier, serviceRef, fqnPath,
          baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getContextMap().get(SERVICE_GIT_BRANCH));
      NexusRegistryArtifactConfig nexusRegistryArtifactConfig = (NexusRegistryArtifactConfig) artifactSpecFromService;
      switch (nexusRegistryArtifactConfig.getRepositoryFormat().getValue()) {
        case NexusConstant.DOCKER:
          NexusRegistryDockerConfig nexusRegistryDockerConfig =
              (NexusRegistryDockerConfig) nexusRegistryArtifactConfig.getNexusRegistryConfigSpec();
          if (nexusRegistryDockerConfig != null) {
            if (isEmpty(repositoryPort)) {
              repositoryPort = (String) nexusRegistryDockerConfig.getRepositoryPort().fetchFinalValue();
            }

            if (isEmpty(artifactPath)) {
              artifactPath = (String) nexusRegistryDockerConfig.getArtifactPath().fetchFinalValue();
            }

            if (isEmpty(artifactRepositoryUrl)) {
              artifactRepositoryUrl = (String) nexusRegistryDockerConfig.getRepositoryUrl().fetchFinalValue();
            }
          }
          break;
        case NexusConstant.NPM:
          NexusRegistryNpmConfig nexusRegistryNpmConfig =
              (NexusRegistryNpmConfig) nexusRegistryArtifactConfig.getNexusRegistryConfigSpec();
          if (nexusRegistryNpmConfig != null) {
            if (isEmpty(packageName)) {
              packageName = (String) nexusRegistryNpmConfig.getPackageName().fetchFinalValue();
            }
          }
          break;
        case NexusConstant.NUGET:
          NexusRegistryNugetConfig nexusRegistryNugetConfig =
              (NexusRegistryNugetConfig) nexusRegistryArtifactConfig.getNexusRegistryConfigSpec();
          if (nexusRegistryNugetConfig != null) {
            if (isEmpty(packageName)) {
              packageName = (String) nexusRegistryNugetConfig.getPackageName().fetchFinalValue();
            }
          }
          break;
        case NexusConstant.MAVEN:
          NexusRegistryMavenConfig nexusRegistryMavenConfig =
              (NexusRegistryMavenConfig) nexusRegistryArtifactConfig.getNexusRegistryConfigSpec();
          if (nexusRegistryMavenConfig != null) {
            if (isEmpty(artifactId)) {
              artifactId = (String) nexusRegistryMavenConfig.getArtifactId().fetchFinalValue();
            }
            if (isEmpty(groupId)) {
              groupId = (String) nexusRegistryMavenConfig.getGroupId().fetchFinalValue();
            }
            if (isEmpty(classifier)) {
              classifier = (String) nexusRegistryMavenConfig.getClassifier().fetchFinalValue();
            }
            if (isEmpty(extension)) {
              extension = (String) nexusRegistryMavenConfig.getExtension().fetchFinalValue();
            }
          }
          break;
        case NexusConstant.RAW:
          NexusRegistryRawConfig nexusRegistryRawConfig =
              (NexusRegistryRawConfig) nexusRegistryArtifactConfig.getNexusRegistryConfigSpec();
          if (nexusRegistryRawConfig != null) {
            if (isEmpty(group)) {
              group = (String) nexusRegistryRawConfig.getGroup().fetchFinalValue();
            }
          }
          break;
        default:
          throw new NotFoundException(String.format(
              "Repository Format [%s] is not supported", nexusRegistryArtifactConfig.getRepositoryFormat().getValue()));
      }

      if (isEmpty(repositoryName)) {
        repositoryName = (String) nexusRegistryArtifactConfig.getRepository().fetchFinalValue();
      }
      if (isEmpty(repositoryFormat)) {
        repositoryFormat = (String) nexusRegistryArtifactConfig.getRepositoryFormat().fetchFinalValue();
      }

      if (isEmpty(nexusConnectorIdentifier)) {
        nexusConnectorIdentifier = nexusRegistryArtifactConfig.getConnectorRef().getValue();
      }
    }
    CDYamlExpressionEvaluator yamlExpressionEvaluator =
        baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getYamlExpressionEvaluator();

    nexusConnectorIdentifier = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, runtimeInputYaml, nexusConnectorIdentifier, fqnPath, gitEntityBasicInfo,
        serviceRef, yamlExpressionEvaluator)
                                   .getValue();

    checkConnectorIdentifier(nexusConnectorIdentifier);

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(nexusConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    checkConnectorAccess(connectorRef);

    groupId = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, groupId, fqnPath, gitEntityBasicInfo, serviceRef, yamlExpressionEvaluator)
                  .getValue();

    classifier = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, classifier, fqnPath, gitEntityBasicInfo, serviceRef,
        yamlExpressionEvaluator)
                     .getValue();

    extension = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, extension, fqnPath, gitEntityBasicInfo, serviceRef,
        yamlExpressionEvaluator)
                    .getValue();

    artifactRepositoryUrl = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, runtimeInputYaml, artifactRepositoryUrl, fqnPath, gitEntityBasicInfo,
        serviceRef, yamlExpressionEvaluator)
                                .getValue();
    artifactId = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, artifactId, fqnPath, gitEntityBasicInfo, serviceRef,
        yamlExpressionEvaluator)
                     .getValue();
    repositoryPort = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, repositoryPort, fqnPath, gitEntityBasicInfo, serviceRef,
        yamlExpressionEvaluator)
                         .getValue();
    packageName = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, packageName, fqnPath, gitEntityBasicInfo, serviceRef,
        yamlExpressionEvaluator)
                      .getValue();
    group = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, group, fqnPath, gitEntityBasicInfo, serviceRef, yamlExpressionEvaluator)
                .getValue();
    repositoryName = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, repositoryName, fqnPath, gitEntityBasicInfo, serviceRef,
        yamlExpressionEvaluator)
                         .getValue();
    artifactPath = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, artifactPath, fqnPath, gitEntityBasicInfo, serviceRef,
        yamlExpressionEvaluator)
                       .getValue();
    ScopeInfo scopeInfo =
        featureFlagHelperService.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;
    return nexusResourceService.getBuildDetails(connectorRef, repositoryName, repositoryPort, artifactPath,
        repositoryFormat, artifactRepositoryUrl, orgIdentifier, projectIdentifier, groupId, artifactId, extension,
        classifier, packageName, group, scopeInfo);
  }

  public NexusResponseDTO getBuildDetailsNexus2(String nexusConnectorIdentifier, String repositoryName,
      String repositoryPort, String artifactPath, String repositoryFormat, String artifactRepositoryUrl,
      String orgIdentifier, String projectIdentifier, String groupId, String artifactId, String extension,
      String classifier, String packageName, String pipelineIdentifier, String fqnPath,
      GitEntityFindInfoDTO gitEntityBasicInfo, String runtimeInputYaml, String serviceRef, String accountId,
      String group, YamlExpressionEvaluatorWithContext baseEvaluatorWithContext) {
    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = locateArtifactInService(accountId, orgIdentifier,
          projectIdentifier, serviceRef, fqnPath,
          baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getContextMap().get(SERVICE_GIT_BRANCH));
      Nexus2RegistryArtifactConfig nexus2RegistryArtifactConfig =
          (Nexus2RegistryArtifactConfig) artifactSpecFromService;
      switch (nexus2RegistryArtifactConfig.getRepositoryFormat().getValue()) {
        case NexusConstant.NPM:
          NexusRegistryNpmConfig nexusRegistryNpmConfig =
              (NexusRegistryNpmConfig) nexus2RegistryArtifactConfig.getNexusRegistryConfigSpec();
          packageName =
              isEmpty(packageName) ? (String) nexusRegistryNpmConfig.getPackageName().fetchFinalValue() : packageName;
          break;
        case NexusConstant.NUGET:
          NexusRegistryNugetConfig nexusRegistryNugetConfig =
              (NexusRegistryNugetConfig) nexus2RegistryArtifactConfig.getNexusRegistryConfigSpec();
          packageName =
              isEmpty(packageName) ? (String) nexusRegistryNugetConfig.getPackageName().fetchFinalValue() : packageName;
          break;
        case NexusConstant.MAVEN:
          NexusRegistryMavenConfig nexusRegistryMavenConfig =
              (NexusRegistryMavenConfig) nexus2RegistryArtifactConfig.getNexusRegistryConfigSpec();
          Nexus2MavenDTO nexus2MavenDTO =
              populateNexus2MavenFields(nexusRegistryMavenConfig, groupId, artifactId, classifier, extension);
          groupId = nexus2MavenDTO.getGroupId();
          artifactId = nexus2MavenDTO.getArtifactId();
          classifier = nexus2MavenDTO.getClassifier();
          extension = nexus2MavenDTO.getExtension();
          break;
        default:
          throw new NotFoundException(String.format("Repository Format [%s] is not supported",
              nexus2RegistryArtifactConfig.getRepositoryFormat().getValue()));
      }

      if (isEmpty(repositoryName)) {
        repositoryName = (String) nexus2RegistryArtifactConfig.getRepository().fetchFinalValue();
      }
      if (isEmpty(repositoryFormat)) {
        repositoryFormat = (String) nexus2RegistryArtifactConfig.getRepositoryFormat().fetchFinalValue();
      }
      if (isEmpty(nexusConnectorIdentifier)) {
        nexusConnectorIdentifier = nexus2RegistryArtifactConfig.getConnectorRef().fetchFinalValue().toString();
      }
    }

    CDYamlExpressionEvaluator yamlExpressionEvaluator =
        baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getYamlExpressionEvaluator();

    nexusConnectorIdentifier = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, runtimeInputYaml, nexusConnectorIdentifier, fqnPath, gitEntityBasicInfo,
        serviceRef, yamlExpressionEvaluator)
                                   .getValue();

    checkConnectorIdentifier(nexusConnectorIdentifier);

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(nexusConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    checkConnectorAccess(connectorRef);

    groupId = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, groupId, fqnPath, gitEntityBasicInfo, serviceRef, yamlExpressionEvaluator)
                  .getValue();

    classifier = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, classifier, fqnPath, gitEntityBasicInfo, serviceRef,
        yamlExpressionEvaluator)
                     .getValue();

    extension = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, extension, fqnPath, gitEntityBasicInfo, serviceRef,
        yamlExpressionEvaluator)
                    .getValue();

    artifactRepositoryUrl = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, runtimeInputYaml, artifactRepositoryUrl, fqnPath, gitEntityBasicInfo,
        serviceRef, yamlExpressionEvaluator)
                                .getValue();
    artifactId = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, artifactId, fqnPath, gitEntityBasicInfo, serviceRef,
        yamlExpressionEvaluator)
                     .getValue();
    repositoryPort = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, repositoryPort, fqnPath, gitEntityBasicInfo, serviceRef,
        yamlExpressionEvaluator)
                         .getValue();
    packageName = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, packageName, fqnPath, gitEntityBasicInfo, serviceRef,
        yamlExpressionEvaluator)
                      .getValue();
    group = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, group, fqnPath, gitEntityBasicInfo, serviceRef, yamlExpressionEvaluator)
                .getValue();
    repositoryName = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, repositoryName, fqnPath, gitEntityBasicInfo, serviceRef,
        yamlExpressionEvaluator)
                         .getValue();
    artifactPath = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, artifactPath, fqnPath, gitEntityBasicInfo, serviceRef,
        yamlExpressionEvaluator)
                       .getValue();
    ScopeInfo scopeInfo =
        featureFlagHelperService.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;
    return nexusResourceService.getBuildDetails(connectorRef, repositoryName, repositoryPort, artifactPath,
        repositoryFormat, artifactRepositoryUrl, orgIdentifier, projectIdentifier, groupId, artifactId, extension,
        classifier, packageName, group, scopeInfo);
  }

  public Nexus2MavenDTO populateNexus2MavenFields(NexusRegistryMavenConfig nexusRegistryMavenConfig, String groupId,
      String artifactId, String classifier, String extension) {
    if (isEmpty(groupId) && nexusRegistryMavenConfig.getGroupId() != null) {
      groupId = nexusRegistryMavenConfig.getGroupId().fetchFinalValue().toString();
    }
    if (isEmpty(artifactId) && nexusRegistryMavenConfig.getArtifactId() != null) {
      artifactId = nexusRegistryMavenConfig.getArtifactId().fetchFinalValue().toString();
    }
    if (isEmpty(classifier) && nexusRegistryMavenConfig.getClassifier() != null) {
      classifier = nexusRegistryMavenConfig.getClassifier().getValue();
    }
    if (isEmpty(extension) && nexusRegistryMavenConfig.getExtension() != null) {
      extension = nexusRegistryMavenConfig.getExtension().getValue();
    }

    return Nexus2MavenDTO.builder()
        .groupId(groupId)
        .artifactId(artifactId)
        .classifier(classifier)
        .extension(extension)
        .build();
  }

  public GcrResponseDTO getBuildDetailsV2GCR(String imagePath, String registryHostname, String gcrConnectorIdentifier,
      String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier, String fqnPath,
      String runtimeInputYaml, GitEntityFindInfoDTO gitEntityBasicInfo, String serviceRef) {
    YamlExpressionEvaluatorWithContext baseEvaluatorWithContext = null;

    // remote services can be linked with a specific branch, so we parse the YAML in one go and store the context data
    //  has env git branch and service git branch
    if (isNotEmpty(serviceRef) && isRemoteService(accountId, orgIdentifier, projectIdentifier, serviceRef)) {
      baseEvaluatorWithContext = getYamlExpressionEvaluatorWithContext(accountId, orgIdentifier, projectIdentifier,
          pipelineIdentifier, runtimeInputYaml, fqnPath, gitEntityBasicInfo, serviceRef);
    }
    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = locateArtifactInService(accountId, orgIdentifier,
          projectIdentifier, serviceRef, fqnPath,
          baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getContextMap().get(SERVICE_GIT_BRANCH));
      GcrArtifactConfig gcrArtifactConfig = (GcrArtifactConfig) artifactSpecFromService;
      if (isEmpty(imagePath)) {
        imagePath = (String) gcrArtifactConfig.getImagePath().fetchFinalValue();
      }
      if (isEmpty(registryHostname)) {
        registryHostname = (String) gcrArtifactConfig.getRegistryHostname().fetchFinalValue();
      }

      if (isEmpty(gcrConnectorIdentifier)) {
        gcrConnectorIdentifier = (String) gcrArtifactConfig.getConnectorRef().fetchFinalValue();
      }
    }
    // todo(hinger): resolve other expressions here?
    CDYamlExpressionEvaluator yamlExpressionEvaluator =
        baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getYamlExpressionEvaluator();
    gcrConnectorIdentifier = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, runtimeInputYaml, gcrConnectorIdentifier, fqnPath, gitEntityBasicInfo,
        serviceRef, yamlExpressionEvaluator)
                                 .getValue();

    checkConnectorIdentifier(gcrConnectorIdentifier);

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(gcrConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    checkConnectorAccess(connectorRef);
    imagePath = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, imagePath, fqnPath, gitEntityBasicInfo, serviceRef,
        yamlExpressionEvaluator)
                    .getValue();
    registryHostname = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, registryHostname, fqnPath, gitEntityBasicInfo, serviceRef,
        yamlExpressionEvaluator)
                           .getValue();
    ScopeInfo scopeInfo =
        featureFlagHelperService.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;

    return gcrResourceService.getBuildDetails(
        connectorRef, imagePath, registryHostname, orgIdentifier, projectIdentifier, scopeInfo);
  }

  public GARResponseDTO getBuildDetailsV2GAR(String gcpConnectorIdentifier, String region, String repositoryName,
      String project, String pkg, String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String version, String versionRegex, String fqnPath, String runtimeInputYaml,
      String serviceRef, GitEntityFindInfoDTO gitEntityBasicInfo) {
    YamlExpressionEvaluatorWithContext baseEvaluatorWithContext = null;

    if (StringUtils.isNotBlank(version) && StringUtils.isBlank(versionRegex)) {
      var versionRegexParameterField = RuntimeInputValuesValidator.getInputSetParameterField(version);
      if (versionRegexParameterField != null && checkValidRegexType(versionRegexParameterField)) {
        version = null;
        versionRegex = versionRegexParameterField.getInputSetValidator().getParameters();
      }
    }

    // remote services can be linked with a specific branch, so we parse the YAML in one go and store the context data
    //  has env git branch and service git branch
    if (isNotEmpty(serviceRef) && isRemoteService(accountId, orgIdentifier, projectIdentifier, serviceRef)) {
      baseEvaluatorWithContext = getYamlExpressionEvaluatorWithContext(accountId, orgIdentifier, projectIdentifier,
          pipelineIdentifier, runtimeInputYaml, fqnPath, gitEntityBasicInfo, serviceRef);
    }
    if (StringUtils.isNotBlank(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = locateArtifactInService(accountId, orgIdentifier,
          projectIdentifier, serviceRef, fqnPath,
          baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getContextMap().get(SERVICE_GIT_BRANCH));

      GoogleArtifactRegistryConfig googleArtifactRegistryConfig =
          (GoogleArtifactRegistryConfig) artifactSpecFromService;

      if (isBlank(gcpConnectorIdentifier)) {
        gcpConnectorIdentifier = (String) googleArtifactRegistryConfig.getConnectorRef().fetchFinalValue();
      }

      if (isBlank(region)) {
        region = (String) googleArtifactRegistryConfig.getRegion().fetchFinalValue();
      }

      if (isBlank(repositoryName)) {
        repositoryName = (String) googleArtifactRegistryConfig.getRepositoryName().fetchFinalValue();
      }

      if (isBlank(project)) {
        project = (String) googleArtifactRegistryConfig.getProject().fetchFinalValue();
      }

      if (isBlank(pkg)) {
        pkg = (String) googleArtifactRegistryConfig.getPkg().fetchFinalValue();
      }
    }
    CDYamlExpressionEvaluator yamlExpressionEvaluator =
        baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getYamlExpressionEvaluator();

    // Getting the resolvedConnectorRef
    String resolvedConnectorRef = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, runtimeInputYaml, gcpConnectorIdentifier, fqnPath, gitEntityBasicInfo,
        serviceRef, yamlExpressionEvaluator)
                                      .getValue();

    // Getting the resolvedRegion
    String resolvedRegion = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, runtimeInputYaml, region, fqnPath, gitEntityBasicInfo, serviceRef,
        yamlExpressionEvaluator)
                                .getValue();

    // Getting the resolvedRepositoryName
    String resolvedRepositoryName = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, runtimeInputYaml, repositoryName, fqnPath, gitEntityBasicInfo,
        serviceRef, yamlExpressionEvaluator)
                                        .getValue();

    // Getting the resolvedProject
    String resolvedProject = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, runtimeInputYaml, project, fqnPath, gitEntityBasicInfo, serviceRef,
        yamlExpressionEvaluator)
                                 .getValue();

    // Getting the resolvedPackage
    String resolvedPackage =
        getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
            pipelineIdentifier, runtimeInputYaml, pkg, fqnPath, gitEntityBasicInfo, serviceRef, yamlExpressionEvaluator)
            .getValue();

    checkConnectorIdentifier(resolvedConnectorRef);

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(resolvedConnectorRef, accountId, orgIdentifier, projectIdentifier);
    checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo =
        featureFlagHelperService.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;

    return garResourceService.getBuildDetails(connectorRef, resolvedRegion, resolvedRepositoryName, resolvedProject,
        resolvedPackage, version, versionRegex, orgIdentifier, projectIdentifier, scopeInfo);
  }

  public GARRepositoryDTOList getRepositoriesV2GAR(String gcpConnectorIdentifier, String region, String project,
      String accountId, String orgIdentifier, String pipelineIdentifier, String fqnPath, String serviceRef,
      String runtimeInputYaml, String projectIdentifier, GitEntityFindInfoDTO gitEntityBasicInfo, boolean isServiceV2,
      ScopeInfo scopeInfo) {
    YamlExpressionEvaluatorWithContext baseEvaluatorWithContext = null;

    if (isNotEmpty(serviceRef) && isRemoteService(accountId, orgIdentifier, projectIdentifier, serviceRef)) {
      baseEvaluatorWithContext = getYamlExpressionEvaluatorWithContext(accountId, orgIdentifier, projectIdentifier,
          pipelineIdentifier, runtimeInputYaml, fqnPath, gitEntityBasicInfo, serviceRef);
    }

    if (StringUtils.isNotBlank(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = locateArtifactInService(accountId, orgIdentifier,
          projectIdentifier, serviceRef, fqnPath,
          baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getContextMap().get(SERVICE_GIT_BRANCH));

      GoogleArtifactRegistryConfig googleArtifactRegistryConfig =
          (GoogleArtifactRegistryConfig) artifactSpecFromService;

      if (isBlank(gcpConnectorIdentifier)) {
        gcpConnectorIdentifier = (String) googleArtifactRegistryConfig.getConnectorRef().fetchFinalValue();
      }
      if (isBlank(project)) {
        project = (String) googleArtifactRegistryConfig.getProject().fetchFinalValue();
      }

      if (isBlank(region)) {
        region = (String) googleArtifactRegistryConfig.getRegion().fetchFinalValue();
      }
    }
    CDYamlExpressionEvaluator yamlExpressionEvaluator =
        baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getYamlExpressionEvaluator();

    gcpConnectorIdentifier = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, runtimeInputYaml, gcpConnectorIdentifier, fqnPath, gitEntityBasicInfo,
        serviceRef, yamlExpressionEvaluator)
                                 .getValue();

    region = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, region, fqnPath, gitEntityBasicInfo, serviceRef, yamlExpressionEvaluator)
                 .getValue();

    project = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, project, fqnPath, gitEntityBasicInfo, serviceRef, yamlExpressionEvaluator)
                  .getValue();

    checkConnectorIdentifier(gcpConnectorIdentifier);

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(gcpConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    checkConnectorAccess(connectorRef);

    return getRepositoriesGAR(
        connectorRef, region, project, accountId, orgIdentifier, projectIdentifier, isServiceV2, scopeInfo);
  }

  public GARRepositoryDTOList getRepositoriesGAR(IdentifierRef connectorRef, String region, String project,
      String accountId, String orgIdentifier, String projectIdentifier, boolean isServiceV2, ScopeInfo scopeInfo) {
    long timeStart = System.currentTimeMillis();
    GARRepositoryDTOList buildDetails =
        garResourceService.getRepositories(connectorRef, region, project, orgIdentifier, projectIdentifier, scopeInfo);
    long timeTaken = System.currentTimeMillis() - timeStart;

    List<GarRepositoryDTO> modifiedBuilds = new ArrayList<>();

    if (EmptyPredicate.isNotEmpty(buildDetails.getGarRepositoryDTOList())) {
      int index = buildDetails.getGarRepositoryDTOList().get(0).getRepository().lastIndexOf("/");
      buildDetails.getGarRepositoryDTOList().forEach(repo -> {
        String repoName = repo.getRepository().substring(index + 1);
        if (DOCKER.equals(repo.getFormat())) {
          GarRepositoryDTO modifiedRepo = GarRepositoryDTO.builder()
                                              .repository(repoName)
                                              .format(repo.getFormat())
                                              .createTime(repo.getCreateTime())
                                              .updateTime(repo.getUpdateTime())
                                              .build();
          modifiedBuilds.add(modifiedRepo);
        }
      });
      modifiedBuilds.sort(Comparator.comparing(GarRepositoryDTO::getRepository));
    }

    artifactSourceInstrumentationHelper.sendArtifactApiEvent(ArtifactSourceType.GOOGLE_ARTIFACT_REGISTRY, accountId,
        orgIdentifier, projectIdentifier, FETCH_REPOSITORIES, timeTaken, modifiedBuilds.size(), isServiceV2, true);

    return GARRepositoryDTOList.builder().garRepositoryDTOList(modifiedBuilds).build();
  }

  public GARBuildDetailsDTO getLastSuccessfulBuildV2GAR(String gcpConnectorIdentifier, String region,
      String repositoryName, String project, String pkg, String accountId, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, GarRequestDTO garRequestDTO, String fqnPath,
      String serviceRef, GitEntityFindInfoDTO gitEntityBasicInfo) {
    if (StringUtils.isNotBlank(serviceRef)) {
      final ArtifactConfig artifactSpecFromService =
          locateArtifactInService(accountId, orgIdentifier, projectIdentifier, serviceRef, fqnPath);

      GoogleArtifactRegistryConfig googleArtifactRegistryConfig =
          (GoogleArtifactRegistryConfig) artifactSpecFromService;

      if (isEmpty(garRequestDTO.getVersion())) {
        garRequestDTO.setVersion((String) googleArtifactRegistryConfig.getVersion().fetchFinalValue());
      }

      if (isBlank(gcpConnectorIdentifier)) {
        gcpConnectorIdentifier = (String) googleArtifactRegistryConfig.getConnectorRef().fetchFinalValue();
      }

      if (isBlank(repositoryName)) {
        repositoryName = (String) googleArtifactRegistryConfig.getRepositoryName().fetchFinalValue();
      }

      if (isBlank(region)) {
        region = (String) googleArtifactRegistryConfig.getRegion().fetchFinalValue();
      }

      if (isBlank(pkg)) {
        pkg = (String) googleArtifactRegistryConfig.getPkg().fetchFinalValue();
      }

      if (isBlank(project)) {
        project = (String) googleArtifactRegistryConfig.getProject().fetchFinalValue();
      }

      if (isEmpty(garRequestDTO.getVersionRegex())) {
        garRequestDTO.setVersionRegex((String) googleArtifactRegistryConfig.getVersionRegex().fetchFinalValue());
      }
    }

    repositoryName = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, garRequestDTO.getRuntimeInputYaml(), repositoryName, fqnPath, gitEntityBasicInfo,
        serviceRef, null)
                         .getValue();

    gcpConnectorIdentifier = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, garRequestDTO.getRuntimeInputYaml(), gcpConnectorIdentifier, fqnPath,
        gitEntityBasicInfo, serviceRef, null)
                                 .getValue();

    region = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, garRequestDTO.getRuntimeInputYaml(), region, fqnPath, gitEntityBasicInfo, serviceRef, null)
                 .getValue();

    garRequestDTO.setVersion(getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, garRequestDTO.getRuntimeInputYaml(), garRequestDTO.getVersion(), fqnPath,
        gitEntityBasicInfo, serviceRef, null)
                                 .getValue());

    project = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, garRequestDTO.getRuntimeInputYaml(), project, fqnPath, gitEntityBasicInfo, serviceRef, null)
                  .getValue();

    checkConnectorIdentifier(gcpConnectorIdentifier);

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(gcpConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    checkConnectorAccess(connectorRef);

    pkg = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, garRequestDTO.getRuntimeInputYaml(), pkg, fqnPath, gitEntityBasicInfo, serviceRef, null)
              .getValue();

    garRequestDTO.setVersionRegex(getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, garRequestDTO.getRuntimeInputYaml(), garRequestDTO.getVersionRegex(),
        fqnPath, gitEntityBasicInfo, serviceRef, null)
                                      .getValue());
    ScopeInfo scopeInfo =
        featureFlagHelperService.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;
    return garResourceService.getLastSuccessfulBuild(
        connectorRef, region, repositoryName, project, pkg, garRequestDTO, orgIdentifier, projectIdentifier, scopeInfo);
  }

  public ArtifactoryImagePathsDTO getArtifactoryImagePath(String repositoryType, String artifactoryConnectorIdentifier,
      String accountId, String orgIdentifier, String projectIdentifier, String repository, String fqnPath,
      String runtimeInputYaml, String pipelineIdentifier, String serviceRef, GitEntityFindInfoDTO gitEntityBasicInfo) {
    YamlExpressionEvaluatorWithContext baseEvaluatorWithContext = null;

    // remote services can be linked with a specific branch, so we parse the YAML in one go and store the context data
    //  has env git branch and service git branch
    if (isNotEmpty(serviceRef) && isRemoteService(accountId, orgIdentifier, projectIdentifier, serviceRef)) {
      baseEvaluatorWithContext = getYamlExpressionEvaluatorWithContext(accountId, orgIdentifier, projectIdentifier,
          pipelineIdentifier, runtimeInputYaml, fqnPath, gitEntityBasicInfo, serviceRef);
    }

    if (StringUtils.isNotBlank(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = locateArtifactInService(accountId, orgIdentifier,
          projectIdentifier, serviceRef, fqnPath,
          baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getContextMap().get(SERVICE_GIT_BRANCH));

      ArtifactoryRegistryArtifactConfig artifactoryRegistryArtifactConfig =
          (ArtifactoryRegistryArtifactConfig) artifactSpecFromService;

      if (isBlank(artifactoryConnectorIdentifier)) {
        artifactoryConnectorIdentifier =
            artifactoryRegistryArtifactConfig.getConnectorRef().fetchFinalValue().toString();
      }

      if (isBlank(repository)) {
        repository = artifactoryRegistryArtifactConfig.getRepository().fetchFinalValue().toString();
      }
    }

    CDYamlExpressionEvaluator yamlExpressionEvaluator =
        baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getYamlExpressionEvaluator();

    checkConnectorIdentifier(artifactoryConnectorIdentifier);

    // resolving connectorRef
    IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(
        artifactoryConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    checkConnectorAccess(connectorRef);

    // resolving Repository
    String resolvedRepository = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, runtimeInputYaml, repository, fqnPath, gitEntityBasicInfo, serviceRef,
        yamlExpressionEvaluator)
                                    .getValue();
    ScopeInfo scopeInfo =
        featureFlagHelperService.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;

    return artifactoryResourceService.getImagePaths(
        repositoryType, connectorRef, orgIdentifier, projectIdentifier, resolvedRepository, scopeInfo);
  }

  public List<NexusRepositories> getRepositoriesNexus3(String orgIdentifier, String projectIdentifier,
      String repositoryFormat, String accountId, String pipelineIdentifier, String runtimeInputYaml,
      String nexusConnectorIdentifier, String fqnPath, GitEntityFindInfoDTO gitEntityBasicInfo, String serviceRef,
      YamlExpressionEvaluatorWithContext baseEvaluatorWithContext) {
    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = locateArtifactInService(accountId, orgIdentifier,
          projectIdentifier, serviceRef, fqnPath,
          baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getContextMap().get(SERVICE_GIT_BRANCH));
      NexusRegistryArtifactConfig nexusRegistryArtifactConfig = (NexusRegistryArtifactConfig) artifactSpecFromService;
      if (isEmpty(nexusConnectorIdentifier)) {
        nexusConnectorIdentifier = nexusRegistryArtifactConfig.getConnectorRef().getValue();
      }
      if (isEmpty(repositoryFormat) || repositoryFormat.equalsIgnoreCase("defaultParam")) {
        repositoryFormat = nexusRegistryArtifactConfig.getRepositoryFormat().fetchFinalValue().toString();
      }
    }
    repositoryFormat = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, repositoryFormat, fqnPath, gitEntityBasicInfo, serviceRef,
        baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getYamlExpressionEvaluator())
                           .getValue();

    checkConnectorIdentifier(nexusConnectorIdentifier);

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(nexusConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo =
        featureFlagHelperService.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;
    return nexusResourceService.getRepositories(
        connectorRef, orgIdentifier, projectIdentifier, repositoryFormat, scopeInfo);
  }

  public JenkinsJobDetailsDTO getJenkinsJobDetails(String jenkinsConnectorIdentifier, String accountId,
      String orgIdentifier, String projectIdentifier, String pipelineIdentifier, String parentJobName,
      GitEntityFindInfoDTO gitEntityBasicInfo, String fqnPath, String serviceRef, String runtimeInputYaml) {
    YamlExpressionEvaluatorWithContext baseEvaluatorWithContext = null;

    // remote services can be linked with a specific branch, so we parse the YAML in one go and store the context data
    //  has env git branch and service git branch
    if (isNotEmpty(serviceRef) && isRemoteService(accountId, orgIdentifier, projectIdentifier, serviceRef)) {
      baseEvaluatorWithContext = getYamlExpressionEvaluatorWithContext(accountId, orgIdentifier, projectIdentifier,
          pipelineIdentifier, runtimeInputYaml, fqnPath, gitEntityBasicInfo, serviceRef);
    }
    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = locateArtifactInService(accountId, orgIdentifier,
          projectIdentifier, serviceRef, fqnPath,
          baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getContextMap().get(SERVICE_GIT_BRANCH));
      JenkinsArtifactConfig jenkinsArtifactConfig = (JenkinsArtifactConfig) artifactSpecFromService;
      if (isEmpty(jenkinsConnectorIdentifier)) {
        jenkinsConnectorIdentifier = (String) jenkinsArtifactConfig.getConnectorRef().fetchFinalValue();
      }
    }

    jenkinsConnectorIdentifier =
        getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
            pipelineIdentifier, runtimeInputYaml, jenkinsConnectorIdentifier, fqnPath, gitEntityBasicInfo, serviceRef,
            baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getYamlExpressionEvaluator())
            .getValue();

    checkConnectorIdentifier(jenkinsConnectorIdentifier);

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(jenkinsConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo =
        featureFlagHelperService.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;
    return jenkinsResourceService.getJobDetails(
        connectorRef, orgIdentifier, projectIdentifier, parentJobName, scopeInfo);
  }

  public List<String> getJenkinsArtifactPaths(String jenkinsConnectorIdentifier, String accountId, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, String jobName, GitEntityFindInfoDTO gitEntityBasicInfo,
      String fqnPath, String serviceRef, String runtimeInputYaml) {
    YamlExpressionEvaluatorWithContext baseEvaluatorWithContext = null;

    // remote services can be linked with a specific branch, so we parse the YAML in one go and store the context data
    //  has env git branch and service git branch
    if (isNotEmpty(serviceRef) && isRemoteService(accountId, orgIdentifier, projectIdentifier, serviceRef)) {
      baseEvaluatorWithContext = getYamlExpressionEvaluatorWithContext(accountId, orgIdentifier, projectIdentifier,
          pipelineIdentifier, runtimeInputYaml, fqnPath, gitEntityBasicInfo, serviceRef);
    }
    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = locateArtifactInService(accountId, orgIdentifier,
          projectIdentifier, serviceRef, fqnPath,
          baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getContextMap().get(SERVICE_GIT_BRANCH));
      JenkinsArtifactConfig jenkinsArtifactConfig = (JenkinsArtifactConfig) artifactSpecFromService;
      if (isEmpty(jenkinsConnectorIdentifier)) {
        jenkinsConnectorIdentifier = (String) jenkinsArtifactConfig.getConnectorRef().fetchFinalValue();
      }

      if (isEmpty(jobName)) {
        jobName = (String) jenkinsArtifactConfig.getJobName().fetchFinalValue();
      }
    }
    CDYamlExpressionEvaluator yamlExpressionEvaluator =
        baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getYamlExpressionEvaluator();
    jenkinsConnectorIdentifier = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, runtimeInputYaml, jenkinsConnectorIdentifier, fqnPath,
        gitEntityBasicInfo, serviceRef, yamlExpressionEvaluator)
                                     .getValue();
    jobName = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, jobName, fqnPath, gitEntityBasicInfo, serviceRef, yamlExpressionEvaluator)
                  .getValue();

    checkConnectorIdentifier(jenkinsConnectorIdentifier);

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(jenkinsConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo =
        featureFlagHelperService.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;
    return jenkinsResourceService.getArtifactPath(connectorRef, orgIdentifier, projectIdentifier, jobName, scopeInfo);
  }

  public List<BuildDetails> getJenkinsJobBuildsV2(String jenkinsConnectorIdentifier, String accountId,
      String orgIdentifier, String projectIdentifier, String pipelineIdentifier, String jobName, String artifactPath,
      GitEntityFindInfoDTO gitEntityBasicInfo, String fqnPath, String serviceRef, String runtimeInputYaml) {
    YamlExpressionEvaluatorWithContext baseEvaluatorWithContext = null;

    // remote services can be linked with a specific branch, so we parse the YAML in one go and store the context data
    //  has env git branch and service git branch
    if (isNotEmpty(serviceRef) && isRemoteService(accountId, orgIdentifier, projectIdentifier, serviceRef)) {
      baseEvaluatorWithContext = getYamlExpressionEvaluatorWithContext(accountId, orgIdentifier, projectIdentifier,
          pipelineIdentifier, runtimeInputYaml, fqnPath, gitEntityBasicInfo, serviceRef);
    }
    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = locateArtifactInService(accountId, orgIdentifier,
          projectIdentifier, serviceRef, fqnPath,
          baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getContextMap().get(SERVICE_GIT_BRANCH));
      JenkinsArtifactConfig jenkinsArtifactConfig = (JenkinsArtifactConfig) artifactSpecFromService;
      if (isEmpty(jenkinsConnectorIdentifier)) {
        jenkinsConnectorIdentifier = (String) jenkinsArtifactConfig.getConnectorRef().fetchFinalValue();
      }
      if (isEmpty(jobName)) {
        jobName = (String) jenkinsArtifactConfig.getJobName().fetchFinalValue();
      }
      if (isEmpty(artifactPath)) {
        artifactPath = (String) jenkinsArtifactConfig.getArtifactPath().fetchFinalValue();
      }
    }
    CDYamlExpressionEvaluator yamlExpressionEvaluator =
        baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getYamlExpressionEvaluator();
    jenkinsConnectorIdentifier = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, runtimeInputYaml, jenkinsConnectorIdentifier, fqnPath,
        gitEntityBasicInfo, serviceRef, yamlExpressionEvaluator)
                                     .getValue();
    jobName = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, jobName, fqnPath, gitEntityBasicInfo, serviceRef, yamlExpressionEvaluator)
                  .getValue();
    artifactPath = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, artifactPath, fqnPath, gitEntityBasicInfo, serviceRef,
        yamlExpressionEvaluator)
                       .getValue();

    checkConnectorIdentifier(jenkinsConnectorIdentifier);

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(jenkinsConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo =
        featureFlagHelperService.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;
    return jenkinsResourceService.getBuildForJob(
        connectorRef, orgIdentifier, projectIdentifier, jobName, Arrays.asList(artifactPath), scopeInfo);
  }

  public List<JobDetails.JobParameter> getJenkinsJobParameters(String jenkinsConnectorIdentifier, String accountId,
      String orgIdentifier, String projectIdentifier, String pipelineIdentifier, String jobName,
      GitEntityFindInfoDTO gitEntityBasicInfo, String fqnPath, String serviceRef, String runtimeInputYaml) {
    YamlExpressionEvaluatorWithContext baseEvaluatorWithContext = null;

    // remote services can be linked with a specific branch, so we parse the YAML in one go and store the context data
    //  has env git branch and service git branch
    if (isNotEmpty(serviceRef) && isRemoteService(accountId, orgIdentifier, projectIdentifier, serviceRef)) {
      baseEvaluatorWithContext = getYamlExpressionEvaluatorWithContext(accountId, orgIdentifier, projectIdentifier,
          pipelineIdentifier, runtimeInputYaml, fqnPath, gitEntityBasicInfo, serviceRef);
    }
    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = locateArtifactInService(accountId, orgIdentifier,
          projectIdentifier, serviceRef, fqnPath,
          baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getContextMap().get(SERVICE_GIT_BRANCH));
      JenkinsArtifactConfig jenkinsArtifactConfig = (JenkinsArtifactConfig) artifactSpecFromService;
      if (isEmpty(jenkinsConnectorIdentifier)) {
        jenkinsConnectorIdentifier = (String) jenkinsArtifactConfig.getConnectorRef().fetchFinalValue();
      }

      if (isEmpty(jobName)) {
        jobName = (String) jenkinsArtifactConfig.getJobName().fetchFinalValue();
      }
    }
    CDYamlExpressionEvaluator yamlExpressionEvaluator =
        baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getYamlExpressionEvaluator();
    jenkinsConnectorIdentifier = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, runtimeInputYaml, jenkinsConnectorIdentifier, fqnPath,
        gitEntityBasicInfo, serviceRef, yamlExpressionEvaluator)
                                     .getValue();
    jobName = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, jobName, fqnPath, gitEntityBasicInfo, serviceRef, yamlExpressionEvaluator)
                  .getValue();

    checkConnectorIdentifier(jenkinsConnectorIdentifier);

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(jenkinsConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo =
        featureFlagHelperService.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;
    List<JobDetails> jobDetails =
        jenkinsResourceService.getJobParameters(connectorRef, orgIdentifier, projectIdentifier, jobName, scopeInfo);
    return jobDetails.get(0).getParameters();
  }

  public List<NexusRepositories> getRepositoriesNexus2(String orgIdentifier, String projectIdentifier,
      String repositoryFormat, String accountId, String pipelineIdentifier, String runtimeInputYaml,
      String nexusConnectorIdentifier, String fqnPath, GitEntityFindInfoDTO gitEntityBasicInfo, String serviceRef,
      YamlExpressionEvaluatorWithContext baseEvaluatorWithContext) {
    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = locateArtifactInService(accountId, orgIdentifier,
          projectIdentifier, serviceRef, fqnPath,
          baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getContextMap().get(SERVICE_GIT_BRANCH));
      Nexus2RegistryArtifactConfig nexus2RegistryArtifactConfig =
          (Nexus2RegistryArtifactConfig) artifactSpecFromService;
      if (isEmpty(nexusConnectorIdentifier)) {
        nexusConnectorIdentifier = nexus2RegistryArtifactConfig.getConnectorRef().getValue();
      }
      if (isEmpty(repositoryFormat) || repositoryFormat.equalsIgnoreCase("defaultParam")) {
        repositoryFormat = nexus2RegistryArtifactConfig.getRepositoryFormat().fetchFinalValue().toString();
      }
    }
    repositoryFormat = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, repositoryFormat, fqnPath, gitEntityBasicInfo, serviceRef,
        baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getYamlExpressionEvaluator())
                           .getValue();

    checkConnectorIdentifier(nexusConnectorIdentifier);

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(nexusConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo =
        featureFlagHelperService.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;
    return nexusResourceService.getRepositories(
        connectorRef, orgIdentifier, projectIdentifier, repositoryFormat, scopeInfo);
  }

  public List<BuildDetails> getCustomGetBuildDetails(String arrayPath, String versionPath,
      CustomScriptInfo customScriptInfo, String serviceRef, String accountId, String orgIdentifier,
      String projectIdentifier, String fqnPath, String pipelineIdentifier, GitEntityFindInfoDTO gitEntityBasicInfo) {
    YamlExpressionEvaluatorWithContext baseEvaluatorWithContext = null;

    // remote services can be linked with a specific branch, so we parse the YAML in one go and store the context data
    //  has env git branch and service git branch
    if (isNotEmpty(serviceRef) && isRemoteService(accountId, orgIdentifier, projectIdentifier, serviceRef)) {
      baseEvaluatorWithContext = getYamlExpressionEvaluatorWithContext(accountId, orgIdentifier, projectIdentifier,
          pipelineIdentifier, customScriptInfo.getRuntimeInputYaml(), fqnPath, gitEntityBasicInfo, serviceRef);
    }
    String script = customScriptInfo.getScript();
    List<NGVariable> inputs = customScriptInfo.getInputs();
    List<TaskSelectorYaml> delegateSelector = customScriptInfo.getDelegateSelector();
    int secretFunctor = HashGenerator.generateIntegerHash();
    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = locateArtifactInService(accountId, orgIdentifier,
          projectIdentifier, serviceRef, fqnPath,
          baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getContextMap().get(SERVICE_GIT_BRANCH));
      CustomArtifactConfig customArtifactConfig = (CustomArtifactConfig) artifactSpecFromService;
      if (isEmpty(customScriptInfo.getScript())) {
        if (customArtifactConfig.getScripts() != null
            && customArtifactConfig.getScripts().getFetchAllArtifacts() != null
            && customArtifactConfig.getScripts().getFetchAllArtifacts().getShellScriptBaseStepInfo() != null
            && customArtifactConfig.getScripts().getFetchAllArtifacts().getShellScriptBaseStepInfo().getSource() != null
            && customArtifactConfig.getScripts()
                    .getFetchAllArtifacts()
                    .getShellScriptBaseStepInfo()
                    .getSource()
                    .getSpec()
                != null) {
          io.harness.cdng.artifact.bean.yaml.customartifact.CustomScriptInlineSource customScriptInlineSource =
              (io.harness.cdng.artifact.bean.yaml.customartifact.CustomScriptInlineSource) customArtifactConfig
                  .getScripts()
                  .getFetchAllArtifacts()
                  .getShellScriptBaseStepInfo()
                  .getSource()
                  .getSpec();
          if (customScriptInlineSource.getScript() != null
              && isNotEmpty(customScriptInlineSource.getScript().fetchFinalValue().toString())) {
            script = customScriptInlineSource.getScript().fetchFinalValue().toString();
          }
        }
        if (isEmpty(customScriptInfo.getDelegateSelector())) {
          delegateSelector =
              (List<io.harness.plancreator.steps.TaskSelectorYaml>) customArtifactConfig.getDelegateSelectors()
                  .fetchFinalValue();
        }
      }

      // The UI only sends the inputs that are editable/inline at configuration time. Fixed-value inputs
      // defined directly on the artifact source template are not part of that payload, so they must be
      // merged back in from the resolved template/service config, otherwise the script runs with those
      // variables missing and fails to produce an artifact result. (CDS-128111)
      inputs = mergeCustomArtifactInputs(customArtifactConfig.getInputs(), customScriptInfo.getInputs());

      if (isEmpty(script) || NGExpressionUtils.isRuntimeField(script)) {
        return Collections.emptyList();
      }

      if (isEmpty(arrayPath) && customArtifactConfig.getScripts() != null
          && customArtifactConfig.getScripts().getFetchAllArtifacts() != null
          && customArtifactConfig.getScripts().getFetchAllArtifacts().getArtifactsArrayPath() != null) {
        arrayPath = customArtifactConfig.getScripts()
                        .getFetchAllArtifacts()
                        .getArtifactsArrayPath()
                        .fetchFinalValue()
                        .toString();
      }
      if (isEmpty(versionPath) && customArtifactConfig.getScripts() != null
          && customArtifactConfig.getScripts().getFetchAllArtifacts() != null
          && customArtifactConfig.getScripts().getFetchAllArtifacts().getVersionPath() != null) {
        versionPath =
            customArtifactConfig.getScripts().getFetchAllArtifacts().getVersionPath().fetchFinalValue().toString();
      }
    }

    if (isEmpty(arrayPath) || arrayPath.equalsIgnoreCase("<+input>")) {
      throw new io.harness.exception.HintException("Array path can not be empty");
    }

    if (isEmpty(versionPath) || versionPath.equalsIgnoreCase("<+input>")) {
      throw new io.harness.exception.HintException("Version path can not be empty");
    }
    ResolvedFieldValueWithYamlExpressionEvaluator resolvedFieldValueWithYamlExpressionEvaluator = null;
    ResolvedFieldValueWithYamlExpressionEvaluator resolvedFieldValueWithCDExpressionEvaluator = null;
    Map<String, String> inputVariables = NGVariablesUtils.getStringMapVariables(
        inputs, 0L, featureFlagHelperService.isEnabled(accountId, FeatureName.CDS_USE_SINGLE_QUOTES_IN_SECRET_FUNCTOR));

    CDYamlExpressionEvaluator yamlExpressionEvaluator =
        baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getYamlExpressionEvaluator();

    if (isNotEmpty(customScriptInfo.getRuntimeInputYaml())) {
      resolvedFieldValueWithCDExpressionEvaluator = getResolvedExpression(accountId, orgIdentifier, projectIdentifier,
          pipelineIdentifier, customScriptInfo.getRuntimeInputYaml(), script, fqnPath, gitEntityBasicInfo, serviceRef,
          secretFunctor, null);
      script = resolvedFieldValueWithCDExpressionEvaluator.getValue();

      for (Map.Entry<String, String> entry : inputVariables.entrySet()) {
        resolvedFieldValueWithCDExpressionEvaluator = getResolvedFieldValueWithCDExpressionEvaluator(accountId,
            orgIdentifier, projectIdentifier, pipelineIdentifier, customScriptInfo.getRuntimeInputYaml(),
            entry.getValue(), fqnPath, gitEntityBasicInfo, serviceRef, secretFunctor,
            resolvedFieldValueWithCDExpressionEvaluator.getCdExpressionEvaluator());

        inputVariables.put(entry.getKey(), resolvedFieldValueWithCDExpressionEvaluator.getValue());
      }

      resolvedFieldValueWithYamlExpressionEvaluator = getResolvedFieldValueWithYamlExpressionEvaluator(accountId,
          orgIdentifier, projectIdentifier, pipelineIdentifier, customScriptInfo.getRuntimeInputYaml(), arrayPath,
          fqnPath, gitEntityBasicInfo, serviceRef, yamlExpressionEvaluator);
      arrayPath = resolvedFieldValueWithYamlExpressionEvaluator.getValue();
      resolvedFieldValueWithYamlExpressionEvaluator =
          getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
              pipelineIdentifier, customScriptInfo.getRuntimeInputYaml(), versionPath, fqnPath, gitEntityBasicInfo,
              serviceRef, resolvedFieldValueWithYamlExpressionEvaluator.getYamlExpressionEvaluator());
      versionPath = resolvedFieldValueWithYamlExpressionEvaluator.getValue();
    }

    return customResourceService.getBuilds(script, versionPath, arrayPath, inputVariables, accountId, orgIdentifier,
        projectIdentifier, secretFunctor, delegateSelector);
  }

  /**
   * Merges the inputs resolved from the artifact source template/service config with the inline inputs
   * sent in the request. The template inputs act as the base (so fixed-value inputs defined on the
   * template are always available to the script), and the inline request inputs override them by name
   * (so live/unsaved values typed by the user take precedence).
   */
  private List<NGVariable> mergeCustomArtifactInputs(List<NGVariable> templateInputs, List<NGVariable> requestInputs) {
    if (isEmpty(templateInputs)) {
      return requestInputs;
    }
    if (isEmpty(requestInputs)) {
      return templateInputs;
    }
    Map<String, NGVariable> mergedInputsByName = new LinkedHashMap<>();
    for (NGVariable templateInput : templateInputs) {
      if (templateInput != null && isNotEmpty(templateInput.getName())) {
        mergedInputsByName.put(templateInput.getName(), templateInput);
      }
    }
    for (NGVariable requestInput : requestInputs) {
      if (requestInput != null && isNotEmpty(requestInput.getName())) {
        mergedInputsByName.put(requestInput.getName(), requestInput);
      }
    }
    return new ArrayList<>(mergedInputsByName.values());
  }

  public DockerBuildDetailsDTO getLastSuccessfulBuildV2Docker(String imagePath, String dockerConnectorIdentifier,
      String tag, String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier,
      String fqnPath, GitEntityFindInfoDTO gitEntityBasicInfo, DockerRequestDTO requestDTO, String serviceRef) {
    YamlExpressionEvaluatorWithContext baseEvaluatorWithContext = null;

    if (isNotEmpty(serviceRef) && isRemoteService(accountId, orgIdentifier, projectIdentifier, serviceRef)) {
      baseEvaluatorWithContext = getYamlExpressionEvaluatorWithContext(accountId, orgIdentifier, projectIdentifier,
          pipelineIdentifier, requestDTO.getRuntimeInputYaml(), fqnPath, gitEntityBasicInfo, serviceRef);
    }

    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = locateArtifactInService(accountId, orgIdentifier,
          projectIdentifier, serviceRef, fqnPath,
          baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getContextMap().get(SERVICE_GIT_BRANCH));

      DockerHubArtifactConfig dockerHubArtifactConfig = (DockerHubArtifactConfig) artifactSpecFromService;
      if (isEmpty(imagePath)) {
        imagePath = (String) dockerHubArtifactConfig.getImagePath().fetchFinalValue();
      }
      if (isEmpty(dockerConnectorIdentifier)) {
        dockerConnectorIdentifier = dockerHubArtifactConfig.getConnectorRef().getValue();
      }
      if (isEmpty(requestDTO.getTag())) {
        requestDTO.setTag((String) dockerHubArtifactConfig.getTag().fetchFinalValue());
      }
      if (isEmpty(requestDTO.getTagRegex())) {
        requestDTO.setTagRegex((String) dockerHubArtifactConfig.getTagRegex().fetchFinalValue());
      }
    }

    CDYamlExpressionEvaluator yamlExpressionEvaluator =
        baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getYamlExpressionEvaluator();

    dockerConnectorIdentifier = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, requestDTO.getRuntimeInputYaml(), dockerConnectorIdentifier, fqnPath,
        gitEntityBasicInfo, serviceRef, yamlExpressionEvaluator)
                                    .getValue();
    imagePath = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, requestDTO.getRuntimeInputYaml(), imagePath, fqnPath, gitEntityBasicInfo, serviceRef,
        yamlExpressionEvaluator)
                    .getValue();

    requestDTO.setTag(getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, requestDTO.getRuntimeInputYaml(), requestDTO.getTag(), fqnPath, gitEntityBasicInfo,
        serviceRef, yamlExpressionEvaluator)
                          .getValue());

    requestDTO.setTagRegex(getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, requestDTO.getRuntimeInputYaml(), requestDTO.getTagRegex(), fqnPath, gitEntityBasicInfo,
        serviceRef, yamlExpressionEvaluator)
                               .getValue());

    checkConnectorIdentifier(dockerConnectorIdentifier);

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(dockerConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo =
        featureFlagHelperService.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;

    return dockerResourceService.getSuccessfulBuild(
        connectorRef, imagePath, requestDTO, orgIdentifier, projectIdentifier, scopeInfo);
  }

  public GcrBuildDetailsDTO getSuccessfulBuildV2GCR(String imagePath, String gcrConnectorIdentifier, String accountId,
      String orgIdentifier, String projectIdentifier, String fqnPath, String serviceRef, String pipelineIdentifier,
      GitEntityFindInfoDTO gitEntityBasicInfo, GcrRequestDTO gcrRequestDTO) {
    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService =
          locateArtifactInService(accountId, orgIdentifier, projectIdentifier, serviceRef, fqnPath);
      GcrArtifactConfig gcrArtifactConfig = (GcrArtifactConfig) artifactSpecFromService;
      if (isEmpty(imagePath)) {
        imagePath = (String) gcrArtifactConfig.getImagePath().fetchFinalValue();
      }

      if (isEmpty(gcrRequestDTO.getTag())) {
        gcrRequestDTO.setTag((String) gcrArtifactConfig.getTag().fetchFinalValue());
      }

      if (isEmpty(gcrRequestDTO.getRegistryHostname())) {
        gcrRequestDTO.setRegistryHostname((String) gcrArtifactConfig.getRegistryHostname().fetchFinalValue());
      }

      if (isEmpty(gcrConnectorIdentifier)) {
        gcrConnectorIdentifier = (String) gcrArtifactConfig.getConnectorRef().fetchFinalValue();
      }

      if (isEmpty(gcrRequestDTO.getTagRegex())) {
        gcrRequestDTO.setTagRegex((String) gcrArtifactConfig.getTagRegex().fetchFinalValue());
      }
    }

    imagePath = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, gcrRequestDTO.getRuntimeInputYaml(), imagePath, fqnPath, gitEntityBasicInfo, serviceRef,
        null)
                    .getValue();

    gcrConnectorIdentifier = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, gcrRequestDTO.getRuntimeInputYaml(), gcrConnectorIdentifier, fqnPath,
        gitEntityBasicInfo, serviceRef, null)
                                 .getValue();

    gcrRequestDTO.setTag(getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, gcrRequestDTO.getRuntimeInputYaml(), gcrRequestDTO.getTag(), fqnPath, gitEntityBasicInfo,
        serviceRef, null)
                             .getValue());

    gcrRequestDTO.setRegistryHostname(getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, gcrRequestDTO.getRuntimeInputYaml(), gcrRequestDTO.getRegistryHostname(),
        fqnPath, gitEntityBasicInfo, serviceRef, null)
                                          .getValue());

    gcrRequestDTO.setTagRegex(getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, gcrRequestDTO.getRuntimeInputYaml(), gcrRequestDTO.getTagRegex(),
        fqnPath, gitEntityBasicInfo, serviceRef, null)
                                  .getValue());

    checkConnectorIdentifier(gcrConnectorIdentifier);

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(gcrConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo =
        featureFlagHelperService.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;

    return gcrResourceService.getSuccessfulBuild(
        connectorRef, imagePath, gcrRequestDTO, orgIdentifier, projectIdentifier, scopeInfo);
  }

  public EcrBuildDetailsDTO getLastSuccessfulBuildV2ECR(String registryId, String imagePath,
      String ecrConnectorIdentifier, String accountId, String orgIdentifier, String projectIdentifier, String fqnPath,
      String serviceRef, String pipelineIdentifier, GitEntityFindInfoDTO gitEntityBasicInfo,
      EcrRequestDTO ecrRequestDTO) {
    YamlExpressionEvaluatorWithContext baseEvaluatorWithContext = null;

    if (isNotEmpty(serviceRef) && isRemoteService(accountId, orgIdentifier, projectIdentifier, serviceRef)) {
      baseEvaluatorWithContext = getYamlExpressionEvaluatorWithContext(accountId, orgIdentifier, projectIdentifier,
          pipelineIdentifier, ecrRequestDTO.getRuntimeInputYaml(), fqnPath, gitEntityBasicInfo, serviceRef);
    }

    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = locateArtifactInService(accountId, orgIdentifier,
          projectIdentifier, serviceRef, fqnPath,
          baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getContextMap().get(SERVICE_GIT_BRANCH));

      EcrArtifactConfig ecrArtifactConfig = (EcrArtifactConfig) artifactSpecFromService;

      if (isEmpty(ecrRequestDTO.getTag())) {
        ecrRequestDTO.setTag((String) ecrArtifactConfig.getTag().fetchFinalValue());
      }

      if (isEmpty(ecrConnectorIdentifier)) {
        ecrConnectorIdentifier = (String) ecrArtifactConfig.getConnectorRef().fetchFinalValue();
      }

      if (isEmpty(ecrRequestDTO.getRegion())) {
        ecrRequestDTO.setRegion((String) ecrArtifactConfig.getRegion().fetchFinalValue());
      }

      if (isEmpty(imagePath)) {
        imagePath = (String) ecrArtifactConfig.getImagePath().fetchFinalValue();
      }

      if (isEmpty(registryId) && ParameterField.isNotNull(ecrArtifactConfig.getRegistryId())) {
        registryId = (String) ecrArtifactConfig.getRegistryId().fetchFinalValue();
      }

      if (isEmpty(ecrRequestDTO.getTagRegex())) {
        ecrRequestDTO.setTagRegex((String) ecrArtifactConfig.getTagRegex().fetchFinalValue());
      }
    }

    CDYamlExpressionEvaluator yamlExpressionEvaluator =
        baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getYamlExpressionEvaluator();

    ecrConnectorIdentifier = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, ecrRequestDTO.getRuntimeInputYaml(), ecrConnectorIdentifier, fqnPath,
        gitEntityBasicInfo, serviceRef, yamlExpressionEvaluator)
                                 .getValue();

    ecrRequestDTO.setTag(getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, ecrRequestDTO.getRuntimeInputYaml(), ecrRequestDTO.getTag(), fqnPath, gitEntityBasicInfo,
        serviceRef, yamlExpressionEvaluator)
                             .getValue());

    ecrRequestDTO.setRegion(getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, ecrRequestDTO.getRuntimeInputYaml(), ecrRequestDTO.getRegion(), fqnPath,
        gitEntityBasicInfo, serviceRef, yamlExpressionEvaluator)
                                .getValue());

    imagePath = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, ecrRequestDTO.getRuntimeInputYaml(), imagePath, fqnPath, gitEntityBasicInfo, serviceRef,
        yamlExpressionEvaluator)
                    .getValue();

    registryId = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, ecrRequestDTO.getRuntimeInputYaml(), registryId, fqnPath, gitEntityBasicInfo, serviceRef,
        yamlExpressionEvaluator)
                     .getValue();

    ecrRequestDTO.setTagRegex(getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, ecrRequestDTO.getRuntimeInputYaml(), ecrRequestDTO.getTagRegex(),
        fqnPath, gitEntityBasicInfo, serviceRef, yamlExpressionEvaluator)
                                  .getValue());

    checkConnectorIdentifier(ecrConnectorIdentifier);

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(ecrConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo =
        featureFlagHelperService.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;
    return ecrResourceService.getSuccessfulBuild(
        connectorRef, registryId, imagePath, ecrRequestDTO, orgIdentifier, projectIdentifier, scopeInfo);
  }

  public AcrBuildDetailsDTO getLastSuccessfulBuildV2ACR(String subscriptionId, String registry, String repository,
      String azureConnectorIdentifier, String accountId, String orgIdentifier, String projectIdentifier, String fqnPath,
      String serviceRef, String pipelineIdentifier, GitEntityFindInfoDTO gitEntityBasicInfo,
      AcrRequestDTO acrRequestDTO) {
    YamlExpressionEvaluatorWithContext baseEvaluatorWithContext = null;

    // remote services can be linked with a specific branch, so we parse the YAML in one go and store the context data
    //  has env git branch and service git branch
    if (isNotEmpty(serviceRef) && isRemoteService(accountId, orgIdentifier, projectIdentifier, serviceRef)) {
      baseEvaluatorWithContext = getYamlExpressionEvaluatorWithContext(accountId, orgIdentifier, projectIdentifier,
          pipelineIdentifier, acrRequestDTO.getRuntimeInputYaml(), fqnPath, gitEntityBasicInfo, serviceRef);
    }

    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = locateArtifactInService(accountId, orgIdentifier,
          projectIdentifier, serviceRef, fqnPath,
          baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getContextMap().get(SERVICE_GIT_BRANCH));

      AcrArtifactConfig acrArtifactConfig = (AcrArtifactConfig) artifactSpecFromService;
      resolveParameterFieldValues(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
          acrRequestDTO.getRuntimeInputYaml(), acrArtifactConfig.getStringParameterFields(), fqnPath,
          gitEntityBasicInfo, serviceRef);
      if (isEmpty(registry)) {
        registry = (String) acrArtifactConfig.getRegistry().fetchFinalValue();
      }

      if (isEmpty(subscriptionId)) {
        subscriptionId = (String) acrArtifactConfig.getSubscriptionId().fetchFinalValue();
      }

      if (isEmpty(acrRequestDTO.getTag())) {
        acrRequestDTO.setTag((String) acrArtifactConfig.getTag().fetchFinalValue());
      }

      if (isEmpty(acrRequestDTO.getTagRegex())) {
        acrRequestDTO.setTagRegex((String) acrArtifactConfig.getTagRegex().fetchFinalValue());
      }

      if (isEmpty(azureConnectorIdentifier)) {
        azureConnectorIdentifier = (String) acrArtifactConfig.getConnectorRef().fetchFinalValue();
      }

      if (isEmpty(repository)) {
        repository = (String) acrArtifactConfig.getRepository().fetchFinalValue();
      }
    }

    CDYamlExpressionEvaluator yamlExpressionEvaluator =
        baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getYamlExpressionEvaluator();

    registry = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, acrRequestDTO.getRuntimeInputYaml(), registry, fqnPath, gitEntityBasicInfo, serviceRef,
        yamlExpressionEvaluator)
                   .getValue();

    subscriptionId = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, acrRequestDTO.getRuntimeInputYaml(), subscriptionId, fqnPath, gitEntityBasicInfo,
        serviceRef, yamlExpressionEvaluator)
                         .getValue();

    acrRequestDTO.setTag(getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, acrRequestDTO.getRuntimeInputYaml(), acrRequestDTO.getTag(), fqnPath, gitEntityBasicInfo,
        serviceRef, yamlExpressionEvaluator)
                             .getValue());

    acrRequestDTO.setTagRegex(getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, acrRequestDTO.getRuntimeInputYaml(), acrRequestDTO.getTagRegex(),
        fqnPath, gitEntityBasicInfo, serviceRef, yamlExpressionEvaluator)
                                  .getValue());

    azureConnectorIdentifier = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, acrRequestDTO.getRuntimeInputYaml(), azureConnectorIdentifier, fqnPath,
        gitEntityBasicInfo, serviceRef, yamlExpressionEvaluator)
                                   .getValue();

    repository = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, acrRequestDTO.getRuntimeInputYaml(), repository, fqnPath, gitEntityBasicInfo, serviceRef,
        yamlExpressionEvaluator)
                     .getValue();

    IdentifierRef connectorRef = IdentifierRefHelper.getConnectorIdentifierRef(
        azureConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(
        connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier());

    return acrResourceService.getLastSuccessfulBuild(
        connectorRef, subscriptionId, registry, repository, orgIdentifier, projectIdentifier, acrRequestDTO, scopeInfo);
  }

  public NexusBuildDetailsDTO getLastSuccessfulBuildV2Nexus3(String repository, String repositoryPort,
      String artifactPath, String repositoryFormat, String artifactRepositoryUrl, String nexusConnectorIdentifier,
      String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier, String fqnPath,
      GitEntityFindInfoDTO gitEntityBasicInfo, String serviceRef, NexusRequestDTO nexusRequestDTO) {
    YamlExpressionEvaluatorWithContext baseEvaluatorWithContext = null;

    // remote services can be linked with a specific branch, so we parse the YAML in one go and store the context data
    //  has env git branch and service git branch
    if (isNotEmpty(serviceRef) && isRemoteService(accountId, orgIdentifier, projectIdentifier, serviceRef)) {
      baseEvaluatorWithContext = getYamlExpressionEvaluatorWithContext(accountId, orgIdentifier, projectIdentifier,
          pipelineIdentifier, nexusRequestDTO.getRuntimeInputYaml(), fqnPath, gitEntityBasicInfo, serviceRef);
    }
    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = locateArtifactInService(accountId, orgIdentifier,
          projectIdentifier, serviceRef, fqnPath,
          baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getContextMap().get(SERVICE_GIT_BRANCH));
      NexusRegistryArtifactConfig nexusRegistryArtifactConfig = (NexusRegistryArtifactConfig) artifactSpecFromService;
      if (NexusConstant.DOCKER.equals(nexusRegistryArtifactConfig.getRepositoryFormat().getValue())) {
        NexusRegistryDockerConfig nexusRegistryDockerConfig =
            (NexusRegistryDockerConfig) nexusRegistryArtifactConfig.getNexusRegistryConfigSpec();

        if (isEmpty(artifactRepositoryUrl)) {
          artifactRepositoryUrl = (String) nexusRegistryDockerConfig.getRepositoryUrl().fetchFinalValue();
        }

        if (isEmpty(artifactPath)) {
          artifactPath = (String) nexusRegistryDockerConfig.getArtifactPath().fetchFinalValue();
        }

        if (isEmpty(repositoryFormat)) {
          repositoryFormat = (String) nexusRegistryArtifactConfig.getRepositoryFormat().fetchFinalValue();
        }

        if (isEmpty(nexusConnectorIdentifier)) {
          nexusConnectorIdentifier = (String) nexusRegistryArtifactConfig.getConnectorRef().fetchFinalValue();
        }

        if (isEmpty(repositoryPort)) {
          repositoryPort = (String) nexusRegistryDockerConfig.getRepositoryPort().fetchFinalValue();
        }

        if (isEmpty(nexusRequestDTO.getTag())) {
          nexusRequestDTO.setTag((String) nexusRegistryArtifactConfig.getTag().fetchFinalValue());
        }

        if (isEmpty(nexusRequestDTO.getTagRegex())) {
          nexusRequestDTO.setTagRegex((String) nexusRegistryArtifactConfig.getTagRegex().fetchFinalValue());
        }

        if (isEmpty(repository)) {
          repository = (String) nexusRegistryArtifactConfig.getRepository().fetchFinalValue();
        }

      } else {
        throw new InvalidRequestException("Please select a docker artifact");
      }
    }
    CDYamlExpressionEvaluator yamlExpressionEvaluator =
        baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getYamlExpressionEvaluator();

    nexusConnectorIdentifier = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, nexusRequestDTO.getRuntimeInputYaml(), nexusConnectorIdentifier, fqnPath,
        gitEntityBasicInfo, serviceRef, yamlExpressionEvaluator)
                                   .getValue();
    repositoryPort = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, nexusRequestDTO.getRuntimeInputYaml(), repositoryPort, fqnPath, gitEntityBasicInfo,
        serviceRef, yamlExpressionEvaluator)
                         .getValue();

    artifactRepositoryUrl = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, nexusRequestDTO.getRuntimeInputYaml(), artifactRepositoryUrl, fqnPath,
        gitEntityBasicInfo, serviceRef, yamlExpressionEvaluator)
                                .getValue();

    nexusRequestDTO.setTag(getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, nexusRequestDTO.getRuntimeInputYaml(), nexusRequestDTO.getTag(), fqnPath,
        gitEntityBasicInfo, serviceRef, yamlExpressionEvaluator)
                               .getValue());

    nexusRequestDTO.setTagRegex(getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, nexusRequestDTO.getRuntimeInputYaml(), nexusRequestDTO.getTagRegex(),
        fqnPath, gitEntityBasicInfo, serviceRef, yamlExpressionEvaluator)
                                    .getValue());

    artifactPath = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, nexusRequestDTO.getRuntimeInputYaml(), artifactPath, fqnPath, gitEntityBasicInfo,
        serviceRef, yamlExpressionEvaluator)
                       .getValue();

    repository = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, nexusRequestDTO.getRuntimeInputYaml(), repository, fqnPath, gitEntityBasicInfo, serviceRef,
        yamlExpressionEvaluator)
                     .getValue();

    checkConnectorIdentifier(nexusConnectorIdentifier);

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(nexusConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo =
        featureFlagHelperService.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;
    return nexusResourceService.getSuccessfulBuild(connectorRef, repository, repositoryPort, artifactPath,
        repositoryFormat, artifactRepositoryUrl, nexusRequestDTO, orgIdentifier, projectIdentifier, scopeInfo);
  }

  public ArtifactoryBuildDetailsDTO getLastSuccessfulBuildV2Artifactory(String repository, String artifactPath,
      String repositoryFormat, String artifactRepositoryUrl, String artifactoryConnectorIdentifier, String accountId,
      String orgIdentifier, String projectIdentifier, String pipelineIdentifier, String fqnPath,
      GitEntityFindInfoDTO gitEntityBasicInfo, String serviceRef, ArtifactoryRequestDTO artifactoryRequestDTO) {
    YamlExpressionEvaluatorWithContext baseEvaluatorWithContext = null;

    // remote services can be linked with a specific branch, so we parse the YAML in one go and store the context data
    //  has env git branch and service git branch
    if (isNotEmpty(serviceRef) && isRemoteService(accountId, orgIdentifier, projectIdentifier, serviceRef)) {
      baseEvaluatorWithContext = getYamlExpressionEvaluatorWithContext(accountId, orgIdentifier, projectIdentifier,
          pipelineIdentifier, artifactoryRequestDTO.getRuntimeInputYaml(), fqnPath, gitEntityBasicInfo, serviceRef);
    }

    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = locateArtifactInService(accountId, orgIdentifier,
          projectIdentifier, serviceRef, fqnPath,
          baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getContextMap().get(SERVICE_GIT_BRANCH));

      ArtifactoryRegistryArtifactConfig artifactoryRegistryArtifactConfig =
          (ArtifactoryRegistryArtifactConfig) artifactSpecFromService;
      if (isEmpty(repository)) {
        repository = (String) artifactoryRegistryArtifactConfig.getRepository().fetchFinalValue();
      }
      // There is an overload in this endpoint so to make things clearer:
      // artifactPath is the artifactDirectory for Artifactory Generic
      // artifactPath is the artifactPath for Artifactory Docker
      if (isEmpty(artifactPath)) {
        if (artifactoryRegistryArtifactConfig.getRepositoryFormat().fetchFinalValue().equals("docker")) {
          artifactPath = (String) artifactoryRegistryArtifactConfig.getArtifactPath().fetchFinalValue();
        } else {
          artifactPath = (String) artifactoryRegistryArtifactConfig.getArtifactDirectory().fetchFinalValue();
        }
      }

      if (isEmpty(artifactRepositoryUrl)) {
        artifactRepositoryUrl = (String) artifactoryRegistryArtifactConfig.getRepositoryUrl().fetchFinalValue();
      }

      if (isEmpty(artifactoryConnectorIdentifier)) {
        artifactoryConnectorIdentifier = (String) artifactoryRegistryArtifactConfig.getConnectorRef().fetchFinalValue();
      }
      if (isEmpty(artifactoryRequestDTO.getTagRegex())) {
        artifactoryRequestDTO.setTagRegex((String) artifactoryRegistryArtifactConfig.getTagRegex().fetchFinalValue());
      }

      if (isEmpty(repositoryFormat)) {
        repositoryFormat = (String) artifactoryRegistryArtifactConfig.getRepositoryFormat().fetchFinalValue();
      }

      if (isEmpty(artifactoryRequestDTO.getTag())) {
        artifactoryRequestDTO.setTag((String) artifactoryRegistryArtifactConfig.getTag().fetchFinalValue());
      }
    }

    CDYamlExpressionEvaluator yamlExpressionEvaluator =
        baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getYamlExpressionEvaluator();

    artifactoryConnectorIdentifier = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, artifactoryRequestDTO.getRuntimeInputYaml(),
        artifactoryConnectorIdentifier, fqnPath, gitEntityBasicInfo, serviceRef, yamlExpressionEvaluator)
                                         .getValue();
    repository = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, artifactoryRequestDTO.getRuntimeInputYaml(), repository, fqnPath, gitEntityBasicInfo,
        serviceRef, yamlExpressionEvaluator)
                     .getValue();
    artifactoryRequestDTO.setTag(getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, artifactoryRequestDTO.getRuntimeInputYaml(),
        artifactoryRequestDTO.getTag(), fqnPath, gitEntityBasicInfo, serviceRef, yamlExpressionEvaluator)
                                     .getValue());

    artifactoryRequestDTO.setTagRegex(getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, artifactoryRequestDTO.getRuntimeInputYaml(),
        artifactoryRequestDTO.getTagRegex(), fqnPath, gitEntityBasicInfo, serviceRef, yamlExpressionEvaluator)
                                          .getValue());

    artifactPath = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, artifactoryRequestDTO.getRuntimeInputYaml(), artifactPath, fqnPath, gitEntityBasicInfo,
        serviceRef, yamlExpressionEvaluator)
                       .getValue();

    artifactRepositoryUrl = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, artifactoryRequestDTO.getRuntimeInputYaml(), artifactRepositoryUrl,
        fqnPath, gitEntityBasicInfo, serviceRef, yamlExpressionEvaluator)
                                .getValue();

    checkConnectorIdentifier(artifactoryConnectorIdentifier);

    IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(
        artifactoryConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    checkConnectorAccess(connectorRef);

    ScopeInfo scopeInfo =
        featureFlagHelperService.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;

    return artifactoryResourceService.getSuccessfulBuild(connectorRef, repository, artifactPath, repositoryFormat,
        artifactRepositoryUrl, artifactoryRequestDTO, orgIdentifier, projectIdentifier, scopeInfo);
  }

  public AcrResponseDTO getBuildDetailsV2ACR(String subscriptionId, String registry, String repository,
      String azureConnectorIdentifier, String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String fqnPath, GitEntityFindInfoDTO gitEntityBasicInfo, String runtimeInputYaml,
      String serviceRef) {
    YamlExpressionEvaluatorWithContext baseEvaluatorWithContext = null;

    // remote services can be linked with a specific branch, so we parse the YAML in one go and store the context data
    //  has env git branch and service git branch
    if (isNotEmpty(serviceRef) && isRemoteService(accountId, orgIdentifier, projectIdentifier, serviceRef)) {
      baseEvaluatorWithContext = getYamlExpressionEvaluatorWithContext(accountId, orgIdentifier, projectIdentifier,
          pipelineIdentifier, runtimeInputYaml, fqnPath, gitEntityBasicInfo, serviceRef);
    }

    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = locateArtifactInService(accountId, orgIdentifier,
          projectIdentifier, serviceRef, fqnPath,
          baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getContextMap().get(SERVICE_GIT_BRANCH));

      AcrArtifactConfig acrArtifactConfig = (AcrArtifactConfig) artifactSpecFromService;
      resolveParameterFieldValues(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, runtimeInputYaml,
          acrArtifactConfig.getStringParameterFields(), fqnPath, gitEntityBasicInfo, serviceRef);
      if (isEmpty(subscriptionId)) {
        subscriptionId = (String) acrArtifactConfig.getSubscriptionId().fetchFinalValue();
      }
      if (isEmpty(registry)) {
        registry = (String) acrArtifactConfig.getRegistry().fetchFinalValue();
      }
      if (isEmpty(repository)) {
        repository = (String) acrArtifactConfig.getRepository().fetchFinalValue();
      }
      if (isEmpty(azureConnectorIdentifier)) {
        azureConnectorIdentifier = acrArtifactConfig.getConnectorRef().getValue();
      }
    }

    CDYamlExpressionEvaluator yamlExpressionEvaluator =
        baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getYamlExpressionEvaluator();

    subscriptionId = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, subscriptionId, fqnPath, gitEntityBasicInfo, serviceRef,
        yamlExpressionEvaluator)
                         .getValue();

    registry = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, registry, fqnPath, gitEntityBasicInfo, serviceRef,
        yamlExpressionEvaluator)
                   .getValue();

    repository = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, repository, fqnPath, gitEntityBasicInfo, serviceRef,
        yamlExpressionEvaluator)
                     .getValue();

    azureConnectorIdentifier = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, runtimeInputYaml, azureConnectorIdentifier, fqnPath, gitEntityBasicInfo,
        serviceRef, yamlExpressionEvaluator)
                                   .getValue();

    IdentifierRef connectorRef = IdentifierRefHelper.getConnectorIdentifierRef(
        azureConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(
        connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier());

    return acrResourceService.getBuildDetails(
        connectorRef, subscriptionId, registry, repository, orgIdentifier, projectIdentifier, scopeInfo);
  }

  public AcrRepositoriesDTO getAzureRepositoriesV3(String azureConnectorIdentifier, String accountId,
      String orgIdentifier, String projectIdentifier, String pipelineIdentifier, String subscriptionId, String registry,
      String fqnPath, GitEntityFindInfoDTO gitEntityBasicInfo, String runtimeInputYaml, String serviceRef) {
    YamlExpressionEvaluatorWithContext baseEvaluatorWithContext = null;

    // remote services can be linked with a specific branch, so we parse the YAML in one go and store the context data
    //  has env git branch and service git branch
    if (isNotEmpty(serviceRef) && isRemoteService(accountId, orgIdentifier, projectIdentifier, serviceRef)) {
      baseEvaluatorWithContext = getYamlExpressionEvaluatorWithContext(accountId, orgIdentifier, projectIdentifier,
          pipelineIdentifier, runtimeInputYaml, fqnPath, gitEntityBasicInfo, serviceRef);
    }

    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = locateArtifactInService(accountId, orgIdentifier,
          projectIdentifier, serviceRef, fqnPath,
          baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getContextMap().get(SERVICE_GIT_BRANCH));

      AcrArtifactConfig acrArtifactConfig = (AcrArtifactConfig) artifactSpecFromService;
      resolveParameterFieldValues(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, runtimeInputYaml,
          acrArtifactConfig.getStringParameterFields(), fqnPath, gitEntityBasicInfo, serviceRef);
      if (isEmpty(azureConnectorIdentifier)) {
        azureConnectorIdentifier = acrArtifactConfig.getConnectorRef().getValue();
      }
      if (isEmpty(subscriptionId)) {
        subscriptionId = acrArtifactConfig.getSubscriptionId().getValue();
      }
      if (isEmpty(registry)) {
        registry = acrArtifactConfig.getRegistry().getValue();
      }
    }

    CDYamlExpressionEvaluator yamlExpressionEvaluator =
        baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getYamlExpressionEvaluator();

    subscriptionId = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, subscriptionId, fqnPath, gitEntityBasicInfo, serviceRef,
        yamlExpressionEvaluator)
                         .getValue();

    registry = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, registry, fqnPath, gitEntityBasicInfo, serviceRef,
        yamlExpressionEvaluator)
                   .getValue();

    azureConnectorIdentifier = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, runtimeInputYaml, azureConnectorIdentifier, fqnPath, gitEntityBasicInfo,
        serviceRef, yamlExpressionEvaluator)
                                   .getValue();

    IdentifierRef connectorRef = IdentifierRefHelper.getConnectorIdentifierRef(
        azureConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(
        connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier());

    return acrResourceService.getRepositories(
        connectorRef, orgIdentifier, projectIdentifier, subscriptionId, registry, scopeInfo);
  }

  public AcrRegistriesDTO getAzureContainerRegisteriesV3(String azureConnectorIdentifier, String accountId,
      String orgIdentifier, String projectIdentifier, String pipelineIdentifier, String subscriptionId, String fqnPath,
      GitEntityFindInfoDTO gitEntityBasicInfo, String runtimeInputYaml, String serviceRef) {
    YamlExpressionEvaluatorWithContext baseEvaluatorWithContext = null;

    // remote services can be linked with a specific branch, so we parse the YAML in one go and store the context data
    //  has env git branch and service git branch
    if (isNotEmpty(serviceRef) && isRemoteService(accountId, orgIdentifier, projectIdentifier, serviceRef)) {
      baseEvaluatorWithContext = getYamlExpressionEvaluatorWithContext(accountId, orgIdentifier, projectIdentifier,
          pipelineIdentifier, runtimeInputYaml, fqnPath, gitEntityBasicInfo, serviceRef);
    }

    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = locateArtifactInService(accountId, orgIdentifier,
          projectIdentifier, serviceRef, fqnPath,
          baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getContextMap().get(SERVICE_GIT_BRANCH));

      AcrArtifactConfig acrArtifactConfig = (AcrArtifactConfig) artifactSpecFromService;
      resolveParameterFieldValues(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, runtimeInputYaml,
          acrArtifactConfig.getStringParameterFields(), fqnPath, gitEntityBasicInfo, serviceRef);
      if (isEmpty(azureConnectorIdentifier)) {
        azureConnectorIdentifier = acrArtifactConfig.getConnectorRef().getValue();
      }
      if (isEmpty(subscriptionId)) {
        subscriptionId = acrArtifactConfig.getSubscriptionId().getValue();
      }
    }

    CDYamlExpressionEvaluator yamlExpressionEvaluator =
        baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getYamlExpressionEvaluator();

    subscriptionId = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, subscriptionId, fqnPath, gitEntityBasicInfo, serviceRef,
        yamlExpressionEvaluator)
                         .getValue();

    azureConnectorIdentifier = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, runtimeInputYaml, azureConnectorIdentifier, fqnPath, gitEntityBasicInfo,
        serviceRef, yamlExpressionEvaluator)
                                   .getValue();

    IdentifierRef connectorRef = IdentifierRefHelper.getConnectorIdentifierRef(
        azureConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(
        connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier());

    return acrResourceService.getRegistries(connectorRef, orgIdentifier, projectIdentifier, subscriptionId, scopeInfo);
  }

  public AzureSubscriptionsDTO getAzureSubscriptionV2(String azureConnectorIdentifier, String accountId,
      String orgIdentifier, String projectIdentifier, String pipelineIdentifier, String fqnPath,
      GitEntityFindInfoDTO gitEntityBasicInfo, String runtimeInputYaml, String serviceRef) {
    YamlExpressionEvaluatorWithContext baseEvaluatorWithContext = null;

    // remote services can be linked with a specific branch, so we parse the YAML in one go and store the context data
    //  has env git branch and service git branch
    if (isNotEmpty(serviceRef) && isRemoteService(accountId, orgIdentifier, projectIdentifier, serviceRef)) {
      baseEvaluatorWithContext = getYamlExpressionEvaluatorWithContext(accountId, orgIdentifier, projectIdentifier,
          pipelineIdentifier, runtimeInputYaml, fqnPath, gitEntityBasicInfo, serviceRef);
    }

    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = locateArtifactInService(accountId, orgIdentifier,
          projectIdentifier, serviceRef, fqnPath,
          baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getContextMap().get(SERVICE_GIT_BRANCH));

      AcrArtifactConfig acrArtifactConfig = (AcrArtifactConfig) artifactSpecFromService;
      if (isEmpty(azureConnectorIdentifier)) {
        resolveParameterFieldValues(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, runtimeInputYaml,
            acrArtifactConfig.getStringParameterFields(), fqnPath, gitEntityBasicInfo, serviceRef);
        azureConnectorIdentifier = acrArtifactConfig.getConnectorRef().getValue();
      }
    }

    CDYamlExpressionEvaluator yamlExpressionEvaluator =
        baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getYamlExpressionEvaluator();

    azureConnectorIdentifier = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, runtimeInputYaml, azureConnectorIdentifier, fqnPath, gitEntityBasicInfo,
        serviceRef, yamlExpressionEvaluator)
                                   .getValue();

    IdentifierRef connectorRef = IdentifierRefHelper.getConnectorIdentifierRef(
        azureConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(
        connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier());

    return azureResourceService.getSubscriptions(connectorRef, orgIdentifier, projectIdentifier, scopeInfo);
  }

  public List<FilePathDTO> getFilePathsForServiceV2S3(String region, String awsConnectorIdentifier, String bucketName,
      String filePathRegex, String fileFilter, String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String fqnPath, String runtimeInputYaml, String serviceRef,
      GitEntityFindInfoDTO gitEntityBasicInfo) {
    YamlExpressionEvaluatorWithContext baseEvaluatorWithContext = null;

    if (isNotEmpty(serviceRef) && isRemoteService(accountId, orgIdentifier, projectIdentifier, serviceRef)) {
      baseEvaluatorWithContext = getYamlExpressionEvaluatorWithContext(accountId, orgIdentifier, projectIdentifier,
          pipelineIdentifier, runtimeInputYaml, fqnPath, gitEntityBasicInfo, serviceRef);
    }

    if (StringUtils.isNotBlank(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = locateArtifactInService(accountId, orgIdentifier,
          projectIdentifier, serviceRef, fqnPath,
          baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getContextMap().get(SERVICE_GIT_BRANCH));

      AmazonS3ArtifactConfig amazonS3ArtifactConfig = (AmazonS3ArtifactConfig) artifactSpecFromService;

      if (StringUtils.isBlank(region)) {
        region = (String) amazonS3ArtifactConfig.getRegion().fetchFinalValue();
      }

      if (StringUtils.isBlank(awsConnectorIdentifier)) {
        awsConnectorIdentifier = (String) amazonS3ArtifactConfig.getConnectorRef().fetchFinalValue();
      }

      if (StringUtils.isBlank(bucketName)) {
        bucketName = (String) amazonS3ArtifactConfig.getBucketName().fetchFinalValue();
      }
      if (StringUtils.isBlank(fileFilter) && ParameterField.isNotNull(amazonS3ArtifactConfig.getFileFilter())) {
        fileFilter = (String) amazonS3ArtifactConfig.getFileFilter().fetchFinalValue();
      }
    }

    CDYamlExpressionEvaluator yamlExpressionEvaluator =
        baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getYamlExpressionEvaluator();

    // Getting the resolved region in case of expressions
    String resolvedRegion = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, runtimeInputYaml, region, fqnPath, gitEntityBasicInfo, serviceRef,
        yamlExpressionEvaluator)
                                .getValue();

    // Getting the resolved awsConnectorIdentifier in case of expressions
    String resolvedAwsConnectorIdentifier = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, runtimeInputYaml, awsConnectorIdentifier, fqnPath, gitEntityBasicInfo,
        serviceRef, yamlExpressionEvaluator)
                                                .getValue();

    // Getting the resolved bucketName in case of expressions
    String resolvedBucketName = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, runtimeInputYaml, bucketName, fqnPath, gitEntityBasicInfo, serviceRef,
        yamlExpressionEvaluator)
                                    .getValue();
    // Getting the resolved fileFilter in case of expressions
    fileFilter = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, fileFilter, fqnPath, gitEntityBasicInfo, serviceRef,
        yamlExpressionEvaluator)
                     .getValue();

    checkConnectorIdentifier(resolvedAwsConnectorIdentifier);

    // Common logic in case of ServiceV1 and ServiceV2
    IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(
        resolvedAwsConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    checkConnectorAccess(connectorRef);

    ScopeInfo scopeInfo =
        featureFlagHelperService.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;

    List<BuildDetails> s3ArtifactPaths = s3ResourceService.getFilePaths(
        connectorRef, resolvedRegion, resolvedBucketName, fileFilter, orgIdentifier, projectIdentifier, scopeInfo);

    List<FilePathDTO> artifactPathDTOS = new ArrayList<>();

    for (BuildDetails s : s3ArtifactPaths) {
      FilePathDTO artifactPathDTO = FilePathDTO.builder().buildDetails(s).build();
      artifactPathDTOS.add(artifactPathDTO);
    }

    return artifactPathDTOS;
  }

  public List<FilePathDTO> getFilePathsS3(String region, String awsConnectorIdentifier, String bucketName,
      String filePathRegex, String fileFilter, String accountId, String orgIdentifier, String projectIdentifier) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(awsConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo =
        featureFlagHelperService.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;
    List<BuildDetails> s3ArtifactPaths = s3ResourceService.getFilePaths(
        connectorRef, region, bucketName, fileFilter, orgIdentifier, projectIdentifier, scopeInfo);

    List<FilePathDTO> artifactPathDTOS = new ArrayList<>();

    for (BuildDetails s : s3ArtifactPaths) {
      FilePathDTO artifactPathDTO = FilePathDTO.builder().buildDetails(s).build();
      artifactPathDTOS.add(artifactPathDTO);
    }

    return artifactPathDTOS;
  }

  public List<BucketResponseDTO> getBucketsV2WithServiceV2S3(String region, String awsConnectorIdentifier,
      String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier, String fqnPath,
      String runtimeInputYaml, String serviceRef, GitEntityFindInfoDTO gitEntityBasicInfo) {
    YamlExpressionEvaluatorWithContext baseEvaluatorWithContext = null;

    if (isNotEmpty(serviceRef) && isRemoteService(accountId, orgIdentifier, projectIdentifier, serviceRef)) {
      baseEvaluatorWithContext = getYamlExpressionEvaluatorWithContext(accountId, orgIdentifier, projectIdentifier,
          pipelineIdentifier, runtimeInputYaml, fqnPath, gitEntityBasicInfo, serviceRef);
    }

    if (StringUtils.isNotBlank(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = locateArtifactInService(accountId, orgIdentifier,
          projectIdentifier, serviceRef, fqnPath,
          baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getContextMap().get(SERVICE_GIT_BRANCH));

      AmazonS3ArtifactConfig amazonS3ArtifactConfig = (AmazonS3ArtifactConfig) artifactSpecFromService;

      if (StringUtils.isBlank(region)) {
        region = (String) amazonS3ArtifactConfig.getRegion().fetchFinalValue();
      }

      if (StringUtils.isBlank(awsConnectorIdentifier)) {
        awsConnectorIdentifier = (String) amazonS3ArtifactConfig.getConnectorRef().fetchFinalValue();
      }
    }

    CDYamlExpressionEvaluator yamlExpressionEvaluator =
        baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getYamlExpressionEvaluator();

    // Getting the resolved region in case of expressions
    String resolvedRegion = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, runtimeInputYaml, region, fqnPath, gitEntityBasicInfo, serviceRef,
        yamlExpressionEvaluator)
                                .getValue();

    // Getting the resolved awsConnectorIdentifier in case of expressions
    String resolvedAwsConnectorIdentifier = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, runtimeInputYaml, awsConnectorIdentifier, fqnPath, gitEntityBasicInfo,
        serviceRef, yamlExpressionEvaluator)
                                                .getValue();

    checkConnectorIdentifier(resolvedAwsConnectorIdentifier);

    // Common logic in case of ServiceV1 and ServiceV2
    IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(
        resolvedAwsConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    checkConnectorAccess(connectorRef);

    ScopeInfo scopeInfo =
        featureFlagHelperService.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;
    Map<String, String> s3Buckets =
        s3ResourceService.getBuckets(connectorRef, resolvedRegion, orgIdentifier, projectIdentifier, scopeInfo);

    List<String> bucketList = new ArrayList<>(s3Buckets.values());

    List<BucketResponseDTO> bucketResponse = new ArrayList<>();

    for (String s : bucketList) {
      BucketResponseDTO bucket = BucketResponseDTO.builder().bucketName(s).build();
      bucketResponse.add(bucket);
    }

    return bucketResponse;
  }

  public List<BucketResponseDTO> getBucketsV2S3(
      String region, String awsConnectorIdentifier, String accountId, String orgIdentifier, String projectIdentifier) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(awsConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo =
        featureFlagHelperService.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;
    Map<String, String> s3Buckets =
        s3ResourceService.getBuckets(connectorRef, region, orgIdentifier, projectIdentifier, scopeInfo);

    List<String> bucketList = new ArrayList<>(s3Buckets.values());

    List<BucketResponseDTO> bucketResponse = new ArrayList<>();

    for (String s : bucketList) {
      BucketResponseDTO bucket = BucketResponseDTO.builder().bucketName(s).build();

      bucketResponse.add(bucket);
    }

    return bucketResponse;
  }

  public Map<String, String> getBucketsInManifestsS3(String region, String awsConnectorIdentifier, String accountId,
      String orgIdentifier, String projectIdentifier, String pipelineIdentifier, String fqnPath,
      String runtimeInputYaml, String serviceRef) {
    if (isNotEmpty(serviceRef)) {
      S3StoreConfig storeConfig = (S3StoreConfig) bucketsResourceUtils.locateStoreConfigInService(
          accountId, orgIdentifier, projectIdentifier, serviceRef, fqnPath);

      if (StringUtils.isBlank(region)) {
        region = storeConfig.getRegion().getValue();
      }

      if (StringUtils.isBlank(awsConnectorIdentifier)) {
        awsConnectorIdentifier = storeConfig.getConnectorRef().getValue();
      }
    }

    // Getting the resolved region in case of expressions
    String resolvedRegion = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, runtimeInputYaml, region, fqnPath, null, serviceRef, null)
                                .getValue();

    // Getting the resolved region in case of expressions
    String resolvedConnectorIdentifier =
        getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
            pipelineIdentifier, runtimeInputYaml, awsConnectorIdentifier, fqnPath, null, serviceRef, null)
            .getValue();

    checkConnectorIdentifier(resolvedConnectorIdentifier);

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(resolvedConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo =
        featureFlagHelperService.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;
    return s3ResourceService.getBuckets(connectorRef, resolvedRegion, orgIdentifier, projectIdentifier, scopeInfo);
  }

  public Map<String, String> getBucketsS3(String region, String awsConnectorIdentifier, String accountId,
      String orgIdentifier, String projectIdentifier, String fqnPath, String serviceRef) {
    if (isNotEmpty(serviceRef)) {
      S3StoreConfig storeConfig = (S3StoreConfig) bucketsResourceUtils.locateStoreConfigInService(
          accountId, orgIdentifier, projectIdentifier, serviceRef, fqnPath);
      if (isEmpty(region)) {
        region = storeConfig.getRegion().getValue();
      }
      if (isEmpty(awsConnectorIdentifier)) {
        awsConnectorIdentifier = storeConfig.getConnectorRef().getValue();
      }
    }
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(awsConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo =
        featureFlagHelperService.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;
    return s3ResourceService.getBuckets(connectorRef, region, orgIdentifier, projectIdentifier, scopeInfo);
  }

  public GARPackageDTOList getPackagesV2GAR(String gcpConnectorIdentifier, String region, String repositoryName,
      String project, String accountId, String orgIdentifier, String pipelineIdentifier, String fqnPath,
      String serviceRef, String runtimeInputYaml, String projectIdentifier, GitEntityFindInfoDTO gitEntityBasicInfo,
      Boolean isServiceV2) {
    YamlExpressionEvaluatorWithContext baseEvaluatorWithContext = null;

    if (isNotEmpty(serviceRef) && isRemoteService(accountId, orgIdentifier, projectIdentifier, serviceRef)) {
      baseEvaluatorWithContext = getYamlExpressionEvaluatorWithContext(accountId, orgIdentifier, projectIdentifier,
          pipelineIdentifier, runtimeInputYaml, fqnPath, gitEntityBasicInfo, serviceRef);
    }

    if (StringUtils.isNotBlank(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = locateArtifactInService(accountId, orgIdentifier,
          projectIdentifier, serviceRef, fqnPath,
          baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getContextMap().get(SERVICE_GIT_BRANCH));

      GoogleArtifactRegistryConfig googleArtifactRegistryConfig =
          (GoogleArtifactRegistryConfig) artifactSpecFromService;

      if (isBlank(gcpConnectorIdentifier)) {
        gcpConnectorIdentifier = (String) googleArtifactRegistryConfig.getConnectorRef().fetchFinalValue();
      }
      if (isBlank(project)) {
        project = (String) googleArtifactRegistryConfig.getProject().fetchFinalValue();
      }
      if (isBlank(region)) {
        region = (String) googleArtifactRegistryConfig.getRegion().fetchFinalValue();
      }
      if (isBlank(repositoryName)) {
        repositoryName = (String) googleArtifactRegistryConfig.getRepositoryName().fetchFinalValue();
      }
    }
    CDYamlExpressionEvaluator yamlExpressionEvaluator =
        baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getYamlExpressionEvaluator();

    gcpConnectorIdentifier = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, runtimeInputYaml, gcpConnectorIdentifier, fqnPath, gitEntityBasicInfo,
        serviceRef, yamlExpressionEvaluator)
                                 .getValue();

    project = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, project, fqnPath, gitEntityBasicInfo, serviceRef, yamlExpressionEvaluator)
                  .getValue();

    region = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, region, fqnPath, gitEntityBasicInfo, serviceRef, yamlExpressionEvaluator)
                 .getValue();

    repositoryName = getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, repositoryName, fqnPath, gitEntityBasicInfo, serviceRef,
        yamlExpressionEvaluator)
                         .getValue();

    checkConnectorIdentifier(gcpConnectorIdentifier);

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(gcpConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo =
        featureFlagHelperService.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;

    return getPackagesGAR(connectorRef, region, repositoryName, project, accountId, orgIdentifier, projectIdentifier,
        isServiceV2, scopeInfo);
  }

  public GARPackageDTOList getPackagesGAR(IdentifierRef connectorRef, String region, String repositoryName,
      String project, String accountId, String orgIdentifier, String projectIdentifier, Boolean isServiceV2,
      ScopeInfo scopeInfo) {
    long timeStart = System.currentTimeMillis();
    GARPackageDTOList buildDetails = garResourceService.getPackages(
        connectorRef, region, repositoryName, project, orgIdentifier, projectIdentifier, scopeInfo);
    long timeTaken = System.currentTimeMillis() - timeStart;

    List<GARPackageDTO> modifiedBuilds = new ArrayList<>();

    if (EmptyPredicate.isNotEmpty(buildDetails.getGarPackageDTOList())) {
      int index = buildDetails.getGarPackageDTOList().get(0).getPackageName().lastIndexOf("/");
      buildDetails.getGarPackageDTOList().forEach(repo -> {
        String packageName = repo.getPackageName().substring(index + 1);

        // Decoding the package name to handle encoded slashes
        packageName = URLDecoderUtility.getDecodedString(packageName);

        GARPackageDTO modifiedRepo = GARPackageDTO.builder()
                                         .packageName(packageName)
                                         .createTime(repo.getCreateTime())
                                         .updateTime(repo.getUpdateTime())
                                         .build();
        modifiedBuilds.add(modifiedRepo);
      });
      modifiedBuilds.sort(Comparator.comparing(GARPackageDTO::getPackageName));
    }

    artifactSourceInstrumentationHelper.sendArtifactApiEvent(ArtifactSourceType.GOOGLE_ARTIFACT_REGISTRY, accountId,
        orgIdentifier, projectIdentifier, FETCH_PACKAGES, timeTaken, modifiedBuilds.size(), isServiceV2, true);

    return GARPackageDTOList.builder().garPackageDTOList(modifiedBuilds).build();
  }

  private void checkConnectorIdentifier(String connectorIdentifier) {
    if (EmptyPredicate.isEmpty(connectorIdentifier)) {
      throw new InvalidIdentifierRefException("Connector identifier must not be empty");
    }
  }

  @Data
  @Builder
  private static class EntityRefAndFQN {
    String entityRef;
    String entityFQN;
  }
}
