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
import io.harness.serializer.JsonUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class HarnessFileStoreSpecTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testFromInputs_whenValidSecretFiles_shouldBuildSpec() {
    Map<String, Object> inputs = new HashMap<>();
    inputs.put("secretFiles", Arrays.asList("secret1", "secret2"));

    HarnessFileStoreSpec result = HarnessFileStoreSpec.fromInputs(inputs);

    assertThat(result.getSecretFilePaths())
        .as("Secret file paths should match input list")
        .containsExactly("secret1", "secret2");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testFromInputs_whenNullInputs_shouldReturnEmptySpec() {
    HarnessFileStoreSpec result = HarnessFileStoreSpec.fromInputs(null);

    assertThat(result).as("Null inputs should return non-null spec").isNotNull();
    assertThat(result.getSecretFilePaths()).as("Null inputs should produce empty secret file paths").isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testFromInputs_whenEmptyInputs_shouldReturnEmptySecretFiles() {
    Map<String, Object> inputs = new HashMap<>();

    HarnessFileStoreSpec result = HarnessFileStoreSpec.fromInputs(inputs);

    assertThat(result.getSecretFilePaths()).as("Empty inputs should produce empty secret file paths").isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testFromInputs_withHyphenatedAlias_shouldDeserializeCorrectly() {
    Map<String, Object> inputs = new HashMap<>();
    inputs.put("secret-files", Arrays.asList("secretA", "secretB"));

    HarnessFileStoreSpec result = HarnessFileStoreSpec.fromInputs(inputs);

    assertThat(result.getSecretFilePaths())
        .as("Hyphenated alias 'secret-files' should map to secretFilePaths")
        .containsExactly("secretA", "secretB");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testFromInputs_whenSingleSecretFile_shouldReturnSingletonList() {
    Map<String, Object> inputs = new HashMap<>();
    inputs.put("secretFiles", Collections.singletonList("onlySecret"));

    HarnessFileStoreSpec result = HarnessFileStoreSpec.fromInputs(inputs);

    assertThat(result.getSecretFilePaths())
        .as("Single secret file should produce singleton list")
        .hasSize(1)
        .containsExactly("onlySecret");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testFromInputs_whenConversionFails_shouldThrowInvalidRequestException() {
    Map<String, Object> inputs = new HashMap<>();
    inputs.put("secretFiles", "someValue");

    try (MockedStatic<JsonUtils> jsonUtilsMockedStatic = Mockito.mockStatic(JsonUtils.class)) {
      jsonUtilsMockedStatic.when(() -> JsonUtils.convertValue(Mockito.any(), Mockito.eq(HarnessFileStoreSpec.class)))
          .thenThrow(new IllegalArgumentException("conversion failed"));

      assertThatThrownBy(() -> HarnessFileStoreSpec.fromInputs(inputs))
          .as("IllegalArgumentException from JsonUtils should be wrapped in InvalidRequestException")
          .isInstanceOf(InvalidRequestException.class)
          .hasMessageContaining("Invalid Harness config file inputs")
          .hasMessageContaining("conversion failed");
    }
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testIsHarness_shouldReturnTrue() {
    HarnessFileStoreSpec spec = HarnessFileStoreSpec.builder().build();
    assertThat(spec.isHarness()).as("HarnessFileStoreSpec should identify as harness").isTrue();
    assertThat(spec.isGit()).as("HarnessFileStoreSpec should not identify as git").isFalse();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testFromInputs_whenValidFiles_shouldBuildSpec() {
    Map<String, Object> inputs = new HashMap<>();
    inputs.put("files", Arrays.asList("/account/file1.yaml", "/account/file2.yaml"));

    HarnessFileStoreSpec result = HarnessFileStoreSpec.fromInputs(inputs);

    assertThat(result.getFilePaths())
        .as("Harness File Store file paths should match input list")
        .containsExactly("/account/file1.yaml", "/account/file2.yaml");
    assertThat(result.getSecretFilePaths()).as("Secret file paths should be empty when only files set").isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testFromInputs_whenSingleFile_shouldReturnSingletonList() {
    Map<String, Object> inputs = new HashMap<>();
    inputs.put("files", Collections.singletonList("/account/onlyFile.yaml"));

    HarnessFileStoreSpec result = HarnessFileStoreSpec.fromInputs(inputs);

    assertThat(result.getFilePaths())
        .as("Single file should produce singleton list")
        .hasSize(1)
        .containsExactly("/account/onlyFile.yaml");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testFromInputs_whenFilesAndSecretFiles_shouldBuildBoth() {
    Map<String, Object> inputs = new HashMap<>();
    inputs.put("files", Collections.singletonList("/account/file.yaml"));
    inputs.put("secretFiles", Collections.singletonList("secretRef"));

    HarnessFileStoreSpec result = HarnessFileStoreSpec.fromInputs(inputs);

    assertThat(result.getFilePaths()).as("File paths should match").containsExactly("/account/file.yaml");
    assertThat(result.getSecretFilePaths()).as("Secret file paths should match").containsExactly("secretRef");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testFromInputs_whenNullInputs_shouldReturnEmptyFiles() {
    HarnessFileStoreSpec result = HarnessFileStoreSpec.fromInputs(null);

    assertThat(result.getFilePaths()).as("Null inputs should produce empty file paths").isEmpty();
  }
}
