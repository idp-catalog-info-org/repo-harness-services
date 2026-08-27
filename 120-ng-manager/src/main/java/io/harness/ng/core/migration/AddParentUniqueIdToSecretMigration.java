/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.mongo.MongoConfig.NO_LIMIT;
import static io.harness.ng.core.entities.Organization.OrganizationKeys;

import static java.lang.Integer.MAX_VALUE;
import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.beans.NGMigration;
import io.harness.ng.core.entities.Organization;
import io.harness.ng.core.entities.Project;
import io.harness.ng.core.entities.Project.ProjectKeys;
import io.harness.ng.core.models.Secret;
import io.harness.ng.core.models.Secret.SecretKeys;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@Slf4j
@OwnedBy(PL)
public class AddParentUniqueIdToSecretMigration implements NGMigration {
  private static final int BATCH_SIZE = 500;
  private static final String SECRET_PARENT_ID_GENERATION_LOG_CONST = "[NGAddParentIdToProjectMigration]:";
  private static final String LOCAL_MAP_DELIMITER = "|";

  private final MongoTemplate mongoTemplate;
  private final Map<String, String> parentUniqueIdMap;

  @Inject
  public AddParentUniqueIdToSecretMigration(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
    parentUniqueIdMap = new HashMap<>();
  }

  @Override
  public void migrate() {
    log.info(format("%s Starting migration for Entity Type: [%s] ", SECRET_PARENT_ID_GENERATION_LOG_CONST,
        Secret.class.getSimpleName()));

    int migratedCounter = 0;
    int totalCounter = 0;
    int updateCounter = 0;
    int batchSizeCounter = 0;

    Query documentQuery = new Query(new Criteria());
    log.info(format("%s Entity Type: [%s], Total count: [%s]", SECRET_PARENT_ID_GENERATION_LOG_CONST,
        Secret.class.getSimpleName(), mongoTemplate.count(documentQuery, Secret.class)));

    BulkOperations bulkOperations = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Secret.class);

    // iterate over all Secret documents
    try (Stream<Secret> stream =
             mongoTemplate.stream(documentQuery.limit(NO_LIMIT).maxTimeMsec(MAX_VALUE), Secret.class)) {
      Iterator<Secret> iterator = stream.iterator();
      while (iterator.hasNext()) {
        totalCounter++;
        Secret nextSecret = iterator.next();
        if (null != nextSecret && isEmpty(nextSecret.getParentUniqueId())) {
          updateCounter++;
          final StringBuilder keyBuilder = new StringBuilder(nextSecret.getAccountIdentifier());
          if (isNotEmpty(nextSecret.getOrgIdentifier())) {
            keyBuilder.append(LOCAL_MAP_DELIMITER).append(nextSecret.getOrgIdentifier());
          }
          if (isNotEmpty(nextSecret.getProjectIdentifier())) {
            keyBuilder.append(LOCAL_MAP_DELIMITER).append(nextSecret.getProjectIdentifier());
          }
          String mapKey = keyBuilder.toString();

          String parentUniqueId = null;

          // check if key with uniqueId is present locally
          if (parentUniqueIdMap.containsKey(mapKey)) {
            parentUniqueId = parentUniqueIdMap.get(mapKey);
          } else {
            // if project id is present use project's unique id as secret's parent id
            if (isNotEmpty(nextSecret.getProjectIdentifier())) {
              Criteria projectCriteria = Criteria.where(ProjectKeys.accountIdentifier)
                                             .is(nextSecret.getAccountIdentifier())
                                             .and(ProjectKeys.orgIdentifier)
                                             .is(nextSecret.getOrgIdentifier())
                                             .and(ProjectKeys.identifier)
                                             .is(nextSecret.getProjectIdentifier());
              Project project = mongoTemplate.findOne(new Query(projectCriteria), Project.class);
              if (project != null && isNotEmpty(project.getUniqueId())) {
                parentUniqueId = project.getUniqueId();
                parentUniqueIdMap.put(mapKey, parentUniqueId);
              }
            }
            // if organization id is present use organization's unique id as secret's parent id
            else if (isNotEmpty(nextSecret.getOrgIdentifier())) {
              Criteria orgCriteria = Criteria.where(OrganizationKeys.accountIdentifier)
                                         .is(nextSecret.getAccountIdentifier())
                                         .and(OrganizationKeys.identifier)
                                         .is(nextSecret.getOrgIdentifier());
              Organization organization = mongoTemplate.findOne(new Query(orgCriteria), Organization.class);
              if (organization != null && isNotEmpty(organization.getUniqueId())) {
                parentUniqueId = organization.getUniqueId();
                parentUniqueIdMap.put(mapKey, parentUniqueId);
              }
            }
            // fallback to use account id as secret's parent id
            else {
              parentUniqueId = nextSecret.getAccountIdentifier();
              parentUniqueIdMap.put(mapKey, parentUniqueId);
            }
          }

          if (isNotEmpty(parentUniqueId)) {
            batchSizeCounter++;
            Update update = new Update().set(SecretKeys.parentUniqueId, parentUniqueId);
            bulkOperations.updateOne(new Query(Criteria.where("_id").is(nextSecret.getId())), update);

            if (batchSizeCounter == BATCH_SIZE) {
              migratedCounter += bulkOperations.execute().getModifiedCount();
              bulkOperations = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Project.class);
              batchSizeCounter = 0;
            }
          }
        }
      }
      if (batchSizeCounter > 0) { // for the last remaining batch of entities
        migratedCounter += bulkOperations.execute().getModifiedCount();
      }
    } catch (Exception exc) {
      log.error(format("%s job failed for Entity Type: [%s]", SECRET_PARENT_ID_GENERATION_LOG_CONST,
                    Secret.class.getSimpleName()),
          exc);
    }
    log.info(format(
        "%s Migration for Entity Type: [%s]. Total documents: [%d], documents to Update: [%s], Successful: [%d], Failed: [%d]",
        SECRET_PARENT_ID_GENERATION_LOG_CONST, Secret.class.getSimpleName(), totalCounter, updateCounter,
        migratedCounter, updateCounter - migratedCounter));
  }
}
