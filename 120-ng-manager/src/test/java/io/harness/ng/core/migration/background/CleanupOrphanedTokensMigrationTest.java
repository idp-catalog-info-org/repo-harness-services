/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.background;

import static io.harness.rule.OwnerRule.ABHISHEK_SINGH;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.user.entities.UserMembership;
import io.harness.rule.Owner;

import java.util.stream.Stream;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@OwnedBy(HarnessTeam.PL)
public class CleanupOrphanedTokensMigrationTest extends CategoryTest {
  private static final String ACCOUNT_ID = "accountId";
  private static final String USER_ID = "userId";
  private static final String USER_ID_2 = "userId2";

  @Mock private MongoTemplate mongoTemplate;
  @InjectMocks private CleanupOrphanedTokensMigration migration;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void whenMigrate_AndAccountIdentifierIsNull_ThenTokenIsDeleted() {
    Document token = new Document("_id", "token1")
                         .append("apiKeyType", "USER")
                         .append("accountIdentifier", null)
                         .append("parentIdentifier", USER_ID);

    when(mongoTemplate.stream(any(Query.class), eq(Document.class), eq("tokens"))).thenReturn(Stream.of(token));

    migration.migrate();

    verify(mongoTemplate).remove(argThat((Query q) -> q.toString().contains("token1")), eq("tokens"));
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void whenMigrate_AndParentIdentifierIsNull_ThenTokenIsDeleted() {
    Document token = new Document("_id", "token2")
                         .append("apiKeyType", "USER")
                         .append("accountIdentifier", ACCOUNT_ID)
                         .append("parentIdentifier", null);

    when(mongoTemplate.stream(any(Query.class), eq(Document.class), eq("tokens"))).thenReturn(Stream.of(token));

    migration.migrate();

    verify(mongoTemplate).remove(argThat((Query q) -> q.toString().contains("token2")), eq("tokens"));
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void whenMigrate_AndNoAccountLevelMembershipExists_ThenTokenIsDeleted() {
    Document token = new Document("_id", "token3")
                         .append("apiKeyType", "USER")
                         .append("accountIdentifier", ACCOUNT_ID)
                         .append("parentIdentifier", USER_ID);

    when(mongoTemplate.stream(any(Query.class), eq(Document.class), eq("tokens"))).thenReturn(Stream.of(token));
    when(mongoTemplate.stream(
             argThat(q -> q != null && q.toString().contains("parentUniqueId") && q.toString().contains(ACCOUNT_ID)),
             eq(UserMembership.class)))
        .thenReturn(Stream.empty());

    migration.migrate();

    verify(mongoTemplate).remove(argThat((Query q) -> q.toString().contains("token3")), eq("tokens"));
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void whenMigrate_AndAccountLevelMembershipExists_ThenTokenIsNotDeleted() {
    Document token = new Document("_id", "token4")
                         .append("apiKeyType", "USER")
                         .append("accountIdentifier", ACCOUNT_ID)
                         .append("parentIdentifier", USER_ID);

    // For account-level membership, parentUniqueId == accountIdentifier
    UserMembership accountLevelMembership =
        UserMembership.builder().userId(USER_ID).accountIdentifier(ACCOUNT_ID).parentUniqueId(ACCOUNT_ID).build();

    when(mongoTemplate.stream(any(Query.class), eq(Document.class), eq("tokens"))).thenReturn(Stream.of(token));
    when(mongoTemplate.stream(any(Query.class), eq(UserMembership.class)))
        .thenReturn(Stream.of(accountLevelMembership));

    migration.migrate();

    verify(mongoTemplate, never()).remove(any(Query.class), eq("tokens"));
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void whenMigrate_AndBothAccountAndParentIdentifiersAreNull_ThenTokenIsDeleted() {
    Document token = new Document("_id", "token6")
                         .append("apiKeyType", "USER")
                         .append("accountIdentifier", null)
                         .append("parentIdentifier", null);

    when(mongoTemplate.stream(any(Query.class), eq(Document.class), eq("tokens"))).thenReturn(Stream.of(token));

    migration.migrate();

    verify(mongoTemplate).remove(argThat((Query q) -> q.toString().contains("token6")), eq("tokens"));
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void whenMigrate_AndMixedBatch_ThenOnlyNullAndOrphanedTokensAreDeleted() {
    // tokenNull → null accountIdentifier, deleted immediately in the nullFieldTokens path
    Document tokenNull = new Document("_id", "tokenNull")
                             .append("apiKeyType", "USER")
                             .append("accountIdentifier", null)
                             .append("parentIdentifier", USER_ID);
    // tokenOrphaned → User has no account-level membership
    Document tokenOrphaned = new Document("_id", "tokenOrphaned")
                                 .append("apiKeyType", "USER")
                                 .append("accountIdentifier", ACCOUNT_ID)
                                 .append("parentIdentifier", USER_ID_2);
    // tokenValid → User has account-level membership
    Document tokenValid = new Document("_id", "tokenValid")
                              .append("apiKeyType", "USER")
                              .append("accountIdentifier", ACCOUNT_ID)
                              .append("parentIdentifier", USER_ID);

    UserMembership validMembership =
        UserMembership.builder().userId(USER_ID).accountIdentifier(ACCOUNT_ID).parentUniqueId(ACCOUNT_ID).build();

    when(mongoTemplate.stream(any(Query.class), eq(Document.class), eq("tokens")))
        .thenReturn(Stream.of(tokenNull, tokenOrphaned, tokenValid));
    when(mongoTemplate.stream(any(Query.class), eq(UserMembership.class))).thenReturn(Stream.of(validMembership));

    migration.migrate();

    verify(mongoTemplate).remove(argThat((Query q) -> q.toString().contains("tokenNull")), eq("tokens"));
    verify(mongoTemplate).remove(argThat((Query q) -> q.toString().contains("tokenOrphaned")), eq("tokens"));
    verify(mongoTemplate, never()).remove(argThat((Query q) -> q.toString().contains("tokenValid")), eq("tokens"));
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void whenMigrate_AndTwoUsersInBatch_OneWithMembership_ThenOnlyOrphanedUserTokenIsDeleted() {
    // user1 token has account-level membership; user2 token does not
    Document token1 = new Document("_id", "token1")
                          .append("apiKeyType", "USER")
                          .append("accountIdentifier", ACCOUNT_ID)
                          .append("parentIdentifier", USER_ID);
    Document token2 = new Document("_id", "token2")
                          .append("apiKeyType", "USER")
                          .append("accountIdentifier", ACCOUNT_ID)
                          .append("parentIdentifier", USER_ID_2);

    UserMembership membershipForUser1 =
        UserMembership.builder().userId(USER_ID).accountIdentifier(ACCOUNT_ID).parentUniqueId(ACCOUNT_ID).build();

    when(mongoTemplate.stream(any(Query.class), eq(Document.class), eq("tokens")))
        .thenReturn(Stream.of(token1, token2));
    when(mongoTemplate.stream(any(Query.class), eq(UserMembership.class))).thenReturn(Stream.of(membershipForUser1));

    migration.migrate();

    verify(mongoTemplate)
        .remove(
            argThat((Query q) -> q.toString().contains("token2") && !q.toString().contains("token1")), eq("tokens"));
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void whenMigrate_AndMultipleTokensForSameUserWithMembership_ThenNoneAreDeleted() {
    Document token1 = new Document("_id", "token1")
                          .append("apiKeyType", "USER")
                          .append("accountIdentifier", ACCOUNT_ID)
                          .append("parentIdentifier", USER_ID);
    Document token2 = new Document("_id", "token2")
                          .append("apiKeyType", "USER")
                          .append("accountIdentifier", ACCOUNT_ID)
                          .append("parentIdentifier", USER_ID);
    Document token3 = new Document("_id", "token3")
                          .append("apiKeyType", "USER")
                          .append("accountIdentifier", ACCOUNT_ID)
                          .append("parentIdentifier", USER_ID);

    UserMembership membership =
        UserMembership.builder().userId(USER_ID).accountIdentifier(ACCOUNT_ID).parentUniqueId(ACCOUNT_ID).build();

    when(mongoTemplate.stream(any(Query.class), eq(Document.class), eq("tokens")))
        .thenReturn(Stream.of(token1, token2, token3));
    when(mongoTemplate.stream(any(Query.class), eq(UserMembership.class))).thenReturn(Stream.of(membership));

    migration.migrate();

    verify(mongoTemplate, never()).remove(any(Query.class), eq("tokens"));
  }
}
