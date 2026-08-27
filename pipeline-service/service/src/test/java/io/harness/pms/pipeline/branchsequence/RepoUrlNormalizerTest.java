/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.branchsequence;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.rule.OwnerRule.HARSH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CI)
public class RepoUrlNormalizerTest extends CategoryTest {
  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testNormalizeHttpsUrl() {
    String result = RepoUrlNormalizer.normalize("https://github.com/harness/harness-core.git");
    assertThat(result).isEqualTo("github.com/harness/harness-core");
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testNormalizeHttpsUrlWithoutGitSuffix() {
    String result = RepoUrlNormalizer.normalize("https://github.com/harness/harness-core");
    assertThat(result).isEqualTo("github.com/harness/harness-core");
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testNormalizeSshUrl() {
    String result = RepoUrlNormalizer.normalize("git@github.com:harness/harness-core.git");
    assertThat(result).isEqualTo("github.com/harness/harness-core");
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testNormalizeSshUrlWithoutGitSuffix() {
    String result = RepoUrlNormalizer.normalize("git@github.com:harness/harness-core");
    assertThat(result).isEqualTo("github.com/harness/harness-core");
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testNormalizeUrlWithPort() {
    String result = RepoUrlNormalizer.normalize("https://github.com:443/harness/harness-core.git");
    assertThat(result).isEqualTo("github.com/harness/harness-core");
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testNormalizeUrlWithCredentials() {
    // Note: GitClientHelper retains the credentials in the host portion,
    // so URLs with credentials will have the credentials as part of the normalized output
    String result = RepoUrlNormalizer.normalize("https://user:password@github.com/harness/harness-core.git");
    // The credentials are retained in the host portion by GitClientHelper
    assertThat(result).isNotNull();
    assertThat(result).contains("github.com");
    assertThat(result).contains("harness/harness-core");
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testNormalizeUrlCaseInsensitive() {
    String result = RepoUrlNormalizer.normalize("https://GitHub.COM/Harness/Harness-Core.git");
    assertThat(result).isEqualTo("github.com/harness/harness-core");
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testNormalizeNullUrl() {
    String result = RepoUrlNormalizer.normalize(null);
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testNormalizeEmptyUrl() {
    String result = RepoUrlNormalizer.normalize("");
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testNormalizeBranch() {
    assertThat(RepoUrlNormalizer.normalizeBranch("refs/heads/main")).isEqualTo("main");
    assertThat(RepoUrlNormalizer.normalizeBranch("refs/heads/feature/test")).isEqualTo("feature/test");
    assertThat(RepoUrlNormalizer.normalizeBranch("main")).isEqualTo("main");
    assertThat(RepoUrlNormalizer.normalizeBranch("feature/test")).isEqualTo("feature/test");
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testNormalizeBranchNull() {
    assertThat(RepoUrlNormalizer.normalizeBranch(null)).isNull();
    assertThat(RepoUrlNormalizer.normalizeBranch("")).isNull();
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testNormalizeBitbucketSshUrl() {
    String result = RepoUrlNormalizer.normalize("git@bitbucket.org:harness/harness-core.git");
    assertThat(result).isEqualTo("bitbucket.org/harness/harness-core");
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testNormalizeGitLabUrl() {
    String result = RepoUrlNormalizer.normalize("https://gitlab.com/harness/harness-core.git");
    assertThat(result).isEqualTo("gitlab.com/harness/harness-core");
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testNormalizeAzureDevOpsUrl() {
    String result = RepoUrlNormalizer.normalize("https://dev.azure.com/harness/project/_git/harness-core");
    assertThat(result).isEqualTo("dev.azure.com/harness/project/_git/harness-core");
  }
}
