/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.cache;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
@OwnedBy(HarnessTeam.IDP)
public class ScopeTopology implements Serializable {
  private static final long serialVersionUID = 1L;
  String accountUniqueId;
  Map<String, OrgNode> orgs;

  @FieldDefaults(level = AccessLevel.PRIVATE)
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  @Data
  public static class OrgNode implements Serializable {
    private static final long serialVersionUID = 1L;
    String uniqueId;
    Map<String, String> projects;
  }

  public List<String> resolveParentUniqueIds(String scopeString) {
    List<String> result = new ArrayList<>();
    if (scopeString == null || scopeString.isEmpty()) {
      return result;
    }

    String[] tokens = scopeString.split(",");
    for (String token : tokens) {
      resolveToken(token.trim(), result);
    }
    return result;
  }

  private void resolveToken(String token, List<String> result) {
    if (token.equalsIgnoreCase("account")) {
      addIfAbsent(result, accountUniqueId);
    } else if (token.equalsIgnoreCase("account.*")) {
      addIfAbsent(result, accountUniqueId);
      addAllOrgAndProjectUniqueIds(result);
    } else if (token.equalsIgnoreCase("account.org")) {
      addAllOrgUniqueIds(result);
    } else if (token.equalsIgnoreCase("account.org.project")) {
      addAllProjectUniqueIds(result);
    } else if (token.startsWith("account.") && token.endsWith(".*")) {
      String orgIdentifier = extractOrgFromWildcard(token);
      if (orgIdentifier != null && orgs != null && orgs.containsKey(orgIdentifier)) {
        OrgNode orgNode = orgs.get(orgIdentifier);
        addIfAbsent(result, orgNode.getUniqueId());
        if (orgNode.getProjects() != null) {
          orgNode.getProjects().values().forEach(uid -> addIfAbsent(result, uid));
        }
      }
    } else {
      String[] parts = token.split("\\.");
      if (parts.length == 3 && orgs != null) {
        OrgNode orgNode = orgs.get(parts[1]);
        if (orgNode != null && orgNode.getProjects() != null && orgNode.getProjects().containsKey(parts[2])) {
          addIfAbsent(result, orgNode.getProjects().get(parts[2]));
        }
      } else if (parts.length == 2 && orgs != null) {
        OrgNode orgNode = orgs.get(parts[1]);
        if (orgNode != null) {
          addIfAbsent(result, orgNode.getUniqueId());
        }
      }
    }
  }

  private String extractOrgFromWildcard(String token) {
    String[] parts = token.split("\\.");
    if (parts.length == 3) {
      return parts[1];
    }
    return null;
  }

  private void addAllOrgAndProjectUniqueIds(List<String> result) {
    if (orgs == null) {
      return;
    }
    orgs.forEach((orgId, orgNode) -> {
      addIfAbsent(result, orgNode.getUniqueId());
      if (orgNode.getProjects() != null) {
        orgNode.getProjects().values().forEach(uid -> addIfAbsent(result, uid));
      }
    });
  }

  private void addAllOrgUniqueIds(List<String> result) {
    if (orgs == null) {
      return;
    }
    orgs.values().forEach(orgNode -> addIfAbsent(result, orgNode.getUniqueId()));
  }

  private void addAllProjectUniqueIds(List<String> result) {
    if (orgs == null) {
      return;
    }
    orgs.values().forEach(orgNode -> {
      if (orgNode.getProjects() != null) {
        orgNode.getProjects().values().forEach(uid -> addIfAbsent(result, uid));
      }
    });
  }

  public String resolveNamespaceToUniqueId(String namespace) {
    if (namespace == null || namespace.isEmpty()) {
      return null;
    }
    String[] parts = namespace.split("\\.");
    if (parts.length == 1) {
      return accountUniqueId;
    } else if (parts.length == 2 && orgs != null) {
      OrgNode orgNode = orgs.get(parts[1]);
      return orgNode != null ? orgNode.getUniqueId() : null;
    } else if (parts.length == 3 && orgs != null) {
      OrgNode orgNode = orgs.get(parts[1]);
      if (orgNode != null && orgNode.getProjects() != null) {
        return orgNode.getProjects().get(parts[2]);
      }
    }
    return null;
  }

  public List<String> getAllUniqueIds() {
    List<String> result = new ArrayList<>();
    addIfAbsent(result, accountUniqueId);
    addAllOrgAndProjectUniqueIds(result);
    return result;
  }

  public List<ScopeInfo> buildScopeInfos(List<String> uniqueIds) {
    List<ScopeInfo> scopeInfos = new ArrayList<>();
    Map<String, ScopeInfoData> uniqueIdToScopeData = buildUniqueIdToScopeDataMap();
    for (String uniqueId : uniqueIds) {
      ScopeInfoData data = uniqueIdToScopeData.get(uniqueId);
      if (data != null) {
        scopeInfos.add(ScopeInfo.builder()
                           .accountIdentifier(accountUniqueId)
                           .orgIdentifier(data.orgIdentifier)
                           .projectIdentifier(data.projectIdentifier)
                           .scopeType(data.scopeLevel)
                           .uniqueId(uniqueId)
                           .build());
      }
    }
    return scopeInfos;
  }

  private Map<String, ScopeInfoData> buildUniqueIdToScopeDataMap() {
    Map<String, ScopeInfoData> map = new HashMap<>();
    map.put(accountUniqueId, new ScopeInfoData(null, null, ScopeLevel.ACCOUNT));
    if (orgs != null) {
      orgs.forEach((orgId, orgNode) -> {
        map.put(orgNode.getUniqueId(), new ScopeInfoData(orgId, null, ScopeLevel.ORGANIZATION));
        if (orgNode.getProjects() != null) {
          orgNode.getProjects().forEach(
              (projId, projUniqueId) -> map.put(projUniqueId, new ScopeInfoData(orgId, projId, ScopeLevel.PROJECT)));
        }
      });
    }
    return map;
  }

  public String getOrgIdentifierForUniqueId(String uniqueId) {
    if (orgs == null) {
      return null;
    }
    for (Map.Entry<String, OrgNode> entry : orgs.entrySet()) {
      if (entry.getValue().getUniqueId().equals(uniqueId)) {
        return entry.getKey();
      }
      if (entry.getValue().getProjects() != null) {
        for (Map.Entry<String, String> projEntry : entry.getValue().getProjects().entrySet()) {
          if (projEntry.getValue().equals(uniqueId)) {
            return entry.getKey();
          }
        }
      }
    }
    return null;
  }

  public String getProjectIdentifierForUniqueId(String uniqueId) {
    if (orgs == null) {
      return null;
    }
    for (OrgNode orgNode : orgs.values()) {
      if (orgNode.getProjects() != null) {
        for (Map.Entry<String, String> projEntry : orgNode.getProjects().entrySet()) {
          if (projEntry.getValue().equals(uniqueId)) {
            return projEntry.getKey();
          }
        }
      }
    }
    return null;
  }

  private static void addIfAbsent(List<String> list, String value) {
    if (value != null && !list.contains(value)) {
      list.add(value);
    }
  }

  @Data
  @AllArgsConstructor
  private static class ScopeInfoData {
    String orgIdentifier;
    String projectIdentifier;
    ScopeLevel scopeLevel;
  }
}
