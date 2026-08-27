/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.gitops.changestreams;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventHandler.DebeziumAbstractRedisEventHandler;
import io.harness.events.base.Appsync;
import io.harness.timescaledb.Tables;
import io.harness.timescaledb.tables.records.GitopsAppInfoRecord;

import com.google.inject.Inject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
@OwnedBy(GITOPS)
public class GitopsAppsyncRedisEventHandler extends DebeziumAbstractRedisEventHandler {
  @Inject private DSLContext dsl;
  @Inject private MongoTemplate mongoTemplate;

  @SneakyThrows
  private GitopsAppInfoRecord createRecord(String value) {
    Document document = Document.parse(value);
    Appsync appsync = mongoTemplate.getConverter().read(Appsync.class, document);
    return dsl.newRecord(Tables.GITOPS_APP_INFO)
        .setAccountid(appsync.getAccountIdentifier())
        .setOrgidentifier(appsync.getOrgIdentifier())
        .setProjectidentifier(appsync.getProjectIdentifier())
        .setAgentId(appsync.getAgentIdentifier())
        .setApplicationname(appsync.getApplicationName())
        .setLastSyncStartedatTs(appsync.getOperationState().getStartedat().getTime())
        .setLastSyncFinishedatTs(appsync.getOperationState().getFinishedat() == null
                ? null
                : appsync.getOperationState().getFinishedat().getTime());
  }

  @Override
  public boolean handleCreateEvent(String id, String value) {
    GitopsAppInfoRecord gitopsAppInfoRecord = createRecord(value);
    return upsertGitopsAppsyncRecord(gitopsAppInfoRecord);
  }

  @Override
  public boolean handleDeleteEvent(String id) {
    return false;
  }

  @Override
  public boolean handleUpdateEvent(String id, String value) {
    GitopsAppInfoRecord gitopsAppInfoRecord = createRecord(value);
    return upsertGitopsAppsyncRecord(gitopsAppInfoRecord);
  }

  private boolean upsertGitopsAppsyncRecord(GitopsAppInfoRecord record) {
    // Appsync events do not set serviceid; upsert uses the without-serviceid partial unique index.
    try {
      GitopsAppInfoUpsert.execute(dsl, record);
      return true;
    } catch (DataAccessException ex) {
      log.error("Failed to upsert Appsync Record in gitops_app_info", ex);
      return false;
    }
  }
}
