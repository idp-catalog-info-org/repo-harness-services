/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.graph.service.impl;

import static io.harness.rule.OwnerRule.YUVRAJ;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.OrchestrationVisualizationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.contracts.interrupts.IssuedBy;
import io.harness.pms.contracts.interrupts.ManualIssuer;
import io.harness.rule.Owner;

import com.google.api.client.util.Base64;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jooq.JSONB;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class InterruptHistoriesParserTest extends OrchestrationVisualizationTestBase {
  private static final InterruptConfig SAMPLE_INTERRUPT_CONFIG =
      InterruptConfig.newBuilder()
          .setIssuedBy(IssuedBy.newBuilder()
                           .setManualIssuer(
                               ManualIssuer.newBuilder().setEmailId("test@harness.io").setIdentifier("user1").build())
                           .build())
          .build();

  // ===================== parseToJsonb =====================

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testParseToJsonb_withScalarFields() {
    Map<String, Object> item = new HashMap<>();
    item.put("interruptId", "interrupt-1");
    item.put("tookEffectAt", 1234567890L);
    item.put("interruptType", "ABORT");

    List<Object> list = new ArrayList<>();
    list.add(item);

    JSONB result = InterruptHistoriesParser.parseToJsonb(list);
    assertThat(result).isNotNull();
    assertThat(result.data()).contains("interrupt-1");
    assertThat(result.data()).contains("ABORT");
    assertThat(result.data()).contains("1234567890");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testParseToJsonb_withBinaryInterruptConfig() {
    String base64 = Base64.encodeBase64String(SAMPLE_INTERRUPT_CONFIG.toByteArray());
    Map<String, Object> configMap = new HashMap<>();
    configMap.put("_serialised", base64);

    Map<String, Object> item = new HashMap<>();
    item.put("interruptId", "interrupt-2");
    item.put("tookEffectAt", 9999L);
    item.put("interruptType", "ABORT_ALL");
    item.put("interruptConfig", configMap);

    List<Object> list = new ArrayList<>();
    list.add(item);

    JSONB result = InterruptHistoriesParser.parseToJsonb(list);
    assertThat(result).isNotNull();
    assertThat(result.data()).contains("interrupt-2");
    assertThat(result.data()).contains("test@harness.io");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testParseToJsonb_withPlainJsonInterruptConfig() {
    Map<String, Object> configMap = new HashMap<>();
    configMap.put("someConfigField", "someValue");

    Map<String, Object> item = new HashMap<>();
    item.put("interruptId", "interrupt-3");
    item.put("interruptConfig", configMap);

    List<Object> list = new ArrayList<>();
    list.add(item);

    JSONB result = InterruptHistoriesParser.parseToJsonb(list);
    assertThat(result).isNotNull();
    assertThat(result.data()).contains("interrupt-3");
    assertThat(result.data()).contains("someConfigField");
    assertThat(result.data()).contains("someValue");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testParseToJsonb_withBinaryNestedInExtendedJson() {
    String base64 = Base64.encodeBase64String(SAMPLE_INTERRUPT_CONFIG.toByteArray());

    Map<String, Object> binaryMap = new HashMap<>();
    binaryMap.put("base64", base64);
    binaryMap.put("subType", "00");

    Map<String, Object> serialisedMap = new HashMap<>();
    serialisedMap.put("$binary", binaryMap);

    Map<String, Object> configMap = new HashMap<>();
    configMap.put("_serialised", serialisedMap);

    Map<String, Object> item = new HashMap<>();
    item.put("interruptId", "interrupt-nested");
    item.put("interruptConfig", configMap);

    List<Object> list = new ArrayList<>();
    list.add(item);

    JSONB result = InterruptHistoriesParser.parseToJsonb(list);
    assertThat(result).isNotNull();
    assertThat(result.data()).contains("interrupt-nested");
    assertThat(result.data()).contains("test@harness.io");
  }
}
