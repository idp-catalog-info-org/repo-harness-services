/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.utils;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.NgSetupFields.NG;
import static io.harness.delegate.beans.NgSetupFields.OWNER;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.DelegateTaskRequest.DelegateTaskRequestBuilder;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.scm.intfc.ScmConnector;
import io.harness.delegate.task.scm.ScmPathFilterEvaluationTaskParams;
import io.harness.delegate.task.scm.ScmPathFilterEvaluationTaskResponse;
import io.harness.delegate.task.scm.ScmPathFilterEvaluationsTaskResponse;
import io.harness.delegate.task.scm.TriggerCondition;
import io.harness.delegate.task.scm.TriggerFilepathResponse;
import io.harness.delegate.utils.TaskSetupAbstractionHelper;
import io.harness.exception.TriggerException;
import io.harness.exception.WingsException;
import io.harness.ngtriggers.beans.source.webhook.v2.condition.TriggerEventDataCondition;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;
import io.harness.serializer.KryoSerializer;
import io.harness.tasks.BinaryResponseData;
import io.harness.tasks.ErrorResponseData;
import io.harness.tasks.ResponseData;
import io.harness.utils.PmsFeatureFlagEvaluator;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CI)
public class SCMFilePathEvaluatorOnDelegate extends SCMFilePathEvaluator {
  private TaskExecutionUtils taskExecutionUtils;
  private KryoSerializer kryoSerializer;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  private final TaskSetupAbstractionHelper taskSetupAbstractionHelper;
  private final PmsFeatureFlagEvaluator pmsFeatureFlagEvaluator;

  @Override
  public ScmPathFilterEvaluationTaskResponse execute(FilterRequestData filterRequestData,
      TriggerEventDataCondition pathCondition, ConnectorDetails connectorDetails, ScmConnector scmConnector) {
    ScmPathFilterEvaluationTaskParams params =
        getScmPathFilterEvaluationTaskParams(filterRequestData, pathCondition, connectorDetails, scmConnector);
    params.setBBOnpremCompareCommitsIssueFixFFEnabled(
        pmsFeatureFlagEvaluator.isBBOnpremCompareCommitsIssueFixFFEnabled(filterRequestData.getAccountId()));

    DelegateTaskRequestBuilder delegateTaskRequestBuilder =
        DelegateTaskRequest.builder()
            .accountId(filterRequestData.getAccountId())
            .taskType(TaskType.SCM_PATH_FILTER_EVALUATION_TASK.toString())
            .taskParameters(params)
            .executionTimeout(Duration.ofMinutes(1l))
            .taskSetupAbstraction(NG, "true");

    String owner = taskSetupAbstractionHelper.getOwner(
        filterRequestData.getAccountId(), connectorDetails.getOrgIdentifier(), connectorDetails.getProjectIdentifier());
    if (isNotEmpty(owner)) {
      delegateTaskRequestBuilder.taskSetupAbstraction(OWNER, owner);
    }

    if (connectorDetails.getOrgIdentifier() != null) {
      delegateTaskRequestBuilder.taskSetupAbstraction("orgIdentifier", connectorDetails.getOrgIdentifier());
    }

    if (connectorDetails.getProjectIdentifier() != null) {
      delegateTaskRequestBuilder.taskSetupAbstraction("projectIdentifier", connectorDetails.getProjectIdentifier());
    }

    if (connectorDetails.getDelegateSelectors() != null) {
      delegateTaskRequestBuilder.taskSelectors(connectorDetails.getDelegateSelectors());
    }

    ResponseData responseData = taskExecutionUtils.executeSyncTask(delegateTaskRequestBuilder.build());

    if (BinaryResponseData.class.isAssignableFrom(responseData.getClass())) {
      BinaryResponseData binaryResponseData = (BinaryResponseData) responseData;
      Object object = binaryResponseData.isUsingKryoWithoutReference()
          ? referenceFalseKryoSerializer.asInflatedObject(binaryResponseData.getData())
          : kryoSerializer.asInflatedObject(binaryResponseData.getData());
      if (object instanceof ScmPathFilterEvaluationTaskResponse) {
        return (ScmPathFilterEvaluationTaskResponse) object;
      } else if (object instanceof ErrorResponseData) {
        ErrorResponseData errorResponseData = (ErrorResponseData) object;
        throw new TriggerException(
            format("Failed to fetch PR Details. Reason: {%s}", errorResponseData.getErrorMessage()),
            WingsException.SRE);
      }
    }

    return null;
  }

