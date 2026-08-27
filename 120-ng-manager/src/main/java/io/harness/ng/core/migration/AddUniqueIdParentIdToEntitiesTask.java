/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.mongo.IndexManager.Mode.AUTO;
import static io.harness.mongo.MongoConfig.NO_LIMIT;
import static io.harness.ng.core.migration.FieldNameHelper.isFieldSupported;
import static io.harness.ng.core.migration.FieldNameHelper.readNestedFieldValue;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.lang.Integer.MAX_VALUE;
import static java.lang.String.format;
import static java.util.Map.of;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.cdng.common.aws.entity.StandAloneTrafficShiftRollbackInfo;
import io.harness.cdng.creator.plan.stage.DeploymentStagePlanCreationInfo;
import io.harness.cdng.envGroup.beans.EnvironmentGroupEntity;
import io.harness.cdng.execution.StageExecutionInfo;
import io.harness.cdng.execution.StageExecutionInstanceInfo;
import io.harness.cdng.execution.StepExecutionEntity;
import io.harness.cdng.gitops.entity.Cluster;
import io.harness.cdng.instance.InstanceDeploymentInfo;
import io.harness.cdng.instance.InstanceDeploymentInfoV2;
import io.harness.cdng.k8s.trafficrouting.K8sTrafficRoutingInfo;
import io.harness.cdng.provision.awscdk.beans.AwsCdkConfig;
import io.harness.cdng.provision.azure.beans.AzureARMConfig;
import io.harness.cdng.provision.cloudformation.beans.CloudformationConfig;
import io.harness.cdng.provision.terraform.TerraformConfig;
import io.harness.cdng.provision.terraform.executions.TerraformApplyExecutionDetails;
import io.harness.cdng.provision.terraform.executions.TerraformCloudPlanExecutionDetails;
import io.harness.cdng.provision.terraform.executions.TerraformPlanExecutionDetails;
import io.harness.cdng.provision.terraformcloud.dal.TerraformCloudConfig;
import io.harness.cdng.provision.terragrunt.TerragruntConfig;
import io.harness.connector.entities.Connector;
import io.harness.data.structure.UUIDGenerator;
import io.harness.entities.DeploymentSummary;
import io.harness.entities.InfrastructureMapping;
import io.harness.entities.Instance;
import io.harness.entities.InstanceSyncPerpetualTaskMapping;
import io.harness.entities.ReleaseDetailsMapping;
import io.harness.entities.ReleaseDetailsMapping.ReleaseDetailsMappingNGKeys;
import io.harness.favorites.entities.Favorite;
import io.harness.filestore.entities.NGFile;
import io.harness.freeze.entity.FreezeConfigEntity;
import io.harness.freeze.entity.FrozenExecution;
import io.harness.gitsync.gitxwebhooks.entity.GitXWebhook;
import io.harness.ipallowlist.entity.IPAllowlistEntity;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.mongo.IndexManager;
import io.harness.mongo.MongoConfig;
import io.harness.ng.core.activityhistory.entity.NGActivity;
import io.harness.ng.core.activityhistory.entity.NGActivity.ActivityHistoryEntityKeys;
import io.harness.ng.core.entities.ApiKey;
import io.harness.ng.core.entities.NGEncryptedData;
import io.harness.ng.core.entities.Organization;
import io.harness.ng.core.entities.Organization.OrganizationKeys;
import io.harness.ng.core.entities.Project;
import io.harness.ng.core.entities.Project.ProjectKeys;
import io.harness.ng.core.entities.Token;
import io.harness.ng.core.entities.migration.NGManagerUniqueIdParentIdMigrationStatus;
import io.harness.ng.core.entities.migration.NGManagerUniqueIdParentIdMigrationStatus.NGManagerUniqueIdParentIdMigrationStatusKeys;
import io.harness.ng.core.entitysetupusage.entity.EntitySetupUsage;
import io.harness.ng.core.entitysetupusage.entity.EntitySetupUsage.EntitySetupUsageKeys;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.invites.entities.Invite;
import io.harness.ng.core.licenseusage.entities.LicenseUsage;
import io.harness.ng.core.models.Secret;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.entity.ServiceSequence;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.user.entities.UserGroup;
import io.harness.ng.core.user.entities.UserMembership;
import io.harness.ng.core.user.entities.UserMembership.UserMembershipKeys;
import io.harness.ng.core.variable.entity.Variable;
import io.harness.ng.serviceaccounts.entities.ServiceAccount;
import io.harness.ngsettings.entities.AccountSetting;
import io.harness.persistence.HPersistence;
import io.harness.persistence.UniqueIdAccess;
import io.harness.persistence.UniqueIdAware;
import io.harness.persistence.store.Store;
import io.harness.polling.bean.PollingDocument;

import com.google.inject.Inject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.UpdateOneModel;
import dev.morphia.Morphia;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bson.BsonDocument;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mapping.model.MappingInstantiationException;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@Slf4j
@OwnedBy(HarnessTeam.PL)
public class AddUniqueIdParentIdToEntitiesTask implements Runnable {
  private static final String PARENT_UNIQUE_ID_KEY = "parentUniqueId";
  private static final String LOCK_NAME_PREFIX = "NGEntitiesPeriodicMigrationTaskLock";
  private static final String ORPHAN_ENTITY_PARENT_UNIQUE_ID_PREFIX = "orphan_";
  private static final String NG_MANAGER_ENTITIES_MIGRATION_LOG =
      "[NGManagerAddUniqueIdAndParentUniqueIdToEntitiesTask]:";
  private static final long SLEEP_DELAY_MS =
      Long.parseLong(System.getenv().getOrDefault("PROJECT_MIGRATION_MONGO_MIGRATION_SLEEP_DELAY_MS", "2000"));
  private static final int BATCH_SIZE = 500;

  private final MongoTemplate mongoTemplate;
  private final PersistentLocker persistentLocker;
  private final IndexManager indexManager;
  private final HPersistence persistence;
  private final MongoConfig mongoConfig;

  private static final String PARENT_UNIQUE_ID_FIELD_NAME = "parentUniqueIdFieldName";
  private static final String ID_FIELD_NAME = "idFieldName";
  private static final String ACCOUNT_IDENTIFIER_FIELD_NAME = "accountIdentifierFieldName";
  private static final String ORG_IDENTIFIER_FIELD_NAME = "orgIdentifierFieldName";
  private static final String PROJECT_IDENTIFIER_FIELD_NAME = "projectIdentifierFieldName";

  private Map<String, String> scopeEntityUniqueIdMap;
  private Map<String, ScopeInfo> scopeInfoMap;

  private static final String ORG_ID_KEY = "orgIdKey";
  private static final String PROJECT_ID_KEY = "projectIdKey";
  private static final String UNIQUE_ID_KEY = "uniqueIdKey";

  // the following are for the releaseDetailsMappingNg
  public static final String releaseServiceOrgIdentifier = "releaseDetails.serviceDetails.orgIdentifier";
  public static final String releaseServiceProjectIdentifier = "releaseDetails.serviceDetails.projectIdentifier";
  public static final String releaseServiceParentUniqueId = "releaseDetails.serviceDetails.parentUniqueId";
  public static final String releaseEnvOrgIdentifier = "releaseDetails.envDetails.orgIdentifier";
  public static final String releaseEnvProjectIdentifier = "releaseDetails.envDetails.projectIdentifier";
  public static final String releaseEnvParentUniqueId = "releaseDetails.envDetails.parentUniqueId";

