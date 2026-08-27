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
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.rule.Owner;

import com.google.api.client.util.Base64;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jooq.JSONB;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class ProtobufBinaryParserTest extends OrchestrationVisualizationTestBase {
  private static final FailureInfo SAMPLE_FAILURE_INFO =
      FailureInfo.newBuilder().setErrorMessage("test error").addFailureTypes(FailureType.APPLICATION_FAILURE).build();

  private static String encodeProto(com.google.protobuf.Message message) {
    return Base64.encodeBase64String(message.toByteArray());
  }

  // ===================== parseToJsonb =====================

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testParseToJsonb_fromBase64String() {
    String base64 = encodeProto(SAMPLE_FAILURE_INFO);
    JSONB result = ProtobufBinaryParser.parseToJsonb(base64, FailureInfo::parseFrom);
    assertThat(result).isNotNull();
    assertThat(result.data()).contains("test error");
    assertThat(result.data()).contains("APPLICATION_FAILURE");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testParseToJsonb_fromSerialisedMap() {
    String base64 = encodeProto(SAMPLE_FAILURE_INFO);
    Map<String, Object> binaryMap = new HashMap<>();
    binaryMap.put("base64", base64);
    binaryMap.put("subType", "00");

    Map<String, Object> serialisedMap = new HashMap<>();
    serialisedMap.put("$binary", binaryMap);

    Map<String, Object> obj = new HashMap<>();
    obj.put("_serialised", serialisedMap);

    JSONB result = ProtobufBinaryParser.parseToJsonb(obj, FailureInfo::parseFrom);
    assertThat(result).isNotNull();
    assertThat(result.data()).contains("test error");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testParseToJsonb_plainJsonMap() {
    Map<String, Object> obj = new HashMap<>();
    obj.put("errorMessage", "plain error");

    JSONB result = ProtobufBinaryParser.parseToJsonb(obj, FailureInfo::parseFrom);
    assertThat(result).isNotNull();
    assertThat(result.data()).contains("plain error");
  }

  // ===================== parseListToJsonb =====================

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testParseListToJsonb_withItems() {
    String base64 = encodeProto(SAMPLE_FAILURE_INFO);
    Map<String, Object> item = new HashMap<>();
    item.put("_serialised", base64);

    List<Object> list = new ArrayList<>();
    list.add(item);

    JSONB result = ProtobufBinaryParser.parseListToJsonb(list, FailureInfo::parseFrom);
    assertThat(result).isNotNull();
    assertThat(result.data()).contains("test error");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testParseListToJsonb_withStringItems() {
    String base64 = encodeProto(SAMPLE_FAILURE_INFO);
    List<Object> list = new ArrayList<>();
    list.add(base64);

    JSONB result = ProtobufBinaryParser.parseListToJsonb(list, FailureInfo::parseFrom);
    assertThat(result).isNotNull();
    assertThat(result.data()).contains("test error");
  }

  // ===================== parseToObject =====================

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testParseToObject_fromBase64String() {
    String base64 = encodeProto(SAMPLE_FAILURE_INFO);
    Optional<FailureInfo> result = ProtobufBinaryParser.parseToObject(base64, FailureInfo::parseFrom);
    assertThat(result).isPresent();
    assertThat(result.get().getErrorMessage()).isEqualTo("test error");
    assertThat(result.get().getFailureTypesList()).contains(FailureType.APPLICATION_FAILURE);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testParseToObject_fromSerialisedMap() {
    String base64 = encodeProto(SAMPLE_FAILURE_INFO);
    Map<String, Object> binaryMap = new HashMap<>();
    binaryMap.put("base64", base64);
    binaryMap.put("subType", "00");

    Map<String, Object> serialisedMap = new HashMap<>();
    serialisedMap.put("$binary", binaryMap);

    Map<String, Object> obj = new HashMap<>();
    obj.put("_serialised", serialisedMap);

    Optional<FailureInfo> result = ProtobufBinaryParser.parseToObject(obj, FailureInfo::parseFrom);
    assertThat(result).isPresent();
    assertThat(result.get().getErrorMessage()).isEqualTo("test error");
  }
}
