/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.fme;

import static io.harness.rule.OwnerRule.GONZALO;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.FME)
public class FmeFlagPatchDefinitionParametersTest extends CategoryTest {
  private static final String FLAG_NAME = "testFlag";
  private static final String ENVIRONMENT = "production";
  private static final String OPERATIONS =
      "[{\"op\": \"replace\", \"path\": \"/description\", \"value\": \"new description\"}]";

  @Test
  @Owner(developers = GONZALO)
  @Category(UnitTests.class)
  public void testInputVariables() {
    Map<String, Object> inputVars = new HashMap<>();
    inputVars.put("var1", "value1");
    inputVars.put("var2", "value2");

    FmeFlagPatchDefinitionParameters stepParams = FmeFlagPatchDefinitionParameters.builder()
                                                      .flagName(ParameterField.createValueField(FLAG_NAME))
                                                      .environment(ParameterField.createValueField(ENVIRONMENT))
                                                      .operations(ParameterField.createValueField(OPERATIONS))
                                                      .inputVariables(inputVars)
                                                      .build();

    assertThat(stepParams.getInputVariables()).isNotNull();
    assertThat(stepParams.getInputVariables()).isNotNull();
    assertThat(stepParams.getInputVariables()).hasSize(2);
    assertThat(stepParams.getInputVariables()).containsKeys("var1", "var2");
    assertThat(stepParams.getInputVariables().get("var1")).isEqualTo("value1");
    assertThat(stepParams.getInputVariables().get("var2")).isEqualTo("value2");
  }

  @Test
  @Owner(developers = GONZALO)
  @Category(UnitTests.class)
  public void testParametersWithoutInputVariables() {
    FmeFlagPatchDefinitionParameters stepParams = FmeFlagPatchDefinitionParameters.builder()
                                                      .flagName(ParameterField.createValueField(FLAG_NAME))
                                                      .environment(ParameterField.createValueField(ENVIRONMENT))
                                                      .operations(ParameterField.createValueField(OPERATIONS))
                                                      .build();

    assertThat(stepParams.getFlagName().getValue()).isEqualTo(FLAG_NAME);
    assertThat(stepParams.getEnvironment().getValue()).isEqualTo(ENVIRONMENT);
    assertThat(stepParams.getOperations().getValue()).isEqualTo(OPERATIONS);
    assertThat(stepParams.getInputVariables()).isNull();
  }
}
