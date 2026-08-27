/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.gitxwebhook;

import io.harness.beans.ScopeInfo;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.common.dtos.UserSourceCodeManagerDTO;
import io.harness.gitsync.common.dtos.UserSourceCodeManagerResponseDTO;
import io.harness.gitsync.common.dtos.UserSourceCodeManagerResponseDTOList;
import io.harness.gitsync.common.mappers.UserSourceCodeManagerMapper;
import io.harness.gitsync.common.service.UserSourceCodeManagerService;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.userprofile.commons.SCMType;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

@Slf4j
abstract class AbstractUserSourceCodeManagerApiImpl {
  private final UserSourceCodeManagerService userSourceCodeManagerService;
  private final Map<SCMType, UserSourceCodeManagerMapper> scmMapBinder;
  private final ScopeInfoService scopeInfoService;

  AbstractUserSourceCodeManagerApiImpl(UserSourceCodeManagerService userSourceCodeManagerService,
      Map<SCMType, UserSourceCodeManagerMapper> scmMapBinder, ScopeInfoService scopeInfoService) {
    this.userSourceCodeManagerService = userSourceCodeManagerService;
    this.scmMapBinder = scmMapBinder;
    this.scopeInfoService = scopeInfoService;
  }

  Response getUserSourceCodeManagers(String harnessAccount, String orgIdentifier, String projectIdentifier,
      String userIdentifier, String connectorRef, String type) {
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(harnessAccount, orgIdentifier, projectIdentifier);
    SCMType scmType = parseScmType(type);
    List<UserSourceCodeManagerDTO> userSourceCodeManagerDTOs =
        userSourceCodeManagerService.getSourceCodeManagers(scopeInfo, userIdentifier, scmType, connectorRef, true);
    List<UserSourceCodeManagerResponseDTO> userSourceCodeManagerResponseDTOs = userSourceCodeManagerDTOs.stream()
                                                                                   .map(this::toResponseDTO)
                                                                                   .filter(Objects::nonNull)
                                                                                   .collect(Collectors.toList());
    UserSourceCodeManagerResponseDTOList responseData =
        UserSourceCodeManagerResponseDTOList.builder()
            .userSourceCodeManagerResponseDTOList(userSourceCodeManagerResponseDTOs)
            .build();
    return Response.ok(GitxUserSourceCodeManagerOpenApiMapper.toUserSourceCodeManagersApiResponse(responseData))
        .build();
  }

  private SCMType parseScmType(String type) {
    if (type == null) {
      return null;
    }
    try {
      return SCMType.valueOf(type);
    } catch (IllegalArgumentException ex) {
      throw new InvalidRequestException(String.format("Invalid SCM type [%s]", type), ex);
    }
  }

  private UserSourceCodeManagerResponseDTO toResponseDTO(UserSourceCodeManagerDTO scm) {
    UserSourceCodeManagerMapper mapper = scmMapBinder.get(scm.getType());
    if (mapper == null) {
      log.warn("No UserSourceCodeManagerMapper registered for SCM type [{}], skipping entry", scm.getType());
      return null;
    }
    return mapper.toResponseDTO(scm);
  }
}
