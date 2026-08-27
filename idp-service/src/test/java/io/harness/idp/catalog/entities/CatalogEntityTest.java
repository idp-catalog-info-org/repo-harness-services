/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.entities;

import static io.harness.rule.OwnerRule.SATHISH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.catalog.beans.Scope;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.rule.Owner;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class CatalogEntityTest extends CategoryTest {
  public static final String TEST_ACCOUNT_IDENTIFIER = "testAccount123";
  public static final String TEST_ORG_IDENTIFIER = "testOrg123";
  public static final String TEST_PROJECT_IDENTIFIER = "testProject123";

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testCatalogEntityMongoIndexes() {
    List<MongoIndex> catalogEntityMongoIndexes = CatalogEntity.mongoIndexes();

    assertThat(catalogEntityMongoIndexes.size()).isEqualTo(4);
    assertThat(catalogEntityMongoIndexes.stream().map(MongoIndex::getName).collect(Collectors.toSet()).size())
        .isEqualTo(4);
    catalogEntityMongoIndexes.forEach(catalogEntityMongoIndex -> {
      String name = catalogEntityMongoIndex.getName();
      if (name.startsWith("unique_")) {
        name = name.substring(7);
      }
      if (name.endsWith("_collation")) {
        name = name.substring(0, name.length() - "_collation".length());
      }
      // Drop trailing "_for_<purpose>" descriptive suffix (e.g., "_for_endpoint_sync") so the
      // field-count convention still applies to indexes tagged with an ops-friendly purpose.
      int forIdx = name.indexOf("_for_");
      if (forIdx > 0) {
        name = name.substring(0, forIdx);
      }
      // SortCompoundMongoIndex keeps equality fields separate from sort fields; both count.
      int totalIndexedFields = catalogEntityMongoIndex.getFields().size();
      if (catalogEntityMongoIndex instanceof SortCompoundMongoIndex) {
        totalIndexedFields += ((SortCompoundMongoIndex) catalogEntityMongoIndex).getSortFields().size();
      }
      assertThat(totalIndexedFields).isEqualTo(name.split("_").length);
    });
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testScope() {
    InlineCatalogEntity inlineCatalogEntity = new InlineCatalogEntity();
    inlineCatalogEntity.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    assertThat(inlineCatalogEntity.getScope()).isEqualTo(Scope.ACCOUNT.name());
    inlineCatalogEntity.setOrgIdentifier(TEST_ORG_IDENTIFIER);
    assertThat(inlineCatalogEntity.getScope()).isEqualTo(Scope.ORGANIZATION.name());
    inlineCatalogEntity.setProjectIdentifier(TEST_PROJECT_IDENTIFIER);
    assertThat(inlineCatalogEntity.getScope()).isEqualTo(Scope.PROJECT.name());

    GitReferencedCatalogEntity gitReferencedCatalogEntity = new GitReferencedCatalogEntity();
    gitReferencedCatalogEntity.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    assertThat(gitReferencedCatalogEntity.getScope()).isEqualTo(Scope.ACCOUNT.name());
    gitReferencedCatalogEntity.setOrgIdentifier(TEST_ORG_IDENTIFIER);
    assertThat(gitReferencedCatalogEntity.getScope()).isEqualTo(Scope.ORGANIZATION.name());
    gitReferencedCatalogEntity.setProjectIdentifier(TEST_PROJECT_IDENTIFIER);
    assertThat(gitReferencedCatalogEntity.getScope()).isEqualTo(Scope.PROJECT.name());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testFromSpecification() {
    InlineCatalogEntity inlineCatalogEntity = new InlineCatalogEntity();
    inlineCatalogEntity.setSpec(Map.of("abc", Map.of("def", Map.of("ghi", "jkl"))));
    assertThat(inlineCatalogEntity.fromSpecification("abc")).isEqualTo(Map.of("def", Map.of("ghi", "jkl")));
    assertThat(inlineCatalogEntity.fromSpecification("abcd")).isEqualTo(null);
    assertThat(inlineCatalogEntity.fromSpecification("abc.def")).isEqualTo(Map.of("ghi", "jkl"));
    assertThat(inlineCatalogEntity.fromSpecification("abc.def.ghi")).isEqualTo("jkl");

    assertThat(inlineCatalogEntity.fromSpecification("abc", Map.class)).isEqualTo(Map.of("def", Map.of("ghi", "jkl")));
    assertThat(inlineCatalogEntity.fromSpecification("abcd", String.class)).isEqualTo(null);
    assertThat(inlineCatalogEntity.fromSpecification("abc.def", Map.class)).isEqualTo(Map.of("ghi", "jkl"));
    assertThat(inlineCatalogEntity.fromSpecification("abc.def.ghi", String.class)).isEqualTo("jkl");

    GitReferencedCatalogEntity gitReferencedCatalogEntity = new GitReferencedCatalogEntity();
    gitReferencedCatalogEntity.setSpec(Map.of("abc", Map.of("def", Map.of("ghi", "jkl"))));
    assertThat(gitReferencedCatalogEntity.fromSpecification("abc")).isEqualTo(Map.of("def", Map.of("ghi", "jkl")));
    assertThat(gitReferencedCatalogEntity.fromSpecification("abcd")).isEqualTo(null);
    assertThat(gitReferencedCatalogEntity.fromSpecification("abc.def")).isEqualTo(Map.of("ghi", "jkl"));
    assertThat(gitReferencedCatalogEntity.fromSpecification("abc.def.ghi")).isEqualTo("jkl");

    assertThat(gitReferencedCatalogEntity.fromSpecification("abc", Map.class))
        .isEqualTo(Map.of("def", Map.of("ghi", "jkl")));
    assertThat(gitReferencedCatalogEntity.fromSpecification("abcd", String.class)).isEqualTo(null);
    assertThat(gitReferencedCatalogEntity.fromSpecification("abc.def", Map.class)).isEqualTo(Map.of("ghi", "jkl"));
    assertThat(gitReferencedCatalogEntity.fromSpecification("abc.def.ghi", String.class)).isEqualTo("jkl");
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testFromMetadata() {
    InlineCatalogEntity inlineCatalogEntity = new InlineCatalogEntity();
    inlineCatalogEntity.setMetadata(Map.of("abc", Map.of("def", Map.of("ghi", "jkl"))));
    assertThat(inlineCatalogEntity.fromMetadata("abc")).isEqualTo(Map.of("def", Map.of("ghi", "jkl")));
    assertThat(inlineCatalogEntity.fromMetadata("abcd")).isEqualTo(null);
    assertThat(inlineCatalogEntity.fromMetadata("abc.def")).isEqualTo(Map.of("ghi", "jkl"));
    assertThat(inlineCatalogEntity.fromMetadata("abc.def.ghi")).isEqualTo("jkl");

    assertThat(inlineCatalogEntity.fromMetadata("abc", Map.class)).isEqualTo(Map.of("def", Map.of("ghi", "jkl")));
    assertThat(inlineCatalogEntity.fromMetadata("abcd", String.class)).isEqualTo(null);
    assertThat(inlineCatalogEntity.fromMetadata("abc.def", Map.class)).isEqualTo(Map.of("ghi", "jkl"));
    assertThat(inlineCatalogEntity.fromMetadata("abc.def.ghi", String.class)).isEqualTo("jkl");

    GitReferencedCatalogEntity gitReferencedCatalogEntity = new GitReferencedCatalogEntity();
    gitReferencedCatalogEntity.setMetadata(Map.of("abc", Map.of("def", Map.of("ghi", "jkl"))));
    assertThat(gitReferencedCatalogEntity.fromMetadata("abc")).isEqualTo(Map.of("def", Map.of("ghi", "jkl")));
    assertThat(gitReferencedCatalogEntity.fromMetadata("abcd")).isEqualTo(null);
    assertThat(gitReferencedCatalogEntity.fromMetadata("abc.def")).isEqualTo(Map.of("ghi", "jkl"));
    assertThat(gitReferencedCatalogEntity.fromMetadata("abc.def.ghi")).isEqualTo("jkl");

    assertThat(gitReferencedCatalogEntity.fromMetadata("abc", Map.class))
        .isEqualTo(Map.of("def", Map.of("ghi", "jkl")));
    assertThat(gitReferencedCatalogEntity.fromMetadata("abcd", String.class)).isEqualTo(null);
    assertThat(gitReferencedCatalogEntity.fromMetadata("abc.def", Map.class)).isEqualTo(Map.of("ghi", "jkl"));
    assertThat(gitReferencedCatalogEntity.fromMetadata("abc.def.ghi", String.class)).isEqualTo("jkl");
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetRelationsFor() {
    InlineCatalogEntity inlineCatalogEntity = new InlineCatalogEntity();
    inlineCatalogEntity.setRelations(Map.of("partOf", Set.of("api1", "project2")));
    assertThat(inlineCatalogEntity.getRelationsFor("partOf")).size().isEqualTo(2);
    assertThat(inlineCatalogEntity.getRelationsFor("partOf")).isEqualTo(Set.of("api1", "project2"));
    assertThat(inlineCatalogEntity.getRelationsFor("memberOf")).isEqualTo(null);

    GitReferencedCatalogEntity gitReferencedCatalogEntity = new GitReferencedCatalogEntity();
    gitReferencedCatalogEntity.setRelations(Map.of("ownedBy", Set.of("owner1")));
    assertThat(gitReferencedCatalogEntity.getRelationsFor("ownedBy")).size().isEqualTo(1);
    assertThat(gitReferencedCatalogEntity.getRelationsFor("ownedBy")).isEqualTo(Set.of("owner1"));
    assertThat(gitReferencedCatalogEntity.getRelationsFor("partOf")).isEqualTo(null);
  }
}
