/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.api.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.ng.accesscontrol.PlatformPermissions.MANAGEAPIKEY_SERVICEACCOUNT_PERMISSION;
import static io.harness.ng.core.account.ServiceAccountConfig.DEFAULT_API_KEY_LIMIT;
import static io.harness.ng.core.utils.NGUtils.validate;
import static io.harness.outbox.OutboxSDKConstants.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.account.services.AccountService;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.governance.GovernanceMetadata;
import io.harness.ng.accesscontrol.PlatformResourceTypes;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.account.ServiceAccountConfig;
import io.harness.ng.core.api.ApiKeyService;
import io.harness.ng.core.api.TokenService;
import io.harness.ng.core.common.beans.ApiKeyType;
import io.harness.ng.core.common.beans.NGTag.NGTagKeys;
import io.harness.ng.core.dto.ApiKeyAggregateDTO;
import io.harness.ng.core.dto.ApiKeyDTO;
import io.harness.ng.core.dto.ApiKeyFilterDTO;
import io.harness.ng.core.dto.GatewayAccountRequestDTO;
import io.harness.ng.core.entities.ApiKey;
import io.harness.ng.core.entities.ApiKey.ApiKeyKeys;
import io.harness.ng.core.events.ApiKeyCreateEvent;
import io.harness.ng.core.events.ApiKeyDeleteEvent;
import io.harness.ng.core.events.ApiKeyUpdateEvent;
import io.harness.ng.core.mapper.ApiKeyDTOMapper;
import io.harness.ng.core.user.UserInfo;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.ng.opa.entities.apiKey.ApiKeyOpaService;
import io.harness.ng.serviceaccounts.service.api.ServiceAccountService;
import io.harness.opaclient.model.OpaConstants;
import io.harness.outbox.api.OutboxService;
import io.harness.repositories.ng.core.spring.ApiKeyRepository;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.security.dto.PrincipalType;
import io.harness.utils.PageUtils;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@OwnedBy(PL)
public class ApiKeyServiceImpl implements ApiKeyService {
  @Inject private ApiKeyRepository apiKeyRepository;
  @Inject private OutboxService outboxService;
  @Inject private TokenService tokenService;
  @Inject @Named(OUTBOX_TRANSACTION_TEMPLATE) private TransactionTemplate transactionTemplate;
  @Inject private AccessControlClient accessControlClient;
  @Inject private AccountService accountService;
  @Inject private NgUserService ngUserService;
  @Inject private ServiceAccountService serviceAccountService;
  @Inject private ApiKeyOpaService apiKeyOpaService;

