/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.background;

import static io.harness.mongo.MongoConfig.NO_LIMIT;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.beans.NGMigration;
import io.harness.ng.core.user.entities.UserMembership;
import io.harness.ng.core.user.entities.UserMembership.UserMembershipKeys;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@Slf4j
@OwnedBy(HarnessTeam.PL)
public class CleanupOrphanedTokensMigration implements NGMigration {
  @Inject private MongoTemplate mongoTemplate;

  private static final String DEBUG_LOG = "[CleanupOrphanedTokensMigration]: ";
  private static final int BATCH_SIZE = 100;
  private static final String COLLECTION = "tokens";
  private static final String FIELD_API_KEY_TYPE = "apiKeyType";
  private static final String FIELD_ACCOUNT_IDENTIFIER = "accountIdentifier";
  private static final String FIELD_PARENT_IDENTIFIER = "parentIdentifier";

  @Override
  public void migrate() {
    log.info(DEBUG_LOG + "Starting cleanup of orphaned USER tokens with no account-level user membership");
    int iterationCounter = 0;
    int deletionCounter = 0;

    Query query = new Query(Criteria.where(FIELD_API_KEY_TYPE).is("USER")).limit(NO_LIMIT).cursorBatchSize(BATCH_SIZE);
    query.fields().include("_id", FIELD_ACCOUNT_IDENTIFIER, FIELD_PARENT_IDENTIFIER, FIELD_API_KEY_TYPE);

    try (Stream<Document> stream = mongoTemplate.stream(query, Document.class, COLLECTION)) {
      Iterator<Document> iterator = stream.iterator();
      List<Document> batch = new ArrayList<>(BATCH_SIZE);

      while (iterator.hasNext()) {
        batch.add(iterator.next());
        iterationCounter++;

        if (batch.size() == BATCH_SIZE || !iterator.hasNext()) {
          deletionCounter += deleteOrphanedTokensInBatch(batch);
          batch.clear();
        }
      }
    } catch (Exception e) {
      log.error(
          format("%s Migration failed. Iterated %d entries, deleted %d.", DEBUG_LOG, iterationCounter, deletionCounter),
          e);
      throw e;
    }

    log.info(format(
        "%s Migration completed. Iterated %d entries, deleted %d.", DEBUG_LOG, iterationCounter, deletionCounter));
  }

  private int deleteOrphanedTokensInBatch(List<Document> batch) {
    List<Document> nullFieldTokens = new ArrayList<>();
    List<Document> validFieldTokens = new ArrayList<>();
    for (Document doc : batch) {
      if (doc.getString(FIELD_ACCOUNT_IDENTIFIER) == null || doc.getString(FIELD_PARENT_IDENTIFIER) == null) {
        nullFieldTokens.add(doc);
      } else {
        validFieldTokens.add(doc);
      }
    }

    int deleted = deleteTokens(nullFieldTokens);

    if (validFieldTokens.isEmpty()) {
      return deleted;
    }

    Set<String> accountIds =
        validFieldTokens.stream().map(d -> d.getString(FIELD_ACCOUNT_IDENTIFIER)).collect(Collectors.toSet());
    Set<String> userIds =
        validFieldTokens.stream().map(d -> d.getString(FIELD_PARENT_IDENTIFIER)).collect(Collectors.toSet());

    Query membershipQuery = new Query(Criteria.where(UserMembershipKeys.accountIdentifier)
                                          .in(accountIds)
                                          .and(UserMembershipKeys.userId)
                                          .in(userIds)
                                          .and(UserMembershipKeys.parentUniqueId)
                                          .in(accountIds));
    membershipQuery.fields().include(UserMembershipKeys.userId, UserMembershipKeys.accountIdentifier);

    Set<String> existingMembershipKeys;
    try (Stream<UserMembership> membershipStream = mongoTemplate.stream(membershipQuery, UserMembership.class)) {
      existingMembershipKeys =
          membershipStream.map(m -> membershipKey(m.getAccountIdentifier(), m.getUserId())).collect(Collectors.toSet());
    }

    List<Document> toDelete = validFieldTokens.stream()
                                  .filter(d
                                      -> !existingMembershipKeys.contains(membershipKey(
                                          d.getString(FIELD_ACCOUNT_IDENTIFIER), d.getString(FIELD_PARENT_IDENTIFIER))))
                                  .collect(Collectors.toList());

    deleted += deleteTokens(toDelete);
    return deleted;
  }

  private int deleteTokens(List<Document> tokens) {
    if (tokens.isEmpty()) {
      return 0;
    }
    List<Object> ids = tokens.stream().map(d -> d.get("_id")).filter(Objects::nonNull).collect(Collectors.toList());
    if (ids.isEmpty()) {
      return 0;
    }
    try {
      mongoTemplate.remove(new Query(Criteria.where("_id").in(ids)), COLLECTION);
      return ids.size();
    } catch (Exception e) {
      log.error("{} Failed to bulk delete {} tokens: {}", DEBUG_LOG, ids.size(), e.getMessage(), e);
      return 0;
    }
  }

  private String membershipKey(String accountId, String userId) {
    return accountId + "_" + userId;
  }
}
