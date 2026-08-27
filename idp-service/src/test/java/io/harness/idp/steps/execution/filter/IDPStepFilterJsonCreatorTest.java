/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.steps.execution.filter;

import static io.harness.rule.OwnerRule.KOTA_KARTHIK;
import static io.harness.steps.common.Constants.SLACK_NOTIFY;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.steps.Constants;
import io.harness.idp.steps.StepSpecTypeConstants;
import io.harness.rule.Owner;

import java.util.Set;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class IDPStepFilterJsonCreatorTest extends CategoryTest {
  IDPStepFilterJsonCreator idpStepFilterJsonCreator;

  @Before
  public void setUp() {
    idpStepFilterJsonCreator = new IDPStepFilterJsonCreator();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetSupportedStepTypes() {
    Set<String> supportedStepTypes = idpStepFilterJsonCreator.getSupportedStepTypes();

    assertNotNull(supportedStepTypes);
    assertFalse(supportedStepTypes.isEmpty());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetSupportedStepTypesContainsStepSpecTypeConstants() {
    Set<String> supportedStepTypes = idpStepFilterJsonCreator.getSupportedStepTypes();

    assertTrue(supportedStepTypes.contains(StepSpecTypeConstants.RUN));
    assertTrue(supportedStepTypes.contains(StepSpecTypeConstants.PLUGIN));
    assertTrue(supportedStepTypes.contains(StepSpecTypeConstants.GIT_CLONE));
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetSupportedStepTypesContainsIdpConstants() {
    Set<String> supportedStepTypes = idpStepFilterJsonCreator.getSupportedStepTypes();

    assertTrue(supportedStepTypes.contains(Constants.COOKIECUTTER));
    assertTrue(supportedStepTypes.contains(Constants.CREATE_REPO));
    assertTrue(supportedStepTypes.contains(Constants.DIRECT_PUSH));
    assertTrue(supportedStepTypes.contains(Constants.REGISTER_CATALOG));
    assertTrue(supportedStepTypes.contains(Constants.CREATE_CATALOG));
    assertTrue(supportedStepTypes.contains(Constants.CREATE_ORGANISATION));
    assertTrue(supportedStepTypes.contains(Constants.CREATE_PROJECT));
    assertTrue(supportedStepTypes.contains(Constants.CREATE_RESOURCE));
    assertTrue(supportedStepTypes.contains(Constants.UPDATE_CATALOG_PROPERTY));
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetSupportedStepTypesContainsSlackNotify() {
    Set<String> supportedStepTypes = idpStepFilterJsonCreator.getSupportedStepTypes();

    assertTrue(supportedStepTypes.contains(SLACK_NOTIFY));
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetSupportedStepTypesContainsLiteEngineStep() {
    Set<String> supportedStepTypes = idpStepFilterJsonCreator.getSupportedStepTypes();

    assertTrue(supportedStepTypes.contains("liteEngineTask"));
  }
}
