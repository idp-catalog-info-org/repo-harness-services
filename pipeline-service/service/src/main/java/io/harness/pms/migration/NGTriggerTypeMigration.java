/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.migration;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.beans.NGMigration;
import io.harness.ngtriggers.Constants;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity.NGTriggerEntityKeys;
import io.harness.ngtriggers.beans.entity.TriggerEventHistory;
import io.harness.ngtriggers.beans.entity.TriggerEventHistory.TriggerEventHistoryKeys;
import io.harness.ngtriggers.beans.source.NGTriggerType;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(PIPELINE)
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
public class NGTriggerTypeMigration implements NGMigration {
  @Inject private final MongoTemplate mongoTemplate;

  private static final int MAX_BATCH_SIZE = 1000;
  @Override
  public void migrate() {
    long totalCount = mongoTemplate.count(new Query(), NGTriggerEntity.class);
    int pageSize = 20;
    int maxPages = ((int) Math.ceil((double) totalCount / pageSize)) + 1000;
    int pageIdx = 0;
    Set<NGTriggerType> triggerTypeSet = Set.of(NGTriggerType.WEBHOOK, NGTriggerType.ARTIFACT, NGTriggerType.MANIFEST,
        NGTriggerType.MULTI_REGION_ARTIFACT, NGTriggerType.SCHEDULED);

    while (pageIdx < maxPages) {
      Pageable pageable = PageRequest.of(pageIdx, pageSize);

      Query query = new Query().with(pageable);

      query.addCriteria(Criteria.where(NGTriggerEntityKeys.type).in(triggerTypeSet));

      query.fields()
          .include(NGTriggerEntityKeys.accountId)
          .include(NGTriggerEntityKeys.orgIdentifier)
          .include(NGTriggerEntityKeys.projectIdentifier)
          .include(NGTriggerEntityKeys.targetIdentifier)
          .include(NGTriggerEntityKeys.identifier)
          .include(NGTriggerEntityKeys.type)
          .include(NGTriggerEntityKeys.name)
          .include(NGTriggerEntityKeys.metadata);

      List<NGTriggerEntity> triggerEntities = mongoTemplate.find(query, NGTriggerEntity.class);
      if (triggerEntities.isEmpty()) {
        break;
      }

      for (NGTriggerEntity ngTriggerEntity : triggerEntities) {
        try {
          // This migration has already run in SaaS, it should be better to clean up.
          // This has also run in SMP due to mandatory releases.
          // This job can be cleaned up when triggerEventHistory's projectIdentifer and orgIdentifier are removed.
          Query query1 = new Query(Criteria.where(TriggerEventHistoryKeys.accountId)
                                       .is(ngTriggerEntity.getAccountId())
                                       .and(TriggerEventHistoryKeys.orgIdentifier)
                                       .is(ngTriggerEntity.getOrgIdentifier())
                                       .and(TriggerEventHistoryKeys.projectIdentifier)
                                       .is(ngTriggerEntity.getProjectIdentifier())
                                       .and(TriggerEventHistoryKeys.triggerIdentifier)
                                       .is(ngTriggerEntity.getIdentifier())
                                       .and(TriggerEventHistoryKeys.targetIdentifier)
                                       .is(ngTriggerEntity.getTargetIdentifier())
                                       .and(TriggerEventHistoryKeys.triggerSubType)
                                       .is(null));

          query1.cursorBatchSize(MAX_BATCH_SIZE);
          query1.fields().include(TriggerEventHistoryKeys.uuid);
          query1.fields().include(TriggerEventHistoryKeys.exceptionOccurred);
          Stream<TriggerEventHistory> eventHistoryListStream = mongoTemplate.stream(query1, TriggerEventHistory.class);
          Iterator<TriggerEventHistory> eventHistoryIterator = eventHistoryListStream.iterator();
          List<String> eventHistoryUuid = new ArrayList<>();
          while (eventHistoryIterator.hasNext()) {
            TriggerEventHistory eventHistory = eventHistoryIterator.next();
            eventHistoryUuid.add(eventHistory.getUuid());
          }
          String triggerType = triggerTypeMapper(ngTriggerEntity);
          for (int i = 0; i < eventHistoryUuid.size(); i += MAX_BATCH_SIZE) {
            List<String> batch = eventHistoryUuid.subList(i, Math.min(i + MAX_BATCH_SIZE, eventHistoryUuid.size()));
            Query updateQuery = new Query(Criteria.where(TriggerEventHistoryKeys.uuid).in(batch));
            Update update = new Update();
            update.set(TriggerEventHistoryKeys.ngTriggerType, ngTriggerEntity.getType());
            update.set(TriggerEventHistoryKeys.triggerName, ngTriggerEntity.getName());
            update.set(TriggerEventHistoryKeys.triggerSubType, triggerType);
            mongoTemplate.updateMulti(updateQuery, update, TriggerEventHistory.class);
          }
          log.info("TriggerEventHistories updated for ngTriggerEntity: {}", ngTriggerEntity.getIdentifier());

        } catch (Exception ex) {
          log.error("Failed to process NGTriggerEntity for triggerId {} in projectId {}, accountId {}, and orgId {}",
              ngTriggerEntity.getIdentifier(), ngTriggerEntity.getProjectIdentifier(), ngTriggerEntity.getAccountId(),
              ngTriggerEntity.getOrgIdentifier(), ex);
        }
      }

      pageIdx++;
      if (pageIdx % (maxPages / 5) == 0) {
        log.info("NGTriggerType Migration in progress...");
      }
    }
    log.info("TriggerEventHistory is successfully migrated.");
  }

