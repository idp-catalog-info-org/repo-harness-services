/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.Constants.DOT_SEPARATOR;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.backstage.entities.BackstageScaffolderTaskEntity;
import io.harness.idp.backstage.entities.BackstageScaffolderTaskEntity.BackstageScaffolderTasksKeys;
import io.harness.idp.backstage.repositories.BackstageScaffolderTaskEntityRepository;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.migration.beans.NGMigration;
import io.harness.timescaledb.Tables;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.query.Criteria;

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class PostgresBackstageScaffolderTasksDataMigration implements NGMigration {
  @Inject private DSLContext dsl;
  @Inject private NamespaceService namespaceService;
  @Inject private BackstageScaffolderTaskEntityRepository scaffolderTaskEntityRepository;
  @Inject private ObjectMapper objectMapper;
  private static final Integer PAGE_SIZE = 5000;

  @Override
  public void migrate() {
    log.info("Starting the migration for updating tags column in backstage_scaffolder_tasks table.");
    if (columnExists("backstage_scaffolder_tasks", "tags")) {
      List<String> accountIdentifiers = namespaceService.getAccountIds();
      int totalTasksProcessed = 0;

      for (String accountIdentifier : accountIdentifiers) {
        log.info("Processing tasks for account: {}", accountIdentifier);
        Criteria criteria = Criteria.where(BackstageScaffolderTasksKeys.accountIdentifier).is(accountIdentifier);
        int pageNumber = 0;
        Page<BackstageScaffolderTaskEntity> page;

        do {
          page = scaffolderTaskEntityRepository.findAll(criteria, PageRequest.of(pageNumber, PAGE_SIZE));
          List<BackstageScaffolderTaskEntity> tasks = page.getContent();

          if (!isEmpty(tasks)) {
            log.info(
                "Processing page {} for account {}, tasks in page: {}", pageNumber, accountIdentifier, tasks.size());
            updateBulkScaffolderTasksTags(tasks);
            totalTasksProcessed += tasks.size();
          }

          pageNumber++;
        } while (page.hasNext());

        log.info("Completed processing for account: {}", accountIdentifier);
      }

      log.info(
          "Migration complete for updating tags column in backstage_scaffolder_tasks table. Total tasks processed: {}",
          totalTasksProcessed);
    } else {
      log.info("PostgresBackstageScaffolderTasksDataMigration didn't run as TAGS column doesn't exist in "
          + "BACKSTAGE_SCAFFOLDER_TASKS.");
    }
  }

  private boolean columnExists(String tableName, String columnName) {
    try {
      Integer count = dsl.selectCount()
                          .from("information_schema.columns")
                          .where("table_name = ? AND column_name = ?", tableName, columnName)
                          .fetchOne(0, Integer.class);
      return count != null && count > 0;
    } catch (Exception e) {
      log.error("Error checking if column exists: table={}, column={}", tableName, columnName, e);
      return false;
    }
  }

  private void updateBulkScaffolderTasksTags(List<BackstageScaffolderTaskEntity> tasks) {
    if (isEmpty(tasks)) {
      log.warn("No scaffolder tasks found to migrate");
      return;
    }

    try {
      List<org.jooq.Query> batchQueries = new ArrayList<>();
      tasks.forEach(task -> {
        try {
          String[] tags = extractTagsFromSpec(task.getSpec());
          if (tags != null && tags.length > 0) {
            batchQueries.add(dsl.update(Tables.BACKSTAGE_SCAFFOLDER_TASKS)
                                 .set(Tables.BACKSTAGE_SCAFFOLDER_TASKS.TAGS, tags)
                                 .where(Tables.BACKSTAGE_SCAFFOLDER_TASKS.ID.eq(task.getId())));
          }
        } catch (Exception e) {
          log.error("Exception while preparing update for task id: {}", task.getId(), e);
        }
      });

      if (!batchQueries.isEmpty()) {
        int[] result = dsl.batch(batchQueries).execute();
        log.info("Successfully updated tags for batch size {} for ids: {}", result.length,
            tasks.stream().map(task -> task.getAccountIdentifier() + DOT_SEPARATOR + task.getIdentifier()).toList());
      }
    } catch (Exception e) {
      log.error("Exception while bulk updating scaffolder tasks tags", e);
    }
  }

  @SneakyThrows
  private String[] extractTagsFromSpec(String specJson) {
    if (isEmpty(specJson)) {
      return null;
    }

    try {
      JsonNode spec = objectMapper.readTree(specJson);
      if (spec.get("templateInfo") != null) {
        JsonNode templateInfo = spec.get("templateInfo");
        if (templateInfo.get("entity") != null && templateInfo.get("entity").get("metadata") != null) {
          JsonNode metadata = templateInfo.get("entity").get("metadata");
          if (metadata.get("tags") != null && metadata.get("tags").isArray()) {
            JsonNode nodeTags = metadata.get("tags");
            return StreamSupport.stream(nodeTags.spliterator(), false).map(JsonNode::asText).toArray(String[] ::new);
          }
        }
      }
    } catch (Exception e) {
      log.error("Exception while extracting tags from spec", e);
    }

    return null;
  }
}
