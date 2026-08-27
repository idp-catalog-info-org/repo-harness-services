/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.rollback;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.rollback.BatchRollbackRequestDTO;
import io.harness.dtos.rollback.BatchRollbackResponseDTO;
import io.harness.dtos.rollback.PostProdRollbackCheckDTO;
import io.harness.dtos.rollback.PostProdRollbackResponseDTO;
import io.harness.dtos.rollback.RollbackRequestDTO;
import io.harness.dtos.rollback.RollbackResponseDTO;

@OwnedBy(HarnessTeam.CDP)
public interface PostProdRollbackService {
  PostProdRollbackCheckDTO checkIfRollbackAllowed(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String instanceKey, String infraMappingId);
  PostProdRollbackResponseDTO triggerRollback(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String instanceKey, String infraMappingId);
  RollbackResponseDTO triggerRollbackV2(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, RollbackRequestDTO rollbackRequestDTO);
  BatchRollbackResponseDTO triggerRollbackV3(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      BatchRollbackRequestDTO batchRollbackRequestDTO);
}
