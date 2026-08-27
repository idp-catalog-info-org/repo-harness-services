/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.groups.mappers;

import static io.harness.annotations.dev.HarnessTeam.IDP;
import static io.harness.idp.catalog.utils.Constants.WORKFLOW_KIND;
import static io.harness.rule.OwnerRule.DEVESH;

import static junit.framework.TestCase.assertEquals;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.backstage.entities.BackstageCatalogEntity;
import io.harness.idp.backstage.entities.BackstageCatalogTemplateEntity;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.InlineCatalogEntity;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.WorkflowsInfoResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

@OwnedBy(IDP)
public class WorkflowsMapperTest extends CategoryTest {
  private static final String TEST_ACCOUNT_IDENTIFIER = "test-account-identifier";

  private static final String TEST_TEMPLATE_UUID = "test-template-uuid";
  private static final String TEST_TEMPLATE_API_VERSION = "test-template-api-version";
  private static final String TEST_TEMPLATE_YAML = "test-template-yaml";
  private static final String TEST_CATALOG_TYPE = "Template";
  private static final String TEST_CATALOG_NAME = "test-catalog-name";
  private static final String TEST_CATALOG_DESCRIPTION = "test-catalog-description";
  private static final String TEST_CATALOG_TITLE = "test-catalog-title";
  private static final String TEST_CATALOG_OWNER = "test-catalog-owner";
  private static final String TEST_CATALOG_ICON = "test-catalog-icon";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void toResponseFromBackstageCatalogEntitiesTest() {
    WorkflowsInfoResponse workflowsInfoResponse =
        WorkflowsMapper.toResponseFromBackstageCatalogEntities(new ArrayList<>(List.of(getBackstageCatalogEntity())));
    assertEquals(TEST_CATALOG_NAME, workflowsInfoResponse.getWorkflows().get(0).getName());
    assertEquals(TEST_CATALOG_DESCRIPTION, workflowsInfoResponse.getWorkflows().get(0).getDescription());
    assertEquals(TEST_CATALOG_TITLE, workflowsInfoResponse.getWorkflows().get(0).getTitle());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void toResponseFromCatalogEntitiesTest() {
    WorkflowsInfoResponse workflowsInfoResponse =
        WorkflowsMapper.toResponseFromCatalogEntities(new ArrayList<>(List.of(getCatalogEntity())));
    assertEquals(TEST_TEMPLATE_UUID, workflowsInfoResponse.getWorkflows().get(0).getName());
    assertEquals(TEST_CATALOG_DESCRIPTION, workflowsInfoResponse.getWorkflows().get(0).getDescription());
    assertEquals(TEST_CATALOG_NAME, workflowsInfoResponse.getWorkflows().get(0).getTitle());
  }

  private CatalogEntity getCatalogEntity() {
    InlineCatalogEntity inlineCatalogEntity = new InlineCatalogEntity();
    inlineCatalogEntity.setName(TEST_CATALOG_NAME);
    inlineCatalogEntity.setDescription(TEST_CATALOG_DESCRIPTION);
    inlineCatalogEntity.setKind(WORKFLOW_KIND);
    inlineCatalogEntity.setOwner(TEST_ACCOUNT_IDENTIFIER);

    Map<String, Object> metadata = new HashMap<>();
    metadata.put("icon", TEST_CATALOG_ICON);

    inlineCatalogEntity.setMetadata(metadata);
    inlineCatalogEntity.setIdentifier(TEST_TEMPLATE_UUID);
    inlineCatalogEntity.setType(TEST_CATALOG_TYPE);
    return inlineCatalogEntity;
  }

  private BackstageCatalogEntity getBackstageCatalogEntity() {
    BackstageCatalogTemplateEntity backstageCatalogTemplateEntity = new BackstageCatalogTemplateEntity();
    backstageCatalogTemplateEntity.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    backstageCatalogTemplateEntity.setEntityUid(TEST_TEMPLATE_UUID);
    backstageCatalogTemplateEntity.setKind(TEST_CATALOG_TYPE);
    backstageCatalogTemplateEntity.setYaml(TEST_TEMPLATE_YAML);
    backstageCatalogTemplateEntity.setApiVersion(TEST_TEMPLATE_API_VERSION);
    backstageCatalogTemplateEntity.setSpec(
        BackstageCatalogTemplateEntity.Spec.builder().type(TEST_CATALOG_TYPE).build());

    Map<String, Object> metadata = new HashMap<>();
    metadata.put("name", TEST_CATALOG_NAME);
    metadata.put("description", TEST_CATALOG_DESCRIPTION);
    metadata.put("title", TEST_CATALOG_TITLE);
    metadata.put("owner", TEST_CATALOG_OWNER);
    metadata.put("icon", TEST_CATALOG_ICON);

    backstageCatalogTemplateEntity.setMetadata(metadata);
    return backstageCatalogTemplateEntity;
  }
}
