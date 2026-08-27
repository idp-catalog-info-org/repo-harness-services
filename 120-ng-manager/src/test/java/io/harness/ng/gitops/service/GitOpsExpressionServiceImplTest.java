/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.gitops.service;

import static io.harness.rule.OwnerRule.ASHINSABU;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.cdng.expressionEvaluator.GitOpsExpressionEvaluator;
import io.harness.cdng.expressionEvaluator.GitOpsSecretFunctor;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.expression.common.ExpressionMode;
import io.harness.gitops.models.AgentExpressionRequest;
import io.harness.gitops.models.AgentExpressionResponse;
import io.harness.gitops.models.Expression;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.serviceoverridev2.service.ServiceOverridesServiceV2;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.variable.VariableValueType;
import io.harness.ng.core.variable.dto.StringVariableConfigDTO;
import io.harness.ng.core.variable.dto.VariableDTO;
import io.harness.ng.core.variable.dto.VariableResponseDTO;
import io.harness.ng.core.variable.services.VariableService;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionType;

import software.wings.beans.LocalEncryptionConfig;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.GITOPS)
@RunWith(MockitoJUnitRunner.class)
@Category(UnitTests.class)
public class GitOpsExpressionServiceImplTest extends CategoryTest {
  @Mock private SecretManagerClientService ngSecretService;
  @Mock private CDFeatureFlagHelper cdFeatureFlagHelper;
  @Mock private VariableService variableService;
  @Mock private ScopeInfoService scopeInfoService;
  @Mock private ServiceEntityService serviceEntityService;
  @Mock private EnvironmentService environmentService;
  @Mock private ServiceOverridesServiceV2 serviceOverridesServiceV2;
  @Mock private GitOpsExpressionEvaluator mockExpressionEvaluator;
  @Mock private GitOpsSecretFunctor mockGitOpsSecretFunctor;

  @InjectMocks private GitOpsExpressionServiceImpl gitOpsExpressionService;

  private AgentExpressionRequest validRequest;
  private List<Expression> expressions;

  @Before
  public void setUp() {
    expressions =
        Collections.singletonList(Expression.builder().value("<+secrets.getValue(\"secret1\")>").index(0).build());

    validRequest = AgentExpressionRequest.builder()
                       .accountIdentifier("accountId")
                       .orgIdentifier("orgId")
                       .projectIdentifier("projectId")
                       .application("testApp")
                       .token(123)
                       .expressions(expressions)
                       .context(new HashMap<>())
                       .build();
  }

  @Test
  @Owner(developers = ASHINSABU)
  @Category(UnitTests.class)
  public void testGetExpressionSuccess() {
    // Given
    when(cdFeatureFlagHelper.isEnabled(eq("accountId"), eq(FeatureName.CDS_GITOPS_SECRET_RESOLUTION_ENABLED)))
        .thenReturn(true);
    when(ngSecretService.getEncryptionDetails(any(), any()))
        .thenReturn(Collections.singletonList(
            EncryptedDataDetail.builder()
                .encryptedData(EncryptedRecordData.builder().build())
                .encryptionConfig(LocalEncryptionConfig.builder().encryptionType(EncryptionType.VAULT).build())
                .build()));

    // When
    AgentExpressionResponse response = gitOpsExpressionService.getExpression(validRequest);

    // Then
    assertNotNull(response);
    assertNotNull(response.getExpressions());
    assertEquals(1, response.getExpressions().size());
    assertEquals(0, response.getExpressions().get(0).getIndex());
    assertNotNull(response.getSecrets());
    assertNotNull(response.getEncryptionConfigs());
  }

  @Test
  @Owner(developers = ASHINSABU)
  @Category(UnitTests.class)
  public void testValidateRequestWithNullRequest() {
    // When & Then
    Exception exception = assertThrows(
        io.harness.exception.InvalidArgumentsException.class, () -> { gitOpsExpressionService.getExpression(null); });
    assertEquals("AgentExpressionRequest is empty/null", exception.getMessage());
  }

  @Test
  @Owner(developers = ASHINSABU)
  @Category(UnitTests.class)
  public void testValidateRequestWithNullExpressions() {
    // Given
    AgentExpressionRequest request = AgentExpressionRequest.builder()
                                         .accountIdentifier("accountId")
                                         .orgIdentifier("orgId")
                                         .projectIdentifier("projectId")
                                         .application("testApp")
                                         .token(123)
                                         .expressions(null)
                                         .build();

    // When & Then
    Exception exception = assertThrows(io.harness.exception.InvalidArgumentsException.class,
        () -> { gitOpsExpressionService.getExpression(request); });
    assertEquals("AgentExpressionRequest has null or empty expressions field", exception.getMessage());
  }

