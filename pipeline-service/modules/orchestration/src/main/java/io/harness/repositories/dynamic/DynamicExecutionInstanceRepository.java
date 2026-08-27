/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.dynamic;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.OwnedBy;
import io.harness.execution.DynamicExecutionInstance;

import java.util.Optional;
import java.util.Set;
import org.springframework.data.repository.CrudRepository;

@OwnedBy(PIPELINE)
@HarnessRepo
public interface DynamicExecutionInstanceRepository extends CrudRepository<DynamicExecutionInstance, String> {
  Optional<DynamicExecutionInstance> findByNodeExecutionId(String nodeExecutionId);
  Optional<DynamicExecutionInstance> findByPlanExecutionIdAndIdentifier(String planExecutionId, String name);

  /**
   * Delete all DynamicExecutionInstance for given nodeExecutionIds
   * Uses - nodeExecutionId_1 index
   * @param nodeExecutionIds
   */
  void deleteAllByNodeExecutionIdIn(Set<String> nodeExecutionIds);
}
