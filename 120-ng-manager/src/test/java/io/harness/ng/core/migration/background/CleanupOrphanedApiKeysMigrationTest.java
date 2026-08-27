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
public class CleanupOrphanedApiKeysMigrationTest extends CategoryTest {
  private static final String ACCOUNT_ID = "accountId";
  private static final String USER_ID = "userId";
  private static final String USER_ID_2 = "userId2";

  @Mock private MongoTemplate mongoTemplate;
  @InjectMocks private CleanupOrphanedApiKeysMigration migration;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void whenMigrate_AndAccountIdentifierIsNull_ThenAPIKeyIsDeleted() {
    Document key = new Document("_id", "key1")
                       .append("apiKeyType", "USER")
                       .append("accountIdentifier", null)
                       .append("parentIdentifier", USER_ID);

    when(mongoTemplate.stream(any(Query.class), eq(Document.class), eq("ngApiKeys"))).thenReturn(Stream.of(key));

    migration.migrate();

    verify(mongoTemplate).remove(argThat((Query q) -> q.toString().contains("key1")), eq("ngApiKeys"));
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void whenMigrate_AndParentIdentifierIsNull_ThenAPIKeyIsDeleted() {
    Document key = new Document("_id", "key2")
                       .append("apiKeyType", "USER")
                       .append("accountIdentifier", ACCOUNT_ID)
                       .append("parentIdentifier", null);

    when(mongoTemplate.stream(any(Query.class), eq(Document.class), eq("ngApiKeys"))).thenReturn(Stream.of(key));

    migration.migrate();

    verify(mongoTemplate).remove(argThat((Query q) -> q.toString().contains("key2")), eq("ngApiKeys"));
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void whenMigrate_AndNoAccountLevelMembershipExists_ThenAPIKeyIsDeleted() {
    Document key = new Document("_id", "key3")
                       .append("apiKeyType", "USER")
                       .append("accountIdentifier", ACCOUNT_ID)
                       .append("parentIdentifier", USER_ID);

    when(mongoTemplate.stream(any(Query.class), eq(Document.class), eq("ngApiKeys"))).thenReturn(Stream.of(key));
    when(mongoTemplate.stream(
             argThat(q -> q != null && q.toString().contains("parentUniqueId") && q.toString().contains(ACCOUNT_ID)),
             eq(UserMembership.class)))
        .thenReturn(Stream.empty());

    migration.migrate();

    verify(mongoTemplate).remove(argThat((Query q) -> q.toString().contains("key3")), eq("ngApiKeys"));
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void whenMigrate_AndAccountLevelMembershipExistsThenAPIKey_IsNotDeleted() {
    Document key = new Document("_id", "key4")
                       .append("apiKeyType", "USER")
                       .append("accountIdentifier", ACCOUNT_ID)
                       .append("parentIdentifier", USER_ID);

    // For account-level membership, parentUniqueId == accountIdentifier
    UserMembership accountLevelMembership =
        UserMembership.builder().userId(USER_ID).accountIdentifier(ACCOUNT_ID).parentUniqueId(ACCOUNT_ID).build();

    when(mongoTemplate.stream(any(Query.class), eq(Document.class), eq("ngApiKeys"))).thenReturn(Stream.of(key));
    when(mongoTemplate.stream(any(Query.class), eq(UserMembership.class)))
        .thenReturn(Stream.of(accountLevelMembership));

    migration.migrate();

    verify(mongoTemplate, never()).remove(any(Query.class), eq("ngApiKeys"));
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void whenMigrate_AndBothAccountAndParentIdentifiersAreNull_ThenAPIKeyIsDeleted() {
    Document key = new Document("_id", "key6")
                       .append("apiKeyType", "USER")
                       .append("accountIdentifier", null)
                       .append("parentIdentifier", null);

    when(mongoTemplate.stream(any(Query.class), eq(Document.class), eq("ngApiKeys"))).thenReturn(Stream.of(key));

    migration.migrate();

    verify(mongoTemplate).remove(argThat((Query q) -> q.toString().contains("key6")), eq("ngApiKeys"));
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void whenMigrate_AndMixedBatch_ThenOnlyNullAndOrphanedKeysAreDeleted() {
    // keyNull → null accountIdentifier, deleted immediately in the nullFieldKeys path
    Document keyNull = new Document("_id", "keyNull")
                           .append("apiKeyType", "USER")
                           .append("accountIdentifier", null)
                           .append("parentIdentifier", USER_ID);
    // keyOrphaned → User has no account-level membership
    Document keyOrphaned = new Document("_id", "keyOrphaned")
                               .append("apiKeyType", "USER")
                               .append("accountIdentifier", ACCOUNT_ID)
                               .append("parentIdentifier", USER_ID_2);
    // keyValid → User has account-level membership
    Document keyValid = new Document("_id", "keyValid")
                            .append("apiKeyType", "USER")
                            .append("accountIdentifier", ACCOUNT_ID)
                            .append("parentIdentifier", USER_ID);

    UserMembership validMembership =
        UserMembership.builder().userId(USER_ID).accountIdentifier(ACCOUNT_ID).parentUniqueId(ACCOUNT_ID).build();

    when(mongoTemplate.stream(any(Query.class), eq(Document.class), eq("ngApiKeys")))
        .thenReturn(Stream.of(keyNull, keyOrphaned, keyValid));
    when(mongoTemplate.stream(any(Query.class), eq(UserMembership.class))).thenReturn(Stream.of(validMembership));

    migration.migrate();

    verify(mongoTemplate).remove(argThat((Query q) -> q.toString().contains("keyNull")), eq("ngApiKeys"));
    verify(mongoTemplate).remove(argThat((Query q) -> q.toString().contains("keyOrphaned")), eq("ngApiKeys"));
    verify(mongoTemplate, never()).remove(argThat((Query q) -> q.toString().contains("keyValid")), eq("ngApiKeys"));
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void whenMigrate_AndTwoUsersInBatch_OneWithMembership_ThenOnlyOrphanedUserKeyIsDeleted() {
    // user1 key has account-level membership; user2 key does not
    Document key1 = new Document("_id", "key1")
                        .append("apiKeyType", "USER")
                        .append("accountIdentifier", ACCOUNT_ID)
                        .append("parentIdentifier", USER_ID);
    Document key2 = new Document("_id", "key2")
                        .append("apiKeyType", "USER")
                        .append("accountIdentifier", ACCOUNT_ID)
                        .append("parentIdentifier", USER_ID_2);

    UserMembership membershipForUser1 =
        UserMembership.builder().userId(USER_ID).accountIdentifier(ACCOUNT_ID).parentUniqueId(ACCOUNT_ID).build();

    when(mongoTemplate.stream(any(Query.class), eq(Document.class), eq("ngApiKeys"))).thenReturn(Stream.of(key1, key2));
    when(mongoTemplate.stream(any(Query.class), eq(UserMembership.class))).thenReturn(Stream.of(membershipForUser1));

    migration.migrate();

    verify(mongoTemplate)
        .remove(argThat((Query q) -> q.toString().contains("key2") && !q.toString().contains("key1")), eq("ngApiKeys"));
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void whenMigrate_AndMultipleKeysForSameUserWithMembership_ThenNoneAreDeleted() {
    Document key1 = new Document("_id", "key1")
                        .append("apiKeyType", "USER")
                        .append("accountIdentifier", ACCOUNT_ID)
                        .append("parentIdentifier", USER_ID);
    Document key2 = new Document("_id", "key2")
                        .append("apiKeyType", "USER")
                        .append("accountIdentifier", ACCOUNT_ID)
                        .append("parentIdentifier", USER_ID);
    Document key3 = new Document("_id", "key3")
                        .append("apiKeyType", "USER")
                        .append("accountIdentifier", ACCOUNT_ID)
                        .append("parentIdentifier", USER_ID);

    UserMembership membership =
        UserMembership.builder().userId(USER_ID).accountIdentifier(ACCOUNT_ID).parentUniqueId(ACCOUNT_ID).build();

    when(mongoTemplate.stream(any(Query.class), eq(Document.class), eq("ngApiKeys")))
        .thenReturn(Stream.of(key1, key2, key3));
    when(mongoTemplate.stream(any(Query.class), eq(UserMembership.class))).thenReturn(Stream.of(membership));

    migration.migrate();

    verify(mongoTemplate, never()).remove(any(Query.class), eq("ngApiKeys"));
  }
}
