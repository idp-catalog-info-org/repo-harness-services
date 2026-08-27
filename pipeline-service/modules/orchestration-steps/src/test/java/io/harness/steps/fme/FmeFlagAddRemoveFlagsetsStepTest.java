/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.fme;

import static io.harness.rule.OwnerRule.ROHITPAL;
import static io.harness.steps.StepUtils.PIE_SIMPLIFY_LOG_BASE_KEY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.fme.FMEPipelineClient;
import io.harness.fme.FeatureFlagDefinition;
import io.harness.fme.FlagSetAssociationRef;
import io.harness.fme.FmePatchOperation;
import io.harness.fme.FmeResponse;
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
import io.harness.steps.StepSpecTypeConstants;

import java.io.IOException;
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
public class FmeFlagAddRemoveFlagsetsStepTest extends CategoryTest {
  @InjectMocks FmeFlagAddRemoveFlagsetsStep step;
  private Ambiance ambiance;
  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Mock private FMEPipelineClient fmePipelineClient;
  @Spy private FmeStepResponseBuilder fmeStepResponseBuilder;
  @Spy private ExceptionManager exceptionManager;

  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  private static final String FLAG_NAME = "test-flag";
  private static final String ENVIRONMENT = "Production";

  @Before
  public void setup() {
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

    fmeStepResponseBuilder.setExceptionManager(exceptionManager);
  }

