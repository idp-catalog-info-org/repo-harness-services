/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.cd.governance;

import static io.harness.rule.OwnerRule.CHIRAG_S;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.app.beans.entities.ServiceBasicInfo;
import io.harness.category.element.UnitTests;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class UnifiedServiceExpandedValueTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetKey_shouldReturnService() {
    UnifiedServiceExpandedValue value = UnifiedServiceExpandedValue.builder().build();
    assertThat(value.getKey()).as("key should be 'service'").isEqualTo(YAMLFieldNameConstants.SERVICE);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testToJson_whenSingleService_shouldReturnServiceDirectly() {
    ServiceBasicInfo service = ServiceBasicInfo.builder()
                                   .id("svc1")
                                   .name("my-service")
                                   .description("A test service")
                                   .accountIdentifier("acc1")
                                   .orgIdentifier("org1")
                                   .projectIdentifier("proj1")
                                   .build();

    UnifiedServiceExpandedValue value =
        UnifiedServiceExpandedValue.builder().servicesInfo(Collections.singletonList(service)).build();

    String json = value.toJson();
    JsonNode result = parseJson(json);

    assertThat(result.has("id")).as("single service JSON should contain id").isTrue();
    assertThat(result.get("id").asText()).as("id should match").isEqualTo("svc1");
    assertThat(result.get("name").asText()).as("name should match").isEqualTo("my-service");
    assertThat(result.get("description").asText()).as("description should match").isEqualTo("A test service");
    assertThat(result.get("accountIdentifier").asText()).as("accountIdentifier should match").isEqualTo("acc1");
    assertThat(result.has("items")).as("single service should not have items wrapper").isFalse();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testToJson_whenMultipleServices_shouldReturnItemsWrapper() {
    ServiceBasicInfo svc1 = ServiceBasicInfo.builder().id("svc1").name("service-one").build();
    ServiceBasicInfo svc2 = ServiceBasicInfo.builder().id("svc2").name("service-two").build();

    UnifiedServiceExpandedValue value =
        UnifiedServiceExpandedValue.builder().servicesInfo(Arrays.asList(svc1, svc2)).build();

    String json = value.toJson();
    JsonNode result = parseJson(json);

    assertThat(result.has("items")).as("multi-service should have items field").isTrue();
    JsonNode items = result.get("items");
    assertThat(items.isArray()).as("items should be an array").isTrue();
    assertThat(items.size()).as("items should contain 2 services").isEqualTo(2);
    assertThat(items.get(0).get("id").asText()).as("first service id").isEqualTo("svc1");
    assertThat(items.get(1).get("id").asText()).as("second service id").isEqualTo("svc2");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testToJson_whenSingleServiceWithNullFields_shouldOmitNullFields() {
    ServiceBasicInfo service = ServiceBasicInfo.builder().id("svc1").name("minimal").build();

    UnifiedServiceExpandedValue value =
        UnifiedServiceExpandedValue.builder().servicesInfo(Collections.singletonList(service)).build();

    String json = value.toJson();
    JsonNode result = parseJson(json);

    assertThat(result.get("id").asText()).as("id should be present").isEqualTo("svc1");
    assertThat(result.get("name").asText()).as("name should be present").isEqualTo("minimal");
    assertThat(result.has("description")).as("null description should be omitted").isFalse();
    assertThat(result.has("accountIdentifier")).as("null accountIdentifier should be omitted").isFalse();
    assertThat(result.has("tags")).as("null tags should be omitted").isFalse();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testToJson_whenSingleServiceWithTags_shouldIncludeTags() {
    Map<String, String> tags = new HashMap<>();
    tags.put("env", "prod");
    tags.put("team", "platform");

    ServiceBasicInfo service = ServiceBasicInfo.builder().id("svc1").name("tagged-svc").tags(tags).build();

    UnifiedServiceExpandedValue value =
        UnifiedServiceExpandedValue.builder().servicesInfo(Collections.singletonList(service)).build();

    String json = value.toJson();
    JsonNode result = parseJson(json);

    assertThat(result.has("tags")).as("tags should be present when non-null").isTrue();
    assertThat(result.get("tags").get("env").asText()).as("tag 'env' should be 'prod'").isEqualTo("prod");
    assertThat(result.get("tags").get("team").asText()).as("tag 'team' should be 'platform'").isEqualTo("platform");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testToJson_whenMultipleServicesWithThreeItems_shouldReturnAllInItems() {
    ServiceBasicInfo svc1 = ServiceBasicInfo.builder().id("svc1").name("alpha").build();
    ServiceBasicInfo svc2 = ServiceBasicInfo.builder().id("svc2").name("beta").build();
    ServiceBasicInfo svc3 = ServiceBasicInfo.builder().id("svc3").name("gamma").build();

    UnifiedServiceExpandedValue value =
        UnifiedServiceExpandedValue.builder().servicesInfo(Arrays.asList(svc1, svc2, svc3)).build();

    String json = value.toJson();
    JsonNode result = parseJson(json);

    assertThat(result.has("items")).as("should have items wrapper for 3 services").isTrue();
    assertThat(result.get("items").size()).as("items array should have 3 entries").isEqualTo(3);
    assertThat(result.get("items").get(2).get("name").asText()).as("third service name").isEqualTo("gamma");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testToJson_whenEmptyList_shouldReturnEmptyJson() {
    UnifiedServiceExpandedValue value =
        UnifiedServiceExpandedValue.builder().servicesInfo(Collections.emptyList()).build();

    String json = value.toJson();
    JsonNode result = parseJson(json);

    assertThat(result.has("items")).as("empty list is omitted by NON_EMPTY serialization").isFalse();
    assertThat(result.size()).as("result should be an empty object").isEqualTo(0);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testToJson_whenNullList_shouldThrowNpe() {
    UnifiedServiceExpandedValue value = UnifiedServiceExpandedValue.builder().servicesInfo(null).build();

    assertThatThrownBy(value::toJson)
        .as("null servicesInfo should throw NullPointerException")
        .isInstanceOf(NullPointerException.class);
  }

  private JsonNode parseJson(String json) {
    try {
      return MAPPER.readTree(json);
    } catch (Exception e) {
      throw new RuntimeException("Failed to parse JSON: " + json, e);
    }
  }
}
