/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputs.helper;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class CloneRefRuntimeInputHelperTest extends CategoryTest {
  @Test
  @Owner(developers = OwnerRule.SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testShouldPipelineCloneRefRuntimeInput_CloneEnabledRefMissing() {
    // Clone is enabled but ref is missing - should return true
    String pipelineYaml = "pipeline:\n"
        + "  clone:\n"
        + "    connector: TestRepo\n"
        + "  stages:\n"
        + "    - runtime: shell\n"
        + "      steps:\n"
        + "        - run:\n"
        + "            script: echo hello\n";

    boolean result = CloneRefRuntimeInputHelper.shouldPipelineCloneRefRuntimeInput(pipelineYaml);
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = OwnerRule.SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testShouldPipelineCloneRefRuntimeInput_CloneEnabledRefPresent() {
    // Clone is enabled and ref is present - should return false
    String pipelineYaml = "pipeline:\n"
        + "  clone:\n"
        + "    connector: TestRepo\n"
        + "    ref:\n"
        + "      type: branch\n"
        + "      name: main\n"
        + "  stages:\n"
        + "    - runtime: shell\n"
        + "      steps:\n"
        + "        - run:\n"
        + "            script: echo hello\n";

    boolean result = CloneRefRuntimeInputHelper.shouldPipelineCloneRefRuntimeInput(pipelineYaml);
    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = OwnerRule.SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testShouldPipelineCloneRefRuntimeInput_CloneDisabledExplicitly() {
    // Clone is explicitly disabled - should return false
    String pipelineYaml = "pipeline:\n"
        + "  clone:\n"
        + "    enabled: false\n"
        + "  stages:\n"
        + "    - runtime: shell\n"
        + "      steps:\n"
        + "        - run:\n"
        + "            script: echo hello\n";

    boolean result = CloneRefRuntimeInputHelper.shouldPipelineCloneRefRuntimeInput(pipelineYaml);
    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = OwnerRule.SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testShouldPipelineCloneRefRuntimeInput_CloneDisabledLegacy() {
    // Clone is disabled using legacy "disabled: true" - should return false
    String pipelineYaml = "pipeline:\n"
        + "  clone:\n"
        + "    disabled: true\n"
        + "  stages:\n"
        + "    - runtime: shell\n"
        + "      steps:\n"
        + "        - run:\n"
        + "            script: echo hello\n";

    boolean result = CloneRefRuntimeInputHelper.shouldPipelineCloneRefRuntimeInput(pipelineYaml);
    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = OwnerRule.SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testShouldPipelineCloneRefRuntimeInput_CloneBooleanFalse() {
    // Clone is just "false" boolean - should return false
    String pipelineYaml = "pipeline:\n"
        + "  clone: false\n"
        + "  stages:\n"
        + "    - runtime: shell\n"
        + "      steps:\n"
        + "        - run:\n"
        + "            script: echo hello\n";

    boolean result = CloneRefRuntimeInputHelper.shouldPipelineCloneRefRuntimeInput(pipelineYaml);
    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = OwnerRule.SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testShouldPipelineCloneRefRuntimeInput_CloneBooleanTrue() {
    // Clone is just "true" boolean (enabled but no details) - should return true
    String pipelineYaml = "pipeline:\n"
        + "  clone: true\n"
        + "  stages:\n"
        + "    - runtime: shell\n"
        + "      steps:\n"
        + "        - run:\n"
        + "            script: echo hello\n";

    boolean result = CloneRefRuntimeInputHelper.shouldPipelineCloneRefRuntimeInput(pipelineYaml);
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = OwnerRule.SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testShouldPipelineCloneRefRuntimeInput_NoCloneDefined() {
    // No clone defined at all - should return false
    String pipelineYaml = "pipeline:\n"
        + "  stages:\n"
        + "    - runtime: shell\n"
        + "      steps:\n"
        + "        - run:\n"
        + "            script: echo hello\n";

    boolean result = CloneRefRuntimeInputHelper.shouldPipelineCloneRefRuntimeInput(pipelineYaml);
    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = OwnerRule.SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testShouldPipelineCloneRefRuntimeInput_CloneEnabledExplicitly() {
    // Clone is explicitly enabled with "enabled: true" but no ref - should return true
    String pipelineYaml = "pipeline:\n"
        + "  clone:\n"
        + "    enabled: true\n"
        + "    connector: TestRepo\n"
        + "  stages:\n"
        + "    - runtime: shell\n"
        + "      steps:\n"
        + "        - run:\n"
        + "            script: echo hello\n";

    boolean result = CloneRefRuntimeInputHelper.shouldPipelineCloneRefRuntimeInput(pipelineYaml);
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = OwnerRule.SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testShouldPipelineCloneRefRuntimeInput_CloneRefNull() {
    // Clone ref is explicitly null - should return true
    String pipelineYaml = "pipeline:\n"
        + "  clone:\n"
        + "    connector: TestRepo\n"
        + "    ref: null\n"
        + "  stages:\n"
        + "    - runtime: shell\n"
        + "      steps:\n"
        + "        - run:\n"
        + "            script: echo hello\n";

    boolean result = CloneRefRuntimeInputHelper.shouldPipelineCloneRefRuntimeInput(pipelineYaml);
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = OwnerRule.SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testShouldPipelineCloneRefRuntimeInput_InvalidYaml() {
    // Invalid YAML - should return false (graceful handling)
    String pipelineYaml = "this is not valid yaml: [[[";

    boolean result = CloneRefRuntimeInputHelper.shouldPipelineCloneRefRuntimeInput(pipelineYaml);
    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = OwnerRule.SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testShouldPipelineCloneRefRuntimeInput_EmptyYaml() {
    // Empty YAML - should return false
    String pipelineYaml = "";

    boolean result = CloneRefRuntimeInputHelper.shouldPipelineCloneRefRuntimeInput(pipelineYaml);
    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = OwnerRule.SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testShouldPipelineCloneRefRuntimeInput_NullYaml() {
    // Null YAML - should return false
    boolean result = CloneRefRuntimeInputHelper.shouldPipelineCloneRefRuntimeInput(null);
    assertThat(result).isFalse();
  }

  // Tests for injectCloneRefAsRuntimeInput method

  @Test
  @Owner(developers = OwnerRule.SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testInjectCloneRefAsRuntimeInput_CloneEnabledRefMissing() {
    // Clone is enabled but ref is missing - should inject <+input>
    String pipelineYaml = "pipeline:\n"
        + "  clone:\n"
        + "    connector: TestRepo\n"
        + "  stages:\n"
        + "    - runtime: shell\n";

    String result = CloneRefRuntimeInputHelper.injectCloneRefAsRuntimeInput(pipelineYaml);
    assertThat(result).contains("<+input>");
  }

  @Test
  @Owner(developers = OwnerRule.SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testInjectCloneRefAsRuntimeInput_CloneEnabledRefPresent() {
    // Clone is enabled and ref is present - should NOT inject
    String pipelineYaml = "pipeline:\n"
        + "  clone:\n"
        + "    connector: TestRepo\n"
        + "    ref:\n"
        + "      type: branch\n"
        + "      name: main\n"
        + "  stages:\n"
        + "    - runtime: shell\n";

    String result = CloneRefRuntimeInputHelper.injectCloneRefAsRuntimeInput(pipelineYaml);
    assertThat(result).doesNotContain("<+input>");
    assertThat(result).contains("type: branch");
  }

  @Test
  @Owner(developers = OwnerRule.SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testInjectCloneRefAsRuntimeInput_CloneBooleanTrue() {
    // Clone is just "true" - should convert to object and inject <+input>
    String pipelineYaml = "pipeline:\n"
        + "  clone: true\n"
        + "  stages:\n"
        + "    - runtime: shell\n";

    String result = CloneRefRuntimeInputHelper.injectCloneRefAsRuntimeInput(pipelineYaml);
    assertThat(result).contains("<+input>");
    assertThat(result).contains("enabled: true");
  }

  @Test
  @Owner(developers = OwnerRule.SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testInjectCloneRefAsRuntimeInput_CloneBooleanFalse() {
    // Clone is "false" - should NOT inject
    String pipelineYaml = "pipeline:\n"
        + "  clone: false\n"
        + "  stages:\n"
        + "    - runtime: shell\n";

    String result = CloneRefRuntimeInputHelper.injectCloneRefAsRuntimeInput(pipelineYaml);
    assertThat(result).doesNotContain("<+input>");
    assertThat(result).isEqualTo(pipelineYaml);
  }

  @Test
  @Owner(developers = OwnerRule.SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testInjectCloneRefAsRuntimeInput_CloneDisabled() {
    // Clone is explicitly disabled - should NOT inject
    String pipelineYaml = "pipeline:\n"
        + "  clone:\n"
        + "    enabled: false\n"
        + "  stages:\n"
        + "    - runtime: shell\n";

    String result = CloneRefRuntimeInputHelper.injectCloneRefAsRuntimeInput(pipelineYaml);
    assertThat(result).doesNotContain("<+input>");
    assertThat(result).isEqualTo(pipelineYaml);
  }

  @Test
  @Owner(developers = OwnerRule.SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testInjectCloneRefAsRuntimeInput_NoCloneDefined() {
    // No clone defined - should NOT inject
    String pipelineYaml = "pipeline:\n"
        + "  stages:\n"
        + "    - runtime: shell\n";

    String result = CloneRefRuntimeInputHelper.injectCloneRefAsRuntimeInput(pipelineYaml);
    assertThat(result).doesNotContain("<+input>");
    assertThat(result).isEqualTo(pipelineYaml);
  }

  @Test
  @Owner(developers = OwnerRule.SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testInjectCloneRefAsRuntimeInput_NullYaml() {
    // Null YAML - should return null gracefully
    String result = CloneRefRuntimeInputHelper.injectCloneRefAsRuntimeInput(null);
    assertThat(result).isNull();
  }
}
