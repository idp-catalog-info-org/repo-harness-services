/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ldap.service.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.NgSetupFields.NG;
import static io.harness.delegate.beans.NgSetupFields.OWNER;
import static io.harness.exception.WingsException.USER;
import static io.harness.ldap.service.impl.NGLdapServiceImpl.ISSUE_WITH_LDAP_TEST_AUTHENTICATION;
import static io.harness.security.encryption.EncryptionType.LOCAL;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import static software.wings.beans.TaskType.NG_LDAP_GROUPS_SYNC;
import static software.wings.beans.TaskType.NG_LDAP_SEARCH_GROUPS;
import static software.wings.beans.TaskType.NG_LDAP_TEST_AUTHENTICATION;
import static software.wings.beans.TaskType.NG_LDAP_TEST_CONN_SETTINGS;
import static software.wings.beans.TaskType.NG_LDAP_TEST_GROUP_SETTINGS;
import static software.wings.beans.TaskType.NG_LDAP_TEST_USER_SETTINGS;
import static software.wings.beans.sso.LdapTestResponse.Status.FAILURE;
import static software.wings.helpers.ext.ldap.LdapConstants.NG_LDAP_LOGIN_TEST_PASSWORD;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.ScopeInfo;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.ldap.NGLdapDelegateTaskParameters;
import io.harness.delegate.beans.ldap.NGLdapDelegateTaskResponse;
import io.harness.delegate.beans.ldap.NGLdapGroupSearchTaskParameters;
import io.harness.delegate.beans.ldap.NGLdapGroupSearchTaskResponse;
import io.harness.delegate.beans.ldap.NGLdapGroupSyncTaskResponse;
import io.harness.delegate.beans.ldap.NGLdapTestAuthenticationTaskParameters;
import io.harness.delegate.beans.ldap.NGLdapTestAuthenticationTaskResponse;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.utils.TaskSetupAbstractionHelper;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.exception.DelegateNotAvailableException;
import io.harness.exception.DelegateServiceDriverException;
import io.harness.exception.ExplanationException;
import io.harness.exception.GeneralException;
import io.harness.exception.HintException;
import io.harness.exception.InvalidEntityException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.NoResultFoundException;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.exceptionhandler.DocumentLinksConstants;
import io.harness.iterator.PersistentCronIterable;
import io.harness.ldap.dto.LDAPCreateAuditEvent;
import io.harness.ldap.dto.LDAPDeleteAuditEvent;
import io.harness.ldap.dto.NGLdapSettingsWithEncryptedDataDetailsDTO;
import io.harness.ldap.dto.NGLoginSettingsAbstractLDAPConfigurationEvent.NGLoginSettingsAbstractLDAPConfigurationEventBuilder;
import io.harness.ldap.dto.NgLdapSettingsYamlDTO;
import io.harness.ldap.entity.NGLdapSettings;
import io.harness.ldap.scheduler.NGLdapGroupSyncHelper;
import io.harness.ldap.service.NGLdapSettingsService;
import io.harness.ldap.service.impl.errors.LdapErrorHandler;
import io.harness.mappers.SecretManagerConfigMapper;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.user.entities.UserGroup;
import io.harness.outbox.api.OutboxService;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.repositories.LdapSettingsRepository;
import io.harness.repositories.SSOSettingsRepository;
import io.harness.secretmanagerclient.dto.config.LocalConfigDTO;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.SimpleEncryption;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionConfig;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.sso.entity.SSOSettings.NgSsoSettingsKeys;

