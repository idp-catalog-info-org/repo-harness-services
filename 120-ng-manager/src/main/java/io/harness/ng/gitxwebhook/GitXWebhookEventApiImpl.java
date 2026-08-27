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
import io.harness.exception.AccessDeniedException;
import io.harness.exception.WingsException;
import io.harness.gitsync.common.beans.GitXWebhookEventStatus;
import io.harness.gitsync.gitxwebhooks.dtos.GitXEventDTO;
import io.harness.gitsync.gitxwebhooks.dtos.GitXEventUpdateRequestDTO;
import io.harness.gitsync.gitxwebhooks.mapper.GitXWebhookMapper;
import io.harness.gitsync.gitxwebhooks.service.gitxwebhook.GitXWebhookEventService;
import io.harness.security.dto.UserPrincipal;
import io.harness.spec.server.ng.v1.GitXWebhookEventApi;
import io.harness.spec.server.ng.v1.model.GitXWebhookEventResponse;
import io.harness.spec.server.ng.v1.model.UpdateGitXWebhookEventRequest;
import io.harness.utils.UserHelperService;

import com.google.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITX})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class GitXWebhookEventApiImpl implements GitXWebhookEventApi {
  private GitXWebhookEventService gitXWebhookEventService;
  private UserHelperService userHelperService;
  private static final String USER_ID_PLACEHOLDER = "{{USER}}";

  @Override
  public Response patchGitxWebhookEventsGitxWebhookEvent(
      String gitxWebhookEvent, @Valid UpdateGitXWebhookEventRequest body, String harnessAccount) {
    checkUserAuthorization(String.format("User : %s not allowed to change the webhook event status for event %s",
        USER_ID_PLACEHOLDER, gitxWebhookEvent));

    GitXWebhookEventStatus gitXWebhookEventStatus =
        GitXWebhookEventStatus.getGitXWebhookEventStatus(body.getEventStatus());
    GitXEventDTO gitXEventDTO = gitXWebhookEventService.updateEvent(harnessAccount, gitxWebhookEvent,
        GitXEventUpdateRequestDTO.builder().gitXWebhookEventStatus(gitXWebhookEventStatus).build());
    GitXWebhookEventResponse responseBody = GitXWebhookMapper.buildPatchGitXWebhookEventResponse(gitXEventDTO);
    return Response.ok().entity(responseBody).build();
  }

  private void checkUserAuthorization(String errorMessageIfAuthorizationFailed) {
    UserPrincipal userPrincipal = userHelperService.getUserPrincipalOrThrow();
    String userId = userPrincipal.getName();
    if (!userHelperService.isHarnessSupportUser(userId)) {
      log.error(errorMessageIfAuthorizationFailed.replace(USER_ID_PLACEHOLDER, userId));
      throw new AccessDeniedException("Not Authorized", WingsException.USER);
    }
  }
}
