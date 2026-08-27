/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.repositories;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.catalog.entities.RelationshipTask;
import io.harness.idp.catalog.entities.TaskStatus;

import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

@HarnessRepo
@OwnedBy(HarnessTeam.IDP)
public interface RelationshipTaskRepository extends MongoRepository<RelationshipTask, String> {
  Optional<RelationshipTask> findByEntityId(String entityId);

  @Query("{'status': ?0, 'nextRetryAt': {$lte: ?1}, 'retryCount': {$lt: ?2}}")
  List<RelationshipTask> findTasksReadyForRetry(TaskStatus status, long currentTime, int maxRetries);

  List<RelationshipTask> findByAccountIdentifierAndStatus(String accountIdentifier, TaskStatus status);

  long countByStatus(TaskStatus status);

  long countByAccountIdentifierAndStatus(String accountIdentifier, TaskStatus status);
}
