/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.helper;

import static io.harness.pms.rbac.PipelineRbacPermissions.PIPELINE_VIEW;
import static io.harness.rule.OwnerRule.NIKHIL_NEERUDU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.dynamic.DynamicExecutionService;
import io.harness.execution.dynamic.dtos.DynamicExecutionInstanceResponseDTO;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.plan.execution.RetryExecutionHelper;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.service.intfc.PMSExecutionService;
import io.harness.rule.Owner;
import io.harness.spec.server.pipeline.v1.model.DynamicExecutionDetailsResponseBody;

import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineExecutionDetailsApiImplTest extends CategoryTest {
  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  private static final String PIPELINE_ID = "pipelineId";
  private static final String PLAN_EXECUTION_ID = "planExecutionId";
  private static final String NODE_EXECUTION_ID = "nodeExecutionId";
  private static final String DYNAMIC_YAML = "pipeline:\n  identifier: dynamic\n";

  @Mock private PMSExecutionService pmsExecutionService;
  @Mock private AccessControlClient accessControlClient;
  @Mock private ExecutionHelper executionHelper;
  @Mock private PmsGitSyncHelper pmsGitSyncHelper;
  @Mock private DynamicExecutionService dynamicExecutionService;
  @Mock private RetryExecutionHelper retryExecutionHelper;

  private PipelineExecutionDetailsApiImpl pipelineExecutionDetailsApi;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    pipelineExecutionDetailsApi = new PipelineExecutionDetailsApiImpl(pmsExecutionService, accessControlClient,
        executionHelper, pmsGitSyncHelper, dynamicExecutionService, retryExecutionHelper);
  }

  private PipelineExecutionSummaryEntity executionSummary() {
    return PipelineExecutionSummaryEntity.builder()
        .accountId(ACCOUNT_ID)
        .orgIdentifier(ORG_ID)
        .projectIdentifier(PROJECT_ID)
        .pipelineIdentifier(PIPELINE_ID)
        .planExecutionId(PLAN_EXECUTION_ID)
        .build();
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testGetDynamicExecutionDetails_checksPipelineView() {
    when(pmsExecutionService.getPipelineExecutionSummaryEntity(ACCOUNT_ID, PLAN_EXECUTION_ID, false))
        .thenReturn(executionSummary());
    when(dynamicExecutionService.getByNodeExecutionId(NODE_EXECUTION_ID))
        .thenReturn(DynamicExecutionInstanceResponseDTO.builder()
                        .nodeExecutionId(NODE_EXECUTION_ID)
                        .planExecutionId(PLAN_EXECUTION_ID)
                        .yaml(DYNAMIC_YAML)
                        .processedYaml(DYNAMIC_YAML)
                        .build());

    Response response = pipelineExecutionDetailsApi.getDynamicExecutionDetails(
        ORG_ID, PROJECT_ID, PLAN_EXECUTION_ID, NODE_EXECUTION_ID, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(200);
    DynamicExecutionDetailsResponseBody body = (DynamicExecutionDetailsResponseBody) response.getEntity();
    assertThat(body.getYaml()).isEqualTo(DYNAMIC_YAML);
    verify(accessControlClient)
        .checkForAccessOrThrow(eq(ResourceScope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID)),
            eq(Resource.of("PIPELINE", PIPELINE_ID)), eq(PIPELINE_VIEW));
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testGetDynamicExecutionDetails_accessDeniedDoesNotFetchDynamicYaml() {
    when(pmsExecutionService.getPipelineExecutionSummaryEntity(ACCOUNT_ID, PLAN_EXECUTION_ID, false))
        .thenReturn(executionSummary());
    doThrow(new InvalidRequestException("denied"))
        .when(accessControlClient)
        .checkForAccessOrThrow(any(ResourceScope.class), any(Resource.class), eq(PIPELINE_VIEW));

    assertThatThrownBy(()
                           -> pipelineExecutionDetailsApi.getDynamicExecutionDetails(
                               ORG_ID, PROJECT_ID, PLAN_EXECUTION_ID, NODE_EXECUTION_ID, ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class);
    verify(dynamicExecutionService, never()).getByNodeExecutionId(any());
  }
}
