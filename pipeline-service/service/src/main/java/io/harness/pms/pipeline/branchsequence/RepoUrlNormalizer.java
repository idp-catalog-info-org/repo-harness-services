/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.branchsequence;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.git.GitClientHelper;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for normalizing Git repository URLs to a canonical format.
 *
 * <p>Normalized format: {@code host/owner/repo} (all lowercase, no protocol, no .git suffix)
 *
 * <p>This ensures that the same repository accessed via different URL formats
 * (SSH, HTTPS, with/without .git suffix) maps to the same counter key.
 *
 * <p>Uses {@link GitClientHelper} for URL parsing, which is the same utility used by
 * {@code BillingHelper} for repository normalization.
 *
 * <p>Examples:
 * <ul>
 *   <li>{@code https://github.com/Org/Repo.git} → {@code github.com/org/repo}</li>
 *   <li>{@code git@github.com:Org/Repo.git} → {@code github.com/org/repo}</li>
 *   <li>{@code ssh://git@github.com/Org/Repo} → {@code github.com/org/repo}</li>
 *   <li>{@code https://gitlab.com/Group/SubGroup/Repo} → {@code gitlab.com/group/subgroup/repo}</li>
 * </ul>
 *
 * @see io.harness.billing.service.BillingHelper#normalizeRepositoryUrl(String)
 */
@UtilityClass
@OwnedBy(CI)
@Slf4j
public class RepoUrlNormalizer {
  private static final String REFS_HEADS_PREFIX = "refs/heads/";
  private static final String REFS_TAGS_PREFIX = "refs/tags/";

  /**
   * Normalizes a Git repository URL to canonical format: host/owner/repo (lowercase).
   *
   * <p>Uses {@link GitClientHelper} methods for parsing, which handles various Git URL formats
   * including SSH, HTTPS, and provider-specific URLs.
   *
   * @param repoUrl the repository URL (SSH, HTTPS, or any Git URL format)
   * @return normalized URL in format host/owner/repo, or null if parsing fails
   */
  public static String normalize(String repoUrl) {
    if (isEmpty(repoUrl)) {
      return null;
    }

    try {
      // Use GitClientHelper to extract components (same as BillingHelper)
      String host = GitClientHelper.getGitSCM(repoUrl);
      String owner = GitClientHelper.getGitOwner(repoUrl, true);
      String repo = GitClientHelper.getGitRepo(repoUrl);

      StringBuilder canonicalUrl = new StringBuilder();
      if (isNotEmpty(host)) {
        canonicalUrl.append(host.toLowerCase());
      }
      if (isNotEmpty(owner)) {
        canonicalUrl.append('/').append(owner.toLowerCase());
      }
      if (isNotEmpty(repo)) {
        canonicalUrl.append('/').append(repo.toLowerCase());
      }

      String normalized = canonicalUrl.toString();
      if (isNotEmpty(normalized)) {
        log.debug("Normalized repo URL: {} -> {}", repoUrl, normalized);
        return normalized;
      }

      log.warn("Failed to normalize repo URL (empty result): {}", repoUrl);
      return null;

    } catch (Exception e) {
      log.warn("Error normalizing repo URL '{}': {}", repoUrl, e.getMessage());
      return null;
    }
  }

  /**
   * Normalizes a branch name by stripping refs/heads/ or refs/tags/ prefixes.
   *
   * @param branch the branch name (may include refs/heads/ prefix)
   * @return normalized branch name without prefix, or null if empty
   */
  public static String normalizeBranch(String branch) {
    if (isEmpty(branch)) {
      return null;
    }

    String normalized = branch.trim();

    // Strip refs/heads/ prefix
    if (normalized.startsWith(REFS_HEADS_PREFIX)) {
      normalized = normalized.substring(REFS_HEADS_PREFIX.length());
    }

    // Strip refs/tags/ prefix
    if (normalized.startsWith(REFS_TAGS_PREFIX)) {
      normalized = normalized.substring(REFS_TAGS_PREFIX.length());
    }

    return normalized;
  }
}
