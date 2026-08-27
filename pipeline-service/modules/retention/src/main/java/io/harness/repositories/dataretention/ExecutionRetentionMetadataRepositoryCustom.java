/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.dataretention;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.dataretention.entity.ExecutionRetentionMetadata;

import com.mongodb.client.result.DeleteResult;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_DATA_RETENTION})
@OwnedBy(PIPELINE)
public interface ExecutionRetentionMetadataRepositoryCustom {
  /**
   * Fetches ExecutionRetentionMetadata from secondary DB based on the criteria
   * @param criteria criteria
   * @return ExecutionRetentionMetadata
   */
  ExecutionRetentionMetadata fetchFromSecondary(Criteria criteria);

  /**
   * Fetches List of ExecutionRetentionMetadata from secondary DB based on the criteria
   * @param criteria criteria
   * @return List of ExecutionRetentionMetadata
   */
  List<ExecutionRetentionMetadata> fetchAllFromSecondary(Criteria criteria);

  List<ExecutionRetentionMetadata> fetchAllFromSecondary(Query query);

  /**
   * Upsert ExecutionRetentionMetadata using planExecutionId, creates record if not found
   * Uses - planExecutionId_1 idx
   * @param planExecutionId planExecutionId
   * @param updateOps updates to perform
   * @return ExecutionRetentionMetadata
   */
  ExecutionRetentionMetadata upsert(String planExecutionId, Update updateOps);

  /**
   * Updates ExecutionRetentionMetadata using uuid
   * Uses - _id_ idx
   * @param uuid uuid
   * @param updateOps updates to perform
   * @return ExecutionRetentionMetadata
   */
  ExecutionRetentionMetadata update(String uuid, Update updateOps);

  /**
   *
   * @param criteria
   * @return
   */
  Stream<ExecutionRetentionMetadata> streamFromSecondary(Criteria criteria);

  Stream<ExecutionRetentionMetadata> streamFromSecondary(Query query);

  /**
   * Fetches all unique account ids
   * @param criteria
   * @return
   */
  List<String> getAllUniqueAccountIdsFromSecondary(Criteria criteria);

  /**
   * Deletes ExecutionRetentionMetadata's with given criteria
   * @param criteria
   * @return
   */
  DeleteResult delete(Criteria criteria);

  /**
   * It updates the ExecutionRetentionMetadata
   * @param criteria
   * @param update
   * @return
   */
  ExecutionRetentionMetadata update(Criteria criteria, Update update);
}
