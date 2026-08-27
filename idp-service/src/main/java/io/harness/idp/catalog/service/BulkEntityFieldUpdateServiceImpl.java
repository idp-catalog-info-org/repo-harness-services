/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.service;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.USER;

import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.idp.catalog.entities.BulkFieldUpdateOperation;
import io.harness.idp.catalog.entities.BulkUpdatableField;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.OperationStatus;
import io.harness.idp.catalog.helpers.CatalogServiceHelper;
import io.harness.idp.catalog.helpers.IDPGitXHelper;
import io.harness.idp.catalog.helpers.KindServiceHelper;
import io.harness.idp.catalog.repositories.BulkFieldUpdateOperationRepository;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.catalog.utils.CatalogUtils;
import io.harness.idp.common.YamlUtils;
import io.harness.idp.events.producers.BulkFieldUpdateEventProducer;
import io.harness.spec.server.idp.v1.model.BulkEntityFieldUpdateError;
import io.harness.spec.server.idp.v1.model.BulkEntityFieldUpdateRequest;
import io.harness.spec.server.idp.v1.model.BulkEntityFieldUpdateSkipped;
import io.harness.spec.server.idp.v1.model.BulkFieldUpdateOperationResponse;
import io.harness.spec.server.idp.v1.model.BulkFieldUpdateProperty;
import io.harness.spec.server.idp.v1.model.BulkFieldUpdateSubmitResponse;
import io.harness.spec.server.idp.v1.model.EntityResponse;
import io.harness.spec.server.idp.v1.model.EntityUpdateRequest;
import io.harness.spec.server.idp.v1.model.GitDetails;
import io.harness.spec.server.idp.v1.model.GitUpdateDetails;
import io.harness.spec.server.idp.v1.model.ScorecardFilter;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.data.domain.Page;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
public class BulkEntityFieldUpdateServiceImpl implements BulkEntityFieldUpdateService {
  private static final String EDIT_PERMISSION = "edit";
  private static final String REASON_NO_PERMISSION = "NO_PERMISSION";
  private static final String REASON_VALIDATION_FAILED = "VALIDATION_FAILED";
  private static final String OWNER_KEY = "owner";

  private final CatalogEntityRepository catalogEntityRepository;
  private final CatalogServiceHelper catalogServiceHelper;
  private final KindServiceHelper kindServiceHelper;
  private final CatalogService catalogService;
  private final BulkFieldUpdateOperationRepository operationRepository;
  private final BulkFieldUpdateEventProducer eventProducer;
  private final IDPGitXHelper idpGitXHelper;

