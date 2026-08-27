/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.service;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.Scope;
import io.harness.beans.request.GitFileRequest;
import io.harness.beans.response.GitFileResponse;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.utils.HarnessCodeConnectorUtils;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.HarnessConnectorDTO;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.harness.HarnessApiAccessDTO;
import io.harness.delegate.beans.connector.scm.harness.HarnessApiAccessType;
import io.harness.delegate.beans.connector.scm.harness.HarnessJWTTokenSpecDTO;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.GitReferencedCatalogEntity;
import io.harness.idp.catalog.mapper.CatalogMapper;
import io.harness.idp.catalog.processor.PlaceholderProcessor;
import io.harness.idp.catalog.processor.PlaceholderProcessor.GitInfo;
import io.harness.idp.catalog.processor.api.SpecFetchException;
import io.harness.idp.catalog.processor.api.SpecFetcher;
import io.harness.idp.common.HarnessCodeRepoConfig;
import io.harness.idp.common.YamlUtils;
import io.harness.idp.integrations.entities.git.GitIntegrationEntity;
import io.harness.idp.integrations.service.git.GitIntegrationOps;
import io.harness.idp.integrations.service.git.GitIntegrationServiceImpl;
import io.harness.product.ci.scm.proto.SCMGrpc;
import io.harness.service.ScmServiceClient;
import io.harness.spec.server.idp.v1.model.EntityResponse;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
public class ApiDefinitionResolver {
  private static final Pattern ABSOLUTE_URL = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*://.*");
  private static final Pattern GITHUB_RAW = Pattern.compile("https?://github\\.com/([^/]+)/([^/]+)/blob/(.+)");
  private static final Pattern GHE_RAW = Pattern.compile("(https?://[^/]+)/([^/]+)/([^/]+)/blob/(.+)");
  private static final Pattern GITLAB_RAW = Pattern.compile("(https?://[^/]+/[^?]*?)/-/blob/(.+)");
  private static final Pattern BITBUCKET_CLOUD_RAW = Pattern.compile("(https?://bitbucket\\.org/[^/]+/[^/]+)/src/(.+)");
  private static final Pattern BITBUCKET_SERVER_BROWSE =
      Pattern.compile("(https?://[^/]+/projects/[^/]+/repos/[^/]+)/browse/([^?]*)(.*)");

  private static final String SPEC_KEY = "spec";
  private static final String DEFINITION_KEY = "definition";
  private static final String TEXT_PLACEHOLDER = "$text";
  private static final String YAML_PLACEHOLDER = "$yaml";
  private static final String JSON_PLACEHOLDER = "$json";

  @Inject private PlaceholderProcessor placeholderProcessor;
  @Inject private SpecFetcher specFetcher;
  @Inject private GitIntegrationServiceImpl gitIntegrationService;
  @Inject @Named("harnessCodeRepoConfig") private HarnessCodeRepoConfig harnessCodeRepoConfig;
  @Inject private HarnessCodeConnectorUtils harnessCodeConnectorUtils;
  @Inject private ScmServiceClient scmServiceClient;
  @Inject private SCMGrpc.SCMBlockingStub scmBlockingStub;

  public EntityResponse resolve(CatalogEntity catalogEntity) {
    if (catalogEntity instanceof GitReferencedCatalogEntity && !isEmpty(catalogEntity.getYaml())) {
      Map<String, Object> yamlMap = YamlUtils.loadYamlStringAsMap(catalogEntity.getYaml());
      Object spec = yamlMap.get("spec");
      if (spec instanceof Map) {
        catalogEntity.setSpec((Map<String, Object>) spec);
      }
    }

    String entityRef = catalogEntity.getQueryableEntityRef();
    Map<String, Object> spec = catalogEntity.getSpec();
    if (spec == null || !spec.containsKey(DEFINITION_KEY)) {
      log.warn("no spec.definition found entityRef={}", entityRef);
      throw new InvalidRequestException("Entity has no spec.definition");
    }

    Object definition = spec.get(DEFINITION_KEY);
    String placeholderKey = findPlaceholderKey(definition);
    String rawUrl;
    if (placeholderKey != null) {
      rawUrl = String.valueOf(((Map<?, ?>) definition).get(placeholderKey));
    } else if (definition instanceof String && ABSOLUTE_URL.matcher(((String) definition).trim()).matches()) {
      rawUrl = ((String) definition).trim();
      placeholderKey = TEXT_PLACEHOLDER;
    } else {
      return CatalogMapper.entityToResponse(catalogEntity, null, null, null, null, null, false);
    }

    String content = fetchContent(catalogEntity, rawUrl);
    log.info("resolved successfully entityRef={} contentLength={}", entityRef, content.length());

    Object resolvedDefinition;
    if (TEXT_PLACEHOLDER.equals(placeholderKey)) {
      resolvedDefinition = content;
    } else {
      try {
        resolvedDefinition = new Yaml().load(content);
      } catch (YAMLException ex) {
        throw new InvalidRequestException("Fetched content from " + PlaceholderProcessor.removeRefParam(rawUrl)
                + " is not valid YAML: " + ex.getMessage(),
            ex);
      }
    }

    Map<String, Object> resolvedSpec = new LinkedHashMap<>(spec);
    resolvedSpec.put(DEFINITION_KEY, resolvedDefinition);
    catalogEntity.setSpec(resolvedSpec);

    Object yamlNode;
    try {
      yamlNode = new Yaml().load(catalogEntity.getYaml());
    } catch (YAMLException ex) {
      return CatalogMapper.entityToResponse(catalogEntity, null, null, null, null, null, false);
    }
    injectResolvedDefinition(yamlNode, placeholderKey, content);
    catalogEntity.setYaml(new Yaml().dump(yamlNode));

    return CatalogMapper.entityToResponse(catalogEntity, null, null, null, null, null, false);
  }

