/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.gitintegration.repositories;

import static io.harness.outbox.OutboxSDKConstants.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.idp.events.producers.SetupUsageProducer;
import io.harness.idp.gitintegration.entities.CatalogConnectorEntity;
import io.harness.idp.gitintegration.events.catalogconnector.CatalogConnectorCreateEvent;
import io.harness.idp.gitintegration.events.catalogconnector.CatalogConnectorUpdateEvent;
import io.harness.idp.integrations.service.git.GitIntegrationServiceImpl;
import io.harness.outbox.api.OutboxService;
import io.harness.spec.server.idp.v1.model.BaseIntegrationRequest;
import io.harness.spec.server.idp.v1.model.GitIntegrationRequest;

import com.google.inject.name.Named;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
public class CatalogConnectorRepositoryCustomImpl implements CatalogConnectorRepositoryCustom {
  private MongoTemplate mongoTemplate;
  private SetupUsageProducer setupUsageProducer;
  private GitIntegrationServiceImpl gitIntegrationService;
  private TransactionTemplate transactionTemplate;
  private OutboxService outboxService;
  private static final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_RETRY_POLICY;

  CatalogConnectorRepositoryCustomImpl(MongoTemplate mongoTemplate, OutboxService outboxService,
      GitIntegrationServiceImpl gitIntegrationService, SetupUsageProducer setupUsageProducer,
      @Named(OUTBOX_TRANSACTION_TEMPLATE) TransactionTemplate transactionTemplate) {
    this.mongoTemplate = mongoTemplate;
    this.outboxService = outboxService;
    this.gitIntegrationService = gitIntegrationService;
    this.setupUsageProducer = setupUsageProducer;
    this.transactionTemplate = transactionTemplate;
  }