  @Getter
  public static final Map<Class<? extends UniqueIdAware>, Map<String, String>> entityWithOrgProjectKeysMap =
      Map.ofEntries(Map.entry(Organization.class, Map.of()),
          Map.entry(Project.class, Map.of(ORG_ID_KEY, NGCommonEntityConstants.ORG_KEY)),
          Map.entry(Connector.class,
              Map.of(ORG_ID_KEY, NGCommonEntityConstants.ORG_KEY, PROJECT_ID_KEY, NGCommonEntityConstants.PROJECT_KEY)),
          Map.entry(ServiceAccount.class,
              Map.of(ORG_ID_KEY, NGCommonEntityConstants.ORG_KEY, PROJECT_ID_KEY, NGCommonEntityConstants.PROJECT_KEY)),
          Map.entry(Secret.class,
              Map.of(ORG_ID_KEY, NGCommonEntityConstants.ORG_KEY, PROJECT_ID_KEY, NGCommonEntityConstants.PROJECT_KEY)),
          Map.entry(NGEncryptedData.class,
              Map.of(ORG_ID_KEY, NGCommonEntityConstants.ORG_KEY, PROJECT_ID_KEY, NGCommonEntityConstants.PROJECT_KEY)),
          Map.entry(Favorite.class,
              Map.of(ORG_ID_KEY, NGCommonEntityConstants.ORG_KEY, PROJECT_ID_KEY, NGCommonEntityConstants.PROJECT_KEY)),
          Map.entry(Variable.class,
              Map.of(ORG_ID_KEY, NGCommonEntityConstants.ORG_KEY, PROJECT_ID_KEY, NGCommonEntityConstants.PROJECT_KEY)),
          Map.entry(Token.class,
              Map.of(ORG_ID_KEY, NGCommonEntityConstants.ORG_KEY, PROJECT_ID_KEY, NGCommonEntityConstants.PROJECT_KEY)),
          Map.entry(ApiKey.class,
              Map.of(ORG_ID_KEY, NGCommonEntityConstants.ORG_KEY, PROJECT_ID_KEY, NGCommonEntityConstants.PROJECT_KEY)),
          Map.entry(AccountSetting.class,
              Map.of(ORG_ID_KEY, NGCommonEntityConstants.ORG_KEY, PROJECT_ID_KEY, NGCommonEntityConstants.PROJECT_KEY)),
          Map.entry(UserGroup.class,
              Map.of(ORG_ID_KEY, NGCommonEntityConstants.ORG_KEY, PROJECT_ID_KEY, NGCommonEntityConstants.PROJECT_KEY)),
          Map.entry(Invite.class,
              Map.of(ORG_ID_KEY, NGCommonEntityConstants.ORG_KEY, PROJECT_ID_KEY, NGCommonEntityConstants.PROJECT_KEY)),
          Map.entry(IPAllowlistEntity.class,
              Map.of(ORG_ID_KEY, NGCommonEntityConstants.ORG_KEY, PROJECT_ID_KEY, NGCommonEntityConstants.PROJECT_KEY)),
          Map.entry(LicenseUsage.class,
              Map.of(ORG_ID_KEY, NGCommonEntityConstants.ORG_KEY, PROJECT_ID_KEY, NGCommonEntityConstants.PROJECT_KEY)),
          Map.entry(GitXWebhook.class,
              Map.of(ORG_ID_KEY, NGCommonEntityConstants.ORG_KEY, PROJECT_ID_KEY, NGCommonEntityConstants.PROJECT_KEY)),
          Map.entry(AwsCdkConfig.class,
              Map.of(ORG_ID_KEY, NGCommonEntityConstants.ORG_ID, PROJECT_ID_KEY, NGCommonEntityConstants.PROJECT_ID)),
          Map.entry(ServiceSequence.class,
              Map.of(ORG_ID_KEY, NGCommonEntityConstants.ORG_KEY, PROJECT_ID_KEY, NGCommonEntityConstants.PROJECT_KEY)),
          Map.entry(StepExecutionEntity.class,
              Map.of(ORG_ID_KEY, NGCommonEntityConstants.ORG_KEY, PROJECT_ID_KEY, NGCommonEntityConstants.PROJECT_KEY)),
          Map.entry(ServiceEntity.class,
              Map.of(ORG_ID_KEY, NGCommonEntityConstants.ORG_KEY, PROJECT_ID_KEY, NGCommonEntityConstants.PROJECT_KEY)),
          Map.entry(InfrastructureMapping.class,
              Map.of(ORG_ID_KEY, NGCommonEntityConstants.ORG_KEY, PROJECT_ID_KEY, NGCommonEntityConstants.PROJECT_KEY)),
          Map.entry(InfrastructureEntity.class,
              Map.of(ORG_ID_KEY, NGCommonEntityConstants.ORG_KEY, PROJECT_ID_KEY, NGCommonEntityConstants.PROJECT_KEY)),
          Map.entry(StageExecutionInfo.class,
              Map.of(ORG_ID_KEY, NGCommonEntityConstants.ORG_KEY, PROJECT_ID_KEY, NGCommonEntityConstants.PROJECT_KEY)),
          Map.entry(StageExecutionInstanceInfo.class,
              Map.of(ORG_ID_KEY, NGCommonEntityConstants.ORG_KEY, PROJECT_ID_KEY, NGCommonEntityConstants.PROJECT_KEY)),
          Map.entry(NGServiceOverridesEntity.class,
              Map.of(ORG_ID_KEY, NGCommonEntityConstants.ORG_KEY, PROJECT_ID_KEY, NGCommonEntityConstants.PROJECT_KEY)),
          Map.entry(AzureARMConfig.class,
              Map.of(ORG_ID_KEY, NGCommonEntityConstants.ORG_ID, PROJECT_ID_KEY, NGCommonEntityConstants.PROJECT_ID)),
          Map.entry(CloudformationConfig.class,
              Map.of(ORG_ID_KEY, NGCommonEntityConstants.ORG_ID, PROJECT_ID_KEY, NGCommonEntityConstants.PROJECT_ID)),
          Map.entry(TerragruntConfig.class,
              Map.of(ORG_ID_KEY, NGCommonEntityConstants.ORG_ID, PROJECT_ID_KEY, NGCommonEntityConstants.PROJECT_ID)),
          Map.entry(TerraformConfig.class,
              Map.of(ORG_ID_KEY, NGCommonEntityConstants.ORG_ID, PROJECT_ID_KEY, NGCommonEntityConstants.PROJECT_ID)),
          Map.entry(TerraformPlanExecutionDetails.class,
              Map.of(ORG_ID_KEY, NGCommonEntityConstants.ORG_KEY, PROJECT_ID_KEY, NGCommonEntityConstants.PROJECT_KEY)),
          Map.entry(TerraformApplyExecutionDetails.class,
              Map.of(ORG_ID_KEY, NGCommonEntityConstants.ORG_KEY, PROJECT_ID_KEY, NGCommonEntityConstants.PROJECT_KEY)),
          Map.entry(TerraformCloudPlanExecutionDetails.class,
              Map.of(ORG_ID_KEY, NGCommonEntityConstants.ORG_KEY, PROJECT_ID_KEY, NGCommonEntityConstants.PROJECT_KEY)),
          Map.entry(TerraformCloudConfig.class,
              Map.of(ORG_ID_KEY, NGCommonEntityConstants.ORG_ID, PROJECT_ID_KEY, NGCommonEntityConstants.PROJECT_ID)),
          Map.entry(Environment.class,
              Map.of(ORG_ID_KEY, NGCommonEntityConstants.ORG_KEY, PROJECT_ID_KEY, NGCommonEntityConstants.PROJECT_KEY)),
          Map.entry(Cluster.class,
              Map.of(ORG_ID_KEY, NGCommonEntityConstants.ORG_KEY, PROJECT_ID_KEY, NGCommonEntityConstants.PROJECT_KEY)),
          Map.entry(FrozenExecution.class,
              Map.of(ORG_ID_KEY, NGCommonEntityConstants.ORG_ID, PROJECT_ID_KEY, NGCommonEntityConstants.PROJECT_ID)),
          Map.entry(FreezeConfigEntity.class,
              Map.of(ORG_ID_KEY, NGCommonEntityConstants.ORG_KEY, PROJECT_ID_KEY, NGCommonEntityConstants.PROJECT_KEY)),
          Map.entry(PollingDocument.class,
              Map.of(ORG_ID_KEY, NGCommonEntityConstants.ORG_KEY, PROJECT_ID_KEY, NGCommonEntityConstants.PROJECT_KEY)),
          Map.entry(EnvironmentGroupEntity.class,
              Map.of(ORG_ID_KEY, NGCommonEntityConstants.ORG_KEY, PROJECT_ID_KEY, NGCommonEntityConstants.PROJECT_KEY)),
          Map.entry(K8sTrafficRoutingInfo.class,
              Map.of(ORG_ID_KEY, NGCommonEntityConstants.ORG_ID, PROJECT_ID_KEY, NGCommonEntityConstants.PROJECT_ID,
                  UNIQUE_ID_KEY, NGCommonEntityConstants.MONGODB_ID)),
          Map.entry(DeploymentSummary.class,
              Map.of(ORG_ID_KEY, NGCommonEntityConstants.ORG_KEY, PROJECT_ID_KEY, NGCommonEntityConstants.PROJECT_KEY)),
          Map.entry(NGFile.class,
              Map.of(ORG_ID_KEY, NGCommonEntityConstants.ORG_KEY, PROJECT_ID_KEY, NGCommonEntityConstants.PROJECT_KEY)),
          Map.entry(ReleaseDetailsMapping.class,
              Map.of(ORG_ID_KEY, NGCommonEntityConstants.ORG_KEY, PROJECT_ID_KEY, NGCommonEntityConstants.PROJECT_KEY)),
          Map.entry(DeploymentStagePlanCreationInfo.class,
              Map.of(ORG_ID_KEY, NGCommonEntityConstants.ORG_KEY, PROJECT_ID_KEY, NGCommonEntityConstants.PROJECT_KEY)),
          Map.entry(Instance.class,
              Map.of(ORG_ID_KEY, NGCommonEntityConstants.ORG_KEY, PROJECT_ID_KEY, NGCommonEntityConstants.PROJECT_KEY)),
          Map.entry(InstanceDeploymentInfo.class,
              Map.of(ORG_ID_KEY, NGCommonEntityConstants.ORG_KEY, PROJECT_ID_KEY, NGCommonEntityConstants.PROJECT_KEY)),
          Map.entry(InstanceDeploymentInfoV2.class,
              Map.of(ORG_ID_KEY, NGCommonEntityConstants.ORG_KEY, PROJECT_ID_KEY, NGCommonEntityConstants.PROJECT_KEY)),
          Map.entry(InstanceSyncPerpetualTaskMapping.class,
              Map.of(ORG_ID_KEY, NGCommonEntityConstants.ORG_ID, PROJECT_ID_KEY, NGCommonEntityConstants.PROJECT_ID)),
          Map.entry(StandAloneTrafficShiftRollbackInfo.class,
              Map.of(
                  ORG_ID_KEY, NGCommonEntityConstants.ORG_KEY, PROJECT_ID_KEY, NGCommonEntityConstants.PROJECT_KEY)));

