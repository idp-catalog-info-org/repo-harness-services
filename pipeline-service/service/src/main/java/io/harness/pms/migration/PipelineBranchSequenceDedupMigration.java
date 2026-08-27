/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.migration;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.beans.NGMigration;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

@OwnedBy(CI)
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
public class PipelineBranchSequenceDedupMigration implements NGMigration {
  private static final String COLLECTION_NAME = "pipelineBranchSequence";
  private static final int DELETE_BATCH_SIZE = 100;

  private final MongoTemplate mongoTemplate;

  @Override
  public void migrate() {
    log.info("Starting PipelineBranchSequence dedup migration");

    List<Document> pipeline = List.of(
        new Document("$group",
            new Document("_id",
                new Document("accountIdentifier", "$accountIdentifier")
                    .append("orgIdentifier", "$orgIdentifier")
                    .append("projectIdentifier", "$projectIdentifier")
                    .append("pipelineIdentifier", "$pipelineIdentifier")
                    .append("normalizedRepoUrl", "$normalizedRepoUrl")
                    .append("branch", "$branch"))
                .append("count", new Document("$sum", 1))
                .append("maxSeq", new Document("$max", "$sequenceId"))
                .append("docs", new Document("$push", new Document("id", "$_id").append("sequenceId", "$sequenceId")))),
        new Document("$match", new Document("count", new Document("$gt", 1))));

    List<ObjectId> idsToDelete = new ArrayList<>();
    int duplicateGroups = 0;
    long totalDeleted = 0;

    try {
      for (Document group :
          mongoTemplate.getDb().getCollection(COLLECTION_NAME).aggregate(pipeline, Document.class).allowDiskUse(true)) {
        duplicateGroups++;
        Number maxSeq = (Number) group.get("maxSeq");
        List<Document> docs = group.getList("docs", Document.class);

        boolean keptOne = false;
        for (Document doc : docs) {
          Number docSeq = (Number) doc.get("sequenceId");
          if (!keptOne && docSeq.longValue() == maxSeq.longValue()) {
            keptOne = true;
            continue;
          }
          idsToDelete.add(doc.getObjectId("id"));
          if (idsToDelete.size() >= DELETE_BATCH_SIZE) {
            totalDeleted += deleteBatch(idsToDelete);
          }
        }
      }

      if (!idsToDelete.isEmpty()) {
        totalDeleted += deleteBatch(idsToDelete);
      }

      log.info(
          "PipelineBranchSequence dedup migration complete. Found {} duplicate groups, deleted {} duplicate documents",
          duplicateGroups, totalDeleted);

    } catch (Exception e) {
      log.error("PipelineBranchSequence dedup migration failed", e);
      throw new RuntimeException("PipelineBranchSequence dedup migration failed", e);
    }
  }

  private long deleteBatch(List<ObjectId> idsToDelete) {
    Document filter = new Document("_id", new Document("$in", new ArrayList<>(idsToDelete)));
    long deleted = mongoTemplate.getDb().getCollection(COLLECTION_NAME).deleteMany(filter).getDeletedCount();
    log.info("PipelineBranchSequence dedup: deleted {} duplicate documents in batch", deleted);
    idsToDelete.clear();
    return deleted;
  }
}
