/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.gitops.resource;

import static io.harness.rule.OwnerRule.ACASIAN;
import static io.harness.rule.OwnerRule.HIMANSHU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.cdng.gitops.beans.ClusterBatchRequest;
import io.harness.cdng.gitops.beans.ClusterLinkRequest;
import io.harness.cdng.gitops.beans.ClusterResponse;
import io.harness.cdng.gitops.entity.Cluster;
import io.harness.cdng.gitops.service.ClusterService;
import io.harness.cdng.service.steps.helpers.serviceoverridesv2.validators.EnvironmentValidationHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.gitops.models.ClusterQuery;
import io.harness.gitops.remote.GitopsResourceClient;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.utils.OrgAndProjectValidationHelper;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.GITOPS)
@RunWith(MockitoJUnitRunner.class)
public class ClusterResourceTest extends CategoryTest {
  @Mock private GitopsResourceClient gitopsResourceClient;
  @Mock private OrgAndProjectValidationHelper orgAndProjectValidationHelper;
  @Mock private EnvironmentValidationHelper environmentValidationHelper;
  @Mock private ClusterService clusterService;
  @Mock private AccessControlClient accessControlClient;
  @Mock private io.harness.ng.core.services.ScopeInfoService scopeInfoService;
  @Mock private Call<PageResponse<io.harness.gitops.models.Cluster>> accClusterListCall;
  @Mock private Call<PageResponse<io.harness.gitops.models.Cluster>> orgClusterListCall;
  @Mock private Call<PageResponse<io.harness.gitops.models.Cluster>> projectClusterListCall;

  @InjectMocks private ClusterResource clusterResource;

  private static final String ACCOUNT_ID = "account1";
  private static final String ORG_ID = "org1";
  private static final String PROJECT_ID = "proj1";
  private static final String ENV_ID = "env1";
  private static final String AGENT_1_ID = "agent1";
  private static final String AGENT_2_ID = "agent2";
  private static final String CLUSTER_REF = "incluster";
  private static final int PAGE_INDEX = 1;
  private static final int PAGE_SIZE = 10;
  private static final int DEFAULT_PAGE_SIZE = 1000;

  private static final ClusterQuery accLevelClustersListQuery = ClusterQuery.builder()
                                                                    .accountId(ACCOUNT_ID)
                                                                    .orgIdentifier("")
                                                                    .projectIdentifier("")
                                                                    .searchTerm("")
                                                                    .pageIndex(0)
                                                                    .pageSize(DEFAULT_PAGE_SIZE)
                                                                    .filter(null)
                                                                    .build();

  private static final ClusterQuery orgLevelClustersListQuery = ClusterQuery.builder()
                                                                    .accountId(ACCOUNT_ID)
                                                                    .orgIdentifier(ORG_ID)
                                                                    .projectIdentifier("")
                                                                    .searchTerm("")
                                                                    .pageIndex(0)
                                                                    .pageSize(DEFAULT_PAGE_SIZE)
                                                                    .filter(null)
                                                                    .build();

