/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event.preStepCheckObserver;

import static io.harness.rule.OwnerRule.NAVNEET_KHANDELWAL;
import static io.harness.rule.OwnerRule.SHASHANK_JAIN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.authorization.AuthorizationServiceHeader;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.plan.service.PlanExecutionMetadataService;
import io.harness.engine.facilitation.facilitator.FacilitatorMetadata;
import io.harness.engine.governance.PolicyEvaluationFailureException;
import io.harness.exception.InvalidRequestException;
import io.harness.opaclient.OpaServiceClientHelper;
import io.harness.opaclient.model.OpaConstants;
import io.harness.opaclient.model.OpaEvaluationResponseHolder;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.pms.contracts.plan.PrincipalType;
import io.harness.pms.data.stepparameters.PmsStepParameters;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.rule.Owner;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.security.dto.ServicePrincipal;
import io.harness.security.dto.UserPrincipal;
import io.harness.utils.PolicyEvalUtils;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class PreStepCheckPolicyEvaluationHandlerTest extends CategoryTest {
  private MockedStatic<PolicyEvalUtils> mockedStatic;
  InvalidRequestException invalidRequestException;
  @InjectMocks @Spy private PreStepCheckPolicyEvaluationHandler preStepCheckPolicyEvaluationHandler;
  @Mock PlanExecutionMetadataService planExecutionMetadataService;
  @Mock OpaServiceClientHelper opaServiceClientHelper;
  private FacilitatorMetadata facilitatorMetadata;
  private Ambiance ambiance;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    facilitatorMetadata = FacilitatorMetadata.builder()
                              .mode(ExecutionMode.PRE_STEP_CHECK)
                              .resolvedParams(new PmsStepParameters())
                              .build();

    ambiance = Ambiance.newBuilder()
                   .setMetadata(ExecutionMetadata.newBuilder()
                                    .putFeatureFlagToValueMap(OpaConstants.PIPE_IS_ON_STEP_START_POLICY_PRESENT, true)
                                    .build())
                   .build();
    MockitoAnnotations.openMocks(this);
    mockedStatic = Mockito.mockStatic(PolicyEvalUtils.class);
    invalidRequestException = new InvalidRequestException("Failure");
  }

  @After
  public void tearDown() {
    if (mockedStatic != null) {
      mockedStatic.close();
    }
    SecurityContextBuilder.unsetCompleteContext();
  }

  @Test
  @Owner(developers = SHASHANK_JAIN)
  @Category(UnitTests.class)
  public void success() {
    OpaEvaluationResponseHolder opaEvaluationResponseHolder =
        OpaEvaluationResponseHolder.builder().id("1").status("pass").build();
    when(opaServiceClientHelper.evaluateWithCredentialsWithRetry(
             any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(opaEvaluationResponseHolder);
    doNothing().when(planExecutionMetadataService).updateEvaluatedPolicyIds(anyString(), anyList());
    try {
      preStepCheckPolicyEvaluationHandler.onPreStepCheck(ambiance, facilitatorMetadata);
    } catch (Exception ignored) {
      Assert.fail("Should not throw exception");
    }
  }

  @Test
  @Owner(developers = SHASHANK_JAIN)
  @Category(UnitTests.class)
  public void testOpaResponseInvalidRequestException() {
    when(opaServiceClientHelper.evaluateWithCredentialsWithRetry(
             any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenThrow(invalidRequestException);
    doNothing().when(planExecutionMetadataService).updateEvaluatedPolicyIds(anyString(), anyList());
    try {
      preStepCheckPolicyEvaluationHandler.onPreStepCheck(ambiance, facilitatorMetadata);
    } catch (InvalidRequestException ignored) {
      System.out.println("Inside catch block of invalid request exception");
      return;
    }
    Assert.fail("Method expected to throw invalid request exception");
  }

  @Test
  @Owner(developers = SHASHANK_JAIN)
  @Category(UnitTests.class)
  public void testOpaResponsePolicyEvaluationFailureException() {
    OpaEvaluationResponseHolder opaEvaluationResponseHolder =
        OpaEvaluationResponseHolder.builder().id("1").status("error").build();
    when(opaServiceClientHelper.evaluateWithCredentialsWithRetry(
             any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(opaEvaluationResponseHolder);
    mockedStatic.when(() -> PolicyEvalUtils.buildPolicyEvaluationFailureMessage(any())).thenReturn("Failure");
    doNothing().when(planExecutionMetadataService).updateEvaluatedPolicyIds(anyString(), anyList());
    try {
      preStepCheckPolicyEvaluationHandler.onPreStepCheck(ambiance, facilitatorMetadata);
    } catch (PolicyEvaluationFailureException ignored) {
      System.out.println("Inside catch block of policy evaluation failure exception");
      return;
    }
    Assert.fail("Method expected to throw policy evaluation failure exception");
  }

  @Test
  @Owner(developers = NAVNEET_KHANDELWAL)
  @Category(UnitTests.class)
  public void testOpaCallCarriesServicePrincipalWhenAmbianceSkipsRbac() {
    OpaEvaluationResponseHolder opaEvaluationResponseHolder =
        OpaEvaluationResponseHolder.builder().id("1").status("pass").build();
    AtomicReference<Principal> principalDuringCall = new AtomicReference<>();
    when(opaServiceClientHelper.evaluateWithCredentialsWithRetry(
             any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenAnswer(invocation -> {
          principalDuringCall.set(SecurityContextBuilder.getPrincipal());
          return opaEvaluationResponseHolder;
        });
    doNothing().when(planExecutionMetadataService).updateEvaluatedPolicyIds(anyString(), anyList());

    preStepCheckPolicyEvaluationHandler.onPreStepCheck(ambiance, facilitatorMetadata);

    assertThat(principalDuringCall.get())
        .isEqualTo(new ServicePrincipal(AuthorizationServiceHeader.PIPELINE_SERVICE.getServiceId()));
    assertThat(SecurityContextBuilder.getPrincipal()).isNull();
    assertThat(SourcePrincipalContextBuilder.getSourcePrincipal()).isNull();
  }

  @Test
  @Owner(developers = NAVNEET_KHANDELWAL)
  @Category(UnitTests.class)
  public void testOpaCallCarriesUserPrincipalFromAmbiance() {
    Ambiance userAmbiance =
        Ambiance.newBuilder()
            .putSetupAbstractions(SetupAbstractionKeys.accountId, "accountId")
            .setMetadata(ExecutionMetadata.newBuilder()
                             .putFeatureFlagToValueMap(OpaConstants.PIPE_IS_ON_STEP_START_POLICY_PRESENT, true)
                             .setPrincipalInfo(ExecutionPrincipalInfo.newBuilder()
                                                   .setPrincipal("userId")
                                                   .setPrincipalType(PrincipalType.USER)
                                                   .setShouldValidateRbac(true)
                                                   .build())
                             .build())
            .build();
    OpaEvaluationResponseHolder opaEvaluationResponseHolder =
        OpaEvaluationResponseHolder.builder().id("1").status("pass").build();
    AtomicReference<Principal> principalDuringCall = new AtomicReference<>();
    when(opaServiceClientHelper.evaluateWithCredentialsWithRetry(
             any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenAnswer(invocation -> {
          principalDuringCall.set(SecurityContextBuilder.getPrincipal());
          return opaEvaluationResponseHolder;
        });
    doNothing().when(planExecutionMetadataService).updateEvaluatedPolicyIds(anyString(), anyList());

    preStepCheckPolicyEvaluationHandler.onPreStepCheck(userAmbiance, facilitatorMetadata);

    assertThat(principalDuringCall.get()).isInstanceOf(UserPrincipal.class);
    assertThat(principalDuringCall.get().getName()).isEqualTo("userId");
    assertThat(SecurityContextBuilder.getPrincipal()).isNull();
    assertThat(SourcePrincipalContextBuilder.getSourcePrincipal()).isNull();
  }
}