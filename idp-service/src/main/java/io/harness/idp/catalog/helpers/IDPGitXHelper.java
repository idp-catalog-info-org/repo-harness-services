/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.helpers;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.EntityType;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.Scope;
import io.harness.beans.ScopeInfo;
import io.harness.exception.InvalidRequestException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitaware.helper.GitAwareEntityHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.helpers.GitContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.gitx.GitXSettingsHelper;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.GitReferencedCatalogEntity;
import io.harness.spec.server.idp.v1.model.GitCreateDetails;
import io.harness.spec.server.idp.v1.model.GitDetails;
import io.harness.spec.server.idp.v1.model.GitImportDetails;
import io.harness.spec.server.idp.v1.model.GitMoveDetails;
import io.harness.spec.server.idp.v1.model.GitUpdateDetails;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
public class IDPGitXHelper {
  public static final String INVALID_FILE_PATH_ERROR =
      "File path must not start with a slash. Use a repository-relative path (e.g., .harness/catalog-info.yaml)";

  @Inject GitXSettingsHelper gitXSettingsHelper;
  @Inject GitAwareEntityHelper gitAwareEntityHelper;

  public void validateFilePath(String filePath) {
    if (filePath != null && filePath.startsWith("/")) {
      throw new InvalidRequestException(INVALID_FILE_PATH_ERROR);
    }
  }

  public GitEntityInfo populateGitCreateDetails(GitCreateDetails gitDetails) {
    if (gitDetails == null) {
      return GitEntityInfo.builder().build();
    }
    validateFilePath(gitDetails.getFilePath());
    return GitEntityInfo.builder()
        .branch(gitDetails.getBranchName())
        .filePath(gitDetails.getFilePath())
        .commitMsg(gitDetails.getCommitMessage())
        .isNewBranch(isNotEmpty(gitDetails.getBranchName()) && isNotEmpty(gitDetails.getBaseBranch()))
        .baseBranch(gitDetails.getBaseBranch())
        .connectorRef(gitDetails.getConnectorRef())
        .storeType(StoreType.getFromStringOrNull(gitDetails.getStoreType().toString()))
        .repoName(gitDetails.getRepoName())
        .isHarnessCodeRepo(gitDetails.isIsHarnessCodeRepo())
        .build();
  }

  public GitEntityInfo populateGitUpdateDetails(GitUpdateDetails gitDetails) {
    if (gitDetails == null) {
      return GitEntityInfo.builder().build();
    }
    validateFilePath(gitDetails.getFilePath());
    return GitEntityInfo.builder()
        .branch(gitDetails.getBranchName())
        .commitMsg(gitDetails.getCommitMessage())
        .isNewBranch(isNotEmpty(gitDetails.getBranchName()) && isNotEmpty(gitDetails.getBaseBranch()))
        .baseBranch(gitDetails.getBaseBranch())
        .lastCommitId(gitDetails.getLastCommitId())
        .lastObjectId(gitDetails.getLastObjectId())
        .repoName(gitDetails.getRepoName())
        .storeType(StoreType.getFromStringOrNull(
            (gitDetails.getStoreType() == null) ? null : gitDetails.getStoreType().value()))
        .connectorRef(gitDetails.getConnectorRef())
        .filePath(gitDetails.getFilePath())
        .build();
  }

  public GitEntityInfo populateGitImportDetails(GitImportDetails gitImportDetails) {
    if (gitImportDetails == null) {
      return GitEntityInfo.builder().build();
    }
    validateFilePath(gitImportDetails.getFilePath());
    boolean isHarnessCodeRepo = isEmpty(gitImportDetails.getConnectorRef()) ? true : false;
    return GitEntityInfo.builder()
        .branch(gitImportDetails.getBranchName())
        .filePath(gitImportDetails.getFilePath())
        .connectorRef(gitImportDetails.getConnectorRef())
        .repoName(gitImportDetails.getRepoName())
        .isHarnessCodeRepo(isHarnessCodeRepo)
        .build();
  }