  private static final ClusterQuery projectLevelClustersListQuery = ClusterQuery.builder()
                                                                        .accountId(ACCOUNT_ID)
                                                                        .orgIdentifier(ORG_ID)
                                                                        .projectIdentifier(PROJECT_ID)
                                                                        .searchTerm("")
                                                                        .pageIndex(0)
                                                                        .pageSize(DEFAULT_PAGE_SIZE)
                                                                        .filter(null)
                                                                        .build();

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldListLinkedProjectClusters() throws IOException {
    Page<Cluster> linkedClusters = new PageImpl<>(List.of(buildCluster(AGENT_1_ID), buildCluster(AGENT_2_ID)));

    doNothing()
        .when(accessControlClient)
        .checkForAccessOrThrow(any(ResourceScope.class), any(Resource.class), anyString(), anyString());
    when(environmentValidationHelper.checkThatEnvExists(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(Environment.builder().build());
    when(clusterService.list(anyInt(), anyInt(), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV_ID), anyString(),
             anyList(), anyList(), any()))
        .thenReturn(linkedClusters);

    // account
    io.harness.gitops.models.Cluster accCluster = new io.harness.gitops.models.Cluster("incluster", "testCluster");
    Response<PageResponse<io.harness.gitops.models.Cluster>> accClusterResponse =
        Response.success(PageResponse.<io.harness.gitops.models.Cluster>builder().content(List.of(accCluster)).build());

    when(gitopsResourceClient.listClusters(accLevelClustersListQuery)).thenReturn(accClusterListCall);
    when(accClusterListCall.execute()).thenReturn(accClusterResponse);

    // org
    io.harness.gitops.models.Cluster orgCluster = new io.harness.gitops.models.Cluster("incluster", "testCluster");
    Response<PageResponse<io.harness.gitops.models.Cluster>> orgClusterResponse =
        Response.success(PageResponse.<io.harness.gitops.models.Cluster>builder().content(List.of(orgCluster)).build());

    when(gitopsResourceClient.listClusters(orgLevelClustersListQuery)).thenReturn(orgClusterListCall);
    when(orgClusterListCall.execute()).thenReturn(orgClusterResponse);

    // project
    io.harness.gitops.models.Cluster projectCluster1 = new io.harness.gitops.models.Cluster("incluster", "cluster1");
    projectCluster1.setAgentIdentifier(AGENT_1_ID);
    io.harness.gitops.models.Cluster projectCluster2 = new io.harness.gitops.models.Cluster("incluster", "cluster2");
    projectCluster2.setAgentIdentifier(AGENT_2_ID);

    Response<PageResponse<io.harness.gitops.models.Cluster>> projectClusterResponse =
        Response.success(PageResponse.<io.harness.gitops.models.Cluster>builder()
                             .content(List.of(projectCluster1, projectCluster2))
                             .build());

    when(gitopsResourceClient.listClusters(projectLevelClustersListQuery)).thenReturn(projectClusterListCall);
    when(projectClusterListCall.execute()).thenReturn(projectClusterResponse);

    // Execute
    ResponseDTO<PageResponse<ClusterResponse>> result = clusterResource.list(PAGE_INDEX, PAGE_SIZE, ACCOUNT_ID, ORG_ID,
        PROJECT_ID, ENV_ID, "", Collections.emptyList(), Collections.emptyList(), ScopeLevel.PROJECT);

    // Verify
    assertNotNull(result);
    assertThat(result.getData().getContent().size()).isEqualTo(2);
    assertEquals(List.of(buildClusterResponse(AGENT_1_ID, "cluster1"), buildClusterResponse(AGENT_2_ID, "cluster2")),
        result.getData().getContent());
    verify(orgAndProjectValidationHelper).checkThatTheOrganizationAndProjectExists(ORG_ID, PROJECT_ID, ACCOUNT_ID);
    verify(environmentValidationHelper).checkThatEnvExists(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_ID);
  }

  // we have same cluster at acc, org, proj with same agent. We should return only acc level cluster
  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldListLinkedAccClusters() throws IOException {
    Cluster linkedAccCluster = Cluster.builder()
                                   .accountId(ACCOUNT_ID)
                                   .agentIdentifier(AGENT_1_ID)
                                   .clusterRef("account." + CLUSTER_REF)
                                   .build();
    Page<Cluster> linkedClusters = new PageImpl<>(List.of(linkedAccCluster));

    doNothing()
        .when(accessControlClient)
        .checkForAccessOrThrow(any(ResourceScope.class), any(Resource.class), anyString(), anyString());
    when(environmentValidationHelper.checkThatEnvExists(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(Environment.builder().build());
    when(clusterService.list(anyInt(), anyInt(), eq(ACCOUNT_ID), eq(null), eq(null), eq(ENV_ID), anyString(), anyList(),
             anyList(), any()))
        .thenReturn(linkedClusters);

    // account
    io.harness.gitops.models.Cluster accCluster = new io.harness.gitops.models.Cluster("incluster", "accCluster");
    accCluster.setAgentIdentifier(AGENT_1_ID);
    Response<PageResponse<io.harness.gitops.models.Cluster>> accClusterResponse =
        Response.success(PageResponse.<io.harness.gitops.models.Cluster>builder().content(List.of(accCluster)).build());

    when(gitopsResourceClient.listClusters(accLevelClustersListQuery)).thenReturn(accClusterListCall);
    when(accClusterListCall.execute()).thenReturn(accClusterResponse);

    // org
    ClusterQuery organisationLevelClustersListQuery = ClusterQuery.builder()
                                                          .accountId(ACCOUNT_ID)
                                                          .orgIdentifier(null)
                                                          .projectIdentifier("")
                                                          .searchTerm("")
                                                          .pageIndex(0)
                                                          .pageSize(DEFAULT_PAGE_SIZE)
                                                          .filter(null)
                                                          .build();

    io.harness.gitops.models.Cluster orgCluster = new io.harness.gitops.models.Cluster("incluster", "orgCluster");
    orgCluster.setAgentIdentifier(AGENT_1_ID);
    Response<PageResponse<io.harness.gitops.models.Cluster>> orgClusterResponse =
        Response.success(PageResponse.<io.harness.gitops.models.Cluster>builder().content(List.of(orgCluster)).build());

    when(gitopsResourceClient.listClusters(organisationLevelClustersListQuery)).thenReturn(orgClusterListCall);
    when(orgClusterListCall.execute()).thenReturn(orgClusterResponse);

    // project
    ClusterQuery prjLevelClustersListQuery = ClusterQuery.builder()
                                                 .accountId(ACCOUNT_ID)
                                                 .orgIdentifier(null)
                                                 .projectIdentifier(null)
                                                 .searchTerm("")
                                                 .pageIndex(0)
                                                 .pageSize(DEFAULT_PAGE_SIZE)
                                                 .filter(null)
                                                 .build();

    io.harness.gitops.models.Cluster projectCluster =
        new io.harness.gitops.models.Cluster("incluster", "projectCluster");
    projectCluster.setAgentIdentifier(AGENT_1_ID);

    Response<PageResponse<io.harness.gitops.models.Cluster>> projectClusterResponse = Response.success(
        PageResponse.<io.harness.gitops.models.Cluster>builder().content(List.of(projectCluster)).build());

    when(gitopsResourceClient.listClusters(prjLevelClustersListQuery)).thenReturn(projectClusterListCall);
    when(projectClusterListCall.execute()).thenReturn(projectClusterResponse);

    // Execute
    ResponseDTO<PageResponse<ClusterResponse>> result = clusterResource.list(PAGE_INDEX, PAGE_SIZE, ACCOUNT_ID, null,
        null, ENV_ID, "", Collections.emptyList(), Collections.emptyList(), ScopeLevel.ACCOUNT);

    ClusterResponse clusterResponse = ClusterResponse.builder()
                                          .clusterRef("account." + CLUSTER_REF)
                                          .orgIdentifier(null)
                                          .projectIdentifier(null)
                                          .accountIdentifier(ACCOUNT_ID)
                                          .agentIdentifier(AGENT_1_ID)
                                          .envRef(null)
                                          .linkedAt(null)
                                          .scope(ScopeLevel.ACCOUNT)
                                          .name("accCluster")
                                          .tags(Collections.emptyMap())
                                          .build();

    // Verify
    assertNotNull(result);
    assertThat(result.getData().getContent().size()).isEqualTo(1);
    assertEquals(List.of(clusterResponse), result.getData().getContent());
    verify(orgAndProjectValidationHelper).checkThatTheOrganizationAndProjectExists(null, null, ACCOUNT_ID);
    verify(environmentValidationHelper).checkThatEnvExists(ACCOUNT_ID, null, null, ENV_ID);
  }

  // we have same cluster at acc, org, proj with same agent. We should return only org level cluster
  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldListLinkedOrgClusters() throws IOException {
    Cluster linkedAccCluster = Cluster.builder()
                                   .accountId(ACCOUNT_ID)
                                   .orgIdentifier(ORG_ID)
                                   .agentIdentifier(AGENT_1_ID)
                                   .clusterRef("org." + CLUSTER_REF)
                                   .build();
    Page<Cluster> linkedClusters = new PageImpl<>(List.of(linkedAccCluster));

    doNothing()
        .when(accessControlClient)
        .checkForAccessOrThrow(any(ResourceScope.class), any(Resource.class), anyString(), anyString());
    when(environmentValidationHelper.checkThatEnvExists(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(Environment.builder().build());
    when(clusterService.list(anyInt(), anyInt(), eq(ACCOUNT_ID), eq(ORG_ID), eq(null), eq(ENV_ID), anyString(),
             anyList(), anyList(), any()))
        .thenReturn(linkedClusters);

    // account
    io.harness.gitops.models.Cluster accCluster = new io.harness.gitops.models.Cluster("incluster", "accCluster");
    accCluster.setAgentIdentifier(AGENT_1_ID);
    Response<PageResponse<io.harness.gitops.models.Cluster>> accClusterResponse =
        Response.success(PageResponse.<io.harness.gitops.models.Cluster>builder().content(List.of(accCluster)).build());

    when(gitopsResourceClient.listClusters(accLevelClustersListQuery)).thenReturn(accClusterListCall);
    when(accClusterListCall.execute()).thenReturn(accClusterResponse);

    // org
    ClusterQuery organisationLevelClustersListQuery = ClusterQuery.builder()
                                                          .accountId(ACCOUNT_ID)
                                                          .orgIdentifier(ORG_ID)
                                                          .projectIdentifier("")
                                                          .searchTerm("")
                                                          .pageIndex(0)
                                                          .pageSize(DEFAULT_PAGE_SIZE)
                                                          .filter(null)
                                                          .build();

    io.harness.gitops.models.Cluster orgCluster = new io.harness.gitops.models.Cluster("incluster", "orgCluster");
    orgCluster.setAgentIdentifier(AGENT_1_ID);
    Response<PageResponse<io.harness.gitops.models.Cluster>> orgClusterResponse =
        Response.success(PageResponse.<io.harness.gitops.models.Cluster>builder().content(List.of(orgCluster)).build());

    when(gitopsResourceClient.listClusters(organisationLevelClustersListQuery)).thenReturn(orgClusterListCall);
    when(orgClusterListCall.execute()).thenReturn(orgClusterResponse);

    // project
    ClusterQuery prjLevelClustersListQuery = ClusterQuery.builder()
                                                 .accountId(ACCOUNT_ID)
                                                 .orgIdentifier(ORG_ID)
                                                 .projectIdentifier(null)
                                                 .searchTerm("")
                                                 .pageIndex(0)
                                                 .pageSize(DEFAULT_PAGE_SIZE)
                                                 .filter(null)
                                                 .build();

    io.harness.gitops.models.Cluster projectCluster =
        new io.harness.gitops.models.Cluster("incluster", "projectCluster");
    projectCluster.setAgentIdentifier(AGENT_1_ID);

    Response<PageResponse<io.harness.gitops.models.Cluster>> projectClusterResponse = Response.success(
        PageResponse.<io.harness.gitops.models.Cluster>builder().content(List.of(projectCluster)).build());

    when(gitopsResourceClient.listClusters(prjLevelClustersListQuery)).thenReturn(projectClusterListCall);
    when(projectClusterListCall.execute()).thenReturn(projectClusterResponse);

    // Execute
    ResponseDTO<PageResponse<ClusterResponse>> result = clusterResource.list(PAGE_INDEX, PAGE_SIZE, ACCOUNT_ID, ORG_ID,
        null, ENV_ID, "", Collections.emptyList(), Collections.emptyList(), ScopeLevel.ORGANIZATION);

    ClusterResponse clusterResponse = ClusterResponse.builder()
                                          .clusterRef("org." + CLUSTER_REF)
                                          .orgIdentifier(ORG_ID)
                                          .projectIdentifier(null)
                                          .accountIdentifier(ACCOUNT_ID)
                                          .agentIdentifier(AGENT_1_ID)
                                          .envRef(null)
                                          .linkedAt(null)
                                          .scope(ScopeLevel.ORGANIZATION)
                                          .name("orgCluster")
                                          .tags(Collections.emptyMap())
                                          .build();

    // Verify
    assertNotNull(result);
    assertThat(result.getData().getContent().size()).isEqualTo(1);
    assertEquals(List.of(clusterResponse), result.getData().getContent());
    verify(orgAndProjectValidationHelper).checkThatTheOrganizationAndProjectExists(ORG_ID, null, ACCOUNT_ID);
    verify(environmentValidationHelper).checkThatEnvExists(ACCOUNT_ID, ORG_ID, null, ENV_ID);
  }

  private Cluster buildCluster(String agentIdentifier) {
    return Cluster.builder()
        .accountId(ACCOUNT_ID)
        .orgIdentifier(ORG_ID)
        .projectIdentifier(PROJECT_ID)
        .agentIdentifier(agentIdentifier)
        .clusterRef(CLUSTER_REF)
        .build();
  }

  private ClusterResponse buildClusterResponse(String agentIdentifier, String name) {
    return ClusterResponse.builder()
        .clusterRef(CLUSTER_REF)
        .orgIdentifier(ORG_ID)
        .projectIdentifier(PROJECT_ID)
        .accountIdentifier(ACCOUNT_ID)
        .agentIdentifier(agentIdentifier)
        .envRef(null)
        .linkedAt(null)
        .scope(ScopeLevel.PROJECT)
        .name(name)
        .tags(Collections.emptyMap())
        .build();
  }

  // Test cluster identifier validation - cluster doesn't exist (Detailed API)
  @Test
  @Owner(developers = HIMANSHU)
  @Category(UnitTests.class)
  public void testLinkBatchInternalWithWrongClusterIdentifier() throws IOException {
    // Request with non-existent cluster
    ClusterLinkRequest request = ClusterLinkRequest.builder()
                                     .orgIdentifier(ORG_ID)
                                     .projectIdentifier(PROJECT_ID)
                                     .envRef(ENV_ID)
                                     .linkAllClusters(false)
                                     .clusters(Collections.singletonList(new ClusterBatchRequest.ClusterBasicDTO() {
                                       {
                                         setIdentifier("non-existent-cluster");
                                         setAgentIdentifier(AGENT_1_ID);
                                         setScope(ScopeLevel.PROJECT);
                                       }
                                     }))
                                     .build();

    // Mock successful validations
    when(orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
             eq(ORG_ID), eq(PROJECT_ID), eq(ACCOUNT_ID)))
        .thenReturn(true);
    when(environmentValidationHelper.checkThatEnvExists(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV_ID)))
        .thenReturn(Environment.builder().build());
    doNothing()
        .when(accessControlClient)
        .checkForAccessOrThrow(any(ResourceScope.class), any(Resource.class), anyString(), anyString());

    // Mock empty responses - cluster not found
    Response<PageResponse<io.harness.gitops.models.Cluster>> emptyResponse = Response.success(
        PageResponse.<io.harness.gitops.models.Cluster>builder().content(Collections.emptyList()).build());

    when(gitopsResourceClient.listClusters(any())).thenReturn(accClusterListCall);
    when(accClusterListCall.execute()).thenReturn(emptyResponse);
    when(orgClusterListCall.execute()).thenReturn(emptyResponse);
    when(projectClusterListCall.execute()).thenReturn(emptyResponse);

    // Execute - should return 200 OK with failure in response
    ResponseDTO response = clusterResource.linkBatchInternal(ACCOUNT_ID, request);

    // Verify response structure
    assertNotNull(response);
    assertNotNull(response.getData());
    io.harness.cdng.gitops.beans.ClusterBatchResponse batchResponse =
        (io.harness.cdng.gitops.beans.ClusterBatchResponse) response.getData();

    // Verify failure details
    assertThat(batchResponse.getSuccess()).isEmpty();
    assertThat(batchResponse.getFailed()).hasSize(1);
    assertThat(batchResponse.getFailed().get(0).getClusterRef()).contains("non-existent-cluster");
    assertThat(batchResponse.getFailed().get(0).getFailureReason()).contains("does not exist");
  }

  // Test agent identifier validation - wrong agent for cluster (Detailed API)
  @Test
  @Owner(developers = HIMANSHU)
  @Category(UnitTests.class)
  public void testLinkBatchInternalWithWrongAgentIdentifier() throws IOException {
    // Request with wrong agent identifier
    ClusterLinkRequest request = ClusterLinkRequest.builder()
                                     .orgIdentifier(ORG_ID)
                                     .projectIdentifier(PROJECT_ID)
                                     .envRef(ENV_ID)
                                     .linkAllClusters(false)
                                     .clusters(Collections.singletonList(new ClusterBatchRequest.ClusterBasicDTO() {
                                       {
                                         setIdentifier("org-cluster");
                                         setAgentIdentifier(AGENT_1_ID);
                                         setScope(ScopeLevel.PROJECT);
                                       }
                                     }))
                                     .build();

    // Mock successful validations
    when(orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
             eq(ORG_ID), eq(PROJECT_ID), eq(ACCOUNT_ID)))
        .thenReturn(true);
    when(environmentValidationHelper.checkThatEnvExists(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV_ID)))
        .thenReturn(Environment.builder().build());
    doNothing()
        .when(accessControlClient)
        .checkForAccessOrThrow(any(ResourceScope.class), any(Resource.class), anyString(), anyString());

    // Mock empty responses - cluster+agent not found at PROJECT scope
    Response<PageResponse<io.harness.gitops.models.Cluster>> emptyResponse = Response.success(
        PageResponse.<io.harness.gitops.models.Cluster>builder().content(Collections.emptyList()).build());

    when(gitopsResourceClient.listClusters(any())).thenReturn(accClusterListCall);
    when(accClusterListCall.execute()).thenReturn(emptyResponse);
    when(orgClusterListCall.execute()).thenReturn(emptyResponse);
    when(projectClusterListCall.execute()).thenReturn(emptyResponse);

    // Execute - should return 200 OK with failure in response
    ResponseDTO response = clusterResource.linkBatchInternal(ACCOUNT_ID, request);

    // Verify response structure
    assertNotNull(response);
    assertNotNull(response.getData());
    io.harness.cdng.gitops.beans.ClusterBatchResponse batchResponse =
        (io.harness.cdng.gitops.beans.ClusterBatchResponse) response.getData();

    // Verify failure details
    assertThat(batchResponse.getSuccess()).isEmpty();
    assertThat(batchResponse.getFailed()).hasSize(1);
    assertThat(batchResponse.getFailed().get(0).getClusterRef()).contains("org-cluster");
    assertThat(batchResponse.getFailed().get(0).getFailureReason()).contains("does not exist");
  }

  // Test scope validation - cluster exists at ORG but requested as PROJECT (Detailed API)
  @Test
  @Owner(developers = HIMANSHU)
  @Category(UnitTests.class)
  public void testLinkBatchInternalWithWrongScope() throws IOException {
    // Request cluster as PROJECT scope but exists at ORG
    ClusterLinkRequest request = ClusterLinkRequest.builder()
                                     .orgIdentifier(ORG_ID)
                                     .projectIdentifier(PROJECT_ID)
                                     .envRef(ENV_ID)
                                     .linkAllClusters(false)
                                     .clusters(Collections.singletonList(new ClusterBatchRequest.ClusterBasicDTO() {
                                       {
                                         setIdentifier("existing-org-cluster");
                                         setAgentIdentifier(AGENT_1_ID);
                                         setScope(ScopeLevel.PROJECT);
                                       }
                                     }))
                                     .build();

    // Mock successful validations
    when(orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
             eq(ORG_ID), eq(PROJECT_ID), eq(ACCOUNT_ID)))
        .thenReturn(true);
    when(environmentValidationHelper.checkThatEnvExists(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV_ID)))
        .thenReturn(Environment.builder().build());
    doNothing()
        .when(accessControlClient)
        .checkForAccessOrThrow(any(ResourceScope.class), any(Resource.class), anyString(), anyString());