  public static Map<Object, List<Map<String, String>>> getEntitiesWithNestedParentuniqueId() {
    Map<Object, List<Map<String, String>>> entities = new HashMap<>();
    entities.put(NGActivity.class,
        List.of(of(ID_FIELD_NAME, ActivityHistoryEntityKeys.id, PARENT_UNIQUE_ID_FIELD_NAME,
                    ActivityHistoryEntityKeys.referredEntityParentUniqueId, ACCOUNT_IDENTIFIER_FIELD_NAME,
                    ActivityHistoryEntityKeys.referredEntityAccountIdentifier, ORG_IDENTIFIER_FIELD_NAME,
                    ActivityHistoryEntityKeys.referredEntityOrgIdentifier, PROJECT_IDENTIFIER_FIELD_NAME,
                    ActivityHistoryEntityKeys.referredEntityProjectIdentifier),
            of(ID_FIELD_NAME, ActivityHistoryEntityKeys.id, PARENT_UNIQUE_ID_FIELD_NAME,
                ActivityHistoryEntityKeys.referredByEntityParentUniqueId, ACCOUNT_IDENTIFIER_FIELD_NAME,
                ActivityHistoryEntityKeys.referredByEntityAccountIdentifier, ORG_IDENTIFIER_FIELD_NAME,
                ActivityHistoryEntityKeys.referredByEntityOrgIdentifier, PROJECT_IDENTIFIER_FIELD_NAME,
                ActivityHistoryEntityKeys.referredByEntityProjectIdentifier)));

    entities.put(EntitySetupUsage.class,
        List.of(of(ID_FIELD_NAME, EntitySetupUsageKeys.id, PARENT_UNIQUE_ID_FIELD_NAME,
                    EntitySetupUsageKeys.referredEntityParentUniqueId, ACCOUNT_IDENTIFIER_FIELD_NAME,
                    EntitySetupUsageKeys.referredEntityAccountIdentifier, ORG_IDENTIFIER_FIELD_NAME,
                    EntitySetupUsageKeys.referredEntityOrgIdentifier, PROJECT_IDENTIFIER_FIELD_NAME,
                    EntitySetupUsageKeys.referredEntityProjectIdentifier),
            of(ID_FIELD_NAME, EntitySetupUsageKeys.id, PARENT_UNIQUE_ID_FIELD_NAME,
                EntitySetupUsageKeys.referredByEntityParentUniqueId, ACCOUNT_IDENTIFIER_FIELD_NAME,
                EntitySetupUsageKeys.referredByEntityAccountIdentifier, ORG_IDENTIFIER_FIELD_NAME,
                EntitySetupUsageKeys.referredByEntityOrgIdentifier, PROJECT_IDENTIFIER_FIELD_NAME,
                EntitySetupUsageKeys.referredByEntityProjectIdentifier)));

    entities.put(ReleaseDetailsMapping.class,
        List.of(
            of(ID_FIELD_NAME, ReleaseDetailsMappingNGKeys.id, PARENT_UNIQUE_ID_FIELD_NAME, releaseServiceParentUniqueId,
                ACCOUNT_IDENTIFIER_FIELD_NAME, ReleaseDetailsMappingNGKeys.accountIdentifier, ORG_IDENTIFIER_FIELD_NAME,
                releaseServiceOrgIdentifier, PROJECT_IDENTIFIER_FIELD_NAME, releaseServiceProjectIdentifier),
            of(ID_FIELD_NAME, ReleaseDetailsMappingNGKeys.id, PARENT_UNIQUE_ID_FIELD_NAME, releaseEnvParentUniqueId,
                ACCOUNT_IDENTIFIER_FIELD_NAME, ReleaseDetailsMappingNGKeys.accountIdentifier, ORG_IDENTIFIER_FIELD_NAME,
                releaseEnvOrgIdentifier, PROJECT_IDENTIFIER_FIELD_NAME, releaseEnvProjectIdentifier)));

    return entities;
  }

  @Inject
  public AddUniqueIdParentIdToEntitiesTask(MongoTemplate mongoTemplate, PersistentLocker persistentLocker,
      IndexManager indexManager, HPersistence persistence, MongoConfig mongoConfig) {
    this.mongoTemplate = mongoTemplate;
    this.persistentLocker = persistentLocker;
    this.indexManager = indexManager;
    this.persistence = persistence;
    this.mongoConfig = mongoConfig;
    this.scopeEntityUniqueIdMap = new HashMap<>();
    this.scopeInfoMap = new HashMap<>();
  }

  @Override
  public void run() {
    log.info(format("%s starting...", NG_MANAGER_ENTITIES_MIGRATION_LOG));

    for (Map.Entry<Class<? extends UniqueIdAware>, Map<String, String>> entityMapEntry :
        entityWithOrgProjectKeysMap.entrySet()) {
      Map<String, String> fieldMap = entityMapEntry.getValue();
      String orgIdentifierFieldName = fieldMap.get(ORG_ID_KEY);
      String projectIdentifierFieldName = fieldMap.get(PROJECT_ID_KEY);
      String uniqueIdField = fieldMap.getOrDefault(UNIQUE_ID_KEY, UniqueIdAccess.UNIQUE_ID_KEY);

      Class<? extends UniqueIdAware> clazz = entityMapEntry.getKey();
      final String typeAliasName = getTypeAliasValueOrNameForClass(clazz);
      NGManagerUniqueIdParentIdMigrationStatus foundEntity = mongoTemplate.findOne(
          new Query(Criteria.where(NGManagerUniqueIdParentIdMigrationStatusKeys.entityClassName).is(typeAliasName)),
          NGManagerUniqueIdParentIdMigrationStatus.class);
      if (foundEntity == null) {
        foundEntity = NGManagerUniqueIdParentIdMigrationStatus.builder()
                          .entityClassName(typeAliasName)
                          .parentIdMigrationCompleted(Boolean.FALSE)
                          .uniqueIdMigrationCompleted(Boolean.FALSE)
                          .build();
      }

      if (TRUE.equals(foundEntity.getUniqueIdMigrationCompleted())) {
        log.info(format("%s job for uniqueId on Entity Type: [%s] already completed.",
            NG_MANAGER_ENTITIES_MIGRATION_LOG, clazz.getSimpleName()));
      } else {
        performUniqueIdMigrationTask(foundEntity, clazz, uniqueIdField);
      }

      if (TRUE.equals(foundEntity.getParentIdMigrationCompleted())) {
        log.info(format("%s job for parentId on Entity Type: [%s] already completed.",
            NG_MANAGER_ENTITIES_MIGRATION_LOG, clazz.getSimpleName()));
      } else {
        performParentIdMigrationTask(foundEntity, clazz, orgIdentifierFieldName, projectIdentifierFieldName);
      }

      if (TRUE.equals(foundEntity.getOrphanEntityParentIdMigrationCompleted())) {
        log.info(format("%s job for orphan records parentUniqueId on Entity Type: [%s] already completed.",
            NG_MANAGER_ENTITIES_MIGRATION_LOG, clazz.getSimpleName()));
      } else {
        performOrphanEntityParentUniqueIdMigration(
            foundEntity, clazz, orgIdentifierFieldName, projectIdentifierFieldName);
      }

      if (TRUE.equals(foundEntity.getIndexCreationCompleted())) {
        log.info(format("%s job for index creation on Entity Type: [%s] already completed.",
            NG_MANAGER_ENTITIES_MIGRATION_LOG, clazz.getSimpleName()));
      } else {
        performMissingIndexCreation(foundEntity, clazz);
      }
    }

    // Migration for parent unique id present at nested level
    Map<Object, List<Map<String, String>>> entities = getEntitiesWithNestedParentuniqueId();
    String entityClassName;

    for (Map.Entry<Object, List<Map<String, String>>> entity : entities.entrySet()) {
      entityClassName = ((Class<?>) entity.getKey()).getName();
      if (entityClassName.equals("io.harness.entities.ReleaseDetailsMapping")) {
        entityClassName += "_nestedFields";
      }
      NGManagerUniqueIdParentIdMigrationStatus foundEntity = mongoTemplate.findOne(
          new Query(Criteria.where(NGManagerUniqueIdParentIdMigrationStatusKeys.entityClassName).is(entityClassName)),
          NGManagerUniqueIdParentIdMigrationStatus.class);
      if (foundEntity == null) {
        foundEntity = NGManagerUniqueIdParentIdMigrationStatus.builder()
                          .entityClassName(entityClassName)
                          .parentIdMigrationCompleted(Boolean.FALSE)
                          .build();
      }
      log.info(format(
          "%s starting job for nested parentId on entity [%s]", NG_MANAGER_ENTITIES_MIGRATION_LOG, entity.getKey()));
      if (TRUE.equals(foundEntity.getParentIdMigrationCompleted())) {
        log.info(format("%s job for parentId on entity [%s] already completed successfully.",
            NG_MANAGER_ENTITIES_MIGRATION_LOG, entity.getKey()));
      } else {
        performNestedParentUniqueIdMigration(entity, foundEntity);
      }

      if (TRUE.equals(foundEntity.getIndexCreationCompleted())) {
        log.info(format("%s job for index creation on Entity Type: [%s] already completed.",
            NG_MANAGER_ENTITIES_MIGRATION_LOG, entity.getKey()));
      } else {
        performMissingIndexCreation(foundEntity, (Class<?>) entity.getKey());
      }
    }

    performMigrationForUserMembership();
  }