  @Override
  public BulkFieldUpdateSubmitResponse submit(BulkEntityFieldUpdateRequest request, String harnessAccount) {
    validateRequest(request);
    List<BulkFieldUpdateProperty> properties = request.getProperties();
    List<BulkFieldUpdateOperation.PropertyUpdate> resolvedProperties = new ArrayList<>();
    for (BulkFieldUpdateProperty prop : properties) {
      BulkUpdatableField field = BulkUpdatableField.fromKey(prop.getKey());
      String resolvedValue = resolveFieldValue(field, prop.getValue(), harnessAccount);
      resolvedProperties.add(BulkFieldUpdateOperation.PropertyUpdate.builder()
                                 .key(prop.getKey())
                                 .value(resolvedValue)
                                 .mode(prop.getMode() != null ? prop.getMode().toString() : "REPLACE")
                                 .build());
    }

    List<CatalogEntity> matchedEntities = selectEntities(request, harnessAccount);
    int matchedCount = matchedEntities.size();

    if (matchedCount == 0) {
      BulkFieldUpdateOperation operation = BulkFieldUpdateOperation.builder()
                                               .accountIdentifier(harnessAccount)
                                               .permittedEntityRefs(Collections.emptyList())
                                               .properties(resolvedProperties)
                                               .status(OperationStatus.SUCCESS)
                                               .matched(0)
                                               .permitted(0)
                                               .updated(0)
                                               .skipped(Collections.emptyList())
                                               .errors(Collections.emptyList())
                                               .retryCount(0)
                                               .createdAt(System.currentTimeMillis())
                                               .lastUpdatedAt(System.currentTimeMillis())
                                               .build();
      BulkFieldUpdateOperation savedOperation = operationRepository.save(operation);
      return new BulkFieldUpdateSubmitResponse()
          .operationId(savedOperation.getId())
          .status(OperationStatus.SUCCESS.name())
          .matched(0)
          .permitted(0);
    }

    List<BulkFieldUpdateOperation.SkippedItem> initialSkipped = new ArrayList<>();
    String resolvedOwnerValue = getResolvedOwnerValue(resolvedProperties);
    if (resolvedOwnerValue != null) {
      List<CatalogEntity> scopeValidEntities = new ArrayList<>();
      for (CatalogEntity entity : matchedEntities) {
        String entityScope = CatalogUtils.getScope(entity.getOrgIdentifier(), entity.getProjectIdentifier());
        try {
          catalogServiceHelper.validateOwnerScope(entityScope, resolvedOwnerValue);
          scopeValidEntities.add(entity);
        } catch (InvalidRequestException ex) {
          initialSkipped.add(BulkFieldUpdateOperation.SkippedItem.builder()
                                 .entityRef(CatalogUtils.entityRef(entity))
                                 .reason(REASON_VALIDATION_FAILED)
                                 .build());
        }
      }
      matchedEntities = scopeValidEntities;
      if (matchedEntities.isEmpty()) {
        throw new InvalidRequestException(String.format("No matched entities are within a valid scope for owner '%s'. "
                + "Entities must be at the owner's scope or a child scope.",
            resolvedOwnerValue));
      }
    }
    Set<String> allEntityRefs = matchedEntities.stream().map(CatalogUtils::entityRef).collect(Collectors.toSet());
    Set<String> allowedEntityRefs;
    if (canUseTeamEditPermission(properties, resolvedProperties.get(0).getValue())) {
      String ownerGroupRef = resolvedProperties.get(0).getValue();
      if (checkTeamEditPermission(harnessAccount, ownerGroupRef)) {
        allowedEntityRefs = allEntityRefs;
        log.info("User has idp_team_edit permission on {} for {} entities", ownerGroupRef, matchedCount);
      } else {
        allowedEntityRefs = checkEntityRefsRbacAndUpdateSkippedRefs(harnessAccount, initialSkipped, allEntityRefs);
      }
    } else {
      allowedEntityRefs = checkEntityRefsRbacAndUpdateSkippedRefs(harnessAccount, initialSkipped, allEntityRefs);
    }

    int permittedCount = allowedEntityRefs.size();
    List<String> permittedEntityRefs = new ArrayList<>(allowedEntityRefs);

    BulkFieldUpdateOperation operation = BulkFieldUpdateOperation.builder()
                                             .accountIdentifier(harnessAccount)
                                             .permittedEntityRefs(permittedEntityRefs)
                                             .properties(resolvedProperties)
                                             .status(OperationStatus.QUEUED)
                                             .matched(matchedCount)
                                             .permitted(permittedCount)
                                             .updated(0)
                                             .skipped(initialSkipped)
                                             .errors(Collections.emptyList())
                                             .retryCount(0)
                                             .createdAt(System.currentTimeMillis())
                                             .lastUpdatedAt(System.currentTimeMillis())
                                             .build();
    BulkFieldUpdateOperation savedOperation = operationRepository.save(operation);
    boolean published = eventProducer.publish(savedOperation.getId(), harnessAccount);
    if (!published) {
      log.warn("Failed to publish bulk field update event for operationId={}", savedOperation.getId());
    }

    log.info("Bulk field update submitted: operationId={}, matched={}, permitted={}", savedOperation.getId(),
        matchedCount, permittedCount);
    return new BulkFieldUpdateSubmitResponse()
        .operationId(savedOperation.getId())
        .status(OperationStatus.QUEUED.name())
        .matched(matchedCount)
        .permitted(permittedCount);
  }

