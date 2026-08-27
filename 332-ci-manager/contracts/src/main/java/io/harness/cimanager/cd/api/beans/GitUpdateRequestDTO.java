/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.cd.api.beans;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.sdk.GitSyncApiConstants;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.QueryParam;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITX})
@Getter
@Builder
@FieldNameConstants(innerTypeName = "GitUpdateRequestKeys")
@OwnedBy(DX)
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "GitUpdateRequest", description = "This contains details of the Git Entity for update")
@TargetModule(HarnessModule._878_NG_COMMON_UTILITIES)
public class GitUpdateRequestDTO {
  @Parameter(description = GitSyncApiConstants.BRANCH_PARAM_MESSAGE)
  @QueryParam(GitSyncApiConstants.BRANCH_KEY)
  String branch;
  @Parameter(description = GitSyncApiConstants.FILEPATH_PARAM_MESSAGE)
  @QueryParam(GitSyncApiConstants.FILE_PATH_KEY)
  String filePath;
  @Parameter(description = GitSyncApiConstants.COMMIT_MESSAGE_PARAM_MESSAGE)
  @QueryParam(GitSyncApiConstants.COMMIT_MSG_KEY)
  String commitMsg;
  @Parameter(
      description =
          "Its required field during update call request. It can be fetched from the response of GET API call for the entity")
  @QueryParam(GitSyncApiConstants.LAST_OBJECT_ID_KEY)
  String lastObjectId; // required in case of update file
  @Parameter(
      description =
          "If the entity is git-synced, this parameter represents the commit id against which file conflicts are resolved")
  @QueryParam(GitSyncApiConstants.RESOLVED_CONFLICT_COMMIT_ID)
  String resolvedConflictCommitId; // required in case of update file, empty if its a fresh update
  @Parameter(description = GitSyncApiConstants.DEFAULT_BRANCH_PARAM_MESSAGE)
  @QueryParam(GitSyncApiConstants.BASE_BRANCH)
  String baseBranch;
  @Parameter(description = GitSyncApiConstants.GIT_CONNECTOR_REF_PARAM_MESSAGE)
  @QueryParam(GitSyncApiConstants.CONNECTOR_REF)
  String connectorRef;
  @Parameter(description = GitSyncApiConstants.REPO_NAME_PARAM_MESSAGE)
  @QueryParam(GitSyncApiConstants.REPO_NAME)
  String repoName;
  @Parameter(description = GitSyncApiConstants.STORE_TYPE_PARAM_MESSAGE)
  @Hidden
  @QueryParam(GitSyncApiConstants.STORE_TYPE)
  StoreType storeType;
  @Parameter(description = "last commit id of the file")
  @QueryParam(GitSyncApiConstants.LAST_COMMIT_ID)
  String lastCommitId;
  @Parameter(description = "Checks the new branch")
  @DefaultValue("false")
  @QueryParam(GitSyncApiConstants.NEW_BRANCH)
  Boolean isNewBranch;
  @Parameter(description = GitSyncApiConstants.HARNESS_CODE_PARAM_MESSAGE)
  @QueryParam(GitSyncApiConstants.HARNESS_CODE_REPO)
  Boolean isHarnessCodeRepo;
}
