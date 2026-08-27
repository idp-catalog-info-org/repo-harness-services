/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.gitops.changestreams;

import static io.harness.rule.OwnerRule.PARTH_SHARMA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.timescaledb.Tables;
import io.harness.timescaledb.tables.records.GitopsAppInfoRecord;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class GitopsAppInfoUpsertTest extends CategoryTest {
  private static final String ACCOUNT_ID = "accountId1";
  private static final String ORG_ID = "orgId1";
  private static final String PROJECT_ID = "projectId1";
  private static final String AGENT_ID = "agentId1";
  private static final String APP_NAME = "appName1";

  private DSLContext dsl;

  @Before
  public void setUp() {
    dsl = DSL.using(SQLDialect.POSTGRES);
  }

  @Test
  @Owner(developers = PARTH_SHARMA)
  @Category(UnitTests.class)
  public void testBuildUpsert_LinkedService_IncludesPartialIndexWhereClause() {
    GitopsAppInfoRecord record = baseRecord().setServiceid("svcA");

    String sql = dsl.renderInlined(GitopsAppInfoUpsert.buildUpsert(dsl, record));

    assertThat(sql).containsIgnoringCase("on conflict");
    assertThat(sql).contains("serviceid");
    assertThat(sql.replace("\"", "")).containsIgnoringCase("serviceid is not null");
    assertThat(sql).containsIgnoringCase("coalesce");
    assertThat(sql).containsIgnoringCase("excluded.last_sync_startedat_ts");
    assertThat(sql).containsIgnoringCase("is distinct from");
    assertThat(sql.toLowerCase()).doesNotContain("max(");
  }

  @Test
  @Owner(developers = PARTH_SHARMA)
  @Category(UnitTests.class)
  public void testBuildUpsert_LinkedService_WritesSyncTimestampsDirectly() {
    GitopsAppInfoRecord record = baseRecord().setServiceid("svcA");

    String sql = dsl.renderInlined(GitopsAppInfoUpsert.buildUpsert(dsl, record));

    assertThat(sql).contains(String.valueOf(record.getLastSyncStartedatTs()));
    assertThat(sql).contains(String.valueOf(record.getLastSyncFinishedatTs()));
  }

  @Test
  @Owner(developers = PARTH_SHARMA)
  @Category(UnitTests.class)
  public void testBuildUpsert_UnlinkedService_IncludesPartialIndexWhereClause() {
    GitopsAppInfoRecord record = baseRecord().setServiceid((String) null);

    String sql = dsl.renderInlined(GitopsAppInfoUpsert.buildUpsert(dsl, record));

    assertThat(sql).containsIgnoringCase("on conflict");
    assertThat(sql).doesNotContain("serviceid is not null");
    assertThat(sql.replace("\"", "")).containsIgnoringCase("serviceid is null");
    assertThat(sql).containsIgnoringCase("is distinct from");
    assertThat(sql.toLowerCase()).doesNotContain("max(");
  }

  @Test
  @Owner(developers = PARTH_SHARMA)
  @Category(UnitTests.class)
  public void testBuildUpsert_BlankServiceId_NormalizesToUnlinkedPartialIndex() {
    GitopsAppInfoRecord record = baseRecord().setServiceid("");

    String sql = dsl.renderInlined(GitopsAppInfoUpsert.buildUpsert(dsl, record));

    assertThat(record.getServiceid()).isNull();
    assertThat(sql).doesNotContain("serviceid is not null");
    assertThat(sql.replace("\"", "")).containsIgnoringCase("serviceid is null");
  }

  @Test
  @Owner(developers = PARTH_SHARMA)
  @Category(UnitTests.class)
  public void testBuildUpsert_WhitespaceServiceId_NormalizesToUnlinkedPartialIndex() {
    GitopsAppInfoRecord record = baseRecord().setServiceid("   ");

    String sql = dsl.renderInlined(GitopsAppInfoUpsert.buildUpsert(dsl, record));

    assertThat(record.getServiceid()).isNull();
    assertThat(sql.replace("\"", "")).containsIgnoringCase("serviceid is null");
  }

  @Test
  @Owner(developers = PARTH_SHARMA)
  @Category(UnitTests.class)
  public void testAttributedSyncTimestamp_RemovedFromUpsert() {
    assertThat(GitopsAppInfoUpsert.class.getDeclaredMethods())
        .noneMatch(method -> "attributedSyncTimestamp".equals(method.getName()));
  }

  private static GitopsAppInfoRecord baseRecord() {
    return DSL.using(SQLDialect.POSTGRES)
        .newRecord(Tables.GITOPS_APP_INFO)
        .setAccountid(ACCOUNT_ID)
        .setOrgidentifier(ORG_ID)
        .setProjectidentifier(PROJECT_ID)
        .setAgentId(AGENT_ID)
        .setApplicationname(APP_NAME)
        .setLastSyncStartedatTs(1700000000000L)
        .setLastSyncFinishedatTs(1700000005000L);
  }
}
