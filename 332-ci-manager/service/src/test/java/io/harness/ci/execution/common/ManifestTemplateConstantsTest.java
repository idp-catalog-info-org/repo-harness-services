/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.common;

import static io.harness.rule.OwnerRule.CHIRAG_S;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ManifestTemplateConstantsTest {
  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testManifestOutputKeys() {
    assertThat(ManifestTemplateConstants.PRIMARY).isEqualTo("primary");
    assertThat(ManifestTemplateConstants.OVERRIDES).isEqualTo("overrides");
    assertThat(ManifestTemplateConstants.TO_RENDER).isEqualTo("toRender");
    assertThat(ManifestTemplateConstants.TO_TEMPLATE).isEqualTo("toTemplate");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testInputsKeys() {
    assertThat(ManifestTemplateConstants.INPUTS_KEY_PATHS).isEqualTo("paths");
    assertThat(ManifestTemplateConstants.INPUTS_KEY_OVERRIDES).isEqualTo("overrides");
    assertThat(ManifestTemplateConstants.INPUTS_KEY_VALUES).isEqualTo("values");
    assertThat(ManifestTemplateConstants.INPUT_KEY_VALUES_PATHS).isEqualTo("valuesPaths");
    assertThat(ManifestTemplateConstants.INPUTS_KEY_PARAMS).isEqualTo("params");
    assertThat(ManifestTemplateConstants.INPUTS_KEY_PATCHES).isEqualTo("patches");
  }
}