  @Test
  @Owner(developers = ASHINSABU)
  @Category(UnitTests.class)
  public void testValidateRequestWithEmptyExpressions() {
    // Given
    AgentExpressionRequest request = AgentExpressionRequest.builder()
                                         .accountIdentifier("accountId")
                                         .orgIdentifier("orgId")
                                         .projectIdentifier("projectId")
                                         .application("testApp")
                                         .token(123)
                                         .expressions(Arrays.asList())
                                         .build();

    // When & Then
    Exception exception = assertThrows(io.harness.exception.InvalidArgumentsException.class,
        () -> { gitOpsExpressionService.getExpression(request); });
    assertEquals("AgentExpressionRequest has null or empty expressions field", exception.getMessage());
  }

  @Test
  @Owner(developers = ASHINSABU)
  @Category(UnitTests.class)
  public void testValidateRequestWithEmptyAccountIdentifier() {
    // Given
    AgentExpressionRequest request = AgentExpressionRequest.builder()
                                         .accountIdentifier("")
                                         .orgIdentifier("orgId")
                                         .projectIdentifier("projectId")
                                         .application("testApp")
                                         .token(123)
                                         .expressions(expressions)
                                         .build();

    // When & Then
    Exception exception = assertThrows(io.harness.exception.InvalidArgumentsException.class,
        () -> { gitOpsExpressionService.getExpression(request); });
    assertEquals("AccountIdentifier cannot be empty", exception.getMessage());
  }

  @Test
  @Owner(developers = ASHINSABU)
  @Category(UnitTests.class)
  public void testValidateRequestWithEmptyOrgIdentifier() {
    // Given
    AgentExpressionRequest request = AgentExpressionRequest.builder()
                                         .accountIdentifier("accountId")
                                         .orgIdentifier("")
                                         .projectIdentifier("projectId")
                                         .application("testApp")
                                         .token(123)
                                         .expressions(expressions)
                                         .build();

    // When & Then
    Exception exception = assertThrows(io.harness.exception.InvalidArgumentsException.class,
        () -> { gitOpsExpressionService.getExpression(request); });
    assertEquals("OrgIdentifier cannot be empty", exception.getMessage());
  }

  @Test
  @Owner(developers = ASHINSABU)
  @Category(UnitTests.class)
  public void testValidateRequestWithEmptyProjectIdentifier() {
    // Given
    AgentExpressionRequest request = AgentExpressionRequest.builder()
                                         .accountIdentifier("accountId")
                                         .orgIdentifier("orgId")
                                         .projectIdentifier("")
                                         .application("testApp")
                                         .token(123)
                                         .expressions(expressions)
                                         .build();

    // When & Then
    Exception exception = assertThrows(io.harness.exception.InvalidArgumentsException.class,
        () -> { gitOpsExpressionService.getExpression(request); });
    assertEquals("ProjectIdentifier cannot be empty", exception.getMessage());
  }

  @Test
  @Owner(developers = ASHINSABU)
  @Category(UnitTests.class)
  public void testValidateRequestWithEmptyApplication_SecretExpressionRequiresScope() {
    // Without application, project-scoped secret expression requires org + project
    AgentExpressionRequest request =
        AgentExpressionRequest.builder()
            .accountIdentifier("accountId")
            .orgIdentifier("")
            .projectIdentifier("")
            .application("")
            .token(123)
            .expressions(expressions) // contains secrets.getValue without account./org. prefix
            .build();

    when(cdFeatureFlagHelper.isEnabled(eq("accountId"), eq(FeatureName.CDS_GITOPS_SECRET_RESOLUTION_ENABLED)))
        .thenReturn(true);

    Exception exception = assertThrows(io.harness.exception.InvalidArgumentsException.class,
        () -> { gitOpsExpressionService.getExpression(request); });
    assertTrue(exception.getMessage().contains("OrgIdentifier cannot be empty for project-scoped expression"));
  }

  @Test
  @Owner(developers = ASHINSABU)
  @Category(UnitTests.class)
  public void testValidateRequestWithDisabledFeatureFlag() {
    // Given
    when(cdFeatureFlagHelper.isEnabled(eq("accountId"), eq(FeatureName.CDS_GITOPS_SECRET_RESOLUTION_ENABLED)))
        .thenReturn(false);

    // When & Then
    Exception exception = assertThrows(io.harness.exception.InvalidRequestException.class,
        () -> { gitOpsExpressionService.getExpression(validRequest); });
  }

  @Test
  @Owner(developers = ASHINSABU)
  @Category(UnitTests.class)
  public void testPrepareContextMapWithNullContext() throws Exception {
    // Given
    Method prepareContextMapMethod =
        GitOpsExpressionServiceImpl.class.getDeclaredMethod("prepareContextMap", Map.class);
    prepareContextMapMethod.setAccessible(true);

    // When
    @SuppressWarnings("unchecked")
    Map<String, Object> result =
        (Map<String, Object>) prepareContextMapMethod.invoke(gitOpsExpressionService, (Map<String, String>) null);

    // Then
    assertNotNull(result);
    assertEquals(0, result.size());
  }

