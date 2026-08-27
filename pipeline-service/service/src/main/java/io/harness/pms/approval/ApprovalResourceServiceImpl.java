/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.approval;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.delegate.task.shell.ShellScriptTaskNG.COMMAND_UNIT;
import static io.harness.remote.client.NGRestUtils.getResponse;
import static io.harness.security.dto.PrincipalType.SERVICE_ACCOUNT;
import static io.harness.security.dto.PrincipalType.USER;

import static java.util.Objects.isNull;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.EmbeddedServiceAccount;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.IdentifierRef;
import io.harness.beans.ScopeInfo;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.Scope;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.exception.InvalidRequestException;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.UserGroupFilterDTO;
import io.harness.ng.core.dto.UserGroupFilterDTO.UserGroupFilterDTOBuilder;
import io.harness.ng.core.user.UserInfo;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.helpers.CurrentUserHelper;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.remote.client.CGRestUtils;
import io.harness.security.dto.Principal;
import io.harness.security.dto.ServiceAccountPrincipal;
import io.harness.serviceaccount.ServiceAccountDTOInternal;
import io.harness.serviceaccount.remote.ServiceAccountClient;
import io.harness.steps.approval.step.ApprovalInstanceResponseMapper;
import io.harness.steps.approval.step.beans.ApprovalInstanceResponseDTO;
import io.harness.steps.approval.step.beans.ApprovalStatus;
import io.harness.steps.approval.step.beans.ApprovalType;
import io.harness.steps.approval.step.beans.HarnessApprovalInstanceDetailsDTO;
import io.harness.steps.approval.step.beans.PendingApprovalSummaryDTO;
import io.harness.steps.approval.step.custom.IrregularApprovalInstanceHandler;
import io.harness.steps.approval.step.entities.ApprovalInstance;
import io.harness.steps.approval.step.entities.ApprovalInstanceService;
import io.harness.steps.approval.step.entities.HarnessApprovalInstance;
import io.harness.steps.approval.step.harness.beans.ApproversDTO;
import io.harness.steps.approval.step.harness.beans.HarnessApprovalAction;
import io.harness.steps.approval.step.harness.beans.HarnessApprovalActivityRequestDTO;
import io.harness.steps.approval.step.harness.beans.HarnessApprovalInstanceAuthorizationDTO;
import io.harness.telemetry.helpers.ApprovalApiInstrumentationHelper;
import io.harness.user.remote.UserClient;
import io.harness.usergroups.UserGroupClient;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.ScopeResolutionHelper;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotEmpty;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_APPROVALS})
@OwnedBy(CDC)
@Slf4j
public class ApprovalResourceServiceImpl implements ApprovalResourceService {
  private static final int APPROVAL_REFRESH_RATE_LIMIT_MS = 60000; // 60 seconds rate limit

  private final ApprovalInstanceService approvalInstanceService;
  private final ApprovalInstanceResponseMapper approvalInstanceResponseMapper;
  private final PlanExecutionService planExecutionService;
  private final UserGroupClient userGroupClient;
  private final CurrentUserHelper currentUserHelper;
  private final LogStreamingStepClientFactory logStreamingStepClientFactory;
  private final UserClient userClient;
  private final ApprovalApiInstrumentationHelper instrumentationHelper;
  private final NodeExecutionService nodeExecutionService;
  private final ServiceAccountClient serviceAccountClient;
  private final IrregularApprovalInstanceHandler irregularApprovalInstanceHandler;
  private final ScopeResolutionHelper scopeResolutionHelper;

