/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.resources;

import static io.harness.rule.OwnerRule.CHRISTIAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.catalog.beans.GetEntityVersionsDTO;
import io.harness.idp.catalog.beans.Kind;
import io.harness.idp.catalog.service.CatalogService;
import io.harness.idp.catalog.service.CatalogVersionService;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.EntityVersionCreateRequest;
import io.harness.spec.server.idp.v1.model.EntityVersionResponse;
import io.harness.spec.server.idp.v1.model.EntityVersionUpdateRequest;

import java.util.List;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class EntityVersionsApiImplTest extends CategoryTest {
  public static final String TEST_ACCOUNT_IDENTIFIER = "testAccount123";
  public static final String TEST_ORG_IDENTIFIER = "testOrg123";
  public static final String TEST_PROJECT_IDENTIFIER = "testProject123";
  public static final String TEST_IDENTIFIER = "testIdentifier123";
  public static final String TEST_VERSION = "v1";
  public static final String TEST_SCOPE = "account"
      + "." + TEST_ORG_IDENTIFIER + "." + TEST_PROJECT_IDENTIFIER;
  public static final String ENTITY_ID = "entityId";
  public static final String ENTITY_YAML = "apiVersion: harness.io/v1\n"
      + "kind: environmentblueprint\n"
      + "type: ''\n"
      + "identifier: testIdentifier123\n"
      + "name: testIdentifier123\n"
      + "owner: group:account/_account_all_users\n"
      + "description: 'This is a test environment blueprint.'\n"
      + "spec:\n"
      + "  entities:\n"
      + "  - identifier: git\n"
      + "    backend:\n"
      + "      type: HarnessCD\n"
      + "      steps:\n"
      + "        apply:\n"
      + "          pipeline: gittest\n"
      + "          branch: main\n"
      + "        destroy:\n"
      + "          pipeline: gittest\n"
      + "          branch: not-main\n"
      + "  ownedBy:\n"
      + "  - group:account/_account_all_users\n";

  @Mock CatalogService catalogService;

  @Mock CatalogVersionService catalogVersionService;

  @InjectMocks EntitiesApiImpl entityVersionsApi;

  AutoCloseable openMocks;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }

  @Test
  @Owner(developers = CHRISTIAN)
  @Category(UnitTests.class)
  public void testCreateEntityVersion() {
    EntityVersionCreateRequest entityVersionRequest = new EntityVersionCreateRequest();
    EntityVersionResponse entityVersionResponse = new EntityVersionResponse();

    EntityVersionCreateRequest entityVersionCreateRequest = new EntityVersionCreateRequest();
    entityVersionCreateRequest.setYaml(ENTITY_YAML);
    entityVersionCreateRequest.setVersion(TEST_VERSION);

    when(catalogService.createEntity(any(), any(), any(), any(), any(), any(), any(), anyBoolean()))
        .thenReturn(Pair.of(null, entityVersionResponse));

    Response response = entityVersionsApi.createEntityVersion(
        entityVersionRequest, TEST_ACCOUNT_IDENTIFIER, TEST_ORG_IDENTIFIER, TEST_PROJECT_IDENTIFIER);

    verify(catalogService, times(1)).createEntity(any(), any(), any(), any(), any(), any(), any(), anyBoolean());
    assertThat(response.getStatus()).isEqualTo(Response.Status.CREATED.getStatusCode());
    assertThat(response.getEntity()).isEqualTo(entityVersionResponse);
  }

  @Test
  @Owner(developers = CHRISTIAN)
  @Category(UnitTests.class)
  public void testUpdateEntityVersion() {
    EntityVersionUpdateRequest entityVersionUpdateRequest = new EntityVersionUpdateRequest();
    EntityVersionResponse entityVersionResponse = new EntityVersionResponse();

    when(catalogService.updateEntity(any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean(), any(),
             anyBoolean(), any(), anyBoolean()))
        .thenReturn(Pair.of(null, entityVersionResponse));

    Response response =
        entityVersionsApi.updateEntityVersion(entityVersionUpdateRequest, TEST_SCOPE, Kind.environmentblueprint.name(),
            TEST_IDENTIFIER, TEST_VERSION, TEST_ACCOUNT_IDENTIFIER, TEST_ORG_IDENTIFIER, TEST_PROJECT_IDENTIFIER);

    verify(catalogService, times(1))
        .updateEntity(any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean(), any(), anyBoolean(),
            any(), anyBoolean());

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(response.getEntity()).isEqualTo(entityVersionResponse);
  }

  @Test
  @Owner(developers = CHRISTIAN)
  @Category(UnitTests.class)
  public void testGetEntity() {
    EntityVersionResponse entityVersionResponse = new EntityVersionResponse();
    when(catalogVersionService.getEntityVersion(TEST_ACCOUNT_IDENTIFIER, TEST_ORG_IDENTIFIER, TEST_PROJECT_IDENTIFIER,
             TEST_SCOPE, Kind.environmentblueprint.name(), TEST_IDENTIFIER, TEST_VERSION))
        .thenReturn(entityVersionResponse);

    Response response = entityVersionsApi.getEntityVersion(TEST_SCOPE, Kind.environmentblueprint.name(),
        TEST_IDENTIFIER, TEST_VERSION, TEST_ACCOUNT_IDENTIFIER, TEST_ORG_IDENTIFIER, TEST_PROJECT_IDENTIFIER);

    verify(catalogVersionService, times(1))
        .getEntityVersion(TEST_ACCOUNT_IDENTIFIER, TEST_ORG_IDENTIFIER, TEST_PROJECT_IDENTIFIER, TEST_SCOPE,
            Kind.environmentblueprint.name(), TEST_IDENTIFIER, TEST_VERSION);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(response.getEntity()).isEqualTo(entityVersionResponse);
  }

  @Test
  @Owner(developers = CHRISTIAN)
  @Category(UnitTests.class)
  public void testGetEntityVersions() {
    GetEntityVersionsDTO getEntityVersionsDTO =
        GetEntityVersionsDTO.builder().pageNumber(0).totalElements(1).entityVersionResponses(List.of()).build();
    when(catalogVersionService.getEntityVersions(eq(TEST_ACCOUNT_IDENTIFIER), eq(TEST_ORG_IDENTIFIER),
             eq(TEST_PROJECT_IDENTIFIER), eq(TEST_SCOPE), eq(Kind.environmentblueprint.name()), eq(TEST_IDENTIFIER),
             eq(0), eq(10), eq(null), eq(null)))
        .thenReturn(getEntityVersionsDTO);
    Response response = entityVersionsApi.getEntityVersions(TEST_SCOPE, Kind.environmentblueprint.name(),
        TEST_IDENTIFIER, TEST_ORG_IDENTIFIER, TEST_PROJECT_IDENTIFIER, TEST_ACCOUNT_IDENTIFIER, 0, 10, null, null);
    verify(catalogVersionService, times(1))
        .getEntityVersions(TEST_ACCOUNT_IDENTIFIER, TEST_ORG_IDENTIFIER, TEST_PROJECT_IDENTIFIER, TEST_SCOPE,
            Kind.environmentblueprint.name(), TEST_IDENTIFIER, 0, 10, null, null);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }
}
