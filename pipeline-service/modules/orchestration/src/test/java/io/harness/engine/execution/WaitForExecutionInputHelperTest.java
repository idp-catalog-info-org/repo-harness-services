/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.execution;

import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.MAYANK_AGARWAL;
import static io.harness.rule.OwnerRule.SHIVAM;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanExecutionMetadataService;
import io.harness.engine.pms.data.expression.PmsEngineExpressionService;
import io.harness.execution.ExecutionInputInstance;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.expression.common.ExpressionMode;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.waiter.WaitNotifyEngine;

import java.util.EnumSet;
import java.util.Optional;
import java.util.function.Consumer;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.PIPELINE)
public class WaitForExecutionInputHelperTest extends CategoryTest {
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private WaitNotifyEngine waitNotifyEngine;
  @Mock private ExecutionInputService executionInputService;
  @InjectMocks private WaitForExecutionInputHelper waitForExecutionInputHelper;
  @Mock private PmsEngineExpressionService pmsEngineExpressionService;
  @Mock private PlanExecutionMetadataService planExecutionMetadataService;
  @Mock private PmsFeatureFlagHelper featureFlagHelper;
  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testWaitForExecutionInput() {
    String nodeExecutionId = "nodeExecutionId";
    String template = "template";
    NodeExecution nodeExecution = NodeExecution.builder().uuid(nodeExecutionId).build();
    ArgumentCaptor<WaitForExecutionInputCallback> callbackArgumentCaptor =
        ArgumentCaptor.forClass(WaitForExecutionInputCallback.class);
    ArgumentCaptor<ExecutionInputInstance> inputInstanceArgumentCaptor =
        ArgumentCaptor.forClass(ExecutionInputInstance.class);
    String fieldYaml = "pipeline:\n  name: \"pipeline1\"\n  var: \"var/<+pipeline.name>\"\n";
    String resolvedFieldYaml = "pipeline:\n  name: pipeline1\"\n  var: var/pipeline1\n";
    doReturn(Optional.of(fieldYaml)).when(planExecutionMetadataService).getYaml(any(), any());
    Ambiance ambiance = Ambiance.newBuilder()
                            .addLevels(Level.newBuilder().setOriginalIdentifier("pipeline").buildPartial())
                            .putSetupAbstractions("accountId", "accountId")
                            .build();
    doReturn(YamlUtils.readYamlTree(resolvedFieldYaml).getNode().getCurrJsonNode())
        .when(pmsEngineExpressionService)
        .resolve(ambiance,
            YamlNode.getNodeYaml(YamlUtils.readYamlTree(fieldYaml).getNode(), ambiance.getLevelsList(), false),
            ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
    waitForExecutionInputHelper.waitForExecutionInput(
        ambiance, nodeExecution.getUuid(), PlanNode.builder().executionInputTemplate(template).build());
    verify(waitNotifyEngine, times(1)).waitForAllOnInList(any(), callbackArgumentCaptor.capture(), any(), any());
    WaitForExecutionInputCallback waitForExecutionInputCallback = callbackArgumentCaptor.getValue();

    assertNotNull(waitForExecutionInputCallback);
    assertEquals(waitForExecutionInputCallback.getNodeExecutionId(), nodeExecutionId);

    verify(executionInputService, times(1)).save(inputInstanceArgumentCaptor.capture());
    ExecutionInputInstance inputInstance = inputInstanceArgumentCaptor.getValue();

    assertNotNull(inputInstance);
    assertEquals(inputInstance.getNodeExecutionId(), nodeExecutionId);
    assertEquals(inputInstance.getTemplate(), template);
    // expressions will be resolved in the fieldYaml and then saved in executionInputInstance.
    assertEquals(inputInstance.getFieldYaml(), resolvedFieldYaml);

    // InputInstanceId should be same in inputInstance and callback.
    assertEquals(inputInstance.getInputInstanceId(), waitForExecutionInputCallback.getInputInstanceId());

    ArgumentCaptor<Consumer<Update>> opsArgumentCaptor = ArgumentCaptor.forClass(Consumer.class);
    verify(nodeExecutionService, times(1))
        .updateStatusWithOps(eq(nodeExecutionId), eq(Status.INPUT_WAITING), opsArgumentCaptor.capture(),
            eq(EnumSet.noneOf(Status.class)));

    Consumer<Update> capturedOps = opsArgumentCaptor.getValue();
    assertNotNull(capturedOps);
    Update update = new Update();
    capturedOps.accept(update);
    assertNotNull(update.getUpdateObject().get("$set", Document.class).get(NodeExecutionKeys.startTs));
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testWaitForExecutionInputResolveExpression() {
    String nodeExecutionId = "nodeExecutionId";
    String validExecutionYaml = "stage:\n"
        + "  identifier: S3\n"
        + "  type: Custom\n"
        + "  variables:\n"
        + "    - name: var2\n"
        + "      type: String\n"
        + "      value: <+input>.executionInput().allowedValues(v1,v2,v3,v4)\n";
    String template = "stage:\n"
        + "  identifier: S3\n"
        + "  type: Custom\n"
        + "  variables:\n"
        + "    - name: var2\n"
        + "      type: String\n"
        + "      value: "
        + "<+input>.executionInput().allowedValues(<+pipeline.stages.TestDep.spec.execution.steps.ShellScript_1."
        + "output.outputVariables.RFC_IQOQ>)\n";
    NodeExecution nodeExecution = NodeExecution.builder().uuid(nodeExecutionId).build();
    ArgumentCaptor<WaitForExecutionInputCallback> callbackArgumentCaptor =
        ArgumentCaptor.forClass(WaitForExecutionInputCallback.class);
    ArgumentCaptor<ExecutionInputInstance> inputInstanceArgumentCaptor =
        ArgumentCaptor.forClass(ExecutionInputInstance.class);
    String fieldYaml = "pipeline:\n  name: \"pipeline1\"\n  var: \"var/<+pipeline.name>\"\n";
    String resolvedFieldYaml = "pipeline:\n  name: pipeline1\"\n  var: var/pipeline1\n";
    doReturn(Optional.of(fieldYaml)).when(planExecutionMetadataService).getYaml(any(), any());
    Ambiance ambiance = Ambiance.newBuilder()
                            .addLevels(Level.newBuilder().setOriginalIdentifier("pipeline").buildPartial())
                            .putSetupAbstractions("accountId", "accountId")
                            .build();
    doReturn(true).when(featureFlagHelper).isEnabled(anyString(), anyString());
    doReturn(YamlUtils.readYamlTree(resolvedFieldYaml).getNode().getCurrJsonNode())
        .when(pmsEngineExpressionService)
        .resolve(ambiance,
            YamlNode.getNodeYaml(YamlUtils.readYamlTree(fieldYaml).getNode(), ambiance.getLevelsList(), false),
            ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
    doReturn(validExecutionYaml)
        .when(pmsEngineExpressionService)
        .resolve(ambiance, template, ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
    waitForExecutionInputHelper.waitForExecutionInput(
        ambiance, nodeExecution.getUuid(), PlanNode.builder().executionInputTemplate(template).build());
    verify(waitNotifyEngine, times(1)).waitForAllOnInList(any(), callbackArgumentCaptor.capture(), any(), any());
    WaitForExecutionInputCallback waitForExecutionInputCallback = callbackArgumentCaptor.getValue();

    assertNotNull(waitForExecutionInputCallback);
    assertEquals(waitForExecutionInputCallback.getNodeExecutionId(), nodeExecutionId);

    verify(executionInputService, times(1)).save(inputInstanceArgumentCaptor.capture());
    ExecutionInputInstance inputInstance = inputInstanceArgumentCaptor.getValue();

    assertNotNull(inputInstance);
    assertEquals(inputInstance.getNodeExecutionId(), nodeExecutionId);
    assertEquals(inputInstance.getTemplate(), validExecutionYaml);
    // expressions will be resolved in the fieldYaml and then saved in executionInputInstance.
    assertEquals(inputInstance.getFieldYaml(), resolvedFieldYaml);

    // InputInstanceId should be same in inputInstance and callback.
    assertEquals(inputInstance.getInputInstanceId(), waitForExecutionInputCallback.getInputInstanceId());

    ArgumentCaptor<Consumer<Update>> opsArgumentCaptor = ArgumentCaptor.forClass(Consumer.class);
    verify(nodeExecutionService, times(1))
        .updateStatusWithOps(eq(nodeExecutionId), eq(Status.INPUT_WAITING), opsArgumentCaptor.capture(),
            eq(EnumSet.noneOf(Status.class)));

    Consumer<Update> capturedOps = opsArgumentCaptor.getValue();
    assertNotNull(capturedOps);
    Update update = new Update();
    capturedOps.accept(update);
    assertNotNull(update.getUpdateObject().get("$set", Document.class).get(NodeExecutionKeys.startTs));
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testWaitForExecutionInputResolveExpressionInvalidYaml() {
    String nodeExecutionId = "nodeExecutionId";
    String invalidExecutionYaml = "stage:\n"
        + "  identifier: S3\n"
        + "  type: Custom\n"
        + "  variables:\n"
        + "    - name: var2\n"
        + "      type: String\n"
        + "      value: <+input>.executionInput().allowedValues(|-LIVE_VERSION: 2.0.12 - current image running in "
        + "rke-retailaccount-west-nonpciprod/nDEPLOY_VERSION: 2.0.13 - to be deployed into "
        + "rke-retailaccount-west-nonpciprod\\nPERFORM_CLEANUP: YES - if yes, after new image is deployed, 2.0.12 "
        + "will be deleted\\nTRAFFIC_ROUTING: 100 % traffic will be routed to 2.0.13 0 % traffic to be routed "
        + "2.0.12\\n                                              GitHub_APP_REPO: "
        + "cvs-health-source-code/account-lookup-app - Deployment status is posted to this repo\\n                   "
        + "                           APPROVAL_GROUP: account.digital_pharmacy_prod_approver)\n";
    String template = "stage:\n"
        + "  identifier: S3\n"
        + "  type: Custom\n"
        + "  variables:\n"
        + "    - name: var2\n"
        + "      type: String\n"
        + "      value: "
        + "<+input>.executionInput().allowedValues(<+pipeline.stages.TestDep.spec.execution.steps.ShellScript_1."
        + "output.outputVariables.RFC_IQOQ>)\n";
    NodeExecution nodeExecution = NodeExecution.builder().uuid(nodeExecutionId).build();
    ArgumentCaptor<WaitForExecutionInputCallback> callbackArgumentCaptor =
        ArgumentCaptor.forClass(WaitForExecutionInputCallback.class);
    ArgumentCaptor<ExecutionInputInstance> inputInstanceArgumentCaptor =
        ArgumentCaptor.forClass(ExecutionInputInstance.class);
    String fieldYaml = "pipeline:\n  name: \"pipeline1\"\n  var: \"var/<+pipeline.name>\"\n";
    String resolvedFieldYaml = "pipeline:\n  name: pipeline1\"\n  var: var/pipeline1\n";
    doReturn(Optional.of(fieldYaml)).when(planExecutionMetadataService).getYaml(any(), any());
    Ambiance ambiance = Ambiance.newBuilder()
                            .addLevels(Level.newBuilder().setOriginalIdentifier("pipeline").buildPartial())
                            .putSetupAbstractions("accountId", "accountId")
                            .build();
    doReturn(true).when(featureFlagHelper).isEnabled(anyString(), anyString());
    doReturn(YamlUtils.readYamlTree(resolvedFieldYaml).getNode().getCurrJsonNode())
        .when(pmsEngineExpressionService)
        .resolve(ambiance,
            YamlNode.getNodeYaml(YamlUtils.readYamlTree(fieldYaml).getNode(), ambiance.getLevelsList(), false),
            ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
    doReturn(invalidExecutionYaml)
        .when(pmsEngineExpressionService)
        .resolve(ambiance, template, ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
    waitForExecutionInputHelper.waitForExecutionInput(
        ambiance, nodeExecution.getUuid(), PlanNode.builder().executionInputTemplate(template).build());
    verify(waitNotifyEngine, times(1)).waitForAllOnInList(any(), callbackArgumentCaptor.capture(), any(), any());
    WaitForExecutionInputCallback waitForExecutionInputCallback = callbackArgumentCaptor.getValue();

    assertNotNull(waitForExecutionInputCallback);
    assertEquals(waitForExecutionInputCallback.getNodeExecutionId(), nodeExecutionId);

    verify(executionInputService, times(1)).save(inputInstanceArgumentCaptor.capture());
    ExecutionInputInstance inputInstance = inputInstanceArgumentCaptor.getValue();

    assertNotNull(inputInstance);
    assertEquals(inputInstance.getNodeExecutionId(), nodeExecutionId);
    assertEquals(inputInstance.getTemplate(), template);
    // expressions will be resolved in the fieldYaml and then saved in executionInputInstance.
    assertEquals(inputInstance.getFieldYaml(), resolvedFieldYaml);

    // InputInstanceId should be same in inputInstance and callback.
    assertEquals(inputInstance.getInputInstanceId(), waitForExecutionInputCallback.getInputInstanceId());

    ArgumentCaptor<Consumer<Update>> opsArgumentCaptor = ArgumentCaptor.forClass(Consumer.class);
    verify(nodeExecutionService, times(1))
        .updateStatusWithOps(eq(nodeExecutionId), eq(Status.INPUT_WAITING), opsArgumentCaptor.capture(),
            eq(EnumSet.noneOf(Status.class)));

    Consumer<Update> capturedOps = opsArgumentCaptor.getValue();
    assertNotNull(capturedOps);
    Update update = new Update();
    capturedOps.accept(update);
    assertNotNull(update.getUpdateObject().get("$set", Document.class).get(NodeExecutionKeys.startTs));
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testWaitForExecutionInputForValidYaml() {
    String executionYaml = "stage:\n"
        + "  identifier: S3\n"
        + "  type: Custom\n"
        + "  variables:\n"
        + "    - name: var2\n"
        + "      type: String\n"
        + "      value: "
        + "<+input>.executionInput().allowedValues(<+pipeline.stages.TestDep.spec.execution.steps.ShellScript_1."
        + "output.outputVariables.RFC_IQOQ>, "
        + "<+pipeline.stages.TestDep.spec.execution.steps.ShellScript_1.output.outputVariables.RFC_IQOQ>, abcd)\n";
    String executionDefaultYaml = "stage:\n"
        + "  identifier: S3\n"
        + "  type: Custom\n"
        + "  variables:\n"
        + "    - name: var2\n"
        + "      type: String\n"
        + "      value: "
        + "<+input>.executionInput().default(<+pipeline.stages.TestDep.spec.execution.steps.ShellScript_1.output."
        + "outputVariables.RFC_IQOQ>).allowedValues(<+pipeline.stages.TestDep.spec.execution.steps.ShellScript_1."
        + "output.outputVariables.RFC_IQOQ>, "
        + "<+pipeline.stages.TestDep.spec.execution.steps.ShellScript_1.output.outputVariables.RFC_IQOQ>, abcd)\n";
    String executionDefaultYaml2 = "stage:\n"
        + "  identifier: S3\n"
        + "  type: Custom\n"
        + "  variables:\n"
        + "    - name: var2\n"
        + "      type: String\n"
        + "      value: "
        + "<+input>.executionInput().default(v1).allowedValues(<+pipeline.stages.TestDep.spec.execution.steps."
        + "ShellScript_1.output.outputVariables.RFC_IQOQ>, "
        + "<+pipeline.stages.TestDep.spec.execution.steps.ShellScript_1.output.outputVariables.RFC_IQOQ>, abcd, "
        + "v2)\n";
    String executionDefaultYaml3 = "stage:\n"
        + "  identifier: S3\n"
        + "  type: Custom\n"
        + "  variables:\n"
        + "    - name: var2\n"
        + "      type: String\n"
        + "      value: "
        + "<+input>.executionInput().allowedValues(<+pipeline.stages.TestDep.spec.execution.steps.ShellScript_1."
        + "output.outputVariables.RFC_IQOQ>, "
        + "<+pipeline.stages.TestDep.spec.execution.steps.ShellScript_1.output.outputVariables.RFC_IQOQ>, abcd, "
        + "v2).default(v1)\n";
    String executionDefaultYaml4 = "stage:\n"
        + "  identifier: S3\n"
        + "  type: Custom\n"
        + "  variables:\n"
        + "    - name: var2\n"
        + "      type: String\n"
        + "      value: <+input>.executionInput().default(v1).allowedValues(v1,v2)\n";
    String executionYamlWithoutExpression = "stage:\n"
        + "  identifier: S3\n"
        + "  type: Custom\n"
        + "  variables:\n"
        + "    - name: var2\n"
        + "      type: String\n"
        + "      value: <+input>.executionInput().allowedValues(v1,v2,v3,v4)\n";
    String invalidExecutionYaml = "stage:\n"
        + "  identifier: S3\n"
        + "  type: Custom\n"
        + "  variables:\n"
        + "    - name: var2\n"
        + "      type: String\n"
        + "      value: <+input>.executionInput().allowedValues(|-LIVE_VERSION: 2.0.12 - current image running in "
        + "rke-retailaccount-west-nonpciprod/nDEPLOY_VERSION: 2.0.13 - to be deployed into "
        + "rke-retailaccount-west-nonpciprod\\nPERFORM_CLEANUP: YES - if yes, after new image is deployed, 2.0.12 "
        + "will be deleted\\nTRAFFIC_ROUTING: 100 % traffic will be routed to 2.0.13 0 % traffic to be routed "
        + "2.0.12\\n                                              GitHub_APP_REPO: "
        + "cvs-health-source-code/account-lookup-app - Deployment status is posted to this repo\\n                   "
        + "                           APPROVAL_GROUP: account.digital_pharmacy_prod_approver)\n";
    assertFalse(YamlUtils.isValidYaml(invalidExecutionYaml));
    assertTrue(YamlUtils.isValidYaml(executionYamlWithoutExpression));
    assertTrue(waitForExecutionInputHelper.containExpressionInAllowedValues(executionYaml));
    assertTrue(waitForExecutionInputHelper.containExpressionInAllowedValues(executionDefaultYaml));
    assertTrue(waitForExecutionInputHelper.containExpressionInAllowedValues(executionDefaultYaml2));
    assertTrue(waitForExecutionInputHelper.containExpressionInAllowedValues(executionDefaultYaml3));
    assertFalse(waitForExecutionInputHelper.containExpressionInAllowedValues(executionDefaultYaml4));
    assertFalse(waitForExecutionInputHelper.containExpressionInAllowedValues(executionYamlWithoutExpression));
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testWaitForExecutionInputV1FallbackOnFieldYamlResolutionFailure() {
    String nodeExecutionId = "nodeExecutionId";
    String template = "run:\n  script: <+input>.executionInput()\n";
    NodeExecution nodeExecution = NodeExecution.builder().uuid(nodeExecutionId).build();
    ArgumentCaptor<WaitForExecutionInputCallback> callbackArgumentCaptor =
        ArgumentCaptor.forClass(WaitForExecutionInputCallback.class);
    ArgumentCaptor<ExecutionInputInstance> inputInstanceArgumentCaptor =
        ArgumentCaptor.forClass(ExecutionInputInstance.class);
    String v1PipelineYaml = "stages:\n"
        + "  - id: stage1\n"
        + "    steps:\n"
        + "      - id: ShellScript_1\n"
        + "        run:\n"
        + "          script: echo hello\n";
    doReturn(Optional.of(v1PipelineYaml)).when(planExecutionMetadataService).getYaml(any(), any());
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setMetadata(ExecutionMetadata.newBuilder().setHarnessVersion(HarnessYamlVersion.V1).build())
            .addLevels(Level.newBuilder().setOriginalIdentifier("pipeline").buildPartial())
            .addLevels(Level.newBuilder().setOriginalIdentifier("stages").buildPartial())
            .addLevels(Level.newBuilder().setOriginalIdentifier("stage1").buildPartial())
            .putSetupAbstractions("accountId", "accountId")
            .build();
    waitForExecutionInputHelper.waitForExecutionInput(
        ambiance, nodeExecution.getUuid(), PlanNode.builder().executionInputTemplate(template).build());

    verify(waitNotifyEngine, times(1)).waitForAllOnInList(any(), callbackArgumentCaptor.capture(), any(), any());
    WaitForExecutionInputCallback waitForExecutionInputCallback = callbackArgumentCaptor.getValue();
    assertNotNull(waitForExecutionInputCallback);
    assertEquals(waitForExecutionInputCallback.getNodeExecutionId(), nodeExecutionId);

    verify(executionInputService, times(1)).save(inputInstanceArgumentCaptor.capture());
    ExecutionInputInstance inputInstance = inputInstanceArgumentCaptor.getValue();
    assertNotNull(inputInstance);
    assertEquals(inputInstance.getNodeExecutionId(), nodeExecutionId);
    assertEquals(inputInstance.getTemplate(), template);
    assertEquals(inputInstance.getFieldYaml(), "{}");
    assertEquals(inputInstance.getInputInstanceId(), waitForExecutionInputCallback.getInputInstanceId());
  }
}