  @Test
  @Owner(developers = ASHINSABU)
  @Category(UnitTests.class)
  public void testPrepareContextMapWithValidContext() throws Exception {
    // Given
    Method prepareContextMapMethod =
        GitOpsExpressionServiceImpl.class.getDeclaredMethod("prepareContextMap", Map.class);
    prepareContextMapMethod.setAccessible(true);

    Map<String, String> inputContext = new HashMap<>();
    inputContext.put("key1", "value1");
    inputContext.put("key2", "value2");

    // When
    @SuppressWarnings("unchecked")
    Map<String, Object> result =
        (Map<String, Object>) prepareContextMapMethod.invoke(gitOpsExpressionService, inputContext);

    // Then
    assertNotNull(result);
    assertEquals(2, result.size());
    assertEquals("value1", result.get("key1"));
    assertEquals("value2", result.get("key2"));
  }

  @Test
  @Owner(developers = ASHINSABU)
  @Category(UnitTests.class)
  public void testEvaluateSingleExpression() throws Exception {
    // Given
    Method evaluateSingleExpressionMethod = GitOpsExpressionServiceImpl.class.getDeclaredMethod(
        "evaluateSingleExpression", Expression.class, Map.class, GitOpsExpressionEvaluator.class);
    evaluateSingleExpressionMethod.setAccessible(true);

    Expression inputExpression = Expression.builder().value("<+secrets.getValue(\"test\")>").index(5).build();
    Map<String, Object> contextMap = new HashMap<>();

    when(mockExpressionEvaluator.renderExpressionWithSecretTracking(eq(5), eq("<+secrets.getValue(\"test\")>"),
             eq(contextMap), eq(ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED)))
        .thenReturn("evaluatedTestValue");
    when(mockExpressionEvaluator.wasExpressionSecretResolution(5)).thenReturn(false);

    // When
    Expression result = (Expression) evaluateSingleExpressionMethod.invoke(
        gitOpsExpressionService, inputExpression, contextMap, mockExpressionEvaluator);

    // Then
    assertNotNull(result);
    assertEquals("evaluatedTestValue", result.getValue());
    assertEquals(5, result.getIndex());
    assertEquals(false, result.isSecret());
  }

  @Test
  @Owner(developers = ASHINSABU)
  @Category(UnitTests.class)
  public void testEvaluateSingleExpression_WithSecret() throws Exception {
    // Given
    Method evaluateSingleExpressionMethod = GitOpsExpressionServiceImpl.class.getDeclaredMethod(
        "evaluateSingleExpression", Expression.class, Map.class, GitOpsExpressionEvaluator.class);
    evaluateSingleExpressionMethod.setAccessible(true);

    Expression inputExpression = Expression.builder().value("<+secrets.getValue(\"dbPassword\")>").index(3).build();
    Map<String, Object> contextMap = new HashMap<>();

    when(mockExpressionEvaluator.renderExpressionWithSecretTracking(eq(3), eq("<+secrets.getValue(\"dbPassword\")>"),
             eq(contextMap), eq(ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED)))
        .thenReturn("{{ secret \"uuid123\" 0 }}");
    when(mockExpressionEvaluator.wasExpressionSecretResolution(3)).thenReturn(true);

    // When
    Expression result = (Expression) evaluateSingleExpressionMethod.invoke(
        gitOpsExpressionService, inputExpression, contextMap, mockExpressionEvaluator);

    // Then
    assertNotNull(result);
    assertEquals("{{ secret \"uuid123\" 0 }}", result.getValue());
    assertEquals(3, result.getIndex());
    assertEquals(true, result.isSecret());
  }

  @Test
  @Owner(developers = ASHINSABU)
  @Category(UnitTests.class)
  public void testCreateExpressionEvaluator() throws Exception {
    Method createEvaluatorMethod = GitOpsExpressionServiceImpl.class.getDeclaredMethod("createExpressionEvaluator",
        AgentExpressionRequest.class, Map.class, Map.class, io.harness.ng.core.environment.beans.Environment.class);
    createEvaluatorMethod.setAccessible(true);

    Map<String, Object> serviceVariables = new HashMap<>();
    Map<String, Object> environmentVariables = new HashMap<>();

    // When
    GitOpsExpressionEvaluator result = (GitOpsExpressionEvaluator) createEvaluatorMethod.invoke(
        gitOpsExpressionService, validRequest, serviceVariables, environmentVariables, null);

    // Then
    assertNotNull(result);
  }

