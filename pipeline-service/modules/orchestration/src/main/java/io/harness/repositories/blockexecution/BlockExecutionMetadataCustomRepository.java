/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.blockexecution;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.execution.BlockExecutionMetadata;

import com.mongodb.client.result.DeleteResult;
import java.util.List;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
public interface BlockExecutionMetadataCustomRepository {
  List<BlockExecutionMetadata> findAll(String accountId);

  DeleteResult delete(String pipelineIdentifier, String parentUniqueId);

  boolean existsByAccountId(String accountId);
}
