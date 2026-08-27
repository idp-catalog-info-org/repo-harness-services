/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.proxy.environments;

import static io.harness.rule.OwnerRule.NISARG;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.clients.POServerClientUtils;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.service.CatalogService;
import io.harness.idp.proxy.environments.service.EnvironmentProxyServiceImpl;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.EntityCreateRequest;
import io.harness.spec.server.idp.v1.model.EntityUpdateRequest;
import io.harness.spec.server.idp.v1.model.EnvironmentProxyCreateRequest;
import io.harness.spec.server.idp.v1.model.EnvironmentProxyUpdateRequest;
import io.harness.springdata.TransactionHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class EnvironmentProxyServiceImplTest extends CategoryTest {
  private static final String ACCOUNT_IDENTIFIER = "test-account";
  private static final String ORG_IDENTIFIER = "test-org";
  private static final String PROJECT_IDENTIFIER = "test-project";
  private static final String ENVIRONMENT_ID = "test-env-id";
  private static final String ENVIRONMENT_NAME = "Test Environment";
  private static final String ENVIRONMENT_BLUEPRINT_ID = "blueprint-1";
  private static final String OWNER = "test-owner";

  AutoCloseable openMocks;

  @Mock POServerClientUtils poserverClientUtils;
  @Mock CatalogService catalogService;
  @Mock TransactionHelper transactionHelper;

  @InjectMocks EnvironmentProxyServiceImpl environmentProxyService;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testCreateCompileAndExecuteEnvironmentSuccess() {
    EnvironmentProxyCreateRequest request = new EnvironmentProxyCreateRequest();
    request.setEnvironmentIdentifier(ENVIRONMENT_ID);
    request.setEnvironmentName(ENVIRONMENT_NAME);
    request.setEnvironmentBlueprintIdentifier(ENVIRONMENT_BLUEPRINT_ID);
    request.setOwner(OWNER);

    Map<String, Object> compileResponse = new HashMap<>();
    compileResponse.put("compiled", true);

    Map<String, Object> executeResponse = new HashMap<>();
    executeResponse.put("status", "success");

    when(poserverClientUtils.compile(anyString(), eq(ACCOUNT_IDENTIFIER), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER)))
        .thenReturn(compileResponse);
    when(poserverClientUtils.execute(any(), eq(ACCOUNT_IDENTIFIER), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER)))
        .thenReturn(executeResponse);
    doNothing()
        .when(catalogService)
        .createEntity(
            anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), any(EntityCreateRequest.class));

    Object result = environmentProxyService.createCompileAndExecuteEnvironment(
        request, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);

    assertNotNull(result);
    assertEquals(executeResponse, result);
    verify(poserverClientUtils)
        .compile(anyString(), eq(ACCOUNT_IDENTIFIER), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER));
    verify(poserverClientUtils).execute(any(), eq(ACCOUNT_IDENTIFIER), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER));
    verify(catalogService)
        .createEntity(eq(ACCOUNT_IDENTIFIER), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER), eq(false), eq(false),
            any(EntityCreateRequest.class));
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testCreateCompileAndExecuteEnvironmentWithTags() {
    EnvironmentProxyCreateRequest request = new EnvironmentProxyCreateRequest();
    request.setEnvironmentIdentifier(ENVIRONMENT_ID);
    request.setEnvironmentName(ENVIRONMENT_NAME);
    request.setEnvironmentBlueprintIdentifier(ENVIRONMENT_BLUEPRINT_ID);
    request.setOwner(OWNER);

    List<String> tags = new ArrayList<>();
    tags.add("tag1");
    tags.add("tag2");
    request.setTags(tags);

    Map<String, Object> compileResponse = new HashMap<>();
    Map<String, Object> executeResponse = new HashMap<>();

    when(poserverClientUtils.compile(anyString(), anyString(), anyString(), anyString())).thenReturn(compileResponse);
    when(poserverClientUtils.execute(any(), anyString(), anyString(), anyString())).thenReturn(executeResponse);
    doNothing()
        .when(catalogService)
        .createEntity(
            anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), any(EntityCreateRequest.class));

    Object result = environmentProxyService.createCompileAndExecuteEnvironment(
        request, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);

    assertNotNull(result);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testCreateCompileAndExecuteEnvironmentWithDescription() {
    EnvironmentProxyCreateRequest request = new EnvironmentProxyCreateRequest();
    request.setEnvironmentIdentifier(ENVIRONMENT_ID);
    request.setEnvironmentName(ENVIRONMENT_NAME);
    request.setEnvironmentBlueprintIdentifier(ENVIRONMENT_BLUEPRINT_ID);
    request.setOwner(OWNER);
    request.setDescription("Test description");

    Map<String, Object> compileResponse = new HashMap<>();
    Map<String, Object> executeResponse = new HashMap<>();

    when(poserverClientUtils.compile(anyString(), anyString(), anyString(), anyString())).thenReturn(compileResponse);
    when(poserverClientUtils.execute(any(), anyString(), anyString(), anyString())).thenReturn(executeResponse);
    doNothing()
        .when(catalogService)
        .createEntity(
            anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), any(EntityCreateRequest.class));

    Object result = environmentProxyService.createCompileAndExecuteEnvironment(
        request, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);

    assertNotNull(result);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testCreateCompileAndExecuteEnvironmentWithOverrides() {
    EnvironmentProxyCreateRequest request = new EnvironmentProxyCreateRequest();
    request.setEnvironmentIdentifier(ENVIRONMENT_ID);
    request.setEnvironmentName(ENVIRONMENT_NAME);
    request.setEnvironmentBlueprintIdentifier(ENVIRONMENT_BLUEPRINT_ID);
    request.setOwner(OWNER);
    request.setOverrides("key: value\nanother: data");

    Map<String, Object> compileResponse = new HashMap<>();
    Map<String, Object> executeResponse = new HashMap<>();

    when(poserverClientUtils.compile(anyString(), anyString(), anyString(), anyString())).thenReturn(compileResponse);
    when(poserverClientUtils.execute(any(), anyString(), anyString(), anyString())).thenReturn(executeResponse);
    doNothing()
        .when(catalogService)
        .createEntity(
            anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), any(EntityCreateRequest.class));

    Object result = environmentProxyService.createCompileAndExecuteEnvironment(
        request, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);

    assertNotNull(result);
  }

  @Test(expected = RuntimeException.class)
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testCreateCompileAndExecuteEnvironmentFailure() {
    EnvironmentProxyCreateRequest request = new EnvironmentProxyCreateRequest();
    request.setEnvironmentIdentifier(ENVIRONMENT_ID);
    request.setEnvironmentName(ENVIRONMENT_NAME);
    request.setEnvironmentBlueprintIdentifier(null);

    environmentProxyService.createCompileAndExecuteEnvironment(
        request, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testUpdateCompileAndExecuteEnvironmentSuccess() throws Exception {
    EnvironmentProxyUpdateRequest request = new EnvironmentProxyUpdateRequest();
    request.setOverrides("key: value");

    CatalogEntity catalogEntity = new CatalogEntity();
    String yaml = "apiVersion: backstage.io/v1alpha1\n"
        + "kind: Environment\n"
        + "metadata:\n"
        + "  name: test\n"
        + "spec:\n"
        + "  type: test";
    catalogEntity.setYaml(yaml);

    Map<String, Object> compileResponse = new HashMap<>();
    Map<String, Object> executeResponse = new HashMap<>();

    when(catalogService.getCatalogEntityByParentUniqueIdAndKindAndIdentifier(
             eq(ACCOUNT_IDENTIFIER), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER), eq("environment"), eq(ENVIRONMENT_ID)))
        .thenReturn(catalogEntity);
    when(poserverClientUtils.compile(anyString(), eq(ACCOUNT_IDENTIFIER), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER)))
        .thenReturn(compileResponse);
    when(poserverClientUtils.execute(any(), eq(ACCOUNT_IDENTIFIER), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER)))
        .thenReturn(executeResponse);
    doNothing()
        .when(catalogService)
        .updateEntity(anyString(), anyString(), anyString(), anyString(), any(EntityUpdateRequest.class), anyBoolean(),
            anyBoolean(), anyBoolean(), anyBoolean());

    Object result = environmentProxyService.updateCompileAndExecuteEnvironment(
        ENVIRONMENT_ID, request, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);

    assertNotNull(result);
    assertEquals(executeResponse, result);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testUpdateCompileAndExecuteEnvironmentWithTargetState() throws Exception {
    EnvironmentProxyUpdateRequest request = new EnvironmentProxyUpdateRequest();
    request.setTargetState("RUNNING");

    CatalogEntity catalogEntity = new CatalogEntity();
    String yaml = "apiVersion: backstage.io/v1alpha1\n"
        + "kind: Environment\n"
        + "metadata:\n"
        + "  name: test\n"
        + "spec:\n"
        + "  type: test";
    catalogEntity.setYaml(yaml);

    Map<String, Object> compileResponse = new HashMap<>();
    Map<String, Object> executeResponse = new HashMap<>();

    when(catalogService.getCatalogEntityByParentUniqueIdAndKindAndIdentifier(
             anyString(), anyString(), anyString(), eq("environment"), anyString()))
        .thenReturn(catalogEntity);
    when(poserverClientUtils.compile(anyString(), anyString(), anyString(), anyString())).thenReturn(compileResponse);
    when(poserverClientUtils.execute(any(), anyString(), anyString(), anyString())).thenReturn(executeResponse);
    doNothing()
        .when(catalogService)
        .updateEntity(anyString(), anyString(), anyString(), anyString(), any(EntityUpdateRequest.class), anyBoolean(),
            anyBoolean(), anyBoolean(), anyBoolean());

    Object result = environmentProxyService.updateCompileAndExecuteEnvironment(
        ENVIRONMENT_ID, request, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);

    assertNotNull(result);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testUpdateCompileAndExecuteEnvironmentWithBothOverridesAndTargetState() throws Exception {
    EnvironmentProxyUpdateRequest request = new EnvironmentProxyUpdateRequest();
    request.setOverrides("key: value");
    request.setTargetState("STOPPED");

    CatalogEntity catalogEntity = new CatalogEntity();
    String yaml = "apiVersion: backstage.io/v1alpha1\n"
        + "kind: Environment\n"
        + "metadata:\n"
        + "  name: test\n"
        + "spec:\n"
        + "  type: test";
    catalogEntity.setYaml(yaml);

    Map<String, Object> compileResponse = new HashMap<>();
    Map<String, Object> executeResponse = new HashMap<>();

    when(catalogService.getCatalogEntityByParentUniqueIdAndKindAndIdentifier(
             anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(catalogEntity);
    when(poserverClientUtils.compile(anyString(), anyString(), anyString(), anyString())).thenReturn(compileResponse);
    when(poserverClientUtils.execute(any(), anyString(), anyString(), anyString())).thenReturn(executeResponse);
    doNothing()
        .when(catalogService)
        .updateEntity(anyString(), anyString(), anyString(), anyString(), any(EntityUpdateRequest.class), anyBoolean(),
            anyBoolean(), anyBoolean(), anyBoolean());

    Object result = environmentProxyService.updateCompileAndExecuteEnvironment(
        ENVIRONMENT_ID, request, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);

    assertNotNull(result);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testDeleteEnvironmentSuccess() {
    doNothing()
        .when(poserverClientUtils)
        .deleteEnvironment(eq(ENVIRONMENT_ID), eq(ACCOUNT_IDENTIFIER), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER));
    doNothing().when(catalogService).deleteEntity(anyString(), anyString(), anyString(), anyString(), anyBoolean());

    environmentProxyService.deleteEnvironment(ENVIRONMENT_ID, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);

    verify(poserverClientUtils)
        .deleteEnvironment(ENVIRONMENT_ID, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);
    verify(catalogService)
        .deleteEntity(eq(ACCOUNT_IDENTIFIER), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER),
            eq("environment:account." + ORG_IDENTIFIER + "." + PROJECT_IDENTIFIER + "/" + ENVIRONMENT_ID), eq(false));
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}
