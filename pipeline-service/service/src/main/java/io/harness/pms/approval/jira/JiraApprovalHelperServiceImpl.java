/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.approval.jira;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.FeatureName.PL_ENABLE_GRANULAR_CLAIMS_FOR_VAULT;
import static io.harness.delegate.task.shell.ShellScriptTaskNG.COMMAND_UNIT;
import static io.harness.pms.approval.UnifiedApprovalConstants.JIRA_URL_PLUGIN_ENV;
import static io.harness.steps.approval.step.entities.ApprovalInstance.ASYNC_DELEGATE_TIMEOUT;
import static io.harness.steps.approval.step.entities.ApprovalUtils.JIRA_DELEGATE_TASK_NAME;
import static io.harness.steps.approval.step.entities.ApprovalUtils.updateTaskId;

import static java.lang.String.format;
import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.IdentifierRef;
import io.harness.beans.steps.stepinfo.RunStepInfoV1;
import io.harness.ci.execution.states.helpers.CDStepsEnvironmentVarsHelper;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.constants.OrchestrationPublisherName;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.TaskDetails;
import io.harness.delegate.TaskMode;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.TaskType;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.JiraConnectorDTO;
import io.harness.delegate.task.jira.JiraTaskNGParameters;
import io.harness.engine.pms.tasks.NgDelegate2TaskExecutor;
import io.harness.engine.pms.tasks.RunnerTaskExecutor;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.HarnessJiraException;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.ConnectorVariableConstants;
import io.harness.iterator.interfaces.PersistenceIterator;
import io.harness.jira.JiraActionNG;
import io.harness.logging.AutoLogContext;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccessWithEncryptionConsumer;
import io.harness.oidc.helper.OIDCContextHelper;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.gitsync.PmsGitSyncBranchContextGuard;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.ParameterField;
import io.harness.remote.client.NGRestUtils;
import io.harness.secrets.remote.SecretNGManagerClient;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.KryoSerializer;
import io.harness.serializer.recaster.RecastOrchestrationUtils;
import io.harness.steps.StepUtils;
import io.harness.steps.TaskRequestsUtils;
import io.harness.steps.approval.step.entities.ApprovalInstance;
import io.harness.steps.approval.step.entities.ApprovalInstance.ApprovalInstanceKeys;
import io.harness.steps.approval.step.entities.ApprovalInstanceService;
import io.harness.steps.approval.step.entities.JiraApprovalHelperService;
import io.harness.steps.approval.step.entities.JiraApprovalInstance;
import io.harness.steps.approval.step.entities.JiraApprovalInstance.JiraApprovalInstanceKeys;
import io.harness.steps.approval.step.jira.JiraApprovalSpecParameters;
import io.harness.steps.approval.step.jira.v1.JiraApprovalStepParameters;
import io.harness.steps.jira.JiraStepHelperService;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.waiter.NotifyCallback;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.beans.LogColor;
import software.wings.beans.LogHelper;
import software.wings.beans.LogWeight;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.ByteString;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_APPROVALS})
@OwnedBy(CDC)
@Slf4j
public class JiraApprovalHelperServiceImpl implements JiraApprovalHelperService {
  private final NgDelegate2TaskExecutor ngDelegate2TaskExecutor;
  private final ConnectorResourceClient connectorResourceClient;
  private final KryoSerializer referenceFalseKryoSerializer;
  private final SecretNGManagerClient secretManagerClient;
  private final WaitNotifyEngine waitNotifyEngine;
  private final LogStreamingStepClientFactory logStreamingStepClientFactory;
  private final String publisherName;
  private final PmsGitSyncHelper pmsGitSyncHelper;
  private final ApprovalInstanceService approvalInstanceService;
  private final RunnerTaskExecutor runnerTaskExecutor;
  private final OIDCContextHelper oidcContextHelper;
  private final PmsFeatureFlagHelper pmsFeatureFlagHelper;

