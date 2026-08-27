/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.backstage.resources;

import static io.harness.rule.OwnerRule.AGNIVA;
import static io.harness.rule.OwnerRule.SATHISH;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.backstage.service.BackstageService;
import io.harness.idp.common.IdpCommonService;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.BackstageHarnessResolveExpressionsRequest;
import io.harness.spec.server.idp.v1.model.BackstageHarnessResolveExpressionsResponse;
import io.harness.spec.server.idp.v1.model.BackstageHarnessSyncRequest;
import io.harness.spec.server.idp.v1.model.User;

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
public class BackstageHarnessApiImplTest extends CategoryTest {
  static final String TEST_ACCOUNT_IDENTIFIER = "testAccount123";
  static final String TEST_ENTITY_IDENTIFIER = "testEntity123";
  static final String TEST_USER_IDENTIFIER = "testUser123";
  static final String TEST_USER_EMAIL = "testEmail123";
  static final String TEST_USER_NAME = "testName123";

  AutoCloseable openMocks;
  @InjectMocks BackstageHarnessApiImpl backstageHarnessApi;
  @Mock IdpCommonService idpCommonService;
  @Mock BackstageService backstageService;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testBackstageHarnessSyncAllAccounts() {
    doNothing().when(idpCommonService).checkUserAuthorization();
    doNothing().when(backstageService).sync();
    Response response = backstageHarnessApi.backstageHarnessSyncAllAccounts();
    assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testBackstageHarnessSyncForAccount() {
    doNothing().when(idpCommonService).checkUserAuthorization();
    when(backstageService.sync(TEST_ACCOUNT_IDENTIFIER)).thenReturn(true);
    Response response = backstageHarnessApi.backstageHarnessSyncForAccount(TEST_ACCOUNT_IDENTIFIER);
    assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testBackstageHarnessSyncForAccountErrorResponse() {
    doNothing().when(idpCommonService).checkUserAuthorization();
    when(backstageService.sync(TEST_ACCOUNT_IDENTIFIER)).thenReturn(false);
    Response response = backstageHarnessApi.backstageHarnessSyncForAccount(TEST_ACCOUNT_IDENTIFIER);
    assertEquals(response.getStatus(), Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  @Test
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testBackstageHarnessResolveExpressions() {
    String testEntity = "sampleEntity";
    String resolvedEntity = "resolvedEntity";
    BackstageHarnessResolveExpressionsRequest request =
        new BackstageHarnessResolveExpressionsRequest().entity(testEntity);
    when(backstageService.resolveExpressions(testEntity, TEST_ACCOUNT_IDENTIFIER)).thenReturn(resolvedEntity);
    Response response = backstageHarnessApi.backstageHarnessResolveExpressions(request, TEST_ACCOUNT_IDENTIFIER);
    assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
    BackstageHarnessResolveExpressionsResponse responseBody =
        (BackstageHarnessResolveExpressionsResponse) response.getEntity();
    assertEquals(responseBody.getEntity(), resolvedEntity);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testBackstageHarnessSyncForAccountEntity() {
    doNothing().when(idpCommonService).checkUserAuthorization();
    when(backstageService.syncByType(TEST_ACCOUNT_IDENTIFIER, BackstageHarnessSyncRequest.TypeEnum.ENTITY,
             TEST_ENTITY_IDENTIFIER, BackstageHarnessSyncRequest.ActionEnum.CREATE.value(),
             BackstageHarnessSyncRequest.SyncModeEnum.ASYNC.value(), user()))
        .thenReturn(true);
    Response response = backstageHarnessApi.backstageHarnessSyncForAccountEntity(
        backstageHarnessSyncRequest(), TEST_ACCOUNT_IDENTIFIER);
    assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testBackstageHarnessSyncForAccountEntityErrorResponse() {
    doNothing().when(idpCommonService).checkUserAuthorization();
    when(backstageService.syncByType(TEST_ACCOUNT_IDENTIFIER, BackstageHarnessSyncRequest.TypeEnum.ENTITY,
             TEST_ENTITY_IDENTIFIER, BackstageHarnessSyncRequest.ActionEnum.CREATE.value(),
             BackstageHarnessSyncRequest.SyncModeEnum.ASYNC.value(), user()))
        .thenReturn(false);
    Response response = backstageHarnessApi.backstageHarnessSyncForAccountEntity(
        backstageHarnessSyncRequest(), TEST_ACCOUNT_IDENTIFIER);
    assertEquals(response.getStatus(), Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }

  private BackstageHarnessSyncRequest backstageHarnessSyncRequest() {
    return new BackstageHarnessSyncRequest()
        .identifier(TEST_ENTITY_IDENTIFIER)
        .type(BackstageHarnessSyncRequest.TypeEnum.ENTITY)
        .syncMode(BackstageHarnessSyncRequest.SyncModeEnum.ASYNC)
        .action(BackstageHarnessSyncRequest.ActionEnum.CREATE)
        .user(user());
  }

  private User user() {
    return new User().uuid(TEST_USER_IDENTIFIER).email(TEST_USER_EMAIL).name(TEST_USER_NAME);
  }
}
