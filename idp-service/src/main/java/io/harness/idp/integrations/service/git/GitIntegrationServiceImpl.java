/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.integrations.service.git;

import static io.harness.NGConstants.HARNESS_SECRET_MANAGER_IDENTIFIER;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.git.GitClientHelper.isBitBucketSAAS;
import static io.harness.idp.common.CommonUtils.findObjectByName;
import static io.harness.idp.common.CommonUtils.removeScopeFromIdentifier;
import static io.harness.idp.common.CommonUtils.replaceAccountScopeFromIdentifier;
import static io.harness.idp.common.Constants.AZURE_REPO;
import static io.harness.idp.common.Constants.BITBUCKET_CLOUD;
import static io.harness.idp.common.Constants.BITBUCKET_SERVER;
import static io.harness.idp.common.Constants.GITHUB;
import static io.harness.idp.common.Constants.GITLAB;
import static io.harness.idp.common.Constants.HARNESS;
import static io.harness.idp.common.Constants.IDP_PREFIX;
import static io.harness.idp.integrations.service.git.GitIntegrationOps.FAILED;
import static io.harness.idp.integrations.utils.Constants.HCR_CONNECTOR_IDENTIFIER;
import static io.harness.idp.integrations.utils.Constants.IDP_GIT_INTEGRATION_MANAGED_HCR;
import static io.harness.idp.integrations.utils.Constants.IDP_MANAGED_HCR_WRITE;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.NGResourceFilterConstants;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.authorization.AuthorizationServiceHeader;
import io.harness.beans.DecryptableEntity;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.connector.utils.HarnessCodeConnectorUtils;
import io.harness.delegate.beans.connector.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.GithubConnectorDTO;
import io.harness.delegate.beans.connector.HarnessAuthenticationDTO;
import io.harness.delegate.beans.connector.HarnessConnectorDTO;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubAppSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.harness.HarnessApiAccessDTO;
import io.harness.delegate.beans.connector.scm.harness.HarnessApiAccessType;
import io.harness.delegate.beans.connector.scm.harness.HarnessHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.harness.HarnessHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.harness.HarnessJWTTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.harness.HarnessUsernameTokenDTO;
import io.harness.delegate.beans.connector.utils.ConnectorType;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.exception.UnsupportedOperationException;
import io.harness.http.HttpHeaderConfig;
import io.harness.idp.catalog.entities.GitReferencedCatalogEntity;
import io.harness.idp.common.HarnessCodeRepoConfig;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.common.YamlUtils;
import io.harness.idp.common.delegateselectors.cache.DelegateSelectorsCache;
import io.harness.idp.configmanager.entities.AppConfigEntity;
import io.harness.idp.configmanager.repositories.AppConfigRepository;
import io.harness.idp.configmanager.service.ConfigManagerService;
import io.harness.idp.configmanager.utils.ConfigType;
import io.harness.idp.envvariable.service.BackstageEnvVariableService;
import io.harness.idp.events.producers.SetupUsageProducer;
import io.harness.idp.integrations.beans.common.DiscoverEntitiesDTO;
import io.harness.idp.integrations.beans.common.ImportedEntitiesDTO;
import io.harness.idp.integrations.entities.IntegrationEntity;
import io.harness.idp.integrations.entities.IntegrationEntity.Integration;
import io.harness.idp.integrations.entities.IntegrationEntity.ParentType;
import io.harness.idp.integrations.entities.git.GitIntegrationEntity;
import io.harness.idp.integrations.events.GitIntegrationCreateEvent;
import io.harness.idp.integrations.events.GitIntegrationDeleteEvent;
import io.harness.idp.integrations.events.GitIntegrationUpdateEvent;
import io.harness.idp.integrations.mapper.git.GitIntegrationMapper;
import io.harness.idp.integrations.repositories.IntegrationEntityRepository;
import io.harness.idp.integrations.service.common.CommonIntegrationService;
import io.harness.idp.proxy.envvariable.ProxyEnvVariableServiceWrapper;
import io.harness.idp.status.enums.StatusType;
import io.harness.idp.status.service.StatusInfoService;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretRequestWrapper;
import io.harness.ng.core.dto.secrets.SecretTextSpecDTO;
import io.harness.outbox.api.OutboxService;
import io.harness.remote.client.NGRestUtils;
import io.harness.secretmanagerclient.SecretType;
import io.harness.secretmanagerclient.ValueType;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.security.dto.ServicePrincipal;
import io.harness.spec.server.idp.v1.model.AppConfig;
import io.harness.spec.server.idp.v1.model.BackstageEnvConfigVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvSecretVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariable;
import io.harness.spec.server.idp.v1.model.BaseIntegrationRequest;
import io.harness.spec.server.idp.v1.model.GitIntegrationRequest;
import io.harness.spec.server.idp.v1.model.GitIntegrationResponse;
import io.harness.spec.server.idp.v1.model.ReadValidationDetails;
import io.harness.spec.server.idp.v1.model.SaveDiscoverEntitiesRequest;
import io.harness.spec.server.idp.v1.model.StatusInfo;
import io.harness.spec.server.idp.v1.model.StatusInfoV2;
import io.harness.spec.server.idp.v1.model.UnlinkIntegrationEntitiesResponse;
import io.harness.spec.server.idp.v1.model.WriteValidationDetails;
import io.harness.springdata.TransactionHelper;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.json.JSONObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
public class GitIntegrationServiceImpl
    implements CommonIntegrationService<GitIntegrationRequest, GitIntegrationResponse> {
  static final Map<ParentType, String> PARENT_TYPE_MAP = Map.of(ParentType.AZURE, AZURE_REPO,
      ParentType.BITBUCKET_CLOUD, BITBUCKET_CLOUD, ParentType.BITBUCKET_SERVER, BITBUCKET_SERVER, ParentType.GITHUB,
      GITHUB, ParentType.GITLAB, GITLAB, ParentType.HARNESS_CODE_REPO, HARNESS);
  public static final Pattern AZURE_CLOUD_PATTERN = Pattern.compile("dev.azure.com");
  public static final Pattern AZURE_ORG_PATTERN = Pattern.compile("https://dev\\.azure\\.com/([^/]+)/.*");

  @Inject @Named("harnessCodeRepoConfig") private HarnessCodeRepoConfig harnessCodeRepoConfig;
  @Inject @Named("PRIVILEGED") private SecretManagerClientService ngSecretService;
  @Inject IdpCommonService idpCommonService;
  @Inject ConnectorResourceClient connectorResourceClient;
  @Inject HarnessCodeConnectorUtils harnessCodeConnectorUtils;
  @Inject AzureIntegrationOpsImpl azureIntegrationService;
  @Inject BitbucketCloudIntegrationOpsImpl bitbucketCloudIntegrationService;
  @Inject BitbucketServerIntegrationOpsImpl bitbucketServerIntegrationService;
  @Inject GithubIntegrationOpsImpl githubIntegrationService;
  @Inject GitlabIntegrationOpsImpl gitlabIntegrationService;
  @Inject HarnessCodeRepoIntegrationOpsImpl harnessCodeRepoIntegrationService;
  @Inject ConfigManagerService configManagerService;
  @Inject BackstageEnvVariableService backstageEnvVariableService;
  @Inject StatusInfoService statusInfoService;
  @Inject IntegrationEntityRepository integrationEntityRepository;
  @Inject ProxyEnvVariableServiceWrapper proxyEnvVariableServiceWrapper;
  @Inject DelegateSelectorsCache delegateSelectorsCache;
  @Inject TransactionHelper transactionHelper;
  @Inject SetupUsageProducer setupUsageProducer;
  @Inject OutboxService outboxService;
  @Inject AppConfigRepository appConfigRepository;

  @Override
  public GitIntegrationResponse save(
      String accountIdentifier, GitIntegrationRequest request, boolean dryRun, boolean writeValidation) {
    validateRequest(request, dryRun, writeValidation);
    String connectorIdentifier = request.getConnectorIdentifier();
    connectorIdentifier = replaceAccountScopeFromIdentifier(connectorIdentifier);
    ConnectorInfoDTO connectorInfoDTO = connectorInfoDTO(accountIdentifier, connectorIdentifier, request);
    String gitIntegrationType = getGitIntegrationType(connectorInfoDTO);
    GitIntegrationOps<GitIntegrationEntity, ConnectorConfigDTO> gitIntegrationOps =
        (GitIntegrationOps<GitIntegrationEntity, ConnectorConfigDTO>) getServiceForGitIntegration(gitIntegrationType);
    GitIntegrationEntity integrationEntity = gitIntegrationOps.prepare(connectorInfoDTO);
    if (dryRun && writeValidation) {
      performWriteDryRun(accountIdentifier, request.getWriteValidationDetails(), connectorInfoDTO, gitIntegrationOps,
          integrationEntity);
      return GitIntegrationMapper.toResponse(integrationEntity);
    }
    prepareForReadDryRun(request, integrationEntity);
    if (!dryRun) {
      validateForCreate(gitIntegrationOps, integrationEntity);
    }
    return saveUpdateInternal(
        accountIdentifier, dryRun, connectorInfoDTO, gitIntegrationOps, integrationEntity, null, true, true);
  }

  public String getRepoUrl(ConnectorInfoDTO connectorInfoDTO) {
    String gitIntegrationType = getGitIntegrationType(connectorInfoDTO);
    GitIntegrationOps<GitIntegrationEntity, ConnectorConfigDTO> gitIntegrationOps =
        (GitIntegrationOps<GitIntegrationEntity, ConnectorConfigDTO>) getServiceForGitIntegration(gitIntegrationType);
    return gitIntegrationOps.getRepoUrl(connectorInfoDTO.getConnectorConfig());
  }

  public String getConnectionType(ConnectorInfoDTO connectorInfoDTO) {
    String gitIntegrationType = getGitIntegrationType(connectorInfoDTO);
    GitIntegrationOps<GitIntegrationEntity, ConnectorConfigDTO> gitIntegrationOps =
        (GitIntegrationOps<GitIntegrationEntity, ConnectorConfigDTO>) getServiceForGitIntegration(gitIntegrationType);
    return gitIntegrationOps.getGitConnectionType(connectorInfoDTO.getConnectorConfig());
  }

  @Override
  public GitIntegrationResponse update(
      String accountIdentifier, String identifier, GitIntegrationRequest request, boolean dryRun) {
    validateRequest(request, dryRun, false);
    IntegrationEntity existingGitIntegrationEntity = getByAccountAndIdentifier(accountIdentifier, identifier);
    String connectorIdentifier = request.getConnectorIdentifier();
    connectorIdentifier = replaceAccountScopeFromIdentifier(connectorIdentifier);
    ConnectorInfoDTO connectorInfoDTO = getConnectorInfo(accountIdentifier, null, null, connectorIdentifier);
    String gitIntegrationType = getGitIntegrationType(connectorInfoDTO);
    validateForUpdate(existingGitIntegrationEntity.getParentType(), gitIntegrationType);

    // Validate host changes and GitHub App ID
    GitIntegrationOps<GitIntegrationEntity, ConnectorConfigDTO> gitIntegrationOps =
        (GitIntegrationOps<GitIntegrationEntity, ConnectorConfigDTO>) getServiceForGitIntegration(gitIntegrationType);
    GitIntegrationEntity integrationEntity = gitIntegrationOps.prepare(connectorInfoDTO);
    String newHost = integrationEntity.getHost();
    validateHostChangeWithEnabledPlugins(accountIdentifier, connectorIdentifier, newHost);
    validateGithubAppId(connectorInfoDTO);
    integrationEntity.setId(existingGitIntegrationEntity.getId());
    integrationEntity.setCreatedAt(existingGitIntegrationEntity.getCreatedAt());
    prepareForReadDryRun(request, integrationEntity);
    return saveUpdateInternal(accountIdentifier, dryRun, connectorInfoDTO, gitIntegrationOps, integrationEntity,
        (GitIntegrationEntity) existingGitIntegrationEntity, true, true);
  }

  @Override
  public GitIntegrationResponse saveOrUpdate(String accountIdentifier, GitIntegrationRequest request) {
    String connectorIdentifier = request.getConnectorIdentifier();
    connectorIdentifier = replaceAccountScopeFromIdentifier(connectorIdentifier);
    ConnectorInfoDTO connectorInfoDTO = getConnectorInfo(accountIdentifier, null, null, connectorIdentifier);
    String gitIntegrationType = getGitIntegrationType(connectorInfoDTO);
    GitIntegrationOps<GitIntegrationEntity, ConnectorConfigDTO> gitIntegrationOps =
        (GitIntegrationOps<GitIntegrationEntity, ConnectorConfigDTO>) getServiceForGitIntegration(gitIntegrationType);
    GitIntegrationEntity integrationEntity = gitIntegrationOps.prepare(connectorInfoDTO);
    Optional<IntegrationEntity> optionalGitIntegrationEntity =
        integrationEntityRepository.findByAccountIdentifierAndParentTypeAndSubTypeAndAdditionalIndexer(
            accountIdentifier, integrationEntity.getParentType(), integrationEntity.getSubType(),
            integrationEntity.getAdditionalIndexer());
    GitIntegrationEntity existingGitIntegrationEntity = null;
    if (optionalGitIntegrationEntity.isPresent()) {
      existingGitIntegrationEntity = (GitIntegrationEntity) optionalGitIntegrationEntity.get();
      validateForUpdate(existingGitIntegrationEntity.getParentType(), gitIntegrationType);
      integrationEntity.setId(existingGitIntegrationEntity.getId());
      integrationEntity.setCreatedAt(existingGitIntegrationEntity.getCreatedAt());
    }
    return saveUpdateInternal(accountIdentifier, false, connectorInfoDTO, gitIntegrationOps, integrationEntity,
        existingGitIntegrationEntity, true, true);
  }

  @Override
  public List<GitIntegrationResponse> get(String accountIdentifier, Pageable pageRequest, String searchTerm) {
    Criteria criteria = buildCriteria(accountIdentifier, searchTerm);
    Page<IntegrationEntity> entities = integrationEntityRepository.findAll(criteria, pageRequest);
    return GitIntegrationMapper.toResponse(entities.getContent());
  }

  @Override
  public GitIntegrationResponse get(String accountIdentifier, String identifier) {
    return GitIntegrationMapper.toResponse(getByAccountAndIdentifier(accountIdentifier, identifier));
  }

  @Override
  public void delete(String accountIdentifier, String identifier, boolean forceDelete) {
    GitIntegrationEntity existingGitIntegrationEntity =
        (GitIntegrationEntity) getByAccountAndIdentifier(accountIdentifier, identifier);
    String host = existingGitIntegrationEntity.getHostForHostProxy();
    Set<String> hostsToBeRemoved = Collections.singleton(host);
    transactionHelper.performTransaction(() -> {
      delegateSelectorsCache.remove(accountIdentifier, hostsToBeRemoved);
      proxyEnvVariableServiceWrapper.removeFromHostProxyEnvVariable(accountIdentifier, hostsToBeRemoved);
      configManagerService.deleteAppConfigAndMergeConfigForAccount(
          accountIdentifier, existingGitIntegrationEntity.getConfigId(), ConfigType.INTEGRATION);
      existingGitIntegrationEntity.setParentDeleted(true);
      String gitIntegrationType = getConnectorType(existingGitIntegrationEntity);
      GitIntegrationOps<GitIntegrationEntity, ConnectorConfigDTO> gitIntegrationOps =
          (GitIntegrationOps<GitIntegrationEntity, ConnectorConfigDTO>) getServiceForGitIntegration(gitIntegrationType);
      Map<String, String> configsForGitIntegration =
          gitIntegrationOps.getIntegrationConfigs(existingGitIntegrationEntity);
      Map<String, String> secretsForGitIntegration =
          gitIntegrationOps.getIntegrationSecrets(existingGitIntegrationEntity);
      List<String> envNames = new ArrayList<>();
      configsForGitIntegration.forEach((k, v) -> envNames.add(k));
      secretsForGitIntegration.forEach((k, v) -> envNames.add(k));
      backstageEnvVariableService.deleteMultiUsingEnvNames(envNames, accountIdentifier);
      if (forceDelete) {
        integrationEntityRepository.delete(existingGitIntegrationEntity);
      } else {
        integrationEntityRepository.save(existingGitIntegrationEntity);
      }
      setupUsageProducer.deleteConnectorSetupUsage(accountIdentifier, existingGitIntegrationEntity.getIdentifier());
      outboxService.save(new GitIntegrationDeleteEvent(accountIdentifier, existingGitIntegrationEntity));
      return null;
    });
  }

  @Override
  public void delete(String accountIdentifier) {
    List<IntegrationEntity> integrationEntities =
        integrationEntityRepository.findByAccountIdentifier(accountIdentifier);
    integrationEntities.removeIf(IntegrationEntity::isManaged);
    integrationEntities.forEach(
        integrationEntity -> delete(accountIdentifier, integrationEntity.getIdentifier(), true));
  }

  @Override
  public DiscoverEntitiesDTO discoverEntities(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String integrationId, int pageIndex, int pageLimit, String sort, String searchTerm, String kinds,
      List<String> filters, String includeFields, String includePaths, Integer prevOffset, Integer nextOffset) {
    throw new UnsupportedOperationException("Git integration discoverEntities not supported yet");
  }

  @Override
  public void saveDiscoverEntities(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String integrationId, SaveDiscoverEntitiesRequest saveDiscoverEntitiesRequest) {
    throw new UnsupportedOperationException("Git integration saveDiscoverEntities not supported yet");
  }

  @Override
  public UnlinkIntegrationEntitiesResponse unlinkIntegrationEntities(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String integrationId, List<String> entityRefs) {
    throw new UnsupportedOperationException("Git integration unlinkIntegrationEntities not supported yet");
  }

  @Override
  public ImportedEntitiesDTO getImportedEntities(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String integrationId, int pageIndex, int pageLimit, String sort, String searchTerm,
      String kinds) {
    throw new UnsupportedOperationException("Git integration getImportedEntities not supported yet");
  }

  public DecryptableEntity getAuthenticationDetailsForDelegateTask(
      String accountIdentifier, String url, List<HttpHeaderConfig> headers, Object catalogEntity) {
    if (catalogEntity instanceof GitReferencedCatalogEntity gitReferencedCatalogEntity) {
      String connectorRef = gitReferencedCatalogEntity.getConnectorRef();
      String[] connectorRefSplit = connectorRef.split("[.]");
      String orgIdentifier = null;
      String projectIdentifier = null;
      if (connectorRefSplit.length == 2 && connectorRefSplit[0].equals("org")) {
        orgIdentifier = gitReferencedCatalogEntity.getOrgIdentifier();
      }
      if (connectorRefSplit.length == 1) {
        orgIdentifier = gitReferencedCatalogEntity.getOrgIdentifier();
        projectIdentifier = gitReferencedCatalogEntity.getProjectIdentifier();
      }
      Optional<ConnectorDTO> optionalConnectorDTO = Optional.empty();
      try {
        optionalConnectorDTO = NGRestUtils.getResponse(connectorResourceClient.get(
            removeScopeFromIdentifier(connectorRef), accountIdentifier, orgIdentifier, projectIdentifier));
      } catch (Exception ex) {
        log.warn("Error in connector resource get for connector = {} account = {} org = {} project = {} error = {}",
            removeScopeFromIdentifier(connectorRef), accountIdentifier, orgIdentifier, projectIdentifier,
            ex.getMessage(), ex);
      }
      if (optionalConnectorDTO.isPresent()) {
        ConnectorInfoDTO connectorInfoDTO = optionalConnectorDTO.get().getConnectorInfo();
        String gitIntegrationType = getGitIntegrationType(connectorInfoDTO);
        GitIntegrationOps<GitIntegrationEntity, ConnectorConfigDTO> gitIntegrationOps =
            (GitIntegrationOps<GitIntegrationEntity, ConnectorConfigDTO>) getServiceForGitIntegration(
                gitIntegrationType);
        return gitIntegrationOps.getAuthenticationDetailsForDelegateTask(connectorInfoDTO);
      }
    }
    URI uri;
    try {
      uri = new URI(url);
    } catch (URISyntaxException e) {
      throw new UnexpectedException("Error while getting the host name for the URL: " + url);
    }
    String host = removeApiPrefixFromHost(uri.getHost());
    String additionalIndexer = getGitIntegrationAdditionalIndexer(host, url);
    List<IntegrationEntity> integrationEntities =
        integrationEntityRepository.findByAccountIdentifierAndAdditionalIndexer(accountIdentifier, additionalIndexer);
    Optional<IntegrationEntity> integrationEntityOptional =
        integrationEntities.stream().filter(entity -> ((GitIntegrationEntity) entity).getHost().equals(host)).findAny();
    if (integrationEntityOptional.isEmpty()) {
      // Flow comes here for plugins use-case as well.
      log.info("Cannot find git integrations for the account: {}, host: {}, additionalIndexer: {}", accountIdentifier,
          host, additionalIndexer);
      return null;
    }
    GitIntegrationEntity gitIntegrationEntity = (GitIntegrationEntity) integrationEntityOptional.get();
    String gitIntegrationName = getGitIntegrationNameForParentType(gitIntegrationEntity);
    GitIntegrationOps<GitIntegrationEntity, ConnectorConfigDTO> gitIntegrationOps =
        (GitIntegrationOps<GitIntegrationEntity, ConnectorConfigDTO>) getServiceForGitIntegration(gitIntegrationName);
    return gitIntegrationOps.getAuthenticationDetailsForDelegateTask(gitIntegrationEntity, headers);
  }

  public void processConnectorUpdate(String accountIdentifier, String connectorIdentifier) {
    String gitIntegrationIdentifier = IDP_PREFIX + connectorIdentifier;
    boolean canProcessConnectorEvent = canProcessConnectorEvent(accountIdentifier, gitIntegrationIdentifier);
    if (canProcessConnectorEvent) {
      GitIntegrationRequest gitIntegrationRequest = new GitIntegrationRequest();
      gitIntegrationRequest.setType(BaseIntegrationRequest.TypeEnum.GIT);
      gitIntegrationRequest.setConnectorIdentifier(connectorIdentifier);
      update(accountIdentifier, gitIntegrationIdentifier, gitIntegrationRequest, false);
    }
  }

  public void processConnectorDelete(String accountIdentifier, String connectorIdentifier) {
    String gitIntegrationIdentifier = IDP_PREFIX + connectorIdentifier;
    boolean canProcessConnectorEvent = canProcessConnectorEvent(accountIdentifier, gitIntegrationIdentifier);
    if (canProcessConnectorEvent) {
      delete(accountIdentifier, gitIntegrationIdentifier, false);
    }
  }

  public void writeThroughAPI(
      String accountIdentifier, GitIntegrationRequest request, List<Pair<String, String>> files) {
    String connectorIdentifier = request.getConnectorIdentifier();
    connectorIdentifier = replaceAccountScopeFromIdentifier(connectorIdentifier);
    ConnectorInfoDTO connectorInfoDTO = connectorInfoDTO(accountIdentifier, connectorIdentifier, request);
    String gitIntegrationType = getGitIntegrationType(connectorInfoDTO);
    GitIntegrationOps<GitIntegrationEntity, ConnectorConfigDTO> gitIntegrationOps =
        (GitIntegrationOps<GitIntegrationEntity, ConnectorConfigDTO>) getServiceForGitIntegration(gitIntegrationType);
    gitIntegrationOps.writeThroughAPI(accountIdentifier, request.getWriteValidationDetails(), connectorInfoDTO, files);
  }

  public void setupDefaultConnectorLessManagedHarnessCodeRepoIntegration(String accountIdentifier) {
    createIdpGitIntegrationManagedHcrSecret(accountIdentifier, "dummy");

    String baseUrl = getAccountBaseUrl(accountIdentifier);

    ConnectorInfoDTO connectorInfoDTO = idpGitIntegrationManagedHcrConnector(accountIdentifier, baseUrl);

    GitIntegrationOps<GitIntegrationEntity, ConnectorConfigDTO> gitIntegrationOps =
        (GitIntegrationOps<GitIntegrationEntity, ConnectorConfigDTO>) getServiceForGitIntegration(HARNESS);
    GitIntegrationEntity integrationEntity = gitIntegrationOps.prepare(connectorInfoDTO);
    validateForCreate(gitIntegrationOps, integrationEntity);
    saveUpdateInternal(
        accountIdentifier, false, connectorInfoDTO, gitIntegrationOps, integrationEntity, null, false, true);
  }

  public void setupDefaultConnectorLessManagedHarnessCodeRepoIntegrationIfNotAlready(String accountIdentifier) {
    Optional<IntegrationEntity> optionalIntegrationEntity =
        integrationEntityRepository.findByAccountIdentifierAndParentTypeAndSubTypeAndAdditionalIndexer(
            accountIdentifier, ParentType.HARNESS_CODE_REPO, null, null);
    if (optionalIntegrationEntity.isEmpty()) {
      try {
        setupDefaultConnectorLessManagedHarnessCodeRepoIntegration(accountIdentifier);
      } catch (Exception ex) {
        log.error("Error in setting up default connector less HCR integration for account = {}", accountIdentifier, ex);
      }
    }
  }

  public List<IntegrationEntity> fetchNonManagedGitIntegrations(String accountIdentifier) {
    return integrationEntityRepository.findByAccountIdentifierAndIntegrationAndManagedFalse(
        accountIdentifier, Integration.GIT);
  }

  public List<IntegrationEntity> fetchManagedGitIntegrations(String accountIdentifier) {
    return integrationEntityRepository.findByAccountIdentifierAndIntegrationAndManagedTrue(
        accountIdentifier, Integration.GIT);
  }

  public void updateDefaultConnectorLessManagedHarnessCodeRepoIntegration(String accountIdentifier) {
    String baseUrl = getAccountBaseUrl(accountIdentifier);

    ConnectorInfoDTO connectorInfoDTO = idpGitIntegrationManagedHcrConnector(accountIdentifier, baseUrl);

    Optional<IntegrationEntity> optionalGitIntegrationEntity =
        integrationEntityRepository.findByAccountIdentifierAndIdentifierAndIntegration(
            accountIdentifier, IDP_GIT_INTEGRATION_MANAGED_HCR, Integration.GIT);
    if (optionalGitIntegrationEntity.isPresent()) {
      IntegrationEntity existingGitIntegrationEntity = optionalGitIntegrationEntity.get();
      validateForUpdate(existingGitIntegrationEntity.getParentType(), HARNESS);
      GitIntegrationOps<GitIntegrationEntity, ConnectorConfigDTO> gitIntegrationOps =
          (GitIntegrationOps<GitIntegrationEntity, ConnectorConfigDTO>) getServiceForGitIntegration(HARNESS);
      GitIntegrationEntity integrationEntity = gitIntegrationOps.prepare(connectorInfoDTO);
      integrationEntity.setId(existingGitIntegrationEntity.getId());
      integrationEntity.setCreatedAt(existingGitIntegrationEntity.getCreatedAt());
      saveUpdateInternal(accountIdentifier, false, connectorInfoDTO, gitIntegrationOps, integrationEntity,
          (GitIntegrationEntity) existingGitIntegrationEntity, false, true);
    }
  }

  public void updateDefaultConnectorLessManagedHarnessCodeRepoIntegrationWithoutBaseUrlOverriding(
      String accountIdentifier) {
    ConnectorInfoDTO connectorInfoDTO =
        idpGitIntegrationManagedHcrConnector(accountIdentifier, harnessCodeRepoConfig.getBaseUrl());

    IntegrationEntity existingGitIntegrationEntity =
        getByAccountAndIdentifier(accountIdentifier, IDP_GIT_INTEGRATION_MANAGED_HCR);
    validateForUpdate(existingGitIntegrationEntity.getParentType(), HARNESS);
    GitIntegrationOps<GitIntegrationEntity, ConnectorConfigDTO> gitIntegrationOps =
        (GitIntegrationOps<GitIntegrationEntity, ConnectorConfigDTO>) getServiceForGitIntegration(HARNESS);
    GitIntegrationEntity integrationEntity = gitIntegrationOps.prepare(connectorInfoDTO);
    integrationEntity.setId(existingGitIntegrationEntity.getId());
    integrationEntity.setCreatedAt(existingGitIntegrationEntity.getCreatedAt());
    saveUpdateInternal(accountIdentifier, false, connectorInfoDTO, gitIntegrationOps, integrationEntity,
        (GitIntegrationEntity) existingGitIntegrationEntity, false, true);
  }

  private String getGitIntegrationAdditionalIndexer(String host, String url) {
    if (AZURE_CLOUD_PATTERN.matcher(host).find()) {
      Matcher matcher = AZURE_ORG_PATTERN.matcher(url);
      if (matcher.find()) {
        return matcher.group(1);
      }
    }
    return host;
  }

  private String removeApiPrefixFromHost(String host) {
    return host.startsWith("api.") ? host.replaceFirst("^api\\.", "") : host;
  }

  private String getGitIntegrationNameForParentType(GitIntegrationEntity gitIntegrationEntity) {
    switch (gitIntegrationEntity.getParentType()) {
      case GITHUB -> {
        return GITHUB;
      }
      case BITBUCKET_CLOUD -> {
        return BITBUCKET_CLOUD;
      }
      case BITBUCKET_SERVER -> {
        return BITBUCKET_SERVER;
      }
      case GITLAB -> {
        return GITLAB;
      }
      case AZURE -> {
        return AZURE_REPO;
      }
      default -> throw new UnsupportedOperationException("Parent type " + gitIntegrationEntity.getParentType() + " not supported");
    }
  }

  private void validateRequest(GitIntegrationRequest request, boolean dryRun, boolean writeValidation) {
    if (dryRun && !writeValidation) {
      ReadValidationDetails readValidationDetails = request.getReadValidationDetails();
      if (readValidationDetails == null) {
        throw new InvalidRequestException("Read Validation Details cannot be null");
      }
      if (isEmpty(readValidationDetails.getFileUrl())) {
        throw new InvalidRequestException("File url field is required");
      }
    }

    if (dryRun && writeValidation) {
      WriteValidationDetails writeValidationDetails = request.getWriteValidationDetails();
      if (writeValidationDetails == null) {
        throw new InvalidRequestException("Write Validation Details cannot be null");
      }
      if (isEmpty(writeValidationDetails.getRepository())) {
        throw new InvalidRequestException("Repository field is required");
      }

      if (isEmpty(writeValidationDetails.getBranch())) {
        throw new InvalidRequestException("Branch field is required");
      }

      if (isEmpty(writeValidationDetails.getPath())) {
        throw new InvalidRequestException("Path field is required");
      }
    }
  }

  private IntegrationEntity getByAccountAndIdentifier(String accountIdentifier, String identifier) {
    Optional<IntegrationEntity> optionalGitIntegrationEntity =
        integrationEntityRepository.findByAccountIdentifierAndIdentifierAndIntegration(
            accountIdentifier, identifier, Integration.GIT);
    if (optionalGitIntegrationEntity.isEmpty()) {
      throw new InvalidRequestException("Git integration with identifier " + identifier + " not found");
    }
    return optionalGitIntegrationEntity.get();
  }

  public ConnectorInfoDTO getConnectorInfo(String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorIdentifier) {
    Optional<ConnectorDTO> connectorDTO;
    try {
      connectorDTO =
          NGRestUtils.getResponse(connectorResourceClient.get(connectorIdentifier, accountIdentifier, orgIdentifier, projectIdentifier));
    } catch (Exception ex) {
      throw new UnexpectedException("Unexpected error in fetching connector details");
    }
    if (connectorDTO.isEmpty()) {
      throw new UnexpectedException("Connector " + connectorIdentifier + " not found");
    }
    return connectorDTO.get().getConnectorInfo();
  }

  public String getGitIntegrationType(ConnectorInfoDTO connectorInfoDTO) {
    if (connectorInfoDTO.getConnectorType().equals(ConnectorType.BITBUCKET)) {
      BitbucketConnectorDTO bitbucketConnectorDTO = (BitbucketConnectorDTO) connectorInfoDTO.getConnectorConfig();
      if (isBitBucketSAAS(bitbucketConnectorDTO.getUrl())) {
        return BITBUCKET_CLOUD;
      }
      return BITBUCKET_SERVER;
    }
    return connectorInfoDTO.getConnectorType().toString();
  }

  private void validateForUpdate(ParentType existingType, String incomingType) {
    if (!PARENT_TYPE_MAP.get(existingType).equals(incomingType)) {
      throw new InvalidRequestException("Invalid git integration type provided for update");
    }
  }

  public GitIntegrationOps<?, ?> getServiceForGitIntegration(String gitIntegration) {
    return switch (gitIntegration) {
      case AZURE_REPO -> azureIntegrationService;
      case BITBUCKET_CLOUD -> bitbucketCloudIntegrationService;
      case BITBUCKET_SERVER -> bitbucketServerIntegrationService;
      case GITHUB -> githubIntegrationService;
      case GITLAB -> gitlabIntegrationService;
      case HARNESS -> harnessCodeRepoIntegrationService;
      default -> throw new UnexpectedException("GIT Integration " + gitIntegration + " not supported yet");
    };
  }

  private void validateForCreate(GitIntegrationOps<GitIntegrationEntity, ConnectorConfigDTO> gitIntegrationOps,
      GitIntegrationEntity integrationEntity) {
    Optional<IntegrationEntity> optionalIntegrationEntity =
        integrationEntityRepository.findByAccountIdentifierAndParentTypeAndSubTypeAndAdditionalIndexer(
            integrationEntity.getAccountIdentifier(), integrationEntity.getParentType(), integrationEntity.getSubType(),
            integrationEntity.getAdditionalIndexer());
    if (optionalIntegrationEntity.isPresent()) {
      throw new InvalidRequestException(gitIntegrationOps.getAlreadyExistErrorMessage(integrationEntity)
          + "Please update the existing Git integration.");
    }
  }

  private void prepareForReadDryRun(GitIntegrationRequest request, GitIntegrationEntity integrationEntity) {
    if (request.getReadValidationDetails() != null) {
      GitIntegrationEntity.ReadPermissionValidation readPermissionValidation =
          new GitIntegrationEntity.ReadPermissionValidation();
      String fileUrl = request.getReadValidationDetails().getFileUrl();
      if (fileUrl.endsWith(".git")) {
        fileUrl = fileUrl.substring(0, fileUrl.length() - 4);
      }
      readPermissionValidation.setFileUrl(fileUrl);
      integrationEntity.setReadPermissionValidation(readPermissionValidation);
    }
  }

  private void performWriteDryRun(String accountIdentifier, WriteValidationDetails writeValidationDetails,
      ConnectorInfoDTO connectorInfoDTO, GitIntegrationOps<GitIntegrationEntity, ConnectorConfigDTO> gitIntegrationOps,
      GitIntegrationEntity integrationEntity) {
    GitIntegrationEntity.ReadPermissionValidation readPermissionValidation =
        new GitIntegrationEntity.ReadPermissionValidation();
    try {
      gitIntegrationOps.validateWritePermission(accountIdentifier, writeValidationDetails, connectorInfoDTO);
      readPermissionValidation.setStatus("success");
      readPermissionValidation.setError("");
    } catch (Exception e) {
      readPermissionValidation.setStatus(FAILED);
      readPermissionValidation.setError(e.getMessage());
    }
    integrationEntity.setReadPermissionValidation(readPermissionValidation);
  }

  private GitIntegrationResponse saveUpdateInternal(String accountIdentifier, boolean dryRun,
      ConnectorInfoDTO connectorInfoDTO, GitIntegrationOps<GitIntegrationEntity, ConnectorConfigDTO> gitIntegrationOps,
      GitIntegrationEntity integrationEntity, GitIntegrationEntity existingGitIntegrationEntity, boolean setupUsage,
      boolean publishAudit) {
    AppConfig appConfigForGitIntegration =
        gitIntegrationOps.getAppConfig(integrationEntity, connectorInfoDTO.getConnectorConfig());
    Map<String, String> configsForGitIntegration = gitIntegrationOps.getIntegrationConfigs(integrationEntity);
    Map<String, String> secretsForGitIntegration = gitIntegrationOps.getIntegrationSecrets(integrationEntity);
    List<BackstageEnvVariable> backstageEnvVariables =
        prepareBackstageEnvVariables(configsForGitIntegration, secretsForGitIntegration);
    gitIntegrationOps.validateReadPermission(
        accountIdentifier, connectorInfoDTO.getConnectorConfig(), integrationEntity, configsForGitIntegration, secretsForGitIntegration);
    if (!dryRun) {
      performTransaction(accountIdentifier, appConfigForGitIntegration, backstageEnvVariables, integrationEntity,
          existingGitIntegrationEntity, setupUsage, publishAudit);
    }
    return GitIntegrationMapper.toResponse(integrationEntity);
  }

  public boolean validateReadPermission(String accountIdentifier,ConnectorInfoDTO connectorInfoDTO, String urlForValidation){
    String gitIntegrationType = getGitIntegrationType(connectorInfoDTO);
    GitIntegrationOps<GitIntegrationEntity, ConnectorConfigDTO> gitIntegrationOps =
            (GitIntegrationOps<GitIntegrationEntity, ConnectorConfigDTO>) getServiceForGitIntegration(gitIntegrationType);
    GitIntegrationEntity integrationEntity = gitIntegrationOps.prepare(connectorInfoDTO);
    gitIntegrationOps.validateReadPermissionForUrl(accountIdentifier, connectorInfoDTO.getConnectorConfig(), integrationEntity, urlForValidation);
    return integrationEntity.getReadPermissionValidation().getStatus().equals("success");
    }

    private void performTransaction(String accountIdentifier, AppConfig appConfigForGitIntegration,
        List<BackstageEnvVariable> backstageEnvVariables, GitIntegrationEntity gitIntegrationEntity,
        GitIntegrationEntity existingGitIntegrationEntity, boolean setupUsage, boolean publishAudit) {
      transactionHelper.performTransaction(() -> {
        updateHostProxyAndDelegateSelectorsCache(gitIntegrationEntity);
        
        // Delete old appconfig if this is an update operation
        if (existingGitIntegrationEntity != null) {
          configManagerService.deleteAppConfigAndMergeConfigForAccount(
              accountIdentifier, existingGitIntegrationEntity.getConfigId(), ConfigType.INTEGRATION);
        }
        
        // Apply the new appconfig
        configManagerService.setupAndPropagateIntegrationConfig(accountIdentifier, appConfigForGitIntegration);
        backstageEnvVariableService.createOrUpdate(backstageEnvVariables, accountIdentifier);
        integrationEntityRepository.save(gitIntegrationEntity);
        saveIntegrationStatusIfNotAlreadyCompleted(accountIdentifier);
        setupUsagePublishAuditIfNeeded(
            accountIdentifier, gitIntegrationEntity, existingGitIntegrationEntity, setupUsage, publishAudit);
        return null;
      });
    }

    private List<BackstageEnvVariable> prepareBackstageEnvVariables(
        Map<String, String> configsForGitIntegration, Map<String, String> secretsForGitIntegration) {
      List<BackstageEnvVariable> backstageEnvVariables = new ArrayList<>();

      configsForGitIntegration.forEach((k, v) -> {
        BackstageEnvConfigVariable backstageEnvConfigVariable = new BackstageEnvConfigVariable();
        backstageEnvConfigVariable.envName(k);
        backstageEnvConfigVariable.value(v);
        backstageEnvConfigVariable.setType(BackstageEnvVariable.TypeEnum.CONFIG);
        backstageEnvVariables.add(backstageEnvConfigVariable);
      });

      secretsForGitIntegration.forEach((k, v) -> {
        BackstageEnvSecretVariable backstageEnvSecretVariable = new BackstageEnvSecretVariable();
        backstageEnvSecretVariable.setEnvName(k);
        backstageEnvSecretVariable.harnessSecretIdentifier(v);
        backstageEnvSecretVariable.setType(BackstageEnvVariable.TypeEnum.SECRET);
        backstageEnvVariables.add(backstageEnvSecretVariable);
      });

      return backstageEnvVariables;
    }

    private void updateHostProxyAndDelegateSelectorsCache(GitIntegrationEntity gitIntegrationEntity) {
      boolean isProxyNew = gitIntegrationEntity.isExecuteOnDelegate();
      JSONObject hostProxyMap =
          proxyEnvVariableServiceWrapper.getHostProxyMap(gitIntegrationEntity.getAccountIdentifier());
      JSONObject originalHostProxyMap = new JSONObject(hostProxyMap.toString());
      Optional<IntegrationEntity> optionalIntegrationEntity =
          integrationEntityRepository.findByAccountIdentifierAndIdentifierAndIntegration(
              gitIntegrationEntity.getAccountIdentifier(), gitIntegrationEntity.getIdentifier(), Integration.GIT);

      String host = gitIntegrationEntity.getHostForHostProxy();

      if (optionalIntegrationEntity.isPresent()) {
        String existingHost = ((GitIntegrationEntity) optionalIntegrationEntity.get()).getHost();
        Set<String> existingDelegateSelectors =
            ((GitIntegrationEntity) optionalIntegrationEntity.get()).getDelegateSelectors();
        boolean isProxyExisting = ((GitIntegrationEntity) optionalIntegrationEntity.get()).isExecuteOnDelegate();
        Set<String> hostsToBeRemoved = Collections.singleton(existingHost);

        if (!existingHost.equals(host)) {
          hostProxyMap.remove(existingHost);
          hostProxyMap.put(host, isProxyNew);
          delegateSelectorsCache.remove(gitIntegrationEntity.getAccountIdentifier(), hostsToBeRemoved);
          delegateSelectorsCache.put(
              gitIntegrationEntity.getAccountIdentifier(), host, gitIntegrationEntity.getDelegateSelectors());
        } else {
          if (isProxyExisting != isProxyNew) {
            hostProxyMap.put(existingHost, isProxyNew);
          }
          if (!existingDelegateSelectors.equals(gitIntegrationEntity.getDelegateSelectors())) {
            delegateSelectorsCache.put(
                gitIntegrationEntity.getAccountIdentifier(), existingHost, gitIntegrationEntity.getDelegateSelectors());
          }
        }
      } else {
        hostProxyMap.put(host, isProxyNew);
        if (!gitIntegrationEntity.getDelegateSelectors().isEmpty()) {
          delegateSelectorsCache.put(
              gitIntegrationEntity.getAccountIdentifier(), host, gitIntegrationEntity.getDelegateSelectors());
        }
      }
      if (!originalHostProxyMap.similar(hostProxyMap)) {
        proxyEnvVariableServiceWrapper.setHostProxyMap(gitIntegrationEntity.getAccountIdentifier(), hostProxyMap);
      }
    }

    private Criteria buildCriteria(String accountIdentifier, String searchTerm) {
      Criteria criteria = new Criteria();
      criteria.and(IntegrationEntity.IntegrationsKeys.accountIdentifier).is(accountIdentifier);
      criteria.and(IntegrationEntity.IntegrationsKeys.integration).is(IntegrationEntity.Integration.GIT);

      if (isNotEmpty(searchTerm)) {
        criteria.andOperator(buildSearchCriteria(searchTerm));
      }
      return criteria;
    }

    private Criteria buildSearchCriteria(String searchTerm) {
      return new Criteria().orOperator(
          where(IntegrationEntity.IntegrationsKeys.identifier)
              .regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS));
    }

    private void saveIntegrationStatusIfNotAlreadyCompleted(String accountIdentifier) {
      StatusInfoV2 statusInfoV2 =
          statusInfoService.findByAccountIdentifierAndTypeV2(accountIdentifier, StatusType.GIT_INTEGRATION.name());
      StatusInfo statusInfo = statusInfoV2.get(StatusType.GIT_INTEGRATION.name().toLowerCase());
      if (statusInfo != null && !StatusInfo.CurrentStatusEnum.COMPLETED.equals(statusInfo.getCurrentStatus())) {
        statusInfoService.save(prepareStatusInfo(), accountIdentifier, StatusType.GIT_INTEGRATION.name());
      }
    }

    private StatusInfo prepareStatusInfo() {
      StatusInfo statusInfo = new StatusInfo();
      statusInfo.setCurrentStatus(StatusInfo.CurrentStatusEnum.COMPLETED);
      statusInfo.setReason("Integration completed successfully");
      return statusInfo;
    }

    private void setupUsagePublishAuditIfNeeded(String accountIdentifier, GitIntegrationEntity gitIntegrationEntity,
        GitIntegrationEntity existingGitIntegrationEntity, boolean setupUsage, boolean publishAudit) {
      if (existingGitIntegrationEntity == null) {
        if (setupUsage) {
          setupUsageProducer.publishConnectorSetupUsage(
              accountIdentifier, gitIntegrationEntity.getConnectorIdentifier(), gitIntegrationEntity.getIdentifier());
        }
        if (publishAudit) {
          outboxService.save(new GitIntegrationCreateEvent(accountIdentifier, gitIntegrationEntity));
        }
      } else {
        if (setupUsage) {
          setupUsageProducer.deleteConnectorSetupUsage(accountIdentifier, existingGitIntegrationEntity.getIdentifier());
          setupUsageProducer.publishConnectorSetupUsage(
              accountIdentifier, gitIntegrationEntity.getConnectorIdentifier(), gitIntegrationEntity.getIdentifier());
        }
        if (publishAudit) {
          outboxService.save(
              new GitIntegrationUpdateEvent(accountIdentifier, existingGitIntegrationEntity, gitIntegrationEntity));
        }
      }
    }

    private boolean canProcessConnectorEvent(String accountIdentifier, String gitIntegrationIdentifier) {
      Optional<IntegrationEntity> optionalGitIntegrationEntity =
          integrationEntityRepository.findByAccountIdentifierAndIdentifierAndIntegration(
              accountIdentifier, gitIntegrationIdentifier, Integration.GIT);
      return optionalGitIntegrationEntity.isPresent();
    }

    private void createIdpGitIntegrationManagedHcrSecret(String accountIdentifier, String secretValue) {
      SecretRequestWrapper secretRequestWrapper = secretRequestWrapper(secretValue);
      Principal originalSourcePrincipal = SourcePrincipalContextBuilder.getSourcePrincipal();
      SourcePrincipalContextBuilder.setSourcePrincipal(
          new ServicePrincipal(AuthorizationServiceHeader.IDP_SERVICE.getServiceId()));
      try {
        ngSecretService.create(accountIdentifier, null, null, true, secretRequestWrapper);
      } catch (Exception ex) {
        throw new UnexpectedException(ex.getMessage());
      } finally {
        SourcePrincipalContextBuilder.setSourcePrincipal(originalSourcePrincipal);
      }
    }

    public void updateIdpGitIntegrationManagedHcrSecret(String accountIdentifier, String secretValue) {
      SecretRequestWrapper secretRequestWrapper = secretRequestWrapper(secretValue);
      ngSecretService.updateSecret(
          IDP_GIT_INTEGRATION_MANAGED_HCR, accountIdentifier, null, null, secretRequestWrapper);
    }

    private SecretRequestWrapper secretRequestWrapper(String secretValue) {
      return SecretRequestWrapper.builder()
          .secret(SecretDTOV2.builder()
                      .identifier(IDP_GIT_INTEGRATION_MANAGED_HCR)
                      .name(IDP_GIT_INTEGRATION_MANAGED_HCR)
                      .description("IDP Git Integration Managed HarnessCodeRepo")
                      .type(SecretType.SecretText)
                      .spec(SecretTextSpecDTO.builder()
                                .secretManagerIdentifier(HARNESS_SECRET_MANAGER_IDENTIFIER)
                                .value(secretValue)
                                .valueType(ValueType.Inline)
                                .build())
                      .build())
          .build();
    }

    private ConnectorInfoDTO idpGitIntegrationManagedHcrConnector(String accountIdentifier, String url) {
      ConnectorInfoDTO connectorInfoDTO = new ConnectorInfoDTO();
      connectorInfoDTO.setAccountIdentifier(accountIdentifier);
      connectorInfoDTO.setIdentifier(IDP_GIT_INTEGRATION_MANAGED_HCR);
      connectorInfoDTO.setConnectorType(ConnectorType.HARNESS);
      HarnessConnectorDTO harnessConnectorDTO =
          HarnessConnectorDTO.builder()
              .url(url)
              .authentication(
                  HarnessAuthenticationDTO.builder()
                      .authType(GitAuthType.HTTP)
                      .credentials(
                          HarnessHttpCredentialsDTO.builder()
                              .type(HarnessHttpAuthenticationType.USERNAME_AND_TOKEN)
                              .httpCredentialsSpec(
                                  HarnessUsernameTokenDTO.builder()
                                      .username(null)
                                      .tokenRef(
                                          SecretRefData.builder().identifier(IDP_GIT_INTEGRATION_MANAGED_HCR).build())
                                      .build())
                              .build())
                      .build())
              .build();
      connectorInfoDTO.setConnectorConfig(harnessConnectorDTO);
      return connectorInfoDTO;
    }

    public ConnectorInfoDTO idpManagedHcrConnectorForWrite(String accountIdentifier, String repositoryUrl) {
      Principal sourcePrincipal = SourcePrincipalContextBuilder.getSourcePrincipal();
      Principal securityPrincipal = SecurityContextBuilder.getPrincipal();
      SourcePrincipalContextBuilder.setSourcePrincipal(
          new ServicePrincipal(AuthorizationServiceHeader.IDP_SERVICE.getServiceId()));
      SecurityContextBuilder.setContext(new ServicePrincipal(AuthorizationServiceHeader.IDP_SERVICE.getServiceId()));

      if (repositoryUrl.endsWith(".git")) {
        repositoryUrl = repositoryUrl.substring(0, repositoryUrl.length() - 4);
      }
      String[] repositoryUrlSplit = repositoryUrl.split("/");

      String organizationId = null;
      String projectId = null;
      String slug = null;
      if (repositoryUrlSplit.length == 7) {
        organizationId = repositoryUrlSplit[4];
        projectId = repositoryUrlSplit[5];
        slug = repositoryUrlSplit[6];
      }
      if (repositoryUrlSplit.length == 6) {
        organizationId = repositoryUrlSplit[4];
        slug = repositoryUrlSplit[5];
      }
      if (repositoryUrlSplit.length == 5) {
        slug = repositoryUrlSplit[4];
      }

      ConnectorInfoDTO connectorInfoDTO = new ConnectorInfoDTO();
      connectorInfoDTO.setAccountIdentifier(accountIdentifier);
      connectorInfoDTO.setIdentifier(IDP_MANAGED_HCR_WRITE);
      connectorInfoDTO.setName(IDP_MANAGED_HCR_WRITE);
      connectorInfoDTO.setConnectorType(ConnectorType.HARNESS);

      HarnessConnectorDTO harnessConnectorDTO =
          HarnessConnectorDTO.builder()
              .url(harnessCodeRepoConfig.getBaseUrl())
              .connectionType(GitConnectionType.REPO)
              .slug(slug)
              .accountId(accountIdentifier)
              .orgId(organizationId)
              .projectId(projectId)
              .executeOnDelegate(false)
              .authentication(
                  HarnessAuthenticationDTO.builder()
                      .authType(GitAuthType.HTTP)
                      .credentials(
                          HarnessHttpCredentialsDTO.builder()
                              .type(HarnessHttpAuthenticationType.USERNAME_AND_TOKEN)
                              .httpCredentialsSpec(
                                  HarnessUsernameTokenDTO.builder()
                                      .username(null)
                                      .tokenRef(
                                          SecretRefData.builder().identifier(IDP_GIT_INTEGRATION_MANAGED_HCR).build())
                                      .build())
                              .build())
                      .build())
              .apiAccess(
                  HarnessApiAccessDTO.builder()
                      .spec(HarnessJWTTokenSpecDTO.builder()
                                .tokenRef(SecretRefData.builder()
                                              .decryptedValue(
                                                  harnessCodeConnectorUtils
                                                      .getToken(harnessCodeRepoConfig.getServiceClientSharedSecret())
                                                      .toCharArray())
                                              .build())
                                .build())
                      .type(HarnessApiAccessType.JWT_TOKEN)
                      .build())
              .apiUrl(harnessCodeRepoConfig.getApiUrl())
              .gitBaseUrl(harnessCodeRepoConfig.getGitBaseUrl())
              .build();
      connectorInfoDTO.setConnectorConfig(harnessConnectorDTO);

      SourcePrincipalContextBuilder.setSourcePrincipal(sourcePrincipal);
      SecurityContextBuilder.setContext(securityPrincipal);

      return connectorInfoDTO;
    }

    public ConnectorInfoDTO connectorInfoDTO(
        String harnessAccount, String connectorIdentifier, GitIntegrationRequest gitIntegrationRequest) {
      ConnectorInfoDTO connectorInfoDTO;
      if (connectorIdentifier.equals(HCR_CONNECTOR_IDENTIFIER)) {
        connectorInfoDTO = idpManagedHcrConnectorForWrite(
            harnessAccount, gitIntegrationRequest.getWriteValidationDetails().getRepository());
      } else {
        connectorInfoDTO = getConnectorInfo(harnessAccount, null, null, connectorIdentifier);
      }
      return connectorInfoDTO;
    }

    public String getAccountBaseUrl(String accountIdentifier) {
      AccountDTO accountDTO = idpCommonService.getAccountDTO(accountIdentifier);
      String baseUrl = harnessCodeRepoConfig.getBaseUrl();
      if (isNotEmpty(accountDTO.getSubdomainURL())) {
        baseUrl = accountDTO.getSubdomainURL();
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
          baseUrl = "https://" + baseUrl;
        }
      }
      return baseUrl;
    }

    public String getConnectorType(IntegrationEntity integrationEntity) {
      switch (integrationEntity.getParentType()) {
      case AZURE -> {
        return AZURE_REPO;
      }
      case BITBUCKET_CLOUD -> {
        return BITBUCKET_CLOUD;
      }
      case BITBUCKET_SERVER -> {
        return BITBUCKET_SERVER;
      }
      case GITHUB -> {
        return GITHUB;
      }
      case GITLAB -> {
        return GITLAB;
      }
      case HARNESS_CODE_REPO -> {
        return HARNESS;
      }
      default -> throw new IllegalArgumentException("Parent type " + integrationEntity.getParentType() + " not supported yet");
    }
  }

  private void validateGithubAppId(ConnectorInfoDTO connectorInfoDTO) {
    if (connectorInfoDTO.getConnectorType() != ConnectorType.GITHUB) {
      return;
    }

    GithubConnectorDTO githubConnector = (GithubConnectorDTO) connectorInfoDTO.getConnectorConfig();
    GithubHttpCredentialsDTO credentials =
        (GithubHttpCredentialsDTO) githubConnector.getAuthentication().getCredentials();

    if (credentials.getType() != GithubHttpAuthenticationType.GITHUB_APP) {
      return;
    }

    GithubApiAccessDTO apiAccess = githubConnector.getApiAccess();
    if (apiAccess != null && apiAccess.getSpec() instanceof GithubAppSpecDTO) {
      GithubAppSpecDTO appSpec = (GithubAppSpecDTO) apiAccess.getSpec();
      String applicationId = appSpec.getApplicationId();

      if (applicationId != null && !applicationId.isEmpty() && !applicationId.matches("\\d+")) {
        throw new InvalidRequestException("Github App ID must be numeric. Please verify the App ID is correct");
      }
    }
  }

  private void validateHostChangeWithEnabledPlugins(
      String accountIdentifier, String connectorIdentifier, String newHost) {
    Optional<IntegrationEntity> existingIntegration =
        integrationEntityRepository.findByAccountIdentifierAndIdentifierAndIntegration(
            accountIdentifier, IDP_PREFIX + connectorIdentifier, Integration.GIT);

    if (!existingIntegration.isPresent()) {
      return;
    }

    if (!(existingIntegration.get() instanceof GitIntegrationEntity)) {
      return;
    }

    GitIntegrationEntity gitIntegration = (GitIntegrationEntity) existingIntegration.get();
    String oldHost = gitIntegration.getHost();
    if (oldHost.equals(newHost)) {
      return;
    }

    List<AppConfigEntity> allEnabledPlugins =
        appConfigRepository.findAllByAccountIdentifierAndConfigTypeAndEnabled(
            accountIdentifier, ConfigType.PLUGIN, true);
    
    if (allEnabledPlugins.isEmpty()) {
      return;
    }

    List<String> pluginsUsingOldHost = new ArrayList<>();
    for (AppConfigEntity pluginConfig : allEnabledPlugins) {
          String configs = pluginConfig.getConfigs();
          String pluginId = pluginConfig.getConfigId();
          String pluginName = pluginConfig.getConfigName();
          if (configs != null && !configs.isEmpty()) {
            if (containsHostValue(configs, oldHost)) {
              pluginsUsingOldHost.add(pluginName);
            }
          }
        }

        if (!pluginsUsingOldHost.isEmpty()) {
          String errorMsg =
              String.format("Cannot update connector host from '%s' to '%s'. The following plugin(s) are enabled and "
                      + "using the current host: %s. "
                      + "Please disable these plugins before updating the connector host.",
                  oldHost, newHost, String.join(", ", pluginsUsingOldHost));
          throw new InvalidRequestException(errorMsg);
        }
    }

    private boolean containsHostValue(String yamlConfig, String hostValue) {
      try {
        Map<String, Object> configMap = YamlUtils.loadYamlStringAsMap(yamlConfig);
        Object hostObject = findObjectByName(configMap, "host");
        return hostObject != null && hostValue.equals(hostObject.toString());
      } catch (Exception e) {
        return yamlConfig.contains(hostValue);
      }
    }
  }