  @Inject
  public JiraApprovalHelperServiceImpl(NgDelegate2TaskExecutor ngDelegate2TaskExecutor,
      ConnectorResourceClient connectorResourceClient,
      @Named("referenceFalseKryoSerializer") KryoSerializer referenceFalseKryoSerializer,
      @Named("PRIVILEGED") SecretNGManagerClient secretManagerClient, WaitNotifyEngine waitNotifyEngine,
      LogStreamingStepClientFactory logStreamingStepClientFactory,
      @Named(OrchestrationPublisherName.PUBLISHER_NAME) String publisherName, PmsGitSyncHelper pmsGitSyncHelper,
      JiraStepHelperService jiraStepHelperService, ApprovalInstanceService approvalInstanceService,
      RunnerTaskExecutor runnerTaskExecutor, OIDCContextHelper oidcContextHelper,
      PmsFeatureFlagHelper pmsFeatureFlagHelper) {
    this.ngDelegate2TaskExecutor = ngDelegate2TaskExecutor;
    this.connectorResourceClient = connectorResourceClient;
    this.referenceFalseKryoSerializer = referenceFalseKryoSerializer;
    this.secretManagerClient = secretManagerClient;
    this.waitNotifyEngine = waitNotifyEngine;
    this.logStreamingStepClientFactory = logStreamingStepClientFactory;
    this.publisherName = publisherName;
    this.pmsGitSyncHelper = pmsGitSyncHelper;
    this.approvalInstanceService = approvalInstanceService;
    this.runnerTaskExecutor = runnerTaskExecutor;
    this.oidcContextHelper = oidcContextHelper;
    this.pmsFeatureFlagHelper = pmsFeatureFlagHelper;
  }

  @Override
  public void handlePollingEvent(PersistenceIterator<ApprovalInstance> iterator, JiraApprovalInstance instance) {
    try (PmsGitSyncBranchContextGuard ignore1 =
             pmsGitSyncHelper.createGitSyncBranchContextGuard(instance.getAmbiance(), true);
         AutoLogContext ignore2 = instance.autoLogContext()) {
      if (pmsFeatureFlagHelper.isEnabled(instance.getAccountId(), PL_ENABLE_GRANULAR_CLAIMS_FOR_VAULT)) {
        Map<String, String> oidcParamMap = AmbianceUtils.extractOidcContextFields(instance.getAmbiance());
        oidcContextHelper.upsertOIDCContextInThreadLocal(oidcParamMap);
      }

      handlePollingEventInternal(iterator, instance);
    }
  }

