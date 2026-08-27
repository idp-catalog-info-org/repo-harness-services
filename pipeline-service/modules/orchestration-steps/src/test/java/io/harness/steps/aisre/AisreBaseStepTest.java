/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.aisre;

import static io.harness.rule.OwnerRule.CAMERON;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.aisre.AiSrePipelineContextData;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.category.element.UnitTests;
import io.harness.context.GlobalContext;
import io.harness.manage.GlobalContextManager;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CHAOS)
public class AisreBaseStepTest extends CategoryTest {
  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";

  private TestAisreBaseStep step;
  private Ambiance ambiance;

  @Before
  public void setup() {
    GlobalContextManager.set(new GlobalContext());
    step = new TestAisreBaseStep();
    ambiance = Ambiance.newBuilder()
                   .putSetupAbstractions("accountId", ACCOUNT_ID)
                   .putSetupAbstractions("orgIdentifier", ORG_ID)
                   .putSetupAbstractions("projectIdentifier", PROJECT_ID)
                   .build();
  }

  @After
  public void tearDown() {
    GlobalContextManager.unset();
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testResolveOrgIdentifierUsesAmbianceWhenUnset() {
    assertThat(step.resolveOrgIdentifier(ambiance, null)).isEqualTo(ORG_ID);
    assertThat(step.resolveOrgIdentifier(ambiance, ParameterField.createValueField(null))).isEqualTo(ORG_ID);
    assertThat(step.resolveOrgIdentifier(ambiance, ParameterField.createValueField(""))).isEqualTo(ORG_ID);
    assertThat(step.resolveOrgIdentifier(ambiance, ParameterField.createValueField("   "))).isEqualTo(ORG_ID);
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testResolveOrgIdentifierUsesOverrideWhenProvided() {
    assertThat(step.resolveOrgIdentifier(ambiance, ParameterField.createValueField("targetOrg")))
        .isEqualTo("targetOrg");
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testResolveProjectIdentifierUsesAmbianceWhenUnset() {
    assertThat(step.resolveProjectIdentifier(ambiance, null)).isEqualTo(PROJECT_ID);
    assertThat(step.resolveProjectIdentifier(ambiance, ParameterField.createValueField(null))).isEqualTo(PROJECT_ID);
    assertThat(step.resolveProjectIdentifier(ambiance, ParameterField.createValueField(""))).isEqualTo(PROJECT_ID);
    assertThat(step.resolveProjectIdentifier(ambiance, ParameterField.createValueField("   "))).isEqualTo(PROJECT_ID);
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testResolveProjectIdentifierUsesOverrideWhenProvided() {
    assertThat(step.resolveProjectIdentifier(ambiance, ParameterField.createValueField("targetProject")))
        .isEqualTo("targetProject");
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testGetScopeFallsBackToAmbianceForBlankOverrides() {
    Scope scope = step.getScope(ambiance, ParameterField.createValueField(""), ParameterField.createValueField(""));

    assertThat(scope.getAccountIdentifier()).isEqualTo(ACCOUNT_ID);
    assertThat(scope.getOrgIdentifier()).isEqualTo(ORG_ID);
    assertThat(scope.getProjectIdentifier()).isEqualTo(PROJECT_ID);
    assertTargetScope(ACCOUNT_ID, ORG_ID, PROJECT_ID);
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testGetScopeUsesProvidedOverrides() {
    Scope scope = step.getScope(
        ambiance, ParameterField.createValueField("targetOrg"), ParameterField.createValueField("targetProject"));

    assertThat(scope.getAccountIdentifier()).isEqualTo(ACCOUNT_ID);
    assertThat(scope.getOrgIdentifier()).isEqualTo("targetOrg");
    assertThat(scope.getProjectIdentifier()).isEqualTo("targetProject");
    assertTargetScope(ACCOUNT_ID, "targetOrg", "targetProject");
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testGetScopeFallsBackToAmbianceForUnevaluatedExpressionFields() {
    Scope scope =
        step.getScope(ambiance, ParameterField.createExpressionField(true, "<+pipeline.variables.org>", null, true),
            ParameterField.createExpressionField(true, "<+pipeline.variables.project>", null, true));

    assertThat(scope.getOrgIdentifier()).isEqualTo(ORG_ID);
    assertThat(scope.getProjectIdentifier()).isEqualTo(PROJECT_ID);
    assertTargetScope(ACCOUNT_ID, ORG_ID, PROJECT_ID);
  }

  private static void assertTargetScope(String accountId, String orgId, String projectId) {
    AiSrePipelineContextData context = AiSrePipelineContextData.get();
    assertThat(context.getAccountIdentifier()).isEqualTo(accountId);
    assertThat(context.getOrgIdentifier()).isEqualTo(orgId);
    assertThat(context.getProjectIdentifier()).isEqualTo(projectId);
  }

  private static class TestAisreBaseStep extends AisreBaseStep {
    @Override
    protected StepResponse executeAisreStep(Ambiance ambiance, StepBaseParameters stepParameters,
        io.harness.logstreaming.NGLogCallback logCallback, long startTime) {
      throw new UnsupportedOperationException();
    }

    @Override
    protected StepType getAisreStepType() {
      return AisreCreateIncidentStep.STEP_TYPE;
    }
  }
}