    // Cluster exists at ORG level
    io.harness.gitops.models.Cluster orgCluster = new io.harness.gitops.models.Cluster();
    orgCluster.setIdentifier("existing-org-cluster");
    orgCluster.setAgentIdentifier(AGENT_1_ID);

    // Mock responses - cluster at ORG, not PROJECT
    Response<PageResponse<io.harness.gitops.models.Cluster>> emptyResponse = Response.success(
        PageResponse.<io.harness.gitops.models.Cluster>builder().content(Collections.emptyList()).build());
    Response<PageResponse<io.harness.gitops.models.Cluster>> orgResponse =
        Response.success(PageResponse.<io.harness.gitops.models.Cluster>builder()
                             .content(Collections.singletonList(orgCluster))
                             .build());

    when(gitopsResourceClient.listClusters(any())).thenReturn(accClusterListCall);
    when(accClusterListCall.execute()).thenReturn(emptyResponse);
    when(orgClusterListCall.execute()).thenReturn(orgResponse);
    when(projectClusterListCall.execute()).thenReturn(emptyResponse);

    // Execute - should return 200 OK with failure in response
    ResponseDTO response = clusterResource.linkBatchInternal(ACCOUNT_ID, request);

    // Verify response structure
    assertNotNull(response);
    assertNotNull(response.getData());
    io.harness.cdng.gitops.beans.ClusterBatchResponse batchResponse =
        (io.harness.cdng.gitops.beans.ClusterBatchResponse) response.getData();