  @Inject
  public ApprovalResourceServiceImpl(ApprovalInstanceService approvalInstanceService,
      ApprovalInstanceResponseMapper approvalInstanceResponseMapper, PlanExecutionService planExecutionService,
      @Named("PRIVILEGED") UserGroupClient userGroupClient, CurrentUserHelper currentUserHelper, UserClient userClient,
      LogStreamingStepClientFactory logStreamingStepClientFactory,
      ApprovalApiInstrumentationHelper instrumentationHelper, NodeExecutionService nodeExecutionService,
      ServiceAccountClient serviceAccountClient, IrregularApprovalInstanceHandler irregularApprovalInstanceHandler,
      ScopeResolutionHelper scopeResolutionHelper) {
    this.approvalInstanceService = approvalInstanceService;
    this.approvalInstanceResponseMapper = approvalInstanceResponseMapper;
    this.planExecutionService = planExecutionService;
    this.userGroupClient = userGroupClient;
    this.currentUserHelper = currentUserHelper;
    this.userClient = userClient;
    this.logStreamingStepClientFactory = logStreamingStepClientFactory;
    this.instrumentationHelper = instrumentationHelper;
    this.nodeExecutionService = nodeExecutionService;
    this.serviceAccountClient = serviceAccountClient;
    this.irregularApprovalInstanceHandler = irregularApprovalInstanceHandler;
    this.scopeResolutionHelper = scopeResolutionHelper;
  }

  @Override
  public ApprovalInstanceResponseDTO get(String approvalInstanceId, String accountId) {
    return get(approvalInstanceId, accountId, false);
  }

  @Override
  public ApprovalInstanceResponseDTO get(String approvalInstanceId, String accountId, boolean refresh) {
    ApprovalInstance approvalInstance =
        approvalInstanceService.fetchFromObjectStoreWithFallback(accountId, approvalInstanceId);
    if (!isNull(accountId) && !accountId.equals(approvalInstance.getAccountId())) {
      throw new InvalidRequestException(
          String.format("Account Identifier provided %s doesn't match with approval instance's account identifier: %s",
              accountId, approvalInstance.getAccountId()));
    }

    if (refresh) {
      triggerAsyncApprovalRefresh(approvalInstance);
    }

    ApprovalInstanceResponseDTO response =
        approvalInstanceResponseMapper.toApprovalInstanceResponseDTO(approvalInstance, true);
    populateCurrentUserActions(response);
    return response;
  }

  public void triggerAsyncApprovalRefresh(ApprovalInstance approvalInstance) {
    NGLogCallback logCallback =
        new NGLogCallback(logStreamingStepClientFactory, approvalInstance.getAmbiance(), COMMAND_UNIT, false);

    // Check if a manual refresh was triggered within the rate limit period
    long currentTime = System.currentTimeMillis();
    Long lastManualRunTimestamp = null;

    // Check if this approval type supports manual refresh
    if (!approvalInstance.isManualRefreshSupported()) {
      log.info("Skipping refresh for approval instance: {} since type {} is not supported for manual refresh",
          approvalInstance.getId(), approvalInstance.getType());
      logCallback.saveExecutionLog(
          String.format("Manual refresh not supported for approval type: %s", approvalInstance.getType()));
      return;
    }

    // Get lastManualRunTimestamp for supported approval type
    lastManualRunTimestamp = approvalInstance.getLastManualRunTimestamp();

    // Rate limiting - only allow refresh if no previous refresh within rate limit period
    if (lastManualRunTimestamp != null && (currentTime - lastManualRunTimestamp) < APPROVAL_REFRESH_RATE_LIMIT_MS) {
      logCallback.saveExecutionLog(String.format("Manual refresh skipped - rate limited (last refresh was %d ms ago)",
          (currentTime - lastManualRunTimestamp)));
      log.info("Skipping refresh for approval instance: {} due to rate limiting (last refresh: {})",
          approvalInstance.getId(), lastManualRunTimestamp);
      return;
    }

    logCallback.saveExecutionLog("Manual status refresh triggered successfully.");
    log.info("Triggering async refresh for approval instance: {}", approvalInstance.getId());

    // Manual refresh implementation:
    // 1. Record the current time as the last manual run timestamp for rate limiting
    // 2. Fetch the latest instance from the database
    // 3. Reset the next poll iterations via the instance's polymorphic setNextIterations method
    // 4. Calculate a new sequence of polling timestamps via polymorphic recalculateNextIterations
    // 5. Persist the updated polling schedule and wake up the handler to process immediately
    approvalInstanceService.updateLastManualRunTimestamp(approvalInstance.getId(), currentTime);

    ApprovalInstance updatedInstance = approvalInstanceService.get(approvalInstance.getId());
    updatedInstance.setNextIterations(null);
    List<Long> nextIterations = updatedInstance.recalculateNextIterations();
    approvalInstanceService.resetNextIterations(approvalInstance.getId(), nextIterations);

    irregularApprovalInstanceHandler.wakeup();
  }

