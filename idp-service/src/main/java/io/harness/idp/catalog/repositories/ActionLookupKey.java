/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.idp.catalog.repositories;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.Builder;
import lombok.Value;

/**
 * Composite key identifying a single {@code Action} document by its unique
 * (parentUniqueId, identifier, version) tuple. Used for bulk lookups where the caller already
 * knows the exact tuples it needs (e.g. resolving every step of a workflow in one DB round-trip).
 */
@Value
@Builder
@OwnedBy(HarnessTeam.IDP)
public class ActionLookupKey {
  String parentUniqueId;
  String identifier;
  String version;
}
