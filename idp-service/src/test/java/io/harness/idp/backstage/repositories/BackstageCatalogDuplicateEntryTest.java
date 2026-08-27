/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.backstage.repositories;

import static io.harness.rule.OwnerRule.KOTA_KARTHIK;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class BackstageCatalogDuplicateEntryTest extends CategoryTest {
  private static final String TEST_ACCOUNT_ID = "test-account-123";
  private static final String TEST_ENTITY_UID = "default/Component/my-service";

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testBuilder() {
    List<String> duplicates = Arrays.asList("id1", "id2", "id3");

    BackstageCatalogDuplicateEntry entry = BackstageCatalogDuplicateEntry.builder()
                                               .accountIdentifier(TEST_ACCOUNT_ID)
                                               .entityUid(TEST_ENTITY_UID)
                                               .duplicates(duplicates)
                                               .build();

    assertThat(entry).isNotNull();
    assertThat(entry.getAccountIdentifier()).isEqualTo(TEST_ACCOUNT_ID);
    assertThat(entry.getEntityUid()).isEqualTo(TEST_ENTITY_UID);
    assertThat(entry.getDuplicates()).hasSize(3);
    assertThat(entry.getDuplicates()).containsExactly("id1", "id2", "id3");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testBuilderWithEmptyDuplicates() {
    BackstageCatalogDuplicateEntry entry = BackstageCatalogDuplicateEntry.builder()
                                               .accountIdentifier(TEST_ACCOUNT_ID)
                                               .entityUid(TEST_ENTITY_UID)
                                               .duplicates(Collections.emptyList())
                                               .build();

    assertThat(entry).isNotNull();
    assertThat(entry.getDuplicates()).isEmpty();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testBuilderWithNullDuplicates() {
    BackstageCatalogDuplicateEntry entry = BackstageCatalogDuplicateEntry.builder()
                                               .accountIdentifier(TEST_ACCOUNT_ID)
                                               .entityUid(TEST_ENTITY_UID)
                                               .duplicates(null)
                                               .build();

    assertThat(entry).isNotNull();
    assertThat(entry.getDuplicates()).isNull();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetters() {
    List<String> duplicates = Arrays.asList("id1", "id2");

    BackstageCatalogDuplicateEntry entry = BackstageCatalogDuplicateEntry.builder()
                                               .accountIdentifier(TEST_ACCOUNT_ID)
                                               .entityUid(TEST_ENTITY_UID)
                                               .duplicates(duplicates)
                                               .build();

    assertThat(entry.getAccountIdentifier()).isEqualTo(TEST_ACCOUNT_ID);
    assertThat(entry.getEntityUid()).isEqualTo(TEST_ENTITY_UID);
    assertThat(entry.getDuplicates()).isEqualTo(duplicates);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testMultipleDuplicates() {
    List<String> manyDuplicates = Arrays.asList("id1", "id2", "id3", "id4", "id5");

    BackstageCatalogDuplicateEntry entry = BackstageCatalogDuplicateEntry.builder()
                                               .accountIdentifier(TEST_ACCOUNT_ID)
                                               .entityUid(TEST_ENTITY_UID)
                                               .duplicates(manyDuplicates)
                                               .build();

    assertThat(entry.getDuplicates()).hasSize(5);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testDifferentAccountIdentifiers() {
    BackstageCatalogDuplicateEntry entry1 = BackstageCatalogDuplicateEntry.builder()
                                                .accountIdentifier("account1")
                                                .entityUid(TEST_ENTITY_UID)
                                                .duplicates(Arrays.asList("id1"))
                                                .build();

    BackstageCatalogDuplicateEntry entry2 = BackstageCatalogDuplicateEntry.builder()
                                                .accountIdentifier("account2")
                                                .entityUid(TEST_ENTITY_UID)
                                                .duplicates(Arrays.asList("id2"))
                                                .build();

    assertThat(entry1.getAccountIdentifier()).isNotEqualTo(entry2.getAccountIdentifier());
  }
}
