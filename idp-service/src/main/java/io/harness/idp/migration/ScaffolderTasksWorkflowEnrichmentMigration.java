/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.backstage.entities.BackstageScaffolderTaskEntity;
import io.harness.idp.backstage.entities.BackstageScaffolderTaskEntity.BackstageScaffolderTasksKeys;
import io.harness.idp.backstage.repositories.BackstageScaffolderTaskEntityRepository;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.migration.beans.NGMigration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.query.Criteria;

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class ScaffolderTasksWorkflowEnrichmentMigration implements NGMigration {
  @Inject private NamespaceService namespaceService;
  @Inject private BackstageScaffolderTaskEntityRepository scaffolderTaskEntityRepository;
  private static final ObjectMapper objectMapper = NG_DEFAULT_OBJECT_MAPPER;

  private static final int PAGE_SIZE = 5000;

  @Override
  public void migrate() {
    log.info("Starting ScaffolderTasksWorkflowEnrichmentMigration - enriching existing records with workflow data");

    List<String> accountIdentifiers = namespaceService.getAccountIds();
    int totalProcessed = 0;
    int totalEnriched = 0;

    for (String accountIdentifier : accountIdentifiers) {
      log.info("Processing account: {}", accountIdentifier);
      try {
        int[] result = enrichAccountTasks(accountIdentifier);
        totalProcessed += result[0];
        totalEnriched += result[1];
      } catch (Exception e) {
        log.error("Error processing account {}: {}", accountIdentifier, e.getMessage(), e);
      }
    }

    log.info("ScaffolderTasksWorkflowEnrichmentMigration complete. Total processed: {}, enriched: {}", totalProcessed,
        totalEnriched);
  }

  private int[] enrichAccountTasks(String accountIdentifier) {
    int processed = 0;
    int enriched = 0;
    int pageNumber = 0;

    Criteria criteria = Criteria.where(BackstageScaffolderTasksKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(BackstageScaffolderTasksKeys.entityRef)
                            .is(null);

    Page<BackstageScaffolderTaskEntity> page;
    do {
      page = scaffolderTaskEntityRepository.findAll(criteria, PageRequest.of(pageNumber, PAGE_SIZE));
      List<BackstageScaffolderTaskEntity> tasks = page.getContent();

      if (tasks.isEmpty()) {
        break;
      }

      List<BackstageScaffolderTaskEntity> enrichedTasks = new ArrayList<>();
      for (BackstageScaffolderTaskEntity task : tasks) {
        try {
          JsonNode spec = objectMapper.readTree(task.getSpec());
          JsonNode templateInfo = spec.get("templateInfo");
          String entityRef = templateInfo.get("entityRef").asText();
          task.setEntityRef(entityRef);

          if (templateInfo.get("entity") != null && templateInfo.get("entity").get("metadata") != null) {
            JsonNode metadata = templateInfo.get("entity").get("metadata");
            if (metadata.get("title") != null) {
              task.setName(metadata.get("title").asText());
            } else if (metadata.get("name") != null) {
              task.setName(metadata.get("name").asText());
            } else {
              task.setName(entityRef.split("/")[1]);
            }
          } else {
            task.setName(entityRef.split("/")[1]);
          }
          enrichedTasks.add(task);
        } catch (Exception e) {
          log.warn(
              "Error enriching task {} for account {}: {}", task.getIdentifier(), accountIdentifier, e.getMessage());
        }
      }

      if (!enrichedTasks.isEmpty()) {
        scaffolderTaskEntityRepository.saveAll(enrichedTasks);
        enriched += enrichedTasks.size();
      }

      processed += tasks.size();
      pageNumber++;
      log.info("Account {}: processed {} tasks, enriched {} so far", accountIdentifier, processed, enriched);
    } while (page.hasNext());

    return new int[] {processed, enriched};
  }
}
