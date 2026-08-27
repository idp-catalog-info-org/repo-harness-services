/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.fme;

import static io.harness.rule.OwnerRule.GONZALO;
import static io.harness.steps.StepUtils.PIE_SIMPLIFY_LOG_BASE_KEY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.fme.AllocationDTO;
import io.harness.fme.FMEPipelineClient;
import io.harness.fme.FeatureFlagDefinition;
import io.harness.fme.FmeResponse;
import io.harness.fme.TargetingRuleDTO;
import io.harness.fme.TargetingRulesDTO;
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
import io.harness.utils.PmsFeatureFlagHelper;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
public class FmeFlagSetTargetingRulesStepTest extends CategoryTest {
  @InjectMocks FmeFlagSetTargetingRulesStep fmeFlagSetTargetingRulesStep;
  private Ambiance ambiance;
  @Mock private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Mock private FMEPipelineClient fmePipelineClient;
  @Spy private FmeStepResponseBuilder fmeStepResponseBuilder;
  @Spy private ExceptionManager exceptionManager;

  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  private static final String FLAG_NAME = "testFlag";
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
    Mockito.when(pmsFeatureFlagHelper.isEnabled(anyString(), any(FeatureName.class))).thenReturn(false);

    // Inject ExceptionManager spy into FmeStepResponseBuilder spy
    fmeStepResponseBuilder.setExceptionManager(exceptionManager);
  }

  @Test
  @Owner(developers = GONZALO)
  @Category(UnitTests.class)
  public void testSetTargetingRulesSuccess() throws Exception {
    // Setup - create targeting rules with conditions and allocations
    Rule rule1 = Rule.builder()
                     .type(ParameterField.createValueField(RuleConditionType.IN_SEGMENT))
                     .negate(ParameterField.createValueField(false))
                     .attribute(ParameterField.createValueField("segment"))
                     .value(ParameterField.createValueField("beta_users"))
                     .build();

    Rule rule2 = Rule.builder()
                     .type(ParameterField.createValueField(RuleConditionType.EQUAL_TO_SEMVER))
                     .negate(ParameterField.createValueField(false))
                     .attribute(ParameterField.createValueField("version"))
                     .value(ParameterField.createValueField("1.0.0"))
                     .build();

    RuleCondition condition =
        RuleCondition.builder().rules(ParameterField.createValueField(Arrays.asList(rule1, rule2))).build();

    RuleAllocation allocation1 = RuleAllocation.builder()
                                     .treatment(ParameterField.createValueField("on"))
                                     .size(ParameterField.createValueField(80))
                                     .build();

    RuleAllocation allocation2 = RuleAllocation.builder()
                                     .treatment(ParameterField.createValueField("off"))
                                     .size(ParameterField.createValueField(20))
                                     .build();

    TargetRules targetRules = TargetRules.builder()
                                  .condition(ParameterField.createValueField(condition))
                                  .allocation(ParameterField.createValueField(Arrays.asList(allocation1, allocation2)))
                                  .build();

    List<TargetRules> targetingRules = Collections.singletonList(targetRules);

    FmeFlagSetTargetingRulesParameters stepParams = FmeFlagSetTargetingRulesParameters.builder()
                                                        .environment(ParameterField.createValueField(ENVIRONMENT))
                                                        .flagName(ParameterField.createValueField(FLAG_NAME))
                                                        .targetingRules(ParameterField.createValueField(targetingRules))
                                                        .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(stepParams);

    // Mock successful API response
    Call<FmeResponse<FeatureFlagDefinition>> updateCall = mock(Call.class);
    Response<FmeResponse<FeatureFlagDefinition>> updateResponse = mock(Response.class);
    when(updateCall.execute()).thenReturn(updateResponse);
    when(updateResponse.isSuccessful()).thenReturn(true);
    when(updateResponse.body()).thenReturn(FmeResponse.<FeatureFlagDefinition>builder().entity(null).build());

    ArgumentCaptor<List<TargetingRulesDTO>> ruleDtosCaptor = ArgumentCaptor.forClass(List.class);
    when(fmePipelineClient.updateFeatureFlagDefinitionRules(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(FLAG_NAME), eq(ENVIRONMENT), ruleDtosCaptor.capture()))
        .thenReturn(updateCall);

    // Execute
    StepResponse response = fmeFlagSetTargetingRulesStep.executeSyncAfterRbac(
        ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    // Verify response
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    // Verify the DTO conversion
    List<TargetingRulesDTO> capturedRuleDtos = ruleDtosCaptor.getValue();
    assertThat(capturedRuleDtos).hasSize(1);

    TargetingRulesDTO ruleDto = capturedRuleDtos.get(0);
    assertThat(ruleDto.getCondition()).isNotNull();
    assertThat(ruleDto.getCondition().getRules()).hasSize(2);
    assertThat(ruleDto.getAllocation()).hasSize(2);

    // Verify rules conversion
    TargetingRuleDTO ruleDto1 = ruleDto.getCondition().getRules().get(0);
    assertThat(ruleDto1.getType()).isEqualTo("IN_SEGMENT");
    assertThat(ruleDto1.getNegate()).isEqualTo(false);
    assertThat(ruleDto1.getAttribute()).isEqualTo("segment");
    assertThat(ruleDto1.getValue()).isEqualTo("beta_users");

    TargetingRuleDTO ruleDto2 = ruleDto.getCondition().getRules().get(1);
    assertThat(ruleDto2.getType()).isEqualTo("EQUAL_TO_SEMVER");
    assertThat(ruleDto2.getAttribute()).isEqualTo("version");
    assertThat(ruleDto2.getValue()).isEqualTo("1.0.0");

    // Verify allocations conversion
    AllocationDTO allocationDto1 = ruleDto.getAllocation().get(0);
    assertThat(allocationDto1.getTreatment()).isEqualTo("on");
    assertThat(allocationDto1.getSize()).isEqualTo(80);

    AllocationDTO allocationDto2 = ruleDto.getAllocation().get(1);
    assertThat(allocationDto2.getTreatment()).isEqualTo("off");
    assertThat(allocationDto2.getSize()).isEqualTo(20);
  }

  @Test
  @Owner(developers = GONZALO)
  @Category(UnitTests.class)
  public void testSetTargetingRulesWithNullCondition() throws Exception {
    // Setup - create targeting rules without conditions (only allocations)
    RuleAllocation allocation1 = RuleAllocation.builder()
                                     .treatment(ParameterField.createValueField("on"))
                                     .size(ParameterField.createValueField(50))
                                     .build();

    RuleAllocation allocation2 = RuleAllocation.builder()
                                     .treatment(ParameterField.createValueField("off"))
                                     .size(ParameterField.createValueField(50))
                                     .build();

    TargetRules targetRules = TargetRules.builder()
                                  .condition(ParameterField.ofNull())
                                  .allocation(ParameterField.createValueField(Arrays.asList(allocation1, allocation2)))
                                  .build();

    List<TargetRules> targetingRules = Collections.singletonList(targetRules);

    FmeFlagSetTargetingRulesParameters stepParams = FmeFlagSetTargetingRulesParameters.builder()
                                                        .environment(ParameterField.createValueField(ENVIRONMENT))
                                                        .flagName(ParameterField.createValueField(FLAG_NAME))
                                                        .targetingRules(ParameterField.createValueField(targetingRules))
                                                        .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(stepParams);

    // Mock successful API response
    Call<FmeResponse<FeatureFlagDefinition>> updateCall = mock(Call.class);
    Response<FmeResponse<FeatureFlagDefinition>> updateResponse = mock(Response.class);
    when(updateCall.execute()).thenReturn(updateResponse);
    when(updateResponse.isSuccessful()).thenReturn(true);
    when(updateResponse.body()).thenReturn(FmeResponse.<FeatureFlagDefinition>builder().entity(null).build());

    ArgumentCaptor<List<TargetingRulesDTO>> ruleDtosCaptor = ArgumentCaptor.forClass(List.class);
    when(fmePipelineClient.updateFeatureFlagDefinitionRules(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(FLAG_NAME), eq(ENVIRONMENT), ruleDtosCaptor.capture()))
        .thenReturn(updateCall);

    // Execute
    StepResponse response = fmeFlagSetTargetingRulesStep.executeSyncAfterRbac(
        ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    // Verify
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    List<TargetingRulesDTO> capturedRuleDtos = ruleDtosCaptor.getValue();
    assertThat(capturedRuleDtos).hasSize(1);

    TargetingRulesDTO ruleDto = capturedRuleDtos.get(0);
    assertThat(ruleDto.getCondition()).isNull();
    assertThat(ruleDto.getAllocation()).hasSize(2);
  }

  @Test
  @Owner(developers = GONZALO)
  @Category(UnitTests.class)
  public void testSetTargetingRulesWithFeatureFlagCondition() throws Exception {
    // Setup - create rule with IN_SPLIT type that references another feature flag
    Rule rule = Rule.builder()
                    .type(ParameterField.createValueField(RuleConditionType.IN_SPLIT))
                    .negate(ParameterField.createValueField(true))
                    .featureFlag(ParameterField.createValueField("parent_flag"))
                    .build();

    RuleCondition condition =
        RuleCondition.builder().rules(ParameterField.createValueField(Collections.singletonList(rule))).build();

    RuleAllocation allocation = RuleAllocation.builder()
                                    .treatment(ParameterField.createValueField("on"))
                                    .size(ParameterField.createValueField(100))
                                    .build();

    TargetRules targetRules = TargetRules.builder()
                                  .condition(ParameterField.createValueField(condition))
                                  .allocation(ParameterField.createValueField(Collections.singletonList(allocation)))
                                  .build();

    List<TargetRules> targetingRules = Collections.singletonList(targetRules);

    FmeFlagSetTargetingRulesParameters stepParams = FmeFlagSetTargetingRulesParameters.builder()
                                                        .environment(ParameterField.createValueField(ENVIRONMENT))
                                                        .flagName(ParameterField.createValueField(FLAG_NAME))
                                                        .targetingRules(ParameterField.createValueField(targetingRules))
                                                        .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(stepParams);

    Call<FmeResponse<FeatureFlagDefinition>> updateCall = mock(Call.class);
    Response<FmeResponse<FeatureFlagDefinition>> updateResponse = mock(Response.class);
    when(updateCall.execute()).thenReturn(updateResponse);
    when(updateResponse.isSuccessful()).thenReturn(true);
    when(updateResponse.body()).thenReturn(FmeResponse.<FeatureFlagDefinition>builder().entity(null).build());

    ArgumentCaptor<List<TargetingRulesDTO>> ruleDtosCaptor = ArgumentCaptor.forClass(List.class);
    when(fmePipelineClient.updateFeatureFlagDefinitionRules(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(FLAG_NAME), eq(ENVIRONMENT), ruleDtosCaptor.capture()))
        .thenReturn(updateCall);

    // Execute
    StepResponse response = fmeFlagSetTargetingRulesStep.executeSyncAfterRbac(
        ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    // Verify
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    List<TargetingRulesDTO> capturedRuleDtos = ruleDtosCaptor.getValue();
    TargetingRuleDTO ruleDto = capturedRuleDtos.get(0).getCondition().getRules().get(0);

    assertThat(ruleDto.getType()).isEqualTo("IN_SPLIT");
    assertThat(ruleDto.getNegate()).isEqualTo(true);
    assertThat(ruleDto.getFeatureFlag()).isEqualTo("parent_flag");
  }

  @Test
  @Owner(developers = GONZALO)
  @Category(UnitTests.class)
  public void testSetTargetingRulesMultipleRules() throws Exception {
    // Setup - multiple target rules
    RuleAllocation allocation1 = RuleAllocation.builder()
                                     .treatment(ParameterField.createValueField("on"))
                                     .size(ParameterField.createValueField(100))
                                     .build();

    TargetRules targetRule1 = TargetRules.builder()
                                  .condition(ParameterField.ofNull())
                                  .allocation(ParameterField.createValueField(Collections.singletonList(allocation1)))
                                  .build();

    RuleAllocation allocation2 = RuleAllocation.builder()
                                     .treatment(ParameterField.createValueField("off"))
                                     .size(ParameterField.createValueField(100))
                                     .build();

    TargetRules targetRule2 = TargetRules.builder()
                                  .condition(ParameterField.ofNull())
                                  .allocation(ParameterField.createValueField(Collections.singletonList(allocation2)))
                                  .build();

    List<TargetRules> targetingRules = Arrays.asList(targetRule1, targetRule2);

    FmeFlagSetTargetingRulesParameters stepParams = FmeFlagSetTargetingRulesParameters.builder()
                                                        .environment(ParameterField.createValueField(ENVIRONMENT))
                                                        .flagName(ParameterField.createValueField(FLAG_NAME))
                                                        .targetingRules(ParameterField.createValueField(targetingRules))
                                                        .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(stepParams);

    Call<FmeResponse<FeatureFlagDefinition>> updateCall = mock(Call.class);
    Response<FmeResponse<FeatureFlagDefinition>> updateResponse = mock(Response.class);
    when(updateCall.execute()).thenReturn(updateResponse);
    when(updateResponse.isSuccessful()).thenReturn(true);
    when(updateResponse.body()).thenReturn(FmeResponse.<FeatureFlagDefinition>builder().entity(null).build());

    ArgumentCaptor<List<TargetingRulesDTO>> ruleDtosCaptor = ArgumentCaptor.forClass(List.class);
    when(fmePipelineClient.updateFeatureFlagDefinitionRules(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(FLAG_NAME), eq(ENVIRONMENT), ruleDtosCaptor.capture()))
        .thenReturn(updateCall);

    // Execute
    StepResponse response = fmeFlagSetTargetingRulesStep.executeSyncAfterRbac(
        ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    // Verify
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    List<TargetingRulesDTO> capturedRuleDtos = ruleDtosCaptor.getValue();
    assertThat(capturedRuleDtos).hasSize(2);
  }

  @Test
  @Owner(developers = GONZALO)
  @Category(UnitTests.class)
  public void testExecuteSyncAfterRbacFailureOnUpdate() throws Exception {
    RuleAllocation allocation = RuleAllocation.builder()
                                    .treatment(ParameterField.createValueField("on"))
                                    .size(ParameterField.createValueField(100))
                                    .build();

    TargetRules targetRules = TargetRules.builder()
                                  .condition(ParameterField.ofNull())
                                  .allocation(ParameterField.createValueField(Collections.singletonList(allocation)))
                                  .build();

    FmeFlagSetTargetingRulesParameters stepParams =
        FmeFlagSetTargetingRulesParameters.builder()
            .environment(ParameterField.createValueField(ENVIRONMENT))
            .flagName(ParameterField.createValueField(FLAG_NAME))
            .targetingRules(ParameterField.createValueField(Collections.singletonList(targetRules)))
            .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(stepParams);

    // Mock error response
    Call<FmeResponse<FeatureFlagDefinition>> updateCall = mock(Call.class);
    Response<FmeResponse<FeatureFlagDefinition>> updateResponse = mock(Response.class);
    when(updateCall.execute()).thenReturn(updateResponse);
    when(updateResponse.isSuccessful()).thenReturn(false);
    when(updateResponse.code()).thenReturn(500);
    when(updateResponse.errorBody())
        .thenReturn(ResponseBody.create(MediaType.parse("application/json"), "{\"error\": \"Internal server error\"}"));

    when(fmePipelineClient.updateFeatureFlagDefinitionRules(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(FLAG_NAME), eq(ENVIRONMENT), any()))
        .thenReturn(updateCall);

    // Execute
    StepResponse response = fmeFlagSetTargetingRulesStep.executeSyncAfterRbac(
        ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    // Verify failure
    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
    assertThat(response.getFailureInfo()).isNotNull();
  }

  @Test
  @Owner(developers = GONZALO)
  @Category(UnitTests.class)
  public void testGetStepType() {
    assertThat(fmeFlagSetTargetingRulesStep.STEP_TYPE)
        .isEqualTo(StepSpecTypeConstants.FME_FLAG_SET_TARGETING_RULES_STEP_TYPE);
  }
}
