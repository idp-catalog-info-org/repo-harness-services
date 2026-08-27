/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.outbox.OutboxEvent;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(PIPELINE)
@Getter
@SuperBuilder
@NoArgsConstructor
@FieldNameConstants(innerTypeName = "ExecutionOutboxEventKeys")
@StoreIn(DbAliases.PMS)
@Entity(value = "executionOutboxEvents", noClassnameStored = true)
@Document("executionOutboxEvents")
@TypeAlias("executionOutboxEvents")
public class ExecutionOutboxEvent extends OutboxEvent {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("blocked_createdAt_nextUnblockAttemptAt_outbox_Idx")
                 .field(OutboxEventKeys.blocked)
                 .field(OutboxEventKeys.createdAt)
                 .field(OutboxEventKeys.nextUnblockAttemptAt)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("eventType_blocked_outbox_Idx")
                 .field(OutboxEventKeys.eventType)
                 .field(OutboxEventKeys.blocked)
                 .build())
        .build();
  }
}