/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.cache;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
public class RbacPermissions implements Serializable {
  private static final long serialVersionUID = 1L;

  @Builder.Default Map<String, List<String>> scopePermissions = new HashMap<>();

  @Builder.Default List<String> allowedEntityRefs = new ArrayList<>();

  public List<String> getAllowedScopeUniqueIds(String permissionType) {
    return scopePermissions.getOrDefault(permissionType, new ArrayList<>());
  }

  public List<String> getAllAllowedScopeUniqueIds() {
    return scopePermissions.values().stream().flatMap(Collection::stream).distinct().collect(Collectors.toList());
  }

  public List<String> filterEntityRefsByKindPrefix(String kindPrefix) {
    if (kindPrefix == null || kindPrefix.isEmpty()) {
      return new ArrayList<>(allowedEntityRefs);
    }
    String prefix = kindPrefix + ":";
    return allowedEntityRefs.stream().filter(ref -> ref.startsWith(prefix)).collect(Collectors.toList());
  }

  public List<String> filterEntityRefsByScope(List<String> scopeUniqueIds) {
    if (scopeUniqueIds == null || scopeUniqueIds.isEmpty()) {
      return new ArrayList<>();
    }
    return allowedEntityRefs.stream()
        .filter(ref -> {
          String scope = extractScopeFromEntityRef(ref);
          return scopeUniqueIds.stream().anyMatch(uid -> scope.contains(uid) || uid.contains(scope));
        })
        .collect(Collectors.toList());
  }

  private String extractScopeFromEntityRef(String entityRef) {
    int colonIdx = entityRef.indexOf(':');
    if (colonIdx < 0) {
      return entityRef;
    }
    String afterKind = entityRef.substring(colonIdx + 1);
    int slashIdx = afterKind.lastIndexOf('/');
    if (slashIdx < 0) {
      return afterKind;
    }
    return afterKind.substring(0, slashIdx);
  }
}