  @Override
  public ApprovalInstanceResponseDTO addHarnessApprovalActivity(
      @NotNull String approvalInstanceId, @NotNull @Valid HarnessApprovalActivityRequestDTO request) {
    HarnessApprovalInstanceAuthorizationDTO harnessApprovalInstanceAuthorizationDTO =
        getHarnessApprovalInstanceAuthorization(approvalInstanceId, false);
    if (!harnessApprovalInstanceAuthorizationDTO.isAuthorized()) {
      throw new InvalidRequestException(harnessApprovalInstanceAuthorizationDTO.getReason());
    }

    HarnessApprovalInstance instance =
        approvalInstanceService.addHarnessApprovalActivity(approvalInstanceId, getEmbeddedUser(), request);
    if (request.getAction() == HarnessApprovalAction.APPROVE) {
      rejectPreviousExecutions(instance);
    }
    approvalInstanceService.closeHarnessApprovalStep(instance);
    return approvalInstanceResponseMapper.toApprovalInstanceResponseDTO(instance, true);
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  public ApprovalInstanceResponseDTO addHarnessApprovalActivityByPlanExecutionId(
      @NotNull @AccountIdentifier String accountId, @NotNull @OrgIdentifier String orgIdentifier,
      @NotNull @ProjectIdentifier String projectIdentifier, @NotNull String planExecutionId,
      @NotNull @Valid HarnessApprovalActivityRequestDTO request, String callbackId) {
    List<ApprovalInstance> approvalInstances = approvalInstanceService.getApprovalInstancesByExecutionId(
        planExecutionId, ApprovalStatus.WAITING, ApprovalType.HARNESS_APPROVAL, null, callbackId);
    if (EmptyPredicate.isEmpty(approvalInstances)) {
      instrumentationHelper.sendApprovalApiEvent(accountId, orgIdentifier, projectIdentifier, planExecutionId,
          ApprovalApiInstrumentationHelper.FAILURE, ApprovalApiInstrumentationHelper.NO_APPROVALS_FOUND);
      throw new InvalidRequestException(
          String.format("Found no Harness Approval Instance waiting for pipeline execution id: %s and callback id: %s",
              planExecutionId, callbackId));
    }
    ApprovalInstance approvalInstance = approvalInstances.get(0);
    if (approvalInstances.size() > 1) {
      instrumentationHelper.sendApprovalApiEvent(accountId, orgIdentifier, projectIdentifier, planExecutionId,
          ApprovalApiInstrumentationHelper.FAILURE, ApprovalApiInstrumentationHelper.MULTIPLE_APPROVALS_FOUND);
      throw new InvalidRequestException(String.format(
          "Found more than 1 Harness Approval Instance waiting for pipeline execution id: %s and callback id: %s",
          planExecutionId, callbackId));
    }
    NGLogCallback logCallback =
        new NGLogCallback(logStreamingStepClientFactory, approvalInstance.getAmbiance(), COMMAND_UNIT, false);
    logCallback.saveExecutionLog(
        String.format("Approval request from Approval API with pipeline execution id: %s and approval instance id: %s",
            planExecutionId, approvalInstance.getId()));
    return addHarnessApprovalActivity(approvalInstance.getId(), request);
  }

  public void rejectPreviousExecutions(HarnessApprovalInstance instance) {
    if (instance.getIsAutoRejectEnabled() == null || !instance.getIsAutoRejectEnabled()) {
      return;
    }
    Ambiance ambiance = instance.getAmbiance();
    String accountId = instance.getAccountId();
    // orgId and projectId are not used in findAllPreviousWaitingApprovals,
    // these can be safely removed
    String orgId = instance.getOrgIdentifier();
    String projectId = instance.getProjectIdentifier();
    String pipelineId = instance.getPipelineIdentifier();
    String approvalKey = instance.getApprovalKey();
    Long createdAt = instance.getCreatedAt();
    List<String> rejectedApprovalIds = approvalInstanceService.findAllPreviousWaitingApprovals(
        accountId, orgId, projectId, pipelineId, approvalKey, ambiance, createdAt);
    final long[] cnt = {0};
    rejectedApprovalIds.forEach(id -> {
      boolean unauthorized = !getHarnessApprovalInstanceAuthorization(id, true).isAuthorized();
      if (!unauthorized) {
        cnt[0]++;
      }
      approvalInstanceService.rejectPreviousExecutions(id, getEmbeddedUser(), unauthorized, ambiance);
    });
    NGLogCallback logCallback = new NGLogCallback(logStreamingStepClientFactory, ambiance, COMMAND_UNIT, false);
    logCallback.saveExecutionLog(String.format("Successfully rejected %s previous executions waiting for approval on "
            + "this step that the user was authorized to reject",
        cnt[0]));
  }

  private void populateCurrentUserActions(ApprovalInstanceResponseDTO response) {
    try {
      if (response == null || !(response.getDetails() instanceof HarnessApprovalInstanceDetailsDTO harnessDetails)) {
        return;
      }
      EmbeddedUser currentUser = getEmbeddedUser();
      response.setApprovedByCurrentUser(
          hasActionByCurrentUser(harnessDetails, currentUser, HarnessApprovalAction.APPROVE));
      response.setRejectedByCurrentUser(
          hasActionByCurrentUser(harnessDetails, currentUser, HarnessApprovalAction.REJECT));
    } catch (Exception ex) {
      log.warn("Failed to populate approvedByCurrentUser/rejectedByCurrentUser for approval instance response", ex);
    }
  }

  private boolean hasActionByCurrentUser(
      HarnessApprovalInstanceDetailsDTO harnessDetails, EmbeddedUser currentUser, HarnessApprovalAction action) {
    if (EmptyPredicate.isEmpty(harnessDetails.getApprovalActivities()) || currentUser == null
        || EmptyPredicate.isEmpty(currentUser.getUuid())) {
      return false;
    }
    return harnessDetails.getApprovalActivities().stream().anyMatch(activity
        -> activity.getAction() == action && activity.getUser() != null
            && Objects.equals(activity.getUser().getUuid(), currentUser.getUuid()));
  }

  private EmbeddedUser getEmbeddedUser() {
    Principal principal = currentUserHelper.getPrincipalFromSecurityContext();
    if (!(USER.equals(principal.getType()) || SERVICE_ACCOUNT.equals(principal.getType()))) {
      // TODO: handle api key and service account approvals
      throw new InvalidRequestException(principal.getType() + " is not supported for Harness Approval Step yet");
    }

    if (USER.equals(principal.getType())) {
      String userId = principal.getName();
      Optional<UserInfo> userOptional = CGRestUtils.getResponse(userClient.getUserById(userId));
      if (!userOptional.isPresent()) {
        throw new InvalidRequestException(String.format("Invalid user: %s", userId));
      }
      UserInfo user = userOptional.get();
      return EmbeddedUser.builder().uuid(user.getUuid()).name(user.getName()).email(user.getEmail()).build();
    } else if (SERVICE_ACCOUNT.equals(principal.getType())) {
      ServiceAccountPrincipal serviceAccountPrincipal = (ServiceAccountPrincipal) principal;
      String uniqueId = serviceAccountPrincipal.getUniqueId();
      ServiceAccountDTOInternal serviceAccount = null;
      if (EmptyPredicate.isNotEmpty(uniqueId)) {
        List<ServiceAccountDTOInternal> serviceAccounts =
            getResponse(serviceAccountClient.listServiceAccountsByUniqueIdInternal(
                serviceAccountPrincipal.getAccountId(), Arrays.asList(uniqueId)));
        serviceAccount = serviceAccounts.get(0);
      }
      if (serviceAccount == null) {
        throw new InvalidRequestException(
            String.format("Service account [%s] does not exist.", serviceAccountPrincipal.getName()));
      }
      return EmbeddedServiceAccount.builder()
          .uuid(serviceAccount.getUniqueIdInternal())
          .name(serviceAccount.getName())
          .email(serviceAccount.getEmail())
          .accountIdentifier(serviceAccount.getAccountIdentifier())
          .orgIdentifier(serviceAccount.getOrgIdentifier())
          .projectIdentifier(serviceAccount.getProjectIdentifier())
          .serviceAccountIdentifier(serviceAccount.getIdentifier())
          .build();
    }
    return EmbeddedUser.builder().build();
  }

  @Override
  public List<ApprovalInstanceResponseDTO> getApprovalInstancesByExecutionId(@NotEmpty String planExecutionId,
      @Valid ApprovalStatus approvalStatus, @Valid ApprovalType approvalType, String nodeExecutionId, String callbackId,
      boolean isRetry) {
    List<ApprovalInstance> approvalInstances;

    if (isRetry) {
      List<String> approvalInstanceIds =
          nodeExecutionService.fetchListOfApprovalInstanceIdsForPlanExecutionId(planExecutionId);

      approvalInstances = approvalInstanceService.getApprovalInstancesByApprovalInstanceIds(
          planExecutionId, approvalStatus, approvalType, nodeExecutionId, callbackId, approvalInstanceIds);
    } else {
      approvalInstances = approvalInstanceService.getApprovalInstancesByExecutionId(
          planExecutionId, approvalStatus, approvalType, nodeExecutionId, callbackId);
    }

    return approvalInstances.stream()
        .map(approvalInstance -> approvalInstanceResponseMapper.toApprovalInstanceResponseDTO(approvalInstance, false))
        .collect(Collectors.toList());
  }

  @Override
  public HarnessApprovalInstanceAuthorizationDTO getHarnessApprovalInstanceAuthorization(
      @NotNull String approvalInstanceId, boolean skipHasAlreadyApprovedValidation) {
    EmbeddedUser user = getEmbeddedUser();
    HarnessApprovalInstance instance = approvalInstanceService.getHarnessApprovalInstance(approvalInstanceId);
    return checkApprovalAuthorization(instance, user, skipHasAlreadyApprovedValidation);
  }

  private HarnessApprovalInstanceAuthorizationDTO checkApprovalAuthorization(
      HarnessApprovalInstance instance, EmbeddedUser user, boolean skipHasAlreadyApprovedValidation) {
    // Check if the user has already approved/rejected.
    if (alreadyHasApprovalActivity(instance, user) && !skipHasAlreadyApprovedValidation) {
      return HarnessApprovalInstanceAuthorizationDTO.builder()
          .authorized(false)
          .reason("You have already approved/rejected the pipeline")
          .build();
    }

    // Check if the user is the pipeline executor.
    if (instance.getApprovers().isDisallowPipelineExecutor()) {
      ExecutionMetadata metadata =
          planExecutionService.getExecutionMetadataFromPlanExecution(instance.getAmbiance().getPlanExecutionId());
      if (metadata != null && metadata.hasTriggerInfo() && metadata.getTriggerInfo().hasTriggeredBy()
          && metadata.getTriggerInfo().getTriggeredBy().getUuid().equals(user.getUuid())) {
        return HarnessApprovalInstanceAuthorizationDTO.builder()
            .authorized(false)
            .reason("Pipeline executor is not allowed to approve/reject")
            .build();
      }
    }

    // Check if user is in disallowed user list
    if (isUserDisallowedFromApproving(instance, user)) {
      return HarnessApprovalInstanceAuthorizationDTO.builder()
          .authorized(false)
          .reason(String.format("Action not permitted: You [%s] are on the disallowed user list and do not have "
                  + "permission to approve or reject.",
              user.getEmail()))
          .build();
    }

    // Check if the user is member of any user groups given in step parameters. If there are no user groups configures,
    // we do not allow approval/rejection.
    if (user instanceof EmbeddedServiceAccount) {
      if (!isServiceAccountAuthorized(instance, (EmbeddedServiceAccount) user)) {
        return HarnessApprovalInstanceAuthorizationDTO.builder()
            .authorized(false)
            .reason("The service account configured in the request is not authorized to approve/reject")
            .build();
      }
    } else {
      if (!isMemberOfUserGroups(instance, user)) {
        return HarnessApprovalInstanceAuthorizationDTO.builder()
            .authorized(false)
            .reason("User not authorized to approve/reject")
            .build();
      }
    }
    return HarnessApprovalInstanceAuthorizationDTO.builder().authorized(true).build();
  }

  protected boolean isUserDisallowedFromApproving(HarnessApprovalInstance instance, EmbeddedUser user) {
    List<String> disallowedEmails = Optional.ofNullable(instance)
                                        .map(HarnessApprovalInstance::getApprovers)
                                        .map(ApproversDTO::getDisallowedUserEmails)
                                        .orElse(Collections.emptyList());

    return disallowedEmails.stream().anyMatch(email -> email.equalsIgnoreCase(user.getEmail()));
  }

  private boolean isServiceAccountAuthorized(
      HarnessApprovalInstance instance, EmbeddedServiceAccount embeddedServiceAccount) {
    List<String> serviceAccounts = instance.getApprovers().getServiceAccounts();

    if (EmptyPredicate.isEmpty(serviceAccounts)) {
      return false;
    }
    // use parentUniqueId to resolve the current scope (handles pipelines moved across orgs/projects)
    ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(instance.getAccountId(), instance.getParentUniqueId());
    String orgIdentifierFromInstance = scopeInfo.getOrgIdentifier();
    String projectIdentifierFromInstance = scopeInfo.getProjectIdentifier();

    String accountId = embeddedServiceAccount.getAccountIdentifier();
    String orgIdentifier = embeddedServiceAccount.getOrgIdentifier();
    String projectIdentifier = embeddedServiceAccount.getProjectIdentifier();
    String serviceAccountIdentifier = embeddedServiceAccount.getServiceAccountIdentifier();

    String scopedServiceAccountRef =
        getScopedServiceAccountRef(accountId, orgIdentifier, projectIdentifier, serviceAccountIdentifier);

    for (String serviceAccount : serviceAccounts) {
      if (serviceAccount.equals(scopedServiceAccountRef)) {
        if (scopedServiceAccountRef.startsWith(NGCommonEntityConstants.ACCOUNT_SCOPE_PREFIX)) {
          return true;
        } else if (scopedServiceAccountRef.startsWith(NGCommonEntityConstants.ORG_SCOPE_PREFIX)
            && orgIdentifierFromInstance.equals(orgIdentifier)) {
          return true;
        } else if (orgIdentifierFromInstance.equals(orgIdentifier)
            && projectIdentifierFromInstance.equals(projectIdentifier)) {
          return true;
        } else {
          return false;
        }
      }
    }
    return false;
  }

  private String getScopedServiceAccountRef(
      String accountId, String orgIdentifier, String projectIdentifier, String serviceAccountIdentifier) {
    if (EmptyPredicate.isNotEmpty(accountId) && EmptyPredicate.isNotEmpty(orgIdentifier)
        && EmptyPredicate.isNotEmpty(projectIdentifier)) {
      return serviceAccountIdentifier;
    } else if (EmptyPredicate.isNotEmpty(accountId) && EmptyPredicate.isNotEmpty(orgIdentifier)) {
      return NGCommonEntityConstants.ORG_SCOPE_PREFIX + serviceAccountIdentifier;
    } else if (EmptyPredicate.isNotEmpty(accountId)) {
      return NGCommonEntityConstants.ACCOUNT_SCOPE_PREFIX + serviceAccountIdentifier;
    } else {
      throw new InvalidRequestException(
          "AccountId cannot be null for the service account [" + serviceAccountIdentifier + "]");
    }
  }

  @Override
  public String getYamlSnippet(ApprovalType approvalType, String accountId) throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    String yamlFile = approvalType.getDisplayName();

    return Resources.toString(
        Objects.requireNonNull(classLoader.getResource(String.format("approval_stage_yamls/%s.yaml", yamlFile))),
        StandardCharsets.UTF_8);
  }