  @Test
  @Owner(developers = ASHINSABU)
  @Category(UnitTests.class)
  public void testGetExpressionWithNullContext() {
    // Given
    when(cdFeatureFlagHelper.isEnabled(eq("accountId"), eq(FeatureName.CDS_GITOPS_SECRET_RESOLUTION_ENABLED)))
        .thenReturn(true);

    AgentExpressionRequest requestWithNullContext = AgentExpressionRequest.builder()
                                                        .accountIdentifier("accountId")
                                                        .orgIdentifier("orgId")
                                                        .projectIdentifier("projectId")
                                                        .application("testApp")
                                                        .token(123)
                                                        .expressions(expressions)
                                                        .context(null)
                                                        .build();

    AgentExpressionResponse response = gitOpsExpressionService.getExpression(requestWithNullContext);

    // Then
    assertNotNull(response);
    assertNotNull(response.getExpressions());
  }

  @Test
  @Owner(developers = ASHINSABU)
  @Category(UnitTests.class)
  public void testGetExpressionWithVariableExpression() {
    // Given
    List<Expression> variableExpressions =
        Collections.singletonList(Expression.builder().value("<+variable.myVar>").index(0).build());

    AgentExpressionRequest requestWithVariable = AgentExpressionRequest.builder()
                                                     .accountIdentifier("accountId")
                                                     .orgIdentifier("orgId")
                                                     .projectIdentifier("projectId")
                                                     .application("testApp")
                                                     .token(123)
                                                     .expressions(variableExpressions)
                                                     .context(new HashMap<>())
                                                     .build();

    when(cdFeatureFlagHelper.isEnabled(eq("accountId"), eq(FeatureName.CDS_GITOPS_SECRET_RESOLUTION_ENABLED)))
        .thenReturn(true);

    ScopeInfo projectScope = ScopeInfo.builder()
                                 .accountIdentifier("accountId")
                                 .orgIdentifier("orgId")
                                 .projectIdentifier("projectId")
                                 .uniqueId("test-unique-id")
                                 .build();

    when(scopeInfoService.getScopeInfo("accountId", "orgId", "projectId")).thenReturn(projectScope);

    VariableDTO variable =
        VariableDTO.builder()
            .identifier("myVar")
            .variableConfig(
                StringVariableConfigDTO.builder().fixedValue("myValue").valueType(VariableValueType.FIXED).build())
            .build();

    VariableResponseDTO variableResponse =
        VariableResponseDTO.builder().variable(variable).createdAt(0L).lastModifiedAt(0L).build();

    when(variableService.get(any(ScopeInfo.class), eq("myVar"))).thenReturn(Optional.of(variableResponse));

    // When
    AgentExpressionResponse response = gitOpsExpressionService.getExpression(requestWithVariable);

    // Then
    assertNotNull(response);
    assertNotNull(response.getExpressions());
    assertEquals(1, response.getExpressions().size());
    assertEquals("myValue", response.getExpressions().get(0).getValue());
    assertEquals(0, response.getExpressions().get(0).getIndex());
  }

  @Test
  @Owner(developers = ASHINSABU)
  @Category(UnitTests.class)
  public void testGetExpressionWithMixedSecretsAndVariables() {
    // Given
    List<Expression> mixedExpressions =
        Arrays.asList(Expression.builder().value("<+variable.appName>").index(0).build(),
            Expression.builder().value("<+secrets.getValue(\"dbPassword\")>").index(1).build(),
            Expression.builder().value("<+variable.account.region>").index(2).build());

    AgentExpressionRequest mixedRequest = AgentExpressionRequest.builder()
                                              .accountIdentifier("accountId")
                                              .orgIdentifier("orgId")
                                              .projectIdentifier("projectId")
                                              .application("testApp")
                                              .token(123)
                                              .expressions(mixedExpressions)
                                              .context(new HashMap<>())
                                              .build();

    when(cdFeatureFlagHelper.isEnabled(eq("accountId"), eq(FeatureName.CDS_GITOPS_SECRET_RESOLUTION_ENABLED)))
        .thenReturn(true);

    // Setup variable mocks
    ScopeInfo projectScope = ScopeInfo.builder()
                                 .accountIdentifier("accountId")
                                 .orgIdentifier("orgId")
                                 .projectIdentifier("projectId")
                                 .uniqueId("test-project-id")
                                 .build();

    ScopeInfo accountScope = ScopeInfo.builder().accountIdentifier("accountId").uniqueId("test-account-id").build();

    when(scopeInfoService.getScopeInfo("accountId", "orgId", "projectId")).thenReturn(projectScope);
    when(scopeInfoService.getScopeInfo("accountId", null, null)).thenReturn(accountScope);

    VariableDTO appNameVar =
        VariableDTO.builder()
            .identifier("appName")
            .variableConfig(
                StringVariableConfigDTO.builder().fixedValue("myApp").valueType(VariableValueType.FIXED).build())
            .build();

    VariableDTO regionVar =
        VariableDTO.builder()
            .identifier("region")
            .variableConfig(
                StringVariableConfigDTO.builder().fixedValue("us-east-1").valueType(VariableValueType.FIXED).build())
            .build();

    when(variableService.get(any(ScopeInfo.class), eq("appName")))
        .thenReturn(
            Optional.of(VariableResponseDTO.builder().variable(appNameVar).createdAt(0L).lastModifiedAt(0L).build()));

    when(variableService.get(any(ScopeInfo.class), eq("region")))
        .thenReturn(
            Optional.of(VariableResponseDTO.builder().variable(regionVar).createdAt(0L).lastModifiedAt(0L).build()));

    when(ngSecretService.getEncryptionDetails(any(), any()))
        .thenReturn(Collections.singletonList(
            EncryptedDataDetail.builder()
                .encryptedData(EncryptedRecordData.builder().build())
                .encryptionConfig(LocalEncryptionConfig.builder().encryptionType(EncryptionType.VAULT).build())
                .build()));

    // When
    AgentExpressionResponse response = gitOpsExpressionService.getExpression(mixedRequest);

    // Then
    assertNotNull(response);
    assertNotNull(response.getExpressions());
    assertEquals(3, response.getExpressions().size());

    // Variable expressions are returned unresolved in this test context
    // In real usage, JEXL would resolve them
    assertNotNull(response.getExpressions().get(0).getValue());
    assertNotNull(response.getExpressions().get(2).getValue());

    // Secret expression should contain template syntax
    assertNotNull(response.getExpressions().get(1).getValue());
  }

