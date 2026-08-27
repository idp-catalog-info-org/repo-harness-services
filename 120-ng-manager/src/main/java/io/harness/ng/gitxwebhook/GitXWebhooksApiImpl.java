/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.gitxwebhook;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.Scope;
import io.harness.gitsync.gitxwebhooks.dtos.CreateGitXWebhookResponseDTO;
import io.harness.gitsync.gitxwebhooks.dtos.DeleteGitXWebhookResponseDTO;
import io.harness.gitsync.gitxwebhooks.dtos.GetGitXWebhookResponseDTO;
import io.harness.gitsync.gitxwebhooks.dtos.GitXEventsListRequestDTO;
import io.harness.gitsync.gitxwebhooks.dtos.GitXEventsListResponseDTO;
import io.harness.gitsync.gitxwebhooks.dtos.ListGitXWebhookResponseDTO;
import io.harness.gitsync.gitxwebhooks.dtos.UpdateGitXWebhookResponseDTO;
import io.harness.gitsync.gitxwebhooks.gitbackedentities.RepoSyncStatusListResponseDTO;
import io.harness.gitsync.gitxwebhooks.gitbackedentities.UntrackedFilePathsPageDTO;
import io.harness.gitsync.gitxwebhooks.mapper.GitXWebhookMapper;
import io.harness.gitsync.gitxwebhooks.service.gitxwebhook.GitXWebhookEventService;
import io.harness.grpc.utils.FlowName;
import io.harness.grpc.utils.GrpcContextMetadataDto;
import io.harness.grpc.utils.GrpcContextMetadataHelper;
import io.harness.spec.server.ng.v1.GitXWebhooksApi;
import io.harness.spec.server.ng.v1.model.CreateGitXWebhookRequest;
import io.harness.spec.server.ng.v1.model.CreateGitXWebhookResponse;
import io.harness.spec.server.ng.v1.model.DeleteGitXWebhookResponse;
import io.harness.spec.server.ng.v1.model.GitXRateLimitProvider;
import io.harness.spec.server.ng.v1.model.GitXRateLimitWindow;
import io.harness.spec.server.ng.v1.model.GitXWebhookEventResponse;
import io.harness.spec.server.ng.v1.model.GitXWebhookResponse;
import io.harness.spec.server.ng.v1.model.ListGitXWebhookStatusResponseDTO;
import io.harness.spec.server.ng.v1.model.UntrackedFilePath;
import io.harness.spec.server.ng.v1.model.UpdateGitXWebhookRequest;
import io.harness.spec.server.ng.v1.model.UpdateGitXWebhookResponse;
import io.harness.utils.ApiUtils;

