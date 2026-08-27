/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.remote.client.NGRestUtils.executeGeneralRequestWithRetry;
import static io.harness.remote.client.NGRestUtils.getGeneralResponse;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.authorization.AuthorizationServiceHeader;
import io.harness.clients.integrationmanager.EntityMappedEntityResponse;
import io.harness.clients.integrationmanager.EntityMappedEntityResponseObject;
import io.harness.clients.integrationmanager.EntitySubscribeEntitiesResponse;
import io.harness.clients.integrationmanager.IntegrationManagerClientHelper;
import io.harness.clients.integrationmanager.OpenapiGetMappedEntitiesRequest;
import io.harness.clients.integrationmanager.OpenapiSubscribeEntitiesRequest;
import io.harness.exception.UnexpectedException;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.integrations.helpers.CatalogIntegrationServiceHelper;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.migration.beans.NGMigration;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.ServicePrincipal;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import retrofit2.Response;

/**
 * Repairs entities that IDP has linked to an integration but integration-manager does not hold as subscribed.
 * The drift came from subscribe calls that failed inside integration-manager after the catalog entity had already
 * been linked, which left those entities receiving no further updates from integration-manager.
 *
 * For every integration-linked catalog entity, this asks integration-manager which of the linked UUIDs are still
 * unsubscribed and re-subscribes only those, with skip_event so the repair does not surface a fresh import event
 * to users. Subscribe is idempotent, so a re-run is harmless.
 */
@Slf4j
@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
public class IntegrationEntitySubscriptionBackfillMigration implements NGMigration {
  private static final String INTEGRATION_LINKAGE_PATH = "decorator._processed_data.metadata.integration";
  private static final String INTEGRATION_METADATA_KEY = "integration";
  private static final String METADATA_KEY = "metadata";

  // UUIDs per integration-manager call, and the bound on UUIDs buffered per integration in memory.
  private static final int UUID_BATCH_SIZE = 100;
  private static final int MAPPED_ENTITIES_LIMIT = 1000;

  @Inject NamespaceService namespaceService;
  @Inject MongoTemplate mongoTemplate;
  @Inject CatalogIntegrationServiceHelper catalogIntegrationServiceHelper;
  @Inject IntegrationManagerClientHelper integrationManagerClientHelper;

  @Override
  public void migrate() {
    log.info("Starting IntegrationEntitySubscriptionBackfillMigration.");
    SecurityContextBuilder.setContext(new ServicePrincipal(AuthorizationServiceHeader.IDP_SERVICE.getServiceId()));
    SourcePrincipalContextBuilder.setSourcePrincipal(
        new ServicePrincipal(AuthorizationServiceHeader.IDP_SERVICE.getServiceId()));
    try {
      for (String accountIdentifier : namespaceService.getAccountIds()) {
        try {
          backfillForAccount(accountIdentifier);
        } catch (Exception e) {
          log.error("IntegrationEntitySubscriptionBackfillMigration failed for account {}", accountIdentifier, e);
        }
      }
    } finally {
      SecurityContextBuilder.unsetCompleteContext();
    }
    log.info("Completed IntegrationEntitySubscriptionBackfillMigration.");
  }

  private void backfillForAccount(String accountIdentifier) {
    Query query = new Query(Criteria.where(CatalogEntity.CatalogKeys.accountIdentifier)
                                .is(accountIdentifier)
                                .and(INTEGRATION_LINKAGE_PATH)
                                .exists(true));
    BackfillCounters counters = new BackfillCounters();
    // (space path, integration) -> uuid -> kind. Flushed as soon as an integration reaches UUID_BATCH_SIZE so a
    // large account never buffers all of its linked UUIDs at once.
    Map<LinkageGroup, Map<String, String>> pendingByIntegration = new HashMap<>();

    try (Stream<CatalogEntity> stream = mongoTemplate.stream(query, CatalogEntity.class)) {
      Iterator<CatalogEntity> iterator = stream.iterator();
      while (iterator.hasNext()) {
        collectLinkedEntities(iterator.next(), pendingByIntegration, counters);
        flushFullGroups(accountIdentifier, pendingByIntegration, counters);
      }
    }
    flushAllGroups(accountIdentifier, pendingByIntegration, counters);

    log.info("Subscription backfill summary for account {}: linked={}, unsubscribed={}, repaired={}, failed={}.",
        accountIdentifier, counters.linked, counters.unsubscribed, counters.repaired, counters.failed);
  }

