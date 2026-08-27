/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.observer.entity;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.schemas.entity.ScopeInfo;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.events.delete.PipelineDeleteEvent;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.observer.PipelineActionObserver;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.StringValue;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(PIPELINE)
public class PipelineEntityCrudObserver implements PipelineActionObserver {
  @Inject @Named(EventsFrameworkConstants.ENTITY_CRUD) private Producer eventProducer;

  @Override
  public void onDelete(PipelineDeleteEvent pipelineDeleteEvent) {
    PipelineEntity pipelineEntity = pipelineDeleteEvent.getPipeline();
    EntityChangeDTO.Builder pipelineEntityChangeDTOBuilder =
        EntityChangeDTO.newBuilder()
            .setAccountIdentifier(StringValue.of(pipelineEntity.getAccountId()))
            .setOrgIdentifier(StringValue.of(pipelineEntity.getOrgIdentifier()))
            .setProjectIdentifier(StringValue.of(pipelineEntity.getProjectIdentifier()))
            .setIdentifier(StringValue.of(pipelineEntity.getIdentifier()));

    if (pipelineEntity.getParentUniqueId() != null) {
      pipelineEntityChangeDTOBuilder.setUniqueId(StringValue.of(pipelineEntity.getUniqueId()))
          .setScopeInfo(ScopeInfo.newBuilder().setUniqueId(StringValue.of(pipelineEntity.getParentUniqueId())).build());

    } else {
      log.error(
          "ParentUniqueId is null while creating pipelineEntityChangeDTOBuilder for {} in account: {} org: {} project: {}",
          pipelineEntity.getIdentifier(), pipelineEntity.getAccountId(), pipelineEntity.getOrgIdentifier(),
          pipelineEntity.getProjectIdentifier());
    }

    try {
      eventProducer.send(
          Message.newBuilder()
              .putAllMetadata(ImmutableMap.of("accountId", pipelineEntity.getAccountId(),
                  EventsFrameworkMetadataConstants.ENTITY_TYPE, EventsFrameworkMetadataConstants.PIPELINE_ENTITY,
                  EventsFrameworkMetadataConstants.ACTION, EventsFrameworkMetadataConstants.DELETE_ACTION))
              .setData(pipelineEntityChangeDTOBuilder.build().toByteString())
              .build());
    } catch (EventsFrameworkDownException ex) {
      throw new InvalidRequestException("Redis Producer shutdown unexpectedly", ex);
    }
  }
}
