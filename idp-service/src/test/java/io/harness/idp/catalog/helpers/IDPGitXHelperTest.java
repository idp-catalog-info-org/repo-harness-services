/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.helpers;

import static io.harness.rule.OwnerRule.DEVESH;
import static io.harness.rule.OwnerRule.KOTA_KARTHIK;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.EntityType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitaware.helper.GitAwareEntityHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.helpers.GitContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitx.GitXSettingsHelper;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.GitReferencedCatalogEntity;
import io.harness.idp.catalog.entities.InlineCatalogEntity;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.GitCreateDetails;
import io.harness.spec.server.idp.v1.model.GitDetails;
import io.harness.spec.server.idp.v1.model.GitImportDetails;
import io.harness.spec.server.idp.v1.model.GitMoveDetails;
import io.harness.spec.server.idp.v1.model.GitUpdateDetails;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class IDPGitXHelperTest extends CategoryTest {
  @Mock private GitXSettingsHelper gitXSettingsHelper;
  @Mock private GitAwareEntityHelper gitAwareEntityHelper;

  @InjectMocks private IDPGitXHelper idpGitXHelper;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testPopulateGitCreateDetails() {
    GitCreateDetails gitDetails = new GitCreateDetails();
    gitDetails.setBranchName("feature-branch");
    gitDetails.setFilePath(".harness/catalog-info.yaml");
    gitDetails.setCommitMessage("Test commit");
    gitDetails.setBaseBranch("main");
    gitDetails.setConnectorRef("connector-ref");
    gitDetails.setStoreType(GitCreateDetails.StoreTypeEnum.REMOTE);
    gitDetails.setRepoName("test-repo");
    gitDetails.setIsHarnessCodeRepo(true);

    GitEntityInfo result = idpGitXHelper.populateGitCreateDetails(gitDetails);

    assertThat(result.getBranch()).isEqualTo("feature-branch");
    assertThat(result.getFilePath()).isEqualTo(".harness/catalog-info.yaml");
    assertThat(result.getCommitMsg()).isEqualTo("Test commit");
    assertThat(result.isNewBranch()).isTrue();
    assertThat(result.getBaseBranch()).isEqualTo("main");
    assertThat(result.getConnectorRef()).isEqualTo("connector-ref");
    assertThat(result.getRepoName()).isEqualTo("test-repo");
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testPopulateGitCreateDetailsWithNull() {
    GitEntityInfo result = idpGitXHelper.populateGitCreateDetails(null);

    assertThat(result).isNotNull();
    assertThat(result.getBranch()).isNull();
    assertThat(result.getFilePath()).isNull();
    assertThat(result.getCommitMsg()).isNull();
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testPopulateGitUpdateDetails() {
    GitUpdateDetails gitDetails = new GitUpdateDetails();
    gitDetails.setBranchName("update-branch");
    gitDetails.setCommitMessage("Update commit");
    gitDetails.setBaseBranch("main");
    gitDetails.setLastCommitId("commit-id");
    gitDetails.setLastObjectId("object-id");
    gitDetails.setRepoName("test-repo");
    gitDetails.setConnectorRef("connector-ref");
    gitDetails.setFilePath(".harness/catalog-info.yaml");

    GitEntityInfo result = idpGitXHelper.populateGitUpdateDetails(gitDetails);

    assertThat(result.getBranch()).isEqualTo("update-branch");
    assertThat(result.getCommitMsg()).isEqualTo("Update commit");
    assertThat(result.isNewBranch()).isTrue();
    assertThat(result.getBaseBranch()).isEqualTo("main");
    assertThat(result.getLastCommitId()).isEqualTo("commit-id");
    assertThat(result.getLastObjectId()).isEqualTo("object-id");
    assertThat(result.getRepoName()).isEqualTo("test-repo");
    assertThat(result.getConnectorRef()).isEqualTo("connector-ref");
    assertThat(result.getFilePath()).isEqualTo(".harness/catalog-info.yaml");
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testPopulateGitImportDetails() {
    GitImportDetails gitImportDetails = new GitImportDetails();
    gitImportDetails.setBranchName("import-branch");
    gitImportDetails.setFilePath("import/path.yaml");
    gitImportDetails.setConnectorRef("connector-ref");
    gitImportDetails.setRepoName("import-repo");

    GitEntityInfo result = idpGitXHelper.populateGitImportDetails(gitImportDetails);

    assertThat(result.getBranch()).isEqualTo("import-branch");
    assertThat(result.getFilePath()).isEqualTo("import/path.yaml");
    assertThat(result.getConnectorRef()).isEqualTo("connector-ref");
    assertThat(result.getRepoName()).isEqualTo("import-repo");
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testPopulateGitMoveDetails() {
    GitMoveDetails gitDetails = new GitMoveDetails();
    gitDetails.setBranchName("move-branch");
    gitDetails.setFilePath("new/path.yaml");
    gitDetails.setCommitMessage("Move commit");
    gitDetails.setBaseBranch("main");
    gitDetails.setConnectorRef("connector-ref");
    gitDetails.setRepoName("test-repo");
    gitDetails.setIsHarnessCodeRepo(false);

    GitEntityInfo result = idpGitXHelper.populateGitMoveDetails(gitDetails);

    assertThat(result.getBranch()).isEqualTo("move-branch");
    assertThat(result.getFilePath()).isEqualTo("new/path.yaml");
    assertThat(result.getCommitMsg()).isEqualTo("Move commit");
    assertThat(result.isNewBranch()).isTrue();
    assertThat(result.getBaseBranch()).isEqualTo("main");
    assertThat(result.getConnectorRef()).isEqualTo("connector-ref");
    assertThat(result.getRepoName()).isEqualTo("test-repo");
    assertThat(result.getStoreType()).isEqualTo(StoreType.REMOTE);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetEntityDetails() {
    GitReferencedCatalogEntity catalogEntity = GitReferencedCatalogEntity.builder()
                                                   .storeType(StoreType.REMOTE)
                                                   .connectorRef("connector-ref")
                                                   .fallBackBranch("main")
                                                   .filePath(".harness/entity.yaml")
                                                   .repo("test-repo")
                                                   .build();

    GitDetails result = idpGitXHelper.getEntityDetails(catalogEntity);

    assertThat(result).isNotNull();
    assertThat(result.getStoreType()).isEqualTo(GitDetails.StoreTypeEnum.REMOTE);
    assertThat(result.getConnectorRef()).isEqualTo("connector-ref");
    assertThat(result.getBranchName()).isEqualTo("main");
    assertThat(result.getFilePath()).isEqualTo(".harness/entity.yaml");
    assertThat(result.getRepoName()).isEqualTo("test-repo");
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetEntityDetailsForInlineEntity() {
    InlineCatalogEntity catalogEntity = InlineCatalogEntity.builder()
                                            .accountIdentifier("account-id")
                                            .identifier("entity-id")
                                            .yaml("test: yaml")
                                            .build();

    GitDetails result = idpGitXHelper.getEntityDetails(catalogEntity);

    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testIsDefaultBranch() {
    GitReferencedCatalogEntity catalogEntity =
        GitReferencedCatalogEntity.builder().connectorRef("connector-ref").repo("test-repo").build();

    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier("account-id")
                              .orgIdentifier("org-id")
                              .projectIdentifier("project-id")
                              .uniqueId("unique-id")
                              .build();

    when(gitAwareEntityHelper.getDefaultBranch("connector-ref", "test-repo", scopeInfo, true, EntityType.IDP_CATALOG))
        .thenReturn("main");

    boolean result = idpGitXHelper.isDefaultBranch(catalogEntity, scopeInfo, "main", true);

    assertThat(result).isTrue();

    result = idpGitXHelper.isDefaultBranch(catalogEntity, scopeInfo, "feature-branch", true);

    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testPushToGit() {
    try (MockedStatic<GitAwareContextHelper> contextHelper = mockStatic(GitAwareContextHelper.class)) {
      contextHelper.when(GitAwareContextHelper::isRemoteEntity).thenReturn(true);

      GitReferencedCatalogEntity catalogEntity = GitReferencedCatalogEntity.builder()
                                                     .accountIdentifier("account-id")
                                                     .orgIdentifier("org-id")
                                                     .projectIdentifier("project-id")
                                                     .yaml("test: yaml")
                                                     .build();

      ScopeInfo scopeInfo = ScopeInfo.builder()
                                .accountIdentifier("account-id")
                                .orgIdentifier("org-id")
                                .projectIdentifier("project-id")
                                .uniqueId("unique-id")
                                .build();

      idpGitXHelper.pushToGit(catalogEntity, scopeInfo);

      verify(gitAwareEntityHelper).createEntityOnGit(any(GitReferencedCatalogEntity.class), eq("test: yaml"), any());
    }
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testUpdateGit() {
    try (MockedStatic<GitAwareContextHelper> contextHelper = mockStatic(GitAwareContextHelper.class)) {
      contextHelper.when(GitAwareContextHelper::isRemoteEntity).thenReturn(true);

      GitReferencedCatalogEntity catalogEntity = GitReferencedCatalogEntity.builder()
                                                     .accountIdentifier("account-id")
                                                     .orgIdentifier("org-id")
                                                     .projectIdentifier("project-id")
                                                     .yaml("updated: yaml")
                                                     .build();

      ScopeInfo scopeInfo = ScopeInfo.builder()
                                .accountIdentifier("account-id")
                                .orgIdentifier("org-id")
                                .projectIdentifier("project-id")
                                .uniqueId("unique-id")
                                .build();

      idpGitXHelper.updateGit(catalogEntity, scopeInfo);

      verify(gitAwareEntityHelper).updateEntityOnGit(any(GitReferencedCatalogEntity.class), eq("updated: yaml"), any());
    }
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testValidateRepo() {
    GitReferencedCatalogEntity catalogEntity = GitReferencedCatalogEntity.builder()
                                                   .accountIdentifier("account-id")
                                                   .orgIdentifier("org-id")
                                                   .projectIdentifier("project-id")
                                                   .connectorRef("connector-ref")
                                                   .repo("test-repo")
                                                   .build();

    idpGitXHelper.validateRepo(catalogEntity);

    verify(gitAwareEntityHelper).validateRepo("account-id", "org-id", "project-id", "connector-ref", "test-repo", null);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testPopulateGitCreateDetailsThrowsOnLeadingSlash() {
    GitCreateDetails gitDetails = new GitCreateDetails();
    gitDetails.setFilePath("/.harness/catalog-info.yaml");
    gitDetails.setStoreType(GitCreateDetails.StoreTypeEnum.REMOTE);

    assertThatThrownBy(() -> idpGitXHelper.populateGitCreateDetails(gitDetails))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(IDPGitXHelper.INVALID_FILE_PATH_ERROR);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testPopulateGitCreateDetailsThrowsOnMultipleLeadingSlashes() {
    GitCreateDetails gitDetails = new GitCreateDetails();
    gitDetails.setFilePath("//.harness/catalog-info.yaml");
    gitDetails.setStoreType(GitCreateDetails.StoreTypeEnum.REMOTE);

    assertThatThrownBy(() -> idpGitXHelper.populateGitCreateDetails(gitDetails))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(IDPGitXHelper.INVALID_FILE_PATH_ERROR);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testPopulateGitUpdateDetailsThrowsOnLeadingSlash() {
    GitUpdateDetails gitDetails = new GitUpdateDetails();
    gitDetails.setFilePath("/.harness/catalog-info.yaml");

    assertThatThrownBy(() -> idpGitXHelper.populateGitUpdateDetails(gitDetails))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(IDPGitXHelper.INVALID_FILE_PATH_ERROR);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testPopulateGitImportDetailsThrowsOnLeadingSlash() {
    GitImportDetails gitImportDetails = new GitImportDetails();
    gitImportDetails.setFilePath("/.harness/catalog-info.yaml");
    gitImportDetails.setConnectorRef("connector-ref");

    assertThatThrownBy(() -> idpGitXHelper.populateGitImportDetails(gitImportDetails))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(IDPGitXHelper.INVALID_FILE_PATH_ERROR);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testPopulateGitMoveDetailsThrowsOnLeadingSlash() {
    GitMoveDetails gitDetails = new GitMoveDetails();
    gitDetails.setFilePath("/.harness/catalog-info.yaml");

    assertThatThrownBy(() -> idpGitXHelper.populateGitMoveDetails(gitDetails))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(IDPGitXHelper.INVALID_FILE_PATH_ERROR);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testApplyGitXSettingsIfApplicable() {
    try (MockedStatic<GitAwareContextHelper> contextHelper = mockStatic(GitAwareContextHelper.class)) {
      contextHelper.when(GitAwareContextHelper::isRemoteEntity).thenReturn(true);

      String accountIdentifier = "account-id";
      String orgIdentifier = "org-id";
      String projIdentifier = "project-id";
      EntityType entityType = EntityType.IDP_CATALOG;

      idpGitXHelper.applyGitXSettingsIfApplicable(accountIdentifier, orgIdentifier, projIdentifier, entityType);

      verify(gitXSettingsHelper).enforceGitExperienceIfApplicable(accountIdentifier, orgIdentifier, projIdentifier);
      verify(gitXSettingsHelper)
          .setDefaultStoreTypeForEntities(accountIdentifier, orgIdentifier, projIdentifier, entityType);
      verify(gitXSettingsHelper).setConnectorRefForRemoteEntity(accountIdentifier, orgIdentifier, projIdentifier);
      verify(gitXSettingsHelper).setDefaultRepoForRemoteEntity(accountIdentifier, orgIdentifier, projIdentifier);
    }
  }
}
