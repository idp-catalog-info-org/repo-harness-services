/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.mapper;

import io.harness.beans.Scope;
import io.harness.data.structure.EmptyPredicate;
import io.harness.ngtriggers.beans.dto.NGTriggerEventsApiResponse;
import io.harness.ngtriggers.beans.dto.NGTriggerEventsDTOResponse;
import io.harness.ngtriggers.beans.dto.NGTriggerMetaData;
import io.harness.ngtriggers.beans.entity.TriggerEventHistory;
import io.harness.ngtriggers.beans.response.TriggerEventResponse;
import io.harness.ngtriggers.beans.source.NGTriggerType;
import io.harness.ngtriggers.helpers.TriggerEventStatusHelper;

import org.apache.commons.lang3.EnumUtils;

public class NGTriggerEventsMapper {
  public static NGTriggerEventsDTOResponse toTriggerEventsDto(
      TriggerEventHistory triggerEventHistory, Scope scope, boolean isParentIdQueryingEnabled) {
    NGTriggerEventsDTOResponse ngTriggerEventsDTO =
        toNGTriggerEventsDto(triggerEventHistory, scope, isParentIdQueryingEnabled);
    if (NGTriggerType.ARTIFACT.equals(triggerEventHistory.getNgTriggerType())
        || NGTriggerType.MANIFEST.equals(triggerEventHistory.getNgTriggerType())) {
      if (EmptyPredicate.isNotEmpty(triggerEventHistory.getPollingDocId())
          && EmptyPredicate.isNotEmpty(triggerEventHistory.getBuild())) {
        ngTriggerEventsDTO.setNgTriggerMetaData(NGTriggerMetaData.builder()
                                                    .pollingDocumentId(triggerEventHistory.getPollingDocId())
                                                    .build(triggerEventHistory.getBuild())
                                                    .build());
      }
    }
    return ngTriggerEventsDTO;
  }

  public static NGTriggerEventsDTOResponse toNGTriggerEventsDto(
      TriggerEventHistory triggerEventHistory, Scope scope, boolean isParentIdQueryingEnabled) {
    return NGTriggerEventsDTOResponse.builder()
        .triggerIdentifier(triggerEventHistory.getTriggerIdentifier())
        .triggerName(triggerEventHistory.getTriggerName())
        .scope(Scope.builder()
                   .accountIdentifier(isParentIdQueryingEnabled && scope != null && scope.getAccountIdentifier() != null
                           ? scope.getAccountIdentifier()
                           : triggerEventHistory.getAccountId())
                   .orgIdentifier(isParentIdQueryingEnabled && scope != null && scope.getOrgIdentifier() != null
                           ? scope.getOrgIdentifier()
                           : triggerEventHistory.getOrgIdentifier())
                   .projectIdentifier(isParentIdQueryingEnabled && scope != null && scope.getProjectIdentifier() != null
                           ? scope.getProjectIdentifier()
                           : triggerEventHistory.getProjectIdentifier())
                   .build())
        .eventCorrelationId(triggerEventHistory.getEventCorrelationId())
        .eventCreatedAt(triggerEventHistory.getEventCreatedAt())
        .message(triggerEventHistory.getMessage())
        .triggerEventStatus(TriggerEventStatusHelper.toStatus(
            EnumUtils.getEnum(TriggerEventResponse.FinalStatus.class, triggerEventHistory.getFinalStatus())))
        .ngTriggerType(triggerEventHistory.getNgTriggerType())
        .triggerSubType(triggerEventHistory.getTriggerSubType())
        .build();
  }

  public static NGTriggerEventsApiResponse toNGTriggerApiResponse(
      NGTriggerEventsDTOResponse ngTriggerEventsDTOResponse) {
    return NGTriggerEventsApiResponse.builder()
        .triggerIdentifier(ngTriggerEventsDTOResponse.getTriggerIdentifier())
        .name(ngTriggerEventsDTOResponse.getTriggerName())
        .scope(ngTriggerEventsDTOResponse.getScope())
        .eventCorrelationId(ngTriggerEventsDTOResponse.getEventCorrelationId())
        .eventCreatedAt(ngTriggerEventsDTOResponse.getEventCreatedAt())
        .message(ngTriggerEventsDTOResponse.getMessage())
        .triggerEventStatus(ngTriggerEventsDTOResponse.getTriggerEventStatus())
        .ngTriggerType(ngTriggerEventsDTOResponse.getNgTriggerType())
        .subTriggerType(ngTriggerEventsDTOResponse.getTriggerSubType())
        .ngTriggerMetaData(ngTriggerEventsDTOResponse.getNgTriggerMetaData())
        .build();
  }
}