  public GitEntityInfo populateGitMoveDetails(GitMoveDetails gitDetails) {
    if (gitDetails == null) {
      return GitEntityInfo.builder().build();
    }
    validateFilePath(gitDetails.getFilePath());
    return GitEntityInfo.builder()
        .branch(gitDetails.getBranchName())
        .filePath(gitDetails.getFilePath())
        .commitMsg(gitDetails.getCommitMessage())
        .isNewBranch(isNotEmpty(gitDetails.getBranchName()) && isNotEmpty(gitDetails.getBaseBranch()))
        .baseBranch(gitDetails.getBaseBranch())
        .connectorRef(gitDetails.getConnectorRef())
        .repoName(gitDetails.getRepoName())
        .isHarnessCodeRepo(gitDetails.isIsHarnessCodeRepo())
        .storeType(StoreType.REMOTE)
        .build();
  }

  public void populateGitDetailsIfRequired(CatalogEntity catalogEntity) {
    if (catalogEntity instanceof GitReferencedCatalogEntity) {
      populateGitDetails(GitEntityInfo.builder()
                             .branch(((GitReferencedCatalogEntity) catalogEntity).getFallBackBranch())
                             .filePath(((GitReferencedCatalogEntity) catalogEntity).getFilePath())
                             .storeType(StoreType.getFromStringOrNull(
                                 ((GitReferencedCatalogEntity) catalogEntity).getStoreType().toString()))
                             .repoName(((GitReferencedCatalogEntity) catalogEntity).getRepo())
                             .build());
    }
  }

  public void populateGitDetails(GitEntityInfo gitEntityInfo) {
    GitAwareContextHelper.populateGitDetails(gitEntityInfo);
  }

  public void applyGitXSettingsIfApplicable(
      String accountIdentifier, String orgIdentifier, String projIdentifier, EntityType entityType) {
    if (GitAwareContextHelper.isRemoteEntity()) {
      gitXSettingsHelper.enforceGitExperienceIfApplicable(accountIdentifier, orgIdentifier, projIdentifier);
      gitXSettingsHelper.setDefaultStoreTypeForEntities(accountIdentifier, orgIdentifier, projIdentifier, entityType);
      gitXSettingsHelper.setConnectorRefForRemoteEntity(accountIdentifier, orgIdentifier, projIdentifier);
      gitXSettingsHelper.setDefaultRepoForRemoteEntity(accountIdentifier, orgIdentifier, projIdentifier);
    }
  }

  public void addGitParamsToOverrideEntity(CatalogEntity catalogEntity, ScopeInfo scopeInfo) {
    if (GitAwareContextHelper.isRemoteEntity()) {
      GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();
      ((GitReferencedCatalogEntity) catalogEntity)
          .setRepoURL(gitAwareEntityHelper.getRepoUrl(
              scopeInfo.getAccountIdentifier(), scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier()));
      ((GitReferencedCatalogEntity) catalogEntity).setStoreType(gitEntityInfo.getStoreType());
      ((GitReferencedCatalogEntity) catalogEntity).setRepo(gitEntityInfo.getRepoName());
      ((GitReferencedCatalogEntity) catalogEntity).setFilePath(gitEntityInfo.getFilePath());
      ((GitReferencedCatalogEntity) catalogEntity).setConnectorRef(gitEntityInfo.getConnectorRef());
      ((GitReferencedCatalogEntity) catalogEntity).setFallBackBranch(gitEntityInfo.getBranch());
    }
  }

  public void addGitParamsFromExistingEntity(CatalogEntity catalogEntity, CatalogEntity existingCatalogEntity) {
    if (existingCatalogEntity instanceof GitReferencedCatalogEntity) {
      ((GitReferencedCatalogEntity) catalogEntity)
          .setRepoURL(((GitReferencedCatalogEntity) existingCatalogEntity).getRepoURL());
      ((GitReferencedCatalogEntity) catalogEntity)
          .setStoreType(((GitReferencedCatalogEntity) existingCatalogEntity).getStoreType());
      ((GitReferencedCatalogEntity) catalogEntity)
          .setRepo(((GitReferencedCatalogEntity) existingCatalogEntity).getRepo());
      ((GitReferencedCatalogEntity) catalogEntity)
          .setFilePath(((GitReferencedCatalogEntity) existingCatalogEntity).getFilePath());
      ((GitReferencedCatalogEntity) catalogEntity)
          .setConnectorRef(((GitReferencedCatalogEntity) existingCatalogEntity).getConnectorRef());
      ((GitReferencedCatalogEntity) catalogEntity)
          .setFallBackBranch(((GitReferencedCatalogEntity) existingCatalogEntity).getFallBackBranch());
    }
  }

