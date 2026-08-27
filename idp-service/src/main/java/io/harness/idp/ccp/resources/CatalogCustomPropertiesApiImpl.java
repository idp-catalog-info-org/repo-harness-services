/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.ccp.resources;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.idp.ccp.service.CatalogCustomPropertiesService;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.idp.v1.CatalogCustomPropertiesApi;
import io.harness.spec.server.idp.v1.model.CustomPropertyByEntityDeleteRequest;
import io.harness.spec.server.idp.v1.model.CustomPropertyByEntityRequest;
import io.harness.spec.server.idp.v1.model.CustomPropertyByFieldDeleteRequest;
import io.harness.spec.server.idp.v1.model.CustomPropertyByFieldRequest;
import io.harness.spec.server.idp.v1.model.CustomPropertyFilterDeleteRequest;
import io.harness.spec.server.idp.v1.model.CustomPropertyFilterRequest;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.core.Response;

@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
@NextGenManagerAuth
@Timed
@ResponseMetered
public class CatalogCustomPropertiesApiImpl implements CatalogCustomPropertiesApi {
  @Inject CatalogCustomPropertiesService ccpService;

  @Override
  public Response ingestCatalogCustomProperties(
      @Valid CustomPropertyFilterRequest body, @AccountIdentifier String harnessAccount, Boolean dryRun) {
    dryRun = dryRun != null && dryRun;
    return Response.status(Response.Status.OK)
        .entity(ccpService.resolveEntitiesAndUpsertCustomProperties(body, harnessAccount, dryRun))
        .build();
  }

  @Override
  public Response deleteCatalogCustomProperties(
      @Valid CustomPropertyFilterDeleteRequest body, @AccountIdentifier String harnessAccount, Boolean dryRun) {
    dryRun = dryRun != null && dryRun;
    return Response.status(Response.Status.OK)
        .entity(ccpService.deleteCustomProperties(body, harnessAccount, dryRun))
        .build();
  }

  @Override
  public Response getCatalogCustomPropertiesByEntity(String entityRef, @AccountIdentifier String harnessAccount) {
    return Response.status(Response.Status.OK)
        .entity(ccpService.getCustomPropertiesForEntity(harnessAccount, entityRef))
        .build();
  }

  @Override
  public Response ingestCatalogCustomPropertiesByEntity(
      @Valid CustomPropertyByEntityRequest body, @AccountIdentifier String harnessAccount, Boolean dryRun) {
    dryRun = dryRun != null && dryRun;
    return Response.status(Response.Status.OK)
        .entity(ccpService.resolveCustomPropertiesForEntity(body, harnessAccount, dryRun))
        .build();
  }

  @Override
  public Response ingestEntitiesByCatalogCustomProperty(
      @Valid CustomPropertyByFieldRequest body, @AccountIdentifier String harnessAccount, Boolean dryRun) {
    dryRun = dryRun != null && dryRun;
    return Response.status(Response.Status.OK)
        .entity(ccpService.resolveEntitiesForCustomProperty(body, harnessAccount, dryRun))
        .build();
  }

  @Override
  public Response toggleCatalogCustomProperties(@NotNull Boolean enabled, @AccountIdentifier String harnessAccount) {
    ccpService.toggleCustomProperties(harnessAccount, enabled);
    return Response.status(Response.Status.NO_CONTENT).build();
  }

  @Override
  public Response deleteCatalogCustomPropertiesByEntity(
      @Valid CustomPropertyByEntityDeleteRequest body, @AccountIdentifier String harnessAccount, Boolean dryRun) {
    dryRun = dryRun != null && dryRun;
    return Response.status(Response.Status.OK)
        .entity(ccpService.deleteCustomPropertiesForEntity(body, harnessAccount, dryRun))
        .build();
  }

  @Override
  public Response getEntitiesByCatalogCustomProperty(String property, @AccountIdentifier String harnessAccount) {
    return Response.status(Response.Status.OK)
        .entity(ccpService.getCustomPropertiesForCustomProperty(harnessAccount, property))
        .build();
  }

  @Override
  public Response getEntityRefs(String harnessAccount, String searchTerm) {
    return Response.status(Response.Status.OK).entity(ccpService.fetchEntityRefs(harnessAccount, searchTerm)).build();
  }

  @Override
  public Response deleteEntitiesByCatalogCustomProperty(
      @Valid CustomPropertyByFieldDeleteRequest body, @AccountIdentifier String harnessAccount, Boolean dryRun) {
    dryRun = dryRun != null && dryRun;
    return Response.status(Response.Status.OK)
        .entity(ccpService.deleteEntitiesForCustomProperty(body, harnessAccount, dryRun))
        .build();
  }
}
