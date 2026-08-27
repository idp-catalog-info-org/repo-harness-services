/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.backstage.entities.BackstageScaffolderTaskEntity;
import io.harness.idp.backstage.entities.BackstageScaffolderTaskEntity.BackstageScaffolderTasksKeys;
import io.harness.idp.backstage.repositories.BackstageScaffolderTaskEntityRepository;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.migration.beans.NGMigration;
import io.harness.timescaledb.Tables;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.query.Criteria;

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class PostgresBackstageScaffolderTasksCreatedByDataMigration implements NGMigration {
  @Inject private DSLContext dsl;
  @Inject private NamespaceService namespaceService;
  @Inject private BackstageScaffolderTaskEntityRepository scaffolderTaskEntityRepository;
  private static final Integer PAGE_SIZE = 5000;

  @Override
  public void migrate() {
    log.info("Starting the migration for updating created_by column in backstage_scaffolder_tasks table.");

    if (!columnExists("backstage_scaffolder_tasks", "created_by")) {
      log.info("Skipping migration as CREATED_BY column doesn't exist in BACKSTAGE_SCAFFOLDER_TASKS table.");
      return;
    }

    List<String> accountIdentifiers = namespaceService.getAccountIds();
    for (String accountIdentifier : accountIdentifiers) {
      try {
        log.info("Processing tasks for account {}", accountIdentifier);
        Criteria criteria = Criteria.where(BackstageScaffolderTasksKeys.accountIdentifier).is(accountIdentifier);
        int pageNumber = 0;
        Page<BackstageScaffolderTaskEntity> page;
        do {
          page = scaffolderTaskEntityRepository.findAll(criteria, PageRequest.of(pageNumber, PAGE_SIZE));
          List<BackstageScaffolderTaskEntity> tasks = page.getContent();
          if (!isEmpty(tasks)) {
            updateBulkScaffolderTasksCreatedBy(tasks);
          }
          pageNumber++;
        } while (page.hasNext());
        log.info("Completed processing for account {}", accountIdentifier);
      } catch (Exception ex) {
        log.error("Error processing tasks for account {} Exception = {}", accountIdentifier, ex.getMessage(), ex);
      }
    }

    log.info("Migration complete for updating created_by column in backstage_scaffolder_tasks table.");
  }

  private boolean columnExists(String tableName, String columnName) {
    try {
      Integer count = dsl.selectCount()
                          .from("information_schema.columns")
                          .where("table_name = ? AND column_name = ?", tableName, columnName)
                          .fetchOne(0, Integer.class);
      return count != null && count > 0;
    } catch (Exception e) {
      log.error("Error checking if column exists table {}, column {}", tableName, columnName, e);
      return false;
    }
  }

  private void updateBulkScaffolderTasksCreatedBy(List<BackstageScaffolderTaskEntity> tasks) {
    List<org.jooq.Query> batchQueries = new ArrayList<>();
    tasks.forEach(task -> {
      String taskCreatedBy = task.getTaskCreatedBy();
      if (!isEmpty(taskCreatedBy)) {
        batchQueries.add(dsl.update(Tables.BACKSTAGE_SCAFFOLDER_TASKS)
                             .set(Tables.BACKSTAGE_SCAFFOLDER_TASKS.CREATED_BY, taskCreatedBy)
                             .where(Tables.BACKSTAGE_SCAFFOLDER_TASKS.ID.eq(task.getId())));
      }
    });
    if (!batchQueries.isEmpty()) {
      dsl.batch(batchQueries).execute();
    }
  }
}
