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
import io.harness.events.base.UtilizationSnapshot;
import io.harness.timescaledb.Tables;
import io.harness.timescaledb.tables.records.GitopsInstanceStatsRecord;

import com.google.inject.Inject;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
@OwnedBy(GITOPS)
public class GitOpsUtilizationSnapshotRedisEventHandler extends DebeziumAbstractRedisEventHandler {
  @Inject private DSLContext dsl;
  @Inject private MongoTemplate mongoTemplate;

  @SneakyThrows
  private GitopsInstanceStatsRecord createRecord(String value) {
    Document document = Document.parse(value);
    UtilizationSnapshot utilizationSnapshot = mongoTemplate.getConverter().read(UtilizationSnapshot.class, document);
    return dsl.newRecord(Tables.GITOPS_INSTANCE_STATS)
        .setAccountid(utilizationSnapshot.accountIdentifier())
        .setOrgid(utilizationSnapshot.orgIdentifier())
        .setProjectid(utilizationSnapshot.projectIdentifier())
        .setAgentId(utilizationSnapshot.agentIdentifier())
        .setApplicationname(utilizationSnapshot.applicationName())
        .setServiceid(utilizationSnapshot.serviceIdentifier())
        .setReportedat(OffsetDateTime.ofInstant(Instant.ofEpochMilli(utilizationSnapshot.reportedAt()), ZoneOffset.UTC))
        .setSnapshotId(utilizationSnapshot.id())
        .setInstancetype("Pod")
        .setInstancecount(utilizationSnapshot.instanceCount());
  }

  @Override
  public boolean handleCreateEvent(String id, String value) {
    GitopsInstanceStatsRecord gitopsInstanceStatsRecord = createRecord(value);
    try {
      dsl.insertInto(Tables.GITOPS_INSTANCE_STATS).set(gitopsInstanceStatsRecord).onConflict().doNothing().execute();
      return true;
    } catch (DataAccessException ex) {
      log.error("Failed to insert GitOps utilization snapshot", ex);
      return false;
    }
  }

  @Override
  public boolean handleDeleteEvent(String id) {
    return true;
  }

  @Override
  public boolean handleUpdateEvent(String id, String value) {
    return true;
  }
}