import software.wings.beans.dto.LdapSettings;
import software.wings.beans.sso.LdapGroupResponse;
import software.wings.beans.sso.LdapTestResponse;
import software.wings.beans.sso.SSOType;
import software.wings.helpers.ext.ldap.LdapConstants;
import software.wings.helpers.ext.ldap.LdapResponse;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import org.ldaptive.ResultCode;
import org.springframework.transaction.support.TransactionTemplate;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.PL)
public class NGLdapSettingsServiceImpl implements NGLdapSettingsService {
  private final LdapSettingsRepository ldapSettingsRepository;
  private final SSOSettingsRepository ssoSettingsRepository;
  private final OutboxService outboxService;
  private final TransactionTemplate transactionTemplate;
  private final UserGroupService userGroupService;
  private final ScopeInfoService scopeInfoService;
  private static final long MIN_INTERVAL = 900;
  private final NGLdapGroupSyncHelper ngLdapGroupSyncHelper;
  private final DelegateGrpcClientWrapper delegateService;
  private final SecretManagerClientService secretManagerClientService;
  private final TaskSetupAbstractionHelper taskSetupAbstractionHelper;
  public static final String ISSUE_WITH_LDAP_CONNECTION = "Issue with Ldap Connection";
  public static final String ISSUE_WITH_USER_QUERY_SETTINGS_PROVIDED = "Issue with User Query Settings provided";
  public static final String ISSUE_WITH_GROUP_QUERY_SETTINGS_PROVIDED = "Issue with Group Query Settings provided";
  public static final int LDAP_TASK_DEFAULT_MINIMUM_TIMEOUT_MILLIS = 60000; // 60 seconds
  public static final int LDAP_TASK_DEFAULT_MAXIMUM_TIMEOUT_MILLIS = 180000; // 180 seconds

  @Inject
  public NGLdapSettingsServiceImpl(LdapSettingsRepository ldapSettingsRepository,
      SSOSettingsRepository ssoSettingsRepository, OutboxService outboxService, TransactionTemplate transactionTemplate,
      UserGroupService userGroupService, ScopeInfoService scopeInfoService, NGLdapGroupSyncHelper ngLdapGroupSyncHelper,
      DelegateGrpcClientWrapper delegateService,
      @Named("PRIVILEGED") SecretManagerClientService secretManagerClientService,
      TaskSetupAbstractionHelper taskSetupAbstractionHelper) {
    this.ldapSettingsRepository = ldapSettingsRepository;
    this.ssoSettingsRepository = ssoSettingsRepository;
    this.outboxService = outboxService;
    this.transactionTemplate = transactionTemplate;
    this.userGroupService = userGroupService;
    this.scopeInfoService = scopeInfoService;
    this.ngLdapGroupSyncHelper = ngLdapGroupSyncHelper;
    this.delegateService = delegateService;
    this.secretManagerClientService = secretManagerClientService;
    this.taskSetupAbstractionHelper = taskSetupAbstractionHelper;
  }

