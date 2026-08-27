/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.catalog.graph.service;

import static io.harness.rule.OwnerRule.ANKUR;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.catalog.graph.beans.CatalogGraphEntity;
import io.harness.idp.catalog.graph.beans.GraphEdge;
import io.harness.idp.catalog.graph.beans.GraphMetadata;
import io.harness.idp.catalog.graph.beans.GraphNode;
import io.harness.idp.catalog.graph.strategy.GraphTraversalStrategy;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.GraphTraversalResponse;

import java.util.List;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class GraphTraversalServiceTest extends CategoryTest {
  static final String TEST_ACCOUNT_ID = "test-account-id";
  static final String TEST_ENTITY_REF = "system:account/payment-platform";

  AutoCloseable openMocks;

  @Mock GraphTraversalStrategy strategy;
  RelationsMaster relationsMaster = new RelationsMaster();
  GraphTraversalService graphTraversalService;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
    graphTraversalService = new GraphTraversalService(strategy, relationsMaster);
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testTraverseDelegatesToStrategyAndMapsGraphResponse() {
    CatalogGraphEntity catalogGraphEntity = CatalogGraphEntity.builder()
                                                .nodes(List.of(GraphNode.builder()
                                                                   .entityRef("system:account/payment-platform")
                                                                   .kind("system")
                                                                   .name("payment-platform")
                                                                   .type("domain")
                                                                   .build(),
                                                    GraphNode.builder()
                                                        .entityRef("component:account/payment-service")
                                                        .kind("component")
                                                        .name("payment-service")
                                                        .type("service")
                                                        .build()))
                                                .edges(List.of(GraphEdge.builder()
                                                                   .sourceEntityRef("system:account/payment-platform")
                                                                   .targetEntityRef("component:account/payment-service")
                                                                   .relationType("hasPart")
                                                                   .depth(1)
                                                                   .build()))
                                                .metadata(GraphMetadata.builder()
                                                              .baseEntityRef("system:account/payment-platform")
                                                              .maxDepthReached(1)
                                                              .totalEdges(1)
                                                              .build())
                                                .build();

    when(strategy.traverse(TEST_ACCOUNT_ID, TEST_ENTITY_REF, List.of("hasPart"), List.of("component"), 1))
        .thenReturn(catalogGraphEntity);

    GraphTraversalResponse response =
        graphTraversalService.traverse(TEST_ACCOUNT_ID, TEST_ENTITY_REF, List.of("hasPart"), List.of("component"), 1);

    verify(strategy).traverse(TEST_ACCOUNT_ID, TEST_ENTITY_REF, List.of("hasPart"), List.of("component"), 1);
    assertThat(response).isNotNull();
    assertThat(response.getNodes()).hasSize(2);
    assertThat(response.getNodes().get(0).getEntityRef()).isEqualTo("system:account/payment-platform");
    assertThat(response.getNodes().get(0).getKind()).isEqualTo("system");
    assertThat(response.getNodes().get(0).getName()).isEqualTo("payment-platform");
    assertThat(response.getNodes().get(0).getType()).isEqualTo("domain");
    assertThat(response.getNodes().get(1).getEntityRef()).isEqualTo("component:account/payment-service");
    assertThat(response.getEdges()).hasSize(1);
    assertThat(response.getEdges().get(0).getSource()).isEqualTo("system:account/payment-platform");
    assertThat(response.getEdges().get(0).getTarget()).isEqualTo("component:account/payment-service");
    assertThat(response.getEdges().get(0).getRelation()).isEqualTo("hasPart");
    assertThat(response.getEdges().get(0).getReverseRelation()).isEqualTo("partOf");
    assertThat(response.getEdges().get(0).getDepth()).isEqualTo(1);
    assertThat(response.getMetadata()).isNotNull();
    assertThat(response.getMetadata().getBaseEntityRef()).isEqualTo("system:account/payment-platform");
    assertThat(response.getMetadata().getMaxDepthReached()).isEqualTo(1);
    assertThat(response.getMetadata().getTotalEdges()).isEqualTo(1);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testTraverseHandlesNullCollectionsAndMetadata() {
    CatalogGraphEntity catalogGraphEntity = CatalogGraphEntity.builder().nodes(null).edges(null).metadata(null).build();

    when(strategy.traverse(TEST_ACCOUNT_ID, TEST_ENTITY_REF, List.of(), List.of(), 2)).thenReturn(catalogGraphEntity);

    GraphTraversalResponse response =
        graphTraversalService.traverse(TEST_ACCOUNT_ID, TEST_ENTITY_REF, List.of(), List.of(), 2);

    verify(strategy).traverse(TEST_ACCOUNT_ID, TEST_ENTITY_REF, List.of(), List.of(), 2);
    assertThat(response).isNotNull();
    assertThat(response.getNodes()).isEmpty();
    assertThat(response.getEdges()).isEmpty();
    assertThat(response.getMetadata()).isNull();
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testTraverseMapsReverseRelationForKnownAndUnknownRelations() {
    CatalogGraphEntity catalogGraphEntity = CatalogGraphEntity.builder()
                                                .nodes(List.of(GraphNode.builder()
                                                                   .entityRef("component:account/service-a")
                                                                   .kind("component")
                                                                   .name("service-a")
                                                                   .type("service")
                                                                   .build(),
                                                    GraphNode.builder()
                                                        .entityRef("component:account/service-b")
                                                        .kind("component")
                                                        .name("service-b")
                                                        .type("service")
                                                        .build(),
                                                    GraphNode.builder()
                                                        .entityRef("group:account/team-a")
                                                        .kind("group")
                                                        .name("team-a")
                                                        .type("team")
                                                        .build()))
                                                .edges(List.of(GraphEdge.builder()
                                                                   .sourceEntityRef("component:account/service-a")
                                                                   .targetEntityRef("component:account/service-b")
                                                                   .relationType("dependsOn")
                                                                   .depth(1)
                                                                   .build(),
                                                    GraphEdge.builder()
                                                        .sourceEntityRef("component:account/service-a")
                                                        .targetEntityRef("group:account/team-a")
                                                        .relationType("ownedBy")
                                                        .depth(1)
                                                        .build(),
                                                    GraphEdge.builder()
                                                        .sourceEntityRef("component:account/service-a")
                                                        .targetEntityRef("component:account/service-b")
                                                        .relationType("customRelation")
                                                        .depth(1)
                                                        .build()))
                                                .metadata(GraphMetadata.builder()
                                                              .baseEntityRef("component:account/service-a")
                                                              .maxDepthReached(1)
                                                              .totalEdges(3)
                                                              .build())
                                                .build();

    when(strategy.traverse(TEST_ACCOUNT_ID, TEST_ENTITY_REF, List.of(), List.of(), 1)).thenReturn(catalogGraphEntity);

    GraphTraversalResponse response =
        graphTraversalService.traverse(TEST_ACCOUNT_ID, TEST_ENTITY_REF, List.of(), List.of(), 1);

    assertThat(response.getEdges()).hasSize(3);
    assertThat(response.getEdges().get(0).getRelation()).isEqualTo("dependsOn");
    assertThat(response.getEdges().get(0).getReverseRelation()).isEqualTo("dependencyOf");
    assertThat(response.getEdges().get(1).getRelation()).isEqualTo("ownedBy");
    assertThat(response.getEdges().get(1).getReverseRelation()).isEqualTo("ownerOf");
    assertThat(response.getEdges().get(2).getRelation()).isEqualTo("customRelation");
    assertThat(response.getEdges().get(2).getReverseRelation()).isNull();
  }
}
