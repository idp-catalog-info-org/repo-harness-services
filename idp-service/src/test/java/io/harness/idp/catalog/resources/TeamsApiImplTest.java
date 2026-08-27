/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.resources;

import static io.harness.rule.OwnerRule.VIGNESWARA;
import static io.harness.utils.ApiUtils.X_PAGE_NUMBER;
import static io.harness.utils.ApiUtils.X_PAGE_SIZE;
import static io.harness.utils.ApiUtils.X_TOTAL_ELEMENTS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.catalog.beans.TeamHierarchyResult;
import io.harness.idp.catalog.service.TeamHierarchyService;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.TeamHierarchyNode;

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
public class TeamsApiImplTest extends CategoryTest {
  static final String TEST_ACCOUNT_ID = "testAccount";
  static final String TEST_SCOPES = "account";

  AutoCloseable openMocks;

  @Mock TeamHierarchyService teamHierarchyService;
  @InjectMocks TeamsApiImpl teamsApi;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetTeamHierarchyReturnsOkResponseWithHeaders() {
    TeamHierarchyNode node = new TeamHierarchyNode();
    node.setIdentifier("team1");
    TeamHierarchyResult result =
        TeamHierarchyResult.builder().nodes(List.of(node)).pageNumber(0).pageSize(10).totalElements(1).build();

    when(teamHierarchyService.getTeamHierarchy(TEST_ACCOUNT_ID, TEST_SCOPES, true, 0, 10, "name,asc", "search", true))
        .thenReturn(result);

    Response response =
        teamsApi.getTeamHierarchy(TEST_ACCOUNT_ID, TEST_SCOPES, true, true, 0, 10, "name,asc", "search");

    verify(teamHierarchyService)
        .getTeamHierarchy(TEST_ACCOUNT_ID, TEST_SCOPES, true, 0, 10, "name,asc", "search", true);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(response.getEntity()).isEqualTo(result.getNodes());
    assertThat(Long.valueOf(response.getHeaderString(X_TOTAL_ELEMENTS))).isEqualTo(1);
    assertThat(Long.valueOf(response.getHeaderString(X_PAGE_NUMBER))).isZero();
    assertThat(Long.valueOf(response.getHeaderString(X_PAGE_SIZE))).isEqualTo(10);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetTeamHierarchyPropagatesServiceException() {
    when(teamHierarchyService.getTeamHierarchy(
             eq(TEST_ACCOUNT_ID), eq(TEST_SCOPES), eq(false), eq(null), eq(null), eq(null), eq(null), eq(true)))
        .thenThrow(new RuntimeException("boom"));

    assertThatThrownBy(
        () -> teamsApi.getTeamHierarchy(TEST_ACCOUNT_ID, TEST_SCOPES, false, true, null, null, null, null))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("boom");
  }
}