  @SuppressWarnings("unchecked")
  private void collectLinkedEntities(CatalogEntity catalogEntity,
      Map<LinkageGroup, Map<String, String>> pendingByIntegration, BackfillCounters counters) {
    Map<String, Object> processedData = catalogEntity.getFailSafeProcessedData();
    if (isEmpty(processedData)) {
      return;
    }
    Object metadataObj = processedData.get(METADATA_KEY);
    if (!(metadataObj instanceof Map)) {
      return;
    }
    Object integrationObj = ((Map<String, Object>) metadataObj).get(INTEGRATION_METADATA_KEY);
    if (!(integrationObj instanceof Map)) {
      return;
    }

    Map<String, Object> integrationBySpacePath = (Map<String, Object>) integrationObj;
    for (Map.Entry<String, Object> spacePathEntry : integrationBySpacePath.entrySet()) {
      if (!(spacePathEntry.getValue() instanceof Map)) {
        continue;
      }
      Map<String, Object> integrationsById = (Map<String, Object>) spacePathEntry.getValue();
      for (Map.Entry<String, Object> integrationEntry : integrationsById.entrySet()) {
        if (!(integrationEntry.getValue() instanceof Map)) {
          continue;
        }
        Map<String, String> uuidToKind =
            catalogIntegrationServiceHelper.collectEntityUuidToKind((Map<String, Object>) integrationEntry.getValue());
        if (uuidToKind.isEmpty()) {
          continue;
        }
        counters.linked += uuidToKind.size();
        LinkageGroup group = new LinkageGroup(spacePathEntry.getKey(), integrationEntry.getKey());
        pendingByIntegration.computeIfAbsent(group, key -> new HashMap<>()).putAll(uuidToKind);
      }
    }
  }

  private void flushFullGroups(String accountIdentifier, Map<LinkageGroup, Map<String, String>> pendingByIntegration,
      BackfillCounters counters) {
    Iterator<Map.Entry<LinkageGroup, Map<String, String>>> iterator = pendingByIntegration.entrySet().iterator();
    while (iterator.hasNext()) {
      Map.Entry<LinkageGroup, Map<String, String>> entry = iterator.next();
      if (entry.getValue().size() >= UUID_BATCH_SIZE) {
        repairGroup(accountIdentifier, entry.getKey(), entry.getValue(), counters);
        iterator.remove();
      }
    }
  }

  private void flushAllGroups(String accountIdentifier, Map<LinkageGroup, Map<String, String>> pendingByIntegration,
      BackfillCounters counters) {
    pendingByIntegration.forEach((group, uuidToKind) -> repairGroup(accountIdentifier, group, uuidToKind, counters));
    pendingByIntegration.clear();
  }

