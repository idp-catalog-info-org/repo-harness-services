/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.webhook;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.gitsync.gitxwebhooks.dtos.CreateGitXWebhookResponseDTO;
import io.harness.gitsync.gitxwebhooks.dtos.DeleteGitXWebhookResponseDTO;
import io.harness.gitsync.gitxwebhooks.dtos.GetGitXWebhookResponseDTO;
import io.harness.gitsync.gitxwebhooks.dtos.UpdateGitXWebhookResponseDTO;
import io.harness.gitsync.gitxwebhooks.mapper.GitXWebhookMapper;
import io.harness.gitsync.gitxwebhooks.service.gitxwebhook.GitXWebhookEventService;
import io.harness.gitsync.gitxwebhooks.service.gitxwebhook.GitXWebhookService;
import io.harness.ng.gitxwebhook.GitXWebhooksApiHelper;
import io.harness.spec.server.ng.v1.OrgWebhooksApi;
import io.harness.spec.server.ng.v1.model.CreateWebhookRequest;
import io.harness.spec.server.ng.v1.model.CreateWebhookResponse;
import io.harness.spec.server.ng.v1.model.DeleteGitXWebhookResponse;
import io.harness.spec.server.ng.v1.model.ListWebhookRequest;
import io.harness.spec.server.ng.v1.model.UpdateWebhookRequest;
import io.harness.spec.server.ng.v1.model.UpdateWebhookResponse;
import io.harness.spec.server.ng.v1.model.WebhookResponse;
import io.harness.utils.ApiUtils;

import com.google.inject.Inject;
import java.util.Optional;
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
@OwnedBy(CDC)
public class OrgWebhooksApiImpl implements OrgWebhooksApi {
  WebhooksHelper webhooksHelper;
  GitXWebhookService gitXWebhookService;
  GitXWebhookEventService gitXWebhookEventService;
  GitXWebhooksApiHelper gitXWebhooksApiHelper;

  public static final int HTTP_404 = 404;
  public static final int HTTP_204 = 204;
  public static final int HTTP_201 = 201;

  @Override
  public Response createOrgWebhooks(String org, @Valid CreateWebhookRequest body, String harnessAccount) {
    CreateGitXWebhookResponseDTO createGitXWebhookResponseDTO =
        gitXWebhooksApiHelper.createWebhook(harnessAccount, org, null, body);
    CreateWebhookResponse responseBody = GitXWebhookMapper.buildCreateWebhookResponse(createGitXWebhookResponseDTO);
    return Response.status(HTTP_201).entity(responseBody).build();
  }

  @Override
  public Response deleteOrgWebhook(String org, String webhook, String harnessAccount) {
    DeleteGitXWebhookResponseDTO deleteGitXWebhookResponse =
        gitXWebhooksApiHelper.deleteGitXWebhook(harnessAccount, org, null, webhook);
    DeleteGitXWebhookResponse responseBody =
        GitXWebhookMapper.buildDeleteGitXWebhookResponse(deleteGitXWebhookResponse);
    return Response.status(HTTP_204).entity(responseBody).build();
  }

  @Override
  public Response getOrgWebhook(String org, String webhook, String harnessAccount) {
    Optional<GetGitXWebhookResponseDTO> optionalGetGitXWebhookResponseDTO =
        gitXWebhooksApiHelper.getGitXWebhook(harnessAccount, org, null, webhook);
    if (optionalGetGitXWebhookResponseDTO.isEmpty()) {
      return Response.status(HTTP_404).build();
    }
    WebhookResponse responseBody =
        GitXWebhookMapper.buildGetWebhookResponseDTO(optionalGetGitXWebhookResponseDTO.get());
    return Response.ok().entity(responseBody).build();
  }

  @Override
  public Response listOrgWebhooks(String org, @Valid ListWebhookRequest listWebhookRequest, String harnessAccount,
      Integer page, @Max(1000L) Integer limit) {
    Page<WebhookResponse> webhookResponse =
        webhooksHelper.listWebhooks(harnessAccount, org, null, listWebhookRequest, page, limit);
    ResponseBuilder responseBuilder = Response.ok();
    ResponseBuilder responseBuilderWithLinks =
        ApiUtils.addLinksHeader(responseBuilder, webhookResponse.getTotalElements(), page, limit);
    return responseBuilderWithLinks.entity(webhookResponse.getContent()).build();
  }

  @Override
  public Response updateOrgWebhook(
      String org, String webhook, @Valid UpdateWebhookRequest body, String harnessAccount) {
    UpdateGitXWebhookResponseDTO updateGitXWebhookResponseDTO =
        gitXWebhooksApiHelper.updateWebhook(harnessAccount, org, null, webhook, body);
    UpdateWebhookResponse responseBody = GitXWebhookMapper.buildUpdateWebhookResponse(updateGitXWebhookResponseDTO);
    return Response.ok().entity(responseBody).build();
  }
}