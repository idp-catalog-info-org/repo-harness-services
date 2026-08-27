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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.NamespaceInfo;

import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class AccountInfoApiImplTest extends CategoryTest {
  static final String TEST_ACCOUNT_IDENTIFIER = "testAccountId";
  static final String TEST_NAMESPACE = "testNamespace";

  @Mock IdpCommonService idpCommonService;

  @Mock NamespaceService namespaceService;

  AccountInfoApiImpl accountInfoApiImpl;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    accountInfoApiImpl = new AccountInfoApiImpl(idpCommonService, namespaceService);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetAccountForNamespaceSuccess() {
    doNothing().when(idpCommonService).checkUserAuthorization();
    NamespaceInfo namespaceInfo = new NamespaceInfo();
    namespaceInfo.setNamespace(TEST_NAMESPACE);
    namespaceInfo.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    when(namespaceService.getAccountIdForNamespace(TEST_NAMESPACE)).thenReturn(namespaceInfo);

    Response response = accountInfoApiImpl.getAccountForNamespace(TEST_NAMESPACE);

    assertNotNull(response);
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    NamespaceInfo result = (NamespaceInfo) response.getEntity();
    assertEquals(TEST_NAMESPACE, result.getNamespace());
    assertEquals(TEST_ACCOUNT_IDENTIFIER, result.getAccountIdentifier());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetAccountForNamespaceException() {
    doNothing().when(idpCommonService).checkUserAuthorization();
    when(namespaceService.getAccountIdForNamespace(TEST_NAMESPACE))
        .thenThrow(new RuntimeException("Error fetching account"));

    Response response = accountInfoApiImpl.getAccountForNamespace(TEST_NAMESPACE);

    assertNotNull(response);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetAccountForNamespaceWithNullNamespace() {
    doNothing().when(idpCommonService).checkUserAuthorization();
    NamespaceInfo namespaceInfo = new NamespaceInfo();
    namespaceInfo.setNamespace(null);
    namespaceInfo.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    when(namespaceService.getAccountIdForNamespace(null)).thenReturn(namespaceInfo);

    Response response = accountInfoApiImpl.getAccountForNamespace(null);

    assertNotNull(response);
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
  }
}
