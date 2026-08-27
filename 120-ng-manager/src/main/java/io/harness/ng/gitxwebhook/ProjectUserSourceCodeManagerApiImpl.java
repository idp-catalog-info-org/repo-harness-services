/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.gitxwebhook;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.gitsync.common.mappers.UserSourceCodeManagerMapper;
import io.harness.gitsync.common.service.UserSourceCodeManagerService;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.userprofile.commons.SCMType;
import io.harness.spec.server.ng.v1.ProjectUserSourceCodeManagerApi;

import com.google.inject.Inject;
import java.util.Map;
import javax.ws.rs.core.Response;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITX})
public class ProjectUserSourceCodeManagerApiImpl
    extends AbstractUserSourceCodeManagerApiImpl implements ProjectUserSourceCodeManagerApi {
  @Inject
  public ProjectUserSourceCodeManagerApiImpl(UserSourceCodeManagerService userSourceCodeManagerService,
      Map<SCMType, UserSourceCodeManagerMapper> scmMapBinder, ScopeInfoService scopeInfoService) {
    super(userSourceCodeManagerService, scmMapBinder, scopeInfoService);
  }

  @Override
  public Response listProjectSourceCodeManagers(
      String org, String project, String userIdentifier, String harnessAccount, String connectorRef, String type) {
    return getUserSourceCodeManagers(harnessAccount, org, project, userIdentifier, connectorRef, type);
  }
}
