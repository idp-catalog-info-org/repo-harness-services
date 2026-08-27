/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.gitops.changestreams;

import static io.harness.rule.OwnerRule.PARTH_SHARMA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.events.base.Application;
import io.harness.events.base.Appsync;
import io.harness.rule.Owner;
import io.harness.timescaledb.Tables;
import io.harness.timescaledb.tables.records.GitopsAppInfoRecord;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.bson.Document;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoConverter;

/**
 * Unit tests for {@link GitopsApplicationsRedisEventHandler}. Guards the serviceid write-through
 * path added in CDS-120057: when a Debezium change event for the gitops {@code applications}
 * collection arrives, the {@code harness.io/serviceRef} label must be carried from the Argo
 * Application's {@code metadata.labels} into {@code gitops_app_info.serviceid}, and removing the
 * label on a subsequent update must clear the column.
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class GitopsApplicationsRedisEventHandlerTest extends CategoryTest {
  private static final String ACCOUNT_ID = "accountId1";
  private static final String ORG_ID = "orgId1";
  private static final String PROJECT_ID = "projectId1";
  private static final String AGENT_ID = "agentId1";
  private static final String APP_NAME = "appName1";
  private static final String SERVICE_REF = "myService";
  private static final long STARTED_AT = 1700000000000L;
  private static final long FINISHED_AT = 1700000005000L;

  private static final String SERVICE_REF_LABEL_KEY = "harness.io/serviceRef";
  private static final String ENV_REF_LABEL_KEY = "harness.io/envRef";

  @Mock private MongoTemplate mongoTemplate;
  @Mock private MongoConverter mongoConverter;

  private GitopsApplicationsRedisEventHandler handler;

  @Before
  public void setUp() throws Exception {
    handler = new GitopsApplicationsRedisEventHandler();
    // Real standalone DSLContext -- no JDBC connection; newRecord() works offline.
    DSLContext dsl = DSL.using(SQLDialect.POSTGRES);
    FieldUtils.writeField(handler, "dsl", dsl, true);
    FieldUtils.writeField(handler, "mongoTemplate", mongoTemplate, true);
    when(mongoTemplate.getConverter()).thenReturn(mongoConverter);
  }

  // -------------------- extractServiceRef: pure helper --------------------

  @Test
  @Owner(developers = PARTH_SHARMA)
  @Category(UnitTests.class)
  public void testExtractServiceRef_LabelPresent_ReturnsValue() {
    Application.App appNode = appWithLabels(labels(SERVICE_REF_LABEL_KEY, SERVICE_REF));
    assertThat(GitopsApplicationsRedisEventHandler.extractServiceRef(appNode)).isEqualTo(SERVICE_REF);
  }

  @Test
  @Owner(developers = PARTH_SHARMA)
  @Category(UnitTests.class)
  public void testExtractServiceRef_LabelTrimmed() {
    Application.App appNode = appWithLabels(labels(SERVICE_REF_LABEL_KEY, "  svc1  "));
    assertThat(GitopsApplicationsRedisEventHandler.extractServiceRef(appNode)).isEqualTo("svc1");
  }

  @Test
  @Owner(developers = PARTH_SHARMA)
  @Category(UnitTests.class)
  public void testExtractServiceRef_OtherLabelsOnly_ReturnsNull() {
    // App carries unrelated labels but not the harness.io/serviceRef one.
    Application.App appNode = appWithLabels(labels(ENV_REF_LABEL_KEY, "someEnv"));
    assertThat(GitopsApplicationsRedisEventHandler.extractServiceRef(appNode)).isNull();
  }

  @Test
  @Owner(developers = PARTH_SHARMA)
  @Category(UnitTests.class)
  public void testExtractServiceRef_EmptyLabel_ReturnsNull() {
    Application.App appNode = appWithLabels(labels(SERVICE_REF_LABEL_KEY, "   "));
    assertThat(GitopsApplicationsRedisEventHandler.extractServiceRef(appNode)).isNull();
  }

  @Test
  @Owner(developers = PARTH_SHARMA)
  @Category(UnitTests.class)
  public void testExtractServiceRef_LabelsNull_ReturnsNull() {
    Application.App appNode = appWithObjectMeta(Application.ObjectMeta.builder().labels(null).build());
    assertThat(GitopsApplicationsRedisEventHandler.extractServiceRef(appNode)).isNull();
  }

  @Test
  @Owner(developers = PARTH_SHARMA)
  @Category(UnitTests.class)
  public void testExtractServiceRef_ObjectMetaNull_ReturnsNull() {
    Application.App appNode = appWithObjectMeta(null);
    assertThat(GitopsApplicationsRedisEventHandler.extractServiceRef(appNode)).isNull();
  }

  @Test
  @Owner(developers = PARTH_SHARMA)
  @Category(UnitTests.class)
  public void testExtractServiceRef_AppNull_ReturnsNull() {
    assertThat(GitopsApplicationsRedisEventHandler.extractServiceRef(null)).isNull();
  }

  // -------------------- createRecord: end-to-end wire-through --------------------

  @Test
  @Owner(developers = PARTH_SHARMA)
  @Category(UnitTests.class)
  public void testCreateRecord_WithServiceRefLabel_PropagatesToServiceid() throws Exception {
    Application app = applicationPojo(labels(SERVICE_REF_LABEL_KEY, SERVICE_REF));
    when(mongoConverter.read(eq(Application.class), any(Document.class))).thenReturn(app);

    GitopsAppInfoRecord record = invokeCreateRecord("{}");

    assertThat(record).isNotNull();
    assertThat(record.getServiceid()).isEqualTo(SERVICE_REF);
    assertThat(record.getAccountid()).isEqualTo(ACCOUNT_ID);
    assertThat(record.getOrgidentifier()).isEqualTo(ORG_ID);
    assertThat(record.getProjectidentifier()).isEqualTo(PROJECT_ID);
    assertThat(record.getAgentId()).isEqualTo(AGENT_ID);
    assertThat(record.getApplicationname()).isEqualTo(APP_NAME);
    assertThat(record.getLastSyncStartedatTs()).isEqualTo(STARTED_AT);
    assertThat(record.getLastSyncFinishedatTs()).isEqualTo(FINISHED_AT);
  }

  @Test
  @Owner(developers = PARTH_SHARMA)
  @Category(UnitTests.class)
  public void testCreateRecord_LabelRemoved_EmitsUnlinkedTuple() throws Exception {
    // Label removal inserts/updates the (app, serviceid=NULL) row; prior linked rows are preserved.
    Application app = applicationPojo(labels(ENV_REF_LABEL_KEY, "someEnv"));
    when(mongoConverter.read(eq(Application.class), any(Document.class))).thenReturn(app);

    GitopsAppInfoRecord record = invokeCreateRecord("{}");

    assertThat(record).isNotNull();
    assertThat(record.getServiceid()).isNull();
    assertThat(record.changed(Tables.GITOPS_APP_INFO.SERVICEID)).isTrue();
  }

  @Test
  @Owner(developers = PARTH_SHARMA)
  @Category(UnitTests.class)
  public void testCreateRecord_DifferentServiceRefs_ProduceDistinctConflictKeys() throws Exception {
    Application linkedToA = applicationPojo(labels(SERVICE_REF_LABEL_KEY, "svcA"));
    when(mongoConverter.read(eq(Application.class), any(Document.class))).thenReturn(linkedToA);
    GitopsAppInfoRecord recordA = invokeCreateRecord("{}");
    assertThat(recordA.getServiceid()).isEqualTo("svcA");

    Application linkedToB = applicationPojo(labels(SERVICE_REF_LABEL_KEY, "svcB"));
    when(mongoConverter.read(eq(Application.class), any(Document.class))).thenReturn(linkedToB);
    GitopsAppInfoRecord recordB = invokeCreateRecord("{}");
    assertThat(recordB.getServiceid()).isEqualTo("svcB");

    assertThat(recordA.getApplicationname()).isEqualTo(recordB.getApplicationname());
    assertThat(recordA.getServiceid()).isNotEqualTo(recordB.getServiceid());
  }

  @Test
  @Owner(developers = PARTH_SHARMA)
  @Category(UnitTests.class)
  public void testCreateRecord_NoOperationStartedAt_ReturnsNull() throws Exception {
    // Pre-existing guard: drop events that arrive before a sync has produced a startedAt timestamp.
    Application app =
        Application.builder()
            .accountIdentifier(ACCOUNT_ID)
            .orgIdentifier(ORG_ID)
            .projectIdentifier(PROJECT_ID)
            .agentIdentifier(AGENT_ID)
            .name(APP_NAME)
            .app(Application.App.builder()
                     .status(
                         Application.Status.builder().operationstate(Appsync.OperationState.builder().build()).build())
                     .build())
            .build();
    when(mongoConverter.read(eq(Application.class), any(Document.class))).thenReturn(app);

    GitopsAppInfoRecord record = invokeCreateRecord("{}");

    assertThat(record).isNull();
  }

  // -------------------- helpers --------------------

  private static Map<String, String> labels(String key, String value) {
    Map<String, String> m = new HashMap<>();
    m.put(key, value);
    return m;
  }

  private static Application.App appWithLabels(Map<String, String> labels) {
    return appWithObjectMeta(Application.ObjectMeta.builder().labels(labels).build());
  }

  private static Application.App appWithObjectMeta(Application.ObjectMeta objectMeta) {
    return Application.App.builder()
        .status(Application.Status.builder().operationstate(operationState()).build())
        .objectmeta(objectMeta)
        .build();
  }

  private static Appsync.OperationState operationState() {
    return Appsync.OperationState.builder()
        .startedat(Appsync.OperationState.LocalDateTimeWrapper.builder().time(STARTED_AT).build())
        .finishedat(Appsync.OperationState.LocalDateTimeWrapper.builder().time(FINISHED_AT).build())
        .build();
  }

  private static Application applicationPojo(Map<String, String> labels) {
    return Application.builder()
        .accountIdentifier(ACCOUNT_ID)
        .orgIdentifier(ORG_ID)
        .projectIdentifier(PROJECT_ID)
        .agentIdentifier(AGENT_ID)
        .name(APP_NAME)
        .app(appWithLabels(labels))
        .build();
  }

  private GitopsAppInfoRecord invokeCreateRecord(String value) throws Exception {
    Method m = GitopsApplicationsRedisEventHandler.class.getDeclaredMethod("createRecord", String.class);
    m.setAccessible(true);
    return (GitopsAppInfoRecord) m.invoke(handler, value);
  }
}
