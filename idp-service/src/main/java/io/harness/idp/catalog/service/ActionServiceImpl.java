/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.idp.catalog.service;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.catalog.entities.Action;
import io.harness.idp.catalog.entities.ActionStatus;
import io.harness.idp.catalog.entities.ActionType;
import io.harness.idp.catalog.events.ActionCreateEvent;
import io.harness.idp.catalog.events.ActionDeleteEvent;
import io.harness.idp.catalog.events.ActionUpdateEvent;
import io.harness.idp.catalog.mapper.ActionMapper;
import io.harness.idp.catalog.repositories.ActionRepository;
import io.harness.idp.catalog.utils.ActionInputSchemaValidator;
import io.harness.idp.catalog.utils.ActionLifecycleValidator;
import io.harness.outbox.api.OutboxService;
import io.harness.spec.server.idp.v1.model.ActionUpdateRequest;
import io.harness.springdata.TransactionHelper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class ActionServiceImpl implements ActionService {
  private static final ObjectMapper objectMapper = NG_DEFAULT_OBJECT_MAPPER;

  private final ActionRepository actionRepository;
  private final OutboxService outboxService;
  private final TransactionHelper transactionHelper;
  private final CatalogScopeResolver catalogScopeResolver;

  @Inject
  public ActionServiceImpl(ActionRepository actionRepository, OutboxService outboxService,
      TransactionHelper transactionHelper, CatalogScopeResolver catalogScopeResolver) {
    this.actionRepository = actionRepository;
    this.outboxService = outboxService;
    this.transactionHelper = transactionHelper;
    this.catalogScopeResolver = catalogScopeResolver;
  }

  @Override
  public Action createAction(ScopeInfo scopeInfo, Action action) {
    String parentUniqueId = scopeInfo.getUniqueId();
    if (Action.GLOBAL_PARENT_UNIQUE_ID.equals(parentUniqueId)
        || Action.GLOBAL_ACCOUNT_IDENTIFIER.equals(scopeInfo.getAccountIdentifier())) {
      throw new InvalidRequestException(
          "Cannot create Actions under the reserved global scope; this scope is owned by Harness OOTB Actions.");
    }

    if (findActionWithGlobalFallback(parentUniqueId, action.getIdentifier(), action.getVersion()).isPresent()) {
      throw new DuplicateFieldException(String.format(
          "Action with identifier [%s] and version [%s] already exists in this scope or as an OOTB Action",
          action.getIdentifier(), action.getVersion()));
    }

    ActionInputSchemaValidator.validate(action.getInputSchema());
    stripIrrelevantConfig(action);
    action.setParentUniqueId(parentUniqueId);
    action.setAccountIdentifier(scopeInfo.getAccountIdentifier());
    return transactionHelper.performTransaction(() -> {
      Action saved = actionRepository.save(action);
      outboxService.save(new ActionCreateEvent(
          scopeInfo, toJson(saved), saved.getIdentifier(), saved.getVersion(), saved.getUniqueId()));
      return saved;
    });
  }

  @Override
  public Action getAction(ScopeInfo scopeInfo, String identifier, String version) {
    return findActionWithGlobalFallback(scopeInfo.getUniqueId(), identifier, version)
        .orElseThrow(()
                         -> new InvalidRequestException(
                             String.format("Action [%s] version [%s] not found", identifier, version)));
  }

  @Override
  public Action getPublishedAction(ScopeInfo scopeInfo, String identifier) {
    return actionRepository.findPublishedVersion(scopeInfo.getUniqueId(), identifier)
        .or(() -> actionRepository.findPublishedVersion(Action.GLOBAL_PARENT_UNIQUE_ID, identifier))
        .orElseThrow(
            () -> new InvalidRequestException(String.format("No published version found for Action [%s]", identifier)));
  }

  @Override
  public Page<Action> listActions(ScopeInfo scopeInfo, ActionStatus status, String category, String searchTerm,
      Integer page, Integer limit, String sort) {
    String orgIdentifier = scopeInfo.getOrgIdentifier(), projectIdentifier = scopeInfo.getProjectIdentifier(),
           accountIdentifier = scopeInfo.getAccountIdentifier();
    List<String> parentUniqueIds;
    if (!isEmpty(orgIdentifier)) {
      String scopeString = buildScopeString(orgIdentifier, projectIdentifier);
      CatalogScopeResolver.ScopeResolveResult scopeResult =
          catalogScopeResolver.resolve(accountIdentifier, scopeString);
      parentUniqueIds = scopeResult.getScopeInfos()
                            .stream()
                            .map(ScopeInfo::getUniqueId)
                            .collect(Collectors.toCollection(ArrayList::new));
    } else {
      parentUniqueIds = new ArrayList<>(2);
      parentUniqueIds.add(scopeInfo.getUniqueId());
    }
    if (!parentUniqueIds.contains(Action.GLOBAL_PARENT_UNIQUE_ID)) {
      parentUniqueIds.add(Action.GLOBAL_PARENT_UNIQUE_ID);
    }
    return actionRepository.findAll(
        accountIdentifier, parentUniqueIds, status, category, searchTerm, page, limit, sort);
  }

  @Override
  public Action updateAction(ScopeInfo scopeInfo, String identifier, String version, ActionUpdateRequest request) {
    String parentUniqueId = scopeInfo.getUniqueId();

    Action existing = actionRepository.findByParentUniqueIdAndIdentifierAndVersion(parentUniqueId, identifier, version)
                          .orElseThrow(()
                                           -> new InvalidRequestException(String.format(
                                               "Action [%s] version [%s] not found", identifier, version)));

    if (existing.getStatus() == ActionStatus.DEPRECATED) {
      throw new InvalidRequestException("Cannot modify a DEPRECATED action. Create a new version instead.");
    }

    if (existing.getStatus() == ActionStatus.PUBLISHED) {
      throw new InvalidRequestException("Cannot modify a PUBLISHED action. Deprecate it and create a new version.");
    }
    if (request.getInputSchema() != null) {
      if (!(request.getInputSchema() instanceof Map)) {
        throw new InvalidRequestException("inputSchema must be a JSON object");
      }
      @SuppressWarnings("unchecked") Map<String, Object> schema = (Map<String, Object>) request.getInputSchema();
      ActionInputSchemaValidator.validate(schema);
    }
    String oldJson = toJson(existing);
    ActionMapper.applyUpdate(request, existing);
    stripIrrelevantConfig(existing);
    return transactionHelper.performTransaction(() -> {
      Action saved = actionRepository.save(existing);
      outboxService.save(new ActionUpdateEvent(
          scopeInfo, oldJson, toJson(saved), saved.getIdentifier(), saved.getVersion(), saved.getUniqueId()));
      return saved;
    });
  }

  @Override
  public Action changeStatus(ScopeInfo scopeInfo, String identifier, String version, ActionStatus targetStatus) {
    String parentUniqueId = scopeInfo.getUniqueId();

    Action existing = actionRepository.findByParentUniqueIdAndIdentifierAndVersion(parentUniqueId, identifier, version)
                          .orElseThrow(()
                                           -> new InvalidRequestException(String.format(
                                               "Action [%s] version [%s] not found", identifier, version)));

    ActionLifecycleValidator.validateStatusTransition(existing.getStatus(), targetStatus);
    if (targetStatus == ActionStatus.PUBLISHED) {
      ActionLifecycleValidator.validateReadyToPublish(existing);
    }
    String oldJson = toJson(existing);

    existing.setStatus(targetStatus);
    if (targetStatus == ActionStatus.DEPRECATED) {
      existing.setDeprecatedAt(System.currentTimeMillis());
    }

    return transactionHelper.performTransaction(() -> {
      if (targetStatus == ActionStatus.PUBLISHED) {
        actionRepository.deprecateCurrentlyPublished(parentUniqueId, identifier);
      }
      Action saved;
      try {
        saved = actionRepository.save(existing);
      } catch (DuplicateKeyException e) {
        throw new InvalidRequestException(String.format(
            "Action [%s] cannot be published because another version was published concurrently. Retry the operation.",
            identifier));
      }
      outboxService.save(new ActionUpdateEvent(
          scopeInfo, oldJson, toJson(saved), saved.getIdentifier(), saved.getVersion(), saved.getUniqueId()));
      return saved;
    });
  }

  @Override
  public List<Action> listActionVersions(ScopeInfo scopeInfo, String identifier) {
    List<Action> versions = actionRepository.findByParentUniqueIdAndIdentifier(scopeInfo.getUniqueId(), identifier);
    if (versions == null) {
      versions = new ArrayList<>();
    }
    List<Action> globalVersions =
        actionRepository.findByParentUniqueIdAndIdentifier(Action.GLOBAL_PARENT_UNIQUE_ID, identifier);
    if (!isEmpty(globalVersions)) {
      if (!(versions instanceof ArrayList)) {
        versions = new ArrayList<>(versions);
      }
      versions.addAll(globalVersions);
    }
    return versions;
  }

  @Override
  public void deleteAction(ScopeInfo scopeInfo, String identifier, String version) {
    Action existing =
        actionRepository.findByParentUniqueIdAndIdentifierAndVersion(scopeInfo.getUniqueId(), identifier, version)
            .orElseThrow(()
                             -> new InvalidRequestException(
                                 String.format("Action [%s] version [%s] not found", identifier, version)));

    if (existing.getStatus() == ActionStatus.PUBLISHED) {
      throw new InvalidRequestException("Cannot delete a PUBLISHED action. Deprecate it first.");
    }

    String oldJson = toJson(existing);
    transactionHelper.performTransaction(() -> {
      actionRepository.delete(existing);
      outboxService.save(new ActionDeleteEvent(scopeInfo, oldJson, identifier, version, existing.getUniqueId()));
      return null;
    });
  }

  private Optional<Action> findActionWithGlobalFallback(String parentUniqueId, String identifier, String version) {
    return actionRepository.findByParentUniqueIdAndIdentifierAndVersion(parentUniqueId, identifier, version)
        .or(()
                -> actionRepository.findByParentUniqueIdAndIdentifierAndVersion(
                    Action.GLOBAL_PARENT_UNIQUE_ID, identifier, version));
  }

  private static String buildScopeString(String orgIdentifier, String projectIdentifier) {
    if (!isEmpty(orgIdentifier) && !isEmpty(projectIdentifier)) {
      return "account." + orgIdentifier + "." + projectIdentifier;
    }
    if (!isEmpty(orgIdentifier)) {
      return "account." + orgIdentifier + ".*";
    }
    return "account.*";
  }

  private static void stripIrrelevantConfig(Action action) {
    if (action.getType() == ActionType.HTTP) {
      action.setBuiltinConfig(null);
    } else if (action.getType() == ActionType.BUILTIN) {
      action.setHttpConfig(null);
    }
  }

  private String toJson(Action action) {
    try {
      return objectMapper.writeValueAsString(action);
    } catch (JsonProcessingException e) {
      log.warn(
          "Failed to serialize Action [{}] version [{}] for audit", action.getIdentifier(), action.getVersion(), e);
      return "{}";
    }
  }
}
