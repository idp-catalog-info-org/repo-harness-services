/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.notification.helper;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.task.shell.ShellScriptTaskNG.COMMAND_UNIT;
import static io.harness.pms.approval.notification.summary.ApprovalSummary.NEWLINE_STAGE_DELIMITER;
import static io.harness.remote.client.NGRestUtils.getResponse;
import static io.harness.utils.IdentifierRefHelper.IDENTIFIER_REF_DELIMITER;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.IdentifierRef;
import io.harness.beans.ScopeInfo;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.Scope;
import io.harness.exception.ApprovalStepNGException;
import io.harness.exception.ExceptionUtils;
import io.harness.logging.AutoLogContext;
import io.harness.logging.LogLevel;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.ng.core.dto.OrganizationResponse;
import io.harness.ng.core.dto.ProjectResponse;
import io.harness.ng.core.dto.UserGroupDTO;
import io.harness.ng.core.dto.UserGroupFilterDTO;
import io.harness.ng.core.dto.UserGroupFilterDTO.UserGroupFilterDTOBuilder;
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
import io.harness.pms.approval.notification.stagemetadata.summary.StagesSummary;
import io.harness.pms.approval.notification.summary.ApprovalSummary;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.dto.GraphLayoutNodeDTO;
import io.harness.pms.plan.execution.service.intfc.PMSExecutionService;
import io.harness.project.remote.ProjectClient;
import io.harness.remote.client.NGRestUtils;
import io.harness.steps.approval.ApprovalNotificationHandler;
import io.harness.steps.approval.step.beans.ApprovalStatus;
import io.harness.steps.approval.step.entities.ApprovalUtils;
import io.harness.steps.approval.step.entities.HarnessApprovalInstance;
import io.harness.steps.approval.step.harness.beans.HarnessApprovalAction;
import io.harness.steps.approval.step.harness.beans.HarnessApprovalActivity;
import io.harness.usergroups.UserGroupClient;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.ScopeResolutionHelper;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_APPROVALS})
@OwnedBy(CDC)
@Slf4j
public class ApprovalNotificationHandlerImpl implements ApprovalNotificationHandler {
  public static final DateTimeFormatter DISPLAY_TIME_FORMAT = DateTimeFormatter.ofPattern("MMM dd, hh:mm a z");
  public static final String STAGE_IDENTIFIER = "STAGE";
  public static final String PARALLEL_NODE_TYPE = "parallel";

  public static final Set<ExecutionStatus> FINAL_EXECUTION_STATUSES =
      StatusUtils.finalStatuses().stream().map(ExecutionStatus::getExecutionStatus).collect(Collectors.toSet());
  private final UserGroupClient userGroupClient;
  private final NotificationClient notificationClient;
  private final NotificationHelper notificationHelper;
  private final PMSExecutionService pmsExecutionService;
  private final LogStreamingStepClientFactory logStreamingStepClientFactory;
  private final ProjectClient projectClient;
  private final OrganizationClient organizationClient;
  private final StageMetadataNotificationHelper stageMetadataNotificationHelper;
  private final ScopeResolutionHelper scopeResolutionHelper;

  @Inject
  public ApprovalNotificationHandlerImpl(@Named("PRIVILEGED") UserGroupClient userGroupClient,
      NotificationClient notificationClient, NotificationHelper notificationHelper,
      PMSExecutionService pmsExecutionService, LogStreamingStepClientFactory logStreamingStepClientFactory,
      @Named("PRIVILEGED") OrganizationClient organizationClient, @Named("PRIVILEGED") ProjectClient projectClient,
      StageMetadataNotificationHelper stageMetadataNotificationHelper, ScopeResolutionHelper scopeResolutionHelper) {
    this.userGroupClient = userGroupClient;
    this.notificationClient = notificationClient;
    this.notificationHelper = notificationHelper;
    this.pmsExecutionService = pmsExecutionService;
    this.logStreamingStepClientFactory = logStreamingStepClientFactory;
    this.organizationClient = organizationClient;
    this.projectClient = projectClient;
    this.stageMetadataNotificationHelper = stageMetadataNotificationHelper;
    this.scopeResolutionHelper = scopeResolutionHelper;
  }

