/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.steps.eventlistener.entities;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.mongo.index.MongoIndex;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
public class EventListenerStepInstanceTest {
  @Test
  @Owner(developers = OwnerRule.RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testMongoIndexesContainParentUniqueIdIndex() {
    List<MongoIndex> indexes = EventListenerStepInstance.mongoIndexes();
    assertThat(indexes).isNotEmpty();
    assertThat(indexes.stream().anyMatch(index -> index.getName().contains("parentUniqueId"))).isTrue();
  }
}