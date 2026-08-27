/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event.entitycrud;

import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.SETTINGS;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.SETTINGS_CATEGORY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.UPDATE_ACTION;

import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.schemas.entity.ScopeInfo;
import io.harness.eventsframework.schemas.entity_crud.settings.SettingsEntityChangeDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.event.MessageListener;
import io.harness.ngsettings.SettingCategory;
import io.harness.ngtriggers.beans.source.NGTriggerType;
import io.harness.ngtriggers.mapper.TriggerFilterHelper;
import io.harness.ngtriggers.service.NGTriggerService;
import io.harness.telemetry.helpers.PlanConcurrencyInstrumentationHelper;

import com.google.inject.Inject;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Map;
import org.springframework.data.mongodb.core.query.Criteria;

public class PipelineSettingCRUDStreamListener implements MessageListener {
  private final NGTriggerService ngTriggerService;
  private final PlanConcurrencyInstrumentationHelper planConcurrencyInstrumentationHelper;
  public String MANIFEST_COLLECTION_NG_INTERVAL_MINUTES = "manifest_collection_ng_interval_minutes";
  public String ARTIFACT_COLLECTION_NG_INTERVAL_MINUTES = "artifact_collection_ng_interval_minutes";
  // Per-project concurrency settings (PIPE-35674) — a change to any of these is worth a telemetry event.
  private static final String PIPELINE_EXECUTION_CONCURRENCY_MODE = "pipeline_execution_concurrency_mode";
  private static final String DEFAULT_PROJECT_EXECUTION_CONCURRENCY = "default_project_execution_concurrency";
  private static final String PROJECT_EXECUTION_CONCURRENCY_LIMIT = "project_execution_concurrency_limit";
  private static final String[] CONCURRENCY_SETTING_IDENTIFIERS = {
      PIPELINE_EXECUTION_CONCURRENCY_MODE, DEFAULT_PROJECT_EXECUTION_CONCURRENCY, PROJECT_EXECUTION_CONCURRENCY_LIMIT};
  @Inject
  public PipelineSettingCRUDStreamListener(
      NGTriggerService ngTriggerService, PlanConcurrencyInstrumentationHelper planConcurrencyInstrumentationHelper) {
    this.ngTriggerService = ngTriggerService;
    this.planConcurrencyInstrumentationHelper = planConcurrencyInstrumentationHelper;
  }
  @Override
  public boolean handleMessage(Message message) {
    if (message != null && message.hasMessage()) {
      Map<String, String> metadataMap = message.getMessage().getMetadataMap();
      if (metadataMap != null && metadataMap.get(ENTITY_TYPE) != null && isPipelineSettingEvent(metadataMap)) {
        SettingsEntityChangeDTO settingsEntityChangeDTO = getSettingsEntityChangeDTO(message);
        String action = metadataMap.get(ACTION);
        if (action != null) {
          return processSettingsChangeEvent(settingsEntityChangeDTO, action);
        }
      }
    }
    return true;
  }

  private boolean isPipelineSettingEvent(final Map<String, String> metadataMap) {
    return metadataMap != null && SETTINGS.equals(metadataMap.get(ENTITY_TYPE))
        && SettingCategory.PMS.name().equals(metadataMap.get(SETTINGS_CATEGORY));
  }

  private boolean processSettingsChangeEvent(SettingsEntityChangeDTO settingsEntityChangeDTO, String action) {
    switch (action) {
      case UPDATE_ACTION:
        return processUpdateEvent(settingsEntityChangeDTO);
      default:
    }
    return true;
  }

  private boolean processUpdateEvent(SettingsEntityChangeDTO settingsEntityChangeDTO) {
    emitConcurrencyConfigChangeTelemetry(settingsEntityChangeDTO);
    return resetPollingInterval(settingsEntityChangeDTO);
  }

  // Fire a Segment/Amplitude telemetry event for each per-project concurrency setting that changed
  // (PIPE-35674). The change DTO only carries the new value, so we report identifier + new value +
  // scope. Best-effort: the helper swallows failures, and this must never break settings propagation.
  private void emitConcurrencyConfigChangeTelemetry(SettingsEntityChangeDTO settingsEntityChangeDTO) {
    Map<String, String> settingIdentifiersMap = settingsEntityChangeDTO.getSettingIdentifiersMap();
    if (settingIdentifiersMap == null || settingIdentifiersMap.isEmpty()) {
      return;
    }
    String accountId = settingsEntityChangeDTO.getAccountIdentifier().getValue();
    String orgId = settingsEntityChangeDTO.getOrgIdentifier().getValue();
    String projectId = settingsEntityChangeDTO.getProjectIdentifier().getValue();
    for (String identifier : CONCURRENCY_SETTING_IDENTIFIERS) {
      if (settingIdentifiersMap.containsKey(identifier)) {
        planConcurrencyInstrumentationHelper.sendConcurrencyConfigChangeEvent(
            accountId, orgId, projectId, identifier, settingIdentifiersMap.get(identifier));
      }
    }
  }

  private boolean resetPollingInterval(SettingsEntityChangeDTO settingsEntityChangeDTO) {
    String accountId = settingsEntityChangeDTO.getAccountIdentifier().getValue();
    String orgId = settingsEntityChangeDTO.getOrgIdentifier().getValue();
    String projectId = settingsEntityChangeDTO.getProjectIdentifier().getValue();
    ScopeInfo eventScopeInfo = settingsEntityChangeDTO.getScopeInfo();
    boolean isParentIdQueryingEnabled = true;
    Criteria criteria = null;
    if (settingsEntityChangeDTO.getSettingIdentifiersMap().containsKey(ARTIFACT_COLLECTION_NG_INTERVAL_MINUTES)) {
      criteria = TriggerFilterHelper.getCriteriaAccountIdAndOrgIdentifierAndProjectIdentifierTypeEnabled(accountId,
          orgId, projectId, NGTriggerType.ARTIFACT, eventScopeInfo.getUniqueId().getValue(), isParentIdQueryingEnabled);
    } else if (settingsEntityChangeDTO.getSettingIdentifiersMap().containsKey(
                   MANIFEST_COLLECTION_NG_INTERVAL_MINUTES)) {
      criteria = TriggerFilterHelper.getCriteriaAccountIdAndOrgIdentifierAndProjectIdentifierTypeEnabled(accountId,
          orgId, projectId, NGTriggerType.MANIFEST, eventScopeInfo.getUniqueId().getValue(), isParentIdQueryingEnabled);
    }
    if (criteria != null) {
      ngTriggerService.resetPollingTriggers(criteria, isParentIdQueryingEnabled, accountId);
    }
    return true;
  }

  private SettingsEntityChangeDTO getSettingsEntityChangeDTO(final Message message) {
    SettingsEntityChangeDTO settingsEntityChangeDTO;
    try {
      settingsEntityChangeDTO = SettingsEntityChangeDTO.parseFrom(message.getMessage().getData());
    } catch (final InvalidProtocolBufferException ex) {
      throw new InvalidRequestException(
          String.format("Exception in unpacking SettingsEntityChangeDTO for key %s", message.getId()), ex);
    }
    return settingsEntityChangeDTO;
  }
}
