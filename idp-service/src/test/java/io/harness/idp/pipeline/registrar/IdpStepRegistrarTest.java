/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.pipeline.registrar;

import static io.harness.rule.OwnerRule.KOTA_KARTHIK;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ci.execution.states.InitializeTaskStep;
import io.harness.ci.states.V1.InitializeTaskStepV2;
import io.harness.idp.pipeline.stages.step.IDPStageStepPMS;
import io.harness.idp.pipeline.steps.ActionStep;
import io.harness.idp.pipeline.steps.IdpCookieCutterStep;
import io.harness.idp.pipeline.steps.IdpCreateCatalogStep;
import io.harness.idp.pipeline.steps.IdpCreateOrganisationStep;
import io.harness.idp.pipeline.steps.IdpCreateProjectStep;
import io.harness.idp.pipeline.steps.IdpCreateRepoStep;
import io.harness.idp.pipeline.steps.IdpCreateResourceStep;
import io.harness.idp.pipeline.steps.IdpDirectPushStep;
import io.harness.idp.pipeline.steps.IdpRegisterCatalogStep;
import io.harness.idp.pipeline.steps.IdpUpdateCatalogPropertyStep;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.Step;
import io.harness.rule.Owner;
import io.harness.steps.executions.executable.IdpSlackNotifyStep;

import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class IdpStepRegistrarTest extends CategoryTest {
  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetEngineSteps() {
    Map<StepType, Class<? extends Step>> steps = IdpStepRegistrar.getEngineSteps();

    assertNotNull(steps);
    assertTrue(steps.size() > 0);

    // Verify key IDP steps are registered
    assertTrue(steps.containsKey(InitializeTaskStep.STEP_TYPE));
    assertEquals(InitializeTaskStepV2.class, steps.get(InitializeTaskStep.STEP_TYPE));

    assertTrue(steps.containsKey(IDPStageStepPMS.STEP_TYPE));
    assertEquals(IDPStageStepPMS.class, steps.get(IDPStageStepPMS.STEP_TYPE));

    assertTrue(steps.containsKey(ActionStep.STEP_TYPE));
    assertEquals(ActionStep.class, steps.get(ActionStep.STEP_TYPE));

    assertTrue(steps.containsKey(IdpCookieCutterStep.STEP_TYPE));
    assertEquals(IdpCookieCutterStep.class, steps.get(IdpCookieCutterStep.STEP_TYPE));

    assertTrue(steps.containsKey(IdpCreateRepoStep.STEP_TYPE));
    assertEquals(IdpCreateRepoStep.class, steps.get(IdpCreateRepoStep.STEP_TYPE));

    assertTrue(steps.containsKey(IdpDirectPushStep.STEP_TYPE));
    assertEquals(IdpDirectPushStep.class, steps.get(IdpDirectPushStep.STEP_TYPE));

    assertTrue(steps.containsKey(IdpRegisterCatalogStep.STEP_TYPE));
    assertEquals(IdpRegisterCatalogStep.class, steps.get(IdpRegisterCatalogStep.STEP_TYPE));

    assertTrue(steps.containsKey(IdpCreateCatalogStep.STEP_TYPE));
    assertEquals(IdpCreateCatalogStep.class, steps.get(IdpCreateCatalogStep.STEP_TYPE));

    assertTrue(steps.containsKey(IdpSlackNotifyStep.STEP_TYPE));
    assertEquals(IdpSlackNotifyStep.class, steps.get(IdpSlackNotifyStep.STEP_TYPE));

    assertTrue(steps.containsKey(IdpCreateOrganisationStep.STEP_TYPE));
    assertEquals(IdpCreateOrganisationStep.class, steps.get(IdpCreateOrganisationStep.STEP_TYPE));

    assertTrue(steps.containsKey(IdpCreateProjectStep.STEP_TYPE));
    assertEquals(IdpCreateProjectStep.class, steps.get(IdpCreateProjectStep.STEP_TYPE));

    assertTrue(steps.containsKey(IdpCreateResourceStep.STEP_TYPE));
    assertEquals(IdpCreateResourceStep.class, steps.get(IdpCreateResourceStep.STEP_TYPE));

    assertTrue(steps.containsKey(IdpUpdateCatalogPropertyStep.STEP_TYPE));
    assertEquals(IdpUpdateCatalogPropertyStep.class, steps.get(IdpUpdateCatalogPropertyStep.STEP_TYPE));
  }
}
