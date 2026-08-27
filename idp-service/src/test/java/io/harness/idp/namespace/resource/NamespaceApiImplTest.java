/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.namespace.resource;

import static io.harness.rule.OwnerRule.KOTA_KARTHIK;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.namespace.beans.entity.NamespaceEntity;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.NamespaceInfo;
import io.harness.spec.server.idp.v1.model.NamespaceMetadata;
import io.harness.spec.server.idp.v1.model.NamespaceRequest;

import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DuplicateKeyException;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class NamespaceApiImplTest extends CategoryTest {
  static final String TEST_ACCOUNT_IDENTIFIER = "testAccountId";
  static final String TEST_NAMESPACE = "testNamespace";

  @Mock IdpCommonService idpCommonService;

  @Mock NamespaceService namespaceService;

  NamespaceApiImpl namespaceApiImpl;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    namespaceApiImpl = new NamespaceApiImpl(idpCommonService, namespaceService);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testCreateNamespaceSuccess() {
    doNothing().when(idpCommonService).checkUserAuthorization();
    NamespaceEntity savedEntity =
        NamespaceEntity.builder().id(TEST_NAMESPACE).accountIdentifier(TEST_ACCOUNT_IDENTIFIER).build();
    when(namespaceService.saveAccountIdNamespace(TEST_ACCOUNT_IDENTIFIER)).thenReturn(savedEntity);

    Response response = namespaceApiImpl.createNamespace(TEST_ACCOUNT_IDENTIFIER);

    assertNotNull(response);
    assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
    NamespaceInfo namespaceInfo = (NamespaceInfo) response.getEntity();
    assertEquals(TEST_NAMESPACE, namespaceInfo.getNamespace());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testCreateNamespaceDuplicateKey() {
    doNothing().when(idpCommonService).checkUserAuthorization();
    when(namespaceService.saveAccountIdNamespace(TEST_ACCOUNT_IDENTIFIER))
        .thenThrow(new DuplicateKeyException("Duplicate key"));

    Response response = namespaceApiImpl.createNamespace(TEST_ACCOUNT_IDENTIFIER);

    assertNotNull(response);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testCreateNamespaceGenericException() {
    doNothing().when(idpCommonService).checkUserAuthorization();
    when(namespaceService.saveAccountIdNamespace(TEST_ACCOUNT_IDENTIFIER))
        .thenThrow(new RuntimeException("Generic error"));

    Response response = namespaceApiImpl.createNamespace(TEST_ACCOUNT_IDENTIFIER);

    assertNotNull(response);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetNamespaceInfoSuccess() {
    doNothing().when(idpCommonService).checkUserAuthorization();
    NamespaceInfo namespaceInfo = new NamespaceInfo();
    namespaceInfo.setNamespace(TEST_NAMESPACE);
    namespaceInfo.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    when(namespaceService.getNamespaceForAccountIdentifier(TEST_ACCOUNT_IDENTIFIER)).thenReturn(namespaceInfo);

    Response response = namespaceApiImpl.getNamespaceInfo(TEST_ACCOUNT_IDENTIFIER);

    assertNotNull(response);
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    NamespaceInfo result = (NamespaceInfo) response.getEntity();
    assertEquals(TEST_NAMESPACE, result.getNamespace());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetNamespaceInfoException() {
    doNothing().when(idpCommonService).checkUserAuthorization();
    when(namespaceService.getNamespaceForAccountIdentifier(TEST_ACCOUNT_IDENTIFIER))
        .thenThrow(new RuntimeException("Error fetching namespace"));

    Response response = namespaceApiImpl.getNamespaceInfo(TEST_ACCOUNT_IDENTIFIER);

    assertNotNull(response);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testUpdateNamespaceInfoSuccess() {
    NamespaceInfo namespaceInfo = new NamespaceInfo();
    namespaceInfo.setNamespace(TEST_NAMESPACE);
    namespaceInfo.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    when(namespaceService.update(anyString(), any(NamespaceRequest.class))).thenReturn(namespaceInfo);

    NamespaceRequest request = new NamespaceRequest();
    NamespaceMetadata metadata = new NamespaceMetadata();
    metadata.setPostgresIdpV2MigrationCompleted(true);
    request.setMetadata(metadata);

    Response response = namespaceApiImpl.updateNamespaceInfo(request, TEST_ACCOUNT_IDENTIFIER);

    assertNotNull(response);
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    NamespaceInfo result = (NamespaceInfo) response.getEntity();
    assertEquals(TEST_NAMESPACE, result.getNamespace());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testUpdateNamespaceInfoException() {
    when(namespaceService.update(anyString(), any(NamespaceRequest.class)))
        .thenThrow(new RuntimeException("Error updating namespace"));

    NamespaceRequest request = new NamespaceRequest();
    request.setMetadata(new NamespaceMetadata());

    Response response = namespaceApiImpl.updateNamespaceInfo(request, TEST_ACCOUNT_IDENTIFIER);

    assertNotNull(response);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
  }
}
