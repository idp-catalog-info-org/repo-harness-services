/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.mapper;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.HeaderConfig;
import io.harness.beans.ScopeInfo;
import io.harness.ngtriggers.beans.dto.ArtifactTriggerEventInfo;
import io.harness.ngtriggers.beans.dto.ManifestTriggerEventInfo;
import io.harness.ngtriggers.beans.dto.NGTriggerEventHistoryDTO;
import io.harness.ngtriggers.beans.dto.NGTriggerEventHistoryDTO.NGTriggerEventHistoryDTOBuilder;
import io.harness.ngtriggers.beans.dto.PollingDocumentInfo;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.TriggerEventHistory;
import io.harness.ngtriggers.beans.response.TriggerEventResponse;
import io.harness.ngtriggers.beans.source.NGTriggerType;
import io.harness.ngtriggers.helpers.TriggerEventStatusHelper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.EnumUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@OwnedBy(PIPELINE)
@UtilityClass
@Slf4j
public class NGTriggerEventHistoryMapper {
  private static final String[] SENSITIVE_HEADERS = {"Authorization", "Proxy-Authorization", "X-Api-Key",
      "X-Amz-Security-Token", "X-Amz-Credential", "Set-Cookie", "Cookie"};

  private static final Pattern SENSITIVE_VALUE_PATTERN =
      Pattern.compile("(?i)^Bearer\\s+([a-zA-Z0-9-_]+\\.[a-zA-Z0-9-_]+\\.[a-zA-Z0-9-_]+)$" + // Matches Bearer JWT
          "|^([a-zA-Z0-9-_]+\\.[a-zA-Z0-9-_]+\\.[a-zA-Z0-9-_]+)$" + // Matches standalone JWT
          "|^([a-zA-Z0-9+/]{20,}={0,2})$" // Matches Base64-encoded strings
      );

  public NGTriggerEventHistoryDTO toTriggerEventHistoryDto(TriggerEventHistory triggerEventHistory,
      NGTriggerEntity triggerEntity, boolean shouldSendTriggerPayload, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled) {
    if (triggerEntity.getType().equals(NGTriggerType.ARTIFACT)
        || triggerEntity.getType().equals(NGTriggerType.MANIFEST)) {
      shouldSendTriggerPayload = true;
    }
    NGTriggerEventHistoryDTO ngTriggerEventHistoryDTO =
        toTriggerEventHistoryDto(triggerEventHistory, shouldSendTriggerPayload, scopeInfo, isParentIdQueryingEnabled);
    ngTriggerEventHistoryDTO.setType(triggerEntity.getType());
    if (ngTriggerEventHistoryDTO.getType().equals(NGTriggerType.ARTIFACT)) {
      ngTriggerEventHistoryDTO.setNgTriggerEventInfo(
          ArtifactTriggerEventInfo.builder()
              .build(triggerEventHistory.getBuild())
              .pollingDocumentInfo(
                  PollingDocumentInfo.builder().pollingDocumentId(triggerEventHistory.getPollingDocId()).build())
              .build());
    } else if (ngTriggerEventHistoryDTO.getType().equals(NGTriggerType.MANIFEST)) {
      ngTriggerEventHistoryDTO.setNgTriggerEventInfo(
          ManifestTriggerEventInfo.builder()
              .build(triggerEventHistory.getBuild())
              .pollingDocumentInfo(
                  PollingDocumentInfo.builder().pollingDocumentId(triggerEventHistory.getPollingDocId()).build())
              .build());
    }
    return ngTriggerEventHistoryDTO;
  }

