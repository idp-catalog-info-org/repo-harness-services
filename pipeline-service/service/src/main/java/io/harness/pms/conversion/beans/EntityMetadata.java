/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.conversion.beans;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.beans.StoreType;

import lombok.Builder;
import lombok.Data;

/**
 * Metadata about the V0 entity being converted.
 * Populated during Phase 1 (YAML fetch), used during Phase 2 (conversion + save).
 * Cleared ($unset) when the job reaches a final status.
 */
@OwnedBy(PIPELINE)
@Data
@Builder
public class EntityMetadata {
  // The V0 YAML content
  String yaml;

  // Entity name (e.g. pipeline name, template name)
  String name;

  // Template version label (only for TEMPLATE entities)
  String versionLabel;

  // Whether this version is the stable version in V0 (only for TEMPLATE entities)
  Boolean stableVersion;

  // YAML version of the entity (V0 or V1) — used to skip already-V1 entities
  String harnessVersion;

  // Input set entity type (INPUT_SET or OVERLAY_INPUT_SET) — only for INPUT_SET entities
  String inputSetEntityType;

  // Parent pipeline V0 YAML — used as context for input set / trigger conversion
  String contextPipelineYaml;

  // Git metadata (REMOTE entities only) — used to create V1 entity in the same repo
  String connectorRef;
  String repo;
  String filePath;
  String branch;
  StoreType storeType;
}