  private Set<String> checkEntityRefsRbacAndUpdateSkippedRefs(
      String harnessAccount, List<BulkFieldUpdateOperation.SkippedItem> initialSkipped, Set<String> allEntityRefs) {
    Set<String> allowedEntityRefs;
    allowedEntityRefs = catalogServiceHelper.checkEntityRefsPermission(harnessAccount, allEntityRefs, EDIT_PERMISSION);
    if (isEmpty(allowedEntityRefs)) {
      throw new NGAccessDeniedException("Missing Catalog Edit Permission", USER, Collections.emptyList());
    }
    for (String entityRef : allEntityRefs) {
      if (!allowedEntityRefs.contains(entityRef)) {
        initialSkipped.add(
            BulkFieldUpdateOperation.SkippedItem.builder().entityRef(entityRef).reason(REASON_NO_PERMISSION).build());
      }
    }
    return allowedEntityRefs;
  }

  @Override
  public BulkFieldUpdateOperationResponse getOperation(String harnessAccount, String operationId) {
    Optional<BulkFieldUpdateOperation> opOpt =
        operationRepository.findByIdAndAccountIdentifier(operationId, harnessAccount);
    if (opOpt.isEmpty()) {
      throw new EntityNotFoundException(String.format("Bulk field update operation not found: %s", operationId));
    }

    BulkFieldUpdateOperation op = opOpt.get();
    List<BulkEntityFieldUpdateSkipped> skipped = op.getSkipped() != null
        ? op.getSkipped()
              .stream()
              .map(s -> new BulkEntityFieldUpdateSkipped().entityRef(s.getEntityRef()).reason(s.getReason()))
              .collect(Collectors.toList())
        : Collections.emptyList();

    List<BulkEntityFieldUpdateError> errors = op.getErrors() != null
        ? op.getErrors()
              .stream()
              .map(e -> new BulkEntityFieldUpdateError().entityRef(e.getEntityRef()).errorMessage(e.getErrorMessage()))
              .collect(Collectors.toList())
        : Collections.emptyList();

    return new BulkFieldUpdateOperationResponse()
        .operationId(op.getId())
        .status(op.getStatus().name())
        .matched(op.getMatched())
        .permitted(op.getPermitted())
        .updated(op.getUpdated())
        .skipped(skipped)
        .errors(errors)
        .errorMessage(op.getErrorMessage());
  }

