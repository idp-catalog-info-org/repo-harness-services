/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.utils.configfile;

import static io.harness.rule.OwnerRule.CHIRAG_S;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;
import io.harness.unified.cd.service.manifests.StoreType;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class ResolvedConfigFileStoreSpecFactoryTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testFromInputs_whenHarnessStoreType_shouldReturnHarnessSpec() {
    Map<String, Object> inputs = new HashMap<>();
    inputs.put("uses", "harness");
    inputs.put("secretFiles", Arrays.asList("secret1"));

    FileStoreSpec result = ResolvedConfigFileStoreSpecFactory.fromInputs(inputs);

    assertThat(result)
        .as("Harness store type should return HarnessFileStoreSpec")
        .isInstanceOf(HarnessFileStoreSpec.class);
    assertThat(result.isHarness()).as("Result should identify as harness").isTrue();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testFromInputs_whenGithubStoreType_shouldReturnGitSpec() {
    Map<String, Object> inputs = new HashMap<>();
    inputs.put("uses", "github");
    inputs.put("connectorRef", "account.myConnector");
    inputs.put("repoName", "my-repo");

    FileStoreSpec result = ResolvedConfigFileStoreSpecFactory.fromInputs(inputs);

    assertThat(result).as("Github store type should return GitFileStoreSpec").isInstanceOf(GitFileStoreSpec.class);
    assertThat(result.isGit()).as("Result should identify as git").isTrue();
    GitFileStoreSpec gitSpec = (GitFileStoreSpec) result;
    assertThat(gitSpec.getStoreType()).as("Store type should be GITHUB").isEqualTo(StoreType.GITHUB);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testFromInputs_whenGitStoreType_shouldReturnGitSpec() {
    Map<String, Object> inputs = new HashMap<>();
    inputs.put("uses", "git");
    inputs.put("connectorRef", "account.myConnector");
    inputs.put("repoName", "my-repo");

    FileStoreSpec result = ResolvedConfigFileStoreSpecFactory.fromInputs(inputs);

    assertThat(result).as("Git store type should return GitFileStoreSpec").isInstanceOf(GitFileStoreSpec.class);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testFromInputs_whenGitlabStoreType_shouldReturnGitSpec() {
    Map<String, Object> inputs = new HashMap<>();
    inputs.put("uses", "gitlab");
    inputs.put("connectorRef", "account.myConnector");
    inputs.put("repoName", "my-repo");

    FileStoreSpec result = ResolvedConfigFileStoreSpecFactory.fromInputs(inputs);

    assertThat(result).as("Gitlab store type should return GitFileStoreSpec").isInstanceOf(GitFileStoreSpec.class);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testFromInputs_whenBitbucketStoreType_shouldReturnGitSpec() {
    Map<String, Object> inputs = new HashMap<>();
    inputs.put("uses", "bitbucket");
    inputs.put("connectorRef", "account.myConnector");
    inputs.put("repoName", "my-repo");

    FileStoreSpec result = ResolvedConfigFileStoreSpecFactory.fromInputs(inputs);

    assertThat(result).as("Bitbucket store type should return GitFileStoreSpec").isInstanceOf(GitFileStoreSpec.class);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testFromInputs_whenNullInputs_shouldThrowException() {
    assertThatThrownBy(() -> ResolvedConfigFileStoreSpecFactory.fromInputs(null))
        .as("Null inputs should throw InvalidRequestException")
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Config file inputs are required");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testFromInputs_whenEmptyInputs_shouldThrowException() {
    assertThatThrownBy(() -> ResolvedConfigFileStoreSpecFactory.fromInputs(Collections.emptyMap()))
        .as("Empty inputs should throw InvalidRequestException")
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Config file inputs are required");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testFromInputs_whenUnsupportedStoreType_shouldThrowException() {
    Map<String, Object> inputs = new HashMap<>();
    inputs.put("uses", "s3");

    assertThatThrownBy(() -> ResolvedConfigFileStoreSpecFactory.fromInputs(inputs))
        .as("Unsupported store type should throw InvalidRequestException")
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Unsupported config file store type");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testParseStoreType_whenUsesKeyPresent_shouldReturnStoreType() {
    Map<String, Object> map = new HashMap<>();
    map.put("uses", "github");

    StoreType result = ResolvedConfigFileStoreSpecFactory.parseStoreType(map);

    assertThat(result).as("'uses' key should resolve to GITHUB store type").isEqualTo(StoreType.GITHUB);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testParseStoreType_whenStoreTypeKeyPresent_shouldReturnStoreType() {
    Map<String, Object> map = new HashMap<>();
    map.put("storeType", "harness");

    StoreType result = ResolvedConfigFileStoreSpecFactory.parseStoreType(map);

    assertThat(result).as("'storeType' key should resolve to HARNESS store type").isEqualTo(StoreType.HARNESS);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testParseStoreType_whenUsesKeyTakesPrecedence_shouldReturnUsesValue() {
    Map<String, Object> map = new HashMap<>();
    map.put("uses", "github");
    map.put("storeType", "harness");

    StoreType result = ResolvedConfigFileStoreSpecFactory.parseStoreType(map);

    assertThat(result).as("'uses' key should take precedence over 'storeType'").isEqualTo(StoreType.GITHUB);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testParseStoreType_whenNoTypeKey_shouldThrowException() {
    Map<String, Object> map = new HashMap<>();
    map.put("connectorRef", "someValue");

    assertThatThrownBy(() -> ResolvedConfigFileStoreSpecFactory.parseStoreType(map))
        .as("Missing store type key should throw InvalidRequestException")
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Config file store type (uses / storeType) is required");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testParseStoreType_whenUnknownValue_shouldThrowException() {
    Map<String, Object> map = new HashMap<>();
    map.put("uses", "unknownStore");

    assertThatThrownBy(() -> ResolvedConfigFileStoreSpecFactory.parseStoreType(map))
        .as("Unknown store type value should throw InvalidRequestException")
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Unknown config file store type: unknownStore");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testParseStoreType_whenCaseInsensitive_shouldResolveCorrectly() {
    Map<String, Object> map = new HashMap<>();
    map.put("uses", "GitHub");

    StoreType result = ResolvedConfigFileStoreSpecFactory.parseStoreType(map);

    assertThat(result).as("Case-insensitive store type should resolve correctly").isEqualTo(StoreType.GITHUB);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testIsGitStoreType_shouldReturnTrueForGitTypes() {
    assertThat(ResolvedConfigFileStoreSpecFactory.isGitStoreType(StoreType.GITHUB))
        .as("GITHUB should be a git store type")
        .isTrue();
    assertThat(ResolvedConfigFileStoreSpecFactory.isGitStoreType(StoreType.GIT))
        .as("GIT should be a git store type")
        .isTrue();
    assertThat(ResolvedConfigFileStoreSpecFactory.isGitStoreType(StoreType.GITLAB))
        .as("GITLAB should be a git store type")
        .isTrue();
    assertThat(ResolvedConfigFileStoreSpecFactory.isGitStoreType(StoreType.BITBUCKET))
        .as("BITBUCKET should be a git store type")
        .isTrue();
    assertThat(ResolvedConfigFileStoreSpecFactory.isGitStoreType(StoreType.CODE))
        .as("CODE should be a git store type")
        .isTrue();
    assertThat(ResolvedConfigFileStoreSpecFactory.isGitStoreType(StoreType.AZURE))
        .as("AZURE should be a git store type")
        .isTrue();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testIsGitStoreType_shouldReturnFalseForNonGitTypes() {
    assertThat(ResolvedConfigFileStoreSpecFactory.isGitStoreType(StoreType.HARNESS))
        .as("HARNESS should not be a git store type")
        .isFalse();
    assertThat(ResolvedConfigFileStoreSpecFactory.isGitStoreType(StoreType.S3))
        .as("S3 should not be a git store type")
        .isFalse();
    assertThat(ResolvedConfigFileStoreSpecFactory.isGitStoreType(StoreType.HTTP))
        .as("HTTP should not be a git store type")
        .isFalse();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testFromInputs_whenCodeStoreType_shouldReturnGitSpec() {
    Map<String, Object> inputs = new HashMap<>();
    inputs.put("uses", "code");
    inputs.put("connectorRef", "account.myConnector");
    inputs.put("repoName", "my-repo");

    FileStoreSpec result = ResolvedConfigFileStoreSpecFactory.fromInputs(inputs);

    assertThat(result).as("Code store type should return GitFileStoreSpec").isInstanceOf(GitFileStoreSpec.class);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testFromInputs_whenAzureStoreType_shouldReturnGitSpec() {
    Map<String, Object> inputs = new HashMap<>();
    inputs.put("uses", "azure");
    inputs.put("connectorRef", "account.myConnector");
    inputs.put("repoName", "my-repo");

    FileStoreSpec result = ResolvedConfigFileStoreSpecFactory.fromInputs(inputs);

    assertThat(result).as("Azure store type should return GitFileStoreSpec").isInstanceOf(GitFileStoreSpec.class);
  }
}