  @Override
  public void sendNotification(HarnessApprovalInstance approvalInstance, Ambiance ambiance) {
    try (AutoLogContext ignore = approvalInstance.autoLogContext()) {
      NGLogCallback logCallback = new NGLogCallback(logStreamingStepClientFactory, ambiance, COMMAND_UNIT, false);

      if (ApprovalStatus.APPROVED.equals(approvalInstance.getStatus())
          || ApprovalStatus.REJECTED.equals(approvalInstance.getStatus())) {
        try {
          ApprovalSummary approvalSummary =
              getApprovalSummary(ambiance, approvalInstance, getPipelineExecutionSummary(ambiance));
          sendNotificationInternal(approvalInstance, approvalInstance.getValidatedUserGroups(),
              approvalSummary.toParams(NEWLINE_STAGE_DELIMITER), logCallback);
        } catch (Exception e) {
          logCallback.saveExecutionLog(
              String.format("Error sending notification to user groups for Harness approval Action: %s",
                  ExceptionUtils.getMessage(e)));
          log.error("Error while sending notification for harness approval Action", e);
        }
      } else {
        sendNotificationInternal(approvalInstance, ambiance, logCallback);
      }
    }
  }

  @VisibleForTesting
  protected void sendNotificationInternal(
      HarnessApprovalInstance approvalInstance, Ambiance ambiance, NGLogCallback logCallback) {
    try {
      log.info("Sending notification to user groups for harness approval");
      logCallback.saveExecutionLog("-----");
      logCallback.saveExecutionLog("Sending notification to user groups for harness approval");
      PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity = getPipelineExecutionSummary(ambiance);

      List<UserGroupDTO> userGroups = approvalInstance.getValidatedUserGroups();
      List<String> invalidUserGroupIds =
          findInvalidInputUserGroups(userGroups, approvalInstance.getApprovers().getUserGroups());
      if (isNotEmpty(invalidUserGroupIds)) {
        logCallback.saveExecutionLog(
            String.format("Some invalid user groups were given as inputs: %s", invalidUserGroupIds), LogLevel.WARN);
        log.warn("Some invalid user groups were given as inputs: {}", invalidUserGroupIds);
      }

      ApprovalSummary approvalSummary = getApprovalSummary(ambiance, approvalInstance, pipelineExecutionSummaryEntity);
      sendNotificationInternal(
          approvalInstance, userGroups, approvalSummary.toParams(NEWLINE_STAGE_DELIMITER), logCallback);
    } catch (Exception e) {
      logCallback.saveExecutionLog(String.format(
          "Error sending notification to user groups for harness approval: %s", ExceptionUtils.getMessage(e)));
      log.error("Error while sending notification for harness approval", e);
    }
  }

  public ApprovalSummary getApprovalSummary(Ambiance ambiance, HarnessApprovalInstance approvalInstance,
      PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity) {
    ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(
        pipelineExecutionSummaryEntity.getAccountId(), pipelineExecutionSummaryEntity.getParentUniqueId());
    boolean useScopeInfo = scopeInfo != null;

    ApprovalSummary approvalSummary =
        ApprovalSummary.builder()
            .pipelineName(StringUtils.isEmpty(pipelineExecutionSummaryEntity.getName())
                    ? pipelineExecutionSummaryEntity.getPipelineIdentifier()
                    : pipelineExecutionSummaryEntity.getName())
            .orgName(getOrganizationNameElseIdentifier(
                useScopeInfo ? scopeInfo.getOrgIdentifier() : pipelineExecutionSummaryEntity.getOrgIdentifier(),
                pipelineExecutionSummaryEntity.getAccountId()))
            .projectName(getProjectNameElseIdentifier(
                useScopeInfo ? scopeInfo.getProjectIdentifier() : pipelineExecutionSummaryEntity.getProjectIdentifier(),
                useScopeInfo ? scopeInfo.getOrgIdentifier() : pipelineExecutionSummaryEntity.getOrgIdentifier(),
                pipelineExecutionSummaryEntity.getAccountId()))
            .approvalMessage(approvalInstance.getApprovalMessage())
            .startedAt(formatTime(approvalInstance.getCreatedAt()))
            .expiresAt(formatTime(approvalInstance.getDeadline()))
            .triggeredBy(getUser(ambiance))
            .runningStages(new LinkedHashSet<>())
            .upcomingStages(new LinkedHashSet<>())
            .finishedStages(new LinkedHashSet<>())
            .action(getAction(approvalInstance))
            .status(approvalInstance.getStatus())
            .pipelineExecutionLink(notificationHelper.generateUrl(ambiance, pipelineExecutionSummaryEntity))
            .timeRemainingForApproval(formatDuration(approvalInstance.getDeadline() - System.currentTimeMillis()))
            .currentStageName(approvalInstance.isIncludePipelineExecutionHistory()
                    ? getCurrentStageName(ambiance, pipelineExecutionSummaryEntity)
                    // this can't be null else will lead to npe in notification client
                    // this value is only used when isIncludePipelineExecutionHistory is true
                    : "")
            .build();
    if (approvalInstance.isIncludePipelineExecutionHistory()) {
      generateModuleSpecificSummary(approvalSummary, pipelineExecutionSummaryEntity, scopeInfo);
    }
    return approvalSummary;
  }

