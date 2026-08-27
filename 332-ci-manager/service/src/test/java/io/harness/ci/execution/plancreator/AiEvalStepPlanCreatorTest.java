/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.plancreator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.nodes.AiEvalStepNode;
import io.harness.category.element.UnitTests;
import io.harness.ci.plancreator.AiEvalStepPlanCreator;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.plan.PlanCreationContextValue;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.AI)
public class AiEvalStepPlanCreatorTest extends CategoryTest {
  private static final String ACCOUNT_ID = "account_id";
  private static final String ORG_ID = "org_id";
  private static final String PROJECT_ID = "project_id";
  private static final String STEP_IDENTIFIER = "ai_eval_step";

  private AiEvalStepPlanCreator aiEvalStepPlanCreator = new AiEvalStepPlanCreator();

  @Test
  @Owner(developers = OwnerRule.AI)
  @Category(UnitTests.class)
  public void testGetSupportedStepTypes() {
    Set<String> supportedStepTypes = aiEvalStepPlanCreator.getSupportedStepTypes();
    assertThat(supportedStepTypes).contains(CIStepInfoType.AI_EVAL.getDisplayName());
  }

  @Test
  @Owner(developers = OwnerRule.AI)
  @Category(UnitTests.class)
  public void testGetFieldClass() {
    assertThat(aiEvalStepPlanCreator.getFieldClass()).isEqualTo(AiEvalStepNode.class);
  }

  @Test
  @Owner(developers = OwnerRule.AI)
  @Category(UnitTests.class)
  public void testGetSupportedYamlVersions() {
    Set<String> supportedYamlVersions = aiEvalStepPlanCreator.getSupportedYamlVersions();
    assertThat(supportedYamlVersions).contains(HarnessYamlVersion.V0);
  }

  @Test
  @Owner(developers = OwnerRule.AI)
  @Category(UnitTests.class)
  public void testCreatePlanForField_throwsException() {
    AiEvalStepNode aiEvalStepNode = new AiEvalStepNode();
    aiEvalStepNode.setIdentifier(STEP_IDENTIFIER);

    PlanCreationContext planCreationContext = PlanCreationContext.builder()
                                                  .currentField(null)
                                                  .globalContext(Map.of("metadata",
                                                      PlanCreationContextValue.newBuilder()
                                                          .setProjectIdentifier(PROJECT_ID)
                                                          .setOrgIdentifier(ORG_ID)
                                                          .setAccountIdentifier(ACCOUNT_ID)
                                                          .build()))
                                                  .build();

    assertThatThrownBy(() -> aiEvalStepPlanCreator.createPlanForField(planCreationContext, aiEvalStepNode))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("AiEval step")
        .hasMessageContaining(STEP_IDENTIFIER)
        .hasMessageContaining("was not expanded")
        .hasMessageContaining("preprocessing")
        .hasMessageContaining("AI_ENABLE_EVAL_STEP");
  }
}
