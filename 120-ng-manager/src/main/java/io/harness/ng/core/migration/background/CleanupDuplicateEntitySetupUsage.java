/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.background;

import static org.springframework.data.mongodb.core.query.Query.query;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.beans.NGMigration;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOptions;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.data.mongodb.core.aggregation.LimitOperation;
import org.springframework.data.mongodb.core.aggregation.MatchOperation;
import org.springframework.data.mongodb.core.aggregation.ProjectionOperation;
import org.springframework.data.mongodb.core.aggregation.SortOperation;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(HarnessTeam.PL)
@Slf4j
public class CleanupDuplicateEntitySetupUsage implements NGMigration {
  @Inject private MongoTemplate mongoTemplate;
  public static final int NO_LIMIT = Integer.MAX_VALUE;
  public static final int BATCH_SIZE = 1000;

  @Override
  public void migrate() {
    cleanUpDuplicateData();
  }

  private void cleanUpDuplicateData() {
    GroupOperation groupBy = Aggregation
                                 .group("referredByEntityType", "referredByEntityFQN", "referredByEntityRepoIdentifier",
                                     "referredByEntityBranch", "referredEntityType", "referredEntityFQN",
                                     "referredEntityRepoIdentifier", "referredEntityBranch", "accountIdentifier")
                                 .addToSet("_id")
                                 .as("ids");
    ProjectionOperation projectionStage = Aggregation.project().andExclude("_id").andInclude("ids");
    SortOperation sortStage = Aggregation.sort(Sort.by("createdAt").ascending());
    LimitOperation limitStage = Aggregation.limit(NO_LIMIT);
    MatchOperation matchStage = Aggregation.match(
        new Criteria().andOperator(Criteria.where("$expr").gt(Arrays.asList(new Document("$size", "$ids"), 1))));
    Aggregation aggregation = Aggregation.newAggregation(sortStage, groupBy, projectionStage, limitStage, matchStage)
                                  .withOptions(AggregationOptions.builder().allowDiskUse(true).build());

    List<Document> results =
        mongoTemplate.aggregate(aggregation, "entitySetupUsage", Document.class).getMappedResults();

    List<Object> idsTobeRemoved = new ArrayList<>();
    for (Document doc : results) {
      List<Object> ids = (List<Object>) doc.get("ids");
      if (ids.size() > 1) {
        idsTobeRemoved.addAll(ids.subList(1, ids.size()));
      }
    }
    List<List<Object>> batchesOfIdsTobeRemoved = Lists.partition(idsTobeRemoved, BATCH_SIZE);
    removeDuplicatesInBatch(batchesOfIdsTobeRemoved);
  }

  private void removeDuplicatesInBatch(List<List<Object>> batchesOfIdsTobeRemoved) {
    for (List<Object> ids : batchesOfIdsTobeRemoved) {
      mongoTemplate.remove(query(Criteria.where("_id").in(ids)), "entitySetupUsage");
    }
  }
}
