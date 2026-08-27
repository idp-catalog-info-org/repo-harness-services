/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.sfexecution.resources;

import static io.harness.rule.OwnerRule.HARSHIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.sfexecution.SalesforceExecutionOrchestrationService;
import io.harness.ng.core.sfexecution.entity.SalesforceExecutionEntity;
import io.harness.ng.core.sfexecution.entity.SalesforceExecutionStatus;
import io.harness.ng.core.sfexecution.entity.SalesforceExecutionType;
import io.harness.ng.core.sfexecution.services.SalesforceExecutionService;
import io.harness.rule.Owner;
import io.harness.spec.server.ng.v1.model.SalesforceExecution;
import io.harness.spec.server.ng.v1.model.SalesforceExecutionListItem;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.ws.rs.core.Response;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

public class AbstractSalesforceExecutionsApiImplTest extends CategoryTest {
  @Mock private SalesforceExecutionService service;
  @Mock private io.harness.ng.core.services.ScopeInfoService scopeInfoService;
  @Mock private SalesforceExecutionOrchestrationService orchestrationService;

  @InjectMocks private AbstractSalesforceExecutionsApiImpl abstractApi;

  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  private static final String IDENTIFIER = "execution1";
  private static final String NAME = "Execution 1";
  private static final String PIPELINE_ID = "myPipeline";
  private static final String PIPELINE_EXECUTION_ID = "execAbc123";

  private AutoCloseable mocks;
  private ScopeInfo scopeInfo;
  private SalesforceExecutionEntity entity;

  @Before
  public void setUp() {
    mocks = MockitoAnnotations.openMocks(this);

    scopeInfo = ScopeInfo.builder()
                    .accountIdentifier(ACCOUNT_ID)
                    .orgIdentifier(ORG_ID)
                    .projectIdentifier(PROJECT_ID)
                    .uniqueId("uniqueId")
                    .build();

    entity = SalesforceExecutionEntity.builder()
                 .accountIdentifier(ACCOUNT_ID)
                 .orgIdentifier(ORG_ID)
                 .projectIdentifier(PROJECT_ID)
                 .identifier(IDENTIFIER)
                 .name(NAME)
                 .pipelineId(PIPELINE_ID)
                 .pipelineExecutionId(PIPELINE_EXECUTION_ID)
                 .type(SalesforceExecutionType.DEPLOY)
                 .status(SalesforceExecutionStatus.IN_PROGRESS)
                 .build();
  }

  @After
  public void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testGetExecution_Success() {
    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(scopeInfo);
    when(service.get(scopeInfo, IDENTIFIER)).thenReturn(Optional.of(entity));
    when(orchestrationService.resolveStatusIfInProgress(entity)).thenReturn(entity);

    Response response = abstractApi.getExecution(ORG_ID, PROJECT_ID, IDENTIFIER, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(response.getEntity()).isInstanceOf(SalesforceExecution.class);
    verify(orchestrationService).resolveStatusIfInProgress(entity);
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testGetExecutions() {
    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(scopeInfo);
    SalesforceExecutionEntity succeededEntity = SalesforceExecutionEntity.builder()
                                                    .accountIdentifier(ACCOUNT_ID)
                                                    .orgIdentifier(ORG_ID)
                                                    .projectIdentifier(PROJECT_ID)
                                                    .identifier(IDENTIFIER)
                                                    .name(NAME)
                                                    .pipelineId(PIPELINE_ID)
                                                    .pipelineExecutionId(PIPELINE_EXECUTION_ID)
                                                    .type(SalesforceExecutionType.DEPLOY)
                                                    .status(SalesforceExecutionStatus.SUCCEEDED)
                                                    .build();
    Page<SalesforceExecutionEntity> page = new PageImpl<>(Arrays.asList(succeededEntity));
    when(service.list(any(Criteria.class), any(Pageable.class))).thenReturn(page);

    Response response = abstractApi.getExecutions(ORG_ID, PROJECT_ID, 0, 10, null, null, null, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    List<SalesforceExecutionListItem> result = (List<SalesforceExecutionListItem>) response.getEntity();
    assertThat(result).hasSize(1);
    // SUCCEEDED entity should NOT call resolveStatusIfInProgress
    verify(orchestrationService, org.mockito.Mockito.never()).resolveStatusIfInProgress(any());
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testGetExecutions_InProgressEntityResolved() {
    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(scopeInfo);
    Page<SalesforceExecutionEntity> page = new PageImpl<>(Arrays.asList(entity)); // IN_PROGRESS
    when(service.list(any(Criteria.class), any(Pageable.class))).thenReturn(page);
    SalesforceExecutionEntity resolvedEntity = SalesforceExecutionEntity.builder()
                                                   .accountIdentifier(ACCOUNT_ID)
                                                   .orgIdentifier(ORG_ID)
                                                   .projectIdentifier(PROJECT_ID)
                                                   .identifier(IDENTIFIER)
                                                   .name(NAME)
                                                   .pipelineId(PIPELINE_ID)
                                                   .pipelineExecutionId(PIPELINE_EXECUTION_ID)
                                                   .type(SalesforceExecutionType.DEPLOY)
                                                   .status(SalesforceExecutionStatus.SUCCEEDED)
                                                   .build();
    when(orchestrationService.resolveStatusIfInProgress(entity)).thenReturn(resolvedEntity);

    Response response = abstractApi.getExecutions(ORG_ID, PROJECT_ID, 0, 10, null, null, null, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    verify(orchestrationService).resolveStatusIfInProgress(entity);
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testGetExecutions_WithSearchTerm() {
    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(scopeInfo);
    Page<SalesforceExecutionEntity> page = new PageImpl<>(Arrays.asList(entity));
    when(service.list(any(Criteria.class), any(Pageable.class))).thenReturn(page);

    Response response = abstractApi.getExecutions(ORG_ID, PROJECT_ID, 0, 10, "searchTerm", "DEPLOY", null, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

    ArgumentCaptor<Criteria> criteriaCaptor = ArgumentCaptor.forClass(Criteria.class);
    verify(service).list(criteriaCaptor.capture(), any(Pageable.class));

    List<SalesforceExecutionListItem> result = (List<SalesforceExecutionListItem>) response.getEntity();
    assertThat(result).hasSize(1);
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testGetExecutions_WithChangesetIdFilter() {
    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(scopeInfo);
    Page<SalesforceExecutionEntity> page = new PageImpl<>(Arrays.asList(entity));
    when(service.list(any(Criteria.class), any(Pageable.class))).thenReturn(page);

    Response response = abstractApi.getExecutions(ORG_ID, PROJECT_ID, 0, 10, null, null, "changeset123", ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

    ArgumentCaptor<Criteria> criteriaCaptor = ArgumentCaptor.forClass(Criteria.class);
    verify(service).list(criteriaCaptor.capture(), any(Pageable.class));
    assertThat(criteriaCaptor.getValue().getCriteriaObject().toString()).contains("changeset123");
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testGetExecutions_InvalidTypeFilter_ThrowsInvalidRequestException() {
    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(scopeInfo);

    assertThatThrownBy(() -> abstractApi.getExecutions(ORG_ID, PROJECT_ID, 0, 10, null, "INVALID", null, ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("INVALID");
  }
}
