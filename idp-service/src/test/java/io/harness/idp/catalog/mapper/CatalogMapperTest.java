/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.mapper;

import static io.harness.idp.catalog.utils.Constants.COMPONENT_KIND;
import static io.harness.rule.OwnerRule.KOTA_KARTHIK;
import static io.harness.rule.OwnerRule.ROUNAK;
import static io.harness.rule.OwnerRule.VIGNESWARA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.exception.UnexpectedException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.idp.catalog.beans.ReferenceType;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.GitReferencedCatalogEntity;
import io.harness.idp.catalog.entities.InlineCatalogEntity;
import io.harness.idp.catalog.utils.CatalogUtils;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.EntityResponse;
import io.harness.spec.server.idp.v1.model.EntityResponseScorecards;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockedStatic;

@OwnedBy(HarnessTeam.IDP)
public class CatalogMapperTest extends CategoryTest {
  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testPresentationYaml() {
    InlineCatalogEntity entity = new InlineCatalogEntity();
    entity.setAccountIdentifier("test-account");
    entity.setOrgIdentifier("test-org");
    entity.setProjectIdentifier("test-project");
    entity.setIdentifier("test-id");
    entity.setApiVersion("harness.io/v1");
    entity.setKind(COMPONENT_KIND);
    entity.setType("service");
    entity.setName("Test Component");
    entity.setDescription("Test Description");
    entity.setOwner("user:test-owner");
    entity.setTags(Arrays.asList("tag1", "tag2"));
    entity.setSourceLocation("url:https://github.com/test/repo");

    Map<String, Object> spec = new HashMap<>();
    spec.put("system", Arrays.asList("system1"));
    spec.put("providesApis", Arrays.asList("api1", "api2"));
    entity.setSpec(spec);

    Map<String, Object> metadata = new HashMap<>();
    metadata.put("custom", "value");
    entity.setMetadata(metadata);

    Map<String, Set<String>> relations = new HashMap<>();
    relations.put("ownedBy", new HashSet<>(Arrays.asList("user:owner")));
    relations.put("partOf", new HashSet<>(Arrays.asList("system1", "system2")));
    relations.put("dependsOn", new HashSet<>(Arrays.asList("component:dep1")));
    entity.setRelations(relations);

    String yaml = CatalogMapper.presentationYaml(entity);

    assertThat(yaml).isNotNull();
    assertThat(yaml).contains("kind: Component");
    assertThat(yaml).contains("type: service");
    assertThat(yaml).contains("name: Test Component");
    assertThat(yaml).contains("description: Test Description");
    assertThat(yaml).contains("tags:");
    assertThat(yaml).contains("- tag1");
    assertThat(yaml).contains("- tag2");
    assertThat(yaml).contains("dependsOn:");
    assertThat(yaml).contains("- component:dep1");
    assertThat(yaml).doesNotContain("accountIdentifier");
    assertThat(yaml).doesNotContain("uniqueId");
    assertThat(yaml).doesNotContain("createdAt");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testPresentationYamlWithPartOfFiltering() {
    InlineCatalogEntity entity = new InlineCatalogEntity();
    entity.setIdentifier("test-id");
    entity.setKind(COMPONENT_KIND);
    entity.setName("Test Component");

    Map<String, Object> spec = new HashMap<>();
    spec.put("system", Arrays.asList("system1", "system2"));
    entity.setSpec(spec);

    Map<String, Set<String>> relations = new HashMap<>();
    relations.put("partOf", new HashSet<>(Arrays.asList("system1", "system2", "system3")));
    entity.setRelations(relations);

    String yaml = CatalogMapper.presentationYaml(entity);

    assertThat(yaml).contains("partOf:");
    assertThat(yaml).contains("- system3");
    // system1 and system2 should still appear in spec.system
    assertThat(yaml).contains("system:");
    assertThat(yaml).contains("- system1");
    assertThat(yaml).contains("- system2");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testYamlToEntityInline() {
    try (MockedStatic<GitAwareContextHelper> mockedStatic = mockStatic(GitAwareContextHelper.class)) {
      mockedStatic.when(GitAwareContextHelper::isRemoteEntity).thenReturn(false);

      ScopeInfo scopeInfo = ScopeInfo.builder().accountIdentifier("test-account").uniqueId("unique-id").build();

      String yaml = "apiVersion: harness.io/v1\n"
          + "kind: Component\n"
          + "type: service\n"
          + "name: Test Component\n"
          + "owner: user:test-owner\n"
          + "orgIdentifier: test-org\n"
          + "projectIdentifier: test-project\n"
          + "metadata:\n"
          + "  description: Test Description\n"
          + "  tags:\n"
          + "    - tag1\n"
          + "    - tag2\n"
          + "  annotations:\n"
          + "    backstage.io/source-location: url:https://github.com/test/repo\n"
          + "spec:\n"
          + "  system: system1\n"
          + "  providesApis:\n"
          + "    - api1\n"
          + "    - api2\n";

      CatalogEntity entity = CatalogMapper.yamlToEntity(scopeInfo, "test-id", "component", yaml, null);

      assertThat(entity).isInstanceOf(InlineCatalogEntity.class);
      assertThat(entity.getReferenceType()).isEqualTo(ReferenceType.INLINE);
      assertThat(entity.getAccountIdentifier()).isEqualTo("test-account");
      assertThat(entity.getOrgIdentifier()).isEqualTo("test-org");
      assertThat(entity.getProjectIdentifier()).isEqualTo("test-project");
      assertThat(entity.getIdentifier()).isEqualTo("test-id");
      assertThat(entity.getKind()).isEqualTo(COMPONENT_KIND);
      assertThat(entity.getType()).isEqualTo("service");
      assertThat(entity.getName()).isEqualTo("Test Component");
      assertThat(entity.getDescription()).isEqualTo("Test Description");
      assertThat(entity.getOwner()).isEqualTo("user:test-owner");
      assertThat(entity.getTags()).containsExactly("tag1", "tag2");
      assertThat(entity.getSourceLocation()).isEqualTo("url:https://github.com/test/repo");
      assertThat(entity.getSpec()).containsKey("providesApis");
      assertThat(entity.getSpec()).containsEntry("system", Arrays.asList("system1"));
      assertThat(entity.getRelations()).containsEntry("ownedBy", Set.of("user:test-owner"));
      assertThat(entity.getRelations()).containsEntry("partOf", Set.of("system1"));
      assertThat(entity.getParentUniqueId()).isEqualTo("unique-id");
    }
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testYamlToEntityGitReferenced() {
    try (MockedStatic<GitAwareContextHelper> mockedStatic = mockStatic(GitAwareContextHelper.class)) {
      mockedStatic.when(GitAwareContextHelper::isRemoteEntity).thenReturn(true);

      ScopeInfo scopeInfo = ScopeInfo.builder().accountIdentifier("test-account").uniqueId("unique-id").build();

      String yaml = "apiVersion: harness.io/v1\n"
          + "kind: Component\n"
          + "name: Git Component\n";

      CatalogEntity entity = CatalogMapper.yamlToEntity(scopeInfo, "git-id", "component", yaml, null);

      assertThat(entity).isInstanceOf(GitReferencedCatalogEntity.class);
      assertThat(entity.getReferenceType()).isEqualTo(ReferenceType.GIT);
    }
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testYamlToEntityWithRelations() {
    try (MockedStatic<GitAwareContextHelper> mockedStatic = mockStatic(GitAwareContextHelper.class)) {
      mockedStatic.when(GitAwareContextHelper::isRemoteEntity).thenReturn(false);

      ScopeInfo scopeInfo = ScopeInfo.builder().accountIdentifier("test-account").uniqueId("unique-id").build();

      String yaml = "apiVersion: harness.io/v1\n"
          + "kind: Component\n"
          + "name: Component with Relations\n"
          + "spec:\n"
          + "  subcomponentOf: parent-component\n"
          + "  members:\n"
          + "    - user:member1\n"
          + "    - user:member2\n"
          + "  parent: parent-group\n"
          + "  dependsOn:\n"
          + "    - component:dep1\n"
          + "    - component:dep2\n";

      Map<String, Object> decorator = new HashMap<>();
      decorator.put("test", "value");

      CatalogEntity entity = CatalogMapper.yamlToEntity(scopeInfo, "test-id", "component", yaml, decorator);

      assertThat(entity.getRelations()).containsEntry("partOf", Set.of("parent-component"));
      assertThat(entity.getRelations()).containsEntry("hasMember", Set.of("user:member1", "user:member2"));
      assertThat(entity.getRelations()).containsEntry("childOf", Set.of("parent-group"));
      assertThat(entity.getRelations()).containsEntry("dependsOn", Set.of("component:dep1", "component:dep2"));
      assertThat(entity.getDecorator()).containsEntry("test", "value");
    }
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testYamlToEntityWithLeaderRelations() {
    try (MockedStatic<GitAwareContextHelper> mockedStatic = mockStatic(GitAwareContextHelper.class)) {
      mockedStatic.when(GitAwareContextHelper::isRemoteEntity).thenReturn(false);

      ScopeInfo scopeInfo = ScopeInfo.builder().accountIdentifier("test-account").uniqueId("unique-id").build();

      String yaml = "apiVersion: harness.io/v1\n"
          + "kind: Group\n"
          + "name: Group with Leaders\n"
          + "spec:\n"
          + "  leaders:\n"
          + "    - user:leader1\n"
          + "    - user:leader2\n";

      CatalogEntity entity = CatalogMapper.yamlToEntity(scopeInfo, "test-id", "group", yaml, null);

      assertThat(entity.getRelations()).containsEntry("hasLeader", Set.of("user:leader1", "user:leader2"));
    }
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testPresentationYamlExcludesHasLeaderFromSpec() {
    InlineCatalogEntity entity = new InlineCatalogEntity();
    entity.setIdentifier("test-id");
    entity.setKind("group");
    entity.setName("Test Group");

    Map<String, Object> spec = new HashMap<>();
    entity.setSpec(spec);

    Map<String, Set<String>> relations = new HashMap<>();
    relations.put("hasLeader", new HashSet<>(List.of("user:leader1")));
    entity.setRelations(relations);

    String yaml = CatalogMapper.presentationYaml(entity);

    assertThat(yaml).doesNotContain("hasLeader");
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  @SuppressWarnings("unchecked")
  public void testYamlToEntityStripsSystemManagedMetadataApis() {
    // The system-managed metadata.apis subtree (populated by the API endpoint extraction processor
    // into the decorator, and merged into the YAML the UI sees on GET) must never be persisted from
    // submitted YAML. yamlToEntity drops it; other metadata fields are preserved. The decorator is
    // untouched.
    try (MockedStatic<GitAwareContextHelper> mockedStatic = mockStatic(GitAwareContextHelper.class)) {
      mockedStatic.when(GitAwareContextHelper::isRemoteEntity).thenReturn(false);

      ScopeInfo scopeInfo = ScopeInfo.builder().accountIdentifier("test-account").uniqueId("unique-id").build();

      String yaml = "apiVersion: harness.io/v1\n"
          + "kind: API\n"
          + "name: Payments API\n"
          + "metadata:\n"
          + "  customKey: keep-me\n"
          + "  apis:\n"
          + "    specHash: deadbeef\n"
          + "    paths:\n"
          + "      \"GET /fake\":\n"
          + "        method: GET\n";

      CatalogEntity entity = CatalogMapper.yamlToEntity(scopeInfo, "payments", "api", yaml, null);

      assertThat(entity.getMetadata()).doesNotContainKey("apis");
      // Sibling metadata is preserved — we strip only the system subtree.
      assertThat(entity.getMetadata()).containsEntry("customKey", "keep-me");
      // The rebuilt yaml must not carry metadata.apis either.
      assertThat(entity.getYaml()).doesNotContain("specHash");
    }
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testEntityToResponse() {
    InlineCatalogEntity entity = new InlineCatalogEntity();
    entity.setAccountIdentifier("test-account");
    entity.setOrgIdentifier("test-org");
    entity.setProjectIdentifier("test-project");
    entity.setIdentifier("test-id");
    entity.setReferenceType(ReferenceType.INLINE);
    entity.setKind(COMPONENT_KIND);
    entity.setType("service");
    entity.setName("Test Component");
    entity.setDescription("Test Description");
    entity.setOwner("user:test-owner");
    entity.setTags(Arrays.asList("tag1", "tag2"));
    entity.setCreatedAt(1000L);
    entity.setLastUpdatedAt(2000L);
    entity.setYaml("test: yaml");

    Map<String, Object> spec = new HashMap<>();
    spec.put("lifecycle", "production");
    entity.setSpec(spec);

    Map<String, Object> metadata = new HashMap<>();
    metadata.put("custom", "value");
    entity.setMetadata(metadata);

    Map<String, Set<String>> relations = new HashMap<>();
    relations.put("ownedBy", Set.of("user:owner"));
    entity.setRelations(relations);

    List<Map<String, String>> statuses = new ArrayList<>();
    Map<String, String> status = new HashMap<>();
    status.put("type", "health");
    status.put("level", "ok");
    status.put("message", "Healthy");
    statuses.add(status);
    entity.setStatus(statuses);

    EntityResponseScorecards scorecards = new EntityResponseScorecards();

    String userFavoriteEntityRefs = "component:account/test-id,api:test";

    EntityResponse response = CatalogMapper.entityToResponse(
        entity, "Org Name", "Project Name", userFavoriteEntityRefs, null, scorecards, false);

    assertThat(response.getIdentifier()).isEqualTo("test-id");
    assertThat(response.getEntityRef()).isEqualTo(CatalogUtils.entityRef(entity));
    assertThat(response.getOrgIdentifier()).isEqualTo("test-org");
    assertThat(response.getOrgName()).isEqualTo("Org Name");
    assertThat(response.getProjectIdentifier()).isEqualTo("test-project");
    assertThat(response.getProjectName()).isEqualTo("Project Name");
    assertThat(response.getScope()).isEqualTo(EntityResponse.ScopeEnum.PROJECT);
    assertThat(response.getReferenceType()).isEqualTo(EntityResponse.ReferenceTypeEnum.INLINE);
    assertThat(response.getKindIdentifier()).isEqualTo(COMPONENT_KIND);
    assertThat(response.getType()).isEqualTo("service");
    assertThat(response.getName()).isEqualTo("Test Component");
    assertThat(response.getDescription()).isEqualTo("Test Description");
    assertThat(response.getOwner()).isEqualTo("user:test-owner");
    assertThat(response.getTags()).containsExactly("tag1", "tag2");
    assertThat(response.getLifecycle()).isEqualTo("production");
    assertThat(response.getCreated()).isEqualTo(1000L);
    assertThat(response.getUpdated()).isEqualTo(2000L);
    assertThat(response.getYaml()).isEqualTo("test: yaml");
    assertThat(response.getStatus()).hasSize(1);
    assertThat(response.getStatus().get(0).getType()).isEqualTo("health");
    assertThat(response.getStatus().get(0).getLevel()).isEqualTo("ok");
    assertThat(response.getStatus().get(0).getMessage()).isEqualTo("Healthy");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testEntityToResponseWithGitDetails() {
    GitReferencedCatalogEntity entity = new GitReferencedCatalogEntity();
    entity.setAccountIdentifier("test-account");
    entity.setIdentifier("test-id");
    entity.setKind(COMPONENT_KIND);
    entity.setName("Git Component");
    entity.setReferenceType(ReferenceType.GIT);
    entity.setYaml("test: yaml");

    try (MockedStatic<IDPGitXMapper> mockedStatic = mockStatic(IDPGitXMapper.class)) {
      mockedStatic.when(IDPGitXMapper::getEntityGitDetails).thenReturn(null);
      mockedStatic.when(IDPGitXMapper::getCacheResponseFromGitContext).thenReturn(null);

      EntityResponse response = CatalogMapper.entityToResponse(entity, null, null, null, null, null, false);

      assertThat(response.getGitDetails()).isNull();
      assertThat(response.getCacheResponseData()).isNull();
    }
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testMergeYamlWithPlaceholdersSimple() {
    Map<String, Object> yamlNode = new HashMap<>();
    yamlNode.put("key1", "value1");
    yamlNode.put("$text", "placeholder");

    Map<String, Object> decoratorNode = new HashMap<>();
    decoratorNode.put("$text", "replaced text");

    Object result = CatalogMapper.mergeYamlWithPlaceholders(yamlNode, decoratorNode);

    assertThat(result).isInstanceOf(Map.class);
    Map<String, Object> resultMap = (Map<String, Object>) result;
    assertThat(resultMap).containsEntry("key1", "value1");
    assertThat(resultMap).containsEntry("$text", "replaced text");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testMergeYamlWithPlaceholdersYaml() {
    Map<String, Object> yamlNode = new HashMap<>();
    yamlNode.put("key1", "value1");
    yamlNode.put("$yaml", "placeholder");

    Map<String, Object> decoratorNode = new HashMap<>();
    decoratorNode.put("$yaml", "key2: value2\nkey3: value3");

    Object result = CatalogMapper.mergeYamlWithPlaceholders(yamlNode, decoratorNode);

    assertThat(result).isInstanceOf(Map.class);
    Map<String, Object> resultMap = (Map<String, Object>) result;
    assertThat(resultMap).containsEntry("key1", "value1");
    assertThat(resultMap).containsEntry("key2", "value2");
    assertThat(resultMap).containsEntry("key3", "value3");
    assertThat(resultMap).doesNotContainKey("$yaml");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testMergeYamlWithPlaceholdersJson() {
    Map<String, Object> yamlNode = new HashMap<>();
    yamlNode.put("key1", "value1");
    yamlNode.put("$json", "placeholder");

    Map<String, Object> decoratorNode = new HashMap<>();
    decoratorNode.put("$json", "{\"key2\": \"value2\", \"key3\": \"value3\"}");

    Object result = CatalogMapper.mergeYamlWithPlaceholders(yamlNode, decoratorNode);

    assertThat(result).isInstanceOf(Map.class);
    Map<String, Object> resultMap = (Map<String, Object>) result;
    assertThat(resultMap).containsEntry("key1", "value1");
    assertThat(resultMap).containsEntry("key2", "value2");
    assertThat(resultMap).containsEntry("key3", "value3");
    assertThat(resultMap).doesNotContainKey("$json");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testMergeYamlWithPlaceholdersInvalidJson() {
    Map<String, Object> yamlNode = new HashMap<>();
    yamlNode.put("$json", "placeholder");

    Map<String, Object> decoratorNode = new HashMap<>();
    decoratorNode.put("$json", "invalid json");

    assertThatThrownBy(() -> CatalogMapper.mergeYamlWithPlaceholders(yamlNode, decoratorNode))
        .isInstanceOf(UnexpectedException.class);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testMergeYamlWithPlaceholdersNested() {
    Map<String, Object> nestedMap = new HashMap<>();
    nestedMap.put("nested1", "value1");

    Map<String, Object> yamlNode = new HashMap<>();
    yamlNode.put("key1", nestedMap);

    Map<String, Object> nestedDecorator = new HashMap<>();
    nestedDecorator.put("$text", "replaced");

    Map<String, Object> decoratorNode = new HashMap<>();
    decoratorNode.put("key1", nestedDecorator);

    Object result = CatalogMapper.mergeYamlWithPlaceholders(yamlNode, decoratorNode);

    assertThat(result).isInstanceOf(Map.class);
    Map<String, Object> resultMap = (Map<String, Object>) result;
    assertThat(resultMap).containsKey("key1");
    assertThat(resultMap.get("key1")).isInstanceOf(Map.class);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testMergeYamlWithPlaceholdersNullDecorator() {
    Map<String, Object> yamlNode = new HashMap<>();
    yamlNode.put("key1", "value1");

    Object result = CatalogMapper.mergeYamlWithPlaceholders(yamlNode, null);

    assertThat(result).isEqualTo(yamlNode);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testMergeYamlWithPlaceholdersNonMapYaml() {
    String yamlNode = "not a map";
    Map<String, Object> decoratorNode = new HashMap<>();

    Object result = CatalogMapper.mergeYamlWithPlaceholders(yamlNode, decoratorNode);

    assertThat(result).isEqualTo(yamlNode);
  }
}
