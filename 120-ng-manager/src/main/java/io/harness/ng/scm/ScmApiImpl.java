/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.scm;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeInfoResolutionApi;
import io.harness.gitsync.common.service.ScmFacilitatorService;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.spec.server.ng.v1.ScmApi;
import io.harness.spec.server.ng.v1.model.CreatePullRequest;
import io.harness.utils.ScopeResolutionHelper;

import com.google.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.NotNull;
import javax.ws.rs.core.Response;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITX})
@ScopeInfoResolutionApi
public class ScmApiImpl extends AbstractScmApiImpl implements ScmApi {
  @Inject
  public ScmApiImpl(ScmFacilitatorService scmFacilitatorService, ScopeInfoService scopeInfoService,
      ScopeResolutionHelper scopeResolutionHelper, ScopeInfo scopeInfo) {
    super(scmFacilitatorService, scopeInfoService, scopeResolutionHelper, scopeInfo);
  }

  @Override
  public Response createAccountPullRequest(@Valid CreatePullRequest body, String harnessAccount) {
    return createPullRequest(body, harnessAccount, null, null);
  }

  @Override
  public Response getAccountPullRequest(
      @NotNull String repoName, @NotNull Integer prNumber, String harnessAccount, String connectorRef) {
    return getPullRequest(harnessAccount, null, null, connectorRef, repoName, prNumber);
  }

  @Override
  public Response listAccountRepos(String harnessAccount, String connectorRef, Integer page, @Max(100) Integer size,
      String repoNameSearchTerm, String userNameSearchTerm, Boolean applyGitXRepoAllowListFilter) {
    return listRepos(harnessAccount, null, null, connectorRef, page, size, repoNameSearchTerm, userNameSearchTerm,
        applyGitXRepoAllowListFilter);
  }

  @Override
  public Response listAccountBranches(@NotNull String repoName, String harnessAccount, String connectorRef,
      Integer page, @Max(100) Integer size, String branchNameSearchTerm) {
    return listBranches(harnessAccount, null, null, repoName, connectorRef, page, size, branchNameSearchTerm);
  }
}
