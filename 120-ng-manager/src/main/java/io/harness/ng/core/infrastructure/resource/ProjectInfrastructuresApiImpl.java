/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.infrastructure.resource;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.customdeploymentng.CustomDeploymentInfrastructureHelper;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.service.steps.helpers.serviceoverridesv2.validators.EnvironmentValidationHelper;
import io.harness.cdng.ssh.SshEntityHelper;
import io.harness.ng.core.customDeployment.helper.CustomDeploymentYamlHelper;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.ng.core.infrastructure.services.impl.InfrastructureEntityVersionAwareFacade;
import io.harness.ng.core.service.resources.ServiceResourceApiUtils;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.utils.OrgAndProjectValidationHelper;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.ng.v1.ProjectInfrastructuresApi;
import io.harness.spec.server.ng.v1.model.InfrastructureCreateRequest;
import io.harness.spec.server.ng.v1.model.InfrastructureUpdateRequest;

import com.google.inject.Inject;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.ws.rs.core.Response;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@NextGenManagerAuth
public class ProjectInfrastructuresApiImpl extends AbstractInfrastructuresApiImpl implements ProjectInfrastructuresApi {
  @Inject
  ProjectInfrastructuresApiImpl(InfrastructureEntityService infrastructureEntityService,
      OrgAndProjectValidationHelper orgAndProjectValidationHelper,
      EnvironmentValidationHelper environmentValidationHelper, AccessControlClient accessControlClient,
      CustomDeploymentYamlHelper customDeploymentYamlHelper,
      CustomDeploymentInfrastructureHelper customDeploymentInfrastructureHelper, SshEntityHelper sshEntityHelper,
      CDFeatureFlagHelper cdFeatureFlagHelper, ServiceResourceApiUtils serviceResourceApiUtils,
      InfrastructureEntityVersionAwareFacade infraVersionAwareFacade, ScopeInfoService scopeInfoService) {
    super(infrastructureEntityService, orgAndProjectValidationHelper, environmentValidationHelper, accessControlClient,
        customDeploymentYamlHelper, customDeploymentInfrastructureHelper, sshEntityHelper, serviceResourceApiUtils,
        infraVersionAwareFacade, scopeInfoService);
  }

  @Override
  public Response createInfrastructure(
      @Valid InfrastructureCreateRequest body, String org, String project, String environment, String harnessAccount) {
    return super.createInfrastructureEntity(body, org, project, environment, harnessAccount);
  }

  @Override
  public Response deleteInfrastructure(String org, String project, String environment, String infrastructureDefinition,
      String harnessAccount, Boolean forceDelete) {
    return super.deleteInfrastructureEntity(
        org, project, environment, infrastructureDefinition, harnessAccount, forceDelete);
  }

  @Override
  public Response getInfrastructure(
      String org, String project, String environment, String infrastructureDefinition, String harnessAccount) {
    return super.getInfrastructureEntity(org, project, environment, infrastructureDefinition, harnessAccount);
  }

  @Override
  public Response getInfrastructures(String org, String project, String environment, String harnessAccount,
      Integer page, @Max(1000L) Integer limit, String searchTerm, List<String> infraIds, String sort,
      Boolean isAccessList, List<String> serviceRefs, String templateIdentifier, String templateVersion,
      String deploymentType, String order) {
    return super.getInfrastructureEntities(org, project, environment, harnessAccount, page, limit, searchTerm, infraIds,
        sort, isAccessList, serviceRefs, templateIdentifier, templateVersion, deploymentType, order);
  }

  @Override
  public Response updateInfrastructure(@Valid InfrastructureUpdateRequest body, String org, String project,
      String environment, String infrastructureDefinition, String harnessAccount) {
    return super.updateInfrastructureEntity(body, org, project, environment, infrastructureDefinition, harnessAccount);
  }
}
