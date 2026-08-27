/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.user.beans.entity;

import static io.harness.rule.OwnerRule.SATHISH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.mongo.index.MongoIndex;
import io.harness.rule.Owner;

import java.util.List;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class UserEventEntityTest extends CategoryTest {
  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testUserEventEntityMongoIndexes() {
    List<MongoIndex> userEventEntityMongoIndexes = UserEventEntity.mongoIndexes();

    assertThat(userEventEntityMongoIndexes.size()).isEqualTo(1);
    assertThat(userEventEntityMongoIndexes.stream().map(MongoIndex::getName).collect(Collectors.toSet()).size())
        .isEqualTo(1);
  }
}
