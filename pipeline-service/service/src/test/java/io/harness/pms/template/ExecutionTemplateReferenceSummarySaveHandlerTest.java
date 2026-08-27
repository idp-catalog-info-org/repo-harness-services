/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.template;

import static io.harness.beans.FeatureName.PIPE_STORE_TEMPLATE_REFERENCE_SUMMARY_PER_EXECUTION;
import static io.harness.rule.OwnerRule.RISHABH;
import static io.harness.rule.OwnerRule.RISHIKESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.observers.NodeCreateInfo;
import io.harness.graph.stepDetail.service.NodeExecutionInfoService;
import io.harness.plan.Node;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.template.TemplateReferenceSummary;
import io.harness.pms.data.stepdetails.PmsStepDetails;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.rule.Owner;

import java.util.concurrent.ExecutorService;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class ExecutionTemplateReferenceSummarySaveHandlerTest extends CategoryTest {
  private static final String NODE_EXECUTION_ID = "nodeExecutionId";
  private static final String PLAN_EXECUTION_ID = "planExecutionId";
  private static final TemplateReferenceSummary TEMPLATE_REFERENCE_SUMMARY =
      TemplateReferenceSummary.newBuilder().setTemplateRef("account.template").setVersionLabel("v1").build();

  @Mock private NodeExecutionInfoService nodeExecutionInfoService;
  @Mock private ExecutorService executorService;

  @InjectMocks private ExecutionTemplateReferenceSummarySaveHandler handler;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    mockStatic(AmbianceUtils.class);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testOnNodeCreateWithValidTemplateReference() {
    Node node = mock(Node.class);
    Ambiance ambiance = mock(Ambiance.class);
    NodeCreateInfo nodeCreateInfo = NodeCreateInfo.builder()
                                        .node(node)
                                        .nodeExecutionId(NODE_EXECUTION_ID)
                                        .planExecutionId(PLAN_EXECUTION_ID)
                                        .ambiance(ambiance)
                                        .build();
    when(node.getTemplateReferenceSummary()).thenReturn(TEMPLATE_REFERENCE_SUMMARY);
    when(AmbianceUtils.checkIfFeatureFlagEnabled(ambiance, PIPE_STORE_TEMPLATE_REFERENCE_SUMMARY_PER_EXECUTION.name()))
        .thenReturn(true);
    handler.onNodeCreate(nodeCreateInfo);
    ArgumentCaptor<PmsStepDetails> stepDetailsCaptor = ArgumentCaptor.forClass(PmsStepDetails.class);

    verify(nodeExecutionInfoService, times(1))
        .addStepDetail(
            eq(NODE_EXECUTION_ID), eq(PLAN_EXECUTION_ID), stepDetailsCaptor.capture(), eq("templateReferenceSummary"));
    PmsStepDetails capturedStepDetails = stepDetailsCaptor.getValue();
    assertThat(capturedStepDetails.toJson())
        .isEqualTo("{\"__recast\":\"io.harness.pms.contracts.template.TemplateReferenceSummary\",\"__encodedValue\":\"{"
            + "\\n  \\\"templateRef\\\": \\\"account.template\\\",\\n  \\\"versionLabel\\\": \\\"v1\\\"\\n}\"}");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testOnNodeCreateWithFeatureFlagDisabled() {
    Node node = mock(Node.class);
    Ambiance ambiance = mock(Ambiance.class);
    NodeCreateInfo nodeCreateInfo = NodeCreateInfo.builder()
                                        .node(node)
                                        .nodeExecutionId(NODE_EXECUTION_ID)
                                        .planExecutionId(PLAN_EXECUTION_ID)
                                        .ambiance(ambiance)
                                        .build();
    when(node.getTemplateReferenceSummary()).thenReturn(TEMPLATE_REFERENCE_SUMMARY);
    when(AmbianceUtils.checkIfFeatureFlagEnabled(ambiance, PIPE_STORE_TEMPLATE_REFERENCE_SUMMARY_PER_EXECUTION.name()))
        .thenReturn(false);
    handler.onNodeCreate(nodeCreateInfo);
    verify(nodeExecutionInfoService, never()).addStepDetail(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testOnNodeCreateWithEmptyTemplateReference() {
    Node node = mock(Node.class);
    Ambiance ambiance = mock(Ambiance.class);
    NodeCreateInfo nodeCreateInfo = NodeCreateInfo.builder()
                                        .node(node)
                                        .nodeExecutionId(NODE_EXECUTION_ID)
                                        .planExecutionId(PLAN_EXECUTION_ID)
                                        .ambiance(ambiance)
                                        .build();
    TemplateReferenceSummary emptyTemplateReferenceSummary = TemplateReferenceSummary.newBuilder().build();
    when(node.getTemplateReferenceSummary()).thenReturn(emptyTemplateReferenceSummary);
    when(AmbianceUtils.checkIfFeatureFlagEnabled(ambiance, PIPE_STORE_TEMPLATE_REFERENCE_SUMMARY_PER_EXECUTION.name()))
        .thenReturn(true);
    handler.onNodeCreate(nodeCreateInfo);
    verify(nodeExecutionInfoService, never()).addStepDetail(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testOnNodeCreateWithNullNode() {
    Ambiance ambiance = mock(Ambiance.class);
    NodeCreateInfo nodeCreateInfo = NodeCreateInfo.builder()
                                        .node(null)
                                        .nodeExecutionId(NODE_EXECUTION_ID)
                                        .planExecutionId(PLAN_EXECUTION_ID)
                                        .ambiance(ambiance)
                                        .build();
    handler.onNodeCreate(nodeCreateInfo);
    verify(nodeExecutionInfoService, never()).addStepDetail(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testOnNodeCreateWithNullTemplateReference() {
    Node node = mock(Node.class);
    Ambiance ambiance = mock(Ambiance.class);
    NodeCreateInfo nodeCreateInfo = NodeCreateInfo.builder()
                                        .node(node)
                                        .nodeExecutionId(NODE_EXECUTION_ID)
                                        .planExecutionId(PLAN_EXECUTION_ID)
                                        .ambiance(ambiance)
                                        .build();
    when(node.getTemplateReferenceSummary()).thenReturn(null);
    when(AmbianceUtils.checkIfFeatureFlagEnabled(ambiance, PIPE_STORE_TEMPLATE_REFERENCE_SUMMARY_PER_EXECUTION.name()))
        .thenReturn(true);
    handler.onNodeCreate(nodeCreateInfo);
    verify(nodeExecutionInfoService, never()).addStepDetail(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testOnNodeCreateWithV1YamlVersionAndFeatureFlagDisabled() {
    // Test that template reference is stored when FF is disabled but yaml version is V1
    Node node = mock(Node.class);
    Ambiance ambiance = mock(Ambiance.class);
    TemplateReferenceSummary v1TemplateReferenceSummary = TemplateReferenceSummary.newBuilder()
                                                              .setUses("account.global_template@1.0")
                                                              .setIconName("custom_icon")
                                                              .setDescription("V1 template description")
                                                              .setName("v1_template_step")
                                                              .build();
    NodeCreateInfo nodeCreateInfo = NodeCreateInfo.builder()
                                        .node(node)
                                        .nodeExecutionId(NODE_EXECUTION_ID)
                                        .planExecutionId(PLAN_EXECUTION_ID)
                                        .ambiance(ambiance)
                                        .build();
    when(node.getTemplateReferenceSummary()).thenReturn(v1TemplateReferenceSummary);
    when(AmbianceUtils.checkIfFeatureFlagEnabled(ambiance, PIPE_STORE_TEMPLATE_REFERENCE_SUMMARY_PER_EXECUTION.name()))
        .thenReturn(false);
    when(AmbianceUtils.getPipelineVersion(ambiance)).thenReturn(HarnessYamlVersion.V1);
    handler.onNodeCreate(nodeCreateInfo);
    ArgumentCaptor<PmsStepDetails> stepDetailsCaptor = ArgumentCaptor.forClass(PmsStepDetails.class);

    verify(nodeExecutionInfoService, times(1))
        .addStepDetail(
            eq(NODE_EXECUTION_ID), eq(PLAN_EXECUTION_ID), stepDetailsCaptor.capture(), eq("templateReferenceSummary"));
    PmsStepDetails capturedStepDetails = stepDetailsCaptor.getValue();
    assertThat(capturedStepDetails.toJson()).contains("account.global_template@1.0");
    assertThat(capturedStepDetails.toJson()).contains("custom_icon");
    assertThat(capturedStepDetails.toJson()).contains("V1 template description");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testOnNodeCreateWithV0YamlVersionAndFeatureFlagDisabled() {
    // Test that template reference is NOT stored when FF is disabled and yaml version is V0
    Node node = mock(Node.class);
    Ambiance ambiance = mock(Ambiance.class);
    NodeCreateInfo nodeCreateInfo = NodeCreateInfo.builder()
                                        .node(node)
                                        .nodeExecutionId(NODE_EXECUTION_ID)
                                        .planExecutionId(PLAN_EXECUTION_ID)
                                        .ambiance(ambiance)
                                        .build();
    when(node.getTemplateReferenceSummary()).thenReturn(TEMPLATE_REFERENCE_SUMMARY);
    when(AmbianceUtils.checkIfFeatureFlagEnabled(ambiance, PIPE_STORE_TEMPLATE_REFERENCE_SUMMARY_PER_EXECUTION.name()))
        .thenReturn(false);
    when(AmbianceUtils.getPipelineVersion(ambiance)).thenReturn(HarnessYamlVersion.V0);
    handler.onNodeCreate(nodeCreateInfo);
    verify(nodeExecutionInfoService, never()).addStepDetail(any(), any(), any(), any());
  }
}
