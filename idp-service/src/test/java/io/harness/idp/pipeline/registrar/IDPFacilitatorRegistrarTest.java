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
import io.harness.ci.execution.states.IntegrationStageStepPMSFacilitator;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.sdk.core.execution.events.node.facilitate.response.Facilitator;
import io.harness.rule.Owner;

import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class IDPFacilitatorRegistrarTest extends CategoryTest {
  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetEngineFacilitators() {
    Map<FacilitatorType, Class<? extends Facilitator>> facilitators = IDPFacilitatorRegistrar.getEngineFacilitators();

    assertNotNull(facilitators);
    assertEquals(1, facilitators.size());
    assertTrue(facilitators.containsKey(IntegrationStageStepPMSFacilitator.FACILITATOR_TYPE));
    assertEquals(IntegrationStageStepPMSFacilitator.class,
        facilitators.get(IntegrationStageStepPMSFacilitator.FACILITATOR_TYPE));
  }
}
