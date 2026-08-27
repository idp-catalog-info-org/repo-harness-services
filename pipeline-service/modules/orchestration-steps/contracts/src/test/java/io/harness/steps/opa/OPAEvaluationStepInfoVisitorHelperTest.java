/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.opa;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.MAYANK_AGARWAL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.walktree.visitor.validation.ValidationVisitor;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class OPAEvaluationStepInfoVisitorHelperTest extends CategoryTest {
  @Mock private ValidationVisitor validationVisitor;

  private OPAEvaluationStepInfoVisitorHelper visitorHelper;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    visitorHelper = new OPAEvaluationStepInfoVisitorHelper();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testValidate() {
    OPAEvaluationStepInfo stepInfo =
        OPAEvaluationStepInfo.infoBuilder().policySetId(ParameterField.createValueField("policy-set-123")).build();

    // Should not throw any exception
    visitorHelper.validate(stepInfo, validationVisitor);
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testValidateWithNullObject() {
    // Should not throw any exception even with null
    visitorHelper.validate(null, validationVisitor);
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testCreateDummyVisitableElement() {
    OPAEvaluationStepInfo originalElement =
        OPAEvaluationStepInfo.infoBuilder().policySetId(ParameterField.createValueField("policy-set-123")).build();

    Object dummyElement = visitorHelper.createDummyVisitableElement(originalElement);

    assertThat(dummyElement).isNotNull();
    assertThat(dummyElement).isInstanceOf(OPAEvaluationStepInfo.class);
    OPAEvaluationStepInfo dummyStepInfo = (OPAEvaluationStepInfo) dummyElement;
    assertThat(dummyStepInfo.getPolicySetId()).isNull(); // Dummy element has null values
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testCreateDummyVisitableElementWithNull() {
    Object dummyElement = visitorHelper.createDummyVisitableElement(null);

    assertThat(dummyElement).isNotNull();
    assertThat(dummyElement).isInstanceOf(OPAEvaluationStepInfo.class);
  }
}
