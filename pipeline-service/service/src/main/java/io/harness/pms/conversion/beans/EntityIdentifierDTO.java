/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.conversion.beans;

import io.harness.goconvert.EntityType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * Lightweight DTO for entity identification in conversion jobs.
 * Git metadata (storeType, repo, filePath, connectorRef) lives in EntityMetadata,
 * populated at fetch time from the actual entity.
 */
@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "EntityIdentifier", description = "Entity identifier for conversion")
public class EntityIdentifierDTO {
  @Schema(description = "Entity identifier") private String entityId;

  @Schema(description = "Entity type (PIPELINE, TEMPLATE, or INPUT_SET)") private EntityType entityType;

  @Schema(description = "Version label (for TEMPLATE entities)") private String versionLabel;

  @Schema(description = "Branch name (for REMOTE entities)") private String branch;

  @Schema(description = "Convert all existing branches for this remote entity") private boolean convertAllBranches;
}
