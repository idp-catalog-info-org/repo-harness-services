/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.event.streams.serde;

import static io.harness.rule.OwnerRule.YUVRAJ;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.OrchestrationVisualizationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.event.streams.model.ChangeDataEvent;
import io.harness.rule.Owner;

import java.nio.charset.StandardCharsets;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class GraphStreamSerdesTest extends OrchestrationVisualizationTestBase {
  // ===================== ChangeDataEventDeserializer =====================

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testChangeDataEventDeserializer_validJson() {
    String json = "{\"operationType\":\"insert\","
        + "\"documentKey\":{\"_id\":\"doc-1\"},"
        + "\"ns\":{\"db\":\"pms\",\"coll\":\"nodeExecutions\"}}";

    GraphStreamSerdes.ChangeDataEventDeserializer deserializer = new GraphStreamSerdes.ChangeDataEventDeserializer();
    ChangeDataEvent event = deserializer.deserialize("topic", json.getBytes(StandardCharsets.UTF_8));

    assertThat(event).isNotNull();
    assertThat(event.getOperationType()).isEqualTo("insert");
    assertThat(event.isCreate()).isTrue();
    assertThat(event.getCollection()).isEqualTo("nodeExecutions");
    assertThat(event.getDocumentId()).isEqualTo("doc-1");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testChangeDataEventDeserializer_invalidJson() {
    GraphStreamSerdes.ChangeDataEventDeserializer deserializer = new GraphStreamSerdes.ChangeDataEventDeserializer();
    ChangeDataEvent event = deserializer.deserialize("topic", "not-json".getBytes(StandardCharsets.UTF_8));
    assertThat(event).isNull();
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testChangeDataEventDeserializer_withUpdateDescription() {
    String json = "{\"operationType\":\"update\","
        + "\"documentKey\":{\"_id\":\"doc-2\"},"
        + "\"updateDescription\":{\"updatedFields\":{\"status\":\"SUCCEEDED\"},\"removedFields\":[]}}";

    GraphStreamSerdes.ChangeDataEventDeserializer deserializer = new GraphStreamSerdes.ChangeDataEventDeserializer();
    ChangeDataEvent event = deserializer.deserialize("topic", json.getBytes(StandardCharsets.UTF_8));

    assertThat(event).isNotNull();
    assertThat(event.isUpdate()).isTrue();
    assertThat(event.hasUpdatedFields()).isTrue();
    assertThat(event.getUpdatedFields()).containsEntry("status", "SUCCEEDED");
  }
}
