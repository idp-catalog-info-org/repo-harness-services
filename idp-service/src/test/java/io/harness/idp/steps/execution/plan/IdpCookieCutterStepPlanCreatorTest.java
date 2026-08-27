/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.steps.execution.plan;

import static io.harness.rule.OwnerRule.KOTA_KARTHIK;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.steps.Constants;
import io.harness.idp.steps.beans.stepnode.IdpCookieCutterStepNode;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.rule.Owner;

import java.util.Set;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class IdpCookieCutterStepPlanCreatorTest extends CategoryTest {
  IdpCookieCutterStepPlanCreator planCreator;

  @Before
  public void setUp() {
    planCreator = new IdpCookieCutterStepPlanCreator();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetSupportedStepTypes() {
    Set<String> supportedStepTypes = planCreator.getSupportedStepTypes();

    assertNotNull(supportedStepTypes);
    assertFalse(supportedStepTypes.isEmpty());
    assertTrue(supportedStepTypes.contains(Constants.COOKIECUTTER));
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetFieldClass() {
    Class<IdpCookieCutterStepNode> fieldClass = planCreator.getFieldClass();

    assertNotNull(fieldClass);
    assertEquals(IdpCookieCutterStepNode.class, fieldClass);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetSupportedYamlVersions() {
    Set<String> supportedYamlVersions = planCreator.getSupportedYamlVersions();

    assertNotNull(supportedYamlVersions);
    assertTrue(supportedYamlVersions.contains(HarnessYamlVersion.V0));
  }
}
