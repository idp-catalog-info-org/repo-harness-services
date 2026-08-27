/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.graph.utils;

import static io.harness.rule.OwnerRule.ANKUR;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.catalog.cache.ScopeTopology;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.InlineCatalogEntity;
import io.harness.idp.catalog.graph.utils.EntityRefResolver.BackstageRef;
import io.harness.idp.catalog.graph.utils.EntityRefResolver.ParsedEntityRef;
import io.harness.idp.catalog.graph.utils.EntityRefResolver.ScopedEntityLookup;
import io.harness.rule.Owner;

import java.util.Map;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class EntityRefResolverTest extends CategoryTest {
  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testParseHarnessEntityRef_AccountLevel() {
    Optional<ParsedEntityRef> result = EntityRefResolver.parseHarnessEntityRef("component:account/my-service");

    assertThat(result).isPresent();
    ParsedEntityRef parsed = result.get();
    assertThat(parsed.kind).isEqualTo("component");
    assertThat(parsed.org).isNull();
    assertThat(parsed.project).isNull();
    assertThat(parsed.identifier).isEqualTo("my-service");
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testParseHarnessEntityRef_OrgLevel() {
    Optional<ParsedEntityRef> result = EntityRefResolver.parseHarnessEntityRef("component:account.myorg/my-service");

    assertThat(result).isPresent();
    ParsedEntityRef parsed = result.get();
    assertThat(parsed.kind).isEqualTo("component");
    assertThat(parsed.org).isEqualTo("myorg");
    assertThat(parsed.project).isNull();
    assertThat(parsed.identifier).isEqualTo("my-service");
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testParseHarnessEntityRef_ProjectLevel() {
    Optional<ParsedEntityRef> result =
        EntityRefResolver.parseHarnessEntityRef("component:account.myorg.myproject/my-service");

    assertThat(result).isPresent();
    ParsedEntityRef parsed = result.get();
    assertThat(parsed.kind).isEqualTo("component");
    assertThat(parsed.org).isEqualTo("myorg");
    assertThat(parsed.project).isEqualTo("myproject");
    assertThat(parsed.identifier).isEqualTo("my-service");
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testParseHarnessEntityRef_InvalidFormat_NoColon() {
    Optional<ParsedEntityRef> result = EntityRefResolver.parseHarnessEntityRef("component-account/my-service");
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testParseHarnessEntityRef_InvalidFormat_NoSlash() {
    Optional<ParsedEntityRef> result = EntityRefResolver.parseHarnessEntityRef("component:account-my-service");
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testParseHarnessEntityRef_EmptyString() {
    Optional<ParsedEntityRef> result = EntityRefResolver.parseHarnessEntityRef("");
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testParseHarnessEntityRef_NullString() {
    Optional<ParsedEntityRef> result = EntityRefResolver.parseHarnessEntityRef(null);
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testParseBackstageRelationRef_Valid() {
    Optional<BackstageRef> result =
        EntityRefResolver.parseBackstageRelationRef("component:account.myorg.myproject/my-service");

    assertThat(result).isPresent();
    BackstageRef parsed = result.get();
    assertThat(parsed.kind).isEqualTo("component");
    assertThat(parsed.namespace).isEqualTo("account.myorg.myproject");
    assertThat(parsed.identifier).isEqualTo("my-service");
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testUserEntityRefValid() {
    Optional<BackstageRef> result = EntityRefResolver.parseBackstageRelationRef("user:ankur.anand@harness.io");

    assertThat(result).isPresent();
    BackstageRef parsed = result.get();
    assertThat(parsed.kind).isEqualTo("user");
    assertThat(parsed.namespace).isEqualTo("account");
    assertThat(parsed.identifier).isEqualTo("ankur.anand@harness.io");
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testParseBackstageRelationRef_AccountLevel() {
    Optional<BackstageRef> result = EntityRefResolver.parseBackstageRelationRef("api:account/payment-api");

    assertThat(result).isPresent();
    BackstageRef parsed = result.get();
    assertThat(parsed.kind).isEqualTo("api");
    assertThat(parsed.namespace).isEqualTo("account");
    assertThat(parsed.identifier).isEqualTo("payment-api");
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testParseBackstageRelationRef_InvalidFormat() {
    Optional<BackstageRef> result = EntityRefResolver.parseBackstageRelationRef("invalid-ref");
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testConvertToQueryableEntityRef_FromBackstageRef() {
    String queryableEntityRef =
        EntityRefResolver.convertToQueryableEntityRef("component:account.myorg.myproject/my-service");

    assertThat(queryableEntityRef).isEqualTo("account.myorg.myproject/component/my-service");
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testConvertToQueryableEntityRef_AccountLevel() {
    String queryableEntityRef = EntityRefResolver.convertToQueryableEntityRef("component:account/my-service");

    assertThat(queryableEntityRef).isEqualTo("account/component/my-service");
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testConvertToQueryableEntityRef_InvalidRef() {
    String queryableEntityRef = EntityRefResolver.convertToQueryableEntityRef("invalid-ref");
    assertThat(queryableEntityRef).isNull();
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testConvertToQueryableEntityRef_FromParsedEntityRef() {
    ParsedEntityRef parsed = new ParsedEntityRef("component", "myorg", "myproject", "my-service");
    String queryableEntityRef = EntityRefResolver.convertToQueryableEntityRef(parsed);

    assertThat(queryableEntityRef).isEqualTo("account.myorg.myproject/component/my-service");
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testConvertToQueryableEntityRef_FromParsedEntityRef_AccountLevel() {
    ParsedEntityRef parsed = new ParsedEntityRef("component", null, null, "my-service");
    String queryableEntityRef = EntityRefResolver.convertToQueryableEntityRef(parsed);

    assertThat(queryableEntityRef).isEqualTo("account/component/my-service");
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testBuildEntityKey_FromEntity() {
    CatalogEntity entity = InlineCatalogEntity.builder().kind("component").identifier("my-service").build();

    String key = EntityRefResolver.buildEntityKey(entity);

    assertThat(key).isEqualTo("component:my-service");
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testBuildEntityKey_FromStrings() {
    String key = EntityRefResolver.buildEntityKey("component", "my-service");

    assertThat(key).isEqualTo("component:my-service");
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testBuildEntityKey_CaseInsensitive() {
    String key = EntityRefResolver.buildEntityKey("Component", "My-Service");

    assertThat(key).isEqualTo("component:My-Service");
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testBuildEntityRef_DelegatesToCatalogUtils() {
    CatalogEntity entity = InlineCatalogEntity.builder()
                               .kind("component")
                               .identifier("my-service")
                               .accountIdentifier("acc1")
                               .orgIdentifier("org1")
                               .projectIdentifier("proj1")
                               .build();

    String ref = EntityRefResolver.buildEntityRef(entity);

    // CatalogUtils.entityRef format: "kind:account[.org[.project]]/identifier"
    assertThat(ref).isEqualTo("component:account.org1.proj1/my-service");
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testBuildEntityRef_AccountLevel() {
    CatalogEntity entity =
        InlineCatalogEntity.builder().kind("component").identifier("my-service").accountIdentifier("acc1").build();

    String ref = EntityRefResolver.buildEntityRef(entity);

    assertThat(ref).isEqualTo("component:account/my-service");
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testParseRelationRefToLookup_AccountLevel() {
    ScopeTopology topology = buildTestTopology();
    Optional<ScopedEntityLookup> result = EntityRefResolver.parseRelationRefToLookup(
        "component:account/my-service", topology::resolveNamespaceToUniqueId);

    assertThat(result).isPresent();
    ScopedEntityLookup lookup = result.get();
    assertThat(lookup.kind).isEqualTo("component");
    assertThat(lookup.parentUniqueId).isEqualTo("acc1");
    assertThat(lookup.identifier).isEqualTo("my-service");
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testParseRelationRefToLookup_OrgLevel() {
    ScopeTopology topology = buildTestTopology();
    Optional<ScopedEntityLookup> result = EntityRefResolver.parseRelationRefToLookup(
        "component:account.myorg/my-service", topology::resolveNamespaceToUniqueId);

    assertThat(result).isPresent();
    ScopedEntityLookup lookup = result.get();
    assertThat(lookup.kind).isEqualTo("component");
    assertThat(lookup.parentUniqueId).isEqualTo("orgUid1");
    assertThat(lookup.identifier).isEqualTo("my-service");
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testParseRelationRefToLookup_ProjectLevel() {
    ScopeTopology topology = buildTestTopology();
    Optional<ScopedEntityLookup> result = EntityRefResolver.parseRelationRefToLookup(
        "component:account.myorg.myproject/my-service", topology::resolveNamespaceToUniqueId);

    assertThat(result).isPresent();
    ScopedEntityLookup lookup = result.get();
    assertThat(lookup.kind).isEqualTo("component");
    assertThat(lookup.parentUniqueId).isEqualTo("projUid1");
    assertThat(lookup.identifier).isEqualTo("my-service");
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testParseRelationRefToLookup_GroupWithNoSlash() {
    ScopeTopology topology = buildTestTopology();
    Optional<ScopedEntityLookup> result =
        EntityRefResolver.parseRelationRefToLookup("group:my-team", topology::resolveNamespaceToUniqueId);

    assertThat(result).isPresent();
    ScopedEntityLookup lookup = result.get();
    assertThat(lookup.kind).isEqualTo("group");
    assertThat(lookup.parentUniqueId).isEqualTo("acc1");
    assertThat(lookup.identifier).isEqualTo("my-team");
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testParseRelationRefToLookup_InvalidRef() {
    ScopeTopology topology = buildTestTopology();
    Optional<ScopedEntityLookup> result =
        EntityRefResolver.parseRelationRefToLookup("invalid-ref", topology::resolveNamespaceToUniqueId);
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testParseRelationRefToLookup_NullInput() {
    ScopeTopology topology = buildTestTopology();
    Optional<ScopedEntityLookup> result =
        EntityRefResolver.parseRelationRefToLookup(null, topology::resolveNamespaceToUniqueId);
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testParseRelationRefToLookup_EmptyInput() {
    ScopeTopology topology = buildTestTopology();
    Optional<ScopedEntityLookup> result =
        EntityRefResolver.parseRelationRefToLookup("", topology::resolveNamespaceToUniqueId);
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testParseRelationRefToLookup_KindIsLowercased() {
    ScopeTopology topology =
        ScopeTopology.builder()
            .accountUniqueId("acc1")
            .orgs(Map.of("org1",
                ScopeTopology.OrgNode.builder().uniqueId("orgUid1").projects(Map.of("proj1", "projUid1")).build()))
            .build();
    Optional<ScopedEntityLookup> result = EntityRefResolver.parseRelationRefToLookup(
        "Component:account.org1.proj1/my-service", topology::resolveNamespaceToUniqueId);

    assertThat(result).isPresent();
    ScopedEntityLookup lookup = result.get();
    assertThat(lookup.kind).isEqualTo("component");
    assertThat(lookup.parentUniqueId).isEqualTo("projUid1");
    assertThat(lookup.identifier).isEqualTo("my-service");
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testParseRelationRefToLookup_UserWithImplicitAccountScope() {
    ScopeTopology topology = buildTestTopology();
    Optional<ScopedEntityLookup> result =
        EntityRefResolver.parseRelationRefToLookup("user:john.doe", topology::resolveNamespaceToUniqueId);

    assertThat(result).isPresent();
    ScopedEntityLookup lookup = result.get();
    assertThat(lookup.kind).isEqualTo("user");
    assertThat(lookup.parentUniqueId).isEqualTo("acc1");
    assertThat(lookup.identifier).isEqualTo("john.doe");
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testParseRelationRefToLookup_UnresolvableNamespace() {
    ScopeTopology topology = buildTestTopology();
    Optional<ScopedEntityLookup> result = EntityRefResolver.parseRelationRefToLookup(
        "component:account.unknownOrg/my-service", topology::resolveNamespaceToUniqueId);

    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testScopedEntityLookupFieldsAreCorrectlyStored() {
    ScopedEntityLookup lookup = new ScopedEntityLookup("parentUid123", "system", "my-identifier");

    assertThat(lookup.parentUniqueId).isEqualTo("parentUid123");
    assertThat(lookup.kind).isEqualTo("system");
    assertThat(lookup.identifier).isEqualTo("my-identifier");
  }

  private ScopeTopology buildTestTopology() {
    return ScopeTopology.builder()
        .accountUniqueId("acc1")
        .orgs(Map.of("myorg",
            ScopeTopology.OrgNode.builder().uniqueId("orgUid1").projects(Map.of("myproject", "projUid1")).build()))
        .build();
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testScopedEntityLookupAllowsNullParentUniqueId() {
    ScopedEntityLookup lookup = new ScopedEntityLookup(null, "group", "_account_all_users");

    assertThat(lookup.parentUniqueId).isNull();
    assertThat(lookup.kind).isEqualTo("group");
    assertThat(lookup.identifier).isEqualTo("_account_all_users");
  }
}
