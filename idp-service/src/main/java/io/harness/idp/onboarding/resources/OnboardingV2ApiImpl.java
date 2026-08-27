/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.onboarding.resources;

import static io.harness.idp.common.RbacConstants.IDP_ADVANCED_CONFIGURATION;
import static io.harness.idp.common.RbacConstants.IDP_ADVANCED_CONFIGURATION_EDIT;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.idp.onboarding.service.OnboardingServiceV2;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.idp.v1.OnboardingV2Api;
import io.harness.spec.server.idp.v1.model.OnboardingCdEntitiesCountResponse;
import io.harness.spec.server.idp.v1.model.OnboardingCdEntitiesFetchRequest;
import io.harness.spec.server.idp.v1.model.OnboardingCdEntitiesFetchResponse;
import io.harness.spec.server.idp.v1.model.OnboardingGenerateYamlDefRequest;
import io.harness.spec.server.idp.v1.model.OnboardingGenerateYamlDefResponse;
import io.harness.spec.server.idp.v1.model.OnboardingImportCdEntitiesRequest;
import io.harness.spec.server.idp.v1.model.OnboardingImportCdEntitiesResponse;
import io.harness.spec.server.idp.v1.model.OnboardingSkipRequest;
import io.harness.spec.server.idp.v1.model.OnboardingSkipResponse;
import io.harness.spec.server.idp.v1.model.OnboardingStatusResponse;
import io.harness.utils.ApiUtils;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@NextGenManagerAuth
@Slf4j
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@Timed
@ResponseMetered
public class OnboardingV2ApiImpl implements OnboardingV2Api {
  private OnboardingServiceV2 onboardingServiceV2;

  @Override
  public Response cdEntitiesCount(@AccountIdentifier String harnessAccount) {
    OnboardingCdEntitiesCountResponse response = onboardingServiceV2.cdEntitiesCount(harnessAccount);
    return Response.status(Response.Status.OK).entity(response).build();
  }

  @Override
  public Response cdEntitiesFetch(@Valid OnboardingCdEntitiesFetchRequest body,
      @AccountIdentifier String harnessAccount, Integer page, Integer limit, String searchTerm) {
    int pageIndex = page == null ? 0 : page;
    int pageLimit = limit == null ? 100 : limit;
    Pageable pageRequest = PageRequest.of(pageIndex, pageLimit);
    OnboardingCdEntitiesFetchResponse cdEntitiesFetch =
        onboardingServiceV2.cdEntitiesFetch(harnessAccount, body, pageRequest, searchTerm);
    ResponseBuilder response = Response.ok();
    response = ApiUtils.addLinksHeader(response, cdEntitiesFetch.getServicesCount(), pageIndex, pageLimit);
    return response.entity(cdEntitiesFetch).build();
  }

  @Override
  public Response generateYamlDef(
      @Valid OnboardingGenerateYamlDefRequest body, @AccountIdentifier String harnessAccount) {
    OnboardingGenerateYamlDefResponse response = onboardingServiceV2.generateYamlDef(harnessAccount, body);
    return Response.status(Response.Status.OK).entity(response).build();
  }

  @Override
  public Response getOnboardingStatus(@AccountIdentifier String harnessAccount) {
    OnboardingStatusResponse response = onboardingServiceV2.getOnboardingStatus(harnessAccount);
    return Response.status(Response.Status.OK).entity(response).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_ADVANCED_CONFIGURATION, permission = IDP_ADVANCED_CONFIGURATION_EDIT)
  public Response importCdEntities(
      @Valid OnboardingImportCdEntitiesRequest body, @AccountIdentifier String harnessAccount) {
    OnboardingImportCdEntitiesResponse response = onboardingServiceV2.importCdEntities(harnessAccount, body);
    return Response.status(Response.Status.OK).entity(response).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_ADVANCED_CONFIGURATION, permission = IDP_ADVANCED_CONFIGURATION_EDIT)
  public Response postOnboardingSkip(@Valid OnboardingSkipRequest body, @AccountIdentifier String harnessAccount) {
    OnboardingSkipResponse response = onboardingServiceV2.postOnboardingSkip(harnessAccount, body);
    return Response.status(Response.Status.OK).entity(response).build();
  }
}
