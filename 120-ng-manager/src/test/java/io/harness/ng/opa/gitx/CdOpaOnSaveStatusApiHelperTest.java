/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.opa.gitx;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.THRISHANK;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.base.NgManagerTestBase;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.scm.beans.ScmGitMetaData;
import io.harness.governance.GovernanceMetadata;
import io.harness.ng.core.opa.OpaOnSaveEvaluationStatus;
import io.harness.ng.core.opa.OpaOnSaveStatusResponseDTO;
import io.harness.opa.gitx.AbstractOpaOnSaveStatusHandler;
import io.harness.opa.gitx.OpaGitxStatus;
import io.harness.opa.gitx.OpaOnSaveStatusDTO;
import io.harness.persistence.gitaware.GitAware;
import io.harness.rule.Owner;
import io.harness.utils.NGFeatureFlagHelperService;

import java.util.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

@OwnedBy(CDP)
public class CdOpaOnSaveStatusApiHelperTest extends NgManagerTestBase {
  private static final String ACCOUNT_ID = "acc";
  private static final String COMMIT_ID = "commit-123";

  @Mock private NGFeatureFlagHelperService featureFlagHelperService;
  @Mock private AbstractOpaOnSaveStatusHandler<GitAware> handler;
  @Mock private GitAware entity;

  private CdOpaOnSaveStatusApiHelper helper;
  private ScopeInfo scopeInfo;

  @Before
  public void setup() {
    helper = new CdOpaOnSaveStatusApiHelper(featureFlagHelperService);
    scopeInfo = ScopeInfo.builder().accountIdentifier(ACCOUNT_ID).uniqueId("uid").build();
  }

  @After
  public void cleanup() {
    // Reset the SCM thread-local so a commit id set by one test never leaks into others sharing the runner thread.
    GitAwareContextHelper.updateScmGitMetaData(ScmGitMetaData.builder().build());
  }