  @Override
  public PageResponse<PendingApprovalSummaryDTO> listPendingApprovals(@NotNull String accountId, int size) {
    if (size <= 0) {
      throw new InvalidRequestException("Size must be greater than 0");
    }
    size = Math.min(size, 100);

    List<ApprovalInstance> waitingApprovals =
        approvalInstanceService.getWaitingApprovalsByAccountAndType(accountId, ApprovalType.HARNESS_APPROVAL, size);

    EmbeddedUser user = getEmbeddedUser();
    List<PendingApprovalSummaryDTO> approvals =
        waitingApprovals.stream()
            .map(instance -> (HarnessApprovalInstance) instance)
            .filter(instance -> checkApprovalAuthorization(instance, user, false).isAuthorized())
            .map(this::toPendingApprovalSummaryDTO)
            .limit(size)
            .collect(Collectors.toList());

    return PageResponse.<PendingApprovalSummaryDTO>builder()
        .totalItems(approvals.size())
        .pageItemCount(approvals.size())
        .pageSize(size)
        .content(approvals)
        .pageIndex(0)
        .empty(approvals.isEmpty())
        .build();
  }

  private PendingApprovalSummaryDTO toPendingApprovalSummaryDTO(HarnessApprovalInstance instance) {
    return PendingApprovalSummaryDTO.builder()
        .id(instance.getId())
        .type(instance.getType())
        .status(instance.getStatus())
        .deadline(instance.getDeadline())
        .accountIdentifier(instance.getAccountId())
        .orgIdentifier(instance.getOrgIdentifier())
        .projectIdentifier(instance.getProjectIdentifier())
        .pipelineIdentifier(instance.getPipelineIdentifier())
        .planExecutionId(instance.getPlanExecutionId())
        .approvalMessage(instance.getApprovalMessage())
        .createdAt(instance.getCreatedAt())
        .lastModifiedAt(instance.getLastModifiedAt())
        .errorMessage(instance.getErrorMessage())
        .build();
  }

