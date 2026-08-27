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
import io.harness.idp.catalog.graph.utils.EntityRefResolver.ScopedEntityLookup;
import io.harness.idp.catalog.graph.utils.RelationExtractor.EdgeDescriptor;
import io.harness.idp.catalog.graph.utils.RelationExtractor.RelationBatch;
import io.harness.rule.Owner;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class RelationExtractorTest extends CategoryTest {
  private static final String ACCOUNT_UID = "acc1";
  private static final String ORG_UID = "orgUniqueId1";
  private static final String PROJECT_UID = "projUniqueId1";

  private RelationExtractor relationExtractor;
  private ScopeTopology topology;

  @Before
  public void setUp() {
    relationExtractor = new RelationExtractor();
    topology = ScopeTopology.builder()
                   .accountUniqueId(ACCOUNT_UID)
                   .orgs(Map.of("org1",
                       ScopeTopology.OrgNode.builder().uniqueId(ORG_UID).projects(Map.of("proj1", PROJECT_UID)).build(),
                       "default",
                       ScopeTopology.OrgNode.builder()
                           .uniqueId("defaultOrgUid")
                           .projects(Map.of("Commons", "commonsProjectUid"))
                           .build()))
                   .build();
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testExtractRelations_EmptyFrontier() {
    RelationBatch batch = relationExtractor.extractRelations(List.of(), Set.of("dependsOn"), Set.of("component"),
        new HashSet<>(), "acc1", topology::resolveNamespaceToUniqueId);

    assertThat(batch.getScopedEntityLookups()).isEmpty();
    assertThat(batch.getEdgeDescriptors()).isEmpty();
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testExtractRelations_NoRelations() {
    CatalogEntity entity = InlineCatalogEntity.builder()
                               .kind("component")
                               .identifier("my-service")
                               .accountIdentifier("acc1")
                               .relations(Map.of())
                               .build();

    RelationBatch batch = relationExtractor.extractRelations(List.of(entity), Set.of("dependsOn"), Set.of("component"),
        new HashSet<>(), "acc1", topology::resolveNamespaceToUniqueId);

    assertThat(batch.getScopedEntityLookups()).isEmpty();
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testExtractRelations_SingleRelation() {
    CatalogEntity sourceEntity = InlineCatalogEntity.builder()
                                     .kind("component")
                                     .identifier("service-a")
                                     .accountIdentifier("acc1")
                                     .relations(Map.of("dependsOn", Set.of("component:account/service-b")))
                                     .build();

    RelationBatch batch = relationExtractor.extractRelations(List.of(sourceEntity), Set.of("dependsOn"),
        Set.of("component"), new HashSet<>(), "acc1", topology::resolveNamespaceToUniqueId);

    assertThat(batch.getScopedEntityLookups()).hasSize(1);
    ScopedEntityLookup lookup = batch.getScopedEntityLookups().get(0);
    assertThat(lookup.kind).isEqualTo("component");
    assertThat(lookup.identifier).isEqualTo("service-b");
    assertThat(lookup.parentUniqueId).isEqualTo(ACCOUNT_UID);
    assertThat(batch.getEdgeDescriptors()).hasSize(1);
    assertThat(batch.getEdgeDescriptors().get(0).targetEntityKey()).isEqualTo("component:service-b");
    assertThat(batch.getEdgeDescriptors().get(0).relationType()).isEqualTo("dependsOn");
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testExtractRelations_FilterByRelationshipType() {
    CatalogEntity sourceEntity = InlineCatalogEntity.builder()
                                     .kind("component")
                                     .identifier("service-a")
                                     .accountIdentifier("acc1")
                                     .relations(Map.of("dependsOn", Set.of("component:account/service-b"),
                                         "providesApis", Set.of("api:account/payment-api")))
                                     .build();

    RelationBatch batch = relationExtractor.extractRelations(List.of(sourceEntity), Set.of("dependsOn"), Set.of(),
        new HashSet<>(), "acc1", topology::resolveNamespaceToUniqueId);

    assertThat(batch.getScopedEntityLookups()).hasSize(1);
    assertThat(batch.getScopedEntityLookups().get(0).kind).isEqualTo("component");
    assertThat(batch.getScopedEntityLookups().get(0).identifier).isEqualTo("service-b");
    List<String> edgeRelations = batch.getEdgeDescriptors().stream().map(EdgeDescriptor::relationType).toList();
    assertThat(edgeRelations).containsOnly("dependsOn");
    List<String> edgeTargets = batch.getEdgeDescriptors().stream().map(EdgeDescriptor::targetEntityKey).toList();
    assertThat(edgeTargets).doesNotContain("api:payment-api");
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testExtractRelations_FilterByKind() {
    CatalogEntity sourceEntity =
        InlineCatalogEntity.builder()
            .kind("component")
            .identifier("service-a")
            .accountIdentifier("acc1")
            .relations(Map.of("dependsOn",
                Set.of("component:account/service-b", "api:account/payment-api", "workflow:account/deploy-workflow")))
            .build();

    RelationBatch batch = relationExtractor.extractRelations(List.of(sourceEntity), Set.of("dependsOn"),
        Set.of("component", "api"), new HashSet<>(), "acc1", topology::resolveNamespaceToUniqueId);

    assertThat(batch.getScopedEntityLookups()).hasSize(2);
    List<String> kinds = batch.getScopedEntityLookups().stream().map(l -> l.kind).toList();
    List<String> identifiers = batch.getScopedEntityLookups().stream().map(l -> l.identifier).toList();
    assertThat(kinds).containsExactlyInAnyOrder("component", "api");
    assertThat(identifiers).containsExactlyInAnyOrder("service-b", "payment-api");
    List<String> filteredTargets = batch.getEdgeDescriptors().stream().map(EdgeDescriptor::targetEntityKey).toList();
    assertThat(filteredTargets).doesNotContain("workflow:deploy-workflow");
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testExtractRelations_SkipVisited() {
    CatalogEntity sourceEntity =
        InlineCatalogEntity.builder()
            .kind("component")
            .identifier("service-a")
            .accountIdentifier("acc1")
            .relations(Map.of("dependsOn", Set.of("component:account/service-b", "component:account/service-c")))
            .build();

    Set<String> visited = new HashSet<>(Set.of("component:service-b"));

    RelationBatch batch = relationExtractor.extractRelations(
        List.of(sourceEntity), Set.of("dependsOn"), Set.of(), visited, "acc1", topology::resolveNamespaceToUniqueId);

    assertThat(batch.getScopedEntityLookups()).hasSize(1);
    assertThat(batch.getScopedEntityLookups().get(0).identifier).isEqualTo("service-c");
    List<String> skipTargets = batch.getEdgeDescriptors().stream().map(EdgeDescriptor::targetEntityKey).toList();
    assertThat(skipTargets).contains("component:service-c");
    assertThat(skipTargets).doesNotContain("component:service-b");
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testExtractRelations_MultipleSourceEntities() {
    CatalogEntity entity1 = InlineCatalogEntity.builder()
                                .kind("component")
                                .identifier("service-a")
                                .accountIdentifier("acc1")
                                .relations(Map.of("dependsOn", Set.of("component:account/service-x")))
                                .build();

    CatalogEntity entity2 = InlineCatalogEntity.builder()
                                .kind("component")
                                .identifier("service-b")
                                .accountIdentifier("acc1")
                                .relations(Map.of("dependsOn", Set.of("component:account/service-y")))
                                .build();

    RelationBatch batch = relationExtractor.extractRelations(List.of(entity1, entity2), Set.of("dependsOn"), Set.of(),
        new HashSet<>(), "acc1", topology::resolveNamespaceToUniqueId);

    assertThat(batch.getScopedEntityLookups()).hasSize(2);
    List<String> identifiers = batch.getScopedEntityLookups().stream().map(l -> l.identifier).toList();
    assertThat(identifiers).containsExactlyInAnyOrder("service-x", "service-y");
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testExtractRelations_EmptyRelationshipTypeFilter() {
    CatalogEntity sourceEntity = InlineCatalogEntity.builder()
                                     .kind("component")
                                     .identifier("service-a")
                                     .accountIdentifier("acc1")
                                     .relations(Map.of("dependsOn", Set.of("component:account/service-b"),
                                         "providesApis", Set.of("api:account/payment-api")))
                                     .build();

    RelationBatch batch = relationExtractor.extractRelations(
        List.of(sourceEntity), Set.of(), Set.of(), new HashSet<>(), "acc1", topology::resolveNamespaceToUniqueId);

    assertThat(batch.getScopedEntityLookups()).hasSize(2);
    List<String> identifiers = batch.getScopedEntityLookups().stream().map(l -> l.identifier).toList();
    assertThat(identifiers).containsExactlyInAnyOrder("service-b", "payment-api");
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testExtractRelations_ProjectLevelScope() {
    CatalogEntity sourceEntity = InlineCatalogEntity.builder()
                                     .kind("component")
                                     .identifier("service-a")
                                     .accountIdentifier("acc1")
                                     .orgIdentifier("org1")
                                     .projectIdentifier("proj1")
                                     .relations(Map.of("dependsOn", Set.of("component:account.org1.proj1/service-b")))
                                     .build();

    RelationBatch batch = relationExtractor.extractRelations(List.of(sourceEntity), Set.of("dependsOn"),
        Set.of("component"), new HashSet<>(), "acc1", topology::resolveNamespaceToUniqueId);

    assertThat(batch.getScopedEntityLookups()).hasSize(1);
    ScopedEntityLookup lookup = batch.getScopedEntityLookups().get(0);
    assertThat(lookup.kind).isEqualTo("component");
    assertThat(lookup.identifier).isEqualTo("service-b");
    assertThat(lookup.parentUniqueId).isEqualTo(PROJECT_UID);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testExtractRelations_InvalidRelationRef() {
    CatalogEntity sourceEntity = InlineCatalogEntity.builder()
                                     .kind("component")
                                     .identifier("service-a")
                                     .accountIdentifier("acc1")
                                     .relations(Map.of("dependsOn", Set.of("invalid-ref", "component:account/valid")))
                                     .build();

    RelationBatch batch = relationExtractor.extractRelations(List.of(sourceEntity), Set.of("dependsOn"), Set.of(),
        new HashSet<>(), "acc1", topology::resolveNamespaceToUniqueId);

    assertThat(batch.getScopedEntityLookups()).hasSize(1);
    assertThat(batch.getScopedEntityLookups().get(0).identifier).isEqualTo("valid");
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testExtractRelations_EmptyStringRelationRef() {
    CatalogEntity sourceEntity = InlineCatalogEntity.builder()
                                     .kind("component")
                                     .identifier("service-a")
                                     .accountIdentifier("acc1")
                                     .relations(Map.of("dependsOn", Set.of("", "component:account/valid")))
                                     .build();

    RelationBatch batch = relationExtractor.extractRelations(List.of(sourceEntity), Set.of("dependsOn"), Set.of(),
        new HashSet<>(), "acc1", topology::resolveNamespaceToUniqueId);

    assertThat(batch.getScopedEntityLookups()).hasSize(1);
    assertThat(batch.getScopedEntityLookups().get(0).identifier).isEqualTo("valid");
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testExtractRelations_NullRelationsMap() {
    CatalogEntity sourceEntity = InlineCatalogEntity.builder()
                                     .kind("component")
                                     .identifier("service-a")
                                     .accountIdentifier("acc1")
                                     .relations(null)
                                     .build();

    RelationBatch batch = relationExtractor.extractRelations(List.of(sourceEntity), Set.of("dependsOn"),
        Set.of("component"), new HashSet<>(), "acc1", topology::resolveNamespaceToUniqueId);

    assertThat(batch.getScopedEntityLookups()).isEmpty();
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testExtractRelations_MixedScopesInSingleBatch() {
    CatalogEntity sourceEntity =
        InlineCatalogEntity.builder()
            .kind("system")
            .identifier("my-system")
            .accountIdentifier("acc1")
            .orgIdentifier("default")
            .projectIdentifier("Commons")
            .relations(Map.of("hasPart", Set.of("component:account.default.Commons/app-component"), "ownedBy",
                Set.of("group:account/_account_all_users")))
            .build();

    RelationBatch batch = relationExtractor.extractRelations(
        List.of(sourceEntity), Set.of(), Set.of(), new HashSet<>(), "acc1", topology::resolveNamespaceToUniqueId);

    assertThat(batch.getScopedEntityLookups()).hasSize(2);

    ScopedEntityLookup projectLookup =
        batch.getScopedEntityLookups().stream().filter(l -> "component".equals(l.kind)).findFirst().orElseThrow();
    assertThat(projectLookup.parentUniqueId).isEqualTo("commonsProjectUid");
    assertThat(projectLookup.identifier).isEqualTo("app-component");

    ScopedEntityLookup accountLookup =
        batch.getScopedEntityLookups().stream().filter(l -> "group".equals(l.kind)).findFirst().orElseThrow();
    assertThat(accountLookup.parentUniqueId).isEqualTo(ACCOUNT_UID);
    assertThat(accountLookup.identifier).isEqualTo("_account_all_users");
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testExtractRelations_OrgLevelScope() {
    CatalogEntity sourceEntity = InlineCatalogEntity.builder()
                                     .kind("component")
                                     .identifier("service-a")
                                     .accountIdentifier("acc1")
                                     .orgIdentifier("org1")
                                     .relations(Map.of("dependsOn", Set.of("component:account.org1/service-b")))
                                     .build();

    RelationBatch batch = relationExtractor.extractRelations(List.of(sourceEntity), Set.of("dependsOn"), Set.of(),
        new HashSet<>(), "acc1", topology::resolveNamespaceToUniqueId);

    assertThat(batch.getScopedEntityLookups()).hasSize(1);
    ScopedEntityLookup lookup = batch.getScopedEntityLookups().get(0);
    assertThat(lookup.kind).isEqualTo("component");
    assertThat(lookup.identifier).isEqualTo("service-b");
    assertThat(lookup.parentUniqueId).isEqualTo(ORG_UID);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testExtractRelations_TracksSourceEntityInBatch() {
    CatalogEntity sourceEntity = InlineCatalogEntity.builder()
                                     .kind("system")
                                     .identifier("my-system")
                                     .accountIdentifier("acc1")
                                     .relations(Map.of("hasPart", Set.of("component:account/service-a")))
                                     .build();

    RelationBatch batch = relationExtractor.extractRelations(
        List.of(sourceEntity), Set.of(), Set.of(), new HashSet<>(), "acc1", topology::resolveNamespaceToUniqueId);

    assertThat(batch.getKeyToSourceEntity()).containsKey("component:service-a");
    assertThat(batch.getKeyToSourceEntity().get("component:service-a")).isSameAs(sourceEntity);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testExtractRelations_DuplicateTargetFromMultipleSources_KeepsFirstMetadata() {
    CatalogEntity entity1 = InlineCatalogEntity.builder()
                                .kind("component")
                                .identifier("service-a")
                                .accountIdentifier("acc1")
                                .relations(Map.of("dependsOn", Set.of("component:account/shared-lib")))
                                .build();

    CatalogEntity entity2 = InlineCatalogEntity.builder()
                                .kind("component")
                                .identifier("service-b")
                                .accountIdentifier("acc1")
                                .relations(Map.of("dependsOn", Set.of("component:account/shared-lib")))
                                .build();

    RelationBatch batch = relationExtractor.extractRelations(List.of(entity1, entity2), Set.of("dependsOn"), Set.of(),
        new HashSet<>(), "acc1", topology::resolveNamespaceToUniqueId);

    assertThat(batch.getScopedEntityLookups()).hasSize(1);
    assertThat(batch.getEdgeDescriptors()).hasSize(2);
    assertThat(batch.getEdgeDescriptors())
        .extracting(EdgeDescriptor::targetEntityKey)
        .containsOnly("component:shared-lib");
    assertThat(batch.getKeyToSourceEntity()).containsKey("component:shared-lib");
    assertThat(batch.getKeyToSourceEntity().get("component:shared-lib")).isSameAs(entity1);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testExtractRelations_MultipleRelationTypesToSameTarget() {
    CatalogEntity sourceEntity =
        InlineCatalogEntity.builder()
            .kind("api")
            .identifier("cchg")
            .accountIdentifier("acc1")
            .relations(Map.of("partOf", Set.of("user:account/justin"), "ownedBy", Set.of("user:account/justin")))
            .build();

    RelationBatch batch = relationExtractor.extractRelations(
        List.of(sourceEntity), Set.of(), Set.of(), new HashSet<>(), "acc1", topology::resolveNamespaceToUniqueId);

    assertThat(batch.getScopedEntityLookups()).hasSize(1);
    assertThat(batch.getEdgeDescriptors()).hasSize(2);
    List<String> relationTypes = batch.getEdgeDescriptors().stream().map(EdgeDescriptor::relationType).toList();
    assertThat(relationTypes).containsExactlyInAnyOrder("partOf", "ownedBy");
    assertThat(batch.getEdgeDescriptors()).extracting(EdgeDescriptor::targetEntityKey).containsOnly("user:justin");
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testExtractRelations_ImplicitAccountScopeForGroupRef() {
    CatalogEntity sourceEntity = InlineCatalogEntity.builder()
                                     .kind("component")
                                     .identifier("service-a")
                                     .accountIdentifier("acc1")
                                     .relations(Map.of("ownedBy", Set.of("group:my-team")))
                                     .build();

    RelationBatch batch = relationExtractor.extractRelations(
        List.of(sourceEntity), Set.of(), Set.of(), new HashSet<>(), "acc1", topology::resolveNamespaceToUniqueId);

    assertThat(batch.getScopedEntityLookups()).hasSize(1);
    ScopedEntityLookup lookup = batch.getScopedEntityLookups().get(0);
    assertThat(lookup.kind).isEqualTo("group");
    assertThat(lookup.identifier).isEqualTo("my-team");
    assertThat(lookup.parentUniqueId).isEqualTo(ACCOUNT_UID);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testExtractRelations_UnresolvableNamespaceSkipsLookup() {
    CatalogEntity sourceEntity =
        InlineCatalogEntity.builder()
            .kind("component")
            .identifier("service-a")
            .accountIdentifier("acc1")
            .relations(Map.of("dependsOn",
                Set.of("component:account.unknownOrg.unknownProj/service-b", "component:account/service-c")))
            .build();

    RelationBatch batch = relationExtractor.extractRelations(List.of(sourceEntity), Set.of("dependsOn"), Set.of(),
        new HashSet<>(), "acc1", topology::resolveNamespaceToUniqueId);

    assertThat(batch.getScopedEntityLookups()).hasSize(1);
    assertThat(batch.getScopedEntityLookups().get(0).identifier).isEqualTo("service-c");
    assertThat(batch.getScopedEntityLookups().get(0).parentUniqueId).isEqualTo(ACCOUNT_UID);
  }
}
