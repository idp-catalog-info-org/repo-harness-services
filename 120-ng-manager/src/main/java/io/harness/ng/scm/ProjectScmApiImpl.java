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
import io.harness.spec.server.ng.v1.ProjectScmApi;
import io.harness.spec.server.ng.v1.model.CreatePullRequest;
import io.harness.utils.ScopeResolutionHelper;

import com.google.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.NotNull;
import javax.ws.rs.core.Response;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITX})
@ScopeInfoResolutionApi
public class ProjectScmApiImpl extends AbstractScmApiImpl implements ProjectScmApi {
  @Inject
  public ProjectScmApiImpl(ScmFacilitatorService scmFacilitatorService, ScopeInfoService scopeInfoService,
      ScopeResolutionHelper scopeResolutionHelper, ScopeInfo scopeInfo) {
    super(scmFacilitatorService, scopeInfoService, scopeResolutionHelper, scopeInfo);
  }

  @Override
  public Response createProjectPullRequest(
      @Valid CreatePullRequest body, String org, String project, String harnessAccount) {
    return createPullRequest(body, harnessAccount, org, project);
  }

  @Override
  public Response getProjectPullRequest(String org, String project, @NotNull String repoName,
      @NotNull Integer prNumber, String harnessAccount, String connectorRef) {
    return getPullRequest(harnessAccount, org, project, connectorRef, repoName, prNumber);
  }

  @Override
  public Response listProjectRepos(String org, String project, String harnessAccount, String connectorRef, Integer page,
      @Max(100) Integer size, String repoNameSearchTerm, String userNameSearchTerm,
      Boolean applyGitXRepoAllowListFilter) {
    return listRepos(harnessAccount, org, project, connectorRef, page, size, repoNameSearchTerm, userNameSearchTerm,
        applyGitXRepoAllowListFilter);
  }

  @Override
  public Response listProjectBranches(String org, String project, @NotNull String repoName, String harnessAccount,
      String connectorRef, Integer page, @Max(100) Integer size, String branchNameSearchTerm) {
    return listBranches(harnessAccount, org, project, repoName, connectorRef, page, size, branchNameSearchTerm);
  }
}