  private boolean alreadyHasApprovalActivity(HarnessApprovalInstance instance, EmbeddedUser user) {
    if (EmptyPredicate.isEmpty(instance.getApprovalActivities())) {
      return false;
    }
    return instance.getApprovalActivities().stream().anyMatch(aa -> aa.getUser().getUuid().equals(user.getUuid()));
  }

  private boolean isMemberOfUserGroups(HarnessApprovalInstance instance, EmbeddedUser user) {
    List<String> userGroups = instance.getApprovers().getUserGroups();
    if (EmptyPredicate.isEmpty(userGroups)) {
      return false;
    }

    Ambiance ambiance = instance.getAmbiance();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectId = AmbianceUtils.getProjectIdentifier(ambiance);
    Map<Scope, List<IdentifierRef>> identifierRefs =
        new HashSet<>(userGroups)
            .stream()
            .map(ug -> IdentifierRefHelper.getIdentifierRef(ug, accountId, orgId, projectId))
            .collect(Collectors.groupingBy(IdentifierRef::getScope));

    List<UserGroupFilterDTO> userGroupFilters = ImmutableList.of(Scope.ACCOUNT, Scope.ORG, Scope.PROJECT)
                                                    .stream()
                                                    // Find user groups corresponding to each scope.
                                                    .map(identifierRefs::get)
                                                    // Create a user group filter for each scope.
                                                    .map(l -> prepareUserGroupFilter(l, user))
                                                    // Remove any scope that doesn't have any user group.
                                                    .filter(Optional::isPresent)
                                                    .map(Optional::get)
                                                    .collect(Collectors.toList());
    if (EmptyPredicate.isEmpty(userGroupFilters)) {
      return false;
    }

    for (UserGroupFilterDTO userGroupFilter : userGroupFilters) {
      if (EmptyPredicate.isNotEmpty(getResponse(userGroupClient.getFilteredUserGroups(userGroupFilter)))) {
        return true;
      }
    }
    return false;
  }

  private Optional<UserGroupFilterDTO> prepareUserGroupFilter(List<IdentifierRef> identifierRefs, EmbeddedUser user) {
    if (EmptyPredicate.isEmpty(identifierRefs)) {
      return Optional.empty();
    }

    IdentifierRef identifierRef = identifierRefs.get(0);
    UserGroupFilterDTOBuilder builder =
        UserGroupFilterDTO.builder()
            .identifierFilter(identifierRefs.stream().map(IdentifierRef::getIdentifier).collect(Collectors.toSet()))
            .userIdentifierFilter(Collections.singleton(user.getUuid()));
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
}
