/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.resources;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.catalog.service.EntityLinkService;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.idp.v1.EntityLinksApi;
import io.harness.spec.server.idp.v1.model.EntityLinkExistsResponse;
import io.harness.spec.server.idp.v1.model.EntityLinkRequest;
import io.harness.spec.server.idp.v1.model.EntityLinkResponse;
import io.harness.spec.server.idp.v1.model.ResolveFieldMappingsRequest;
import io.harness.spec.server.idp.v1.model.ResolveFieldMappingsResponse;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import java.util.List;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@NextGenManagerAuth
@Slf4j
@OwnedBy(HarnessTeam.IDP)
@Timed
@ResponseMetered
public class EntityLinksApiImpl implements EntityLinksApi {
  private EntityLinkService entityLinkService;

  @Override
  public Response createEntityLink(
      String accountIdentifier, EntityLinkRequest body, @AccountIdentifier String harnessAccount) {
    EntityLinkResponse response = entityLinkService.createLink(harnessAccount, body);
    return Response.status(Response.Status.CREATED).entity(response).build();
  }

  @Override
  public Response getEntityLink(String scope, String kind, String identifier, String accountIdentifier,
      @AccountIdentifier String harnessAccount) {
    String entityRef = buildEntityRef(scope, kind, identifier);
    EntityLinkResponse response = entityLinkService.getLink(harnessAccount, entityRef);
    if (response == null) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    return Response.ok(response).build();
  }

  @Override
  public Response updateEntityLink(String accountIdentifier, String scope, String kind, String identifier,
      EntityLinkRequest body, @AccountIdentifier String harnessAccount) {
    String entityRef = buildEntityRef(scope, kind, identifier);
    EntityLinkResponse response = entityLinkService.updateLink(harnessAccount, entityRef, body);
    return Response.ok(response).build();
  }

  @Override
  public Response deleteEntityLink(String scope, String kind, String identifier, String accountIdentifier,
      @AccountIdentifier String harnessAccount) {
    String entityRef = buildEntityRef(scope, kind, identifier);
    entityLinkService.deleteLink(harnessAccount, entityRef);
    return Response.noContent().build();
  }

  @Override
  public Response checkEntityLinkExists(String scope, String kind, String identifier, String accountIdentifier,
      @AccountIdentifier String harnessAccount) {
    String entityRef = buildEntityRef(scope, kind, identifier);
    EntityLinkExistsResponse response = entityLinkService.linkExists(harnessAccount, entityRef);
    return Response.ok(response).build();
  }

  @Override
  public Response getLinkedEntities(String accountIdentifier, String entityKind, String entityType, String entityRef,
      @AccountIdentifier String harnessAccount) {
    List<String> responses = entityLinkService.getLinkedEntities(harnessAccount, entityKind, entityType, entityRef);
    return Response.ok(responses).build();
  }

  @Override
  public Response resolveEntityLinkMappings(String accountIdentifier, String scope, String kind, String identifier,
      ResolveFieldMappingsRequest body, @AccountIdentifier String harnessAccount) {
    ResolveFieldMappingsResponse response =
        entityLinkService.resolveFieldMappings(harnessAccount, scope, kind, identifier, body);
    return Response.ok(response).build();
  }

  @Override
  public Response getEntityLinksByIntegration(String integrationIdentifier, String accountIdentifier,
      @AccountIdentifier String harnessAccount, String orgIdentifier, String projectIdentifier) {
    List<String> responses = entityLinkService.getEntityLinksByIntegration(
        harnessAccount, integrationIdentifier, orgIdentifier, projectIdentifier);
    return Response.ok(responses).build();
  }

  private String buildEntityRef(String scope, String kind, String identifier) {
    return kind + ":" + scope + "/" + identifier;
  }
}
