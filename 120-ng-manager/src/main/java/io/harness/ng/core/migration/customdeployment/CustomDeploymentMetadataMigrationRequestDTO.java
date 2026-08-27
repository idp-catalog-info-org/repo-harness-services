/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.customdeployment;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@OwnedBy(HarnessTeam.CDP)
public class CustomDeploymentMetadataMigrationRequestDTO {
  public enum EntityType { SERVICE, INFRA }

  public enum MigrationMode {
    /**
     * Process only each entity's fallback branch and the repo's git default branch.
     * Faster; recommended for first-time migrations.
     */
    DEFAULT_AND_FALLBACK,
    /**
     * Process every branch in the repo. More thorough but significantly slower.
     */
    ALL_BRANCHES
  }

  /**
   * Scopes infra migration to a specific environment and optionally a subset of infrastructure
   * identifiers within that environment. If infraIdentifiers is null or empty, all matching remote
   * custom deployment infras in the given environment are processed.
   *
   * <p>Note: infraIdentifiers are matched by identifier string alone; if the same identifier exists
   * in multiple environments, each environment's entry must be listed separately via infraTargets.
   */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class InfraTarget {
    private String envIdentifier;
    private List<String> infraIdentifiers;
  }

  /**
   * Which entity type to migrate. Required — must be SERVICE or INFRA.
   */
  private EntityType entityType;

  /**
   * Determines which branches are processed per entity. Defaults to DEFAULT_AND_FALLBACK if null.
   */
  private MigrationMode migrationMode;

  /**
   * Optional extra branches to process in addition to those selected by migrationMode.
   * Deduplicated against the mode's branch set.
   */
  private List<String> additionalBranches;

  /**
   * If non-empty, limits migration to only these service identifiers.
   * When null or empty, all matching remote custom deployment services are processed.
   */
  private List<String> serviceIdentifiers;

  /**
   * If non-empty, limits infra migration to the specified (environment, infraIdentifiers) targets.
   * When null or empty, all matching remote custom deployment infras are processed.
   * To target all infras in an environment, set envIdentifier and leave infraIdentifiers null/empty.
   */
  private List<InfraTarget> infraTargets;

  /**
   * When true, skips all DB writes and logs what would be written instead.
   * Useful for validating the migration scope before committing changes.
   */
  private boolean dryRun;
}