  @VisibleForTesting
  protected List<String> findInvalidInputUserGroups(
      List<UserGroupDTO> validatedUserGroups, List<String> inputUserGroups) {
    if (isEmpty(inputUserGroups)) {
      return null;
    }
    if (isEmpty(validatedUserGroups)) {
      return inputUserGroups;
    }
    return new ArrayList<>(Sets.difference(Sets.newHashSet(inputUserGroups),
        validatedUserGroups.stream()
            .map(ug
                -> toUserGroupId(ug.getOrgIdentifier(), ug.getProjectIdentifier(), ug.getIdentifier(),
                    IDENTIFIER_REF_DELIMITER.substring(1)))
            .collect(Collectors.toSet())));
  }

  private String toUserGroupId(String orgIdentifier, String projectIdentifier, String identifier, String delimiter) {
    if (!isBlank(projectIdentifier)) {
      return identifier;
    }
    if (!isBlank(orgIdentifier)) {
      return StringUtils.joinWith(delimiter, Scope.ORG.getYamlRepresentation(), identifier);
    }
    return StringUtils.joinWith(delimiter, Scope.ACCOUNT.getYamlRepresentation(), identifier);
  }

  private void generateModuleSpecificSummary(ApprovalSummary approvalSummary,
      PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity, ScopeInfo scopeInfo) {
    boolean useScopeInfo = scopeInfo != null;

    String startingNodeId = pipelineExecutionSummaryEntity.getStartingNodeId();
    StagesSummary stagesSummary = StagesSummary.builder().build();
    traverseGraph(stagesSummary, pipelineExecutionSummaryEntity.getLayoutNodeMap(), startingNodeId);
    io.harness.beans.Scope scope = io.harness.beans.Scope.of(pipelineExecutionSummaryEntity.getAccountId(),
        useScopeInfo ? scopeInfo.getOrgIdentifier() : pipelineExecutionSummaryEntity.getOrgIdentifier(),
        useScopeInfo ? scopeInfo.getProjectIdentifier() : pipelineExecutionSummaryEntity.getProjectIdentifier());
    try {
      stageMetadataNotificationHelper.setFormattedSummaryOfFinishedStages(
          stagesSummary.getFinishedStages(), approvalSummary.getFinishedStages(), scope);
      stageMetadataNotificationHelper.setFormattedSummaryOfRunningStages(stagesSummary.getRunningStages(),
          approvalSummary.getRunningStages(), scope, pipelineExecutionSummaryEntity.getPlanExecutionId());
      stageMetadataNotificationHelper.setFormattedSummaryOfUpcomingStages(stagesSummary.getUpcomingStages(),
          approvalSummary.getUpcomingStages(), scope, pipelineExecutionSummaryEntity.getPlanExecutionId());
    } catch (Exception ex) {
      log.warn("Error occurred while formatting stage metadata: {}", ExceptionUtils.getMessage(ex), ex);
      throw new ApprovalStepNGException(
          String.format("Error occurred while formatting stage metadata: %s", ExceptionUtils.getMessage(ex)), false,
          ex);
    }
  }

