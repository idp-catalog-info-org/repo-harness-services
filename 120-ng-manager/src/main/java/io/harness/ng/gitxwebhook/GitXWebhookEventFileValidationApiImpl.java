/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.gitxwebhook;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.gitsync.gitxwebhooks.entity.GitXWebhookEventValidationInfo;
import io.harness.gitsync.gitxwebhooks.mapper.GitXWebhookMapper;
import io.harness.gitsync.gitxwebhooks.service.gitxwebhook.GitXWebhookEventValidationService;
import io.harness.spec.server.ng.v1.GitXWebhooksEventFileValidationApi;
import io.harness.spec.server.ng.v1.model.GetGitXWebhookEventFileValidationResponse;
import io.harness.utils.ApiUtils;

import com.google.inject.Inject;
import java.util.List;
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
public class GitXWebhookEventFileValidationApiImpl implements GitXWebhooksEventFileValidationApi {
  @Inject private GitXWebhookEventValidationService gitXWebhookEventValidationService;

  @Override
  public Response gitxWebhookEventFileValidation(
      String gitxWebhookEvent, String harnessAccount, Integer page, @Max(1000L) Integer limit) {
    List<GitXWebhookEventValidationInfo> gitXWebhookEventValidationInfoList =
        gitXWebhookEventValidationService.listValidationInfo(harnessAccount, gitxWebhookEvent);

    Page<GetGitXWebhookEventFileValidationResponse> gitXWebhookEventValidationInfoPage =
        GitXWebhookMapper.buildListGitXWebhookEventValidationInfoResponse(
            gitXWebhookEventValidationInfoList, page, limit);
    ResponseBuilder responseBuilder = Response.ok();
    ResponseBuilder responseBuilderWithLinks =
        ApiUtils.addLinksHeader(responseBuilder, gitXWebhookEventValidationInfoPage.getTotalElements(), page, limit);
    return responseBuilderWithLinks.entity(gitXWebhookEventValidationInfoPage.getContent()).build();
  }
}
