/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.spring;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.source.NGTriggerType;
import io.harness.repositories.custom.NGTriggerRepositoryCustom;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;

@HarnessRepo
@OwnedBy(PIPELINE)
public interface NGTriggerRepository extends PagingAndSortingRepository<NGTriggerEntity, String>,
                                             CrudRepository<NGTriggerEntity, String>, NGTriggerRepositoryCustom {
  Optional<NGTriggerEntity> findByAccountIdAndOrgIdentifierAndProjectIdentifierAndTargetIdentifierAndIdentifier(
      String accountId, String orgIdentifier, String projectIdentifier, String targetIdentifier, String identifier);

  Optional<NGTriggerEntity> findByParentUniqueIdAndTargetIdentifierAndIdentifier(
      String parentUniqueId, String targetIdentifier, String identifier);

  Optional<List<NGTriggerEntity>> findByAccountIdAndOrgIdentifierAndProjectIdentifierAndEnabled(
      String accountId, String orgIdentifier, String projectIdentifier, boolean enabled);

  Optional<List<NGTriggerEntity>> findByAccountIdAndOrgIdentifierAndProjectIdentifierAndTypeAndEnabled(
      String accountId, String orgIdentifier, String projectIdentifier, NGTriggerType type, boolean enabled);

  Optional<List<NGTriggerEntity>> findByAccountIdAndOrgIdentifierAndEnabled(
      String accountId, String orgIdentifier, boolean enabled);

  Optional<List<NGTriggerEntity>> findByAccountIdAndEnabled(String accountId, boolean enabled);

  Optional<List<NGTriggerEntity>> findByAccountIdAndOrgIdentifierAndProjectIdentifierAndTargetIdentifier(
      String accountId, String orgIdentifier, String projectIdentifier, String targetIdentifier);

  Optional<List<NGTriggerEntity>> findByParentUniqueIdAndTargetIdentifier(
      String parentUniqueId, String targetIdentifier);

  Optional<NGTriggerEntity> findByCustomWebhookToken(String customWebhookToken);
}