  @Test
  @Owner(developers = ASHINSABU)
  @Category(UnitTests.class)
  public void testGetExpressionWithOrgScopedVariable() {
    // Given
    List<Expression> orgVarExpressions =
        Collections.singletonList(Expression.builder().value("<+variable.org.teamName>").index(0).build());

    AgentExpressionRequest requestWithOrgVar = AgentExpressionRequest.builder()
                                                   .accountIdentifier("accountId")
                                                   .orgIdentifier("orgId")
                                                   .projectIdentifier("projectId")
                                                   .application("testApp")
                                                   .token(123)
                                                   .expressions(orgVarExpressions)
                                                   .context(new HashMap<>())
                                                   .build();

    when(cdFeatureFlagHelper.isEnabled(eq("accountId"), eq(FeatureName.CDS_GITOPS_SECRET_RESOLUTION_ENABLED)))
        .thenReturn(true);

    ScopeInfo orgScope =
        ScopeInfo.builder().accountIdentifier("accountId").orgIdentifier("orgId").uniqueId("test-org-id").build();

    when(scopeInfoService.getScopeInfo("accountId", "orgId", null)).thenReturn(orgScope);

    VariableDTO orgVariable =
        VariableDTO.builder()
            .identifier("teamName")
            .variableConfig(
                StringVariableConfigDTO.builder().fixedValue("platformTeam").valueType(VariableValueType.FIXED).build())
            .build();

    when(variableService.get(any(ScopeInfo.class), eq("teamName")))
        .thenReturn(
            Optional.of(VariableResponseDTO.builder().variable(orgVariable).createdAt(0L).lastModifiedAt(0L).build()));

    // When
    AgentExpressionResponse response = gitOpsExpressionService.getExpression(requestWithOrgVar);

    // Then
    assertNotNull(response);
    assertNotNull(response.getExpressions());
    assertEquals(1, response.getExpressions().size());
    // Expression is returned unresolved in this test context
    assertNotNull(response.getExpressions().get(0).getValue());
  }

  @Test
  @Owner(developers = ASHINSABU)
  @Category(UnitTests.class)
  public void testMergeVariables() throws Exception {
    // Given
    Method mergeVariablesMethod =
        GitOpsExpressionServiceImpl.class.getDeclaredMethod("mergeVariables", Map.class, Map.class);
    mergeVariablesMethod.setAccessible(true);

    Map<String, Object> baseVariables = new HashMap<>();
    baseVariables.put("var1", "base1");
    baseVariables.put("var2", "base2");
    baseVariables.put("var3", "base3");

    Map<String, Object> overrideVariables = new HashMap<>();
    overrideVariables.put("var2", "override2");
    overrideVariables.put("var4", "override4");

    // When
    @SuppressWarnings("unchecked")
    Map<String, Object> result =
        (Map<String, Object>) mergeVariablesMethod.invoke(gitOpsExpressionService, baseVariables, overrideVariables);

    // Then
    assertNotNull(result);
    assertEquals(4, result.size());
    assertEquals("base1", result.get("var1"));
    assertEquals("override2", result.get("var2")); // Override wins
    assertEquals("base3", result.get("var3"));
    assertEquals("override4", result.get("var4"));
  }

