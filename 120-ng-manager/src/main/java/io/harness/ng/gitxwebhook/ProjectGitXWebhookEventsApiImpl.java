/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.gitxwebhook;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.Scope;
import io.harness.beans.ScopeInfo;
import io.harness.exception.InternalServerErrorException;
import io.harness.gitsync.gitxwebhooks.dtos.GitXEventsListRequestDTO;
import io.harness.gitsync.gitxwebhooks.dtos.GitXEventsListResponseDTO;
import io.harness.gitsync.gitxwebhooks.mapper.GitXWebhookMapper;
import io.harness.gitsync.gitxwebhooks.service.gitxwebhook.GitXWebhookEventService;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.spec.server.ng.v1.ProjectGitxWebhooksEventsApi;
import io.harness.spec.server.ng.v1.model.GitXWebhookEventResponse;
import io.harness.utils.ApiUtils;

import com.google.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;
import javax.validation.constraints.Max;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITX})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class ProjectGitXWebhookEventsApiImpl implements ProjectGitxWebhooksEventsApi {
  GitXWebhookEventService gitXWebhookEventService;
  ScopeInfoService scopeResolverService;

  @Override
  public Response listProjectGitxWebhookEvents(String org, String project, String harnessAccount, Integer page,
      @Max(1000L) Integer limit, String webhookIdentifier, Long eventStartTime, Long eventEndTime, String repoName,
      String filePath, String eventIdentifier, List<String> eventStatus, String connectorRef,
      Boolean includeParentScope, String commitId, String branch) {
    GitXEventsListRequestDTO gitXEventsListRequestDTO = GitXWebhookMapper.buildEventsListGitXWebhookRequestDTO(
        Scope.of(scopeResolverService.getScopeInfo(harnessAccount, org, project)), webhookIdentifier, eventStartTime,
        eventEndTime, repoName, filePath, eventIdentifier, eventStatus, connectorRef, includeParentScope, commitId,
        branch, page, limit);
    GitXEventsListResponseDTO gitXEventsListResponseDTO = gitXWebhookEventService.listEvents(gitXEventsListRequestDTO);

    List<GitXWebhookEventResponse> gitXWebhookEvents =
        GitXWebhookMapper.buildListGitXWebhookEventResponse(gitXEventsListResponseDTO);
    ResponseBuilder responseBuilder = Response.ok();
    ResponseBuilder responseBuilderWithLinks =
        ApiUtils.addLinksHeader(responseBuilder, gitXEventsListResponseDTO.getTotalEvents(), page, limit);
    return responseBuilderWithLinks
        .entity(gitXWebhookEvents.stream()
                    .map(GitXWebhookMapper::buildGitXWebhookEventResponse)
                    .collect(Collectors.toList()))
        .build();
  }

  public String getParentUniqueIdentifier(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    ScopeInfo scopeInfo = null;
    try {
      scopeInfo = scopeResolverService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);
    } catch (Exception ex) {
      log.error("Error occurred while fetching scopeInfo", ex);
      throw new InternalServerErrorException(
          "Exception occurred while fetching scope. Please contact harness customer care.", ex);
    }
    return scopeInfo.getUniqueId();
  }
}
