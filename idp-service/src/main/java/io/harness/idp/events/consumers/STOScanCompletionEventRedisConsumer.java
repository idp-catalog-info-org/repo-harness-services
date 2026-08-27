/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.consumers;

import static io.harness.eventsframework.EventsFrameworkConstants.STO_SCAN_COMPLETION_EVENT;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CREATE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.STO_SCAN_INFO;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.consumer.Message;
import io.harness.idp.catalog.helpers.STOHelper;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.events.utils.ResourceLocker;
import io.harness.queue.QueueController;
import io.harness.sto.VulnerabilityScan;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.ByteString;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
public class STOScanCompletionEventRedisConsumer extends AbstractIdpServiceRedisStreamConsumer {
  private static final String CONSUMER_NAME = "STOScanCompletionEventRedisConsumer";
  @Inject private STOHelper stoHelper;
  @Inject IdpCommonService idpCommonService;

  @Inject
  public STOScanCompletionEventRedisConsumer(@Named(STO_SCAN_COMPLETION_EVENT) Consumer redisConsumer,
      QueueController queueController, ResourceLocker resourceLocker) {
    super(redisConsumer, queueController, resourceLocker);
  }

  @Override
  protected boolean processMessage(Message message) {
    log.info("Processing message with id: {} in {} consumer", message.getId(), CONSUMER_NAME);
    if (message.hasMessage()) {
      boolean entityTypeAndActionValidation;
      Map<String, String> metadataMap = message.getMessage().getMetadataMap();
      String entityType = metadataMap.get(EventsFrameworkMetadataConstants.ENTITY_TYPE);
      entityTypeAndActionValidation =
          entityTypeAndActionValidation(CONSUMER_NAME, message, STO_SCAN_INFO, List.of(CREATE_ACTION));
      if (entityTypeAndActionValidation) {
        try {
          ByteString data = message.getMessage().getData();
          VulnerabilityScan vulnerabilityScan = VulnerabilityScan.parseFrom(data.toByteArray());
          if (idpCommonService.idpStoEnabled(vulnerabilityScan.getScope().getAccountIdentifier())) {
            return lockAndProcessData(CONSUMER_NAME + "_EVENT_" + vulnerabilityScan.getScope().getAccountIdentifier()
                    + "_" + vulnerabilityScan.getScope().getOrgIdentifier() + "_"
                    + vulnerabilityScan.getScope().getProjectIdentifier() + "_" + vulnerabilityScan.getCreatedAt(),
                entityType, data);
          }
        } catch (Exception ex) {
          log.error("Error occurred in processing message of entityType {} and id {} in class:{} with message: {}",
              entityType, message.getId(), getClass().getSimpleName(), message, ex);
          return false;
        }
      }
      log.info("Processed messageId = {} in {} consumer", message.getId(), CONSUMER_NAME);
    }
    return true;
  }

  @Override
  protected void processInternal(String entityType, ByteString data) throws Exception {
    VulnerabilityScan vulnerabilityScan = VulnerabilityScan.parseFrom(data.toByteArray());
    processVulnerabilityScan(vulnerabilityScan);
  }

  void processVulnerabilityScan(VulnerabilityScan vulnerabilityScan) {
    stoHelper.processEvent(vulnerabilityScan);
  }
}
