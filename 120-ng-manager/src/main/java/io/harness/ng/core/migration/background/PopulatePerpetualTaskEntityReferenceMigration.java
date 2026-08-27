/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.background;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.mongo.MongoConfig.NO_LIMIT;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.migration.beans.NGMigration;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.polling.bean.PollingDocument;
import io.harness.polling.service.intfc.PollingPerpetualTaskService;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

/**
 * Backfills the {@code perpetualTaskEntityReferences} index for existing polling documents so that already-running
 * artifact/manifest triggers benefit from entity-change driven refresh without being recreated.
 */
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class PopulatePerpetualTaskEntityReferenceMigration implements NGMigration {
  @Inject private MongoTemplate mongoTemplate;
  @Inject private ScopeInfoService scopeInfoService;
  @Inject private PollingPerpetualTaskService pollingPerpetualTaskService;

  private static final String DEBUG_LOG = "[PopulatePerpetualTaskEntityReferenceMigration]: ";
  private static final int BATCH_SIZE = 100;

  @Override
  public void migrate() {
    log.info(DEBUG_LOG + "Starting backfill of perpetual task entity references for polling documents");
    int iterationCounter = 0;
    int registeredCounter = 0;
    Query query = new Query(new Criteria()).limit(NO_LIMIT).cursorBatchSize(BATCH_SIZE);
    try (Stream<PollingDocument> stream = mongoTemplate.stream(query, PollingDocument.class)) {
      Iterator<PollingDocument> iterator = stream.iterator();
      List<PollingDocument> batch = new ArrayList<>(BATCH_SIZE);
      while (iterator.hasNext()) {
        batch.add(iterator.next());
        iterationCounter++;
        if (batch.size() >= BATCH_SIZE) {
          registeredCounter += processBatch(batch);
          batch.clear();
        }
      }
      if (!batch.isEmpty()) {
        registeredCounter += processBatch(batch);
      }
    } catch (Exception e) {
      log.error(format("%s Migration failed after iterating %s docs, registered %s.", DEBUG_LOG, iterationCounter,
                    registeredCounter),
          e);
      return;
    }
    log.info(format("%s Migration successful. Iterated %s docs, registered references for %s.", DEBUG_LOG,
        iterationCounter, registeredCounter));
  }

  private int processBatch(List<PollingDocument> batch) {
    List<PollingDocument> toProcess =
        batch.stream().filter(doc -> isNotEmpty(doc.getPerpetualTaskId())).collect(Collectors.toList());
    if (toProcess.isEmpty()) {
      return 0;
    }

    Map<String, Set<String>> parentUniqueIdsByAccount =
        toProcess.stream()
            .filter(doc -> isNotEmpty(doc.getParentUniqueId()))
            .collect(Collectors.groupingBy(PollingDocument::getAccountId,
                Collectors.mapping(PollingDocument::getParentUniqueId, Collectors.toSet())));

    Map<String, Map<String, Optional<ScopeInfo>>> scopeInfoByAccount = new HashMap<>();
    for (Map.Entry<String, Set<String>> entry : parentUniqueIdsByAccount.entrySet()) {
      scopeInfoByAccount.put(entry.getKey(), scopeInfoService.getScopeInfo(entry.getKey(), entry.getValue()));
    }

    int registered = 0;
    for (PollingDocument pollingDocument : toProcess) {
      try {
        ScopeInfo scopeInfo = resolveScopeInfo(pollingDocument, scopeInfoByAccount);
        if (scopeInfo == null) {
          log.warn(DEBUG_LOG + "Skipping polling doc {} because scope info could not be resolved for parentUniqueId {}",
              pollingDocument.getUuid(), pollingDocument.getParentUniqueId());
          continue;
        }
        pollingPerpetualTaskService.registerEntityReferences(pollingDocument, scopeInfo);
        registered++;
      } catch (Exception ex) {
        log.error(DEBUG_LOG + "Failed to backfill references for polling doc " + pollingDocument.getUuid(), ex);
      }
    }
    return registered;
  }

  private ScopeInfo resolveScopeInfo(
      PollingDocument pollingDocument, Map<String, Map<String, Optional<ScopeInfo>>> scopeInfoByAccount) {
    if (isEmpty(pollingDocument.getParentUniqueId())) {
      return null;
    }
    return scopeInfoByAccount.getOrDefault(pollingDocument.getAccountId(), Collections.emptyMap())
        .getOrDefault(pollingDocument.getParentUniqueId(), Optional.empty())
        .orElse(null);
  }
}
