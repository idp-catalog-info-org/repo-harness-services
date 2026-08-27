/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.utils;

import static io.harness.idp.catalog.utils.Constants.COMPONENT_KIND;
import static io.harness.rule.OwnerRule.SATHISH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.backstage.entities.BackstageCatalogComponentEntity;
import io.harness.idp.catalog.entities.InlineCatalogEntity;
import io.harness.rule.Owner;

import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class CatalogUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testParseBackstageEntityReferenceToCatalogRelationRef() {
    assertThat(CatalogUtils.parseBackstageEntityReferenceToCatalogRelationRef(null, null)).isEqualTo(null);
    assertThat(CatalogUtils.parseBackstageEntityReferenceToCatalogRelationRef("component:default/test", null))
        .isEqualTo("component:test");
    assertThat(CatalogUtils.parseBackstageEntityReferenceToCatalogRelationRef("component:test", null))
        .isEqualTo("component:test");
    assertThat(CatalogUtils.parseBackstageEntityReferenceToCatalogRelationRef("test", null)).isEqualTo("test");
    assertThat(CatalogUtils.parseBackstageEntityReferenceToCatalogRelationRef("group:default/harness_group", null))
        .isEqualTo("group:_group");
    assertThat(CatalogUtils.parseBackstageEntityReferenceToCatalogRelationRef("user:default/test", null))
        .isEqualTo("user:test");
    assertThat(CatalogUtils.parseBackstageEntityReferenceToCatalogRelationRef("user:default/testplus", null))
        .isEqualTo("user:test+");
    assertThat(CatalogUtils.parseBackstageEntityReferenceToCatalogRelationRef("domain:default/test", null))
        .isEqualTo(null);
    assertThat(CatalogUtils.parseBackstageEntityReferenceToCatalogRelationRef("system:default/test", null))
        .isEqualTo(null);
    assertThat(CatalogUtils.parseBackstageEntityReferenceToCatalogRelationRef("location:default/test", null))
        .isEqualTo(null);
    assertThat(CatalogUtils.parseBackstageEntityReferenceToCatalogRelationRef("template:default/test", null))
        .isEqualTo("workflow:test");
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testParseBackstageEntityRefFromCatalogRef() {
    assertThat(CatalogUtils.parseBackstageEntityRefFromCatalogRef(null, false)).isEqualTo(null);
    assertThat(CatalogUtils.parseBackstageEntityRefFromCatalogRef("component:test", false))
        .isEqualTo("component:account/test");
    assertThat(CatalogUtils.parseBackstageEntityRefFromCatalogRef("test", false)).isEqualTo("test");
    assertThat(CatalogUtils.parseBackstageEntityRefFromCatalogRef("user:test+@harness.io", false))
        .isEqualTo("user:account/testplus");
    assertThat(CatalogUtils.parseBackstageEntityRefFromCatalogRef("workflow:test", false))
        .isEqualTo("template:account/test");
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetBackstageCatalogKindFromEntityUid() {
    assertThat(CatalogUtils.getBackstageCatalogKindFromEntityUid("test")).isEqualTo("");
    assertThat(CatalogUtils.getBackstageCatalogKindFromEntityUid("default/component/test")).isEqualTo("component");
    assertThat(CatalogUtils.getBackstageCatalogKindFromEntityUid("default/template/test")).isEqualTo("workflow");
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetBackstageCatalogNameFromEntityUid() {
    assertThat(CatalogUtils.getBackstageCatalogNameFromEntityUid("test")).isEqualTo("");
    assertThat(CatalogUtils.getBackstageCatalogNameFromEntityUid("default/component/test")).isEqualTo("test");
    assertThat(CatalogUtils.getBackstageCatalogNameFromEntityUid("default/template/test")).isEqualTo("test");
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetEntityUniqueIdForByNameAPI() {
    assertThat(CatalogUtils.getEntityUniqueIdForByNameAPI("default/component/test"))
        .isEqualTo("component/default/test");
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetIdentifierForWorkflowsInGroup() {
    assertThat(CatalogUtils.getIdentifierForWorkflowsInGroup("default", "component", "test"))
        .isEqualTo("default/component/test");
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetEntityUUId() {
    InlineCatalogEntity inlineCatalogEntity = new InlineCatalogEntity();
    inlineCatalogEntity.setKind(COMPONENT_KIND);
    inlineCatalogEntity.setIdentifier("test");
    assertThat(CatalogUtils.getEntityUUId(inlineCatalogEntity)).isEqualTo("account/component/test");

    BackstageCatalogComponentEntity backstageCatalogComponentEntity = new BackstageCatalogComponentEntity();
    backstageCatalogComponentEntity.setMetadata(Map.of("namespace", "default", "name", "test"));
    assertThat(CatalogUtils.getEntityUUId(backstageCatalogComponentEntity)).isEqualTo("default/Component/test");
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetEntityRef() {
    InlineCatalogEntity inlineCatalogEntity = new InlineCatalogEntity();
    inlineCatalogEntity.setKind(COMPONENT_KIND);
    inlineCatalogEntity.setIdentifier("test");
    assertThat(CatalogUtils.getEntityRefInBackstageNaming(inlineCatalogEntity)).isEqualTo("component:account/test");

    BackstageCatalogComponentEntity backstageCatalogComponentEntity = new BackstageCatalogComponentEntity();
    backstageCatalogComponentEntity.setMetadata(Map.of("namespace", "default", "name", "test"));
    assertThat(CatalogUtils.getEntityRef(backstageCatalogComponentEntity)).isEqualTo("component:default/test");

    Object entity = inlineCatalogEntity;
    assertThat(CatalogUtils.getEntityRef(entity)).isEqualTo("component:account/test");
    entity = backstageCatalogComponentEntity;
    assertThat(CatalogUtils.getEntityRef(entity)).isEqualTo("component:default/test");
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetEntityRefFromUid() {
    InlineCatalogEntity inlineCatalogEntity = new InlineCatalogEntity();
    inlineCatalogEntity.setKind(COMPONENT_KIND);
    inlineCatalogEntity.setIdentifier("test");
    assertThat(CatalogUtils.getEntityRefFromUid(inlineCatalogEntity)).isEqualTo("component:account/test");

    BackstageCatalogComponentEntity backstageCatalogComponentEntity = new BackstageCatalogComponentEntity();
    backstageCatalogComponentEntity.setMetadata(Map.of("namespace", "default", "name", "test"));
    backstageCatalogComponentEntity.setEntityUid("default/component/test");
    assertThat(CatalogUtils.getEntityRefFromUid(backstageCatalogComponentEntity)).isEqualTo("component:default/test");

    assertThat(CatalogUtils.getEntityRefFromUid("default/component/test")).isEqualTo("component:account/test");
  }
}
