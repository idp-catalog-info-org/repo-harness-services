/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps.internal;

import static io.harness.rule.OwnerRule.GONZALO;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.steps.StepSpecTypeConstants;

import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.FME)
public class FmeFlagPatchDefinitionStepVariableCreatorTest extends CategoryTest {
  FmeFlagPatchDefinitionStepVariableCreator variableCreator = new FmeFlagPatchDefinitionStepVariableCreator();

  @Test
  @Owner(developers = GONZALO)
  @Category(UnitTests.class)
  public void testGetSupportedStepTypes() {
    Set<String> supportedStepTypes = variableCreator.getSupportedStepTypes();
    assertThat(supportedStepTypes).hasSize(1);
    assertThat(supportedStepTypes).contains(StepSpecTypeConstants.FME_FLAG_PATCH_DEFINITION);
  }

  @Test
  @Owner(developers = GONZALO)
  @Category(UnitTests.class)
  public void testGetFieldClass() {
    assertThat(variableCreator.getFieldClass()).isEqualTo(FmeFlagPatchDefinitionNode.class);
  }
}
