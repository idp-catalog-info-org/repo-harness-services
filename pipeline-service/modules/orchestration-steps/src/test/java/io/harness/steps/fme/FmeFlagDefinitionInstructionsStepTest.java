/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.fme;

import static io.harness.rule.OwnerRule.KESHAV;
import static io.harness.steps.StepUtils.PIE_SIMPLIFY_LOG_BASE_KEY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.fme.Bucket;
import io.harness.fme.FMEPipelineClient;
import io.harness.fme.FeatureFlag;
import io.harness.fme.FeatureFlagDefinition;
import io.harness.fme.FmePatchOperation;
import io.harness.fme.FmeResponse;
import io.harness.fme.TargetingRulesDTO;
import io.harness.fme.Treatment;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.LogStreamingStepClientImpl;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagHelper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.split.client.dtos.URN;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.FME)
@RunWith(MockitoJUnitRunner.class)
public class FmeFlagDefinitionInstructionsStepTest extends CategoryTest {
  @InjectMocks FmeFlagDefinitionInstructionsStep step;
  private Ambiance ambiance;
  @Mock private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Mock private FMEPipelineClient fmePipelineClient;
  @Spy private FmeStepResponseBuilder fmeStepResponseBuilder;
  @Spy private ExceptionManager exceptionManager;

  private static final String ACCOUNT_ID = "testAccountId";
  private static final String ORG_ID = "testOrgId";
  private static final String PROJECT_ID = "testProjectId";
  private static final String FLAG = "my_feature_flag";
  private static final String ENV = "staging";

  @Before
  public void setup() throws Exception {
    LogStreamingStepClientImpl logClient = mock(LogStreamingStepClientImpl.class);
    Mockito.when(logStreamingStepClientFactory.getLogStreamingStepClient(any())).thenReturn(logClient);
    ambiance =
        Ambiance.newBuilder()
            .putSetupAbstractions("accountId", ACCOUNT_ID)
            .putSetupAbstractions("orgIdentifier", ORG_ID)
            .putSetupAbstractions("projectIdentifier", PROJECT_ID)
            .setMetadata(
                ExecutionMetadata.newBuilder().putFeatureFlagToValueMap(PIE_SIMPLIFY_LOG_BASE_KEY, false).build())
            .build();
    Mockito.when(pmsFeatureFlagHelper.isEnabled(anyString(), any(FeatureName.class))).thenReturn(false);
    fmeStepResponseBuilder.setExceptionManager(exceptionManager);

    mockGetDefinition(buildDefinition(Treatment.builder().name("on").build(), Treatment.builder().name("off").build()));
  }

  private StepResponse executeWith(FmeFlagDefinitionInstructionsStepParameters params) {
    StepBaseParameters base = mock(StepBaseParameters.class);
    when(base.getSpec()).thenReturn(params);
    return step.executeSyncAfterRbac(ambiance, base, StepInputPackage.builder().build(), null);
  }

  private FmeFlagDefinitionInstructionsStepParameters buildParams(
      String flagName, String env, List<FmeDefinitionInstruction> instructions) {
    var builder = FmeFlagDefinitionInstructionsStepParameters.builder();
    if (flagName != null) {
      builder.flagName(ParameterField.createValueField(flagName));
    }
    if (env != null) {
      builder.environment(ParameterField.createValueField(env));
    }
    if (instructions != null) {
      builder.instructions(ParameterField.createValueField(instructions));
    }
    return builder.build();
  }

