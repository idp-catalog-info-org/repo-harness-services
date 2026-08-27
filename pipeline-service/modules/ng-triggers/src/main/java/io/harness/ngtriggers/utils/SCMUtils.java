/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.utils;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static software.wings.beans.TaskType.SCM_GIT_REF_TASK;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.DecryptableEntity;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.IdentifierRef;
import io.harness.beans.ScopeInfo;
import io.harness.connector.helper.GitApiAccessDecryptionHelper;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.scm.intfc.ScmConnector;
import io.harness.delegate.task.scm.GitRefType;
import io.harness.delegate.task.scm.ScmGitRefTaskParams;
import io.harness.delegate.task.scm.ScmGitRefTaskResponseData;
import io.harness.exception.ConnectorNotFoundException;
import io.harness.exception.TriggerException;
import io.harness.exception.WingsException;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;
import io.harness.product.ci.scm.proto.Commit;
import io.harness.product.ci.scm.proto.FindCommitResponse;
import io.harness.product.ci.scm.proto.SCMGrpc.SCMBlockingStub;
import io.harness.secrets.SecretDecryptor;
import io.harness.serializer.KryoSerializer;
import io.harness.service.ScmServiceClient;
import io.harness.tasks.BinaryResponseData;
import io.harness.tasks.ErrorResponseData;
import io.harness.tasks.ResponseData;
import io.harness.utils.ConnectorUtils;
import io.harness.utils.ScopeResolutionHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.InvalidProtocolBufferException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@Singleton
@OwnedBy(PIPELINE)
public class SCMUtils {
  private ConnectorUtils connectorUtils;
  private KryoSerializer kryoSerializer;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  private SCMBlockingStub scmBlockingStub;
  private SCMDataObtainer scmDataObtainer;
  private ScmServiceClient scmServiceClient;
  private SecretDecryptor secretDecryptor;
  private TaskExecutionUtils taskExecutionUtils;
  private ScopeResolutionHelper scopeResolutionHelper;
  private static final Duration RETRY_SLEEP_DURATION = Duration.ofSeconds(2);
  private static final int MAX_ATTEMPTS = 3;

  public String fetchCommitMessage(String commitRef, FilterRequestData filterRequestData) {
    Optional<Commit> commit = fetchCommitDetails(commitRef, filterRequestData);
    return commit.map(Commit::getMessage).orElse("");
  }

  private Optional<Commit> fetchCommitDetails(String commitRef, FilterRequestData filterRequestData) {
    Map<String, Optional<ScopeInfo>> parentUniqueIdToScopeInfoMap = null;
    boolean isParentIdQueryingEnabled = true;
    if (isParentIdQueryingEnabled) {
      List<String> parentUniqueIds = filterRequestData.getDetails()
                                         .stream()
                                         .map(triggerDetails -> triggerDetails.getNgTriggerEntity().getParentUniqueId())
                                         .collect(Collectors.toList());
      parentUniqueIdToScopeInfoMap =
          scopeResolutionHelper.getScopeInfos(filterRequestData.getAccountId(), parentUniqueIds);
    }
    for (TriggerDetails triggerDetails : filterRequestData.getDetails()) {
      try {
        String connectorIdentifier =
            triggerDetails.getNgTriggerEntity().getMetadata().getWebhook().getGit().getConnectorIdentifier();
        ScopeInfo scopeInfo = isParentIdQueryingEnabled
            ? parentUniqueIdToScopeInfoMap
                  .getOrDefault(triggerDetails.getNgTriggerEntity().getParentUniqueId(), Optional.empty())
                  .orElse(null)
            : null;
        ConnectorDetails connectorDetails = connectorUtils.getConnectorDetails(
            IdentifierRef.builder()
                .accountIdentifier(triggerDetails.getNgTriggerEntity().getAccountId())
                .orgIdentifier(isParentIdQueryingEnabled ? scopeInfo.getOrgIdentifier()
                                                         : triggerDetails.getNgTriggerEntity().getOrgIdentifier())
                .projectIdentifier(isParentIdQueryingEnabled
                        ? scopeInfo.getProjectIdentifier()
                        : triggerDetails.getNgTriggerEntity().getProjectIdentifier())
                .build(),
            connectorIdentifier);
        ScmConnector scmConnector = (ScmConnector) connectorDetails.getConnectorConfig();
        scmConnector.setUrl(scmDataObtainer.getGitURL(connectorDetails, triggerDetails));
        boolean executeOnDelegate =
            connectorDetails.getExecuteOnDelegate() == null || connectorDetails.getExecuteOnDelegate();
        if (executeOnDelegate) {
          return Optional.of(getCommitDetailsViaDelegate(commitRef, scmConnector, triggerDetails, connectorDetails));
        } else {
          return Optional.of(getCommitDetailsViaManager(commitRef, scmConnector, connectorDetails));
        }
      } catch (Exception e) {
        log.error(format("Failed to fetch commit details for commitRef [%s] and Account [%s]. Exception: %s", commitRef,
                      filterRequestData.getAccountId(), e.getMessage()),
            e);
      }
    }
    return Optional.empty();
  }

