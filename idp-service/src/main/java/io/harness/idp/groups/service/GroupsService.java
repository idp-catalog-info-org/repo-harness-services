/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.idp.groups.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.backstage.entities.BackstageCatalogEntity;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.groups.entities.GroupEntity;
import io.harness.spec.server.idp.v1.model.GroupRequest;
import io.harness.spec.server.idp.v1.model.GroupResponse;

import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;

@OwnedBy(HarnessTeam.IDP)
public interface GroupsService {
  List<GroupResponse> getAllGroupsForAccount(String accountId, String orgIdentifier, String projectIdentifier);

  void deleteGroup(String accountIdentifier, String orgIdentifier, String projectIdentifier, String groupIdentifier);

  GroupResponse getGroup(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String groupIdentifier);

  GroupResponse saveGroup(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, GroupRequest groupRequest);
  List<GroupResponse> updateGroup(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, List<GroupRequest> groupRequest);

  Page<BackstageCatalogEntity> getWorkflowsInfo(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, int page, int limit);

  Page<CatalogEntity> getCatalogEntitiesForWorkflowsInfo(
      String accountIdentifier, int page, int limit, String orgIdentifier, String projectIdentifier, String searchTerm);

  void modifyEntityIdentifierForIdpV2(String accountIdentifier, Set<String> conflictedEntityUids);

  void modifyScopeForEntityIdentifier(List<GroupEntity> groupEntities, String accountIdentifier,
      String existingEntityIdentifier, String modifiedEntityIdentifier);

  void addUniqueIdAndParentUniqueIdInfo(String accountIdentifier);
}
