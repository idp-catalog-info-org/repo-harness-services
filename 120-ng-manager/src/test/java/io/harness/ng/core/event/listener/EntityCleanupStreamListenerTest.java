/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.event.listener;

import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACCOUNT_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CREATE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.rule.OwnerRule.ABHINAV_MITTAL;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.AutoCleanupConfig;
import io.harness.annotations.CleanupTriggerEntity;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.cdng.execution.StageExecutionInfo;
import io.harness.eventsframework.consumer.Message;
import io.harness.rule.Owner;

import com.google.protobuf.ByteString;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.util.ReflectionTestUtils;

public class EntityCleanupStreamListenerTest extends CategoryTest {
  private EntityCleanupStreamListener listener;
  private ExecutorService executorService;
  private MongoTemplate mongoTemplate;
  private BulkOperations bulkOperations;
  private Set<Class<?>> entitiesToCleanUp;
  private Class<?> annotatedClass = StageExecutionInfo.class;
  private Map<Class<?>, List<AutoCleanupConfig>> entityCleanupMap;
  private int batchSize = 5; // Example batch size
  @Captor private ArgumentCaptor<Query> queryCaptor;

  private AutoCloseable mocks;

  @Before
  public void setUp() {
    mocks = MockitoAnnotations.openMocks(this);
    executorService = mock(ExecutorService.class);
    mongoTemplate = mock(MongoTemplate.class);
    bulkOperations = mock(BulkOperations.class);
    entitiesToCleanUp = new HashSet<>();
    entityCleanupMap = new HashMap<>();
    listener =
        new EntityCleanupStreamListener(executorService, mongoTemplate, entitiesToCleanUp, entityCleanupMap, batchSize);
    ReflectionTestUtils.setField(listener, "executorService", executorService);
    ReflectionTestUtils.setField(listener, "mongoTemplate", mongoTemplate);
  }