  private String fetchContent(CatalogEntity catalogEntity, String rawUrl) {
    String entityRef = catalogEntity.getQueryableEntityRef();
    String cleanUrl = PlaceholderProcessor.removeRefParam(rawUrl);

    // 1. For Harness Code URLs, use synthetic connector (no real connector exists in DB)
    if (isHarnessCodeUrl(cleanUrl)) {
      log.info("fetching via harness code entityRef={} url={}", entityRef, cleanUrl);
      try {
        return fetchViaHarnessCode(catalogEntity, rawUrl);
      } catch (Exception ex) {
        log.error("harness code fetch failed entityRef={} url={} error={}", entityRef, rawUrl, ex.getMessage());
        throw new InvalidRequestException("Could not resolve API definition from Harness Code URL: " + cleanUrl, ex);
      }
    }

    // 2. If entity is a gitX entity, try via its connector
    if (catalogEntity instanceof GitReferencedCatalogEntity) {
      String gitXConnectorRef = ((GitReferencedCatalogEntity) catalogEntity).getConnectorRef();
      if (!isEmpty(gitXConnectorRef)) {
        log.info(
            "fetching via gitX connector entityRef={} connectorRef={} url={}", entityRef, gitXConnectorRef, rawUrl);
        try {
          return fetchViaConnector(catalogEntity, gitXConnectorRef, rawUrl);
        } catch (Exception ex) {
          log.warn("gitX connector fetch failed entityRef={} connectorRef={} url={} error={}", entityRef,
              gitXConnectorRef, rawUrl, ex.getMessage());
        }
      }
    }

    // 3. Try integration connector
    String integrationConnectorRef = placeholderProcessor.getConnectorRefForApiResolve(catalogEntity, cleanUrl);
    if (!isEmpty(integrationConnectorRef)) {
      boolean alreadyTried = (catalogEntity instanceof GitReferencedCatalogEntity)
          && integrationConnectorRef.equals(((GitReferencedCatalogEntity) catalogEntity).getConnectorRef());
      if (!alreadyTried) {
        log.info("fetching via integration connector entityRef={} connectorRef={} url={}", entityRef,
            integrationConnectorRef, rawUrl);
        try {
          return fetchViaConnector(catalogEntity, integrationConnectorRef, rawUrl);
        } catch (Exception ex) {
          log.error("integration connector fetch failed entityRef={} connectorRef={} url={} error={}", entityRef,
              integrationConnectorRef, rawUrl, ex.getMessage());
        }
      }
    }

    // 4. anonymous public fetch. Reject HTML responses (e.g. Azure DevOps UI pages).
    String rawContentUrl = toRawContentUrl(cleanUrl);
    log.info("attempting anonymous fetch entityRef={} url={}", entityRef, rawContentUrl);
    try {
      String content = specFetcher.fetch(rawContentUrl);
      if (looksLikeHtml(content)) {
        log.warn("anonymous fetch returned HTML entityRef={} url={}", entityRef, rawContentUrl);
        throw new InvalidRequestException("Could not resolve API definition from URL: " + cleanUrl);
      }
      return content;
    } catch (SpecFetchException ex) {
      log.info("anonymous fetch failed entityRef={} url={} error={}", entityRef, rawContentUrl, ex.getMessage());
    }

    throw new InvalidRequestException("Could not resolve API definition from URL: " + cleanUrl);
  }

