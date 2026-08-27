/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import static io.harness.mongo.iterator.pojos.SchedulingType.REGULAR;

import static java.time.Duration.ofSeconds;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.authorization.AuthorizationServiceHeader;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.idp.catalog.beans.GetEntitiesDTO;
import io.harness.idp.catalog.helpers.IDPGitXHelper;
import io.harness.idp.catalog.service.CatalogService;
import io.harness.idp.iterators.entity.IteratorEntity;
import io.harness.idp.namespace.beans.entity.NamespaceEntity;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.filter.SpringFilterExpander;
import io.harness.mongo.iterator.pojos.IteratorConfig;
import io.harness.mongo.iterator.provider.SpringPersistenceProvider;
import io.harness.persistence.HPersistence;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.ServicePrincipal;
import io.harness.spec.server.idp.v1.model.EntityResponse;
import io.harness.spec.server.idp.v1.model.EntityUpdateRequest;
import io.harness.spec.server.idp.v1.model.GitDetails;
import io.harness.spec.server.idp.v1.model.GitUpdateDetails;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import java.util.List;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

@AllArgsConstructor(onConstructor = @__({ @com.google.inject.Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = HarnessModuleComponent.IDP_SERVICE)
public class ModifyWorkflowFormContextDataForIdpV2Handler implements MongoPersistenceIterator.Handler<IteratorEntity> {
  private PersistenceIteratorFactory persistenceIteratorFactory;
  private HPersistence persistence;
  private MongoTemplate mongoTemplate;
  private NamespaceService namespaceService;
  private CatalogService catalogService;
  private IDPGitXHelper idpGitXHelper;
  private static final String MODIFY_WORKFLOW_FORM_CONTEXT_DATA_FOR_IDP_V2_HANDLER =
      "ModifyWorkflowFormContextDataForIdpV2Handler";

  @Override
  public void handle(IteratorEntity entity) {
    final DBCollection collection = persistence.getCollection(NamespaceEntity.class);
    BasicDBObject queryOps =
        new BasicDBObject("isDeleted", false)
            .append(
                "metadata.migrateCatalogEntitiesFromBackstageToHarnessCompleted", new BasicDBObject("$exists", true))
            .append("metadata.migrateCatalogEntitiesFromBackstageToHarnessCompleted", true)
            .append("metadata.postgresIdpV2MigrationCompleted", new BasicDBObject("$exists", true))
            .append("metadata.postgresIdpV2MigrationCompleted", true)
            .append("metadata.idpV2MigrationInfo", new BasicDBObject("$exists", true))
            .append("metadata.idpV2MigrationInfo.migrateDefaultToAccountNamespaceInBackstageCompleted", true)
            .append("metadata.idpV2MigrationInfo.migrateDefaultToAccountNamespaceInDependentsCompleted", true)
            .append("metadata.idpV2MigrationInfo.migrateWorkflowFormContextDataCompleted", false)
            .append("metadata.idpV2MigrationInfo.migrateWorkflowFormContextDataFrom",
                new BasicDBObject("$lte", System.currentTimeMillis()));
    try (DBCursor records = collection.find(queryOps).limit(5)) {
      while (records.hasNext()) {
        DBObject record = records.next();
        NamespaceEntity namespaceEntity = persistence.convertToEntity(NamespaceEntity.class, record);
        String accountIdentifier = namespaceEntity.getAccountIdentifier();

        long start = System.currentTimeMillis();
        log.info("Starting the migration for modifying workflow form context data for account - {}", accountIdentifier);
        try {
          SecurityContextBuilder.setContext(
              new ServicePrincipal(AuthorizationServiceHeader.IDP_SERVICE.getServiceId()));

          GetEntitiesDTO getEntitiesDTO = catalogService.getEntities(accountIdentifier, 0, -1, null, null, false,
              "account.*", null, false, false, "workflow", null, null, null, null, null, false);
          SourcePrincipalContextBuilder.setSourcePrincipal(
              new ServicePrincipal(AuthorizationServiceHeader.IDP_SERVICE.getServiceId()));
          List<EntityResponse> entityResponses = getEntitiesDTO.getEntityResponses();
          entityResponses.forEach(entityResponse -> {
            String yaml = entityResponse.getYaml();
            if (yaml.contains("getContextData: '{{ formContext")) {
              yaml = yaml.replace("getContextData: '{{ formContext", "getContextData: '${{ formContext");
              EntityUpdateRequest entityUpdateRequest = new EntityUpdateRequest();
              entityUpdateRequest.setYaml(yaml);
              if (entityResponse.getGitDetails() != null) {
                EntityResponse getEntityResponse =
                    catalogService.getEntity(accountIdentifier, entityResponse.getOrgIdentifier(),
                        entityResponse.getProjectIdentifier(), entityResponse.getEntityRef(), false, true, false);
                GitDetails gitDetails = getEntityResponse.getGitDetails();
                GitUpdateDetails gitUpdateDetails = new GitUpdateDetails();
                gitUpdateDetails.setStoreType(GitUpdateDetails.StoreTypeEnum.valueOf(gitDetails.getStoreType().name()));
                gitUpdateDetails.setRepoName(gitDetails.getRepoName());
                gitUpdateDetails.setLastObjectId(gitDetails.getObjectId());
                gitUpdateDetails.setLastCommitId(gitDetails.getCommitId());
                gitUpdateDetails.setIsHarnessCodeRepo(gitDetails.isIsHarnessCodeRepo());
                gitUpdateDetails.setFilePath(gitDetails.getFilePath());
                gitUpdateDetails.setConnectorRef(gitDetails.getConnectorRef());
                gitUpdateDetails.setCommitMessage(gitDetails.getCommitMessage());
                gitUpdateDetails.setBranchName(gitDetails.getBranchName());
                gitUpdateDetails.setBaseBranch(gitDetails.getBaseBranch());
                entityUpdateRequest.setGitDetails(gitUpdateDetails);
                GitAwareContextHelper.populateGitDetails(
                    idpGitXHelper.populateGitUpdateDetails(entityUpdateRequest.getGitDetails()));
              }
              catalogService.updateEntity(accountIdentifier, entityResponse.getOrgIdentifier(),
                  entityResponse.getProjectIdentifier(), entityResponse.getEntityRef(), entityUpdateRequest, false,
                  true, false, false);
              GitAwareContextHelper.populateGitDetails(GitEntityInfo.builder().build());
            }
          });
          updateIdpV2MigrationInfo(namespaceEntity);
          log.info(
              "Total time taken to complete the migration modifying workflow form context data for account {} - {}",
              accountIdentifier, System.currentTimeMillis() - start);
        } catch (Exception e) {
          log.error("Error occurred during the migration for modifying workflow form context for account {}",
              accountIdentifier, e);
        }
      }
    }
  }

  public void registerIterators(IteratorConfig iteratorConfig) {
    persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(
        PersistenceIteratorFactory.PumpExecutorOptions.builder()
            .name(MODIFY_WORKFLOW_FORM_CONTEXT_DATA_FOR_IDP_V2_HANDLER)
            .poolSize(iteratorConfig.getThreadPoolCount())
            .interval(ofSeconds(30))
            .build(),
        ModifyWorkflowFormContextDataForIdpV2Handler.class,
        MongoPersistenceIterator.<IteratorEntity, SpringFilterExpander>builder()
            .clazz(IteratorEntity.class)
            .filterExpander(query
                -> query.addCriteria(
                    where(IteratorEntity.IteratorsKeys.name).is(MODIFY_WORKFLOW_FORM_CONTEXT_DATA_FOR_IDP_V2_HANDLER)))
            .fieldName(IteratorEntity.IteratorsKeys.nextIteration)
            .targetInterval(ofSeconds(iteratorConfig.getTargetIntervalInSeconds()))
            .acceptableExecutionTime(ofSeconds(480))
            .acceptableNoAlertDelay(ofSeconds(120))
            .handler(this)
            .schedulingType(REGULAR)
            .persistenceProvider(new SpringPersistenceProvider<>(mongoTemplate))
            .redistribute(true));
  }

  private void updateIdpV2MigrationInfo(NamespaceEntity namespaceEntity) {
    NamespaceEntity.Metadata metadata = Objects.isNull(namespaceEntity.getMetadata())
        ? NamespaceEntity.Metadata.builder().build()
        : namespaceEntity.getMetadata();
    NamespaceEntity.Metadata.IdpV2MigrationInfo idpV2MigrationInfo =
        Objects.isNull(namespaceEntity.getMetadata().getIdpV2MigrationInfo())
        ? NamespaceEntity.Metadata.IdpV2MigrationInfo.builder().build()
        : namespaceEntity.getMetadata().getIdpV2MigrationInfo();
    if (idpV2MigrationInfo != null) {
      idpV2MigrationInfo.setMigrateWorkflowFormContextDataCompleted(true);
      metadata.setIdpV2MigrationInfo(idpV2MigrationInfo);
      namespaceEntity.setMetadata(metadata);
      namespaceService.save(namespaceEntity);
    }
  }
}
