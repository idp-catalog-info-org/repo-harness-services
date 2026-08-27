/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.common;

import static io.harness.rule.OwnerRule.NISARG;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class DiffUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testIsCollectionUpdatedWithBothNull() {
    assertThat(DiffUtils.isCollectionUpdated(null, null)).isFalse();
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testIsCollectionUpdatedWithOldNull() {
    Set<String> newCollection = new HashSet<>(Arrays.asList("item1", "item2"));
    assertThat(DiffUtils.isCollectionUpdated(null, newCollection)).isTrue();
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testIsCollectionUpdatedWithNewNull() {
    Set<String> oldCollection = new HashSet<>(Arrays.asList("item1", "item2"));
    assertThat(DiffUtils.isCollectionUpdated(oldCollection, null)).isTrue();
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testIsCollectionUpdatedWithSameElements() {
    Set<String> oldCollection = new HashSet<>(Arrays.asList("item1", "item2", "item3"));
    Set<String> newCollection = new HashSet<>(Arrays.asList("item1", "item2", "item3"));
    assertThat(DiffUtils.isCollectionUpdated(oldCollection, newCollection)).isFalse();
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testIsCollectionUpdatedWithAddedElements() {
    Set<String> oldCollection = new HashSet<>(Arrays.asList("item1", "item2"));
    Set<String> newCollection = new HashSet<>(Arrays.asList("item1", "item2", "item3"));
    assertThat(DiffUtils.isCollectionUpdated(oldCollection, newCollection)).isTrue();
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testIsCollectionUpdatedWithRemovedElements() {
    Set<String> oldCollection = new HashSet<>(Arrays.asList("item1", "item2", "item3"));
    Set<String> newCollection = new HashSet<>(Arrays.asList("item1", "item2"));
    assertThat(DiffUtils.isCollectionUpdated(oldCollection, newCollection)).isTrue();
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testIsCollectionUpdatedWithDifferentElements() {
    Set<String> oldCollection = new HashSet<>(Arrays.asList("item1", "item2"));
    Set<String> newCollection = new HashSet<>(Arrays.asList("item3", "item4"));
    assertThat(DiffUtils.isCollectionUpdated(oldCollection, newCollection)).isTrue();
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testIsCollectionUpdatedIgnoresDuplicates() {
    Set<String> oldCollection = new HashSet<>(Arrays.asList("item1", "item2"));
    Set<String> newCollection = new HashSet<>(Arrays.asList("item1", "item2"));
    // Even if we add duplicate to a set, it will be ignored
    assertThat(DiffUtils.isCollectionUpdated(oldCollection, newCollection)).isFalse();
  }
}
