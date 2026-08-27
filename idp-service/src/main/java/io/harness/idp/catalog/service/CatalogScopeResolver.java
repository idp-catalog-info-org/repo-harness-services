/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.service;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.idp.catalog.cache.CachedOrgInfo;
import io.harness.idp.catalog.cache.CachedProjectInfo;
import io.harness.idp.catalog.cache.CatalogOrgCache;
import io.harness.idp.catalog.cache.CatalogProjectCache;
import io.harness.idp.catalog.cache.CatalogScopeTopologyCache;
import io.harness.idp.catalog.cache.ScopeTopology;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.OrganizationDTO;
import io.harness.ng.core.dto.OrganizationResponse;
import io.harness.ng.core.dto.ProjectDTO;
import io.harness.ng.core.dto.ProjectResponse;
import io.harness.organization.remote.OrganizationClient;
import io.harness.project.remote.ProjectClient;
import io.harness.remote.client.NGRestUtils;
import io.harness.scopeinfoclient.remote.ScopeInfoClient;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class CatalogScopeResolver {
  private static final String GET_ENTITIES_FLOW_LOG = "[getEntities flow]";
  private static final int PROJECT_PAGE_SIZE = 100;

  private final CatalogScopeTopologyCache scopeTopologyCache;
  private final CatalogOrgCache orgCache;
  private final CatalogProjectCache projectCache;
  private final OrganizationClient organizationClient;
  private final ProjectClient projectClient;
  private final ScopeInfoClient scopeInfoClient;
  private final ExecutorService scopeProjectValidatorExecutor;

  @Inject
  public CatalogScopeResolver(CatalogScopeTopologyCache scopeTopologyCache, CatalogOrgCache orgCache,
      CatalogProjectCache projectCache, @Named("PRIVILEGED") OrganizationClient organizationClient,
      @Named("PRIVILEGED") ProjectClient projectClient, @Named("PRIVILEGED") ScopeInfoClient scopeInfoClient,
      @Named("ScopeProjectValidator") ExecutorService scopeProjectValidatorExecutor) {
    this.scopeTopologyCache = scopeTopologyCache;
    this.orgCache = orgCache;
    this.projectCache = projectCache;
    this.organizationClient = organizationClient;
    this.projectClient = projectClient;
    this.scopeInfoClient = scopeInfoClient;
    this.scopeProjectValidatorExecutor = scopeProjectValidatorExecutor;
  }

  public ScopeTopology buildScopeTopology(String accountIdentifier) {
    return buildAndCacheTopology(accountIdentifier);
  }

  public ScopeResolveResult resolve(String accountIdentifier, String scopes) {
    ScopeTopology topology = scopeTopologyCache.get(accountIdentifier);
    if (topology == null) {
      log.info("{} Scope topology cache miss account={}. Building topology.", GET_ENTITIES_FLOW_LOG, accountIdentifier);
      topology = buildScopeTopology(accountIdentifier);
    } else {
      log.info("{} Scope topology cache hit account={} orgs={}", GET_ENTITIES_FLOW_LOG, accountIdentifier,
          topology.getOrgs() == null ? 0 : topology.getOrgs().size());
    }
    List<String> requestedUniqueIds = topology.resolveParentUniqueIds(scopes);
    if (requestedUniqueIds.isEmpty()) {
      requestedUniqueIds.add(accountIdentifier);
    }

    List<ScopeInfo> scopeInfos = topology.buildScopeInfos(requestedUniqueIds);
    if (scopeInfos.isEmpty()) {
      scopeInfos.add(ScopeInfo.builder()
                         .accountIdentifier(accountIdentifier)
                         .scopeType(ScopeLevel.ACCOUNT)
                         .uniqueId(accountIdentifier)
                         .build());
    }

    log.info("{} Scope resolver completed account={} scopes={} requestedUniqueIds={} resolvedScopeInfos={}",
        GET_ENTITIES_FLOW_LOG, accountIdentifier, scopes, requestedUniqueIds.size(), scopeInfos.size());

    return ScopeResolveResult.builder().scopeInfos(scopeInfos).topology(topology).build();
  }

  public ScopeInfo resolveSingleScopeInfo(String accountIdentifier, String scope) {
    String[] scopes = scope.split(",");
    if (scopes.length > 1) {
      throw new IllegalArgumentException(
          "Cannot have multiple scopes for account = " + accountIdentifier + ", scope = " + scope);
    }
    ScopeResolveResult resolveResult = resolve(accountIdentifier, scope);
    ScopeInfo scopeInfo = resolveResult.getScopeInfos().get(0);
    String[] scopeSplit = scopes[0].split("\\.");
    String orgIdentifier = scopeSplit.length >= 2 ? scopeSplit[1] : null;
    String projectIdentifier = scopeSplit.length == 3 ? scopeSplit[2] : null;
    ScopeLevel requestedScope;
    if (orgIdentifier != null && projectIdentifier != null) {
      requestedScope = ScopeLevel.PROJECT;
    } else if (orgIdentifier != null) {
      requestedScope = ScopeLevel.ORGANIZATION;
    } else {
      requestedScope = ScopeLevel.ACCOUNT;
    }
    if (!requestedScope.equals(scopeInfo.getScopeType())) {
      log.error("Cannot resolve scope from Cache. Falling back to the scopeInfo API call...");
      return NGRestUtils.getResponse(scopeInfoClient.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier));
    }
    return scopeInfo;
  }

  private ScopeTopology buildAndCacheTopology(String accountIdentifier) {
    log.info("{} Building scope topology account={}", GET_ENTITIES_FLOW_LOG, accountIdentifier);
    Set<String> orgIdentifiers = listAllOrgs(accountIdentifier);
    if (isEmpty(orgIdentifiers)) {
      ScopeTopology topology = ScopeTopology.builder().accountUniqueId(accountIdentifier).orgs(new HashMap<>()).build();
      scopeTopologyCache.put(accountIdentifier, topology);
      log.info("{} Built empty scope topology account={} orgs=0", GET_ENTITIES_FLOW_LOG, accountIdentifier);
      return topology;
    }

    Map<String, String> orgUniqueIds = getOrgUniqueIds(accountIdentifier, orgIdentifiers);

    Map<String, CompletableFuture<OrgProcessingResult>> futures = new HashMap<>();
    for (String orgId : orgIdentifiers) {
      String orgUniqueId = orgUniqueIds.get(orgId);
      if (orgUniqueId == null) {
        continue;
      }
      futures.put(orgId,
          CompletableFuture.supplyAsync(
              () -> processOrgProjects(accountIdentifier, orgId), scopeProjectValidatorExecutor));
    }

    Map<String, ScopeTopology.OrgNode> orgs = new HashMap<>();
    for (Map.Entry<String, CompletableFuture<OrgProcessingResult>> entry : futures.entrySet()) {
      String orgId = entry.getKey();
      String orgUniqueId = orgUniqueIds.get(orgId);
      try {
        OrgProcessingResult result = entry.getValue().join();
        ScopeTopology.OrgNode orgNode =
            ScopeTopology.OrgNode.builder().uniqueId(orgUniqueId).projects(result.getProjectUniqueIds()).build();
        orgs.put(orgId, orgNode);
      } catch (Exception ex) {
        log.error("{} Failed to process projects for org={} in account={}. Skipping this org from topology. "
                + "Error={}",
            GET_ENTITIES_FLOW_LOG, orgId, accountIdentifier, ex.getMessage(), ex);
      }
    }

    ScopeTopology topology = ScopeTopology.builder().accountUniqueId(accountIdentifier).orgs(orgs).build();
    scopeTopologyCache.put(accountIdentifier, topology);
    log.info("{} Built scope topology account={} requestedOrgs={} resolvedOrgs={} totalUniqueIds={}",
        GET_ENTITIES_FLOW_LOG, accountIdentifier, orgIdentifiers.size(), orgs.size(),
        topology.getAllUniqueIds().size());
    return topology;
  }

  private Set<String> listAllOrgs(String accountIdentifier) {
    try {
      List<OrganizationResponse> responses =
          NGRestUtils.getResponse(organizationClient.listAllOrganizations(accountIdentifier, new ArrayList<>(), null))
              .getContent();

      Map<String, CachedOrgInfo> toCache = new HashMap<>();
      Set<String> orgIdentifiers = new HashSet<>();
      for (OrganizationResponse resp : responses) {
        OrganizationDTO org = resp.getOrganization();
        orgIdentifiers.add(org.getIdentifier());
        toCache.put(
            org.getIdentifier(), CachedOrgInfo.builder().identifier(org.getIdentifier()).name(org.getName()).build());
      }
      if (!toCache.isEmpty()) {
        orgCache.putAll(accountIdentifier, toCache);
      }
      return orgIdentifiers;
    } catch (Exception ex) {
      log.error("{} Failed to list orgs for account={}. Failing closed (returning empty set). Error={}",
          GET_ENTITIES_FLOW_LOG, accountIdentifier, ex.getMessage(), ex);
      return new HashSet<>();
    }
  }

  private Map<String, String> getOrgUniqueIds(String accountIdentifier, Set<String> orgIdentifiers) {
    Map<String, String> orgUniqueIdMap = new HashMap<>();
    try {
      List<ScopeInfo> scopeInfos =
          NGRestUtils.getResponse(scopeInfoClient.getScopeInfoList(accountIdentifier, orgIdentifiers));
      if (scopeInfos != null) {
        for (ScopeInfo scopeInfo : scopeInfos) {
          orgUniqueIdMap.put(scopeInfo.getOrgIdentifier(), scopeInfo.getUniqueId());
        }
      }
    } catch (Exception ex) {
      log.warn("{} Failed to resolve org uniqueIds for account={}. Orgs without uniqueIds will be skipped. Error={}",
          GET_ENTITIES_FLOW_LOG, accountIdentifier, ex.getMessage(), ex);
    }
    return orgUniqueIdMap;
  }

  private OrgProcessingResult processOrgProjects(String accountIdentifier, String orgId) {
    Set<String> projectIdentifiers = listAllProjectsForOrg(accountIdentifier, orgId);
    if (isEmpty(projectIdentifiers)) {
      return new OrgProcessingResult(new HashMap<>());
    }
    Map<String, String> projectUniqueIds = getProjectUniqueIds(accountIdentifier, orgId, projectIdentifiers);
    return new OrgProcessingResult(projectUniqueIds);
  }

  private Set<String> listAllProjectsForOrg(String accountIdentifier, String orgId) {
    Set<String> projectIdentifiers = new HashSet<>();
    Map<String, CachedProjectInfo> toCache = new HashMap<>();
    int page = 0;
    while (true) {
      PageResponse<ProjectResponse> projects;
      try {
        projects = NGRestUtils.getResponse(projectClient.listWithMultiOrg(
            accountIdentifier, Set.of(orgId), false, null, null, null, page, PROJECT_PAGE_SIZE, null, false));
      } catch (Exception ex) {
        log.warn("{} Failed to list projects for org={} in account={} at page={}. Returning projects collected so "
                + "far. Error={}",
            GET_ENTITIES_FLOW_LOG, orgId, accountIdentifier, page, ex.getMessage(), ex);
        break;
      }
      if (projects == null || isEmpty(projects.getContent())) {
        break;
      }
      for (ProjectResponse resp : projects.getContent()) {
        ProjectDTO proj = resp.getProject();
        projectIdentifiers.add(proj.getIdentifier());
        toCache.put(CatalogProjectCache.buildProjectKey(proj.getOrgIdentifier(), proj.getIdentifier()),
            CachedProjectInfo.builder()
                .identifier(proj.getIdentifier())
                .orgIdentifier(proj.getOrgIdentifier())
                .name(proj.getName())
                .build());
      }
      if (projects.getContent().size() < PROJECT_PAGE_SIZE) {
        break;
      }
      page++;
    }
    if (!toCache.isEmpty()) {
      projectCache.putAll(accountIdentifier, toCache);
    }
    return projectIdentifiers;
  }

  private Map<String, String> getProjectUniqueIds(
      String accountIdentifier, String orgId, Set<String> projectIdentifiers) {
    Map<String, String> projectUniqueIdMap = new HashMap<>();
    try {
      List<ScopeInfo> scopeInfos =
          NGRestUtils.getResponse(scopeInfoClient.getScopeInfoList(accountIdentifier, orgId, projectIdentifiers));
      if (scopeInfos != null) {
        for (ScopeInfo scopeInfo : scopeInfos) {
          projectUniqueIdMap.put(scopeInfo.getProjectIdentifier(), scopeInfo.getUniqueId());
        }
      }
    } catch (Exception ex) {
      log.warn("{} Failed to resolve project uniqueIds for org={} in account={}. Projects for this org will be "
              + "excluded from topology. Error={}",
          GET_ENTITIES_FLOW_LOG, orgId, accountIdentifier, ex.getMessage(), ex);
    }
    return projectUniqueIdMap;
  }

  @Data
  @AllArgsConstructor
  private static class OrgProcessingResult {
    Map<String, String> projectUniqueIds;
  }

  /**
   * Resolves a namespace string (e.g. "account", "account.org1", "account.org1.proj1")
   * to its parentUniqueId. First attempts resolution via the cached ScopeTopology.
   * If the cache misses or the namespace can't be resolved from topology, falls back
   * to calling ScopeInfoClient.getScopeInfo() to resolve the uniqueId.
   */
  public String resolveNamespaceToUniqueId(String accountIdentifier, String namespace) {
    if (isEmpty(namespace)) {
      return null;
    }

    ScopeTopology topology = scopeTopologyCache.get(accountIdentifier);
    if (topology != null) {
      String uniqueId = topology.resolveNamespaceToUniqueId(namespace);
      if (uniqueId != null) {
        return uniqueId;
      }
    }

    // Fallback: parse namespace and call ScopeInfoClient
    String[] parts = namespace.split("\\.");
    String orgIdentifier = parts.length > 1 ? parts[1] : null;
    String projectIdentifier = parts.length > 2 ? parts[2] : null;

    try {
      ScopeInfo scopeInfo =
          NGRestUtils.getResponse(scopeInfoClient.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier));
      if (scopeInfo != null) {
        log.info("{} Resolved namespace via ScopeInfoClient fallback account={} namespace={} uniqueId={}",
            GET_ENTITIES_FLOW_LOG, accountIdentifier, namespace, scopeInfo.getUniqueId());
        return scopeInfo.getUniqueId();
      }
    } catch (Exception ex) {
      log.warn("{} Failed to resolve namespace via ScopeInfoClient account={} namespace={} error={}",
          GET_ENTITIES_FLOW_LOG, accountIdentifier, namespace, ex.getMessage(), ex);
    }
    return null;
  }

  /**
   * Returns the cached ScopeTopology for the account, building it if not present.
   * Used by graph traversal for batch namespace resolution during relation extraction.
   */
  public ScopeTopology getOrBuildTopology(String accountIdentifier) {
    ScopeTopology topology = scopeTopologyCache.get(accountIdentifier);
    if (topology == null) {
      log.info("{} Scope topology cache miss account={}. Building topology.", GET_ENTITIES_FLOW_LOG, accountIdentifier);
      topology = buildAndCacheTopology(accountIdentifier);
    }
    return topology;
  }

  @Data
  @Builder
  @AllArgsConstructor
  public static class ScopeResolveResult {
    List<ScopeInfo> scopeInfos;
    ScopeTopology topology;
  }
}