  public NGTriggerEventHistoryDTO toTriggerEventHistoryDto(TriggerEventHistory triggerEventHistory,
      boolean shouldSendTriggerPayload, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    NGTriggerEventHistoryDTOBuilder ngTriggerEventHistoryDTOBuilder =
        NGTriggerEventHistoryDTO.builder()
            .triggerIdentifier(triggerEventHistory.getTriggerIdentifier())
            .accountId(
                isParentIdQueryingEnabled ? scopeInfo.getAccountIdentifier() : triggerEventHistory.getAccountId())
            .orgIdentifier(
                isParentIdQueryingEnabled ? scopeInfo.getOrgIdentifier() : triggerEventHistory.getOrgIdentifier())
            .projectIdentifier(isParentIdQueryingEnabled ? scopeInfo.getProjectIdentifier()
                                                         : triggerEventHistory.getProjectIdentifier())
            .targetIdentifier(triggerEventHistory.getTargetIdentifier())
            .eventCorrelationId(triggerEventHistory.getEventCorrelationId())
            .eventCreatedAt(triggerEventHistory.getEventCreatedAt())
            .finalStatus(
                EnumUtils.getEnum(TriggerEventResponse.FinalStatus.class, triggerEventHistory.getFinalStatus(), null))
            .triggerEventStatus(TriggerEventStatusHelper.toStatus(
                EnumUtils.getEnum(TriggerEventResponse.FinalStatus.class, triggerEventHistory.getFinalStatus(), null)))
            .message(triggerEventHistory.getMessage())
            .targetExecutionSummary(triggerEventHistory.getTargetExecutionSummary());
    if (shouldSendTriggerPayload) {
      ngTriggerEventHistoryDTOBuilder.payload(triggerEventHistory.getPayload());
      ngTriggerEventHistoryDTOBuilder.headers(populateHeaders(triggerEventHistory.getHeaders()));
    }
    return ngTriggerEventHistoryDTOBuilder.build();
  }

  public NGTriggerEventHistoryDTO toTriggerEventHistoryDto(
      TriggerEventHistory triggerEventHistory, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    NGTriggerEventHistoryDTOBuilder ngTriggerEventHistoryDTOBuilder =
        NGTriggerEventHistoryDTO.builder()
            .triggerIdentifier(triggerEventHistory.getTriggerIdentifier())
            .accountId(isParentIdQueryingEnabled && scopeInfo != null && scopeInfo.getAccountIdentifier() != null
                    ? scopeInfo.getAccountIdentifier()
                    : triggerEventHistory.getAccountId())
            .orgIdentifier(isParentIdQueryingEnabled && scopeInfo != null && scopeInfo.getOrgIdentifier() != null
                    ? scopeInfo.getOrgIdentifier()
                    : triggerEventHistory.getOrgIdentifier())
            .projectIdentifier(
                isParentIdQueryingEnabled && scopeInfo != null && scopeInfo.getProjectIdentifier() != null
                    ? scopeInfo.getProjectIdentifier()
                    : triggerEventHistory.getProjectIdentifier())
            .targetIdentifier(triggerEventHistory.getTargetIdentifier())
            .eventCorrelationId(triggerEventHistory.getEventCorrelationId())
            .payload(triggerEventHistory.getPayload())
            .eventCreatedAt(triggerEventHistory.getEventCreatedAt())
            .finalStatus(
                EnumUtils.getEnum(TriggerEventResponse.FinalStatus.class, triggerEventHistory.getFinalStatus(), null))
            .triggerEventStatus(TriggerEventStatusHelper.toStatus(
                EnumUtils.getEnum(TriggerEventResponse.FinalStatus.class, triggerEventHistory.getFinalStatus(), null)))
            .message(triggerEventHistory.getMessage())
            .targetExecutionSummary(triggerEventHistory.getTargetExecutionSummary());
    if (isCustomWebhookTrigger(triggerEventHistory)) {
      ngTriggerEventHistoryDTOBuilder.headers(populateHeaders(triggerEventHistory.getHeaders()));
    }
    return ngTriggerEventHistoryDTOBuilder.build();
  }

  private Map<String, String> populateHeaders(List<HeaderConfig> headerConfigs) {
    return Optional.ofNullable(headerConfigs)
        .map(headers
            -> headers.stream().collect(Collectors.toMap(HeaderConfig::getKey,
                header
                -> isSensitive(header.getKey(), header.getValues()) ? "****" : String.join(",", header.getValues()))))
        .orElse(null);
  }

  private boolean isSensitive(String key, List<String> values) {
    for (String sensitiveHeader : SENSITIVE_HEADERS) {
      if (sensitiveHeader.equalsIgnoreCase(key)) {
        return true;
      }
    }
    return values.stream().anyMatch(value -> SENSITIVE_VALUE_PATTERN.matcher(value).matches());
  }

  private boolean isCustomWebhookTrigger(TriggerEventHistory triggerEventHistory) {
    return triggerEventHistory.getNgTriggerType() != null
        && triggerEventHistory.getNgTriggerType().equals(NGTriggerType.WEBHOOK)
        && isNotEmpty(triggerEventHistory.getTriggerSubType())
        && triggerEventHistory.getTriggerSubType().equals("CUSTOM");
  }
}
