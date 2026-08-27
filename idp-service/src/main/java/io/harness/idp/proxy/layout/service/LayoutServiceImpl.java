/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.idp.proxy.layout.service;

import static io.harness.idp.common.CommonUtils.getUserFromEmbeddedUser;
import static io.harness.outbox.OutboxSDKConstants.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.remote.client.NGRestUtils.getGeneralResponse;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.clients.BackstageResourceClient;
import io.harness.idp.proxy.layout.beans.entity.LayoutEntity;
import io.harness.idp.proxy.layout.events.LayoutCreateEvent;
import io.harness.idp.proxy.layout.events.LayoutDeleteEvent;
import io.harness.idp.proxy.layout.events.LayoutUpdateEvent;
import io.harness.idp.proxy.layout.mappers.LayoutMapper;
import io.harness.idp.proxy.layout.repositories.LayoutRepository;
import io.harness.outbox.api.OutboxService;
import io.harness.spec.server.idp.v1.model.LayoutRequest;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import javax.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@Slf4j
public class LayoutServiceImpl implements LayoutService {
  private LayoutRepository layoutsRepository;
  private BackstageResourceClient backstageResourceClient;
  private TransactionTemplate transactionTemplate;
  private OutboxService outboxService;
  private static final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_RETRY_POLICY;

  @Inject
  private LayoutServiceImpl(LayoutRepository layoutsRepository, BackstageResourceClient backstageResourceClient,
      @Named(OUTBOX_TRANSACTION_TEMPLATE) TransactionTemplate transactionTemplate, OutboxService outboxService) {
    this.layoutsRepository = layoutsRepository;
    this.backstageResourceClient = backstageResourceClient;
    this.transactionTemplate = transactionTemplate;
    this.outboxService = outboxService;
  }

  @Override
  public void saveOrUpdateLayouts(LayoutRequest layoutRequest, String accountIdentifier) {
    Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      LayoutEntity existingLayoutEntity = layoutsRepository.findByAccountIdentifierAndNameAndType(
          accountIdentifier, layoutRequest.getName(), layoutRequest.getType());

      LayoutRequest existingLayoutDTO = new LayoutRequest();
      LayoutEntity toSaveLayoutEntity = LayoutMapper.fromDTO(layoutRequest, accountIdentifier);

      if (existingLayoutEntity != null) {
        existingLayoutDTO = LayoutMapper.toDTO(existingLayoutEntity);
        toSaveLayoutEntity.setId(existingLayoutEntity.getId());
        toSaveLayoutEntity.setCreatedAt(existingLayoutEntity.getCreatedAt());
        toSaveLayoutEntity.setCreatedBy(existingLayoutEntity.getCreatedBy());
      }

      LayoutEntity layout = layoutsRepository.save(toSaveLayoutEntity);
      layoutRequest.setCreatedBy(getUserFromEmbeddedUser(layout.getCreatedBy()));
      layoutRequest.setUpdatedBy(getUserFromEmbeddedUser(layout.getLastUpdatedBy()));

      if (existingLayoutEntity == null) {
        outboxService.save(new LayoutCreateEvent(layoutRequest, accountIdentifier));
      } else {
        if (!existingLayoutEntity.getYaml().equals(layoutRequest.getYaml())) {
          outboxService.save(new LayoutUpdateEvent(layoutRequest, existingLayoutDTO, accountIdentifier));
        }
      }
      return true;
    }));
  }

  @Override
  public Object deleteLayoutAndSaveAuditEvent(LayoutRequest layoutRequest, String accountIdentifier) {
    LayoutEntity existingLayoutEntity = layoutsRepository.findByAccountIdentifierAndNameAndType(
        accountIdentifier, layoutRequest.getName(), layoutRequest.getType());

    LayoutRequest existingLayoutDTO;

    if (existingLayoutEntity != null) {
      existingLayoutDTO = LayoutMapper.toDTO(existingLayoutEntity);
    } else {
      log.error(String.format("Layout with name {%s} and type {%s} not found in account: {%s}", layoutRequest.getName(),
          layoutRequest.getType(), accountIdentifier));
      throw new NotFoundException(String.format(
          "Layout with name {%s} and type {%s} not found", layoutRequest.getName(), layoutRequest.getType()));
    }

    final LayoutRequest existingLayoutDTOWrapper = existingLayoutDTO;
    return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      outboxService.save(new LayoutDeleteEvent(existingLayoutDTOWrapper, accountIdentifier));
      layoutsRepository.delete(existingLayoutEntity);
      return getGeneralResponse(backstageResourceClient.deleteLayout(layoutRequest, accountIdentifier));
    }));
  }
}
