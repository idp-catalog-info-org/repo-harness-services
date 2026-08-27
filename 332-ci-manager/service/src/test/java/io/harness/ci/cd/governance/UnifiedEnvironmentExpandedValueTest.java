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
import io.harness.category.element.UnitTests;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class UnifiedEnvironmentExpandedValueTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetKey_shouldReturnEnvironment() {
    UnifiedEnvironmentExpandedValue value = UnifiedEnvironmentExpandedValue.builder().build();
    assertThat(value.getKey()).as("key should be 'environment'").isEqualTo(YAMLFieldNameConstants.ENVIRONMENT);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testToJson_whenSingleEnvironment_shouldReturnSingleEnvJson() {
    UnifiedSingleEnvironmentExpandedValue env = UnifiedSingleEnvironmentExpandedValue.builder()
                                                    .id("env1")
                                                    .name("dev-env")
                                                    .description("Development environment")
                                                    .build();

    UnifiedEnvironmentExpandedValue value =
        UnifiedEnvironmentExpandedValue.builder().isMultiEnv(false).environment(env).build();

    String json = value.toJson();

    assertThat(json).as("should contain environment id").contains("\"id\" : \"env1\"");
    assertThat(json).as("should contain environment name").contains("\"name\" : \"dev-env\"");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testToJson_whenSingleEnvironmentWithInfrastructure_shouldProcessInfraArray() {
    ObjectNode infraNodeContent = MAPPER.createObjectNode();
    infraNodeContent.put("type", "KubernetesDirect");
    infraNodeContent.put("namespace", "default");

    UnifiedInfrastructureExpandedValue infra =
        UnifiedInfrastructureExpandedValue.builder().id("infra1").name("k8s-infra").infraNode(infraNodeContent).build();

    UnifiedSingleEnvironmentExpandedValue env = UnifiedSingleEnvironmentExpandedValue.builder()
                                                    .id("env1")
                                                    .name("dev-env")
                                                    .infrastructure(Collections.singletonList(infra))
                                                    .build();

    UnifiedEnvironmentExpandedValue value =
        UnifiedEnvironmentExpandedValue.builder().isMultiEnv(false).environment(env).build();

    String json = value.toJson();

    JsonNode result = parseJson(json);
    JsonNode infraArray = result.get("infrastructure");
    assertThat(infraArray).as("infrastructure array should exist").isNotNull();
    assertThat(infraArray.isArray()).as("infrastructure should be an array").isTrue();
    assertThat(infraArray.size()).as("should have one infra entry").isEqualTo(1);

    JsonNode firstInfra = infraArray.get(0);
    assertThat(firstInfra.has("id")).as("infra should have id field").isTrue();
    assertThat(firstInfra.get("id").asText()).as("id value should be preserved").isEqualTo("infra1");
    assertThat(firstInfra.get("name").asText()).as("name value should be preserved").isEqualTo("k8s-infra");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testToJson_whenMultiEnvironmentNotEnvGroup_shouldReturnItemsWithSequential() {
    UnifiedSingleEnvironmentExpandedValue env1 =
        UnifiedSingleEnvironmentExpandedValue.builder().id("env1").name("dev").build();
    UnifiedSingleEnvironmentExpandedValue env2 =
        UnifiedSingleEnvironmentExpandedValue.builder().id("env2").name("staging").build();

    UnifiedEnvironmentExpandedValue value = UnifiedEnvironmentExpandedValue.builder()
                                                .isMultiEnv(true)
                                                .isEnvGroup(false)
                                                .sequential(true)
                                                .environments(Arrays.asList(env1, env2))
                                                .build();

    String json = value.toJson();
    JsonNode result = parseJson(json);

    assertThat(result.has("sequential")).as("should have sequential field").isTrue();
    assertThat(result.get("sequential").asBoolean()).as("sequential should be true").isTrue();
    assertThat(result.has("items")).as("should have items field").isTrue();
    assertThat(result.get("items").size()).as("should have 2 environment items").isEqualTo(2);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testToJson_whenMultiEnvironmentWithEnvGroup_shouldReturnGroupStructure() {
    UnifiedSingleEnvironmentExpandedValue env1 =
        UnifiedSingleEnvironmentExpandedValue.builder().id("env1").name("prod-1").build();

    UnifiedEnvGroupExpandedValue envGroup = UnifiedEnvGroupExpandedValue.builder()
                                                .id("grp1")
                                                .name("prod-group")
                                                .items(Collections.singletonList(env1))
                                                .build();

    UnifiedEnvironmentExpandedValue value = UnifiedEnvironmentExpandedValue.builder()
                                                .isMultiEnv(true)
                                                .isEnvGroup(true)
                                                .sequential(false)
                                                .environmentGroup(envGroup)
                                                .build();

    String json = value.toJson();
    JsonNode result = parseJson(json);

    assertThat(result.has("group")).as("should have group field for env group").isTrue();
    assertThat(result.get("group").has("items")).as("group should contain items").isTrue();
    assertThat(result.get("sequential").asBoolean()).as("sequential should be false").isFalse();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testToJson_whenMultiEnvironmentWithInfrastructure_shouldMoveInfraNodesInEachEnv() {
    ObjectNode infraNodeContent = MAPPER.createObjectNode();
    infraNodeContent.put("connectorRef", "k8s-connector");

    UnifiedInfrastructureExpandedValue infra =
        UnifiedInfrastructureExpandedValue.builder().id("infra1").name("k8s").infraNode(infraNodeContent).build();

    UnifiedSingleEnvironmentExpandedValue env1 = UnifiedSingleEnvironmentExpandedValue.builder()
                                                     .id("env1")
                                                     .name("dev")
                                                     .infrastructure(Collections.singletonList(infra))
                                                     .build();

    UnifiedEnvironmentExpandedValue value = UnifiedEnvironmentExpandedValue.builder()
                                                .isMultiEnv(true)
                                                .isEnvGroup(false)
                                                .sequential(false)
                                                .environments(Collections.singletonList(env1))
                                                .build();

    String json = value.toJson();
    JsonNode result = parseJson(json);

    JsonNode items = result.get("items");
    assertThat(items).as("should have items").isNotNull();
    JsonNode firstEnvInfra = items.get(0).get("infrastructure");
    assertThat(firstEnvInfra).as("infrastructure should exist in environment").isNotNull();
    JsonNode firstInfraItem = firstEnvInfra.get(0);
    assertThat(firstInfraItem.has("connectorRef")).as("infraNode fields should be moved to parent").isTrue();
    assertThat(firstInfraItem.has("infraNode")).as("infraNode should be removed").isFalse();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testToJson_whenMultiEnvironmentWithNullInfra_shouldNotThrow() {
    UnifiedSingleEnvironmentExpandedValue env1 =
        UnifiedSingleEnvironmentExpandedValue.builder().id("env1").name("dev").build();

    UnifiedEnvironmentExpandedValue value = UnifiedEnvironmentExpandedValue.builder()
                                                .isMultiEnv(true)
                                                .isEnvGroup(false)
                                                .sequential(null)
                                                .environments(Collections.singletonList(env1))
                                                .build();

    String json = value.toJson();
    JsonNode result = parseJson(json);

    assertThat(result.get("sequential").asBoolean()).as("null sequential should default to false").isFalse();
    assertThat(result.has("items")).as("should still have items").isTrue();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testToJson_whenMultiEnvGroupWithInfrastructure_shouldMoveInfraNodesInGroupItems() {
    ObjectNode infraNodeContent = MAPPER.createObjectNode();
    infraNodeContent.put("region", "us-east-1");

    UnifiedInfrastructureExpandedValue infra =
        UnifiedInfrastructureExpandedValue.builder().id("infra1").name("aws").infraNode(infraNodeContent).build();

    UnifiedSingleEnvironmentExpandedValue env1 = UnifiedSingleEnvironmentExpandedValue.builder()
                                                     .id("env1")
                                                     .name("prod")
                                                     .infrastructure(Collections.singletonList(infra))
                                                     .build();

    UnifiedEnvGroupExpandedValue envGroup = UnifiedEnvGroupExpandedValue.builder()
                                                .id("grp1")
                                                .name("prod-group")
                                                .items(Collections.singletonList(env1))
                                                .build();

    UnifiedEnvironmentExpandedValue value = UnifiedEnvironmentExpandedValue.builder()
                                                .isMultiEnv(true)
                                                .isEnvGroup(true)
                                                .sequential(true)
                                                .environmentGroup(envGroup)
                                                .build();

    String json = value.toJson();
    JsonNode result = parseJson(json);

    JsonNode groupItems = result.get("group").get("items");
    assertThat(groupItems).as("group items should exist").isNotNull();
    JsonNode firstEnvInfra = groupItems.get(0).get("infrastructure");
    assertThat(firstEnvInfra).as("infrastructure in group env should exist").isNotNull();
    JsonNode firstInfraItem = firstEnvInfra.get(0);
    assertThat(firstInfraItem.get("region").asText()).as("infraNode field should be moved up").isEqualTo("us-east-1");
    assertThat(firstInfraItem.has("infraNode")).as("infraNode child should be removed").isFalse();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testToJson_whenSingleEnvironmentWithNullInfrastructure_shouldNotThrow() {
    UnifiedSingleEnvironmentExpandedValue env =
        UnifiedSingleEnvironmentExpandedValue.builder().id("env1").name("dev-env").build();

    UnifiedEnvironmentExpandedValue value =
        UnifiedEnvironmentExpandedValue.builder().isMultiEnv(false).environment(env).build();

    String json = value.toJson();

    JsonNode result = parseJson(json);
    assertThat(result.get("id").asText()).as("should contain environment id").isEqualTo("env1");
    assertThat(result.has("infrastructure")).as("should not have infrastructure when null").isFalse();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testToJson_whenMultiEnvironmentWithEmptyList_shouldThrowNpe() {
    UnifiedEnvironmentExpandedValue value = UnifiedEnvironmentExpandedValue.builder()
                                                .isMultiEnv(true)
                                                .isEnvGroup(false)
                                                .sequential(false)
                                                .environments(Collections.emptyList())
                                                .build();

    assertThatThrownBy(value::toJson)
        .as("empty environments list causes NPE in getEnvironmentNodes")
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