  @Override
  public CatalogConnectorEntity saveOrUpdate(CatalogConnectorEntity catalogConnectorEntity) {
    Criteria criteria = Criteria.where(CatalogConnectorEntity.CatalogConnectorKeys.accountIdentifier)
                            .is(catalogConnectorEntity.getAccountIdentifier())
                            .and(CatalogConnectorEntity.CatalogConnectorKeys.connectorProviderType)
                            .is(catalogConnectorEntity.getConnectorProviderType());
    CatalogConnectorEntity connector = findOneByAccountIdentifierAndProviderType(criteria);
    if (connector == null) {
      String accountIdentifier = catalogConnectorEntity.getAccountIdentifier();
      String connectorIdentifier = catalogConnectorEntity.getConnectorIdentifier();
      try {
        log.info(
            "Processing {}/{} catalog connector for migrate to integration", accountIdentifier, connectorIdentifier);
        gitIntegrationService.save(accountIdentifier, gitIntegrationRequest(connectorIdentifier), false, false);
      } catch (Exception ex) {
        log.error("Error in processing {}/{} catalog connector for migrate to integration", accountIdentifier,
            connectorIdentifier);
      }
      return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
        CatalogConnectorEntity savedCatalogConnectorEntity = mongoTemplate.save(catalogConnectorEntity);
        outboxService.save(
            new CatalogConnectorCreateEvent(catalogConnectorEntity.getAccountIdentifier(), catalogConnectorEntity));
        setupUsageProducer.publishConnectorSetupUsage(savedCatalogConnectorEntity.getAccountIdentifier(),
            savedCatalogConnectorEntity.getConnectorIdentifier(), savedCatalogConnectorEntity.getIdentifier());
        return savedCatalogConnectorEntity;
      }));
    }
    Query query = new Query(criteria);
    Update update = buildUpdateQuery(catalogConnectorEntity);
    FindAndModifyOptions options = new FindAndModifyOptions().returnNew(true);

    return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      String accountIdentifier = catalogConnectorEntity.getAccountIdentifier();
      String connectorIdentifier = catalogConnectorEntity.getConnectorIdentifier();
      try {
        log.info(
            "Processing {}/{} catalog connector for migrate to integration", accountIdentifier, connectorIdentifier);
        gitIntegrationService.update(accountIdentifier, catalogConnectorEntity.getIdentifier(),
            gitIntegrationRequest(connectorIdentifier), false);
      } catch (Exception ex) {
        log.error("Error in processing {}/{} catalog connector for migrate to integration", accountIdentifier,
            connectorIdentifier);
      }
      CatalogConnectorEntity updatedCatalogConnectorEntity =
          mongoTemplate.findAndModify(query, update, options, CatalogConnectorEntity.class);
      outboxService.save(new CatalogConnectorUpdateEvent(
          catalogConnectorEntity.getAccountIdentifier(), catalogConnectorEntity, connector));
      setupUsageProducer.deleteConnectorSetupUsage(connector.getAccountIdentifier(), connector.getIdentifier());
      setupUsageProducer.publishConnectorSetupUsage(updatedCatalogConnectorEntity.getAccountIdentifier(),
          updatedCatalogConnectorEntity.getConnectorIdentifier(), updatedCatalogConnectorEntity.getIdentifier());
      return updatedCatalogConnectorEntity;
    }));
  }

  @Override
  public CatalogConnectorEntity findLastUpdated(String accountIdentifier) {
    Query query =
        new Query(Criteria.where(CatalogConnectorEntity.CatalogConnectorKeys.accountIdentifier).is(accountIdentifier));
    query.with(Sort.by(Sort.Direction.DESC, CatalogConnectorEntity.CatalogConnectorKeys.lastUpdatedAt));
    query.limit(1);
    return mongoTemplate.findOne(query, CatalogConnectorEntity.class);
  }

  @Override
  public List<CatalogConnectorEntity> findAllHostsByAccountIdentifier(String accountIdentifier) {
    Criteria criteria =
        Criteria.where(CatalogConnectorEntity.CatalogConnectorKeys.accountIdentifier).is(accountIdentifier);
    Query query = new Query(criteria);
    query.fields().include(CatalogConnectorEntity.CatalogConnectorKeys.type);
    query.fields().include(CatalogConnectorEntity.CatalogConnectorKeys.host);
    query.fields().include(CatalogConnectorEntity.CatalogConnectorKeys.delegateSelectors);
    return mongoTemplate.find(query, CatalogConnectorEntity.class);
  }

  private CatalogConnectorEntity findOneByAccountIdentifierAndProviderType(Criteria criteria) {
    return mongoTemplate.findOne(Query.query(criteria), CatalogConnectorEntity.class);
  }

  private GitIntegrationRequest gitIntegrationRequest(String connectorIdentifier) {
    GitIntegrationRequest gitIntegrationRequest = new GitIntegrationRequest();
    gitIntegrationRequest.setType(BaseIntegrationRequest.TypeEnum.GIT);
    gitIntegrationRequest.setConnectorIdentifier(connectorIdentifier);
    return gitIntegrationRequest;
  }

  private Update buildUpdateQuery(CatalogConnectorEntity catalogConnectorEntity) {
    Update update = new Update();
    update.set(CatalogConnectorEntity.CatalogConnectorKeys.identifier, catalogConnectorEntity.getIdentifier());
    update.set(CatalogConnectorEntity.CatalogConnectorKeys.connectorIdentifier,
        catalogConnectorEntity.getConnectorIdentifier());
    update.set(CatalogConnectorEntity.CatalogConnectorKeys.type, catalogConnectorEntity.getType());
    update.set(CatalogConnectorEntity.CatalogConnectorKeys.lastUpdatedAt, System.currentTimeMillis());
    update.set(CatalogConnectorEntity.CatalogConnectorKeys.host, catalogConnectorEntity.getHost());
    update.set(
        CatalogConnectorEntity.CatalogConnectorKeys.delegateSelectors, catalogConnectorEntity.getDelegateSelectors());
    if (catalogConnectorEntity.getCatalogRepositoryDetails() != null) {
      update.set(CatalogConnectorEntity.CatalogConnectorKeys.catalogRepositoryDetails,
          catalogConnectorEntity.getCatalogRepositoryDetails());
    }
    return update;
  }
}
