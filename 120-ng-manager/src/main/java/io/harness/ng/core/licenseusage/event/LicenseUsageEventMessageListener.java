/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.licenseusage.event;

import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.beans.FeatureName;
import io.harness.eventsframework.NgEventLogContext;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.schemas.platform.LicenseUsageEvent;
import io.harness.logging.AutoLogContext;
import io.harness.ng.core.event.MessageListener;
import io.harness.ng.core.licenseusage.dto.LicenseUsageDTO;
import io.harness.ng.core.licenseusage.mapper.LicenseUsageProtoToRestDTOMapper;
import io.harness.ng.core.licenseusage.services.LicenseUsageService;
import io.harness.ng.core.licenseusage.utils.LicenseUsageMetricHelper;
import io.harness.utils.NGFeatureFlagHelperService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class LicenseUsageEventMessageListener implements MessageListener {
  @Inject LicenseUsageProtoToRestDTOMapper licenseUsageProtoToRestDTOMapper;
  @Inject private LicenseUsageService licenseUsageService;
  @Inject private NGFeatureFlagHelperService ngFeatureFlagHelperService;
  @Inject private LicenseUsageMetricHelper metricHelper;
  private static final String LICENSE_USAGE_EVENT_PROCESSING_FAILURE = "license_usage_event_processing_failure";

  @Override
  public boolean handleMessage(Message message) {
    String messageId = message.getId();
    String accountIdentifier = null;
    try (AutoLogContext ignore1 = new NgEventLogContext(messageId, OVERRIDE_ERROR)) {
      if (message.hasMessage()) {
        LicenseUsageEvent licenseUsageEventProtoDTO = getLicenseUsageEvent(message);
        Set<String> featureFlagEnabledAccountIds = ngFeatureFlagHelperService.getFeatureFlagEnabledAccountIds(
            FeatureName.PL_ENABLE_LICENSE_USAGE_COMPUTE.name());
        // check if the FF is enabled for the incoming account's event and process if so, otherwise skip
        LicenseUsageDTO licenseUsageDTO = licenseUsageProtoToRestDTOMapper.toRestDTO(licenseUsageEventProtoDTO);
        accountIdentifier = licenseUsageDTO.getAccountIdentifier();
        if (featureFlagEnabledAccountIds.contains(accountIdentifier)) {
          processLicenseUsageEvent(licenseUsageDTO);
          return true;
        } else {
          log.debug(
              "Skipping! {} is not enabled for AccountIdentifier={} found with PL_ENABLE_LICENSE_USAGE_COMPUTE enabled",
              FeatureName.PL_ENABLE_LICENSE_USAGE_COMPUTE.name(), accountIdentifier);
        }
      }
    } catch (Exception ex) {
      log.error("Error processing the license_usage event with the id {} for accountIdentifier={}", messageId,
          accountIdentifier, ex);
      metricHelper.recordMetricForAccount(LICENSE_USAGE_EVENT_PROCESSING_FAILURE, 1, accountIdentifier);
    }
    return false;
  }

  private LicenseUsageEvent getLicenseUsageEvent(Message licenseUsageEventMessage) {
    LicenseUsageEvent licenseUsageEvent = null;
    try {
      licenseUsageEvent = LicenseUsageEvent.parseFrom(licenseUsageEventMessage.getMessage().getData());
    } catch (InvalidProtocolBufferException e) {
      log.error("Exception in unpacking LicenseUsageEvent for key {}", licenseUsageEventMessage.getId(), e);
      throw new RuntimeException(e);
    }
    return licenseUsageEvent;
  }

  private void processLicenseUsageEvent(LicenseUsageDTO licenseUsageDTO) {
    licenseUsageService.save(licenseUsageDTO);
  }
}
