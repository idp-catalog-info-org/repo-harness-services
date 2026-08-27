/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.outbox.OutboxEvent;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.time.Instant;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@OwnedBy(PIPELINE)
@Getter
@SuperBuilder
@NoArgsConstructor
@FieldNameConstants(innerTypeName = "KafkaOutboxEventKeys")
@StoreIn(DbAliases.PMS)
@Entity(value = "kafkaOutboxEvents", noClassnameStored = true)
@Document("kafkaOutboxEvents")
@TypeAlias("kafkaOutboxEvents")
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
public class KafkaOutboxEvent extends OutboxEvent {
  @NotNull private String topic;
  @NotNull private Integer retryCount;
  @NotNull @Field("kafkaBlocked") private Boolean blocked;
  private Instant lastUpdatedAt;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("eventType_blocked_createdAt_nextUnblockAttemptAt_kafka_outbox_Idx")
                 .field(OutboxEventKeys.eventType)
                 .field(OutboxEventKeys.blocked)
                 .field(OutboxEventKeys.createdAt)
                 .field(OutboxEventKeys.nextUnblockAttemptAt)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("topic_retryCount_kafka_outbox_Idx")
                 .field(KafkaOutboxEventKeys.topic)
                 .field(KafkaOutboxEventKeys.retryCount)
                 .build())
        .build();
  }
}
