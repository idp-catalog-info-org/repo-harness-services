/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps.internal;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.steps.StepSpecTypeConstants;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.FME)
public class FmeFlagsetCreateNodeTest extends CategoryTest {
  @Test
  @Owner(developers = OwnerRule.ROHITPAL)
  @Category(UnitTests.class)
  public void testGetType() {
    FmeFlagsetCreateNode node = new FmeFlagsetCreateNode();
    String type = node.getType();
    assertThat(type).isEqualTo(StepSpecTypeConstants.FME_FLAGSET_CREATE_STEP_TYPE.getType());
  }

  @Test
  @Owner(developers = OwnerRule.ROHITPAL)
  @Category(UnitTests.class)
  public void testGetStepSpecType() {
    FmeFlagsetCreateNode node = new FmeFlagsetCreateNode();
    FmeFlagsetCreateInfo info =
        FmeFlagsetCreateInfo.builder().name(ParameterField.createValueField("test-flagset")).build();
    node.setFmeFlagsetCreateInfo(info);

    assertThat(node.getStepSpecType()).isEqualTo(info);
  }

  @Test
  @Owner(developers = OwnerRule.ROHITPAL)
  @Category(UnitTests.class)
  public void testDefaultStepType() {
    FmeFlagsetCreateNode node = new FmeFlagsetCreateNode();
    assertThat(node.getType()).isNotNull();
    assertThat(node.getType()).isEqualTo(StepSpecTypeConstants.FME_FLAGSET_CREATE_STEP_TYPE.getType());
  }
}