  @VisibleForTesting
  protected static String getCurrentStageName(
      Ambiance ambiance, PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity) {
    Optional<Level> stageLevel = AmbianceUtils.getStageLevelFromAmbiance(ambiance);

    if (stageLevel.isEmpty() || StringUtils.isBlank(stageLevel.get().getIdentifier())) {
      log.error(
          "Stage level or its identifier not found in ambiance of harness approval step, failing to send notification");
      throw new IllegalStateException("Internal error occurred while getting current stage name");
    }
    String currentStageIdentifier = stageLevel.get().getIdentifier();

    Optional<Map.Entry<String, GraphLayoutNodeDTO>> currentStageNode =
        pipelineExecutionSummaryEntity.getLayoutNodeMap()
            .entrySet()
            .stream()
            .filter(entry -> currentStageIdentifier.equals(entry.getValue().getNodeIdentifier()))
            .findFirst();

    if (currentStageNode.isEmpty()) {
      log.error("Current stage node with id {} not found in execution layout, failing to send notification",
          currentStageIdentifier);
      throw new IllegalStateException("Internal error occurred while getting current stage name");
    }

    return StringUtils.defaultIfBlank(
        currentStageNode.get().getValue().getName(), currentStageNode.get().getValue().getNodeIdentifier());
  }

  private void traverseGraph(
      StagesSummary stagesSummary, Map<String, GraphLayoutNodeDTO> layoutNodeMap, String currentNodeId) {
    GraphLayoutNodeDTO node = layoutNodeMap.get(currentNodeId);
    if (node.getNodeGroup().matches(STAGE_IDENTIFIER) && !PARALLEL_NODE_TYPE.equals(node.getNodeType())) {
      if (node.getStatus() == ExecutionStatus.NOTSTARTED) {
        if (!isEmpty(node.getName())) {
          StageMetadataNotificationHelper.addStageNodeToStagesSummary(stagesSummary.getUpcomingStages(), node);
        }
      } else if (FINAL_EXECUTION_STATUSES.contains(node.getStatus())) {
        StageMetadataNotificationHelper.addStageNodeToStagesSummary(stagesSummary.getFinishedStages(), node);
      } else {
        StageMetadataNotificationHelper.addStageNodeToStagesSummary(stagesSummary.getRunningStages(), node);
      }
    }

    if (!isNull(node.getEdgeLayoutList())
        && (!isEmpty(node.getEdgeLayoutList().getNextIds())
            || !isEmpty(node.getEdgeLayoutList().getCurrentNodeChildren()))) {
      if (!isEmpty(node.getEdgeLayoutList().getCurrentNodeChildren())) {
        for (String nextNodeChildrenId : node.getEdgeLayoutList().getCurrentNodeChildren()) {
          // iterating through stages which are set to run in parallel
          traverseGraph(stagesSummary, layoutNodeMap, nextNodeChildrenId);
        }
      }

      for (String nextId : node.getEdgeLayoutList().getNextIds()) {
        traverseGraph(stagesSummary, layoutNodeMap, nextId);
      }
    }
  }

  private void sendNotificationInternal(HarnessApprovalInstance instance, List<UserGroupDTO> userGroups,
      Map<String, String> templateData, NGLogCallback logCallback) {
    for (NotificationChannelType notificationChannelType : NotificationChannelType.values()) {
      List<UserGroupDTO> filteredGroups =
          userGroups.stream()
              .filter(
                  ug -> ug.getNotificationConfigs().stream().anyMatch(nsc -> nsc.getType() == notificationChannelType))
              .toList();
      if (isNotEmpty(filteredGroups)) {
        NotificationChannel notificationChannel =
            getNotificationChannel(instance, notificationChannelType, filteredGroups, templateData);
        if (notificationChannel != null) {
          notificationClient.sendNotificationAsync(notificationChannel);
        } else {
          log.warn("Error constructing NotificationChannel for {}", notificationChannelType);
          logCallback.saveExecutionLog(
              String.format("Error sending notification to user groups for harness approval: Error occurred while "
                      + "sending notification to %s user groups via %s notification channel",
                  String.join(", ", filteredGroups.stream().map(UserGroupDTO::getName).toList()),
                  notificationChannelType),
              LogLevel.ERROR);
        }
      }
    }
  }

