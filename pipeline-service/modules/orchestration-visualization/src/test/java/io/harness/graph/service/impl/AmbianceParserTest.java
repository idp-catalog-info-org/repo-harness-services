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
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.rule.Owner;

import com.google.api.client.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class AmbianceParserTest extends OrchestrationVisualizationTestBase {
  private static final String ACCOUNT_ID = "test-account-id";
  private static final String PLAN_EXECUTION_ID = "test-plan-execution-id";

  private static final Ambiance SAMPLE_AMBIANCE =
      Ambiance.newBuilder().putSetupAbstractions("accountId", ACCOUNT_ID).setPlanExecutionId(PLAN_EXECUTION_ID).build();

  // ===================== parse from binary =====================

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testParse_fromBase64String() {
    String base64 = Base64.encodeBase64String(SAMPLE_AMBIANCE.toByteArray());

    Optional<AmbianceParser.AmbianceResult> result = AmbianceParser.parse(base64);
    assertThat(result).isPresent();
    assertThat(result.get().getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(result.get().getPlanExecutionId()).isEqualTo(PLAN_EXECUTION_ID);
    assertThat(result.get().getAmbiance()).isNotNull();
    assertThat(result.get().hasAccountId()).isTrue();
    assertThat(result.get().hasPlanExecutionId()).isTrue();
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testParse_fromSerialisedMap() {
    String base64 = Base64.encodeBase64String(SAMPLE_AMBIANCE.toByteArray());
    Map<String, Object> binaryMap = new HashMap<>();
    binaryMap.put("base64", base64);
    binaryMap.put("subType", "00");

    Map<String, Object> serialisedMap = new HashMap<>();
    serialisedMap.put("$binary", binaryMap);

    Map<String, Object> obj = new HashMap<>();
    obj.put("_serialised", serialisedMap);

    Optional<AmbianceParser.AmbianceResult> result = AmbianceParser.parse(obj);
    assertThat(result).isPresent();
    assertThat(result.get().getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(result.get().getPlanExecutionId()).isEqualTo(PLAN_EXECUTION_ID);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testParse_fromSerialisedString() {
    String base64 = Base64.encodeBase64String(SAMPLE_AMBIANCE.toByteArray());
    Map<String, Object> obj = new HashMap<>();
    obj.put("_serialised", base64);

    Optional<AmbianceParser.AmbianceResult> result = AmbianceParser.parse(obj);
    assertThat(result).isPresent();
    assertThat(result.get().getAccountId()).isEqualTo(ACCOUNT_ID);
  }

  // ===================== parse from plain map =====================

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testParse_fromPlainMap() {
    Map<String, Object> setupAbstractions = new HashMap<>();
    setupAbstractions.put("accountId", ACCOUNT_ID);

    Map<String, Object> map = new HashMap<>();
    map.put("setupAbstractions", setupAbstractions);
    map.put("planExecutionId", PLAN_EXECUTION_ID);

    Optional<AmbianceParser.AmbianceResult> result = AmbianceParser.parse(map);
    assertThat(result).isPresent();
    assertThat(result.get().getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(result.get().getPlanExecutionId()).isEqualTo(PLAN_EXECUTION_ID);
    // Plain map path does not produce an Ambiance object
    assertThat(result.get().getAmbiance()).isNull();
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testParse_ambianceWithoutAccountId() {
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId(PLAN_EXECUTION_ID).build();
    String base64 = Base64.encodeBase64String(ambiance.toByteArray());

    Optional<AmbianceParser.AmbianceResult> result = AmbianceParser.parse(base64);
    assertThat(result).isPresent();
    assertThat(result.get().getAccountId()).isNull();
    assertThat(result.get().hasAccountId()).isFalse();
    assertThat(result.get().getPlanExecutionId()).isEqualTo(PLAN_EXECUTION_ID);
  }

  // ===================== extractAccountIdFromSubField =====================

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testExtractAccountIdFromSubField_accountId() {
    Optional<String> result =
        AmbianceParser.extractAccountIdFromSubField("ambiance.setupAbstractions.accountId", "abc123");
    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo("abc123");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testExtractAccountIdFromSubField_irrelevantField() {
    Optional<String> result = AmbianceParser.extractAccountIdFromSubField("ambiance.planExecutionId", "someValue");
    assertThat(result).isEmpty();
  }
}
