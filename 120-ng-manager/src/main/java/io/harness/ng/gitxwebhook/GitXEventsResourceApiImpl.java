/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.gitxwebhook;

import static io.harness.ng.gitxwebhook.GitXWebhooksApiImpl.HTTP_201;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.Scope;
import io.harness.gitsync.gitxwebhooks.dtos.GitXEventBranchListRequestDTO;
import io.harness.gitsync.gitxwebhooks.service.gitxwebhook.GitXWebhookEventService;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.spec.server.ng.v1.GitXEventsResourceApi;
import io.harness.spec.server.ng.v1.model.ListGitXWebhookBranchesDTO;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITX})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
public class GitXEventsResourceApiImpl implements GitXEventsResourceApi {
  GitXWebhookEventService gitXWebhookEventService;
  ScopeInfoService scopeResolverService;

  @Override
  public Response listWebhookEventBranches(String harnessAccount, String org, String project, String webhookIdentifier,
      String repoName, String connectorRef, Boolean includeParentScope) {
    GitXEventBranchListRequestDTO listRequestDTO =
        GitXEventBranchListRequestDTO.builder()
            .scope(Scope.of(scopeResolverService.getScopeInfo(harnessAccount, org, project)))
            .webhookIdentifier(webhookIdentifier)
            .repoName(repoName)
            .connectorRef(connectorRef)
            .includeParentScope(includeParentScope)
            .build();
    List<String> branches = gitXWebhookEventService.listBranches(listRequestDTO);
    List<String> branchResponse = new ArrayList<>();
    for (String branch : branches) {
      if (branch != null) {
        branchResponse.add(branch);
      }
    }
    ListGitXWebhookBranchesDTO response = new ListGitXWebhookBranchesDTO();
    response.setBranches(branchResponse);
    return Response.status(HTTP_201).entity(response).build();
  }
}
