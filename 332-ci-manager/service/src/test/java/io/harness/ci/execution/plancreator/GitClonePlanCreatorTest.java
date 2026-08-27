/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.plancreator;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.stepinfo.CIStepInfo;
import io.harness.beans.steps.stepinfo.GitCloneStepInfoV1;
import io.harness.beans.steps.stepinfo.StepNodeV1;
import io.harness.beans.steps.stepinfo.Strategy;
import io.harness.category.element.UnitTests;
import io.harness.ci.execution.plancreator.V1.GitClonePlanCreator;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.yaml.extended.ci.codebase.PRCloneStrategy;

import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class GitClonePlanCreatorTest extends CategoryTest {
  private TestableGitClonePlanCreator gitClonePlanCreator;

  private static class TestableGitClonePlanCreator extends GitClonePlanCreator {
    public CIStepInfo exposedGetSpec(StepNodeV1 stepElementConfig) {
      return getSpec(stepElementConfig);
    }

    public Strategy exposedToCloneStrategy(ParameterField<PRCloneStrategy> prCloneStrategy) {
      return toCloneStrategy(prCloneStrategy);
    }
  }

  @Before
  public void setUp() {
    gitClonePlanCreator = new TestableGitClonePlanCreator();
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetSupportedStepTypes_shouldReturnClone() {
    Set<String> supportedStepTypes = gitClonePlanCreator.getSupportedStepTypes();

    assertThat(supportedStepTypes).as("should contain 'clone'").containsExactlyInAnyOrder("clone");
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetStepType_shouldReturnGitCloneStepType() {
    StepType expected = StepType.newBuilder()
                            .setType(CIStepInfoType.GIT_CLONE.getDisplayName())
                            .setStepCategory(StepCategory.STEP)
                            .build();

    assertThat(GitClonePlanCreator.STEP_TYPE).as("should match GIT_CLONE step type").isEqualTo(expected);
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetSpec_shouldReturnCloneFromStepNode() {
    GitCloneStepInfoV1 cloneInfo = GitCloneStepInfoV1.builder().build();
    StepNodeV1 stepNodeV1 = StepNodeV1.builder().clone(cloneInfo).build();

    assertThat(gitClonePlanCreator.exposedGetSpec(stepNodeV1)).as("should return clone info").isEqualTo(cloneInfo);
  }

  @Test
  @Owner(developers = OwnerRule.GARGI)
  @Category(UnitTests.class)
  public void testToCloneStrategy_mapsSourceBranch() {
    assertThat(
        gitClonePlanCreator.exposedToCloneStrategy(ParameterField.createValueField(PRCloneStrategy.SOURCE_BRANCH)))
        .isEqualTo(Strategy.SOURCE_BRANCH);
  }

  @Test
  @Owner(developers = OwnerRule.GARGI)
  @Category(UnitTests.class)
  public void testToCloneStrategy_mapsMergeCommit() {
    assertThat(
        gitClonePlanCreator.exposedToCloneStrategy(ParameterField.createValueField(PRCloneStrategy.MERGE_COMMIT)))
        .isEqualTo(Strategy.MERGE);
  }

  @Test
  @Owner(developers = OwnerRule.GARGI)
  @Category(UnitTests.class)
  public void testToCloneStrategy_returnsNullWhenUnset() {
    assertThat(gitClonePlanCreator.exposedToCloneStrategy(ParameterField.ofNull())).isNull();
    assertThat(gitClonePlanCreator.exposedToCloneStrategy(ParameterField.createValueField(null))).isNull();
  }
}
