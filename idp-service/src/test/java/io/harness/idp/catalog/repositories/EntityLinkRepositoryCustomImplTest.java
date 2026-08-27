/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.repositories;

import static io.harness.rule.OwnerRule.DEVESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.catalog.entities.EntityLinks;
import io.harness.idp.catalog.entities.EntityLinks.LinkTarget;
import io.harness.rule.Owner;

import java.util.List;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class EntityLinkRepositoryCustomImplTest extends CategoryTest {
  @Mock MongoTemplate mongoTemplate;
  @InjectMocks EntityLinkRepositoryCustomImpl repository;

  AutoCloseable openMocks;

  private static final String ACCOUNT_ID = "test-account-id";

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testFindByAccountIdentifierAndTargetEntityKindAndType_returnsMatchingLinks() {
    EntityLinks link = EntityLinks.builder()
                           .accountIdentifier(ACCOUNT_ID)
                           .entityRef("workflow:account/my-workflow")
                           .targets(List.of(LinkTarget.builder().entityKind("component").entityType("service").build()))
                           .build();
    when(mongoTemplate.find(any(Query.class), eq(EntityLinks.class))).thenReturn(List.of(link));

    List<EntityLinks> result =
        repository.findByAccountIdentifierAndTargetEntityKindAndType(ACCOUNT_ID, "component", "service");

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getEntityRef()).isEqualTo("workflow:account/my-workflow");
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testFindByAccountIdentifierAndTargetEntityKindAndType_noMatches_returnsEmpty() {
    when(mongoTemplate.find(any(Query.class), eq(EntityLinks.class))).thenReturn(List.of());

    List<EntityLinks> result =
        repository.findByAccountIdentifierAndTargetEntityKindAndType(ACCOUNT_ID, "resource", "database");

    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testFindByAccountIdentifierAndTargetEntityKindAndType_passesCorrectQueryToMongo() {
    when(mongoTemplate.find(any(Query.class), eq(EntityLinks.class))).thenReturn(List.of());

    repository.findByAccountIdentifierAndTargetEntityKindAndType(ACCOUNT_ID, "component", "service");

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
    verify(mongoTemplate).find(queryCaptor.capture(), eq(EntityLinks.class));
    String queryString = queryCaptor.getValue().getQueryObject().toJson();
    assertThat(queryString).contains("accountIdentifier");
    assertThat(queryString).contains("targets");
    assertThat(queryString).contains("entityKind");
    assertThat(queryString).contains("entityType");
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testFindByAccountIdentifierAndTargetEntityKindAndType_multipleResults_allReturned() {
    EntityLinks link1 =
        EntityLinks.builder()
            .accountIdentifier(ACCOUNT_ID)
            .entityRef("workflow:account/wf1")
            .targets(List.of(LinkTarget.builder().entityKind("component").entityType("service").build()))
            .build();
    EntityLinks link2 =
        EntityLinks.builder()
            .accountIdentifier(ACCOUNT_ID)
            .entityRef("workflow:account/wf2")
            .targets(List.of(LinkTarget.builder().entityKind("component").entityType("service").build()))
            .build();
    when(mongoTemplate.find(any(Query.class), eq(EntityLinks.class))).thenReturn(List.of(link1, link2));

    List<EntityLinks> result =
        repository.findByAccountIdentifierAndTargetEntityKindAndType(ACCOUNT_ID, "component", "service");

    assertThat(result).hasSize(2);
    assertThat(result)
        .extracting(EntityLinks::getEntityRef)
        .containsExactlyInAnyOrder("workflow:account/wf1", "workflow:account/wf2");
  }
}