  @Override
  public List<TriggerFilepathResponse> execute(FilterRequestData filterRequestData,
      TriggerEventDataCondition pathCondition, List<TriggerCondition> triggerConditions,
      ConnectorDetails connectorDetails, ScmConnector scmConnector) {
    ScmPathFilterEvaluationTaskParams params = getScmPathFilterEvaluationTaskParams(
        filterRequestData, pathCondition, triggerConditions, connectorDetails, scmConnector);
    params.setBBOnpremCompareCommitsIssueFixFFEnabled(
        pmsFeatureFlagEvaluator.isBBOnpremCompareCommitsIssueFixFFEnabled(filterRequestData.getAccountId()));

    DelegateTaskRequestBuilder delegateTaskRequestBuilder =
        DelegateTaskRequest.builder()
            .accountId(filterRequestData.getAccountId())
            .taskType(TaskType.SCM_PATH_FILTER_EVALUATION_TASK.toString())
            .taskParameters(params)
            .executionTimeout(Duration.ofMinutes(1l))
            .taskSetupAbstraction(NG, "true");

    String owner = taskSetupAbstractionHelper.getOwner(
        filterRequestData.getAccountId(), connectorDetails.getOrgIdentifier(), connectorDetails.getProjectIdentifier());
    if (isNotEmpty(owner)) {
      delegateTaskRequestBuilder.taskSetupAbstraction(OWNER, owner);
    }

    if (connectorDetails.getOrgIdentifier() != null) {
      delegateTaskRequestBuilder.taskSetupAbstraction("orgIdentifier", connectorDetails.getOrgIdentifier());
    }

    if (connectorDetails.getProjectIdentifier() != null) {
      delegateTaskRequestBuilder.taskSetupAbstraction("projectIdentifier", connectorDetails.getProjectIdentifier());
    }

    if (connectorDetails.getDelegateSelectors() != null) {
      delegateTaskRequestBuilder.taskSelectors(connectorDetails.getDelegateSelectors());
    }

    log.info("Executing SCM Path Filter Evaluation Task on Delegate for multiple trigger conditions");
    ResponseData responseData = taskExecutionUtils.executeSyncTask(delegateTaskRequestBuilder.build());

    if (BinaryResponseData.class.isAssignableFrom(responseData.getClass())) {
      BinaryResponseData binaryResponseData = (BinaryResponseData) responseData;
      Object object = binaryResponseData.isUsingKryoWithoutReference()
          ? referenceFalseKryoSerializer.asInflatedObject(binaryResponseData.getData())
          : kryoSerializer.asInflatedObject(binaryResponseData.getData());
      if (object instanceof ScmPathFilterEvaluationsTaskResponse) {
        return ((ScmPathFilterEvaluationsTaskResponse) object).getTriggerFilepathResponses();
      } else if (object instanceof ErrorResponseData) {
        ErrorResponseData errorResponseData = (ErrorResponseData) object;
        throw new TriggerException(
            format("Failed to fetch PR Details. Reason: {%s}", errorResponseData.getErrorMessage()),
            WingsException.SRE);
      } else {
        String objectType = (object != null) ? object.getClass().getSimpleName() : "null";
        throw new TriggerException(
            format("Unable to deserialize SCM Path Filter Evaluation Task Response. Response type: {%s}", objectType),
            WingsException.SRE);
      }
    }

    return null;
  }
}
