/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.pipeline.steps;

import static io.harness.rule.OwnerRule.KOTA_KARTHIK;

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.stepstatus.StepMapOutput;
import io.harness.delegate.task.stepstatus.StepStatus;
import io.harness.idp.onboarding.service.OnboardingService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.rule.Owner;

import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class IdpRegisterCatalogStepTest extends CategoryTest {
  @Mock private OnboardingService onboardingService;
  @Mock private StepStatus stepStatus;
  @Mock private StepMapOutput stepMapOutput;
  @Mock private Ambiance ambiance;

  private IdpRegisterCatalogStep idpRegisterCatalogStep;

  private static final String TEST_CATALOG_URL = "https://github.com/test/catalog.yaml";
  private static final String TEST_ACCOUNT_ID = "test-account-id";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    idpRegisterCatalogStep = new IdpRegisterCatalogStep();
    idpRegisterCatalogStep.onboardingService = onboardingService;

    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(IdpRegisterCatalogStep.ACCOUNT_ID_KEY, TEST_ACCOUNT_ID);
    when(ambiance.getSetupAbstractionsMap()).thenReturn(setupAbstractions);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testShouldPublishArtifact() {
    assertTrue(idpRegisterCatalogStep.shouldPublishArtifact(stepStatus));
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testShouldPublishOutcome() {
    assertTrue(idpRegisterCatalogStep.shouldPublishOutcome(stepStatus));
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testModifyStepStatus_WithCatalogUrl() {
    Map<String, String> outputMap = new HashMap<>();
    outputMap.put(IdpRegisterCatalogStep.CATALOG_URL_KEY, TEST_CATALOG_URL);

    when(stepStatus.getOutput()).thenReturn(stepMapOutput);
    when(stepMapOutput.getMap()).thenReturn(outputMap);

    idpRegisterCatalogStep.modifyStepStatus(ambiance, stepStatus, "test-step");

    verify(onboardingService).registerLocationInBackstage(eq(TEST_ACCOUNT_ID), anyString(), anyList());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testModifyStepStatus_WithoutCatalogUrl() {
    Map<String, String> outputMap = new HashMap<>();

    when(stepStatus.getOutput()).thenReturn(stepMapOutput);
    when(stepMapOutput.getMap()).thenReturn(outputMap);

    idpRegisterCatalogStep.modifyStepStatus(ambiance, stepStatus, "test-step");

    verify(onboardingService, never()).registerLocationInBackstage(anyString(), anyString(), anyList());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testModifyStepStatus_NullOutput() {
    when(stepStatus.getOutput()).thenReturn(null);

    idpRegisterCatalogStep.modifyStepStatus(ambiance, stepStatus, "test-step");

    verify(onboardingService, never()).registerLocationInBackstage(anyString(), anyString(), anyList());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testModifyStepStatus_NullMap() {
    when(stepStatus.getOutput()).thenReturn(stepMapOutput);
    when(stepMapOutput.getMap()).thenReturn(null);

    idpRegisterCatalogStep.modifyStepStatus(ambiance, stepStatus, "test-step");

    verify(onboardingService, never()).registerLocationInBackstage(anyString(), anyString(), anyList());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetOutputVariableStepOutcome_WithCatalogUrl() {
    Map<String, String> outputVariables = new HashMap<>();
    outputVariables.put(IdpRegisterCatalogStep.CATALOG_URL_KEY, TEST_CATALOG_URL);

    StepResponse.StepOutcome outcome = idpRegisterCatalogStep.getOutputVariableStepOutcome(ambiance, outputVariables);

    assertNotNull(outcome);
    verify(onboardingService).registerLocationInBackstage(eq(TEST_ACCOUNT_ID), anyString(), anyList());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetOutputVariableStepOutcome_WithoutCatalogUrl() {
    Map<String, String> outputVariables = new HashMap<>();

    StepResponse.StepOutcome outcome = idpRegisterCatalogStep.getOutputVariableStepOutcome(ambiance, outputVariables);

    assertNotNull(outcome);
    verify(onboardingService, never()).registerLocationInBackstage(anyString(), anyString(), anyList());
  }
}
