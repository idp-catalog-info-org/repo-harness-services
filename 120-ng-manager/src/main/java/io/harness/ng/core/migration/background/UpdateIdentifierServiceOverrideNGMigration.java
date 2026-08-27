/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.background;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.account.utils.AccountUtils;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.encryption.Scope;
import io.harness.migration.beans.NGMigration;
import io.harness.mongo.MongoPersistence;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity.NGServiceOverridesEntityKeys;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.scope.ScopeHelper;
import io.harness.utils.IdentifierRefHelper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.client.model.DBCollectionFindOptions;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@Slf4j
public class UpdateIdentifierServiceOverrideNGMigration implements NGMigration {
  @Inject private MongoPersistence mongoPersistence;
  @Inject private MongoTemplate mongoTemplate;
  @Inject private AccountUtils accountUtils;

  private static final String DEBUG_LOG = "[UpdateIdentifierServiceOverrideNGMigration]: ";
  private static final String SERVICE_OVERRIDES_NODE = "serviceOverrides";

  @Override
  public void migrate() {
    try {
      log.info(DEBUG_LOG + "Starting migration of updating identifier in serviceOverridesNG");
      List<String> accountIdentifiers = accountUtils.getAllNGAccountIds();

      accountIdentifiers.forEach(accountId -> {
        try {
          log.info(
              DEBUG_LOG + "Starting migration of updating identifier in serviceOverridesNG for account : " + accountId);

          List<DBObject> dbObjects = new ArrayList<>();
          final DBCollection collection = mongoPersistence.getCollection(NGServiceOverridesEntity.class);
          DBObject idFilter = new BasicDBObject("accountId", accountId);

          int batchSize = 100;

          DBCursor cursor = collection.find(idFilter, new DBCollectionFindOptions().batchSize(batchSize));
          while (cursor.hasNext()) {
            dbObjects.add(cursor.next());
          }

          for (DBObject dbObject : dbObjects) {
            final String orgId = getValueFromDBObject(dbObject, "orgIdentifier");
            final String projectId = getValueFromDBObject(dbObject, "projectIdentifier");
            final String environmentRefInDb = getValueFromDBObject(dbObject, "environmentRef");
            final String serviceRefInDb = getValueFromDBObject(dbObject, "serviceRef");
            try {
              final String identifierInDb = getValueFromDBObject(dbObject, "identifier");
              final String type = getValueFromDBObject(dbObject, "type");
              final String id = getValueFromDBObject(dbObject, "_id");
              String yaml = getValueFromDBObject(dbObject, "yaml");

              if (identifierInDb == null || type == null || id == null
                  || !ServiceOverridesType.ENV_SERVICE_OVERRIDE.name().equals(type) || environmentRefInDb == null
                  || serviceRefInDb == null) {
                continue;
              }

              String newEnvironmentRef = environmentRefInDb;
              String envId = IdentifierRefHelper.getIdentifier(environmentRefInDb);
              Scope envScope = IdentifierRefHelper.getScopeFromScopedRef(environmentRefInDb);

              String newServiceRef = serviceRefInDb;
              String serviceId = IdentifierRefHelper.getIdentifier(serviceRefInDb);
              Scope serviceScope = IdentifierRefHelper.getScopeFromScopedRef(serviceRefInDb);

              Scope scope = ScopeHelper.getScope(accountId, orgId, projectId);

              // If serviceOverride is not of project scope, check and correct serviceRef and environmentRef with scope
              if (!Scope.PROJECT.equals(scope)) {
                if (Scope.PROJECT.equals(envScope)) {
                  newEnvironmentRef =
                      IdentifierRefHelper.getRefFromIdentifierOrRef(accountId, orgId, projectId, environmentRefInDb);
                  envScope = scope;
                }
                if (Scope.PROJECT.equals(serviceScope)) {
                  newServiceRef =
                      IdentifierRefHelper.getRefFromIdentifierOrRef(accountId, orgId, projectId, serviceRefInDb);
                  serviceScope = scope;
                }
              }

              // Construct identifier following format and compare with value in db
              if (!Scope.PROJECT.equals(envScope)) {
                envId = getUnderscoreSeparatedValue(envScope.getYamlRepresentation(), envId);
              }
              if (!Scope.PROJECT.equals(serviceScope)) {
                serviceId = getUnderscoreSeparatedValue(serviceScope.getYamlRepresentation(), serviceId);
              }

              String identifier = getUnderscoreSeparatedValue(envId, serviceId);

              // if nothing to update, continue
              if (identifierInDb.equals(identifier) && serviceRefInDb.equals(newServiceRef)
                  && environmentRefInDb.equals(newEnvironmentRef)) {
                continue;
              }

              Update update = new Update();

              // update identifier if found different
              if (!identifierInDb.equals(identifier)) {
                update.set(NGServiceOverridesEntityKeys.identifier, identifier);
              }

              String updatedYaml = yaml;
              // update serviceRef if found different
              if (!serviceRefInDb.equals(newServiceRef)) {
                String key = NGServiceOverridesEntityKeys.serviceRef;
                update.set(key, newServiceRef);
                if (StringUtils.isNotBlank(updatedYaml)) {
                  updatedYaml = getServiceEnvRefUpdatedYaml(newServiceRef, updatedYaml, key);
                }
              }

              // update environmentRef if found different
              if (!environmentRefInDb.equals(newEnvironmentRef)) {
                String key = NGServiceOverridesEntityKeys.environmentRef;
                update.set(key, newEnvironmentRef);
                if (StringUtils.isNotBlank(updatedYaml)) {
                  updatedYaml = getServiceEnvRefUpdatedYaml(newEnvironmentRef, updatedYaml, key);
                }
              }

              if (StringUtils.isNotBlank(updatedYaml) && !yaml.equals(updatedYaml)) {
                update.set(NGServiceOverridesEntityKeys.yaml, updatedYaml);
              }

              Criteria serviceOverrideEqualityCriteria = getServiceOverrideEqualityCriteria(id);
              org.springframework.data.mongodb.core.query.Query query =
                  new org.springframework.data.mongodb.core.query.Query(serviceOverrideEqualityCriteria);

              mongoTemplate.findAndModify(
                  query, update, new FindAndModifyOptions().returnNew(true), NGServiceOverridesEntity.class);
            } catch (Exception e) {
              log.error(
                  String.format(DEBUG_LOG
                          + "Could not update identifier for serviceOverridesNG with accountId: [%s], orgId: [%s], projectId: [%s], serviceRef: [%s], environmentRef: [%s]",
                      accountId, orgId, projectId, serviceRefInDb, environmentRefInDb),
                  e);
            }
          }

          log.info(DEBUG_LOG
              + "Migration of updating identifier in serviceOverridesNG completed for account : " + accountId);
        } catch (Exception e) {
          log.error(
              DEBUG_LOG + "Migration of updating identifier in serviceOverridesNG failed for account: " + accountId, e);
        }
      });
      log.info(DEBUG_LOG + "Migration of updating identifier in serviceOverridesNG completed");
    } catch (Exception e) {
      log.error(DEBUG_LOG + "Migration of updating identifier in serviceOverridesNG failed.", e);
    }
  }