  @Test
  @Owner(developers = ASHINSABU)
  @Category(UnitTests.class)
  public void testMergeVariablesWithEmptyOverride() throws Exception {
    // Given
    Method mergeVariablesMethod =
        GitOpsExpressionServiceImpl.class.getDeclaredMethod("mergeVariables", Map.class, Map.class);
    mergeVariablesMethod.setAccessible(true);

    Map<String, Object> baseVariables = new HashMap<>();
    baseVariables.put("var1", "base1");
    baseVariables.put("var2", "base2");

    Map<String, Object> emptyOverride = new HashMap<>();

    // When
    @SuppressWarnings("unchecked")
    Map<String, Object> result =
        (Map<String, Object>) mergeVariablesMethod.invoke(gitOpsExpressionService, baseVariables, emptyOverride);

    // Then
    assertNotNull(result);
    assertEquals(2, result.size());
    assertEquals("base1", result.get("var1"));
    assertEquals("base2", result.get("var2"));
  }

  @Test
  @Owner(developers = ASHINSABU)
  @Category(UnitTests.class)
  public void testMergeVariablesWithEmptyBase() throws Exception {
    // Given
    Method mergeVariablesMethod =
        GitOpsExpressionServiceImpl.class.getDeclaredMethod("mergeVariables", Map.class, Map.class);
    mergeVariablesMethod.setAccessible(true);

    Map<String, Object> emptyBase = new HashMap<>();

    Map<String, Object> overrideVariables = new HashMap<>();
    overrideVariables.put("var1", "override1");
    overrideVariables.put("var2", "override2");

    // When
    @SuppressWarnings("unchecked")
    Map<String, Object> result =
        (Map<String, Object>) mergeVariablesMethod.invoke(gitOpsExpressionService, emptyBase, overrideVariables);

    // Then
    assertNotNull(result);
    assertEquals(2, result.size());
    assertEquals("override1", result.get("var1"));
    assertEquals("override2", result.get("var2"));
  }

  @Test
  @Owner(developers = ASHINSABU)
  @Category(UnitTests.class)
  public void testMergeVariablesPriority() throws Exception {
    // Given - test that overrides always win
    Method mergeVariablesMethod =
        GitOpsExpressionServiceImpl.class.getDeclaredMethod("mergeVariables", Map.class, Map.class);
    mergeVariablesMethod.setAccessible(true);

    Map<String, Object> baseVariables = new HashMap<>();
    baseVariables.put("region", "us-east-1");
    baseVariables.put("replicas", "3");

    Map<String, Object> overrideVariables = new HashMap<>();
    overrideVariables.put("region", "us-west-2");
    overrideVariables.put("replicas", "5");

    // When
    @SuppressWarnings("unchecked")
    Map<String, Object> result =
        (Map<String, Object>) mergeVariablesMethod.invoke(gitOpsExpressionService, baseVariables, overrideVariables);

    // Then - all values should be from override
    assertNotNull(result);
    assertEquals(2, result.size());
    assertEquals("us-west-2", result.get("region"));
    assertEquals("5", result.get("replicas"));
  }

  @Test
  @Owner(developers = ASHINSABU)
  @Category(UnitTests.class)
  public void testConvertToVariablesMap() throws Exception {
    // Given
    Method convertToVariablesMapMethod =
        GitOpsExpressionServiceImpl.class.getDeclaredMethod("convertToVariablesMap", List.class);
    convertToVariablesMapMethod.setAccessible(true);

    // Create mock NGVariable list
    List<io.harness.yaml.core.variables.NGVariable> variables =
        Arrays.asList(io.harness.yaml.core.variables.StringNGVariable.builder()
                          .name("var1")
                          .value(io.harness.pms.yaml.ParameterField.createValueField("value1"))
                          .build(),
            io.harness.yaml.core.variables.StringNGVariable.builder()
                .name("var2")
                .value(io.harness.pms.yaml.ParameterField.createValueField("value2"))
                .build());

    // When
    @SuppressWarnings("unchecked")
    Map<String, Object> result =
        (Map<String, Object>) convertToVariablesMapMethod.invoke(gitOpsExpressionService, variables);

    // Then
    assertNotNull(result);
    assertEquals(2, result.size());
    assertNotNull(result.get("var1"));
    assertNotNull(result.get("var2"));
  }

  @Test
  @Owner(developers = ASHINSABU)
  @Category(UnitTests.class)
  public void testConvertToVariablesMapWithEmptyList() throws Exception {
    // Given
    Method convertToVariablesMapMethod =
        GitOpsExpressionServiceImpl.class.getDeclaredMethod("convertToVariablesMap", List.class);
    convertToVariablesMapMethod.setAccessible(true);

    List<io.harness.yaml.core.variables.NGVariable> emptyList = Collections.emptyList();

    // When
    @SuppressWarnings("unchecked")
    Map<String, Object> result =
        (Map<String, Object>) convertToVariablesMapMethod.invoke(gitOpsExpressionService, emptyList);

    // Then
    assertNotNull(result);
    assertEquals(0, result.size());
  }