  private void mockGetDefinition(List<FlagSetAssociationRef> existingFlagSets) throws IOException {
    FeatureFlagDefinition definition =
        FeatureFlagDefinition.builder().name(FLAG_NAME).flagSets(existingFlagSets).build();
    FmeResponse<FeatureFlagDefinition> getResponse = new FmeResponse<>();
    getResponse.setEntity(definition);

    Call<FmeResponse<FeatureFlagDefinition>> getCall = mock(Call.class);
    when(getCall.execute()).thenReturn(Response.success(getResponse));
    when(fmePipelineClient.getFeatureFlagDefinitionInEnvironment(
             eq(FLAG_NAME), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENVIRONMENT)))
        .thenReturn(getCall);
  }

  private void mockPatchDefinition() throws IOException {
    FeatureFlagDefinition patchedDef = FeatureFlagDefinition.builder().name(FLAG_NAME).build();
    FmeResponse<FeatureFlagDefinition> patchResponse = new FmeResponse<>();
    patchResponse.setEntity(patchedDef);

    Call<FmeResponse<FeatureFlagDefinition>> patchCall = mock(Call.class);
    when(patchCall.execute()).thenReturn(Response.success(patchResponse));
    when(fmePipelineClient.patchFeatureFlagDefinition(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENVIRONMENT), eq(FLAG_NAME), anyList()))
        .thenReturn(patchCall);
  }

  @SuppressWarnings("unchecked")
  private List<FmePatchOperation> capturePatchOps() {
    ArgumentCaptor<List<FmePatchOperation>> captor = ArgumentCaptor.forClass(List.class);
    verify(fmePipelineClient, times(1))
        .patchFeatureFlagDefinition(
            eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENVIRONMENT), eq(FLAG_NAME), captor.capture());
    return captor.getValue();
  }

  @Test
  @Owner(developers = ROHITPAL)
  @Category(UnitTests.class)
  public void testExecuteSyncAfterRbacWithAddFlagsets() throws Exception {
    mockGetDefinition(List.of(FlagSetAssociationRef.of("existing-fs-1")));
    mockPatchDefinition();

    FmeFlagAddRemoveFlagsetsParameters params =
        FmeFlagAddRemoveFlagsetsParameters.builder()
            .flagName(ParameterField.createValueField(FLAG_NAME))
            .environment(ParameterField.createValueField(ENVIRONMENT))
            .addFlagsets(ParameterField.createValueField(Arrays.asList("new-fs-1", "new-fs-2")))
            .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    StepResponse response =
        step.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    List<FmePatchOperation> ops = capturePatchOps();
    assertThat(ops).hasSize(1);
    assertThat(ops.get(0).getOp()).isEqualTo("add");
    assertThat(ops.get(0).getPath()).isEqualTo("/flagSets");

    List<FlagSetAssociationRef> flagSets = (List<FlagSetAssociationRef>) ops.get(0).getValue();
    List<String> ids = flagSets.stream().map(FlagSetAssociationRef::getId).toList();
    assertThat(ids).containsExactly("existing-fs-1", "new-fs-1", "new-fs-2");
  }

  @Test
  @Owner(developers = ROHITPAL)
  @Category(UnitTests.class)
  public void testExecuteSyncAfterRbacWithRemoveFlagsets() throws Exception {
    mockGetDefinition(
        List.of(FlagSetAssociationRef.of("fs-a"), FlagSetAssociationRef.of("fs-b"), FlagSetAssociationRef.of("fs-c")));
    mockPatchDefinition();

    FmeFlagAddRemoveFlagsetsParameters params =
        FmeFlagAddRemoveFlagsetsParameters.builder()
            .flagName(ParameterField.createValueField(FLAG_NAME))
            .environment(ParameterField.createValueField(ENVIRONMENT))
            .removeFlagsets(ParameterField.createValueField(Arrays.asList("fs-b")))
            .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    StepResponse response =
        step.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    List<FmePatchOperation> ops = capturePatchOps();
    List<FlagSetAssociationRef> flagSets = (List<FlagSetAssociationRef>) ops.get(0).getValue();
    List<String> ids = flagSets.stream().map(FlagSetAssociationRef::getId).toList();
    assertThat(ids).containsExactly("fs-a", "fs-c");
  }

  @Test
  @Owner(developers = ROHITPAL)
  @Category(UnitTests.class)
  public void testExecuteSyncAfterRbacWithBothAddAndRemoveFlagsets() throws Exception {
    mockGetDefinition(
        List.of(FlagSetAssociationRef.of("fs-1"), FlagSetAssociationRef.of("fs-2"), FlagSetAssociationRef.of("fs-3")));
    mockPatchDefinition();

    FmeFlagAddRemoveFlagsetsParameters params =
        FmeFlagAddRemoveFlagsetsParameters.builder()
            .flagName(ParameterField.createValueField(FLAG_NAME))
            .environment(ParameterField.createValueField(ENVIRONMENT))
            .addFlagsets(ParameterField.createValueField(Arrays.asList("fs-new")))
            .removeFlagsets(ParameterField.createValueField(Arrays.asList("fs-2")))
            .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    StepResponse response =
        step.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    List<FmePatchOperation> ops = capturePatchOps();
    List<FlagSetAssociationRef> flagSets = (List<FlagSetAssociationRef>) ops.get(0).getValue();
    List<String> ids = flagSets.stream().map(FlagSetAssociationRef::getId).toList();
    assertThat(ids).containsExactly("fs-1", "fs-3", "fs-new");
  }

  @Test
  @Owner(developers = ROHITPAL)
  @Category(UnitTests.class)
  public void testExecuteSyncAfterRbacWithNoExistingFlagSets() throws Exception {
    mockGetDefinition(null);
    mockPatchDefinition();

    FmeFlagAddRemoveFlagsetsParameters params =
        FmeFlagAddRemoveFlagsetsParameters.builder()
            .flagName(ParameterField.createValueField(FLAG_NAME))
            .environment(ParameterField.createValueField(ENVIRONMENT))
            .addFlagsets(ParameterField.createValueField(Arrays.asList("fs-brand-new")))
            .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    StepResponse response =
        step.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    List<FmePatchOperation> ops = capturePatchOps();
    List<FlagSetAssociationRef> flagSets = (List<FlagSetAssociationRef>) ops.get(0).getValue();
    List<String> ids = flagSets.stream().map(FlagSetAssociationRef::getId).toList();
    assertThat(ids).containsExactly("fs-brand-new");
  }

  @Test
  @Owner(developers = ROHITPAL)
  @Category(UnitTests.class)
  public void testExecuteSyncAfterRbacFailure() throws Exception {
    Call<FmeResponse<FeatureFlagDefinition>> getCall = mock(Call.class);
    when(getCall.execute()).thenThrow(new IOException("Network error"));
    when(fmePipelineClient.getFeatureFlagDefinitionInEnvironment(
             eq(FLAG_NAME), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENVIRONMENT)))
        .thenReturn(getCall);

    FmeFlagAddRemoveFlagsetsParameters params =
        FmeFlagAddRemoveFlagsetsParameters.builder()
            .flagName(ParameterField.createValueField(FLAG_NAME))
            .environment(ParameterField.createValueField(ENVIRONMENT))
            .addFlagsets(ParameterField.createValueField(Arrays.asList("flagset1")))
            .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    StepResponse response =
        step.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = ROHITPAL)
  @Category(UnitTests.class)
  public void testExecuteSyncAfterRbacMissingFlagName() {
    FmeFlagAddRemoveFlagsetsParameters params =
        FmeFlagAddRemoveFlagsetsParameters.builder()
            .flagName(ParameterField.createValueField(null))
            .environment(ParameterField.createValueField(ENVIRONMENT))
            .addFlagsets(ParameterField.createValueField(Arrays.asList("flagset1")))
            .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    StepResponse response =
        step.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
    assertThat(response.getFailureInfo()).isNotNull();
  }

  @Test
  @Owner(developers = ROHITPAL)
  @Category(UnitTests.class)
  public void testExecuteSyncAfterRbacMissingEnvironment() {
    FmeFlagAddRemoveFlagsetsParameters params =
        FmeFlagAddRemoveFlagsetsParameters.builder()
            .flagName(ParameterField.createValueField(FLAG_NAME))
            .environment(ParameterField.createValueField(null))
            .addFlagsets(ParameterField.createValueField(Arrays.asList("flagset1")))
            .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    StepResponse response =
        step.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
    assertThat(response.getFailureInfo()).isNotNull();
  }

  @Test
  @Owner(developers = ROHITPAL)
  @Category(UnitTests.class)
  public void testExecuteSyncAfterRbacNoFlagsetsProvided() {
    FmeFlagAddRemoveFlagsetsParameters params = FmeFlagAddRemoveFlagsetsParameters.builder()
                                                    .flagName(ParameterField.createValueField(FLAG_NAME))
                                                    .environment(ParameterField.createValueField(ENVIRONMENT))
                                                    .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    StepResponse response =
        step.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
    assertThat(response.getFailureInfo()).isNotNull();

    verify(fmePipelineClient, never()).getFeatureFlagDefinitionInEnvironment(any(), any(), any(), any(), any());
    verify(fmePipelineClient, never()).patchFeatureFlagDefinition(any(), any(), any(), any(), any(), anyList());
  }

  @Test
  @Owner(developers = ROHITPAL)
  @Category(UnitTests.class)
  public void testExecuteSyncAfterRbacEmptyFlagsetsProvided() {
    FmeFlagAddRemoveFlagsetsParameters params =
        FmeFlagAddRemoveFlagsetsParameters.builder()
            .flagName(ParameterField.createValueField(FLAG_NAME))
            .environment(ParameterField.createValueField(ENVIRONMENT))
            .addFlagsets(ParameterField.createValueField(Collections.emptyList()))
            .removeFlagsets(ParameterField.createValueField(Collections.emptyList()))
            .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    StepResponse response =
        step.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
    assertThat(response.getFailureInfo()).isNotNull();

    verify(fmePipelineClient, never()).getFeatureFlagDefinitionInEnvironment(any(), any(), any(), any(), any());
    verify(fmePipelineClient, never()).patchFeatureFlagDefinition(any(), any(), any(), any(), any(), anyList());
  }

  @Test
  @Owner(developers = ROHITPAL)
  @Category(UnitTests.class)
  public void testStepType() {
    assertThat(FmeFlagAddRemoveFlagsetsStep.STEP_TYPE)
        .isEqualTo(StepSpecTypeConstants.FME_FLAG_ADD_REMOVE_FLAGSETS_STEP_TYPE);
  }

  @Test
  @Owner(developers = ROHITPAL)
  @Category(UnitTests.class)
  public void testGetStepParametersClass() {
    assertThat(step.getStepParametersClass()).isEqualTo(StepBaseParameters.class);
  }
}
