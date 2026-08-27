/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.notification.helper;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.NAMANG;
import static io.harness.rule.OwnerRule.SANDESH_SALUNKHE;
import static io.harness.rule.OwnerRule.SOURABH;
import static io.harness.rule.OwnerRule.vivekveman;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.IdentifierRef;
import io.harness.category.element.UnitTests;
import io.harness.encryption.Scope;
import io.harness.logging.LogLevel;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.ng.core.dto.OrganizationDTO;
import io.harness.ng.core.dto.OrganizationResponse;
import io.harness.ng.core.dto.ProjectDTO;
import io.harness.ng.core.dto.ProjectResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.dto.UserGroupDTO;
import io.harness.ng.core.dto.UserGroupFilterDTO;
import io.harness.ng.core.notification.EmailConfigDTO;
import io.harness.ng.core.notification.MicrosoftTeamsConfigDTO;
import io.harness.ng.core.notification.NotificationSettingConfigDTO;
import io.harness.ng.core.notification.SlackConfigDTO;
import io.harness.notification.NotificationChannelType;
import io.harness.notification.NotificationRequest;
import io.harness.notification.Team;
import io.harness.notification.channeldetails.EmailChannel;
import io.harness.notification.channeldetails.MSTeamChannel;
import io.harness.notification.channeldetails.NotificationChannel;
import io.harness.notification.channeldetails.SlackChannel;
import io.harness.notification.notificationclient.NotificationClient;
import io.harness.notification.templates.PredefinedTemplate;
import io.harness.organization.remote.OrganizationClient;
import io.harness.pms.approval.notification.stagemetadata.StageMetadataNotificationHelper;
import io.harness.pms.approval.notification.stagemetadata.summary.StageSummary;
import io.harness.pms.approval.notification.summary.ApprovalSummary.ApprovalSummaryKeys;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.plan.EdgeLayoutList;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.GraphLayoutNode;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.pipeline.mappers.GraphLayoutDtoMapper;
import io.harness.pms.plan.creation.PlanCreatorConstants;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.dto.GraphLayoutNodeDTO;
import io.harness.pms.plan.execution.service.intfc.PMSExecutionService;
import io.harness.project.remote.ProjectClient;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;
import io.harness.steps.approval.step.beans.ApprovalStatus;
import io.harness.steps.approval.step.beans.ApprovalType;
import io.harness.steps.approval.step.entities.ApprovalInstance;
import io.harness.steps.approval.step.entities.HarnessApprovalInstance;
import io.harness.steps.approval.step.harness.beans.ApproversDTO;
import io.harness.steps.approval.step.harness.beans.HarnessApprovalAction;
import io.harness.steps.approval.step.harness.beans.HarnessApprovalActivity;
import io.harness.usergroups.UserGroupClient;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.ScopeResolutionHelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(PIPELINE)
@RunWith(MockitoJUnitRunner.class)
public class ApprovalNotificationHandlerImplTest extends CategoryTest {
  @Mock private UserGroupClient userGroupClient;
  @Mock private NotificationClient notificationClient;
  @Mock private NotificationHelper notificationHelper;
  @Mock private PMSExecutionService pmsExecutionService;
  @Mock private ApprovalInstance approvalInstance;
  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Mock private ProjectClient projectClient;
  @Mock private OrganizationClient organizationClient;
  @Mock private StageMetadataNotificationHelper stageMetadataNotificationHelper;
  @Mock private ScopeResolutionHelper scopeResolutionHelper;
  @InjectMocks ApprovalNotificationHandlerImpl approvalNotificationHandler;
  private static String accountId = "accountId";

  private static String userGroupIdentifier = "userGroupIdentifier";
  private static String orgIdentifier = "orgIdentifier";
  private static String projectIdentifier = "projectIdentifier";
  private static String pipelineIdentifier = "pipelineIdentifier";
  private static String startingNodeId = "startingNodeId";
  private static String secondNodeId = "secondNodeId";
  private static String thirdNodeId = "thirdNodeId";
  private static String pipelineName = "pipeline name";
  private static String orgName = "org name";
  private static String projectName = "project name";
  private static String userUuid = "XXXX YYYY XXXX";
  private static String userId = "userID";
  private static Ambiance ambiance = Ambiance.newBuilder()
                                         .putSetupAbstractions("accountId", accountId)
                                         .putSetupAbstractions("orgIdentifier", orgIdentifier)
                                         .putSetupAbstractions("projectIdentifier", projectIdentifier)
                                         .putSetupAbstractions("pipelineIdentifier", pipelineIdentifier)
                                         .setExpressionFunctorToken(1234L)
                                         .build();

  private static List<UserGroupDTO> basicUserGroupDTOS = Collections.singletonList(
      UserGroupDTO.builder()
          .identifier(userGroupIdentifier)
          .notificationConfigs(List.of(SlackConfigDTO.builder().build(), EmailConfigDTO.builder().build()))
          .build());

  private static HarnessApprovalInstance basicApprovalInstance =
      HarnessApprovalInstance.builder()
          .approvers(ApproversDTO.builder().userGroups(Collections.singletonList("user")).build())
          .build();

  private static PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity =
      PipelineExecutionSummaryEntity.builder()
          .accountId(accountId)
          .orgIdentifier(orgIdentifier)
          .projectIdentifier(projectIdentifier)
          .pipelineIdentifier(pipelineIdentifier)
          .startingNodeId(startingNodeId)
          .layoutNodeMap(new HashMap<>())
          .build();

  private static List<ArgumentCaptor<Set<StageSummary>>> stageSummaryArgumentCaptors = new ArrayList<>();