  private void performMigrationForUserMembership() {
    String userMembershipClassName = UserMembership.class.getName();
    NGManagerUniqueIdParentIdMigrationStatus foundEntity = mongoTemplate.findOne(
        new Query(
            Criteria.where(NGManagerUniqueIdParentIdMigrationStatusKeys.entityClassName).is(userMembershipClassName)),
        NGManagerUniqueIdParentIdMigrationStatus.class);
    if (foundEntity == null) {
      foundEntity = NGManagerUniqueIdParentIdMigrationStatus.builder()
                        .entityClassName(userMembershipClassName)
                        .parentIdMigrationCompleted(Boolean.FALSE)
                        .build();
    }
    log.info(format("%s starting job for nested parentId on entity [%s]", NG_MANAGER_ENTITIES_MIGRATION_LOG,
        userMembershipClassName));
    if (TRUE.equals(foundEntity.getParentIdMigrationCompleted())) {
      log.info(format("%s job for parentId on entity [%s] already completed successfully.",
          NG_MANAGER_ENTITIES_MIGRATION_LOG, userMembershipClassName));
    } else {
      performUserMembershipParentUniqueIdMigration(foundEntity);
    }

    if (TRUE.equals(foundEntity.getOrphanEntityParentIdMigrationCompleted())) {
      log.info(format("%s job for parentId on entity [%s] already completed successfully.",
          NG_MANAGER_ENTITIES_MIGRATION_LOG, userMembershipClassName));
    } else {
      performOrphanUserMembershipParentUniqueIdMigration(foundEntity);
    }

    if (TRUE.equals(foundEntity.getIndexCreationCompleted())) {
      log.info(format("%s job for index creation on Entity Type: [%s] already completed.",
          NG_MANAGER_ENTITIES_MIGRATION_LOG, userMembershipClassName));
    } else {
      performMissingIndexCreation(foundEntity, UserMembership.class);
    }
  }

  private void performMissingIndexCreation(NGManagerUniqueIdParentIdMigrationStatus foundEntity, final Class<?> clazz) {
    Morphia morphia = new Morphia();
    morphia.map(clazz);

    Store store = null;
    if (Objects.nonNull(mongoConfig.getAliasDBName())) {
      store = Store.builder().name(mongoConfig.getAliasDBName()).build();
    }

    try {
      indexManager.ensureIndexes(AUTO, persistence.getDatastore(clazz), morphia, store);
      foundEntity.setIndexCreationCompleted(TRUE);
      mongoTemplate.save(foundEntity);
      log.info(format("%s job Succeeded for index creation on Entity Type [%s]", NG_MANAGER_ENTITIES_MIGRATION_LOG,
          clazz.getSimpleName()));
    } catch (Exception e) {
      log.error(format("%s job failed for index creation on Entity Type [%s]", NG_MANAGER_ENTITIES_MIGRATION_LOG,
                    clazz.getSimpleName()),
          e);
    }
  }

  private void performUniqueIdMigrationTask(NGManagerUniqueIdParentIdMigrationStatus migrationStatusEntity,
      final Class<? extends UniqueIdAware> clazz, final String uniqueIdField) {
    log.info(format(
        "%s Starting uniqueId migration for Entity: [%s]", NG_MANAGER_ENTITIES_MIGRATION_LOG, clazz.getSimpleName()));

    int migratedCounter = 0;
    int batchSizeCounter = 0;
    int toUpdateCounter = 0;
    int skippedCounter = 0;

    try (AcquiredLock<?> lock =
             persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(LOCK_NAME_PREFIX, Duration.ofSeconds(5))) {
      if (lock == null) {
        log.info(format("%s failed to acquire lock for Entity type: [%s] during uniqueId migration task",
            NG_MANAGER_ENTITIES_MIGRATION_LOG, clazz.getSimpleName()));
        return;
      }
      try {
        String collectionName = mongoTemplate.getCollectionName(clazz);
        List<UpdateOneModel<Document>> bulkUpdates = new ArrayList<>();
        BulkWriteOptions options = new BulkWriteOptions().ordered(false);
        // Use raw MongoDB cursor to avoid deserialization
        try (MongoCursor<Document> cursor = mongoTemplate.getCollection(collectionName)
                                                .find()
                                                .limit(NO_LIMIT)
                                                .maxTime(MAX_VALUE, TimeUnit.MILLISECONDS)
                                                .noCursorTimeout(true)
                                                .iterator()) {
          // last document for debugging nested interface/abstract class fields or corrupted records that cannot be
          // cast.
          Document lastDoc = null;
          while (cursor.hasNext()) {
            try {
              Document doc = cursor.next();
              lastDoc = doc;
              String uniqueId = doc.getString(uniqueIdField);

              if (isEmpty(uniqueId)) {
                Object idObj = doc.get("_id");
                if (idObj != null) {
                  toUpdateCounter++;

                  // Create the update operation with Bson
                  BsonDocument update = new BsonDocument(
                      "$set", new BsonDocument(uniqueIdField, new BsonString(UUIDGenerator.generateUuid())));

                  // Dynamically create the filter based on the type of _id
                  BsonDocument filter;
                  if (idObj instanceof String) {
                    filter = new BsonDocument("_id", new BsonString((String) idObj)); // _id is a String
                  } else if (idObj instanceof ObjectId) {
                    filter = new BsonDocument("_id", new BsonObjectId((ObjectId) idObj)); // _id is an ObjectId
                  } else {
                    // If it's neither, we'll just skip it for now (you can add error handling here)
                    log.debug("Unexpected _id type, skipping document: " + doc.toJson());
                    skippedCounter++;
                    continue;
                  }
                  batchSizeCounter++;
                  // Add to bulk update list
                  bulkUpdates.add(new UpdateOneModel<>(filter, update));

                  // If batch size reaches the limit, execute the bulk operation
                  if (batchSizeCounter == BATCH_SIZE) {
                    // Execute bulk write operation
                    migratedCounter +=
                        mongoTemplate.getCollection(collectionName).bulkWrite(bulkUpdates, options).getModifiedCount();
                    bulkUpdates.clear(); // Clear the list for the next batch
                    Thread.sleep(SLEEP_DELAY_MS);
                    batchSizeCounter = 0;
                  }
                }
              }
            } catch (Exception exc) {
              log.error(format("%s job for uniqueId migration on Entity: [%s], encountered error processing document: "
                                + "[%s], skipping",
                            NG_MANAGER_ENTITIES_MIGRATION_LOG, clazz.getSimpleName(),
                            lastDoc != null ? lastDoc.toJson() : "null"),
                  exc);
              skippedCounter++;
            }
          }
          if (batchSizeCounter > 0) { // for the last remaining batch of entities
            migratedCounter +=
                mongoTemplate.getCollection(collectionName).bulkWrite(bulkUpdates, options).getModifiedCount();
          }
        } catch (Exception e) {
          log.error(format("%s job for uniqueId failed to iterate over entities of Entity Type [%s]",
                        NG_MANAGER_ENTITIES_MIGRATION_LOG, clazz.getSimpleName()),
              e);
          return;
        }
      } catch (Exception exc) {
        log.error(format("%s job for uniqueId failed on Entity Type [%s]", NG_MANAGER_ENTITIES_MIGRATION_LOG,
                      clazz.getSimpleName()),
            exc);
        return;
      }
    }

    if (toUpdateCounter == migratedCounter) {
      migrationStatusEntity.setUniqueIdMigrationCompleted(TRUE);
      log.info(format("%s job on entity [%s] for uniqueId Succeeded. Documents to Update and Successful: [%d], "
              + "Skipped(Failed or Invalid Entities): [%d]",
          NG_MANAGER_ENTITIES_MIGRATION_LOG, clazz.getSimpleName(), toUpdateCounter, skippedCounter));
    } else {
      log.warn(format("%s job failed on entity [%s] for uniqueId. Documents to Update: [%d], Successful: [%d], Failed: "
              + "[%d], Skipped(Failed or Invalid Entities): [%d]",
          NG_MANAGER_ENTITIES_MIGRATION_LOG, clazz.getSimpleName(), toUpdateCounter, migratedCounter,
          toUpdateCounter - migratedCounter, skippedCounter));
    }
    mongoTemplate.save(migrationStatusEntity);
  }

