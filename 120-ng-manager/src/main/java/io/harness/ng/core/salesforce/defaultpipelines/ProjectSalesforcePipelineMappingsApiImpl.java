/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.salesforce.defaultpipelines;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.ng.v1.ProjectSalesforcePipelineMappingsApi;

import com.google.inject.Inject;
import java.util.Map;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;

@NextGenManagerAuth
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.SALESFORCE})
@OwnedBy(HarnessTeam.CDP)
public class ProjectSalesforcePipelineMappingsApiImpl implements ProjectSalesforcePipelineMappingsApi {
  private final SalesforcePipelineMappingsService salesforcePipelineMappingsService;
  private final AccessControlClient accessControlClient;

  @Override
  public Response getProjectScopedSalesforcePipelineMappings(String org, String project, String harnessAccount) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(harnessAccount, org, project),
        Resource.of("PIPELINE", null), PipelineRbacPermissions.PIPELINE_EXECUTE);
    Map<String, String> mappings = salesforcePipelineMappingsService.getPipelineMappings();
    return Response.ok(mappings).build();
  }
}