  @Test
  @Owner(developers = ASHINSABU)
  @Category(UnitTests.class)
  public void testConvertToVariablesMapWithNullList() throws Exception {
    // Given
    Method convertToVariablesMapMethod =
        GitOpsExpressionServiceImpl.class.getDeclaredMethod("convertToVariablesMap", List.class);
    convertToVariablesMapMethod.setAccessible(true);

    // When
    @SuppressWarnings("unchecked")
    Map<String, Object> result =
        (Map<String, Object>) convertToVariablesMapMethod.invoke(gitOpsExpressionService, (List) null);

    // Then
    assertNotNull(result);
    assertEquals(0, result.size());
  }

  // Tests for new scope-validation logic (no-application path)

  @Test
  @Owner(developers = ASHINSABU)
  @Category(UnitTests.class)
  public void testValidateRequest_WithApplication_RequiresOrgAndProject() {
    // When application is present, org + project must be non-empty
    AgentExpressionRequest request = AgentExpressionRequest.builder()
                                         .accountIdentifier("accountId")
                                         .orgIdentifier("")
                                         .projectIdentifier("projectId")
                                         .application("myApp")
                                         .token(123)
                                         .expressions(expressions)
                                         .build();

    Exception exception = assertThrows(io.harness.exception.InvalidArgumentsException.class,
        () -> { gitOpsExpressionService.getExpression(request); });
    assertEquals("OrgIdentifier cannot be empty", exception.getMessage());
  }

  @Test
  @Owner(developers = ASHINSABU)
  @Category(UnitTests.class)
  public void testValidateRequest_WithApplication_RequiresProject() {
    AgentExpressionRequest request = AgentExpressionRequest.builder()
                                         .accountIdentifier("accountId")
                                         .orgIdentifier("orgId")
                                         .projectIdentifier("")
                                         .application("myApp")
                                         .token(123)
                                         .expressions(expressions)
                                         .build();

    Exception exception = assertThrows(io.harness.exception.InvalidArgumentsException.class,
        () -> { gitOpsExpressionService.getExpression(request); });
    assertEquals("ProjectIdentifier cannot be empty", exception.getMessage());
  }

  @Test
  @Owner(developers = ASHINSABU)
  @Category(UnitTests.class)
  public void testValidateRequest_NoApplication_AccountScopedSecretPassesWithoutOrgOrProject() {
    // account-scoped secret (account. prefix) — no org/project required
    List<Expression> accountScopedExpressions = Collections.singletonList(
        Expression.builder().value("<+secrets.getValue(\"account.mySecret\")>").index(0).build());

    AgentExpressionRequest request = AgentExpressionRequest.builder()
                                         .accountIdentifier("accountId")
                                         .orgIdentifier("")
                                         .projectIdentifier("")
                                         .application("")
                                         .token(123)
                                         .expressions(accountScopedExpressions)
                                         .context(new HashMap<>())
                                         .build();

    when(cdFeatureFlagHelper.isEnabled(eq("accountId"), eq(FeatureName.CDS_GITOPS_SECRET_RESOLUTION_ENABLED)))
        .thenReturn(true);
    when(ngSecretService.getEncryptionDetails(any(), any())).thenReturn(Collections.emptyList());

    // Should not throw — account-scoped secret needs no org/project
    AgentExpressionResponse response = gitOpsExpressionService.getExpression(request);
    assertNotNull(response);
  }

  @Test
  @Owner(developers = ASHINSABU)
  @Category(UnitTests.class)
  public void testValidateRequest_NoApplication_OrgScopedSecretRequiresOrg() {
    // org-scoped secret: org. prefix → requires org but not project
    List<Expression> orgScopedExpressions = Collections.singletonList(
        Expression.builder().value("<+secrets.getValue(\"org.teamSecret\")>").index(0).build());

    AgentExpressionRequest missingOrg = AgentExpressionRequest.builder()
                                            .accountIdentifier("accountId")
                                            .orgIdentifier("")
                                            .projectIdentifier("")
                                            .application("")
                                            .token(123)
                                            .expressions(orgScopedExpressions)
                                            .build();

    when(cdFeatureFlagHelper.isEnabled(eq("accountId"), eq(FeatureName.CDS_GITOPS_SECRET_RESOLUTION_ENABLED)))
        .thenReturn(true);

    Exception exception = assertThrows(io.harness.exception.InvalidArgumentsException.class,
        () -> { gitOpsExpressionService.getExpression(missingOrg); });
    assertTrue(exception.getMessage().contains("OrgIdentifier cannot be empty for org-scoped expression"));
  }

