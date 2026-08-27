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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.fme.FMEPipelineClient;
import io.harness.fme.MatcherExternalDTO;
import io.harness.fme.RuleBasedSegmentExternalDTO;
import io.harness.fme.SegmentRuleExternalDTO;
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

import java.util.Arrays;
import java.util.Collections;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
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
public class FmeSegmentSetTargetingRulesStepTest extends CategoryTest {
  @InjectMocks FmeSegmentSetTargetingRulesStep step;
  private Ambiance ambiance;
  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Mock private FMEPipelineClient fmePipelineClient;
  @Spy private FmeStepResponseBuilder fmeStepResponseBuilder;
  @Spy private ExceptionManager exceptionManager;

  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  private static final String SEGMENT_NAME = "high-value-users";
  private static final String ENVIRONMENT = "production";

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

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testExecuteSuccess() throws Exception {
    SegmentTargetRules segmentRule =
        SegmentTargetRules.builder()
            .condition(ParameterField.createValueField(
                RuleCondition.builder()
                    .rules(ParameterField.createValueField(Collections.singletonList(
                        Rule.builder()
                            .type(ParameterField.createValueField(RuleConditionType.GREATER_THAN_OR_EQUAL_NUMBER))
                            .attribute(ParameterField.createValueField("age"))
                            .value(ParameterField.createValueField(25))
                            .build())))
                    .build()))
            .build();
    FmeSegmentSetTargetingRulesParameters params =
        FmeSegmentSetTargetingRulesParameters.builder()
            .segmentName(ParameterField.createValueField(SEGMENT_NAME))
            .environment(ParameterField.createValueField(ENVIRONMENT))
            .rules(ParameterField.createValueField(Collections.singletonList(segmentRule)))
            .build();
    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    Call<Object> updateCall = mock(Call.class);
    Response<Object> updateResponse = mock(Response.class);
    when(updateCall.execute()).thenReturn(updateResponse);
    when(updateResponse.isSuccessful()).thenReturn(true);

    ArgumentCaptor<RuleBasedSegmentExternalDTO> payloadCaptor =
        ArgumentCaptor.forClass(RuleBasedSegmentExternalDTO.class);
    when(fmePipelineClient.updateSegmentRules(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), payloadCaptor.capture()))
        .thenReturn(updateCall);

    StepResponse response =
        step.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    RuleBasedSegmentExternalDTO captured = payloadCaptor.getValue();
    assertThat(captured.getName()).isEqualTo(SEGMENT_NAME);
    assertThat(captured.getEnvironment()).isEqualTo(ENVIRONMENT);
    assertThat(captured.getOrgId()).isNull();
    assertThat(captured.getRules()).hasSize(1);

    SegmentRuleExternalDTO ruleDto = captured.getRules().get(0);
    assertThat(ruleDto.getCondition()).isNotNull();
    assertThat(ruleDto.getCondition().getCombiner()).isEqualTo("AND");
    assertThat(ruleDto.getCondition().getMatchers()).hasSize(1);

    MatcherExternalDTO matcher = ruleDto.getCondition().getMatchers().get(0);
    assertThat(matcher.getType()).isEqualTo("GREATER_THAN_OR_EQUAL_NUMBER");
    assertThat(matcher.getAttribute()).isEqualTo("age");
    assertThat(matcher.getNumber()).isEqualTo(25L);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testExecuteSuccessWithMinimalParams() throws Exception {
    FmeSegmentSetTargetingRulesParameters params = FmeSegmentSetTargetingRulesParameters.builder()
                                                       .segmentName(ParameterField.createValueField(SEGMENT_NAME))
                                                       .environment(ParameterField.createValueField(ENVIRONMENT))
                                                       .build();
    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    Call<Object> updateCall = mock(Call.class);
    Response<Object> updateResponse = mock(Response.class);
    when(updateCall.execute()).thenReturn(updateResponse);
    when(updateResponse.isSuccessful()).thenReturn(true);

    ArgumentCaptor<RuleBasedSegmentExternalDTO> payloadCaptor =
        ArgumentCaptor.forClass(RuleBasedSegmentExternalDTO.class);
    when(fmePipelineClient.updateSegmentRules(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), payloadCaptor.capture()))
        .thenReturn(updateCall);

