/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.response;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.EXCEPTION_WHILE_PROCESSING;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.FAILED_TO_FETCH_PR_COMMITS;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.FAILED_TO_FETCH_PR_DETAILS;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.INVALID_HARNESS_ARTIFACT_REGISTRY_TRIGGER_ACTION;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.INVALID_PAYLOAD;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.INVALID_RUNTIME_INPUT_YAML;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.MERGE_QUEUE_CHECKS_ALREADY_RUNNING;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.MERGE_QUEUE_CHECKS_CANCELED;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.NEW_ARTIFACT_EVENT_PROCESSED;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.NEW_MANIFEST_EVENT_PROCESSED;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.SCM_SERVICE_CONNECTION_FAILED;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.SKIPPED;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.TARGET_DID_NOT_EXECUTE;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.TARGET_EXECUTION_REQUESTED;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.TRIGGER_AUTHENTICATION_FAILED;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.TRIGGER_CONFIRMATION_FAILED;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.TRIGGER_CONFIRMATION_SUCCESSFUL;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.TRIGGER_DID_NOT_MATCH_ARTIFACT_JEXL_CONDITION;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.TRIGGER_DID_NOT_MATCH_EVENT_CONDITION;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.TRIGGER_DID_NOT_MATCH_METADATA_CONDITION;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.VALIDATION_FAILED_FOR_TRIGGER;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.HeaderConfig;
import io.harness.ngtriggers.beans.source.NGTriggerType;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@Data
@Builder
@OwnedBy(PIPELINE)
public class TriggerEventResponse {
  public enum FinalStatus {
    SCM_SERVICE_CONNECTION_FAILED("Scm service connection failed"),
    INVALID_PAYLOAD("Invalid payload"),
    TRIGGER_DID_NOT_MATCH_EVENT_CONDITION("Trigger did not match event condition"),
    TRIGGER_DID_NOT_MATCH_METADATA_CONDITION("Trigger did not match metadata condition"),
    TRIGGER_DID_NOT_MATCH_ARTIFACT_JEXL_CONDITION("Trigger did not match artifact jexl condition"),
    NO_MATCHING_TRIGGER_FOR_REPO("No matching trigger for repo"),
    NO_MATCHING_TRIGGER_FOR_EVENT_ACTION("No matching trigger for event action"),
    NO_MATCHING_TRIGGER_FOR_METADATA_CONDITIONS("No matching trigger for metadata conditions"),
    NO_MATCHING_TRIGGER_FOR_PAYLOAD_CONDITIONS("No matching trigger for payload conditions"),
    NO_MATCHING_TRIGGER_FOR_JEXL_CONDITIONS("No matching trigger for jexl conditions"),
    NO_MATCHING_TRIGGER_FOR_HEADER_CONDITIONS("No matching trigger for header conditions"),
    INVALID_RUNTIME_INPUT_YAML("Invalid runtime input yaml"),
    TARGET_DID_NOT_EXECUTE("Target did not execute"),
    TARGET_EXECUTION_REQUESTED("Target execution requested"),
    NO_ENABLED_CUSTOM_TRIGGER_FOUND("No enabled custom trigger found"),
    NO_ENABLED_CUSTOM_TRIGGER_FOUND_FOR_ACCOUNT("No enabled custom trigger found for account"),
    NO_ENABLED_TRIGGER_FOR_PROJECT("No enabled trigger for project"),
    NO_ENABLED_TRIGGER_FOR_ACCOUNT("No enabled trigger for account"),
    NO_ENABLED_TRIGGER_FOR_SOURCEREPO_TYPE("No enabled trigger for source repo type"),
    NO_ENABLED_TRIGGER_FOR_ACCOUNT_SOURCE_REPO("No enabled trigger for account source repo"),
    NO_MATCHING_TRIGGER_FOR_FILEPATH_CONDITIONS("No matching trigger for filepath conditions"),
    FAILED_TO_FETCH_PR_DETAILS("Failed to fetch pr details"),
    FAILED_TO_FETCH_PR_COMMITS("Failed to fetch PR commits from SCM provider"),
    EXCEPTION_WHILE_PROCESSING("Exception while processing"),
    TRIGGER_CONFIRMATION_SUCCESSFUL("Trigger confirmation successful"),
    TRIGGER_CONFIRMATION_FAILED("Trigger confirmation failed"),
    TRIGGER_AUTHENTICATION_FAILED("Trigger authentication failed"),

