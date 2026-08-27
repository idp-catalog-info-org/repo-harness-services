/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.opa.gitx;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.scm.beans.ScmGitMetaData;
import io.harness.ng.core.opa.OpaOnSaveEvaluationStatus;
import io.harness.ng.core.opa.OpaOnSaveStatusResponseDTO;
import io.harness.opa.gitx.AbstractOpaOnSaveStatusHandler;
import io.harness.opa.gitx.OpaGitxStatus;
import io.harness.opa.gitx.OpaOnSaveStatusDTO;
import io.harness.persistence.gitaware.GitAware;
import io.harness.utils.NGFeatureFlagHelperService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;

/**
 * Shared helper that enriches CD GitX entity GET responses (Service, Environment, Infrastructure, ServiceOverrides)
 * with the persisted OPA onSave governance status, mirroring the pipeline GET enrichment. Generic over the entity
 * type so a single instance serves all four resources.
 */
@Singleton
@OwnedBy(CDP)
public class CdOpaOnSaveStatusApiHelper {
  private final NGFeatureFlagHelperService featureFlagHelperService;

  @Inject
  public CdOpaOnSaveStatusApiHelper(NGFeatureFlagHelperService featureFlagHelperService) {
    this.featureFlagHelperService = featureFlagHelperService;
  }

  /**
   * Resolves the OPA onSave status to embed in a single-entity GET response. Returns empty when
   * PIPE_OPA_GITX_ENFORCEMENT is disabled, when the entity is not remote, or when no status is available (fail-open
   * is inherited from the handler). The current commit id is read from the SCM thread-local populated while fetching
   * the entity.
   */
  public <E extends GitAware> Optional<OpaOnSaveStatusResponseDTO> resolveGetOpaOnSaveStatus(
      E entity, String accountId, ScopeInfo scopeInfo, AbstractOpaOnSaveStatusHandler<E> handler) {
    if (!featureFlagHelperService.isEnabled(accountId, FeatureName.PIPE_OPA_GITX_ENFORCEMENT)) {
      return Optional.empty();
    }
    ScmGitMetaData scm = GitAwareContextHelper.getScmGitMetaData();
    String currentCommitId = scm != null ? scm.getCommitId() : null;
    return handler.getOnSaveStatus(entity, accountId, currentCommitId, scopeInfo)
        .map(dto -> toResponse(dto, currentCommitId));
  }

  static OpaOnSaveStatusResponseDTO toResponse(OpaOnSaveStatusDTO dto, String currentCommitId) {
    return OpaOnSaveStatusResponseDTO.builder()
        .status(mapToEvaluationStatus(dto.getStatus()))
        .repoURL(dto.getRepoURL())
        .filePath(dto.getFilePath())
        .evaluatedAtCommitId(dto.getEvaluatedAtCommitId())
        .lastValidCommitId(dto.getLastValidCommitId())
        .evaluatedAt(dto.getEvaluatedAt())
        .message(dto.getMessage())
        .currentCommitId(currentCommitId)
        .governanceMetadata(dto.getGovernanceMetadata())
        .build();
  }

  static OpaOnSaveEvaluationStatus mapToEvaluationStatus(OpaGitxStatus status) {
    if (status == null) {
      return null;
    }
    try {
      return OpaOnSaveEvaluationStatus.valueOf(status.name());
    } catch (IllegalArgumentException e) {
      return OpaOnSaveEvaluationStatus.UNKNOWN;
    }
  }
}
