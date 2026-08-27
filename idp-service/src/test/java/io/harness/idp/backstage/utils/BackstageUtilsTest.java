/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.backstage.utils;

import static io.harness.rule.OwnerRule.KOTA_KARTHIK;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.backstage.beans.MetadataFieldConstants;
import io.harness.idp.backstage.entities.BackstageCatalogComponentEntity;
import io.harness.idp.backstage.entities.BackstageCatalogEntity;
import io.harness.rule.Owner;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class BackstageUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetEntityUniqueId_FromEntity() {
    BackstageCatalogComponentEntity entity = new BackstageCatalogComponentEntity();
    Map<String, Object> metadata = new HashMap<>();
    metadata.put(MetadataFieldConstants.NAMESPACE, "test-namespace");
    metadata.put(MetadataFieldConstants.NAME, "test-name");
    entity.setMetadata(metadata);
    entity.setKind("Component");

    String uniqueId = BackstageUtils.getEntityUniqueId(entity);
    assertThat(uniqueId).isEqualTo("test-namespace/Component/test-name");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetEntityUniqueId_DefaultNamespace() {
    BackstageCatalogComponentEntity entity = new BackstageCatalogComponentEntity();
    Map<String, Object> metadata = new HashMap<>();
    metadata.put(MetadataFieldConstants.NAME, "test-name");
    entity.setMetadata(metadata);
    entity.setKind("Component");

    String uniqueId = BackstageUtils.getEntityUniqueId(entity);
    assertThat(uniqueId).isEqualTo("default/Component/test-name");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetEntityUniqueId_WithParams() {
    String uniqueId = BackstageUtils.getEntityUniqueId("custom-namespace", "API", "my-api");
    assertThat(uniqueId).isEqualTo("custom-namespace/API/my-api");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetEntityUniqueId_NullNamespace() {
    String uniqueId = BackstageUtils.getEntityUniqueId(null, "Component", "test-component");
    assertThat(uniqueId).isEqualTo("default/Component/test-component");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetEntityUniqueId_EmptyNamespace() {
    String uniqueId = BackstageUtils.getEntityUniqueId("", "Component", "test-component");
    assertThat(uniqueId).isEqualTo("default/Component/test-component");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetEntityRef_FromEntity() {
    BackstageCatalogComponentEntity entity = new BackstageCatalogComponentEntity();
    Map<String, Object> metadata = new HashMap<>();
    metadata.put(MetadataFieldConstants.NAMESPACE, "test-namespace");
    metadata.put(MetadataFieldConstants.NAME, "test-name");
    entity.setMetadata(metadata);
    entity.setKind("Component");

    String entityRef = BackstageUtils.getEntityRef(entity);
    assertThat(entityRef).isEqualTo("component:test-namespace/test-name");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetEntityRef_WithParams() {
    String entityRef = BackstageUtils.getEntityRef("custom-namespace", "API", "my-api");
    assertThat(entityRef).isEqualTo("api:custom-namespace/my-api");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetEntityRef_DefaultNamespace() {
    String entityRef = BackstageUtils.getEntityRef(null, "Component", "test-component");
    assertThat(entityRef).isEqualTo("component:default/test-component");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetEntityUniqueIdForByNameAPI() {
    String entityUid = "namespace/kind/name";
    String uniqueId = BackstageUtils.getEntityUniqueIdForByNameAPI(entityUid);
    assertThat(uniqueId).isEqualTo("kind/namespace/name");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetEntityRefFromUid_AlreadyFormatted() {
    String entityUid = "component:default/my-component";
    String entityRef = BackstageUtils.getEntityRefFromUid(entityUid);
    assertThat(entityRef).isEqualTo("component:default/my-component");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetEntityRefFromUid_NeedsConversion() {
    String entityUid = "default/Component/my-component";
    String entityRef = BackstageUtils.getEntityRefFromUid(entityUid);
    assertThat(entityRef).isEqualTo("component:default/my-component");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetEntityRefFromUid_APIKind() {
    String entityUid = "default/API/my-api";
    String entityRef = BackstageUtils.getEntityRefFromUid(entityUid);
    assertThat(entityRef).isEqualTo("api:default/my-api");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetEntityUidFromEntityRef() {
    String entityRef = "component:default/my-component";
    String entityUid = BackstageUtils.getEntityUidFromEntityRef(entityRef);
    assertThat(entityUid).isEqualTo("default/Component/my-component");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetEntityUidFromEntityRef_API() {
    String entityRef = "api:default/my-api";
    String entityUid = BackstageUtils.getEntityUidFromEntityRef(entityRef);
    assertThat(entityUid).isEqualTo("default/API/my-api");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetFullyQualifiedEntityRef_Complete() {
    String entityRef = "component:custom-namespace/my-component";
    String fullyQualified = BackstageUtils.getFullyQualifiedEntityRef(entityRef);
    assertThat(fullyQualified).isEqualTo("component:custom-namespace/my-component");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetFullyQualifiedEntityRef_OnlyName() {
    String entityRef = "my-component";
    String fullyQualified = BackstageUtils.getFullyQualifiedEntityRef(entityRef);
    assertThat(fullyQualified).isEqualTo("component:default/my-component");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetFullyQualifiedEntityRef_KindAndName() {
    String entityRef = "api:my-api";
    String fullyQualified = BackstageUtils.getFullyQualifiedEntityRef(entityRef);
    assertThat(fullyQualified).isEqualTo("api:default/my-api");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetFullyQualifiedEntityRef_NamespaceAndName() {
    String entityRef = "custom-namespace/my-component";
    String fullyQualified = BackstageUtils.getFullyQualifiedEntityRef(entityRef);
    assertThat(fullyQualified).isEqualTo("component:custom-namespace/my-component");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetFullyQualifiedEntityRef_EmptyString() {
    assertThatThrownBy(() -> BackstageUtils.getFullyQualifiedEntityRef(""))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("entity_ref cannot be empty");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetFullyQualifiedEntityRef_UnsupportedKind() {
    String entityRef = "unsupported-kind:default/my-entity";
    assertThatThrownBy(() -> BackstageUtils.getFullyQualifiedEntityRef(entityRef))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Kind unsupported-kind is not supported");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testConstants() {
    assertThat(BackstageUtils.DEFAULT_NAMESPACE).isEqualTo("default");
    assertThat(BackstageUtils.DEFAULT_KIND).isEqualTo("component");
    assertThat(BackstageUtils.ENTITY_REF_PATTERN).isEqualTo("%s:%s/%s");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetFullyQualifiedEntityRef_AllSupportedKinds() {
    assertThat(BackstageUtils.getFullyQualifiedEntityRef("component:default/test")).isEqualTo("component:default/test");
    assertThat(BackstageUtils.getFullyQualifiedEntityRef("api:default/test")).isEqualTo("api:default/test");
    assertThat(BackstageUtils.getFullyQualifiedEntityRef("template:default/test")).isEqualTo("template:default/test");
    assertThat(BackstageUtils.getFullyQualifiedEntityRef("user:default/test")).isEqualTo("user:default/test");
    assertThat(BackstageUtils.getFullyQualifiedEntityRef("group:default/test")).isEqualTo("group:default/test");
    assertThat(BackstageUtils.getFullyQualifiedEntityRef("system:default/test")).isEqualTo("system:default/test");
    assertThat(BackstageUtils.getFullyQualifiedEntityRef("domain:default/test")).isEqualTo("domain:default/test");
    assertThat(BackstageUtils.getFullyQualifiedEntityRef("resource:default/test")).isEqualTo("resource:default/test");
    assertThat(BackstageUtils.getFullyQualifiedEntityRef("location:default/test")).isEqualTo("location:default/test");
  }
}