  @Before
  public void setup() throws Exception {
    Call<ResponseDTO<Optional<OrganizationResponse>>> orgDTOCall = mock(Call.class);
    when(organizationClient.getOrganization(any(), any())).thenReturn(orgDTOCall);
    OrganizationResponse organizationResponse =
        OrganizationResponse.builder()
            .organization(OrganizationDTO.builder().identifier(orgIdentifier).name(orgName).build())
            .build();
    ResponseDTO<Optional<OrganizationResponse>> orgRestResponse =
        ResponseDTO.newResponse(Optional.of(organizationResponse));
    Response<ResponseDTO<Optional<OrganizationResponse>>> orgResponse = Response.success(orgRestResponse);
    when(orgDTOCall.execute()).thenReturn(orgResponse);

    Call<ResponseDTO<Optional<ProjectResponse>>> projDTOCall = mock(Call.class);
    when(projectClient.getProject(projectIdentifier, accountId, orgIdentifier)).thenReturn(projDTOCall);
    ProjectResponse projectResponse =
        ProjectResponse.builder()
            .project(ProjectDTO.builder().identifier(projectIdentifier).name(projectName).build())
            .build();
    ResponseDTO<Optional<ProjectResponse>> projRestResponse = ResponseDTO.newResponse(Optional.of(projectResponse));
    Response<ResponseDTO<Optional<ProjectResponse>>> projResponse = Response.success(projRestResponse);
    when(projDTOCall.execute()).thenReturn(projResponse);

    basicApprovalInstance.setAmbiance(ambiance);
    basicApprovalInstance.setCreatedAt(System.currentTimeMillis());
    basicApprovalInstance.setDeadline(2L * System.currentTimeMillis());
    basicApprovalInstance.setType(ApprovalType.HARNESS_APPROVAL);
    basicApprovalInstance.setIncludePipelineExecutionHistory(true);
    basicApprovalInstance.setValidatedUserGroups(basicUserGroupDTOS);
    basicApprovalInstance.setStatus(ApprovalStatus.WAITING);
    for (int i = 0; i < 3; i++) {
      stageSummaryArgumentCaptors.add(ArgumentCaptor.forClass(Set.class));
    }
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testSendNotification() throws Exception {
    // isIncludePipelineExecutionHistory false
    try (MockedConstruction<NGLogCallback> ngLogCallback = mockConstruction(NGLogCallback.class)) {
      String url = "https://qa.harness.io/ng/#/account/zEaak-FLS425IEO7OLzMUg/cd/orgs/CV/projects/Brijesh_Dhakar/"
          + "pipelines/DockerTest/executions/szmvyw4wQR2W4_iKkq9bfQ/pipeline";
      TriggeredBy triggeredBy = TriggeredBy.newBuilder().setIdentifier(userId).setUuid(userUuid).build();
      ExecutionTriggerInfo triggerInfo = ExecutionTriggerInfo.newBuilder().setTriggeredBy(triggeredBy).build();
      ExecutionMetadata executionMetadata = ExecutionMetadata.newBuilder().setTriggerInfo(triggerInfo).build();
      Ambiance ambiance = Ambiance.newBuilder()
                              .putSetupAbstractions("accountId", accountId)
                              .putSetupAbstractions("orgIdentifier", orgIdentifier)
                              .putSetupAbstractions("projectIdentifier", projectIdentifier)
                              .putSetupAbstractions("pipelineIdentifier", pipelineIdentifier)
                              .setMetadata(executionMetadata)
                              .build();
      HarnessApprovalInstance approvalInstance =
          HarnessApprovalInstance.builder()
              .approvers(
                  ApproversDTO.builder()
                      .userGroups(new ArrayList<>(Arrays.asList("proj_faulty", "proj_right", "org.org_faulty",
                          "org.org_right", "account.acc_faulty", "account.acc_right", "proj_faulty", "proj_right")))
                      .build())
              .approvalMessage("this is first line \n this is second line")
              .build();
      approvalInstance.setAmbiance(ambiance);
      approvalInstance.setCreatedAt(System.currentTimeMillis());
      approvalInstance.setDeadline(2L * System.currentTimeMillis());
      approvalInstance.setType(ApprovalType.HARNESS_APPROVAL);

      PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                          .accountId(accountId)
                                                                          .orgIdentifier(orgIdentifier)
                                                                          .projectIdentifier(projectIdentifier)
                                                                          .pipelineIdentifier(pipelineIdentifier)
                                                                          .name(pipelineName)
                                                                          .build();
      doReturn(pipelineExecutionSummaryEntity)
          .when(pmsExecutionService)
          .getPipelineExecutionSummaryEntity(anyString(), anyString(), anyBoolean());
      List<NotificationSettingConfigDTO> notificationSettingConfigDTOS = new ArrayList<>();
      notificationSettingConfigDTOS.add(SlackConfigDTO.builder().build());
      notificationSettingConfigDTOS.add(EmailConfigDTO.builder().build());
      notificationSettingConfigDTOS.add(MicrosoftTeamsConfigDTO.builder().build());

      List<UserGroupDTO> userGroupDTOS =
          new ArrayList<>(Arrays.asList(UserGroupDTO.builder()
                                            .identifier("proj_right")
                                            .accountIdentifier(accountId)
                                            .orgIdentifier(orgIdentifier)
                                            .projectIdentifier(projectIdentifier)
                                            .notificationConfigs(notificationSettingConfigDTOS)
                                            .build(),
              UserGroupDTO.builder()
                  .identifier("org_right")
                  .accountIdentifier(accountId)
                  .orgIdentifier(orgIdentifier)
                  .notificationConfigs(notificationSettingConfigDTOS)
                  .build(),
              UserGroupDTO.builder()
                  .identifier("acc_right")
                  .accountIdentifier(accountId)
                  .notificationConfigs(notificationSettingConfigDTOS)
                  .build()));

      Call<ResponseDTO<List<UserGroupDTO>>> responseDTOCall = mock(Call.class);
      when(userGroupClient.getFilteredUserGroups(any())).thenReturn(responseDTOCall);
      ResponseDTO<List<UserGroupDTO>> restResponse = ResponseDTO.newResponse(userGroupDTOS);
      Response<ResponseDTO<List<UserGroupDTO>>> response = Response.success(restResponse);
      when(responseDTOCall.execute()).thenReturn(response);

      approvalInstance.setValidatedUserGroups(userGroupDTOS);

      doReturn(url).when(notificationHelper).generateUrl(eq(ambiance), any(PipelineExecutionSummaryEntity.class));
      when(logStreamingStepClientFactory.getLogStreamingStepClient(ambiance))
          .thenReturn(Mockito.mock(ILogStreamingStepClient.class));

      approvalNotificationHandler.sendNotification(approvalInstance, ambiance);
      ArgumentCaptor<NotificationChannel> notificationChannelArgumentCaptor =
          ArgumentCaptor.forClass(NotificationChannel.class);
      verify(notificationClient, times(3)).sendNotificationAsync(notificationChannelArgumentCaptor.capture());
      List<NotificationChannel> notificationChannels = notificationChannelArgumentCaptor.getAllValues();
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.pipelineName))
          .isEqualTo(pipelineName);
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.orgName)).isEqualTo(orgName);
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.projectName))
          .isEqualTo(projectName);
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.currentStageName)).isEqualTo("");
      // get userId in triggeredBy because email is not present
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.triggeredBy)).isEqualTo(userId);
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.pipelineExecutionLink))
          .isEqualTo(url);
      // testing whether approval message has lin breaks added in case of email but in other channels
      assertThat(notificationChannels.get(1).getTemplateData().get(ApprovalSummaryKeys.approvalMessage))
          .isEqualTo("this is first line \n this is second line");
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.approvalMessage))
          .isEqualTo("this is first line <br> this is second line");
      assertThat(notificationChannels.get(2).getTemplateId())
          .isEqualTo(PredefinedTemplate.HARNESS_APPROVAL_NOTIFICATION_MSTEAMS.getIdentifier());
      assertThat(notificationChannels.get(2).getTeam()).isEqualTo(Team.PIPELINE);
      verify(pmsExecutionService, times(1)).getPipelineExecutionSummaryEntity(anyString(), anyString(), anyBoolean());
      verify(ngLogCallback.constructed().get(0), times(2)).saveExecutionLog(anyString());
      ArgumentCaptor<String> stringArgumentCaptor = ArgumentCaptor.forClass(String.class);
      verify(ngLogCallback.constructed().get(0), times(1))
          .saveExecutionLog(stringArgumentCaptor.capture(), eq(LogLevel.WARN));

      String invalidUserGroups = stringArgumentCaptor.getValue().split(":")[1].trim();
      List<String> invalidUserGroupsList =
          Arrays.stream(invalidUserGroups.substring(1, invalidUserGroups.length() - 1).split(","))
              .map(String::trim)
              .collect(Collectors.toList());
      List<String> expectedInvalidUserGroupsList =
          new ArrayList<>(Arrays.asList("proj_faulty", "org.org_faulty", "account.acc_faulty"));
      assertThat(invalidUserGroupsList.size() == expectedInvalidUserGroupsList.size()
          && invalidUserGroupsList.containsAll(expectedInvalidUserGroupsList)
          && expectedInvalidUserGroupsList.containsAll(invalidUserGroupsList))
          .isTrue();
    }
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testSendNotification1() throws IOException {
    // isIncludePipelineExecutionHistory true with an approval stage; FF off
    try (MockedConstruction<NGLogCallback> ngLogCallback = mockConstruction(NGLogCallback.class)) {
      String url = "https://qa.harness.io/ng/#/account/zEaak-FLS425IEO7OLzMUg/cd/orgs/CV/projects/Brijesh_Dhakar/"
          + "pipelines/DockerTest/executions/szmvyw4wQR2W4_iKkq9bfQ/pipeline";

      GraphLayoutNode graphLayoutNode = GraphLayoutNode.newBuilder()
                                            .setNodeIdentifier("nodeIdentifier")
                                            .setNodeType("Approval")
                                            .setNodeUUID("aBcDeFgH")
                                            .setName("Node name")
                                            .setNodeGroup("STAGE")
                                            .build();
      GraphLayoutNodeDTO graphLayoutNodeDTO = GraphLayoutDtoMapper.toDto(graphLayoutNode);
      graphLayoutNodeDTO.setStatus(ExecutionStatus.APPROVAL_WAITING);
      HashMap<String, GraphLayoutNodeDTO> layoutNodeDTOMap = new HashMap<>();
      layoutNodeDTOMap.put(startingNodeId, graphLayoutNodeDTO);

      pipelineExecutionSummaryEntity.setLayoutNodeMap(layoutNodeDTOMap);
      doReturn(pipelineExecutionSummaryEntity)
          .when(pmsExecutionService)
          .getPipelineExecutionSummaryEntity(anyString(), anyString(), anyBoolean());

      mockUserGroupResponse(basicUserGroupDTOS);
      Ambiance ambiance = buildAmbianceWithStageId("nodeIdentifier");
      doReturn(url).when(notificationHelper).generateUrl(eq(ambiance), any(PipelineExecutionSummaryEntity.class));

      doAnswer(invocationOnMock -> {
        Set<String> formattedStages = invocationOnMock.getArgument(1);
        formattedStages.add("Node name");
        return null;
      })
          .when(stageMetadataNotificationHelper)
          .setFormattedSummaryOfRunningStages(stageSummaryArgumentCaptors.get(0).capture(), anySet(), any(), any());

      approvalNotificationHandler.sendNotification(basicApprovalInstance, ambiance);

      ArgumentCaptor<NotificationChannel> notificationChannelArgumentCaptor =
          ArgumentCaptor.forClass(NotificationChannel.class);
      verify(notificationClient, times(2)).sendNotificationAsync(notificationChannelArgumentCaptor.capture());
      List<NotificationChannel> notificationChannels = notificationChannelArgumentCaptor.getAllValues();
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.runningStages))
          .isEqualTo("Node name");
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.upcomingStages))
          .isEqualTo("N/A");
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.finishedStages))
          .isEqualTo("N/A");
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.currentStageName))
          .isEqualTo("Node name");

      assertThat(stageSummaryArgumentCaptors.get(0)
                     .getValue()
                     .stream()
                     .map(StageSummary::getFormattedEntityName)
                     .collect(Collectors.toSet()))
          .contains("Node name");

      verify(ngLogCallback.constructed().get(0), times(2)).saveExecutionLog(anyString());
      verify(ngLogCallback.constructed().get(0), times(1)).saveExecutionLog(anyString(), eq(LogLevel.WARN));
    }
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testSendNotification2() throws IOException {
    // isIncludePipelineExecutionHistory true with 3 approval stages; two completed, one waiting; FF off
    try (MockedConstruction<NGLogCallback> ngLogCallback = mockConstruction(NGLogCallback.class)) {
      String url = "https://qa.harness.io/ng/#/account/zEaak-FLS425IEO7OLzMUg/cd/orgs/CV/projects/Brijesh_Dhakar/"
          + "pipelines/DockerTest/executions/szmvyw4wQR2W4_iKkq9bfQ/pipeline";

      GraphLayoutNode firstGraphLayoutNode =
          GraphLayoutNode.newBuilder()
              .setNodeIdentifier("firstIdentifier")
              .setNodeType("Approval")
              .setNodeUUID("aBcDeFgH")
              .setName("First Name")
              .setNodeGroup("STAGE")
              .setEdgeLayoutList(EdgeLayoutList.newBuilder().addNextIds(secondNodeId).build())
              .build();

      GraphLayoutNodeDTO firstGraphLayoutNodeDTO = GraphLayoutDtoMapper.toDto(firstGraphLayoutNode);
      firstGraphLayoutNodeDTO.setStatus(ExecutionStatus.SUCCESS);

      GraphLayoutNode secondGraphLayoutNode =
          GraphLayoutNode.newBuilder()
              .setNodeIdentifier("secondIdentifier")
              .setNodeType("Approval")
              .setNodeUUID("aBcDeFgH")
              .setName("Second Name")
              .setNodeGroup("STAGE")
              .setEdgeLayoutList(EdgeLayoutList.newBuilder().addNextIds(thirdNodeId).build())
              .build();
      GraphLayoutNodeDTO secondGraphLayoutNodeDTO = GraphLayoutDtoMapper.toDto(secondGraphLayoutNode);
      secondGraphLayoutNodeDTO.setStatus(ExecutionStatus.SUCCESS);

      GraphLayoutNode thirdGraphLayoutNode = GraphLayoutNode.newBuilder()
                                                 .setNodeIdentifier("thirdIdentifier")
                                                 .setNodeType("Approval")
                                                 .setNodeUUID("aBcDeFgH")
                                                 .setName("Third Name")
                                                 .setNodeGroup("STAGE")
                                                 .build();
      GraphLayoutNodeDTO thirdGraphLayoutNodeDTO = GraphLayoutDtoMapper.toDto(thirdGraphLayoutNode);
      thirdGraphLayoutNodeDTO.setStatus(ExecutionStatus.APPROVAL_WAITING);

      //    graphLayoutNodeDTO.status= ExecutionStatus.SUCCESS;
      HashMap<String, GraphLayoutNodeDTO> layoutNodeDTOMap = new HashMap<>();
      layoutNodeDTOMap.put(startingNodeId, firstGraphLayoutNodeDTO);
      layoutNodeDTOMap.put(secondNodeId, secondGraphLayoutNodeDTO);
      layoutNodeDTOMap.put(thirdNodeId, thirdGraphLayoutNodeDTO);

      pipelineExecutionSummaryEntity.setLayoutNodeMap(layoutNodeDTOMap);
      doReturn(pipelineExecutionSummaryEntity)
          .when(pmsExecutionService)
          .getPipelineExecutionSummaryEntity(anyString(), anyString(), anyBoolean());

      mockUserGroupResponse(basicUserGroupDTOS);

      Ambiance ambiance = buildAmbianceWithStageId("thirdIdentifier");
      doReturn(url).when(notificationHelper).generateUrl(eq(ambiance), any(PipelineExecutionSummaryEntity.class));

      doAnswer(invocationOnMock -> {
        Set<String> formattedStages = invocationOnMock.getArgument(1);
        formattedStages.addAll(List.of("First Name", "Second Name"));
        return null;
      })
          .when(stageMetadataNotificationHelper)
          .setFormattedSummaryOfFinishedStages(stageSummaryArgumentCaptors.get(0).capture(), anySet(), any());

      doAnswer(invocationOnMock -> {
        Set<String> formattedStages = invocationOnMock.getArgument(1);
        formattedStages.add("Third Name");
        return null;
      })
          .when(stageMetadataNotificationHelper)
          .setFormattedSummaryOfRunningStages(stageSummaryArgumentCaptors.get(1).capture(), anySet(), any(), any());

      approvalNotificationHandler.sendNotification(basicApprovalInstance, ambiance);

      assertThat(stageSummaryArgumentCaptors.get(0)
                     .getValue()
                     .stream()
                     .map(StageSummary::getFormattedEntityName)
                     .collect(Collectors.toSet()))
          .contains("First Name", "Second Name");

      assertThat(stageSummaryArgumentCaptors.get(1)
                     .getValue()
                     .stream()
                     .map(StageSummary::getFormattedEntityName)
                     .collect(Collectors.toSet()))
          .contains("Third Name");

      ArgumentCaptor<NotificationChannel> notificationChannelArgumentCaptor =
          ArgumentCaptor.forClass(NotificationChannel.class);
      verify(notificationClient, times(2)).sendNotificationAsync(notificationChannelArgumentCaptor.capture());
      List<NotificationChannel> notificationChannels = notificationChannelArgumentCaptor.getAllValues();
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.finishedStages))
          .isEqualTo("First Name<br> Second Name");
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.runningStages))
          .isEqualTo("Third Name");
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.upcomingStages))
          .isEqualTo("N/A");
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.currentStageName))
          .isEqualTo("Third Name");

      verify(ngLogCallback.constructed().get(0), times(2)).saveExecutionLog(anyString());
      verify(ngLogCallback.constructed().get(0), times(1)).saveExecutionLog(anyString(), eq(LogLevel.WARN));
    }
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testSendNotification3() throws IOException {
    // isIncludePipelineExecutionHistory true with 3 approval stages; two completed, one waiting; FF on
    try (MockedConstruction<NGLogCallback> ngLogCallback = mockConstruction(NGLogCallback.class)) {
      String url = "https://qa.harness.io/ng/#/account/zEaak-FLS425IEO7OLzMUg/cd/orgs/CV/projects/Brijesh_Dhakar/"
          + "pipelines/DockerTest/executions/szmvyw4wQR2W4_iKkq9bfQ/pipeline";

      GraphLayoutNode firstGraphLayoutNode =
          GraphLayoutNode.newBuilder()
              .setNodeIdentifier("firstIdentifier")
              .setNodeType("Approval")
              .setNodeUUID("aBcDeFgH")
              .setName("First Name")
              .setNodeGroup("STAGE")
              .setEdgeLayoutList(EdgeLayoutList.newBuilder().addNextIds(secondNodeId).build())
              .build();

      GraphLayoutNodeDTO firstGraphLayoutNodeDTO = GraphLayoutDtoMapper.toDto(firstGraphLayoutNode);
      firstGraphLayoutNodeDTO.setStatus(ExecutionStatus.SUCCESS);

      GraphLayoutNode secondGraphLayoutNode =
          GraphLayoutNode.newBuilder()
              .setNodeIdentifier("secondIdentifier")
              .setNodeType("Approval")
              .setNodeUUID("aBcDeFgH")
              .setName("Second Name")
              .setNodeGroup("STAGE")
              .setEdgeLayoutList(EdgeLayoutList.newBuilder().addNextIds(thirdNodeId).build())
              .build();
      GraphLayoutNodeDTO secondGraphLayoutNodeDTO = GraphLayoutDtoMapper.toDto(secondGraphLayoutNode);
      secondGraphLayoutNodeDTO.setStatus(ExecutionStatus.SUCCESS);

      GraphLayoutNode thirdGraphLayoutNode = GraphLayoutNode.newBuilder()
                                                 .setNodeIdentifier("thirdIdentifier")
                                                 .setNodeType("Approval")
                                                 .setNodeUUID("aBcDeFgH")
                                                 .setName("Third Name")
                                                 .setNodeGroup("STAGE")
                                                 .build();
      GraphLayoutNodeDTO thirdGraphLayoutNodeDTO = GraphLayoutDtoMapper.toDto(thirdGraphLayoutNode);
      thirdGraphLayoutNodeDTO.setStatus(ExecutionStatus.APPROVAL_WAITING);

      HashMap<String, GraphLayoutNodeDTO> layoutNodeDTOMap = new HashMap<>();
      layoutNodeDTOMap.put(startingNodeId, firstGraphLayoutNodeDTO);
      layoutNodeDTOMap.put(secondNodeId, secondGraphLayoutNodeDTO);
      layoutNodeDTOMap.put(thirdNodeId, thirdGraphLayoutNodeDTO);

      pipelineExecutionSummaryEntity.setLayoutNodeMap(layoutNodeDTOMap);
      doReturn(pipelineExecutionSummaryEntity)
          .when(pmsExecutionService)
          .getPipelineExecutionSummaryEntity(anyString(), anyString(), anyBoolean());
      mockUserGroupResponse(basicUserGroupDTOS);

      Ambiance ambiance = buildAmbianceWithStageId("thirdIdentifier");
      doReturn(url).when(notificationHelper).generateUrl(eq(ambiance), any(PipelineExecutionSummaryEntity.class));

      doAnswer(invocationOnMock -> {
        Set<String> formattedStages = invocationOnMock.getArgument(1);
        formattedStages.addAll(List.of("First Name", "Second Name"));
        return null;
      })
          .when(stageMetadataNotificationHelper)
          .setFormattedSummaryOfFinishedStages(stageSummaryArgumentCaptors.get(0).capture(), anySet(), any());

      doAnswer(invocationOnMock -> {
        Set<String> formattedStages = invocationOnMock.getArgument(1);
        formattedStages.add("Third Name");
        return null;
      })
          .when(stageMetadataNotificationHelper)
          .setFormattedSummaryOfRunningStages(stageSummaryArgumentCaptors.get(1).capture(), anySet(), any(), any());

      doAnswer(invocationOnMock -> {
        Set<String> formattedStages = invocationOnMock.getArgument(1);
        return null;
      })
          .when(stageMetadataNotificationHelper)
          .setFormattedSummaryOfUpcomingStages(stageSummaryArgumentCaptors.get(2).capture(), anySet(), any(), any());

      approvalNotificationHandler.sendNotification(basicApprovalInstance, ambiance);

      ArgumentCaptor<NotificationChannel> notificationChannelArgumentCaptor =
          ArgumentCaptor.forClass(NotificationChannel.class);
      verify(notificationClient, times(2)).sendNotificationAsync(notificationChannelArgumentCaptor.capture());
      List<NotificationChannel> notificationChannels = notificationChannelArgumentCaptor.getAllValues();
      assertThat(notificationChannels.get(1).getTemplateData().get(ApprovalSummaryKeys.finishedStages))
          .isEqualTo("First Name\n Second Name");
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.finishedStages))
          .isEqualTo("First Name<br> Second Name");
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.runningStages))
          .isEqualTo("Third Name");
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.upcomingStages))
          .isEqualTo("N/A");
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.currentStageName))
          .isEqualTo("Third Name");

      assertThat(stageSummaryArgumentCaptors.get(0)
                     .getValue()
                     .stream()
                     .map(StageSummary::getFormattedEntityName)
                     .collect(Collectors.toSet()))
          .contains("First Name", "Second Name");
      assertThat(stageSummaryArgumentCaptors.get(1)
                     .getValue()
                     .stream()
                     .map(StageSummary::getFormattedEntityName)
                     .collect(Collectors.toSet()))
          .contains("Third Name");
      assertThat(stageSummaryArgumentCaptors.get(2)
                     .getValue()
                     .stream()
                     .map(StageSummary::getFormattedEntityName)
                     .collect(Collectors.toSet()))
          .isEmpty();

      verify(ngLogCallback.constructed().get(0), times(2)).saveExecutionLog(anyString());
      verify(ngLogCallback.constructed().get(0), times(1)).saveExecutionLog(anyString(), eq(LogLevel.WARN));
    }
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testSendNotification5() throws IOException {
    // when FF on, running CD stage with approval, followed by custom stage
    try (MockedConstruction<NGLogCallback> ngLogCallback = mockConstruction(NGLogCallback.class)) {
      String url = "https://qa.harness.io/ng/#/account/zEaak-FLS425IEO7OLzMUg/cd/orgs/CV/projects/Brijesh_Dhakar/"
          + "pipelines/DockerTest/executions/szmvyw4wQR2W4_iKkq9bfQ/pipeline";

      GraphLayoutNode graphLayoutNode1 =
          GraphLayoutNode.newBuilder()
              .setNodeIdentifier("nodeIdentifier1")
              .setNodeType("Deployment")
              .setNodeUUID("aBcDeFgH")
              .setName("cd stage")
              .setNodeGroup("STAGE")
              .setEdgeLayoutList(EdgeLayoutList.newBuilder().addNextIds(PlanCreatorConstants.NEXT_ID).build())
              .build();
      GraphLayoutNodeDTO graphLayoutNodeDTO1 = GraphLayoutDtoMapper.toDto(graphLayoutNode1);
      graphLayoutNodeDTO1.setModule("cd");
      graphLayoutNodeDTO1.setStatus(ExecutionStatus.APPROVAL_WAITING);
      HashMap<String, GraphLayoutNodeDTO> layoutNodeDTOMap = new HashMap<>();
      layoutNodeDTOMap.put(startingNodeId, graphLayoutNodeDTO1);

      GraphLayoutNode graphLayoutNode2 = GraphLayoutNode.newBuilder()
                                             .setNodeIdentifier("nodeIdentifier2")
                                             .setNodeType("Custom")
                                             .setNodeUUID("aBcDeFgH")
                                             .setName("custom stage")
                                             .setNodeGroup("STAGE")
                                             .build();
      GraphLayoutNodeDTO graphLayoutNodeDTO2 = GraphLayoutDtoMapper.toDto(graphLayoutNode2);
      layoutNodeDTOMap.put(PlanCreatorConstants.NEXT_ID, graphLayoutNodeDTO2);

      pipelineExecutionSummaryEntity.setLayoutNodeMap(layoutNodeDTOMap);
      doReturn(pipelineExecutionSummaryEntity)
          .when(pmsExecutionService)
          .getPipelineExecutionSummaryEntity(anyString(), anyString(), anyBoolean());

      mockUserGroupResponse(basicUserGroupDTOS);

      Ambiance ambiance = buildAmbianceWithStageId("nodeIdentifier1");
      doReturn(url).when(notificationHelper).generateUrl(eq(ambiance), any(PipelineExecutionSummaryEntity.class));

      doAnswer(invocationOnMock -> {
        Set<String> formattedStages = invocationOnMock.getArgument(1);
        return null;
      })
          .when(stageMetadataNotificationHelper)
          .setFormattedSummaryOfFinishedStages(stageSummaryArgumentCaptors.get(0).capture(), anySet(), any());

      doAnswer(invocationOnMock -> {
        Set<String> formattedStages = invocationOnMock.getArgument(1);
        formattedStages.add("cd stage : \n"
            + "     Service  :  service name\n"
            + "     Environment  :  env name\n"
            + "     Infrastructure Definition  :  infra name");
        return null;
      })
          .when(stageMetadataNotificationHelper)
          .setFormattedSummaryOfRunningStages(stageSummaryArgumentCaptors.get(1).capture(), anySet(), any(), any());

      doAnswer(invocationOnMock -> {
        Set<String> formattedStages = invocationOnMock.getArgument(1);
        formattedStages.add("custom stage");
        return null;
      })
          .when(stageMetadataNotificationHelper)
          .setFormattedSummaryOfUpcomingStages(stageSummaryArgumentCaptors.get(2).capture(), anySet(), any(), any());

      approvalNotificationHandler.sendNotification(basicApprovalInstance, ambiance);

      verify(ngLogCallback.constructed().get(0), times(2)).saveExecutionLog(anyString());
      verify(ngLogCallback.constructed().get(0), times(1)).saveExecutionLog(anyString(), eq(LogLevel.WARN));

      ArgumentCaptor<NotificationChannel> notificationChannelArgumentCaptor =
          ArgumentCaptor.forClass(NotificationChannel.class);
      verify(notificationClient, times(2)).sendNotificationAsync(notificationChannelArgumentCaptor.capture());
      List<NotificationChannel> notificationChannels = notificationChannelArgumentCaptor.getAllValues();
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.finishedStages))
          .isEqualTo("N/A");
      assertThat(notificationChannels.get(1).getTemplateData().get(ApprovalSummaryKeys.runningStages))
          .isEqualTo("cd stage : \n"
              + "     Service  :  service name\n"
              + "     Environment  :  env name\n"
              + "     Infrastructure Definition  :  infra name");
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.runningStages))
          .isEqualTo("cd stage : <br>"
              + "     Service  :  service name<br>"
              + "     Environment  :  env name<br>"
              + "     Infrastructure Definition  :  infra name");
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.upcomingStages))
          .isEqualTo("custom stage");
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.currentStageName))
          .isEqualTo("cd stage");

      assertThat(stageSummaryArgumentCaptors.get(0)
                     .getValue()
                     .stream()
                     .map(StageSummary::getFormattedEntityName)
                     .collect(Collectors.toSet()))
          .isEmpty();
      assertThat(stageSummaryArgumentCaptors.get(1)
                     .getValue()
                     .stream()
                     .map(StageSummary::getFormattedEntityName)
                     .collect(Collectors.toSet()))
          .contains("cd stage");
      assertThat(stageSummaryArgumentCaptors.get(2)
                     .getValue()
                     .stream()
                     .map(StageSummary::getFormattedEntityName)
                     .collect(Collectors.toSet()))
          .contains("custom stage");
    }
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testSendNotification6WhenGetErrorWhileStageFormatting() throws IOException {
    // when FF on, running CD stage with approval, followed by custom stage
    try (MockedConstruction<NGLogCallback> ngLogCallback = mockConstruction(NGLogCallback.class)) {
      String url = "https://qa.harness.io/ng/#/account/zEaak-FLS425IEO7OLzMUg/cd/orgs/CV/projects/Brijesh_Dhakar/"
          + "pipelines/DockerTest/executions/szmvyw4wQR2W4_iKkq9bfQ/pipeline";

      GraphLayoutNode graphLayoutNode1 =
          GraphLayoutNode.newBuilder()
              .setNodeIdentifier("nodeIdentifier1")
              .setNodeType("Deployment")
              .setNodeUUID("aBcDeFgH")
              .setName("cd stage")
              .setNodeGroup("STAGE")
              .setEdgeLayoutList(EdgeLayoutList.newBuilder().addNextIds(PlanCreatorConstants.NEXT_ID).build())
              .build();
      GraphLayoutNodeDTO graphLayoutNodeDTO1 = GraphLayoutDtoMapper.toDto(graphLayoutNode1);
      graphLayoutNodeDTO1.setModule("cd");
      graphLayoutNodeDTO1.setStatus(ExecutionStatus.APPROVAL_WAITING);
      HashMap<String, GraphLayoutNodeDTO> layoutNodeDTOMap = new HashMap<>();
      layoutNodeDTOMap.put(startingNodeId, graphLayoutNodeDTO1);

      GraphLayoutNode graphLayoutNode2 = GraphLayoutNode.newBuilder()
                                             .setNodeIdentifier("nodeIdentifier2")
                                             .setNodeType("Custom")
                                             .setNodeUUID("aBcDeFgH")
                                             .setName("custom stage")
                                             .setNodeGroup("STAGE")
                                             .build();
      GraphLayoutNodeDTO graphLayoutNodeDTO2 = GraphLayoutDtoMapper.toDto(graphLayoutNode2);
      layoutNodeDTOMap.put(PlanCreatorConstants.NEXT_ID, graphLayoutNodeDTO2);

      pipelineExecutionSummaryEntity.setLayoutNodeMap(layoutNodeDTOMap);
      doReturn(pipelineExecutionSummaryEntity)
          .when(pmsExecutionService)
          .getPipelineExecutionSummaryEntity(anyString(), anyString(), anyBoolean());

      mockUserGroupResponse(basicUserGroupDTOS);

      Ambiance ambiance = buildAmbianceWithStageId("nodeIdentifier2");
      doReturn(url).when(notificationHelper).generateUrl(eq(ambiance), any(PipelineExecutionSummaryEntity.class));

      doThrow(new IllegalStateException("dummy"))
          .when(stageMetadataNotificationHelper)
          .setFormattedSummaryOfFinishedStages(anySet(), anySet(), any());

      doAnswer(invocationOnMock -> {
        Set<String> formattedStages = invocationOnMock.getArgument(1);
        formattedStages.add("cd stage : \n"
            + "     Service  :  service name\n"
            + "     Environment  :  env name\n"
            + "     Infrastructure Definition  :  infra name");
        return null;
      })
          .when(stageMetadataNotificationHelper)
          .setFormattedSummaryOfRunningStages(anySet(), anySet(), any(), any());

      doAnswer(invocationOnMock -> {
        Set<String> formattedStages = invocationOnMock.getArgument(1);
        formattedStages.add("custom stage");
        return null;
      })
          .when(stageMetadataNotificationHelper)
          .setFormattedSummaryOfUpcomingStages(anySet(), anySet(), any(), any());

      approvalNotificationHandler.sendNotification(basicApprovalInstance, ambiance);

      verify(ngLogCallback.constructed().get(0), times(3)).saveExecutionLog(anyString());
    }
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testSendNotificationException() {
    HarnessApprovalInstance approvalInstance = mock(HarnessApprovalInstance.class);
    Ambiance ambiance = mock(Ambiance.class);
    ILogStreamingStepClient iLogStreamingStepClient = mock(ILogStreamingStepClient.class);
    when(logStreamingStepClientFactory.getLogStreamingStepClient(ambiance)).thenReturn(iLogStreamingStepClient);
    doReturn(ApprovalStatus.APPROVED).when(approvalInstance).getStatus();
    approvalNotificationHandler.sendNotification(approvalInstance, ambiance);
    verify(iLogStreamingStepClient, times(1)).writeLogLine(any(), any());
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testSendNotificationInternalException() {
    HarnessApprovalInstance approvalInstance = mock(HarnessApprovalInstance.class);
    Ambiance ambiance = mock(Ambiance.class);
    NGLogCallback ngLogCallback = mock(NGLogCallback.class);
    approvalNotificationHandler.sendNotificationInternal(approvalInstance, ambiance, ngLogCallback);
    verify(ngLogCallback, times(3)).saveExecutionLog(any());
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testFindInvalidInputUserGroupsEmptyInputUserGroups() {
    UserGroupDTO userGroupDTO = UserGroupDTO.builder().build();
    List<UserGroupDTO> validatedUserGroups = Collections.singletonList(userGroupDTO);
    List<String> inputUserGroups = Collections.emptyList();
    assertThat(approvalNotificationHandler.findInvalidInputUserGroups(validatedUserGroups, inputUserGroups)).isNull();
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testFindInvalidInputUserGroupsEmptyValidatedUserGroups() {
    List<UserGroupDTO> validatedUserGroups = Collections.emptyList();
    List<String> inputUserGroups = Collections.singletonList("UserGroup1");
    List<String> userGroups =
        approvalNotificationHandler.findInvalidInputUserGroups(validatedUserGroups, inputUserGroups);
    assertThat(userGroups).isNotEmpty().containsExactly("UserGroup1");
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testGetNotificationChannelWithoutUserGroupReturnsNull() {
    HarnessApprovalInstance instance = HarnessApprovalInstance.builder().build();
    Map<String, String> templateData = Collections.singletonMap("key", "value");
    assertThat(
        approvalNotificationHandler.getNotificationChannel(instance, NotificationChannelType.EMAIL, null, templateData))
        .isNull();
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetSlackNotificationChannelContainingSecretWebhook() {
    basicApprovalInstance.setStatus(ApprovalStatus.WAITING);
    // approval waiting notification
    //// without execution history
    basicApprovalInstance.setIncludePipelineExecutionHistory(false);
    basicApprovalInstance.setAccountId(accountId);
    SlackConfigDTO slackConfigDTO = SlackConfigDTO.builder().slackWebhookUrl("<+secret.getValue(\"dummy\")>").build();
    UserGroupDTO userGroupDTO = UserGroupDTO.builder()
                                    .identifier(userGroupIdentifier)
                                    .accountIdentifier(accountId)
                                    .orgIdentifier(orgIdentifier)
                                    .projectIdentifier(projectIdentifier)
                                    .notificationConfigs(Collections.singletonList(slackConfigDTO))
                                    .build();
    Map<String, String> templateData = Collections.singletonMap("key", "value");
    SlackChannel slackChannel = (SlackChannel) approvalNotificationHandler.getNotificationChannel(
        basicApprovalInstance, NotificationChannelType.SLACK, Collections.singletonList(userGroupDTO), templateData);
    SlackChannel expectedChannel =
        SlackChannel.builder()
            .accountId(accountId)
            .team(Team.PIPELINE)
            .templateId(PredefinedTemplate.HARNESS_APPROVAL_NOTIFICATION_SLACK.getIdentifier())
            .templateData(templateData)
            .webhookUrls(Collections.emptyList())
            .expressionFunctorToken(1234L)
            .userGroups(new ArrayList<>(Collections.singleton(NotificationRequest.UserGroup.newBuilder()
                                                                  .setIdentifier(userGroupIdentifier)
                                                                  .setOrgIdentifier(orgIdentifier)
                                                                  .setProjectIdentifier(projectIdentifier)
                                                                  .build())))
            .orgIdentifier(orgIdentifier)
            .projectIdentifier(projectIdentifier)
            .build();
    assertEquality(slackChannel, expectedChannel);

    //// with execution history
    basicApprovalInstance.setIncludePipelineExecutionHistory(true);
    slackChannel = (SlackChannel) approvalNotificationHandler.getNotificationChannel(
        basicApprovalInstance, NotificationChannelType.SLACK, Collections.singletonList(userGroupDTO), templateData);
    expectedChannel.setTemplateId(PredefinedTemplate.HARNESS_APPROVAL_EXECUTION_NOTIFICATION_SLACK.getIdentifier());
    assertEquality(slackChannel, expectedChannel);

    // approval rejected notification
    basicApprovalInstance.setStatus(ApprovalStatus.REJECTED);
    //// without execution history
    basicApprovalInstance.setIncludePipelineExecutionHistory(false);
    slackChannel = (SlackChannel) approvalNotificationHandler.getNotificationChannel(
        basicApprovalInstance, NotificationChannelType.SLACK, Collections.singletonList(userGroupDTO), templateData);
    expectedChannel.setTemplateId(PredefinedTemplate.HARNESS_APPROVAL_ACTION_NOTIFICATION_SLACK.getIdentifier());
    assertEquality(slackChannel, expectedChannel);
    //// with execution history
    basicApprovalInstance.setIncludePipelineExecutionHistory(true);
    slackChannel = (SlackChannel) approvalNotificationHandler.getNotificationChannel(
        basicApprovalInstance, NotificationChannelType.SLACK, Collections.singletonList(userGroupDTO), templateData);
    expectedChannel.setTemplateId(
        PredefinedTemplate.HARNESS_APPROVAL_ACTION_EXECUTION_NOTIFICATION_SLACK.getIdentifier());
    assertEquality(slackChannel, expectedChannel);
  }

  private void assertEquality(NotificationChannel notificationChannel, NotificationChannel expected) {
    assertThat(expected.getAccountId()).isEqualTo(notificationChannel.getAccountId());
    assertThat(expected.getTeam()).isEqualTo(notificationChannel.getTeam());
    assertThat(expected.getTemplateId()).isEqualTo(notificationChannel.getTemplateId());
    assertThat(expected.getTemplateData()).isEqualTo(notificationChannel.getTemplateData());
    assertThat(expected.getUserGroups()).isEqualTo(notificationChannel.getUserGroups());
    if (notificationChannel instanceof SlackChannel) {
      assertThat(((SlackChannel) expected).getWebhookUrls())
          .isEqualTo(((SlackChannel) notificationChannel).getWebhookUrls());
      assertThat(((SlackChannel) expected).getOrgIdentifier())
          .isEqualTo(((SlackChannel) notificationChannel).getOrgIdentifier());
      assertThat(((SlackChannel) expected).getProjectIdentifier())
          .isEqualTo(((SlackChannel) notificationChannel).getProjectIdentifier());
      assertThat(((SlackChannel) expected).getExpressionFunctorToken())
          .isEqualTo(((SlackChannel) notificationChannel).getExpressionFunctorToken());
    } else if (notificationChannel instanceof MSTeamChannel) {
      assertThat(((MSTeamChannel) expected).getMsTeamKeys())
          .isEqualTo(((MSTeamChannel) notificationChannel).getMsTeamKeys());
      assertThat(((MSTeamChannel) expected).getExpressionFunctorToken())
          .isEqualTo(((MSTeamChannel) notificationChannel).getExpressionFunctorToken());
      assertThat(((MSTeamChannel) expected).getOrgIdentifier())
          .isEqualTo(((MSTeamChannel) notificationChannel).getOrgIdentifier());
      assertThat(((MSTeamChannel) expected).getProjectIdentifier())
          .isEqualTo(((MSTeamChannel) notificationChannel).getProjectIdentifier());
    } else if (notificationChannel instanceof EmailChannel) {
      assertThat(((EmailChannel) notificationChannel).getRecipients())
          .isEqualTo(((EmailChannel) notificationChannel).getRecipients());
    }
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetMsTeamsNotificationChannelContainingSecretWebhook() {
    basicApprovalInstance.setStatus(ApprovalStatus.WAITING);
    // approval waiting notification
    //// without execution history
    basicApprovalInstance.setIncludePipelineExecutionHistory(false);
    MicrosoftTeamsConfigDTO microsoftTeamsConfigDTO =
        MicrosoftTeamsConfigDTO.builder().microsoftTeamsWebhookUrl("<+secret.getValue(\"dummy\")>").build();
    UserGroupDTO userGroupDTO = UserGroupDTO.builder()
                                    .identifier(userGroupIdentifier)
                                    .accountIdentifier(accountId)
                                    .orgIdentifier(orgIdentifier)
                                    .projectIdentifier(projectIdentifier)
                                    .notificationConfigs(Collections.singletonList(microsoftTeamsConfigDTO))
                                    .build();
    Map<String, String> templateData = new HashMap<>(Map.of("key", "value"));

    MSTeamChannel msTeamChannel = (MSTeamChannel) approvalNotificationHandler.getNotificationChannel(
        basicApprovalInstance, NotificationChannelType.MSTEAMS, Collections.singletonList(userGroupDTO), templateData);
    MSTeamChannel expectedChannel =
        MSTeamChannel.builder()
            .msTeamKeys(Collections.emptyList())
            .accountId(accountId)
            .team(Team.PIPELINE)
            .templateId(PredefinedTemplate.HARNESS_APPROVAL_NOTIFICATION_MSTEAMS.getIdentifier())
            .templateData(templateData)
            .userGroups(new ArrayList<>(Collections.singleton(NotificationRequest.UserGroup.newBuilder()
                                                                  .setIdentifier(userGroupIdentifier)
                                                                  .setOrgIdentifier(orgIdentifier)
                                                                  .setProjectIdentifier(projectIdentifier)
                                                                  .build())))
            .orgIdentifier(orgIdentifier)
            .projectIdentifier(projectIdentifier)
            .expressionFunctorToken(1234L)
            .build();
    assertEquality(msTeamChannel, expectedChannel);

    //// with execution history
    basicApprovalInstance.setIncludePipelineExecutionHistory(true);
    msTeamChannel = (MSTeamChannel) approvalNotificationHandler.getNotificationChannel(
        basicApprovalInstance, NotificationChannelType.MSTEAMS, Collections.singletonList(userGroupDTO), templateData);
    expectedChannel.setTemplateId(PredefinedTemplate.HARNESS_APPROVAL_EXECUTION_NOTIFICATION_MSTEAMS.getIdentifier());
    assertEquality(msTeamChannel, expectedChannel);

    // approval rejected notification
    basicApprovalInstance.setStatus(ApprovalStatus.REJECTED);
    //// without execution history
    basicApprovalInstance.setIncludePipelineExecutionHistory(false);
    msTeamChannel = (MSTeamChannel) approvalNotificationHandler.getNotificationChannel(
        basicApprovalInstance, NotificationChannelType.MSTEAMS, Collections.singletonList(userGroupDTO), templateData);
    expectedChannel.setTemplateId(PredefinedTemplate.HARNESS_APPROVAL_ACTION_NOTIFICATION_MSTEAMS.getIdentifier());
    assertEquality(msTeamChannel, expectedChannel);
    //// with execution history
    basicApprovalInstance.setIncludePipelineExecutionHistory(true);
    msTeamChannel = (MSTeamChannel) approvalNotificationHandler.getNotificationChannel(
        basicApprovalInstance, NotificationChannelType.MSTEAMS, Collections.singletonList(userGroupDTO), templateData);
    expectedChannel.setTemplateId(
        PredefinedTemplate.HARNESS_APPROVAL_ACTION_EXECUTION_NOTIFICATION_MSTEAMS.getIdentifier());
    assertEquality(msTeamChannel, expectedChannel);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetEmailNotificationChannel() {
    basicApprovalInstance.setStatus(ApprovalStatus.WAITING);
    // approval waiting notification
    //// without execution history
    basicApprovalInstance.setIncludePipelineExecutionHistory(false);
    EmailConfigDTO emailConfigDTO =
        EmailConfigDTO.builder().groupEmail("test@harness.io").sendEmailToAllUsers(false).build();
    UserGroupDTO userGroupDTO = UserGroupDTO.builder()
                                    .identifier(userGroupIdentifier)
                                    .accountIdentifier(accountId)
                                    .orgIdentifier(orgIdentifier)
                                    .projectIdentifier(projectIdentifier)
                                    .notificationConfigs(Collections.singletonList(emailConfigDTO))
                                    .build();
    Map<String, String> templateData =
        Collections.singletonMap(ApprovalSummaryKeys.approvalMessage, "this is first line \n this is second line");
    Map<String, String> expectedTemplateData =
        Collections.singletonMap(ApprovalSummaryKeys.approvalMessage, "this is first line <br> this is second line");
    EmailChannel emailChannel = (EmailChannel) approvalNotificationHandler.getNotificationChannel(
        basicApprovalInstance, NotificationChannelType.EMAIL, Collections.singletonList(userGroupDTO), templateData);
    EmailChannel expectedChannel =
        EmailChannel.builder()
            .accountId(accountId)
            .team(Team.PIPELINE)
            .templateId(PredefinedTemplate.HARNESS_APPROVAL_NOTIFICATION_EMAIL.getIdentifier())
            .templateData(expectedTemplateData)
            .userGroups(new ArrayList<>(Collections.singleton(NotificationRequest.UserGroup.newBuilder()
                                                                  .setIdentifier(userGroupIdentifier)
                                                                  .setOrgIdentifier(orgIdentifier)
                                                                  .setProjectIdentifier(projectIdentifier)
                                                                  .build())))
            .recipients(Collections.emptyList())
            .build();
    assertEquality(emailChannel, expectedChannel);

    //// with execution history
    basicApprovalInstance.setIncludePipelineExecutionHistory(true);
    emailChannel = (EmailChannel) approvalNotificationHandler.getNotificationChannel(
        basicApprovalInstance, NotificationChannelType.EMAIL, Collections.singletonList(userGroupDTO), templateData);
    expectedChannel.setTemplateId(PredefinedTemplate.HARNESS_APPROVAL_EXECUTION_NOTIFICATION_EMAIL.getIdentifier());
    assertEquality(emailChannel, expectedChannel);

    // approval rejected notification
    basicApprovalInstance.setStatus(ApprovalStatus.REJECTED);
    //// without execution history
    basicApprovalInstance.setIncludePipelineExecutionHistory(false);
    emailChannel = (EmailChannel) approvalNotificationHandler.getNotificationChannel(
        basicApprovalInstance, NotificationChannelType.EMAIL, Collections.singletonList(userGroupDTO), templateData);
    expectedChannel.setTemplateId(PredefinedTemplate.HARNESS_APPROVAL_ACTION_NOTIFICATION_EMAIL.getIdentifier());
    assertEquality(emailChannel, expectedChannel);
    //// with execution history
    basicApprovalInstance.setIncludePipelineExecutionHistory(true);
    emailChannel = (EmailChannel) approvalNotificationHandler.getNotificationChannel(
        basicApprovalInstance, NotificationChannelType.EMAIL, Collections.singletonList(userGroupDTO), templateData);
    expectedChannel.setTemplateId(
        PredefinedTemplate.HARNESS_APPROVAL_ACTION_EXECUTION_NOTIFICATION_EMAIL.getIdentifier());
    assertEquality(emailChannel, expectedChannel);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testSendNotificationForApprovalAction() throws Exception {
    try (MockedConstruction<NGLogCallback> ngLogCallback = mockConstruction(NGLogCallback.class)) {
      String url = "https://qa.harness.io/ng/#/account/zEaak-FLS425IEO7OLzMUg/cd/orgs/CV/projects/Brijesh_Dhakar/"
          + "pipelines/DockerTest/executions/szmvyw4wQR2W4_iKkq9bfQ/pipeline";
      TriggeredBy triggeredBy = TriggeredBy.newBuilder().setIdentifier(userId).setUuid(userUuid).build();
      ExecutionTriggerInfo triggerInfo = ExecutionTriggerInfo.newBuilder().setTriggeredBy(triggeredBy).build();
      ExecutionMetadata executionMetadata = ExecutionMetadata.newBuilder().setTriggerInfo(triggerInfo).build();
      Ambiance ambiance = Ambiance.newBuilder()
                              .putSetupAbstractions("accountId", accountId)
                              .putSetupAbstractions("orgIdentifier", orgIdentifier)
                              .putSetupAbstractions("projectIdentifier", projectIdentifier)
                              .putSetupAbstractions("pipelineIdentifier", pipelineIdentifier)
                              .setMetadata(executionMetadata)
                              .build();
      HarnessApprovalInstance approvalInstance =
          HarnessApprovalInstance.builder()
              .approvers(
                  ApproversDTO.builder()
                      .userGroups(new ArrayList<>(Arrays.asList("proj_faulty", "proj_right", "org.org_faulty",
                          "org.org_right", "account.acc_faulty", "account.acc_right", "proj_faulty", "proj_right")))
                      .build())
              .approvalActivities(Arrays.asList(HarnessApprovalActivity.builder()
                                                    .user(EmbeddedUser.builder().email("email").build())
                                                    .approvedAt(20000000)
                                                    .action(HarnessApprovalAction.APPROVE)
                                                    .build(),
                  HarnessApprovalActivity.builder()
                      .user(EmbeddedUser.builder().email("email2").build())
                      .approvedAt(20000020)
                      .action(HarnessApprovalAction.APPROVE)
                      .build())

                      )
              .build();
      approvalInstance.setAmbiance(ambiance);
      approvalInstance.setCreatedAt(System.currentTimeMillis());
      approvalInstance.setDeadline(2L * System.currentTimeMillis());
      approvalInstance.setType(ApprovalType.HARNESS_APPROVAL);
      approvalInstance.setStatus(ApprovalStatus.APPROVED);

      PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                          .accountId(accountId)
                                                                          .orgIdentifier(orgIdentifier)
                                                                          .projectIdentifier(projectIdentifier)
                                                                          .pipelineIdentifier(pipelineIdentifier)
                                                                          .name(pipelineName)
                                                                          .build();
      doReturn(pipelineExecutionSummaryEntity)
          .when(pmsExecutionService)
          .getPipelineExecutionSummaryEntity(anyString(), anyString(), anyBoolean());
      List<NotificationSettingConfigDTO> notificationSettingConfigDTOS = new ArrayList<>();
      notificationSettingConfigDTOS.add(SlackConfigDTO.builder().build());
      notificationSettingConfigDTOS.add(EmailConfigDTO.builder().build());
      notificationSettingConfigDTOS.add(MicrosoftTeamsConfigDTO.builder().build());

      List<UserGroupDTO> userGroupDTOS =
          new ArrayList<>(Arrays.asList(UserGroupDTO.builder()
                                            .identifier("proj_right")
                                            .accountIdentifier(accountId)
                                            .orgIdentifier(orgIdentifier)
                                            .projectIdentifier(projectIdentifier)
                                            .notificationConfigs(notificationSettingConfigDTOS)
                                            .build(),
              UserGroupDTO.builder()
                  .identifier("org_right")
                  .accountIdentifier(accountId)
                  .orgIdentifier(orgIdentifier)
                  .notificationConfigs(notificationSettingConfigDTOS)
                  .build(),
              UserGroupDTO.builder()
                  .identifier("acc_right")
                  .accountIdentifier(accountId)
                  .notificationConfigs(notificationSettingConfigDTOS)
                  .build()));

      Call<ResponseDTO<List<UserGroupDTO>>> responseDTOCall = mock(Call.class);
      when(userGroupClient.getFilteredUserGroups(any())).thenReturn(responseDTOCall);
      ResponseDTO<List<UserGroupDTO>> restResponse = ResponseDTO.newResponse(userGroupDTOS);
      Response<ResponseDTO<List<UserGroupDTO>>> response = Response.success(restResponse);
      when(responseDTOCall.execute()).thenReturn(response);

      approvalInstance.setValidatedUserGroups(userGroupDTOS);

      doReturn(url).when(notificationHelper).generateUrl(eq(ambiance), any(PipelineExecutionSummaryEntity.class));
      when(logStreamingStepClientFactory.getLogStreamingStepClient(ambiance))
          .thenReturn(Mockito.mock(ILogStreamingStepClient.class));

      approvalNotificationHandler.sendNotification(approvalInstance, ambiance);
      ArgumentCaptor<NotificationChannel> notificationChannelArgumentCaptor =
          ArgumentCaptor.forClass(NotificationChannel.class);
      verify(notificationClient, times(3)).sendNotificationAsync(notificationChannelArgumentCaptor.capture());
      List<NotificationChannel> notificationChannels = notificationChannelArgumentCaptor.getAllValues();
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.pipelineName))
          .isEqualTo(pipelineName);
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.orgName)).isEqualTo(orgName);
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.projectName))
          .isEqualTo(projectName);

      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.status))
          .isEqualTo(ApprovalStatus.APPROVED.toString().toLowerCase(Locale.ROOT));
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.action)).contains("email");
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.currentStageName)).isEqualTo("");

      // get userId in triggeredBy because email is not present
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.triggeredBy)).isEqualTo(userId);
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.pipelineExecutionLink))
          .isEqualTo(url);
      // checking whether line breaks are added properly in case of multiple approval activities for email only
      // two comparisons are made to prevent flakiness depend on system's locale
      String actionNonEmail = notificationChannels.get(1).getTemplateData().get(ApprovalSummaryKeys.action);
      assertThat(
          actionNonEmail.equals("email approved on Jan 01, 05:33 AM GMT   \nemail2 approved on Jan 01, 05:33 AM GMT   ")
          || actionNonEmail.equals(
              "email approved on Jan 01, 05:33 am GMT   \nemail2 approved on Jan 01, 05:33 am GMT   "))
          .isTrue();
      String actionEmail = notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.action);
      assertThat(
          actionEmail.equals("email approved on Jan 01, 05:33 AM GMT   <br>email2 approved on Jan 01, 05:33 AM GMT   ")
          || actionEmail.equals(
              "email approved on Jan 01, 05:33 am GMT   <br>email2 approved on Jan 01, 05:33 am GMT   "))
          .isTrue();

      assertThat(notificationChannels.get(2).getTemplateId())
          .isEqualTo(PredefinedTemplate.HARNESS_APPROVAL_ACTION_NOTIFICATION_MSTEAMS.getIdentifier());
      assertThat(notificationChannels.get(2).getTeam()).isEqualTo(Team.PIPELINE);
      verify(pmsExecutionService, times(1)).getPipelineExecutionSummaryEntity(anyString(), anyString(), anyBoolean());
    }
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testSendNotificationForRejectionAction() throws Exception {
    try (MockedConstruction<NGLogCallback> ngLogCallback = mockConstruction(NGLogCallback.class)) {
      String url = "https://qa.harness.io/ng/#/account/zEaak-FLS425IEO7OLzMUg/cd/orgs/CV/projects/Brijesh_Dhakar/"
          + "pipelines/DockerTest/executions/szmvyw4wQR2W4_iKkq9bfQ/pipeline";
      TriggeredBy triggeredBy = TriggeredBy.newBuilder().setIdentifier(userId).setUuid(userUuid).build();
      ExecutionTriggerInfo triggerInfo = ExecutionTriggerInfo.newBuilder().setTriggeredBy(triggeredBy).build();
      ExecutionMetadata executionMetadata = ExecutionMetadata.newBuilder().setTriggerInfo(triggerInfo).build();
      Ambiance ambiance = Ambiance.newBuilder()
                              .putSetupAbstractions("accountId", accountId)
                              .putSetupAbstractions("orgIdentifier", orgIdentifier)
                              .putSetupAbstractions("projectIdentifier", projectIdentifier)
                              .putSetupAbstractions("pipelineIdentifier", pipelineIdentifier)
                              .setMetadata(executionMetadata)
                              .build();
      HarnessApprovalInstance approvalInstance =
          HarnessApprovalInstance.builder()
              .approvers(
                  ApproversDTO.builder()
                      .userGroups(new ArrayList<>(Arrays.asList("proj_faulty", "proj_right", "org.org_faulty",
                          "org.org_right", "account.acc_faulty", "account.acc_right", "proj_faulty", "proj_right")))
                      .build())
              .approvalActivities(Collections.singletonList(HarnessApprovalActivity.builder()
                                                                .user(EmbeddedUser.builder().email("email").build())
                                                                .approvedAt(20000000)
                                                                .action(HarnessApprovalAction.REJECT)
                                                                .build())

                      )
              .build();
      approvalInstance.setAmbiance(ambiance);
      approvalInstance.setCreatedAt(System.currentTimeMillis());
      approvalInstance.setDeadline(2L * System.currentTimeMillis());
      approvalInstance.setType(ApprovalType.HARNESS_APPROVAL);
      approvalInstance.setStatus(ApprovalStatus.REJECTED);

      PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                          .accountId(accountId)
                                                                          .orgIdentifier(orgIdentifier)
                                                                          .projectIdentifier(projectIdentifier)
                                                                          .pipelineIdentifier(pipelineIdentifier)
                                                                          .name(pipelineName)
                                                                          .build();
      doReturn(pipelineExecutionSummaryEntity)
          .when(pmsExecutionService)
          .getPipelineExecutionSummaryEntity(anyString(), anyString(), anyBoolean());
      List<NotificationSettingConfigDTO> notificationSettingConfigDTOS = new ArrayList<>();
      notificationSettingConfigDTOS.add(SlackConfigDTO.builder().build());
      notificationSettingConfigDTOS.add(EmailConfigDTO.builder().build());
      notificationSettingConfigDTOS.add(MicrosoftTeamsConfigDTO.builder().build());

      List<UserGroupDTO> userGroupDTOS =
          new ArrayList<>(Arrays.asList(UserGroupDTO.builder()
                                            .identifier("proj_right")
                                            .accountIdentifier(accountId)
                                            .orgIdentifier(orgIdentifier)
                                            .projectIdentifier(projectIdentifier)
                                            .notificationConfigs(notificationSettingConfigDTOS)
                                            .build(),
              UserGroupDTO.builder()
                  .identifier("org_right")
                  .accountIdentifier(accountId)
                  .orgIdentifier(orgIdentifier)
                  .notificationConfigs(notificationSettingConfigDTOS)
                  .build(),
              UserGroupDTO.builder()
                  .identifier("acc_right")
                  .accountIdentifier(accountId)
                  .notificationConfigs(notificationSettingConfigDTOS)
                  .build()));

      Call<ResponseDTO<List<UserGroupDTO>>> responseDTOCall = mock(Call.class);
      when(userGroupClient.getFilteredUserGroups(any())).thenReturn(responseDTOCall);
      ResponseDTO<List<UserGroupDTO>> restResponse = ResponseDTO.newResponse(userGroupDTOS);
      Response<ResponseDTO<List<UserGroupDTO>>> response = Response.success(restResponse);
      when(responseDTOCall.execute()).thenReturn(response);

      approvalInstance.setValidatedUserGroups(userGroupDTOS);

      doReturn(url).when(notificationHelper).generateUrl(eq(ambiance), any(PipelineExecutionSummaryEntity.class));
      when(logStreamingStepClientFactory.getLogStreamingStepClient(ambiance))
          .thenReturn(Mockito.mock(ILogStreamingStepClient.class));

      approvalNotificationHandler.sendNotification(approvalInstance, ambiance);
      ArgumentCaptor<NotificationChannel> notificationChannelArgumentCaptor =
          ArgumentCaptor.forClass(NotificationChannel.class);
      verify(notificationClient, times(3)).sendNotificationAsync(notificationChannelArgumentCaptor.capture());
      List<NotificationChannel> notificationChannels = notificationChannelArgumentCaptor.getAllValues();
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.pipelineName))
          .isEqualTo(pipelineName);
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.orgName)).isEqualTo(orgName);
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.projectName))
          .isEqualTo(projectName);

      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.status))
          .isEqualTo(ApprovalStatus.REJECTED.toString().toLowerCase(Locale.ROOT));
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.action)).contains("email");
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.currentStageName)).isEqualTo("");

      // get userId in triggeredBy because email is not present
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.triggeredBy)).isEqualTo(userId);
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.pipelineExecutionLink))
          .isEqualTo(url);
      assertThat(notificationChannels.get(2).getTemplateId())
          .isEqualTo(PredefinedTemplate.HARNESS_APPROVAL_ACTION_NOTIFICATION_MSTEAMS.getIdentifier());
      assertThat(notificationChannels.get(2).getTeam()).isEqualTo(Team.PIPELINE);
      verify(pmsExecutionService, times(1)).getPipelineExecutionSummaryEntity(anyString(), anyString(), anyBoolean());
    }
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testSendNotificationForApprovalActionWithStageMetadata() throws IOException {
    // when FF on, CD stage with approval completed, followed by 2 upcoming custom stages
    try (MockedConstruction<NGLogCallback> ngLogCallback = mockConstruction(NGLogCallback.class)) {
      String url = "https://qa.harness.io/ng/#/account/zEaak-FLS425IEO7OLzMUg/cd/orgs/CV/projects/Brijesh_Dhakar/"
          + "pipelines/DockerTest/executions/szmvyw4wQR2W4_iKkq9bfQ/pipeline";

      GraphLayoutNode graphLayoutNode1 =
          GraphLayoutNode.newBuilder()
              .setNodeIdentifier("nodeIdentifier1")
              .setNodeType("Deployment")
              .setNodeUUID("aBcDeFgH")
              .setName("cd stage")
              .setNodeGroup("STAGE")
              .setEdgeLayoutList(EdgeLayoutList.newBuilder().addNextIds(PlanCreatorConstants.NEXT_ID).build())
              .build();
      GraphLayoutNodeDTO graphLayoutNodeDTO1 = GraphLayoutDtoMapper.toDto(graphLayoutNode1);
      graphLayoutNodeDTO1.setModule("cd");
      graphLayoutNodeDTO1.setStatus(ExecutionStatus.SUCCESS);
      HashMap<String, GraphLayoutNodeDTO> layoutNodeDTOMap = new HashMap<>();
      layoutNodeDTOMap.put(startingNodeId, graphLayoutNodeDTO1);

      GraphLayoutNode graphLayoutNode2 =
          GraphLayoutNode.newBuilder()
              .setNodeIdentifier("nodeIdentifier2")
              .setNodeType("Custom")
              .setNodeUUID("aBcDeFgH")
              .setName("custom stage upcoming 1")
              .setNodeGroup("STAGE")
              .setEdgeLayoutList(EdgeLayoutList.newBuilder().addNextIds(thirdNodeId).build())
              .build();
      GraphLayoutNodeDTO graphLayoutNodeDTO2 = GraphLayoutDtoMapper.toDto(graphLayoutNode2);
      layoutNodeDTOMap.put(PlanCreatorConstants.NEXT_ID, graphLayoutNodeDTO2);

      GraphLayoutNode graphLayoutNode3 = GraphLayoutNode.newBuilder()
                                             .setNodeIdentifier(thirdNodeId)
                                             .setNodeType("Custom")
                                             .setNodeUUID("aBcDeFgH")
                                             .setName("custom stage upcoming 2")
                                             .setNodeGroup("STAGE")
                                             .build();
      GraphLayoutNodeDTO graphLayoutNodeDTO3 = GraphLayoutDtoMapper.toDto(graphLayoutNode3);
      layoutNodeDTOMap.put(thirdNodeId, graphLayoutNodeDTO3);

      pipelineExecutionSummaryEntity.setLayoutNodeMap(layoutNodeDTOMap);
      doReturn(pipelineExecutionSummaryEntity)
          .when(pmsExecutionService)
          .getPipelineExecutionSummaryEntity(anyString(), anyString(), anyBoolean());

      mockUserGroupResponse(basicUserGroupDTOS);

      Ambiance ambiance = buildAmbianceWithStageId("nodeIdentifier1");
      doReturn(url).when(notificationHelper).generateUrl(eq(ambiance), any(PipelineExecutionSummaryEntity.class));

      doAnswer(invocationOnMock -> {
        Set<String> formattedStages = invocationOnMock.getArgument(1);
        formattedStages.add("cd stage : \n"
            + "     Service  :  service name\n"
            + "     Environment  :  env name\n"
            + "     Infrastructure Definition  :  infra name");
        return null;
      })
          .when(stageMetadataNotificationHelper)
          .setFormattedSummaryOfFinishedStages(stageSummaryArgumentCaptors.get(0).capture(), anySet(), any());

      doAnswer(invocationOnMock -> {
        Set<String> formattedStages = invocationOnMock.getArgument(1);
        return null;
      })
          .when(stageMetadataNotificationHelper)
          .setFormattedSummaryOfRunningStages(stageSummaryArgumentCaptors.get(1).capture(), anySet(), any(), any());

      doAnswer(invocationOnMock -> {
        Set<String> formattedStages = invocationOnMock.getArgument(1);
        formattedStages.addAll(List.of("custom stage upcoming 1", "custom stage upcoming 2"));
        return null;
      })
          .when(stageMetadataNotificationHelper)
          .setFormattedSummaryOfUpcomingStages(stageSummaryArgumentCaptors.get(2).capture(), anySet(), any(), any());

      basicApprovalInstance.setStatus(ApprovalStatus.APPROVED);
      basicApprovalInstance.setApprovalActivities(Arrays.asList(HarnessApprovalActivity.builder()
                                                                    .user(EmbeddedUser.builder().email("email").build())
                                                                    .approvedAt(20000000)
                                                                    .action(HarnessApprovalAction.APPROVE)
                                                                    .build(),
          HarnessApprovalActivity.builder()
              .user(EmbeddedUser.builder().email("email2").build())
              .approvedAt(20000020)
              .action(HarnessApprovalAction.APPROVE)
              .build()));
      approvalNotificationHandler.sendNotification(basicApprovalInstance, ambiance);

      ArgumentCaptor<NotificationChannel> notificationChannelArgumentCaptor =
          ArgumentCaptor.forClass(NotificationChannel.class);
      verify(notificationClient, times(2)).sendNotificationAsync(notificationChannelArgumentCaptor.capture());
      List<NotificationChannel> notificationChannels = notificationChannelArgumentCaptor.getAllValues();
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.runningStages)).isEqualTo("N/A");
      assertThat(notificationChannels.get(1).getTemplateData().get(ApprovalSummaryKeys.finishedStages))
          .isEqualTo("cd stage : \n"
              + "     Service  :  service name\n"
              + "     Environment  :  env name\n"
              + "     Infrastructure Definition  :  infra name");
      assertThat(notificationChannels.get(1).getTemplateData().get(ApprovalSummaryKeys.upcomingStages))
          .isEqualTo("custom stage upcoming 1\n custom stage upcoming 2");

      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.pipelineName))
          .isEqualTo(pipelineIdentifier);
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.orgName)).isEqualTo(orgName);
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.projectName))
          .isEqualTo(projectName);

      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.status))
          .isEqualTo(ApprovalStatus.APPROVED.toString().toLowerCase(Locale.ROOT));
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.action)).contains("email");
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.currentStageName))
          .isEqualTo("cd stage");

      // get userId in triggeredBy because email is not present
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.triggeredBy))
          .isEqualTo("Unknown");
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.pipelineExecutionLink))
          .isEqualTo(url);
      // checking whether line breaks are added properly in case of multiple approval activities for email only
      // two comparisons are made to prevent flakiness depend on system's locale
      String actionNonEmail = notificationChannels.get(1).getTemplateData().get(ApprovalSummaryKeys.action);
      assertThat(
          actionNonEmail.equals("email approved on Jan 01, 05:33 AM GMT   \nemail2 approved on Jan 01, 05:33 AM GMT   ")
          || actionNonEmail.equals(
              "email approved on Jan 01, 05:33 am GMT   \nemail2 approved on Jan 01, 05:33 am GMT   "))
          .isTrue();
      String actionEmail = notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.action);
      assertThat(
          actionEmail.equals("email approved on Jan 01, 05:33 AM GMT   <br>email2 approved on Jan 01, 05:33 AM GMT   ")
          || actionEmail.equals(
              "email approved on Jan 01, 05:33 am GMT   <br>email2 approved on Jan 01, 05:33 am GMT   "))
          .isTrue();

      assertThat(notificationChannels.get(0).getTeam()).isEqualTo(Team.PIPELINE);
      verify(pmsExecutionService, times(1)).getPipelineExecutionSummaryEntity(anyString(), anyString(), anyBoolean());

      assertThat(stageSummaryArgumentCaptors.get(0)
                     .getValue()
                     .stream()
                     .map(StageSummary::getFormattedEntityName)
                     .collect(Collectors.toSet()))
          .contains("cd stage");
      assertThat(stageSummaryArgumentCaptors.get(1)
                     .getValue()
                     .stream()
                     .map(StageSummary::getFormattedEntityName)
                     .collect(Collectors.toSet()))
          .isEmpty();
      assertThat(stageSummaryArgumentCaptors.get(2)
                     .getValue()
                     .stream()
                     .map(StageSummary::getFormattedEntityName)
                     .collect(Collectors.toSet()))
          .contains("custom stage upcoming 1", "custom stage upcoming 2");
    }
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testSendNotificationForApprovalActionWithParallelStages() throws Exception {
    try (MockedConstruction<NGLogCallback> ngLogCallback = mockConstruction(NGLogCallback.class)) {
      String url = "https://qa.harness.io/ng/#/account/zEaak-FLS425IEO7OLzMUg/cd/orgs/CV/projects/Brijesh_Dhakar/"
          + "pipelines/DockerTest/executions/szmvyw4wQR2W4_iKkq9bfQ/pipeline";
      TriggeredBy triggeredBy = TriggeredBy.newBuilder().setIdentifier(userId).setUuid(userUuid).build();
      ExecutionTriggerInfo triggerInfo = ExecutionTriggerInfo.newBuilder().setTriggeredBy(triggeredBy).build();
      ExecutionMetadata executionMetadata = ExecutionMetadata.newBuilder().setTriggerInfo(triggerInfo).build();
      Ambiance ambiance = Ambiance.newBuilder()
                              .putSetupAbstractions("accountId", accountId)
                              .putSetupAbstractions("orgIdentifier", orgIdentifier)
                              .putSetupAbstractions("projectIdentifier", projectIdentifier)
                              .putSetupAbstractions("pipelineIdentifier", pipelineIdentifier)
                              .setMetadata(executionMetadata)
                              .addLevels(Level.newBuilder().setGroup("STAGE").setIdentifier("stage_approval1").build())
                              .build();
      HarnessApprovalInstance approvalInstance =
          HarnessApprovalInstance.builder()
              .approvers(
                  ApproversDTO.builder()
                      .userGroups(new ArrayList<>(Arrays.asList("proj_faulty", "proj_right", "org.org_faulty",
                          "org.org_right", "account.acc_faulty", "account.acc_right", "proj_faulty", "proj_right")))
                      .build())
              .approvalActivities(Collections.singletonList(HarnessApprovalActivity.builder()
                                                                .user(EmbeddedUser.builder().email("email").build())
                                                                .approvedAt(20000000)
                                                                .action(HarnessApprovalAction.APPROVE)
                                                                .build())

                      )
              .build();
      approvalInstance.setAmbiance(ambiance);
      approvalInstance.setCreatedAt(System.currentTimeMillis());
      approvalInstance.setDeadline(2L * System.currentTimeMillis());
      approvalInstance.setType(ApprovalType.HARNESS_APPROVAL);
      approvalInstance.setStatus(ApprovalStatus.APPROVED);
      approvalInstance.setIncludePipelineExecutionHistory(true);

      GraphLayoutNode graphLayoutNode1 =
          GraphLayoutNode.newBuilder()
              .setNodeIdentifier("parallelnodeIdentifier_1parallel")
              .setNodeType("parallel")
              .setNodeUUID("nodeIdentifier_1")
              .setName("")
              .setNodeGroup("STAGE")
              .setEdgeLayoutList(EdgeLayoutList.newBuilder()
                                     .addNextIds("stage_approval1")
                                     .addAllCurrentNodeChildren(List.of("stageBuild-1", "stageBuild-2"))
                                     .build())
              .build();

      GraphLayoutNode graphLayoutNode2 = GraphLayoutNode.newBuilder()
                                             .setNodeIdentifier("stageBuild-1")
                                             .setNodeType("Custom")
                                             .setNodeUUID("stageBuild-1")
                                             .setName("stageBuild-1")
                                             .setNodeGroup("STAGE")
                                             .build();

      GraphLayoutNode graphLayoutNode3 = GraphLayoutNode.newBuilder()
                                             .setNodeIdentifier("stageBuild-2")
                                             .setNodeType("Custom")
                                             .setNodeUUID("stageBuild-2")
                                             .setName("stageBuild-2")
                                             .setNodeGroup("STAGE")
                                             .build();

      GraphLayoutNode graphLayoutNode4 =
          GraphLayoutNode.newBuilder()
              .setNodeIdentifier("stage_approval1")
              .setNodeType("Approval")
              .setNodeUUID("stage_approval1")
              .setName("Approval stage name")
              .setNodeGroup("STAGE")
              .setEdgeLayoutList(EdgeLayoutList.newBuilder().addNextIds("stageDeploy-1").build())
              .build();

      GraphLayoutNode graphLayoutNode5 =
          GraphLayoutNode.newBuilder()
              .setNodeIdentifier("stageDeploy-1")
              .setNodeType("Custom")
              .setNodeUUID("stageDeploy-1")
              .setName("stageDeploy-1")
              .setNodeGroup("STAGE")
              .setEdgeLayoutList(EdgeLayoutList.newBuilder().addNextIds("stageDeploy-2").build())
              .build();

      GraphLayoutNode graphLayoutNode6 = GraphLayoutNode.newBuilder()
                                             .setNodeIdentifier("stageDeploy-2")
                                             .setNodeType("Custom")
                                             .setNodeUUID("stageDeploy-2")
                                             .setName("stageDeploy-2")
                                             .setNodeGroup("STAGE")
                                             .build();

      GraphLayoutNodeDTO graphLayoutNodeDTO1 = GraphLayoutDtoMapper.toDto(graphLayoutNode1);
      graphLayoutNodeDTO1.setStatus(ExecutionStatus.NOTSTARTED);
      GraphLayoutNodeDTO graphLayoutNodeDTO2 = GraphLayoutDtoMapper.toDto(graphLayoutNode2);
      graphLayoutNodeDTO2.setStatus(ExecutionStatus.SUCCESS);
      GraphLayoutNodeDTO graphLayoutNodeDTO3 = GraphLayoutDtoMapper.toDto(graphLayoutNode3);
      graphLayoutNodeDTO3.setStatus(ExecutionStatus.SUCCESS);
      GraphLayoutNodeDTO graphLayoutNodeDTO4 = GraphLayoutDtoMapper.toDto(graphLayoutNode4);
      graphLayoutNodeDTO4.setStatus(ExecutionStatus.APPROVAL_WAITING);
      GraphLayoutNodeDTO graphLayoutNodeDTO5 = GraphLayoutDtoMapper.toDto(graphLayoutNode5);
      graphLayoutNodeDTO5.setStatus(ExecutionStatus.NOTSTARTED);
      GraphLayoutNodeDTO graphLayoutNodeDTO6 = GraphLayoutDtoMapper.toDto(graphLayoutNode6);
      graphLayoutNodeDTO6.setStatus(ExecutionStatus.NOTSTARTED);

      HashMap<String, GraphLayoutNodeDTO> layoutNodeDTOMap = new HashMap<>();
      layoutNodeDTOMap.put(startingNodeId, graphLayoutNodeDTO1);
      layoutNodeDTOMap.put("stageBuild-1", graphLayoutNodeDTO2);
      layoutNodeDTOMap.put("stageBuild-2", graphLayoutNodeDTO3);
      layoutNodeDTOMap.put("stage_approval1", graphLayoutNodeDTO4);
      layoutNodeDTOMap.put("stageDeploy-1", graphLayoutNodeDTO5);
      layoutNodeDTOMap.put("stageDeploy-2", graphLayoutNodeDTO6);

      PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                          .accountId(accountId)
                                                                          .orgIdentifier(orgIdentifier)
                                                                          .projectIdentifier(projectIdentifier)
                                                                          .pipelineIdentifier(pipelineIdentifier)
                                                                          .name(pipelineName)
                                                                          .layoutNodeMap(layoutNodeDTOMap)
                                                                          .startingNodeId(startingNodeId)
                                                                          .build();
      doReturn(pipelineExecutionSummaryEntity)
          .when(pmsExecutionService)
          .getPipelineExecutionSummaryEntity(anyString(), anyString(), anyBoolean());
      List<NotificationSettingConfigDTO> notificationSettingConfigDTOS = new ArrayList<>();
      notificationSettingConfigDTOS.add(SlackConfigDTO.builder().build());
      List<UserGroupDTO> userGroupDTOS =
          new ArrayList<>(Arrays.asList(UserGroupDTO.builder()
                                            .identifier("proj_right")
                                            .accountIdentifier(accountId)
                                            .orgIdentifier(orgIdentifier)
                                            .projectIdentifier(projectIdentifier)
                                            .notificationConfigs(notificationSettingConfigDTOS)
                                            .build()));

      Call<ResponseDTO<List<UserGroupDTO>>> responseDTOCall = mock(Call.class);
      when(userGroupClient.getFilteredUserGroups(any())).thenReturn(responseDTOCall);
      ResponseDTO<List<UserGroupDTO>> restResponse = ResponseDTO.newResponse(userGroupDTOS);
      Response<ResponseDTO<List<UserGroupDTO>>> response = Response.success(restResponse);
      when(responseDTOCall.execute()).thenReturn(response);

      approvalInstance.setValidatedUserGroups(userGroupDTOS);

      doReturn(url).when(notificationHelper).generateUrl(eq(ambiance), any(PipelineExecutionSummaryEntity.class));
      when(logStreamingStepClientFactory.getLogStreamingStepClient(ambiance))
          .thenReturn(Mockito.mock(ILogStreamingStepClient.class));

      doAnswer(invocationOnMock -> {
        Set<String> formattedStages = invocationOnMock.getArgument(1);
        formattedStages.addAll(List.of("stageBuild-1", "stageBuild-2"));
        return null;
      })
          .when(stageMetadataNotificationHelper)
          .setFormattedSummaryOfFinishedStages(stageSummaryArgumentCaptors.get(0).capture(), anySet(), any());

      doAnswer(invocationOnMock -> {
        Set<String> formattedStages = invocationOnMock.getArgument(1);
        formattedStages.add("Approval stage name");
        return null;
      })
          .when(stageMetadataNotificationHelper)
          .setFormattedSummaryOfRunningStages(stageSummaryArgumentCaptors.get(1).capture(), anySet(), any(), any());

      doAnswer(invocationOnMock -> {
        Set<String> formattedStages = invocationOnMock.getArgument(1);
        formattedStages.addAll(List.of("stageDeploy-1", "stageDeploy-2"));
        return null;
      })
          .when(stageMetadataNotificationHelper)
          .setFormattedSummaryOfUpcomingStages(stageSummaryArgumentCaptors.get(2).capture(), anySet(), any(), any());

      approvalNotificationHandler.sendNotification(approvalInstance, ambiance);
      ArgumentCaptor<NotificationChannel> notificationChannelArgumentCaptor =
          ArgumentCaptor.forClass(NotificationChannel.class);
      verify(notificationClient, times(1)).sendNotificationAsync(notificationChannelArgumentCaptor.capture());
      List<NotificationChannel> notificationChannels = notificationChannelArgumentCaptor.getAllValues();
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.pipelineName))
          .isEqualTo(pipelineName);
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.orgName)).isEqualTo(orgName);
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.projectName))
          .isEqualTo(projectName);

      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.finishedStages))
          .isEqualTo("stageBuild-1\n stageBuild-2");
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.runningStages))
          .isEqualTo("Approval stage name");
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.upcomingStages))
          .isEqualTo("stageDeploy-1\n stageDeploy-2");

      assertThat(stageSummaryArgumentCaptors.get(0)
                     .getValue()
                     .stream()
                     .map(StageSummary::getFormattedEntityName)
                     .collect(Collectors.toSet()))
          .contains("stageBuild-1", "stageBuild-2");
      assertThat(stageSummaryArgumentCaptors.get(1)
                     .getValue()
                     .stream()
                     .map(StageSummary::getFormattedEntityName)
                     .collect(Collectors.toSet()))
          .contains("Approval stage name");
      assertThat(stageSummaryArgumentCaptors.get(2)
                     .getValue()
                     .stream()
                     .map(StageSummary::getFormattedEntityName)
                     .collect(Collectors.toSet()))
          .contains("stageDeploy-1", "stageDeploy-2");

      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.status))
          .isEqualTo(ApprovalStatus.APPROVED.toString().toLowerCase(Locale.ROOT));

      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.triggeredBy)).isEqualTo(userId);
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.pipelineExecutionLink))
          .isEqualTo(url);
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.currentStageName))
          .isEqualTo("Approval stage name");
      verify(pmsExecutionService, times(1)).getPipelineExecutionSummaryEntity(anyString(), anyString(), anyBoolean());
    }
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testSendNotificationForTerminalParallelStages() throws IOException {
    // isIncludePipelineExecutionHistory true with 1 approval stages; 2 parallel custom stages; FF on
    try (MockedConstruction<NGLogCallback> ngLogCallback = mockConstruction(NGLogCallback.class)) {
      String url = "https://qa.harness.io/ng/#/account/zEaak-FLS425IEO7OLzMUg/cd/orgs/CV/projects/Brijesh_Dhakar/"
          + "pipelines/DockerTest/executions/szmvyw4wQR2W4_iKkq9bfQ/pipeline";

      GraphLayoutNode parallelLayoutNode =
          GraphLayoutNode.newBuilder()
              .setNodeIdentifier("parallelIdentifier")
              .setNodeType("parallel")
              .setNodeUUID("parallelIdentifier")
              .setName("parallelNodeWithName")
              .setNodeGroup("STAGE")
              .setEdgeLayoutList(EdgeLayoutList.newBuilder()
                                     .addAllCurrentNodeChildren(List.of("stageBuild-1", "stageBuild-2"))
                                     .build())
              .build();
      GraphLayoutNodeDTO parallelLayoutNodeDTO = GraphLayoutDtoMapper.toDto(parallelLayoutNode);

      GraphLayoutNode build1LayoutNode = GraphLayoutNode.newBuilder()
                                             .setNodeIdentifier("stageBuild-1")
                                             .setNodeType("Custom")
                                             .setNodeUUID("stageBuild-1")
                                             .setName("stageBuild-1")
                                             .setNodeGroup("STAGE")
                                             .build();
      GraphLayoutNodeDTO build1LayoutNodeDTO = GraphLayoutDtoMapper.toDto(build1LayoutNode);

      GraphLayoutNode build2LayoutNode = GraphLayoutNode.newBuilder()
                                             .setNodeIdentifier("stageBuild-2")
                                             .setNodeType("Custom")
                                             .setNodeUUID("stageBuild-2")
                                             .setName("stageBuild-2")
                                             .setNodeGroup("STAGE")
                                             .build();
      GraphLayoutNodeDTO build2LayoutNodeDTO = GraphLayoutDtoMapper.toDto(build2LayoutNode);

      GraphLayoutNode approvalLayoutNode =
          GraphLayoutNode.newBuilder()
              .setNodeIdentifier("stage_approval1")
              .setNodeType("Approval")
              .setNodeUUID("stage_approval1")
              .setName("stage_approval 1")
              .setNodeGroup("STAGE")
              .setEdgeLayoutList(EdgeLayoutList.newBuilder().addNextIds("parallelIdentifier").build())
              .build();

      GraphLayoutNodeDTO approvalLayoutNodeDTO = GraphLayoutDtoMapper.toDto(approvalLayoutNode);
      approvalLayoutNodeDTO.setStatus(ExecutionStatus.APPROVAL_WAITING);

      HashMap<String, GraphLayoutNodeDTO> layoutNodeDTOMap = new HashMap<>();
      layoutNodeDTOMap.put(startingNodeId, approvalLayoutNodeDTO);
      layoutNodeDTOMap.put("parallelIdentifier", parallelLayoutNodeDTO);
      layoutNodeDTOMap.put("stageBuild-1", build1LayoutNodeDTO);
      layoutNodeDTOMap.put("stageBuild-2", build2LayoutNodeDTO);

      pipelineExecutionSummaryEntity.setLayoutNodeMap(layoutNodeDTOMap);
      doReturn(pipelineExecutionSummaryEntity)
          .when(pmsExecutionService)
          .getPipelineExecutionSummaryEntity(anyString(), anyString(), anyBoolean());
      mockUserGroupResponse(basicUserGroupDTOS);
      Ambiance ambiance = buildAmbianceWithStageId("stage_approval1");
      doReturn(url).when(notificationHelper).generateUrl(eq(ambiance), any(PipelineExecutionSummaryEntity.class));

      ArgumentCaptor<Set<StageSummary>> stageSummaryArgumentCaptor = ArgumentCaptor.forClass(Set.class);

      doAnswer(invocationOnMock -> { return null; })
          .when(stageMetadataNotificationHelper)
          .setFormattedSummaryOfFinishedStages(stageSummaryArgumentCaptors.get(0).capture(), anySet(), any());

      doAnswer(invocationOnMock -> {
        Set<String> formattedStages = invocationOnMock.getArgument(1);
        formattedStages.add("stage_approval1");
        return null;
      })
          .when(stageMetadataNotificationHelper)
          .setFormattedSummaryOfRunningStages(stageSummaryArgumentCaptors.get(1).capture(), anySet(), any(), any());

      doAnswer(invocationOnMock -> {
        Set<String> formattedStages = invocationOnMock.getArgument(1);
        formattedStages.addAll(Arrays.asList("stageBuild-1", "stageBuild-2"));
        return null;
      })
          .when(stageMetadataNotificationHelper)
          .setFormattedSummaryOfUpcomingStages(stageSummaryArgumentCaptors.get(2).capture(), anySet(), any(), any());

      approvalNotificationHandler.sendNotification(basicApprovalInstance, ambiance);

      ArgumentCaptor<NotificationChannel> notificationChannelArgumentCaptor =
          ArgumentCaptor.forClass(NotificationChannel.class);
      verify(notificationClient, times(2)).sendNotificationAsync(notificationChannelArgumentCaptor.capture());
      List<NotificationChannel> notificationChannels = notificationChannelArgumentCaptor.getAllValues();
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.finishedStages))
          .isEqualTo("N/A");
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.runningStages))
          .isEqualTo("stage_approval1");
      assertThat(notificationChannels.get(1).getTemplateData().get(ApprovalSummaryKeys.upcomingStages))
          .isEqualTo("stageBuild-1\n stageBuild-2");
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.upcomingStages))
          .isEqualTo("stageBuild-1<br> stageBuild-2");
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.currentStageName))
          .isEqualTo("stage_approval 1");

      assertThat(stageSummaryArgumentCaptors.get(0)
                     .getValue()
                     .stream()
                     .map(StageSummary::getFormattedEntityName)
                     .collect(Collectors.toSet()))
          .isEmpty();
      assertThat(stageSummaryArgumentCaptors.get(1)
                     .getValue()
                     .stream()
                     .map(StageSummary::getFormattedEntityName)
                     .collect(Collectors.toSet()))
          .contains("stage_approval 1");
      assertThat(stageSummaryArgumentCaptors.get(2)
                     .getValue()
                     .stream()
                     .map(StageSummary::getFormattedEntityName)
                     .collect(Collectors.toSet()))
          .contains("stageBuild-1", "stageBuild-2");
    }
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testFinalExecutionStatuses() {
    assertThat(ApprovalNotificationHandlerImpl.FINAL_EXECUTION_STATUSES)
        .contains(ExecutionStatus.ABORTED, ExecutionStatus.ABORTEDBYFREEZE, ExecutionStatus.FAILED,
            ExecutionStatus.EXPIRED, ExecutionStatus.APPROVALREJECTED, ExecutionStatus.SUSPENDED,
            ExecutionStatus.ERRORED, ExecutionStatus.IGNOREFAILED, ExecutionStatus.SKIPPED, ExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testGetUserGroupsWithUserGroupFilters() {
    mockStatic(IdentifierRefHelper.class);
    mockStatic(NGRestUtils.class);
    List<String> userGroupIds = Collections.singletonList("UserGroup1");
    ApproversDTO approversDTO = ApproversDTO.builder().userGroups(userGroupIds).build();
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, accountId)
                            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, orgIdentifier)
                            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, projectIdentifier)
                            .build();
    ApprovalInstance instance = HarnessApprovalInstance.builder().approvers(approversDTO).build();
    instance.setAmbiance(ambiance);
    IdentifierRef identifierRef = IdentifierRef.builder()
                                      .identifier("identifier")
                                      .accountIdentifier(accountId)
                                      .orgIdentifier(orgIdentifier)
                                      .scope(Scope.ACCOUNT)
                                      .build();
    UserGroupFilterDTO userGroupFilterDTO = UserGroupFilterDTO.builder()
                                                .accountIdentifier(accountId)
                                                .orgIdentifier(orgIdentifier)
                                                .projectIdentifier(projectIdentifier)
                                                .build();
    UserGroupDTO userGroupDTO = UserGroupDTO.builder().users(userGroupIds).build();
    List<UserGroupDTO> userGroupFilterDTOList = List.of(userGroupDTO);
    Call<ResponseDTO<List<UserGroupDTO>>> userGroupResponse = mock(Call.class);
    doReturn(userGroupResponse).when(userGroupClient).getFilteredUserGroups(userGroupFilterDTO);
    when(NGRestUtils.getResponse(userGroupResponse)).thenReturn(userGroupFilterDTOList);
    when(IdentifierRefHelper.getIdentifierRef(userGroupIds.get(0), accountId, orgIdentifier, projectIdentifier))
        .thenReturn(identifierRef);
    assertThat(approvalNotificationHandler.getUserGroups((HarnessApprovalInstance) instance)).isEmpty();
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testGetUserGroupsNoUserGroupFilters() {
    mockStatic(IdentifierRefHelper.class);
    List<String> userGroupIds = Collections.singletonList("UserGroup1");
    ApproversDTO approversDTO = ApproversDTO.builder().userGroups(userGroupIds).build();
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, accountId)
                            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, orgIdentifier)
                            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, projectIdentifier)
                            .build();
    ApprovalInstance instance = HarnessApprovalInstance.builder().approvers(approversDTO).build();
    instance.setAmbiance(ambiance);
    IdentifierRef identifierRef = IdentifierRef.builder().build();
    when(IdentifierRefHelper.getIdentifierRef(userGroupIds.get(0), accountId, orgIdentifier, projectIdentifier))
        .thenReturn(identifierRef);
    assertThat(approvalNotificationHandler.getUserGroups((HarnessApprovalInstance) instance)).isEmpty();
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testGetUserGroupsEmptyUserGroups() {
    ApproversDTO approversDTO = ApproversDTO.builder().userGroups(Collections.emptyList()).build();
    HarnessApprovalInstance instance = HarnessApprovalInstance.builder().approvers(approversDTO).build();
    assertThat(approvalNotificationHandler.getUserGroups(instance)).isEmpty();
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testFormatDuration() {
    assertThat(ApprovalNotificationHandlerImpl.formatDuration(1L)).isEqualTo("0s");
    assertThat(ApprovalNotificationHandlerImpl.formatDuration(60000L)).isEqualTo("1m");
    assertThat(ApprovalNotificationHandlerImpl.formatDuration(3600000L)).isEqualTo("1h");
    assertThat(ApprovalNotificationHandlerImpl.formatDuration(86400000L)).isEqualTo("1d");
    assertThat(ApprovalNotificationHandlerImpl.formatDuration(99999000L)).isEqualTo("1d 3h 46m 39s");
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testGetUserIdentification() {
    EmbeddedUser user_without_name_email = EmbeddedUser.builder().build();
    assertThat(approvalNotificationHandler.getUserIdentification(user_without_name_email)).isEqualTo("Unknown");
    EmbeddedUser user_without_email = EmbeddedUser.builder().name("name").build();
    assertThat(approvalNotificationHandler.getUserIdentification(user_without_email))
        .isEqualTo(user_without_email.getName());
    EmbeddedUser user_without_name = EmbeddedUser.builder().email("email@email.com").build();
    assertThat(approvalNotificationHandler.getUserIdentification(user_without_name))
        .isEqualTo(user_without_name.getEmail());
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetCurrentStageName() {
    // stage level not found in ambiance
    assertThatThrownBy(
        () -> ApprovalNotificationHandlerImpl.getCurrentStageName(ambiance, pipelineExecutionSummaryEntity))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Internal error occurred while getting current stage name");

    Ambiance ambianceWithStage = buildAmbianceWithStageId("nodeId");

    // stage node not found in layoutNodeMap
    assertThatThrownBy(
        () -> ApprovalNotificationHandlerImpl.getCurrentStageName(ambiance, pipelineExecutionSummaryEntity))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Internal error occurred while getting current stage name");

    Map<String, GraphLayoutNodeDTO> layoutNodeDTOMap = new HashMap<>();

    GraphLayoutNode graphLayoutNode = GraphLayoutNode.newBuilder()
                                          .setNodeIdentifier("nodeId")
                                          .setNodeType("ApprovalStage")
                                          .setNodeUUID(startingNodeId)
                                          .setName("approval stage")
                                          .setNodeGroup("STAGE")
                                          .build();
    GraphLayoutNodeDTO graphLayoutNodeDTO = GraphLayoutDtoMapper.toDto(graphLayoutNode);

    layoutNodeDTOMap.put(startingNodeId, graphLayoutNodeDTO);
    pipelineExecutionSummaryEntity.setLayoutNodeMap(layoutNodeDTOMap);

    assertThat(ApprovalNotificationHandlerImpl.getCurrentStageName(ambianceWithStage, pipelineExecutionSummaryEntity))
        .isEqualTo("approval stage");
    graphLayoutNodeDTO.setName(" ");
    assertThat(ApprovalNotificationHandlerImpl.getCurrentStageName(ambianceWithStage, pipelineExecutionSummaryEntity))
        .isEqualTo("nodeId");
  }

  private void mockUserGroupResponse(List<UserGroupDTO> userGroupDTOS) throws IOException {
    ResponseDTO<List<UserGroupDTO>> restResponse = ResponseDTO.newResponse(userGroupDTOS);
    Response<ResponseDTO<List<UserGroupDTO>>> response = Response.success(restResponse);

    Call<ResponseDTO<List<UserGroupDTO>>> responseDTOCall = mock(Call.class);
    when(responseDTOCall.execute()).thenReturn(response);

    when(userGroupClient.getFilteredUserGroups(any())).thenReturn(responseDTOCall);
  }

  private Ambiance buildAmbianceWithStageId(String stageNodeId) {
    return Ambiance.newBuilder()
        .putSetupAbstractions("accountId", accountId)
        .putSetupAbstractions("orgIdentifier", orgIdentifier)
        .putSetupAbstractions("projectIdentifier", projectIdentifier)
        .putSetupAbstractions("pipelineIdentifier", pipelineIdentifier)
        .addLevels(Level.newBuilder().setGroup("STAGE").setIdentifier(stageNodeId).build())
        .build();
  }
}