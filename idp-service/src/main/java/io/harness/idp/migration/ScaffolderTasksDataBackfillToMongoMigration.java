/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.idp.backstage.beans.BackstageScaffolderTaskListItem.toEntities;
import static io.harness.idp.common.JacksonUtils.convert;
import static io.harness.remote.client.NGRestUtils.getGeneralResponse;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.clients.BackstageResourceClient;
import io.harness.idp.backstage.beans.BackstageScaffolderTaskListItem;
import io.harness.idp.backstage.entities.BackstageScaffolderTaskEntity;
import io.harness.idp.backstage.repositories.BackstageScaffolderTaskEntityRepository;
import io.harness.idp.namespace.beans.entity.NamespaceEntity;
import io.harness.idp.namespace.service.NamespaceServiceImpl;
import io.harness.migration.beans.NGMigration;

import com.google.inject.Inject;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class ScaffolderTasksDataBackfillToMongoMigration implements NGMigration {
  @Inject private NamespaceServiceImpl namespaceService;
  @Inject private BackstageResourceClient backstageResourceClient;
  @Inject private BackstageScaffolderTaskEntityRepository scaffolderTaskEntityRepository;

  private static final int DEFAULT_PAGE_LIMIT = 100;

  @Override
  public void migrate() {
    log.info("Starting ScaffolderTasksDataBackfillToMongoMigration");
    try {
      syncScaffolderTasks();
    } catch (Exception ex) {
      log.error("Error in Scaffolder Tasks Backfill. Error = {}", ex.getMessage(), ex);
    }
    log.info("ScaffolderTasksDataBackfillToMongoMigration completed successfully");
  }

  private void syncScaffolderTasks() {
    List<NamespaceEntity> activeAccounts = namespaceService.getActiveAccounts();
    log.info("Fetched {} IDP active accounts for scaffolder tasks sync", activeAccounts.size());
    Map<String, Integer> missingTasksPerAccount = new HashMap<>();
    int totalMissingTasks = 0;

    for (NamespaceEntity namespaceEntity : activeAccounts) {
      String accountIdentifier = namespaceEntity.getAccountIdentifier();
      int missingCount = syncScaffolderTasksPaginated(accountIdentifier);
      if (missingCount > 0) {
        missingTasksPerAccount.put(accountIdentifier, missingCount);
        totalMissingTasks += missingCount;
      }
    }

    log.info("ScaffolderTasksDataBackfillToMongoMigration summary: totalMissingTasks={}, accountsWithMissingTasks={}, "
            + "missingTasksPerAccount={}",
        totalMissingTasks, missingTasksPerAccount.size(), missingTasksPerAccount);
  }

  @SuppressWarnings("unchecked")
  private int syncScaffolderTasksPaginated(String accountIdentifier) {
    OffsetDateTime currentTime = OffsetDateTime.now();
    long listFrom = currentTime.minusMonths(12).toInstant().toEpochMilli();
    long listTo = currentTime.toInstant().toEpochMilli();

    log.info("Back filling scaffolder tasks (paginated) for accountIdentifier = {} listFrom = {} listTo = {}",
        accountIdentifier, listFrom, listTo);

    try {
      // Fetch first page to get totalCount
      Object firstResponse = getGeneralResponse(backstageResourceClient.scaffolderListTasksPaginated(
          accountIdentifier, listFrom, listTo, 1, DEFAULT_PAGE_LIMIT));

      Map<String, Object> firstResponseMap = (Map<String, Object>) firstResponse;
      int totalCount = ((Number) firstResponseMap.get("totalCount")).intValue();

      if (totalCount == 0) {
        log.info("No scaffolder tasks to back fill for accountIdentifier = {}", accountIdentifier);
        return 0;
      }

      int totalPages = (int) Math.ceil((double) totalCount / DEFAULT_PAGE_LIMIT);
      log.info("Total {} scaffolder tasks to back fill in {} pages for accountIdentifier = {}", totalCount, totalPages,
          accountIdentifier);

      int totalTasksSynced = 0;

      for (int page = 1; page <= totalPages; page++) {
        log.info(
            "Fetching scaffolder tasks page {}/{} for accountIdentifier = {}", page, totalPages, accountIdentifier);

        Object response = (page == 1) ? firstResponse
                                      : getGeneralResponse(backstageResourceClient.scaffolderListTasksPaginated(
                                            accountIdentifier, listFrom, listTo, page, DEFAULT_PAGE_LIMIT));

        Map<String, Object> responseMap = (Map<String, Object>) response;
        List<Object> tasksData = (List<Object>) responseMap.get("data");

        if (tasksData == null || tasksData.isEmpty()) {
          log.info("No tasks found on page {} for accountIdentifier = {}", page, accountIdentifier);
          continue;
        }

        List<BackstageScaffolderTaskListItem> scaffolderTasks =
            convert(tasksData, BackstageScaffolderTaskListItem.class);

        if (isNotEmpty(scaffolderTasks)) {
          List<BackstageScaffolderTaskEntity> scaffolderTasksEntities =
              toEntities(accountIdentifier, scaffolderTasks, scaffolderTaskEntityRepository);

          List<BackstageScaffolderTaskEntity> newTasks =
              scaffolderTasksEntities.stream().filter(task -> task.getId() == null).toList();

          scaffolderTaskEntityRepository.saveAll(newTasks);

          totalTasksSynced += newTasks.size();

          log.info("Back filled {} new scaffolder tasks (page {}/{}, totalCount {}) for accountIdentifier = {}",
              totalTasksSynced, page, totalPages, totalCount, accountIdentifier);
        }
      }

      log.info("Successfully back filled {} missing scaffolder tasks (out of {} total in backstage) for "
              + "accountIdentifier = {}",
          totalTasksSynced, totalCount, accountIdentifier);

      return totalTasksSynced;

    } catch (Exception ex) {
      log.error("Error in back filling scaffolder tasks (paginated) for accountIdentifier = {} Error = {}",
          accountIdentifier, ex.getMessage(), ex);
      return 0;
    }
  }
}