  private static String getUnderscoreSeparatedValue(String value1, String value2) {
    return String.format("%s_%s", value1, value2);
  }

  private static Criteria getServiceOverrideEqualityCriteria(String id) {
    return Criteria.where(NGServiceOverridesEntityKeys.id).is(id);
  }

  private static String getValueFromDBObject(DBObject dbObject, String field) {
    if (dbObject.containsField(field) && dbObject.get(field) != null) {
      return dbObject.get(field).toString();
    }
    return null;
  }

  private static String getServiceEnvRefUpdatedYaml(String qualifiedRef, String originalYaml, String key)
      throws IOException {
    String updatedYaml = StringUtils.EMPTY;
    if (isNotBlank(originalYaml)) {
      YamlField yamlField = YamlUtils.readTree(originalYaml);
      JsonNode serviceOverridesJsonNode =
          yamlField.getNode().getField(SERVICE_OVERRIDES_NODE).getNode().getCurrJsonNode();
      YamlField refYamlField = yamlField.getNode().getField(SERVICE_OVERRIDES_NODE).getNode().getField(key);
      if (refYamlField != null) {
        JsonNode refJsonNode = refYamlField.getNode().getCurrJsonNode();
        if (refJsonNode != null && refJsonNode.isTextual()) {
          ((ObjectNode) serviceOverridesJsonNode).put(key, qualifiedRef);
        }
        updatedYaml = YamlUtils.writeYamlString(yamlField.getNode().getCurrJsonNode());
      }
    }
    return updatedYaml;
  }
}
