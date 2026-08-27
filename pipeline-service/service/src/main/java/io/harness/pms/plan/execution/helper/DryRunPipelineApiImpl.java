/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.helper;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.exception.InvalidRequestException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.spec.server.pipeline.v1.DryRunPipelineApi;
import io.harness.spec.server.pipeline.v1.model.DryRunPipelineRequestBody;
import io.harness.spec.server.pipeline.v1.model.DryRunPipelineResponseBody;
import io.harness.utils.ScopeResolutionHelper;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@PipelineServiceAuth
public class DryRunPipelineApiImpl implements DryRunPipelineApi {
  @Inject DryRunHelper dryRunHelper;
  @Inject ScopeResolutionHelper scopeResolutionHelper;

  @Override
  @Timed
  @ResponseMetered
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  public Response pipelineDryRun(@OrgIdentifier String org, @ProjectIdentifier String project,
      @Valid DryRunPipelineRequestBody body, @AccountIdentifier String harnessAccount) {
    if (body == null) {
      throw new InvalidRequestException("Request body cannot be null");
    }
    GitAwareContextHelper.populateGitDetails(GitEntityInfo.builder().branch(body.getBranch()).build());
    ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(harnessAccount, org, project);
    DryRunPipelineResponseBody responseBody =
        dryRunHelper.startDryRun(harnessAccount, org, project, scopeInfo, body, true);
    return Response.ok().entity(responseBody).build();
  }
}
