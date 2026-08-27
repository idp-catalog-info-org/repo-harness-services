/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.idp.personaview.repositories;

import static io.harness.idp.personaview.entities.PersonaViewEntity.PersonaViewEntityKeys;
import static io.harness.rule.OwnerRule.HARJAS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.personaview.entities.PersonaViewEntity;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.List;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@OwnedBy(HarnessTeam.IDP)
public class PersonaViewRepositoryCustomImplTest extends CategoryTest {
  private static final String ACCOUNT = "acc1";

  @Mock private MongoTemplate mongoTemplate;
  @InjectMocks private PersonaViewRepositoryCustomImpl repository;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  /**
   * Strict ACL: a user with no resolved groups should receive zero rows from findViewsForUser, and the
   * repository must short-circuit without ever querying Mongo (no `find` invocation). This is the regression
   * guard for the bug where views with no user_group_identifiers were leaking to every user.
   */
  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testFindViewsForUserReturnsEmptyWhenUserHasNoGroupsAndDoesNotQueryMongo() {
    List<PersonaViewEntity> result = repository.findViewsForUser(ACCOUNT, Collections.emptyList());

    assertThat(result).isEmpty();
    verify(mongoTemplate, never()).find(any(Query.class), eq(PersonaViewEntity.class));
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testFindViewsForUserReturnsEmptyWhenUserGroupsNullAndDoesNotQueryMongo() {
    List<PersonaViewEntity> result = repository.findViewsForUser(ACCOUNT, null);

    assertThat(result).isEmpty();
    verify(mongoTemplate, never()).find(any(Query.class), eq(PersonaViewEntity.class));
  }

  /**
   * The visibility filter must be a plain `userGroupIdentifiers IN [user's groups]` — no OR-branch that treats
   * empty/null/missing user_group_identifiers as "public". This is what was wrong before: the OR caused views
   * with no groups assigned to be shown to every user.
   */
  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testFindViewsForUserBuildsStrictAclQuery() {
    PersonaViewEntity entity = PersonaViewEntity.builder()
                                   .accountIdentifier(ACCOUNT)
                                   .identifier("view1")
                                   .name("View 1")
                                   .userGroupIdentifiers(List.of("g1"))
                                   .cards(List.of())
                                   .build();
    when(mongoTemplate.find(any(Query.class), eq(PersonaViewEntity.class)))
        .thenReturn(Collections.singletonList(entity));

    List<PersonaViewEntity> result = repository.findViewsForUser(ACCOUNT, List.of("g1", "g2"));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getIdentifier()).isEqualTo("view1");

    ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
    verify(mongoTemplate).find(captor.capture(), eq(PersonaViewEntity.class));
    Document queryDoc = captor.getValue().getQueryObject();

    // The query must filter on accountIdentifier AND userGroupIdentifiers IN [user's groups]. Crucially, it
    // must NOT contain a branch that matches when userGroupIdentifiers is null/missing/empty — that was the
    // source of the visibility leak.
    String json = queryDoc.toJson();
    assertThat(json).contains(PersonaViewEntityKeys.accountIdentifier);
    assertThat(json).contains(PersonaViewEntityKeys.userGroupIdentifiers);
    assertThat(json).contains("g1");
    assertThat(json).contains("g2");
    assertThat(json).doesNotContain("$exists");
    assertThat(json).doesNotContain("$size");
  }
}
