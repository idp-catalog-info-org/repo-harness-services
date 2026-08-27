/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.migration;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.rule.OwnerRule.ABHAY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.DeleteResult;
import java.util.Arrays;
import java.util.List;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;

@OwnedBy(CI)
public class PipelineBranchSequenceDedupMigrationTest extends CategoryTest {
  @Mock private MongoTemplate mongoTemplate;
  @Mock private MongoDatabase mongoDatabase;
  @Mock private MongoCollection<Document> mongoCollection;
  @Mock private AggregateIterable<Document> aggregateIterable;
  @Mock private MongoCursor<Document> cursor;

  @InjectMocks private PipelineBranchSequenceDedupMigration migration;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    when(mongoTemplate.getDb()).thenReturn(mongoDatabase);
    when(mongoDatabase.getCollection("pipelineBranchSequence")).thenReturn(mongoCollection);
    when(mongoCollection.aggregate(anyList(), eq(Document.class))).thenReturn(aggregateIterable);
    when(aggregateIterable.allowDiskUse(true)).thenReturn(aggregateIterable);
    when(aggregateIterable.iterator()).thenReturn(cursor);
  }

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void testMigrate_NoDuplicates() {
    when(cursor.hasNext()).thenReturn(false);

    migration.migrate();

    verify(mongoCollection, never()).deleteMany(any(Document.class));
  }

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void testMigrate_WithDuplicates_KeepsHighestSequenceId() {
    ObjectId keepId = new ObjectId();
    ObjectId deleteId = new ObjectId();

    Document group = new Document("_id",
        new Document("accountIdentifier", "acc1")
            .append("orgIdentifier", "org1")
            .append("projectIdentifier", "proj1")
            .append("pipelineIdentifier", "pipe1")
            .append("normalizedRepoUrl", "github.com/org/repo")
            .append("branch", "main"))
                         .append("count", 2)
                         .append("maxSeq", 5)
                         .append("docs",
                             Arrays.asList(new Document("id", keepId).append("sequenceId", 5),
                                 new Document("id", deleteId).append("sequenceId", 1)));

    when(cursor.hasNext()).thenReturn(true, false);
    when(cursor.next()).thenReturn(group);
    when(mongoCollection.deleteMany(any(Document.class))).thenReturn(DeleteResult.acknowledged(1));

    migration.migrate();

    verify(mongoCollection, times(1)).deleteMany(any(Document.class));
  }

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void testMigrate_MultipleDuplicateGroups() {
    ObjectId keep1 = new ObjectId();
    ObjectId delete1 = new ObjectId();
    ObjectId keep2 = new ObjectId();
    ObjectId delete2a = new ObjectId();
    ObjectId delete2b = new ObjectId();

    Document group1 = new Document("_id",
        new Document("accountIdentifier", "acc1")
            .append("orgIdentifier", "org1")
            .append("projectIdentifier", "proj1")
            .append("pipelineIdentifier", "pipe1")
            .append("normalizedRepoUrl", "github.com/org/repo")
            .append("branch", "main"))
                          .append("count", 2)
                          .append("maxSeq", 10)
                          .append("docs",
                              Arrays.asList(new Document("id", keep1).append("sequenceId", 10),
                                  new Document("id", delete1).append("sequenceId", 1)));

    Document group2 = new Document("_id",
        new Document("accountIdentifier", "acc2")
            .append("orgIdentifier", "org2")
            .append("projectIdentifier", "proj2")
            .append("pipelineIdentifier", "pipe2")
            .append("normalizedRepoUrl", "github.com/org2/repo2")
            .append("branch", "develop"))
                          .append("count", 3)
                          .append("maxSeq", 7)
                          .append("docs",
                              Arrays.asList(new Document("id", delete2a).append("sequenceId", 1),
                                  new Document("id", keep2).append("sequenceId", 7),
                                  new Document("id", delete2b).append("sequenceId", 3)));

    when(cursor.hasNext()).thenReturn(true, true, false);
    when(cursor.next()).thenReturn(group1, group2);
    when(mongoCollection.deleteMany(any(Document.class))).thenReturn(DeleteResult.acknowledged(3));

    migration.migrate();

    verify(mongoCollection, times(1)).deleteMany(any(Document.class));
  }

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void testMigrate_AllSameSequenceId_KeepsFirst() {
    ObjectId id1 = new ObjectId();
    ObjectId id2 = new ObjectId();

    Document group = new Document("_id",
        new Document("accountIdentifier", "acc1")
            .append("orgIdentifier", "org1")
            .append("projectIdentifier", "proj1")
            .append("pipelineIdentifier", "pipe1")
            .append("normalizedRepoUrl", "github.com/org/repo")
            .append("branch", "feature"))
                         .append("count", 2)
                         .append("maxSeq", 1)
                         .append("docs",
                             Arrays.asList(new Document("id", id1).append("sequenceId", 1),
                                 new Document("id", id2).append("sequenceId", 1)));

    when(cursor.hasNext()).thenReturn(true, false);
    when(cursor.next()).thenReturn(group);
    when(mongoCollection.deleteMany(any(Document.class))).thenReturn(DeleteResult.acknowledged(1));

    migration.migrate();

    ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
    verify(mongoCollection, times(1)).deleteMany(captor.capture());
    List<ObjectId> deletedIds = captor.getValue().get("_id", Document.class).getList("$in", ObjectId.class);
    assertThat(deletedIds).hasSize(1);
    assertThat(deletedIds).doesNotContain(id1);
  }

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void testMigrate_ExceptionRethrown() {
    when(mongoCollection.aggregate(anyList(), eq(Document.class)))
        .thenThrow(new RuntimeException("DB connection failed"));

    assertThatThrownBy(() -> migration.migrate()).isInstanceOf(RuntimeException.class);

    verify(mongoCollection, never()).deleteMany(any(Document.class));
  }
}
