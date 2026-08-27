/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.common.dependencyUtils;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.AVEESHA_JINDAL;
import static io.harness.rule.OwnerRule.KUSHAL_DASARI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.plan.DependencyEntry;
import io.harness.pms.contracts.plan.DependencyGraphProto;
import io.harness.pms.contracts.plan.EdgeLayoutList;
import io.harness.pms.contracts.plan.GraphLayoutNode;
import io.harness.pms.contracts.plan.StringArray;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class DependencyUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testResolveDependencyNodeUuids_WithValidDependencies_ShouldReturnNodeUuids() {
    YamlField dependsOnField = mock(YamlField.class);
    YamlNode dependsOnNode = mock(YamlNode.class);
    YamlNode dep1Node = mock(YamlNode.class);
    YamlNode dep2Node = mock(YamlNode.class);

    when(dependsOnField.getNode()).thenReturn(dependsOnNode);
    when(dependsOnNode.asArray()).thenReturn(Arrays.asList(dep1Node, dep2Node));
    JsonNode jsonNode1 = mock(JsonNode.class);
    JsonNode jsonNode2 = mock(JsonNode.class);
    when(dep1Node.getCurrJsonNode()).thenReturn(jsonNode1);
    when(dep2Node.getCurrJsonNode()).thenReturn(jsonNode2);
    when(jsonNode1.asText()).thenReturn("stage1");
    when(jsonNode2.asText()).thenReturn("stage2");

    Map<String, String> identifierToNodeUuid = new HashMap<>();
    identifierToNodeUuid.put("stage1", "uuid1");
    identifierToNodeUuid.put("stage2", "uuid2");

    List<String> result = DependencyUtils.resolveDependencyNodeUuids(dependsOnField, identifierToNodeUuid);

    assertThat(result).containsExactlyInAnyOrder("uuid1", "uuid2");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testResolveDependencyNodeUuids_WithMissingDependency_ShouldSkipMissingDependency() {
    YamlField dependsOnField = mock(YamlField.class);
    YamlNode dependsOnNode = mock(YamlNode.class);
    YamlNode dep1Node = mock(YamlNode.class);
    YamlNode dep2Node = mock(YamlNode.class);

    when(dependsOnField.getNode()).thenReturn(dependsOnNode);
    when(dependsOnNode.asArray()).thenReturn(Arrays.asList(dep1Node, dep2Node));
    JsonNode jsonNode1 = mock(JsonNode.class);
    JsonNode jsonNode2 = mock(JsonNode.class);
    when(dep1Node.getCurrJsonNode()).thenReturn(jsonNode1);
    when(dep2Node.getCurrJsonNode()).thenReturn(jsonNode2);
    when(jsonNode1.asText()).thenReturn("stage1");
    when(jsonNode2.asText()).thenReturn("nonexistent");

    Map<String, String> identifierToNodeUuid = new HashMap<>();
    identifierToNodeUuid.put("stage1", "uuid1");

    assertThatThrownBy(() -> DependencyUtils.resolveDependencyNodeUuids(dependsOnField, identifierToNodeUuid))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Dependency node 'nonexistent' not found");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testValidateNoCycles_WithValidGraph_ShouldNotThrowException() {
    Map<String, List<String>> dependencyGraph = new HashMap<>();
    dependencyGraph.put("node1", Arrays.asList());
    dependencyGraph.put("node2", Arrays.asList("node1"));
    dependencyGraph.put("node3", Arrays.asList("node1", "node2"));

    // Should not throw any exception
    DependencyUtils.validateNoCycles(dependencyGraph);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testValidateNoCycles_WithCircularDependency_ShouldThrowException() {
    Map<String, List<String>> dependencyGraph = new HashMap<>();
    dependencyGraph.put("node1", Arrays.asList("node2"));
    dependencyGraph.put("node2", Arrays.asList("node1"));

    assertThatThrownBy(() -> DependencyUtils.validateNoCycles(dependencyGraph))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessageContaining("Circular dependency detected");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testHasTopologicalSortCycle_WithValidGraph_ShouldReturnFalse() {
    Map<String, List<String>> dependencyGraph = new HashMap<>();
    dependencyGraph.put("node1", Arrays.asList());
    dependencyGraph.put("node2", Arrays.asList("node1"));
    dependencyGraph.put("node3", Arrays.asList("node1", "node2"));

    boolean result = DependencyUtils.hasTopologicalSortCycle(dependencyGraph);

    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testHasTopologicalSortCycle_WithCircularDependency_ShouldReturnTrue() {
    Map<String, List<String>> dependencyGraph = new HashMap<>();
    dependencyGraph.put("node1", Arrays.asList("node2"));
    dependencyGraph.put("node2", Arrays.asList("node1"));

    boolean result = DependencyUtils.hasTopologicalSortCycle(dependencyGraph);

    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testCreateDependencyGraph_WithStageNodeName_ShouldCreateValidGraph() {
    // Mock YAML structure for stages
    YamlField yamlField = mock(YamlField.class);
    YamlNode rootNode = mock(YamlNode.class);
    YamlNode stage1Node = mock(YamlNode.class);
    YamlNode stage2Node = mock(YamlNode.class);
    YamlField stage1Field = mock(YamlField.class);
    YamlField stage2Field = mock(YamlField.class);
    YamlNode stage1FieldNode = mock(YamlNode.class);
    YamlNode stage2FieldNode = mock(YamlNode.class);

    when(yamlField.getNode()).thenReturn(rootNode);
    when(rootNode.asArray()).thenReturn(Arrays.asList(stage1Node, stage2Node));

    // Mock stage1 with no dependencies
    when(stage1Node.getField(YAMLFieldNameConstants.STAGE)).thenReturn(stage1Field);
    when(stage1Field.getUuid()).thenReturn("uuid1");
    when(stage1Field.getNode()).thenReturn(stage1FieldNode);
    when(stage1FieldNode.getField(YAMLFieldNameConstants.DEPENDS_ON)).thenReturn(null);

    // Mock stage2 with dependency on stage1
    when(stage2Node.getField(YAMLFieldNameConstants.STAGE)).thenReturn(stage2Field);
    when(stage2Field.getUuid()).thenReturn("uuid2");
    when(stage2Field.getNode()).thenReturn(stage2FieldNode);

    YamlField dependsOnField = mock(YamlField.class);
    YamlNode dependsOnNode = mock(YamlNode.class);
    YamlNode depNode = mock(YamlNode.class);
    when(stage2FieldNode.getField(YAMLFieldNameConstants.DEPENDS_ON)).thenReturn(dependsOnField);
    when(dependsOnField.getNode()).thenReturn(dependsOnNode);
    when(dependsOnNode.asArray()).thenReturn(Arrays.asList(depNode));
    JsonNode jsonNode = mock(JsonNode.class);
    when(depNode.getCurrJsonNode()).thenReturn(jsonNode);
    when(jsonNode.asText()).thenReturn("stage1");

    Map<String, String> identifierToNodeUuid = new LinkedHashMap<>();
    identifierToNodeUuid.put("stage1", "uuid1");
    identifierToNodeUuid.put("stage2", "uuid2");

    DependencyGraphProto result =
        DependencyUtils.createDependencyGraph(yamlField, identifierToNodeUuid, YAMLFieldNameConstants.STAGE);

    assertThat(result).isNotNull();
    assertThat(result.getEntriesList()).hasSize(2);
    assertThat(result.getEntriesList().get(0).getNodeId()).isEqualTo("uuid1");
    assertThat(result.getEntriesList().get(0).getDependencies().getValuesList()).isEmpty();
    assertThat(result.getEntriesList().get(1).getNodeId()).isEqualTo("uuid2");
    assertThat(result.getEntriesList().get(1).getDependencies().getValuesList()).containsExactly("uuid1");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testCreateDependencyGraph_WithCustomNodeName_ShouldCreateValidGraph() {
    // Mock YAML structure for custom node type (e.g., "step")
    YamlField yamlField = mock(YamlField.class);
    YamlNode rootNode = mock(YamlNode.class);
    YamlNode step1Node = mock(YamlNode.class);
    YamlNode step2Node = mock(YamlNode.class);
    YamlField step1Field = mock(YamlField.class);
    YamlField step2Field = mock(YamlField.class);
    YamlNode step1FieldNode = mock(YamlNode.class);
    YamlNode step2FieldNode = mock(YamlNode.class);

    when(yamlField.getNode()).thenReturn(rootNode);
    when(rootNode.asArray()).thenReturn(Arrays.asList(step1Node, step2Node));

    // Mock step1 with no dependencies
    when(step1Node.getField("step")).thenReturn(step1Field);
    when(step1Field.getUuid()).thenReturn("step-uuid1");
    when(step1Field.getNode()).thenReturn(step1FieldNode);
    when(step1FieldNode.getField(YAMLFieldNameConstants.DEPENDS_ON)).thenReturn(null);

    // Mock step2 with dependency on step1
    when(step2Node.getField("step")).thenReturn(step2Field);
    when(step2Field.getUuid()).thenReturn("step-uuid2");
    when(step2Field.getNode()).thenReturn(step2FieldNode);

    YamlField dependsOnField = mock(YamlField.class);
    YamlNode dependsOnNode = mock(YamlNode.class);
    YamlNode depNode = mock(YamlNode.class);
    when(step2FieldNode.getField(YAMLFieldNameConstants.DEPENDS_ON)).thenReturn(dependsOnField);
    when(dependsOnField.getNode()).thenReturn(dependsOnNode);
    when(dependsOnNode.asArray()).thenReturn(Arrays.asList(depNode));
    JsonNode jsonNode = mock(JsonNode.class);
    when(depNode.getCurrJsonNode()).thenReturn(jsonNode);
    when(jsonNode.asText()).thenReturn("step1");

    Map<String, String> identifierToNodeUuid = new LinkedHashMap<>();
    identifierToNodeUuid.put("step1", "step-uuid1");
    identifierToNodeUuid.put("step2", "step-uuid2");

    DependencyGraphProto result = DependencyUtils.createDependencyGraph(yamlField, identifierToNodeUuid, "step");

    assertThat(result).isNotNull();
    assertThat(result.getEntriesList()).hasSize(2);
    assertThat(result.getEntriesList().get(0).getNodeId()).isEqualTo("step-uuid1");
    assertThat(result.getEntriesList().get(0).getDependencies().getValuesList()).isEmpty();
    assertThat(result.getEntriesList().get(1).getNodeId()).isEqualTo("step-uuid2");
    assertThat(result.getEntriesList().get(1).getDependencies().getValuesList()).containsExactly("step-uuid1");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testCreateDependencyGraph_WithCircularDependency_ShouldThrowException() {
    // Mock YAML structure with circular dependency
    YamlField yamlField = mock(YamlField.class);
    YamlNode rootNode = mock(YamlNode.class);
    YamlNode stage1Node = mock(YamlNode.class);
    YamlNode stage2Node = mock(YamlNode.class);
    YamlField stage1Field = mock(YamlField.class);
    YamlField stage2Field = mock(YamlField.class);
    YamlNode stage1FieldNode = mock(YamlNode.class);
    YamlNode stage2FieldNode = mock(YamlNode.class);

    when(yamlField.getNode()).thenReturn(rootNode);
    when(rootNode.asArray()).thenReturn(Arrays.asList(stage1Node, stage2Node));

    // Mock stage1 depends on stage2
    when(stage1Node.getField(YAMLFieldNameConstants.STAGE)).thenReturn(stage1Field);
    when(stage1Field.getUuid()).thenReturn("uuid1");
    when(stage1Field.getNode()).thenReturn(stage1FieldNode);

    YamlField dependsOnField1 = mock(YamlField.class);
    YamlNode dependsOnNode1 = mock(YamlNode.class);
    YamlNode depNode1 = mock(YamlNode.class);
    when(stage1FieldNode.getField(YAMLFieldNameConstants.DEPENDS_ON)).thenReturn(dependsOnField1);
    when(dependsOnField1.getNode()).thenReturn(dependsOnNode1);
    when(dependsOnNode1.asArray()).thenReturn(Arrays.asList(depNode1));
    JsonNode jsonNode1 = mock(JsonNode.class);
    when(depNode1.getCurrJsonNode()).thenReturn(jsonNode1);
    when(jsonNode1.asText()).thenReturn("stage2");

    // Mock stage2 depends on stage1 (circular)
    when(stage2Node.getField(YAMLFieldNameConstants.STAGE)).thenReturn(stage2Field);
    when(stage2Field.getUuid()).thenReturn("uuid2");
    when(stage2Field.getNode()).thenReturn(stage2FieldNode);

    YamlField dependsOnField2 = mock(YamlField.class);
    YamlNode dependsOnNode2 = mock(YamlNode.class);
    YamlNode depNode2 = mock(YamlNode.class);
    when(stage2FieldNode.getField(YAMLFieldNameConstants.DEPENDS_ON)).thenReturn(dependsOnField2);
    when(dependsOnField2.getNode()).thenReturn(dependsOnNode2);
    when(dependsOnNode2.asArray()).thenReturn(Arrays.asList(depNode2));
    JsonNode jsonNode2 = mock(JsonNode.class);
    when(depNode2.getCurrJsonNode()).thenReturn(jsonNode2);
    when(jsonNode2.asText()).thenReturn("stage1");

    Map<String, String> identifierToNodeUuid = new HashMap<>();
    identifierToNodeUuid.put("stage1", "uuid1");
    identifierToNodeUuid.put("stage2", "uuid2");

    assertThatThrownBy(
        () -> DependencyUtils.createDependencyGraph(yamlField, identifierToNodeUuid, YAMLFieldNameConstants.STAGE))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Circular dependency detected");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testCalculateLeafNodes_BasicCase() {
    // Test case: s1 -> [], s2 -> [s1], s3 -> [s1]
    // Expected leaf nodes: s2, s3 (nodes that no other node depends on)
    Map<String, List<String>> dependencyGraph = new HashMap<>();
    dependencyGraph.put("s1", Collections.emptyList());
    dependencyGraph.put("s2", Collections.singletonList("s1"));
    dependencyGraph.put("s3", Collections.singletonList("s1"));

    List<String> leafNodes = DependencyUtils.calculateLeafNodes(dependencyGraph);

    assertThat(leafNodes).hasSize(2);
    assertThat(leafNodes).containsExactlyInAnyOrder("s2", "s3");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testFindDependentNodes_WithValidDependencies_ShouldReturnDependentNodes() {
    // Create a dependency graph: stage1 -> [], stage2 -> [stage1], stage3 -> [stage2]
    DependencyGraphProto dependencyGraph =
        DependencyGraphProto.newBuilder()
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId("stage1-uuid")
                            .setDependencies(StringArray.newBuilder().build())
                            .build())
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId("stage2-uuid")
                            .setDependencies(StringArray.newBuilder().addValues("stage1-uuid").build())
                            .build())
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId("stage3-uuid")
                            .setDependencies(StringArray.newBuilder().addValues("stage2-uuid").build())
                            .build())
            .build();

    // Test: Find nodes that depend on stage1
    List<String> dependentNodes = DependencyUtils.findDependentNodes("stage1-uuid", dependencyGraph);
    assertThat(dependentNodes).hasSize(1);
    assertThat(dependentNodes).contains("stage2-uuid");

    // Test: Find nodes that depend on stage2
    List<String> dependentNodes2 = DependencyUtils.findDependentNodes("stage2-uuid", dependencyGraph);
    assertThat(dependentNodes2).hasSize(1);
    assertThat(dependentNodes2).contains("stage3-uuid");

    // Test: Find nodes that depend on stage3 (should be empty)
    List<String> dependentNodes3 = DependencyUtils.findDependentNodes("stage3-uuid", dependencyGraph);
    assertThat(dependentNodes3).isEmpty();
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testCalculateLeafNodes_ComplexCase() {
    // Test case: a -> [b,c], b -> [c,d], c -> [e,f,g], d -> [], e -> [], f -> [], g -> []
    // Expected leaf nodes: a (only node that no other node depends on)
    Map<String, List<String>> dependencyGraph = new HashMap<>();
    dependencyGraph.put("a", List.of("b", "c"));
    dependencyGraph.put("b", List.of("c", "d"));
    dependencyGraph.put("c", List.of("e", "f", "g"));
    dependencyGraph.put("d", Collections.emptyList());
    dependencyGraph.put("e", Collections.emptyList());
    dependencyGraph.put("f", Collections.emptyList());
    dependencyGraph.put("g", Collections.emptyList());

    List<String> leafNodes = DependencyUtils.calculateLeafNodes(dependencyGraph);

    assertThat(leafNodes).hasSize(1);
    assertThat(leafNodes).containsExactly("a");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testCalculateLeafNodes_LinearChain() {
    // Test case: stage1 -> [], stage2 -> [stage1], stage3 -> [stage2], stage4 -> [stage3]
    // Expected leaf nodes: stage4 (only node that no other node depends on)
    Map<String, List<String>> dependencyGraph = new HashMap<>();
    dependencyGraph.put("stage1", Collections.emptyList());
    dependencyGraph.put("stage2", Collections.singletonList("stage1"));
    dependencyGraph.put("stage3", Collections.singletonList("stage2"));
    dependencyGraph.put("stage4", Collections.singletonList("stage3"));

    List<String> leafNodes = DependencyUtils.calculateLeafNodes(dependencyGraph);

    assertThat(leafNodes).hasSize(1);
    assertThat(leafNodes).containsExactly("stage4");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testCalculateLeafNodes_MultipleLeafNodes() {
    // Test case: root -> [branch1, branch2], branch1 -> [leaf1, leaf2], branch2 -> [leaf3]
    // Expected leaf nodes: leaf1, leaf2, leaf3 (nodes that no other node depends on)
    Map<String, List<String>> dependencyGraph = new HashMap<>();
    dependencyGraph.put("root", List.of("branch1", "branch2"));
    dependencyGraph.put("branch1", List.of("leaf1", "leaf2"));
    dependencyGraph.put("branch2", Collections.singletonList("leaf3"));
    dependencyGraph.put("leaf1", Collections.emptyList());
    dependencyGraph.put("leaf2", Collections.emptyList());
    dependencyGraph.put("leaf3", Collections.emptyList());

    List<String> leafNodes = DependencyUtils.calculateLeafNodes(dependencyGraph);

    assertThat(leafNodes).hasSize(1);
    assertThat(leafNodes).containsExactly("root");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testCalculateLeafNodes_SingleNode() {
    // Test case: single node with no dependencies
    // Expected leaf nodes: the single node
    Map<String, List<String>> dependencyGraph = new HashMap<>();
    dependencyGraph.put("onlyNode", Collections.emptyList());

    List<String> leafNodes = DependencyUtils.calculateLeafNodes(dependencyGraph);

    assertThat(leafNodes).hasSize(1);
    assertThat(leafNodes).containsExactly("onlyNode");
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testComputeReverseDependencyGraph_LinearChain() {
    // Original: S1→S2→S3 (S2 depends on S1, S3 depends on S2)
    Map<String, List<String>> dependencyGraph = new HashMap<>();
    dependencyGraph.put("S1", Collections.emptyList());
    dependencyGraph.put("S2", Collections.singletonList("S1"));
    dependencyGraph.put("S3", Collections.singletonList("S2"));

    Map<String, List<String>> reversed = DependencyUtils.computeReverseDependencyGraph(dependencyGraph);

    // Reversed: S1 depends on [S2], S2 depends on [S3], S3 has no deps
    assertThat(reversed).hasSize(3);
    assertThat(reversed.get("S1")).containsExactly("S2");
    assertThat(reversed.get("S2")).containsExactly("S3");
    assertThat(reversed.get("S3")).isEmpty();
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testComputeReverseDependencyGraph_FanOut() {
    // Original: S3 depends on [S1, S2]
    Map<String, List<String>> dependencyGraph = new HashMap<>();
    dependencyGraph.put("S1", Collections.emptyList());
    dependencyGraph.put("S2", Collections.emptyList());
    dependencyGraph.put("S3", Arrays.asList("S1", "S2"));

    Map<String, List<String>> reversed = DependencyUtils.computeReverseDependencyGraph(dependencyGraph);

    // Reversed: S1 depends on [S3], S2 depends on [S3], S3 has no deps
    assertThat(reversed).hasSize(3);
    assertThat(reversed.get("S1")).containsExactly("S3");
    assertThat(reversed.get("S2")).containsExactly("S3");
    assertThat(reversed.get("S3")).isEmpty();
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testComputeReverseDependencyGraph_Diamond() {
    // Original: S1→[], S2→[S1], S3→[S1], S4→[S2, S3]
    Map<String, List<String>> dependencyGraph = new HashMap<>();
    dependencyGraph.put("S1", Collections.emptyList());
    dependencyGraph.put("S2", Collections.singletonList("S1"));
    dependencyGraph.put("S3", Collections.singletonList("S1"));
    dependencyGraph.put("S4", Arrays.asList("S2", "S3"));

    Map<String, List<String>> reversed = DependencyUtils.computeReverseDependencyGraph(dependencyGraph);

    // Reversed: S1 depends on [S2, S3], S2 depends on [S4], S3 depends on [S4], S4 has no deps
    assertThat(reversed).hasSize(4);
    assertThat(reversed.get("S1")).containsExactlyInAnyOrder("S2", "S3");
    assertThat(reversed.get("S2")).containsExactly("S4");
    assertThat(reversed.get("S3")).containsExactly("S4");
    assertThat(reversed.get("S4")).isEmpty();
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testFindDependentNodes_WithComplexDependencies_ShouldReturnCorrectDependents() {
    // Create a complex dependency graph:
    // stage1 -> [], stage2 -> [stage1], stage3 -> [stage1, stage2], stage4 -> [stage3]
    DependencyGraphProto dependencyGraph =
        DependencyGraphProto.newBuilder()
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId("stage1-uuid")
                            .setDependencies(StringArray.newBuilder().build())
                            .build())
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId("stage2-uuid")
                            .setDependencies(StringArray.newBuilder().addValues("stage1-uuid").build())
                            .build())
            .addEntries(
                DependencyEntry.newBuilder()
                    .setNodeId("stage3-uuid")
                    .setDependencies(StringArray.newBuilder().addValues("stage1-uuid").addValues("stage2-uuid").build())
                    .build())
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId("stage4-uuid")
                            .setDependencies(StringArray.newBuilder().addValues("stage3-uuid").build())
                            .build())
            .build();

    // Test: Find nodes that depend on stage1
    List<String> dependentsOfStage1 = DependencyUtils.findDependentNodes("stage1-uuid", dependencyGraph);
    assertThat(dependentsOfStage1).hasSize(2);
    assertThat(dependentsOfStage1).containsExactlyInAnyOrder("stage2-uuid", "stage3-uuid");

    // Test: Find nodes that depend on stage2
    List<String> dependentsOfStage2 = DependencyUtils.findDependentNodes("stage2-uuid", dependencyGraph);
    assertThat(dependentsOfStage2).hasSize(1);
    assertThat(dependentsOfStage2).contains("stage3-uuid");

    // Test: Find nodes that depend on stage3
    List<String> dependentsOfStage3 = DependencyUtils.findDependentNodes("stage3-uuid", dependencyGraph);
    assertThat(dependentsOfStage3).hasSize(1);
    assertThat(dependentsOfStage3).contains("stage4-uuid");
  }

  // ========== Order Preservation Tests ==========

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testBuildDependencyGraph_PreservesYamlInsertionOrder() {
    // YAML order: stage1, stage2, stage3, stage4
    // Without LinkedHashMap, iteration order could be arbitrary
    YamlField yamlField = mock(YamlField.class);
    YamlNode rootNode = mock(YamlNode.class);

    YamlNode stage1Node = mock(YamlNode.class);
    YamlNode stage2Node = mock(YamlNode.class);
    YamlNode stage3Node = mock(YamlNode.class);
    YamlNode stage4Node = mock(YamlNode.class);

    YamlField stage1Field = mock(YamlField.class);
    YamlField stage2Field = mock(YamlField.class);
    YamlField stage3Field = mock(YamlField.class);
    YamlField stage4Field = mock(YamlField.class);

    YamlNode stage1FieldNode = mock(YamlNode.class);
    YamlNode stage2FieldNode = mock(YamlNode.class);
    YamlNode stage3FieldNode = mock(YamlNode.class);
    YamlNode stage4FieldNode = mock(YamlNode.class);

    when(yamlField.getNode()).thenReturn(rootNode);
    when(rootNode.asArray()).thenReturn(Arrays.asList(stage1Node, stage2Node, stage3Node, stage4Node));

    when(stage1Node.getField(YAMLFieldNameConstants.STAGE)).thenReturn(stage1Field);
    when(stage1Field.getUuid()).thenReturn("uuid1");
    when(stage1Field.getNode()).thenReturn(stage1FieldNode);
    when(stage1FieldNode.getField(YAMLFieldNameConstants.DEPENDS_ON)).thenReturn(null);

    when(stage2Node.getField(YAMLFieldNameConstants.STAGE)).thenReturn(stage2Field);
    when(stage2Field.getUuid()).thenReturn("uuid2");
    when(stage2Field.getNode()).thenReturn(stage2FieldNode);
    when(stage2FieldNode.getField(YAMLFieldNameConstants.DEPENDS_ON)).thenReturn(null);

    when(stage3Node.getField(YAMLFieldNameConstants.STAGE)).thenReturn(stage3Field);
    when(stage3Field.getUuid()).thenReturn("uuid3");
    when(stage3Field.getNode()).thenReturn(stage3FieldNode);
    when(stage3FieldNode.getField(YAMLFieldNameConstants.DEPENDS_ON)).thenReturn(null);

    when(stage4Node.getField(YAMLFieldNameConstants.STAGE)).thenReturn(stage4Field);
    when(stage4Field.getUuid()).thenReturn("uuid4");
    when(stage4Field.getNode()).thenReturn(stage4FieldNode);
    when(stage4FieldNode.getField(YAMLFieldNameConstants.DEPENDS_ON)).thenReturn(null);

    // identifierToNodeUuid must be LinkedHashMap to preserve YAML order
    Map<String, String> identifierToNodeUuid = new LinkedHashMap<>();
    identifierToNodeUuid.put("stage1", "uuid1");
    identifierToNodeUuid.put("stage2", "uuid2");
    identifierToNodeUuid.put("stage3", "uuid3");
    identifierToNodeUuid.put("stage4", "uuid4");

    DependencyGraphProto result =
        DependencyUtils.createDependencyGraph(yamlField, identifierToNodeUuid, YAMLFieldNameConstants.STAGE);

    // Proto entries must preserve YAML insertion order: uuid1, uuid2, uuid3, uuid4
    List<String> entryOrder = new ArrayList<>();
    for (DependencyEntry entry : result.getEntriesList()) {
      entryOrder.add(entry.getNodeId());
    }
    assertThat(entryOrder).containsExactly("uuid1", "uuid2", "uuid3", "uuid4");
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testConvertDependencyGraphToMap_PreservesEntryOrder() {
    // Proto entries in order: C, A, B
    // The converted map must iterate in the same order: C, A, B
    DependencyGraphProto proto =
        DependencyGraphProto.newBuilder()
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId("uuid-C")
                            .setDependencies(StringArray.newBuilder().build())
                            .build())
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId("uuid-A")
                            .setDependencies(StringArray.newBuilder().addValues("uuid-C").build())
                            .build())
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId("uuid-B")
                            .setDependencies(StringArray.newBuilder().addValues("uuid-C").build())
                            .build())
            .build();

    Map<String, List<String>> result = DependencyUtils.convertDependencyGraphToMap(proto);

    List<String> keyOrder = new ArrayList<>(result.keySet());
    assertThat(keyOrder).containsExactly("uuid-C", "uuid-A", "uuid-B");
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testCalculateLeafNodes_PreservesYamlOrder() {
    // Graph: A→[], B→[A], C→[A], D→[A]
    // Leaf nodes (nothing depends on them): B, C, D
    // Must come back in insertion order, not hash order
    Map<String, List<String>> dependencyGraph = new LinkedHashMap<>();
    dependencyGraph.put("uuid-A", Collections.emptyList());
    dependencyGraph.put("uuid-B", Collections.singletonList("uuid-A"));
    dependencyGraph.put("uuid-C", Collections.singletonList("uuid-A"));
    dependencyGraph.put("uuid-D", Collections.singletonList("uuid-A"));

    List<String> leafNodes = DependencyUtils.calculateLeafNodes(dependencyGraph);

    assertThat(leafNodes).containsExactly("uuid-B", "uuid-C", "uuid-D");
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testFindRootNodes_PreservesEntryOrder() {
    // Multiple root nodes (no dependencies): A, C are roots, B depends on A
    // Roots must come back in proto entry order: A, C (not C, A)
    DependencyGraphProto proto =
        DependencyGraphProto.newBuilder()
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId("uuid-A")
                            .setDependencies(StringArray.newBuilder().build())
                            .build())
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId("uuid-B")
                            .setDependencies(StringArray.newBuilder().addValues("uuid-A").build())
                            .build())
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId("uuid-C")
                            .setDependencies(StringArray.newBuilder().build())
                            .build())
            .build();

    List<String> rootNodes = DependencyUtils.findRootNodesInDependencyGraph(proto);

    // Must be in proto entry order: A first, then C
    assertThat(rootNodes).containsExactly("uuid-A", "uuid-C");
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testFindDependentNodes_PreservesEntryOrder() {
    // A is root. B, C, D all depend on A.
    // findDependentNodes("A") must return B, C, D in proto entry order
    DependencyGraphProto proto =
        DependencyGraphProto.newBuilder()
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId("uuid-A")
                            .setDependencies(StringArray.newBuilder().build())
                            .build())
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId("uuid-B")
                            .setDependencies(StringArray.newBuilder().addValues("uuid-A").build())
                            .build())
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId("uuid-C")
                            .setDependencies(StringArray.newBuilder().addValues("uuid-A").build())
                            .build())
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId("uuid-D")
                            .setDependencies(StringArray.newBuilder().addValues("uuid-A").build())
                            .build())
            .build();

    List<String> dependents = DependencyUtils.findDependentNodes("uuid-A", proto);

    assertThat(dependents).containsExactly("uuid-B", "uuid-C", "uuid-D");
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testComputeReverseDependencyGraph_PreservesKeyOrder() {
    // Input order: A, B, C, D
    // Reverse graph keys must iterate in the same order
    Map<String, List<String>> dependencyGraph = new LinkedHashMap<>();
    dependencyGraph.put("uuid-A", Collections.emptyList());
    dependencyGraph.put("uuid-B", Collections.singletonList("uuid-A"));
    dependencyGraph.put("uuid-C", Collections.singletonList("uuid-A"));
    dependencyGraph.put("uuid-D", Arrays.asList("uuid-B", "uuid-C"));

    Map<String, List<String>> reversed = DependencyUtils.computeReverseDependencyGraph(dependencyGraph);

    List<String> keyOrder = new ArrayList<>(reversed.keySet());
    assertThat(keyOrder).containsExactly("uuid-A", "uuid-B", "uuid-C", "uuid-D");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testCollectActiveSubgraphNodes_FanOutInstanceRollback() {
    Map<String, List<String>> reversedGraph = new LinkedHashMap<>();
    reversedGraph.put("uuid-S4", Collections.emptyList());
    reversedGraph.put("uuid-S3", Collections.singletonList("uuid-S4"));
    reversedGraph.put("uuid-S1", Collections.singletonList("uuid-S3"));
    reversedGraph.put("uuid-S2", Collections.singletonList("uuid-S3"));

    Set<String> activeNodes = DependencyUtils.collectActiveSubgraphNodes(reversedGraph, List.of("uuid-S2"));

    assertThat(activeNodes).containsExactlyInAnyOrder("uuid-S2", "uuid-S3", "uuid-S4");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testPruneDependencyGraphAndLeafNodes_FanOutInstanceRollback() {
    Map<String, List<String>> reversedGraph = new LinkedHashMap<>();
    reversedGraph.put("uuid-S4", Collections.emptyList());
    reversedGraph.put("uuid-S3", Collections.singletonList("uuid-S4"));
    reversedGraph.put("uuid-S1", Collections.singletonList("uuid-S3"));
    reversedGraph.put("uuid-S2", Collections.singletonList("uuid-S3"));

    Set<String> activeNodes = DependencyUtils.collectActiveSubgraphNodes(reversedGraph, List.of("uuid-S2"));
    Map<String, List<String>> prunedGraph = DependencyUtils.pruneDependencyGraph(reversedGraph, activeNodes);

    assertThat(prunedGraph.keySet()).containsExactlyInAnyOrder("uuid-S2", "uuid-S3", "uuid-S4");
    assertThat(DependencyUtils.findRootNodesInDependencyGraphMap(prunedGraph)).containsExactly("uuid-S4");
    assertThat(DependencyUtils.calculateLeafNodes(prunedGraph)).containsExactly("uuid-S2");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testCollectActiveSubgraphNodesForDagPostExecutionRollback_FanOutMergeOnForwardLeaf() {
    // Forward: customS1 || customS2 -> merge -> deploy. Reversed: deploy is root with empty deps.
    Map<String, List<String>> reversedGraph = new LinkedHashMap<>();
    reversedGraph.put("uuid-deploy", Collections.emptyList());
    reversedGraph.put("uuid-merge", Collections.singletonList("uuid-deploy"));
    reversedGraph.put("uuid-c1", Collections.singletonList("uuid-merge"));
    reversedGraph.put("uuid-c2", Collections.singletonList("uuid-merge"));

    Set<String> activeNodes =
        DependencyUtils.collectActiveSubgraphNodesForDagPostExecutionRollback(reversedGraph, List.of("uuid-deploy"));

    assertThat(activeNodes).containsExactlyInAnyOrder("uuid-deploy", "uuid-merge", "uuid-c1", "uuid-c2");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testCollectActiveSubgraphNodesForDagPostExecutionRollback_LinearChain() {
    // Reversed linear chain: deploy depends on cs1 (cs1 is reversed root).
    Map<String, List<String>> reversedGraph = new LinkedHashMap<>();
    reversedGraph.put("uuid-deploy", Collections.singletonList("uuid-cs1"));
    reversedGraph.put("uuid-cs1", Collections.emptyList());

    Set<String> activeNodes =
        DependencyUtils.collectActiveSubgraphNodesForDagPostExecutionRollback(reversedGraph, List.of("uuid-deploy"));

    assertThat(activeNodes).containsExactlyInAnyOrder("uuid-deploy", "uuid-cs1");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testCollectActiveSubgraphNodesForDagPostExecutionRollback_ParallelBranchExcludesSibling() {
    Map<String, List<String>> reversedGraph = new LinkedHashMap<>();
    reversedGraph.put("uuid-S4", Collections.emptyList());
    reversedGraph.put("uuid-S3", Collections.singletonList("uuid-S4"));
    reversedGraph.put("uuid-S1", Collections.singletonList("uuid-S3"));
    reversedGraph.put("uuid-S2", Collections.singletonList("uuid-S3"));

    Set<String> activeNodes =
        DependencyUtils.collectActiveSubgraphNodesForDagPostExecutionRollback(reversedGraph, List.of("uuid-S2"));

    assertThat(activeNodes).containsExactlyInAnyOrder("uuid-S2", "uuid-S3", "uuid-S4");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testPruneLayoutNodeMapForSubgraph_ExcludesStageSiblingViaNextIds() {
    // Forward DAG layout: deploy2 --nextIds--> deploy --nextIds--> s2. Focused rollback on deploy2 must not pull
    // sibling deploy or upstream s2 when includeNextIds=false.
    Map<String, GraphLayoutNode> layoutNodeMap = new LinkedHashMap<>();
    layoutNodeMap.put("deploy2",
        GraphLayoutNode.newBuilder()
            .setNodeGroup("STAGE")
            .setEdgeLayoutList(
                EdgeLayoutList.newBuilder().addCurrentNodeChildren("deploy2Steps").addNextIds("deploy").build())
            .build());
    layoutNodeMap.put("deploy2Steps", GraphLayoutNode.newBuilder().setNodeGroup("STEP").build());
    layoutNodeMap.put("deploy",
        GraphLayoutNode.newBuilder()
            .setNodeGroup("STAGE")
            .setEdgeLayoutList(EdgeLayoutList.newBuilder().addNextIds("s2").build())
            .build());
    layoutNodeMap.put("s2", GraphLayoutNode.newBuilder().setNodeGroup("STAGE").build());

    Map<String, GraphLayoutNode> pruned =
        DependencyUtils.pruneLayoutNodeMapForSubgraph(layoutNodeMap, Collections.singletonList("deploy2"), false);

    assertThat(pruned.keySet()).containsExactlyInAnyOrder("deploy2", "deploy2Steps");
    assertThat(pruned.get("deploy2").getEdgeLayoutList().getNextIdsList()).isEmpty();
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testCollectLayoutSubgraphNodes_TraversesChildrenAndNextIds() {
    Map<String, GraphLayoutNode> layoutNodeMap = new LinkedHashMap<>();
    layoutNodeMap.put("deploy",
        GraphLayoutNode.newBuilder()
            .setEdgeLayoutList(EdgeLayoutList.newBuilder().addCurrentNodeChildren("combinedRollback").build())
            .build());
    layoutNodeMap.put("combinedRollback",
        GraphLayoutNode.newBuilder()
            .setEdgeLayoutList(
                EdgeLayoutList.newBuilder().addCurrentNodeChildren("rollbackSteps").addNextIds("strategyDummy").build())
            .build());
    layoutNodeMap.put("rollbackSteps", GraphLayoutNode.newBuilder().build());
    layoutNodeMap.put("strategyDummy", GraphLayoutNode.newBuilder().build());
    layoutNodeMap.put("unrelatedStage", GraphLayoutNode.newBuilder().build());

    Set<String> subgraphNodes =
        DependencyUtils.collectLayoutSubgraphNodes(layoutNodeMap, Collections.singletonList("deploy"));

    assertThat(subgraphNodes).containsExactlyInAnyOrder("deploy", "combinedRollback", "rollbackSteps", "strategyDummy");
    assertThat(subgraphNodes).doesNotContain("unrelatedStage");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testCollectLayoutSubgraphNodes_ReturnsEmptyWhenRootMissing() {
    Map<String, GraphLayoutNode> layoutNodeMap = Map.of("deploy", GraphLayoutNode.newBuilder().build());

    assertThat(DependencyUtils.collectLayoutSubgraphNodes(layoutNodeMap, Collections.singletonList("missing")))
        .isEmpty();
  }
}
