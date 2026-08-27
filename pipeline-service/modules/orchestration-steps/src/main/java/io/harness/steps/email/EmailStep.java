/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.email;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.eraro.ErrorCode.GENERAL_ERROR;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
import io.harness.common.ParameterFieldHelper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.NotificationTaskResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.logging.UnitProgress;
import io.harness.logging.UnitStatus;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.dto.UserBasicInfo;
import io.harness.ng.core.dto.UserGroupResponseV2DTO;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.notification.notificationclient.NotificationClient;
import io.harness.notification.remote.dto.EmailDTO;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.remote.client.NGRestUtils;
import io.harness.serializer.JsonUtils;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.StepUtils;
import io.harness.steps.executables.PipelineSyncExecutable;
import io.harness.usergroups.UserGroupClient;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import retrofit2.Response;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class EmailStep extends PipelineSyncExecutable {
  @Inject private NotificationClient notificationClient;
  public static final StepType STEP_TYPE = StepSpecTypeConstants.EMAIL_STEP_TYPE;
  static final String EMAIL_TO_NON_HARNESS_USERS_SETTING_KEY = "email_to_non_harness_users";
  static final String EMAIL_TO_NON_HARNESS_USERS_TRUE_VALUE = "true";

  @Inject private KryoSerializer kryoSerializer;
  @Inject private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Inject private NGSettingsClient settingsClient;
  @Inject private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Inject private UserGroupClient userGroupClient;

  @Override
  public List<String> getLogKeys(Ambiance ambiance) {
    return StepUtils.generateLogKeys(ambiance, new ArrayList<>());
  }

  @Override
  public StepResponse executeSyncAfterRbac(Ambiance ambiance, StepBaseParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData) {
    long startTime = System.currentTimeMillis();
    NGLogCallback logCallback = new NGLogCallback(logStreamingStepClientFactory, ambiance, null, true);
    EmailStepParameters emailStepParameters = getEmailStepParameters(stepParameters);
    String toMail = emailStepParameters.to.getValue();
    String ccMail = emailStepParameters.cc.getValue();
    Set<String> toRecipients = new HashSet<>();
    Set<String> ccRecipients = new HashSet<>();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String notificationId = generateUuid();

    if (StringUtils.isNotBlank(toMail)) {
      toRecipients = Stream.of(toMail.trim().split("\\s*,\\s*")).collect(Collectors.toSet());
    }
    if (StringUtils.isNotBlank(ccMail)) {
      ccRecipients = Stream.of(ccMail.trim().split("\\s*,\\s*")).collect(Collectors.toSet());
    }

    populateRecipientsFromUserGroups(emailStepParameters, accountId, ambiance, logCallback, toRecipients, ccRecipients);

    if (EmptyPredicate.isEmpty(toRecipients)) {
      throw new InvalidRequestException("At least one recipient must be specified for the email step.");
    }

    if (emailStepParameters.subject == null || StringUtils.isBlank(emailStepParameters.subject.getValue())) {
      throw new InvalidRequestException("Email subject cannot be blank");
    }
    if (emailStepParameters.body == null || StringUtils.isBlank(emailStepParameters.body.getValue())) {
      throw new InvalidRequestException("Email body cannot be blank");
    }

    String settingValue = "";
    try {
      settingValue = NGRestUtils
                         .getResponse(settingsClient.getSetting(EMAIL_TO_NON_HARNESS_USERS_SETTING_KEY,
                             AmbianceUtils.getAccountId(ambiance), AmbianceUtils.getOrgIdentifier(ambiance),
                             AmbianceUtils.getProjectIdentifier(ambiance)))
                         .getValue();
    } catch (Exception ex) {
      log.error("Failed to fetch setting value for {}", EMAIL_TO_NON_HARNESS_USERS_SETTING_KEY, ex);
    }
    StringBuilder body = new StringBuilder(emailStepParameters.body.getValue());
    if (!pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.CDS_EMAIL_USE_DEFAULT_FORMATTING)) {
      String[] lines = emailStepParameters.body.getValue().split("\n");
      body = new StringBuilder();
      for (String line : lines) {
        body.append("<br>").append(line).append("</br>");
      }
    }

    EmailDTO emailDTO = EmailDTO.builder()
                            .toRecipients(toRecipients)
                            .ccRecipients(ccRecipients)
                            .body(body.toString())
                            .subject(emailStepParameters.subject.getValue())
                            .accountId(accountId)
                            .notificationId(notificationId)
                            .sendToNonHarnessRecipients(EMAIL_TO_NON_HARNESS_USERS_TRUE_VALUE.equals(settingValue))
                            .build();
    logCallback.saveExecutionLog("Email step execution started");

    boolean isFireAndForget = Boolean.TRUE.equals(emailStepParameters.getFireAndForget());

    if (isFireAndForget) {
      return executeFireAndForget(emailDTO, notificationId, startTime, logCallback);
    }

    try {
      Response<ResponseDTO<NotificationTaskResponse>> response = notificationClient.sendEmail(emailDTO);

      if (!response.isSuccessful()) {
        logCallback.saveExecutionLog("Failed to send the email", LogLevel.INFO, CommandExecutionStatus.FAILURE);
        ErrorDTO responseDTO =
            JsonUtils.asObjectWithExceptionHandlingType(response.errorBody().string(), ErrorDTO.class);
        FailureData failureData = FailureData.newBuilder()
                                      .addFailureTypes(FailureType.APPLICATION_FAILURE)
                                      .setLevel(io.harness.eraro.Level.ERROR.name())
                                      .setCode(GENERAL_ERROR.name())
                                      .setMessage(responseDTO.getMessage())
                                      .build();
        return StepResponse.builder()
            .status(Status.FAILED)
            .failureInfo(FailureInfo.newBuilder().addFailureData(failureData).build())
            .unitProgressList(Collections.singletonList(UnitProgress.newBuilder()
                                                            .setStatus(UnitStatus.FAILURE)
                                                            .setStartTime(startTime)
                                                            .setEndTime(System.currentTimeMillis())
                                                            .build()))
            .build();
      } else {
        logCallback.saveExecutionLog(
            String.format("Successfully sent an email with subject- [" + emailDTO.getSubject() + "]."));
      }

      if (response.body().getStatus() == io.harness.ng.core.Status.SUCCESS
          && StringUtils.isNotBlank(response.body().getData().getErrorMessage())) {
        logCallback.saveExecutionLog(String.format(response.body().getData().getErrorMessage()));
      }

    } catch (IOException e) {
      logCallback.saveExecutionLog("Failed to send the email. The reasons could be -\n"
              + "- The SMTP server may not be setup correctly(if configured) \n"
              + "- Delegate unable to reach custom SMTP server(if configured)\n"
              + "- Something went wrong on Harness's end.",
          LogLevel.INFO, CommandExecutionStatus.FAILURE);
      log.error("Not able to send emails for notificationId: {}", notificationId, e);
      return StepResponse.builder()
          .status(Status.FAILED)
          .unitProgressList(Collections.singletonList(UnitProgress.newBuilder()
                                                          .setStatus(UnitStatus.FAILURE)
                                                          .setStartTime(startTime)
                                                          .setEndTime(System.currentTimeMillis())
                                                          .build()))
          .build();
    }

    logCallback.saveExecutionLog("Email step execution completed", LogLevel.INFO, CommandExecutionStatus.SUCCESS);
    return buildSuccessResponse(notificationId, startTime);
  }

  private StepResponse executeFireAndForget(
      EmailDTO emailDTO, String notificationId, long startTime, NGLogCallback logCallback) {
    logCallback.saveExecutionLog("Email step running in fire-and-forget mode");
    try {
      Response<ResponseDTO<NotificationTaskResponse>> response = notificationClient.sendEmailAsync(emailDTO);
      if (!response.isSuccessful()) {
        logCallback.saveExecutionLog("Failed to send the email", LogLevel.INFO, CommandExecutionStatus.FAILURE);
        ErrorDTO responseDTO =
            JsonUtils.asObjectWithExceptionHandlingType(response.errorBody().string(), ErrorDTO.class);
        FailureData failureData = FailureData.newBuilder()
                                      .addFailureTypes(FailureType.APPLICATION_FAILURE)
                                      .setLevel(io.harness.eraro.Level.ERROR.name())
                                      .setCode(GENERAL_ERROR.name())
                                      .setMessage(responseDTO.getMessage())
                                      .build();
        return StepResponse.builder()
            .status(Status.FAILED)
            .failureInfo(FailureInfo.newBuilder().addFailureData(failureData).build())
            .unitProgressList(Collections.singletonList(UnitProgress.newBuilder()
                                                            .setStatus(UnitStatus.FAILURE)
                                                            .setStartTime(startTime)
                                                            .setEndTime(System.currentTimeMillis())
                                                            .build()))
            .build();
      }
      logCallback.saveExecutionLog(
          String.format("Successfully sent an email with subject- [%s].", emailDTO.getSubject()));
    } catch (IOException e) {
      logCallback.saveExecutionLog("Failed to submit email. Something went wrong on Harness's end.", LogLevel.INFO,
          CommandExecutionStatus.FAILURE);
      log.error("Not able to send emails for notificationId: {}", notificationId, e);
      return StepResponse.builder()
          .status(Status.FAILED)
          .unitProgressList(Collections.singletonList(UnitProgress.newBuilder()
                                                          .setStatus(UnitStatus.FAILURE)
                                                          .setStartTime(startTime)
                                                          .setEndTime(System.currentTimeMillis())
                                                          .build()))
          .build();
    }
    logCallback.saveExecutionLog("Email step execution completed", LogLevel.INFO, CommandExecutionStatus.SUCCESS);
    return buildSuccessResponse(notificationId, startTime);
  }

  private StepResponse buildSuccessResponse(String notificationId, long startTime) {
    return StepResponse.builder()
        .status(Status.SUCCEEDED)
        .stepOutcome(StepResponse.StepOutcome.builder()
                         .name(YAMLFieldNameConstants.OUTPUT)
                         .outcome(EmailOutcome.builder().notificationId(notificationId).build())
                         .build())
        .unitProgressList(Collections.singletonList(UnitProgress.newBuilder()
                                                        .setStatus(UnitStatus.SUCCESS)
                                                        .setStartTime(startTime)
                                                        .setEndTime(System.currentTimeMillis())
                                                        .build()))
        .build();
  }

  @Override
  public Class<StepBaseParameters> getStepParametersClass() {
    return StepBaseParameters.class;
  }

  EmailStepParameters getEmailStepParameters(StepBaseParameters stepBaseParameters) {
    String version = stepBaseParameters.getSpec().getVersion();
    switch (version) {
      case HarnessYamlVersion.V0:
        return (EmailStepParameters) stepBaseParameters.getSpec();
      case HarnessYamlVersion.V1:
        return ((io.harness.steps.email.v1.EmailStepParameters) stepBaseParameters.getSpec()).toEmailStepParametersV0();
      default:
        log.error("Version {} not supported", version);
        throw new InvalidRequestException(String.format("Version %s not supported", version));
    }
  }

  private Set<String> getEmailsForUserGroup(
      String accountId, String orgId, String projectId, NGLogCallback logCallback, String groupId) {
    if (StringUtils.isBlank(groupId)) {
      logCallback.saveExecutionLog("User group ID cannot be empty.", LogLevel.WARN);
      return Collections.emptySet();
    }
    try {
      IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(groupId, accountId, orgId, projectId);
      UserGroupResponseV2DTO userGroup = NGRestUtils.getResponse(
          userGroupClient.getUserGroupV2(identifierRef.getIdentifier(), identifierRef.getAccountIdentifier(),
              identifierRef.getOrgIdentifier(), identifierRef.getProjectIdentifier()));
      if (userGroup == null || EmptyPredicate.isEmpty(userGroup.getUsers())) {
        log.info("User group {} does not have a users list.", groupId);
        logCallback.saveExecutionLog("User group " + groupId + "does not have a users list.", LogLevel.WARN);
        return Collections.emptySet();
      }
      Set<String> groupEmails = userGroup.getUsers()
                                    .stream()
                                    .filter(Objects::nonNull)
                                    .map(UserBasicInfo::getEmail)
                                    .filter(StringUtils::isNotBlank)
                                    .collect(Collectors.toSet());
      if (EmptyPredicate.isEmpty(groupEmails)) {
        log.info("User group {} does not have any valid users (empty email list).", groupId);
        logCallback.saveExecutionLog("User group " + groupId + " does not have any valid users.", LogLevel.WARN);
        return Collections.emptySet();
      }
      return groupEmails;
    } catch (Exception ex) {
      log.error("Unexpected error while fetching user group emails for groupId: {}", groupId, ex);
      logCallback.saveExecutionLog(
          "Unexpected error while fetching user group emails for groupId: " + groupId + ". Reason: " + ex.getMessage(),
          LogLevel.ERROR);
    }
    return Collections.emptySet();
  }

  private void populateRecipientsFromUserGroups(EmailStepParameters emailStepParameters, String accountId,
      Ambiance ambiance, NGLogCallback logCallback, Set<String> toRecipients, Set<String> ccRecipients) {
    var ccUserGroups = new HashSet<String>();
    var toUserGroups = new HashSet<String>();
    var allUserGroups = new HashSet<String>();

    List<String> toUserGroupsValue = ParameterFieldHelper.getParameterFieldValue(emailStepParameters.getToUserGroups());
    if (toUserGroupsValue != null) {
      toUserGroups.addAll(toUserGroupsValue);
      allUserGroups.addAll(toUserGroupsValue);
    }
    List<String> ccUserGroupsValue = ParameterFieldHelper.getParameterFieldValue(emailStepParameters.getCcUserGroups());
    if (ccUserGroupsValue != null) {
      ccUserGroups.addAll(ccUserGroupsValue);
      allUserGroups.addAll(ccUserGroupsValue);
    }
    for (var groupId : allUserGroups) {
      // get user group emails
      var emails = getEmailsForUserGroup(accountId, AmbianceUtils.getOrgIdentifier(ambiance),
          AmbianceUtils.getProjectIdentifier(ambiance), logCallback, groupId);
      if (ccUserGroups.contains(groupId)) {
        ccRecipients.addAll(emails);
      }
      if (toUserGroups.contains(groupId)) {
        toRecipients.addAll(emails);
      }
    }
  }
}