  static boolean looksLikeHtml(String content) {
    if (content == null) {
      return false;
    }
    String trimmed = content.stripLeading();
    return trimmed.regionMatches(true, 0, "<!doctype", 0, 9) || trimmed.regionMatches(true, 0, "<html", 0, 5);
  }

  @SuppressWarnings("unchecked")
  private String fetchViaConnector(CatalogEntity catalogEntity, String connectorRef, String rawUrl) {
    ConnectorInfoDTO connectorInfoDTO = placeholderProcessor.getConnectorInfo(catalogEntity, connectorRef);
    String gitIntegrationType = gitIntegrationService.getGitIntegrationType(connectorInfoDTO);
    GitIntegrationOps<GitIntegrationEntity, ConnectorConfigDTO> gitIntegrationOps =
        (GitIntegrationOps<GitIntegrationEntity, ConnectorConfigDTO>) gitIntegrationService.getServiceForGitIntegration(
            gitIntegrationType);

    GitInfo gitInfo = placeholderProcessor.parseGitInfoWithRef(rawUrl);
    if (isEmpty(gitInfo.filePath)) {
      log.warn("could not parse filePath from URL entityRef={} url={} parsedProvider={}",
          catalogEntity.getQueryableEntityRef(), rawUrl, gitInfo.provider);
      throw new UnexpectedException("Could not parse filePath from URL: " + rawUrl);
    }
    // Empty branch is intentional — git-sync resolves to the repo's default branch.
    String branch = gitInfo.branch != null ? gitInfo.branch : "";

    String repoName = placeholderProcessor.resolveRepoName(connectorInfoDTO, gitInfo);
    return gitIntegrationOps.getFileContent(Scope.builder()
                                                .accountIdentifier(connectorInfoDTO.getAccountIdentifier())
                                                .orgIdentifier(connectorInfoDTO.getOrgIdentifier())
                                                .projectIdentifier(connectorInfoDTO.getProjectIdentifier())
                                                .build(),
        connectorInfoDTO.getIdentifier(), repoName, branch, gitInfo.filePath);
  }

  static String toRawContentUrl(String url) {
    if (url == null) {
      return url;
    }

    // GitHub: github.com/owner/repo/blob/branch/path → raw.githubusercontent.com/owner/repo/branch/path
    Matcher github = GITHUB_RAW.matcher(url);
    if (github.matches()) {
      return "https://raw.githubusercontent.com/" + github.group(1) + "/" + github.group(2) + "/" + github.group(3);
    }

    // GitLab: uses /-/ marker to distinguish from GHE. Must check before GHE.
    if (url.contains("/-/")) {
      Matcher gitlab = GITLAB_RAW.matcher(url);
      if (gitlab.matches()) {
        return gitlab.group(1) + "/-/raw/" + gitlab.group(2);
      }
    }

    // Bitbucket Cloud: bitbucket.org/owner/repo/src/branch/path → bitbucket.org/owner/repo/raw/branch/path
    Matcher bitbucket = BITBUCKET_CLOUD_RAW.matcher(url);
    if (bitbucket.matches()) {
      return bitbucket.group(1) + "/raw/" + bitbucket.group(2);
    }

    // Bitbucket Server: host/projects/P/repos/R/browse/file?at=... → host/projects/P/repos/R/raw/file?at=...
    Matcher bbServerBrowse = BITBUCKET_SERVER_BROWSE.matcher(url);
    if (bbServerBrowse.matches()) {
      return bbServerBrowse.group(1) + "/raw/" + bbServerBrowse.group(2) + bbServerBrowse.group(3);
    }

    // GitHub Enterprise: host/owner/repo/blob/branch/path → host/raw/owner/repo/branch/path
    // Checked last — the pattern is generic and would false-match GitLab/Bitbucket without /-/ guard above.
    Matcher gheBlob = GHE_RAW.matcher(url);
    if (gheBlob.matches()) {
      return gheBlob.group(1) + "/raw/" + gheBlob.group(2) + "/" + gheBlob.group(3) + "/" + gheBlob.group(4);
    }

    return url;
  }

  @SuppressWarnings("unchecked")
  private static void injectResolvedDefinition(Object yamlNode, String placeholderKey, String content) {
    if (!(yamlNode instanceof Map)) {
      return;
    }
    Map<String, Object> root = (Map<String, Object>) yamlNode;
    Object specObj = root.get(SPEC_KEY);
    if (!(specObj instanceof Map)) {
      return;
    }
    Map<String, Object> specMap = (Map<String, Object>) specObj;
    if (TEXT_PLACEHOLDER.equals(placeholderKey)) {
      specMap.put(DEFINITION_KEY, content);
    } else {
      specMap.put(DEFINITION_KEY, new Yaml().load(content));
    }
  }

