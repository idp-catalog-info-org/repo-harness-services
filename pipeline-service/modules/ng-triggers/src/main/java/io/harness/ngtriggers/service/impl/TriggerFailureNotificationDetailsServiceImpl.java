/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.service.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.beans.ScopeInfo;
import io.harness.ngtriggers.beans.dto.TriggerNotificationData;
import io.harness.ngtriggers.beans.entity.TriggerFailureNotificationDetailsEntity;
import io.harness.ngtriggers.beans.entity.TriggerFailureNotificationDetailsEntity.TriggerFailureNotificationDetailsEntityBuilder;
import io.harness.ngtriggers.service.TriggerFailureNotificationDetailsService;
import io.harness.repositories.spring.TriggerFailureNotificationDetailsRepository;
import io.harness.utils.ScopeResolutionHelper;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.Optional;

public class TriggerFailureNotificationDetailsServiceImpl implements TriggerFailureNotificationDetailsService {
  @Inject private TriggerFailureNotificationDetailsRepository triggerFailureNotificationDetailsRepository;
  @Inject private ScopeResolutionHelper scopeResolutionHelper;

  @Override
  public void saveRecord(TriggerNotificationData triggerNotificationData) {
    TriggerFailureNotificationDetailsEntity triggerFailureNotificationDetailsEntity =
        mapTriggerNotificationDataToEntity(triggerNotificationData);
    validateUniqueIdAndParentUniqueId(triggerFailureNotificationDetailsEntity);
    TriggerFailureNotificationDetailsEntity persistedTriggerFailureNotificationDetailsEntity =
        triggerFailureNotificationDetailsRepository.save(triggerFailureNotificationDetailsEntity);
    triggerNotificationData.setTriggerFailureNotificationEntityUuid(
        persistedTriggerFailureNotificationDetailsEntity.getUuid());
  }

  @Override
  public TriggerNotificationData findById(String uuid) {
    Optional<TriggerFailureNotificationDetailsEntity> optionalTriggerFailureNotificationDetailsEntity =
        triggerFailureNotificationDetailsRepository.findById(uuid);
    if (optionalTriggerFailureNotificationDetailsEntity.isEmpty()) {
      return null;
    }
    TriggerFailureNotificationDetailsEntity triggerFailureNotificationDetailsEntity =
        optionalTriggerFailureNotificationDetailsEntity.get();
    return mapEntityToTriggerNotificationData(triggerFailureNotificationDetailsEntity);
  }

  private TriggerFailureNotificationDetailsEntity mapTriggerNotificationDataToEntity(
      TriggerNotificationData triggerNotificationData) {
    TriggerFailureNotificationDetailsEntityBuilder triggerFailureNotificationDetailsEntityBuilder =
        TriggerFailureNotificationDetailsEntity.builder()
            .accountId(triggerNotificationData.getAccountIdentifier())
            .orgIdentifier(triggerNotificationData.getOrgIdentifier())
            .projectIdentifier(triggerNotificationData.getProjectIdentifier())
            .triggerIdentifier(triggerNotificationData.getTriggerIdentifier())
            .triggerName(triggerNotificationData.getTriggerName())
            .pipelineIdentifier(triggerNotificationData.getPipelineIdentifier())
            .pipelineName(triggerNotificationData.getPipelineName())
            .errorMessage(triggerNotificationData.getErrorMessage())
            .eventCreatedAt(triggerNotificationData.getTriggerEventCreatedAt())
            .headerConfigs(triggerNotificationData.getHeaderConfigs())
            .triggerPayload(triggerNotificationData.getTriggerPayload())
            .payload(triggerNotificationData.getPayload())
            .eventCorrelationId(triggerNotificationData.getEventCorrelationId())
            .triggerPayload(triggerNotificationData.getTriggerPayload())
            .ngTriggerType(triggerNotificationData.getNgTriggerType())
            .triggerSubType(triggerNotificationData.getTriggerSubType());

    if (isEmpty(triggerNotificationData.getHeaderConfigs())) {
      triggerFailureNotificationDetailsEntityBuilder.headerConfigs(Collections.emptyList());
    }

    if (isEmpty(triggerNotificationData.getPayload())) {
      triggerFailureNotificationDetailsEntityBuilder.payload("{}");
    }
    return triggerFailureNotificationDetailsEntityBuilder.build();
  }

  private TriggerNotificationData mapEntityToTriggerNotificationData(
      TriggerFailureNotificationDetailsEntity triggerFailureNotificationDetailsEntity) {
    return TriggerNotificationData.builder()
        .accountIdentifier(triggerFailureNotificationDetailsEntity.getAccountId())
        .orgIdentifier(triggerFailureNotificationDetailsEntity.getOrgIdentifier())
        .projectIdentifier(triggerFailureNotificationDetailsEntity.getProjectIdentifier())
        .triggerIdentifier(triggerFailureNotificationDetailsEntity.getTriggerIdentifier())
        .triggerName(triggerFailureNotificationDetailsEntity.getTriggerName())
        .pipelineIdentifier(triggerFailureNotificationDetailsEntity.getPipelineIdentifier())
        .pipelineName(triggerFailureNotificationDetailsEntity.getPipelineName())
        .errorMessage(triggerFailureNotificationDetailsEntity.getErrorMessage())
        .triggerEventCreatedAt(triggerFailureNotificationDetailsEntity.getEventCreatedAt())
        .headerConfigs(triggerFailureNotificationDetailsEntity.getHeaderConfigs())
        .triggerPayload(triggerFailureNotificationDetailsEntity.getTriggerPayload())
        .payload(triggerFailureNotificationDetailsEntity.getPayload())
        .eventCorrelationId(triggerFailureNotificationDetailsEntity.getEventCorrelationId())
        .triggerPayload(triggerFailureNotificationDetailsEntity.getTriggerPayload())
        .headerConfigs(triggerFailureNotificationDetailsEntity.getHeaderConfigs())
        .payload(triggerFailureNotificationDetailsEntity.getPayload())
        .triggerFailureNotificationEntityUuid(triggerFailureNotificationDetailsEntity.getUuid())
        .ngTriggerType(triggerFailureNotificationDetailsEntity.getNgTriggerType())
        .triggerSubType(triggerFailureNotificationDetailsEntity.getTriggerSubType())
        .build();
  }

  private void validateUniqueIdAndParentUniqueId(
      TriggerFailureNotificationDetailsEntity triggerFailureNotificationDetailsEntity) {
    if (isEmpty(triggerFailureNotificationDetailsEntity.getUniqueId())) {
      triggerFailureNotificationDetailsEntity.setUniqueId(generateUuid());
    }
    if (isEmpty(triggerFailureNotificationDetailsEntity.getParentUniqueId())) {
      Optional<ScopeInfo> scopeInfo =
          scopeResolutionHelper.getScopeInfoOptional(triggerFailureNotificationDetailsEntity.getAccountId(),
              triggerFailureNotificationDetailsEntity.getOrgIdentifier(),
              triggerFailureNotificationDetailsEntity.getProjectIdentifier());
      String parentUniqueId = null;
      if (scopeInfo.isPresent()) {
        parentUniqueId = scopeInfo.get().getUniqueId();
      }
      triggerFailureNotificationDetailsEntity.setParentUniqueId(parentUniqueId);
    }
  }
}
