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
import io.harness.execution.RetryNodeMetadata;
import io.harness.interrupts.InterruptEffect;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.contracts.interrupts.IssuedBy;
import io.harness.pms.contracts.interrupts.ManualIssuer;
import io.harness.pms.data.PmsOutcome;
import io.harness.pms.data.stepdetails.PmsStepDetails;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;

import com.google.protobuf.util.JsonFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jooq.JSONB;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class JsonbParserUtilsTest extends OrchestrationVisualizationTestBase {
  // ===================== parse =====================

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testParse_validMap() {
    JSONB jsonb = JSONB.valueOf("{\"key\":\"value\"}");
    Map result = JsonbParserUtils.parse(jsonb, Map.class);
    assertThat(result).isNotNull();
    assertThat(result.get("key")).isEqualTo("value");
  }

  // ===================== parseProto =====================

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testParseProto_valid() throws Exception {
    FailureInfo fi =
        FailureInfo.newBuilder().setErrorMessage("test error").addFailureTypes(FailureType.APPLICATION_FAILURE).build();
    String json = JsonFormat.printer().omittingInsignificantWhitespace().print(fi);
    JSONB jsonb = JSONB.valueOf(json);

    FailureInfo result = JsonbParserUtils.parseProto(jsonb, FailureInfo.getDefaultInstance());
    assertThat(result).isNotNull();
    assertThat(result.getErrorMessage()).isEqualTo("test error");
    assertThat(result.getFailureTypesList()).contains(FailureType.APPLICATION_FAILURE);
  }

  // ===================== parseProtoList =====================

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testParseProtoList_valid() {
    // Array of JSON objects (Maps)
    String arrayJson = "[{\"errorMessage\":\"err1\"}]";
    JSONB jsonb = JSONB.valueOf(arrayJson);

    List<FailureInfo> result = JsonbParserUtils.parseProtoList(jsonb, FailureInfo.getDefaultInstance());
    assertThat(result).isNotNull().hasSize(1);
    assertThat(result.get(0).getErrorMessage()).isEqualTo("err1");
  }

  // ===================== parseOutcomeDocuments =====================

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testParseOutcomeDocuments_valid() {
    JSONB jsonb = JSONB.valueOf("{\"outcome1\":{\"key1\":\"val1\"}}");
    Map<String, PmsOutcome> result = JsonbParserUtils.parseOutcomeDocuments(jsonb);
    assertThat(result).isNotNull();
    assertThat(result).containsKey("outcome1");
  }

  // ===================== parseStepDetails =====================

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testParseStepDetails_mapFormat() {
    JSONB jsonb = JSONB.valueOf("{\"detail1\":{\"field1\":\"value1\"}}");
    Map<String, PmsStepDetails> result = JsonbParserUtils.parseStepDetails(jsonb);
    assertThat(result).isNotNull();
    assertThat(result).containsKey("detail1");
    assertThat(result.get("detail1").get("field1")).isEqualTo("value1");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testParseStepDetails_arrayFormat() {
    JSONB jsonb = JSONB.valueOf("[{\"name\":\"detail1\",\"stepDetails\":{\"field1\":\"value1\"}}]");
    Map<String, PmsStepDetails> result = JsonbParserUtils.parseStepDetails(jsonb);
    assertThat(result).isNotNull();
    assertThat(result).containsKey("detail1");
    assertThat(result.get("detail1").get("field1")).isEqualTo("value1");
  }

  // ===================== parseInterruptHistories =====================

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testParseInterruptHistories_valid() throws Exception {
    InterruptConfig config =
        InterruptConfig.newBuilder()
            .setIssuedBy(IssuedBy.newBuilder()
                             .setManualIssuer(ManualIssuer.newBuilder().setEmailId("test@harness.io").build())
                             .build())
            .build();
    String configJson = JsonFormat.printer().omittingInsignificantWhitespace().print(config);
    Map<String, Object> configMap = JsonUtils.asObject(configJson, Map.class);

    Map<String, Object> item = new HashMap<>();
    item.put("interruptId", "int-1");
    item.put("tookEffectAt", 12345L);
    item.put("interruptType", "ABORT");
    item.put("interruptConfig", configMap);

    String jsonArray = JsonUtils.asJson(List.of(item));
    JSONB jsonb = JSONB.valueOf(jsonArray);

    List<InterruptEffect> result = JsonbParserUtils.parseInterruptHistories(jsonb);
    assertThat(result).isNotNull().hasSize(1);
    assertThat(result.get(0).getInterruptId()).isEqualTo("int-1");
    assertThat(result.get(0).getTookEffectAt()).isEqualTo(12345L);
    assertThat(result.get(0).getInterruptType()).isEqualTo(InterruptType.ABORT);
    assertThat(result.get(0).getInterruptConfig()).isNotNull();
    assertThat(result.get(0).getInterruptConfig().getIssuedBy().getManualIssuer().getEmailId())
        .isEqualTo("test@harness.io");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testParseInterruptHistories_withNumericInterruptType() {
    Map<String, Object> item = new HashMap<>();
    item.put("interruptId", "int-2");
    item.put("interruptType", InterruptType.ABORT.getNumber());

    String jsonArray = JsonUtils.asJson(List.of(item));
    JSONB jsonb = JSONB.valueOf(jsonArray);

    List<InterruptEffect> result = JsonbParserUtils.parseInterruptHistories(jsonb);
    assertThat(result).isNotNull().hasSize(1);
    assertThat(result.get(0).getInterruptType()).isEqualTo(InterruptType.ABORT);
  }

  // ===================== parseRetryNodeMetadata =====================

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testParseRetryNodeMetadata_valid() {
    Map<String, Object> data = new HashMap<>();
    data.put("startTs", 1000L);
    data.put("endTs", 2000L);
    data.put("runSequence", 3);
    data.put("originalPlanExecutionId", "orig-plan-1");

    JSONB jsonb = JSONB.valueOf(JsonUtils.asJson(data));
    RetryNodeMetadata result = JsonbParserUtils.parseRetryNodeMetadata(jsonb);
    assertThat(result).isNotNull();
    assertThat(result.getStartTs()).isEqualTo(1000L);
    assertThat(result.getEndTs()).isEqualTo(2000L);
    assertThat(result.getRunSequence()).isEqualTo(3);
    assertThat(result.getOriginalPlanExecutionId()).isEqualTo("orig-plan-1");
  }
}