  private Commit getCommitDetailsViaDelegate(
      String commitRef, ScmConnector scmConnector, TriggerDetails triggerDetails, ConnectorDetails connectorDetails) {
    ScmGitRefTaskParams scmGitRefTaskParams = ScmGitRefTaskParams.builder()
                                                  .ref(commitRef)
                                                  .gitRefType(GitRefType.FIND_COMMIT)
                                                  .encryptedDataDetails(connectorDetails.getEncryptedDataDetails())
                                                  .scmConnector(scmConnector)
                                                  .build();
    ResponseData responseData =
        taskExecutionUtils.executeSyncTask(DelegateTaskRequest.builder()
                                               .accountId(triggerDetails.getNgTriggerEntity().getAccountId())
                                               .executionTimeout(Duration.ofSeconds(30))
                                               .taskType(SCM_GIT_REF_TASK.name())
                                               .taskParameters(scmGitRefTaskParams)
                                               .build());

    if (BinaryResponseData.class.isAssignableFrom(responseData.getClass())) {
      BinaryResponseData binaryResponseData = (BinaryResponseData) responseData;
      Object object = binaryResponseData.isUsingKryoWithoutReference()
          ? referenceFalseKryoSerializer.asInflatedObject(binaryResponseData.getData())
          : kryoSerializer.asInflatedObject(binaryResponseData.getData());
      if (ScmGitRefTaskResponseData.class.isAssignableFrom(object.getClass())) {
        ScmGitRefTaskResponseData scmGitRefTaskResponseData = (ScmGitRefTaskResponseData) object;
        try {
          return FindCommitResponse.parseFrom(scmGitRefTaskResponseData.getFindCommitResponse()).getCommit();
        } catch (InvalidProtocolBufferException e) {
          throw new TriggerException("Failed to fetch Commit Details. Reason: " + e.getMessage(), WingsException.SRE);
        }
      } else if (object instanceof ErrorResponseData) {
        ErrorResponseData errorResponseData = (ErrorResponseData) object;
        throw new TriggerException(
            "Failed to fetch Commit Details. Reason: " + errorResponseData.getErrorMessage(), WingsException.SRE);
      }
    }
    throw new TriggerException("Failed to fetch Commit Details", WingsException.SRE);
  }

  private Commit getCommitDetailsViaManager(
      String commitRef, ScmConnector scmConnector, ConnectorDetails connectorDetails) {
    final DecryptableEntity decryptableEntity =
        secretDecryptor.decrypt(GitApiAccessDecryptionHelper.getAPIAccessDecryptableEntity(scmConnector),
            connectorDetails.getEncryptedDataDetails());
    GitApiAccessDecryptionHelper.setAPIAccessDecryptableEntity(scmConnector, decryptableEntity);
    RetryPolicy<Object> retryPolicy = getRetryPolicy(
        format("[Retrying failed call to fetch commit details: [%s], attempt: {}", connectorDetails.getIdentifier()),
        format("Failed call to fetch commit details: [%s] after retrying {} times", connectorDetails.getIdentifier()));
    FindCommitResponse findCommitResponse =
        Failsafe.with(retryPolicy).get(() -> scmServiceClient.findCommit(scmConnector, commitRef, scmBlockingStub));
    return findCommitResponse.getCommit();
  }

  private RetryPolicy<Object> getRetryPolicy(String failedAttemptMessage, String failureMessage) {
    return new RetryPolicy<>()
        .handle(Exception.class)
        .abortOn(ConnectorNotFoundException.class)
        .withDelay(RETRY_SLEEP_DURATION)
        .withMaxAttempts(MAX_ATTEMPTS)
        .onFailedAttempt(event -> log.info(failedAttemptMessage, event.getAttemptCount(), event.getLastFailure()))
        .onFailure(event -> log.error(failureMessage, event.getAttemptCount(), event.getFailure()));
  }
}
