/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.approval;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.DEEPAK_PUTHRAYA;
import static io.harness.rule.OwnerRule.HINGER;
import static io.harness.rule.OwnerRule.RISHABH;
import static io.harness.rule.OwnerRule.SHREYAS_NAGARAJ;
import static io.harness.rule.OwnerRule.TATHAGAT;
import static io.harness.rule.OwnerRule.VED;
import static io.harness.rule.OwnerRule.YAGYANSH;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedServiceAccount;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.common.EntityTypeConstants;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.pms.data.expression.PmsEngineExpressionService;
import io.harness.exception.InvalidRequestException;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogLine;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.UserGroupDTO;
import io.harness.ng.core.user.UserInfo;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.helpers.CurrentUserHelper;
import io.harness.pms.yaml.ParameterField;
import io.harness.remote.client.CGRestUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.security.dto.ServiceAccountPrincipal;
import io.harness.security.dto.UserPrincipal;
import io.harness.serviceaccount.ServiceAccountDTOInternal;
import io.harness.serviceaccount.remote.ServiceAccountClient;
import io.harness.steps.approval.step.ApprovalInstanceResponseMapper;
import io.harness.steps.approval.step.beans.ApprovalInstanceResponseDTO;
import io.harness.steps.approval.step.beans.ApprovalStatus;
import io.harness.steps.approval.step.beans.ApprovalType;
import io.harness.steps.approval.step.beans.HarnessApprovalInstanceDetailsDTO;
import io.harness.steps.approval.step.beans.JiraApprovalInstanceDetailsDTO;
import io.harness.steps.approval.step.beans.PendingApprovalSummaryDTO;
import io.harness.steps.approval.step.custom.IrregularApprovalInstanceHandler;
import io.harness.steps.approval.step.entities.ApprovalInstance;
import io.harness.steps.approval.step.entities.ApprovalInstanceService;
import io.harness.steps.approval.step.entities.CustomApprovalInstance;
import io.harness.steps.approval.step.entities.HarnessApprovalInstance;
import io.harness.steps.approval.step.entities.JiraApprovalInstance;
import io.harness.steps.approval.step.entities.ServiceNowApprovalInstance;
import io.harness.steps.approval.step.harness.beans.ApproversDTO;
import io.harness.steps.approval.step.harness.beans.HarnessApprovalAction;
import io.harness.steps.approval.step.harness.beans.HarnessApprovalActivity;
import io.harness.steps.approval.step.harness.beans.HarnessApprovalActivityRequestDTO;
import io.harness.telemetry.helpers.ApprovalApiInstrumentationHelper;
import io.harness.user.remote.UserClient;
import io.harness.usergroups.UserGroupClient;
import io.harness.utils.ScopeResolutionHelper;
import io.harness.yaml.core.timeout.Timeout;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(PIPELINE)
public class ApprovalResourceImplServiceImplTest extends CategoryTest {
  @Mock private ApprovalInstanceService approvalInstanceService;
  @InjectMocks @Spy private ApprovalInstanceResponseMapper approvalInstanceResponseMapper;
  @Mock private PlanExecutionService planExecutionService;
  @Mock private UserGroupClient userGroupClient;
  @Mock private CurrentUserHelper currentUserHelper;
  @Mock private UserClient userClient;
  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Mock private ApprovalApiInstrumentationHelper instrumentationHelper;
  @Mock private PmsEngineExpressionService pmsEngineExpressionService;
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private ServiceAccountClient serviceAccountClient;
  @Mock private IrregularApprovalInstanceHandler irregularApprovalInstanceHandler;
  @Mock private ScopeResolutionHelper scopeResolutionHelper;
  private static final Long CREATED_AT = 1000L;
  private static final String ACCOUNT_ID = "accountId";
  private static final List<String> TEST_USER_GROUPS = List.of("userGroup1", "userGroup2");
  private static final EmbeddedUser TEST_EMBEDDED_USER =
      new EmbeddedUser("testUUID", "testName", "testUser@gmail.com", "testExternalUserId");
  ApprovalResourceServiceImpl approvalResourceService;
  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    approvalResourceService =
        new ApprovalResourceServiceImpl(approvalInstanceService, approvalInstanceResponseMapper, planExecutionService,
            userGroupClient, currentUserHelper, userClient, logStreamingStepClientFactory, instrumentationHelper,
            nodeExecutionService, serviceAccountClient, irregularApprovalInstanceHandler, scopeResolutionHelper);
    lenient()
        .when(scopeResolutionHelper.getScopeInfo(nullable(String.class), nullable(String.class)))
        .thenReturn(ScopeInfo.builder()
                        .accountIdentifier("accountId")
                        .orgIdentifier("orgIdentifier")
                        .projectIdentifier("projectIdentifier")
                        .uniqueId("uniqueId")
                        .build());
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGet() {
    String id = "dummy";
    ApprovalInstanceResponseDTO approvalInstanceResponseDTO = ApprovalInstanceResponseDTO.builder().id(id).build();
    ApprovalInstance approvalInstance = HarnessApprovalInstance.builder().build();
    approvalInstance.setId(id);
    approvalInstance.setAccountId(ACCOUNT_ID);
    when(approvalInstanceService.fetchFromObjectStoreWithFallback(ACCOUNT_ID, id)).thenReturn(approvalInstance);
    when(approvalInstanceService.fetchFromObjectStoreWithFallback(null, id)).thenReturn(approvalInstance);
    when(approvalInstanceService.fetchFromObjectStoreWithFallback("random", id)).thenReturn(approvalInstance);
    doReturn(approvalInstanceResponseDTO)
        .when(approvalInstanceResponseMapper)
        .toApprovalInstanceResponseDTO(approvalInstance, true);
    assertEquals(approvalResourceService.get(id, ACCOUNT_ID), approvalInstanceResponseDTO);
    assertEquals(approvalResourceService.get(id, null), approvalInstanceResponseDTO);
    assertThatThrownBy(() -> approvalResourceService.get(id, "random"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format(
            "Account Identifier provided %s doesn't match with approval instance's account identifier: %s", "random",
            approvalInstance.getAccountId()));
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testAddHarnessApprovalActivity() throws IOException {
    MockedStatic<NGRestUtils> aStatic = Mockito.mockStatic(NGRestUtils.class);
    String uuid = "uuid";
    String id = "dummy";
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", "accountId")
                            .putSetupAbstractions("orgIdentifier", "orgIdentifier")
                            .putSetupAbstractions("projectIdentifier", "projectIdentifier")
                            .build();
    EmbeddedUser embeddedUser = EmbeddedUser.builder().email("email").name("name").uuid(uuid).build();
    List<String> userGroups = new ArrayList<>();
    userGroups.add("approver");
    HarnessApprovalInstance harnessApprovalInstance =
        HarnessApprovalInstance.builder().approvers(ApproversDTO.builder().userGroups(userGroups).build()).build();
    harnessApprovalInstance.setAmbiance(ambiance);
    HarnessApprovalActivityRequestDTO harnessApprovalActivityRequestDTO =
        HarnessApprovalActivityRequestDTO.builder().build();
    when(approvalInstanceService.getHarnessApprovalInstance(id)).thenReturn(harnessApprovalInstance);
    List<UserGroupDTO> userGroupDTOS = Collections.singletonList(UserGroupDTO.builder().build());
    when(userGroupClient.getFilteredUserGroups(any())).thenReturn(null);
    aStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(userGroupDTOS);
    when(currentUserHelper.getPrincipalFromSecurityContext())
        .thenReturn(new UserPrincipal("email@harness.io", "name", "user", "ACCOUNTID"));
    Call userCall = mock(Call.class);
    when(userClient.getUserById("email@harness.io")).thenReturn(userCall);
    when(userCall.execute()).thenReturn(Response.success(new RestResponse(Optional.of(UserInfo.builder().build()))));
    // Should approve successfully
    approvalResourceService.addHarnessApprovalActivity(id, harnessApprovalActivityRequestDTO);

    harnessApprovalInstance.getApprovers().setUserGroups(Collections.emptyList());
    assertThatCode(() -> approvalResourceService.addHarnessApprovalActivity(id, harnessApprovalActivityRequestDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("User not authorized to approve/reject");

    harnessApprovalInstance.getApprovers().setDisallowPipelineExecutor(true);
    ExecutionMetadata metadata = ExecutionMetadata.newBuilder()
                                     .setTriggerInfo(ExecutionTriggerInfo.newBuilder()
                                                         .setTriggeredBy(TriggeredBy.newBuilder().setUuid(uuid).build())
                                                         .build())
                                     .build();
    when(planExecutionService.getExecutionMetadataFromPlanExecution(any())).thenReturn(metadata);
    assertThatCode(() -> approvalResourceService.addHarnessApprovalActivity(id, harnessApprovalActivityRequestDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("User not authorized to approve/reject");

    harnessApprovalInstance.setApprovalActivities(
        Collections.singletonList(HarnessApprovalActivity.builder().user(embeddedUser).build()));
    assertThatCode(() -> approvalResourceService.addHarnessApprovalActivity(id, harnessApprovalActivityRequestDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("User not authorized to approve/reject");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testAddHarnessApprovalActivityByPlanExecutionId() throws IOException {
    MockedStatic<NGRestUtils> aStatic = Mockito.mockStatic(NGRestUtils.class);
    String uuid = "uuid";
    String id = "dummy";
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", "accountId")
                            .putSetupAbstractions("orgIdentifier", "orgIdentifier")
                            .putSetupAbstractions("projectIdentifier", "projectIdentifier")
                            .build();
    List<String> userGroups = new ArrayList<>();
    userGroups.add("approver");
    HarnessApprovalInstance instance = HarnessApprovalInstance.builder()
                                           .approvalKey("approvalKey")
                                           .approvalMessage("message")
                                           .includePipelineExecutionHistory(false)
                                           .approvalActivities(Collections.emptyList())
                                           .isAutoRejectEnabled(false)
                                           .approvers(ApproversDTO.builder().userGroups(userGroups).build())
                                           .build();
    instance.setId(id);
    instance.setType(ApprovalType.HARNESS_APPROVAL);
    instance.setAmbiance(ambiance);
    List<ApprovalInstance> approvalInstances = Collections.singletonList(instance);
    when(approvalInstanceResponseMapper.toApprovalInstanceResponseDTO(any(), anyBoolean())).thenCallRealMethod();
    when(approvalInstanceService.getApprovalInstancesByExecutionId(any(), any(), any(), any(), any()))
        .thenReturn(approvalInstances);
    HarnessApprovalActivityRequestDTO harnessApprovalActivityRequestDTO =
        HarnessApprovalActivityRequestDTO.builder().build();
    when(approvalInstanceService.getHarnessApprovalInstance(id)).thenReturn(instance);
    List<UserGroupDTO> userGroupDTOS = Collections.singletonList(UserGroupDTO.builder().build());
    when(userGroupClient.getFilteredUserGroups(any())).thenReturn(null);
    aStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(userGroupDTOS);
    when(currentUserHelper.getPrincipalFromSecurityContext())
        .thenReturn(new UserPrincipal("email@harness.io", "name", "user", "ACCOUNTID"));
    Call userCall = mock(Call.class);
    when(userClient.getUserById("email@harness.io")).thenReturn(userCall);
    when(userCall.execute()).thenReturn(Response.success(new RestResponse(Optional.of(UserInfo.builder().build()))));
    ILogStreamingStepClient stepClient = Mockito.mock(ILogStreamingStepClient.class);
    when(logStreamingStepClientFactory.getLogStreamingStepClient(ambiance)).thenReturn(stepClient);
    // Should approve successfully
    approvalResourceService.addHarnessApprovalActivityByPlanExecutionId(
        ACCOUNT_ID, "ORG_ID", "PROJECT_ID", id, harnessApprovalActivityRequestDTO, null);

    instance.getApprovers().setUserGroups(Collections.emptyList());
    when(approvalInstanceService.getHarnessApprovalInstance(id)).thenReturn(instance);
    assertThatCode(()
                       -> approvalResourceService.addHarnessApprovalActivityByPlanExecutionId(
                           ACCOUNT_ID, "ORG_ID", "PROJECT_ID", id, harnessApprovalActivityRequestDTO, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("User not authorized to approve/reject");

    instance.getApprovers().setDisallowPipelineExecutor(true);
    when(approvalInstanceService.getHarnessApprovalInstance(id)).thenReturn(instance);
    ExecutionMetadata metadata = ExecutionMetadata.newBuilder()
                                     .setTriggerInfo(ExecutionTriggerInfo.newBuilder()
                                                         .setTriggeredBy(TriggeredBy.newBuilder().setUuid(uuid).build())
                                                         .build())
                                     .build();
    when(planExecutionService.getExecutionMetadataFromPlanExecution(any())).thenReturn(metadata);
    assertThatCode(()
                       -> approvalResourceService.addHarnessApprovalActivityByPlanExecutionId(
                           ACCOUNT_ID, "ORG_ID", "PROJECT_ID", id, harnessApprovalActivityRequestDTO, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("User not authorized to approve/reject");

    EmbeddedUser embeddedUser = EmbeddedUser.builder().email("email").name("name").uuid(uuid).build();
    instance.setApprovalActivities(
        Collections.singletonList(HarnessApprovalActivity.builder().user(embeddedUser).build()));
    when(approvalInstanceService.getHarnessApprovalInstance(id)).thenReturn(instance);
    assertThatCode(()
                       -> approvalResourceService.addHarnessApprovalActivityByPlanExecutionId(
                           ACCOUNT_ID, "ORG_ID", "PROJECT_ID", id, harnessApprovalActivityRequestDTO, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("User not authorized to approve/reject");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testAddHarnessApprovalActivityByPlanExecutionIdAndCallbackId() throws IOException {
    MockedStatic<NGRestUtils> aStatic = Mockito.mockStatic(NGRestUtils.class);
    String uuid = "uuid";
    String id = "dummy";
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", "accountId")
                            .putSetupAbstractions("orgIdentifier", "orgIdentifier")
                            .putSetupAbstractions("projectIdentifier", "projectIdentifier")
                            .build();
    List<String> userGroups = new ArrayList<>();
    userGroups.add("approver");
    HarnessApprovalInstance instance = HarnessApprovalInstance.builder()
                                           .callbackId("callbackId")
                                           .approvalKey("approvalKey")
                                           .approvalMessage("message")
                                           .includePipelineExecutionHistory(false)
                                           .approvalActivities(Collections.emptyList())
                                           .isAutoRejectEnabled(false)
                                           .approvers(ApproversDTO.builder().userGroups(userGroups).build())
                                           .build();
    instance.setId(id);
    instance.setType(ApprovalType.HARNESS_APPROVAL);
    instance.setAmbiance(ambiance);
    List<ApprovalInstance> approvalInstances = Collections.singletonList(instance);
    when(approvalInstanceResponseMapper.toApprovalInstanceResponseDTO(any(), anyBoolean())).thenCallRealMethod();
    when(approvalInstanceService.getApprovalInstancesByExecutionId(any(), any(), any(), any(), any()))
        .thenReturn(approvalInstances);
    HarnessApprovalActivityRequestDTO harnessApprovalActivityRequestDTO =
        HarnessApprovalActivityRequestDTO.builder().build();
    when(approvalInstanceService.getHarnessApprovalInstance(id)).thenReturn(instance);
    List<UserGroupDTO> userGroupDTOS = Collections.singletonList(UserGroupDTO.builder().build());
    when(userGroupClient.getFilteredUserGroups(any())).thenReturn(null);
    aStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(userGroupDTOS);
    when(currentUserHelper.getPrincipalFromSecurityContext())
        .thenReturn(new UserPrincipal("email@harness.io", "name", "user", "ACCOUNTID"));
    Call userCall = mock(Call.class);
    when(userClient.getUserById("email@harness.io")).thenReturn(userCall);
    when(userCall.execute()).thenReturn(Response.success(new RestResponse(Optional.of(UserInfo.builder().build()))));
    ILogStreamingStepClient stepClient = Mockito.mock(ILogStreamingStepClient.class);
    when(logStreamingStepClientFactory.getLogStreamingStepClient(ambiance)).thenReturn(stepClient);
    // Should approve successfully
    approvalResourceService.addHarnessApprovalActivityByPlanExecutionId(
        ACCOUNT_ID, "ORG_ID", "PROJECT_ID", id, harnessApprovalActivityRequestDTO, "callbackId");

    instance.getApprovers().setUserGroups(Collections.emptyList());
    when(approvalInstanceService.getHarnessApprovalInstance(id)).thenReturn(instance);
    assertThatCode(()
                       -> approvalResourceService.addHarnessApprovalActivityByPlanExecutionId(
                           ACCOUNT_ID, "ORG_ID", "PROJECT_ID", id, harnessApprovalActivityRequestDTO, "callbackId"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("User not authorized to approve/reject");

    instance.getApprovers().setDisallowPipelineExecutor(true);
    when(approvalInstanceService.getHarnessApprovalInstance(id)).thenReturn(instance);
    ExecutionMetadata metadata = ExecutionMetadata.newBuilder()
                                     .setTriggerInfo(ExecutionTriggerInfo.newBuilder()
                                                         .setTriggeredBy(TriggeredBy.newBuilder().setUuid(uuid).build())
                                                         .build())
                                     .build();
    when(planExecutionService.getExecutionMetadataFromPlanExecution(any())).thenReturn(metadata);
    assertThatCode(()
                       -> approvalResourceService.addHarnessApprovalActivityByPlanExecutionId(
                           ACCOUNT_ID, "ORG_ID", "PROJECT_ID", id, harnessApprovalActivityRequestDTO, "callbackId"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("User not authorized to approve/reject");

    EmbeddedUser embeddedUser = EmbeddedUser.builder().email("email").name("name").uuid(uuid).build();
    instance.setApprovalActivities(
        Collections.singletonList(HarnessApprovalActivity.builder().user(embeddedUser).build()));
    when(approvalInstanceService.getHarnessApprovalInstance(id)).thenReturn(instance);
    assertThatCode(()
                       -> approvalResourceService.addHarnessApprovalActivityByPlanExecutionId(
                           ACCOUNT_ID, "ORG_ID", "PROJECT_ID", id, harnessApprovalActivityRequestDTO, "callbackId"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("User not authorized to approve/reject");
  }

  @Test
  @Owner(developers = YAGYANSH)
  @Category(UnitTests.class)
  public void testTriggerAsyncApprovalRefresh_JiraInstance_ShouldRateLimit() {
    // Set up common test data
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", "accountId")
                            .putSetupAbstractions("orgIdentifier", "orgIdentifier")
                            .putSetupAbstractions("projectIdentifier", "projectIdentifier")
                            .build();

    // Mock log callback
    ILogStreamingStepClient logStreamingStepClient = mock(ILogStreamingStepClient.class);
    when(logStreamingStepClientFactory.getLogStreamingStepClient(ambiance)).thenReturn(logStreamingStepClient);

    // Current time for tests
    long currentTime = System.currentTimeMillis();

    // Create JiraApprovalInstance with only essential fields
    JiraApprovalInstance jiraApprovalInstance = JiraApprovalInstance.builder().build();
    jiraApprovalInstance.setId("jira-instance-id");
    jiraApprovalInstance.setType(ApprovalType.JIRA_APPROVAL);
    jiraApprovalInstance.setLastManualRunTimestamp(currentTime - 30000); // 30 seconds ago (within rate limit window)
    jiraApprovalInstance.setAmbiance(ambiance); // Set ambiance

    // Call the method under test
    approvalResourceService.triggerAsyncApprovalRefresh(jiraApprovalInstance);

    // Verify rate limiting behavior
    verify(approvalInstanceService, never()).resetNextIterations(anyString(), any());
    verify(approvalInstanceService, never()).updateLastManualRunTimestamp(anyString(), anyLong());
    verify(irregularApprovalInstanceHandler, never()).wakeup();
  }

  @Test
  @Owner(developers = YAGYANSH)
  @Category(UnitTests.class)
  public void testTriggerAsyncApprovalRefresh_ServiceNowInstance_ShouldSucceed() {
    // Set up common test data
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", "accountId")
                            .putSetupAbstractions("orgIdentifier", "orgIdentifier")
                            .putSetupAbstractions("projectIdentifier", "projectIdentifier")
                            .build();

    // Mock log callback
    ILogStreamingStepClient logStreamingStepClient = mock(ILogStreamingStepClient.class);
    when(logStreamingStepClientFactory.getLogStreamingStepClient(ambiance)).thenReturn(logStreamingStepClient);

    // Test: Successful refresh for ServiceNow approval instance (always refreshed)
    ServiceNowApprovalInstance servicenowApprovalInstance = ServiceNowApprovalInstance.builder().build();
    servicenowApprovalInstance.setId("servicenow-instance-id");
    servicenowApprovalInstance.setType(ApprovalType.SERVICENOW_APPROVAL);
    servicenowApprovalInstance.setLastManualRunTimestamp(null); // No previous manual run
    servicenowApprovalInstance.setAmbiance(ambiance); // Set ambiance
    servicenowApprovalInstance.setRetryInterval(ParameterField.createValueField(Timeout.fromString("30s")));

    // For the new implementation testing with recalculateNextIterations
    when(approvalInstanceService.get("servicenow-instance-id")).thenReturn(servicenowApprovalInstance);

    // Call the method under test
    approvalResourceService.triggerAsyncApprovalRefresh(servicenowApprovalInstance);

    // Verify successful refresh behavior with ArgumentCaptor instead of exact values
    ArgumentCaptor<List<Long>> iterationsCaptor = ArgumentCaptor.forClass(List.class);
    verify(approvalInstanceService).resetNextIterations(eq("servicenow-instance-id"), iterationsCaptor.capture());
    verify(approvalInstanceService).updateLastManualRunTimestamp(eq("servicenow-instance-id"), anyLong());
    verify(irregularApprovalInstanceHandler).wakeup();

    // Verify the structure of the iterations list
    List<Long> capturedIterations = iterationsCaptor.getValue();
    assertThat(capturedIterations).isNotNull();
    assertThat(capturedIterations.size()).isEqualTo(10);
  }

  @Test
  @Owner(developers = YAGYANSH)
  @Category(UnitTests.class)
  public void testTriggerAsyncApprovalRefresh_CustomInstance_OutsideRateLimit_ShouldSucceed() {
    // Set up common test data
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", "accountId")
                            .putSetupAbstractions("orgIdentifier", "orgIdentifier")
                            .putSetupAbstractions("projectIdentifier", "projectIdentifier")
                            .build();

    // Mock log callback
    ILogStreamingStepClient logStreamingStepClient = mock(ILogStreamingStepClient.class);
    when(logStreamingStepClientFactory.getLogStreamingStepClient(ambiance)).thenReturn(logStreamingStepClient);

    long currentTime = System.currentTimeMillis();

    // Test: Successful refresh for Custom approval instance with timestamp older than rate limit
    CustomApprovalInstance customApprovalInstance = CustomApprovalInstance.builder().build();
    customApprovalInstance.setId("custom-instance-id");
    customApprovalInstance.setType(ApprovalType.CUSTOM_APPROVAL);
    customApprovalInstance.setLastManualRunTimestamp(currentTime - 3600000); // Over an hour ago (outside rate limit)
    customApprovalInstance.setAmbiance(ambiance); // Set ambiance
    customApprovalInstance.setRetryInterval(ParameterField.createValueField(Timeout.fromString("30s")));
    customApprovalInstance.setScriptTimeout(ParameterField.createValueField(Timeout.fromString("60s")));

    // For the new implementation testing with recalculateNextIterations
    when(approvalInstanceService.get("custom-instance-id")).thenReturn(customApprovalInstance);

    // Call the method under test
    approvalResourceService.triggerAsyncApprovalRefresh(customApprovalInstance);

    // Verify successful refresh behavior with ArgumentCaptor instead of exact values
    ArgumentCaptor<List<Long>> iterationsCaptor = ArgumentCaptor.forClass(List.class);
    verify(approvalInstanceService).resetNextIterations(eq("custom-instance-id"), iterationsCaptor.capture());
    verify(approvalInstanceService).updateLastManualRunTimestamp(eq("custom-instance-id"), anyLong());
    verify(irregularApprovalInstanceHandler).wakeup();

    // Verify the structure of the iterations list
    List<Long> capturedIterations = iterationsCaptor.getValue();
    assertThat(capturedIterations).isNotNull();
    assertThat(capturedIterations.size()).isEqualTo(10);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testSnippetWithServiceNowCreateUpdate() throws IOException {
    String yaml = approvalResourceService.getYamlSnippet(ApprovalType.SERVICENOW_APPROVAL, "accountId");
    assertThat(yaml.contains(EntityTypeConstants.SERVICENOW_CREATE)).isTrue();
    assertThat(yaml.contains(EntityTypeConstants.SERVICENOW_UPDATE)).isTrue();
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testRejectPreviousExecutions() throws IOException {
    MockedStatic<NGRestUtils> aStatic = Mockito.mockStatic(NGRestUtils.class);
    EmbeddedUser embeddedUser = EmbeddedUser.builder().email("email").name("name").uuid("uuid").build();
    List<String> userGroups = new ArrayList<>();
    userGroups.add("approver");
    String accountId = "accountId";
    String orgId = "orgId";
    String projId = "projectId";
    String pipelineId = "pipelineId";
    Ambiance ambiance1 = Ambiance.newBuilder()
                             .setPlanExecutionId("planId1")
                             .putSetupAbstractions("accountId", accountId)
                             .putSetupAbstractions("orgIdentifier", orgId)
                             .putSetupAbstractions("projectIdentifier", projId)
                             .build();
    Ambiance ambiance2 = Ambiance.newBuilder()
                             .setPlanExecutionId("planId2")
                             .putSetupAbstractions("accountId", accountId)
                             .putSetupAbstractions("orgIdentifier", orgId)
                             .putSetupAbstractions("projectIdentifier", projId)
                             .build();
    HarnessApprovalInstance newInstance = HarnessApprovalInstance.builder()
                                              .approvalKey("approvalKey")
                                              .approvalMessage("message")
                                              .includePipelineExecutionHistory(false)
                                              .approvers(ApproversDTO.builder().userGroups(userGroups).build())
                                              .approvalActivities(Collections.emptyList())
                                              .isAutoRejectEnabled(true)
                                              .build();
    newInstance.setId("uuid1");
    newInstance.setAccountId(accountId);
    newInstance.setOrgIdentifier(orgId);
    newInstance.setProjectIdentifier(projId);
    newInstance.setPipelineIdentifier(pipelineId);
    newInstance.setAmbiance(ambiance1);
    newInstance.setCreatedAt(CREATED_AT);

    HarnessApprovalInstance oldInstance =
        HarnessApprovalInstance.builder()
            .approvalKey("approvalKey")
            .approvalMessage("message")
            .includePipelineExecutionHistory(false)
            .approvers(ApproversDTO.builder().userGroups(userGroups).build())
            .approvalActivities(Collections.singletonList(
                HarnessApprovalActivity.builder().action(HarnessApprovalAction.APPROVE).user(embeddedUser).build()))
            .isAutoRejectEnabled(true)
            .build();
    oldInstance.setId("uuid2");
    oldInstance.setAccountId(accountId);
    oldInstance.setOrgIdentifier(orgId);
    oldInstance.setProjectIdentifier(projId);
    oldInstance.setPipelineIdentifier(pipelineId);
    oldInstance.setAmbiance(ambiance2);
    List<UserGroupDTO> userGroupDTOS = Collections.singletonList(UserGroupDTO.builder().build());
    when(userGroupClient.getFilteredUserGroups(any())).thenReturn(null);
    aStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(userGroupDTOS);
    when(currentUserHelper.getPrincipalFromSecurityContext())
        .thenReturn(new UserPrincipal("email@harness.io", "name", "user", "ACCOUNTID"));
    Call userCall = mock(Call.class);
    when(userClient.getUserById("email@harness.io")).thenReturn(userCall);
    when(userCall.execute()).thenReturn(Response.success(new RestResponse(Optional.of(UserInfo.builder().build()))));
    List<String> approvalInstanceIds = Collections.singletonList("uuid2");
    when(approvalInstanceService.findAllPreviousWaitingApprovals(
             accountId, orgId, projId, pipelineId, "approvalKey", ambiance1, CREATED_AT))
        .thenReturn(approvalInstanceIds);
    when(approvalInstanceService.getHarnessApprovalInstance("uuid2")).thenReturn(oldInstance);
    ILogStreamingStepClient stepClient = Mockito.mock(ILogStreamingStepClient.class);
    when(logStreamingStepClientFactory.getLogStreamingStepClient(ambiance1)).thenReturn(stepClient);
    ArgumentCaptor<LogLine> logLineArgumentCaptor = ArgumentCaptor.forClass(LogLine.class);
    ArgumentCaptor<Boolean> booleanArgumentCaptor = ArgumentCaptor.forClass(Boolean.class);
    ArgumentCaptor<String> stringArgumentCaptor = ArgumentCaptor.forClass(String.class);
    doNothing().when(approvalInstanceService).rejectPreviousExecutions(anyString(), any(), anyBoolean(), any());
    approvalResourceService.rejectPreviousExecutions(newInstance);
    verify(approvalInstanceService, times(1))
        .rejectPreviousExecutions(stringArgumentCaptor.capture(), any(), booleanArgumentCaptor.capture(), any());
    verify(stepClient, times(1)).writeLogLine(logLineArgumentCaptor.capture(), anyString());
    assertThat(stringArgumentCaptor.getValue()).isEqualTo("uuid2");
    assertThat(booleanArgumentCaptor.getValue()).isFalse();
    oldInstance.getApprovers().setUserGroups(Collections.emptyList());
    ArgumentCaptor<Boolean> booleanArgumentCaptor2 = ArgumentCaptor.forClass(Boolean.class);
    ArgumentCaptor<String> stringArgumentCaptor2 = ArgumentCaptor.forClass(String.class);
    approvalResourceService.rejectPreviousExecutions(newInstance);
    verify(approvalInstanceService, times(2))
        .rejectPreviousExecutions(stringArgumentCaptor2.capture(), any(), booleanArgumentCaptor2.capture(), any());
    assertThat(stringArgumentCaptor2.getValue()).isEqualTo("uuid2");
    assertThat(booleanArgumentCaptor2.getValue()).isTrue();
    assertThat(logLineArgumentCaptor.getValue().getMessage())
        .isEqualTo("Successfully rejected 1 previous executions waiting for approval on this step that the user was "
            + "authorized to reject");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetApprovalInstancesByExecutionIdForNormalExecution() {
    HarnessApprovalInstance instance = HarnessApprovalInstance.builder()
                                           .approvalKey("approvalKey")
                                           .approvalMessage("message")
                                           .includePipelineExecutionHistory(false)
                                           .approvalActivities(Collections.emptyList())
                                           .isAutoRejectEnabled(false)
                                           .build();
    instance.setId("uuid1");
    instance.setType(ApprovalType.HARNESS_APPROVAL);
    List<ApprovalInstance> approvalInstances = Collections.singletonList(instance);

    when(approvalInstanceService.getApprovalInstancesByExecutionId(any(), any(), any(), any(), any()))
        .thenReturn(approvalInstances);

    assertThat(approvalResourceService.getApprovalInstancesByExecutionId("planExecutionId", ApprovalStatus.APPROVED,
                   ApprovalType.HARNESS_APPROVAL, "nodeExecutionId", null, false))
        .isEqualTo(
            Collections.singletonList(approvalInstanceResponseMapper.toApprovalInstanceResponseDTO(instance, false)));
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetApprovalInstancesByExecutionIdForRetryExecution() {
    HarnessApprovalInstance instance = HarnessApprovalInstance.builder()
                                           .approvalKey("approvalKey")
                                           .approvalMessage("message")
                                           .includePipelineExecutionHistory(false)
                                           .approvalActivities(Collections.emptyList())
                                           .isAutoRejectEnabled(false)
                                           .build();
    instance.setId("uuid1");
    instance.setType(ApprovalType.HARNESS_APPROVAL);
    List<ApprovalInstance> approvalInstances = Collections.singletonList(instance);
    List<String> approvalInstanceIds = Arrays.asList("uuid1");

    when(nodeExecutionService.fetchListOfApprovalInstanceIdsForPlanExecutionId(any())).thenReturn(approvalInstanceIds);
    when(approvalInstanceService.getApprovalInstancesByApprovalInstanceIds(any(), any(), any(), any(), any(), any()))
        .thenReturn(approvalInstances);

    assertThat(approvalResourceService.getApprovalInstancesByExecutionId("planExecutionId", ApprovalStatus.APPROVED,
                   ApprovalType.HARNESS_APPROVAL, "nodeExecutionId", null, true))
        .isEqualTo(
            Collections.singletonList(approvalInstanceResponseMapper.toApprovalInstanceResponseDTO(instance, false)));
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testAddHarnessApprovalActivityWithServiceAccounts() throws IOException {
    MockedStatic<NGRestUtils> aStatic = Mockito.mockStatic(NGRestUtils.class);
    String uuid = "uuid";
    String id = "dummy";
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", "accountId")
                            .putSetupAbstractions("orgIdentifier", "orgIdentifier")
                            .putSetupAbstractions("projectIdentifier", "projectIdentifier")
                            .build();
    EmbeddedServiceAccount embeddedServiceAccount =
        EmbeddedServiceAccount.builder().email("email").name("name").uuid(uuid).build();
    List<String> serviceAccounts = new ArrayList<>();
    serviceAccounts.add("account.id");
    HarnessApprovalInstance harnessApprovalInstance =
        HarnessApprovalInstance.builder()
            .approvers(ApproversDTO.builder().serviceAccounts(serviceAccounts).build())
            .build();
    harnessApprovalInstance.setAmbiance(ambiance);
    HarnessApprovalActivityRequestDTO harnessApprovalActivityRequestDTO =
        HarnessApprovalActivityRequestDTO.builder().build();
    when(approvalInstanceService.getHarnessApprovalInstance(id)).thenReturn(harnessApprovalInstance);
    when(currentUserHelper.getPrincipalFromSecurityContext())
        .thenReturn(new ServiceAccountPrincipal("name", "email", "name", "ACCOUNTID", "uuid"));
    List<ServiceAccountDTOInternal> serviceAccountDTOs =
        Collections.singletonList((ServiceAccountDTOInternal) ServiceAccountDTOInternal.builder()
                                      .email("email")
                                      .name("name")
                                      .identifier("id")
                                      .accountIdentifier("ACCOUNTID")
                                      .build());
    when(serviceAccountClient.listServiceAccountsByUniqueIdInternal(any(), any())).thenReturn(null);
    aStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(serviceAccountDTOs);
    // Should approve successfully
    approvalResourceService.addHarnessApprovalActivity(id, harnessApprovalActivityRequestDTO);

    harnessApprovalInstance.getApprovers().setServiceAccounts(Collections.emptyList());
    assertThatCode(() -> approvalResourceService.addHarnessApprovalActivity(id, harnessApprovalActivityRequestDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("The service account configured in the request is not authorized to approve/reject");

    harnessApprovalInstance.getApprovers().setDisallowPipelineExecutor(true);
    ExecutionMetadata metadata = ExecutionMetadata.newBuilder()
                                     .setTriggerInfo(ExecutionTriggerInfo.newBuilder()
                                                         .setTriggeredBy(TriggeredBy.newBuilder().setUuid(uuid).build())
                                                         .build())
                                     .build();
    when(planExecutionService.getExecutionMetadataFromPlanExecution(any())).thenReturn(metadata);
    assertThatCode(() -> approvalResourceService.addHarnessApprovalActivity(id, harnessApprovalActivityRequestDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("The service account configured in the request is not authorized to approve/reject");

    harnessApprovalInstance.setApprovalActivities(
        Collections.singletonList(HarnessApprovalActivity.builder().user(embeddedServiceAccount).build()));
    assertThatCode(() -> approvalResourceService.addHarnessApprovalActivity(id, harnessApprovalActivityRequestDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("The service account configured in the request is not authorized to approve/reject");
  }

  @Test
  @Owner(developers = SHREYAS_NAGARAJ)
  @Category(UnitTests.class)
  public void testGracefulHandlingWhenInstanceIsNull() {
    assertThat(approvalResourceService.isUserDisallowedFromApproving(null, TEST_EMBEDDED_USER)).isFalse();
  }

  @Test
  @Owner(developers = SHREYAS_NAGARAJ)
  @Category(UnitTests.class)
  public void testGracefulHandlingWhenApproversIsNull() {
    HarnessApprovalInstance instance = HarnessApprovalInstance.builder().approvers(null).build();
    assertThat(approvalResourceService.isUserDisallowedFromApproving(instance, TEST_EMBEDDED_USER)).isFalse();
  }

  @Test
  @Owner(developers = SHREYAS_NAGARAJ)
  @Category(UnitTests.class)
  public void testGracefulHandlingWhenDisallowedEmailsIsNull() {
    HarnessApprovalInstance instance =
        HarnessApprovalInstance.builder()
            .approvers(ApproversDTO.builder().userGroups(TEST_USER_GROUPS).disallowedUserEmails(null).build())
            .build();
    assertThat(approvalResourceService.isUserDisallowedFromApproving(instance, TEST_EMBEDDED_USER)).isFalse();
  }

  @Test
  @Owner(developers = SHREYAS_NAGARAJ)
  @Category(UnitTests.class)
  public void testUserIsAllowedToApproveDisallowedEmailsIsAnEmptyList() {
    HarnessApprovalInstance instance =
        HarnessApprovalInstance.builder()
            .approvers(ApproversDTO.builder().userGroups(TEST_USER_GROUPS).disallowedUserEmails(List.of()).build())
            .build();
    assertThat(approvalResourceService.isUserDisallowedFromApproving(instance, TEST_EMBEDDED_USER)).isFalse();
  }

  @Test
  @Owner(developers = SHREYAS_NAGARAJ)
  @Category(UnitTests.class)
  public void testUserIsAllowedToApproveWhenNotPartOfDisallowed() {
    HarnessApprovalInstance instance = HarnessApprovalInstance.builder()
                                           .approvers(ApproversDTO.builder()
                                                          .userGroups(TEST_USER_GROUPS)
                                                          .disallowedUserEmails(List.of("disallowedEmail@harness.io"))
                                                          .build())
                                           .build();
    assertThat(approvalResourceService.isUserDisallowedFromApproving(instance, TEST_EMBEDDED_USER)).isFalse();
  }

  @Test
  @Owner(developers = SHREYAS_NAGARAJ)
  @Category(UnitTests.class)
  public void testUserIsNotAllowedToApproveWhenPartOfDisallowed() {
    HarnessApprovalInstance instance = HarnessApprovalInstance.builder()
                                           .approvers(ApproversDTO.builder()
                                                          .userGroups(TEST_USER_GROUPS)
                                                          .disallowedUserEmails(List.of("testUser@gmail.com"))
                                                          .build())
                                           .build();
    assertThat(approvalResourceService.isUserDisallowedFromApproving(instance, TEST_EMBEDDED_USER)).isTrue();
  }

  @Test
  @Owner(developers = SHREYAS_NAGARAJ)
  @Category(UnitTests.class)
  public void testUserIsNotAllowedToApproveWhenInputEmailDiffersInCase() {
    HarnessApprovalInstance instance = HarnessApprovalInstance.builder()
                                           .approvers(ApproversDTO.builder()
                                                          .userGroups(TEST_USER_GROUPS)
                                                          .disallowedUserEmails(List.of("TESTUSER@gmail.COM"))
                                                          .build())
                                           .build();
    assertThat(approvalResourceService.isUserDisallowedFromApproving(instance, TEST_EMBEDDED_USER)).isTrue();
  }

  @Test
  @Owner(developers = SHREYAS_NAGARAJ)
  @Category(UnitTests.class)
  public void testServiceAccountIsNotAllowedToApproveWhenPartOfDisallowed() {
    HarnessApprovalInstance instance = HarnessApprovalInstance.builder()
                                           .approvers(ApproversDTO.builder()
                                                          .userGroups(TEST_USER_GROUPS)
                                                          .disallowedUserEmails(List.of("testservice@harness.io"))
                                                          .build())
                                           .build();

    EmbeddedServiceAccount testServiceAccount = new EmbeddedServiceAccount();
    testServiceAccount.setEmail("testservice@harness.io");
    testServiceAccount.setServiceAccountIdentifier("service-account-id");
    testServiceAccount.setAccountIdentifier("account-id");
    testServiceAccount.setOrgIdentifier("org-id");
    testServiceAccount.setProjectIdentifier("project-id");

    assertThat(approvalResourceService.isUserDisallowedFromApproving(instance, testServiceAccount)).isTrue();
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testListPendingApprovalsReturnsOnlyAuthorizedApprovals() throws IOException {
    try (MockedStatic<NGRestUtils> aStatic = Mockito.mockStatic(NGRestUtils.class)) {
      Ambiance ambiance = Ambiance.newBuilder()
                              .putSetupAbstractions("accountId", ACCOUNT_ID)
                              .putSetupAbstractions("orgIdentifier", "orgId")
                              .putSetupAbstractions("projectIdentifier", "projectId")
                              .build();

      List<String> userGroups = new ArrayList<>();
      userGroups.add("approver");

      // Instance 1: authorized (user is in user group)
      HarnessApprovalInstance authorizedInstance = HarnessApprovalInstance.builder()
                                                       .approvers(ApproversDTO.builder().userGroups(userGroups).build())
                                                       .approvalMessage("Please approve")
                                                       .build();
      authorizedInstance.setId("authorized-id");
      authorizedInstance.setAccountId(ACCOUNT_ID);
      authorizedInstance.setOrgIdentifier("orgId");
      authorizedInstance.setProjectIdentifier("projectId");
      authorizedInstance.setPipelineIdentifier("pipeline1");
      authorizedInstance.setPlanExecutionId("exec1");
      authorizedInstance.setType(ApprovalType.HARNESS_APPROVAL);
      authorizedInstance.setStatus(ApprovalStatus.WAITING);
      authorizedInstance.setAmbiance(ambiance);

      // Instance 2: unauthorized (empty user groups)
      HarnessApprovalInstance unauthorizedInstance =
          HarnessApprovalInstance.builder()
              .approvers(ApproversDTO.builder().userGroups(Collections.emptyList()).build())
              .build();
      unauthorizedInstance.setId("unauthorized-id");
      unauthorizedInstance.setAccountId(ACCOUNT_ID);
      unauthorizedInstance.setType(ApprovalType.HARNESS_APPROVAL);
      unauthorizedInstance.setStatus(ApprovalStatus.WAITING);
      unauthorizedInstance.setAmbiance(ambiance);

      List<ApprovalInstance> allInstances = Arrays.asList(authorizedInstance, unauthorizedInstance);
      when(approvalInstanceService.getWaitingApprovalsByAccountAndType(ACCOUNT_ID, ApprovalType.HARNESS_APPROVAL, 50))
          .thenReturn(allInstances);
      when(currentUserHelper.getPrincipalFromSecurityContext())
          .thenReturn(new UserPrincipal("email@harness.io", "name", "user", ACCOUNT_ID));
      Call userCall = mock(Call.class);
      when(userClient.getUserById("email@harness.io")).thenReturn(userCall);
      when(userCall.execute())
          .thenReturn(Response.success(new RestResponse(Optional.of(UserInfo.builder().uuid("uuid").build()))));
      List<UserGroupDTO> userGroupDTOS = Collections.singletonList(UserGroupDTO.builder().build());
      when(userGroupClient.getFilteredUserGroups(any())).thenReturn(null);
      aStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(userGroupDTOS);

      PageResponse<PendingApprovalSummaryDTO> result = approvalResourceService.listPendingApprovals(ACCOUNT_ID, 50);

      assertThat(result.getTotalItems()).isEqualTo(1);
      assertThat(result.getContent()).hasSize(1);
      assertThat(result.getContent().get(0).getId()).isEqualTo("authorized-id");
      assertThat(result.getContent().get(0).getApprovalMessage()).isEqualTo("Please approve");
    }
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testListPendingApprovalsDefaultsToHarnessApproval() throws IOException {
    try (MockedStatic<NGRestUtils> aStatic = Mockito.mockStatic(NGRestUtils.class)) {
      when(approvalInstanceService.getWaitingApprovalsByAccountAndType(ACCOUNT_ID, ApprovalType.HARNESS_APPROVAL, 50))
          .thenReturn(Collections.emptyList());
      when(currentUserHelper.getPrincipalFromSecurityContext())
          .thenReturn(new UserPrincipal("email@harness.io", "name", "user", ACCOUNT_ID));
      Call userCall = mock(Call.class);
      when(userClient.getUserById("email@harness.io")).thenReturn(userCall);
      when(userCall.execute())
          .thenReturn(Response.success(new RestResponse(Optional.of(UserInfo.builder().uuid("uuid").build()))));
      aStatic.when(() -> NGRestUtils.getResponse(any()))
          .thenReturn(Optional.of(UserInfo.builder().uuid("uuid").build()));

      PageResponse<PendingApprovalSummaryDTO> result = approvalResourceService.listPendingApprovals(ACCOUNT_ID, 50);

      verify(approvalInstanceService)
          .getWaitingApprovalsByAccountAndType(ACCOUNT_ID, ApprovalType.HARNESS_APPROVAL, 50);
      assertThat(result.isEmpty()).isTrue();
      assertThat(result.getTotalItems()).isEqualTo(0);
    }
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testListPendingApprovalsRespectsLimit() throws IOException {
    try (MockedStatic<NGRestUtils> aStatic = Mockito.mockStatic(NGRestUtils.class)) {
      Ambiance ambiance = Ambiance.newBuilder()
                              .putSetupAbstractions("accountId", ACCOUNT_ID)
                              .putSetupAbstractions("orgIdentifier", "orgId")
                              .putSetupAbstractions("projectIdentifier", "projectId")
                              .build();
      List<String> userGroups = Collections.singletonList("approver");

      List<ApprovalInstance> instances = new ArrayList<>();
      for (int i = 0; i < 5; i++) {
        HarnessApprovalInstance instance =
            HarnessApprovalInstance.builder().approvers(ApproversDTO.builder().userGroups(userGroups).build()).build();
        instance.setId("id-" + i);
        instance.setAccountId(ACCOUNT_ID);
        instance.setOrgIdentifier("orgId");
        instance.setProjectIdentifier("projectId");
        instance.setPipelineIdentifier("pipeline");
        instance.setPlanExecutionId("exec");
        instance.setType(ApprovalType.HARNESS_APPROVAL);
        instance.setStatus(ApprovalStatus.WAITING);
        instance.setAmbiance(ambiance);
        instances.add(instance);
      }

      when(approvalInstanceService.getWaitingApprovalsByAccountAndType(ACCOUNT_ID, ApprovalType.HARNESS_APPROVAL, 2))
          .thenReturn(instances.subList(0, 2));
      when(currentUserHelper.getPrincipalFromSecurityContext())
          .thenReturn(new UserPrincipal("email@harness.io", "name", "user", ACCOUNT_ID));
      Call userCall = mock(Call.class);
      when(userClient.getUserById("email@harness.io")).thenReturn(userCall);
      when(userCall.execute())
          .thenReturn(Response.success(new RestResponse(Optional.of(UserInfo.builder().uuid("uuid").build()))));
      List<UserGroupDTO> userGroupDTOS = Collections.singletonList(UserGroupDTO.builder().build());
      when(userGroupClient.getFilteredUserGroups(any())).thenReturn(null);
      aStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(userGroupDTOS);

      PageResponse<PendingApprovalSummaryDTO> result = approvalResourceService.listPendingApprovals(ACCOUNT_ID, 2);
      assertThat(result.getContent()).hasSize(2);
      assertThat(result.getTotalItems()).isEqualTo(2);
    }
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testListPendingApprovalsEmptyResults() throws IOException {
    try (MockedStatic<NGRestUtils> aStatic = Mockito.mockStatic(NGRestUtils.class)) {
      when(approvalInstanceService.getWaitingApprovalsByAccountAndType(ACCOUNT_ID, ApprovalType.HARNESS_APPROVAL, 50))
          .thenReturn(Collections.emptyList());
      when(currentUserHelper.getPrincipalFromSecurityContext())
          .thenReturn(new UserPrincipal("email@harness.io", "name", "user", ACCOUNT_ID));
      Call userCall = mock(Call.class);
      when(userClient.getUserById("email@harness.io")).thenReturn(userCall);
      when(userCall.execute())
          .thenReturn(Response.success(new RestResponse(Optional.of(UserInfo.builder().uuid("uuid").build()))));
      aStatic.when(() -> NGRestUtils.getResponse(any()))
          .thenReturn(Optional.of(UserInfo.builder().uuid("uuid").build()));

      PageResponse<PendingApprovalSummaryDTO> result = approvalResourceService.listPendingApprovals(ACCOUNT_ID, 50);

      assertThat(result.isEmpty()).isTrue();
      assertThat(result.getContent()).isEmpty();
      assertThat(result.getTotalItems()).isEqualTo(0);
    }
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testListPendingApprovalsFiltersAlreadyApprovedAndDisallowedUsers() throws IOException {
    try (MockedStatic<NGRestUtils> aStatic = Mockito.mockStatic(NGRestUtils.class)) {
      String userUuid = "test-uuid";
      Ambiance ambiance = Ambiance.newBuilder()
                              .putSetupAbstractions("accountId", ACCOUNT_ID)
                              .putSetupAbstractions("orgIdentifier", "orgId")
                              .putSetupAbstractions("projectIdentifier", "projectId")
                              .build();
      List<String> userGroups = Collections.singletonList("approver");
      EmbeddedUser embeddedUser = EmbeddedUser.builder().uuid(userUuid).name("Test").email("test@test.com").build();

      // Instance 1: already approved by user
      HarnessApprovalInstance alreadyApprovedInstance =
          HarnessApprovalInstance.builder()
              .approvers(ApproversDTO.builder().userGroups(userGroups).build())
              .approvalActivities(
                  Collections.singletonList(HarnessApprovalActivity.builder().user(embeddedUser).build()))
              .build();
      alreadyApprovedInstance.setId("already-approved");
      alreadyApprovedInstance.setAccountId(ACCOUNT_ID);
      alreadyApprovedInstance.setType(ApprovalType.HARNESS_APPROVAL);
      alreadyApprovedInstance.setStatus(ApprovalStatus.WAITING);
      alreadyApprovedInstance.setAmbiance(ambiance);

      // Instance 2: user is in disallowed list
      HarnessApprovalInstance disallowedInstance =
          HarnessApprovalInstance.builder()
              .approvers(ApproversDTO.builder()
                             .userGroups(userGroups)
                             .disallowedUserEmails(Collections.singletonList("test@test.com"))
                             .build())
              .build();
      disallowedInstance.setId("disallowed");
      disallowedInstance.setAccountId(ACCOUNT_ID);
      disallowedInstance.setType(ApprovalType.HARNESS_APPROVAL);
      disallowedInstance.setStatus(ApprovalStatus.WAITING);
      disallowedInstance.setAmbiance(ambiance);

      // Instance 3: user is the pipeline executor
      HarnessApprovalInstance executorInstance =
          HarnessApprovalInstance.builder()
              .approvers(ApproversDTO.builder().userGroups(userGroups).disallowPipelineExecutor(true).build())
              .build();
      executorInstance.setId("executor-disallowed");
      executorInstance.setAccountId(ACCOUNT_ID);
      executorInstance.setType(ApprovalType.HARNESS_APPROVAL);
      executorInstance.setStatus(ApprovalStatus.WAITING);
      executorInstance.setAmbiance(ambiance);

      ExecutionMetadata metadata =
          ExecutionMetadata.newBuilder()
              .setTriggerInfo(ExecutionTriggerInfo.newBuilder()
                                  .setTriggeredBy(TriggeredBy.newBuilder().setUuid(userUuid).build())
                                  .build())
              .build();
      when(planExecutionService.getExecutionMetadataFromPlanExecution(any())).thenReturn(metadata);

      List<ApprovalInstance> allInstances =
          Arrays.asList(alreadyApprovedInstance, disallowedInstance, executorInstance);
      when(approvalInstanceService.getWaitingApprovalsByAccountAndType(ACCOUNT_ID, ApprovalType.HARNESS_APPROVAL, 50))
          .thenReturn(allInstances);
      when(currentUserHelper.getPrincipalFromSecurityContext())
          .thenReturn(new UserPrincipal("test@test.com", "Test", "user", ACCOUNT_ID));
      Call userCall = mock(Call.class);
      when(userClient.getUserById("test@test.com")).thenReturn(userCall);
      when(userCall.execute())
          .thenReturn(Response.success(new RestResponse(
              Optional.of(UserInfo.builder().uuid(userUuid).name("Test").email("test@test.com").build()))));
      aStatic.when(() -> NGRestUtils.getResponse(any()))
          .thenReturn(Optional.of(UserInfo.builder().uuid(userUuid).name("Test").email("test@test.com").build()));

      PageResponse<PendingApprovalSummaryDTO> result = approvalResourceService.listPendingApprovals(ACCOUNT_ID, 50);

      assertThat(result.getTotalItems()).isEqualTo(0);
      assertThat(result.getContent()).isEmpty();
    }
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testApprovedByCurrentUserIsTrueWhenCurrentUserApproved() throws IOException {
    try (MockedStatic<CGRestUtils> cgStatic = Mockito.mockStatic(CGRestUtils.class)) {
      String approvalInstanceId = "approval-instance-id";
      String currentUserUuid = "current-user-uuid";
      setupCurrentUserForApprovedByCurrentUser(cgStatic, currentUserUuid);

      EmbeddedUser currentUser =
          EmbeddedUser.builder().uuid(currentUserUuid).name("Test User").email("test@test.com").build();
      HarnessApprovalInstanceDetailsDTO harnessDetails =
          HarnessApprovalInstanceDetailsDTO.builder()
              .approvers(ApproversDTO.builder().userGroups(List.of("approver")).build())
              .approvalActivities(List.of(
                  HarnessApprovalActivity.builder().user(currentUser).action(HarnessApprovalAction.APPROVE).build()))
              .build();
      ApprovalInstanceResponseDTO responseDTO = ApprovalInstanceResponseDTO.builder()
                                                    .id(approvalInstanceId)
                                                    .type(ApprovalType.HARNESS_APPROVAL)
                                                    .status(ApprovalStatus.APPROVED)
                                                    .details(harnessDetails)
                                                    .build();
      HarnessApprovalInstance approvalInstance = buildHarnessApprovalInstanceForGet(approvalInstanceId);
      when(approvalInstanceService.fetchFromObjectStoreWithFallback(ACCOUNT_ID, approvalInstanceId))
          .thenReturn(approvalInstance);
      doReturn(responseDTO).when(approvalInstanceResponseMapper).toApprovalInstanceResponseDTO(approvalInstance, true);

      ApprovalInstanceResponseDTO result = approvalResourceService.get(approvalInstanceId, ACCOUNT_ID);

      assertThat(result.getApprovedByCurrentUser()).isTrue();
      assertThat(result.getRejectedByCurrentUser()).isFalse();
    }
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testRejectedByCurrentUserIsTrueWhenCurrentUserRejected() throws IOException {
    try (MockedStatic<CGRestUtils> cgStatic = Mockito.mockStatic(CGRestUtils.class)) {
      String approvalInstanceId = "approval-instance-id";
      String currentUserUuid = "current-user-uuid";
      setupCurrentUserForApprovedByCurrentUser(cgStatic, currentUserUuid);

      EmbeddedUser currentUser =
          EmbeddedUser.builder().uuid(currentUserUuid).name("Test User").email("test@test.com").build();
      HarnessApprovalInstanceDetailsDTO harnessDetails =
          HarnessApprovalInstanceDetailsDTO.builder()
              .approvers(ApproversDTO.builder().userGroups(List.of("approver")).build())
              .approvalActivities(List.of(
                  HarnessApprovalActivity.builder().user(currentUser).action(HarnessApprovalAction.REJECT).build()))
              .build();
      ApprovalInstanceResponseDTO responseDTO = ApprovalInstanceResponseDTO.builder()
                                                    .id(approvalInstanceId)
                                                    .type(ApprovalType.HARNESS_APPROVAL)
                                                    .status(ApprovalStatus.REJECTED)
                                                    .details(harnessDetails)
                                                    .build();
      HarnessApprovalInstance approvalInstance = buildHarnessApprovalInstanceForGet(approvalInstanceId);
      when(approvalInstanceService.fetchFromObjectStoreWithFallback(ACCOUNT_ID, approvalInstanceId))
          .thenReturn(approvalInstance);
      doReturn(responseDTO).when(approvalInstanceResponseMapper).toApprovalInstanceResponseDTO(approvalInstance, true);

      ApprovalInstanceResponseDTO result = approvalResourceService.get(approvalInstanceId, ACCOUNT_ID);

      assertThat(result.getRejectedByCurrentUser()).isTrue();
      assertThat(result.getApprovedByCurrentUser()).isFalse();
    }
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testRejectedByCurrentUserIsFalseWhenAnotherUserRejected() throws IOException {
    try (MockedStatic<CGRestUtils> cgStatic = Mockito.mockStatic(CGRestUtils.class)) {
      String approvalInstanceId = "approval-instance-id";
      String currentUserUuid = "current-user-uuid";
      setupCurrentUserForApprovedByCurrentUser(cgStatic, currentUserUuid);

      HarnessApprovalInstanceDetailsDTO harnessDetails =
          HarnessApprovalInstanceDetailsDTO.builder()
              .approvers(ApproversDTO.builder().userGroups(List.of("approver")).build())
              .approvalActivities(List.of(HarnessApprovalActivity.builder()
                                              .user(EmbeddedUser.builder()
                                                        .uuid("other-user-uuid")
                                                        .name("Other User")
                                                        .email("other@test.com")
                                                        .build())
                                              .action(HarnessApprovalAction.REJECT)
                                              .build()))
              .build();
      ApprovalInstanceResponseDTO responseDTO = ApprovalInstanceResponseDTO.builder()
                                                    .id(approvalInstanceId)
                                                    .type(ApprovalType.HARNESS_APPROVAL)
                                                    .status(ApprovalStatus.REJECTED)
                                                    .details(harnessDetails)
                                                    .build();
      HarnessApprovalInstance approvalInstance = buildHarnessApprovalInstanceForGet(approvalInstanceId);
      when(approvalInstanceService.fetchFromObjectStoreWithFallback(ACCOUNT_ID, approvalInstanceId))
          .thenReturn(approvalInstance);
      doReturn(responseDTO).when(approvalInstanceResponseMapper).toApprovalInstanceResponseDTO(approvalInstance, true);

      ApprovalInstanceResponseDTO result = approvalResourceService.get(approvalInstanceId, ACCOUNT_ID);

      assertThat(result.getRejectedByCurrentUser()).isFalse();
      assertThat(result.getApprovedByCurrentUser()).isFalse();
    }
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testApprovedByCurrentUserIsFalseWhenCurrentUserRejected() throws IOException {
    try (MockedStatic<CGRestUtils> cgStatic = Mockito.mockStatic(CGRestUtils.class)) {
      String approvalInstanceId = "approval-instance-id";
      String currentUserUuid = "current-user-uuid";
      setupCurrentUserForApprovedByCurrentUser(cgStatic, currentUserUuid);

      EmbeddedUser currentUser =
          EmbeddedUser.builder().uuid(currentUserUuid).name("Test User").email("test@test.com").build();
      HarnessApprovalInstanceDetailsDTO harnessDetails =
          HarnessApprovalInstanceDetailsDTO.builder()
              .approvers(ApproversDTO.builder().userGroups(List.of("approver")).build())
              .approvalActivities(List.of(
                  HarnessApprovalActivity.builder().user(currentUser).action(HarnessApprovalAction.REJECT).build()))
              .build();
      ApprovalInstanceResponseDTO responseDTO = ApprovalInstanceResponseDTO.builder()
                                                    .id(approvalInstanceId)
                                                    .type(ApprovalType.HARNESS_APPROVAL)
                                                    .status(ApprovalStatus.REJECTED)
                                                    .details(harnessDetails)
                                                    .build();
      HarnessApprovalInstance approvalInstance = buildHarnessApprovalInstanceForGet(approvalInstanceId);
      when(approvalInstanceService.fetchFromObjectStoreWithFallback(ACCOUNT_ID, approvalInstanceId))
          .thenReturn(approvalInstance);
      doReturn(responseDTO).when(approvalInstanceResponseMapper).toApprovalInstanceResponseDTO(approvalInstance, true);

      ApprovalInstanceResponseDTO result = approvalResourceService.get(approvalInstanceId, ACCOUNT_ID);

      assertThat(result.getApprovedByCurrentUser()).isFalse();
      assertThat(result.getRejectedByCurrentUser()).isTrue();
    }
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testApprovedByCurrentUserIsFalseWhenAnotherUserApproved() throws IOException {
    try (MockedStatic<CGRestUtils> cgStatic = Mockito.mockStatic(CGRestUtils.class)) {
      String approvalInstanceId = "approval-instance-id";
      String currentUserUuid = "current-user-uuid";
      setupCurrentUserForApprovedByCurrentUser(cgStatic, currentUserUuid);

      HarnessApprovalInstanceDetailsDTO harnessDetails =
          HarnessApprovalInstanceDetailsDTO.builder()
              .approvers(ApproversDTO.builder().userGroups(List.of("approver")).build())
              .approvalActivities(List.of(HarnessApprovalActivity.builder()
                                              .user(EmbeddedUser.builder()
                                                        .uuid("other-user-uuid")
                                                        .name("Other User")
                                                        .email("other@test.com")
                                                        .build())
                                              .action(HarnessApprovalAction.APPROVE)
                                              .build()))
              .build();
      ApprovalInstanceResponseDTO responseDTO = ApprovalInstanceResponseDTO.builder()
                                                    .id(approvalInstanceId)
                                                    .type(ApprovalType.HARNESS_APPROVAL)
                                                    .status(ApprovalStatus.APPROVED)
                                                    .details(harnessDetails)
                                                    .build();
      HarnessApprovalInstance approvalInstance = buildHarnessApprovalInstanceForGet(approvalInstanceId);
      when(approvalInstanceService.fetchFromObjectStoreWithFallback(ACCOUNT_ID, approvalInstanceId))
          .thenReturn(approvalInstance);
      doReturn(responseDTO).when(approvalInstanceResponseMapper).toApprovalInstanceResponseDTO(approvalInstance, true);

      ApprovalInstanceResponseDTO result = approvalResourceService.get(approvalInstanceId, ACCOUNT_ID);

      assertThat(result.getApprovedByCurrentUser()).isFalse();
      assertThat(result.getRejectedByCurrentUser()).isFalse();
    }
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testApprovedByCurrentUserNotSetForNonHarnessApprovalDetails() throws IOException {
    try (MockedStatic<CGRestUtils> cgStatic = Mockito.mockStatic(CGRestUtils.class)) {
      String approvalInstanceId = "approval-instance-id";
      String currentUserUuid = "current-user-uuid";
      setupCurrentUserForApprovedByCurrentUser(cgStatic, currentUserUuid);

      ApprovalInstanceResponseDTO responseDTO = ApprovalInstanceResponseDTO.builder()
                                                    .id(approvalInstanceId)
                                                    .type(ApprovalType.JIRA_APPROVAL)
                                                    .status(ApprovalStatus.WAITING)
                                                    .details(JiraApprovalInstanceDetailsDTO.builder().build())
                                                    .build();
      HarnessApprovalInstance approvalInstance = buildHarnessApprovalInstanceForGet(approvalInstanceId);
      when(approvalInstanceService.fetchFromObjectStoreWithFallback(ACCOUNT_ID, approvalInstanceId))
          .thenReturn(approvalInstance);
      doReturn(responseDTO).when(approvalInstanceResponseMapper).toApprovalInstanceResponseDTO(approvalInstance, true);

      ApprovalInstanceResponseDTO result = approvalResourceService.get(approvalInstanceId, ACCOUNT_ID);

      assertThat(result.getApprovedByCurrentUser()).isFalse();
      assertThat(result.getRejectedByCurrentUser()).isFalse();
    }
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testApprovedByCurrentUserGracefulFailureDoesNotBreakApi() throws IOException {
    String approvalInstanceId = "approval-instance-id";
    String currentUserUuid = "current-user-uuid";

    HarnessApprovalInstanceDetailsDTO harnessDetails =
        HarnessApprovalInstanceDetailsDTO.builder()
            .approvers(ApproversDTO.builder().userGroups(List.of("approver")).build())
            .approvalActivities(null)
            .build();
    ApprovalInstanceResponseDTO responseDTO = ApprovalInstanceResponseDTO.builder()
                                                  .id(approvalInstanceId)
                                                  .type(ApprovalType.HARNESS_APPROVAL)
                                                  .status(ApprovalStatus.APPROVED)
                                                  .details(harnessDetails)
                                                  .build();
    HarnessApprovalInstance approvalInstance = buildHarnessApprovalInstanceForGet(approvalInstanceId);
    when(approvalInstanceService.fetchFromObjectStoreWithFallback(ACCOUNT_ID, approvalInstanceId))
        .thenReturn(approvalInstance);
    doReturn(responseDTO).when(approvalInstanceResponseMapper).toApprovalInstanceResponseDTO(approvalInstance, true);
    when(currentUserHelper.getPrincipalFromSecurityContext()).thenThrow(new RuntimeException("principal unavailable"));

    ApprovalInstanceResponseDTO result = approvalResourceService.get(approvalInstanceId, ACCOUNT_ID);

    assertThat(result).isEqualTo(responseDTO);
    assertThat(result.getApprovedByCurrentUser()).isFalse();
    assertThat(result.getRejectedByCurrentUser()).isFalse();
  }

  private void setupCurrentUserForApprovedByCurrentUser(MockedStatic<CGRestUtils> cgStatic, String currentUserUuid) {
    when(currentUserHelper.getPrincipalFromSecurityContext())
        .thenReturn(new UserPrincipal("test@test.com", "Test User", "user", ACCOUNT_ID));
    cgStatic.when(() -> CGRestUtils.getResponse(any()))
        .thenReturn(
            Optional.of(UserInfo.builder().uuid(currentUserUuid).name("Test User").email("test@test.com").build()));
  }

  private HarnessApprovalInstance buildHarnessApprovalInstanceForGet(String approvalInstanceId) {
    HarnessApprovalInstance approvalInstance = HarnessApprovalInstance.builder().build();
    approvalInstance.setId(approvalInstanceId);
    approvalInstance.setAccountId(ACCOUNT_ID);
    return approvalInstance;
  }
}
