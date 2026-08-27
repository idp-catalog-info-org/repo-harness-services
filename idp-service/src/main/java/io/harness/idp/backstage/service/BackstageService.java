/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.backstage.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.backstage.entities.BackstageCatalogEntity;
import io.harness.spec.server.idp.v1.model.BackstageHarnessSyncRequest;
import io.harness.spec.server.idp.v1.model.ScorecardFilter;
import io.harness.spec.server.idp.v1.model.User;

import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;

@OwnedBy(HarnessTeam.IDP)
public interface BackstageService {
  void sync();
  boolean sync(String accountIdentifier);
  boolean sync(String accountIdentifier, String entityUid, String action, String syncMode, User user);
  boolean syncByType(String accountIdentifier, BackstageHarnessSyncRequest.TypeEnum type, String identifier,
      String action, String syncMode, User user);
  boolean syncScaffolderTasks(String accountIdentifier, String taskId, String action, String syncMode, User user);
  void syncScaffolderTasks();
  List<BackstageCatalogEntity> findAllByAccountIdentifier(String accountIdentifier);
  List<BackstageCatalogEntity> queryEntities(
      ScorecardFilter filter, String accountIdentifier, List<String> skipEntityUids);
  List<BackstageCatalogEntity> findAllByAccountIdentifierAndEntityRefs(
      String accountIdentifier, List<String> entityUids);
  BackstageCatalogEntity findByAccountIdentifierAndEntityRef(String accountIdentifier, String entityUid);
  void modifyEntityIdentifier(String accountIdentifier);
  void modifyEntityIdentifierForIdpV2(String accountIdentifier, Set<String> conflictedEntityUids);
  void modifyEntityRefInScaffolderTaskForIdpV2(String accountIdentifier, Set<String> conflictedEntityRefs);
  String resolveExpressions(String entity, String accountIdentifier);
  List<BackstageCatalogEntity> findAllByAccountIdentifierAndKind(String accountIdentifier, String kind);
  Page<BackstageCatalogEntity> findAllByAccountIdentifierAndKind(
      String accountIdentifier, String kind, int page, int limit);
  void removeDuplicateEntries();

  void changeSystemAsList(String accountIdentifier);
}