  private String triggerTypeMapper(NGTriggerEntity ngTriggerEntity) {
    if (ngTriggerEntity == null) {
      return null;
    }

    if (ngTriggerEntity.getType() == null) {
      return null;
    }

    if (ngTriggerEntity.getMetadata() == null) {
      return null;
    }

    if (NGTriggerType.ARTIFACT == ngTriggerEntity.getType() || NGTriggerType.MANIFEST == ngTriggerEntity.getType()) {
      return triggerTypeConverter(ngTriggerEntity.getMetadata().getBuildMetadata().getBuildSourceType());
    }

    if (NGTriggerType.WEBHOOK == ngTriggerEntity.getType()) {
      return ngTriggerEntity.getMetadata().getWebhook() == null ? null
                                                                : ngTriggerEntity.getMetadata().getWebhook().getType();
    }

    if (NGTriggerType.MULTI_REGION_ARTIFACT == ngTriggerEntity.getType()) {
      return ngTriggerEntity.getMetadata().getMultiBuildMetadata() == null
          ? null
          : ngTriggerEntity.getMetadata().getMultiBuildMetadata().get(0).getBuildSourceType();
    }

    if (NGTriggerType.SCHEDULED == ngTriggerEntity.getType()) {
      return ngTriggerEntity.getMetadata().getCron() == null ? null : Constants.CRON;
    }
    return null;
  }

  private String triggerTypeConverter(String absoluteReference) {
    if ("io.harness.ngtriggers.beans.source.artifact.GcrSpec".equals(absoluteReference)) {
      return Constants.GCR;
    } else if ("io.harness.ngtriggers.beans.source.artifact.EcrSpec".equals(absoluteReference)) {
      return Constants.ECR;
    } else if ("io.harness.ngtriggers.beans.source.artifact.DockerRegistrySpec".equals(absoluteReference)) {
      return Constants.DOCKER_REGISTRY;
    } else if ("io.harness.ngtriggers.beans.source.artifact.ArtifactoryRegistrySpec".equals(absoluteReference)) {
      return Constants.ARTIFACTORY_REGISTRY;
    } else if ("io.harness.ngtriggers.beans.source.artifact.AcrSpec".equals(absoluteReference)) {
      return Constants.ACR;
    } else if ("io.harness.ngtriggers.beans.source.artifact.AmazonS3RegistrySpec".equals(absoluteReference)) {
      return Constants.AMAZON_S3;
    } else if ("io.harness.ngtriggers.beans.source.artifact.GarSpec".equals(absoluteReference)) {
      return Constants.GOOGLE_ARTIFACT_REGISTRY;
    } else if ("io.harness.ngtriggers.beans.source.artifact.CustomArtifactSpec".equals(absoluteReference)) {
      return Constants.CUSTOM_ARTIFACT;
    } else if ("io.harness.ngtriggers.beans.source.artifact.GithubPackagesSpec".equals(absoluteReference)) {
      return Constants.GITHUB_PACKAGES;
    } else if ("io.harness.ngtriggers.beans.source.artifact.JenkinsRegistrySpec".equals(absoluteReference)) {
      return Constants.JENKINS;
    } else if ("io.harness.ngtriggers.beans.source.artifact.NexusRegistrySpec".equals(absoluteReference)) {
      return Constants.NEXUS3_REGISTRY;
    } else if ("io.harness.ngtriggers.beans.source.artifact.Nexus2RegistrySpec".equals(absoluteReference)) {
      return Constants.NEXUS2_REGISTRY;
    } else if ("io.harness.ngtriggers.beans.source.artifact.AzureArtifactsRegistrySpec".equals(absoluteReference)) {
      return Constants.AZURE_ARTIFACTS;
    } else if ("io.harness.ngtriggers.beans.source.artifact.AMIRegistrySpec".equals(absoluteReference)) {
      return Constants.AMI;
    } else if ("io.harness.ngtriggers.beans.source.artifact.BambooRegistrySpec".equals(absoluteReference)) {
      return Constants.BAMBOO;
    } else if ("io.harness.ngtriggers.beans.source.artifact.HelmManifestSpec".equals(absoluteReference)) {
      return Constants.HELM_CHART;
    }
    return "";
  }
}