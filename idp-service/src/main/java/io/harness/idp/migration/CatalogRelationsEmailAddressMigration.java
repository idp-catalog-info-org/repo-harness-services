/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import static lombok.AccessLevel.PRIVATE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.catalog.helpers.IDPToHarnessHelper;
import io.harness.idp.namespace.beans.entity.NamespaceEntity;
import io.harness.migration.beans.NGMigration;

import com.google.inject.Inject;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@AllArgsConstructor(access = PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class CatalogRelationsEmailAddressMigration implements NGMigration {
  MongoTemplate mongoTemplate;
  IDPToHarnessHelper idpToHarnessHelper;

  @Override
  public void migrate() {
    Criteria criteria = new Criteria()
                            .andOperator(Criteria.where(NamespaceEntity.NamespaceKeys.isDeleted).is(false),
                                Criteria
                                    .where(NamespaceEntity.NamespaceKeys.metadata + "."
                                        + NamespaceEntity.Metadata.NamespaceMetadataKeys
                                              .migrateCatalogEntitiesFromBackstageToHarnessCompleted)
                                    .exists(true),
                                Criteria
                                    .where(NamespaceEntity.NamespaceKeys.metadata + "."
                                        + NamespaceEntity.Metadata.NamespaceMetadataKeys
                                              .migrateCatalogEntitiesFromBackstageToHarnessCompleted)
                                    .is(true))
                            .orOperator(Criteria
                                            .where(NamespaceEntity.NamespaceKeys.metadata + "."
                                                + NamespaceEntity.Metadata.NamespaceMetadataKeys.userGroupSyncCompleted)
                                            .exists(true),
                                Criteria
                                    .where(NamespaceEntity.NamespaceKeys.metadata + "."
                                        + NamespaceEntity.Metadata.NamespaceMetadataKeys.userGroupSyncCompleted)
                                    .is(true));
    List<NamespaceEntity> namespaceEntities = mongoTemplate.find(new Query(criteria), NamespaceEntity.class);
    for (NamespaceEntity namespaceEntity : namespaceEntities) {
      try {
        idpToHarnessHelper.migrateRelationsEmailAddress(namespaceEntity.getAccountIdentifier());
      } catch (Exception e) {
        log.error("Error occurred while running the migration for Catalog Relations Email Address for account {}",
            namespaceEntity.getAccountIdentifier(), e);
      }
    }
  }
}
