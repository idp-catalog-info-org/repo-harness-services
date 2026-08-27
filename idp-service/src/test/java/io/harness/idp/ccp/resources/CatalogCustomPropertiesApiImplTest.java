/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.ccp.resources;

import static io.harness.rule.OwnerRule.VIGNESWARA;
import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.ccp.service.CatalogCustomPropertiesService;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.CustomPropertyByEntityDeleteRequest;
import io.harness.spec.server.idp.v1.model.CustomPropertyByEntityGetResponse;
import io.harness.spec.server.idp.v1.model.CustomPropertyByEntityRequest;
import io.harness.spec.server.idp.v1.model.CustomPropertyByFieldDeleteRequest;
import io.harness.spec.server.idp.v1.model.CustomPropertyByFieldDeleteResponse;
import io.harness.spec.server.idp.v1.model.CustomPropertyByFieldGetResponse;
import io.harness.spec.server.idp.v1.model.CustomPropertyByFieldRequest;
import io.harness.spec.server.idp.v1.model.CustomPropertyByFieldResponse;
import io.harness.spec.server.idp.v1.model.CustomPropertyEntitiesCount;
import io.harness.spec.server.idp.v1.model.CustomPropertyFilterDeleteRequest;
import io.harness.spec.server.idp.v1.model.CustomPropertyFilterRequest;
import io.harness.spec.server.idp.v1.model.CustomPropertyResponse;

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
public class CatalogCustomPropertiesApiImplTest extends CategoryTest {
  public static final String TEST_ACCOUNT = "account";
  public static final String RELEASE_VERSION = "metadata.releaseVersion";
  public static final String TEST_ENTITY_REF2 = "component:default/location-service";
  AutoCloseable openMocks;
  @InjectMocks CatalogCustomPropertiesApiImpl catalogCustomPropertiesApi;
  @Mock CatalogCustomPropertiesService ccpService;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testIngestCatalogCustomProperties() {
    CustomPropertyFilterRequest request = new CustomPropertyFilterRequest();
    CustomPropertyByFieldResponse mockResponse = mockResponse();

    request.setProperty(RELEASE_VERSION);
    request.setValue("1.6.0");
    when(ccpService.resolveEntitiesAndUpsertCustomProperties(request, TEST_ACCOUNT, false)).thenReturn(mockResponse);

    Response response = catalogCustomPropertiesApi.ingestCatalogCustomProperties(request, TEST_ACCOUNT, false);

    assertEquals(200, response.getStatus());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testToggleCatalogCustomProperties() {
    catalogCustomPropertiesApi.toggleCatalogCustomProperties(false, TEST_ACCOUNT);
    verify(ccpService).toggleCustomProperties(TEST_ACCOUNT, false);

    catalogCustomPropertiesApi.toggleCatalogCustomProperties(true, TEST_ACCOUNT);
    verify(ccpService).toggleCustomProperties(TEST_ACCOUNT, true);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testDeleteCatalogCustomProperties() {
    CustomPropertyFilterDeleteRequest request = new CustomPropertyFilterDeleteRequest();
    request.setProperty(RELEASE_VERSION);
    CustomPropertyByFieldDeleteResponse mockResponse = mockDeleteResponse();

    when(ccpService.deleteCustomProperties(request, TEST_ACCOUNT, false)).thenReturn(mockResponse);

    Response response = catalogCustomPropertiesApi.deleteCatalogCustomProperties(request, TEST_ACCOUNT, false);

    assertEquals(200, response.getStatus());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetCatalogCustomPropertiesByEntity() {
    when(ccpService.getCustomPropertiesForEntity(TEST_ACCOUNT, TEST_ENTITY_REF2))
        .thenReturn(new CustomPropertyByEntityGetResponse());
    Response response = catalogCustomPropertiesApi.getCatalogCustomPropertiesByEntity(TEST_ENTITY_REF2, TEST_ACCOUNT);
    assertEquals(200, response.getStatus());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testIngestCatalogCustomPropertiesByEntity() {
    CustomPropertyByEntityRequest request = new CustomPropertyByEntityRequest();
    CustomPropertyResponse mockResponse = mockCustomPropertyResponse();

    request.setEntityRef(TEST_ENTITY_REF2);
    request.setProperty(RELEASE_VERSION);
    request.setValue("1.6.0");
    when(ccpService.resolveCustomPropertiesForEntity(request, TEST_ACCOUNT, false)).thenReturn(mockResponse);

    Response response = catalogCustomPropertiesApi.ingestCatalogCustomPropertiesByEntity(request, TEST_ACCOUNT, false);
    assertEquals(200, response.getStatus());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testIngestEntitiesByCatalogCustomProperty() {
    CustomPropertyByFieldRequest request = new CustomPropertyByFieldRequest();
    CustomPropertyResponse mockResponse = mockCustomPropertyResponse();

    request.setEntityRef(TEST_ENTITY_REF2);
    request.setProperty(RELEASE_VERSION);
    request.setValue("1.6.0");
    when(ccpService.resolveEntitiesForCustomProperty(request, TEST_ACCOUNT, false)).thenReturn(mockResponse);

    Response response = catalogCustomPropertiesApi.ingestEntitiesByCatalogCustomProperty(request, TEST_ACCOUNT, false);
    assertEquals(200, response.getStatus());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testDeleteCatalogCustomPropertiesByEntity() {
    CustomPropertyByEntityDeleteRequest request = new CustomPropertyByEntityDeleteRequest();
    when(ccpService.deleteCustomPropertiesForEntity(request, TEST_ACCOUNT, false))
        .thenReturn(mockCustomPropertyResponse());

    Response response = catalogCustomPropertiesApi.deleteCatalogCustomPropertiesByEntity(request, TEST_ACCOUNT, false);
    assertEquals(200, response.getStatus());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetEntitiesByCatalogCustomProperty() {
    when(ccpService.getCustomPropertiesForCustomProperty(TEST_ACCOUNT, RELEASE_VERSION))
        .thenReturn(new CustomPropertyByFieldGetResponse());
    Response response = catalogCustomPropertiesApi.getEntitiesByCatalogCustomProperty(RELEASE_VERSION, TEST_ACCOUNT);
    assertEquals(200, response.getStatus());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testDeleteEntitiesByCatalogCustomProperty() {
    CustomPropertyByFieldDeleteRequest request = new CustomPropertyByFieldDeleteRequest();
    when(ccpService.deleteEntitiesForCustomProperty(request, TEST_ACCOUNT, false))
        .thenReturn(mockCustomPropertyResponse());

    Response response = catalogCustomPropertiesApi.deleteEntitiesByCatalogCustomProperty(request, TEST_ACCOUNT, false);
    assertEquals(200, response.getStatus());
  }

  private CustomPropertyByFieldDeleteResponse mockDeleteResponse() {
    CustomPropertyByFieldDeleteResponse response = new CustomPropertyByFieldDeleteResponse();
    response.setProperty(RELEASE_VERSION);
    CustomPropertyEntitiesCount entitiesWithDeletions = new CustomPropertyEntitiesCount();
    entitiesWithDeletions.setCount(1);
    response.setEntitiesWithDeletion(entitiesWithDeletions);
    return response;
  }

  private CustomPropertyByFieldResponse mockResponse() {
    CustomPropertyByFieldResponse response = new CustomPropertyByFieldResponse();
    response.setProperty(RELEASE_VERSION);
    CustomPropertyEntitiesCount entitiesWithAdditions = new CustomPropertyEntitiesCount();
    entitiesWithAdditions.setCount(1);
    CustomPropertyEntitiesCount entitiesWithUpdates = new CustomPropertyEntitiesCount();
    entitiesWithUpdates.setCount(2);
    response.setEntitiesWithAdditions(entitiesWithAdditions);
    response.setEntitiesWithUpdates(entitiesWithUpdates);
    return response;
  }

  private CustomPropertyResponse mockCustomPropertyResponse() {
    CustomPropertyResponse response = new CustomPropertyResponse();
    response.setStatus(CustomPropertyResponse.StatusEnum.SUCCESS);
    response.setMessage("Saved successfully");
    return response;
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}