  private void repairGroup(
      String accountIdentifier, LinkageGroup group, Map<String, String> uuidToKind, BackfillCounters counters) {
    String[] orgAndProject = catalogIntegrationServiceHelper.parseSpacePath(group.getSpacePath());
    String orgIdentifier = orgAndProject[0];
    String projectIdentifier = orgAndProject[1];

    List<String> uuids = new ArrayList<>(uuidToKind.keySet());
    for (int from = 0; from < uuids.size(); from += UUID_BATCH_SIZE) {
      List<String> batch = uuids.subList(from, Math.min(from + UUID_BATCH_SIZE, uuids.size()));
      try {
        List<EntityMappedEntityResponse> unsubscribed = fetchUnsubscribedEntities(
            accountIdentifier, orgIdentifier, projectIdentifier, group.getIntegrationId(), batch, uuidToKind);
        if (isEmpty(unsubscribed)) {
          continue;
        }
        counters.unsubscribed += unsubscribed.size();
        resubscribeWithoutEvent(
            accountIdentifier, orgIdentifier, projectIdentifier, group.getIntegrationId(), unsubscribed, counters);
      } catch (Exception e) {
        counters.failed += batch.size();
        log.error("Subscription backfill failed for account {}, spacePath {}, integration {} (batchSize={})",
            accountIdentifier, group.getSpacePath(), group.getIntegrationId(), batch.size(), e);
      }
    }
  }

  /**
   * Asks integration-manager which of the linked UUIDs it still holds as unsubscribed, so that the repair only
   * touches entities that actually diverged.
   */
  private List<EntityMappedEntityResponse> fetchUnsubscribedEntities(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String integrationId, List<String> uuids, Map<String, String> uuidToKind) {
    OpenapiGetMappedEntitiesRequest request = new OpenapiGetMappedEntitiesRequest();
    request.setIdentifiers(uuids.stream()
                               .map(OpenapiGetMappedEntitiesRequest.OpenapiEntityFilterRequest::new)
                               .collect(Collectors.toList()));
    request.setKinds(
        uuids.stream().map(uuidToKind::get).filter(Objects::nonNull).distinct().collect(Collectors.toList()));

    Response<EntityMappedEntityResponseObject> response = executeGeneralRequestWithRetry(
        integrationManagerClientHelper.getMappedEntities(accountIdentifier, accountIdentifier, orgIdentifier,
            projectIdentifier, integrationId, integrationManagerClientHelper.getIntegrationManagerIdpMappingId(), false,
            "name", "asc", 0, MAPPED_ENTITIES_LIMIT, null, request, true));
    if (!response.isSuccessful()) {
      throw new UnexpectedException(String.format(
          "Failed fetching unsubscribed entities from integration-manager, httpCode=%d", response.code()));
    }
    if (response.body() == null || isEmpty(response.body().getItems())) {
      return Collections.emptyList();
    }
    return response.body().getItems();
  }

  private void resubscribeWithoutEvent(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String integrationId, List<EntityMappedEntityResponse> entities, BackfillCounters counters) {
    OpenapiSubscribeEntitiesRequest request = new OpenapiSubscribeEntitiesRequest();
    request.setSkipEvent(true);
    request.setEntities(entities.stream()
                            .map(entity
                                -> OpenapiSubscribeEntitiesRequest.EntityEntityReference.builder()
                                       .mappingId(integrationManagerClientHelper.getIntegrationManagerIdpMappingId())
                                       .kind(entity.getKind())
                                       .uuid(entity.getUuid())
                                       .build())
                            .collect(Collectors.toList()));

    EntitySubscribeEntitiesResponse response =
        getGeneralResponse(integrationManagerClientHelper.subscribeToEntityUpdates(
            accountIdentifier, accountIdentifier, orgIdentifier, projectIdentifier, integrationId, request));

    int repaired = response != null && response.getSummary() != null && response.getSummary().getTotalSuccess() != null
        ? response.getSummary().getTotalSuccess()
        : 0;
    counters.repaired += repaired;
    counters.failed += entities.size() - repaired;

    if (response != null && isNotEmpty(response.getFailed())) {
      response.getFailed().forEach(failure
          -> log.warn("Subscription backfill could not repair entity uuid={}, kind={} for account {}, integration {}."
                  + " Reason = {}",
              failure.getUuid(), failure.getKind(), accountIdentifier, integrationId, failure.getReason()));
    }
  }

  @Value
  private static class LinkageGroup {
    String spacePath;
    String integrationId;
  }

  private static final class BackfillCounters {
    int linked;
    int unsubscribed;
    int repaired;
    int failed;
  }
}