  @VisibleForTesting
  protected NotificationChannel getNotificationChannel(HarnessApprovalInstance instance,
      NotificationChannelType notificationChannelType, List<UserGroupDTO> userGroups,
      Map<String, String> templateData) {
    if (isEmpty(userGroups)) {
      return null;
    }
    List<NotificationRequest.UserGroup> notifyUserGroups = getNotifyUserGroups(userGroups);

    if (ApprovalStatus.APPROVED.equals(instance.getStatus()) || ApprovalStatus.REJECTED.equals(instance.getStatus())) {
      return notificationTemplateForApprovalAction(instance, notificationChannelType, templateData, notifyUserGroups);
    } else {
      return notificationTemplateForApprovalRequired(instance, notificationChannelType, templateData, notifyUserGroups);
    }
  }

  private static List<NotificationRequest.UserGroup> getNotifyUserGroups(List<UserGroupDTO> userGroups) {
    List<NotificationRequest.UserGroup> notifyUserGroups = new ArrayList<>();
    for (UserGroupDTO userGroup : userGroups) {
      NotificationRequest.UserGroup.Builder notifyUserGroupBuilder = NotificationRequest.UserGroup.newBuilder();
      notifyUserGroupBuilder.setIdentifier(userGroup.getIdentifier());
      if (isNotEmpty(userGroup.getOrgIdentifier())) {
        notifyUserGroupBuilder.setOrgIdentifier(userGroup.getOrgIdentifier());
      }
      if (isNotEmpty(userGroup.getProjectIdentifier())) {
        notifyUserGroupBuilder.setProjectIdentifier(userGroup.getProjectIdentifier());
      }
      notifyUserGroups.add(notifyUserGroupBuilder.build());
    }
    return notifyUserGroups;
  }

  private NotificationChannel notificationTemplateForApprovalAction(HarnessApprovalInstance instance,
      NotificationChannelType notificationChannelType, Map<String, String> templateData,
      List<NotificationRequest.UserGroup> notifyUserGroups) {
    Ambiance ambiance = instance.getAmbiance();
    String accountId = instance.getAccountId();
    switch (notificationChannelType) {
      case SLACK:
        String slackTemplateId = instance.isIncludePipelineExecutionHistory()
            ? PredefinedTemplate.HARNESS_APPROVAL_ACTION_EXECUTION_NOTIFICATION_SLACK.getIdentifier()
            : PredefinedTemplate.HARNESS_APPROVAL_ACTION_NOTIFICATION_SLACK.getIdentifier();
        return SlackChannel.builder()
            .accountId(accountId)
            .team(Team.PIPELINE)
            .templateId(slackTemplateId)
            .templateData(templateData)
            // emptyList for webhookUrls is IMP else npe at client side
            .webhookUrls(Collections.emptyList())
            .userGroups(notifyUserGroups)
            .orgIdentifier(AmbianceUtils.getOrgIdentifier(ambiance))
            .projectIdentifier(AmbianceUtils.getProjectIdentifier(ambiance))
            .expressionFunctorToken(ambiance.getExpressionFunctorToken())
            .build();

      case EMAIL:
        String emailTemplateId = instance.isIncludePipelineExecutionHistory()
            ? PredefinedTemplate.HARNESS_APPROVAL_ACTION_EXECUTION_NOTIFICATION_EMAIL.getIdentifier()
            : PredefinedTemplate.HARNESS_APPROVAL_ACTION_NOTIFICATION_EMAIL.getIdentifier();
        Map<String, String> htmlTemplateData = ApprovalUtils.escapeHTMLForTextFields(
            new HashMap<>(templateData), ApprovalSummary.TEXT_FIELDS_IN_APPROVAL_SUMMARY);
        return EmailChannel.builder()
            .accountId(accountId)
            .userGroups(notifyUserGroups)
            .team(Team.PIPELINE)
            .templateId(emailTemplateId)
            .templateData(htmlTemplateData)
            .recipients(Collections.emptyList())
            .build();

      case MSTEAMS:
        String msTeamsTemplateId = instance.isIncludePipelineExecutionHistory()
            ? PredefinedTemplate.HARNESS_APPROVAL_ACTION_EXECUTION_NOTIFICATION_MSTEAMS.getIdentifier()
            : PredefinedTemplate.HARNESS_APPROVAL_ACTION_NOTIFICATION_MSTEAMS.getIdentifier();
        templateData.put("PIPELINE_EXECUTION_URL", templateData.get("pipelineExecutionLink"));
        return MSTeamChannel.builder()
            .msTeamKeys(Collections.emptyList())
            .accountId(accountId)
            .team(Team.PIPELINE)
            .templateData(templateData)
            .templateId(msTeamsTemplateId)
            .userGroups(notifyUserGroups)
            .orgIdentifier(AmbianceUtils.getOrgIdentifier(ambiance))
            .projectIdentifier(AmbianceUtils.getProjectIdentifier(ambiance))
            .expressionFunctorToken(ambiance.getExpressionFunctorToken())
            .build();

      default:
        return null;
    }
  }

