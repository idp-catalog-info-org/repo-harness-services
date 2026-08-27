/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.data.service.expressions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.blockExecutionMetadata.BlockExecutionMetadataService;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.pms.data.expression.PmsEngineExpressionService;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnresolvedExpressionsException;
import io.harness.execution.NodeExecution;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExpressionMode;
import io.harness.pms.contracts.service.ExpressionRenderBlobResponse;
import io.harness.pms.contracts.service.ExpressionRenderByNodeExecutionIdRequest;
import io.harness.pms.contracts.service.ExpressionResolveWithContextRequest;
import io.harness.pms.contracts.service.ExpressionResolveWithContextResponse;
import io.harness.redis.RedisConfig;
import io.harness.redis.RedissonClientFactory;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.utils.PmsFeatureFlagService;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.grpc.stub.StreamObserver;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.redisson.api.RedissonClient;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class EngineExpressionGrpcServiceImplTest extends CategoryTest {
  @Mock private StreamObserver<ExpressionResolveWithContextResponse> responseObserver;
  @Mock private PmsEngineExpressionService pmsEngineExpressionService;
  @Mock private PmsFeatureFlagService featureFlagService;
  @Mock private BlockExecutionMetadataService blockExecutionMetadataService;
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private RedisConfig redisConfig;
  @Mock private RedissonClient redissonClient;

  private MockedStatic<RedissonClientFactory> redissonClientFactoryMock;
  private EngineExpressionGrpcServiceImpl engineExpressionGrpcService;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    // Mock to allow validation to proceed by default
    when(blockExecutionMetadataService.validate(any())).thenReturn(false);

    // Mock the static RedissonClientFactory to return our mocked RedissonClient
    redissonClientFactoryMock = mockStatic(RedissonClientFactory.class);
    redissonClientFactoryMock.when(() -> RedissonClientFactory.getClient(any(RedisConfig.class)))
        .thenReturn(redissonClient);

    // Create the actual service implementation with mocked dependencies
    engineExpressionGrpcService =
        new EngineExpressionGrpcServiceImpl(pmsEngineExpressionService, nodeExecutionService, redisConfig);

    // Use reflection to set private @Inject fields
    setPrivateField(engineExpressionGrpcService, "featureFlagService", featureFlagService);
    setPrivateField(engineExpressionGrpcService, "blockExecutionMetadataService", blockExecutionMetadataService);
  }

  @After
  public void tearDown() {
    if (redissonClientFactoryMock != null) {
      redissonClientFactoryMock.close();
    }
  }

  private void setPrivateField(Object target, String fieldName, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }

  @Test
  @Owner(developers = OwnerRule.SHASHANK_JAIN)
  @Category(UnitTests.class)
  public void testresolveExpressionWithContext_SuccessfulEvaluation() throws JsonProcessingException {
    // Arrange
    Map<String, String> requestContextMap = new HashMap<>();
    requestContextMap.put("name", "John");
    requestContextMap.put("age", "30");
    requestContextMap.put("active", "true");

    ExpressionResolveWithContextRequest request =
        ExpressionResolveWithContextRequest.newBuilder()
            .setExpression("Hello <+name>")
            .putAllContext(requestContextMap)
            .setExpressionMode(ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED)
            .build();

    // Mock to allow the validation to proceed
    when(blockExecutionMetadataService.validate(any())).thenReturn(false);

    ArgumentCaptor<ExpressionResolveWithContextResponse> responseCaptor =
        ArgumentCaptor.forClass(ExpressionResolveWithContextResponse.class);
    doNothing().when(responseObserver).onNext(any());
    doNothing().when(responseObserver).onCompleted();

    // Act
    engineExpressionGrpcService.resolveExpressionWithContext(request, responseObserver);

    // Assert
    verify(responseObserver, times(1)).onNext(responseCaptor.capture());
    verify(responseObserver, times(1)).onCompleted();

    ExpressionResolveWithContextResponse response = responseCaptor.getValue();
    assertThat(response.getValue()).isEqualTo("Hello John");
  }

  @Test
  @Owner(developers = OwnerRule.SHASHANK_JAIN)
  @Category(UnitTests.class)
  public void testresolveExpressionWithContext_WithAmbiance() throws JsonProcessingException {
    // Arrange
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put("accountId", "testAccount");
    setupAbstractions.put("orgIdentifier", "testOrg");
    setupAbstractions.put("projectIdentifier", "testProject");

    io.harness.pms.contracts.ambiance.Ambiance ambiance = io.harness.pms.contracts.ambiance.Ambiance.newBuilder()
                                                              .putAllSetupAbstractions(setupAbstractions)
                                                              .setPlanExecutionId("testPlanExecutionId")
                                                              .build();

    Map<String, String> requestContextMap = new HashMap<>();
    requestContextMap.put("someKey", "someValue");

    ExpressionResolveWithContextRequest request =
        ExpressionResolveWithContextRequest.newBuilder()
            .setExpression("Hello <+someKey>")
            .setAmbiance(ambiance)
            .putAllContext(requestContextMap)
            .setExpressionMode(ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED)
            .build();

    // Mock the expression service to return a value
    when(pmsEngineExpressionService.resolve(any(), any(), any(), any())).thenReturn("Hello someValue");

    ArgumentCaptor<ExpressionResolveWithContextResponse> responseCaptor =
        ArgumentCaptor.forClass(ExpressionResolveWithContextResponse.class);
    doNothing().when(responseObserver).onNext(any());
    doNothing().when(responseObserver).onCompleted();

    // Act
    engineExpressionGrpcService.resolveExpressionWithContext(request, responseObserver);

    // Assert
    verify(responseObserver, times(1)).onNext(responseCaptor.capture());
    verify(responseObserver, times(1)).onCompleted();

    ExpressionResolveWithContextResponse response = responseCaptor.getValue();
    assertThat(response.getValue()).isEqualTo("Hello someValue");
  }

  @Test
  @Owner(developers = OwnerRule.SHASHANK_JAIN)
  @Category(UnitTests.class)
  public void testresolveExpressionWithContext_UnresolvedExpressionWithThrowException() throws JsonProcessingException {
    // Arrange
    Map<String, String> requestContextMap = new HashMap<>();
    requestContextMap.put("existing", "value");

    ExpressionResolveWithContextRequest request = ExpressionResolveWithContextRequest.newBuilder()
                                                      .setExpression("Hello <+nonExistent>")
                                                      .putAllContext(requestContextMap)
                                                      .setExpressionMode(ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED)
                                                      .build();

    // Act & Assert
    assertThatThrownBy(() -> engineExpressionGrpcService.resolveExpressionWithContext(request, responseObserver))
        .isInstanceOf(UnresolvedExpressionsException.class);
  }

  @Test
  @Owner(developers = OwnerRule.SHASHANK_JAIN)
  @Category(UnitTests.class)
  public void testresolveExpressionWithContext_UnresolvedExpressionWithReturnOriginal() throws JsonProcessingException {
    // Arrange
    Map<String, String> requestContextMap = new HashMap<>();
    requestContextMap.put("existing", "value");

    ExpressionResolveWithContextRequest request =
        ExpressionResolveWithContextRequest.newBuilder()
            .setExpression("Hello <+nonExistent>")
            .putAllContext(requestContextMap)
            .setExpressionMode(ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED)
            .build();

    ArgumentCaptor<ExpressionResolveWithContextResponse> responseCaptor =
        ArgumentCaptor.forClass(ExpressionResolveWithContextResponse.class);
    doNothing().when(responseObserver).onNext(any());
    doNothing().when(responseObserver).onCompleted();

    // Act
    engineExpressionGrpcService.resolveExpressionWithContext(request, responseObserver);

    // Assert
    verify(responseObserver, times(1)).onNext(responseCaptor.capture());
    verify(responseObserver, times(1)).onCompleted();

    ExpressionResolveWithContextResponse response = responseCaptor.getValue();
    assertThat(response.getValue()).isEqualTo("Hello <+nonExistent>");
  }

  @Test
  @Owner(developers = OwnerRule.SHASHANK_JAIN)
  @Category(UnitTests.class)
  public void testresolveExpressionWithContext_EmptyExpression() throws JsonProcessingException {
    // Arrange
    Map<String, String> requestContextMap = new HashMap<>();
    requestContextMap.put("name", "test");

    ExpressionResolveWithContextRequest request =
        ExpressionResolveWithContextRequest.newBuilder()
            .setExpression("")
            .putAllContext(requestContextMap)
            .setExpressionMode(ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED)
            .build();

    ArgumentCaptor<ExpressionResolveWithContextResponse> responseCaptor =
        ArgumentCaptor.forClass(ExpressionResolveWithContextResponse.class);
    doNothing().when(responseObserver).onNext(any());
    doNothing().when(responseObserver).onCompleted();

    // Act
    engineExpressionGrpcService.resolveExpressionWithContext(request, responseObserver);

    // Assert
    verify(responseObserver, times(1)).onNext(responseCaptor.capture());
    verify(responseObserver, times(1)).onCompleted();

    ExpressionResolveWithContextResponse response = responseCaptor.getValue();
    assertThat(response.getValue()).isEmpty();
  }

  @Test
  @Owner(developers = OwnerRule.SHASHANK_JAIN)
  @Category(UnitTests.class)
  public void testresolveExpressionWithContext_WithJsonValues() throws JsonProcessingException {
    // Arrange
    Map<String, String> requestContextMap = new HashMap<>();
    requestContextMap.put("userInfo", "{\"name\": \"John\", \"age\": 30}");
    requestContextMap.put("config", "{\"environment\": \"prod\", \"enabled\": true}");

    ExpressionResolveWithContextRequest request =
        ExpressionResolveWithContextRequest.newBuilder()
            .setExpression("Hello <+userInfo.name>, you are <+userInfo.age> years old")
            .putAllContext(requestContextMap)
            .setExpressionMode(ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED)
            .build();

    ArgumentCaptor<ExpressionResolveWithContextResponse> responseCaptor =
        ArgumentCaptor.forClass(ExpressionResolveWithContextResponse.class);
    doNothing().when(responseObserver).onNext(any());
    doNothing().when(responseObserver).onCompleted();

    // Act
    engineExpressionGrpcService.resolveExpressionWithContext(request, responseObserver);

    // Assert
    verify(responseObserver, times(1)).onNext(responseCaptor.capture());
    verify(responseObserver, times(1)).onCompleted();

    ExpressionResolveWithContextResponse response = responseCaptor.getValue();
    assertThat(response.getValue()).isEqualTo("Hello John, you are 30 years old");
  }

  @Test
  @Owner(developers = OwnerRule.SHASHANK_JAIN)
  @Category(UnitTests.class)
  public void testresolveExpressionWithContext_WithMixedValues() throws JsonProcessingException {
    // Arrange
    Map<String, String> requestContextMap = new HashMap<>();
    requestContextMap.put("userInfo", "{\"name\": \"John\", \"age\": 30}"); // JSON
    requestContextMap.put("simpleString", "just a string"); // Simple string
    requestContextMap.put("number", "123"); // Simple string that looks like number

    ExpressionResolveWithContextRequest request =
        ExpressionResolveWithContextRequest.newBuilder()
            .setExpression("Hello <+userInfo.name>, <+simpleString>, <+number>")
            .putAllContext(requestContextMap)
            .setExpressionMode(ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED)
            .build();

    ArgumentCaptor<ExpressionResolveWithContextResponse> responseCaptor =
        ArgumentCaptor.forClass(ExpressionResolveWithContextResponse.class);
    doNothing().when(responseObserver).onNext(any());
    doNothing().when(responseObserver).onCompleted();

    // Act
    engineExpressionGrpcService.resolveExpressionWithContext(request, responseObserver);

    // Assert
    verify(responseObserver, times(1)).onNext(responseCaptor.capture());
    verify(responseObserver, times(1)).onCompleted();

    ExpressionResolveWithContextResponse response = responseCaptor.getValue();
    assertThat(response.getValue()).isEqualTo("Hello John, just a string, 123");
  }

  @Test
  @Owner(developers = OwnerRule.SHASHANK_JAIN)
  @Category(UnitTests.class)
  public void testresolveExpressionWithContext_ContextMapTooLarge() throws JsonProcessingException {
    // Arrange
    Map<String, String> requestContextMap = new HashMap<>();

    // Create a large context map that exceeds 1MB
    StringBuilder largeJsonBuilder = new StringBuilder("{");
    for (int i = 0; i < 50000; i++) {
      if (i > 0) {
        largeJsonBuilder.append(",");
      }
      largeJsonBuilder.append("\"field").append(i).append("\":\"").append("x".repeat(25)).append("\"");
    }
    largeJsonBuilder.append("}");

    requestContextMap.put("largeData", largeJsonBuilder.toString());

    ExpressionResolveWithContextRequest request =
        ExpressionResolveWithContextRequest.newBuilder()
            .setExpression("Hello <+largeData.field1>")
            .putAllContext(requestContextMap)
            .setExpressionMode(ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED)
            .build();

    // Act & Assert
    assertThatThrownBy(() -> engineExpressionGrpcService.resolveExpressionWithContext(request, responseObserver))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Context map byte size exceeds maximum allowed limit of 1MB");
  }

  // Tests for validateAndGetContextMap method
  @Test
  @Owner(developers = OwnerRule.SHASHANK_JAIN)
  @Category(UnitTests.class)
  public void testValidateAndGetContextMap_WithJsonValues() throws JsonProcessingException {
    // Arrange
    Map<String, String> requestMap = new HashMap<>();
    requestMap.put("userInfo", "{\"name\": \"John\", \"age\": 30}");
    requestMap.put("config", "{\"environment\": \"prod\", \"enabled\": true}");

    // Act
    Map<String, Object> result = engineExpressionGrpcService.validateAndGetContextMap(requestMap);

    // Assert
    assertThat(result).hasSize(2);
    assertThat(result.get("userInfo")).isInstanceOf(Map.class);
    assertThat(result.get("config")).isInstanceOf(Map.class);

    @SuppressWarnings("unchecked") Map<String, Object> userInfo = (Map<String, Object>) result.get("userInfo");
    assertThat(userInfo.get("name")).isEqualTo("John");
    assertThat(userInfo.get("age")).isEqualTo(30);

    @SuppressWarnings("unchecked") Map<String, Object> config = (Map<String, Object>) result.get("config");
    assertThat(config.get("environment")).isEqualTo("prod");
    assertThat(config.get("enabled")).isEqualTo(true);
  }

  @Test
  @Owner(developers = OwnerRule.SHASHANK_JAIN)
  @Category(UnitTests.class)
  public void testValidateAndGetContextMap_WithMixedValues() throws JsonProcessingException {
    // Arrange
    Map<String, String> requestMap = new HashMap<>();
    requestMap.put("userInfo", "{\"name\": \"John\", \"age\": 30}"); // JSON
    requestMap.put("simpleString", "just a string"); // Simple string
    requestMap.put("number", "123"); // Simple string that looks like number

    // Act
    Map<String, Object> result = engineExpressionGrpcService.validateAndGetContextMap(requestMap);

    // Assert
    assertThat(result).hasSize(3);
    assertThat(result.get("userInfo")).isInstanceOf(Map.class);
    assertThat(result.get("simpleString")).isEqualTo("just a string");
    assertThat(result.get("number")).isEqualTo("123");

    @SuppressWarnings("unchecked") Map<String, Object> userInfo = (Map<String, Object>) result.get("userInfo");
    assertThat(userInfo.get("name")).isEqualTo("John");
    assertThat(userInfo.get("age")).isEqualTo(30);
  }

  @Test
  @Owner(developers = OwnerRule.SHASHANK_JAIN)
  @Category(UnitTests.class)
  public void testValidateAndGetContextMap_WithInvalidJson() throws JsonProcessingException {
    // Arrange
    Map<String, String> requestMap = new HashMap<>();
    requestMap.put("invalidJson", "invalid json {");
    requestMap.put("validJson", "{\"key\": \"value\"}");
    requestMap.put("simpleString", "normal string");

    // Act
    Map<String, Object> result = engineExpressionGrpcService.validateAndGetContextMap(requestMap);

    // Assert
    assertThat(result).hasSize(3);
    assertThat(result.get("invalidJson")).isEqualTo("invalid json {"); // Should be treated as string
    assertThat(result.get("validJson")).isInstanceOf(Map.class);
    assertThat(result.get("simpleString")).isEqualTo("normal string");
  }

  @Test
  @Owner(developers = OwnerRule.SHASHANK_JAIN)
  @Category(UnitTests.class)
  public void testValidateAndGetContextMap_EmptyRequestMap() throws JsonProcessingException {
    // Arrange
    Map<String, String> requestMap = new HashMap<>();

    // Act
    Map<String, Object> result = engineExpressionGrpcService.validateAndGetContextMap(requestMap);

    // Assert
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = OwnerRule.SHASHANK_JAIN)
  @Category(UnitTests.class)
  public void testValidateAndGetContextMap_ContextMapTooLarge() throws JsonProcessingException {
    // Arrange - Create a large context map that exceeds 1MB
    Map<String, String> requestMap = new HashMap<>();
    StringBuilder largeValue = new StringBuilder();
    // Create a string that's over 1MB (1,048,576 bytes)
    for (int i = 0; i < 1200000; i++) { // 1.2M characters should exceed 1MB when serialized
      largeValue.append("a");
    }
    requestMap.put("largeData", largeValue.toString());

    // Act & Assert
    assertThatThrownBy(() -> engineExpressionGrpcService.validateAndGetContextMap(requestMap))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Context map byte size exceeds maximum allowed limit of 1MB");
  }

  @Test
  @Owner(developers = OwnerRule.NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testRenderExpressionByNodeExecutionId_EmptyNodeExecutionId() {
    ExpressionRenderByNodeExecutionIdRequest request =
        ExpressionRenderByNodeExecutionIdRequest.newBuilder()
            .setNodeExecutionId("")
            .setExpression("<+artifacts.primary.tag>")
            .setExpressionMode(ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED)
            .build();

    @SuppressWarnings("unchecked")
    StreamObserver<ExpressionRenderBlobResponse> observer =
        (StreamObserver<ExpressionRenderBlobResponse>) org.mockito.Mockito.mock(StreamObserver.class);

    engineExpressionGrpcService.renderExpressionByNodeExecutionId(request, observer);

    ArgumentCaptor<Throwable> errorCaptor = ArgumentCaptor.forClass(Throwable.class);
    verify(observer).onError(errorCaptor.capture());
    verify(observer, never()).onNext(any());
    assertThat(errorCaptor.getValue().getMessage()).contains("nodeExecutionId is required");
  }

  @Test
  @Owner(developers = OwnerRule.NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testRenderExpressionByNodeExecutionId_NodeExecutionNotFound() {
    String nodeExecutionId = "non-existent-id";
    when(nodeExecutionService.getWithFieldsIncludedFromSecondary(eq(nodeExecutionId), anySet()))
        .thenThrow(new InvalidRequestException("Node Execution is null for id: " + nodeExecutionId));

    ExpressionRenderByNodeExecutionIdRequest request =
        ExpressionRenderByNodeExecutionIdRequest.newBuilder()
            .setNodeExecutionId(nodeExecutionId)
            .setExpression("<+artifacts.primary.tag>")
            .setExpressionMode(ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED)
            .build();

    @SuppressWarnings("unchecked")
    StreamObserver<ExpressionRenderBlobResponse> observer =
        (StreamObserver<ExpressionRenderBlobResponse>) org.mockito.Mockito.mock(StreamObserver.class);

    engineExpressionGrpcService.renderExpressionByNodeExecutionId(request, observer);

    ArgumentCaptor<Throwable> errorCaptor = ArgumentCaptor.forClass(Throwable.class);
    verify(observer).onError(errorCaptor.capture());
    verify(observer, never()).onNext(any());
    assertThat(errorCaptor.getValue().getMessage()).contains("NodeExecution not found for id:");
  }

  @Test
  @Owner(developers = OwnerRule.NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testRenderExpressionByNodeExecutionId_NullAmbiance() {
    String nodeExecutionId = "valid-id";
    NodeExecution nodeExecution = NodeExecution.builder().uuid(nodeExecutionId).build();
    when(nodeExecutionService.getWithFieldsIncludedFromSecondary(eq(nodeExecutionId), anySet()))
        .thenReturn(nodeExecution);

    ExpressionRenderByNodeExecutionIdRequest request =
        ExpressionRenderByNodeExecutionIdRequest.newBuilder()
            .setNodeExecutionId(nodeExecutionId)
            .setExpression("<+artifacts.primary.tag>")
            .setExpressionMode(ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED)
            .build();

    @SuppressWarnings("unchecked")
    StreamObserver<ExpressionRenderBlobResponse> observer =
        (StreamObserver<ExpressionRenderBlobResponse>) org.mockito.Mockito.mock(StreamObserver.class);

    engineExpressionGrpcService.renderExpressionByNodeExecutionId(request, observer);

    ArgumentCaptor<Throwable> errorCaptor = ArgumentCaptor.forClass(Throwable.class);
    verify(observer).onError(errorCaptor.capture());
    verify(observer, never()).onNext(any());
    assertThat(errorCaptor.getValue().getMessage()).contains("NodeExecution or Ambiance not found");
  }

  @Test
  @Owner(developers = OwnerRule.NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testRenderExpressionByNodeExecutionId_HappyPath() {
    String nodeExecutionId = "valid-id";
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId("plan-exec-id").build();
    NodeExecution nodeExecution = NodeExecution.builder().uuid(nodeExecutionId).ambiance(ambiance).build();
    when(nodeExecutionService.getWithFieldsIncludedFromSecondary(eq(nodeExecutionId), anySet()))
        .thenReturn(nodeExecution);
    when(pmsEngineExpressionService.renderExpression(
             eq(ambiance), eq("<+artifacts.primary.tag>"), any(io.harness.expression.common.ExpressionMode.class)))
        .thenReturn("v1.2.3");

    ExpressionRenderByNodeExecutionIdRequest request =
        ExpressionRenderByNodeExecutionIdRequest.newBuilder()
            .setNodeExecutionId(nodeExecutionId)
            .setExpression("<+artifacts.primary.tag>")
            .setExpressionMode(ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED)
            .build();

    @SuppressWarnings("unchecked")
    StreamObserver<ExpressionRenderBlobResponse> observer =
        (StreamObserver<ExpressionRenderBlobResponse>) org.mockito.Mockito.mock(StreamObserver.class);

    engineExpressionGrpcService.renderExpressionByNodeExecutionId(request, observer);

    ArgumentCaptor<ExpressionRenderBlobResponse> responseCaptor =
        ArgumentCaptor.forClass(ExpressionRenderBlobResponse.class);
    verify(observer).onNext(responseCaptor.capture());
    verify(observer).onCompleted();
    verify(observer, never()).onError(any());
    assertThat(responseCaptor.getValue().getValue()).isEqualTo("v1.2.3");
  }

  @Test
  @Owner(developers = OwnerRule.NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testRenderExpressionByNodeExecutionId_UnknownModeFallback() {
    String nodeExecutionId = "valid-id";
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId("plan-exec-id").build();
    NodeExecution nodeExecution = NodeExecution.builder().uuid(nodeExecutionId).ambiance(ambiance).build();
    when(nodeExecutionService.getWithFieldsIncludedFromSecondary(eq(nodeExecutionId), anySet()))
        .thenReturn(nodeExecution);
    when(pmsEngineExpressionService.renderExpression(eq(ambiance), eq("<+env.name>"), eq(false)))
        .thenReturn("production");

    ExpressionRenderByNodeExecutionIdRequest request = ExpressionRenderByNodeExecutionIdRequest.newBuilder()
                                                           .setNodeExecutionId(nodeExecutionId)
                                                           .setExpression("<+env.name>")
                                                           .setExpressionMode(ExpressionMode.UNKNOWN_MODE)
                                                           .build();

    @SuppressWarnings("unchecked")
    StreamObserver<ExpressionRenderBlobResponse> observer =
        (StreamObserver<ExpressionRenderBlobResponse>) org.mockito.Mockito.mock(StreamObserver.class);

    engineExpressionGrpcService.renderExpressionByNodeExecutionId(request, observer);

    ArgumentCaptor<ExpressionRenderBlobResponse> responseCaptor =
        ArgumentCaptor.forClass(ExpressionRenderBlobResponse.class);
    verify(observer).onNext(responseCaptor.capture());
    verify(observer).onCompleted();
    verify(observer, never()).onError(any());
    assertThat(responseCaptor.getValue().getValue()).isEqualTo("production");
  }
}