  @Override
  public ApiKeyDTO createApiKey(ApiKeyDTO apiKeyDTO, ScopeInfo scopeInfo) {
    setScopeForUserApiKey(apiKeyDTO, scopeInfo);
    validateApiKeyRequest(scopeInfo, apiKeyDTO.getParentIdentifier(), apiKeyDTO.getApiKeyType());
    validateApiKeyLimit(scopeInfo, apiKeyDTO.getParentIdentifier());
    try {
      ApiKey apiKey = ApiKeyDTOMapper.getApiKeyFromDTO(apiKeyDTO, scopeInfo);
      validate(apiKey);

      ApiKeyDTO opaValidationResponse = evaluateApiKeyForOPAPolicies(scopeInfo, apiKeyDTO);
      if (opaValidationResponse.getGovernanceMetadata() != null
          && OpaConstants.OPA_STATUS_ERROR.equals(opaValidationResponse.getGovernanceMetadata().getStatus())) {
        return opaValidationResponse;
      }
      return Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
        ApiKey savedApiKey = apiKeyRepository.save(apiKey);
        ApiKeyDTO savedDTO = ApiKeyDTOMapper.getDTOFromApiKey(savedApiKey, scopeInfo);
        outboxService.save(new ApiKeyCreateEvent(savedDTO));
        savedDTO.setGovernanceMetadata(opaValidationResponse.getGovernanceMetadata());
        return savedDTO;
      }));
    } catch (DuplicateKeyException e) {
      log.error("Error occured while creating API Key", e);
      throw new DuplicateFieldException(
          String.format("Try using different Key name, [%s] already exists", apiKeyDTO.getIdentifier()));
    }
  }

  private void setScopeForUserApiKey(ApiKeyDTO apiKeyDTO, ScopeInfo scopeInfo) {
    if (ApiKeyType.USER.equals(apiKeyDTO.getApiKeyType())) {
      scopeInfo.setOrgIdentifier(null);
      scopeInfo.setProjectIdentifier(null);
      scopeInfo.setUniqueId(scopeInfo.getAccountIdentifier());
    }
  }

  private ApiKeyDTO evaluateApiKeyForOPAPolicies(ScopeInfo scopeInfo, ApiKeyDTO apiKeyDTO) {
    GovernanceMetadata governanceMetadata = apiKeyOpaService.evaluatePoliciesWithEntity(
        scopeInfo, apiKeyDTO, OpaConstants.OPA_EVALUATION_ACTION_SAVE, apiKeyDTO.getIdentifier());
    ApiKeyDTO opaErrorResponse = ApiKeyDTO.builder().build();
    opaErrorResponse.setGovernanceMetadata(governanceMetadata);
    return opaErrorResponse;
  }

  private void validateApiKeyRequest(ScopeInfo scopeInfo, String parentIdentifier, ApiKeyType apiKeyType) {
    switch (apiKeyType) {
      case USER:
      case SSH_KEY:
      case PGP_KEY:
        break;
      case SERVICE_ACCOUNT:
        serviceAccountService.getServiceAccountDTO(scopeInfo, parentIdentifier);
        break;
      default:
    }
  }

  private void validateApiKeyLimit(ScopeInfo scopeInfo, String parentIdentifier) {
    ServiceAccountConfig serviceAccountConfig =
        accountService.getAccount(scopeInfo.getAccountIdentifier()).getServiceAccountConfig();
    long apiKeyLimit = serviceAccountConfig != null ? serviceAccountConfig.getApiKeyLimit() : DEFAULT_API_KEY_LIMIT;
    long existingAPIKeyCount = apiKeyRepository.countByAccountIdentifierAndParentUniqueIdAndParentIdentifier(
        scopeInfo.getAccountIdentifier(), scopeInfo.getUniqueId(), parentIdentifier);
    if (existingAPIKeyCount >= apiKeyLimit) {
      throw new InvalidRequestException(String.format("Maximum API Key limit has been reached"));
    }
  }

  @Override
  public ApiKeyDTO updateApiKey(ApiKeyDTO apiKeyDTO, ScopeInfo scopeInfo) {
    validateApiKeyRequest(scopeInfo, apiKeyDTO.getParentIdentifier(), apiKeyDTO.getApiKeyType());
    Optional<ApiKey> optionalApiKey =
        apiKeyRepository.findByAccountIdentifierAndParentUniqueIdAndAndApiKeyTypeAndParentIdentifierAndIdentifier(
            scopeInfo.getAccountIdentifier(), scopeInfo.getUniqueId(), apiKeyDTO.getApiKeyType(),
            apiKeyDTO.getParentIdentifier(), apiKeyDTO.getIdentifier());
    Preconditions.checkState(
        optionalApiKey.isPresent(), "Api key not present in scope for identifier: " + apiKeyDTO.getIdentifier());
    ApiKey existingKey = optionalApiKey.get();
    ApiKeyDTO existingDTO = ApiKeyDTOMapper.getDTOFromApiKey(existingKey, scopeInfo);
    ApiKey newKey = ApiKeyDTOMapper.getApiKeyFromDTO(apiKeyDTO, scopeInfo);
    newKey.setUuid(existingKey.getUuid());
    newKey.setCreatedAt(existingKey.getCreatedAt());
    newKey.setParentUniqueId(existingKey.getParentUniqueId());
    newKey.setUniqueId(existingKey.getUniqueId());
    ApiKeyDTO opaValidationResponse = evaluateApiKeyForOPAPolicies(scopeInfo, apiKeyDTO);
    if (opaValidationResponse.getGovernanceMetadata() != null
        && OpaConstants.OPA_STATUS_ERROR.equals(opaValidationResponse.getGovernanceMetadata().getStatus())) {
      return opaValidationResponse;
    }
    return Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
      ApiKey savedApiKey = apiKeyRepository.save(newKey);
      ApiKeyDTO savedDTO = ApiKeyDTOMapper.getDTOFromApiKey(savedApiKey, scopeInfo);
      outboxService.save(new ApiKeyUpdateEvent(existingDTO, savedDTO));
      savedDTO.setGovernanceMetadata(opaValidationResponse.getGovernanceMetadata());
      return savedDTO;
    }));
  }

  @Override
  public boolean deleteApiKey(ScopeInfo scopeInfo, ApiKeyType apiKeyType, String parentIdentifier, String identifier) {
    validateApiKeyRequest(scopeInfo, parentIdentifier, apiKeyType);
    Optional<ApiKey> optionalApiKey =
        apiKeyRepository.findByAccountIdentifierAndParentUniqueIdAndAndApiKeyTypeAndParentIdentifierAndIdentifier(
            scopeInfo.getAccountIdentifier(), scopeInfo.getUniqueId(), apiKeyType, parentIdentifier, identifier);
    if (optionalApiKey.isEmpty()) {
      throw new EntityNotFoundException(
          String.format("API key with identifier [%s] does not exist in account [%s], org [%s], project [%s] "
                  + "for apiKeyType [%s], parentIdentifier [%s]",
              identifier, scopeInfo.getAccountIdentifier(), scopeInfo.getOrgIdentifier(),
              scopeInfo.getProjectIdentifier(), apiKeyType, parentIdentifier));
    }
    ApiKeyDTO existingDTO = ApiKeyDTOMapper.getDTOFromApiKey(optionalApiKey.get(), scopeInfo);
    return Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
      long deleted =
          apiKeyRepository.deleteByAccountIdentifierAndParentUniqueIdAndApiKeyTypeAndParentIdentifierAndIdentifier(
              scopeInfo.getAccountIdentifier(), scopeInfo.getUniqueId(), apiKeyType, parentIdentifier, identifier);
      if (deleted > 0) {
        outboxService.save(new ApiKeyDeleteEvent(existingDTO));
        tokenService.deleteAllByApiKeyIdentifier(scopeInfo, apiKeyType, parentIdentifier, identifier);
        return true;
      } else {
        return false;
      }
    }));
  }

  @Override
  public List<ApiKeyDTO> listApiKeys(
      ScopeInfo scopeInfo, ApiKeyType apiKeyType, String parentIdentifier, List<String> identifiers) {
    List<ApiKey> apiKeys;
    if (isEmpty(identifiers)) {
      apiKeys = apiKeyRepository.findAllByAccountIdentifierAndParentUniqueIdAndApiKeyTypeAndParentIdentifier(
          scopeInfo.getAccountIdentifier(), scopeInfo.getUniqueId(), apiKeyType, parentIdentifier);
    } else {
      apiKeys =
          apiKeyRepository.findAllByAccountIdentifierAndParentUniqueIdAndApiKeyTypeAndParentIdentifierAndIdentifierIn(
              scopeInfo.getAccountIdentifier(), scopeInfo.getUniqueId(), apiKeyType, parentIdentifier, identifiers);
    }
    List<ApiKeyDTO> apiKeyDTOS = new ArrayList<>();
    apiKeys.forEach(apiKey -> apiKeyDTOS.add(ApiKeyDTOMapper.getDTOFromApiKey(apiKey, scopeInfo)));
    return apiKeyDTOS;
  }

  @Override
  public Optional<ApiKey> getApiKey(
      ScopeInfo scopeInfo, ApiKeyType apiKeyType, String parentIdentifier, String identifier) {
    return apiKeyRepository.findByAccountIdentifierAndParentUniqueIdAndAndApiKeyTypeAndParentIdentifierAndIdentifier(
        scopeInfo.getAccountIdentifier(), scopeInfo.getUniqueId(), apiKeyType, parentIdentifier, identifier);
  }

  @Override
  public Map<String, Integer> getApiKeysPerParentIdentifier(
      ScopeInfo scopeInfo, ApiKeyType apiKeyType, List<String> parentIdentifier) {
    return apiKeyRepository.getApiKeysPerParentIdentifier(scopeInfo, apiKeyType, parentIdentifier);
  }

  @Override
  public PageResponse<ApiKeyAggregateDTO> listAggregateApiKeys(
      ScopeInfo scopeInfo, Pageable pageable, ApiKeyFilterDTO filterDTO) {
    Criteria criteria = createApiKeyFilterCriteria(createScopeInfoCriteria(scopeInfo), filterDTO);
    Page<ApiKey> apiKeys = apiKeyRepository.findAll(criteria, pageable);
    List<String> apiKeyIdentifiers =
        apiKeys.stream().map(ApiKey::getIdentifier).distinct().collect(Collectors.toList());
    Map<String, Integer> tokenCountMap = tokenService.getTokensPerApiKeyIdentifier(
        scopeInfo, filterDTO.getApiKeyType(), filterDTO.getParentIdentifier(), apiKeyIdentifiers);
    return PageUtils.getNGPageResponse(apiKeys.map(apiKey -> {
      ApiKeyDTO apiKeyDTO = ApiKeyDTOMapper.getDTOFromApiKey(apiKey, scopeInfo);
      return ApiKeyAggregateDTO.builder()
          .apiKey(apiKeyDTO)
          .createdAt(apiKey.getCreatedAt())
          .lastModifiedAt(apiKey.getLastModifiedAt())
          .tokensCount(tokenCountMap.getOrDefault(apiKey.getIdentifier(), 0))
          .build();
    }));
  }

  private Criteria createApiKeyFilterCriteria(Criteria criteria, ApiKeyFilterDTO filterDTO) {
    if (filterDTO == null) {
      return criteria;
    }
    if (isNotBlank(filterDTO.getSearchTerm())) {
      criteria.orOperator(Criteria.where(ApiKeyKeys.name).regex(filterDTO.getSearchTerm(), "i"),
          Criteria.where(ApiKeyKeys.identifier).regex(filterDTO.getSearchTerm(), "i"),
          Criteria.where(ApiKeyKeys.tags + "." + NGTagKeys.key).regex(filterDTO.getSearchTerm(), "i"),
          Criteria.where(ApiKeyKeys.tags + "." + NGTagKeys.value).regex(filterDTO.getSearchTerm(), "i"));
    }
    criteria.and(ApiKeyKeys.apiKeyType).is(filterDTO.getApiKeyType());
    criteria.and(ApiKeyKeys.parentIdentifier).is(filterDTO.getParentIdentifier());

    if (Objects.nonNull(filterDTO.getIdentifiers()) && !filterDTO.getIdentifiers().isEmpty()) {
      criteria.and(ApiKeyKeys.identifier).in(filterDTO.getIdentifiers());
    }
    return criteria;
  }

  @Override
  public ApiKeyAggregateDTO getApiKeyAggregateDTO(
      ScopeInfo scopeInfo, ApiKeyType apiKeyType, String parentIdentifier, String identifier) {
    Optional<ApiKey> apiKey =
        apiKeyRepository.findByAccountIdentifierAndParentUniqueIdAndAndApiKeyTypeAndParentIdentifierAndIdentifier(
            scopeInfo.getAccountIdentifier(), scopeInfo.getUniqueId(), apiKeyType, parentIdentifier, identifier);
    if (!apiKey.isPresent()) {
      throw new InvalidArgumentsException(String.format("Api key [%s] doesn't exist in scope", identifier));
    }
    ApiKeyDTO apiKeyDTO = ApiKeyDTOMapper.getDTOFromApiKey(apiKey.get(), scopeInfo);
    Map<String, Integer> tokenCountMap = tokenService.getTokensPerApiKeyIdentifier(
        scopeInfo, apiKeyType, parentIdentifier, Collections.singletonList(identifier));
    return ApiKeyAggregateDTO.builder()
        .apiKey(apiKeyDTO)
        .createdAt(apiKey.get().getCreatedAt())
        .lastModifiedAt(apiKey.get().getLastModifiedAt())
        .tokensCount(tokenCountMap.getOrDefault(apiKeyDTO.getIdentifier(), 0))
        .build();
  }

  @Override
  public long deleteAllByParentIdentifier(ScopeInfo scopeInfo, ApiKeyType apiKeyType, String parentIdentifier) {
    return apiKeyRepository.deleteAllByAccountIdentifierAndParentUniqueIdAndApiKeyTypeAndParentIdentifier(
        scopeInfo.getAccountIdentifier(), scopeInfo.getUniqueId(), apiKeyType, parentIdentifier);
  }

  private Criteria createScopeCriteria(String accountIdentifier, String parentUniqueId) {
    Criteria criteria = new Criteria();
    criteria.and(ApiKeyKeys.accountIdentifier).is(accountIdentifier);
    criteria.and(ApiKeyKeys.parentUniqueId).is(parentUniqueId);
    return criteria;
  }
  private Criteria createScopeInfoCriteria(ScopeInfo scopeInfo) {
    Criteria criteria = new Criteria();
    criteria.and(ApiKeyKeys.accountIdentifier).is(scopeInfo.getAccountIdentifier());
    criteria.and(ApiKeyKeys.parentUniqueId).is(scopeInfo.getUniqueId());
    return criteria;
  }

  @Override
  public void deleteAtAllScopes(ScopeInfo scopeInfo) {
    Criteria criteria = createScopeCriteria(scopeInfo.getAccountIdentifier(), scopeInfo.getUniqueId());
    apiKeyRepository.deleteAll(criteria);
  }

  @Override
  public void validateParentIdentifier(ScopeInfo scopeInfo, ApiKeyType apiKeyType, String parentIdentifier) {
    switch (apiKeyType) {
      case USER:
      case SSH_KEY:
      case PGP_KEY:
        validateCallerIsUser(parentIdentifier);
        validateUserExistsInAccount(parentIdentifier, scopeInfo, apiKeyType);
        break;
      case SERVICE_ACCOUNT:
        accessControlClient.checkForAccessOrThrow(ResourceScope.of(scopeInfo.getAccountIdentifier(),
                                                      scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier()),
            Resource.of(PlatformResourceTypes.SERVICEACCOUNT, parentIdentifier),
            MANAGEAPIKEY_SERVICEACCOUNT_PERMISSION);
        break;
      case SCOPED_TOKEN:
        validateScopedTokenParent(scopeInfo, apiKeyType, parentIdentifier);
        break;
      default:
        throw new InvalidArgumentsException(String.format("Invalid API key type: %s", apiKeyType));
    }
  }

  private void validateCallerIsUser(String parentIdentifier) {
    Principal principal = SourcePrincipalContextBuilder.getSourcePrincipal();
    if (principal == null) {
      throw new InvalidArgumentsException("No user identifier present in context");
    }
    if (principal.getType() != PrincipalType.USER) {
      throw new NGAccessDeniedException(
          String.format("User [%s] is not authorized", principal.getName()), WingsException.USER, null);
    }
    if (!principal.getName().equals(parentIdentifier)) {
      throw new NGAccessDeniedException(String.format("User [%s] not authorized to perform the action for user [%s]",
                                            principal.getName(), parentIdentifier),
          WingsException.USER, null);
    }
  }

  private void validateScopedTokenParent(ScopeInfo scopeInfo, ApiKeyType apiKeyType, String parentIdentifier) {
    Principal principal = SourcePrincipalContextBuilder.getSourcePrincipal();
    if (principal == null) {
      throw new InvalidArgumentsException("No principal present in context");
    }
    if (principal.getType() == PrincipalType.USER) {
      if (!principal.getName().equals(parentIdentifier)) {
        throw new NGAccessDeniedException(
            String.format("User [%s] can only create scoped tokens for themselves, not for [%s]", principal.getName(),
                parentIdentifier),
            WingsException.USER, null);
      }
      validateUserExistsInAccount(parentIdentifier, scopeInfo, apiKeyType);
    } else if (principal.getType() == PrincipalType.SERVICE_ACCOUNT) {
      if (!principal.getName().equals(parentIdentifier)) {
        throw new NGAccessDeniedException(
            String.format("Service account [%s] can only create scoped tokens for itself, not for [%s]",
                principal.getName(), parentIdentifier),
            WingsException.USER, null);
      }
      validateServiceAccountExistsInScope(scopeInfo, parentIdentifier);
    } else {
      throw new NGAccessDeniedException(
          String.format("Principal type [%s] is not allowed to create scoped tokens", principal.getType()),
          WingsException.USER, null);
    }
  }

  private void validateUserExistsInAccount(String userId, ScopeInfo scopeInfo, ApiKeyType apiKeyType) {
    Optional<UserInfo> userInfo = ngUserService.getUserById(userId);
    if (userInfo.isEmpty()) {
      throw new InvalidArgumentsException(String.format("No user found with id: [%s]", userId));
    }
    List<GatewayAccountRequestDTO> userAccounts = userInfo.get().getAccounts();
    if (userAccounts == null
        || userAccounts.stream()
               .filter(account -> account.getUuid().equals(scopeInfo.getAccountIdentifier()))
               .findFirst()
               .isEmpty()) {
      throw new NGAccessDeniedException(
          String.format("User [%s] is not authorized to perform action on [%s] Key for account: [%s]", userId,
              apiKeyType, scopeInfo.getAccountIdentifier()),
          WingsException.USER, null);
    }
  }

  private void validateServiceAccountExistsInScope(ScopeInfo scopeInfo, String parentIdentifier) {
    try {
      serviceAccountService.getServiceAccountDTO(scopeInfo, parentIdentifier);
    } catch (Exception ex) {
      throw new InvalidArgumentsException(
          String.format("No service account found with id: [%s] in scope", parentIdentifier));
    }
  }

  @Override
  public Long countApiKeys(String accountIdentifier) {
    return apiKeyRepository.countByAccountIdentifier(accountIdentifier);
  }

  @Override
  public int deleteAllApiKeysForParent(ScopeInfo scopeInfo, ApiKeyType apiKeyType, String parentIdentifier) {
    ApiKeyFilterDTO filterDTO =
        ApiKeyFilterDTO.builder().apiKeyType(apiKeyType).parentIdentifier(parentIdentifier).build();

    int page = 0;
    int pageSize = 100; // reasonable page size
    int deletedCount = 0;

    PageResponse<ApiKeyAggregateDTO> apiKeys;

    do {
      apiKeys = listAggregateApiKeys(scopeInfo, PageRequest.of(page, pageSize), filterDTO);

      List<ApiKeyAggregateDTO> content = apiKeys.getContent();
      for (ApiKeyAggregateDTO apiKeyAggregate : content) {
        ApiKeyDTO apiKey = apiKeyAggregate.getApiKey();
        boolean deleted =
            deleteApiKey(scopeInfo, apiKey.getApiKeyType(), apiKey.getParentIdentifier(), apiKey.getIdentifier());
        if (deleted) {
          deletedCount++;
        }
      }

      page++;
    } while (!apiKeys.getContent().isEmpty());

    return deletedCount;
  }
}