  @Override
  public void execute(String operationId) {
    Optional<BulkFieldUpdateOperation> opOpt = operationRepository.findById(operationId);
    if (opOpt.isEmpty()) {
      log.error("Execute called for non-existent operation: {}", operationId);
      return;
    }

    BulkFieldUpdateOperation op = opOpt.get();
    log.info("Executing bulk field update: operationId={}, permitted={}, properties={}", operationId, op.getPermitted(),
        op.getProperties().size());

    List<BulkFieldUpdateOperation.ErrorItem> errors = new ArrayList<>();
    int updatedCount = 0;

    for (String entityRef : op.getPermittedEntityRefs()) {
      try {
        Triple<String, String, String> kindScopeIdentifier = catalogServiceHelper.getKindScopeIdentifier(entityRef);
        String scope = kindScopeIdentifier.getMiddle();
        String[] scopeSplit = scope.split("\\.");
        String orgIdentifier = scopeSplit.length >= 2 ? scopeSplit[1] : null;
        String projectIdentifier = scopeSplit.length == 3 ? scopeSplit[2] : null;
        EntityResponse entityResponse = catalogService.getEntity(
            op.getAccountIdentifier(), orgIdentifier, projectIdentifier, entityRef, false, false, true, false);
        Map<String, Object> yamlMap = YamlUtils.loadYamlStringAsMap(entityResponse.getYaml());
        for (BulkFieldUpdateOperation.PropertyUpdate propUpdate : op.getProperties()) {
          BulkUpdatableField field = BulkUpdatableField.fromKey(propUpdate.getKey());
          if (field == BulkUpdatableField.OWNER) {
            yamlMap.put("owner", propUpdate.getValue());
          }
          // Future: add other fields here
        }
        String updatedYaml = YamlUtils.yamlObject().dump(yamlMap);
        GitDetails gitDetails = entityResponse.getGitDetails();
        GitUpdateDetails gitUpdateDetails = new GitUpdateDetails();
        if (gitDetails != null) {
          gitUpdateDetails.setStoreType(GitUpdateDetails.StoreTypeEnum.valueOf(gitDetails.getStoreType().name()));
          gitUpdateDetails.setRepoName(gitDetails.getRepoName());
          gitUpdateDetails.setLastObjectId(gitDetails.getObjectId());
          gitUpdateDetails.setLastCommitId(gitDetails.getCommitId());
          gitUpdateDetails.setIsHarnessCodeRepo(gitDetails.isIsHarnessCodeRepo());
          gitUpdateDetails.setFilePath(gitDetails.getFilePath());
          gitUpdateDetails.setConnectorRef(gitDetails.getConnectorRef());
          gitUpdateDetails.setCommitMessage(gitDetails.getCommitMessage());
          gitUpdateDetails.setBranchName(gitDetails.getBranchName());
          gitUpdateDetails.setBaseBranch(gitDetails.getBaseBranch());
          GitAwareContextHelper.populateGitDetails(idpGitXHelper.populateGitUpdateDetails(gitUpdateDetails));
        }
        EntityUpdateRequest updateRequest = new EntityUpdateRequest().yaml(updatedYaml);
        catalogService.updateEntity(op.getAccountIdentifier(), entityResponse.getOrgIdentifier(),
            entityResponse.getProjectIdentifier(), entityRef, updateRequest, false, true, true, false);
        updatedCount++;
      } catch (Exception ex) {
        log.warn("Failed to update entity={} in operationId={}. Error={}", entityRef, operationId, ex.getMessage(), ex);
        errors.add(
            BulkFieldUpdateOperation.ErrorItem.builder().entityRef(entityRef).errorMessage(ex.getMessage()).build());
      } finally {
        GitAwareContextHelper.populateGitDetails(GitEntityInfo.builder().build());
      }
    }
    op.setUpdated(updatedCount);
    op.setErrors(errors);
    op.setStatus(errors.isEmpty() ? OperationStatus.SUCCESS
                                  : (updatedCount == 0 ? OperationStatus.FAILED : OperationStatus.PARTIAL_SUCCESS));
    op.setLastUpdatedAt(System.currentTimeMillis());
    operationRepository.save(op);
    log.info("Bulk field update execution completed: operationId={}, updated={}, errors={}, status={}", operationId,
        updatedCount, errors.size(), op.getStatus());
  }

  private void validateRequest(BulkEntityFieldUpdateRequest request) {
    boolean hasFilter = request.getFilter() != null;
    boolean hasEntityRefs = request.getEntityRefs() != null && !request.getEntityRefs().isEmpty();

    if ((hasFilter && hasEntityRefs) || (!hasFilter && !hasEntityRefs)) {
      throw new InvalidRequestException("Exactly one of 'filter' or 'entityRefs' must be provided");
    }

    List<BulkFieldUpdateProperty> properties = request.getProperties();
    if (properties == null || properties.isEmpty()) {
      throw new InvalidRequestException("Field 'properties' is required and must not be empty");
    }

    for (BulkFieldUpdateProperty prop : properties) {
      if (isEmpty(prop.getKey())) {
        throw new InvalidRequestException("Each property must have a 'key'");
      }
      try {
        BulkUpdatableField.fromKey(prop.getKey());
      } catch (IllegalArgumentException e) {
        throw new InvalidRequestException(e.getMessage());
      }

      if (prop.getMode() != null && prop.getMode() == BulkFieldUpdateProperty.ModeEnum.APPEND) {
        throw new InvalidRequestException("APPEND mode is not yet supported");
      }
    }
  }

