/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.eventlistener.entities;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.eraro.ErrorCode.EVENT_LISTENER_STEP_FAILURE;
import static io.harness.exception.WingsException.USER;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eraro.Level;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UniqueIdAware;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.eventlistener.EventListenerStepParameters;
import io.harness.steps.eventlistener.beans.EventListenerStepInstanceStatus;
import io.harness.timeout.TimeoutParameters;
import io.harness.yaml.core.timeout.Timeout;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
@OwnedBy(CDC)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "EventListenerStepInstanceKeys")
@StoreIn(DbAliases.PMS)
@Document("eventListenerStepInstances")
@Entity(value = "eventListenerStepInstances", noClassnameStored = true)
@Persistent
@TypeAlias("eventListenerStepInstances")
public class EventListenerStepInstance implements PersistentEntity, UniqueIdAware {
  public static final long TTL_MONTHS = 6;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_parentUniqueId_pipelineIdentifier_webhookIdentifier_status_createdAt")
                 .field(EventListenerStepInstanceKeys.accountIdentifier)
                 .field(EventListenerStepInstanceKeys.parentUniqueId)
                 .field(EventListenerStepInstanceKeys.pipelineIdentifier)
                 .field(EventListenerStepInstanceKeys.webhookIdentifier)
                 .field(EventListenerStepInstanceKeys.status)
                 .rangeField(EventListenerStepInstanceKeys.createdAt)
                 .build())
        .build();
  }

  @Id @dev.morphia.annotations.Id String id;
  @NotNull Ambiance ambiance;

  // TTL index
  @FdTtlIndex Date validUntil;

  // preferably use these ambiance fields saved at first-level
  @FdIndex @NotNull String nodeExecutionId;
  @NotNull String planExecutionId;
  @NotNull String accountIdentifier;
  @NotNull @Deprecated String orgIdentifier;
  @NotNull @Deprecated String projectIdentifier;
  @NotNull String pipelineIdentifier;
  @NotNull String webhookIdentifier;
  Map<String, Object> inputVariables;
  Map<String, Object> outputVariables;
  @NotNull String successCriteria;
  String failureCriteria;
  @FdIndex String parentUniqueId;

  @NotNull EventListenerStepInstanceStatus status;
  long deadline;

  @CreatedDate Long createdAt;
  @LastModifiedDate Long lastModifiedAt;

  public static EventListenerStepInstance fromStepParameters(
      Ambiance ambiance, StepBaseParameters stepParameters, ScopeInfo scopeInfo) {
    if (stepParameters == null) {
      return null;
    }

    EventListenerStepParameters specParameters = (EventListenerStepParameters) stepParameters.getSpec();
    if (specParameters.getSuccessCriteria().fetchFinalValue() == null
        || EmptyPredicate.isEmpty(specParameters.getSuccessCriteria().fetchFinalValue().toString())) {
      throw new InvalidRequestException("Success criteria cannot be empty.", USER);
    }
    if (specParameters.getWebhookIdentifier().fetchFinalValue() == null) {
      throw new InvalidRequestException("Webhook identifier cannot be empty.", USER);
    }
    EventListenerStepInstance instance =
        EventListenerStepInstance.builder()
            .webhookIdentifier(specParameters.getWebhookIdentifier().fetchFinalValue().toString())
            .inputVariables(specParameters.getInputVariables())
            .outputVariables(specParameters.getOutputVariables())
            .successCriteria(specParameters.getSuccessCriteria().fetchFinalValue().toString())
            .failureCriteria(specParameters.getFailureCriteria().fetchFinalValue() != null
                    ? specParameters.getFailureCriteria().fetchFinalValue().toString()
                    : null)
            .build();
    instance.updateFromStepParameters(ambiance, stepParameters, scopeInfo);
    return instance;
  }

  public FailureInfo getFailureInfo() {
    if (EventListenerStepInstanceStatus.FAILED == status) {
      FailureData failureData = FailureData.newBuilder()
                                    .setLevel(Level.ERROR.name())
                                    .setCode(EVENT_LISTENER_STEP_FAILURE.name())
                                    .setMessage("Failure criteria has been met.")
                                    .build();
      return FailureInfo.newBuilder().addFailureData(failureData).build();
    }
    if (EventListenerStepInstanceStatus.RUNTIME_EXCEPTION == status) {
      FailureData failureData = FailureData.newBuilder()
                                    .setLevel(Level.ERROR.name())
                                    .setCode(EVENT_LISTENER_STEP_FAILURE.name())
                                    .setMessage("Exception occurred while evaluation criteria.")
                                    .build();
      return FailureInfo.newBuilder().addFailureData(failureData).build();
    }
    return null;
  }

  protected void updateFromStepParameters(Ambiance ambiance, StepBaseParameters stepParameters, ScopeInfo scopeInfo) {
    if (stepParameters == null) {
      return;
    }

    setId(generateUuid());
    setAmbiance(ambiance);
    setParentUniqueId(scopeInfo.getUniqueId());
    // set these ambiance fields as first level fields
    setNodeExecutionId(AmbianceUtils.obtainCurrentRuntimeId(ambiance));
    setAccountIdentifier(AmbianceUtils.getAccountId(ambiance));
    setOrgIdentifier(AmbianceUtils.getOrgIdentifier(ambiance));
    setProjectIdentifier(AmbianceUtils.getProjectIdentifier(ambiance));
    setPipelineIdentifier(AmbianceUtils.getPipelineIdentifier(ambiance));
    setPlanExecutionId(ambiance.getPlanExecutionId());
    setStatus(EventListenerStepInstanceStatus.WAITING);
    setDeadline(calculateDeadline(stepParameters.getTimeout()));
    setValidUntil(Date.from(OffsetDateTime.now().plusMonths(TTL_MONTHS).toInstant()));
  }

  private static long calculateDeadline(ParameterField<String> timeoutField) {
    if (ParameterField.isNull(timeoutField)) {
      return TimeoutParameters.DEFAULT_TIMEOUT_IN_MILLIS;
    } else if (timeoutField.isExpression()) {
      throw new InvalidArgumentsException(String.format("Invalid timeout: '%s'", timeoutField.fetchFinalValue()));
    } else if (timeoutField.getValue() == null) {
      return TimeoutParameters.DEFAULT_TIMEOUT_IN_MILLIS;
    }

    Timeout timeout = Timeout.fromString(timeoutField.getValue());
    if (timeout == null) {
      throw new InvalidArgumentsException(String.format("Invalid timeout: '%s'", timeoutField.fetchFinalValue()));
    }
    return System.currentTimeMillis() + timeout.getTimeoutInMillis();
  }

  @Override
  public void setUniqueId(String uniqueId) {}

  @Override
  public String getUniqueId() {
    return this.id;
  }
}