  private NotificationChannel notificationTemplateForApprovalRequired(HarnessApprovalInstance instance,
      NotificationChannelType notificationChannelType, Map<String, String> templateData,
      List<NotificationRequest.UserGroup> notifyUserGroups) {
    Ambiance ambiance = instance.getAmbiance();
    String accountId = instance.getAccountId();
    switch (notificationChannelType) {
      case SLACK:
        String slackTemplateId = instance.isIncludePipelineExecutionHistory()
            ? PredefinedTemplate.HARNESS_APPROVAL_EXECUTION_NOTIFICATION_SLACK.getIdentifier()
            : PredefinedTemplate.HARNESS_APPROVAL_NOTIFICATION_SLACK.getIdentifier();
        return SlackChannel.builder()
            .accountId(accountId)
            .team(Team.PIPELINE)
            .templateId(slackTemplateId)
            .templateData(templateData)
            .userGroups(notifyUserGroups)
            // emptyList for webhookUrls is IMP else npe at client side
            .webhookUrls(Collections.emptyList())
            .orgIdentifier(AmbianceUtils.getOrgIdentifier(ambiance))
            .projectIdentifier(AmbianceUtils.getProjectIdentifier(ambiance))
            .expressionFunctorToken(ambiance.getExpressionFunctorToken())
            .build();
      case EMAIL:
        String emailTemplateId = instance.isIncludePipelineExecutionHistory()
            ? PredefinedTemplate.HARNESS_APPROVAL_EXECUTION_NOTIFICATION_EMAIL.getIdentifier()
            : PredefinedTemplate.HARNESS_APPROVAL_NOTIFICATION_EMAIL.getIdentifier();
        Map<String, String> htmlTemplateData = ApprovalUtils.escapeHTMLForTextFields(
            new HashMap<>(templateData), ApprovalSummary.TEXT_FIELDS_IN_APPROVAL_SUMMARY);
        return EmailChannel.builder()
            .accountId(accountId)
            .userGroups(notifyUserGroups)
            .team(Team.PIPELINE)
            .templateId(emailTemplateId)
            .templateData(htmlTemplateData)
            .recipients(Collections.emptyList())
            .build();

      case MSTEAMS:
        String msTeamsTemplateId = instance.isIncludePipelineExecutionHistory()
            ? PredefinedTemplate.HARNESS_APPROVAL_EXECUTION_NOTIFICATION_MSTEAMS.getIdentifier()
            : PredefinedTemplate.HARNESS_APPROVAL_NOTIFICATION_MSTEAMS.getIdentifier();
        templateData.put("PIPELINE_EXECUTION_URL", templateData.get("pipelineExecutionLink"));
        return MSTeamChannel.builder()
            .msTeamKeys(Collections.emptyList())
            .accountId(accountId)
            .team(Team.PIPELINE)
            .templateData(templateData)
            .templateId(msTeamsTemplateId)
            .userGroups(notifyUserGroups)
            .orgIdentifier(AmbianceUtils.getOrgIdentifier(ambiance))
            .projectIdentifier(AmbianceUtils.getProjectIdentifier(ambiance))
            .expressionFunctorToken(ambiance.getExpressionFunctorToken())
            .build();

      default:
        return null;
    }
  }

