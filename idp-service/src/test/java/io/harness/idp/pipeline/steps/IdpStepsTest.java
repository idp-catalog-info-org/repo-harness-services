/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.pipeline.steps;

import static io.harness.rule.OwnerRule.KOTA_KARTHIK;

import static junit.framework.TestCase.assertNotNull;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class IdpStepsTest extends CategoryTest {
  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testIdpCookieCutterStep_StepType() {
    assertNotNull(IdpCookieCutterStep.STEP_TYPE);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testIdpCreateCatalogStep_StepType() {
    assertNotNull(IdpCreateCatalogStep.STEP_TYPE);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testIdpCreateOrganisationStep_StepType() {
    assertNotNull(IdpCreateOrganisationStep.STEP_TYPE);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testIdpCreateProjectStep_StepType() {
    assertNotNull(IdpCreateProjectStep.STEP_TYPE);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testIdpCreateRepoStep_StepType() {
    assertNotNull(IdpCreateRepoStep.STEP_TYPE);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testIdpCreateResourceStep_StepType() {
    assertNotNull(IdpCreateResourceStep.STEP_TYPE);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testIdpDirectPushStep_StepType() {
    assertNotNull(IdpDirectPushStep.STEP_TYPE);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testIdpRegisterCatalogStep_StepType() {
    assertNotNull(IdpRegisterCatalogStep.STEP_TYPE);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testIdpUpdateCatalogPropertyStep_StepType() {
    assertNotNull(IdpUpdateCatalogPropertyStep.STEP_TYPE);
  }
}
