/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.mapper;

import static io.harness.rule.OwnerRule.KOTA_KARTHIK;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.sdk.CacheResponse;
import io.harness.gitsync.sdk.CacheState;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.CacheResponseData;
import io.harness.spec.server.idp.v1.model.GitDetails;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockedStatic;

@OwnedBy(HarnessTeam.IDP)
public class IDPGitXMapperTest extends CategoryTest {
  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetCacheResponseFromGitContext() {
    CacheResponse cacheResponse = CacheResponse.builder()
                                      .cacheState(CacheState.VALID_CACHE)
                                      .ttlLeft(3600L)
                                      .lastUpdatedAt(123456789L)
                                      .isSyncEnabled(true)
                                      .build();

    try (MockedStatic<GitAwareContextHelper> mockedStatic = mockStatic(GitAwareContextHelper.class)) {
      mockedStatic.when(GitAwareContextHelper::getCacheResponseFromScmGitMetadata).thenReturn(cacheResponse);

      CacheResponseData result = IDPGitXMapper.getCacheResponseFromGitContext();

      assertThat(result).isNotNull();
      assertThat(result.getCacheState()).isEqualTo(CacheResponseData.CacheStateEnum.VALID_CACHE);
      assertThat(result.getTtlLeft()).isEqualTo(3600L);
      assertThat(result.getLastUpdatedAt()).isEqualTo(123456789L);
      assertThat(result.isIsSyncEnabled()).isTrue();
    }
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetCacheResponseFromGitContextWithStaleCache() {
    CacheResponse cacheResponse = CacheResponse.builder()
                                      .cacheState(CacheState.STALE_CACHE)
                                      .ttlLeft(0L)
                                      .lastUpdatedAt(987654321L)
                                      .isSyncEnabled(false)
                                      .build();

    try (MockedStatic<GitAwareContextHelper> mockedStatic = mockStatic(GitAwareContextHelper.class)) {
      mockedStatic.when(GitAwareContextHelper::getCacheResponseFromScmGitMetadata).thenReturn(cacheResponse);

      CacheResponseData result = IDPGitXMapper.getCacheResponseFromGitContext();

      assertThat(result).isNotNull();
      assertThat(result.getCacheState()).isEqualTo(CacheResponseData.CacheStateEnum.STALE_CACHE);
      assertThat(result.getTtlLeft()).isEqualTo(0L);
      assertThat(result.getLastUpdatedAt()).isEqualTo(987654321L);
      assertThat(result.isIsSyncEnabled()).isFalse();
    }
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetCacheResponseFromGitContextWithValidCacheDisabled() {
    CacheResponse cacheResponse = CacheResponse.builder()
                                      .cacheState(CacheState.VALID_CACHE)
                                      .ttlLeft(100L)
                                      .lastUpdatedAt(111111111L)
                                      .isSyncEnabled(false)
                                      .build();

    try (MockedStatic<GitAwareContextHelper> mockedStatic = mockStatic(GitAwareContextHelper.class)) {
      mockedStatic.when(GitAwareContextHelper::getCacheResponseFromScmGitMetadata).thenReturn(cacheResponse);

      CacheResponseData result = IDPGitXMapper.getCacheResponseFromGitContext();

      assertThat(result).isNotNull();
      assertThat(result.getCacheState()).isEqualTo(CacheResponseData.CacheStateEnum.VALID_CACHE);
      assertThat(result.getTtlLeft()).isEqualTo(100L);
      assertThat(result.getLastUpdatedAt()).isEqualTo(111111111L);
      assertThat(result.isIsSyncEnabled()).isFalse();
    }
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetCacheResponseFromGitContextWithStaleCacheDisabled() {
    CacheResponse cacheResponse = CacheResponse.builder()
                                      .cacheState(CacheState.STALE_CACHE)
                                      .ttlLeft(200L)
                                      .lastUpdatedAt(222222222L)
                                      .isSyncEnabled(false)
                                      .build();

    try (MockedStatic<GitAwareContextHelper> mockedStatic = mockStatic(GitAwareContextHelper.class)) {
      mockedStatic.when(GitAwareContextHelper::getCacheResponseFromScmGitMetadata).thenReturn(cacheResponse);

      CacheResponseData result = IDPGitXMapper.getCacheResponseFromGitContext();

      assertThat(result).isNotNull();
      assertThat(result.getCacheState()).isEqualTo(CacheResponseData.CacheStateEnum.STALE_CACHE);
      assertThat(result.getTtlLeft()).isEqualTo(200L);
      assertThat(result.getLastUpdatedAt()).isEqualTo(222222222L);
      assertThat(result.isIsSyncEnabled()).isFalse();
    }
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetCacheResponseFromGitContextReturnsNullWhenNoCacheResponse() {
    try (MockedStatic<GitAwareContextHelper> mockedStatic = mockStatic(GitAwareContextHelper.class)) {
      mockedStatic.when(GitAwareContextHelper::getCacheResponseFromScmGitMetadata).thenReturn(null);

      CacheResponseData result = IDPGitXMapper.getCacheResponseFromGitContext();

      assertThat(result).isNull();
    }
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetEntityGitDetails() {
    EntityGitDetails entityGitDetails = EntityGitDetails.builder()
                                            .branch("main")
                                            .commitId("abc123def456")
                                            .filePath("/catalog/entity.yaml")
                                            .objectId("obj-123")
                                            .fileUrl("https://github.com/repo/blob/main/catalog/entity.yaml")
                                            .repoName("test-repo")
                                            .isHarnessCodeRepo(true)
                                            .build();

    try (MockedStatic<GitAwareContextHelper> mockedStatic = mockStatic(GitAwareContextHelper.class)) {
      mockedStatic.when(GitAwareContextHelper::getEntityGitDetailsFromScmGitMetadata).thenReturn(entityGitDetails);

      GitDetails result = IDPGitXMapper.getEntityGitDetails();

      assertThat(result).isNotNull();
      assertThat(result.getBranchName()).isEqualTo("main");
      assertThat(result.getCommitId()).isEqualTo("abc123def456");
      assertThat(result.getFilePath()).isEqualTo("/catalog/entity.yaml");
      assertThat(result.getObjectId()).isEqualTo("obj-123");
      assertThat(result.getFileUrl()).isEqualTo("https://github.com/repo/blob/main/catalog/entity.yaml");
      assertThat(result.getRepoName()).isEqualTo("test-repo");
      assertThat(result.isIsHarnessCodeRepo()).isTrue();
    }
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetEntityGitDetailsWithDifferentBranch() {
    EntityGitDetails entityGitDetails = EntityGitDetails.builder()
                                            .branch("develop")
                                            .commitId("xyz789")
                                            .filePath("/services/api.yaml")
                                            .objectId("obj-456")
                                            .fileUrl("https://gitlab.com/repo/blob/develop/services/api.yaml")
                                            .repoName("another-repo")
                                            .isHarnessCodeRepo(false)
                                            .build();

    try (MockedStatic<GitAwareContextHelper> mockedStatic = mockStatic(GitAwareContextHelper.class)) {
      mockedStatic.when(GitAwareContextHelper::getEntityGitDetailsFromScmGitMetadata).thenReturn(entityGitDetails);

      GitDetails result = IDPGitXMapper.getEntityGitDetails();

      assertThat(result).isNotNull();
      assertThat(result.getBranchName()).isEqualTo("develop");
      assertThat(result.getCommitId()).isEqualTo("xyz789");
      assertThat(result.getFilePath()).isEqualTo("/services/api.yaml");
      assertThat(result.getObjectId()).isEqualTo("obj-456");
      assertThat(result.getFileUrl()).isEqualTo("https://gitlab.com/repo/blob/develop/services/api.yaml");
      assertThat(result.getRepoName()).isEqualTo("another-repo");
      assertThat(result.isIsHarnessCodeRepo()).isFalse();
    }
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetEntityGitDetailsWithNullFields() {
    EntityGitDetails entityGitDetails = EntityGitDetails.builder()
                                            .branch(null)
                                            .commitId(null)
                                            .filePath(null)
                                            .objectId(null)
                                            .fileUrl(null)
                                            .repoName(null)
                                            .isHarnessCodeRepo(null)
                                            .build();

    try (MockedStatic<GitAwareContextHelper> mockedStatic = mockStatic(GitAwareContextHelper.class)) {
      mockedStatic.when(GitAwareContextHelper::getEntityGitDetailsFromScmGitMetadata).thenReturn(entityGitDetails);

      GitDetails result = IDPGitXMapper.getEntityGitDetails();

      assertThat(result).isNotNull();
      assertThat(result.getBranchName()).isNull();
      assertThat(result.getCommitId()).isNull();
      assertThat(result.getFilePath()).isNull();
      assertThat(result.getObjectId()).isNull();
      assertThat(result.getFileUrl()).isNull();
      assertThat(result.getRepoName()).isNull();
      assertThat(result.isIsHarnessCodeRepo()).isNull();
    }
  }
}
