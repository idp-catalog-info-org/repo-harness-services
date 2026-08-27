/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.utils.configfile;

import static io.harness.rule.OwnerRule.CHIRAG_S;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class FileStoreSpecTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private ObjectMapper objectMapper;

  static class Wrapper {
    @JsonProperty("val")
    @JsonDeserialize(using = FileStoreSpec.SingleOrListOfStringsDeserializer.class)
    List<String> val;

    List<String> getVal() {
      return val;
    }
  }

  @Before
  public void setUp() {
    objectMapper = new ObjectMapper();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testIsGit_whenGitFileStoreSpec_shouldReturnTrue() {
    FileStoreSpec spec = GitFileStoreSpec.builder().build();
    assertThat(spec.isGit()).as("GitFileStoreSpec should return true for isGit()").isTrue();
    assertThat(spec.isHarness()).as("GitFileStoreSpec should return false for isHarness()").isFalse();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testIsHarness_whenHarnessFileStoreSpec_shouldReturnTrue() {
    FileStoreSpec spec = HarnessFileStoreSpec.builder().build();
    assertThat(spec.isHarness()).as("HarnessFileStoreSpec should return true for isHarness()").isTrue();
    assertThat(spec.isGit()).as("HarnessFileStoreSpec should return false for isGit()").isFalse();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testDeserialize_whenNullToken_shouldReturnNull() throws Exception {
    Wrapper w = objectMapper.readValue("{\"val\": null}", Wrapper.class);
    assertThat(w.getVal()).as("Null JSON value should result in null field").isNull();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testDeserialize_whenFieldMissing_shouldReturnNull() throws Exception {
    Wrapper w = objectMapper.readValue("{}", Wrapper.class);
    assertThat(w.getVal()).as("Missing field should result in null").isNull();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testDeserialize_whenSingleString_shouldReturnSingletonList() throws Exception {
    Wrapper w = objectMapper.readValue("{\"val\": \"hello\"}", Wrapper.class);
    assertThat(w.getVal()).as("Single string should deserialize to singleton list").containsExactly("hello");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testDeserialize_whenEmptyString_shouldReturnEmptyList() throws Exception {
    Wrapper w = objectMapper.readValue("{\"val\": \"\"}", Wrapper.class);
    assertThat(w.getVal()).as("Empty string should deserialize to empty list").isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testDeserialize_whenStringArray_shouldReturnList() throws Exception {
    Wrapper w = objectMapper.readValue("{\"val\": [\"a\", \"b\", \"c\"]}", Wrapper.class);
    assertThat(w.getVal()).as("String array should deserialize to list of strings").containsExactly("a", "b", "c");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testDeserialize_whenArrayWithNulls_shouldFilterNulls() throws Exception {
    Wrapper w = objectMapper.readValue("{\"val\": [\"a\", null, \"c\"]}", Wrapper.class);
    assertThat(w.getVal()).as("Array with null elements should filter them out").containsExactly("a", "c");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testDeserialize_whenEmptyArray_shouldReturnEmptyList() throws Exception {
    Wrapper w = objectMapper.readValue("{\"val\": []}", Wrapper.class);
    assertThat(w.getVal()).as("Empty array should deserialize to empty list").isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testDeserialize_whenNumericValue_shouldReturnStringRepresentation() throws Exception {
    Wrapper w = objectMapper.readValue("{\"val\": 42}", Wrapper.class);
    assertThat(w.getVal()).as("Numeric value should be converted to string in list").containsExactly("42");
  }
}
