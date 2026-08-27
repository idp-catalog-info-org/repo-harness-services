/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.mapper;

import static io.harness.rule.OwnerRule.KOTA_KARTHIK;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.catalog.entities.CatalogEntityVersion;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.EntityVersionResponse;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class CatalogVersionMapperTest extends CategoryTest {
  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testYamlToEntity() {
    String parentUniqueId = "parent-unique-id";
    String yaml = "apiVersion: v1\nkind: Component\nname: test";
    String version = "1.0.0";
    String description = "Test version description";

    CatalogEntityVersion entity =
        CatalogVersionMapper.yamlToEntity(parentUniqueId, yaml, version, description, true, false);

    assertThat(entity).isNotNull();
    assertThat(entity.getEntityId()).isEqualTo(parentUniqueId);
    assertThat(entity.getVersion()).isEqualTo(version);
    assertThat(entity.getYaml()).isEqualTo(yaml);
    assertThat(entity.getDescription()).isEqualTo(description);
    assertThat(entity.isDeprecated()).isTrue();
    assertThat(entity.isStable()).isFalse();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testYamlToEntityWithNullBooleans() {
    String parentUniqueId = "parent-unique-id";
    String yaml = "apiVersion: v1\nkind: Component";
    String version = "2.0.0";
    String description = "Another version";

    CatalogEntityVersion entity =
        CatalogVersionMapper.yamlToEntity(parentUniqueId, yaml, version, description, null, null);

    assertThat(entity).isNotNull();
    assertThat(entity.getEntityId()).isEqualTo(parentUniqueId);
    assertThat(entity.getVersion()).isEqualTo(version);
    assertThat(entity.getYaml()).isEqualTo(yaml);
    assertThat(entity.getDescription()).isEqualTo(description);
    assertThat(entity.isDeprecated()).isFalse();
    assertThat(entity.isStable()).isFalse();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testYamlToEntityWithDefaultValues() {
    String parentUniqueId = "parent-id";
    String yaml = "test: yaml";
    String version = "3.0.0";

    CatalogEntityVersion entity = CatalogVersionMapper.yamlToEntity(parentUniqueId, yaml, version, null, false, true);

    assertThat(entity).isNotNull();
    assertThat(entity.getEntityId()).isEqualTo(parentUniqueId);
    assertThat(entity.getVersion()).isEqualTo(version);
    assertThat(entity.getYaml()).isEqualTo(yaml);
    assertThat(entity.getDescription()).isNull();
    assertThat(entity.isDeprecated()).isFalse();
    assertThat(entity.isStable()).isTrue();
  }
}