  private static String findPlaceholderKey(Object definition) {
    if (!(definition instanceof Map)) {
      return null;
    }
    Map<?, ?> map = (Map<?, ?>) definition;
    if (map.containsKey(TEXT_PLACEHOLDER)) {
      return TEXT_PLACEHOLDER;
    }
    if (map.containsKey(YAML_PLACEHOLDER)) {
      return YAML_PLACEHOLDER;
    }
    if (map.containsKey(JSON_PLACEHOLDER)) {
      return JSON_PLACEHOLDER;
    }
    return null;
  }

  static boolean isHarnessCodeUrl(String url) {
    if (url == null) {
      return false;
    }
    try {
      String host = new URL(url).getHost();
      return host != null && host.endsWith(".harness.io") && url.contains("/module/code/");
    } catch (MalformedURLException ex) {
      return false;
    }
  }

  private String fetchViaHarnessCode(CatalogEntity catalogEntity, String rawUrl) {
    GitInfo gitInfo = placeholderProcessor.parseGitInfoWithRef(rawUrl);
    if (isEmpty(gitInfo.branch) || isEmpty(gitInfo.filePath) || isEmpty(gitInfo.repo)) {
      throw new InvalidRequestException("Could not parse repo/branch/filePath from Harness Code URL: " + rawUrl
          + ". Add ?ref=your-branch if needed.");
    }

    String accountIdentifier = catalogEntity.getAccountIdentifier();
    HarnessCodeRepoRef repoRef = parseHarnessCodeRepoRef(gitInfo.repo);
    String token = harnessCodeConnectorUtils.getToken(harnessCodeRepoConfig.getServiceClientSharedSecret());

    HarnessConnectorDTO harnessConnectorDTO =
        HarnessConnectorDTO.builder()
            .connectionType(GitConnectionType.REPO)
            .slug(repoRef.repoName)
            .accountId(accountIdentifier)
            .orgId(repoRef.org)
            .projectId(repoRef.project)
            .executeOnDelegate(false)
            .apiUrl(harnessCodeRepoConfig.getApiUrl())
            .gitBaseUrl(harnessCodeRepoConfig.getGitBaseUrl())
            .apiAccess(HarnessApiAccessDTO.builder()
                           .type(HarnessApiAccessType.JWT_TOKEN)
                           .spec(HarnessJWTTokenSpecDTO.builder()
                                     .tokenRef(SecretRefData.builder().decryptedValue(token.toCharArray()).build())
                                     .build())
                           .build())
            .build();

    GitFileRequest gitFileRequest =
        GitFileRequest.builder().branch(gitInfo.branch).filepath(gitInfo.filePath).getOnlyFileContent(true).build();

    GitFileResponse response = scmServiceClient.getFile(harnessConnectorDTO, gitFileRequest, scmBlockingStub);
    if (response.getStatusCode() >= 300) {
      throw new UnexpectedException("Harness Code file fetch failed: " + response.getError());
    }
    return response.getContent();
  }

  /**
   * Parses the URL path segment between /module/code/ and /files/ into org, project, repoName.
   * URL formats:
   *   repos/{repo}                              → org=null, project=null, repoName={repo}
   *   orgs/{org}/repos/{repo}                   → org={org}, project=null, repoName={repo}
   *   orgs/{org}/projects/{proj}/repos/{repo}   → org={org}, project={proj}, repoName={repo}
   */
  static HarnessCodeRepoRef parseHarnessCodeRepoRef(String repoPathSegment) {
    String[] parts = repoPathSegment.split("/");
    HarnessCodeRepoRef ref = new HarnessCodeRepoRef();

    for (int i = 0; i < parts.length; i++) {
      if ("repos".equals(parts[i]) && i + 1 < parts.length) {
        ref.repoName = parts[i + 1];
      } else if ("orgs".equals(parts[i]) && i + 1 < parts.length) {
        ref.org = parts[i + 1];
      } else if ("projects".equals(parts[i]) && i + 1 < parts.length) {
        ref.project = parts[i + 1];
      }
    }

    if (ref.repoName == null) {
      ref.repoName = repoPathSegment;
    }
    return ref;
  }

  static class HarnessCodeRepoRef {
    String org;
    String project;
    String repoName;
  }
}
