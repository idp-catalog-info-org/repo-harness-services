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
import io.harness.pms.contracts.ambiance.ExecutionContext;
import io.harness.rule.Owner;

import com.google.api.client.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class ExecutionContextParserTest extends OrchestrationVisualizationTestBase {
  private static final String ACCOUNT_ID = "test-account-id";

  private static final ExecutionContext SAMPLE_EXECUTION_CONTEXT = ExecutionContext.newBuilder()
                                                                       .putSetupAbstractions("accountId", ACCOUNT_ID)
                                                                       .setPlanExecutionId("plan-exec-1")
                                                                       .setPipelineIdentifier("pipeline-1")
                                                                       .setRunSequence(5)
                                                                       .build();

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testParse_fromBase64String() {
    String base64 = Base64.encodeBase64String(SAMPLE_EXECUTION_CONTEXT.toByteArray());

    Optional<ExecutionContextParser.ExecutionContextResult> result = ExecutionContextParser.parse(base64);
    assertThat(result).isPresent();
    assertThat(result.get().getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(result.get().hasAccountId()).isTrue();
    assertThat(result.get().getExecutionContext()).isNotNull();
    assertThat(result.get().hasExecutionContext()).isTrue();
    assertThat(result.get().getExecutionContext().getPipelineIdentifier()).isEqualTo("pipeline-1");
    assertThat(result.get().getExecutionContext().getRunSequence()).isEqualTo(5);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testParse_fromSerialisedMap() {
    String base64 = Base64.encodeBase64String(SAMPLE_EXECUTION_CONTEXT.toByteArray());
    Map<String, Object> binaryMap = new HashMap<>();
    binaryMap.put("base64", base64);
    binaryMap.put("subType", "00");

    Map<String, Object> serialisedMap = new HashMap<>();
    serialisedMap.put("$binary", binaryMap);

    Map<String, Object> obj = new HashMap<>();
    obj.put("_serialised", serialisedMap);

    Optional<ExecutionContextParser.ExecutionContextResult> result = ExecutionContextParser.parse(obj);
    assertThat(result).isPresent();
    assertThat(result.get().getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(result.get().getExecutionContext().getRunSequence()).isEqualTo(5);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testParse_withoutAccountId() {
    ExecutionContext ec = ExecutionContext.newBuilder().setPlanExecutionId("plan-exec-1").build();
    String base64 = Base64.encodeBase64String(ec.toByteArray());

    Optional<ExecutionContextParser.ExecutionContextResult> result = ExecutionContextParser.parse(base64);
    assertThat(result).isPresent();
    assertThat(result.get().getAccountId()).isNull();
    assertThat(result.get().hasAccountId()).isFalse();
    assertThat(result.get().getExecutionContext()).isNotNull();
  }
}
