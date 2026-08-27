/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.utils;

import static io.harness.rule.OwnerRule.DIPENDRA;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class ActionInputSchemaValidatorTest extends CategoryTest {
  @Test
  @Owner(developers = DIPENDRA)
  @Category(UnitTests.class)
  public void nullSchema_succeeds() {
    assertThatCode(() -> ActionInputSchemaValidator.validate(null)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = DIPENDRA)
  @Category(UnitTests.class)
  public void validSchema_succeeds() {
    Map<String, Object> schema = Map.of("type", "object", "properties", Map.of("name", Map.of("type", "string")));
    assertThatCode(() -> ActionInputSchemaValidator.validate(schema)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = DIPENDRA)
  @Category(UnitTests.class)
  public void wrongRootType_throws() {
    Map<String, Object> schema = Map.of("type", "array");
    assertThatThrownBy(() -> ActionInputSchemaValidator.validate(schema))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("type");
  }

  @Test
  @Owner(developers = DIPENDRA)
  @Category(UnitTests.class)
  public void missingRootType_throws() {
    Map<String, Object> schema = Map.of("properties", Map.of("name", Map.of("type", "string")));
    assertThatThrownBy(() -> ActionInputSchemaValidator.validate(schema)).isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = DIPENDRA)
  @Category(UnitTests.class)
  public void invalidPropertyType_throws() {
    Map<String, Object> schema = Map.of("type", "object", "properties", Map.of("ts", Map.of("type", "date")));
    assertThatThrownBy(() -> ActionInputSchemaValidator.validate(schema))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("date");
  }

  @Test
  @Owner(developers = DIPENDRA)
  @Category(UnitTests.class)
  public void propertiesNotObject_throws() {
    Map<String, Object> schema = new HashMap<>();
    schema.put("type", "object");
    schema.put("properties", "bad");
    assertThatThrownBy(() -> ActionInputSchemaValidator.validate(schema))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("properties");
  }

  @Test
  @Owner(developers = DIPENDRA)
  @Category(UnitTests.class)
  public void validBinding_succeeds() {
    Map<String, Object> binding = Map.of("source", "entity", "key", "metadata.name");
    Map<String, Object> schema =
        Map.of("type", "object", "properties", Map.of("entityName", Map.of("type", "string", "binding", binding)));
    assertThatCode(() -> ActionInputSchemaValidator.validate(schema)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = DIPENDRA)
  @Category(UnitTests.class)
  public void bindingMissingSource_throws() {
    Map<String, Object> binding = Map.of("key", "metadata.name");
    Map<String, Object> schema =
        Map.of("type", "object", "properties", Map.of("entityName", Map.of("type", "string", "binding", binding)));
    assertThatThrownBy(() -> ActionInputSchemaValidator.validate(schema))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("source");
  }

  @Test
  @Owner(developers = DIPENDRA)
  @Category(UnitTests.class)
  public void bindingMissingKey_throws() {
    Map<String, Object> binding = Map.of("source", "entity");
    Map<String, Object> schema =
        Map.of("type", "object", "properties", Map.of("entityName", Map.of("type", "string", "binding", binding)));
    assertThatThrownBy(() -> ActionInputSchemaValidator.validate(schema))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("key");
  }

  @Test
  @Owner(developers = DIPENDRA)
  @Category(UnitTests.class)
  public void requiredRefersToMissingProperty_throws() {
    Map<String, Object> schema = new HashMap<>();
    schema.put("type", "object");
    schema.put("properties", Map.of("name", Map.of("type", "string")));
    schema.put("required", List.of("nonexistent"));
    assertThatThrownBy(() -> ActionInputSchemaValidator.validate(schema))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("nonexistent");
  }
}
