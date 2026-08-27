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
import io.harness.idp.catalog.cache.CachedOrgInfo;
import io.harness.idp.catalog.cache.CachedProjectInfo;
import io.harness.idp.catalog.cache.CatalogOrgCache;
import io.harness.idp.catalog.cache.CatalogProjectCache;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.OrganizationDTO;
import io.harness.ng.core.dto.OrganizationResponse;
import io.harness.ng.core.dto.ProjectDTO;
import io.harness.ng.core.dto.ProjectResponse;
import io.harness.organization.remote.OrganizationClient;
import io.harness.project.remote.ProjectClient;
import io.harness.remote.client.NGRestUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class CatalogOrgProjectService {
  private static final String GET_ENTITIES_FLOW_LOG = "[getEntities flow]";
  private final CatalogOrgCache orgCache;
  private final CatalogProjectCache projectCache;
  private final OrganizationClient organizationClient;
  private final ProjectClient projectClient;

  @Inject
  public CatalogOrgProjectService(CatalogOrgCache orgCache, CatalogProjectCache projectCache,
      @Named("PRIVILEGED") OrganizationClient organizationClient, @Named("PRIVILEGED") ProjectClient projectClient) {
    this.orgCache = orgCache;
    this.projectCache = projectCache;
    this.organizationClient = organizationClient;
    this.projectClient = projectClient;
  }

  public String getOrgName(String accountId, String orgId) {
    if (isEmpty(orgId)) {
      return null;
    }
    CachedOrgInfo cached = orgCache.get(accountId, orgId);
    if (cached != null) {
      return cached.getName();
    }
    try {
      Optional<OrganizationResponse> response =
          NGRestUtils.getResponse(organizationClient.getOrganization(orgId, accountId));
      if (response.isPresent()) {
        OrganizationDTO org = response.get().getOrganization();
        orgCache.put(
            accountId, orgId, CachedOrgInfo.builder().identifier(org.getIdentifier()).name(org.getName()).build());
        return org.getName();
      }
    } catch (Exception ex) {
      log.warn("Failed to fetch org name for orgId={}. Error={}", orgId, ex.getMessage(), ex);
    }
    return null;
  }

  public String getProjectName(String accountId, String orgId, String projectId) {
    if (isEmpty(orgId) || isEmpty(projectId)) {
      return null;
    }
    CachedProjectInfo cached = projectCache.get(accountId, orgId, projectId);
    if (cached != null) {
      return cached.getName();
    }
    try {
      Optional<ProjectResponse> response =
          NGRestUtils.getResponse(projectClient.getProject(projectId, accountId, orgId));
      if (response.isPresent()) {
        ProjectDTO proj = response.get().getProject();
        projectCache.put(accountId, orgId, projectId,
            CachedProjectInfo.builder()
                .identifier(proj.getIdentifier())
                .orgIdentifier(proj.getOrgIdentifier())
                .name(proj.getName())
                .build());
        return proj.getName();
      }
    } catch (Exception ex) {
      log.warn("Failed to fetch project name for org={}, project={}. Error={}", orgId, projectId, ex.getMessage(), ex);
    }
    return null;
  }

  public Map<String, String> getOrgNames(String accountId, Set<String> orgIds) {
    Map<String, String> result = new HashMap<>();
    if (isEmpty(orgIds)) {
      return result;
    }

    Map<String, CachedOrgInfo> cached = orgCache.getAll(accountId, orgIds);
    Set<String> missing = new HashSet<>();
    for (String orgId : orgIds) {
      if (cached.containsKey(orgId)) {
        result.put(orgId, cached.get(orgId).getName());
      } else {
        missing.add(orgId);
      }
    }

    if (!missing.isEmpty()) {
      try {
        List<OrganizationResponse> responses =
            NGRestUtils.getResponse(organizationClient.listAllOrganizations(accountId, new ArrayList<>(missing), null))
                .getContent();
        Map<String, CachedOrgInfo> toCache = new HashMap<>();
        for (OrganizationResponse resp : responses) {
          OrganizationDTO org = resp.getOrganization();
          result.put(org.getIdentifier(), org.getName());
          toCache.put(
              org.getIdentifier(), CachedOrgInfo.builder().identifier(org.getIdentifier()).name(org.getName()).build());
        }
        if (!toCache.isEmpty()) {
          orgCache.putAll(accountId, toCache);
        }
      } catch (Exception ex) {
        log.warn("{} Failed to fetch org names for missing orgs={} account={}. Error={}", GET_ENTITIES_FLOW_LOG,
            missing, accountId, ex.getMessage(), ex);
      }
    }
    log.info("{} Org names resolved account={} requested={} cacheHits={} remoteMisses={} resolved={}",
        GET_ENTITIES_FLOW_LOG, accountId, orgIds.size(), cached.size(), missing.size(), result.size());
    return result;
  }

  public Map<String, String> getProjectNames(
      String accountId, Set<String> orgIds, Map<String, Set<String>> projectsByOrg) {
    Map<String, String> result = new HashMap<>();
    if (isEmpty(projectsByOrg)) {
      return result;
    }

    Set<String> cacheKeys = new HashSet<>();
    projectsByOrg.forEach((orgId, projects) -> {
      if (projects != null) {
        projects.forEach(projId -> cacheKeys.add(CatalogProjectCache.buildProjectKey(orgId, projId)));
      }
    });

    Map<String, CachedProjectInfo> cached = projectCache.getAll(accountId, cacheKeys);
    Map<String, Set<String>> missing = new HashMap<>();
    projectsByOrg.forEach((orgId, projects) -> {
      if (projects != null) {
        for (String projId : projects) {
          String key = CatalogProjectCache.buildProjectKey(orgId, projId);
          if (cached.containsKey(key)) {
            String name = cached.get(key).getName();
            if (!isEmpty(name)) {
              result.put(key, name);
            }
          } else {
            missing.computeIfAbsent(orgId, k -> new HashSet<>()).add(projId);
          }
        }
      }
    });

    if (!missing.isEmpty()) {
      List<String> allMissingProjectIds =
          missing.values().stream().flatMap(Set::stream).distinct().collect(Collectors.toList());
      Set<String> allMissingOrgIds = missing.keySet();
      try {
        int page = 0;
        final int pageSize = 100;
        while (true) {
          PageResponse<ProjectResponse> projects = NGRestUtils.getResponse(projectClient.listWithMultiOrg(
              accountId, allMissingOrgIds, false, allMissingProjectIds, null, null, page, pageSize, null, false));
          if (projects == null || isEmpty(projects.getContent())) {
            break;
          }
          Map<String, CachedProjectInfo> toCache = new HashMap<>();
          for (ProjectResponse resp : projects.getContent()) {
            ProjectDTO proj = resp.getProject();
            String key = CatalogProjectCache.buildProjectKey(proj.getOrgIdentifier(), proj.getIdentifier());
            result.put(key, proj.getName());
            toCache.put(key,
                CachedProjectInfo.builder()
                    .identifier(proj.getIdentifier())
                    .orgIdentifier(proj.getOrgIdentifier())
                    .name(proj.getName())
                    .build());
          }
          if (!toCache.isEmpty()) {
            projectCache.putAll(accountId, toCache);
          }
          if (projects.getContent().size() < pageSize) {
            break;
          }
          page++;
        }
      } catch (Exception ex) {
        log.warn("{} Failed to fetch project names for orgs={} account={} projects={}. Error={}", GET_ENTITIES_FLOW_LOG,
            allMissingOrgIds, accountId, allMissingProjectIds, ex.getMessage(), ex);
      }
    }
    log.info("{} Project names resolved account={} requestedKeys={} cacheHits={} missingOrgBuckets={} resolved={}",
        GET_ENTITIES_FLOW_LOG, accountId, cacheKeys.size(), cached.size(), missing.size(), result.size());
    return result;
  }
}
