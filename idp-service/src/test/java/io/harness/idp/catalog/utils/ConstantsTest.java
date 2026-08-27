/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.utils;

import static io.harness.rule.OwnerRule.KOTA_KARTHIK;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class ConstantsTest extends CategoryTest {
  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testApiVersionConstants() {
    assertThat(Constants.HARNESS_API_VERSION).isEqualTo("harness.io/v1");
    assertThat(Constants.BACKSTAGE_API_VERSION).isEqualTo("backstage.io/v1alpha1");
    assertThat(Constants.BACKSTAGE_TEMPLATE_API_VERSION).isEqualTo("scaffolder.backstage.io/v1beta3");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testKindConstants() {
    assertThat(Constants.KIND).isEqualTo("kind");
    assertThat(Constants.COMPONENT).isEqualTo("Component");
    assertThat(Constants.TEMPLATE).isEqualTo("Template");
    assertThat(Constants.API).isEqualTo("API");
    assertThat(Constants.USER).isEqualTo("User");
    assertThat(Constants.GROUP).isEqualTo("Group");
    assertThat(Constants.RESOURCE).isEqualTo("Resource");
    assertThat(Constants.SYSTEM).isEqualTo("System");
    assertThat(Constants.AIASSET).isEqualTo("AIAsset");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testMetadataConstants() {
    assertThat(Constants.METADATA).isEqualTo("metadata");
    assertThat(Constants.ANNOTATIONS).isEqualTo("annotations");
    assertThat(Constants.SOURCE_LOCATION_ANNOTATION).isEqualTo("backstage.io/source-location");
    assertThat(Constants.MANAGED_BY_LOCATION_ANNOTATION).isEqualTo("backstage.io/managed-by-location");
    assertThat(Constants.MANAGED_BY_ORIGIN_LOCATION_ANNOTATION).isEqualTo("backstage.io/managed-by-origin-location");
    assertThat(Constants.ENTITY_UUID_ANNOTATION).isEqualTo("harness.io/entity-uuid");
    assertThat(Constants.ROLES_ANNOTATION).isEqualTo("harness.io/roles");
    assertThat(Constants.UUID).isEqualTo("uuid");
    assertThat(Constants.NAME).isEqualTo("name");
    assertThat(Constants.TITLE).isEqualTo("title");
    assertThat(Constants.NAMESPACE).isEqualTo("namespace");
    assertThat(Constants.DEFAULT_NAMESPACE).isEqualTo("default");
    assertThat(Constants.DESCRIPTION).isEqualTo("description");
    assertThat(Constants.TAGS).isEqualTo("tags");
    assertThat(Constants.METADATA_ANNOTATIONS_BACKSTAGE_IO_SOURCE_LOCATION)
        .isEqualTo("metadata.annotations.backstage\\.io/source-location");
    assertThat(Constants.STO_TEST_TARGET_ANNOTATION).isEqualTo("harness.io/sto-test-target");
    assertThat(Constants.STO).isEqualTo("sto");
    assertThat(Constants.METADATA_TAGS).isEqualTo("metadata.tags");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testStatusConstants() {
    assertThat(Constants.LEVEL).isEqualTo("level");
    assertThat(Constants.MESSAGE).isEqualTo("message");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testRelationConstants() {
    assertThat(Constants.RELATIONS).isEqualTo("relations");
    assertThat(Constants.TARGET).isEqualTo("target");
    assertThat(Constants.TARGET_REF).isEqualTo("targetRef");
    assertThat(Constants.OWNED_BY).isEqualTo("ownedBy");
    assertThat(Constants.OWNER_OF).isEqualTo("ownerOf");
    assertThat(Constants.PROVIDES_API).isEqualTo("providesApis");
    assertThat(Constants.API_PROVIDED_BY).isEqualTo("apiProvidedBy");
    assertThat(Constants.CONSUMES_API).isEqualTo("consumesApis");
    assertThat(Constants.API_CONSUMED_BY).isEqualTo("apiConsumedBy");
    assertThat(Constants.DEPENDS_ON).isEqualTo("dependsOn");
    assertThat(Constants.DEPENDENCY_OF).isEqualTo("dependencyOf");
    assertThat(Constants.PARENT_OF).isEqualTo("parentOf");
    assertThat(Constants.CHILD_OF).isEqualTo("childOf");
    assertThat(Constants.MEMBER_OF).isEqualTo("memberOf");
    assertThat(Constants.HAS_MEMBER).isEqualTo("hasMember");
    assertThat(Constants.PART_OF).isEqualTo("partOf");
    assertThat(Constants.HAS_PART).isEqualTo("hasPart");
    assertThat(Constants.SUB_COMPONENT_OF).isEqualTo("subcomponentOf");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testSpecConstants() {
    assertThat(Constants.SPEC).isEqualTo("spec");
    assertThat(Constants.TYPE).isEqualTo("type");
    assertThat(Constants.LIFECYCLE).isEqualTo("lifecycle");
    assertThat(Constants.OWNER).isEqualTo("owner");
    assertThat(Constants.SYSTEM_SPEC_RELATION_REF).isEqualTo("system");
    assertThat(Constants.PARENT).isEqualTo("parent");
    assertThat(Constants.CHILDREN).isEqualTo("children");
    assertThat(Constants.MEMBERS).isEqualTo("members");
    assertThat(Constants.PROFILE).isEqualTo("profile");
    assertThat(Constants.DISPLAY_NAME).isEqualTo("displayName");
    assertThat(Constants.EMAIL).isEqualTo("email");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testTypeConstants() {
    assertThat(Constants.API_COMPONENT_RESOURCE_TEMPLATE_TYPE).isEqualTo("api_component_resource_template");
    assertThat(Constants.USER_GROUP_TYPE).isEqualTo("user_group");
    assertThat(Constants.USER_TYPE).isEqualTo("user");
    assertThat(Constants.IS_CUSTOM_USER_GROUP).isEqualTo("isCustomUserGroup");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testMiscConstants() {
    assertThat(Constants.API_VERSION).isEqualTo("apiVersion");
    assertThat(Constants.UNSUPPORTED).isEqualTo("");
    assertThat(Constants.NAMESPACE_FOR_ENTITY_CONFLICT).isEqualTo("namespace_for_entity_conflict");
    assertThat(Constants.ENTITY_CONFLICT).isEqualTo("entity_conflict");
    assertThat(Constants.SCOPES).isEqualTo("scopes");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testCoreKindsList() {
    assertThat(Constants.CORE_KINDS).hasSize(5);
    assertThat(Constants.CORE_KINDS).containsExactly("api", "component", "resource", "system", "aiasset");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testSupportedKindsList() {
    assertThat(Constants.SUPPORTED_KINDS).hasSize(11);
    assertThat(Constants.SUPPORTED_KINDS)
        .containsExactly("api", "component", "resource", "user", "group", "workflow", "system", "environmentblueprint",
            "environment", "hierarchy", "aiasset");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testSupportedMigrateKindsList() {
    assertThat(Constants.SUPPORTED_MIGRATE_KINDS).hasSize(4);
    assertThat(Constants.SUPPORTED_MIGRATE_KINDS).containsExactly("api", "component", "resource", "workflow");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testRelationRefsList() {
    assertThat(Constants.RELATION_REFS).hasSize(19);
    assertThat(Constants.RELATION_REFS)
        .contains(Constants.PROVIDES_API, Constants.API_PROVIDED_BY, Constants.CONSUMES_API, Constants.API_CONSUMED_BY,
            Constants.DEPENDS_ON, Constants.DEPENDENCY_OF, Constants.PART_OF, Constants.HAS_PART,
            Constants.SUB_COMPONENT_OF, Constants.SYSTEM_SPEC_RELATION_REF, Constants.MEMBERS, Constants.HAS_MEMBER,
            Constants.MEMBER_OF, Constants.PARENT, Constants.CHILD_OF, Constants.PARENT_OF, Constants.LEADERS,
            Constants.HAS_LEADER, Constants.LEADER_OF);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testSourceLocationSupportedKindsList() {
    assertThat(Constants.SOURCE_LOCATION_UNSUPPORTED_KINDS).hasSize(8);
    assertThat(Constants.SOURCE_LOCATION_UNSUPPORTED_KINDS)
        .containsExactly(
            "system", "workflow", "hierarchy", "group", "user", "environment", "environmentblueprint", "aiasset");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testReferencedTypesList() {
    assertThat(Constants.REFERENCED_TYPES).hasSize(8);
    assertThat(Constants.REFERENCED_TYPES)
        .containsExactly(
            "ownerOf", "apiProvidedBy", "apiConsumedBy", "dependencyOf", "hasPart", "memberOf", "parentOf", "leaderOf");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testVersionSupportedKindsList() {
    assertThat(Constants.VERSION_SUPPORTED_KINDS).hasSize(1);
    assertThat(Constants.VERSION_SUPPORTED_KINDS).containsExactly("environmentblueprint");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testRelationRefsContainsAllRelationConstants() {
    // Ensure RELATION_REFS contains all individual relation constants
    assertThat(Constants.RELATION_REFS)
        .contains(Constants.PROVIDES_API, Constants.API_PROVIDED_BY, Constants.CONSUMES_API, Constants.API_CONSUMED_BY,
            Constants.DEPENDS_ON, Constants.DEPENDENCY_OF, Constants.PART_OF, Constants.HAS_PART,
            Constants.SUB_COMPONENT_OF, Constants.SYSTEM_SPEC_RELATION_REF, Constants.MEMBERS, Constants.HAS_MEMBER,
            Constants.MEMBER_OF, Constants.PARENT, Constants.CHILD_OF, Constants.PARENT_OF);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testListsAreImmutable() {
    // Test that the lists are immutable (will throw exception if they are not)
    assertThatThrownBy(() -> Constants.CORE_KINDS.add("test")).isInstanceOf(UnsupportedOperationException.class);

    assertThatThrownBy(() -> Constants.SUPPORTED_KINDS.add("test")).isInstanceOf(UnsupportedOperationException.class);

    assertThatThrownBy(() -> Constants.SUPPORTED_MIGRATE_KINDS.add("test"))
        .isInstanceOf(UnsupportedOperationException.class);

    assertThatThrownBy(() -> Constants.RELATION_REFS.add("test")).isInstanceOf(UnsupportedOperationException.class);

    assertThatThrownBy(() -> Constants.SOURCE_LOCATION_UNSUPPORTED_KINDS.add("test"))
        .isInstanceOf(UnsupportedOperationException.class);

    assertThatThrownBy(() -> Constants.REFERENCED_TYPES.add("test")).isInstanceOf(UnsupportedOperationException.class);

    assertThatThrownBy(() -> Constants.VERSION_SUPPORTED_KINDS.add("test"))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
