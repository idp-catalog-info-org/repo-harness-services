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
import io.harness.events.base.UtilizationSnapshot;
import io.harness.rule.Owner;
import io.harness.timescaledb.tables.records.GitopsInstanceStatsRecord;

import java.lang.reflect.Method;
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
 * Unit tests for {@link GitOpsUtilizationSnapshotRedisEventHandler}. Guards the serviceid
 * write-through path added in CDS-120057: when a UtilizationSnapshot BSON document arrives from
 * Debezium, serviceIdentifier must be carried into the TimescaleDB record as serviceid.
 */
@RunWith(MockitoJUnitRunner.class)
public class GitOpsUtilizationSnapshotRedisEventHandlerTest extends CategoryTest {
  private static final String ACCOUNT_ID = "accountId1";
  private static final String ORG_ID = "orgId1";
  private static final String PROJECT_ID = "projectId1";
  private static final String AGENT_ID = "agentId1";
  private static final String APP_NAME = "appName1";
  private static final String SERVICE_ID = "serviceId1";
  private static final String ENV_ID = "envId1";
  private static final String SNAPSHOT_ID = "snapshotId1";

  @Mock private MongoTemplate mongoTemplate;
  @Mock private MongoConverter mongoConverter;

  private GitOpsUtilizationSnapshotRedisEventHandler handler;

  @Before
  public void setUp() throws Exception {
    handler = new GitOpsUtilizationSnapshotRedisEventHandler();
    // Real standalone DSLContext — no JDBC connection; newRecord() works offline.
    DSLContext dsl = DSL.using(SQLDialect.POSTGRES);
    FieldUtils.writeField(handler, "dsl", dsl, true);
    FieldUtils.writeField(handler, "mongoTemplate", mongoTemplate, true);
    when(mongoTemplate.getConverter()).thenReturn(mongoConverter);
  }

  @Test
  @Owner(developers = PARTH_SHARMA)
  @Category(UnitTests.class)
  public void testCreateRecord_PropagatesServiceIdentifierToRecord() throws Exception {
    UtilizationSnapshot snapshot = new UtilizationSnapshot(
        SNAPSHOT_ID, ACCOUNT_ID, ORG_ID, PROJECT_ID, AGENT_ID, APP_NAME, SERVICE_ID, ENV_ID, 1700000000000L, 5);
    when(mongoConverter.read(eq(UtilizationSnapshot.class), any(Document.class))).thenReturn(snapshot);

    GitopsInstanceStatsRecord record = invokeCreateRecord("{}");

    assertThat(record.getServiceid()).isEqualTo(SERVICE_ID);
    assertThat(record.getAccountid()).isEqualTo(ACCOUNT_ID);
    assertThat(record.getOrgid()).isEqualTo(ORG_ID);
    assertThat(record.getProjectid()).isEqualTo(PROJECT_ID);
    assertThat(record.getAgentId()).isEqualTo(AGENT_ID);
    assertThat(record.getApplicationname()).isEqualTo(APP_NAME);
    assertThat(record.getSnapshotId()).isEqualTo(SNAPSHOT_ID);
    assertThat(record.getInstancecount()).isEqualTo(5);
    assertThat(record.getInstancetype()).isEqualTo("Pod");
  }

  @Test
  @Owner(developers = PARTH_SHARMA)
  @Category(UnitTests.class)
  public void testCreateRecord_NullServiceIdentifierIsPreserved() throws Exception {
    // Snapshots emitted before the agent serviceRef rollout, or for Argo apps without the
    // harness.io/serviceRef label, carry a null serviceIdentifier. Verify the handler passes it
    // through unchanged so the TSDB row records the absence accurately.
    UtilizationSnapshot snapshot = new UtilizationSnapshot(
        SNAPSHOT_ID, ACCOUNT_ID, ORG_ID, PROJECT_ID, AGENT_ID, APP_NAME, null, null, 1700000000000L, 3);
    when(mongoConverter.read(eq(UtilizationSnapshot.class), any(Document.class))).thenReturn(snapshot);

    GitopsInstanceStatsRecord record = invokeCreateRecord("{}");

    assertThat(record.getServiceid()).isNull();
    assertThat(record.getApplicationname()).isEqualTo(APP_NAME);
  }

  private GitopsInstanceStatsRecord invokeCreateRecord(String value) throws Exception {
    Method m = GitOpsUtilizationSnapshotRedisEventHandler.class.getDeclaredMethod("createRecord", String.class);
    m.setAccessible(true);
    return (GitopsInstanceStatsRecord) m.invoke(handler, value);
  }
}