    StepResponse response =
        step.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    RuleBasedSegmentExternalDTO captured = payloadCaptor.getValue();
    assertThat(captured.getName()).isEqualTo(SEGMENT_NAME);
    assertThat(captured.getEnvironment()).isEqualTo(ENVIRONMENT);
    assertThat(captured.getOrgId()).isNull();
    assertThat(captured.getRules()).isEmpty();
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testExecuteSuccessWithBooleanMatcher() throws Exception {
    SegmentTargetRules segmentRule =
        SegmentTargetRules.builder()
            .condition(ParameterField.createValueField(
                RuleCondition.builder()
                    .rules(ParameterField.createValueField(
                        Collections.singletonList(Rule.builder()
                                                      .type(ParameterField.createValueField(RuleConditionType.BOOLEAN))
                                                      .attribute(ParameterField.createValueField("premium"))
                                                      .value(ParameterField.createValueField(true))
                                                      .build())))
                    .build()))
            .build();
    FmeSegmentSetTargetingRulesParameters params =
        FmeSegmentSetTargetingRulesParameters.builder()
            .segmentName(ParameterField.createValueField(SEGMENT_NAME))
            .environment(ParameterField.createValueField(ENVIRONMENT))
            .rules(ParameterField.createValueField(Collections.singletonList(segmentRule)))
            .build();
    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    Call<Object> updateCall = mock(Call.class);
    Response<Object> updateResponse = mock(Response.class);
    when(updateCall.execute()).thenReturn(updateResponse);
    when(updateResponse.isSuccessful()).thenReturn(true);

    ArgumentCaptor<RuleBasedSegmentExternalDTO> payloadCaptor =
        ArgumentCaptor.forClass(RuleBasedSegmentExternalDTO.class);
    when(fmePipelineClient.updateSegmentRules(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), payloadCaptor.capture()))
        .thenReturn(updateCall);

    StepResponse response =
        step.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    MatcherExternalDTO matcher = payloadCaptor.getValue().getRules().get(0).getCondition().getMatchers().get(0);
    assertThat(matcher.getType()).isEqualTo("BOOLEAN");
    assertThat(matcher.getAttribute()).isEqualTo("premium");
    assertThat(matcher.getBool()).isEqualTo(true);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testExecuteSuccessWithStringListMatcher() throws Exception {
    SegmentTargetRules segmentRule =
        SegmentTargetRules.builder()
            .condition(ParameterField.createValueField(
                RuleCondition.builder()
                    .rules(ParameterField.createValueField(Collections.singletonList(
                        Rule.builder()
                            .type(ParameterField.createValueField(RuleConditionType.EQUAL_SET))
                            .attribute(ParameterField.createValueField("country"))
                            .value(ParameterField.createValueField(Arrays.asList("US", "UK", "CA")))
                            .build())))
                    .build()))
            .build();
    FmeSegmentSetTargetingRulesParameters params =
        FmeSegmentSetTargetingRulesParameters.builder()
            .segmentName(ParameterField.createValueField(SEGMENT_NAME))
            .environment(ParameterField.createValueField(ENVIRONMENT))
            .rules(ParameterField.createValueField(Collections.singletonList(segmentRule)))
            .build();
    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    Call<Object> updateCall = mock(Call.class);
    Response<Object> updateResponse = mock(Response.class);
    when(updateCall.execute()).thenReturn(updateResponse);
    when(updateResponse.isSuccessful()).thenReturn(true);

    ArgumentCaptor<RuleBasedSegmentExternalDTO> payloadCaptor =
        ArgumentCaptor.forClass(RuleBasedSegmentExternalDTO.class);
    when(fmePipelineClient.updateSegmentRules(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), payloadCaptor.capture()))
        .thenReturn(updateCall);

