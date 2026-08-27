/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.steps;

import static io.harness.rule.OwnerRule.KOTA_KARTHIK;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.rule.Owner;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class ConstantsTest extends CategoryTest {
  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testCookieCutterConstant() {
    assertNotNull(Constants.COOKIECUTTER);
    assertEquals("CookieCutter", Constants.COOKIECUTTER);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testCreateRepoConstant() {
    assertNotNull(Constants.CREATE_REPO);
    assertEquals("CreateRepo", Constants.CREATE_REPO);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testDirectPushConstant() {
    assertNotNull(Constants.DIRECT_PUSH);
    assertEquals("DirectPush", Constants.DIRECT_PUSH);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testCreateCatalogConstant() {
    assertNotNull(Constants.CREATE_CATALOG);
    assertEquals("CreateCatalog", Constants.CREATE_CATALOG);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testCreateOrganisationConstant() {
    assertNotNull(Constants.CREATE_ORGANISATION);
    assertEquals("CreateOrganization", Constants.CREATE_ORGANISATION);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testCreateProjectConstant() {
    assertNotNull(Constants.CREATE_PROJECT);
    assertEquals("CreateProject", Constants.CREATE_PROJECT);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testCreateResourceConstant() {
    assertNotNull(Constants.CREATE_RESOURCE);
    assertEquals("CreateResource", Constants.CREATE_RESOURCE);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testUpdateCatalogPropertyConstant() {
    assertNotNull(Constants.UPDATE_CATALOG_PROPERTY);
    assertEquals("UpdateCatalogProperty", Constants.UPDATE_CATALOG_PROPERTY);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testRegisterCatalogConstant() {
    assertNotNull(Constants.REGISTER_CATALOG);
    assertEquals("RegisterCatalog", Constants.REGISTER_CATALOG);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testStepNodeConstants() {
    assertEquals("CookieCutterStepNode", Constants.COOKIECUTTER_STEP_NODE);
    assertEquals("CreateRepoStepNode", Constants.CREATE_REPO_STEP_NODE);
    assertEquals("RegisterCatalogStepNode", Constants.REGISTER_CATALOG_STEP_NODE);
    assertEquals("DirectPushStepNode", Constants.DIRECT_PUSH_STEP_NODE);
    assertEquals("CreateCatalogStepNode", Constants.CREATE_CATALOG_STEP_NODE);
    assertEquals("CreateOrganisationStepNode", Constants.CREATE_ORGANISATION_STEP_NODE);
    assertEquals("CreateProjectStepNode", Constants.CREATE_PROJECT_STEP_NODE);
    assertEquals("CreateResourceStepNode", Constants.CREATE_RESOURCE_STEP_NODE);
    assertEquals("UpdateCatalogPropertyStepNode", Constants.UPDATE_CATALOG_PROPERTY_STEP_NODE);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testCookieCutterStepType() {
    assertNotNull(Constants.COOKIECUTTER_STEP_TYPE);
    assertEquals(Constants.COOKIECUTTER, Constants.COOKIECUTTER_STEP_TYPE.getType());
    assertEquals(StepCategory.STEP, Constants.COOKIECUTTER_STEP_TYPE.getStepCategory());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testCreateRepoStepType() {
    assertNotNull(Constants.CREATE_REPO_STEP_TYPE);
    assertEquals(Constants.CREATE_REPO, Constants.CREATE_REPO_STEP_TYPE.getType());
    assertEquals(StepCategory.STEP, Constants.CREATE_REPO_STEP_TYPE.getStepCategory());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testDirectPushStepType() {
    assertNotNull(Constants.DIRECT_PUSH_STEP_TYPE);
    assertEquals(Constants.DIRECT_PUSH, Constants.DIRECT_PUSH_STEP_TYPE.getType());
    assertEquals(StepCategory.STEP, Constants.DIRECT_PUSH_STEP_TYPE.getStepCategory());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testRegisterCatalogStepType() {
    assertNotNull(Constants.REGISTER_CATALOG_STEP_TYPE);
    assertEquals(Constants.REGISTER_CATALOG, Constants.REGISTER_CATALOG_STEP_TYPE.getType());
    assertEquals(StepCategory.STEP, Constants.REGISTER_CATALOG_STEP_TYPE.getStepCategory());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testCreateCatalogStepType() {
    assertNotNull(Constants.CREATE_CATALOG_STEP_TYPE);
    assertEquals(Constants.CREATE_CATALOG, Constants.CREATE_CATALOG_STEP_TYPE.getType());
    assertEquals(StepCategory.STEP, Constants.CREATE_CATALOG_STEP_TYPE.getStepCategory());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testCreateOrganisationStepType() {
    assertNotNull(Constants.CREATE_ORGANISATION_STEP_TYPE);
    assertEquals(Constants.CREATE_ORGANISATION, Constants.CREATE_ORGANISATION_STEP_TYPE.getType());
    assertEquals(StepCategory.STEP, Constants.CREATE_ORGANISATION_STEP_TYPE.getStepCategory());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testCreateProjectStepType() {
    assertNotNull(Constants.CREATE_PROJECT_STEP_TYPE);
    assertEquals(Constants.CREATE_PROJECT, Constants.CREATE_PROJECT_STEP_TYPE.getType());
    assertEquals(StepCategory.STEP, Constants.CREATE_PROJECT_STEP_TYPE.getStepCategory());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testCreateResourceStepType() {
    assertNotNull(Constants.CREATE_RESOURCE_STEP_TYPE);
    assertEquals(Constants.CREATE_RESOURCE, Constants.CREATE_RESOURCE_STEP_TYPE.getType());
    assertEquals(StepCategory.STEP, Constants.CREATE_RESOURCE_STEP_TYPE.getStepCategory());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testUpdateCatalogPropertyStepType() {
    assertNotNull(Constants.UPDATE_CATALOG_PROPERTY_STEP_TYPE);
    assertEquals(Constants.UPDATE_CATALOG_PROPERTY, Constants.UPDATE_CATALOG_PROPERTY_STEP_TYPE.getType());
    assertEquals(StepCategory.STEP, Constants.UPDATE_CATALOG_PROPERTY_STEP_TYPE.getStepCategory());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testConstantsIsInterface() {
    assertTrue(Constants.class.isInterface());
  }
}
