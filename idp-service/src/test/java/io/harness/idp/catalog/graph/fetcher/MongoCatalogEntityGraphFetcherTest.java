/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.graph.fetcher;

import static io.harness.rule.OwnerRule.ANKUR;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.catalog.beans.ReferenceType;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.CatalogEntity.CatalogKeys;
import io.harness.idp.catalog.entities.InlineCatalogEntity;
import io.harness.idp.catalog.graph.utils.EntityRefResolver.ScopedEntityLookup;
import io.harness.rule.Owner;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.bson.Document;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class MongoCatalogEntityGraphFetcherTest extends CategoryTest {
  static final String PARENT_UNIQUE_ID = "parentUniqueId123";
  static final String ORG_PARENT_UNIQUE_ID = "orgUniqueId456";
  static final String PROJECT_PARENT_UNIQUE_ID = "projectUniqueId789";

  AutoCloseable openMocks;

  @Mock MongoTemplate mongoTemplate;

  MongoCatalogEntityGraphFetcher fetcher;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
    fetcher = new MongoCatalogEntityGraphFetcher(mongoTemplate);
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testFindRootEntityBuildsScopedQueryAndReturnsEntity() {
    CatalogEntity entity = createEntity("Component", "payment-service", null);
    when(mongoTemplate.findOne(any(Query.class), eq(CatalogEntity.class))).thenReturn(entity);

    Optional<CatalogEntity> result = fetcher.findRootEntity(PARENT_UNIQUE_ID, "Component", "payment-service");

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
    verify(mongoTemplate).findOne(queryCaptor.capture(), eq(CatalogEntity.class));

    Document queryObject = queryCaptor.getValue().getQueryObject();
    assertThat(result).contains(entity);
    assertThat(queryObject.get(CatalogKeys.parentUniqueId)).isEqualTo(PARENT_UNIQUE_ID);
    assertThat(queryObject.get(CatalogKeys.kind)).isEqualTo("Component");
    assertThat(queryObject.get(CatalogKeys.identifier)).isEqualTo("payment-service");
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testFindRootEntityReturnsEmptyWhenMongoReturnsNull() {
    when(mongoTemplate.findOne(any(Query.class), eq(CatalogEntity.class))).thenReturn(null);

    Optional<CatalogEntity> result = fetcher.findRootEntity(PARENT_UNIQUE_ID, "Component", "missing-service");

    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testFetchByScopedLookupsReturnsEmptyWhenInputMissing() {
    assertThat(fetcher.fetchByScopedLookups(null)).isEmpty();
    assertThat(fetcher.fetchByScopedLookups(List.of())).isEmpty();

    verifyNoInteractions(mongoTemplate);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testFetchByScopedLookupsQueriesByParentUniqueId() {
    CatalogEntity component = createEntity("component", "payment-service", null);
    CatalogEntity api = createEntity("API", "payment-api", null);

    List<ScopedEntityLookup> lookups =
        List.of(new ScopedEntityLookup(PROJECT_PARENT_UNIQUE_ID, "component", "payment-service"),
            new ScopedEntityLookup(PARENT_UNIQUE_ID, "API", "payment-api"));

    when(mongoTemplate.find(any(Query.class), eq(CatalogEntity.class))).thenReturn(List.of(component, api));

    Map<String, CatalogEntity> result = fetcher.fetchByScopedLookups(lookups);

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
    verify(mongoTemplate).find(queryCaptor.capture(), eq(CatalogEntity.class));

    Document queryObject = queryCaptor.getValue().getQueryObject();
    @SuppressWarnings("unchecked") List<Document> orClauses = (List<Document>) queryObject.get("$or");
    assertThat(orClauses).hasSize(2);

    Document firstClause = orClauses.get(0);
    assertThat(firstClause.get(CatalogKeys.parentUniqueId)).isEqualTo(PROJECT_PARENT_UNIQUE_ID);
    assertThat(firstClause.get(CatalogKeys.kind)).isEqualTo("component");
    assertThat(firstClause.get(CatalogKeys.identifier)).isEqualTo("payment-service");

    Document secondClause = orClauses.get(1);
    assertThat(secondClause.get(CatalogKeys.parentUniqueId)).isEqualTo(PARENT_UNIQUE_ID);
    assertThat(secondClause.get(CatalogKeys.kind)).isEqualTo("API");
    assertThat(secondClause.get(CatalogKeys.identifier)).isEqualTo("payment-api");

    assertThat(result).containsEntry("component:payment-service", component).containsEntry("api:payment-api", api);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testFetchByScopedLookupsSingleAccountScopedEntity() {
    CatalogEntity group = createEntity("group", "_account_all_users", null);

    List<ScopedEntityLookup> lookups = List.of(new ScopedEntityLookup(PARENT_UNIQUE_ID, "group", "_account_all_users"));

    when(mongoTemplate.find(any(Query.class), eq(CatalogEntity.class))).thenReturn(List.of(group));

    Map<String, CatalogEntity> result = fetcher.fetchByScopedLookups(lookups);

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
    verify(mongoTemplate).find(queryCaptor.capture(), eq(CatalogEntity.class));

    Document queryObject = queryCaptor.getValue().getQueryObject();
    @SuppressWarnings("unchecked") List<Document> orClauses = (List<Document>) queryObject.get("$or");
    assertThat(orClauses).hasSize(1);

    Document clause = orClauses.get(0);
    assertThat(clause.get(CatalogKeys.parentUniqueId)).isEqualTo(PARENT_UNIQUE_ID);
    assertThat(clause.get(CatalogKeys.kind)).isEqualTo("group");
    assertThat(clause.get(CatalogKeys.identifier)).isEqualTo("_account_all_users");

    assertThat(result).containsEntry("group:_account_all_users", group);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testFetchByScopedLookupsMixedScopesAccountAndProject() {
    CatalogEntity component = createEntityWithScope("component", "IDP_App", "org1", "proj1");
    CatalogEntity group = createEntity("group", "_account_all_users", null);
    CatalogEntity system = createEntityWithScope("system", "IDP_System", "org1", "proj1");

    List<ScopedEntityLookup> lookups = List.of(new ScopedEntityLookup(PROJECT_PARENT_UNIQUE_ID, "component", "IDP_App"),
        new ScopedEntityLookup(PARENT_UNIQUE_ID, "group", "_account_all_users"),
        new ScopedEntityLookup(PROJECT_PARENT_UNIQUE_ID, "system", "IDP_System"));

    when(mongoTemplate.find(any(Query.class), eq(CatalogEntity.class))).thenReturn(List.of(component, group, system));

    Map<String, CatalogEntity> result = fetcher.fetchByScopedLookups(lookups);

    assertThat(result).hasSize(3);
    assertThat(result).containsEntry("component:IDP_App", component);
    assertThat(result).containsEntry("group:_account_all_users", group);
    assertThat(result).containsEntry("system:IDP_System", system);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testFetchByScopedLookupsReturnsEmptyMapWhenNoMatch() {
    List<ScopedEntityLookup> lookups =
        List.of(new ScopedEntityLookup(PROJECT_PARENT_UNIQUE_ID, "component", "non-existent"));

    when(mongoTemplate.find(any(Query.class), eq(CatalogEntity.class))).thenReturn(List.of());

    Map<String, CatalogEntity> result = fetcher.fetchByScopedLookups(lookups);

    assertThat(result).isEmpty();
    verify(mongoTemplate).find(any(Query.class), eq(CatalogEntity.class));
  }

  private InlineCatalogEntity createEntityWithScope(
      String kind, String identifier, String orgIdentifier, String projectIdentifier) {
    return InlineCatalogEntity.builder()
        .accountIdentifier("accountId")
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .identifier(identifier)
        .referenceType(ReferenceType.INLINE)
        .apiVersion("harness.io/v1")
        .kind(kind)
        .yaml("yaml")
        .build();
  }

  private InlineCatalogEntity createEntity(String kind, String identifier, String queryableEntityRef) {
    return InlineCatalogEntity.builder()
        .accountIdentifier("accountId")
        .identifier(identifier)
        .referenceType(ReferenceType.INLINE)
        .apiVersion("harness.io/v1")
        .kind(kind)
        .queryableEntityRef(queryableEntityRef)
        .yaml("yaml")
        .build();
  }
}
