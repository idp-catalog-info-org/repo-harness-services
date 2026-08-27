/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.strategy;

import static io.harness.idp.catalog.utils.Constants.CHILD_OF;
import static io.harness.idp.catalog.utils.Constants.GROUP_KIND;
import static io.harness.rule.OwnerRule.VIGNESWARA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.catalog.beans.ReferenceType;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.InlineCatalogEntity;
import io.harness.idp.catalog.utils.CatalogUtils;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.TeamHierarchyNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class BottomUpTeamHierarchyAclStrategyTest extends CategoryTest {
  private static final String ACCOUNT_ID = "testAccount";
  private static final String ORG_ID = "org1";
  private static final String ORG_UNIQUE_ID = "testAccount/org1";

  private BottomUpTeamHierarchyAclStrategy strategy;

  @Before
  public void setUp() {
    strategy = new BottomUpTeamHierarchyAclStrategy();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testAssembleTreeAllPermitted() {
    CatalogEntity root = buildTeamEntity("root", null, Collections.emptyMap());
    CatalogEntity child = buildTeamEntity("child", ORG_ID, Map.of(CHILD_OF, Set.of(entityRef(root))));
    CatalogEntity grandchild = buildTeamEntity("grandchild", ORG_ID, Map.of(CHILD_OF, Set.of(entityRef(child))));

    Map<String, List<CatalogEntity>> childrenByParentRef = new HashMap<>();
    childrenByParentRef.put(entityRef(root), List.of(child));
    childrenByParentRef.put(entityRef(child), List.of(grandchild));

    Set<String> permittedRefs = Set.of(entityRef(root), entityRef(child), entityRef(grandchild));

    List<TeamHierarchyNode> nodes =
        strategy.assembleTree(List.of(root), childrenByParentRef, permittedRefs, this::buildNode);

    assertThat(nodes).hasSize(1);
    TeamHierarchyNode rootNode = nodes.get(0);
    assertThat(rootNode.getIdentifier()).isEqualTo("root");
    assertThat(rootNode.getChildren()).hasSize(1);
    TeamHierarchyNode childNode = rootNode.getChildren().get(0);
    assertThat(childNode.getIdentifier()).isEqualTo("child");
    assertThat(childNode.getChildren()).hasSize(1);
    TeamHierarchyNode grandchildNode = childNode.getChildren().get(0);
    assertThat(grandchildNode.getIdentifier()).isEqualTo("grandchild");
    assertThat(grandchildNode.getChildren()).isEmpty();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testAssembleTreeMiddleNodeNotPermittedButDescendantPermittedKeepsPath() {
    CatalogEntity root = buildTeamEntity("root", null, Collections.emptyMap());
    CatalogEntity child = buildTeamEntity("child", ORG_ID, Map.of(CHILD_OF, Set.of(entityRef(root))));
    CatalogEntity grandchild = buildTeamEntity("grandchild", ORG_ID, Map.of(CHILD_OF, Set.of(entityRef(child))));

    Map<String, List<CatalogEntity>> childrenByParentRef = new HashMap<>();
    childrenByParentRef.put(entityRef(root), List.of(child));
    childrenByParentRef.put(entityRef(child), List.of(grandchild));

    Set<String> permittedRefs = Set.of(entityRef(root), entityRef(grandchild));

    List<TeamHierarchyNode> nodes =
        strategy.assembleTree(List.of(root), childrenByParentRef, permittedRefs, this::buildNode);

    assertThat(nodes).hasSize(1);
    TeamHierarchyNode rootNode = nodes.get(0);
    assertThat(rootNode.getIdentifier()).isEqualTo("root");
    assertThat(rootNode.getChildren()).hasSize(1);
    TeamHierarchyNode childNode = rootNode.getChildren().get(0);
    assertThat(childNode.getIdentifier()).isEqualTo("child");
    assertThat(childNode.getChildren()).hasSize(1);
    TeamHierarchyNode grandchildNode = childNode.getChildren().get(0);
    assertThat(grandchildNode.getIdentifier()).isEqualTo("grandchild");
    assertThat(grandchildNode.getChildren()).isEmpty();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testAssembleTreeNodeNotPermittedWithNoPermittedDescendantDropped() {
    CatalogEntity root = buildTeamEntity("root", null, Collections.emptyMap());
    CatalogEntity child = buildTeamEntity("child", ORG_ID, Map.of(CHILD_OF, Set.of(entityRef(root))));
    CatalogEntity grandchild = buildTeamEntity("grandchild", ORG_ID, Map.of(CHILD_OF, Set.of(entityRef(child))));

    Map<String, List<CatalogEntity>> childrenByParentRef = new HashMap<>();
    childrenByParentRef.put(entityRef(root), List.of(child));
    childrenByParentRef.put(entityRef(child), List.of(grandchild));

    Set<String> permittedRefs = Set.of(entityRef(root));

    List<TeamHierarchyNode> nodes =
        strategy.assembleTree(List.of(root), childrenByParentRef, permittedRefs, this::buildNode);

    assertThat(nodes).hasSize(1);
    TeamHierarchyNode rootNode = nodes.get(0);
    assertThat(rootNode.getIdentifier()).isEqualTo("root");
    assertThat(rootNode.getChildren()).isEmpty();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testAssembleTreeRootPermittedChildPermittedGrandchildNotPermitted() {
    CatalogEntity root = buildTeamEntity("root", null, Collections.emptyMap());
    CatalogEntity child = buildTeamEntity("child", ORG_ID, Map.of(CHILD_OF, Set.of(entityRef(root))));
    CatalogEntity grandchild = buildTeamEntity("grandchild", ORG_ID, Map.of(CHILD_OF, Set.of(entityRef(child))));

    Map<String, List<CatalogEntity>> childrenByParentRef = new HashMap<>();
    childrenByParentRef.put(entityRef(root), List.of(child));
    childrenByParentRef.put(entityRef(child), List.of(grandchild));

    Set<String> permittedRefs = Set.of(entityRef(root), entityRef(child));

    List<TeamHierarchyNode> nodes =
        strategy.assembleTree(List.of(root), childrenByParentRef, permittedRefs, this::buildNode);

    assertThat(nodes).hasSize(1);
    TeamHierarchyNode rootNode = nodes.get(0);
    assertThat(rootNode.getIdentifier()).isEqualTo("root");
    assertThat(rootNode.getChildren()).hasSize(1);
    TeamHierarchyNode childNode = rootNode.getChildren().get(0);
    assertThat(childNode.getIdentifier()).isEqualTo("child");
    assertThat(childNode.getChildren()).isEmpty();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testAssembleTreeCycleDetectionTerminates() {
    CatalogEntity teamA = buildTeamEntity("teamA", null, Map.of(CHILD_OF, Set.of("group:account/teamB")));
    CatalogEntity teamB = buildTeamEntity("teamB", null, Map.of(CHILD_OF, Set.of("group:account/teamA")));

    Map<String, List<CatalogEntity>> childrenByParentRef = new HashMap<>();
    childrenByParentRef.put(entityRef(teamA), List.of(teamB));
    childrenByParentRef.put(entityRef(teamB), List.of(teamA));

    Set<String> permittedRefs = Set.of(entityRef(teamA), entityRef(teamB));

    List<TeamHierarchyNode> nodes =
        strategy.assembleTree(List.of(teamA), childrenByParentRef, permittedRefs, this::buildNode);

    assertThat(nodes).hasSize(1);
    TeamHierarchyNode rootNode = nodes.get(0);
    assertThat(rootNode.getIdentifier()).isEqualTo("teamA");
    assertThat(rootNode.getChildren()).hasSize(1);
    TeamHierarchyNode childNode = rootNode.getChildren().get(0);
    assertThat(childNode.getIdentifier()).isEqualTo("teamB");
    assertThat(childNode.getChildren()).isEmpty();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testAssembleTreeDepthExceeds25WithPermittedNodeAtDepthTruncated() {
    int chainLength = 30;
    List<CatalogEntity> chain = buildTeamChain(chainLength);
    CatalogEntity root = chain.get(0);

    Map<String, List<CatalogEntity>> childrenByParentRef = new HashMap<>();
    for (int i = 0; i < chainLength - 1; i++) {
      childrenByParentRef.put(entityRef(chain.get(i)), List.of(chain.get(i + 1)));
    }

    Set<String> permittedRefs = new HashSet<>();
    for (CatalogEntity entity : chain) {
      permittedRefs.add(entityRef(entity));
    }

    List<TeamHierarchyNode> nodes =
        strategy.assembleTree(List.of(root), childrenByParentRef, permittedRefs, this::buildNode);

    assertThat(nodes).hasSize(1);
    TeamHierarchyNode node = nodes.get(0);
    int depthReached = 0;
    while (!node.getChildren().isEmpty()) {
      assertThat(node.getChildren()).hasSize(1);
      node = node.getChildren().get(0);
      depthReached++;
    }
    assertThat(depthReached).isEqualTo(25);
    assertThat(node.getIdentifier()).isEqualTo("team25");
    assertThat(node.getChildren()).isEmpty();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testAssembleTreeDepthExceeds25WithUnpermittedNodeAtDepthDropped() {
    int chainLength = 30;
    List<CatalogEntity> chain = buildTeamChain(chainLength);
    CatalogEntity root = chain.get(0);

    Map<String, List<CatalogEntity>> childrenByParentRef = new HashMap<>();
    for (int i = 0; i < chainLength - 1; i++) {
      childrenByParentRef.put(entityRef(chain.get(i)), List.of(chain.get(i + 1)));
    }

    Set<String> permittedRefs = new HashSet<>();
    for (int i = 0; i < 25; i++) {
      permittedRefs.add(entityRef(chain.get(i)));
    }

    List<TeamHierarchyNode> nodes =
        strategy.assembleTree(List.of(root), childrenByParentRef, permittedRefs, this::buildNode);

    assertThat(nodes).hasSize(1);
    TeamHierarchyNode node = nodes.get(0);
    int depthReached = 0;
    while (!node.getChildren().isEmpty()) {
      assertThat(node.getChildren()).hasSize(1);
      node = node.getChildren().get(0);
      depthReached++;
    }
    assertThat(depthReached).isEqualTo(24);
    assertThat(node.getIdentifier()).isEqualTo("team24");
    assertThat(node.getChildren()).isEmpty();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testAssembleTreeMultipleRoots() {
    CatalogEntity root1 = buildTeamEntity("root1", null, Collections.emptyMap());
    CatalogEntity child1 = buildTeamEntity("child1", ORG_ID, Map.of(CHILD_OF, Set.of(entityRef(root1))));
    CatalogEntity root2 = buildTeamEntity("root2", null, Collections.emptyMap());

    Map<String, List<CatalogEntity>> childrenByParentRef = new HashMap<>();
    childrenByParentRef.put(entityRef(root1), List.of(child1));

    Set<String> permittedRefs = Set.of(entityRef(root1), entityRef(child1), entityRef(root2));

    List<TeamHierarchyNode> nodes =
        strategy.assembleTree(List.of(root1, root2), childrenByParentRef, permittedRefs, this::buildNode);

    assertThat(nodes).hasSize(2);
    TeamHierarchyNode rootNode1 = nodes.get(0);
    assertThat(rootNode1.getIdentifier()).isEqualTo("root1");
    assertThat(rootNode1.getChildren()).hasSize(1);
    TeamHierarchyNode rootNode2 = nodes.get(1);
    assertThat(rootNode2.getIdentifier()).isEqualTo("root2");
    assertThat(rootNode2.getChildren()).isEmpty();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testAssembleTreeMultipleUnpermittedIntermediateNodesWithPermittedLeaf() {
    CatalogEntity root = buildTeamEntity("root", null, Collections.emptyMap());
    CatalogEntity child1 = buildTeamEntity("child1", ORG_ID, Map.of(CHILD_OF, Set.of(entityRef(root))));
    CatalogEntity child2 = buildTeamEntity("child2", ORG_ID, Map.of(CHILD_OF, Set.of(entityRef(child1))));
    CatalogEntity leaf = buildTeamEntity("leaf", ORG_ID, Map.of(CHILD_OF, Set.of(entityRef(child2))));

    Map<String, List<CatalogEntity>> childrenByParentRef = new HashMap<>();
    childrenByParentRef.put(entityRef(root), List.of(child1));
    childrenByParentRef.put(entityRef(child1), List.of(child2));
    childrenByParentRef.put(entityRef(child2), List.of(leaf));

    Set<String> permittedRefs = Set.of(entityRef(root), entityRef(leaf));

    List<TeamHierarchyNode> nodes =
        strategy.assembleTree(List.of(root), childrenByParentRef, permittedRefs, this::buildNode);

    assertThat(nodes).hasSize(1);
    TeamHierarchyNode rootNode = nodes.get(0);
    assertThat(rootNode.getIdentifier()).isEqualTo("root");
    assertThat(rootNode.getChildren()).hasSize(1);
    TeamHierarchyNode child1Node = rootNode.getChildren().get(0);
    assertThat(child1Node.getIdentifier()).isEqualTo("child1");
    assertThat(child1Node.getChildren()).hasSize(1);
    TeamHierarchyNode child2Node = child1Node.getChildren().get(0);
    assertThat(child2Node.getIdentifier()).isEqualTo("child2");
    assertThat(child2Node.getChildren()).hasSize(1);
    TeamHierarchyNode leafNode = child2Node.getChildren().get(0);
    assertThat(leafNode.getIdentifier()).isEqualTo("leaf");
    assertThat(leafNode.getChildren()).isEmpty();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testVisibleRootRefsRootPermittedIncluded() {
    CatalogEntity root = buildTeamEntity("root", null, Collections.emptyMap());
    CatalogEntity child = buildTeamEntity("child", ORG_ID, Map.of(CHILD_OF, Set.of(entityRef(root))));

    Map<String, List<CatalogEntity>> childrenByParentRef = new HashMap<>();
    childrenByParentRef.put(entityRef(root), List.of(child));

    Set<String> permittedRefs = Set.of(entityRef(root), entityRef(child));

    Set<String> visibleRootRefs = strategy.visibleRootRefs(List.of(root), childrenByParentRef, permittedRefs);

    assertThat(visibleRootRefs).containsExactly(entityRef(root));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testVisibleRootRefsRootNotPermittedButDescendantPermittedIncluded() {
    CatalogEntity root = buildTeamEntity("root", null, Collections.emptyMap());
    CatalogEntity child = buildTeamEntity("child", ORG_ID, Map.of(CHILD_OF, Set.of(entityRef(root))));

    Map<String, List<CatalogEntity>> childrenByParentRef = new HashMap<>();
    childrenByParentRef.put(entityRef(root), List.of(child));

    Set<String> permittedRefs = Set.of(entityRef(child));

    Set<String> visibleRootRefs = strategy.visibleRootRefs(List.of(root), childrenByParentRef, permittedRefs);

    assertThat(visibleRootRefs).containsExactly(entityRef(root));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testVisibleRootRefsRootNotPermittedAndNoDescendantPermittedExcluded() {
    CatalogEntity root = buildTeamEntity("root", null, Collections.emptyMap());
    CatalogEntity child = buildTeamEntity("child", ORG_ID, Map.of(CHILD_OF, Set.of(entityRef(root))));

    Map<String, List<CatalogEntity>> childrenByParentRef = new HashMap<>();
    childrenByParentRef.put(entityRef(root), List.of(child));

    Set<String> permittedRefs = Collections.emptySet();

    Set<String> visibleRootRefs = strategy.visibleRootRefs(List.of(root), childrenByParentRef, permittedRefs);

    assertThat(visibleRootRefs).isEmpty();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testVisibleRootRefsCycleInSubtreeWithNoPermittedNodeExcluded() {
    CatalogEntity teamA = buildTeamEntity("teamA", null, Map.of(CHILD_OF, Set.of("group:account/teamB")));
    CatalogEntity teamB = buildTeamEntity("teamB", null, Map.of(CHILD_OF, Set.of("group:account/teamA")));

    Map<String, List<CatalogEntity>> childrenByParentRef = new HashMap<>();
    childrenByParentRef.put(entityRef(teamA), List.of(teamB));
    childrenByParentRef.put(entityRef(teamB), List.of(teamA));

    Set<String> permittedRefs = Collections.emptySet();

    Set<String> visibleRootRefs = strategy.visibleRootRefs(List.of(teamA), childrenByParentRef, permittedRefs);

    assertThat(visibleRootRefs).isEmpty();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testVisibleRootRefsDeepChainWithPermittedNodeBeyondDepth25Excluded() {
    int chainLength = 30;
    List<CatalogEntity> chain = buildTeamChain(chainLength);
    CatalogEntity root = chain.get(0);

    Map<String, List<CatalogEntity>> childrenByParentRef = new HashMap<>();
    for (int i = 0; i < chainLength - 1; i++) {
      childrenByParentRef.put(entityRef(chain.get(i)), List.of(chain.get(i + 1)));
    }

    Set<String> permittedRefs = Set.of(entityRef(chain.get(29)));

    Set<String> visibleRootRefs = strategy.visibleRootRefs(List.of(root), childrenByParentRef, permittedRefs);

    assertThat(visibleRootRefs).isEmpty();
  }

  private List<CatalogEntity> buildTeamChain(int chainLength) {
    List<CatalogEntity> chain = new ArrayList<>();
    for (int i = 0; i < chainLength; i++) {
      String identifier = "team" + i;
      Map<String, Set<String>> relations = new HashMap<>();
      if (i > 0) {
        relations.put(CHILD_OF, Set.of("group:account/team" + (i - 1)));
      }
      chain.add(buildTeamEntity(identifier, null, relations));
    }
    return chain;
  }

  private CatalogEntity buildTeamEntity(String identifier, String orgId, Map<String, Set<String>> relations) {
    String parentUniqueId = orgId != null ? ORG_UNIQUE_ID : ACCOUNT_ID;
    return InlineCatalogEntity.builder()
        .accountIdentifier(ACCOUNT_ID)
        .parentUniqueId(parentUniqueId)
        .kind(GROUP_KIND)
        .identifier(identifier)
        .name(identifier)
        .orgIdentifier(orgId)
        .referenceType(ReferenceType.INLINE)
        .relations(relations)
        .spec(new HashMap<>())
        .metadata(new HashMap<>())
        .tags(List.of())
        .build();
  }

  private String entityRef(CatalogEntity entity) {
    return CatalogUtils.entityRef(entity);
  }

  private TeamHierarchyNode buildNode(CatalogEntity entity, List<TeamHierarchyNode> children) {
    TeamHierarchyNode node = new TeamHierarchyNode();
    node.setIdentifier(entity.getIdentifier());
    node.setKindIdentifier(entity.getKind());
    node.setName(entity.getName());
    node.setChildren(children);
    return node;
  }
}