  public void pushToGit(CatalogEntity catalogEntity, ScopeInfo scopeInfo) {
    if (GitAwareContextHelper.isRemoteEntity()) {
      gitAwareEntityHelper.createEntityOnGit(
          (GitReferencedCatalogEntity) catalogEntity, catalogEntity.getYaml(), Scope.of(scopeInfo));
    }
  }

  public void updateGit(CatalogEntity catalogEntity, ScopeInfo scopeInfo) {
    if (GitAwareContextHelper.isRemoteEntity()) {
      gitAwareEntityHelper.updateEntityOnGit(
          (GitReferencedCatalogEntity) catalogEntity, catalogEntity.getYaml(), Scope.of(scopeInfo));
    }
  }

  public void validateRepo(CatalogEntity catalogEntity) {
    gitAwareEntityHelper.validateRepo(catalogEntity.getAccountIdentifier(), catalogEntity.getOrgIdentifier(),
        catalogEntity.getProjectIdentifier(), ((GitReferencedCatalogEntity) catalogEntity).getConnectorRef(),
        ((GitReferencedCatalogEntity) catalogEntity).getRepo(), null);
  }

  public boolean isDefaultBranch(
      CatalogEntity catalogEntity, ScopeInfo scopeInfo, String branchName, boolean useCache) {
    return branchName.equals(getDefaultBranch(((GitReferencedCatalogEntity) catalogEntity).getConnectorRef(),
        ((GitReferencedCatalogEntity) catalogEntity).getRepo(), scopeInfo, useCache));
  }

  public String getDefaultBranch(String connectorRef, String repo, ScopeInfo scopeInfo, boolean useCache) {
    return gitAwareEntityHelper.getDefaultBranch(connectorRef, repo, scopeInfo, useCache, EntityType.IDP_CATALOG);
  }

  public GitDetails getEntityDetails(CatalogEntity catalogEntity) {
    if (catalogEntity instanceof GitReferencedCatalogEntity) {
      GitDetails gitDetails = new GitDetails();
      gitDetails.setStoreType(
          GitDetails.StoreTypeEnum.valueOf(((GitReferencedCatalogEntity) catalogEntity).getStoreType().toString()));
      gitDetails.setConnectorRef(((GitReferencedCatalogEntity) catalogEntity).getConnectorRef());
      gitDetails.setBranchName(((GitReferencedCatalogEntity) catalogEntity).getFallBackBranch());
      gitDetails.setFilePath(((GitReferencedCatalogEntity) catalogEntity).getFilePath());
      gitDetails.setRepoName(((GitReferencedCatalogEntity) catalogEntity).getRepo());
      return gitDetails;
    }
    return null;
  }

  public void populateGitUpdateDetailsProjectMovement(CatalogEntity catalogEntity) {
    if (catalogEntity instanceof GitReferencedCatalogEntity) {
      EntityGitDetails gitDetails = GitAwareContextHelper.getEntityGitDetailsFromScmGitMetadata();
      GitEntityInfo gitEntityInfo = GitEntityInfo.builder()
                                        .branch(gitDetails.getBranch())
                                        .commitMsg("Harness Project Movement")
                                        .isNewBranch(false)
                                        .baseBranch(null)
                                        .lastCommitId(gitDetails.getCommitId())
                                        .lastObjectId(gitDetails.getObjectId())
                                        .repoName(gitDetails.getRepoName())
                                        .storeType(((GitReferencedCatalogEntity) catalogEntity).getStoreType())
                                        .connectorRef(((GitReferencedCatalogEntity) catalogEntity).getConnectorRef())
                                        .filePath(gitDetails.getFilePath())
                                        .build();
      GitAwareContextHelper.populateGitDetails(gitEntityInfo);
    }
  }
}