  private void performParentIdMigrationTask(NGManagerUniqueIdParentIdMigrationStatus foundEntity,
      final Class<? extends UniqueIdAware> clazz, final String orgIdentifierFieldName,
      final String projectIdentifierFieldName) {
    if (clazz == Organization.class && foundEntity.getEntityClassName() != null
        && foundEntity.getEntityClassName().equals(Organization.class.getName())) {
      performOrganizationParentUniqueIdMigrationTask(foundEntity);
    } else if (clazz == Project.class && foundEntity.getEntityClassName() != null
        && foundEntity.getEntityClassName().equals(Project.class.getName())) {
      performProjectParentUniqueIdMigrationTask(foundEntity);
    } else {
      performEntityParentUniqueIdMigrationTask(foundEntity, clazz, orgIdentifierFieldName, projectIdentifierFieldName);
    }
  }

  private void performOrphanEntityParentUniqueIdMigration(NGManagerUniqueIdParentIdMigrationStatus foundEntity,
      final Class<? extends UniqueIdAware> clazz, final String orgIdentifierFieldName,
      final String projectIdentifierFieldName) {
    if (clazz == Organization.class && foundEntity.getEntityClassName() != null
        && foundEntity.getEntityClassName().equals(Organization.class.getName())) {
      // Orphan entity migration not needed in case of org, It will always assign account id as ParentUniqueId
      return;
    } else if (clazz == Project.class && foundEntity.getEntityClassName() != null
        && foundEntity.getEntityClassName().equals(Project.class.getName())) {
      performProjectParentUniqueIdMigrationTask(foundEntity);
    } else {
      performEntityParentUniqueIdMigrationTask(foundEntity, clazz, orgIdentifierFieldName, projectIdentifierFieldName);
    }
  }

