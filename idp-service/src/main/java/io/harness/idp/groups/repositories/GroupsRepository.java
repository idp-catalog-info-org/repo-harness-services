/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.idp.groups.repositories;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.groups.entities.GroupEntity;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

@HarnessRepo
@OwnedBy(HarnessTeam.IDP)
public interface GroupsRepository extends CrudRepository<GroupEntity, String>, GroupsRepositoryCustom {
  List<GroupEntity> findAllByAccountIdentifier(String accountIdentifier);
  void delete(GroupEntity group);
  Optional<GroupEntity> findByParentUniqueIdAndIdentifier(String parentUniqueId, String identifier);
  GroupEntity save(GroupEntity groupsEntity);
  List<GroupEntity> findAllByParentUniqueId(String parentUniqueId);
  List<GroupEntity> findAllByAccountIdentifierAndOrgIdentifierAndProjectIdentifier(
      String accountIdentifier, String orgIdentifier, String projectIdentifier);
}
