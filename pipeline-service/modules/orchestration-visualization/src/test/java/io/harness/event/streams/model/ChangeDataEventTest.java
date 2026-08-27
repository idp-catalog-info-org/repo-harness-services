/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.event.streams.model;

import static io.harness.rule.OwnerRule.YUVRAJ;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.OrchestrationVisualizationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class ChangeDataEventTest extends OrchestrationVisualizationTestBase {
  // ===================== isCreate / isUpdate / isDelete =====================

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testIsCreate_insert() {
    ChangeDataEvent event = ChangeDataEvent.builder().operationType("insert").build();
    assertThat(event.isCreate()).isTrue();
    assertThat(event.isUpdate()).isFalse();
    assertThat(event.isDelete()).isFalse();
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testIsUpdate() {
    ChangeDataEvent event = ChangeDataEvent.builder().operationType("update").build();
    assertThat(event.isUpdate()).isTrue();
    assertThat(event.isCreate()).isFalse();
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testIsDelete() {
    ChangeDataEvent event = ChangeDataEvent.builder().operationType("delete").build();
    assertThat(event.isDelete()).isTrue();
  }

  // ===================== getCollection =====================

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testGetCollection() {
    ChangeDataEvent.Namespace ns = new ChangeDataEvent.Namespace("pms-harness", "nodeExecutions");
    ChangeDataEvent event = ChangeDataEvent.builder().ns(ns).build();
    assertThat(event.getCollection()).isEqualTo("nodeExecutions");
  }

  // ===================== getDocumentId =====================

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testGetDocumentId() {
    Map<String, Object> docKey = new HashMap<>();
    docKey.put("_id", "doc-123");
    ChangeDataEvent event = ChangeDataEvent.builder().documentKey(docKey).build();
    assertThat(event.getDocumentId()).isEqualTo("doc-123");
  }

  // ===================== hasUpdatedFields =====================

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testHasUpdatedFields_true() {
    Map<String, Object> fields = new HashMap<>();
    fields.put("status", "SUCCEEDED");
    ChangeDataEvent.UpdateDescription ud = new ChangeDataEvent.UpdateDescription(fields, null, null);
    ChangeDataEvent event = ChangeDataEvent.builder().operationType("update").updateDescription(ud).build();
    assertThat(event.hasUpdatedFields()).isTrue();
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testHasUpdatedFields_emptyFields() {
    ChangeDataEvent.UpdateDescription ud = new ChangeDataEvent.UpdateDescription(new HashMap<>(), null, null);
    ChangeDataEvent event = ChangeDataEvent.builder().operationType("update").updateDescription(ud).build();
    assertThat(event.hasUpdatedFields()).isFalse();
  }

  // ===================== extractPlanExecutionId =====================

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testExtractPlanExecutionId_nodeExecutions() {
    Map<String, Object> ambiance = new HashMap<>();
    ambiance.put("planExecutionId", "plan-1");
    Map<String, Object> fullDoc = new HashMap<>();
    fullDoc.put("ambiance", ambiance);

    ChangeDataEvent event = ChangeDataEvent.builder()
                                .operationType("insert")
                                .ns(new ChangeDataEvent.Namespace("db", "nodeExecutions"))
                                .fullDocument(fullDoc)
                                .build();
    assertThat(event.extractPlanExecutionId()).isEqualTo("plan-1");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testExtractPlanExecutionId_outcomeInstances() {
    Map<String, Object> fullDoc = new HashMap<>();
    fullDoc.put("planExecutionId", "plan-2");

    ChangeDataEvent event = ChangeDataEvent.builder()
                                .operationType("insert")
                                .ns(new ChangeDataEvent.Namespace("db", "outcomeInstances"))
                                .fullDocument(fullDoc)
                                .build();
    assertThat(event.extractPlanExecutionId()).isEqualTo("plan-2");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testExtractPlanExecutionId_planExecutions() {
    Map<String, Object> fullDoc = new HashMap<>();
    fullDoc.put("_id", "plan-exec-id");

    ChangeDataEvent event = ChangeDataEvent.builder()
                                .operationType("insert")
                                .ns(new ChangeDataEvent.Namespace("db", "planExecutions"))
                                .fullDocument(fullDoc)
                                .build();
    assertThat(event.extractPlanExecutionId()).isEqualTo("plan-exec-id");
  }

  // ===================== extractAccountId =====================

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testExtractAccountId_fromAccountId() {
    Map<String, Object> fullDoc = new HashMap<>();
    fullDoc.put("accountId", "acct-1");

    ChangeDataEvent event = ChangeDataEvent.builder().fullDocument(fullDoc).build();
    assertThat(event.extractAccountId()).isEqualTo("acct-1");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testExtractAccountId_fromAccountIdentifier() {
    Map<String, Object> fullDoc = new HashMap<>();
    fullDoc.put("accountIdentifier", "acct-2");

    ChangeDataEvent event = ChangeDataEvent.builder().fullDocument(fullDoc).build();
    assertThat(event.extractAccountId()).isEqualTo("acct-2");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testExtractAccountId_fromSetupAbstractions() {
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put("accountId", "acct-3");
    Map<String, Object> fullDoc = new HashMap<>();
    fullDoc.put("setupAbstractions", setupAbstractions);

    ChangeDataEvent event = ChangeDataEvent.builder().fullDocument(fullDoc).build();
    assertThat(event.extractAccountId()).isEqualTo("acct-3");
  }

  // ===================== generateEventId =====================

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testGenerateEventId() {
    Map<String, Object> docKey = new HashMap<>();
    docKey.put("_id", "doc-1");
    ChangeDataEvent event = ChangeDataEvent.builder()
                                .operationType("update")
                                .ns(new ChangeDataEvent.Namespace("db", "nodeExecutions"))
                                .documentKey(docKey)
                                .build();
    assertThat(event.generateEventId()).isEqualTo("nodeExecutions:doc-1:update");
  }

  // ===================== JSON deserialization =====================

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testJsonDeserialization() {
    String json = "{\"operationType\":\"insert\","
        + "\"fullDocument\":{\"status\":\"RUNNING\"},"
        + "\"documentKey\":{\"_id\":\"abc\"},"
        + "\"ns\":{\"db\":\"pms\",\"coll\":\"nodeExecutions\"},"
        + "\"unknownField\":\"ignored\"}";

    ChangeDataEvent event = JsonUtils.asObject(json, ChangeDataEvent.class);
    assertThat(event).isNotNull();
    assertThat(event.getOperationType()).isEqualTo("insert");
    assertThat(event.isCreate()).isTrue();
    assertThat(event.getCollection()).isEqualTo("nodeExecutions");
    assertThat(event.getDocumentId()).isEqualTo("abc");
    assertThat(event.getFullDocument()).containsEntry("status", "RUNNING");
  }
}
