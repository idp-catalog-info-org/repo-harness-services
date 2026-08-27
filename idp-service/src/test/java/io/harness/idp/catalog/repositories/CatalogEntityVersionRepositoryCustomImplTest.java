/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.repositories;

import static io.harness.rule.OwnerRule.CHRISTIAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.catalog.entities.CatalogEntityVersion;
import io.harness.rule.Owner;
import io.harness.springdata.TransactionHelper;

import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class CatalogEntityVersionRepositoryCustomImplTest extends CategoryTest {
  public static final String TEST_ENTITY_ID = "entityId";
  public static final String TEST_ENTITY_ID_2 = "entityID2";
  AutoCloseable openMocks;

  @InjectMocks private CatalogEntityVersionRepositoryCustomImpl catalogEntityVersionRepositoryCustom;

  @Mock private MongoTemplate mongoTemplate;
  @Mock TransactionHelper transactionHelper;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }

  @Test
  @Owner(developers = CHRISTIAN)
  @Category(UnitTests.class)
  public void testGetStableVersionForEntityEmpty() {
    when(mongoTemplate.findOne(any(Query.class), eq(CatalogEntityVersion.class))).thenReturn(null);
    Optional<CatalogEntityVersion> optionalCatalogEntityVersion =
        catalogEntityVersionRepositoryCustom.getStableVersionForEntity(TEST_ENTITY_ID);

    assertThat(optionalCatalogEntityVersion.isEmpty()).isTrue();
  }

  @Test
  @Owner(developers = CHRISTIAN)
  @Category(UnitTests.class)
  public void testGetStableVersionForEntityPresent() {
    CatalogEntityVersion catalogEntityVersion =
        CatalogEntityVersion.builder().entityId(TEST_ENTITY_ID).stable(true).build();
    when(mongoTemplate.findOne(any(Query.class), eq(CatalogEntityVersion.class))).thenReturn(catalogEntityVersion);
    Optional<CatalogEntityVersion> foundCatalogEntityVersion =
        catalogEntityVersionRepositoryCustom.getStableVersionForEntity(TEST_ENTITY_ID);

    assertThat(foundCatalogEntityVersion.isEmpty()).isFalse();
    assertThat(foundCatalogEntityVersion.get()).isEqualTo(catalogEntityVersion);
  }

  @Test
  @Owner(developers = CHRISTIAN)
  @Category(UnitTests.class)
  public void testFindByEntityIdWhenEmpty() {
    Page<CatalogEntityVersion> catalogEntityVersions =
        catalogEntityVersionRepositoryCustom.findByEntityId(TEST_ENTITY_ID, null, null, null, null);
    assertThat(catalogEntityVersions.getTotalElements()).isEqualTo(0);
    assertThat(catalogEntityVersions.getContent()).isEqualTo(List.of());
  }

  @Test
  @Owner(developers = CHRISTIAN)
  @Category(UnitTests.class)
  public void testFindByEntityId() {
    List<CatalogEntityVersion> catalogEntityVersions = List.of(CatalogEntityVersion.builder().build());
    when(mongoTemplate.count(any(Query.class), eq(CatalogEntityVersion.class))).thenReturn(4L);
    when(mongoTemplate.find(any(Query.class), eq(CatalogEntityVersion.class))).thenReturn(catalogEntityVersions);

    Page<CatalogEntityVersion> catalogEntityVersionPage =
        catalogEntityVersionRepositoryCustom.findByEntityId(TEST_ENTITY_ID, 0, 10, null, null);

    assertThat(catalogEntityVersionPage).isNotEmpty();
    assertThat(catalogEntityVersionPage.getTotalElements()).isEqualTo(1);
    assertThat(catalogEntityVersionPage.getContent()).isEqualTo(catalogEntityVersions);
    assertThat(catalogEntityVersionPage.getPageable().getPageNumber()).isEqualTo(0);
    assertThat(catalogEntityVersionPage.getPageable().getPageSize()).isEqualTo(10);
  }

  @Test
  @Owner(developers = CHRISTIAN)
  @Category(UnitTests.class)
  public void testCreateCatalogEntityVersionWithStableAndStableExisting() {
    CatalogEntityVersion catalogEntityVersion =
        CatalogEntityVersion.builder().entityId(TEST_ENTITY_ID).stable(true).build();
    CatalogEntityVersion catalogEntityVersionNotStable =
        CatalogEntityVersion.builder().entityId(TEST_ENTITY_ID).stable(false).build();
    Update update = new Update().unset("stable");
    Query query = new Query().addCriteria(Criteria.where("stable").is(true));
    Query getExistingQuery =
        new Query().addCriteria(Criteria.where("stable").is(true).and("entity_id").is(TEST_ENTITY_ID));
    when(mongoTemplate.findOne(eq(getExistingQuery), eq(CatalogEntityVersion.class))).thenReturn(catalogEntityVersion);

    CatalogEntityVersion catalogEntityVersion2 =
        CatalogEntityVersion.builder().entityId(TEST_ENTITY_ID_2).stable(true).build();
    when(mongoTemplate.findAndModify(eq(query), eq(update), eq(CatalogEntityVersion.class)))
        .thenReturn(catalogEntityVersionNotStable);
    when(mongoTemplate.insert(catalogEntityVersion2)).thenReturn(catalogEntityVersion2);

    when(transactionHelper.performTransaction(any())).thenAnswer(invocation -> {
      TransactionHelper.TransactionFunction<?> function = invocation.getArgument(0);
      return function.execute();
    });

    CatalogEntityVersion created =
        catalogEntityVersionRepositoryCustom.createCatalogEntityVersionAndSyncStable(catalogEntityVersion2);
    assertThat(created).isEqualTo(catalogEntityVersion2);
  }

  @Test
  @Owner(developers = CHRISTIAN)
  @Category(UnitTests.class)
  public void testCreateCatalogEntityVersionWithStableAndStableNotExisting() {
    CatalogEntityVersion catalogEntityVersion =
        CatalogEntityVersion.builder().entityId(TEST_ENTITY_ID).stable(false).build();
    Query query = new Query().addCriteria(Criteria.where("stable").is(true).and("entity_id").is(TEST_ENTITY_ID));
    when(mongoTemplate.findOne(eq(query), eq(CatalogEntityVersion.class))).thenReturn(catalogEntityVersion);

    CatalogEntityVersion catalogEntityVersion2Stable =
        CatalogEntityVersion.builder().entityId(TEST_ENTITY_ID_2).stable(true).build();
    when(mongoTemplate.insert(catalogEntityVersion2Stable)).thenReturn(catalogEntityVersion2Stable);

    when(transactionHelper.performTransaction(any())).thenAnswer(invocation -> {
      TransactionHelper.TransactionFunction<?> function = invocation.getArgument(0);
      return function.execute();
    });

    CatalogEntityVersion created =
        catalogEntityVersionRepositoryCustom.createCatalogEntityVersionAndSyncStable(catalogEntityVersion2Stable);
    assertThat(created).isEqualTo(catalogEntityVersion2Stable);
  }

  @Test
  @Owner(developers = CHRISTIAN)
  @Category(UnitTests.class)
  public void testCreateCatalogEntityVersionStableNotExisting() {
    CatalogEntityVersion catalogEntityVersion =
        CatalogEntityVersion.builder().entityId(TEST_ENTITY_ID).stable(false).build();
    Query query = new Query().addCriteria(Criteria.where("stable").is(true).and("entity_id").is(TEST_ENTITY_ID));
    when(mongoTemplate.findOne(eq(query), eq(CatalogEntityVersion.class))).thenReturn(catalogEntityVersion);

    CatalogEntityVersion catalogEntityVersion2 = CatalogEntityVersion.builder().entityId(TEST_ENTITY_ID_2).build();
    CatalogEntityVersion catalogEntityVersion2Stable =
        CatalogEntityVersion.builder().entityId(TEST_ENTITY_ID_2).stable(true).build();
    when(mongoTemplate.insert(catalogEntityVersion2Stable)).thenReturn(catalogEntityVersion2Stable);

    when(transactionHelper.performTransaction(any())).thenAnswer(invocation -> {
      TransactionHelper.TransactionFunction<?> function = invocation.getArgument(0);
      return function.execute();
    });

    CatalogEntityVersion res =
        catalogEntityVersionRepositoryCustom.createCatalogEntityVersionAndSyncStable(catalogEntityVersion2);

    assertThat(res).isEqualTo(catalogEntityVersion2Stable);
  }

  @Test
  @Owner(developers = CHRISTIAN)
  @Category(UnitTests.class)
  public void testCreateCatalogEntityVersionStableExisting() {
    CatalogEntityVersion catalogEntityVersion =
        CatalogEntityVersion.builder().entityId(TEST_ENTITY_ID).stable(true).build();
    Query query = new Query().addCriteria(Criteria.where("stable").is(true).and("entity_id").is(TEST_ENTITY_ID));
    when(mongoTemplate.findOne(eq(query), eq(CatalogEntityVersion.class))).thenReturn(catalogEntityVersion);

    CatalogEntityVersion catalogEntityVersion2 = CatalogEntityVersion.builder().entityId(TEST_ENTITY_ID_2).build();
    CatalogEntityVersion catalogEntityVersion2Stable =
        CatalogEntityVersion.builder().entityId(TEST_ENTITY_ID_2).stable(true).build();
    when(mongoTemplate.insert(catalogEntityVersion2Stable)).thenReturn(catalogEntityVersion2Stable);

    when(transactionHelper.performTransaction(any())).thenAnswer(invocation -> {
      TransactionHelper.TransactionFunction<?> function = invocation.getArgument(0);
      return function.execute();
    });

    CatalogEntityVersion res =
        catalogEntityVersionRepositoryCustom.createCatalogEntityVersionAndSyncStable(catalogEntityVersion2);

    assertThat(res).isEqualTo(catalogEntityVersion2Stable);
  }
}
