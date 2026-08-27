/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.backstage.service.impl;

import static io.harness.idp.common.JacksonUtils.convert;
import static io.harness.rule.OwnerRule.SATHISH;
import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.clients.BackstageResourceClient;
import io.harness.idp.backstage.beans.BackstageScaffolderTask;
import io.harness.idp.backstage.entities.BackstageCatalogEntity;
import io.harness.idp.backstage.entities.BackstageCatalogTemplateEntity;
import io.harness.idp.backstage.events.BackstageScaffolderTaskStartEvent;
import io.harness.idp.backstage.repositories.BackstageCatalogEntityRepository;
import io.harness.idp.backstage.repositories.BackstageScaffolderTaskEntityRepository;
import io.harness.idp.events.producers.IdpEntityCrudStreamProducer;
import io.harness.idp.events.producers.IdpServiceMiscRedisProducer;
import io.harness.idp.namespace.beans.entity.NamespaceEntity;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxService;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.BackstageHarnessSyncRequest;
import io.harness.spec.server.idp.v1.model.User;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import retrofit2.Call;
import retrofit2.Response;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class BackstageServiceImplTest extends CategoryTest {
  static final String TEST_ACCOUNT_IDENTIFIER = "accountId";
  static final String TEST_IDENTIFIER = "identifier";
  static final String TEST_TASK_IDENTIFIER = "taskId";
  static final String TEST_ACTION = "start";
  static final String TEST_SYNC_MODE = "sync";
  static final String TEST_ASYNC_MODE = "async";
  static final String TEST_USER_NAME = "test";
  static final String TEST_USER_EMAIL = "test@harness.io";
  static final String TEST_USER_UUID = "b43da95857";
  static final String TEST_ENTITY_REF = "template:default/account_access";
  static final long SYNC_FROM = 1707473204;
  final List<String> allowedKindsForCatalogSync =
      List.of("API", "Component", "Domain", "Group", "Resource", "System", "Template", "User");
  final List<String> allowedKindsForAudit = List.of("API", "Component", "Domain", "Resource", "System", "Template");
  AutoCloseable openMocks;
  @Captor private ArgumentCaptor<BackstageScaffolderTaskStartEvent> backstageScaffolderTaskStartEventCaptor;
  @Mock BackstageScaffolderTaskEntityRepository scaffolderTaskEntityRepository;
  @Mock BackstageCatalogEntityRepository backstageCatalogEntityRepository;
  @Mock BackstageResourceClient backstageResourceClient;
  @Mock TransactionTemplate transactionTemplate;
  @Mock IdpServiceMiscRedisProducer idpServiceMiscRedisProducer;
  @Mock OutboxService outboxService;
  @Mock NamespaceService namespaceService;
  @Mock IdpEntityCrudStreamProducer idpEntityCrudStreamProducer;
  private Call<Object> call;
  static Gson gson = new Gson();
  @InjectMocks BackstageServiceImpl backstageService;

  @Before
  public void setUp() throws IllegalAccessException {
    openMocks = MockitoAnnotations.openMocks(this);

    FieldUtils.writeField(backstageService, "allowedKindsForCatalogSync", allowedKindsForCatalogSync, true);
    FieldUtils.writeField(backstageService, "allowedKindsForAudit", allowedKindsForAudit, true);

    call = mock(Call.class);
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testSync() throws IOException {
    when(namespaceService.getAccountIds())
        .thenReturn(
            getNamespaceEntities().stream().map(NamespaceEntity::getAccountIdentifier).collect(Collectors.toList()));
    Map<String, Object> apiResponse = backstageCatalogEntitiesApiResponse();
    Response<Object> response = Response.success(Collections.singletonList(apiResponse));
    when(call.execute()).thenReturn(response);
    when(backstageResourceClient.getCatalogEntities(any())).thenReturn(call);
    List<BackstageCatalogEntity> backstageCatalogEntities =
        convert(Collections.singletonList(apiResponse), BackstageCatalogEntity.class);
    Optional<BackstageCatalogEntity> optionalBackstageCatalogEntity = Optional.of(backstageCatalogEntities.get(0));
    when(backstageCatalogEntityRepository.findByAccountIdentifierAndEntityUid(anyString(), anyString()))
        .thenReturn(optionalBackstageCatalogEntity);
    backstageService.sync();
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testSyncByType() throws IOException {
    Map<String, Object> apiResponse = backstageCatalogEntitiesApiResponse();
    Response<Object> response = Response.success(apiResponse);
    when(call.execute()).thenReturn(response);
    when(backstageResourceClient.getCatalogEntityByName(any(), any())).thenReturn(call);
    when(idpEntityCrudStreamProducer.publishAsyncScoreComputationChangeEventToRedis(
             TEST_ACCOUNT_IDENTIFIER, null, TEST_IDENTIFIER))
        .thenReturn(true);
    when(outboxService.save(any())).thenReturn(OutboxEvent.builder().build());
    backstageService.syncByType(TEST_ACCOUNT_IDENTIFIER, BackstageHarnessSyncRequest.TypeEnum.ENTITY, TEST_IDENTIFIER,
        BackstageHarnessSyncRequest.ActionEnum.CREATE.value(), BackstageHarnessSyncRequest.SyncModeEnum.SYNC.value(),
        getUser());
    backstageService.syncByType(TEST_ACCOUNT_IDENTIFIER, BackstageHarnessSyncRequest.TypeEnum.ENTITY, TEST_IDENTIFIER,
        BackstageHarnessSyncRequest.ActionEnum.UPDATE.value(), BackstageHarnessSyncRequest.SyncModeEnum.SYNC.value(),
        getUser());

    doNothing()
        .when(idpServiceMiscRedisProducer)
        .publishIDPCatalogEntitiesSyncCaptureToRedis(TEST_ACCOUNT_IDENTIFIER, TEST_IDENTIFIER,
            BackstageHarnessSyncRequest.ActionEnum.CREATE.value(), getUser(),
            BackstageHarnessSyncRequest.TypeEnum.ENTITY);
    backstageService.syncByType(TEST_ACCOUNT_IDENTIFIER, BackstageHarnessSyncRequest.TypeEnum.ENTITY, TEST_IDENTIFIER,
        BackstageHarnessSyncRequest.ActionEnum.CREATE.value(), BackstageHarnessSyncRequest.SyncModeEnum.ASYNC.value(),
        getUser());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testSyncScaffolderTasks() {
    User user = getUser();

    MockedStatic<NGRestUtils> mockRestStatic = mockStatic(NGRestUtils.class);
    mockRestStatic.when(() -> NGRestUtils.getGeneralResponse(any())).thenReturn(getScaffolderTaskResponse());

    boolean success = backstageService.syncScaffolderTasks(
        TEST_ACCOUNT_IDENTIFIER, TEST_TASK_IDENTIFIER, TEST_ACTION, TEST_SYNC_MODE, user);

    verify(scaffolderTaskEntityRepository).save(any());
    verify(outboxService).save(backstageScaffolderTaskStartEventCaptor.capture());
    assertTrue(success);
    assertEquals(backstageScaffolderTaskStartEventCaptor.getValue().getTaskId(), "default/account_access/taskId");
    assertEquals(backstageScaffolderTaskStartEventCaptor.getValue().getAccountIdentifier(), TEST_ACCOUNT_IDENTIFIER);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testSyncScaffolderTasksUnknownAction() {
    User user = getUser();

    MockedStatic<NGRestUtils> mockRestStatic = mockStatic(NGRestUtils.class);
    mockRestStatic.when(() -> NGRestUtils.getGeneralResponse(any())).thenReturn(getScaffolderTaskResponse());

    boolean success = backstageService.syncScaffolderTasks(
        TEST_ACCOUNT_IDENTIFIER, TEST_TASK_IDENTIFIER, "END", TEST_SYNC_MODE, user);

    assertFalse(success);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testSyncScaffolderTasksAsync() {
    User user = getUser();

    boolean success = backstageService.syncScaffolderTasks(
        TEST_ACCOUNT_IDENTIFIER, TEST_TASK_IDENTIFIER, TEST_ACTION, TEST_ASYNC_MODE, user);

    verify(idpServiceMiscRedisProducer)
        .publishIDPCatalogEntitiesSyncCaptureToRedis(TEST_ACCOUNT_IDENTIFIER, TEST_TASK_IDENTIFIER, TEST_ACTION, user,
            BackstageHarnessSyncRequest.TypeEnum.TASK);
    assertTrue(success);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testSyncScaffolderTasksForAllActiveAccounts() {
    User user = getUser();

    when(namespaceService.getActiveAccounts()).thenReturn(getNamespaceEntities());
    MockedStatic<NGRestUtils> mockRestStatic = mockStatic(NGRestUtils.class);
    mockRestStatic.when(() -> NGRestUtils.getGeneralResponse(any())).thenReturn(getScaffolderTaskResponse());

    boolean success = backstageService.syncScaffolderTasks(
        TEST_ACCOUNT_IDENTIFIER, TEST_TASK_IDENTIFIER, TEST_ACTION, TEST_SYNC_MODE, user);

    verify(scaffolderTaskEntityRepository).save(any());
    verify(outboxService).save(backstageScaffolderTaskStartEventCaptor.capture());
    assertTrue(success);
    assertEquals(backstageScaffolderTaskStartEventCaptor.getValue().getTaskId(), "default/account_access/taskId");
    assertEquals(backstageScaffolderTaskStartEventCaptor.getValue().getAccountIdentifier(), TEST_ACCOUNT_IDENTIFIER);
  }

  private List<NamespaceEntity> getNamespaceEntities() {
    NamespaceEntity.Metadata metadata = new NamespaceEntity.Metadata();
    metadata.setScaffolderTasksSyncFrom(SYNC_FROM);
    return Collections.singletonList(
        NamespaceEntity.builder().accountIdentifier(TEST_ACCOUNT_IDENTIFIER).metadata(metadata).build());
  }

  private Object getScaffolderTaskResponse() {
    BackstageScaffolderTask task = new BackstageScaffolderTask();
    BackstageCatalogTemplateEntity.Spec spec = new BackstageCatalogTemplateEntity.Spec();
    BackstageCatalogTemplateEntity.TemplateInfo templateInfo = new BackstageCatalogTemplateEntity.TemplateInfo();
    templateInfo.setEntityRef(TEST_ENTITY_REF);
    spec.setTemplateInfo(templateInfo);
    task.setIdentifier(TEST_TASK_IDENTIFIER);
    task.setSpec(spec);
    task.setCreatedAt("2024-02-09T14:15:00.000Z");
    task.setLastHeartbeatAt("2024-02-09T14:15:00.000Z");
    return task;
  }

  private User getUser() {
    User user = new User();
    user.setName(TEST_USER_NAME);
    user.setEmail(TEST_USER_EMAIL);
    user.setUuid(TEST_USER_UUID);
    return user;
  }

  private Map<String, Object> backstageCatalogEntitiesApiResponse() {
    return gson.fromJson("{"
            + "\"metadata\": {"
            + "  \"namespace\": \"default\","
            + "  \"annotations\": {"
            + "    \"backstage.io/managed-by-location\": \"url:https://github.com/.../Service/CCM/lwd-worker.yaml\","
            + "    \"backstage.io/managed-by-origin-location\": \"url:https://github.com/.../Service/**/*.yaml\","
            + "    \"backstage.io/view-url\": \"https://github.com/.../Service/CCM/lwd-worker.yaml\","
            + "    \"backstage.io/edit-url\": \"https://github.com/.../Service/CCM/lwd-worker.yaml\","
            + "    \"backstage.io/source-location\": \"url:https://github.com/.../lightwing\","
            + "    \"harness.io/cd-serviceId\": \"\","
            + "    \"backstage.io/kubernetes-label-selector\": \"app=faktory\","
            + "    \"harness.io/project-url-stage\": \"https://stage.harness.io/.../deployments\","
            + "    \"harness.io/projects-stage\": \"Operations,RELEASEBUILDS\","
            + "    \"harness.io/cd-serviceId-stage\": \"LightwingFaktoryWorker\","
            + "    \"harness.io/ci-pipelineIds-stage\": \"Lightwing_Build\","
            + "    \"jira/project-key\": \"CCM\","
            + "    \"pagerduty.com/service-id\": \"PFVOX97\","
            + "    \"github.com/project-slug\": \"wings-software/lightwing\""
            + "  },"
            + "  \"name\": \"lightwing-worker\","
            + "  \"description\": \"CCM lightwing worker service\","
            + "  \"tags\": [\"go\"],"
            + "  \"links\": [{\"title\": \"repo\", \"url\": \"https://github.com/.../lightwing\"}],"
            + "  \"uid\": \"0cc20b2b-...\","
            + "  \"etag\": \"db04330338...\""
            + "},"
            + "\"kind\": \"Component\","
            + "\"spec\": {"
            + "  \"type\": \"Service\","
            + "  \"lifecycle\": \"production\","
            + "  \"owner\": \"ccmplayacc\","
            + "  \"system\": [\"ccm\"],"
            + "  \"dependsOn\": [\"Component:ng-manager\"]"
            + "},"
            + "\"apiVersion\": \"backstage.io/v1alpha1\","
            + "\"relations\": ["
            + "  {"
            + "    \"type\": \"dependsOn\","
            + "    \"targetRef\": \"component:default/ng-manager\","
            + "    \"target\": {\"kind\": \"component\", \"namespace\": \"default\", \"name\": \"ng-manager\"}"
            + "  },"
            + "  {"
            + "    \"type\": \"ownedBy\","
            + "    \"targetRef\": \"group:default/ccmplayacc\","
            + "    \"target\": {\"kind\": \"group\", \"namespace\": \"default\", \"name\": \"ccmplayacc\"}"
            + "  },"
            + "  {"
            + "    \"type\": \"partOf\","
            + "    \"targetRef\": \"system:default/ccm\","
            + "    \"target\": {\"kind\": \"system\", \"namespace\": \"default\", \"name\": \"ccm\"}"
            + "  }"
            + "]"
            + "}",
        new TypeToken<Map<String, Object>>() {}.getType());
  }
}