  private void mockPatchCall() throws Exception {
    Call<FmeResponse<FeatureFlagDefinition>> patchCall = mock(Call.class);
    Response<FmeResponse<FeatureFlagDefinition>> patchResponse = mock(Response.class);
    when(patchCall.execute()).thenReturn(patchResponse);
    when(patchResponse.isSuccessful()).thenReturn(true);
    when(patchResponse.body())
        .thenReturn(FmeResponse.<FeatureFlagDefinition>builder().entity(mock(FeatureFlagDefinition.class)).build());
    when(fmePipelineClient.patchFeatureFlagDefinition(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV), eq(FLAG), anyList()))
        .thenReturn(patchCall);
  }

  private void mockGetDefinition(FeatureFlagDefinition definition) throws Exception {
    Call<FmeResponse<FeatureFlagDefinition>> getCall = mock(Call.class);
    Response<FmeResponse<FeatureFlagDefinition>> getResponse = mock(Response.class);
    when(getCall.execute()).thenReturn(getResponse);
    when(getResponse.isSuccessful()).thenReturn(true);
    when(getResponse.body()).thenReturn(FmeResponse.<FeatureFlagDefinition>builder().entity(definition).build());
    when(fmePipelineClient.getFeatureFlagDefinitionInEnvironment(
             eq(FLAG), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV)))
        .thenReturn(getCall);
  }

  private FeatureFlagDefinition buildDefinition(Treatment... treatments) {
    URN envUrn = new URN();
    envUrn.name = ENV;
    envUrn.type = "Environment";
    return FeatureFlagDefinition.builder()
        .name(FLAG)
        .environment(envUrn)
        .treatments(new ArrayList<>(Arrays.asList(treatments)))
        .build();
  }

  // --- Simple instruction tests ---

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldSetDefaultTreatment() throws Exception {
    mockPatchCall();
    List<FmeDefinitionInstruction> instructions =
        List.of(FmeSetDefaultTreatmentInstruction.builder().value(ParameterField.createValueField("on")).build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<List<FmePatchOperation>> captor = ArgumentCaptor.forClass(List.class);
    verify(fmePipelineClient)
        .patchFeatureFlagDefinition(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV), eq(FLAG), captor.capture());
    List<FmePatchOperation> ops = captor.getValue();
    assertThat(ops).hasSize(1);
    assertThat(ops.get(0).getOp()).isEqualTo("replace");
    assertThat(ops.get(0).getPath()).isEqualTo("/defaultTreatment");
    assertThat(ops.get(0).getValue()).isEqualTo("on");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldSetBaselineTreatment() throws Exception {
    mockPatchCall();
    List<FmeDefinitionInstruction> instructions =
        List.of(FmeSetBaselineTreatmentInstruction.builder().value(ParameterField.createValueField("off")).build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<List<FmePatchOperation>> captor = ArgumentCaptor.forClass(List.class);
    verify(fmePipelineClient)
        .patchFeatureFlagDefinition(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV), eq(FLAG), captor.capture());
    List<FmePatchOperation> ops = captor.getValue();
    assertThat(ops).hasSize(1);
    assertThat(ops.get(0).getPath()).isEqualTo("/baselineTreatment");
    assertThat(ops.get(0).getValue()).isEqualTo("off");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldSetTrackImpressionTrue() throws Exception {
    mockPatchCall();
    List<FmeDefinitionInstruction> instructions =
        List.of(FmeSetTrackImpressionInstruction.builder().value(ParameterField.createValueField(true)).build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<List<FmePatchOperation>> captor = ArgumentCaptor.forClass(List.class);
    verify(fmePipelineClient)
        .patchFeatureFlagDefinition(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV), eq(FLAG), captor.capture());
    List<FmePatchOperation> ops = captor.getValue();
    assertThat(ops).hasSize(1);
    assertThat(ops.get(0).getPath()).isEqualTo("/impressionsDisabled");
    assertThat(ops.get(0).getValue()).isEqualTo(false);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldSetTrackImpressionFalse() throws Exception {
    mockPatchCall();
    List<FmeDefinitionInstruction> instructions =
        List.of(FmeSetTrackImpressionInstruction.builder().value(ParameterField.createValueField(false)).build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<List<FmePatchOperation>> captor = ArgumentCaptor.forClass(List.class);
    verify(fmePipelineClient)
        .patchFeatureFlagDefinition(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV), eq(FLAG), captor.capture());
    assertThat(captor.getValue().get(0).getValue()).isEqualTo(true);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldSetLimitExposure() throws Exception {
    mockPatchCall();
    List<FmeDefinitionInstruction> instructions =
        List.of(FmeSetLimitExposureInstruction.builder().value(ParameterField.createValueField(50)).build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<List<FmePatchOperation>> captor = ArgumentCaptor.forClass(List.class);
    verify(fmePipelineClient)
        .patchFeatureFlagDefinition(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV), eq(FLAG), captor.capture());
    assertThat(captor.getValue().get(0).getPath()).isEqualTo("/trafficAllocation");
    assertThat(captor.getValue().get(0).getValue()).isEqualTo(50);
  }

  // --- UpdateIndividualTargets tests ---

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldAddKeys() throws Exception {
    FeatureFlagDefinition definition =
        buildDefinition(Treatment.builder().name("on").keys(new ArrayList<>(List.of("existing1"))).build(),
            Treatment.builder().name("off").build());
    mockGetDefinition(definition);
    mockPatchCall();

    List<FmeDefinitionInstruction> instructions = List.of(
        FmeUpdateIndividualTargetsInstruction.builder()
            .value(
                ParameterField.createValueField(List.of(IndividualTargetUpdate.builder()
                                                            .treatment("on")
                                                            .actions(List.of(TargetAction.builder()
                                                                                 .action(TargetActionType.AddKeys)
                                                                                 .value(List.of("newKey1", "newKey2"))
                                                                                 .build()))
                                                            .build())))
            .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<List<FmePatchOperation>> captor = ArgumentCaptor.forClass(List.class);
    verify(fmePipelineClient)
        .patchFeatureFlagDefinition(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV), eq(FLAG), captor.capture());
    List<FmePatchOperation> ops = captor.getValue();
    assertThat(ops).hasSize(1);
    assertThat(ops.get(0).getOp()).isEqualTo("add");
    assertThat(ops.get(0).getPath()).isEqualTo("/treatments/0/keys");
    assertThat((List<String>) ops.get(0).getValue()).containsExactly("existing1", "newKey1", "newKey2");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldAddKeysDeduplicate() throws Exception {
    FeatureFlagDefinition definition =
        buildDefinition(Treatment.builder().name("on").keys(new ArrayList<>(List.of("key1", "key2"))).build());
    mockGetDefinition(definition);
    mockPatchCall();

    List<FmeDefinitionInstruction> instructions = List.of(
        FmeUpdateIndividualTargetsInstruction.builder()
            .value(ParameterField.createValueField(List.of(
                IndividualTargetUpdate.builder()
                    .treatment("on")
                    .actions(List.of(
                        TargetAction.builder().action(TargetActionType.AddKeys).value(List.of("key2", "key3")).build()))
                    .build())))
            .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<List<FmePatchOperation>> captor = ArgumentCaptor.forClass(List.class);
    verify(fmePipelineClient)
        .patchFeatureFlagDefinition(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV), eq(FLAG), captor.capture());
    List<FmePatchOperation> ops = captor.getValue();
    assertThat(ops).hasSize(1);
    assertThat(ops.get(0).getOp()).isEqualTo("add");
    assertThat(ops.get(0).getPath()).isEqualTo("/treatments/0/keys");
    assertThat((List<String>) ops.get(0).getValue()).containsExactly("key1", "key2", "key3");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldRemoveKeys() throws Exception {
    FeatureFlagDefinition definition =
        buildDefinition(Treatment.builder().name("on").keys(new ArrayList<>(List.of("key1", "key2", "key3"))).build());
    mockGetDefinition(definition);
    mockPatchCall();

    List<FmeDefinitionInstruction> instructions = List.of(
        FmeUpdateIndividualTargetsInstruction.builder()
            .value(ParameterField.createValueField(List.of(IndividualTargetUpdate.builder()
                                                               .treatment("on")
                                                               .actions(List.of(TargetAction.builder()
                                                                                    .action(TargetActionType.RemoveKeys)
                                                                                    .value(List.of("key1", "key3"))
                                                                                    .build()))
                                                               .build())))
            .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<List<FmePatchOperation>> captor = ArgumentCaptor.forClass(List.class);
    verify(fmePipelineClient)
        .patchFeatureFlagDefinition(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV), eq(FLAG), captor.capture());
    List<FmePatchOperation> ops = captor.getValue();
    assertThat(ops).hasSize(1);
    assertThat(ops.get(0).getOp()).isEqualTo("add");
    assertThat(ops.get(0).getPath()).isEqualTo("/treatments/0/keys");
    assertThat((List<String>) ops.get(0).getValue()).containsExactly("key2");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldSetKeys() throws Exception {
    FeatureFlagDefinition definition =
        buildDefinition(Treatment.builder().name("on").keys(new ArrayList<>(List.of("old1", "old2"))).build());
    mockGetDefinition(definition);
    mockPatchCall();

    List<FmeDefinitionInstruction> instructions = List.of(
        FmeUpdateIndividualTargetsInstruction.builder()
            .value(ParameterField.createValueField(List.of(
                IndividualTargetUpdate.builder()
                    .treatment("on")
                    .actions(List.of(
                        TargetAction.builder().action(TargetActionType.SetKeys).value(List.of("new1", "new2")).build()))
                    .build())))
            .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<List<FmePatchOperation>> captor = ArgumentCaptor.forClass(List.class);
    verify(fmePipelineClient)
        .patchFeatureFlagDefinition(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV), eq(FLAG), captor.capture());
    List<FmePatchOperation> ops = captor.getValue();
    assertThat(ops).hasSize(1);
    assertThat(ops.get(0).getOp()).isEqualTo("add");
    assertThat(ops.get(0).getPath()).isEqualTo("/treatments/0/keys");
    assertThat((List<String>) ops.get(0).getValue()).containsExactly("new1", "new2");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldAddAndRemoveSegments() throws Exception {
    FeatureFlagDefinition definition = buildDefinition(
        Treatment.builder().name("on").segments(new ArrayList<>(List.of("seg1", "seg2", "seg3"))).build());
    mockGetDefinition(definition);
    mockPatchCall();

    List<FmeDefinitionInstruction> instructions = List.of(
        FmeUpdateIndividualTargetsInstruction.builder()
            .value(ParameterField.createValueField(List.of(
                IndividualTargetUpdate.builder()
                    .treatment("on")
                    .actions(List.of(
                        TargetAction.builder().action(TargetActionType.AddSegments).value(List.of("seg4")).build(),
                        TargetAction.builder().action(TargetActionType.RemoveSegments).value(List.of("seg1")).build()))
                    .build())))
            .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<List<FmePatchOperation>> captor = ArgumentCaptor.forClass(List.class);
    verify(fmePipelineClient)
        .patchFeatureFlagDefinition(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV), eq(FLAG), captor.capture());
    List<FmePatchOperation> ops = captor.getValue();
    assertThat(ops).hasSize(1);
    assertThat(ops.get(0).getOp()).isEqualTo("add");
    assertThat(ops.get(0).getPath()).isEqualTo("/treatments/0/segments");
    assertThat((List<String>) ops.get(0).getValue()).containsExactly("seg2", "seg3", "seg4");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldAddSegmentsDeduplicateAcrossAllFields() throws Exception {
    FeatureFlagDefinition definition = buildDefinition(Treatment.builder()
                                                           .name("on")
                                                           .segments(new ArrayList<>(List.of("seg1")))
                                                           .largeSegments(new ArrayList<>(List.of("large1")))
                                                           .ruleBasedSegments(new ArrayList<>(List.of("rule1")))
                                                           .build());
    mockGetDefinition(definition);
    mockPatchCall();

    List<FmeDefinitionInstruction> instructions =
        List.of(FmeUpdateIndividualTargetsInstruction.builder()
                    .value(ParameterField.createValueField(
                        List.of(IndividualTargetUpdate.builder()
                                    .treatment("on")
                                    .actions(List.of(TargetAction.builder()
                                                         .action(TargetActionType.AddSegments)
                                                         .value(List.of("seg1", "large1", "rule1", "new1"))
                                                         .build()))
                                    .build())))
                    .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<List<FmePatchOperation>> captor = ArgumentCaptor.forClass(List.class);
    verify(fmePipelineClient)
        .patchFeatureFlagDefinition(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV), eq(FLAG), captor.capture());
    List<FmePatchOperation> ops = captor.getValue();
    FmePatchOperation segOp =
        ops.stream().filter(op -> "/treatments/0/segments".equals(op.getPath())).findFirst().get();
    assertThat((List<String>) segOp.getValue()).containsExactly("seg1", "new1");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldRemoveRuleBasedSegment() throws Exception {
    FeatureFlagDefinition definition =
        buildDefinition(Treatment.builder()
                            .name("on")
                            .segments(new ArrayList<>(List.of("seg1")))
                            .ruleBasedSegments(new ArrayList<>(List.of("rule1", "rule2")))
                            .build());
    mockGetDefinition(definition);
    mockPatchCall();

    List<FmeDefinitionInstruction> instructions = List.of(
        FmeUpdateIndividualTargetsInstruction.builder()
            .value(ParameterField.createValueField(List.of(
                IndividualTargetUpdate.builder()
                    .treatment("on")
                    .actions(List.of(
                        TargetAction.builder().action(TargetActionType.RemoveSegments).value(List.of("rule1")).build()))
                    .build())))
            .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<List<FmePatchOperation>> captor = ArgumentCaptor.forClass(List.class);
    verify(fmePipelineClient)
        .patchFeatureFlagDefinition(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV), eq(FLAG), captor.capture());
    List<FmePatchOperation> ops = captor.getValue();
    FmePatchOperation ruleOp =
        ops.stream().filter(op -> "/treatments/0/ruleBasedSegments".equals(op.getPath())).findFirst().get();
    assertThat((List<String>) ruleOp.getValue()).containsExactly("rule2");
    assertThat(ops.stream().noneMatch(op -> "/treatments/0/segments".equals(op.getPath()))).isTrue();
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldRemoveSegmentsAcrossAllFields() throws Exception {
    FeatureFlagDefinition definition = buildDefinition(Treatment.builder()
                                                           .name("on")
                                                           .segments(new ArrayList<>(List.of("seg1", "seg2")))
                                                           .largeSegments(new ArrayList<>(List.of("large1")))
                                                           .ruleBasedSegments(new ArrayList<>(List.of("rule1")))
                                                           .build());
    mockGetDefinition(definition);
    mockPatchCall();

    List<FmeDefinitionInstruction> instructions =
        List.of(FmeUpdateIndividualTargetsInstruction.builder()
                    .value(ParameterField.createValueField(
                        List.of(IndividualTargetUpdate.builder()
                                    .treatment("on")
                                    .actions(List.of(TargetAction.builder()
                                                         .action(TargetActionType.RemoveSegments)
                                                         .value(List.of("seg1", "large1", "rule1"))
                                                         .build()))
                                    .build())))
                    .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<List<FmePatchOperation>> captor = ArgumentCaptor.forClass(List.class);
    verify(fmePipelineClient)
        .patchFeatureFlagDefinition(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV), eq(FLAG), captor.capture());
    List<FmePatchOperation> ops = captor.getValue();

    FmePatchOperation segOp =
        ops.stream().filter(op -> "/treatments/0/segments".equals(op.getPath())).findFirst().get();
    assertThat((List<String>) segOp.getValue()).containsExactly("seg2");

    FmePatchOperation largeOp =
        ops.stream().filter(op -> "/treatments/0/largeSegments".equals(op.getPath())).findFirst().get();
    assertThat((List<String>) largeOp.getValue()).isEmpty();

    FmePatchOperation ruleOp =
        ops.stream().filter(op -> "/treatments/0/ruleBasedSegments".equals(op.getPath())).findFirst().get();
    assertThat((List<String>) ruleOp.getValue()).isEmpty();
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldSetSegmentsClearAllTypedFields() throws Exception {
    FeatureFlagDefinition definition = buildDefinition(Treatment.builder()
                                                           .name("on")
                                                           .segments(new ArrayList<>(List.of("old1")))
                                                           .largeSegments(new ArrayList<>(List.of("large1")))
                                                           .ruleBasedSegments(new ArrayList<>(List.of("rule1")))
                                                           .build());
    mockGetDefinition(definition);
    mockPatchCall();

    List<FmeDefinitionInstruction> instructions = List.of(
        FmeUpdateIndividualTargetsInstruction.builder()
            .value(
                ParameterField.createValueField(List.of(IndividualTargetUpdate.builder()
                                                            .treatment("on")
                                                            .actions(List.of(TargetAction.builder()
                                                                                 .action(TargetActionType.SetSegments)
                                                                                 .value(List.of("Group1", "Group2"))
                                                                                 .build()))
                                                            .build())))
            .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<List<FmePatchOperation>> captor = ArgumentCaptor.forClass(List.class);
    verify(fmePipelineClient)
        .patchFeatureFlagDefinition(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV), eq(FLAG), captor.capture());
    List<FmePatchOperation> ops = captor.getValue();

    FmePatchOperation segOp =
        ops.stream().filter(op -> "/treatments/0/segments".equals(op.getPath())).findFirst().get();
    assertThat((List<String>) segOp.getValue()).containsExactly("Group1", "Group2");

    FmePatchOperation largeOp =
        ops.stream().filter(op -> "/treatments/0/largeSegments".equals(op.getPath())).findFirst().get();
    assertThat((List<String>) largeOp.getValue()).isEmpty();

    FmePatchOperation ruleOp =
        ops.stream().filter(op -> "/treatments/0/ruleBasedSegments".equals(op.getPath())).findFirst().get();
    assertThat((List<String>) ruleOp.getValue()).isEmpty();
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldOnlyModifySpecifiedTreatment() throws Exception {
    FeatureFlagDefinition definition = buildDefinition(
        Treatment.builder().name("on").keys(new ArrayList<>(List.of("k1"))).configurations("{\"a\":1}").build(),
        Treatment.builder().name("off").keys(new ArrayList<>(List.of("k2"))).configurations("{\"b\":2}").build());
    mockGetDefinition(definition);
    mockPatchCall();

    List<FmeDefinitionInstruction> instructions = List.of(
        FmeUpdateIndividualTargetsInstruction.builder()
            .value(ParameterField.createValueField(List.of(
                IndividualTargetUpdate.builder()
                    .treatment("on")
                    .actions(List.of(
                        TargetAction.builder().action(TargetActionType.SetKeys).value(List.of("replaced")).build()))
                    .build())))
            .build());

    executeWith(buildParams(FLAG, ENV, instructions));

    ArgumentCaptor<List<FmePatchOperation>> captor = ArgumentCaptor.forClass(List.class);
    verify(fmePipelineClient)
        .patchFeatureFlagDefinition(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV), eq(FLAG), captor.capture());
    List<FmePatchOperation> ops = captor.getValue();
    assertThat(ops).hasSize(1);
    assertThat(ops.get(0).getOp()).isEqualTo("add");
    assertThat(ops.get(0).getPath()).isEqualTo("/treatments/0/keys");
    assertThat((List<String>) ops.get(0).getValue()).containsExactly("replaced");
  }

  // --- UpdateDynamicConfiguration tests ---

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldSetDynamicConfiguration() throws Exception {
    FeatureFlagDefinition definition =
        buildDefinition(Treatment.builder().name("on").build(), Treatment.builder().name("off").build());
    mockGetDefinition(definition);
    mockPatchCall();

    List<FmeDefinitionInstruction> instructions = List.of(
        FmeUpdateDynamicConfigurationInstruction.builder()
            .value(ParameterField.createValueField(
                List.of(DynamicConfigUpdate.builder().treatment("on").configuration("{\"color\": \"white\"}").build())))
            .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<List<FmePatchOperation>> captor = ArgumentCaptor.forClass(List.class);
    verify(fmePipelineClient)
        .patchFeatureFlagDefinition(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV), eq(FLAG), captor.capture());
    List<FmePatchOperation> ops = captor.getValue();
    assertThat(ops).hasSize(1);
    assertThat(ops.get(0).getOp()).isEqualTo("add");
    assertThat(ops.get(0).getPath()).isEqualTo("/treatments/0/configurations");
    assertThat(ops.get(0).getValue()).isEqualTo("{\"color\": \"white\"}");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldUnsetDynamicConfiguration() throws Exception {
    FeatureFlagDefinition definition =
        buildDefinition(Treatment.builder().name("on").configurations("{\"old\":true}").build(),
            Treatment.builder().name("off").configurations("{\"keep\":true}").build());
    mockGetDefinition(definition);
    mockPatchCall();

    List<FmeDefinitionInstruction> instructions =
        List.of(FmeUpdateDynamicConfigurationInstruction.builder()
                    .value(ParameterField.createValueField(
                        List.of(DynamicConfigUpdate.builder().treatment("on").configuration(null).build())))
                    .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<List<FmePatchOperation>> captor = ArgumentCaptor.forClass(List.class);
    verify(fmePipelineClient)
        .patchFeatureFlagDefinition(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV), eq(FLAG), captor.capture());
    List<FmePatchOperation> ops = captor.getValue();
    assertThat(ops).hasSize(1);
    assertThat(ops.get(0).getOp()).isEqualTo("add");
    assertThat(ops.get(0).getPath()).isEqualTo("/treatments/0/configurations");
    assertThat(ops.get(0).getValue()).isNull();
  }

  // --- Mixed instructions ---

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldHandleMixedInstructions() throws Exception {
    FeatureFlagDefinition definition =
        buildDefinition(Treatment.builder().name("on").keys(new ArrayList<>(List.of("k1"))).build(),
            Treatment.builder().name("off").build());
    mockGetDefinition(definition);
    mockPatchCall();

    List<FmeDefinitionInstruction> instructions = List.of(
        FmeSetDefaultTreatmentInstruction.builder().value(ParameterField.createValueField("on")).build(),
        FmeSetBaselineTreatmentInstruction.builder().value(ParameterField.createValueField("on")).build(),
        FmeSetTrackImpressionInstruction.builder().value(ParameterField.createValueField(true)).build(),
        FmeSetLimitExposureInstruction.builder().value(ParameterField.createValueField(50)).build(),
        FmeUpdateIndividualTargetsInstruction.builder()
            .value(ParameterField.createValueField(
                List.of(IndividualTargetUpdate.builder()
                            .treatment("on")
                            .actions(List.of(
                                TargetAction.builder().action(TargetActionType.AddKeys).value(List.of("k2")).build()))
                            .build())))
            .build(),
        FmeUpdateDynamicConfigurationInstruction.builder()
            .value(ParameterField.createValueField(
                List.of(DynamicConfigUpdate.builder().treatment("on").configuration("{\"color\": \"red\"}").build())))
            .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<List<FmePatchOperation>> captor = ArgumentCaptor.forClass(List.class);
    verify(fmePipelineClient)
        .patchFeatureFlagDefinition(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV), eq(FLAG), captor.capture());

    List<FmePatchOperation> ops = captor.getValue();
    assertThat(ops).hasSize(6);
    assertThat(ops.get(0).getPath()).isEqualTo("/defaultTreatment");
    assertThat(ops.get(1).getPath()).isEqualTo("/baselineTreatment");
    assertThat(ops.get(2).getPath()).isEqualTo("/impressionsDisabled");
    assertThat(ops.get(3).getPath()).isEqualTo("/trafficAllocation");
    assertThat(ops.get(4).getOp()).isEqualTo("add");
    assertThat(ops.get(4).getPath()).isEqualTo("/treatments/0/keys");
    assertThat(ops.get(5).getOp()).isEqualTo("add");
    assertThat(ops.get(5).getPath()).isEqualTo("/treatments/0/configurations");

    verify(fmePipelineClient, times(1))
        .patchFeatureFlagDefinition(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV), eq(FLAG), anyList());
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldNotUseTreatmentOpsForSimpleInstructionsOnly() throws Exception {
    mockPatchCall();
    List<FmeDefinitionInstruction> instructions =
        List.of(FmeSetDefaultTreatmentInstruction.builder().value(ParameterField.createValueField("on")).build(),
            FmeSetLimitExposureInstruction.builder().value(ParameterField.createValueField(75)).build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<List<FmePatchOperation>> captor = ArgumentCaptor.forClass(List.class);
    verify(fmePipelineClient)
        .patchFeatureFlagDefinition(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV), eq(FLAG), captor.capture());
    List<FmePatchOperation> ops = captor.getValue();
    assertThat(ops).hasSize(2);
    assertThat(ops.stream().noneMatch(op -> op.getPath().startsWith("/treatments/"))).isTrue();
  }

  // --- Validation / error tests ---

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldFailWhenFlagNameMissing() {
    StepResponse response = executeWith(buildParams(null, ENV,
        List.of(FmeSetDefaultTreatmentInstruction.builder().value(ParameterField.createValueField("on")).build())));
    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldFailWhenEnvironmentMissing() {
    StepResponse response = executeWith(buildParams(FLAG, null,
        List.of(FmeSetDefaultTreatmentInstruction.builder().value(ParameterField.createValueField("on")).build())));
    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldFailWhenInstructionsMissing() {
    StepResponse response = executeWith(buildParams(FLAG, ENV, null));
    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldFailWhenInstructionsEmpty() {
    StepResponse response = executeWith(buildParams(FLAG, ENV, List.of()));
    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldFailWhenTreatmentNotFound() throws Exception {
    FeatureFlagDefinition definition = buildDefinition(Treatment.builder().name("on").build());
    mockGetDefinition(definition);

    List<FmeDefinitionInstruction> instructions = List.of(
        FmeUpdateIndividualTargetsInstruction.builder()
            .value(ParameterField.createValueField(
                List.of(IndividualTargetUpdate.builder()
                            .treatment("nonexistent")
                            .actions(List.of(
                                TargetAction.builder().action(TargetActionType.AddKeys).value(List.of("k1")).build()))
                            .build())))
            .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldFailForInvalidDynamicConfigJson() throws Exception {
    FeatureFlagDefinition definition = buildDefinition(Treatment.builder().name("on").build());
    mockGetDefinition(definition);

    List<FmeDefinitionInstruction> instructions =
        List.of(FmeUpdateDynamicConfigurationInstruction.builder()
                    .value(ParameterField.createValueField(
                        List.of(DynamicConfigUpdate.builder().treatment("on").configuration("not valid json").build())))
                    .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
  }

  // --- PATCH API error tests ---

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldFailWhenPatchApiReturnsError() throws Exception {
    Call<FmeResponse<FeatureFlagDefinition>> patchCall = mock(Call.class);
    Response<FmeResponse<FeatureFlagDefinition>> patchResponse = mock(Response.class);
    okhttp3.ResponseBody errorBody = mock(okhttp3.ResponseBody.class);
    when(patchCall.execute()).thenReturn(patchResponse);
    when(patchResponse.isSuccessful()).thenReturn(false);
    when(patchResponse.errorBody()).thenReturn(errorBody);
    when(errorBody.string()).thenReturn("upstream error");
    when(fmePipelineClient.patchFeatureFlagDefinition(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV), eq(FLAG), anyList()))
        .thenReturn(patchCall);

    List<FmeDefinitionInstruction> instructions =
        List.of(FmeSetDefaultTreatmentInstruction.builder().value(ParameterField.createValueField("on")).build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
    assertThat(response.getFailureInfo()).isNotNull();
  }

  // --- Conflicting action validation tests ---

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldFailWhenSetKeysAndAddKeysCombined() throws Exception {
    FeatureFlagDefinition definition = buildDefinition(Treatment.builder().name("on").build());
    mockGetDefinition(definition);

    List<FmeDefinitionInstruction> instructions = List.of(
        FmeUpdateIndividualTargetsInstruction.builder()
            .value(ParameterField.createValueField(
                List.of(IndividualTargetUpdate.builder()
                            .treatment("on")
                            .actions(List.of(
                                TargetAction.builder().action(TargetActionType.SetKeys).value(List.of("k1")).build(),
                                TargetAction.builder().action(TargetActionType.AddKeys).value(List.of("k2")).build()))
                            .build())))
            .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldFailWhenSetKeysAndRemoveKeysCombined() throws Exception {
    FeatureFlagDefinition definition = buildDefinition(Treatment.builder().name("on").build());
    mockGetDefinition(definition);

    List<FmeDefinitionInstruction> instructions = List.of(
        FmeUpdateIndividualTargetsInstruction.builder()
            .value(ParameterField.createValueField(List.of(
                IndividualTargetUpdate.builder()
                    .treatment("on")
                    .actions(
                        List.of(TargetAction.builder().action(TargetActionType.SetKeys).value(List.of("k1")).build(),
                            TargetAction.builder().action(TargetActionType.RemoveKeys).value(List.of("k2")).build()))
                    .build())))
            .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldFailWhenSetSegmentsAndAddSegmentsCombined() throws Exception {
    FeatureFlagDefinition definition = buildDefinition(Treatment.builder().name("on").build());
    mockGetDefinition(definition);

    List<FmeDefinitionInstruction> instructions = List.of(
        FmeUpdateIndividualTargetsInstruction.builder()
            .value(ParameterField.createValueField(List.of(
                IndividualTargetUpdate.builder()
                    .treatment("on")
                    .actions(List.of(
                        TargetAction.builder().action(TargetActionType.SetSegments).value(List.of("s1")).build(),
                        TargetAction.builder().action(TargetActionType.AddSegments).value(List.of("s2")).build()))
                    .build())))
            .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldFailWhenSetSegmentsAndRemoveSegmentsCombined() throws Exception {
    FeatureFlagDefinition definition = buildDefinition(Treatment.builder().name("on").build());
    mockGetDefinition(definition);

    List<FmeDefinitionInstruction> instructions = List.of(
        FmeUpdateIndividualTargetsInstruction.builder()
            .value(ParameterField.createValueField(List.of(
                IndividualTargetUpdate.builder()
                    .treatment("on")
                    .actions(List.of(
                        TargetAction.builder().action(TargetActionType.SetSegments).value(List.of("s1")).build(),
                        TargetAction.builder().action(TargetActionType.RemoveSegments).value(List.of("s2")).build()))
                    .build())))
            .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldAllowAddAndRemoveKeysTogether() throws Exception {
    FeatureFlagDefinition definition =
        buildDefinition(Treatment.builder().name("on").keys(new ArrayList<>(List.of("k1", "k2"))).build());
    mockGetDefinition(definition);
    mockPatchCall();

    List<FmeDefinitionInstruction> instructions = List.of(
        FmeUpdateIndividualTargetsInstruction.builder()
            .value(ParameterField.createValueField(List.of(
                IndividualTargetUpdate.builder()
                    .treatment("on")
                    .actions(
                        List.of(TargetAction.builder().action(TargetActionType.AddKeys).value(List.of("k3")).build(),
                            TargetAction.builder().action(TargetActionType.RemoveKeys).value(List.of("k1")).build()))
                    .build())))
            .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);
  }

  // --- Null field edge cases ---

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldAddKeysWhenExistingKeysNull() throws Exception {
    FeatureFlagDefinition definition = buildDefinition(Treatment.builder().name("on").build());
    mockGetDefinition(definition);
    mockPatchCall();

    List<FmeDefinitionInstruction> instructions = List.of(
        FmeUpdateIndividualTargetsInstruction.builder()
            .value(ParameterField.createValueField(List.of(
                IndividualTargetUpdate.builder()
                    .treatment("on")
                    .actions(List.of(
                        TargetAction.builder().action(TargetActionType.AddKeys).value(List.of("k1", "k2")).build()))
                    .build())))
            .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<List<FmePatchOperation>> captor = ArgumentCaptor.forClass(List.class);
    verify(fmePipelineClient)
        .patchFeatureFlagDefinition(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV), eq(FLAG), captor.capture());
    List<FmePatchOperation> ops = captor.getValue();
    assertThat(ops).hasSize(1);
    assertThat(ops.get(0).getPath()).isEqualTo("/treatments/0/keys");
    assertThat((List<String>) ops.get(0).getValue()).containsExactly("k1", "k2");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldRemoveKeysWhenExistingKeysNull() throws Exception {
    FeatureFlagDefinition definition = buildDefinition(Treatment.builder().name("on").build());
    mockGetDefinition(definition);
    mockPatchCall();

    List<FmeDefinitionInstruction> instructions = List.of(
        FmeUpdateIndividualTargetsInstruction.builder()
            .value(ParameterField.createValueField(List.of(
                IndividualTargetUpdate.builder()
                    .treatment("on")
                    .actions(List.of(
                        TargetAction.builder().action(TargetActionType.RemoveKeys).value(List.of("k1")).build()))
                    .build())))
            .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<List<FmePatchOperation>> captor = ArgumentCaptor.forClass(List.class);
    verify(fmePipelineClient)
        .patchFeatureFlagDefinition(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV), eq(FLAG), captor.capture());
    List<FmePatchOperation> ops = captor.getValue();
    assertThat(ops).hasSize(1);
    assertThat(ops.get(0).getPath()).isEqualTo("/treatments/0/keys");
    assertThat((List<String>) ops.get(0).getValue()).isEmpty();
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldAddSegmentsWhenAllSegmentFieldsNull() throws Exception {
    FeatureFlagDefinition definition = buildDefinition(Treatment.builder().name("on").build());
    mockGetDefinition(definition);
    mockPatchCall();

    List<FmeDefinitionInstruction> instructions = List.of(
        FmeUpdateIndividualTargetsInstruction.builder()
            .value(
                ParameterField.createValueField(List.of(IndividualTargetUpdate.builder()
                                                            .treatment("on")
                                                            .actions(List.of(TargetAction.builder()
                                                                                 .action(TargetActionType.AddSegments)
                                                                                 .value(List.of("seg1", "seg2"))
                                                                                 .build()))
                                                            .build())))
            .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<List<FmePatchOperation>> captor = ArgumentCaptor.forClass(List.class);
    verify(fmePipelineClient)
        .patchFeatureFlagDefinition(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV), eq(FLAG), captor.capture());
    FmePatchOperation segOp =
        captor.getValue().stream().filter(op -> "/treatments/0/segments".equals(op.getPath())).findFirst().get();
    assertThat((List<String>) segOp.getValue()).containsExactly("seg1", "seg2");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldRemoveSegmentsWhenNoSegmentsExist() throws Exception {
    FeatureFlagDefinition definition = buildDefinition(Treatment.builder().name("on").build());
    mockGetDefinition(definition);
    mockPatchCall();

    List<FmeDefinitionInstruction> instructions = List.of(
        FmeUpdateIndividualTargetsInstruction.builder()
            .value(ParameterField.createValueField(List.of(
                IndividualTargetUpdate.builder()
                    .treatment("on")
                    .actions(List.of(
                        TargetAction.builder().action(TargetActionType.RemoveSegments).value(List.of("seg1")).build()))
                    .build())))
            .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<List<FmePatchOperation>> captor = ArgumentCaptor.forClass(List.class);
    verify(fmePipelineClient)
        .patchFeatureFlagDefinition(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV), eq(FLAG), captor.capture());
    assertThat(captor.getValue().stream().noneMatch(op -> op.getPath().contains("/segments"))).isTrue();
  }

  // --- No-op edge cases ---

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldAddKeysAllDuplicatesStillEmitsPatch() throws Exception {
    FeatureFlagDefinition definition =
        buildDefinition(Treatment.builder().name("on").keys(new ArrayList<>(List.of("k1", "k2"))).build());
    mockGetDefinition(definition);
    mockPatchCall();

    List<FmeDefinitionInstruction> instructions = List.of(
        FmeUpdateIndividualTargetsInstruction.builder()
            .value(ParameterField.createValueField(List.of(
                IndividualTargetUpdate.builder()
                    .treatment("on")
                    .actions(List.of(
                        TargetAction.builder().action(TargetActionType.AddKeys).value(List.of("k1", "k2")).build()))
                    .build())))
            .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<List<FmePatchOperation>> captor = ArgumentCaptor.forClass(List.class);
    verify(fmePipelineClient)
        .patchFeatureFlagDefinition(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV), eq(FLAG), captor.capture());
    assertThat((List<String>) captor.getValue().get(0).getValue()).containsExactly("k1", "k2");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldRemoveKeysNotPresentNoError() throws Exception {
    FeatureFlagDefinition definition =
        buildDefinition(Treatment.builder().name("on").keys(new ArrayList<>(List.of("k1", "k2"))).build());
    mockGetDefinition(definition);
    mockPatchCall();

    List<FmeDefinitionInstruction> instructions = List.of(
        FmeUpdateIndividualTargetsInstruction.builder()
            .value(ParameterField.createValueField(List.of(IndividualTargetUpdate.builder()
                                                               .treatment("on")
                                                               .actions(List.of(TargetAction.builder()
                                                                                    .action(TargetActionType.RemoveKeys)
                                                                                    .value(List.of("nonexistent"))
                                                                                    .build()))
                                                               .build())))
            .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<List<FmePatchOperation>> captor = ArgumentCaptor.forClass(List.class);
    verify(fmePipelineClient)
        .patchFeatureFlagDefinition(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV), eq(FLAG), captor.capture());
    assertThat((List<String>) captor.getValue().get(0).getValue()).containsExactly("k1", "k2");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldAddSegmentsAllDuplicatesAcrossFields() throws Exception {
    FeatureFlagDefinition definition = buildDefinition(Treatment.builder()
                                                           .name("on")
                                                           .segments(new ArrayList<>(List.of("seg1")))
                                                           .largeSegments(new ArrayList<>(List.of("large1")))
                                                           .build());
    mockGetDefinition(definition);
    mockPatchCall();

    List<FmeDefinitionInstruction> instructions = List.of(
        FmeUpdateIndividualTargetsInstruction.builder()
            .value(
                ParameterField.createValueField(List.of(IndividualTargetUpdate.builder()
                                                            .treatment("on")
                                                            .actions(List.of(TargetAction.builder()
                                                                                 .action(TargetActionType.AddSegments)
                                                                                 .value(List.of("seg1", "large1"))
                                                                                 .build()))
                                                            .build())))
            .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<List<FmePatchOperation>> captor = ArgumentCaptor.forClass(List.class);
    verify(fmePipelineClient)
        .patchFeatureFlagDefinition(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV), eq(FLAG), captor.capture());
    FmePatchOperation segOp =
        captor.getValue().stream().filter(op -> "/treatments/0/segments".equals(op.getPath())).findFirst().get();
    assertThat((List<String>) segOp.getValue()).containsExactly("seg1");
  }

  // --- Empty value edge cases ---

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldSetKeysEmptyListClearsAll() throws Exception {
    FeatureFlagDefinition definition =
        buildDefinition(Treatment.builder().name("on").keys(new ArrayList<>(List.of("k1", "k2"))).build());
    mockGetDefinition(definition);
    mockPatchCall();

    List<FmeDefinitionInstruction> instructions = List.of(
        FmeUpdateIndividualTargetsInstruction.builder()
            .value(ParameterField.createValueField(List.of(
                IndividualTargetUpdate.builder()
                    .treatment("on")
                    .actions(List.of(TargetAction.builder().action(TargetActionType.SetKeys).value(List.of()).build()))
                    .build())))
            .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<List<FmePatchOperation>> captor = ArgumentCaptor.forClass(List.class);
    verify(fmePipelineClient)
        .patchFeatureFlagDefinition(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV), eq(FLAG), captor.capture());
    assertThat((List<String>) captor.getValue().get(0).getValue()).isEmpty();
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldSetSegmentsEmptyListClearsAll() throws Exception {
    FeatureFlagDefinition definition = buildDefinition(Treatment.builder()
                                                           .name("on")
                                                           .segments(new ArrayList<>(List.of("seg1")))
                                                           .largeSegments(new ArrayList<>(List.of("large1")))
                                                           .ruleBasedSegments(new ArrayList<>(List.of("rule1")))
                                                           .build());
    mockGetDefinition(definition);
    mockPatchCall();

    List<FmeDefinitionInstruction> instructions = List.of(
        FmeUpdateIndividualTargetsInstruction.builder()
            .value(ParameterField.createValueField(
                List.of(IndividualTargetUpdate.builder()
                            .treatment("on")
                            .actions(List.of(
                                TargetAction.builder().action(TargetActionType.SetSegments).value(List.of()).build()))
                            .build())))
            .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<List<FmePatchOperation>> captor = ArgumentCaptor.forClass(List.class);
    verify(fmePipelineClient)
        .patchFeatureFlagDefinition(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV), eq(FLAG), captor.capture());
    List<FmePatchOperation> ops = captor.getValue();
    assertThat((List<String>) ops.stream()
                   .filter(op -> "/treatments/0/segments".equals(op.getPath()))
                   .findFirst()
                   .get()
                   .getValue())
        .isEmpty();
    assertThat((List<String>) ops.stream()
                   .filter(op -> "/treatments/0/largeSegments".equals(op.getPath()))
                   .findFirst()
                   .get()
                   .getValue())
        .isEmpty();
    assertThat((List<String>) ops.stream()
                   .filter(op -> "/treatments/0/ruleBasedSegments".equals(op.getPath()))
                   .findFirst()
                   .get()
                   .getValue())
        .isEmpty();
  }

  // --- Multi-treatment edge cases ---

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldUpdateBothTreatmentsInOneInstruction() throws Exception {
    FeatureFlagDefinition definition =
        buildDefinition(Treatment.builder().name("on").keys(new ArrayList<>(List.of("k1"))).build(),
            Treatment.builder().name("off").keys(new ArrayList<>(List.of("k2"))).build());
    mockGetDefinition(definition);
    mockPatchCall();

    List<FmeDefinitionInstruction> instructions = List.of(
        FmeUpdateIndividualTargetsInstruction.builder()
            .value(ParameterField.createValueField(List.of(
                IndividualTargetUpdate.builder()
                    .treatment("on")
                    .actions(
                        List.of(TargetAction.builder().action(TargetActionType.AddKeys).value(List.of("k3")).build()))
                    .build(),
                IndividualTargetUpdate.builder()
                    .treatment("off")
                    .actions(
                        List.of(TargetAction.builder().action(TargetActionType.AddKeys).value(List.of("k4")).build()))
                    .build())))
            .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<List<FmePatchOperation>> captor = ArgumentCaptor.forClass(List.class);
    verify(fmePipelineClient)
        .patchFeatureFlagDefinition(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV), eq(FLAG), captor.capture());
    List<FmePatchOperation> ops = captor.getValue();

    FmePatchOperation onOp = ops.stream().filter(op -> "/treatments/0/keys".equals(op.getPath())).findFirst().get();
    assertThat((List<String>) onOp.getValue()).containsExactly("k1", "k3");

    FmePatchOperation offOp = ops.stream().filter(op -> "/treatments/1/keys".equals(op.getPath())).findFirst().get();
    assertThat((List<String>) offOp.getValue()).containsExactly("k2", "k4");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldHandleThreeTreatmentsWithCorrectIndexes() throws Exception {
    FeatureFlagDefinition definition =
        buildDefinition(Treatment.builder().name("on").build(), Treatment.builder().name("off").build(),
            Treatment.builder().name("custom").keys(new ArrayList<>(List.of("c1"))).build());
    mockGetDefinition(definition);
    mockPatchCall();

    List<FmeDefinitionInstruction> instructions = List.of(
        FmeUpdateIndividualTargetsInstruction.builder()
            .value(ParameterField.createValueField(
                List.of(IndividualTargetUpdate.builder()
                            .treatment("custom")
                            .actions(List.of(
                                TargetAction.builder().action(TargetActionType.AddKeys).value(List.of("c2")).build()))
                            .build())))
            .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<List<FmePatchOperation>> captor = ArgumentCaptor.forClass(List.class);
    verify(fmePipelineClient)
        .patchFeatureFlagDefinition(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV), eq(FLAG), captor.capture());
    FmePatchOperation op = captor.getValue().get(0);
    assertThat(op.getPath()).isEqualTo("/treatments/2/keys");
    assertThat((List<String>) op.getValue()).containsExactly("c1", "c2");
  }

  // --- Segment remove from specific field ---

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldRemoveLargeSegmentOnly() throws Exception {
    FeatureFlagDefinition definition = buildDefinition(Treatment.builder()
                                                           .name("on")
                                                           .segments(new ArrayList<>(List.of("seg1")))
                                                           .largeSegments(new ArrayList<>(List.of("large1", "large2")))
                                                           .build());
    mockGetDefinition(definition);
    mockPatchCall();

    List<FmeDefinitionInstruction> instructions =
        List.of(FmeUpdateIndividualTargetsInstruction.builder()
                    .value(ParameterField.createValueField(
                        List.of(IndividualTargetUpdate.builder()
                                    .treatment("on")
                                    .actions(List.of(TargetAction.builder()
                                                         .action(TargetActionType.RemoveSegments)
                                                         .value(List.of("large1"))
                                                         .build()))
                                    .build())))
                    .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<List<FmePatchOperation>> captor = ArgumentCaptor.forClass(List.class);
    verify(fmePipelineClient)
        .patchFeatureFlagDefinition(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV), eq(FLAG), captor.capture());
    List<FmePatchOperation> ops = captor.getValue();

    FmePatchOperation largeOp =
        ops.stream().filter(op -> "/treatments/0/largeSegments".equals(op.getPath())).findFirst().get();
    assertThat((List<String>) largeOp.getValue()).containsExactly("large2");
    assertThat(ops.stream().noneMatch(op -> "/treatments/0/segments".equals(op.getPath()))).isTrue();
  }

  // --- Conflict valid: AddSegments + RemoveSegments together ---

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldAllowAddAndRemoveSegmentsTogether() throws Exception {
    FeatureFlagDefinition definition =
        buildDefinition(Treatment.builder().name("on").segments(new ArrayList<>(List.of("seg1", "seg2"))).build());
    mockGetDefinition(definition);
    mockPatchCall();

    List<FmeDefinitionInstruction> instructions = List.of(
        FmeUpdateIndividualTargetsInstruction.builder()
            .value(ParameterField.createValueField(List.of(
                IndividualTargetUpdate.builder()
                    .treatment("on")
                    .actions(List.of(
                        TargetAction.builder().action(TargetActionType.AddSegments).value(List.of("seg3")).build(),
                        TargetAction.builder().action(TargetActionType.RemoveSegments).value(List.of("seg1")).build()))
                    .build())))
            .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);
  }

  // --- Config validation edge cases ---

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldFailForJsonArrayConfig() throws Exception {
    FeatureFlagDefinition definition = buildDefinition(Treatment.builder().name("on").build());
    mockGetDefinition(definition);

    List<FmeDefinitionInstruction> instructions =
        List.of(FmeUpdateDynamicConfigurationInstruction.builder()
                    .value(ParameterField.createValueField(
                        List.of(DynamicConfigUpdate.builder().treatment("on").configuration("[1,2,3]").build())))
                    .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldFailForEmptyStringConfig() throws Exception {
    FeatureFlagDefinition definition = buildDefinition(Treatment.builder().name("on").build());
    mockGetDefinition(definition);

    List<FmeDefinitionInstruction> instructions =
        List.of(FmeUpdateDynamicConfigurationInstruction.builder()
                    .value(ParameterField.createValueField(
                        List.of(DynamicConfigUpdate.builder().treatment("on").configuration("").build())))
                    .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
  }

  // --- Validation edge cases ---

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldFailWhenTreatmentNameEmpty() throws Exception {
    FeatureFlagDefinition definition = buildDefinition(Treatment.builder().name("on").build());
    mockGetDefinition(definition);

    List<FmeDefinitionInstruction> instructions = List.of(
        FmeUpdateIndividualTargetsInstruction.builder()
            .value(ParameterField.createValueField(
                List.of(IndividualTargetUpdate.builder()
                            .treatment("")
                            .actions(List.of(
                                TargetAction.builder().action(TargetActionType.AddKeys).value(List.of("k1")).build()))
                            .build())))
            .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldFailWhenDynamicConfigTreatmentNameEmpty() throws Exception {
    FeatureFlagDefinition definition = buildDefinition(Treatment.builder().name("on").build());
    mockGetDefinition(definition);

    List<FmeDefinitionInstruction> instructions =
        List.of(FmeUpdateDynamicConfigurationInstruction.builder()
                    .value(ParameterField.createValueField(
                        List.of(DynamicConfigUpdate.builder().treatment("").configuration("{}").build())))
                    .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldFailWhenActionsListEmpty() throws Exception {
    FeatureFlagDefinition definition = buildDefinition(Treatment.builder().name("on").build());
    mockGetDefinition(definition);

    List<FmeDefinitionInstruction> instructions =
        List.of(FmeUpdateIndividualTargetsInstruction.builder()
                    .value(ParameterField.createValueField(
                        List.of(IndividualTargetUpdate.builder().treatment("on").actions(List.of()).build())))
                    .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
  }

  // --- GET API failure ---

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldFailWhenGetDefinitionReturnsError() throws Exception {
    Call<FmeResponse<FeatureFlagDefinition>> getCall = mock(Call.class);
    Response<FmeResponse<FeatureFlagDefinition>> getResponse = mock(Response.class);
    okhttp3.ResponseBody errorBody = mock(okhttp3.ResponseBody.class);
    when(getCall.execute()).thenReturn(getResponse);
    when(getResponse.isSuccessful()).thenReturn(false);
    when(getResponse.code()).thenReturn(500);
    when(getResponse.errorBody()).thenReturn(errorBody);
    when(errorBody.string()).thenReturn("internal error");
    when(fmePipelineClient.getFeatureFlagDefinitionInEnvironment(
             eq(FLAG), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV)))
        .thenReturn(getCall);

    List<FmeDefinitionInstruction> instructions = List.of(
        FmeUpdateIndividualTargetsInstruction.builder()
            .value(ParameterField.createValueField(
                List.of(IndividualTargetUpdate.builder()
                            .treatment("on")
                            .actions(List.of(
                                TargetAction.builder().action(TargetActionType.AddKeys).value(List.of("k1")).build()))
                            .build())))
            .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
  }

  // --- Sequential mutations on same treatment ---

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldApplySequentialMutationsOnSameTreatment() throws Exception {
    FeatureFlagDefinition definition =
        buildDefinition(Treatment.builder().name("on").keys(new ArrayList<>(List.of("k1"))).build());
    mockGetDefinition(definition);
    mockPatchCall();

    List<FmeDefinitionInstruction> instructions = List.of(
        FmeUpdateIndividualTargetsInstruction.builder()
            .value(ParameterField.createValueField(
                List.of(IndividualTargetUpdate.builder()
                            .treatment("on")
                            .actions(List.of(
                                TargetAction.builder().action(TargetActionType.AddKeys).value(List.of("k2")).build()))
                            .build())))
            .build(),
        FmeUpdateIndividualTargetsInstruction.builder()
            .value(ParameterField.createValueField(
                List.of(IndividualTargetUpdate.builder()
                            .treatment("on")
                            .actions(List.of(
                                TargetAction.builder().action(TargetActionType.AddKeys).value(List.of("k3")).build()))
                            .build())))
            .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<List<FmePatchOperation>> captor = ArgumentCaptor.forClass(List.class);
    verify(fmePipelineClient)
        .patchFeatureFlagDefinition(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV), eq(FLAG), captor.capture());
    FmePatchOperation op =
        captor.getValue().stream().filter(o -> "/treatments/0/keys".equals(o.getPath())).findFirst().get();
    assertThat((List<String>) op.getValue()).containsExactly("k1", "k2", "k3");
  }

  // --- Dynamic config multi-treatment ---

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldSetAndUnsetConfigForDifferentTreatments() throws Exception {
    FeatureFlagDefinition definition =
        buildDefinition(Treatment.builder().name("on").build(), Treatment.builder().name("off").build());
    mockGetDefinition(definition);
    mockPatchCall();

    List<FmeDefinitionInstruction> instructions =
        List.of(FmeUpdateDynamicConfigurationInstruction.builder()
                    .value(ParameterField.createValueField(
                        List.of(DynamicConfigUpdate.builder().treatment("on").configuration("{\"a\":1}").build(),
                            DynamicConfigUpdate.builder().treatment("off").configuration(null).build())))
                    .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<List<FmePatchOperation>> captor = ArgumentCaptor.forClass(List.class);
    verify(fmePipelineClient)
        .patchFeatureFlagDefinition(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV), eq(FLAG), captor.capture());
    List<FmePatchOperation> ops = captor.getValue();

    FmePatchOperation onOp =
        ops.stream().filter(op -> "/treatments/0/configurations".equals(op.getPath())).findFirst().get();
    assertThat(onOp.getValue()).isEqualTo("{\"a\":1}");

    FmePatchOperation offOp =
        ops.stream().filter(op -> "/treatments/1/configurations".equals(op.getPath())).findFirst().get();
    assertThat(offOp.getValue()).isNull();
  }

  // --- SetTargetingRules tests ---

  private void mockUpdateRulesCall() throws Exception {
    Call<FmeResponse<FeatureFlagDefinition>> updateCall = mock(Call.class);
    Response<FmeResponse<FeatureFlagDefinition>> updateResponse = mock(Response.class);
    when(updateCall.execute()).thenReturn(updateResponse);
    when(updateResponse.isSuccessful()).thenReturn(true);
    when(updateResponse.body())
        .thenReturn(FmeResponse.<FeatureFlagDefinition>builder().entity(mock(FeatureFlagDefinition.class)).build());
    when(fmePipelineClient.updateFeatureFlagDefinitionRules(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(FLAG), eq(ENV), any()))
        .thenReturn(updateCall);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldApplySetTargetingRules() throws Exception {
    mockUpdateRulesCall();

    Rule rule = Rule.builder()
                    .type(ParameterField.createValueField(RuleConditionType.EQUAL_SET))
                    .negate(ParameterField.createValueField(false))
                    .attribute(ParameterField.createValueField("country"))
                    .value(ParameterField.createValueField(Arrays.asList("US", "UK")))
                    .build();

    RuleAllocation alloc = RuleAllocation.builder()
                               .treatment(ParameterField.createValueField("on"))
                               .size(ParameterField.createValueField(80))
                               .build();

    TargetRules targetRules = TargetRules.builder()
                                  .condition(ParameterField.createValueField(
                                      RuleCondition.builder()
                                          .rules(ParameterField.createValueField(Collections.singletonList(rule)))
                                          .build()))
                                  .allocation(ParameterField.createValueField(Collections.singletonList(alloc)))
                                  .build();

    List<FmeDefinitionInstruction> instructions =
        List.of(FmeSetTargetingRulesInstruction.builder()
                    .value(ParameterField.createValueField(Collections.singletonList(targetRules)))
                    .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<List<TargetingRulesDTO>> captor = ArgumentCaptor.forClass(List.class);
    verify(fmePipelineClient)
        .updateFeatureFlagDefinitionRules(
            eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(FLAG), eq(ENV), captor.capture());

    List<TargetingRulesDTO> capturedDtos = captor.getValue();
    assertThat(capturedDtos).hasSize(1);
    assertThat(capturedDtos.get(0).getCondition().getRules()).hasSize(1);
    assertThat(capturedDtos.get(0).getCondition().getRules().get(0).getType()).isEqualTo("EQUAL_SET");
    assertThat(capturedDtos.get(0).getAllocation()).hasSize(1);
    assertThat(capturedDtos.get(0).getAllocation().get(0).getTreatment()).isEqualTo("on");
    assertThat(capturedDtos.get(0).getAllocation().get(0).getSize()).isEqualTo(80);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldApplySetTargetingRulesWithPatchInstructions() throws Exception {
    mockPatchCall();
    mockUpdateRulesCall();

    RuleAllocation alloc = RuleAllocation.builder()
                               .treatment(ParameterField.createValueField("on"))
                               .size(ParameterField.createValueField(100))
                               .build();
    TargetRules targetRules = TargetRules.builder()
                                  .condition(ParameterField.ofNull())
                                  .allocation(ParameterField.createValueField(Collections.singletonList(alloc)))
                                  .build();

    List<FmeDefinitionInstruction> instructions =
        List.of(FmeSetDefaultTreatmentInstruction.builder().value(ParameterField.createValueField("on")).build(),
            FmeSetTargetingRulesInstruction.builder()
                .value(ParameterField.createValueField(Collections.singletonList(targetRules)))
                .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    verify(fmePipelineClient)
        .patchFeatureFlagDefinition(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV), eq(FLAG), anyList());
    verify(fmePipelineClient)
        .updateFeatureFlagDefinitionRules(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(FLAG), eq(ENV), any());
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldApplySetTargetingRulesOnlyWithoutPatch() throws Exception {
    mockUpdateRulesCall();

    RuleAllocation alloc = RuleAllocation.builder()
                               .treatment(ParameterField.createValueField("on"))
                               .size(ParameterField.createValueField(100))
                               .build();
    TargetRules targetRules = TargetRules.builder()
                                  .condition(ParameterField.ofNull())
                                  .allocation(ParameterField.createValueField(Collections.singletonList(alloc)))
                                  .build();

    List<FmeDefinitionInstruction> instructions =
        List.of(FmeSetTargetingRulesInstruction.builder()
                    .value(ParameterField.createValueField(Collections.singletonList(targetRules)))
                    .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    verify(fmePipelineClient, never())
        .patchFeatureFlagDefinition(anyString(), anyString(), anyString(), anyString(), anyString(), anyList());
    verify(fmePipelineClient)
        .updateFeatureFlagDefinitionRules(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(FLAG), eq(ENV), any());
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldFailWhenDuplicateSetTargetingRules() throws Exception {
    RuleAllocation alloc = RuleAllocation.builder()
                               .treatment(ParameterField.createValueField("on"))
                               .size(ParameterField.createValueField(100))
                               .build();
    TargetRules targetRules = TargetRules.builder()
                                  .condition(ParameterField.ofNull())
                                  .allocation(ParameterField.createValueField(Collections.singletonList(alloc)))
                                  .build();

    List<FmeDefinitionInstruction> instructions =
        List.of(FmeSetTargetingRulesInstruction.builder()
                    .value(ParameterField.createValueField(Collections.singletonList(targetRules)))
                    .build(),
            FmeSetTargetingRulesInstruction.builder()
                .value(ParameterField.createValueField(Collections.singletonList(targetRules)))
                .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
    assertThat(response.getFailureInfo()).isNotNull();
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldApplySetTargetingRulesWithMultipleRules() throws Exception {
    mockUpdateRulesCall();

    Rule rule1 = Rule.builder()
                     .type(ParameterField.createValueField(RuleConditionType.BOOLEAN))
                     .attribute(ParameterField.createValueField("premium"))
                     .value(ParameterField.createValueField(true))
                     .build();
    Rule rule2 = Rule.builder()
                     .type(ParameterField.createValueField(RuleConditionType.GREATER_THAN_OR_EQUAL_NUMBER))
                     .attribute(ParameterField.createValueField("age"))
                     .value(ParameterField.createValueField(18))
                     .build();

    RuleAllocation alloc1 = RuleAllocation.builder()
                                .treatment(ParameterField.createValueField("on"))
                                .size(ParameterField.createValueField(70))
                                .build();
    RuleAllocation alloc2 = RuleAllocation.builder()
                                .treatment(ParameterField.createValueField("off"))
                                .size(ParameterField.createValueField(30))
                                .build();

    TargetRules targetRule1 =
        TargetRules.builder()
            .condition(ParameterField.createValueField(
                RuleCondition.builder().rules(ParameterField.createValueField(Arrays.asList(rule1, rule2))).build()))
            .allocation(ParameterField.createValueField(Arrays.asList(alloc1, alloc2)))
            .build();

    TargetRules targetRule2 = TargetRules.builder()
                                  .condition(ParameterField.ofNull())
                                  .allocation(ParameterField.createValueField(Collections.singletonList(alloc1)))
                                  .build();

    List<FmeDefinitionInstruction> instructions =
        List.of(FmeSetTargetingRulesInstruction.builder()
                    .value(ParameterField.createValueField(Arrays.asList(targetRule1, targetRule2)))
                    .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<List<TargetingRulesDTO>> captor = ArgumentCaptor.forClass(List.class);
    verify(fmePipelineClient)
        .updateFeatureFlagDefinitionRules(
            eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(FLAG), eq(ENV), captor.capture());

    List<TargetingRulesDTO> capturedDtos = captor.getValue();
    assertThat(capturedDtos).hasSize(2);
    assertThat(capturedDtos.get(0).getCondition().getRules()).hasSize(2);
    assertThat(capturedDtos.get(0).getCondition().getRules().get(0).getValue()).isEqualTo(true);
    assertThat(capturedDtos.get(0).getCondition().getRules().get(1).getValue()).isEqualTo(18L);
    assertThat(capturedDtos.get(1).getCondition()).isNull();
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldApplySetTargetingRulesWithBooleanConversion() throws Exception {
    mockUpdateRulesCall();

    Rule rule = Rule.builder()
                    .type(ParameterField.createValueField(RuleConditionType.BOOLEAN))
                    .attribute(ParameterField.createValueField("premium"))
                    .value(ParameterField.createValueField("true"))
                    .build();

    RuleAllocation alloc = RuleAllocation.builder()
                               .treatment(ParameterField.createValueField("on"))
                               .size(ParameterField.createValueField(100))
                               .build();

    TargetRules targetRules = TargetRules.builder()
                                  .condition(ParameterField.createValueField(
                                      RuleCondition.builder()
                                          .rules(ParameterField.createValueField(Collections.singletonList(rule)))
                                          .build()))
                                  .allocation(ParameterField.createValueField(Collections.singletonList(alloc)))
                                  .build();

    List<FmeDefinitionInstruction> instructions =
        List.of(FmeSetTargetingRulesInstruction.builder()
                    .value(ParameterField.createValueField(Collections.singletonList(targetRules)))
                    .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<List<TargetingRulesDTO>> captor = ArgumentCaptor.forClass(List.class);
    verify(fmePipelineClient)
        .updateFeatureFlagDefinitionRules(
            eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(FLAG), eq(ENV), captor.capture());

    assertThat(captor.getValue().get(0).getCondition().getRules().get(0).getValue()).isEqualTo(true);
  }

  // --- SetDefaultAllocations tests ---

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldSetDefaultAllocations() throws Exception {
    mockPatchCall();

    List<FmeDefinitionInstruction> instructions =
        List.of(FmeSetDefaultAllocationsInstruction.builder()
                    .value(ParameterField.createValueField(List.of(Allocation.builder()
                                                                       .treatment(ParameterField.createValueField("on"))
                                                                       .amount(ParameterField.createValueField(80))
                                                                       .build(),
                        Allocation.builder()
                            .treatment(ParameterField.createValueField("off"))
                            .amount(ParameterField.createValueField(20))
                            .build())))
                    .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<List<FmePatchOperation>> captor = ArgumentCaptor.forClass(List.class);
    verify(fmePipelineClient)
        .patchFeatureFlagDefinition(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV), eq(FLAG), captor.capture());
    List<FmePatchOperation> ops = captor.getValue();
    assertThat(ops).hasSize(1);
    assertThat(ops.get(0).getOp()).isEqualTo("replace");
    assertThat(ops.get(0).getPath()).isEqualTo("/defaultRule");
    List<Bucket> buckets = (List<Bucket>) ops.get(0).getValue();
    assertThat(buckets).hasSize(2);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldFailWhenAllocationsDoNotSumTo100() {
    List<FmeDefinitionInstruction> instructions =
        List.of(FmeSetDefaultAllocationsInstruction.builder()
                    .value(ParameterField.createValueField(List.of(Allocation.builder()
                                                                       .treatment(ParameterField.createValueField("on"))
                                                                       .amount(ParameterField.createValueField(50))
                                                                       .build(),
                        Allocation.builder()
                            .treatment(ParameterField.createValueField("off"))
                            .amount(ParameterField.createValueField(30))
                            .build())))
                    .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldPassNonExistentTreatmentToDownstream() throws Exception {
    mockPatchCall();

    List<FmeDefinitionInstruction> instructions = List.of(
        FmeSetDefaultAllocationsInstruction.builder()
            .value(
                ParameterField.createValueField(List.of(Allocation.builder()
                                                            .treatment(ParameterField.createValueField("nonexistent"))
                                                            .amount(ParameterField.createValueField(100))
                                                            .build())))
            .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<List<FmePatchOperation>> captor = ArgumentCaptor.forClass(List.class);
    verify(fmePipelineClient)
        .patchFeatureFlagDefinition(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV), eq(FLAG), captor.capture());
    List<Bucket> buckets = (List<Bucket>) captor.getValue().get(0).getValue();
    assertThat(buckets).hasSize(1);
    assertThat(buckets.get(0).getTreatment()).isEqualTo("nonexistent");
    assertThat(buckets.get(0).getSize()).isEqualTo(100);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldFailWhenAllocationsEmpty() {
    List<FmeDefinitionInstruction> instructions = List.of(
        FmeSetDefaultAllocationsInstruction.builder().value(ParameterField.createValueField(List.of())).build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
  }

  // --- SetTreatments tests ---

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldSetTreatmentsOverride() throws Exception {
    mockPatchCall();

    List<FmeDefinitionInstruction> instructions = List.of(
        FmeSetTreatmentsInstruction.builder()
            .value(
                ParameterField.createValueField(List.of(TreatmentConfiguration.builder()
                                                            .treatment(ParameterField.createValueField("on"))
                                                            .description(ParameterField.createValueField("Updated on"))
                                                            .build(),
                    TreatmentConfiguration.builder()
                        .treatment(ParameterField.createValueField("beta"))
                        .description(ParameterField.createValueField("New beta"))
                        .build())))
            .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<List<FmePatchOperation>> captor = ArgumentCaptor.forClass(List.class);
    verify(fmePipelineClient)
        .patchFeatureFlagDefinition(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV), eq(FLAG), captor.capture());
    List<FmePatchOperation> ops = captor.getValue();
    assertThat(ops).hasSize(1);
    assertThat(ops.get(0).getOp()).isEqualTo("replace");
    assertThat(ops.get(0).getPath()).isEqualTo("/treatments");
    List<Treatment> treatments = (List<Treatment>) ops.get(0).getValue();
    assertThat(treatments).hasSize(2);
    assertThat(treatments.get(0).getName()).isEqualTo("on");
    assertThat(treatments.get(0).getDescription()).isEqualTo("Updated on");
    assertThat(treatments.get(1).getName()).isEqualTo("beta");
    assertThat(treatments.get(1).getDescription()).isEqualTo("New beta");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldFailWhenSetTreatmentsEmpty() {
    List<FmeDefinitionInstruction> instructions =
        List.of(FmeSetTreatmentsInstruction.builder().value(ParameterField.createValueField(List.of())).build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldFailWhenSetTreatmentsNameEmpty() {
    List<FmeDefinitionInstruction> instructions = List.of(
        FmeSetTreatmentsInstruction.builder()
            .value(ParameterField.createValueField(List.of(TreatmentConfiguration.builder()
                                                               .treatment(ParameterField.createValueField(""))
                                                               .description(ParameterField.createValueField("desc"))
                                                               .build())))
            .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
  }

  // --- SetRolloutStatus tests ---

  private void mockUpdateFeatureFlagCall() throws Exception {
    Call<FmeResponse<FeatureFlag>> updateCall = mock(Call.class);
    Response<FmeResponse<FeatureFlag>> updateResponse = mock(Response.class);
    when(updateCall.execute()).thenReturn(updateResponse);
    when(updateResponse.isSuccessful()).thenReturn(true);
    when(updateResponse.body()).thenReturn(FmeResponse.<FeatureFlag>builder().entity(mock(FeatureFlag.class)).build());
    when(fmePipelineClient.updateFeatureFlag(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(FLAG), anyList()))
        .thenReturn(updateCall);
  }

  private void mockGetFeatureFlagCall() throws Exception {
    FeatureFlag featureFlag = FeatureFlag.builder().name(FLAG).build();
    Call<FmeResponse<FeatureFlag>> getCall = mock(Call.class);
    Response<FmeResponse<FeatureFlag>> getResponse = mock(Response.class);
    when(getCall.execute()).thenReturn(getResponse);
    when(getResponse.isSuccessful()).thenReturn(true);
    when(getResponse.body()).thenReturn(FmeResponse.<FeatureFlag>builder().entity(featureFlag).build());
    when(fmePipelineClient.getFeatureFlag(eq(FLAG), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID))).thenReturn(getCall);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldApplySetRolloutStatus() throws Exception {
    mockUpdateFeatureFlagCall();
    mockGetFeatureFlagCall();

    List<FmeDefinitionInstruction> instructions =
        List.of(FmeSetRolloutStatusInstruction.builder().value(ParameterField.createValueField("Released")).build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<List<FmePatchOperation>> captor = ArgumentCaptor.forClass(List.class);
    verify(fmePipelineClient).updateFeatureFlag(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(FLAG), captor.capture());
    List<FmePatchOperation> ops = captor.getValue();
    assertThat(ops).hasSize(1);
    assertThat(ops.get(0).getOp()).isEqualTo("replace");
    assertThat(ops.get(0).getPath()).isEqualTo("/rolloutStatus");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldFailWhenDuplicateSetRolloutStatus() {
    List<FmeDefinitionInstruction> instructions =
        List.of(FmeSetRolloutStatusInstruction.builder().value(ParameterField.createValueField("Released")).build(),
            FmeSetRolloutStatusInstruction.builder().value(ParameterField.createValueField("Archived")).build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
  }

  // --- SetFlagKilled tests ---

  private void mockKillFlagCall() throws Exception {
    Call<FmeResponse<FeatureFlagDefinition>> killCall = mock(Call.class);
    Response<FmeResponse<FeatureFlagDefinition>> killResponse = mock(Response.class);
    when(killCall.execute()).thenReturn(killResponse);
    when(killResponse.isSuccessful()).thenReturn(true);
    when(killResponse.body())
        .thenReturn(FmeResponse.<FeatureFlagDefinition>builder().entity(mock(FeatureFlagDefinition.class)).build());
    when(fmePipelineClient.killFeatureFlag(eq(FLAG), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV)))
        .thenReturn(killCall);
  }

  private void mockRestoreFlagCall() throws Exception {
    Call<FmeResponse<FeatureFlagDefinition>> restoreCall = mock(Call.class);
    Response<FmeResponse<FeatureFlagDefinition>> restoreResponse = mock(Response.class);
    when(restoreCall.execute()).thenReturn(restoreResponse);
    when(restoreResponse.isSuccessful()).thenReturn(true);
    when(restoreResponse.body())
        .thenReturn(FmeResponse.<FeatureFlagDefinition>builder().entity(mock(FeatureFlagDefinition.class)).build());
    when(fmePipelineClient.restoreFeatureFlag(eq(FLAG), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV)))
        .thenReturn(restoreCall);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldKillFlag() throws Exception {
    mockKillFlagCall();

    List<FmeDefinitionInstruction> instructions =
        List.of(FmeSetFlagKilledInstruction.builder().value(ParameterField.createValueField(true)).build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    verify(fmePipelineClient).killFeatureFlag(eq(FLAG), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV));
    verify(fmePipelineClient, never())
        .restoreFeatureFlag(anyString(), anyString(), anyString(), anyString(), anyString());
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldRestoreFlag() throws Exception {
    mockRestoreFlagCall();

    List<FmeDefinitionInstruction> instructions =
        List.of(FmeSetFlagKilledInstruction.builder().value(ParameterField.createValueField(false)).build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    verify(fmePipelineClient).restoreFeatureFlag(eq(FLAG), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV));
    verify(fmePipelineClient, never()).killFeatureFlag(anyString(), anyString(), anyString(), anyString(), anyString());
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldFailWhenDuplicateSetFlagKilled() {
    List<FmeDefinitionInstruction> instructions =
        List.of(FmeSetFlagKilledInstruction.builder().value(ParameterField.createValueField(true)).build(),
            FmeSetFlagKilledInstruction.builder().value(ParameterField.createValueField(false)).build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
  }

  // --- DefaultDefinition (getOrCreateDefinition) tests ---

  private void mockGetDefinitionNotFound() throws Exception {
    mockGetDefinitionNotFoundThenFound(null);
  }

  private void mockGetDefinitionNotFoundThenFound(FeatureFlagDefinition definitionForSecondCall) throws Exception {
    Call<FmeResponse<FeatureFlagDefinition>> notFoundCall = mock(Call.class);
    Response<FmeResponse<FeatureFlagDefinition>> notFoundResponse = mock(Response.class);
    when(notFoundCall.execute()).thenReturn(notFoundResponse);
    when(notFoundResponse.isSuccessful()).thenReturn(false);
    when(notFoundResponse.code()).thenReturn(404);
    okhttp3.ResponseBody errorBody = mock(okhttp3.ResponseBody.class);
    when(notFoundResponse.errorBody()).thenReturn(errorBody);
    when(errorBody.string()).thenReturn("{\"code\":404,\"message\":\"not found\"}");

    if (definitionForSecondCall != null) {
      Call<FmeResponse<FeatureFlagDefinition>> foundCall = mock(Call.class);
      Response<FmeResponse<FeatureFlagDefinition>> foundResponse = mock(Response.class);
      when(foundCall.execute()).thenReturn(foundResponse);
      when(foundResponse.isSuccessful()).thenReturn(true);
      when(foundResponse.body())
          .thenReturn(FmeResponse.<FeatureFlagDefinition>builder().entity(definitionForSecondCall).build());
      when(fmePipelineClient.getFeatureFlagDefinitionInEnvironment(
               eq(FLAG), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV)))
          .thenReturn(notFoundCall)
          .thenReturn(foundCall);
    } else {
      when(fmePipelineClient.getFeatureFlagDefinitionInEnvironment(
               eq(FLAG), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV)))
          .thenReturn(notFoundCall);
    }
  }

  private void mockCreateDefinitionCall() throws Exception {
    FeatureFlagDefinition created =
        buildDefinition(Treatment.builder().name("beta").description("Beta variant").build(),
            Treatment.builder().name("gamma").description("Gamma variant").build());
    Call<List<FmeResponse<FeatureFlagDefinition>>> createCall = mock(Call.class);
    Response<List<FmeResponse<FeatureFlagDefinition>>> createResponse = mock(Response.class);
    when(createCall.execute()).thenReturn(createResponse);
    when(createResponse.isSuccessful()).thenReturn(true);
    when(createResponse.body())
        .thenReturn(List.of(FmeResponse.<FeatureFlagDefinition>builder().entity(created).build()));
    when(fmePipelineClient.createFeatureFlagDefinition(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV), any(FeatureFlagDefinition.class)))
        .thenReturn(createCall);
  }

  private FmeFlagDefinitionInstructionsStepParameters buildParamsWithDefaultDefinition(String flagName, String env,
      List<FmeDefinitionInstruction> instructions, DefaultDefinitionConfig defaultDefinition) {
    var builder = FmeFlagDefinitionInstructionsStepParameters.builder();
    if (flagName != null) {
      builder.flagName(ParameterField.createValueField(flagName));
    }
    if (env != null) {
      builder.environment(ParameterField.createValueField(env));
    }
    if (instructions != null) {
      builder.instructions(ParameterField.createValueField(instructions));
    }
    builder.defaultDefinition(defaultDefinition);
    return builder.build();
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldCreateDefinitionWhenNotFoundWithDefaultDefinition() throws Exception {
    mockGetDefinitionNotFoundThenFound(
        buildDefinition(Treatment.builder().name("beta").description("Beta variant").build(),
            Treatment.builder().name("gamma").description("Gamma variant").build()));
    mockCreateDefinitionCall();
    mockPatchCall();

    DefaultDefinitionConfig defaultDef = DefaultDefinitionConfig.builder()
                                             .treatments(ParameterField.createValueField(List.of(
                                                 TreatmentConfiguration.builder()
                                                     .treatment(ParameterField.createValueField("beta"))
                                                     .description(ParameterField.createValueField("Beta variant"))
                                                     .build(),
                                                 TreatmentConfiguration.builder()
                                                     .treatment(ParameterField.createValueField("gamma"))
                                                     .description(ParameterField.createValueField("Gamma variant"))
                                                     .build())))
                                             .defaultTreatment(ParameterField.createValueField("beta"))
                                             .baselineTreatment(ParameterField.createValueField("gamma"))
                                             .build();

    List<FmeDefinitionInstruction> instructions =
        List.of(FmeSetDefaultTreatmentInstruction.builder().value(ParameterField.createValueField("beta")).build());

    StepResponse response = executeWith(buildParamsWithDefaultDefinition(FLAG, ENV, instructions, defaultDef));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<FeatureFlagDefinition> createCaptor = ArgumentCaptor.forClass(FeatureFlagDefinition.class);
    verify(fmePipelineClient)
        .createFeatureFlagDefinition(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV), createCaptor.capture());
    FeatureFlagDefinition createdDef = createCaptor.getValue();
    assertThat(createdDef.getTreatments()).hasSize(2);
    assertThat(createdDef.getDefaultTreatment()).isEqualTo("beta");
    assertThat(createdDef.getBaselineTreatment()).isEqualTo("gamma");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldFailWhenDefinitionNotFoundAndNoDefaultDefinition() throws Exception {
    mockGetDefinitionNotFound();

    List<FmeDefinitionInstruction> instructions =
        List.of(FmeSetDefaultTreatmentInstruction.builder().value(ParameterField.createValueField("on")).build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldFailWhenCreateDefinitionReturnsEmptyList() throws Exception {
    mockGetDefinitionNotFound();

    Call<List<FmeResponse<FeatureFlagDefinition>>> createCall = mock(Call.class);
    Response<List<FmeResponse<FeatureFlagDefinition>>> createResponse = mock(Response.class);
    when(createCall.execute()).thenReturn(createResponse);
    when(createResponse.isSuccessful()).thenReturn(true);
    when(createResponse.body()).thenReturn(Collections.emptyList());
    when(fmePipelineClient.createFeatureFlagDefinition(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV), any(FeatureFlagDefinition.class)))
        .thenReturn(createCall);

    DefaultDefinitionConfig defaultDef =
        DefaultDefinitionConfig.builder()
            .treatments(ParameterField.createValueField(List.of(TreatmentConfiguration.builder()
                                                                    .treatment(ParameterField.createValueField("on"))
                                                                    .description(ParameterField.createValueField("On"))
                                                                    .build(),
                TreatmentConfiguration.builder()
                    .treatment(ParameterField.createValueField("off"))
                    .description(ParameterField.createValueField("Off"))
                    .build())))
            .defaultTreatment(ParameterField.createValueField("off"))
            .baselineTreatment(ParameterField.createValueField("on"))
            .build();

    List<FmeDefinitionInstruction> instructions =
        List.of(FmeSetDefaultTreatmentInstruction.builder().value(ParameterField.createValueField("on")).build());

    StepResponse response = executeWith(buildParamsWithDefaultDefinition(FLAG, ENV, instructions, defaultDef));
    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldCreateDefinitionWithDescriptiveTreatmentsAndExplicitConfig() throws Exception {
    FeatureFlagDefinition created = buildDefinition(Treatment.builder().name("alpha").description("Alpha only").build(),
        Treatment.builder().name("beta").description("Beta variant").build());
    mockGetDefinitionNotFoundThenFound(created);

    Call<List<FmeResponse<FeatureFlagDefinition>>> createCall = mock(Call.class);
    Response<List<FmeResponse<FeatureFlagDefinition>>> createResponse = mock(Response.class);
    when(createCall.execute()).thenReturn(createResponse);
    when(createResponse.isSuccessful()).thenReturn(true);
    when(createResponse.body())
        .thenReturn(List.of(FmeResponse.<FeatureFlagDefinition>builder().entity(created).build()));
    when(fmePipelineClient.createFeatureFlagDefinition(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV), any(FeatureFlagDefinition.class)))
        .thenReturn(createCall);
    mockPatchCall();

    DefaultDefinitionConfig defaultDef = DefaultDefinitionConfig.builder()
                                             .treatments(ParameterField.createValueField(
                                                 List.of(TreatmentConfiguration.builder()
                                                             .treatment(ParameterField.createValueField("alpha"))
                                                             .description(ParameterField.createValueField("Alpha only"))
                                                             .build(),
                                                     TreatmentConfiguration.builder()
                                                         .treatment(ParameterField.createValueField("beta"))
                                                         .description(ParameterField.createValueField("Beta variant"))
                                                         .build())))
                                             .defaultTreatment(ParameterField.createValueField("beta"))
                                             .baselineTreatment(ParameterField.createValueField("alpha"))
                                             .build();

    List<FmeDefinitionInstruction> instructions =
        List.of(FmeSetDefaultTreatmentInstruction.builder().value(ParameterField.createValueField("beta")).build());

    StepResponse response = executeWith(buildParamsWithDefaultDefinition(FLAG, ENV, instructions, defaultDef));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<FeatureFlagDefinition> createCaptor = ArgumentCaptor.forClass(FeatureFlagDefinition.class);
    verify(fmePipelineClient)
        .createFeatureFlagDefinition(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV), createCaptor.capture());
    FeatureFlagDefinition createdDef = createCaptor.getValue();
    assertThat(createdDef.getDefaultTreatment()).isEqualTo("beta");
    assertThat(createdDef.getBaselineTreatment()).isEqualTo("alpha");
  }

  // --- Mixed new instruction tests ---

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldHandleSetDefaultAllocationsWithPatchInstructions() throws Exception {
    mockPatchCall();

    List<FmeDefinitionInstruction> instructions =
        List.of(FmeSetDefaultTreatmentInstruction.builder().value(ParameterField.createValueField("on")).build(),
            FmeSetDefaultAllocationsInstruction.builder()
                .value(ParameterField.createValueField(List.of(Allocation.builder()
                                                                   .treatment(ParameterField.createValueField("on"))
                                                                   .amount(ParameterField.createValueField(70))
                                                                   .build(),
                    Allocation.builder()
                        .treatment(ParameterField.createValueField("off"))
                        .amount(ParameterField.createValueField(30))
                        .build())))
                .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<List<FmePatchOperation>> captor = ArgumentCaptor.forClass(List.class);
    verify(fmePipelineClient)
        .patchFeatureFlagDefinition(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV), eq(FLAG), captor.capture());
    List<FmePatchOperation> ops = captor.getValue();
    assertThat(ops).hasSize(2);
    assertThat(ops.get(0).getPath()).isEqualTo("/defaultTreatment");
    assertThat(ops.get(1).getPath()).isEqualTo("/defaultRule");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldHandleKillFlagWithPatchInstructions() throws Exception {
    mockPatchCall();
    mockKillFlagCall();

    List<FmeDefinitionInstruction> instructions =
        List.of(FmeSetDefaultTreatmentInstruction.builder().value(ParameterField.createValueField("on")).build(),
            FmeSetFlagKilledInstruction.builder().value(ParameterField.createValueField(true)).build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    verify(fmePipelineClient)
        .patchFeatureFlagDefinition(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV), eq(FLAG), anyList());
    verify(fmePipelineClient).killFeatureFlag(eq(FLAG), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV));
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldHandleRolloutStatusWithSetTreatments() throws Exception {
    mockPatchCall();
    mockUpdateFeatureFlagCall();
    mockGetFeatureFlagCall();

    List<FmeDefinitionInstruction> instructions = List.of(
        FmeSetTreatmentsInstruction.builder()
            .value(ParameterField.createValueField(List.of(TreatmentConfiguration.builder()
                                                               .treatment(ParameterField.createValueField("gamma"))
                                                               .description(ParameterField.createValueField("Gamma"))
                                                               .build())))
            .build(),
        FmeSetRolloutStatusInstruction.builder().value(ParameterField.createValueField("Released")).build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    verify(fmePipelineClient)
        .patchFeatureFlagDefinition(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV), eq(FLAG), anyList());
    verify(fmePipelineClient).updateFeatureFlag(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(FLAG), anyList());
  }

  // --- Kill/Restore API error paths ---

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldFailWhenKillFlagApiReturnsError() throws Exception {
    Call<FmeResponse<FeatureFlagDefinition>> killCall = mock(Call.class);
    Response<FmeResponse<FeatureFlagDefinition>> killResponse = mock(Response.class);
    okhttp3.ResponseBody errorBody = mock(okhttp3.ResponseBody.class);
    when(killCall.execute()).thenReturn(killResponse);
    when(killResponse.isSuccessful()).thenReturn(false);
    when(killResponse.code()).thenReturn(500);
    when(killResponse.errorBody()).thenReturn(errorBody);
    when(errorBody.string()).thenReturn("kill failed");
    when(fmePipelineClient.killFeatureFlag(eq(FLAG), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV)))
        .thenReturn(killCall);

    List<FmeDefinitionInstruction> instructions =
        List.of(FmeSetFlagKilledInstruction.builder().value(ParameterField.createValueField(true)).build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldFailWhenRestoreFlagApiReturnsError() throws Exception {
    Call<FmeResponse<FeatureFlagDefinition>> restoreCall = mock(Call.class);
    Response<FmeResponse<FeatureFlagDefinition>> restoreResponse = mock(Response.class);
    okhttp3.ResponseBody errorBody = mock(okhttp3.ResponseBody.class);
    when(restoreCall.execute()).thenReturn(restoreResponse);
    when(restoreResponse.isSuccessful()).thenReturn(false);
    when(restoreResponse.code()).thenReturn(500);
    when(restoreResponse.errorBody()).thenReturn(errorBody);
    when(errorBody.string()).thenReturn("restore failed");
    when(fmePipelineClient.restoreFeatureFlag(eq(FLAG), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV)))
        .thenReturn(restoreCall);

    List<FmeDefinitionInstruction> instructions =
        List.of(FmeSetFlagKilledInstruction.builder().value(ParameterField.createValueField(false)).build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldFailWhenSetRolloutStatusApiReturnsError() throws Exception {
    mockGetFeatureFlagCall();
    Call<FmeResponse<FeatureFlag>> updateCall = mock(Call.class);
    Response<FmeResponse<FeatureFlag>> updateResponse = mock(Response.class);
    okhttp3.ResponseBody errorBody = mock(okhttp3.ResponseBody.class);
    when(updateCall.execute()).thenReturn(updateResponse);
    when(updateResponse.isSuccessful()).thenReturn(false);
    when(updateResponse.code()).thenReturn(500);
    when(updateResponse.errorBody()).thenReturn(errorBody);
    when(errorBody.string()).thenReturn("update failed");
    when(fmePipelineClient.updateFeatureFlag(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(FLAG), anyList()))
        .thenReturn(updateCall);

    List<FmeDefinitionInstruction> instructions =
        List.of(FmeSetRolloutStatusInstruction.builder().value(ParameterField.createValueField("Released")).build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
  }

  // --- SetFlagKilled null value ---

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldFailWhenSetFlagKilledValueNull() {
    List<FmeDefinitionInstruction> instructions =
        List.of(FmeSetFlagKilledInstruction.builder().value(ParameterField.createValueField(null)).build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldFailWhenSetRolloutStatusValueNull() {
    List<FmeDefinitionInstruction> instructions =
        List.of(FmeSetRolloutStatusInstruction.builder().value(ParameterField.createValueField(null)).build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldFailWhenSetDefaultAllocationsValueNull() {
    List<FmeDefinitionInstruction> instructions =
        List.of(FmeSetDefaultAllocationsInstruction.builder().value(ParameterField.createValueField(null)).build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldFailWhenSetTreatmentsValueNull() {
    List<FmeDefinitionInstruction> instructions =
        List.of(FmeSetTreatmentsInstruction.builder().value(ParameterField.createValueField(null)).build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
  }

  // --- Partial failure: patch succeeds but targeting rules fail ---

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldWarnAboutPartialUpdateWhenTargetingRulesFailAfterPatch() throws Exception {
    mockPatchCall();

    Call<FmeResponse<FeatureFlagDefinition>> rulesCall = mock(Call.class);
    Response<FmeResponse<FeatureFlagDefinition>> rulesResponse = mock(Response.class);
    okhttp3.ResponseBody errorBody = mock(okhttp3.ResponseBody.class);
    when(rulesCall.execute()).thenReturn(rulesResponse);
    when(rulesResponse.isSuccessful()).thenReturn(false);
    when(rulesResponse.code()).thenReturn(500);
    when(rulesResponse.errorBody()).thenReturn(errorBody);
    when(errorBody.string()).thenReturn("rules failed");
    when(fmePipelineClient.updateFeatureFlagDefinitionRules(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(FLAG), eq(ENV), anyList()))
        .thenReturn(rulesCall);

    RuleAllocation alloc = RuleAllocation.builder()
                               .treatment(ParameterField.createValueField("on"))
                               .size(ParameterField.createValueField(100))
                               .build();
    TargetRules targetRules = TargetRules.builder()
                                  .condition(ParameterField.ofNull())
                                  .allocation(ParameterField.createValueField(Collections.singletonList(alloc)))
                                  .build();

    List<FmeDefinitionInstruction> instructions =
        List.of(FmeSetDefaultTreatmentInstruction.builder().value(ParameterField.createValueField("on")).build(),
            FmeSetTargetingRulesInstruction.builder()
                .value(ParameterField.createValueField(Collections.singletonList(targetRules)))
                .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.FAILED);

    verify(fmePipelineClient)
        .patchFeatureFlagDefinition(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV), eq(FLAG), anyList());
  }

  // --- SetTreatments: override replaces all treatments ---

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldSetTreatmentsOverrideReplacesAll() throws Exception {
    mockPatchCall();

    List<FmeDefinitionInstruction> instructions =
        List.of(FmeSetTreatmentsInstruction.builder()
                    .value(ParameterField.createValueField(
                        List.of(TreatmentConfiguration.builder()
                                    .treatment(ParameterField.createValueField("on"))
                                    .description(ParameterField.createValueField("Updated description"))
                                    .build())))
                    .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<List<FmePatchOperation>> captor = ArgumentCaptor.forClass(List.class);
    verify(fmePipelineClient)
        .patchFeatureFlagDefinition(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV), eq(FLAG), captor.capture());
    List<FmePatchOperation> ops = captor.getValue();
    assertThat(ops).hasSize(1);
    assertThat(ops.get(0).getPath()).isEqualTo("/treatments");
    List<Treatment> treatments = (List<Treatment>) ops.get(0).getValue();
    assertThat(treatments).hasSize(1);
    assertThat(treatments.get(0).getName()).isEqualTo("on");
    assertThat(treatments.get(0).getDescription()).isEqualTo("Updated description");
  }

  // --- SetTreatments: null description keeps existing ---

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldSetTreatmentsWithNullDescription() throws Exception {
    mockPatchCall();

    List<FmeDefinitionInstruction> instructions = List.of(
        FmeSetTreatmentsInstruction.builder()
            .value(ParameterField.createValueField(
                List.of(TreatmentConfiguration.builder().treatment(ParameterField.createValueField("on")).build())))
            .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);
  }

  // --- SetDefaultAllocations: sends only user-specified allocations, downstream handles missing treatments ---

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldSetDefaultAllocationsWithOnlySpecifiedTreatments() throws Exception {
    mockPatchCall();
    FeatureFlagDefinition definition = buildDefinition(Treatment.builder().name("on").build(),
        Treatment.builder().name("off").build(), Treatment.builder().name("beta").build());
    mockGetDefinition(definition);

    List<FmeDefinitionInstruction> instructions =
        List.of(FmeSetDefaultAllocationsInstruction.builder()
                    .value(ParameterField.createValueField(List.of(Allocation.builder()
                                                                       .treatment(ParameterField.createValueField("on"))
                                                                       .amount(ParameterField.createValueField(100))
                                                                       .build())))
                    .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<List<FmePatchOperation>> captor = ArgumentCaptor.forClass(List.class);
    verify(fmePipelineClient)
        .patchFeatureFlagDefinition(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV), eq(FLAG), captor.capture());
    List<FmePatchOperation> ops = captor.getValue();
    List<Bucket> buckets = (List<Bucket>) ops.get(0).getValue();
    assertThat(buckets).hasSize(1);
    assertThat(buckets.get(0).getTreatment()).isEqualTo("on");
    assertThat(buckets.get(0).getSize()).isEqualTo(100);
  }

  // --- DefaultDefinition with explicit defaultTreatment and baselineTreatment ---

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldCreateDefinitionWithExplicitDefaultAndBaseline() throws Exception {
    FeatureFlagDefinition created =
        buildDefinition(Treatment.builder().name("alpha").build(), Treatment.builder().name("beta").build());
    mockGetDefinitionNotFoundThenFound(created);
    mockPatchCall();

    Call<List<FmeResponse<FeatureFlagDefinition>>> createCall = mock(Call.class);
    Response<List<FmeResponse<FeatureFlagDefinition>>> createResponse = mock(Response.class);
    when(createCall.execute()).thenReturn(createResponse);
    when(createResponse.isSuccessful()).thenReturn(true);
    when(createResponse.body())
        .thenReturn(List.of(FmeResponse.<FeatureFlagDefinition>builder().entity(created).build()));
    when(fmePipelineClient.createFeatureFlagDefinition(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV), any(FeatureFlagDefinition.class)))
        .thenReturn(createCall);

    DefaultDefinitionConfig defaultDef = DefaultDefinitionConfig.builder()
                                             .treatments(ParameterField.createValueField(
                                                 List.of(TreatmentConfiguration.builder()
                                                             .treatment(ParameterField.createValueField("alpha"))
                                                             .description(ParameterField.createValueField("Alpha"))
                                                             .build(),
                                                     TreatmentConfiguration.builder()
                                                         .treatment(ParameterField.createValueField("beta"))
                                                         .description(ParameterField.createValueField("Beta"))
                                                         .build())))
                                             .defaultTreatment(ParameterField.createValueField("beta"))
                                             .baselineTreatment(ParameterField.createValueField("alpha"))
                                             .build();

    List<FmeDefinitionInstruction> instructions =
        List.of(FmeSetDefaultTreatmentInstruction.builder().value(ParameterField.createValueField("beta")).build());

    StepResponse response = executeWith(buildParamsWithDefaultDefinition(FLAG, ENV, instructions, defaultDef));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<FeatureFlagDefinition> createCaptor = ArgumentCaptor.forClass(FeatureFlagDefinition.class);
    verify(fmePipelineClient)
        .createFeatureFlagDefinition(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV), createCaptor.capture());
    FeatureFlagDefinition createdDef = createCaptor.getValue();
    assertThat(createdDef.getDefaultTreatment()).isEqualTo("beta");
    assertThat(createdDef.getBaselineTreatment()).isEqualTo("alpha");
    assertThat(createdDef.getDefaultRule()).hasSize(2);
    assertThat(createdDef.getDefaultRule().stream().filter(b -> b.getSize() == 100).findFirst().get().getTreatment())
        .isEqualTo("beta");
  }

  // --- Combined kill with targeting rules (no patch) ---

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldHandleKillFlagWithoutPatchInstructions() throws Exception {
    mockKillFlagCall();

    List<FmeDefinitionInstruction> instructions =
        List.of(FmeSetFlagKilledInstruction.builder().value(ParameterField.createValueField(true)).build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    verify(fmePipelineClient, never())
        .patchFeatureFlagDefinition(anyString(), anyString(), anyString(), anyString(), anyString(), anyList());
    verify(fmePipelineClient).killFeatureFlag(eq(FLAG), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV));
  }

  // --- Restore flag with other patch instructions ---

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldHandleRestoreFlagWithPatchInstructions() throws Exception {
    mockPatchCall();
    mockRestoreFlagCall();

    List<FmeDefinitionInstruction> instructions =
        List.of(FmeSetDefaultTreatmentInstruction.builder().value(ParameterField.createValueField("off")).build(),
            FmeSetFlagKilledInstruction.builder().value(ParameterField.createValueField(false)).build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    verify(fmePipelineClient)
        .patchFeatureFlagDefinition(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV), eq(FLAG), anyList());
    verify(fmePipelineClient).restoreFeatureFlag(eq(FLAG), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV));
  }

  // --- SetDefaultAllocations with all allocations matching all treatments ---

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldSetDefaultAllocationsEvenSplit() throws Exception {
    mockPatchCall();

    List<FmeDefinitionInstruction> instructions =
        List.of(FmeSetDefaultAllocationsInstruction.builder()
                    .value(ParameterField.createValueField(List.of(Allocation.builder()
                                                                       .treatment(ParameterField.createValueField("on"))
                                                                       .amount(ParameterField.createValueField(50))
                                                                       .build(),
                        Allocation.builder()
                            .treatment(ParameterField.createValueField("off"))
                            .amount(ParameterField.createValueField(50))
                            .build())))
                    .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<List<FmePatchOperation>> captor = ArgumentCaptor.forClass(List.class);
    verify(fmePipelineClient)
        .patchFeatureFlagDefinition(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV), eq(FLAG), captor.capture());
    List<Bucket> buckets = (List<Bucket>) captor.getValue().get(0).getValue();
    assertThat(buckets.stream().mapToInt(Bucket::getSize).sum()).isEqualTo(100);
  }

  // --- Ensure STEP_TYPE is not null ---

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldHaveStepType() {
    assertThat(FmeFlagDefinitionInstructionsStep.STEP_TYPE).isNotNull();
  }

  // --- SetTreatments preserves existing treatment order ---

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldSetTreatmentsOverridePreservesInstructionOrder() throws Exception {
    mockPatchCall();
    FeatureFlagDefinition definition =
        buildDefinition(Treatment.builder().name("on").description("On treatment").build(),
            Treatment.builder().name("off").description("Off treatment").build());
    mockGetDefinition(definition);

    List<FmeDefinitionInstruction> instructions = List.of(
        FmeSetTreatmentsInstruction.builder()
            .value(
                ParameterField.createValueField(List.of(TreatmentConfiguration.builder()
                                                            .treatment(ParameterField.createValueField("off"))
                                                            .description(ParameterField.createValueField("Updated off"))
                                                            .build(),
                    TreatmentConfiguration.builder()
                        .treatment(ParameterField.createValueField("new_variant"))
                        .description(ParameterField.createValueField("Brand new"))
                        .build())))
            .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<List<FmePatchOperation>> captor = ArgumentCaptor.forClass(List.class);
    verify(fmePipelineClient)
        .patchFeatureFlagDefinition(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV), eq(FLAG), captor.capture());
    List<Treatment> treatments = (List<Treatment>) captor.getValue().get(0).getValue();
    assertThat(treatments).hasSize(2);
    assertThat(treatments.get(0).getName()).isEqualTo("off");
    assertThat(treatments.get(0).getDescription()).isEqualTo("Updated off");
    assertThat(treatments.get(1).getName()).isEqualTo("new_variant");
    assertThat(treatments.get(1).getDescription()).isEqualTo("Brand new");
  }

  // --- All instruction types combined in one step ---

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldHandleAllInstructionTypesCombined() throws Exception {
    mockPatchCall();
    mockKillFlagCall();
    mockUpdateFeatureFlagCall();
    mockGetFeatureFlagCall();
    mockUpdateRulesCall();

    List<FmeDefinitionInstruction> instructions = List.of(
        FmeSetDefaultTreatmentInstruction.builder().value(ParameterField.createValueField("on")).build(),
        FmeSetBaselineTreatmentInstruction.builder().value(ParameterField.createValueField("off")).build(),
        FmeSetTrackImpressionInstruction.builder().value(ParameterField.createValueField(true)).build(),
        FmeSetLimitExposureInstruction.builder().value(ParameterField.createValueField(80)).build(),
        FmeSetDefaultAllocationsInstruction.builder()
            .value(ParameterField.createValueField(List.of(Allocation.builder()
                                                               .treatment(ParameterField.createValueField("on"))
                                                               .amount(ParameterField.createValueField(60))
                                                               .build(),
                Allocation.builder()
                    .treatment(ParameterField.createValueField("off"))
                    .amount(ParameterField.createValueField(40))
                    .build())))
            .build(),
        FmeSetTreatmentsInstruction.builder()
            .value(ParameterField.createValueField(List.of(TreatmentConfiguration.builder()
                                                               .treatment(ParameterField.createValueField("on"))
                                                               .description(ParameterField.createValueField("updated"))
                                                               .build(),
                TreatmentConfiguration.builder()
                    .treatment(ParameterField.createValueField("off"))
                    .description(ParameterField.createValueField("off treatment"))
                    .build())))
            .build(),
        FmeSetTargetingRulesInstruction.builder()
            .value(ParameterField.createValueField(
                Collections.singletonList(TargetRules.builder()
                                              .condition(ParameterField.ofNull())
                                              .allocation(ParameterField.createValueField(Collections.singletonList(
                                                  RuleAllocation.builder()
                                                      .treatment(ParameterField.createValueField("on"))
                                                      .size(ParameterField.createValueField(100))
                                                      .build())))
                                              .build())))
            .build(),
        FmeSetRolloutStatusInstruction.builder().value(ParameterField.createValueField("Ramping")).build(),
        FmeSetFlagKilledInstruction.builder().value(ParameterField.createValueField(true)).build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    verify(fmePipelineClient)
        .patchFeatureFlagDefinition(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV), eq(FLAG), anyList());
    verify(fmePipelineClient)
        .updateFeatureFlagDefinitionRules(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(FLAG), eq(ENV), anyList());
    verify(fmePipelineClient).updateFeatureFlag(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(FLAG), anyList());
    verify(fmePipelineClient).killFeatureFlag(eq(FLAG), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV));
  }

  // --- SetTreatments + SetDefaultAllocations combined: new treatment with allocation ---

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldSetTreatmentsAndDefaultAllocationsWithNewTreatment() throws Exception {
    mockPatchCall();

    List<FmeDefinitionInstruction> instructions = List.of(
        FmeSetTreatmentsInstruction.builder()
            .value(ParameterField.createValueField(List.of(TreatmentConfiguration.builder()
                                                               .treatment(ParameterField.createValueField("beta"))
                                                               .description(ParameterField.createValueField("Beta"))
                                                               .build())))
            .build(),
        FmeSetDefaultAllocationsInstruction.builder()
            .value(ParameterField.createValueField(List.of(Allocation.builder()
                                                               .treatment(ParameterField.createValueField("beta"))
                                                               .amount(ParameterField.createValueField(100))
                                                               .build())))
            .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<List<FmePatchOperation>> captor = ArgumentCaptor.forClass(List.class);
    verify(fmePipelineClient)
        .patchFeatureFlagDefinition(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV), eq(FLAG), captor.capture());
    List<FmePatchOperation> ops = captor.getValue();
    assertThat(ops).hasSize(2);
    assertThat(ops.get(0).getPath()).isEqualTo("/treatments");
    assertThat(ops.get(1).getPath()).isEqualTo("/defaultRule");

    List<Treatment> treatments = (List<Treatment>) ops.get(0).getValue();
    assertThat(treatments).hasSize(1);
    assertThat(treatments.get(0).getName()).isEqualTo("beta");

    List<Bucket> buckets = (List<Bucket>) ops.get(1).getValue();
    assertThat(buckets).hasSize(1);
    assertThat(buckets.get(0).getTreatment()).isEqualTo("beta");
    assertThat(buckets.get(0).getSize()).isEqualTo(100);
  }

  // --- Reverse YAML order: SetDefaultAllocations before SetTreatments ---

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldHandleDefaultAllocationsBeforeSetTreatmentsInYamlOrder() throws Exception {
    mockPatchCall();

    List<FmeDefinitionInstruction> instructions = List.of(
        FmeSetDefaultAllocationsInstruction.builder()
            .value(ParameterField.createValueField(List.of(Allocation.builder()
                                                               .treatment(ParameterField.createValueField("beta"))
                                                               .amount(ParameterField.createValueField(100))
                                                               .build())))
            .build(),
        FmeSetTreatmentsInstruction.builder()
            .value(ParameterField.createValueField(List.of(TreatmentConfiguration.builder()
                                                               .treatment(ParameterField.createValueField("beta"))
                                                               .description(ParameterField.createValueField("Beta"))
                                                               .build())))
            .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<List<FmePatchOperation>> captor = ArgumentCaptor.forClass(List.class);
    verify(fmePipelineClient)
        .patchFeatureFlagDefinition(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV), eq(FLAG), captor.capture());
    List<FmePatchOperation> ops = captor.getValue();
    assertThat(ops).hasSize(2);
    assertThat(ops.get(0).getPath()).isEqualTo("/treatments");
    assertThat(ops.get(1).getPath()).isEqualTo("/defaultRule");
  }

  // --- SetTreatments override removes old treatments ---

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldSetTreatmentsOverrideRemovesOldTreatments() throws Exception {
    mockPatchCall();
    FeatureFlagDefinition definition = buildDefinition(Treatment.builder().name("on").build(),
        Treatment.builder().name("off").build(), Treatment.builder().name("legacy").build());
    mockGetDefinition(definition);

    List<FmeDefinitionInstruction> instructions = List.of(
        FmeSetTreatmentsInstruction.builder()
            .value(ParameterField.createValueField(
                List.of(TreatmentConfiguration.builder().treatment(ParameterField.createValueField("v1")).build(),
                    TreatmentConfiguration.builder().treatment(ParameterField.createValueField("v2")).build())))
            .build());

    StepResponse response = executeWith(buildParams(FLAG, ENV, instructions));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<List<FmePatchOperation>> captor = ArgumentCaptor.forClass(List.class);
    verify(fmePipelineClient)
        .patchFeatureFlagDefinition(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV), eq(FLAG), captor.capture());
    List<Treatment> treatments = (List<Treatment>) captor.getValue().get(0).getValue();
    assertThat(treatments).hasSize(2);
    assertThat(treatments.get(0).getName()).isEqualTo("v1");
    assertThat(treatments.get(1).getName()).isEqualTo("v2");
  }

  // --- Deserialization test: proves Jackson can parse the actual Main API JSON array response ---

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void shouldDeserializeCreateDefinitionArrayResponse() throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    String jsonArrayResponse = "[\n"
        + "  {\n"
        + "    \"entity\": {\n"
        + "      \"name\": \"test-flag\",\n"
        + "      \"treatments\": [\n"
        + "        {\"name\": \"on\", \"description\": \"Enabled\"},\n"
        + "        {\"name\": \"off\", \"description\": \"Disabled\"}\n"
        + "      ],\n"
        + "      \"defaultTreatment\": \"off\",\n"
        + "      \"baselineTreatment\": \"off\",\n"
        + "      \"trafficAllocation\": 100,\n"
        + "      \"defaultRule\": [\n"
        + "        {\"treatment\": \"on\", \"size\": 0},\n"
        + "        {\"treatment\": \"off\", \"size\": 100}\n"
        + "      ],\n"
        + "      \"id\": \"some-uuid-123\",\n"
        + "      \"unknownField\": \"should-be-ignored\"\n"
        + "    },\n"
        + "    \"governance\": null\n"
        + "  }\n"
        + "]";

    TypeReference<List<FmeResponse<FeatureFlagDefinition>>> typeRef = new TypeReference<>() {};
    List<FmeResponse<FeatureFlagDefinition>> responses = objectMapper.readValue(jsonArrayResponse, typeRef);

    assertThat(responses).hasSize(1);
    assertThat(responses.get(0)).isNotNull();
    assertThat(responses.get(0).getEntity()).isNotNull();

    FeatureFlagDefinition definition = responses.get(0).getEntity();
    assertThat(definition.getName()).isEqualTo("test-flag");
    assertThat(definition.getDefaultTreatment()).isEqualTo("off");
    assertThat(definition.getBaselineTreatment()).isEqualTo("off");
    assertThat(definition.getTrafficAllocation()).isEqualTo(100);
    assertThat(definition.getTreatments()).hasSize(2);
    assertThat(definition.getTreatments().get(0).getName()).isEqualTo("on");
    assertThat(definition.getTreatments().get(0).getDescription()).isEqualTo("Enabled");
    assertThat(definition.getTreatments().get(1).getName()).isEqualTo("off");
    assertThat(definition.getDefaultRule()).hasSize(2);
    assertThat(definition.getDefaultRule().get(0).getTreatment()).isEqualTo("on");
    assertThat(definition.getDefaultRule().get(0).getSize()).isEqualTo(0);
    assertThat(definition.getDefaultRule().get(1).getTreatment()).isEqualTo("off");
    assertThat(definition.getDefaultRule().get(1).getSize()).isEqualTo(100);
  }
}
