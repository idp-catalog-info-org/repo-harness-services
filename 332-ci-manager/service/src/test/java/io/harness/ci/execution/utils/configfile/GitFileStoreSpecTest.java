/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.utils.configfile;

import static io.harness.rule.OwnerRule.CHIRAG_S;
import static io.harness.rule.OwnerRule.DANIEL;

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

public class GitFileStoreSpecTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testFromInputs_whenValidInputs_shouldBuildSpec() {
    Map<String, Object> inputs = new HashMap<>();
    inputs.put("connectorRef", "account.myGitConnector");
    inputs.put("repoName", "my-repo");
    inputs.put("branch", "main");
    inputs.put("paths", Arrays.asList("path/to/file1.yaml", "path/to/file2.yaml"));

    GitFileStoreSpec result = GitFileStoreSpec.fromInputs(inputs, StoreType.GITHUB);

    assertThat(result.getConnectorRef()).as("Connector ref should match input").isEqualTo("account.myGitConnector");
    assertThat(result.getRepoName()).as("Repo name should match input").isEqualTo("my-repo");
    assertThat(result.getBranch()).as("Branch should match input").isEqualTo("main");
    assertThat(result.getStoreType()).as("Store type should be overridden to GITHUB").isEqualTo(StoreType.GITHUB);
    assertThat(result.getPaths())
        .as("Paths should match input list")
        .containsExactly("path/to/file1.yaml", "path/to/file2.yaml");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testFromInputs_whenNullInputs_shouldThrowException() {
    assertThatThrownBy(() -> GitFileStoreSpec.fromInputs(null, StoreType.GIT))
        .as("Null inputs should throw InvalidRequestException")
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Config file git inputs are required");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testFromInputs_whenEmptyInputs_shouldThrowException() {
    assertThatThrownBy(() -> GitFileStoreSpec.fromInputs(Collections.emptyMap(), StoreType.GIT))
        .as("Empty inputs should throw InvalidRequestException")
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Config file git inputs are required");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testFromInputs_whenMissingConnectorRef_shouldThrowException() {
    Map<String, Object> inputs = new HashMap<>();
    inputs.put("repoName", "my-repo");
    inputs.put("branch", "main");

    assertThatThrownBy(() -> GitFileStoreSpec.fromInputs(inputs, StoreType.GIT))
        .as("Missing connector ref should throw InvalidRequestException")
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Git connector reference is required");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testFromInputs_whenConnectorRefIsNullString_shouldThrowException() {
    Map<String, Object> inputs = new HashMap<>();
    inputs.put("connectorRef", "null");
    inputs.put("repoName", "my-repo");

    assertThatThrownBy(() -> GitFileStoreSpec.fromInputs(inputs, StoreType.GIT))
        .as("Connector ref equal to 'null' string should throw InvalidRequestException")
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Git connector reference is required");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testFromInputs_whenCommitIdProvided_shouldSetCommitId() {
    Map<String, Object> inputs = new HashMap<>();
    inputs.put("connectorRef", "account.myGitConnector");
    inputs.put("repoName", "my-repo");
    inputs.put("commitId", "abc123");

    GitFileStoreSpec result = GitFileStoreSpec.fromInputs(inputs, StoreType.GITLAB);

    assertThat(result.getCommitId()).as("Commit ID should match input").isEqualTo("abc123");
    assertThat(result.getStoreType()).as("Store type should be GITLAB").isEqualTo(StoreType.GITLAB);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testFromInputs_whenPathsAreNull_shouldNormalizeTOEmptyList() {
    Map<String, Object> inputs = new HashMap<>();
    inputs.put("connectorRef", "account.myGitConnector");
    inputs.put("repoName", "my-repo");

    GitFileStoreSpec result = GitFileStoreSpec.fromInputs(inputs, StoreType.GIT);

    assertThat(result.getPaths()).as("Null paths should be normalized to empty list").isNotNull().isEmpty();
  }

  @Test
  @Owner(developers = DANIEL)
  @Category(UnitTests.class)
  public void testFromInputs_whenPathsIsJsonArrayString_shouldNormalizeToList() {
    Map<String, Object> inputs = new HashMap<>();
    inputs.put("connectorRef", "account.myGitConnector");
    inputs.put("repoName", "my-repo");
    inputs.put("branch", "main");
    inputs.put("paths", "[\"spot/elastigroup.json\", \"spot/README.md\"]");

    GitFileStoreSpec result = GitFileStoreSpec.fromInputs(inputs, StoreType.GITHUB);

    assertThat(result.getPaths())
        .as("JSON-array-shaped string should be parsed into individual paths")
        .containsExactly("spot/elastigroup.json", "spot/README.md");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testFromInputs_withAliasFields_shouldDeserializeCorrectly() {
    Map<String, Object> inputs = new HashMap<>();
    inputs.put("connector", "account.myConnector");
    inputs.put("repo", "my-repo");
    inputs.put("commit", "sha256");
    inputs.put("branch", "develop");

    GitFileStoreSpec result = GitFileStoreSpec.fromInputs(inputs, StoreType.BITBUCKET);

    assertThat(result.getConnectorRef())
        .as("Alias 'connector' should map to connectorRef")
        .isEqualTo("account.myConnector");
    assertThat(result.getRepoName()).as("Alias 'repo' should map to repoName").isEqualTo("my-repo");
    assertThat(result.getCommitId()).as("Alias 'commit' should map to commitId").isEqualTo("sha256");
  }
}
