/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.pms.expressions.functors.K8sReleaseFunctorV2.KUBERNETES_RELEASE_FUNCTOR_NAME;
import static io.harness.rule.OwnerRule.ABOSII;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.steps.StepType;
import io.harness.rule.Owner;

import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDP)
public class K8sReleaseFunctorV2Test extends CategoryTest {
  private static final Ambiance EMPTY_AMBIANCE = Ambiance.newBuilder().build();
  private static final Ambiance AMBIANCE_NON_K8S = createDummyAmbianceWithStep(StepSpecTypeConstants.COMMAND);
  private static final List<Ambiance> ALL_SUPPORTED_AMBIANCES =
      K8sReleaseFunctorV2.SUPPORTED_STEP_TYPES.stream()
          .map(K8sReleaseFunctorV2Test::createDummyAmbianceWithStep)
          .collect(Collectors.toList());

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testNonStepContext() {
    K8sReleaseFunctorV2 releaseFunctor = new K8sReleaseFunctorV2(EMPTY_AMBIANCE);
    assertThatThrownBy(() -> releaseFunctor.get(K8sReleaseFunctorV2.Field.REVISION.getKey()))
        .hasMessageContaining(String.format(
            "Expression <+%s> cannot be use outside of Execution Steps", KUBERNETES_RELEASE_FUNCTOR_NAME));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testUnsupportedStepType() {
    K8sReleaseFunctorV2 releaseFunctor = new K8sReleaseFunctorV2(AMBIANCE_NON_K8S);

    assertThatThrownBy(() -> releaseFunctor.get(K8sReleaseFunctorV2.Field.REVISION.getKey()))
        .hasMessageContaining(String.format("Expression <+%s> is not yet supported for the step type [%s]",
            KUBERNETES_RELEASE_FUNCTOR_NAME, StepSpecTypeConstants.COMMAND));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testSupportedStepTypesRevision() {
    testAllFunctorValue(K8sReleaseFunctorV2.Field.REVISION.getKey(), K8sReleaseFunctorV2.Field.REVISION.getValue());
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testSupportedStepTypesStageColor() {
    testAllFunctorValue(
        K8sReleaseFunctorV2.Field.STAGE_COLOR.getKey(), K8sReleaseFunctorV2.Field.STAGE_COLOR.getValue());
  }

  private void testAllFunctorValue(String key, String expected) {
    assertThat(ALL_SUPPORTED_AMBIANCES).withFailMessage("Invalid test setup, expecting non empty list").isNotEmpty();
    for (Ambiance ambiance : ALL_SUPPORTED_AMBIANCES) {
      testFunctorValue(ambiance, key, expected);
    }
  }

  private void testFunctorValue(Ambiance ambiance, String key, String expected) {
    K8sReleaseFunctorV2 releaseFunctor = new K8sReleaseFunctorV2(ambiance);
    Object result = releaseFunctor.get(key);
    assertThat(result).isInstanceOf(String.class);
    assertThat((String) result).isEqualTo(expected);
  }

  private static Ambiance createDummyAmbianceWithStep(String stepType) {
    return Ambiance.newBuilder()
        .addLevels(Level.newBuilder().setStepType(StepType.newBuilder().setType(stepType).build()).build())
        .build();
  }
}