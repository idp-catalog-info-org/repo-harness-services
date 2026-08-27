/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.processor;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.CommonUtils.addAccountScopeInIdentifier;
import static io.harness.idp.common.CommonUtils.from;
import static io.harness.idp.common.CommonUtils.getDomainFromUrl;
import static io.harness.idp.common.CommonUtils.removeScopeFromIdentifier;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.intfc.ScmConnector;
import io.harness.exception.UnexpectedException;
import io.harness.git.GitClientHelper;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.GitReferencedCatalogEntity;
import io.harness.idp.common.JacksonUtils;
import io.harness.idp.integrations.entities.IntegrationEntity;
import io.harness.idp.integrations.entities.git.GitIntegrationEntity;
import io.harness.idp.integrations.repositories.IntegrationEntityRepository;
import io.harness.idp.integrations.service.git.GitIntegrationOps;
import io.harness.idp.integrations.service.git.GitIntegrationServiceImpl;
import io.harness.remote.client.NGRestUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.inject.Inject;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class PlaceholderProcessor {
  private static final List<String> PLACEHOLDER_KEYS = List.of("$yaml", "$json", "$text");
  private static final Pattern ABSOLUTE_URL_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*://.*");

  public static class GitInfo {
    public String provider;
    public String owner;
    public String repo;
    public String branch;
    public String filePath;
  }

  @Inject private IntegrationEntityRepository integrationEntityRepository;
  @Inject private GitIntegrationServiceImpl gitIntegrationService;
  @Inject private ConnectorResourceClient connectorResourceClient;

  public String process(CatalogEntity catalogEntity) {
    String rawYaml = catalogEntity.getYaml();
    Object rawYamlNode = new Yaml().load(rawYaml);

    if (!hasPlaceholders(rawYamlNode)) {
      return rawYaml;
    }

    Map<String, Object> placeholders = new LinkedHashMap<>();
    collectAllPlaceholders(rawYamlNode, placeholders, "");

    Map<String, String> absoluteReferencePlaceholders =
        removeBranchParamsFromPlaceholderUrls(collectAllAbsoluteReferencePlaceholders(placeholders));

    String sourceLocation = sourceLocation(catalogEntity.getSourceLocation());

    absoluteReferencePlaceholders.forEach((k, v) -> {
      String connectorRef;
      if (!isEmpty(sourceLocation)
          && !Objects.equals(GitClientHelper.getGitSCM(v), GitClientHelper.getGitSCM(sourceLocation))) {
        connectorRef = getConnectorRefFromGitIntegration(catalogEntity, v);
      } else {
        connectorRef = getConnectorRef(catalogEntity, sourceLocation);
      }
      if (isEmpty(connectorRef)) {
        return;
      }
      ConnectorInfoDTO connectorInfoDTO = getConnectorInfo(catalogEntity, connectorRef);
      String gitIntegrationType = gitIntegrationService.getGitIntegrationType(connectorInfoDTO);
      GitIntegrationOps<GitIntegrationEntity, ConnectorConfigDTO> gitIntegrationOps =
          (GitIntegrationOps<GitIntegrationEntity, ConnectorConfigDTO>)
              gitIntegrationService.getServiceForGitIntegration(gitIntegrationType);
      GitInfo gitInfo = parseGitInfo(v);
      String fileContent =
          gitIntegrationOps.getFileContent(Scope.builder()
                                               .accountIdentifier(connectorInfoDTO.getAccountIdentifier())
                                               .orgIdentifier(connectorInfoDTO.getOrgIdentifier())
                                               .projectIdentifier(connectorInfoDTO.getProjectIdentifier())
                                               .build(),
              connectorInfoDTO.getIdentifier(), resolveRepoName(connectorInfoDTO, gitInfo), gitInfo.branch,
              gitInfo.filePath);
      absoluteReferencePlaceholders.put(k, fileContent);
    });

    replacePlaceholdersWithResolvedData(rawYamlNode, "", absoluteReferencePlaceholders);
    String resolvedYaml = new Yaml().dump(rawYamlNode);

    if (isEmpty(sourceLocation)) {
      return resolvedYaml;
    }

    Map<String, String> relativeResolvedReferencePlaceholders = removeBranchParamsFromPlaceholderUrls(
        resolvePlaceholderReferencesRelativeToSourceLocation(placeholders, sourceLocation));

    String connectorRef = getConnectorRef(catalogEntity, sourceLocation);
    if (isEmpty(connectorRef)) {
      return resolvedYaml;
    }

    ConnectorInfoDTO connectorInfoDTO = getConnectorInfo(catalogEntity, connectorRef);
    String gitIntegrationType = gitIntegrationService.getGitIntegrationType(connectorInfoDTO);
    GitIntegrationOps<GitIntegrationEntity, ConnectorConfigDTO> gitIntegrationOps =
        (GitIntegrationOps<GitIntegrationEntity, ConnectorConfigDTO>) gitIntegrationService.getServiceForGitIntegration(
            gitIntegrationType);

    relativeResolvedReferencePlaceholders.forEach((k, v) -> {
      GitInfo gitInfo = parseGitInfo(v);
      String fileContent =
          gitIntegrationOps.getFileContent(Scope.builder()
                                               .accountIdentifier(connectorInfoDTO.getAccountIdentifier())
                                               .orgIdentifier(connectorInfoDTO.getOrgIdentifier())
                                               .projectIdentifier(connectorInfoDTO.getProjectIdentifier())
                                               .build(),
              connectorInfoDTO.getIdentifier(), resolveRepoName(connectorInfoDTO, gitInfo), gitInfo.branch,
              gitInfo.filePath);
      relativeResolvedReferencePlaceholders.put(k, fileContent);
    });

    replacePlaceholdersWithResolvedData(rawYamlNode, "", relativeResolvedReferencePlaceholders);
    resolvedYaml = new Yaml().dump(rawYamlNode);

    return resolvedYaml;
  }

  public Map<String, Object> getPlaceholdersDecorator(String unresolvedYaml, String resolvedYaml) {
    Object unresolved = new Yaml().load(unresolvedYaml);
    Object resolved = new Yaml().load(resolvedYaml);
    return getPlaceholdersDecorator(unresolved, resolved);
  }

  private Map<String, Object> getPlaceholdersDecorator(Object unresolved, Object resolved) {
    Map<String, Object> diff = new LinkedHashMap<>();

    if (unresolved instanceof Map<?, ?> unresolvedMap && resolved instanceof Map<?, ?> resolvedMap) {
      for (Map.Entry<?, ?> entry : unresolvedMap.entrySet()) {
        String key = entry.getKey().toString();
        Object unresolvedVal = entry.getValue();
        Object resolvedVal = resolvedMap.get(key);

        if (unresolvedVal instanceof Map<?, ?> childMap) {
          Optional<String> placeholderKey =
              childMap.keySet().stream().map(Object::toString).filter(k -> k.startsWith("$")).findFirst();

          if (placeholderKey.isPresent()) {
            String placeholder = placeholderKey.get();
            // $text resolves to a raw String; dumping it again would re-encode the whole spec as a
            // single quoted YAML scalar (a string-of-a-string), which the OpenAPI parser then can't
            // read. Only structured values ($yaml/$json -> Map) need dumping to a YAML document.
            String resolvedYamlString =
                (resolvedVal instanceof String) ? (String) resolvedVal : new Yaml().dump(resolvedVal).trim();
            diff.put(key, Map.of(placeholder, resolvedYamlString));
          } else if (resolvedVal instanceof Map<?, ?>) {
            Map<String, Object> nestedDiff = getPlaceholdersDecorator(unresolvedVal, resolvedVal);
            if (!nestedDiff.isEmpty()) {
              diff.put(key, nestedDiff);
            }
          }
        }
      }
    }

    return diff;
  }

  private boolean hasPlaceholders(Object rawYamlNode) {
    if (rawYamlNode instanceof Map<?, ?> map) {
      for (Object key : map.keySet()) {
        if (key instanceof String && PLACEHOLDER_KEYS.contains(key)) {
          return true;
        }
        Object value = map.get(key);
        if (hasPlaceholders(value)) {
          return true;
        }
      }
    } else if (rawYamlNode instanceof Iterable<?> list) {
      for (Object item : list) {
        if (hasPlaceholders(item)) {
          return true;
        }
      }
    }
    return false;
  }

  private void collectAllPlaceholders(Object rawYamlNode, Map<String, Object> placeholders, String path) {
    if (rawYamlNode instanceof Map<?, ?> map) {
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        Object key = entry.getKey();
        Object value = entry.getValue();
        if (key instanceof String keyStr) {
          String currentPath = path.isEmpty() ? keyStr : path + "." + keyStr;
          if (PLACEHOLDER_KEYS.contains(keyStr)) {
            placeholders.put(currentPath, value);
          }
          collectAllPlaceholders(value, placeholders, currentPath);
        }
      }
    } else if (rawYamlNode instanceof Iterable<?> list) {
      int index = 0;
      for (Object item : list) {
        String currentPath = path + "[" + index + "]";
        collectAllPlaceholders(item, placeholders, currentPath);
        index++;
      }
    }
  }

  private Map<String, String> collectAllAbsoluteReferencePlaceholders(Map<String, Object> placeholders) {
    return placeholders.entrySet()
        .stream()
        .filter(e -> ABSOLUTE_URL_PATTERN.matcher(String.valueOf(e.getValue())).matches())
        .collect(
            Collectors.toMap(Map.Entry::getKey, e -> String.valueOf(e.getValue()), (a, b) -> a, LinkedHashMap::new));
  }

  private String sourceLocation(String sourceLocation) {
    if (isEmpty(sourceLocation)) {
      return sourceLocation;
    }
    if (sourceLocation.startsWith("url:")) {
      sourceLocation = sourceLocation.substring(4);
    }
    if (!sourceLocation.endsWith("/")) {
      sourceLocation = sourceLocation + "/";
    }
    return sourceLocation;
  }

  @SuppressWarnings("unchecked")
  public static void replacePlaceholdersWithResolvedData(Object node, String path, Map<String, String> replacements) {
    if (!(node instanceof Map<?, ?> rawMap)) {
      return;
    }

    List<String> keysToReplace = new ArrayList<>();

    for (Object keyObj : rawMap.keySet()) {
      String key = keyObj.toString();
      String currentPath = path.isEmpty() ? key : path + "." + key;
      Object value = rawMap.get(keyObj);

      if (value instanceof Map<?, ?> childMap) {
        for (Object childKeyObj : childMap.keySet()) {
          String childKey = childKeyObj.toString();
          String childPath = currentPath + "." + childKey;

          if (replacements.containsKey(childPath)) {
            keysToReplace.add(key);
            break;
          }
        }
        replacePlaceholdersWithResolvedData(childMap, currentPath, replacements);
      }
    }

    for (String key : keysToReplace) {
      String placeholderPath = path.isEmpty() ? key : path + "." + key;
      Map<?, ?> childMap = (Map<?, ?>) rawMap.get(key);

      String placeholderKey =
          childMap.keySet().stream().map(Object::toString).filter(k -> k.startsWith("$")).findFirst().orElse(null);

      if (placeholderKey == null)
        continue;

      String fullPath = placeholderPath + "." + placeholderKey;
      String content = replacements.get(fullPath);

      Object replacedValue;
      switch (placeholderKey) {
        case "$yaml":
          replacedValue = new Yaml().load(content);
          break;
        case "$json":
          try {
            replacedValue = new Yaml().load(JacksonUtils.JSON_MAPPER.readTree(content).toString());
          } catch (JsonProcessingException ex) {
            throw new UnexpectedException(ex.getMessage());
          }
          break;
        case "$text":
        default:
          replacedValue = content;
      }

      ((Map<String, Object>) rawMap).put(key, replacedValue);
    }
  }

  private Map<String, String> resolvePlaceholderReferencesRelativeToSourceLocation(
      Map<String, Object> placeholders, String sourceLocation) {
    return placeholders.entrySet()
        .stream()
        .filter(e -> !ABSOLUTE_URL_PATTERN.matcher(String.valueOf(e.getValue())).matches())
        .collect(Collectors.toMap(Map.Entry::getKey,
            e
            -> sourceLocation + String.valueOf(e.getValue()).replaceFirst("^\\./", "").replaceFirst("^/", ""),
            (a, b) -> a, LinkedHashMap::new));
  }

  public String getConnectorRef(CatalogEntity catalogEntity, String sourceLocation) {
    if (catalogEntity instanceof GitReferencedCatalogEntity) {
      return ((GitReferencedCatalogEntity) catalogEntity).getConnectorRef();
    }

    String connectorRefFromSpec = getConnectorRefFromEntitySpecSourceCode(catalogEntity);
    if (!isEmpty(connectorRefFromSpec)) {
      return connectorRefFromSpec;
    }

    return getConnectorRefFromGitIntegration(catalogEntity, sourceLocation);
  }

  private String getConnectorRefFromEntitySpecSourceCode(CatalogEntity catalogEntity) {
    Map<String, Object> sourceCode = from(catalogEntity.getSpec(), "sourceCode", Map.class);
    return isEmpty(sourceCode) ? null : from(sourceCode, "connectorRef", String.class);
  }

  private String getConnectorRefFromGitIntegration(CatalogEntity catalogEntity, String url) {
    String domain = getDomainFromUrl(url);
    List<IntegrationEntity> gitIntegrations = integrationEntityRepository.findByAccountIdentifierAndAdditionalIndexer(
        catalogEntity.getAccountIdentifier(), domain);

    return gitIntegrations.stream()
        .map(entity -> (GitIntegrationEntity) entity)
        .filter(entity -> domain.equals(entity.getHost()))
        .findAny()
        .map(entity -> addAccountScopeInIdentifier(entity.getConnectorIdentifier()))
        .orElse(null);
  }

  public String getConnectorRefForApiResolve(CatalogEntity catalogEntity, String url) {
    String domain = getDomainFromUrl(url);
    String additionalIndexer =
        GitIntegrationServiceImpl.AZURE_CLOUD_PATTERN.matcher(domain).find() ? extractAzureOrg(url) : domain;
    List<IntegrationEntity> gitIntegrations = integrationEntityRepository.findByAccountIdentifierAndAdditionalIndexer(
        catalogEntity.getAccountIdentifier(), additionalIndexer);

    Optional<GitIntegrationEntity> byPrimaryHost = gitIntegrations.stream()
                                                       .map(entity -> (GitIntegrationEntity) entity)
                                                       .filter(entity -> domain.equals(entity.getHost()))
                                                       .findAny();
    if (byPrimaryHost.isPresent()) {
      return addAccountScopeInIdentifier(byPrimaryHost.get().getConnectorIdentifier());
    }

    if (domain.endsWith(".harness.io")) {
      return integrationEntityRepository
          .findByAccountIdentifierAndIntegrationAndManagedTrue(
              catalogEntity.getAccountIdentifier(), IntegrationEntity.Integration.GIT)
          .stream()
          .filter(e -> IntegrationEntity.ParentType.HARNESS_CODE_REPO.equals(e.getParentType()))
          .findFirst()
          .map(e -> addAccountScopeInIdentifier(((GitIntegrationEntity) e).getConnectorIdentifier()))
          .orElse(null);
    }

    return null;
  }

  private String extractAzureOrg(String url) {
    Matcher m = GitIntegrationServiceImpl.AZURE_ORG_PATTERN.matcher(url);
    return m.find() ? m.group(1) : getDomainFromUrl(url);
  }

  public ConnectorInfoDTO getConnectorInfo(CatalogEntity catalogEntity, String connectorRef) {
    String connectorOrgIdentifier = null;
    String connectorProjectIdentifier = null;
    if (connectorRef.startsWith("org.")) {
      connectorOrgIdentifier = catalogEntity.getOrgIdentifier();
    }
    if (!connectorRef.startsWith("account.")) {
      connectorProjectIdentifier = catalogEntity.getProjectIdentifier();
    }
    Optional<ConnectorDTO> connectorDTO;
    try {
      connectorDTO = NGRestUtils.getResponse(connectorResourceClient.get(removeScopeFromIdentifier(connectorRef),
          catalogEntity.getAccountIdentifier(), connectorOrgIdentifier, connectorProjectIdentifier));
    } catch (Exception ex) {
      throw new UnexpectedException("Unexpected error in fetching connector details");
    }
    if (connectorDTO.isEmpty()) {
      throw new UnexpectedException("Connector " + connectorRef + " not found");
    }
    return connectorDTO.get().getConnectorInfo();
  }

  /**
   * Resolves the repoName to pass to git-sync getFile.
   *
   * <p>By default we keep the bare repo name (existing behaviour) so that REPO-type connectors and
   * org-anchored ACCOUNT connectors are untouched. Only when the connector is an ACCOUNT-type
   * connector whose URL carries no owner/org (host-only, e.g. {@code https://ghe.company.com/}) do
   * we qualify the repo with the owner parsed from the placeholder URL ({@code owner/repo}). This is
   * the sole case where the owner would otherwise be lost, and git-sync rebuilds the fetch URL as
   * {@code connectorUrl + "/" + repoName}, so a host-only base + {@code owner/repo} yields the
   * correct URL for any repo the connector's credentials can access.
   */
  public String resolveRepoName(ConnectorInfoDTO connectorInfoDTO, GitInfo gitInfo) {
    if (isEmpty(gitInfo.owner) || isEmpty(gitInfo.repo)) {
      return gitInfo.repo;
    }
    ConnectorConfigDTO connectorConfig = connectorInfoDTO.getConnectorConfig();
    if (!(connectorConfig instanceof ScmConnector scmConnector)) {
      return gitInfo.repo;
    }
    boolean isAccountLevel = GitConnectionType.ACCOUNT.equals(scmConnector.getConnectionTypeForGit());
    boolean isHostOnly;
    try {
      isHostOnly =
          scmConnector.getGitRepositoryDetails() == null || isEmpty(scmConnector.getGitRepositoryDetails().getOrg());
    } catch (Exception ex) {
      // Defensive: if the connector URL cannot be parsed for org, fall back to bare repo.
      return gitInfo.repo;
    }
    if (isAccountLevel && isHostOnly) {
      return gitInfo.owner + "/" + gitInfo.repo;
    }
    return gitInfo.repo;
  }

  public GitInfo parseGitInfo(String url) {
    GitInfo info = new GitInfo();

    if (url.contains("/_git/")) {
      info.provider = "azure";
      parseAzureGitInfo(url, info);
    } else if (url.matches(".*(blob|tree)/.*")) {
      if (url.contains("/-/")) {
        info.provider = "gitlab";
        parseGitLabGitInfo(url, info);
      } else {
        info.provider = "github";
        parseGitHubGitInfo(url, info);
      }
    } else if (url.contains("/src/")) {
      info.provider = "bitbucket-cloud";
      parseBitbucketCloudGitInfo(url, info);
    } else if (url.contains("/projects/") && url.contains("/repos/") && url.contains("/browse/")) {
      info.provider = "bitbucket-server";
      parseBitbucketServerGitInfo(url, info);
    }

    return info;
  }

  private void parseAzureGitInfo(String url, GitInfo gitInfo) {
    URL parsed;
    try {
      parsed = new URL(url);
    } catch (MalformedURLException ex) {
      throw new UnexpectedException(ex.getMessage());
    }
    String[] parts = parsed.getPath().split("/");
    gitInfo.repo = parts[parts.length - 1];

    String query = parsed.getQuery();
    if (query == null) {
      return;
    }
    Matcher branchM = Pattern.compile("version=GB([^&/]+)").matcher(query);
    if (branchM.find()) {
      gitInfo.branch = branchM.group(1);
    }

    Matcher pathM = Pattern.compile("path=([^&]+)").matcher(query);
    if (pathM.find()) {
      gitInfo.filePath = pathM.group(1);
      return;
    }
    Matcher rel = Pattern.compile("version=GB[^/]+/(.+)").matcher(query);
    if (rel.find()) {
      gitInfo.filePath = rel.group(1);
    }
  }

  private void parseGitHubGitInfo(String url, GitInfo gitInfo) {
    Matcher m = Pattern.compile("https?://[^/]+/([^/]+)/([^/]+)/(blob|tree)/([^/]+)/(.+)").matcher(url);
    if (!m.find()) {
      return;
    }
    gitInfo.owner = m.group(1);
    gitInfo.repo = m.group(2);
    gitInfo.branch = m.group(4);
    gitInfo.filePath = m.group(5);
  }

  public GitInfo parseGitInfoWithRef(String url) {
    GitInfo info = new GitInfo();
    if (url.contains("/_git/")) {
      info.provider = "azure";
      parseAzureGitInfoForApiResolve(url, info);
    } else if (url.contains("/repos/") && url.contains("/files/") && url.contains("/~/")) {
      info.provider = "harness-code";
      parseHarnessCodeGitInfoForApiResolve(url, info);
    } else if (url.matches(".*(blob|tree)/.*")) {
      if (url.contains("/-/")) {
        info.provider = "gitlab";
        parseGitLabGitInfoForApiResolve(url, info);
      } else {
        info.provider = "github";
        parseGitHubGitInfoForApiResolve(url, info);
      }
    } else if (url.contains("/src/")) {
      info.provider = "bitbucket-cloud";
      parseBitbucketCloudGitInfoForApiResolve(url, info);
    } else if (url.contains("/projects/") && url.contains("/repos/") && url.contains("/browse/")) {
      info.provider = "bitbucket-server";
      parseBitbucketServerGitInfoForApiResolve(url, info);
    }
    return info;
  }

  private void parseGitHubGitInfoForApiResolve(String url, GitInfo gitInfo) {
    String refFromQuery = extractRefQueryParam(url);
    String pathUrl = url.contains("?") ? url.substring(0, url.indexOf('?')) : url;
    Matcher m = Pattern.compile("https?://[^/]+/([^/]+)/([^/]+)/(?:blob|tree)/(.+)").matcher(pathUrl);
    if (!m.find()) {
      return;
    }
    gitInfo.owner = m.group(1);
    gitInfo.repo = m.group(2);
    String afterBlobTree = m.group(3);
    if (refFromQuery != null) {
      gitInfo.branch = refFromQuery;
      String branchPrefix = refFromQuery + "/";
      gitInfo.filePath =
          afterBlobTree.startsWith(branchPrefix) ? afterBlobTree.substring(branchPrefix.length()) : afterBlobTree;
    } else {
      int slash = afterBlobTree.indexOf('/');
      if (slash >= 0) {
        gitInfo.branch = afterBlobTree.substring(0, slash);
        gitInfo.filePath = afterBlobTree.substring(slash + 1);
      } else {
        gitInfo.branch = afterBlobTree;
        gitInfo.filePath = "";
      }
    }
  }

  private void parseGitLabGitInfoForApiResolve(String url, GitInfo gitInfo) {
    String refFromQuery = extractRefQueryParam(url);
    String pathUrl = url.contains("?") ? url.substring(0, url.indexOf('?')) : url;
    Matcher m = Pattern.compile("https?://[^/]+/(?:[^/]+/)*([^/]+)/-/(?:blob|tree)/(.+)").matcher(pathUrl);
    if (!m.find()) {
      return;
    }
    gitInfo.repo = m.group(1);
    String afterBlobTree = m.group(2);
    if (refFromQuery != null) {
      gitInfo.branch = refFromQuery;
      String branchPrefix = refFromQuery + "/";
      gitInfo.filePath =
          afterBlobTree.startsWith(branchPrefix) ? afterBlobTree.substring(branchPrefix.length()) : afterBlobTree;
    } else {
      int slash = afterBlobTree.indexOf('/');
      if (slash >= 0) {
        gitInfo.branch = afterBlobTree.substring(0, slash);
        gitInfo.filePath = afterBlobTree.substring(slash + 1);
      } else {
        gitInfo.branch = afterBlobTree;
        gitInfo.filePath = "";
      }
    }
  }

  private void parseBitbucketCloudGitInfoForApiResolve(String url, GitInfo gitInfo) {
    String pathUrl = url.contains("?") ? url.substring(0, url.indexOf('?')) : url;
    Matcher m = Pattern.compile("https?://[^/]+/[^/]+/([^/]+)/src/(.+)").matcher(pathUrl);
    if (!m.find()) {
      return;
    }
    gitInfo.repo = m.group(1);
    String afterSrc = m.group(2);

    Matcher atM = Pattern.compile("[?&]at=([^&]+)").matcher(url);
    if (atM.find()) {
      String atValue = URLDecoder.decode(atM.group(1), StandardCharsets.UTF_8);
      gitInfo.branch = atValue.startsWith("refs/heads/") ? atValue.substring("refs/heads/".length()) : atValue;
      String branchPrefix = gitInfo.branch + "/";
      gitInfo.filePath = afterSrc.startsWith(branchPrefix) ? afterSrc.substring(branchPrefix.length()) : afterSrc;
    } else {
      int slash = afterSrc.indexOf('/');
      if (slash >= 0) {
        gitInfo.branch = afterSrc.substring(0, slash);
        gitInfo.filePath = afterSrc.substring(slash + 1);
      } else {
        gitInfo.branch = afterSrc;
        gitInfo.filePath = "";
      }
    }
  }

  private void parseHarnessCodeGitInfoForApiResolve(String url, GitInfo gitInfo) {
    String pathUrl = url.contains("?") ? url.substring(0, url.indexOf('?')) : url;
    // Non-greedy capture picks the shortest slug before /files/refs/heads/, covering all three scopes:
    //   account: repos/{repo}
    //   org:     orgs/{org}/repos/{repo}
    //   project: orgs/{org}/projects/{project}/repos/{repo}
    // The connector's getGitConnectionUrl builds: baseUrl + "/" + repoSlug
    Matcher m = Pattern.compile(".*/module/code/(.*?)/files/refs/heads/(.+)/~/(.+)").matcher(pathUrl);
    if (!m.find()) {
      return;
    }
    gitInfo.repo = m.group(1);
    String refFromQuery = extractRefQueryParam(url);
    gitInfo.branch = (refFromQuery != null) ? refFromQuery : m.group(2);
    gitInfo.filePath = m.group(3);
  }

  public static String removeRefParam(String url) {
    if (url == null || !url.contains("ref=")) {
      return url;
    }
    return url.replaceAll("[?&]ref=[^&]*", "").replaceFirst("[?&]$", "");
  }

  public static String extractRefQueryParam(String url) {
    if (url == null) {
      return null;
    }
    int qIdx = url.indexOf('?');
    if (qIdx < 0) {
      return null;
    }
    for (String param : url.substring(qIdx + 1).split("&")) {
      if (param.startsWith("ref=")) {
        String value = param.substring(4);
        return value.isEmpty() ? null : value;
      }
    }
    return null;
  }

  private void parseGitLabGitInfo(String url, GitInfo gitInfo) {
    Matcher m = Pattern.compile("https?://[^/]+/(?:[^/]+/)*([^/]+)/-/((?:blob|tree))/([^/]+)/(.+)").matcher(url);
    if (!m.find()) {
      return;
    }
    gitInfo.repo = m.group(1);
    gitInfo.branch = m.group(2);
    gitInfo.filePath = m.group(3);
  }

  private void parseAzureGitInfoForApiResolve(String url, GitInfo gitInfo) {
    URL parsed;
    try {
      parsed = new URL(url);
    } catch (MalformedURLException ex) {
      throw new UnexpectedException(ex.getMessage());
    }
    String[] parts = parsed.getPath().split("/");
    gitInfo.repo = parts[parts.length - 1];

    String query = parsed.getQuery();
    if (query == null) {
      return;
    }
    Matcher branchM = Pattern.compile("version=GB([^&]+)").matcher(query);
    if (branchM.find()) {
      gitInfo.branch = branchM.group(1);
    }

    Matcher pathM = Pattern.compile("path=([^&]+)").matcher(query);
    if (pathM.find()) {
      String filePath = pathM.group(1);
      gitInfo.filePath = filePath.startsWith("/") ? filePath.substring(1) : filePath;
    }
  }

  private void parseBitbucketServerGitInfoForApiResolve(String url, GitInfo gitInfo) {
    Matcher m = Pattern.compile("/projects/([^/]+)/repos/([^/]+)/browse/([^?]+)").matcher(url);
    if (m.find()) {
      gitInfo.repo = m.group(2);
      gitInfo.filePath = m.group(3);
    }
    Matcher atM = Pattern.compile("[?&]at=([^&]+)").matcher(url);
    if (atM.find()) {
      String atValue = URLDecoder.decode(atM.group(1), StandardCharsets.UTF_8);
      gitInfo.branch = atValue.startsWith("refs/heads/") ? atValue.substring("refs/heads/".length()) : atValue;
    }
  }

  private void parseBitbucketCloudGitInfo(String url, GitInfo gitInfo) {
    Matcher m = Pattern.compile("https?://[^/]+/[^/]+/([^/]+)/src/([^/]+)/(.+)").matcher(url);
    if (!m.find()) {
      return;
    }
    gitInfo.repo = m.group(1);
    gitInfo.branch = m.group(2);
    gitInfo.filePath = m.group(3);
  }

  private void parseBitbucketServerGitInfo(String url, GitInfo gitInfo) {
    Matcher m = Pattern.compile("/projects/([^/]+)/repos/([^/]+)/browse/(.+)").matcher(url);
    if (m.find()) {
      gitInfo.repo = m.group(2);
      gitInfo.filePath = m.group(3);
    }
    Matcher branchM = Pattern.compile("[?&]at=refs/heads/([^&]+)").matcher(url);
    if (branchM.find()) {
      gitInfo.branch = branchM.group(1);
    }
  }

  private static Map<String, String> removeBranchParamsFromPlaceholderUrls(Map<String, String> placeholders) {
    Map<String, String> cleaned = new LinkedHashMap<>();
    placeholders.forEach((k, v) -> cleaned.put(k, stripBranchParams(v)));
    return cleaned;
  }

  private static String stripBranchParams(String url) {
    if (url == null) {
      return null;
    }
    return url.replaceFirst("[?]ref=.*$", "");
  }
}
