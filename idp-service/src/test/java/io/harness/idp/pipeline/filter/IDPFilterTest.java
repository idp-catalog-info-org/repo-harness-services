/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.pipeline.filter;

import static io.harness.rule.OwnerRule.KOTA_KARTHIK;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class IDPFilterTest extends CategoryTest {
  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testBuilder() {
    Set<String> repoNames = new HashSet<>();
    repoNames.add("repo1");
    repoNames.add("repo2");

    IDPFilter filter = IDPFilter.builder().repoNames(repoNames).build();

    assertNotNull(filter);
    assertNotNull(filter.getRepoNames());
    assertEquals(2, filter.getRepoNames().size());
    assertTrue(filter.getRepoNames().contains("repo1"));
    assertTrue(filter.getRepoNames().contains("repo2"));
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testAddRepoNames_NullRepoNames() {
    IDPFilter filter = IDPFilter.builder().build();

    Set<String> newRepos = new HashSet<>();
    newRepos.add("repo3");
    filter.addRepoNames(newRepos);

    assertNotNull(filter.getRepoNames());
    assertEquals(1, filter.getRepoNames().size());
    assertTrue(filter.getRepoNames().contains("repo3"));
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testAddRepoNames_ExistingRepoNames() {
    Set<String> initialRepos = new HashSet<>();
    initialRepos.add("repo1");

    IDPFilter filter = IDPFilter.builder().repoNames(initialRepos).build();

    Set<String> newRepos = new HashSet<>();
    newRepos.add("repo2");
    newRepos.add("repo3");
    filter.addRepoNames(newRepos);

    assertNotNull(filter.getRepoNames());
    assertEquals(3, filter.getRepoNames().size());
    assertTrue(filter.getRepoNames().contains("repo1"));
    assertTrue(filter.getRepoNames().contains("repo2"));
    assertTrue(filter.getRepoNames().contains("repo3"));
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testAddRepoNames_DuplicateRepoNames() {
    Set<String> initialRepos = new HashSet<>();
    initialRepos.add("repo1");

    IDPFilter filter = IDPFilter.builder().repoNames(initialRepos).build();

    Set<String> newRepos = new HashSet<>();
    newRepos.add("repo1");
    newRepos.add("repo2");
    filter.addRepoNames(newRepos);

    assertNotNull(filter.getRepoNames());
    assertEquals(2, filter.getRepoNames().size());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testAddRepoNames_EmptySet() {
    Set<String> initialRepos = new HashSet<>();
    initialRepos.add("repo1");

    IDPFilter filter = IDPFilter.builder().repoNames(initialRepos).build();

    Set<String> emptyRepos = new HashSet<>();
    filter.addRepoNames(emptyRepos);

    assertNotNull(filter.getRepoNames());
    assertEquals(1, filter.getRepoNames().size());
    assertTrue(filter.getRepoNames().contains("repo1"));
  }
}
