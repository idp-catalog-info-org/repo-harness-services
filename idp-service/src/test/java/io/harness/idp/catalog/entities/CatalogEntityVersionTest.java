/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.entities;

import static io.harness.rule.OwnerRule.CHRISTIAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.mongo.index.MongoIndex;
import io.harness.rule.Owner;

import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class CatalogEntityVersionTest extends CategoryTest {
  @Test
  @Owner(developers = CHRISTIAN)
  @Category(UnitTests.class)
  public void testCatalogEntityMongoIndexes() {
    List<MongoIndex> catalogEntityVersionMongoIndexes = CatalogEntityVersion.mongoIndexes();

    assertThat(catalogEntityVersionMongoIndexes.size()).isEqualTo(2);
    assertThat(catalogEntityVersionMongoIndexes.stream().map(MongoIndex::getName).collect(Collectors.toSet()).size())
        .isEqualTo(2);
    catalogEntityVersionMongoIndexes.forEach(catalogEntityMongoIndex -> {
      String name = catalogEntityMongoIndex.getName();
      if (name.startsWith("unique_")) {
        name = name.substring(7);
      }
      assertThat(catalogEntityMongoIndex.getFields().size()).isEqualTo(name.split("_").length);
    });
  }
}
