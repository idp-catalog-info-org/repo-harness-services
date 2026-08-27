/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.resources;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.ng.core.service.services.ServiceEntityManagementService;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.service.services.impl.ServiceEntityYamlSchemaHelper;
import io.harness.ng.core.service.services.impl.ServiceRbacHelper;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.utils.OrgAndProjectValidationHelper;
import io.harness.pms.rbac.NGResourceType;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.ng.v1.AccountServicesApi;
import io.harness.spec.server.ng.v1.model.GitEntityFindInfoDTO;
import io.harness.spec.server.ng.v1.model.ServiceCreateRequest;
import io.harness.spec.server.ng.v1.model.ServiceUpdateRequest;
import io.harness.utils.NGFeatureFlagHelperService;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import java.util.List;
import javax.validation.Valid;
import javax.ws.rs.core.Response;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@OwnedBy(CDC)
@NextGenManagerAuth
public class AccountServicesApiImpl extends AbstractServicesApiImpl implements AccountServicesApi {
  @Inject
  AccountServicesApiImpl(ServiceEntityService serviceEntityService, AccessControlClient accessControlClient,
      ServiceEntityManagementService serviceEntityManagementService,
      OrgAndProjectValidationHelper orgAndProjectValidationHelper, ServiceResourceApiUtils serviceResourceApiUtils,
      ServiceEntityYamlSchemaHelper serviceSchemaHelper, ServiceRbacHelper serviceRbacHelper,
      ScopeInfoService scopeInfoService, NGFeatureFlagHelperService featureFlagHelperService) {
    super(serviceEntityService, accessControlClient, serviceEntityManagementService, orgAndProjectValidationHelper,
        serviceResourceApiUtils, serviceSchemaHelper, serviceRbacHelper, scopeInfoService, featureFlagHelperService);
  }
  @Timed
  @ResponseMetered
  @Override
  public Response createAccountScopedService(@Valid ServiceCreateRequest serviceRequest, String account) {
    return super.createServiceEntity(serviceRequest, null, null, account);
  }

  @Timed
  @ResponseMetered
  @NGAccessControlCheck(resourceType = NGResourceType.SERVICE, permission = "core_service_delete")
  @Override
  public Response deleteAccountScopedService(
      @ResourceIdentifier String service, @AccountIdentifier String account, Boolean forceDelete) {
    return super.deleteServiceEntity(null, null, service, account, Boolean.TRUE == forceDelete);
  }

  @Timed
  @ResponseMetered
  @Override
  public Response getAccountScopedPrimaryManifests(String service, String harnessAccount) {
    return super.getPrimaryManifestList(service, null, null, null, harnessAccount);
  }

  @Timed
  @ResponseMetered
  @Override
  public Response postAccountScopedPrimaryManifests(
      String service, GitEntityFindInfoDTO gitEntityFindInfoDTO, String harnessAccount) {
    return super.getPrimaryManifestList(service, null, null, gitEntityFindInfoDTO, harnessAccount);
  }

  @Timed
  @ResponseMetered
  @NGAccessControlCheck(resourceType = NGResourceType.SERVICE, permission = "core_service_view")
  @Override
  public Response getAccountScopedService(@ResourceIdentifier String service, @AccountIdentifier String account) {
    return super.getServiceEntity(null, null, service, account);
  }

  @Timed
  @ResponseMetered
  @Override
  public Response getAccountScopedServices(Integer page, Integer limit, String searchTerm, List<String> services,
      String sort, Boolean isAccessList, String type, Boolean gitOpsEnabled, String account, String order) {
    return super.getServicesList(
        null, null, page, limit, searchTerm, services, sort, isAccessList, type, gitOpsEnabled, account, order);
  }

  @Timed
  @ResponseMetered
  @Override
  public Response updateAccountScopedService(
      @Valid ServiceUpdateRequest serviceRequest, String service, String account) {
    return super.updateServiceEntity(serviceRequest, null, null, service, account);
  }
}