/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.pms.expressions.functors;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.yaml.YAMLFieldNameConstants.VARIABLES;
import static io.harness.rule.OwnerRule.AVEESHA_JINDAL;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.execution.NodeExecution;
import io.harness.notification.NotificationConstants;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.rule.Owner;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class NotificationFunctorTest extends CategoryTest {
  private Map<String, Object> resolutionMetadata;
  private NotificationFunctor notificationFunctor;

  @Mock private NodeExecutionService nodeExecutionService;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    resolutionMetadata = new HashMap<>();
    notificationFunctor = new NotificationFunctor(null, resolutionMetadata, null);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testBind_withEventType() {
    resolutionMetadata.put(NotificationConstants.EVENT_TYPE, "Pipeline_Success");
    Map<String, Object> result = (Map<String, Object>) notificationFunctor.bind();
    assertEquals("Pipeline_Success", result.get(NotificationConstants.EVENT_TYPE));
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testBind_withVariables() throws JsonProcessingException {
    Map<String, String> varMap = new HashMap<>();
    varMap.put("key1", "value1");
    resolutionMetadata.put(VARIABLES, varMap);
    Map<String, Object> result = (Map<String, Object>) notificationFunctor.bind();
    assertEquals(varMap, result.get("variables"));
  }

  /**
   * Verifies that when failure info contains YAML-special characters (e.g. '*' which is an alias in YAML),
   * the error message is serialized as a JSON string so that when substituted into notification YAML it
   * remains valid and does not cause YamlPipelineUtils.readAsJsonNode to throw MarkedYAMLException.
   */
  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testBind_withFailureInfoContainingYamlSpecialCharacters_serializesAsJsonString()
      throws JsonProcessingException {
    String planExecutionId = "plan-exec-id";
    String errorMessageWithYamlSpecialChars = "* exit status 1</span>";
    NodeExecution nodeExecution =
        NodeExecution.builder()
            .uuid("node-uuid")
            .failureInfo(
                FailureInfo.newBuilder()
                    .addFailureData(FailureData.newBuilder().setMessage(errorMessageWithYamlSpecialChars).build())
                    .build())
            .build();

    doReturn(Optional.of(nodeExecution))
        .when(nodeExecutionService)
        .getPipelineNodeExecutionWithProjections(eq(planExecutionId), any());

    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(planExecutionId)
            .setMetadata(ExecutionMetadata.newBuilder()
                             .putAllFeatureFlagToValueMap(Map.of("PIPE_NOTIFICATION_TEMPLATE_FALLBACK", true))
                             .build())
            .build();

    // resolutionMetadata must be non-empty or functor returns null before resolving errorMessage
    resolutionMetadata.put(NotificationConstants.EVENT_TYPE, "Pipeline_Failed");

    notificationFunctor = new NotificationFunctor(ambiance, resolutionMetadata, nodeExecutionService);
    Map<String, Object> result = (Map<String, Object>) notificationFunctor.bind();

    assertNotNull(result);
    Object errorMessage = result.get("errorMessage");
    assertNotNull(errorMessage);

    // Error message must be JSON-stringified so that when injected into YAML it is a quoted scalar
    // (e.g. notificationContent: "* exit status 1</span>") and does not break YAML parsing.
    String expectedJsonString = NG_DEFAULT_OBJECT_MAPPER.writeValueAsString(errorMessageWithYamlSpecialChars);
    assertEquals(expectedJsonString, errorMessage);
  }
}
