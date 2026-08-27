/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.gitops.changestreams;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;

import static org.jooq.impl.DSL.coalesce;
import static org.jooq.impl.DSL.field;

import io.harness.annotations.dev.OwnedBy;
import io.harness.timescaledb.Tables;
import io.harness.timescaledb.tables.records.GitopsAppInfoRecord;

import com.google.common.annotations.VisibleForTesting;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Query;
import org.jooq.impl.SQLDataType;

/**
 * Upserts into {@code gitops_app_info} against the PG14 partial unique indexes introduced in
 * migration v125. Postgres requires the partial-index predicate in {@code ON CONFLICT ... WHERE}
 * so the arbiter index can be inferred (see NullAwarePartialIndexes.md).
 *
 * <p>Sync-only writes are enforced upstream via Debezium {@code cursor.pipeline}; this class performs a
 * timestamp upsert per (app, serviceid) tuple and skips no-op updates via {@code IS DISTINCT FROM}.
 */
@OwnedBy(GITOPS)
final class GitopsAppInfoUpsert {
  private static final Field<Long> EXCLUDED_LAST_SYNC_STARTEDAT_TS =
      field("excluded.last_sync_startedat_ts", SQLDataType.BIGINT);
  private static final Field<Long> EXCLUDED_LAST_SYNC_FINISHEDAT_TS =
      field("excluded.last_sync_finishedat_ts", SQLDataType.BIGINT);

  private GitopsAppInfoUpsert() {}

  static void execute(DSLContext dsl, GitopsAppInfoRecord record) {
    buildUpsert(dsl, record).execute();
  }

  @VisibleForTesting
  static Query buildUpsert(DSLContext dsl, GitopsAppInfoRecord record) {
    if (normalizeAndCheckIfLinkedServiceId(record)) {
      return buildLinkedServiceUpsert(dsl, record);
    }
    return buildUnlinkedServiceUpsert(dsl, record);
  }

  private static Query buildLinkedServiceUpsert(DSLContext dsl, GitopsAppInfoRecord record) {
    Field<Long> effectiveStartedAt =
        coalesce(EXCLUDED_LAST_SYNC_STARTEDAT_TS, Tables.GITOPS_APP_INFO.LAST_SYNC_STARTEDAT_TS);
    Field<Long> effectiveFinishedAt =
        coalesce(EXCLUDED_LAST_SYNC_FINISHEDAT_TS, Tables.GITOPS_APP_INFO.LAST_SYNC_FINISHEDAT_TS);
    return dsl.insertInto(Tables.GITOPS_APP_INFO)
        .set(Tables.GITOPS_APP_INFO.ACCOUNTID, record.getAccountid())
        .set(Tables.GITOPS_APP_INFO.ORGIDENTIFIER, record.getOrgidentifier())
        .set(Tables.GITOPS_APP_INFO.PROJECTIDENTIFIER, record.getProjectidentifier())
        .set(Tables.GITOPS_APP_INFO.AGENT_ID, record.getAgentId())
        .set(Tables.GITOPS_APP_INFO.APPLICATIONNAME, record.getApplicationname())
        .set(Tables.GITOPS_APP_INFO.SERVICEID, record.getServiceid())
        .set(Tables.GITOPS_APP_INFO.LAST_SYNC_STARTEDAT_TS, record.getLastSyncStartedatTs())
        .set(Tables.GITOPS_APP_INFO.LAST_SYNC_FINISHEDAT_TS, record.getLastSyncFinishedatTs())
        .onConflict(Tables.GITOPS_APP_INFO.ACCOUNTID, Tables.GITOPS_APP_INFO.ORGIDENTIFIER,
            Tables.GITOPS_APP_INFO.PROJECTIDENTIFIER, Tables.GITOPS_APP_INFO.AGENT_ID,
            Tables.GITOPS_APP_INFO.APPLICATIONNAME, Tables.GITOPS_APP_INFO.SERVICEID)
        .where(Tables.GITOPS_APP_INFO.SERVICEID.isNotNull())
        .doUpdate()
        .set(Tables.GITOPS_APP_INFO.LAST_SYNC_STARTEDAT_TS, effectiveStartedAt)
        .set(Tables.GITOPS_APP_INFO.LAST_SYNC_FINISHEDAT_TS, effectiveFinishedAt)
        .where(Tables.GITOPS_APP_INFO.LAST_SYNC_STARTEDAT_TS.isDistinctFrom(effectiveStartedAt)
                   .or(Tables.GITOPS_APP_INFO.LAST_SYNC_FINISHEDAT_TS.isDistinctFrom(effectiveFinishedAt)));
  }

  private static Query buildUnlinkedServiceUpsert(DSLContext dsl, GitopsAppInfoRecord record) {
    Field<Long> effectiveStartedAt =
        coalesce(EXCLUDED_LAST_SYNC_STARTEDAT_TS, Tables.GITOPS_APP_INFO.LAST_SYNC_STARTEDAT_TS);
    Field<Long> effectiveFinishedAt =
        coalesce(EXCLUDED_LAST_SYNC_FINISHEDAT_TS, Tables.GITOPS_APP_INFO.LAST_SYNC_FINISHEDAT_TS);
    return dsl.insertInto(Tables.GITOPS_APP_INFO)
        .set(record)
        .onConflict(Tables.GITOPS_APP_INFO.ACCOUNTID, Tables.GITOPS_APP_INFO.ORGIDENTIFIER,
            Tables.GITOPS_APP_INFO.PROJECTIDENTIFIER, Tables.GITOPS_APP_INFO.AGENT_ID,
            Tables.GITOPS_APP_INFO.APPLICATIONNAME)
        .where(Tables.GITOPS_APP_INFO.SERVICEID.isNull())
        .doUpdate()
        .set(Tables.GITOPS_APP_INFO.LAST_SYNC_STARTEDAT_TS, effectiveStartedAt)
        .set(Tables.GITOPS_APP_INFO.LAST_SYNC_FINISHEDAT_TS, effectiveFinishedAt)
        .where(Tables.GITOPS_APP_INFO.LAST_SYNC_STARTEDAT_TS.isDistinctFrom(effectiveStartedAt)
                   .or(Tables.GITOPS_APP_INFO.LAST_SYNC_FINISHEDAT_TS.isDistinctFrom(effectiveFinishedAt)));
  }

  private static boolean normalizeAndCheckIfLinkedServiceId(GitopsAppInfoRecord record) {
    String serviceId = record.getServiceid();
    if (serviceId == null || serviceId.isBlank()) {
      record.setServiceid(null);
      return false;
    }
    return true;
  }
}