  private List<CatalogEntity> selectEntities(BulkEntityFieldUpdateRequest request, String harnessAccount) {
    Set<CatalogEntity> entities = new HashSet<>();

    if (request.getFilter() != null) {
      ScorecardFilter filter = request.getFilter();
      String kind = "template".equalsIgnoreCase(filter.getKind()) ? "workflow" : filter.getKind();
      kindServiceHelper.validateKindIfExist(harnessAccount, kind);

      String type = filter.getType();
      String owner = filter.getOwners() != null ? String.join(",", filter.getOwners()) : null;
      String tag = filter.getTags() != null ? String.join(",", filter.getTags()) : null;
      String lifecycle = filter.getLifecycle() != null ? String.join(",", filter.getLifecycle()) : null;
      String scopes = catalogServiceHelper.getAllScopes();
      if (filter.getScopes() != null && !filter.getScopes().isEmpty()) {
        scopes = String.join(",", filter.getScopes());
      }

      Pair<List<ScopeInfo>, Map<String, List<ScopeInfo>>> scopeInfosPair =
          catalogServiceHelper.getScopeInfosBasedOnScopesAndEntityRefs(harnessAccount, scopes, null);

      Page<CatalogEntity> catalogEntitiesPaged;
      int page = 0;
      do {
        catalogEntitiesPaged = catalogEntityRepository.getEntities(harnessAccount, scopeInfosPair.getLeft(), page, 1000,
            null, null, null, null, kind, type, owner, lifecycle, tag, null, null);
        if (!isEmpty(catalogEntitiesPaged) && !isEmpty(catalogEntitiesPaged.getContent())) {
          entities.addAll(catalogEntitiesPaged.getContent());
        }
        page++;
      } while (!isEmpty(catalogEntitiesPaged) && catalogEntitiesPaged.getTotalPages() > page);

    } else {
      String joinedRefs = String.join(",", request.getEntityRefs());
      Pair<List<ScopeInfo>, Map<String, List<ScopeInfo>>> scopeInfosPair =
          catalogServiceHelper.getScopeInfosBasedOnScopesAndEntityRefs(
              harnessAccount, catalogServiceHelper.getAllScopes(), joinedRefs);

      Page<CatalogEntity> catalogEntitiesPaged = catalogEntityRepository.getEntities(harnessAccount,
          scopeInfosPair.getLeft(), null, -1, null, null, null, joinedRefs, null, null, null, null, null, null, null);
      if (!isEmpty(catalogEntitiesPaged) && !isEmpty(catalogEntitiesPaged.getContent())) {
        entities.addAll(catalogEntitiesPaged.getContent());
      }
    }

    return new ArrayList<>(entities);
  }

  private String resolveFieldValue(BulkUpdatableField field, String value, String harnessAccount) {
    if (field == BulkUpdatableField.OWNER) {
      return catalogServiceHelper.resolveOwner(harnessAccount, value);
    }
    return value;
  }

  private boolean canUseTeamEditPermission(List<BulkFieldUpdateProperty> properties, String resolvedValue) {
    if (properties.size() != 1) {
      return false;
    }
    BulkFieldUpdateProperty prop = properties.get(0);
    if (!OWNER_KEY.equals(prop.getKey())) {
      return false;
    }
    return resolvedValue != null && resolvedValue.matches("^group:account([.].*)?/.*$");
  }

  private String getResolvedOwnerValue(List<BulkFieldUpdateOperation.PropertyUpdate> resolvedProperties) {
    return resolvedProperties.stream()
        .filter(p -> OWNER_KEY.equals(p.getKey()))
        .map(BulkFieldUpdateOperation.PropertyUpdate::getValue)
        .findFirst()
        .orElse(null);
  }

  private boolean checkTeamEditPermission(String harnessAccount, String ownerGroupRef) {
    Set<String> allowedGroups =
        catalogServiceHelper.checkEntityRefsPermission(harnessAccount, Set.of(ownerGroupRef), EDIT_PERMISSION);
    return !isEmpty(allowedGroups);
  }
}