import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.NotNull;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITX})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class GitXWebhooksApiImpl implements GitXWebhooksApi {
  GitXWebhookEventService gitXWebhookEventService;
  GitXWebhooksApiHelper gitXWebhooksApiHelper;
  GitXWebhookHealthApiHelper gitXWebhookHealthApiHelper;
  GitXRateLimitApiHelper gitXRateLimitApiHelper;
  public static final int HTTP_201 = 201;
  public static final int HTTP_404 = 404;
  public static final int HTTP_204 = 204;
  public static final String NG_MANAGER = "ng-manager";

  @Override
  public Response createGitxWebhook(@Valid CreateGitXWebhookRequest body, @AccountIdentifier String harnessAccount) {
    GrpcContextMetadataHelper.addMetadata(
        GrpcContextMetadataDto.builder().flowName(FlowName.GITX_WEBHOOK_CREATE).callerName(NG_MANAGER).build());
    CreateGitXWebhookResponseDTO createGitXWebhookResponseDTO =
        gitXWebhooksApiHelper.createGitXWebhook(harnessAccount, null, null, body);
    CreateGitXWebhookResponse responseBody =
        GitXWebhookMapper.buildCreateGitXWebhookResponse(createGitXWebhookResponseDTO);
    return Response.status(HTTP_201).entity(responseBody).build();
  }

  @Override
  public Response getGitxWebhook(String gitXWebhookIdentifier, @AccountIdentifier String harnessAccount) {
    GrpcContextMetadataHelper.addMetadata(
        GrpcContextMetadataDto.builder().flowName(FlowName.GITX_WEBHOOK_GET).callerName(NG_MANAGER).build());
    Optional<GetGitXWebhookResponseDTO> optionalGetGitXWebhookResponseDTO =
        gitXWebhooksApiHelper.getGitXWebhook(harnessAccount, null, null, gitXWebhookIdentifier);
    if (optionalGetGitXWebhookResponseDTO.isEmpty()) {
      return Response.status(HTTP_404).build();
    }
    GitXWebhookResponse responseBody =
        GitXWebhookMapper.buildGetGitXWebhookResponseDTO(optionalGetGitXWebhookResponseDTO.get());
    return Response.ok().entity(responseBody).build();
  }

  @Override
  public Response updateGitxWebhook(
      String gitXWebhookIdentifier, @Valid UpdateGitXWebhookRequest body, @AccountIdentifier String harnessAccount) {
    GrpcContextMetadataHelper.addMetadata(
        GrpcContextMetadataDto.builder().flowName(FlowName.GITX_WEBHOOK_UPDATE).callerName(NG_MANAGER).build());
    UpdateGitXWebhookResponseDTO updateGitXWebhookResponseDTO =
        gitXWebhooksApiHelper.updateGitXWebhook(harnessAccount, null, null, gitXWebhookIdentifier, body);
    UpdateGitXWebhookResponse responseBody =
        GitXWebhookMapper.buildUpdateGitXWebhookResponse(updateGitXWebhookResponseDTO);
    return Response.ok().entity(responseBody).build();
  }

  @Override
  public Response deleteGitxWebhook(String gitXWebhookIdentifier, @AccountIdentifier String harnessAccount) {
    GrpcContextMetadataHelper.addMetadata(
        GrpcContextMetadataDto.builder().flowName(FlowName.GITX_WEBHOOK_DELETE).callerName(NG_MANAGER).build());
    DeleteGitXWebhookResponseDTO deleteGitXWebhookResponse =
        gitXWebhooksApiHelper.deleteGitXWebhook(harnessAccount, null, null, gitXWebhookIdentifier);
    DeleteGitXWebhookResponse responseBody =
        GitXWebhookMapper.buildDeleteGitXWebhookResponse(deleteGitXWebhookResponse);
    return Response.status(HTTP_204).entity(responseBody).build();
  }

  @Override
  public Response listGitxWebhooks(
      @AccountIdentifier String harnessAccount, Integer page, @Max(1000L) Integer limit, String webhookIdentifier) {
    ListGitXWebhookResponseDTO listGitXWebhookResponseDTO =
        gitXWebhooksApiHelper.listGitXWebhooks(harnessAccount, null, null, webhookIdentifier);
    Page<GitXWebhookResponse> gitXWebhooks =
        GitXWebhookMapper.buildListGitXWebhookResponse(listGitXWebhookResponseDTO, page, limit);

    ResponseBuilder responseBuilder = Response.ok();
    ResponseBuilder responseBuilderWithLinks =
        ApiUtils.addLinksHeader(responseBuilder, gitXWebhooks.getTotalElements(), page, limit);
    return responseBuilderWithLinks
        .entity(gitXWebhooks.getContent()
                    .stream()
                    .map(GitXWebhookMapper::buildGetGitXWebhookResponseDTO)
                    .collect(Collectors.toList()))
        .build();
  }

  @Override
  public Response listWebhookStatusPerRepo(
      @NotNull String entityType, String harnessAccount, Integer page, @Max(1000L) Integer limit, String repoName) {
    int effectivePage = page == null ? 0 : page;
    int effectiveLimit = limit == null ? 20 : limit;
    RepoSyncStatusListResponseDTO serviceResponse = gitXWebhookHealthApiHelper.listWebhookStatusPerRepo(
        harnessAccount, repoName, entityType, effectivePage, effectiveLimit);
    ListGitXWebhookStatusResponseDTO responseBody = GitXWebhookHealthApiHelper.mapToApiResponse(serviceResponse);
    ResponseBuilder responseBuilder = Response.ok();
    ResponseBuilder responseBuilderWithLinks =
        ApiUtils.addLinksHeader(responseBuilder, serviceResponse.getTotalRepos(), effectivePage, effectiveLimit);
    return responseBuilderWithLinks.entity(responseBody).build();
  }

  @Override
  public Response getGitxHealthForRepo(@NotNull String repoName, @NotNull String entityType, String harnessAccount,
      Integer page, @Max(1000L) Integer limit) {
    int effectivePage = page == null ? 0 : page;
    int effectiveLimit = limit == null ? 20 : limit;
    UntrackedFilePathsPageDTO pageDto = gitXWebhookHealthApiHelper.listUntrackedFilePaths(
        harnessAccount, repoName, entityType, effectivePage, effectiveLimit);
    List<UntrackedFilePath> entity = GitXWebhookHealthApiHelper.toApiUntrackedFilePaths(pageDto);
    ResponseBuilder responseBuilder = Response.ok();
    ResponseBuilder responseBuilderWithLinks =
        ApiUtils.addLinksHeader(responseBuilder, pageDto.getTotalElements(), effectivePage, effectiveLimit);
    return responseBuilderWithLinks.entity(entity).build();
  }

  @Override
  public Response listGitxRateLimits(String harnessAccount, Integer page, @Max(1000L) Integer limit, String connectorId,
      GitXRateLimitProvider provider) {
    int effectivePage = page == null ? 0 : page;
    int effectiveLimit = limit == null ? 20 : limit;
    GitXRateLimitApiHelper.ListResult result =
        gitXRateLimitApiHelper.listRateLimits(harnessAccount, connectorId, provider, effectivePage, effectiveLimit);
    ResponseBuilder responseBuilder = Response.ok();
    ResponseBuilder responseBuilderWithLinks =
        ApiUtils.addLinksHeader(responseBuilder, result.getTotalElements(), effectivePage, effectiveLimit);
    return responseBuilderWithLinks.entity(result.getBody()).build();
  }

  @Override
  public Response listGitxRateLimitsTimeline(
      @NotNull String connectorId, @NotNull GitXRateLimitWindow window, String harnessAccount, String bucket) {
    return Response.ok()
        .entity(gitXRateLimitApiHelper.getTimeline(harnessAccount, connectorId, window, bucket))
        .build();
  }

  @Override
  public Response listGitxWebhookEvents(String harnessAccount, Integer page, @Max(1000L) Integer limit,
      String webhookIdentifier, Long eventStartTime, Long eventEndTime, String repoName, String filePath,
      String eventIdentifier, List<String> eventStatus, String connectorRef, Boolean includeParentScope,
      String commitId, String branch) {
    GitXEventsListRequestDTO gitXEventsListRequestDTO = GitXWebhookMapper.buildEventsListGitXWebhookRequestDTO(
        Scope.of(harnessAccount, null, null, harnessAccount), webhookIdentifier, eventStartTime, eventEndTime, repoName,
        filePath, eventIdentifier, eventStatus, connectorRef, includeParentScope, commitId, branch, page, limit);
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
}