  @Test
  @Owner(developers = ASHINSABU)
  @Category(UnitTests.class)
  public void testValidateRequest_NoApplication_OrgScopedSecretPassesWithOrgOnly() {
    // org-scoped secret with org provided but no project — should be fine
    List<Expression> orgScopedExpressions = Collections.singletonList(
        Expression.builder().value("<+secrets.getValue(\"org.teamSecret\")>").index(0).build());

    AgentExpressionRequest request = AgentExpressionRequest.builder()
                                         .accountIdentifier("accountId")
                                         .orgIdentifier("orgId")
                                         .projectIdentifier("")
                                         .application("")
                                         .token(123)
                                         .expressions(orgScopedExpressions)
                                         .context(new HashMap<>())
                                         .build();

    when(cdFeatureFlagHelper.isEnabled(eq("accountId"), eq(FeatureName.CDS_GITOPS_SECRET_RESOLUTION_ENABLED)))
        .thenReturn(true);
    when(ngSecretService.getEncryptionDetails(any(), any())).thenReturn(Collections.emptyList());

    AgentExpressionResponse response = gitOpsExpressionService.getExpression(request);
    assertNotNull(response);
  }

  @Test
  @Owner(developers = ASHINSABU)
  @Category(UnitTests.class)
  public void testValidateRequest_NoApplication_ProjectScopedSecretRequiresOrgAndProject() {
    // bare secret (no account./org. prefix) → project-scoped; requires both org + project
    List<Expression> projectScopedExpressions =
        Collections.singletonList(Expression.builder().value("<+secrets.getValue(\"dbPassword\")>").index(0).build());

    AgentExpressionRequest missingProject = AgentExpressionRequest.builder()
                                                .accountIdentifier("accountId")
                                                .orgIdentifier("orgId")
                                                .projectIdentifier("")
                                                .application("")
                                                .token(123)
                                                .expressions(projectScopedExpressions)
                                                .build();

    when(cdFeatureFlagHelper.isEnabled(eq("accountId"), eq(FeatureName.CDS_GITOPS_SECRET_RESOLUTION_ENABLED)))
        .thenReturn(true);

    Exception exception = assertThrows(io.harness.exception.InvalidArgumentsException.class,
        () -> { gitOpsExpressionService.getExpression(missingProject); });
    assertTrue(exception.getMessage().contains("ProjectIdentifier cannot be empty for project-scoped expression"));
  }

  @Test
  @Owner(developers = ASHINSABU)
  @Category(UnitTests.class)
  public void testValidateRequest_NoApplication_NullExpressionValueSkipped() {
    // null expression value should not cause NPE — must be skipped gracefully
    List<Expression> expressionsWithNull = Arrays.asList(Expression.builder().value(null).index(0).build(),
        Expression.builder().value("<+secrets.getValue(\"account.safe\")>").index(1).build());

    AgentExpressionRequest request = AgentExpressionRequest.builder()
                                         .accountIdentifier("accountId")
                                         .orgIdentifier("")
                                         .projectIdentifier("")
                                         .application("")
                                         .token(123)
                                         .expressions(expressionsWithNull)
                                         .context(new HashMap<>())
                                         .build();

    when(cdFeatureFlagHelper.isEnabled(eq("accountId"), eq(FeatureName.CDS_GITOPS_SECRET_RESOLUTION_ENABLED)))
        .thenReturn(true);
    when(ngSecretService.getEncryptionDetails(any(), any())).thenReturn(Collections.emptyList());

    AgentExpressionResponse response = gitOpsExpressionService.getExpression(request);
    assertNotNull(response);
  }

  @Test
  @Owner(developers = ASHINSABU)
  @Category(UnitTests.class)
  public void testValidateRequest_NoApplication_NonSecretExpressionNeedNoScope() {
    // non-secret expression (no secrets.getValue) without application requires no org/project
    List<Expression> nonSecretExpressions =
        Collections.singletonList(Expression.builder().value("<+variable.myVar>").index(0).build());

    AgentExpressionRequest request = AgentExpressionRequest.builder()
                                         .accountIdentifier("accountId")
                                         .orgIdentifier("")
                                         .projectIdentifier("")
                                         .application("")
                                         .token(123)
                                         .expressions(nonSecretExpressions)
                                         .context(new HashMap<>())
                                         .build();

    when(cdFeatureFlagHelper.isEnabled(eq("accountId"), eq(FeatureName.CDS_GITOPS_SECRET_RESOLUTION_ENABLED)))
        .thenReturn(true);

    // Should not throw scope validation errors
    AgentExpressionResponse response = gitOpsExpressionService.getExpression(request);
    assertNotNull(response);
  }
}