  private String getAction(HarnessApprovalInstance approvalInstance) {
    Optional<HarnessApprovalActivity> optionalHarnessApprovalActivity = approvalInstance.fetchLastApprovalActivity();
    String action = "";
    if (optionalHarnessApprovalActivity.isPresent()) {
      HarnessApprovalActivity lastApprovalActivity = optionalHarnessApprovalActivity.get();
      if (HarnessApprovalAction.APPROVE.equals(lastApprovalActivity.getAction())) {
        List<HarnessApprovalActivity> harnessApprovalActivities = approvalInstance.getApprovalActivities();
        for (HarnessApprovalActivity harnessApprovalActivity : harnessApprovalActivities) {
          String userIdentification = getUserIdentification(harnessApprovalActivity.getUser());
          action = action
              + (userIdentification + " approved on " + formatTime(harnessApprovalActivity.getApprovedAt()) + "   \n");
        }
        if (!isEmpty(action)) {
          // removing last redundant new line character
          action = action.substring(0, action.length() - 1);
        }
      } else if (HarnessApprovalAction.REJECT.equals(lastApprovalActivity.getAction())) {
        String userIdentification = getUserIdentification(lastApprovalActivity.getUser());
        action = userIdentification + " rejected on " + formatTime(lastApprovalActivity.getApprovedAt());
      }
      return action;
    }
    return action;
  }

  @VisibleForTesting
  protected String getUserIdentification(EmbeddedUser user) {
    if (isEmpty(user.getEmail()) && isEmpty(user.getName())) {
      return "Unknown";
    } else if (isEmpty(user.getEmail())) {
      return user.getName();
    } else {
      return user.getEmail();
    }
  }

  private String getUser(Ambiance ambiance) {
    ExecutionTriggerInfo triggerInfo = ambiance.getMetadata().getTriggerInfo();
    String triggeredByUserId = triggerInfo.getTriggeredBy().getIdentifier();
    String triggeredByUserEmail = triggerInfo.getTriggeredBy().getExtraInfoOrDefault("email", triggeredByUserId);
    if (EmptyPredicate.isEmpty(triggeredByUserEmail)) {
      triggeredByUserEmail = "Unknown";
    }
    switch (triggerInfo.getTriggerType()) {
      case WEBHOOK:
      case WEBHOOK_CUSTOM:
        return triggeredByUserEmail + " (Webhook trigger)";
      case SCHEDULER_CRON:
        return triggeredByUserEmail + " (Scheduled trigger)";
      default:
        return triggeredByUserEmail;
    }
  }

  public List<UserGroupDTO> getUserGroups(HarnessApprovalInstance instance) {
    List<UserGroupDTO> userGroups = new ArrayList<>();
    List<String> userGroupIds = instance.getApprovers().getUserGroups();

    if (EmptyPredicate.isEmpty(userGroupIds)) {
      return userGroups;
    }

    String accountId = AmbianceUtils.getAccountId(instance.getAmbiance());
    String orgId = AmbianceUtils.getOrgIdentifier(instance.getAmbiance());
    String projectId = AmbianceUtils.getProjectIdentifier(instance.getAmbiance());
    Map<Scope, List<IdentifierRef>> identifierRefs =
        new HashSet<>(userGroupIds)
            .stream()
            .map(ug -> IdentifierRefHelper.getIdentifierRef(ug, accountId, orgId, projectId))
            .collect(Collectors.groupingBy(IdentifierRef::getScope));

    List<UserGroupFilterDTO> userGroupFilters = ImmutableList.of(Scope.ACCOUNT, Scope.ORG, Scope.PROJECT)
                                                    .stream()
                                                    // Find user groups corresponding to each scope.
                                                    .map(identifierRefs::get)
                                                    // Create a user group filter for each scope.
                                                    .map(this::prepareUserGroupFilter)
                                                    // Remove any scope that doesn't have any user group.
                                                    .filter(Optional::isPresent)
                                                    .map(Optional::get)
                                                    .collect(Collectors.toList());
    if (EmptyPredicate.isEmpty(userGroupFilters)) {
      return userGroups;
    }

    for (UserGroupFilterDTO userGroupFilter : userGroupFilters) {
      List<UserGroupDTO> use = NGRestUtils.getResponse(userGroupClient.getFilteredUserGroups(userGroupFilter));
      if (EmptyPredicate.isNotEmpty(use)) {
        userGroups.addAll(use);
      }
    }
    return userGroups;
  }