    StepResponse response =
        step.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    MatcherExternalDTO matcher = payloadCaptor.getValue().getRules().get(0).getCondition().getMatchers().get(0);
    assertThat(matcher.getType()).isEqualTo("EQUAL_SET");
    assertThat(matcher.getStrings()).containsExactly("US", "UK", "CA");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testExecuteSuccessWithExcludeKeysAndSegments() throws Exception {
    FmeSegmentSetTargetingRulesParameters params =
        FmeSegmentSetTargetingRulesParameters.builder()
            .segmentName(ParameterField.createValueField(SEGMENT_NAME))
            .environment(ParameterField.createValueField(ENVIRONMENT))
            .excludeKeys(ParameterField.createValueField(Arrays.asList("user_1", "user_2")))
            .excludeSegments(ParameterField.createValueField(Arrays.asList("beta-testers", "internal-users")))
            .build();
    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    Call<Object> updateCall = mock(Call.class);
    Response<Object> updateResponse = mock(Response.class);
    when(updateCall.execute()).thenReturn(updateResponse);
    when(updateResponse.isSuccessful()).thenReturn(true);

    ArgumentCaptor<RuleBasedSegmentExternalDTO> payloadCaptor =
        ArgumentCaptor.forClass(RuleBasedSegmentExternalDTO.class);
    when(fmePipelineClient.updateSegmentRules(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), payloadCaptor.capture()))
        .thenReturn(updateCall);

    StepResponse response =
        step.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    RuleBasedSegmentExternalDTO captured = payloadCaptor.getValue();
    assertThat(captured.getName()).isEqualTo(SEGMENT_NAME);
    assertThat(captured.getEnvironment()).isEqualTo(ENVIRONMENT);
    assertThat(captured.getExcludedKeys()).containsExactly("user_1", "user_2");
    assertThat(captured.getExcludedSegments()).containsExactly("beta-testers", "internal-users");
    assertThat(captured.getRules()).isEmpty();
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testExecuteMissingSegmentName() {
    FmeSegmentSetTargetingRulesParameters params = FmeSegmentSetTargetingRulesParameters.builder()
                                                       .segmentName(ParameterField.createValueField(null))
                                                       .environment(ParameterField.createValueField("env"))
                                                       .build();
    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    StepResponse response =
        step.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testExecuteMissingEnvironment() {
    FmeSegmentSetTargetingRulesParameters params = FmeSegmentSetTargetingRulesParameters.builder()
                                                       .segmentName(ParameterField.createValueField("seg"))
                                                       .environment(ParameterField.createValueField(null))
                                                       .build();
    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    StepResponse response =
        step.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testExecuteApiFailure() throws Exception {
    FmeSegmentSetTargetingRulesParameters params = FmeSegmentSetTargetingRulesParameters.builder()
                                                       .segmentName(ParameterField.createValueField(SEGMENT_NAME))
                                                       .environment(ParameterField.createValueField(ENVIRONMENT))
                                                       .build();
    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    Call<Object> updateCall = mock(Call.class);
    Response<Object> updateResponse = mock(Response.class);
    when(updateCall.execute()).thenReturn(updateResponse);
    when(updateResponse.isSuccessful()).thenReturn(false);
    when(updateResponse.code()).thenReturn(404);
    when(updateResponse.errorBody())
        .thenReturn(ResponseBody.create(MediaType.parse("application/json"), "{\"error\": \"Segment not found\"}"));

    when(fmePipelineClient.updateSegmentRules(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), any()))
        .thenReturn(updateCall);

    StepResponse response =
        step.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
    assertThat(response.getFailureInfo()).isNotNull();
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testGetStepType() {
    assertThat(FmeSegmentSetTargetingRulesStep.STEP_TYPE).isNotNull();
  }
}