    VALIDATION_FAILED_FOR_TRIGGER("Validation failed for trigger"),
    ALL_MAPPED_TRIGGER_FAILED_VALIDATION_FOR_POLLING_EVENT("All mapped trigger failed validation for polling event"),
    NO_MATCHING_TRIGGER_FOR_FOR_EVENT_SIGNATURES("No matching trigger for event signatures"),
    NO_MATCHING_TRIGGER_FOR_FOR_EVENT_CONDITION("No matching trigger for event condition"),
    POLLING_EVENT_WITH_NO_VERSIONS("Polling event with no versions"),
    // Build Trigger events
    NEW_ARTIFACT_EVENT_PROCESSED("New artifact event processed"),
    NEW_MANIFEST_EVENT_PROCESSED("New manifest event processed"),
    SKIPPED("Trigger event was skipped. Keyword skip ci/ci skip/pipeline skip/skip pipeline has been found."),
    QUEUED("Trigger event was queued"),
    INVALID_HARNESS_ARTIFACT_REGISTRY_TRIGGER_ACTION("Invalid Harness Artifact Registry trigger action"),
    NO_TRIGGERS_FOUND_FOR_HARNESS_ARTIFACT_REGISTRY_WEBHOOK("No trigger found for Harness Artifact Registry webhook"),
    HARNESS_ARTIFACT_REGISTRY_WEBHOOK_NOT_EXECUTED("HARNESS_ARTIFACT_REGISTRY webhook not executed"),
    MERGE_QUEUE_CHECKS_CANCELED("Merge queue checks canceled; in-flight execution(s) aborted"),
    MERGE_QUEUE_CHECKS_ALREADY_RUNNING(
        "Merge queue checks request ignored; an execution is already running for this commit");
    String message;
    FinalStatus(String message) {
      this.message = message;
    }

    public String getMessage() {
      return message;
    }
  }

  private String accountId;
  private String orgIdentifier;
  private String projectIdentifier;
  private String targetIdentifier;
  private String eventCorrelationId;
  private String pollingDocId;
  private String buildSourceType;
  private String build;
  private String payload;
  private List<HeaderConfig> headers;
  private long createdAt;
  private FinalStatus finalStatus;
  private String message;
  private String planExecutionId;
  private boolean exceptionOccurred;
  private String triggerIdentifier;
  private String triggerName;
  private TargetExecutionSummary targetExecutionSummary;
  private boolean enabled;
  NGTriggerType ngTriggerType;
  String triggerSubType;
  private String parentUniqueId;

  public static final Set<FinalStatus> SUCCESS_STATUSES =
      EnumSet.of(NEW_ARTIFACT_EVENT_PROCESSED, NEW_MANIFEST_EVENT_PROCESSED, TRIGGER_CONFIRMATION_SUCCESSFUL,
          TARGET_EXECUTION_REQUESTED, MERGE_QUEUE_CHECKS_CANCELED);

  // MERGE_QUEUE_CHECKS_ALREADY_RUNNING is a suppressed duplicate, not an outcome - nothing ran, so it belongs with
  // SKIPPED rather than SUCCESS.
  public static final Set<FinalStatus> SKIPPED_STATUSES =
      EnumSet.of(TRIGGER_DID_NOT_MATCH_EVENT_CONDITION, TRIGGER_DID_NOT_MATCH_METADATA_CONDITION,
          TRIGGER_DID_NOT_MATCH_ARTIFACT_JEXL_CONDITION, SKIPPED, MERGE_QUEUE_CHECKS_ALREADY_RUNNING);

  public static Set<FinalStatus> FAILED_NOTIFICATION_STATUS = EnumSet.of(SCM_SERVICE_CONNECTION_FAILED, INVALID_PAYLOAD,
      INVALID_RUNTIME_INPUT_YAML, TARGET_DID_NOT_EXECUTE, FAILED_TO_FETCH_PR_DETAILS, FAILED_TO_FETCH_PR_COMMITS,
      EXCEPTION_WHILE_PROCESSING, TRIGGER_CONFIRMATION_FAILED, TRIGGER_AUTHENTICATION_FAILED,
      VALIDATION_FAILED_FOR_TRIGGER, INVALID_HARNESS_ARTIFACT_REGISTRY_TRIGGER_ACTION);

  public static boolean isSuccessResponse(FinalStatus status) {
    return SUCCESS_STATUSES.contains(status);
  }
  public static boolean isSkippedResponse(FinalStatus status) {
    return SKIPPED_STATUSES.contains(status);
  }

  public static Set<FinalStatus> getFailedStatus() {
    return Arrays.stream(TriggerEventResponse.FinalStatus.values())
        .filter(status -> !SUCCESS_STATUSES.contains(status) && !SKIPPED_STATUSES.contains(status))
        .collect(Collectors.toSet());
  }

  public static boolean shouldSendFailureNotificationForStatus(FinalStatus status) {
    return FAILED_NOTIFICATION_STATUS.contains(status);
  }
}
