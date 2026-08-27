/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.gitxwebhook;

import static io.harness.ng.gitxwebhook.GitXWebhooksApiImpl.HTTP_201;
import static io.harness.ng.gitxwebhook.GitXWebhooksApiImpl.HTTP_204;
import static io.harness.ng.gitxwebhook.GitXWebhooksApiImpl.HTTP_404;
import static io.harness.ng.gitxwebhook.GitXWebhooksApiImpl.NG_MANAGER;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.gitsync.gitxwebhooks.dtos.CreateGitXWebhookResponseDTO;
import io.harness.gitsync.gitxwebhooks.dtos.DeleteGitXWebhookResponseDTO;
import io.harness.gitsync.gitxwebhooks.dtos.GetGitXWebhookResponseDTO;
import io.harness.gitsync.gitxwebhooks.dtos.ListGitXWebhookResponseDTO;
import io.harness.gitsync.gitxwebhooks.dtos.UpdateGitXWebhookResponseDTO;
import io.harness.gitsync.gitxwebhooks.mapper.GitXWebhookMapper;
import io.harness.gitsync.gitxwebhooks.service.gitxwebhook.GitXWebhookEventService;
import io.harness.gitsync.gitxwebhooks.service.gitxwebhook.GitXWebhookService;
import io.harness.grpc.utils.FlowName;
import io.harness.grpc.utils.GrpcContextMetadataDto;
import io.harness.grpc.utils.GrpcContextMetadataHelper;
import io.harness.spec.server.ng.v1.ProjectGitxWebhooksApi;
import io.harness.spec.server.ng.v1.model.CreateGitXWebhookRequest;
import io.harness.spec.server.ng.v1.model.CreateGitXWebhookResponse;
import io.harness.spec.server.ng.v1.model.DeleteGitXWebhookResponse;
import io.harness.spec.server.ng.v1.model.GitXWebhookResponse;
import io.harness.spec.server.ng.v1.model.UpdateGitXWebhookRequest;
import io.harness.spec.server.ng.v1.model.UpdateGitXWebhookResponse;
import io.harness.utils.ApiUtils;

import com.google.inject.Inject;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITX})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class ProjectGitXWebhooksApiImpl implements ProjectGitxWebhooksApi {
  GitXWebhookService gitXWebhookService;
  GitXWebhookEventService gitXWebhookEventService;
  GitXWebhooksApiHelper gitXWebhooksApiHelper;

  @Override
  public Response createProjectGitxWebhook(@OrgIdentifier String org, @ProjectIdentifier String project,
      @Valid CreateGitXWebhookRequest body, @AccountIdentifier String harnessAccount) {
    GrpcContextMetadataHelper.addMetadata(
        GrpcContextMetadataDto.builder().flowName(FlowName.GITX_WEBHOOK_CREATE).callerName(NG_MANAGER).build());
    CreateGitXWebhookResponseDTO createGitXWebhookResponseDTO =
        gitXWebhooksApiHelper.createGitXWebhook(harnessAccount, org, project, body);
    CreateGitXWebhookResponse responseBody =
        GitXWebhookMapper.buildCreateGitXWebhookResponse(createGitXWebhookResponseDTO);
    return Response.status(HTTP_201).entity(responseBody).build();
  }

  @Override
  public Response getProjectGitxWebhook(@OrgIdentifier String org, @ProjectIdentifier String project,
      String gitxWebhook, @AccountIdentifier String harnessAccount) {
    GrpcContextMetadataHelper.addMetadata(
        GrpcContextMetadataDto.builder().flowName(FlowName.GITX_WEBHOOK_GET).callerName(NG_MANAGER).build());
    Optional<GetGitXWebhookResponseDTO> optionalGetGitXWebhookResponseDTO =
        gitXWebhooksApiHelper.getGitXWebhook(harnessAccount, org, project, gitxWebhook);
    if (optionalGetGitXWebhookResponseDTO.isEmpty()) {
      return Response.status(HTTP_404).build();
    }
    GitXWebhookResponse responseBody =
        GitXWebhookMapper.buildGetGitXWebhookResponseDTO(optionalGetGitXWebhookResponseDTO.get());
    return Response.ok().entity(responseBody).build();
  }

  @Override
  public Response updateProjectGitxWebhook(@OrgIdentifier String org, @ProjectIdentifier String project,
      String gitxWebhook, @Valid UpdateGitXWebhookRequest body, @AccountIdentifier String harnessAccount) {
    GrpcContextMetadataHelper.addMetadata(
        GrpcContextMetadataDto.builder().flowName(FlowName.GITX_WEBHOOK_UPDATE).callerName(NG_MANAGER).build());
    UpdateGitXWebhookResponseDTO updateGitXWebhookResponseDTO =
        gitXWebhooksApiHelper.updateGitXWebhook(harnessAccount, org, project, gitxWebhook, body);
    UpdateGitXWebhookResponse responseBody =
        GitXWebhookMapper.buildUpdateGitXWebhookResponse(updateGitXWebhookResponseDTO);
    return Response.ok().entity(responseBody).build();
  }

  @Override
  public Response deleteProjectGitxWebhook(@OrgIdentifier String org, @ProjectIdentifier String project,
      String gitxWebhook, @AccountIdentifier String harnessAccount) {
    GrpcContextMetadataHelper.addMetadata(
        GrpcContextMetadataDto.builder().flowName(FlowName.GITX_WEBHOOK_DELETE).callerName(NG_MANAGER).build());
    DeleteGitXWebhookResponseDTO deleteGitXWebhookResponse =
        gitXWebhooksApiHelper.deleteGitXWebhook(harnessAccount, org, project, gitxWebhook);
    DeleteGitXWebhookResponse responseBody =
        GitXWebhookMapper.buildDeleteGitXWebhookResponse(deleteGitXWebhookResponse);
    return Response.status(HTTP_204).entity(responseBody).build();
  }

  @Override
  public Response listProjectGitxWebhook(@OrgIdentifier String org, @ProjectIdentifier String project,
      @AccountIdentifier String harnessAccount, Integer page, @Max(1000L) Integer limit, String webhookIdentifier) {
    ListGitXWebhookResponseDTO listGitXWebhookResponseDTO =
        gitXWebhooksApiHelper.listGitXWebhooks(harnessAccount, org, project, webhookIdentifier);
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
}
