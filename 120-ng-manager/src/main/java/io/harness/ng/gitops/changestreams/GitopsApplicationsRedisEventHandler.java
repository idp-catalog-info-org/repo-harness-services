/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.gitops.changestreams;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventHandler.DebeziumAbstractRedisEventHandler;
import io.harness.events.base.Application;
import io.harness.timescaledb.Tables;
import io.harness.timescaledb.tables.records.GitopsAppInfoRecord;

import com.google.inject.Inject;
import java.util.Map;
import java.util.Optional;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
@OwnedBy(GITOPS)
public class GitopsApplicationsRedisEventHandler extends DebeziumAbstractRedisEventHandler {
  // The Argo CD label customers add to link an Application to a Harness Service. Drives
  // service-based GitOps licensing aggregation under FF CDS_GITOPS_SERVICE_BASED_LICENSING.
  private static final String SERVICE_REF_LABEL_KEY = "harness.io/serviceRef";

  @Inject private DSLContext dsl;
  @Inject private MongoTemplate mongoTemplate;

  @Override
  public boolean handleCreateEvent(String id, String value) {
    GitopsAppInfoRecord gitopsAppInfoRecord = createRecord(value);
    if (gitopsAppInfoRecord != null) {
      return upsertGitopsAppInfoRecord(gitopsAppInfoRecord);
    }
    return true;
  }

  @Override
  public boolean handleDeleteEvent(String id) {
    return true;
  }

  @Override
  public boolean handleUpdateEvent(String id, String value) {
    GitopsAppInfoRecord gitopsAppInfoRecord = createRecord(value);
    if (gitopsAppInfoRecord != null) {
      return upsertGitopsAppInfoRecord(gitopsAppInfoRecord);
    }
    return true;
  }

  @SneakyThrows
  private boolean upsertGitopsAppInfoRecord(GitopsAppInfoRecord record) {
    try {
      GitopsAppInfoUpsert.execute(dsl, record);
      return true;
    } catch (DataAccessException ex) {
      log.error("Failed to upsert gitops application record in gitops_app_info", ex);
      return false;
    }
  }

  @SneakyThrows
  private GitopsAppInfoRecord createRecord(String value) {
    Document document = Document.parse(value);
    Application gitopsApp = mongoTemplate.getConverter().read(Application.class, document);
    Application.App appNode = gitopsApp.getApp();

    if (appNode == null || appNode.getStatus() == null || appNode.getStatus().getOperationstate() == null
        || appNode.getStatus().getOperationstate().getStartedat() == null) {
      return null;
    }

    // Service-based GitOps licensing (CDS-120057): carry the harness.io/serviceRef label into
    // gitops_app_info.serviceid. Non-sync updates are filtered upstream via Debezium cursor.pipeline.
    return dsl.newRecord(Tables.GITOPS_APP_INFO)
        .setAccountid(gitopsApp.getAccountIdentifier())
        .setOrgidentifier(gitopsApp.getOrgIdentifier())
        .setProjectidentifier(gitopsApp.getProjectIdentifier())
        .setAgentId(gitopsApp.getAgentIdentifier())
        .setApplicationname(gitopsApp.getName())
        .setServiceid(extractServiceRef(appNode))
        .setLastSyncStartedatTs(gitopsApp.getApp().getStatus().getOperationstate().getStartedat().getTime())
        .setLastSyncFinishedatTs(gitopsApp.getApp().getStatus().getOperationstate().getFinishedat() == null
                ? null
                : gitopsApp.getApp().getStatus().getOperationstate().getFinishedat().getTime());
  }

  /**
   * Reads the {@code harness.io/serviceRef} label off the typed {@link Application.App}'s
   * {@code objectmeta.labels} map. Returns {@code null} when any segment is missing or the value
   * is blank. Package-private static for direct unit testing.
   */
  static String extractServiceRef(Application.App appNode) {
    String value = Optional.ofNullable(appNode)
                       .map(Application.App::getObjectmeta)
                       .map(Application.ObjectMeta::getLabels)
                       .map((Map<String, String> labels) -> labels.get(SERVICE_REF_LABEL_KEY))
                       .orElse(null);
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
