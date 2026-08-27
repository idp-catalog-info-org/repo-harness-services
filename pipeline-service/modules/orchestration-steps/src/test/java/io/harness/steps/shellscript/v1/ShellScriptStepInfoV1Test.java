/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.shellscript.v1;

import static io.harness.rule.OwnerRule.NAMANG;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ShellScriptStepInfoV1Test extends CategoryTest {
  private static final String connectorRef = "connectorRef";
  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testExtractSecretRefs() {
    ShellScriptStepInfoV1 shellScriptStepInfoV1 = new ShellScriptStepInfoV1(null, null);

    shellScriptStepInfoV1.setExecution_target(
        new ExecutionTargetV1(null, ParameterField.createValueField(connectorRef), null));
    Map<String, ParameterField<String>> secretRefs = shellScriptStepInfoV1.extractSecretRefs();
    assertThat(secretRefs)
        .hasSize(1)
        .containsEntry("execution_target.connector", ParameterField.createValueField(connectorRef));

    shellScriptStepInfoV1.setExecution_target(new ExecutionTargetV1(null, null, null));
    assertThat(shellScriptStepInfoV1.extractSecretRefs()).isEmpty();
    shellScriptStepInfoV1.setExecution_target(null);
    assertThat(shellScriptStepInfoV1.extractSecretRefs()).isEmpty();
  }
}