  private Optional<UserGroupFilterDTO> prepareUserGroupFilter(List<IdentifierRef> identifierRefs) {
    if (EmptyPredicate.isEmpty(identifierRefs)) {
      return Optional.empty();
    }

    IdentifierRef identifierRef = identifierRefs.get(0);
    UserGroupFilterDTOBuilder builder = UserGroupFilterDTO.builder().identifierFilter(
        identifierRefs.stream().map(IdentifierRef::getIdentifier).collect(Collectors.toSet()));
    switch (identifierRef.getScope()) {
      case ACCOUNT:
        builder.accountIdentifier(identifierRef.getAccountIdentifier());
        break;
      case ORG:
        builder.accountIdentifier(identifierRef.getAccountIdentifier()).orgIdentifier(identifierRef.getOrgIdentifier());
        break;
      case PROJECT:
        builder.accountIdentifier(identifierRef.getAccountIdentifier())
            .orgIdentifier(identifierRef.getOrgIdentifier())
            .projectIdentifier(identifierRef.getProjectIdentifier());
        break;
      default:
        return Optional.empty();
    }
    return Optional.of(builder.build());
  }

  private PipelineExecutionSummaryEntity getPipelineExecutionSummary(Ambiance ambiance) {
    String planExecutionId = ambiance.getPlanExecutionId();
    String accountId = AmbianceUtils.getAccountId(ambiance);

    return pmsExecutionService.getPipelineExecutionSummaryEntity(accountId, planExecutionId, false);
  }

  private String getOrganizationNameElseIdentifier(String orgIdentifier, String accountIdentifier) {
    Optional<OrganizationResponse> orgResponse =
        getResponse(organizationClient.getOrganization(orgIdentifier, accountIdentifier));
    if (orgResponse.isPresent() && !StringUtils.isEmpty(orgResponse.get().getOrganization().getName())) {
      return orgResponse.get().getOrganization().getName();
    }
    return orgIdentifier;
  }

  private String getProjectNameElseIdentifier(
      String projectIdentifier, String orgIdentifier, String accountIdentifier) {
    Optional<ProjectResponse> projectResponse =
        getResponse(projectClient.getProject(projectIdentifier, accountIdentifier, orgIdentifier));
    if (projectResponse.isPresent() && !StringUtils.isEmpty(projectResponse.get().getProject().getName())) {
      return projectResponse.get().getProject().getName();
    }
    return projectIdentifier;
  }

  private static String formatTime(long epochMillis) {
    ZonedDateTime time = ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.of("GMT"));
    return DISPLAY_TIME_FORMAT.format(time);
  }

  public static String formatDuration(long durationMillis) {
    long elapsedDays = durationMillis / TimeUnit.DAYS.toMillis(1);
    durationMillis = durationMillis % TimeUnit.DAYS.toMillis(1);

    long elapsedHours = durationMillis / TimeUnit.HOURS.toMillis(1);
    durationMillis = durationMillis % TimeUnit.HOURS.toMillis(1);

    long elapsedMinutes = durationMillis / TimeUnit.MINUTES.toMillis(1);
    durationMillis = durationMillis % TimeUnit.MINUTES.toMillis(1);

    long elapsedSeconds = durationMillis / TimeUnit.SECONDS.toMillis(1);

    StringBuilder elapsed = new StringBuilder();

    if (elapsedDays > 0) {
      elapsed.append(elapsedDays).append('d');
    }
    if (elapsedHours > 0) {
      if (isNotEmpty(elapsed.toString())) {
        elapsed.append(' ');
      }
      elapsed.append(elapsedHours).append('h');
    }
    if (elapsedMinutes > 0) {
      if (isNotEmpty(elapsed.toString())) {
        elapsed.append(' ');
      }
      elapsed.append(elapsedMinutes).append('m');
    }
    if (elapsedSeconds > 0) {
      if (isNotEmpty(elapsed.toString())) {
        elapsed.append(' ');
      }
      elapsed.append(elapsedSeconds).append('s');
    }

    if (isEmpty(elapsed.toString())) {
      elapsed.append("0s");
    }

    return elapsed.toString();
  }
}
