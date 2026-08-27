/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.resources;

import static io.harness.rule.OwnerRule.DEVESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.catalog.service.EntityLinkService;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.EntityLink;
import io.harness.spec.server.idp.v1.model.EntityLinkExistsResponse;
import io.harness.spec.server.idp.v1.model.EntityLinkRequest;
import io.harness.spec.server.idp.v1.model.EntityLinkResponse;
import io.harness.spec.server.idp.v1.model.ResolveFieldMappingsRequest;
import io.harness.spec.server.idp.v1.model.ResolveFieldMappingsResponse;

import java.util.List;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class EntityLinksApiImplTest extends CategoryTest {
  @Mock EntityLinkService entityLinkService;

  @InjectMocks EntityLinksApiImpl entityLinksApi;

  AutoCloseable openMocks;

  private static final String ACCOUNT_ID = "test-account-id";
  private static final String SCOPE = "account";
  private static final String KIND = "workflow";
  private static final String IDENTIFIER = "my-workflow";
  private static final String ENTITY_REF = KIND + ":" + SCOPE + "/" + IDENTIFIER;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testCreateEntityLink_returnsCreated() {
    EntityLinkRequest request = buildRequest();
    EntityLinkResponse serviceResponse = buildResponse();
    when(entityLinkService.createLink(ACCOUNT_ID, request)).thenReturn(serviceResponse);

    Response response = entityLinksApi.createEntityLink(ACCOUNT_ID, request, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.CREATED.getStatusCode());
    assertThat(response.getEntity()).isEqualTo(serviceResponse);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetEntityLink_found_returnsOk() {
    EntityLinkResponse serviceResponse = buildResponse();
    when(entityLinkService.getLink(ACCOUNT_ID, ENTITY_REF)).thenReturn(serviceResponse);

    Response response = entityLinksApi.getEntityLink(SCOPE, KIND, IDENTIFIER, ACCOUNT_ID, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(response.getEntity()).isEqualTo(serviceResponse);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetEntityLink_notFound_returns404() {
    when(entityLinkService.getLink(ACCOUNT_ID, ENTITY_REF)).thenReturn(null);

    Response response = entityLinksApi.getEntityLink(SCOPE, KIND, IDENTIFIER, ACCOUNT_ID, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testUpdateEntityLink_returnsOk() {
    EntityLinkRequest request = buildRequest();
    EntityLinkResponse serviceResponse = buildResponse();
    when(entityLinkService.updateLink(ACCOUNT_ID, ENTITY_REF, request)).thenReturn(serviceResponse);

    Response response = entityLinksApi.updateEntityLink(ACCOUNT_ID, SCOPE, KIND, IDENTIFIER, request, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(response.getEntity()).isEqualTo(serviceResponse);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testDeleteEntityLink_returnsNoContent() {
    doNothing().when(entityLinkService).deleteLink(ACCOUNT_ID, ENTITY_REF);

    Response response = entityLinksApi.deleteEntityLink(SCOPE, KIND, IDENTIFIER, ACCOUNT_ID, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    verify(entityLinkService).deleteLink(ACCOUNT_ID, ENTITY_REF);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testCheckEntityLinkExists_returnsOk() {
    EntityLinkExistsResponse serviceResponse = new EntityLinkExistsResponse();
    serviceResponse.setLinked(true);
    serviceResponse.setMatchingEntitiesCount(2);
    when(entityLinkService.linkExists(ACCOUNT_ID, ENTITY_REF)).thenReturn(serviceResponse);

    Response response = entityLinksApi.checkEntityLinkExists(SCOPE, KIND, IDENTIFIER, ACCOUNT_ID, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    EntityLinkExistsResponse body = (EntityLinkExistsResponse) response.getEntity();
    assertThat(body.isLinked()).isTrue();
    assertThat(body.getMatchingEntitiesCount()).isEqualTo(2);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetLinkedEntities_returnsOk() {
    List<String> refs = List.of("workflow:account/wf1", "workflow:account/wf2");
    when(entityLinkService.getLinkedEntities(ACCOUNT_ID, "component", "service", "component:account/my-comp"))
        .thenReturn(refs);

    Response response =
        entityLinksApi.getLinkedEntities(ACCOUNT_ID, "component", "service", "component:account/my-comp", ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(response.getEntity()).isEqualTo(refs);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testResolveEntityLinkMappings_returnsOk() {
    ResolveFieldMappingsRequest request = new ResolveFieldMappingsRequest();
    ResolveFieldMappingsResponse serviceResponse = new ResolveFieldMappingsResponse();
    serviceResponse.setResolvedValues(List.of());
    when(entityLinkService.resolveFieldMappings(eq(ACCOUNT_ID), eq(SCOPE), eq(KIND), eq(IDENTIFIER), any()))
        .thenReturn(serviceResponse);

    Response response =
        entityLinksApi.resolveEntityLinkMappings(ACCOUNT_ID, SCOPE, KIND, IDENTIFIER, request, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(response.getEntity()).isEqualTo(serviceResponse);
  }

  // ── helpers ──────────────────────────────────────────────────────────────────

  private EntityLinkRequest buildRequest() {
    EntityLink link = new EntityLink();
    link.setEntityRef(ENTITY_REF);
    EntityLinkRequest request = new EntityLinkRequest();
    request.setEntityLink(link);
    return request;
  }

  private EntityLinkResponse buildResponse() {
    EntityLink link = new EntityLink();
    link.setEntityRef(ENTITY_REF);
    EntityLinkResponse response = new EntityLinkResponse();
    response.setEntityLink(link);
    return response;
  }
}