  private void performProjectParentUniqueIdMigrationTask(NGManagerUniqueIdParentIdMigrationStatus foundEntity) {
    int migratedCounter = 0;
    int batchSizeCounter = 0;
    int skippedCounter = 0;
    int orphanCounter = 0;
    final String LOCAL_MAP_DELIMITER = "|";

    try (AcquiredLock<?> lock =
             persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(LOCK_NAME_PREFIX, Duration.ofSeconds(5))) {
      if (lock == null) {
        log.info(format("%s failed to acquire lock for Entity type: [%s] during parentId migration task",
            NG_MANAGER_ENTITIES_MIGRATION_LOG, "Project"));
        return;
      }
      try {
        final Map<String, String> orgIdentifierUniqueIdMap = new HashMap<>();

        Query documentQuery = new Query(new Criteria());
        BulkOperations bulkOperations = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Project.class);
        // iterate over all Project documents
        try (Stream<Project> stream =
                 mongoTemplate.stream(documentQuery.limit(NO_LIMIT).maxTimeMsec(MAX_VALUE), Project.class)) {
          Iterator<Project> iterator = stream.iterator();
          while (iterator.hasNext()) {
            try {
              Project nextProject = iterator.next();
              if (isEmpty(nextProject.getParentUniqueId())) {
                final String mapKey =
                    nextProject.getAccountIdentifier() + LOCAL_MAP_DELIMITER + nextProject.getOrgIdentifier();
                String uniqueIdOfOrg = null;
                // check if Org with uniqueId is present locally
                if (orgIdentifierUniqueIdMap.containsKey(mapKey)) {
                  uniqueIdOfOrg = orgIdentifierUniqueIdMap.get(mapKey);
                } else {
                  Criteria orgCriteria = Criteria.where("accountIdentifier")
                                             .is(nextProject.getAccountIdentifier())
                                             .and("identifier")
                                             .is(nextProject.getOrgIdentifier());
                  Organization organization = mongoTemplate.findOne(new Query(orgCriteria), Organization.class);
                  if (organization != null && isNotEmpty(organization.getUniqueId())) {
                    uniqueIdOfOrg = organization.getUniqueId();
                  } else {
                    uniqueIdOfOrg = ORPHAN_ENTITY_PARENT_UNIQUE_ID_PREFIX + UUIDGenerator.generateUuid();
                    orgIdentifierUniqueIdMap.put(mapKey, uniqueIdOfOrg);
                    log.warn(format("%s For EntityType: %s and ParentType: %s having identifier: %s, not found or "
                            + "uniqueId on parent not present.",
                        NG_MANAGER_ENTITIES_MIGRATION_LOG, "Project", "Organization", nextProject.getOrgIdentifier()));
                    orphanCounter++;
                  }
                }

                if (isNotEmpty(uniqueIdOfOrg)) {
                  batchSizeCounter++;
                  Update update = new Update().set(ProjectKeys.parentUniqueId, uniqueIdOfOrg);
                  bulkOperations.updateOne(new Query(Criteria.where("_id").is(nextProject.getId())), update);

                  if (batchSizeCounter == BATCH_SIZE) {
                    migratedCounter += bulkOperations.execute().getModifiedCount();
                    bulkOperations = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Project.class);
                    batchSizeCounter = 0;
                  }
                }
              }
            } catch (MappingInstantiationException | IllegalArgumentException exc) {
              log.debug(format("%s job for parentUniqueId migration on Entity: [%s], encountered non-supported "
                                + "typeAlias or wrong arguments, skipping entity document",
                            NG_MANAGER_ENTITIES_MIGRATION_LOG, Project.class.getSimpleName()),
                  exc);
              skippedCounter++;
            }
          }
          if (batchSizeCounter > 0) { // for the last remaining batch of projects
            migratedCounter += bulkOperations.execute().getModifiedCount();
          }
        } catch (Exception exc) {
          log.error(format("%s task failed to iterate over entities of Entity Type: [%s]",
                        NG_MANAGER_ENTITIES_MIGRATION_LOG, "Project"),
              exc);
          return;
        }
      } catch (Exception exc) {
        log.error(format("%s task failed for Entity Type [%s]", NG_MANAGER_ENTITIES_MIGRATION_LOG, "Project"), exc);
        return;
      }
      log.info(format("%s task on entity [%s] for parentId. Successful: [%d], Orphan: [%d], Skipped(Failed or Invalid "
              + "Entities): [%d]",
          NG_MANAGER_ENTITIES_MIGRATION_LOG, "Project", migratedCounter, orphanCounter, skippedCounter));
      foundEntity.setParentIdMigrationCompleted(TRUE);
      foundEntity.setOrphanEntityParentIdMigrationCompleted(TRUE);
      mongoTemplate.save(foundEntity);
    }
  }

  private void performEntityParentUniqueIdMigrationTask(NGManagerUniqueIdParentIdMigrationStatus foundEntity,
      final Class<? extends UniqueIdAware> clazz, final String orgIdentifierFieldName,
      final String projectIdentifierFieldName) {
    int migratedCounter = 0;
    int toUpdateCounter = 0;
    int batchSizeCounter = 0;
    int skippedCounter = 0;
    int orphanEntityCounter = 0;
    final String LOCAL_MAP_DELIMITER = "|";

    try (AcquiredLock<?> lock =
             persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(LOCK_NAME_PREFIX, Duration.ofSeconds(5))) {
      if (lock == null) {
        log.info(format("%s failed to acquire lock for Entity type: [%s] during parentId migration task",
            NG_MANAGER_ENTITIES_MIGRATION_LOG, clazz.getSimpleName()));
        return;
      }
      try {
        final Map<String, String> scopeEntityUniqueIdMap = new HashMap<>();
        String collectionName = mongoTemplate.getCollectionName(clazz);
        List<UpdateOneModel<Document>> bulkUpdates = new ArrayList<>();
        BulkWriteOptions options = new BulkWriteOptions().ordered(false);

        // Use raw MongoDB cursor to avoid deserialization
        try (MongoCursor<Document> cursor = mongoTemplate.getCollection(collectionName)
                                                .find()
                                                .limit(NO_LIMIT)
                                                .maxTime(MAX_VALUE, TimeUnit.MILLISECONDS)
                                                .noCursorTimeout(true)
                                                .iterator()) {
          // last document for debugging nested interface/abstract class fields or corrupted records that cannot be
          // cast.
          Document lastDoc = null;
          while (cursor.hasNext()) {
            try {
              Document doc = cursor.next();
              lastDoc = doc;
              // Check if document has parentUniqueId, uniqueId and account fields
              String parentUniqueId = doc.getString(PARENT_UNIQUE_ID_KEY);
              String account = doc.getString(NGCommonEntityConstants.ACCOUNT_KEY);
              if (StringUtils.isEmpty(account)) {
                account = doc.getString(NGCommonEntityConstants.ACCOUNT_ID);
              }
              if (account == null) {
                // we skip this as this is stale document
                continue;
              }
              String org = doc.getString(orgIdentifierFieldName);
              String proj = doc.getString(projectIdentifierFieldName);
              String mapKey = null;
              Boolean forceMigrate = foundEntity.getForceMigrateParentUniqueId();
              if (TRUE.equals(forceMigrate) || isEmpty(parentUniqueId)) {
                toUpdateCounter++;
                if (isNotEmpty(org) && isNotEmpty(proj)) {
                  mapKey = account + LOCAL_MAP_DELIMITER + org + LOCAL_MAP_DELIMITER + proj;
                } else if (isNotEmpty(org)) {
                  mapKey = account + LOCAL_MAP_DELIMITER + org;
                } else {
                  mapKey = account;
                }

                String scopeUniqueId = null;
                if (scopeEntityUniqueIdMap.containsKey(mapKey)) {
                  scopeUniqueId = scopeEntityUniqueIdMap.get(mapKey);
                } else {
                  Criteria entityCriteria = Criteria.where(NGCommonEntityConstants.ACCOUNT_KEY).is(account);
                  if (isNotEmpty(org) && isNotEmpty(proj)) {
                    entityCriteria.and(NGCommonEntityConstants.ORG_KEY)
                        .is(org)
                        .and(NGCommonEntityConstants.IDENTIFIER_KEY)
                        .is(proj);
                  } else if (isNotEmpty(org)) {
                    entityCriteria.and(NGCommonEntityConstants.IDENTIFIER_KEY).is(org);
                  }

                  if (isNotEmpty(org) && isNotEmpty(proj)) {
                    Project project = mongoTemplate.findOne(new Query(entityCriteria), Project.class);
                    if (null != project && isNotEmpty(project.getUniqueId())) {
                      scopeUniqueId = project.getUniqueId();
                    } else {
                      // orphan entities under PROJECT
                      scopeUniqueId = ORPHAN_ENTITY_PARENT_UNIQUE_ID_PREFIX + UUIDGenerator.generateUuid();
                      log.debug(format("%s For EntityType: [%s], and ParentType: %s with identifier: %s, parent not "
                              + "found or uniqueId on parent not present. Skipping...",
                          NG_MANAGER_ENTITIES_MIGRATION_LOG, clazz.getSimpleName(), "Project", proj));
                      orphanEntityCounter++;
                    }
                  } else if (isNotEmpty(org)) {
                    Organization organization = mongoTemplate.findOne(new Query(entityCriteria), Organization.class);
                    if (null != organization && isNotEmpty(organization.getUniqueId())) {
                      scopeUniqueId = organization.getUniqueId();
                    } else {
                      // orphan entities under ORGANIZATION
                      scopeUniqueId = ORPHAN_ENTITY_PARENT_UNIQUE_ID_PREFIX + UUIDGenerator.generateUuid();
                      log.debug(format("%s For EntityType: [%s], and ParentType: %s with identifier: %s, parent not "
                              + "found or uniqueId on parent not present. Skipping...",
                          NG_MANAGER_ENTITIES_MIGRATION_LOG, clazz.getSimpleName(), "Organization", org));
                      orphanEntityCounter++;
                    }
                  } else {
                    scopeUniqueId = account;
                  }
                }
                // If forceMigrate is enabled, check if we are really updating the field and if not reduce the counter
                // and ignore the update and continue to next document
                if (TRUE.equals(forceMigrate) && isNotEmpty(scopeUniqueId) && isNotEmpty(parentUniqueId)
                    && scopeUniqueId.equals(parentUniqueId)) {
                  toUpdateCounter--;
                  continue;
                }

                Object idObj = doc.get("_id");
                if (idObj != null && isNotEmpty(scopeUniqueId)) {
                  scopeEntityUniqueIdMap.put(mapKey, scopeUniqueId);
                  // Create query with the correct id type
                  BsonDocument update =
                      new BsonDocument("$set", new BsonDocument(PARENT_UNIQUE_ID_KEY, new BsonString(scopeUniqueId)));
                  // Dynamically create the filter based on the type of _id
                  BsonDocument filter;
                  if (idObj instanceof String) {
                    filter = new BsonDocument("_id", new BsonString((String) idObj)); // _id is a String
                  } else if (idObj instanceof ObjectId) {
                    filter = new BsonDocument("_id", new BsonObjectId((ObjectId) idObj)); // _id is an ObjectId
                  } else {
                    // If it's neither, we'll just skip it for now (you can add error handling here)
                    log.debug("Unexpected _id type, skipping document: " + doc.toJson());
                    skippedCounter++;
                    continue;
                  }

                  // non-scope entities update logic
                  batchSizeCounter++;
                  bulkUpdates.add(new UpdateOneModel<>(filter, update));
                  if (batchSizeCounter == BATCH_SIZE) {
                    // Execute bulk write operation
                    migratedCounter +=
                        mongoTemplate.getCollection(collectionName).bulkWrite(bulkUpdates, options).getModifiedCount();
                    bulkUpdates.clear(); // Clear the list for the next batch
                    Thread.sleep(SLEEP_DELAY_MS);
                    batchSizeCounter = 0;
                  }
                }
              }
            } catch (MappingInstantiationException | IllegalArgumentException exc) {
              log.error(format("%s job for parentUniqueId migration on Entity: [%s], encountered non-supported "
                                + "typeAlias or wrong arguments, skipping entity document: [%s]",
                            NG_MANAGER_ENTITIES_MIGRATION_LOG, clazz.getSimpleName(),
                            lastDoc != null ? lastDoc.toJson() : "null"),
                  exc);
              skippedCounter++;
            }
          }
          if (batchSizeCounter > 0) { // for the last remaining batch of entities
            migratedCounter +=
                mongoTemplate.getCollection(collectionName).bulkWrite(bulkUpdates, options).getModifiedCount();
          }
        } catch (Exception exc) {
          log.error(
              format("%s task failed to iterate over entities during parentUniqueId migration of Entity Type: [%s]",
                  NG_MANAGER_ENTITIES_MIGRATION_LOG, clazz.getSimpleName()),
              exc);
          return;
        }
      } catch (Exception exc) {
        log.error(format("%s task failed during parentUniqueId migration for Entity Type [%s]",
                      NG_MANAGER_ENTITIES_MIGRATION_LOG, clazz.getSimpleName()),
            exc);
        return;
      }

      if (toUpdateCounter == migratedCounter) {
        foundEntity.setParentIdMigrationCompleted(TRUE);
        foundEntity.setOrphanEntityParentIdMigrationCompleted(TRUE);
        foundEntity.setForceMigrateParentUniqueId(FALSE);
        log.info(format("%s job on entity [%s] for parentUniqueId Succeeded. Documents to Update: [%d], Successful: "
                + "[%d], Orphan: [%d], Skipped(Failed or Invalid Entities): [%d]",
            NG_MANAGER_ENTITIES_MIGRATION_LOG, clazz.getSimpleName(), toUpdateCounter, migratedCounter,
            orphanEntityCounter, skippedCounter));
      } else {
        log.warn(format("%s job failed on entity [%s] for parentUniqueId. Documents to Update: [%d], Successful: [%d], "
                + "Orphan: [%d], Skipped(Failed or Invalid Entities): [%d]",
            NG_MANAGER_ENTITIES_MIGRATION_LOG, clazz.getSimpleName(), toUpdateCounter, migratedCounter,
            orphanEntityCounter, skippedCounter));
      }
      mongoTemplate.save(foundEntity);
    }
  }

  private void performOrganizationParentUniqueIdMigrationTask(NGManagerUniqueIdParentIdMigrationStatus foundEntity) {
    int migratedCounter = 0;
    int updateCounter = 0;
    int batchSizeCounter = 0;

    try (AcquiredLock<?> lock =
             persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(LOCK_NAME_PREFIX, Duration.ofSeconds(5))) {
      if (lock == null) {
        log.info(format("%s failed to acquire lock for Entity type: [%s] during parentId migration task",
            NG_MANAGER_ENTITIES_MIGRATION_LOG, "Organization"));
        return;
      }
      try {
        Query documentQuery = new Query(new Criteria());
        BulkOperations bulkOperations = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Organization.class);
        String idValue = null;
        try (Stream<Organization> stream =
                 mongoTemplate.stream(documentQuery.limit(NO_LIMIT).maxTimeMsec(MAX_VALUE), Organization.class)) {
          Iterator<Organization> iterator = stream.iterator();
          while (iterator.hasNext()) {
            Organization nextOrg = iterator.next();
            if (null != nextOrg && isEmpty(nextOrg.getParentUniqueId())) {
              idValue = nextOrg.getId();
              updateCounter++;
              batchSizeCounter++;
              Update update = new Update().set(OrganizationKeys.parentUniqueId, nextOrg.getAccountIdentifier());
              bulkOperations.updateOne(new Query(Criteria.where("_id").is(idValue)), update);
              if (batchSizeCounter == BATCH_SIZE) {
                migratedCounter += bulkOperations.execute().getModifiedCount();
                bulkOperations = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Organization.class);
                batchSizeCounter = 0;
              }
            }
          }
          if (batchSizeCounter > 0) { // for the last remaining batch of entities
            migratedCounter += bulkOperations.execute().getModifiedCount();
          }
        } catch (Exception exc) {
          log.error(
              format("%s job failed for Entity Type [%s]", NG_MANAGER_ENTITIES_MIGRATION_LOG, "Organization"), exc);
          return;
        }
      } catch (Exception exc) {
        log.error(format("%s job failed for Entity Type [%s]", NG_MANAGER_ENTITIES_MIGRATION_LOG, "Organization"), exc);
        return;
      }
    }
    log.info(format("%s task on entity [%s] for parentId. Successful: [%d], Failed: [%d]",
        NG_MANAGER_ENTITIES_MIGRATION_LOG, "Organization", migratedCounter, updateCounter - migratedCounter));
    foundEntity.setParentIdMigrationCompleted(TRUE);
    foundEntity.setOrphanEntityParentIdMigrationCompleted(TRUE);
    mongoTemplate.save(foundEntity);
  }

  private void performNestedParentUniqueIdMigration(
      Map.Entry<Object, List<Map<String, String>>> entity, NGManagerUniqueIdParentIdMigrationStatus foundEntity) {
    String idValue = null;
    int updatedCounter = 0;
    int migratedCounter = 0;
    int failedCounter = 0;
    try {
      Query documentQuery = new Query(new Criteria());
      BulkOperations bulkOperations =
          mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, (Class<?>) entity.getKey());
      try (Stream<?> stream = mongoTemplate.stream(
               documentQuery.limit(MongoConfig.NO_LIMIT).maxTimeMsec(MAX_VALUE), (Class<?>) entity.getKey())) {
        Iterator<?> iterator = stream.iterator();
        while (iterator.hasNext()) {
          try {
            Object document = iterator.next();
            for (Map<String, String> mapping : entity.getValue()) {
              if (!isFieldSupported(document, mapping.get(ID_FIELD_NAME))
                  || !isFieldSupported(document, mapping.get(PARENT_UNIQUE_ID_FIELD_NAME))
                  || !isFieldSupported(document, mapping.get(ACCOUNT_IDENTIFIER_FIELD_NAME))
                  || !isFieldSupported(document, mapping.get(ORG_IDENTIFIER_FIELD_NAME))
                  || !isFieldSupported(document, mapping.get(PROJECT_IDENTIFIER_FIELD_NAME))) {
                continue;
              }
              idValue = (String) readNestedFieldValue(document, mapping.get(ID_FIELD_NAME));
              String parentUniqueId = (String) readNestedFieldValue(document, mapping.get(PARENT_UNIQUE_ID_FIELD_NAME));

              if (isEmpty(parentUniqueId)) {
                String accountIdentifier =
                    (String) readNestedFieldValue(document, mapping.get(ACCOUNT_IDENTIFIER_FIELD_NAME));
                String orgIdentifier = (String) readNestedFieldValue(document, mapping.get(ORG_IDENTIFIER_FIELD_NAME));
                String projectIdentifier =
                    (String) readNestedFieldValue(document, mapping.get(PROJECT_IDENTIFIER_FIELD_NAME));

                parentUniqueId = getScopeUniqueIdFor(accountIdentifier, orgIdentifier, projectIdentifier);

                Update update = new Update().set(mapping.get(PARENT_UNIQUE_ID_FIELD_NAME), parentUniqueId);
                bulkOperations.updateOne(new Query(Criteria.where("_id").is(new ObjectId(idValue))), update);
                updatedCounter++;
              }

              if (updatedCounter > BATCH_SIZE) {
                migratedCounter += bulkOperations.execute().getModifiedCount();
                bulkOperations = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, (Class<?>) entity.getKey());
                updatedCounter = 0;
              }
            }
          } catch (MappingInstantiationException | IllegalArgumentException exception) {
            log.info(format("%s ", NG_MANAGER_ENTITIES_MIGRATION_LOG), exception);
            failedCounter++;
          }
        }
        if (updatedCounter > 0) { // for the last remaining batch of entities
          migratedCounter += bulkOperations.execute().getModifiedCount();
        }
      } catch (Exception exception) {
        log.error(format("%s job for parentId on entity [%s] Failed. Documents to Update and Successful: [%d], "
                          + "Skipped(Failed or Invalid Entities): [%d]",
                      NG_MANAGER_ENTITIES_MIGRATION_LOG, entity.getKey(), migratedCounter, failedCounter),
            exception);
        return;
      }
    } catch (Exception exception) {
      log.error(format("%s job for parentId on entity [%s] Failed. Documents to Update and Successful: [%d], "
                        + "Skipped(Failed or Invalid Entities): [%d]",
                    NG_MANAGER_ENTITIES_MIGRATION_LOG, entity.getKey(), migratedCounter, failedCounter),
          exception);
      return;
    }

    if (failedCounter > 0) {
      foundEntity.setParentIdMigrationCompleted(FALSE);
      log.error(format("%s job for parentId on entity [%s] Failed. Documents to Update and Successful: [%d], "
              + "Skipped(Failed or Invalid Entities): [%d]",
          NG_MANAGER_ENTITIES_MIGRATION_LOG, entity.getKey(), migratedCounter, failedCounter));
    } else {
      foundEntity.setParentIdMigrationCompleted(TRUE);
      log.info(format("%s job for parentId on entity [%s] Succeeded. Documents to Update and Successful: [%d], "
              + "Skipped(Failed or Invalid Entities): [%d]",
          NG_MANAGER_ENTITIES_MIGRATION_LOG, entity.getKey(), migratedCounter, failedCounter));
    }
    mongoTemplate.save(foundEntity);
  }

  private String getScopeUniqueIdFor(String account, String org, String proj) {
    String mapKey = null;
    String LOCAL_MAP_DELIMITER = "|";
    if (isNotEmpty(org) && isNotEmpty(proj)) {
      mapKey = account + LOCAL_MAP_DELIMITER + org + LOCAL_MAP_DELIMITER + proj;
    } else if (isNotEmpty(org)) {
      mapKey = account + LOCAL_MAP_DELIMITER + org;
    } else {
      mapKey = account;
    }

    String scopeUniqueId;
    if (scopeEntityUniqueIdMap.containsKey(mapKey)) {
      scopeUniqueId = scopeEntityUniqueIdMap.get(mapKey);
    } else {
      Criteria entityCriteria = Criteria.where(NGCommonEntityConstants.ACCOUNT_KEY).is(account);
      if (isNotEmpty(org) && isNotEmpty(proj)) {
        entityCriteria.and(NGCommonEntityConstants.ORG_KEY)
            .is(org)
            .and(NGCommonEntityConstants.IDENTIFIER_KEY)
            .is(proj);
      } else if (isNotEmpty(org)) {
        entityCriteria.and(NGCommonEntityConstants.IDENTIFIER_KEY).is(org);
      }

      if (isNotEmpty(org) && isNotEmpty(proj)) {
        Project project = mongoTemplate.findOne(new Query(entityCriteria), Project.class);
        if (null != project && isNotEmpty(project.getUniqueId())) {
          scopeUniqueId = project.getUniqueId();
        } else {
          // orphan entities under PROJECT
          scopeUniqueId = ORPHAN_ENTITY_PARENT_UNIQUE_ID_PREFIX + UUIDGenerator.generateUuid();
        }
      } else if (isNotEmpty(org)) {
        Organization organization = mongoTemplate.findOne(new Query(entityCriteria), Organization.class);
        if (null != organization && isNotEmpty(organization.getUniqueId())) {
          scopeUniqueId = organization.getUniqueId();
        } else {
          // orphan entities under ORGANIZATION
          scopeUniqueId = ORPHAN_ENTITY_PARENT_UNIQUE_ID_PREFIX + UUIDGenerator.generateUuid();
        }
      } else {
        scopeUniqueId = account;
      }

      scopeEntityUniqueIdMap.put(mapKey, scopeUniqueId);
    }
    return scopeUniqueId;
  }

  private void performUserMembershipParentUniqueIdMigration(NGManagerUniqueIdParentIdMigrationStatus foundEntity) {
    int updatedCounter = 0;
    int migratedCounter = 0;
    int failedCounter = 0;
    try {
      Query documentQuery = new Query(new Criteria());
      BulkOperations bulkOperations = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, UserMembership.class);
      try (Stream<UserMembership> stream = mongoTemplate.stream(
               documentQuery.limit(MongoConfig.NO_LIMIT).maxTimeMsec(MAX_VALUE), UserMembership.class)) {
        Iterator<UserMembership> iterator = stream.iterator();
        while (iterator.hasNext()) {
          try {
            UserMembership userMembership = iterator.next();

            if (Objects.isNull(userMembership.getAccountIdentifier())
                || Objects.isNull(userMembership.getParentUniqueId())
                || Objects.isNull(userMembership.getScopeLevel())) {
              String accountIdentifier = userMembership.getScope().getAccountIdentifier();
              String orgIdentifier = userMembership.getScope().getOrgIdentifier();
              String projectIdentifier = userMembership.getScope().getProjectIdentifier();

              ScopeInfo scopeInfo = getScopeInfoFor(accountIdentifier, orgIdentifier, projectIdentifier);
              Update update = new Update()
                                  .set(UserMembershipKeys.accountIdentifier, scopeInfo.getAccountIdentifier())
                                  .set(UserMembershipKeys.parentUniqueId, scopeInfo.getUniqueId())
                                  .set(UserMembershipKeys.scopeLevel, scopeInfo.getScopeType());
              bulkOperations.updateOne(new Query(Criteria.where("_id").is(userMembership.getUuid())), update);
              updatedCounter++;
            }

            if (updatedCounter > BATCH_SIZE) {
              migratedCounter += bulkOperations.execute().getModifiedCount();
              bulkOperations = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, UserMembership.class);
              updatedCounter = 0;
            }
          } catch (MappingInstantiationException | IllegalArgumentException exception) {
            log.info(format("%s ", NG_MANAGER_ENTITIES_MIGRATION_LOG), exception);
            failedCounter++;
          }
        }
        if (updatedCounter > 0) { // for the last remaining batch of entities
          migratedCounter += bulkOperations.execute().getModifiedCount();
        }
      } catch (Exception exception) {
        log.error(
            format("%s job for parentId on entity [%s] Failed. Documents to Update and Successful: [%d], "
                    + "Skipped(Failed or Invalid Entities): [%d]",
                NG_MANAGER_ENTITIES_MIGRATION_LOG, UserMembership.class.getName(), migratedCounter, failedCounter),
            exception);
        return;
      }
    } catch (Exception exception) {
      log.error(format("%s job for parentId on entity [%s] Failed. Documents to Update and Successful: [%d], "
                        + "Skipped(Failed or Invalid Entities): [%d]",
                    NG_MANAGER_ENTITIES_MIGRATION_LOG, UserMembership.class.getName(), migratedCounter, failedCounter),
          exception);
      return;
    }

    if (failedCounter > 0) {
      foundEntity.setParentIdMigrationCompleted(FALSE);
      log.error(format("%s job for parentId on entity [%s] Failed. Documents to Update and Successful: [%d], "
              + "Skipped(Failed or Invalid Entities): [%d]",
          NG_MANAGER_ENTITIES_MIGRATION_LOG, UserMembership.class.getName(), migratedCounter, failedCounter));
    } else {
      foundEntity.setParentIdMigrationCompleted(TRUE);
      log.info(format("%s job for parentId on entity [%s] Succeeded. Documents to Update and Successful: [%d], "
              + "Skipped(Failed or Invalid Entities): [%d]",
          NG_MANAGER_ENTITIES_MIGRATION_LOG, UserMembership.class.getName(), migratedCounter, failedCounter));
    }
    mongoTemplate.save(foundEntity);
  }

  private void performOrphanUserMembershipParentUniqueIdMigration(
      NGManagerUniqueIdParentIdMigrationStatus foundEntity) {
    int updatedCounter = 0;
    int migratedCounter = 0;
    int failedCounter = 0;
    try {
      Query documentQuery = new Query(new Criteria());
      BulkOperations bulkOperations = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, UserMembership.class);
      try (Stream<UserMembership> stream = mongoTemplate.stream(
               documentQuery.limit(MongoConfig.NO_LIMIT).maxTimeMsec(MAX_VALUE), UserMembership.class)) {
        Iterator<UserMembership> iterator = stream.iterator();
        while (iterator.hasNext()) {
          try {
            UserMembership userMembership = iterator.next();

            if (Objects.isNull(userMembership.getAccountIdentifier())
                || Objects.isNull(userMembership.getParentUniqueId())
                || Objects.isNull(userMembership.getScopeLevel())) {
              String accountIdentifier = userMembership.getScope().getAccountIdentifier();
              String orgIdentifier = userMembership.getScope().getOrgIdentifier();
              String projectIdentifier = userMembership.getScope().getProjectIdentifier();

              ScopeInfo scopeInfo = getScopeInfoFor(accountIdentifier, orgIdentifier, projectIdentifier);
              Update update = new Update()
                                  .set(UserMembershipKeys.accountIdentifier, scopeInfo.getAccountIdentifier())
                                  .set(UserMembershipKeys.parentUniqueId, scopeInfo.getUniqueId())
                                  .set(UserMembershipKeys.scopeLevel, scopeInfo.getScopeType());
              bulkOperations.updateOne(new Query(Criteria.where("_id").is(userMembership.getUuid())), update);
              updatedCounter++;
            }

            if (updatedCounter > BATCH_SIZE) {
              migratedCounter += bulkOperations.execute().getModifiedCount();
              bulkOperations = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, UserMembership.class);
              updatedCounter = 0;
            }
          } catch (MappingInstantiationException | IllegalArgumentException exception) {
            log.info(format("%s ", NG_MANAGER_ENTITIES_MIGRATION_LOG), exception);
            failedCounter++;
          }
        }
        if (updatedCounter > 0) { // for the last remaining batch of entities
          migratedCounter += bulkOperations.execute().getModifiedCount();
        }
      } catch (Exception exception) {
        log.error(
            format("%s job for parentId on entity [%s] Failed. Documents to Update and Successful: [%d], "
                    + "Skipped(Failed or Invalid Entities): [%d]",
                NG_MANAGER_ENTITIES_MIGRATION_LOG, UserMembership.class.getName(), migratedCounter, failedCounter),
            exception);
        return;
      }
    } catch (Exception exception) {
      log.error(format("%s job for parentId on entity [%s] Failed. Documents to Update and Successful: [%d], "
                        + "Skipped(Failed or Invalid Entities): [%d]",
                    NG_MANAGER_ENTITIES_MIGRATION_LOG, UserMembership.class.getName(), migratedCounter, failedCounter),
          exception);
      return;
    }

    if (failedCounter > 0) {
      foundEntity.setOrphanEntityParentIdMigrationCompleted(FALSE);
      log.error(format("%s job for parentId on entity [%s] Failed. Documents to Update and Successful: [%d], "
              + "Skipped(Failed or Invalid Entities): [%d]",
          NG_MANAGER_ENTITIES_MIGRATION_LOG, UserMembership.class.getName(), migratedCounter, failedCounter));
    } else {
      foundEntity.setOrphanEntityParentIdMigrationCompleted(TRUE);
      log.info(format("%s job for parentId on entity [%s] Succeeded. Documents to Update and Successful: [%d], "
              + "Skipped(Failed or Invalid Entities): [%d]",
          NG_MANAGER_ENTITIES_MIGRATION_LOG, UserMembership.class.getName(), migratedCounter, failedCounter));
    }
    mongoTemplate.save(foundEntity);
  }

  private ScopeInfo getScopeInfoFor(String account, String org, String proj) {
    String mapKey = null;
    String LOCAL_MAP_DELIMITER = "|";
    if (isNotEmpty(org) && isNotEmpty(proj)) {
      mapKey = account + LOCAL_MAP_DELIMITER + org + LOCAL_MAP_DELIMITER + proj;
    } else if (isNotEmpty(org)) {
      mapKey = account + LOCAL_MAP_DELIMITER + org;
    } else {
      mapKey = account;
    }

    ScopeInfo scopeInfo = null;
    if (scopeInfoMap.containsKey(mapKey)) {
      scopeInfo = scopeInfoMap.get(mapKey);
    } else {
      Criteria entityCriteria = Criteria.where(NGCommonEntityConstants.ACCOUNT_KEY).is(account);
      if (isNotEmpty(org) && isNotEmpty(proj)) {
        entityCriteria.and(NGCommonEntityConstants.ORG_KEY)
            .is(org)
            .and(NGCommonEntityConstants.IDENTIFIER_KEY)
            .is(proj);
      } else if (isNotEmpty(org)) {
        entityCriteria.and(NGCommonEntityConstants.IDENTIFIER_KEY).is(org);
      }

      if (isNotEmpty(org) && isNotEmpty(proj)) {
        Project project = mongoTemplate.findOne(new Query(entityCriteria), Project.class);
        if (null != project && isNotEmpty(project.getUniqueId())) {
          scopeInfo = ScopeInfo.builder()
                          .accountIdentifier(account)
                          .orgIdentifier(org)
                          .projectIdentifier(proj)
                          .uniqueId(project.getUniqueId())
                          .scopeType(ScopeLevel.PROJECT)
                          .build();
        } else {
          // orphan entities under PROJECT
          scopeInfo = ScopeInfo.builder()
                          .accountIdentifier(account)
                          .orgIdentifier(org)
                          .projectIdentifier(proj)
                          .uniqueId(ORPHAN_ENTITY_PARENT_UNIQUE_ID_PREFIX + UUIDGenerator.generateUuid())
                          .scopeType(ScopeLevel.PROJECT)
                          .build();
        }
      } else if (isNotEmpty(org)) {
        Organization organization = mongoTemplate.findOne(new Query(entityCriteria), Organization.class);
        if (null != organization && isNotEmpty(organization.getUniqueId())) {
          scopeInfo = ScopeInfo.builder()
                          .accountIdentifier(account)
                          .orgIdentifier(org)
                          .uniqueId(organization.getUniqueId())
                          .scopeType(ScopeLevel.ORGANIZATION)
                          .build();
        } else {
          // orphan entities under ORGANIZATION
          scopeInfo = ScopeInfo.builder()
                          .accountIdentifier(account)
                          .orgIdentifier(org)
                          .uniqueId(ORPHAN_ENTITY_PARENT_UNIQUE_ID_PREFIX + UUIDGenerator.generateUuid())
                          .scopeType(ScopeLevel.ORGANIZATION)
                          .build();
        }
      } else {
        scopeInfo =
            ScopeInfo.builder().accountIdentifier(account).uniqueId(account).scopeType(ScopeLevel.ACCOUNT).build();
      }

      scopeInfoMap.put(mapKey, scopeInfo);
    }
    return scopeInfo;
  }

  private String getTypeAliasValueOrNameForClass(Class<? extends UniqueIdAware> clazz) {
    if (clazz.isAnnotationPresent(TypeAlias.class)) {
      TypeAlias annotation = clazz.getAnnotation(TypeAlias.class);
      return annotation.value();
    }
    return clazz.getName();
  }
}
