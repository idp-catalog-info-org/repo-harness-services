/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.catalog.graph.resources;

import static io.harness.rule.OwnerRule.DEVESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.EntityNotFoundException;
import io.harness.idp.catalog.graph.service.GraphTraversalService;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.GraphTraversalResponse;

import java.util.List;
import javax.ws.rs.core.Response;
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
public class GraphApiImplTest extends CategoryTest {
  static final String TEST_ACCOUNT_ID = "test-account-id";
  static final String TEST_ENTITY_REF = "system:account/payment-platform";

  AutoCloseable openMocks;

  @Mock GraphTraversalService graphTraversalService;
  @InjectMocks GraphApiImpl graphApi;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testTraverseEntityGraphReturnsOkResponse() {
    List<String> relationshipTypes = List.of("hasPart", "providesApis");
    List<String> kinds = List.of("component", "api");
    GraphTraversalResponse graphTraversalResponse = new GraphTraversalResponse();

    when(graphTraversalService.traverse(TEST_ACCOUNT_ID, TEST_ENTITY_REF, relationshipTypes, kinds, 2))
        .thenReturn(graphTraversalResponse);

    Response response = graphApi.traverseEntityGraph(TEST_ENTITY_REF, TEST_ACCOUNT_ID, relationshipTypes, kinds, 2);

    verify(graphTraversalService).traverse(TEST_ACCOUNT_ID, TEST_ENTITY_REF, relationshipTypes, kinds, 2);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(response.getEntity()).isEqualTo(graphTraversalResponse);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testTraverseEntityGraphUsesDefaultFiltersAndDepth() {
    GraphTraversalResponse graphTraversalResponse = new GraphTraversalResponse();

    when(graphTraversalService.traverse(TEST_ACCOUNT_ID, TEST_ENTITY_REF, List.of(), List.of(), 1))
        .thenReturn(graphTraversalResponse);

    Response response = graphApi.traverseEntityGraph(TEST_ENTITY_REF, TEST_ACCOUNT_ID, null, null, null);

    verify(graphTraversalService).traverse(TEST_ACCOUNT_ID, TEST_ENTITY_REF, List.of(), List.of(), 1);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(response.getEntity()).isEqualTo(graphTraversalResponse);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testTraverseEntityGraphReturnsNotFoundForMissingRootEntity() {
    when(graphTraversalService.traverse(TEST_ACCOUNT_ID, TEST_ENTITY_REF, List.of(), List.of(), 1))
        .thenThrow(new EntityNotFoundException("Root entity not found: " + TEST_ENTITY_REF));

    Response response = graphApi.traverseEntityGraph(TEST_ENTITY_REF, TEST_ACCOUNT_ID, null, null, null);

    verify(graphTraversalService).traverse(TEST_ACCOUNT_ID, TEST_ENTITY_REF, List.of(), List.of(), 1);
    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
    assertThat(response.getEntity()).isEqualTo("Root entity not found: " + TEST_ENTITY_REF);
  }
}
