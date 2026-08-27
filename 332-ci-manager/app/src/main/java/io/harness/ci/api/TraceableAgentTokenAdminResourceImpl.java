/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.api;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ci.traceableagenttoken.CITraceableAgentTokenService;
import io.harness.cimanager.traceableagenttoken.api.TraceableAgentTokenAdminResource;
import io.harness.cimanager.traceableagenttoken.api.TraceableAgentTokenRequestDTO;
import io.harness.cimanager.traceableagenttoken.api.TraceableAgentTokenResponseDTO;
import io.harness.eraro.ErrorCode;
import io.harness.exception.InvalidRequestException;
import io.harness.rest.RestResponse;

import software.wings.security.annotations.AdminPortalAuth;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@AdminPortalAuth
@Slf4j
@OwnedBy(CI)
public class TraceableAgentTokenAdminResourceImpl implements TraceableAgentTokenAdminResource {
  @Inject CITraceableAgentTokenService traceableAgentTokenService;

  @Override
  public RestResponse<Boolean> createOrUpdateTraceableAgentToken(@AccountIdentifier String accountIdentifier,
      String orgIdentifier, String projectIdentifier, TraceableAgentTokenRequestDTO request) {
    traceableAgentTokenService.createOrUpdate(
        accountIdentifier, orgIdentifier, projectIdentifier, request.getAgentToken());
    return new RestResponse<>(true);
  }

  @Override
  public RestResponse<TraceableAgentTokenResponseDTO> getTraceableAgentToken(
      @AccountIdentifier String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    String token = traceableAgentTokenService.get(accountIdentifier, orgIdentifier, projectIdentifier);
    if (token == null) {
      throw new InvalidRequestException("No Traceable agent token found for account: " + accountIdentifier
              + ", org: " + orgIdentifier + ", project: " + projectIdentifier,
          ErrorCode.RESOURCE_NOT_FOUND, null);
    }
    return new RestResponse<>(TraceableAgentTokenResponseDTO.builder().agentToken(token).build());
  }

  @Override
  public RestResponse<Boolean> deleteTraceableAgentToken(
      @AccountIdentifier String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return new RestResponse<>(traceableAgentTokenService.delete(accountIdentifier, orgIdentifier, projectIdentifier));
  }
}
