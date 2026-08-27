/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.proxy.environments;

import static io.harness.rule.OwnerRule.NISARG;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eraro.ResponseMessage;
import io.harness.idp.proxy.environments.resource.EnvironmentProxyApiImpl;
import io.harness.idp.proxy.environments.service.EnvironmentProxyService;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.EnvironmentProxyCreateRequest;
import io.harness.spec.server.idp.v1.model.EnvironmentProxyUpdateRequest;

import java.util.HashMap;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class EnvironmentProxyApiImplTest extends CategoryTest {
  private static final String ACCOUNT_IDENTIFIER = "test-account";
  private static final String ORG_IDENTIFIER = "test-org";
  private static final String PROJECT_IDENTIFIER = "test-project";
  private static final String ENVIRONMENT_ID = "test-env-id";

  AutoCloseable openMocks;

  @Mock EnvironmentProxyService environmentProxyService;
  @InjectMocks EnvironmentProxyApiImpl environmentProxyApiImpl;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testCreateCompileAndExecuteEnvironmentSuccess() {
    EnvironmentProxyCreateRequest request = new EnvironmentProxyCreateRequest();
    request.setEnvironmentIdentifier("env-1");
    request.setEnvironmentName("Test Environment");
    Map<String, Object> expectedResponse = new HashMap<>();
    expectedResponse.put("status", "success");

    when(environmentProxyService.createCompileAndExecuteEnvironment(
             eq(request), eq(ACCOUNT_IDENTIFIER), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER)))
        .thenReturn(expectedResponse);

    Response response = environmentProxyApiImpl.createCompileAndExecuteEnvironment(
        request, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);

    assertEquals(200, response.getStatus());
    assertEquals(expectedResponse, response.getEntity());
    verify(environmentProxyService)
        .createCompileAndExecuteEnvironment(request, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testCreateCompileAndExecuteEnvironmentFailure() {
    EnvironmentProxyCreateRequest request = new EnvironmentProxyCreateRequest();
    String errorMessage = "Failed to create environment";

    when(environmentProxyService.createCompileAndExecuteEnvironment(
             any(EnvironmentProxyCreateRequest.class), anyString(), anyString(), anyString()))
        .thenThrow(new RuntimeException(errorMessage));

    Response response = environmentProxyApiImpl.createCompileAndExecuteEnvironment(
        request, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);

    assertEquals(500, response.getStatus());
    ResponseMessage responseMessage = (ResponseMessage) response.getEntity();
    assertEquals(errorMessage, responseMessage.getMessage());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testUpdateCompileAndExecuteEnvironmentSuccess() {
    EnvironmentProxyUpdateRequest request = new EnvironmentProxyUpdateRequest();
    request.setOverrides("key: value");
    Map<String, Object> expectedResponse = new HashMap<>();
    expectedResponse.put("status", "updated");

    when(environmentProxyService.updateCompileAndExecuteEnvironment(
             eq(ENVIRONMENT_ID), eq(request), eq(ACCOUNT_IDENTIFIER), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER)))
        .thenReturn(expectedResponse);

    Response response = environmentProxyApiImpl.updateCompileAndExecuteEnvironment(
        ENVIRONMENT_ID, request, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);

    assertEquals(200, response.getStatus());
    assertEquals(expectedResponse, response.getEntity());
    verify(environmentProxyService)
        .updateCompileAndExecuteEnvironment(
            ENVIRONMENT_ID, request, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testUpdateCompileAndExecuteEnvironmentFailure() {
    EnvironmentProxyUpdateRequest request = new EnvironmentProxyUpdateRequest();
    String errorMessage = "Failed to update environment";

    when(environmentProxyService.updateCompileAndExecuteEnvironment(
             anyString(), any(EnvironmentProxyUpdateRequest.class), anyString(), anyString(), anyString()))
        .thenThrow(new RuntimeException(errorMessage));

    Response response = environmentProxyApiImpl.updateCompileAndExecuteEnvironment(
        ENVIRONMENT_ID, request, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);

    assertEquals(500, response.getStatus());
    ResponseMessage responseMessage = (ResponseMessage) response.getEntity();
    assertEquals(errorMessage, responseMessage.getMessage());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testDeleteEnvironmentSuccess() {
    Response response = environmentProxyApiImpl.deleteEnvironment(
        ENVIRONMENT_ID, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);

    assertEquals(200, response.getStatus());
    verify(environmentProxyService)
        .deleteEnvironment(ENVIRONMENT_ID, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testDeleteEnvironmentFailure() {
    String errorMessage = "Failed to delete environment";
    doThrow(new RuntimeException(errorMessage))
        .when(environmentProxyService)
        .deleteEnvironment(anyString(), anyString(), anyString(), anyString());

    Response response = environmentProxyApiImpl.deleteEnvironment(
        ENVIRONMENT_ID, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);

    assertEquals(500, response.getStatus());
    ResponseMessage responseMessage = (ResponseMessage) response.getEntity();
    assertEquals(errorMessage, responseMessage.getMessage());
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}
