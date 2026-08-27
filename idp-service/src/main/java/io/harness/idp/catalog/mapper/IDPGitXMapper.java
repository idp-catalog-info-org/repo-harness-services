/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.mapper;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.sdk.CacheResponse;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.spec.server.idp.v1.model.CacheResponseData;
import io.harness.spec.server.idp.v1.model.GitDetails;

import lombok.experimental.UtilityClass;

@UtilityClass
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
public class IDPGitXMapper {
  public CacheResponseData getCacheResponseFromGitContext() {
    CacheResponse cacheResponse = GitAwareContextHelper.getCacheResponseFromScmGitMetadata();
    if (cacheResponse != null) {
      CacheResponseData cacheResponseData = new CacheResponseData();
      cacheResponseData.setCacheState(
          CacheResponseData.CacheStateEnum.fromValue(cacheResponse.getCacheState().toString()));
      cacheResponseData.setTtlLeft(cacheResponse.getTtlLeft());
      cacheResponseData.setLastUpdatedAt(cacheResponse.getLastUpdatedAt());
      cacheResponseData.isSyncEnabled(cacheResponse.isSyncEnabled());
      return cacheResponseData;
    }
    return null;
  }

  public GitDetails getEntityGitDetails() {
    GitDetails gitDetails = new GitDetails();
    EntityGitDetails entityGitDetails = GitAwareContextHelper.getEntityGitDetailsFromScmGitMetadata();
    gitDetails.setBranchName(entityGitDetails.getBranch());
    gitDetails.setCommitId(entityGitDetails.getCommitId());
    gitDetails.setFilePath(entityGitDetails.getFilePath());
    gitDetails.setObjectId(entityGitDetails.getObjectId());
    gitDetails.setFileUrl(entityGitDetails.getFileUrl());
    gitDetails.setRepoName(entityGitDetails.getRepoName());
    gitDetails.setIsHarnessCodeRepo(entityGitDetails.getIsHarnessCodeRepo());
    return gitDetails;
  }
}
