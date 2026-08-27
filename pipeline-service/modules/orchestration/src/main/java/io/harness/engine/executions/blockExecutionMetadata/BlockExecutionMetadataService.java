/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.blockExecutionMetadata;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.execution.BlockExecutionMetadata;
import io.harness.pms.contracts.ambiance.Ambiance;

import com.mongodb.client.result.DeleteResult;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
public interface BlockExecutionMetadataService {
  boolean validate(Ambiance ambiance);

  BlockExecutionMetadata block(String accountId, String orgId, String projectId, String pipelineIdentifier);

  boolean shouldAllowRun(String accountId, String orgId, String projectId, String pipelineId, ScopeInfo scopeInfo);

  DeleteResult unblock(String accountId, String orgId, String projectId, String pipelineId);
}
