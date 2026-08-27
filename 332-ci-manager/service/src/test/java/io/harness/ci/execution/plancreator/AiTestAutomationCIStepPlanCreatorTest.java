/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.plancreator;

import static io.harness.rule.OwnerRule.SARTHAK_DALMIA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.category.element.UnitTests;
import io.harness.ci.plancreator.AiTestAutomationCIStepPlanCreator;
import io.harness.rule.Owner;

import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.AI)
public class AiTestAutomationCIStepPlanCreatorTest extends CategoryTest {
  private AiTestAutomationCIStepPlanCreator planCreator;

  @Before
  public void setup() {
    planCreator = new AiTestAutomationCIStepPlanCreator();
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testGetSupportedStepTypes() {
    Set<String> supportedTypes = planCreator.getSupportedStepTypes();

    assertThat(supportedTypes).containsExactly(CIStepInfoType.AI_TEST_AUTOMATION.getDisplayName());
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testGetSupportedYamlVersions() {
    Set<String> versions = planCreator.getSupportedYamlVersions();

    assertThat(versions).containsExactly("0");
  }
}
