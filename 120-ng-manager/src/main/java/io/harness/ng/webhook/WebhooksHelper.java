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
import io.harness.beans.Scope;
import io.harness.gitsync.gitxwebhooks.dtos.GetGitXWebhookResponseDTO;
import io.harness.gitsync.gitxwebhooks.dtos.ListGitXWebhookRequestDTO;
import io.harness.gitsync.gitxwebhooks.dtos.ListGitXWebhookResponseDTO;
import io.harness.gitsync.gitxwebhooks.entity.GitXWebhook;
import io.harness.gitsync.gitxwebhooks.mapper.GitXWebhookMapper;
import io.harness.gitsync.gitxwebhooks.service.gitxwebhook.GitXWebhookService;
import io.harness.gitx.GitXWebhhookRbacPermissionsConstants;
import io.harness.ng.gitxwebhook.GitXWebhooksApiHelper;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.ng.v1.model.ListWebhookRequest;
import io.harness.spec.server.ng.v1.model.WebhookResponse;
import io.harness.utils.PageUtils;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(CDC)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@NextGenManagerAuth
public class WebhooksHelper {
  private GitXWebhookService gitXWebhookService;
  private GitXWebhooksApiHelper gitXWebhooksApiHelper;

  public Page<WebhookResponse> listWebhooks(String accountId, String orgId, String projectId,
      ListWebhookRequest listWebhookRequest, Integer page, Integer limit) {
    gitXWebhooksApiHelper.checkForGitXWebhookPermission(
        accountId, orgId, projectId, GitXWebhhookRbacPermissionsConstants.GitXWebhhook_VIEW);
    ListGitXWebhookRequestDTO listGitXWebhookRequestDTO = GitXWebhookMapper.buildListGitXWebhookRequestDTO(
        Scope.of(
            accountId, orgId, projectId, gitXWebhooksApiHelper.getParentUniqueIdentifier(accountId, orgId, projectId)),
        listWebhookRequest);
    ListGitXWebhookResponseDTO listGitXWebhookResponseDTO = listWebhooksWithCriteria(listGitXWebhookRequestDTO);
    return buildListWebhookResponse(listGitXWebhookResponseDTO, page, limit);
  }

  public Page<WebhookResponse> buildListWebhookResponse(
      ListGitXWebhookResponseDTO listXWebhookResponseDTO, Integer page, Integer limit) {
    List<WebhookResponse> getWebhookResponseList = new ArrayList<>();

    for (GetGitXWebhookResponseDTO webhook : listXWebhookResponseDTO.getGitXWebhooksList()) {
      WebhookResponse webhookResponse = GitXWebhookMapper.buildGetWebhookResponseDTO(webhook);
      getWebhookResponseList.add(webhookResponse);
    }

    return PageUtils.getPage(getWebhookResponseList, page, limit);
  }

  public ListGitXWebhookResponseDTO listWebhooksWithCriteria(ListGitXWebhookRequestDTO listGitXWebhookRequestDTO) {
    List<GitXWebhook> webhooks = gitXWebhookService.listGitXWebhooks(listGitXWebhookRequestDTO);
    return ListGitXWebhookResponseDTO.builder()
        .gitXWebhooksList(gitXWebhooksApiHelper.prepareGitXWebhooks(webhooks))
        .build();
  }
}