  private void handlePollingEventInternal(
      PersistenceIterator<ApprovalInstance> iterator, JiraApprovalInstance instance) {
    Ambiance ambiance = instance.getAmbiance();
    NGLogCallback logCallback = new NGLogCallback(logStreamingStepClientFactory, ambiance, COMMAND_UNIT, false);

    try {
      log.info("Polling jira approval instance");
      logCallback.saveExecutionLog("-----");
      logCallback.saveExecutionLog(
          LogHelper.color("Fetching jira issue to check approval/rejection criteria", LogColor.White, LogWeight.Bold));

      String instanceId = instance.getId();
      String accountIdentifier = AmbianceUtils.getAccountId(ambiance);
      String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
      String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);
      String issueKey = instance.getIssueKey();
      String connectorRef = instance.getConnectorRef();
      log.info(String.format("Creating parameters for JiraApproval Instance with id : %s", instanceId));

      validateField(instanceId, ApprovalInstanceKeys.id);
      validateField(accountIdentifier, "accountIdentifier");
      validateField(orgIdentifier, "orgIdentifier");
      validateField(projectIdentifier, "projectIdentifier");

      String taskId;
      if (HarnessYamlVersion.isV1(AmbianceUtils.getPipelineVersion(ambiance))) {
        String jiraUrl = CDStepsEnvironmentVarsHelper.getEnvVar(
            instance.getRunStepInfoV1Outcome(), ConnectorVariableConstants.PLUGIN_JIRA_URL);
        if (EmptyPredicate.isEmpty(jiraUrl)) {
          logCallback.saveExecutionLog(String.format(
              "Skipping Jira URL logging. Environment variable [%s] not set as plugin input", JIRA_URL_PLUGIN_ENV));
        } else {
          logCallback.saveExecutionLog(String.format("Jira URL: %s", jiraUrl));
        }

        taskId = queueUnifiedTask(ambiance, instance);
      } else {
        validateField(issueKey, JiraApprovalInstanceKeys.issueKey);
        validateField(connectorRef, JiraApprovalInstanceKeys.connectorRef);

        // filterFields will be used to filter fields returned. Empty string will return all the fields
        // when filterFields is null then only name to key mapping for all fields possible for jira issue will be
        // fetched
        String filterFields = "";
        if (isNull(instance.getKeyListInKeyValueCriteria())) {
          // means first polling event
          filterFields = null;
        } else {
          // only fetch fields in keyListInKeyValueCriteria, if empty string then all fields will be fetched.
          filterFields = instance.getKeyListInKeyValueCriteria();
        }
        log.debug("Fetching fields list to filter in get issue call - {}", filterFields);

        JiraTaskNGParameters jiraTaskNGParameters = prepareJiraTaskParameters(accountIdentifier, orgIdentifier,
            projectIdentifier, issueKey, connectorRef, filterFields, instance.getDelegateSelectors());
        logCallback.saveExecutionLog(
            String.format("Jira url: %s", jiraTaskNGParameters.getJiraConnectorDTO().getJiraUrl()));

        log.debug("Queuing delegate task");
        taskId = queueTask(ambiance, instanceId, jiraTaskNGParameters, JIRA_DELEGATE_TASK_NAME,
            TaskSelectorYaml.toTaskSelector(instance.getDelegateSelectors()));
      }

      updateTaskId(instanceId, taskId, approvalInstanceService);

      log.info("Jira Approval Instance queued task with taskId - {}", taskId);
      logCallback.saveExecutionLog(String.format("Jira task: %s", taskId));
    } catch (Exception ex) {
      logCallback.saveExecutionLog(
          String.format("Error creating task for fetching jira issue: %s", ExceptionUtils.getMessage(ex)));
      log.warn("Error creating task for fetching jira issue while polling", ex);
      if (iterator != null && ParameterField.isNotNull(instance.getRetryInterval())) {
        resetNextIteration(iterator, instance);
      }
    }
  }

  private String queueUnifiedTask(Ambiance ambiance, JiraApprovalInstance instance) {
    RunStepInfoV1 runStep = RecastOrchestrationUtils.fromMap(instance.getRunStepInfoV1Outcome(), RunStepInfoV1.class);
    String taskId =
        runnerTaskExecutor.submitTask(runStep, ambiance, AmbianceUtils.getStepIdentifierFromAmbiance(ambiance), null);
    NotifyCallback callback = JiraApprovalCallback.builder().approvalInstanceId(instance.getId()).build();
    waitNotifyEngine.waitForAllOn(publisherName, callback, taskId);
    return taskId;
  }

  private JiraTaskNGParameters prepareJiraTaskParameters(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String issueId, String connectorRef, String filterFields,
      ParameterField<List<TaskSelectorYaml>> delegateSelectors) {
    JiraConnectorDTO jiraConnectorDTO =
        getJiraConnector(accountIdentifier, orgIdentifier, projectIdentifier, connectorRef);
    BaseNGAccess baseNGAccess = BaseNGAccess.builder()
                                    .accountIdentifier(accountIdentifier)
                                    .orgIdentifier(orgIdentifier)
                                    .projectIdentifier(projectIdentifier)
                                    .build();

    NGAccessWithEncryptionConsumer ngAccessWithEncryptionConsumer;
    if (!isNull(jiraConnectorDTO.getAuth()) && !isNull(jiraConnectorDTO.getAuth().getCredentials())) {
      ngAccessWithEncryptionConsumer = NGAccessWithEncryptionConsumer.builder()
                                           .ngAccess(baseNGAccess)
                                           .decryptableEntity(jiraConnectorDTO.getAuth().getCredentials())
                                           .build();
    } else {
      ngAccessWithEncryptionConsumer =
          NGAccessWithEncryptionConsumer.builder().ngAccess(baseNGAccess).decryptableEntity(jiraConnectorDTO).build();
    }

    List<EncryptedDataDetail> encryptionDataDetails = NGRestUtils.getResponse(
        secretManagerClient.getEncryptionDetails(accountIdentifier, ngAccessWithEncryptionConsumer));

    return JiraTaskNGParameters.builder()
        .action(JiraActionNG.GET_ISSUE)
        .encryptionDetails(encryptionDataDetails)
        .jiraConnectorDTO(jiraConnectorDTO)
        .issueKey(issueId)
        .filterFields(filterFields)
        .delegateSelectors(StepUtils.getDelegateSelectorListFromTaskSelectorYaml(delegateSelectors))
        .build();
  }

  private String queueTask(Ambiance ambiance, String approvalInstanceId, JiraTaskNGParameters jiraTaskNGParameters,
      String taskName, List<TaskSelector> selectors) {
    TaskRequest jiraTaskRequest = prepareJiraTaskRequest(ambiance, jiraTaskNGParameters, taskName, selectors);
    String taskId =
        ngDelegate2TaskExecutor.queueTask(ambiance.getSetupAbstractionsMap(), jiraTaskRequest, Duration.ofSeconds(0));
    NotifyCallback callback = JiraApprovalCallback.builder().approvalInstanceId(approvalInstanceId).build();
    waitNotifyEngine.waitForAllOn(publisherName, callback, taskId);
    return taskId;
  }

  private TaskRequest prepareJiraTaskRequest(
      Ambiance ambiance, JiraTaskNGParameters jiraTaskNGParameters, String taskName, List<TaskSelector> selectors) {
    TaskDetails taskDetails =
        TaskDetails.newBuilder()
            .setKryoParameters(
                ByteString.copyFrom(referenceFalseKryoSerializer.asDeflatedBytes(jiraTaskNGParameters) == null
                        ? new byte[] {}
                        : referenceFalseKryoSerializer.asDeflatedBytes(jiraTaskNGParameters)))
            .setExecutionTimeout(com.google.protobuf.Duration.newBuilder()
                                     .setSeconds(TimeUnit.MILLISECONDS.toSeconds(ASYNC_DELEGATE_TIMEOUT))
                                     .build())
            .setMode(TaskMode.ASYNC)
            .setParked(false)
            .setType(TaskType.newBuilder().setType(software.wings.beans.TaskType.JIRA_TASK_NG.name()).build())
            .build();

    return TaskRequestsUtils.prepareTaskRequest(ambiance, taskDetails, new ArrayList<>(), selectors, taskName, false);
  }

  @Override
  public JiraConnectorDTO getJiraConnector(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorIdentifierRef) {
    try {
      IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(
          connectorIdentifierRef, accountIdentifier, orgIdentifier, projectIdentifier);
      Optional<ConnectorDTO> connectorDTO = NGRestUtils.getResponse(
          connectorResourceClient.get(connectorRef.getIdentifier(), connectorRef.getAccountIdentifier(),
              connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier()));

      if (!connectorDTO.isPresent()) {
        throw new InvalidRequestException(
            String.format("Connector not found for identifier : [%s]", connectorIdentifierRef));
      }
      ConnectorInfoDTO connectorInfoDTO = connectorDTO.get().getConnectorInfo();
      ConnectorConfigDTO connectorConfigDTO = connectorInfoDTO.getConnectorConfig();
      if (connectorConfigDTO instanceof JiraConnectorDTO) {
        return (JiraConnectorDTO) connectorConfigDTO;
      }
      throw new HarnessJiraException(
          format("Connector of other then Jira type was found : [%s] ", connectorIdentifierRef));
    } catch (Exception e) {
      throw new HarnessJiraException(
          format("Error while getting connector information : [%s]", connectorIdentifierRef), e, null);
    }
  }

  @Override
  public JiraApprovalSpecParameters getJiraApprovalStepParameters(StepBaseParameters stepParameters) {
    String version = stepParameters.getSpec().getVersion();
    switch (version) {
      case HarnessYamlVersion.V0:
        return (JiraApprovalSpecParameters) stepParameters.getSpec();
      case HarnessYamlVersion.V1:
        return ((JiraApprovalStepParameters) stepParameters.getSpec()).toJiraApprovalStepParameterV0();
      default:
        throw new InvalidRequestException(String.format("Version %s not supported", version));
    }
  }

  private void validateField(String name, String value) {
    if (isBlank(value)) {
      throw new InvalidRequestException(format("Field %s can't be empty", name));
    }
  }
  private void resetNextIteration(PersistenceIterator<ApprovalInstance> iterator, JiraApprovalInstance instance) {
    approvalInstanceService.resetNextIterations(instance.getId(), instance.recalculateNextIterations());
    if (iterator != null) {
      iterator.wakeup();
    }
  }
}
