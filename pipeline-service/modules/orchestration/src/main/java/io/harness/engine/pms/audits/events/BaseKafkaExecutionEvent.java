/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2025/03/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.audits.events;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.pms.contracts.execution.failure.FailureInfo;

import java.util.Optional;
import lombok.Data;
import lombok.NoArgsConstructor;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@Data
@NoArgsConstructor
public abstract class BaseKafkaExecutionEvent extends NodeExecutionEvent {
  private Long createdAt;
  private Long lastModifiedAt;
  private Long startTs;
  private Long endTs;
  private FailureInfo failureInfo;

  protected BaseKafkaExecutionEvent(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String parentUniqueId, String pipelineIdentifier, String planExecutionId, Integer runSequence, Long createdAt,
      Long lastModifiedAt, Long startTs, Long endTs, FailureInfo failureInfo) {
    super(accountIdentifier, orgIdentifier, projectIdentifier, parentUniqueId, pipelineIdentifier, planExecutionId,
        runSequence);
    this.createdAt = createdAt;
    this.lastModifiedAt = lastModifiedAt;
    this.startTs = startTs;
    this.endTs = endTs;
    this.failureInfo = failureInfo;
  }

  public Optional<Long> getDurationInMillis() {
    if (startTs != null && endTs != null) {
      return Optional.of(endTs - startTs);
    }
    return Optional.empty();
  }

  public boolean isFinished() {
    return startTs != null && endTs != null;
  }

  public abstract String getStatusAsString();
}