    // Verify failure details
    assertThat(batchResponse.getSuccess()).isEmpty();
    assertThat(batchResponse.getFailed()).hasSize(1);
    assertThat(batchResponse.getFailed().get(0).getClusterRef()).contains("existing-org-cluster");
    assertThat(batchResponse.getFailed().get(0).getFailureReason()).contains("does not exist");
  }

  // ===== ORIGINAL TESTS FOR OLD API (Exception Throwing Behavior) =====

  // Test cluster identifier validation - cluster doesn't exist (OLD API)
  @Test
  @Owner(developers = HIMANSHU)
  @Category(UnitTests.class)
  public void testLinkBatchWithWrongClusterIdentifier() throws IOException {
    // Request with non-existent cluster
    ClusterLinkRequest request = ClusterLinkRequest.builder()
                                     .orgIdentifier(ORG_ID)
                                     .projectIdentifier(PROJECT_ID)
                                     .envRef(ENV_ID)
                                     .linkAllClusters(false)
                                     .clusters(Collections.singletonList(new ClusterBatchRequest.ClusterBasicDTO() {
                                       {
                                         setIdentifier("non-existent-cluster");
                                         setAgentIdentifier(AGENT_1_ID);
                                         setScope(ScopeLevel.PROJECT);
                                       }
                                     }))
                                     .build();

    // Mock successful validations
    when(orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
             eq(ORG_ID), eq(PROJECT_ID), eq(ACCOUNT_ID)))
        .thenReturn(true);
    when(environmentValidationHelper.checkThatEnvExists(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV_ID)))
        .thenReturn(Environment.builder().build());
    doNothing()
        .when(accessControlClient)
        .checkForAccessOrThrow(any(ResourceScope.class), any(Resource.class), anyString(), anyString());

    // Mock empty responses - cluster not found
    Response<PageResponse<io.harness.gitops.models.Cluster>> emptyResponse = Response.success(
        PageResponse.<io.harness.gitops.models.Cluster>builder().content(Collections.emptyList()).build());

    when(gitopsResourceClient.listClusters(any())).thenReturn(accClusterListCall);
    when(accClusterListCall.execute()).thenReturn(emptyResponse);
    when(orgClusterListCall.execute()).thenReturn(emptyResponse);
    when(projectClusterListCall.execute()).thenReturn(emptyResponse);

    // Verify validation fails with exception
    try {
      clusterResource.linkBatch(ACCOUNT_ID, request);
      fail("Expected InvalidRequestException");
    } catch (InvalidRequestException e) {
      assertThat(e.getMessage()).contains("The following clusters do not exist");
      assertThat(e.getMessage()).contains("non-existent-cluster");
    }
  }

  // Test agent identifier validation - wrong agent for cluster (OLD API)
  @Test
  @Owner(developers = HIMANSHU)
  @Category(UnitTests.class)
  public void testLinkBatchWithWrongAgentIdentifier() throws IOException {
    // Request with wrong agent identifier
    ClusterLinkRequest request = ClusterLinkRequest.builder()
                                     .orgIdentifier(ORG_ID)
                                     .projectIdentifier(PROJECT_ID)
                                     .envRef(ENV_ID)
                                     .linkAllClusters(false)
                                     .clusters(Collections.singletonList(new ClusterBatchRequest.ClusterBasicDTO() {
                                       {
                                         setIdentifier("org-cluster");
                                         setAgentIdentifier(AGENT_1_ID);
                                         setScope(ScopeLevel.PROJECT);
                                       }
                                     }))
                                     .build();

    // Mock successful validations
    when(orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
             eq(ORG_ID), eq(PROJECT_ID), eq(ACCOUNT_ID)))
        .thenReturn(true);
    when(environmentValidationHelper.checkThatEnvExists(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV_ID)))
        .thenReturn(Environment.builder().build());
    doNothing()
        .when(accessControlClient)
        .checkForAccessOrThrow(any(ResourceScope.class), any(Resource.class), anyString(), anyString());

    // Mock response with cluster but different agent
    io.harness.gitops.models.Cluster projectCluster =
        new io.harness.gitops.models.Cluster("org-cluster", "Org Cluster");
    projectCluster.setAgentIdentifier(AGENT_2_ID);

    Response<PageResponse<io.harness.gitops.models.Cluster>> emptyResponse = Response.success(
        PageResponse.<io.harness.gitops.models.Cluster>builder().content(Collections.emptyList()).build());
    Response<PageResponse<io.harness.gitops.models.Cluster>> projectResponse = Response.success(
        PageResponse.<io.harness.gitops.models.Cluster>builder().content(List.of(projectCluster)).build());

    when(gitopsResourceClient.listClusters(any()))
        .thenReturn(accClusterListCall, orgClusterListCall, projectClusterListCall);
    when(accClusterListCall.execute()).thenReturn(emptyResponse);
    when(orgClusterListCall.execute()).thenReturn(emptyResponse);
    when(projectClusterListCall.execute()).thenReturn(projectResponse);

    // Verify validation fails with exception
    try {
      clusterResource.linkBatch(ACCOUNT_ID, request);
      fail("Expected InvalidRequestException");
    } catch (InvalidRequestException e) {
      assertThat(e.getMessage()).contains("The following clusters do not exist");
      assertThat(e.getMessage()).contains("org-cluster");
    }
  }

  // Test scope validation - cluster exists at ORG but requested as PROJECT (OLD API)
  @Test
  @Owner(developers = HIMANSHU)
  @Category(UnitTests.class)
  public void testLinkBatchWithWrongScope() throws IOException {
    // Request cluster as PROJECT scope when it exists at ORG scope
    ClusterLinkRequest request = ClusterLinkRequest.builder()
                                     .orgIdentifier(ORG_ID)
                                     .projectIdentifier(PROJECT_ID)
                                     .envRef(ENV_ID)
                                     .linkAllClusters(false)
                                     .clusters(Collections.singletonList(new ClusterBatchRequest.ClusterBasicDTO() {
                                       {
                                         setIdentifier("existing-org-cluster");
                                         setAgentIdentifier(AGENT_1_ID);
                                         setScope(ScopeLevel.PROJECT);
                                       }
                                     }))
                                     .build();

    // Mock successful validations
    when(orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
             eq(ORG_ID), eq(PROJECT_ID), eq(ACCOUNT_ID)))
        .thenReturn(true);
    when(environmentValidationHelper.checkThatEnvExists(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV_ID)))
        .thenReturn(Environment.builder().build());
    doNothing()
        .when(accessControlClient)
        .checkForAccessOrThrow(any(ResourceScope.class), any(Resource.class), anyString(), anyString());

    // Mock response - cluster exists at ORG level
    io.harness.gitops.models.Cluster orgCluster =
        new io.harness.gitops.models.Cluster("existing-org-cluster", "Org Level Cluster");
    orgCluster.setAgentIdentifier(AGENT_1_ID);

    Response<PageResponse<io.harness.gitops.models.Cluster>> emptyResponse = Response.success(
        PageResponse.<io.harness.gitops.models.Cluster>builder().content(Collections.emptyList()).build());
    Response<PageResponse<io.harness.gitops.models.Cluster>> orgResponse =
        Response.success(PageResponse.<io.harness.gitops.models.Cluster>builder().content(List.of(orgCluster)).build());

    when(gitopsResourceClient.listClusters(any()))
        .thenReturn(accClusterListCall, orgClusterListCall, projectClusterListCall);
    when(accClusterListCall.execute()).thenReturn(emptyResponse);
    when(orgClusterListCall.execute()).thenReturn(orgResponse);
    when(projectClusterListCall.execute()).thenReturn(emptyResponse);

    // Verify validation fails with exception
    try {
      clusterResource.linkBatch(ACCOUNT_ID, request);
      fail("Expected InvalidRequestException");
    } catch (InvalidRequestException e) {
      assertThat(e.getMessage()).contains("The following clusters do not exist");
      assertThat(e.getMessage()).contains("existing-org-cluster");
    }
  }

  @Test
  @Owner(developers = HIMANSHU)
  @Category(UnitTests.class)
  public void testScopeHierarchyValidation_ProjectEnvCanLinkAllScopes() throws IOException {
    ClusterLinkRequest request = ClusterLinkRequest.builder()
                                     .orgIdentifier(ORG_ID)
                                     .projectIdentifier(PROJECT_ID)
                                     .envRef(ENV_ID)
                                     .linkAllClusters(false)
                                     .clusters(List.of(
                                         new ClusterBatchRequest.ClusterBasicDTO() {
                                           {
                                             setIdentifier("account-cluster");
                                             setAgentIdentifier(AGENT_1_ID);
                                             setScope(ScopeLevel.ACCOUNT);
                                           }
                                         },
                                         new ClusterBatchRequest.ClusterBasicDTO() {
                                           {
                                             setIdentifier("org-cluster");
                                             setAgentIdentifier(AGENT_1_ID);
                                             setScope(ScopeLevel.ORGANIZATION);
                                           }
                                         },
                                         new ClusterBatchRequest.ClusterBasicDTO() {
                                           {
                                             setIdentifier("project-cluster");
                                             setAgentIdentifier(AGENT_1_ID);
                                             setScope(ScopeLevel.PROJECT);
                                           }
                                         }))
                                     .build();

    when(orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
             eq(ORG_ID), eq(PROJECT_ID), eq(ACCOUNT_ID)))
        .thenReturn(true);
    when(environmentValidationHelper.checkThatEnvExists(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV_ID)))
        .thenReturn(Environment.builder().build());
    when(scopeInfoService.getScopeInfo(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID)))
        .thenReturn(io.harness.beans.ScopeInfo.builder()
                        .accountIdentifier(ACCOUNT_ID)
                        .orgIdentifier(ORG_ID)
                        .projectIdentifier(PROJECT_ID)
                        .scopeType(ScopeLevel.PROJECT)
                        .uniqueId("test-unique-id")
                        .build());
    doNothing()
        .when(accessControlClient)
        .checkForAccessOrThrow(any(ResourceScope.class), any(Resource.class), anyString(), anyString());

    io.harness.gitops.models.Cluster accountCluster =
        new io.harness.gitops.models.Cluster("account-cluster", "Account Cluster");
    accountCluster.setAgentIdentifier(AGENT_1_ID);

    io.harness.gitops.models.Cluster orgCluster = new io.harness.gitops.models.Cluster("org-cluster", "Org Cluster");
    orgCluster.setAgentIdentifier(AGENT_1_ID);

    io.harness.gitops.models.Cluster projectCluster =
        new io.harness.gitops.models.Cluster("project-cluster", "Project Cluster");
    projectCluster.setAgentIdentifier(AGENT_1_ID);

    Response<PageResponse<io.harness.gitops.models.Cluster>> accResponse = Response.success(
        PageResponse.<io.harness.gitops.models.Cluster>builder().content(List.of(accountCluster)).build());
    Response<PageResponse<io.harness.gitops.models.Cluster>> orgResponse =
        Response.success(PageResponse.<io.harness.gitops.models.Cluster>builder().content(List.of(orgCluster)).build());
    Response<PageResponse<io.harness.gitops.models.Cluster>> projectResponse = Response.success(
        PageResponse.<io.harness.gitops.models.Cluster>builder().content(List.of(projectCluster)).build());

    when(gitopsResourceClient.listClusters(any()))
        .thenReturn(accClusterListCall, orgClusterListCall, projectClusterListCall);

    when(accClusterListCall.execute()).thenReturn(accResponse);
    when(orgClusterListCall.execute()).thenReturn(orgResponse);
    when(projectClusterListCall.execute()).thenReturn(projectResponse);

    when(clusterService.bulkCreateInternal(anyList()))
        .thenReturn(io.harness.cdng.gitops.beans.ClusterBulkOperationResult.builder()
                        .successfulClusters(List.of(
                            io.harness.cdng.gitops.beans.ClusterBulkOperationResult.ClusterOperationDetail.builder()
                                .clusterRef("account.account-cluster")
                                .agentIdentifier(AGENT_1_ID)
                                .name("Account Cluster")
                                .build(),
                            io.harness.cdng.gitops.beans.ClusterBulkOperationResult.ClusterOperationDetail.builder()
                                .clusterRef("org.org-cluster")
                                .agentIdentifier(AGENT_1_ID)
                                .name("Org Cluster")
                                .build(),
                            io.harness.cdng.gitops.beans.ClusterBulkOperationResult.ClusterOperationDetail.builder()
                                .clusterRef("project-cluster")
                                .agentIdentifier(AGENT_1_ID)
                                .name("Project Cluster")
                                .build()))
                        .failedClusters(List.of())
                        .build());

    ResponseDTO response = clusterResource.linkBatchInternal(ACCOUNT_ID, request);

    assertNotNull(response);
    io.harness.cdng.gitops.beans.ClusterBatchResponse batchResponse =
        (io.harness.cdng.gitops.beans.ClusterBatchResponse) response.getData();
    assertThat(batchResponse.getSuccess()).hasSize(3);
    assertThat(batchResponse.getFailed()).isEmpty();
  }

  @Test
  @Owner(developers = HIMANSHU)
  @Category(UnitTests.class)
  public void testScopeHierarchyValidation_AccountEnvCannotLinkNarrowerScopes() throws IOException {
    ClusterLinkRequest request = ClusterLinkRequest.builder()
                                     .orgIdentifier(null)
                                     .projectIdentifier(null)
                                     .envRef(ENV_ID)
                                     .linkAllClusters(false)
                                     .clusters(List.of(new ClusterBatchRequest.ClusterBasicDTO() {
                                       {
                                         setIdentifier("org-cluster");
                                         setAgentIdentifier(AGENT_1_ID);
                                         setScope(ScopeLevel.ORGANIZATION);
                                       }
                                     }))
                                     .build();

    when(orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(eq(null), eq(null), eq(ACCOUNT_ID)))
        .thenReturn(true);
    when(environmentValidationHelper.checkThatEnvExists(eq(ACCOUNT_ID), eq(null), eq(null), eq(ENV_ID)))
        .thenReturn(Environment.builder().build());
    when(scopeInfoService.getScopeInfo(eq(ACCOUNT_ID), eq(null), eq(null)))
        .thenReturn(io.harness.beans.ScopeInfo.builder()
                        .accountIdentifier(ACCOUNT_ID)
                        .scopeType(ScopeLevel.ACCOUNT)
                        .uniqueId("test-unique-id")
                        .build());
    doNothing()
        .when(accessControlClient)
        .checkForAccessOrThrow(any(ResourceScope.class), any(Resource.class), anyString(), anyString());

    Response<PageResponse<io.harness.gitops.models.Cluster>> emptyResponse = Response.success(
        PageResponse.<io.harness.gitops.models.Cluster>builder().content(Collections.emptyList()).build());

    when(gitopsResourceClient.listClusters(any())).thenReturn(accClusterListCall);
    when(accClusterListCall.execute()).thenReturn(emptyResponse);

    ResponseDTO response = clusterResource.linkBatchInternal(ACCOUNT_ID, request);

    assertNotNull(response);
    io.harness.cdng.gitops.beans.ClusterBatchResponse batchResponse =
        (io.harness.cdng.gitops.beans.ClusterBatchResponse) response.getData();
    assertThat(batchResponse.getSuccess()).isEmpty();
    assertThat(batchResponse.getFailed()).hasSize(1);
    assertThat(batchResponse.getFailed().get(0).getFailureReason()).contains("scope");
  }

  @Test
  @Owner(developers = HIMANSHU)
  @Category(UnitTests.class)
  public void testScopeHierarchyValidation_OrgEnvCannotLinkProjectScope() throws IOException {
    ClusterLinkRequest request = ClusterLinkRequest.builder()
                                     .orgIdentifier(ORG_ID)
                                     .projectIdentifier(null)
                                     .envRef(ENV_ID)
                                     .linkAllClusters(false)
                                     .clusters(List.of(new ClusterBatchRequest.ClusterBasicDTO() {
                                       {
                                         setIdentifier("project-cluster");
                                         setAgentIdentifier(AGENT_1_ID);
                                         setScope(ScopeLevel.PROJECT);
                                       }
                                     }))
                                     .build();

    when(orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(eq(ORG_ID), eq(null), eq(ACCOUNT_ID)))
        .thenReturn(true);
    when(environmentValidationHelper.checkThatEnvExists(eq(ACCOUNT_ID), eq(ORG_ID), eq(null), eq(ENV_ID)))
        .thenReturn(Environment.builder().build());
    when(scopeInfoService.getScopeInfo(eq(ACCOUNT_ID), eq(ORG_ID), eq(null)))
        .thenReturn(io.harness.beans.ScopeInfo.builder()
                        .accountIdentifier(ACCOUNT_ID)
                        .orgIdentifier(ORG_ID)
                        .scopeType(ScopeLevel.ORGANIZATION)
                        .uniqueId("test-unique-id")
                        .build());
    doNothing()
        .when(accessControlClient)
        .checkForAccessOrThrow(any(ResourceScope.class), any(Resource.class), anyString(), anyString());

    Response<PageResponse<io.harness.gitops.models.Cluster>> emptyResponse = Response.success(
        PageResponse.<io.harness.gitops.models.Cluster>builder().content(Collections.emptyList()).build());

    when(gitopsResourceClient.listClusters(any())).thenReturn(accClusterListCall);
    when(accClusterListCall.execute()).thenReturn(emptyResponse);

    ResponseDTO response = clusterResource.linkBatchInternal(ACCOUNT_ID, request);

    assertNotNull(response);
    io.harness.cdng.gitops.beans.ClusterBatchResponse batchResponse =
        (io.harness.cdng.gitops.beans.ClusterBatchResponse) response.getData();
    assertThat(batchResponse.getSuccess()).isEmpty();
    assertThat(batchResponse.getFailed()).hasSize(1);
    assertThat(batchResponse.getFailed().get(0).getFailureReason()).contains("scope");
  }

  @Test
  @Owner(developers = HIMANSHU)
  @Category(UnitTests.class)
  public void testSkipClusterValidation_SkipsExistenceCheck() throws IOException {
    ClusterLinkRequest request = ClusterLinkRequest.builder()
                                     .orgIdentifier(ORG_ID)
                                     .projectIdentifier(PROJECT_ID)
                                     .envRef(ENV_ID)
                                     .linkAllClusters(false)
                                     .skipClusterValidation(true)
                                     .clusters(List.of(new ClusterBatchRequest.ClusterBasicDTO() {
                                       {
                                         setIdentifier("non-existent-cluster");
                                         setAgentIdentifier(AGENT_1_ID);
                                         setScope(ScopeLevel.PROJECT);
                                       }
                                     }))
                                     .build();

    when(orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
             eq(ORG_ID), eq(PROJECT_ID), eq(ACCOUNT_ID)))
        .thenReturn(true);
    when(environmentValidationHelper.checkThatEnvExists(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV_ID)))
        .thenReturn(Environment.builder().build());
    when(scopeInfoService.getScopeInfo(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID)))
        .thenReturn(io.harness.beans.ScopeInfo.builder()
                        .accountIdentifier(ACCOUNT_ID)
                        .orgIdentifier(ORG_ID)
                        .projectIdentifier(PROJECT_ID)
                        .scopeType(ScopeLevel.PROJECT)
                        .uniqueId("test-unique-id")
                        .build());
    doNothing()
        .when(accessControlClient)
        .checkForAccessOrThrow(any(ResourceScope.class), any(Resource.class), anyString(), anyString());

    when(clusterService.bulkCreateInternal(anyList()))
        .thenReturn(io.harness.cdng.gitops.beans.ClusterBulkOperationResult.builder()
                        .successfulClusters(List.of(
                            io.harness.cdng.gitops.beans.ClusterBulkOperationResult.ClusterOperationDetail.builder()
                                .clusterRef("non-existent-cluster")
                                .agentIdentifier(AGENT_1_ID)
                                .build()))
                        .failedClusters(List.of())
                        .build());

    ResponseDTO response = clusterResource.linkBatchInternal(ACCOUNT_ID, request);

    assertNotNull(response);
    io.harness.cdng.gitops.beans.ClusterBatchResponse batchResponse =
        (io.harness.cdng.gitops.beans.ClusterBatchResponse) response.getData();
    assertThat(batchResponse.getSuccess()).hasSize(1);
    assertThat(batchResponse.getFailed()).isEmpty();
  }

  @Test
  @Owner(developers = HIMANSHU)
  @Category(UnitTests.class)
  public void testPartialSuccess_SomeClustersSucceedSomeFail() throws IOException {
    ClusterLinkRequest request = ClusterLinkRequest.builder()
                                     .orgIdentifier(ORG_ID)
                                     .projectIdentifier(PROJECT_ID)
                                     .envRef(ENV_ID)
                                     .linkAllClusters(false)
                                     .skipClusterValidation(true)
                                     .clusters(List.of(
                                         new ClusterBatchRequest.ClusterBasicDTO() {
                                           {
                                             setIdentifier("valid-cluster");
                                             setAgentIdentifier(AGENT_1_ID);
                                             setScope(ScopeLevel.PROJECT);
                                           }
                                         },
                                         new ClusterBatchRequest.ClusterBasicDTO() {
                                           {
                                             setIdentifier("invalid-cluster");
                                             setAgentIdentifier(AGENT_1_ID);
                                             setScope(ScopeLevel.PROJECT);
                                           }
                                         }))
                                     .build();

    when(orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
             eq(ORG_ID), eq(PROJECT_ID), eq(ACCOUNT_ID)))
        .thenReturn(true);
    when(environmentValidationHelper.checkThatEnvExists(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV_ID)))
        .thenReturn(Environment.builder().build());
    when(scopeInfoService.getScopeInfo(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID)))
        .thenReturn(io.harness.beans.ScopeInfo.builder()
                        .accountIdentifier(ACCOUNT_ID)
                        .orgIdentifier(ORG_ID)
                        .projectIdentifier(PROJECT_ID)
                        .scopeType(ScopeLevel.PROJECT)
                        .uniqueId("test-unique-id")
                        .build());
    doNothing()
        .when(accessControlClient)
        .checkForAccessOrThrow(any(ResourceScope.class), any(Resource.class), anyString(), anyString());

    when(clusterService.bulkCreateInternal(anyList()))
        .thenReturn(io.harness.cdng.gitops.beans.ClusterBulkOperationResult.builder()
                        .successfulClusters(List.of(
                            io.harness.cdng.gitops.beans.ClusterBulkOperationResult.ClusterOperationDetail.builder()
                                .clusterRef("valid-cluster")
                                .agentIdentifier(AGENT_1_ID)
                                .name("Valid Cluster")
                                .build()))
                        .failedClusters(List.of(
                            io.harness.cdng.gitops.beans.ClusterBulkOperationResult.ClusterOperationDetail.builder()
                                .clusterRef("invalid-cluster")
                                .agentIdentifier(AGENT_1_ID)
                                .failureReason("Database constraint violation")
                                .errorCode("DB_ERROR")
                                .build()))
                        .build());

    ResponseDTO response = clusterResource.linkBatchInternal(ACCOUNT_ID, request);

    assertNotNull(response);
    io.harness.cdng.gitops.beans.ClusterBatchResponse batchResponse =
        (io.harness.cdng.gitops.beans.ClusterBatchResponse) response.getData();
    assertThat(batchResponse.getSuccess()).hasSize(1);
    assertThat(batchResponse.getFailed()).hasSize(1);
    assertThat(batchResponse.getFailed().get(0).getClusterRef()).contains("invalid-cluster");
  }

  @Test
  @Owner(developers = HIMANSHU)
  @Category(UnitTests.class)
  public void testNameEnrichment_NamesPopulatedFromGitOps() throws IOException {
    ClusterLinkRequest request = ClusterLinkRequest.builder()
                                     .orgIdentifier(ORG_ID)
                                     .projectIdentifier(PROJECT_ID)
                                     .envRef(ENV_ID)
                                     .linkAllClusters(false)
                                     .skipClusterValidation(true)
                                     .clusters(List.of(new ClusterBatchRequest.ClusterBasicDTO() {
                                       {
                                         setIdentifier("my-cluster");
                                         setAgentIdentifier(AGENT_1_ID);
                                         setScope(ScopeLevel.PROJECT);
                                       }
                                     }))
                                     .build();

    when(orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
             eq(ORG_ID), eq(PROJECT_ID), eq(ACCOUNT_ID)))
        .thenReturn(true);
    when(environmentValidationHelper.checkThatEnvExists(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV_ID)))
        .thenReturn(Environment.builder().build());
    when(scopeInfoService.getScopeInfo(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID)))
        .thenReturn(io.harness.beans.ScopeInfo.builder()
                        .accountIdentifier(ACCOUNT_ID)
                        .orgIdentifier(ORG_ID)
                        .projectIdentifier(PROJECT_ID)
                        .scopeType(ScopeLevel.PROJECT)
                        .uniqueId("test-unique-id")
                        .build());
    doNothing()
        .when(accessControlClient)
        .checkForAccessOrThrow(any(ResourceScope.class), any(Resource.class), anyString(), anyString());

    io.harness.gitops.models.Cluster gitopsCluster =
        new io.harness.gitops.models.Cluster("my-cluster", "My Production Cluster");
    gitopsCluster.setAgentIdentifier(AGENT_1_ID);

    Response<PageResponse<io.harness.gitops.models.Cluster>> accResponse = Response.success(
        PageResponse.<io.harness.gitops.models.Cluster>builder().content(Collections.emptyList()).build());
    Response<PageResponse<io.harness.gitops.models.Cluster>> orgResponse = Response.success(
        PageResponse.<io.harness.gitops.models.Cluster>builder().content(Collections.emptyList()).build());
    Response<PageResponse<io.harness.gitops.models.Cluster>> projectResponse = Response.success(
        PageResponse.<io.harness.gitops.models.Cluster>builder().content(List.of(gitopsCluster)).build());

    when(gitopsResourceClient.listClusters(any()))
        .thenReturn(accClusterListCall, orgClusterListCall, projectClusterListCall, accClusterListCall,
            orgClusterListCall, projectClusterListCall);

    when(accClusterListCall.execute()).thenReturn(accResponse);
    when(orgClusterListCall.execute()).thenReturn(orgResponse);
    when(projectClusterListCall.execute()).thenReturn(projectResponse);

    when(clusterService.bulkCreateInternal(anyList()))
        .thenReturn(io.harness.cdng.gitops.beans.ClusterBulkOperationResult.builder()
                        .successfulClusters(List.of(
                            io.harness.cdng.gitops.beans.ClusterBulkOperationResult.ClusterOperationDetail.builder()
                                .clusterRef("my-cluster")
                                .agentIdentifier(AGENT_1_ID)
                                .build()))
                        .failedClusters(List.of())
                        .build());

    ResponseDTO response = clusterResource.linkBatchInternal(ACCOUNT_ID, request);

    assertNotNull(response);
    io.harness.cdng.gitops.beans.ClusterBatchResponse batchResponse =
        (io.harness.cdng.gitops.beans.ClusterBatchResponse) response.getData();
    assertThat(batchResponse.getSuccess()).hasSize(1);
    assertThat(batchResponse.getSuccess().get(0).getName()).isEqualTo("My Production Cluster");
  }

  @Test
  @Owner(developers = HIMANSHU)
  @Category(UnitTests.class)
  public void testAlwaysReturns200_EvenWithAllFailures() throws IOException {
    ClusterLinkRequest request = ClusterLinkRequest.builder()
                                     .orgIdentifier(ORG_ID)
                                     .projectIdentifier(PROJECT_ID)
                                     .envRef(ENV_ID)
                                     .linkAllClusters(false)
                                     .clusters(List.of(
                                         new ClusterBatchRequest.ClusterBasicDTO() {
                                           {
                                             setIdentifier("invalid-1");
                                             setAgentIdentifier(AGENT_1_ID);
                                             setScope(ScopeLevel.PROJECT);
                                           }
                                         },
                                         new ClusterBatchRequest.ClusterBasicDTO() {
                                           {
                                             setIdentifier("invalid-2");
                                             setAgentIdentifier(AGENT_1_ID);
                                             setScope(ScopeLevel.PROJECT);
                                           }
                                         }))
                                     .build();

    when(orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
             eq(ORG_ID), eq(PROJECT_ID), eq(ACCOUNT_ID)))
        .thenReturn(true);
    when(environmentValidationHelper.checkThatEnvExists(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV_ID)))
        .thenReturn(Environment.builder().build());
    when(scopeInfoService.getScopeInfo(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID)))
        .thenReturn(io.harness.beans.ScopeInfo.builder()
                        .accountIdentifier(ACCOUNT_ID)
                        .orgIdentifier(ORG_ID)
                        .projectIdentifier(PROJECT_ID)
                        .scopeType(ScopeLevel.PROJECT)
                        .uniqueId("test-unique-id")
                        .build());
    doNothing()
        .when(accessControlClient)
        .checkForAccessOrThrow(any(ResourceScope.class), any(Resource.class), anyString(), anyString());

    Response<PageResponse<io.harness.gitops.models.Cluster>> emptyResponse = Response.success(
        PageResponse.<io.harness.gitops.models.Cluster>builder().content(Collections.emptyList()).build());

    when(gitopsResourceClient.listClusters(any())).thenReturn(accClusterListCall);
    when(accClusterListCall.execute()).thenReturn(emptyResponse);
    when(orgClusterListCall.execute()).thenReturn(emptyResponse);
    when(projectClusterListCall.execute()).thenReturn(emptyResponse);

    ResponseDTO response = clusterResource.linkBatchInternal(ACCOUNT_ID, request);

    assertNotNull(response);
    io.harness.cdng.gitops.beans.ClusterBatchResponse batchResponse =
        (io.harness.cdng.gitops.beans.ClusterBatchResponse) response.getData();
    assertThat(batchResponse.getSuccess()).isEmpty();
    assertThat(batchResponse.getFailed()).hasSize(2);
  }
}
