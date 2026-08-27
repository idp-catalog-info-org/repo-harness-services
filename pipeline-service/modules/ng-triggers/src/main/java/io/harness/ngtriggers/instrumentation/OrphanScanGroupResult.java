/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.instrumentation;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;

import lombok.Builder;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.Id;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@Builder
@FieldNameConstants(innerTypeName = "OrphanScanGroupResultKeys")
public record OrphanScanGroupResult(@Id OrphanScanGroupId id, long count, String sampleIdentifier,
    String sampleTargetIdentifier, Long sampleCreatedAt, Boolean sampleDeleted) {
  @Builder
  @FieldNameConstants(innerTypeName = "OrphanScanGroupIdKeys")
  public record OrphanScanGroupId(
      String accountId, String orgIdentifier, String projectIdentifier, String parentUniqueId) {}
}