  @Override
  public NGLdapSettings create(NGLdapSettings ngLdapSettings) {
    NGLdapSettings ngLdapSettingsExisting =
        ldapSettingsRepository.findByAccountIdentifierAndType(ngLdapSettings.getAccountIdentifier(), SSOType.LDAP);
    if (ngLdapSettingsExisting != null) {
      throw new InvalidRequestException("Ldap settings already exist for this account.");
    }
    updateNextIterations(ngLdapSettings);
    return Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
      NGLdapSettings ngldapSettings = ldapSettingsRepository.save(ngLdapSettings);
      //@TODO: UserGroup Sync
      logAuditEvent(ngLdapSettings, null, ngLdapSettings.getAccountIdentifier());
      return ngldapSettings;
    }));
  }

  @Override
  public NGLdapSettings get(String accountId) {
    NGLdapSettings ngLdapSettings = ldapSettingsRepository.findByAccountIdentifierAndType(accountId, SSOType.LDAP);
    if (ngLdapSettings == null) {
      String message = String.format("LDAP Settings not found for account [%s].", accountId);
      throw NoResultFoundException.newBuilder()
          .code(ErrorCode.RESOURCE_NOT_FOUND)
          .message(message)
          .level(Level.ERROR)
          .reportTargets(USER)
          .build();
    }
    return ngLdapSettings;
  }

  @Override
  public NGLdapSettings update(NGLdapSettings ngLdapSettingsToUpdate, String accountIdentifier) throws Exception {
    NGLdapSettings ngldapSettings = get(accountIdentifier);
    ngldapSettings.setUrl(ngLdapSettingsToUpdate.getUrl());
    ngldapSettings.setDisplayName(ngLdapSettingsToUpdate.getDisplayName());
    ngldapSettings.setName(ngLdapSettingsToUpdate.getName());
    ngldapSettings.setDisabled(ngLdapSettingsToUpdate.isDisabled());
    ngldapSettings.setCronExpression(ngLdapSettingsToUpdate.getCronExpression());
    ngldapSettings.setUserSettingsList(ngLdapSettingsToUpdate.getUserSettingsList());
    ngldapSettings.setGroupSettingsList(ngLdapSettingsToUpdate.getGroupSettingsList());
    ngldapSettings.setConnectionSettings(ngLdapSettingsToUpdate.getConnectionSettings());

    updateNextIterations(ngldapSettings);
    return Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
      NGLdapSettings ngLdapSettingsUpdated = ldapSettingsRepository.save(ngldapSettings);
      //@TODO: UserGroup Sync
      logAuditEvent(ngLdapSettingsUpdated, ngldapSettings, accountIdentifier);
      return ngLdapSettingsUpdated;
    }));
  }

  @Override
  public boolean delete(String accountId) {
    NGLdapSettings ngLdapSettings = get(accountId);
    checkForLinkedSSOGroups(accountId, ngLdapSettings.getIdentifier());
    return Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
      ssoSettingsRepository.deleteByAccountIdentifierAndTypeAndIdentifier(
          accountId, SSOType.LDAP, ngLdapSettings.getIdentifier());
      outboxService.save(
          LDAPDeleteAuditEvent.builder()
              .accountIdentifier(accountId)
              .oldNgLdapSettingsYamlDTO(NgLdapSettingsYamlDTO.builder().ngLdapSettings(ngLdapSettings).build())
              .build());
      return true;
    }));
  }

  @Override
  public LdapTestResponse validateLdapConnectionSettings(String accountIdentifier, NGLdapSettings ldapSettings) {
    log.info("Validate ldap connection for account {}: with settings id {}", accountIdentifier, ldapSettings.getUuid());
    NGLdapDelegateTaskParameters taskParameters = NGLdapDelegateTaskParameters.builder()
                                                      .ldapSettings(toTaskDto(ldapSettings))
                                                      .encryptedDataDetail(getEncryptionDetails(ldapSettings))
                                                      .build();
    DelegateResponseData delegateResponseData =
        createDelegateTask(ldapSettings, taskParameters, NG_LDAP_TEST_CONN_SETTINGS.name());
    LdapTestResponse ldapTestResponse = ((NGLdapDelegateTaskResponse) delegateResponseData).getLdapTestResponse();
    if (FAILURE == ldapTestResponse.getStatus()) {
      handleErrorResponseMessageFromDelegate(ISSUE_WITH_LDAP_CONNECTION, ldapTestResponse.getMessage());
    }
    log.info("Delegate response for validateLdapConnectionSettings: " + ldapTestResponse);
    return ldapTestResponse;
  }

  @Override
  public LdapTestResponse validateLdapUserSettings(String accountIdentifier, NGLdapSettings ldapSettings) {
    log.info("Validate ldap user query for account {}: with settings id {}", accountIdentifier, ldapSettings.getUuid());
    NGLdapDelegateTaskParameters parameters = NGLdapDelegateTaskParameters.builder()
                                                  .ldapSettings(toTaskDto(ldapSettings))
                                                  .encryptedDataDetail(getEncryptionDetails(ldapSettings))
                                                  .build();
    DelegateResponseData delegateResponseData =
        createDelegateTask(ldapSettings, parameters, NG_LDAP_TEST_USER_SETTINGS.name());
    LdapTestResponse ldapTestResponse = ((NGLdapDelegateTaskResponse) delegateResponseData).getLdapTestResponse();
    String ldapTestResponseMessage = ldapTestResponse.getMessage();
    if (FAILURE == ldapTestResponse.getStatus()) {
      handleErrorResponseMessageFromDelegate(ISSUE_WITH_USER_QUERY_SETTINGS_PROVIDED, ldapTestResponseMessage);
    }
    return ldapTestResponse;
  }

  @Override
  public LdapTestResponse validateLdapGroupSettings(String accountIdentifier, NGLdapSettings ldapSettings) {
    log.info("Validate ldap group query for account: {}, with ldap settings id {}", accountIdentifier,
        ldapSettings.getUuid());
    NGLdapDelegateTaskParameters parameters = NGLdapDelegateTaskParameters.builder()
                                                  .ldapSettings(toTaskDto(ldapSettings))
                                                  .encryptedDataDetail(getEncryptionDetails(ldapSettings))
                                                  .build();
    DelegateResponseData delegateResponseData =
        createDelegateTask(ldapSettings, parameters, NG_LDAP_TEST_GROUP_SETTINGS.name());
    LdapTestResponse ldapTestResponse = ((NGLdapDelegateTaskResponse) delegateResponseData).getLdapTestResponse();
    String ldapTestResponseMessage = ldapTestResponse.getMessage();
    if (FAILURE == ldapTestResponse.getStatus()) {
      handleErrorResponseMessageFromDelegate(ISSUE_WITH_GROUP_QUERY_SETTINGS_PROVIDED, ldapTestResponseMessage);
    }
    log.info("Delegate response for validateLdapGroupSettings: " + ldapTestResponse);
    return ldapTestResponse;
  }

  @Override
  public Collection<LdapGroupResponse> searchLdapGroupsByName(String accountIdentifier, String name) {
    NGLdapSettings ngLdapSettings = get(accountIdentifier);

    if (ngLdapSettings.getConnectionSettings().getPasswordRef() == null) {
      throw new InvalidEntityException("Password ref missing in NG LDAP settings", USER);
    }

    NGLdapGroupSearchTaskParameters parameters = NGLdapGroupSearchTaskParameters.builder()
                                                     .ldapSettings(toTaskDto(ngLdapSettings))
                                                     .encryptedDataDetail(getEncryptionDetails(ngLdapSettings))
                                                     .name(name)
                                                     .build();
    DelegateResponseData delegateResponseData =
        createDelegateTask(ngLdapSettings, parameters, NG_LDAP_SEARCH_GROUPS.name());
    NGLdapGroupSearchTaskResponse groupSearchResponse = (NGLdapGroupSearchTaskResponse) delegateResponseData;
    log.info("Received delegate response for searchLdapGroupsByName for account: {}", accountIdentifier);
    return groupSearchResponse.getLdapListGroupsResponses();
  }

  @Override
  public void syncUserGroupsJob(String accountIdentifier) {
    NGLdapSettings ngldapSettings = get(accountIdentifier);
    if (ngldapSettings.isDisabled()) {
      log.info("NG LDAP Settings is disabled for account {}", accountIdentifier);
      return;
    }
    List<UserGroup> userGroupsToSync =
        userGroupService.getUserGroupsBySsoId(accountIdentifier, ngldapSettings.getIdentifier());
    if (isEmpty(userGroupsToSync)) {
      log.info("No User groups to sync for NG Ldap account Id:{}", accountIdentifier);
      return;
    }

    Map<UserGroup, LdapGroupResponse> userGroupsToLdapGroupMap = new HashMap<>();
    for (UserGroup userGroup : userGroupsToSync) {
      try {
        NGLdapGroupSearchTaskParameters taskParameters = NGLdapGroupSearchTaskParameters.builder()
                                                             .ldapSettings(toTaskDto(ngldapSettings))
                                                             .encryptedDataDetail(getEncryptionDetails(ngldapSettings))
                                                             .name(userGroup.getSsoGroupId())
                                                             .build();
        DelegateResponseData delegateResponseData =
            createDelegateTask(ngldapSettings, taskParameters, NG_LDAP_GROUPS_SYNC.name());
        NGLdapGroupSyncTaskResponse groupSyncTaskResponse = (NGLdapGroupSyncTaskResponse) delegateResponseData;
        if (groupSyncTaskResponse.getLdapGroupsResponse() == null) {
          log.error("No response received from delegate for LDAP Group Sync for account {} and userGroup {}",
              accountIdentifier, userGroup.getName());
          continue;
        }
        // recheck user group state validity
        Map<String, Optional<ScopeInfo>> scopeInfoMap =
            scopeInfoService.getScopeInfo(userGroup.getAccountIdentifier(), Set.of(userGroup.getParentUniqueId()));
        ScopeInfo ugScopeInfo = scopeInfoMap.get(userGroup.getParentUniqueId()).orElseThrow();
        if (isUserGroupSsoStateValid(ugScopeInfo, userGroup)) {
          userGroupsToLdapGroupMap.put(userGroup, groupSyncTaskResponse.getLdapGroupsResponse());
        }
      } catch (Exception e) {
        log.error("LDAP Sync error for user group {} and accountId {}", userGroup.getName(), accountIdentifier, e);
      }
    }
    ngLdapGroupSyncHelper.reconcileAllUserGroups(userGroupsToLdapGroupMap, ngldapSettings.getUuid(), accountIdentifier);
  }

  @Override
  public void syncUserGroupWithGroupId(ScopeInfo scopeInfo, String userGroupId) {
    NGLdapSettings ngldapSettings = get(scopeInfo.getAccountIdentifier());
    if (ngldapSettings.isDisabled()) {
      log.info("NG LDAP Settings is disabled for account {}", scopeInfo.getAccountIdentifier());
      return;
    }
    log.info("Sync user group with id {} starting for account: {}", userGroupId, scopeInfo.getAccountIdentifier());
    Optional<UserGroup> userGroup = userGroupService.get(scopeInfo, userGroupId);

    if (!userGroup.isPresent()) {
      log.warn("User group with identifier {} not found to trigger LDAP sync in account: {}",
          scopeInfo.getAccountIdentifier(), userGroup);
      return;
    }

    Map<UserGroup, LdapGroupResponse> userGroupsToLdapGroupMap = new HashMap<>();
    try {
      NGLdapGroupSearchTaskParameters taskParameters = NGLdapGroupSearchTaskParameters.builder()
                                                           .ldapSettings(toTaskDto(ngldapSettings))
                                                           .encryptedDataDetail(getEncryptionDetails(ngldapSettings))
                                                           .name(userGroup.get().getSsoGroupId())
                                                           .build();
      DelegateResponseData delegateResponseData =
          createDelegateTask(ngldapSettings, taskParameters, NG_LDAP_GROUPS_SYNC.name());
      NGLdapGroupSyncTaskResponse groupSyncTaskResponse = (NGLdapGroupSyncTaskResponse) delegateResponseData;
      if (groupSyncTaskResponse.getLdapGroupsResponse() == null) {
        log.error("No response received from delegate for LDAP Group Sync for account {} and userGroup {}",
            scopeInfo.getAccountIdentifier(), userGroupId);
        return;
      }
      // recheck user group state validity
      if (isUserGroupSsoStateValid(scopeInfo, userGroup.get())) {
        userGroupsToLdapGroupMap.put(userGroup.get(), groupSyncTaskResponse.getLdapGroupsResponse());
      }
    } catch (Exception e) {
      log.error("LDAP Sync error for user group {} and accountId {}", userGroupId, scopeInfo.getAccountIdentifier(), e);
    }
    ngLdapGroupSyncHelper.reconcileAllUserGroups(
        userGroupsToLdapGroupMap, ngldapSettings.getUuid(), scopeInfo.getAccountIdentifier());
  }

  @Override
  public UserGroup linkToSsoGroup(
      ScopeInfo scopeInfo, String userGroupId, SSOType ssoType, String ssoId, String ssoGroupId, String ssoGroupName) {
    NGLdapSettings ngldapSettings = get(scopeInfo.getAccountIdentifier());
    return userGroupService.linkToSsoGroupNG(
        scopeInfo, userGroupId, SSOType.LDAP, ssoId, ssoGroupId, ssoGroupName, ngldapSettings.getDisplayName());
  }

  @Override
  public NGLdapSettingsWithEncryptedDataDetailsDTO getLdapSettingsWithEncryptedDataDetails(String accountId) {
    NGLdapSettings ngLdapSettings = get(accountId);
    return NGLdapSettingsWithEncryptedDataDetailsDTO.builder()
        .ngLdapSettings(ngLdapSettings)
        .encryptedDataDetail(getEncryptionDetails(ngLdapSettings))
        .build();
  }

  @Override
  public List<Long> getIterationsFromCron(String accountId, String cron) {
    List<Long> nextIterations = new ArrayList<>();
    try {
      getPersistentCronIterableObject().expandNextIterations(true, 0, cron, nextIterations);
    } catch (Exception ex) {
      String message = "Given cron expression doesn't evaluate to a valid time. Please check the expression provided";
      log.error(message, ex);
      throw new InvalidRequestException(message);
    }
    return validateIterationsAndRemoveCurrentTime(nextIterations);
  }

  @Override
  public LdapResponse testLDAPLogin(ScopeInfo scopeInfo, String email, String password) {
    log.info(
        "NGLDAP: Test LDAP authentication for account: {}, with email: {}", scopeInfo.getAccountIdentifier(), email);
    NGLdapSettingsWithEncryptedDataDetailsDTO ngLdapSettingsWithEncryptedDataDetailsDTO =
        getLdapSettingsWithEncryptedDataDetails(scopeInfo.getAccountIdentifier());

    EncryptedDataDetail passwordEncryptedDataDetail = encryptPassword(scopeInfo.getAccountIdentifier(), password);

    NGLdapTestAuthenticationTaskParameters taskParameters =
        NGLdapTestAuthenticationTaskParameters.builder()
            .ldapSettings(toTaskDto(ngLdapSettingsWithEncryptedDataDetailsDTO.getNgLdapSettings()))
            .settingsEncryptedDataDetail(ngLdapSettingsWithEncryptedDataDetailsDTO.getEncryptedDataDetail())
            .passwordEncryptedDataDetail(passwordEncryptedDataDetail)
            .username(email)
            .build();

    DelegateResponseData delegateResponseData =
        createDelegateTask(ngLdapSettingsWithEncryptedDataDetailsDTO.getNgLdapSettings(), taskParameters,
            NG_LDAP_TEST_AUTHENTICATION.name());
    NGLdapTestAuthenticationTaskResponse authResponse = (NGLdapTestAuthenticationTaskResponse) delegateResponseData;
    LdapResponse ldapAuthTestResponse = authResponse.getLdapAuthenticationResponse();
    if (null != ldapAuthTestResponse) {
      final String ldapAuthTestResponseMessage = ldapAuthTestResponse.getMessage();
      if (LdapResponse.Status.FAILURE == ldapAuthTestResponse.getStatus() && isNotEmpty(ldapAuthTestResponseMessage)) {
        handleErrorResponseMessageFromDelegate(ISSUE_WITH_LDAP_TEST_AUTHENTICATION, ldapAuthTestResponseMessage);
      }
    }
    return ldapAuthTestResponse;
  }

  private EncryptedDataDetail encryptPassword(String accountIdentifier, String password) {
    String randomEncryptionKey = UUIDGenerator.generateUuid();
    char[] encryptedValue = new SimpleEncryption(randomEncryptionKey).encryptChars(password.toCharArray());

    EncryptedRecordData encryptedRecordData = EncryptedRecordData.builder()
                                                  .uuid(UUIDGenerator.generateUuid())
                                                  .name("Ldap test login password")
                                                  .encryptionType(LOCAL)
                                                  .encryptionKey(randomEncryptionKey)
                                                  .encryptedValue(encryptedValue)
                                                  .base64Encoded(false)
                                                  .build();

    EncryptionConfig encryptionConfig = SecretManagerConfigMapper.fromDTO(
        LocalConfigDTO.builder().accountIdentifier(accountIdentifier).identifier(null).encryptionType(LOCAL).build());

    return EncryptedDataDetail.builder()
        .encryptedData(encryptedRecordData)
        .encryptionConfig(encryptionConfig)
        .fieldName(NG_LDAP_LOGIN_TEST_PASSWORD)
        .build();
  }

  @VisibleForTesting
  DelegateResponseData createDelegateTask(
      NGLdapSettings ngLdapSettings, TaskParameters taskParameters, String taskType) {
    final DelegateResponseData delegateResponseData;
    try {
      DelegateTaskRequest taskRequest = getDelegateTask(ngLdapSettings, taskParameters, taskType);
      if (taskRequest.getTaskSelectors().size() > 0) {
        log.info(String.format("Task in account {} with type {} for ssoId {} has the following selectors : {}",
            taskRequest.getAccountId(), taskType, ngLdapSettings.getUuid(), taskRequest.getTaskSelectors()));
      }
      delegateResponseData = delegateService.executeSyncTaskV2(taskRequest);
    } catch (DelegateServiceDriverException ex) {
      String message = String.format(
          "Unable to process LDAP delegate task for account id %s", ngLdapSettings.getAccountIdentifier());
      throw buildDelegateNotAvailableHintException(message);
    }
    if (delegateResponseData instanceof ErrorNotifyResponseData) {
      throw buildDelegateNotAvailableHintException(((ErrorNotifyResponseData) delegateResponseData).getErrorMessage());
    }
    return delegateResponseData;
  }

  @VisibleForTesting
  DelegateTaskRequest getDelegateTask(NGLdapSettings ngLdapSettings, TaskParameters taskParameters, String taskType) {
    // Task timeOut range should be between min(60 seconds) and max(180 sec).
    Duration taskTimeOut = Duration.ofMillis(Math.min(
        Math.max(ngLdapSettings.getConnectionSettings().getResponseTimeout(), LDAP_TASK_DEFAULT_MINIMUM_TIMEOUT_MILLIS),
        LDAP_TASK_DEFAULT_MAXIMUM_TIMEOUT_MILLIS));
    List<TaskSelector> delegateSelectors = isNotEmpty(ngLdapSettings.getConnectionSettings().getDelegateSelectors())
        ? TaskSelectorYaml.toTaskSelector(ngLdapSettings.getConnectionSettings().getDelegateSelectors())
        : new ArrayList<>();

    return DelegateTaskRequest.builder()
        .taskType(taskType)
        .taskParameters(taskParameters)
        .executionTimeout(taskTimeOut)
        .accountId(ngLdapSettings.getAccountIdentifier())
        .taskSetupAbstractions(buildAbstractions(ngLdapSettings.getAccountIdentifier()))
        .selectors(delegateSelectors)
        .build();
  }

  private void checkForLinkedSSOGroups(final String accountIdentifier, final String settingsId) {
    List<UserGroup> userGroups = userGroupService.getUserGroupsBySsoId(accountIdentifier, settingsId);
    if (isNotEmpty(userGroups)) {
      throw new InvalidRequestException(
          "Deleting SSO provider with linked user groups is not allowed. Unlink the user groups in NG also first.");
    }
  }

  private void updateNextIterations(NGLdapSettings ldapSettings) {
    ldapSettings.getNextIterations().clear();
    //@Todo: check validateIterationsAndRemoveCurrentTime()
    ldapSettings.setNextIterations(ldapSettings.recalculateNextIterations(NgSsoSettingsKeys.nextIterations, true, 0));
  }

  private PersistentCronIterable getPersistentCronIterableObject() {
    return new PersistentCronIterable() {
      @Override
      public String getUuid() {
        return null;
      }

      @Override
      public Long obtainNextIteration(String fieldName) {
        return null;
      }

      @Override
      public List<Long> recalculateNextIterations(String fieldName, boolean skipMissed, long throttled) {
        return null;
      }
    };
  }

  private List<Long> validateIterationsAndRemoveCurrentTime(List<Long> nextIterations) {
    if (nextIterations.size() > 1 && ((nextIterations.get(1) - nextIterations.get(0)) / 1000 < MIN_INTERVAL)) {
      throw new InvalidRequestException(
          "Cron Expression should evaluate to time intervals of at least " + MIN_INTERVAL + " seconds.");
    }
    if (isEmpty(nextIterations)) {
      throw new InvalidRequestException(
          "Given cron expression doesn't evaluate to a valid time. Please check the expression provided");
    }
    return nextIterations;
  }
  private LdapSettings toTaskDto(NGLdapSettings ngldapSettings) {
    // Add dummy password for delegate task if bindPassword is empty, this is true for newly created NG Ldap settings
    // In delegate task we have check in place while decrypting if bind password is masked format (Refer:
    // LdapSettings::decryptFields()) Adding dummy value to avoid delegate upgrade
    if (isEmpty(ngldapSettings.getConnectionSettings().getBindPassword())
        && ngldapSettings.getConnectionSettings().getPasswordRef() != null) {
      ngldapSettings.getConnectionSettings().setBindPassword(LdapConstants.MASKED_STRING);
    }
    return LdapSettings.builder()
        .accountId(ngldapSettings.getAccountIdentifier())
        .connectionSettings(ngldapSettings.getConnectionSettings())
        .userSettingsList(ngldapSettings.getUserSettingsList())
        .groupSettingsList(ngldapSettings.getGroupSettingsList())
        .displayName(ngldapSettings.getDisplayName())
        .uuid(ngldapSettings.getUuid())
        .disabled(ngldapSettings.isDisabled())
        .build();
  }

  private Map<String, String> buildAbstractions(String accountIdIdentifier) {
    Map<String, String> abstractions = new HashMap<>(2);
    String owner = taskSetupAbstractionHelper.getOwner(accountIdIdentifier, null, null);
    if (isNotEmpty(owner)) {
      abstractions.put(OWNER, owner);
    }
    abstractions.put(NG, "true");
    return abstractions;
  }

  private HintException buildDelegateNotAvailableHintException(String delegateDownErrorMessage) {
    return new HintException(
        String.format(HintException.DELEGATE_NOT_AVAILABLE, DocumentLinksConstants.DELEGATE_INSTALLATION_LINK),
        new DelegateNotAvailableException(delegateDownErrorMessage, WingsException.USER));
  }

  public boolean isUserGroupSsoStateValid(ScopeInfo scopeInfo, UserGroup userGroup) {
    // Check if the User Group State has not changed
    Optional<UserGroup> savedUserGroup = userGroupService.get(scopeInfo, userGroup.getIdentifier());
    if (!savedUserGroup.isPresent()) {
      log.error("User group {} for account {} no longer exists.", userGroup.getIdentifier(),
          userGroup.getAccountIdentifier());
      return false;
    }
    if (!savedUserGroup.get().getIsSsoLinked()) {
      log.error("User group {} for account {} is no longer SSO linked ", userGroup.getIdentifier(),
          userGroup.getAccountIdentifier());
      return false;
    }
    if (!savedUserGroup.get().getSsoGroupId().equals(userGroup.getSsoGroupId())) {
      log.error("User group {} for account {} is linked to SSO Group {} but sync happening for SSO Group {}.",
          userGroup.getIdentifier(), userGroup.getAccountIdentifier(), savedUserGroup.get().getSsoGroupId(),
          userGroup.getSsoGroupId());
      return false;
    }

    return true;
  }

  @VisibleForTesting
  EncryptedDataDetail getEncryptionDetails(NGLdapSettings ngLdapSettings) {
    NGAccess ngAccess = BaseNGAccess.builder().accountIdentifier(ngLdapSettings.getAccountIdentifier()).build();
    List<EncryptedDataDetail> encryptionDetails =
        secretManagerClientService.getEncryptionDetails(ngAccess, ngLdapSettings.getConnectionSettings());
    return isNotEmpty(encryptionDetails) ? encryptionDetails.get(0) : null;
  }

  private void handleErrorResponseMessageFromDelegate(String errorMessage, String ldapTestResponseMessage) {
    if (isEmpty(ldapTestResponseMessage)) {
      log.error("No response received from delegate. {}", errorMessage);
    }
    try {
      if (LdapConstants.INVALID_CREDENTIALS.equals(ldapTestResponseMessage)) {
        LdapErrorHandler.handleError(ResultCode.INVALID_CREDENTIALS, errorMessage, true);
      } else {
        LdapErrorHandler.handleError(ResultCode.valueOf(ldapTestResponseMessage), errorMessage, false);
      }
    } catch (IllegalArgumentException exception) {
      log.error("NGLDAP: Received {} error code from Delegate. Check if this case is not handled in Delegate.",
          ldapTestResponseMessage, exception);
      throw NestedExceptionUtils.hintWithExplanationException(HintException.LDAP_ATTRIBUTES_INCORRECT,
          ExplanationException.LDAP_ATTRIBUTES_INCORRECT, new GeneralException(errorMessage));
    }
  }

  private void logAuditEvent(
      NGLdapSettings newNGLdapSettings, NGLdapSettings oldNGLdapSettings, String accountIdentifier) {
    NGLoginSettingsAbstractLDAPConfigurationEventBuilder ldapAuditEventBuilder =
        LDAPCreateAuditEvent.builder()
            .accountIdentifier(accountIdentifier)
            .newNgLdapSettingsYamlDTO(NgLdapSettingsYamlDTO.builder().ngLdapSettings(newNGLdapSettings).build());
    if (oldNGLdapSettings != null) {
      ldapAuditEventBuilder.oldNgLdapSettingsYamlDTO(
          NgLdapSettingsYamlDTO.builder().ngLdapSettings(oldNGLdapSettings).build());
    }
    outboxService.save(ldapAuditEventBuilder.build());
  }
}