  @Test
  @Owner(developers = THRISHANK)
  @Category(UnitTests.class)
  public void resolveGetOpaOnSaveStatus_ffDisabled_returnsEmptyAndSkipsHandler() {
    when(featureFlagHelperService.isEnabled(ACCOUNT_ID, FeatureName.PIPE_OPA_GITX_ENFORCEMENT)).thenReturn(false);

    Optional<OpaOnSaveStatusResponseDTO> result =
        helper.resolveGetOpaOnSaveStatus(entity, ACCOUNT_ID, scopeInfo, handler);

    assertThat(result).isEmpty();
    verify(handler, never()).getOnSaveStatus(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = THRISHANK)
  @Category(UnitTests.class)
  public void resolveGetOpaOnSaveStatus_statusPresent_mapsResponseWithCurrentCommitId() {
    when(featureFlagHelperService.isEnabled(ACCOUNT_ID, FeatureName.PIPE_OPA_GITX_ENFORCEMENT)).thenReturn(true);
    GitAwareContextHelper.updateScmGitMetaData(ScmGitMetaData.builder().commitId(COMMIT_ID).build());
    GovernanceMetadata gm = GovernanceMetadata.newBuilder().setDeny(true).setMessage("blocked").build();
    OpaOnSaveStatusDTO internal = OpaOnSaveStatusDTO.builder()
                                      .status(OpaGitxStatus.ERROR)
                                      .repoURL("https://repo")
                                      .filePath("path/entity.yaml")
                                      .evaluatedAtCommitId("eval-commit")
                                      .lastValidCommitId("last-valid")
                                      .evaluatedAt(123L)
                                      .message("blocked")
                                      .governanceMetadata(gm)
                                      .build();
    when(handler.getOnSaveStatus(eq(entity), eq(ACCOUNT_ID), eq(COMMIT_ID), eq(scopeInfo)))
        .thenReturn(Optional.of(internal));

    Optional<OpaOnSaveStatusResponseDTO> result =
        helper.resolveGetOpaOnSaveStatus(entity, ACCOUNT_ID, scopeInfo, handler);

    assertThat(result).isPresent();
    OpaOnSaveStatusResponseDTO dto = result.get();
    assertThat(dto.getStatus()).isEqualTo(OpaOnSaveEvaluationStatus.ERROR);
    assertThat(dto.getRepoURL()).isEqualTo("https://repo");
    assertThat(dto.getFilePath()).isEqualTo("path/entity.yaml");
    assertThat(dto.getEvaluatedAtCommitId()).isEqualTo("eval-commit");
    assertThat(dto.getLastValidCommitId()).isEqualTo("last-valid");
    assertThat(dto.getEvaluatedAt()).isEqualTo(123L);
    assertThat(dto.getMessage()).isEqualTo("blocked");
    assertThat(dto.getCurrentCommitId()).isEqualTo(COMMIT_ID);
    assertThat(dto.getGovernanceMetadata()).isEqualTo(gm);
    verify(handler).getOnSaveStatus(eq(entity), eq(ACCOUNT_ID), eq(COMMIT_ID), eq(scopeInfo));
  }

  @Test
  @Owner(developers = THRISHANK)
  @Category(UnitTests.class)
  public void resolveGetOpaOnSaveStatus_handlerEmpty_returnsEmpty() {
    when(featureFlagHelperService.isEnabled(ACCOUNT_ID, FeatureName.PIPE_OPA_GITX_ENFORCEMENT)).thenReturn(true);
    when(handler.getOnSaveStatus(any(), any(), any(), any())).thenReturn(Optional.empty());

    Optional<OpaOnSaveStatusResponseDTO> result =
        helper.resolveGetOpaOnSaveStatus(entity, ACCOUNT_ID, scopeInfo, handler);

    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = THRISHANK)
  @Category(UnitTests.class)
  public void mapToEvaluationStatus_mapsAllStatusesAndNull() {
    assertThat(CdOpaOnSaveStatusApiHelper.mapToEvaluationStatus(null)).isNull();
    assertThat(CdOpaOnSaveStatusApiHelper.mapToEvaluationStatus(OpaGitxStatus.SUCCESS))
        .isEqualTo(OpaOnSaveEvaluationStatus.SUCCESS);
    assertThat(CdOpaOnSaveStatusApiHelper.mapToEvaluationStatus(OpaGitxStatus.WARNING))
        .isEqualTo(OpaOnSaveEvaluationStatus.WARNING);
    assertThat(CdOpaOnSaveStatusApiHelper.mapToEvaluationStatus(OpaGitxStatus.ERROR))
        .isEqualTo(OpaOnSaveEvaluationStatus.ERROR);
    assertThat(CdOpaOnSaveStatusApiHelper.mapToEvaluationStatus(OpaGitxStatus.UNKNOWN))
        .isEqualTo(OpaOnSaveEvaluationStatus.UNKNOWN);
    assertThat(CdOpaOnSaveStatusApiHelper.mapToEvaluationStatus(OpaGitxStatus.NOT_EVALUATED))
        .isEqualTo(OpaOnSaveEvaluationStatus.NOT_EVALUATED);
  }

  @Test
  @Owner(developers = THRISHANK)
  @Category(UnitTests.class)
  public void toResponse_mapsAllFields() {
    GovernanceMetadata gm = GovernanceMetadata.newBuilder().setDeny(false).build();
    OpaOnSaveStatusDTO internal = OpaOnSaveStatusDTO.builder()
                                      .status(OpaGitxStatus.WARNING)
                                      .repoURL("r")
                                      .filePath("f")
                                      .evaluatedAtCommitId("e")
                                      .lastValidCommitId("l")
                                      .evaluatedAt(7L)
                                      .message("m")
                                      .governanceMetadata(gm)
                                      .build();

    OpaOnSaveStatusResponseDTO dto = CdOpaOnSaveStatusApiHelper.toResponse(internal, "cur");

    assertThat(dto.getStatus()).isEqualTo(OpaOnSaveEvaluationStatus.WARNING);
    assertThat(dto.getRepoURL()).isEqualTo("r");
    assertThat(dto.getFilePath()).isEqualTo("f");
    assertThat(dto.getEvaluatedAtCommitId()).isEqualTo("e");
    assertThat(dto.getLastValidCommitId()).isEqualTo("l");
    assertThat(dto.getEvaluatedAt()).isEqualTo(7L);
    assertThat(dto.getMessage()).isEqualTo("m");
    assertThat(dto.getCurrentCommitId()).isEqualTo("cur");
    assertThat(dto.getGovernanceMetadata()).isEqualTo(gm);
  }
}
