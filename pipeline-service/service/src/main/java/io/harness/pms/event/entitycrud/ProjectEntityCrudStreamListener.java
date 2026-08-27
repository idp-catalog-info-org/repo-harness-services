/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event.entitycrud;

import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DISABLE_TRIGGERS;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.PROJECT_ENTITY;

import static org.apache.commons.lang3.StringUtils.stripToNull;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.project.ProjectEntityChangeDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.filter.service.FilterService;
import io.harness.ng.core.event.MessageListener;
import io.harness.ngtriggers.service.NGTriggerService;
import io.harness.pms.pipeline.service.intfc.PMSPipelineService;
import io.harness.utils.PmsFeatureFlagService;

import com.google.inject.Inject;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Map;

@OwnedBy(HarnessTeam.PIPELINE)
public class ProjectEntityCrudStreamListener implements MessageListener {
  private final PMSPipelineService pmsPipelineService;
  private final NGTriggerService ngTriggerService;
  private final FilterService filterService;
  private final PmsFeatureFlagService pmsFeatureFlagService;

  @Inject
  public ProjectEntityCrudStreamListener(PMSPipelineService pmsPipelineService, NGTriggerService ngTriggerService,
      FilterService filterService, PmsFeatureFlagService pmsFeatureFlagService) {
    this.pmsPipelineService = pmsPipelineService;
    this.ngTriggerService = ngTriggerService;
    this.filterService = filterService;
    this.pmsFeatureFlagService = pmsFeatureFlagService;
  }

  @Override
  public boolean handleMessage(Message message) {
    if (message != null && message.hasMessage()) {
      Map<String, String> metadataMap = message.getMessage().getMetadataMap();
      if (metadataMap != null && metadataMap.get(ENTITY_TYPE) != null
          && PROJECT_ENTITY.equals(metadataMap.get(ENTITY_TYPE))) {
        ProjectEntityChangeDTO entityChangeDTO;
        try {
          entityChangeDTO = ProjectEntityChangeDTO.parseFrom(message.getMessage().getData());
        } catch (InvalidProtocolBufferException e) {
          throw new InvalidRequestException(
              String.format("Exception in unpacking EntityChangeDTO for key %s", message.getId()), e);
        }
        String action = metadataMap.get(ACTION);
        if (action != null) {
          return processProjectEntityChangeEvent(entityChangeDTO, action);
        }
      }
    }
    return true;
  }

  private boolean processProjectEntityChangeEvent(ProjectEntityChangeDTO entityChangeDTO, String action) {
    if (DELETE_ACTION.equals(action)) {
      processDeleteEvent(entityChangeDTO);
    } else if (DISABLE_TRIGGERS.equals(action)) {
      processTriggerDisableEvent(entityChangeDTO);
    }
    return true;
  }

  private void processTriggerDisableEvent(ProjectEntityChangeDTO entityChangeDTO) {
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(stripToNull(entityChangeDTO.getAccountIdentifier()))
                              .orgIdentifier(stripToNull(entityChangeDTO.getOrgIdentifier()))
                              .projectIdentifier(stripToNull(entityChangeDTO.getIdentifier()))
                              .uniqueId(stripToNull(entityChangeDTO.getUniqueId()))
                              .scopeType(ScopeLevel.PROJECT)
                              .build();
    ngTriggerService.toggleTriggers(false, entityChangeDTO.getAccountIdentifier(), entityChangeDTO.getOrgIdentifier(),
        entityChangeDTO.getIdentifier(), null, null, scopeInfo);
  }

  private void processDeleteEvent(ProjectEntityChangeDTO entityChangeDTO) {
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(stripToNull(entityChangeDTO.getAccountIdentifier()))
                              .orgIdentifier(stripToNull(entityChangeDTO.getOrgIdentifier()))
                              .projectIdentifier(stripToNull(entityChangeDTO.getIdentifier()))
                              .uniqueId(stripToNull(entityChangeDTO.getUniqueId()))
                              .scopeType(ScopeLevel.PROJECT)
                              .build();

    pmsPipelineService.deleteAllPipelinesInAProject(entityChangeDTO.getAccountIdentifier(),
        entityChangeDTO.getOrgIdentifier(), entityChangeDTO.getIdentifier(), scopeInfo);

    if (pmsFeatureFlagService.isEnabled(entityChangeDTO.getAccountIdentifier(),
            FeatureName.PIPE_SUPPORT_FILTER_DELETION_ON_ORG_OR_PROJECT_DELETION)) {
      filterService.deleteByScope(scopeInfo);
    }
  }
}
