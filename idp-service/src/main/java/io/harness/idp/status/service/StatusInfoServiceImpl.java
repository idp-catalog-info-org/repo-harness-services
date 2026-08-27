/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.status.service;

import static io.harness.idp.common.CommonUtils.getUserIdentifierFromUserPrincipal;
import static io.harness.idp.common.CommonUtils.getUserPrincipalFromPrincipal;
import static io.harness.telemetry.Category.GLOBAL;
import static io.harness.telemetry.Destination.ALL;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.status.beans.StatusInfoEntity;
import io.harness.idp.status.enums.StatusType;
import io.harness.idp.status.k8s.HealthCheck;
import io.harness.idp.status.mappers.StatusInfoMapper;
import io.harness.idp.status.repositories.StatusInfoRepository;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.security.dto.UserPrincipal;
import io.harness.spec.server.idp.v1.model.StatusInfo;
import io.harness.spec.server.idp.v1.model.StatusInfoV2;
import io.harness.telemetry.TelemetryOption;
import io.harness.telemetry.TelemetryReporter;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class StatusInfoServiceImpl implements StatusInfoService {
  private static final String IDP_ACCESSED_TRACK_EVENT = "IDP Accessed";

  private StatusInfoRepository statusInfoRepository;
  private HealthCheck healthCheck;
  private IdpCommonService idpCommonService;
  private TelemetryReporter telemetryReporter;

  @Override
  public Optional<StatusInfo> findByAccountIdentifierAndType(String accountIdentifier, String type) {
    if (StatusType.INFRA.toString().equalsIgnoreCase(type)) {
      return healthCheck.getCurrentStatus(accountIdentifier);
    }
    Optional<StatusInfoEntity> statusEntity =
        statusInfoRepository.findByAccountIdentifierAndType(accountIdentifier, type.toUpperCase());
    if (statusEntity.isEmpty()) {
      statusEntity =
          Optional.ofNullable(StatusInfoEntity.builder().status(StatusInfo.CurrentStatusEnum.NOT_FOUND).build());
    }
    return statusEntity.map(StatusInfoMapper::toDTO);
  }

  @Override
  public StatusInfoV2 findByAccountIdentifierAndTypeV2(String accountIdentifier, String type) {
    StatusInfoV2 statusInfoV2 = new StatusInfoV2();
    switch (StatusType.valueOf(type.toUpperCase())) {
      case INFRA:
        statusInfoV2.put(StatusType.INFRA.toString().toLowerCase(), getStatusInfoForInfra(accountIdentifier));
        break;
      case ONBOARDING:
      case GIT_INTEGRATION:
        statusInfoV2.put(type.toLowerCase(), getStatusInfoFromDB(accountIdentifier, type));
        break;
      case INFRA_ONBOARDING:
        statusInfoV2.put(StatusType.INFRA.toString().toLowerCase(), getStatusInfoForInfra(accountIdentifier));
        statusInfoV2.put(StatusType.ONBOARDING.toString().toLowerCase(),
            getStatusInfoFromDB(accountIdentifier, StatusType.ONBOARDING.toString().toUpperCase()));
        break;
      case ALL:
        statusInfoV2.putAll(getAllStatusInfo(accountIdentifier));
        break;
      default:
        return null;
    }
    publishIDPAccessedEventWithDetails(accountIdentifier);
    return statusInfoV2;
  }

  private StatusInfo getStatusInfoForInfra(String accountIdentifier) {
    return healthCheck.getCurrentStatus(accountIdentifier).get();
  }

  private StatusInfo getStatusInfoFromDB(String accountIdentifier, String type) {
    Optional<StatusInfoEntity> statusEntity =
        statusInfoRepository.findByAccountIdentifierAndType(accountIdentifier, type.toUpperCase());
    if (statusEntity.isEmpty()) {
      statusEntity =
          Optional.ofNullable(StatusInfoEntity.builder().status(StatusInfo.CurrentStatusEnum.NOT_FOUND).build());
    }
    return StatusInfoMapper.toDTO(statusEntity.get());
  }

  private StatusInfoV2 getAllStatusInfo(String accountIdentifier) {
    StatusInfoV2 statusInfoV2 = new StatusInfoV2();
    List<StatusInfoEntity> entities = statusInfoRepository.findByAccountIdentifier(accountIdentifier);
    entities.forEach(
        entity -> statusInfoV2.put(entity.getType().toString().toLowerCase(), StatusInfoMapper.toDTO(entity)));
    statusInfoV2.put(StatusType.INFRA.toString().toLowerCase(), getStatusInfoForInfra(accountIdentifier));
    return statusInfoV2;
  }

  @Override
  public StatusInfo save(StatusInfo statusInfo, String accountIdentifier, String type) {
    StatusInfoEntity statusInfoEntity = StatusInfoMapper.fromDTO(statusInfo, accountIdentifier, type);
    return StatusInfoMapper.toDTO(statusInfoRepository.saveOrUpdate(statusInfoEntity));
  }

  private void publishIDPAccessedEventWithDetails(String accountIdentifier) {
    try {
      UserPrincipal userPrincipal = getUserPrincipalFromPrincipal();
      String userIdentifier = getUserIdentifierFromUserPrincipal(userPrincipal);
      AccountDTO accountDTO = idpCommonService.getAccountDTO(accountIdentifier);
      HashMap<String, Object> properties = new HashMap<>();
      properties.put("groupId", accountIdentifier);
      properties.put("accountName", accountDTO.getName());
      if (userIdentifier != null) {
        properties.put("userEmail", userPrincipal.getEmail());
        telemetryReporter.sendTrackEvent(IDP_ACCESSED_TRACK_EVENT, userIdentifier, accountIdentifier, properties,
            Collections.singletonMap(ALL, true), GLOBAL, TelemetryOption.builder().sendForCommunity(false).build());
      } else {
        telemetryReporter.sendTrackEvent(IDP_ACCESSED_TRACK_EVENT, properties, Collections.singletonMap(ALL, true),
            GLOBAL, TelemetryOption.builder().sendForCommunity(false).build());
      }
    } catch (Exception ex) {
      log.error("Error in publishing IDP accessed event with details for accountIdentifier = {} Error = {}",
          accountIdentifier, ex.getMessage(), ex);
    }
  }
}