  @After
  public void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testHandleMessageWithEntityEligibleForCleanup() throws ExecutionException, InterruptedException {
    Future<Boolean> mockFuture = mock(Future.class);

    entitiesToCleanUp.clear();
    entitiesToCleanUp.add(annotatedClass);

    entityCleanupMap.clear();
    AutoCleanupConfig[] autoCleanupConfigs = buildAutoCleanupConfig("stageExecutionInfo", true);
    entityCleanupMap.put(annotatedClass, java.util.Arrays.stream(autoCleanupConfigs).toList());

    Message message = createMockMessage(ACCOUNT_ENTITY, DELETE_ACTION, "{\"accountId\":\"123\"}");
    when(executorService.submit(any(Callable.class))).thenReturn(mockFuture);
    when(mockFuture.get()).thenReturn(true);

    boolean result = listener.handleMessage(message);

    assertTrue(result);
    verify(executorService, atLeastOnce()).submit(any(Callable.class));
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testHandleMessageWithEntityNotEligibleForCleanup() {
    Future<Boolean> mockFuture = mock(Future.class);

    entitiesToCleanUp.clear();
    entitiesToCleanUp.add(annotatedClass);

    entityCleanupMap.clear();
    AutoCleanupConfig[] autoCleanupConfigs = buildAutoCleanupConfig("environmentGroupEntity", false);
    entityCleanupMap.put(annotatedClass, java.util.Arrays.stream(autoCleanupConfigs).toList());

    Message message = createMockMessage(ACCOUNT_ENTITY, DELETE_ACTION, "{\"accountId\":\"123\"}");
    when(executorService.submit(any(Callable.class))).thenReturn(mockFuture);

    boolean result = listener.handleMessage(message);

    assertTrue(result);
    verify(executorService, never()).submit(any(Callable.class));
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testErrorHandlingDuringDeletion() throws Exception {
    Message message = createMockMessage(ACCOUNT_ENTITY, DELETE_ACTION, "{\"accountId\":\"123\"}");

    entitiesToCleanUp.clear();
    entitiesToCleanUp.add(annotatedClass);

    entityCleanupMap.clear();
    AutoCleanupConfig[] autoCleanupConfigs = buildAutoCleanupConfig("stageExecutionInfo", true);
    entityCleanupMap.put(annotatedClass, java.util.Arrays.stream(autoCleanupConfigs).toList());

    Future<Boolean> future = mock(Future.class);
    when(future.get()).thenThrow(new ExecutionException(new Exception("Deletion failed")));
    when(executorService.submit(any(Callable.class))).thenReturn(future);

    boolean result = listener.handleMessage(message);

    assertFalse(result);
    verify(executorService, atLeastOnce()).submit(any(Callable.class));
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testHandleMessageWithValidEntityIncorrectAction() {
    entitiesToCleanUp.clear();
    entitiesToCleanUp.add(annotatedClass);

    entityCleanupMap.clear();
    AutoCleanupConfig[] autoCleanupConfigs = buildAutoCleanupConfig("stageExecutionInfo", true);
    entityCleanupMap.put(annotatedClass, java.util.Arrays.stream(autoCleanupConfigs).toList());

    Message message = createMockMessage(ACCOUNT_ENTITY, CREATE_ACTION, "{\"accountId\":\"123\"}");

    boolean result = listener.handleMessage(message, createAutoCleanupConfig("stageExecutionInfo", true));

    // Verify outcomes
    assertTrue("The method should return true indicating the message was handled.", result);
    verify(executorService, never()).submit(any(Callable.class)); // Ensure no deletion task is submitted
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testHandleMessageWithNonExistentEntity() {
    entitiesToCleanUp.clear();
    entitiesToCleanUp.add(annotatedClass);

    entityCleanupMap.clear();
    AutoCleanupConfig[] autoCleanupConfigs = buildAutoCleanupConfig("stageExecutionInfo", true);
    entityCleanupMap.put(annotatedClass, java.util.Arrays.stream(autoCleanupConfigs).toList());

    Message message = createMockMessageWithNonExistentEntityType(DELETE_ACTION, "{\"accountId\":\"123\"}");

    boolean result = listener.handleMessage(message, createAutoCleanupConfig("stageExecutionInfo", true));

    // Verify outcomes
    assertTrue("The method should return true indicating the message was handled.", result);
    verify(executorService, never()).submit(any(Callable.class)); // Ensure no deletion task is submitted
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testHandleMessageWithEmptyMetadata() {
    entitiesToCleanUp.clear();
    entitiesToCleanUp.add(annotatedClass);

    entityCleanupMap.clear();
    AutoCleanupConfig[] autoCleanupConfigs = buildAutoCleanupConfig("stageExecutionInfo", true);
    entityCleanupMap.put(annotatedClass, java.util.Arrays.stream(autoCleanupConfigs).toList());

    Message message = createMockMessageWithEmptyMetaData("{\"accountId\":\"123\"}");

    boolean result = listener.handleMessage(message, createAutoCleanupConfig("stageExecutionInfo", true));

    // Verify outcomes
    assertTrue("The method should return true indicating the message was handled.", result);
    verify(executorService, never()).submit(any(Callable.class)); // Ensure no deletion task is submitted
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testDeleteInBatchesWithMultipleBatches() {
    String accountId = "testAccount";
    String collectionName = "testCollection";

    entitiesToCleanUp.clear();
    entitiesToCleanUp.add(annotatedClass);

    entityCleanupMap.clear();
    AutoCleanupConfig[] autoCleanupConfigs = buildAutoCleanupConfig("stageExecutionInfo", true);
    entityCleanupMap.put(annotatedClass, java.util.Arrays.stream(autoCleanupConfigs).toList());

    when(mongoTemplate.find(any(Query.class), eq(Document.class), eq(collectionName)))
        .thenReturn(createMockDocumentsList(5), createMockDocumentsList(3), createMockDocumentsList(0));

    when(mongoTemplate.bulkOps(any(BulkOperations.BulkMode.class), anyString())).thenReturn(bulkOperations);

    ScopeInfo scopeInfo = ScopeInfo.builder().uniqueId("accountId").accountIdentifier("accountId").build();

    Criteria criteria = new Criteria();
    criteria.and("accountId").is(accountId);

    listener.deleteInBatches(scopeInfo, collectionName, criteria);

    verify(mongoTemplate, times(3)).find(any(Query.class), eq(Document.class), eq(collectionName));
    verify(bulkOperations, times(2)).execute();
  }

  private List<Document> createMockDocumentsList(int size) {
    List<Document> documentList = new ArrayList<>();
    for (int i = 0; i < size; i++) {
      documentList.add(new org.bson.Document("_id", new ObjectId()));
    }
    return documentList;
  }

  private io.harness.eventsframework.consumer.Message createMockMessage(
      String entityType, String action, String jsonData) {
    return io.harness.eventsframework.consumer.Message.newBuilder()
        .setId("testId")
        .setTimestamp(com.google.protobuf.Timestamp.newBuilder().build())
        .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                        .setData(ByteString.copyFromUtf8(jsonData))
                        .putMetadata("ENTITY_TYPE", entityType)
                        .putMetadata("ACTION", action)
                        .build())
        .build();
  }

  private io.harness.eventsframework.consumer.Message createMockMessageWithNonExistentEntityType(
      String action, String jsonData) {
    return io.harness.eventsframework.consumer.Message.newBuilder()
        .setId("testId")
        .setTimestamp(com.google.protobuf.Timestamp.newBuilder().build())
        .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                        .setData(ByteString.copyFromUtf8(jsonData))
                        .putMetadata("ACTION", action)
                        .build())
        .build();
  }

  private io.harness.eventsframework.consumer.Message createMockMessageWithEmptyMetaData(String jsonData) {
    return io.harness.eventsframework.consumer.Message.newBuilder()
        .setId("testId")
        .setTimestamp(com.google.protobuf.Timestamp.newBuilder().build())
        .setMessage(
            io.harness.eventsframework.producer.Message.newBuilder().setData(ByteString.copyFromUtf8(jsonData)).build())
        .build();
  }

  private AutoCleanupConfig[] buildAutoCleanupConfig(String collectionName, boolean processDeleteEvents) {
    AutoCleanupConfig[] autoCleanupConfigs = new AutoCleanupConfig[1];
    AutoCleanupConfig autoCleanupConfig = createAutoCleanupConfig(collectionName, processDeleteEvents);
    autoCleanupConfigs[0] = autoCleanupConfig;
    return autoCleanupConfigs;
  }

  public static AutoCleanupConfig createAutoCleanupConfig(String collectionName, boolean processDeleteEvents) {
    return (AutoCleanupConfig) Proxy.newProxyInstance(
        AutoCleanupConfig.class.getClassLoader(), new Class<?>[] {AutoCleanupConfig.class}, (proxy, method, args) -> {
          switch (method.getName()) {
            case "collectionName":
              return collectionName;
            case "cleanupTriggers":
              return createCleanupTriggerEntity();
            case "processDeleteEvents":
              return processDeleteEvents; // or false, depending on your test case
            case "dataStore":
              return "harness";
            default:
              return null; // For simplicity, returning null for other methods
          }
        });
  }

  public static CleanupTriggerEntity createCleanupTriggerEntity() {
    return (CleanupTriggerEntity) Proxy.newProxyInstance(CleanupTriggerEntity.class.getClassLoader(),
        new Class<?>[] {AutoCleanupConfig.class}, (proxy, method, args) -> {
          switch (method.getName()) {
            case "entityType":
              return "ACCOUNT_ENTITY";
            case "identifierField":
              return "account_identifier";
            default:
              return null; // For simplicity, returning null for other methods
          }
        });
  }
}
