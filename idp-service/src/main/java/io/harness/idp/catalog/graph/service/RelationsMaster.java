/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.graph.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

@Singleton
@OwnedBy(HarnessTeam.IDP)
public class RelationsMaster {
  private final Map<String, String> relationToReverse;

  public RelationsMaster() {
    relationToReverse = new HashMap<>();
    registerPair("ownedBy", "ownerOf");
    registerPair("consumesApi", "apiConsumedBy");
    registerPair("providesApi", "apiProvidedBy");
    registerPair("dependsOn", "dependencyOf");
    registerPair("parentOf", "childOf");
    registerPair("memberOf", "hasMember");
    registerPair("partOf", "hasPart");
  }

  private void registerPair(String relation, String reverse) {
    relationToReverse.put(relation.toLowerCase(), reverse);
    relationToReverse.put(reverse.toLowerCase(), relation);
  }

  public String getReverseRelation(String relation) {
    if (relation == null) {
      return null;
    }
    return relationToReverse.get(relation.toLowerCase());
  }
}
