/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.idp.groups.service;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.catalog.utils.Constants.WORKFLOW_KIND;
import static io.harness.outbox.OutboxSDKConstants.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.backstage.beans.BackstageCatalogEntityTypes;
import io.harness.idp.backstage.entities.BackstageCatalogEntity;
import io.harness.idp.backstage.service.BackstageService;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.catalog.utils.CatalogUtils;
import io.harness.idp.common.CommonUtils;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.groups.entities.GroupEntity;
import io.harness.idp.groups.events.GroupCreateEvent;
import io.harness.idp.groups.events.GroupDeleteEvent;
import io.harness.idp.groups.events.GroupUpdateEvent;
import io.harness.idp.groups.mappers.GroupsMapper;
import io.harness.idp.groups.mappers.WorkflowsMapper;
import io.harness.idp.groups.repositories.GroupsRepository;
import io.harness.idp.namespace.beans.entity.NamespaceEntity;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.outbox.api.OutboxService;
import io.harness.remote.client.NGRestUtils;
import io.harness.scopeinfoclient.remote.ScopeInfoClient;
import io.harness.spec.server.idp.v1.model.GroupRequest;
import io.harness.spec.server.idp.v1.model.GroupResponse;
import io.harness.spec.server.idp.v1.model.WorkflowsInfo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@Slf4j
public class GroupsServiceImpl implements GroupsService {
  @Inject GroupsRepository groupRepository;
  @Inject @Named(OUTBOX_TRANSACTION_TEMPLATE) private TransactionTemplate transactionTemplate;
  @Inject BackstageService backstageService;
  @Inject private OutboxService outboxService;
  @Inject CatalogEntityRepository catalogEntityRepository;
  @Inject ScopeInfoClient scopeInfoClient;
  @Inject IdpCommonService idpCommonService;
  @Inject NamespaceService namespaceService;

  private static final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_RETRY_POLICY;

  private static final String NO_GROUP_PRESENT_ERROR_MESSAGE = "No groups are present for account - %s";
  private static final String GROUP_NOT_FOUND_WITH_IDENTIFIER_ERROR_MESSAGE =
      "Group with identifier - %s not found for account - %s";
  private static final String NO_GROUPS_TO_UPDATE_ERROR_MESSAGE = "No group to update for account - %s";
  private static final ObjectMapper objectMapper = NG_DEFAULT_OBJECT_MAPPER;
  private static final String DUPLICATE_KEY_EXCEPTION_ERROR_MESSAGE =
      "Group with identifier - %s already present in account - %s";

  private static final String INVALID_UPDATE_CASE_ERROR_MESSAGE =
      "Groups cannot be updated as few groups with identifiers - %s are not saved yet";

  private static final String INVALID_UPDATE_CASE_ERROR_MESSAGE_IDPV2 =
      "Groups cannot be updated as few groups with identifiers - %s in project - %s, org - %s are not saved yet";

  private static final String INVALID_UPDATE_CASE_NO_ORDER_PROVIDED_ERROR_MESSAGE =
      "Groups cannot be updated as few groups with identifiers - %s are not having order value";

  @Override
  public List<GroupResponse> getAllGroupsForAccount(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    throwExceptionIfDefaultToAccountMigrationNotCompleted(accountIdentifier);

    Boolean isIDP2_0Enabled = true;

    throwExceptionIfIDPV2NotEnabled(isIDP2_0Enabled, orgIdentifier, projectIdentifier);

    ScopeInfo scopeInfo =
        NGRestUtils.getResponse(scopeInfoClient.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier));

    List<GroupEntity> groupsEntities = groupRepository.findAllByParentUniqueId(scopeInfo.getUniqueId());

    List<Object> savedTemplates = getSavedTemplates(scopeInfo, isIDP2_0Enabled);

    if (isEmpty(groupsEntities)) {
      return new ArrayList<>();
    }

    groupsEntities = sortGroupsEntityBasedOnOrder(groupsEntities);

    List<GroupResponse> groupResponses = new ArrayList<>();
    List<GroupEntity> toUpdateBack = new ArrayList<>();

    for (GroupEntity groupEntity : groupsEntities) {
      groupEntity = updateOrRemoveWorkflowsIfAny(groupEntity, savedTemplates, isIDP2_0Enabled);
      groupResponses.add(getGroupResponseForGroupEntity(groupEntity, savedTemplates, isIDP2_0Enabled));

      toUpdateBack.add(groupEntity);
    }

    // Updating back the entities in DB
    groupRepository.saveAll(toUpdateBack);

    return groupResponses;
  }

  @Override
  public void deleteGroup(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String groupIdentifier) {
    throwExceptionIfDefaultToAccountMigrationNotCompleted(accountIdentifier);

    Boolean isIDP2_0Enabled = true;

    throwExceptionIfIDPV2NotEnabled(isIDP2_0Enabled, orgIdentifier, projectIdentifier);

    ScopeInfo scopeInfo =
        NGRestUtils.getResponse(scopeInfoClient.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier));

    List<GroupEntity> storedEntities = groupRepository.findAllByParentUniqueId(scopeInfo.getUniqueId());

    Optional<GroupEntity> groupEntity =
        storedEntities.stream().filter(entity -> entity.getIdentifier().equals(groupIdentifier)).findFirst();

    if (isEmpty(storedEntities) || !groupEntity.isPresent()) {
      return;
    }

    GroupEntity entityToRemove = groupEntity.get();
    List<String> savedWorkflowIds = new ArrayList<>();
    if (!isEmpty(entityToRemove.getWorkflows())) {
      savedWorkflowIds = entityToRemove.getWorkflows();
    }

    List<Object> savedTemplates;
    final GroupResponse[] groupResponseWrapper = new GroupResponse[1];

    if (isIDP2_0Enabled) {
      List<String> savedIDPWorkflowsIdentifiers = getIDPCatalogIdentifiersOfWorkflows(savedWorkflowIds);
      savedTemplates = new ArrayList<>(catalogEntityRepository.findAllByParentUniqueIdAndKindAndIdentifierIn(
          scopeInfo.getUniqueId(), WORKFLOW_KIND, savedIDPWorkflowsIdentifiers));
      groupResponseWrapper[0] =
          getGroupResponseForGroupEntity(updateOrRemoveWorkflowsIfAny(entityToRemove, savedTemplates, isIDP2_0Enabled),
              savedTemplates, isIDP2_0Enabled);
    } else {
      savedTemplates = new ArrayList<>(
          backstageService.findAllByAccountIdentifierAndEntityRefs(accountIdentifier, savedWorkflowIds));
      groupResponseWrapper[0] =
          getGroupResponseForGroupEntity(updateOrRemoveWorkflowsIfAny(entityToRemove, savedTemplates, isIDP2_0Enabled),
              savedTemplates, isIDP2_0Enabled);
    }

    int removedOrder = entityToRemove.getOrder();
    // Update the order fields of the remaining entities after the removed position
    List<GroupEntity> toUpdateBackEntities =
        storedEntities.stream().filter(entity -> !entity.equals(entityToRemove)).collect(Collectors.toList());

    toUpdateBackEntities.stream()
        .filter(entity -> entity.getOrder() > removedOrder)
        .forEach(entity -> entity.setOrder(entity.getOrder() - 1));

    updateGroupEntities(accountIdentifier, toUpdateBackEntities);

    Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      outboxService.save(new GroupDeleteEvent(groupResponseWrapper[0].getGroup(), accountIdentifier));
      groupRepository.delete(entityToRemove);
      return true;
    }));
  }

  @Override
  public GroupResponse getGroup(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String groupIdentifier) {
    throwExceptionIfDefaultToAccountMigrationNotCompleted(accountIdentifier);

    Boolean isIDP2_0Enabled = true;

    throwExceptionIfIDPV2NotEnabled(isIDP2_0Enabled, orgIdentifier, projectIdentifier);

    ScopeInfo scopeInfo =
        NGRestUtils.getResponse(scopeInfoClient.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier));

    Optional<GroupEntity> groupEntity =
        groupRepository.findByParentUniqueIdAndIdentifier(scopeInfo.getUniqueId(), groupIdentifier);

    if (groupEntity.isEmpty()) {
      throw new NotFoundException(
          String.format(GROUP_NOT_FOUND_WITH_IDENTIFIER_ERROR_MESSAGE, groupIdentifier, accountIdentifier));
    }

    List<Object> savedTemplates = getSavedTemplates(scopeInfo, isIDP2_0Enabled);

    GroupEntity finalGroupEntity = updateOrRemoveWorkflowsIfAny(groupEntity.get(), savedTemplates, isIDP2_0Enabled);

    groupRepository.save(finalGroupEntity);

    return getGroupResponseForGroupEntity(finalGroupEntity, savedTemplates, isIDP2_0Enabled);
  }

  @Override
  public GroupResponse saveGroup(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, GroupRequest groupRequest) {
    throwExceptionIfDefaultToAccountMigrationNotCompleted(accountIdentifier);

    Boolean isIDP2_0Enabled = true;

    throwExceptionIfIDPV2NotEnabled(isIDP2_0Enabled, orgIdentifier, projectIdentifier);

    ScopeInfo scopeInfo =
        NGRestUtils.getResponse(scopeInfoClient.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier));

    GroupEntity newGroupsEntity = GroupsMapper.fromDTO(scopeInfo, groupRequest.getGroup());

    List<Object> savedTemplates = getSavedTemplates(scopeInfo, isIDP2_0Enabled);

    newGroupsEntity.setOrder(getMaxGroupOrderValueForAccount(scopeInfo) + 1);

    if (!isEmpty(newGroupsEntity.getWorkflows())) {
      newGroupsEntity = updateOrRemoveWorkflowsIfAny(newGroupsEntity, savedTemplates, isIDP2_0Enabled);
    }

    GroupResponse groupResponse = new GroupResponse();
    final GroupResponse[] groupResponseWrapper = {groupResponse};

    groupResponseWrapper[0] = getGroupResponseForGroupEntity(newGroupsEntity, savedTemplates, isIDP2_0Enabled);

    final GroupEntity[] groupsEntityWrapper = {newGroupsEntity};

    return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      try {
        groupRepository.save(groupsEntityWrapper[0]);
      } catch (DuplicateKeyException e) {
        log.info(e.getMessage());
        throw new InvalidRequestException(String.format(
            DUPLICATE_KEY_EXCEPTION_ERROR_MESSAGE, groupsEntityWrapper[0].getIdentifier(), accountIdentifier));
      }
      outboxService.save(new GroupCreateEvent(groupResponseWrapper[0].getGroup(), accountIdentifier));
      return groupResponseWrapper[0];
    }));
  }

  @Override
  public List<GroupResponse> updateGroup(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, List<GroupRequest> groupRequests) {
    throwExceptionIfDefaultToAccountMigrationNotCompleted(accountIdentifier);

    if (isEmpty(groupRequests)) {
      throw new InvalidRequestException(String.format(NO_GROUPS_TO_UPDATE_ERROR_MESSAGE, accountIdentifier));
    }
    Boolean isIDP2_0Enabled = true;

    throwExceptionIfIDPV2NotEnabled(isIDP2_0Enabled, orgIdentifier, projectIdentifier);

    /* We are doing this kind of update because we are maintaining order field for drag and drop handling
    so here both the purpose of drag and drop as well as updating single group will be solved only UI will have
    to send the Request body as array list */

    ScopeInfo scopeInfo =
        NGRestUtils.getResponse(scopeInfoClient.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier));

    List<GroupEntity> storedEntities = groupRepository.findAllByParentUniqueId(scopeInfo.getUniqueId());

    List<BackstageCatalogEntity> savedTemplates = new ArrayList<>();
    final List<Object>[] savedTemplatesWrapper = new List[] {savedTemplates};

    savedTemplatesWrapper[0] = getSavedTemplates(scopeInfo, isIDP2_0Enabled);

    List<GroupEntity> groupsEntitiesToUpdate = new ArrayList<>();

    Map<String, String> storedIdentifierToIdMap =
        storedEntities.stream().collect(Collectors.toMap(GroupEntity::getIdentifier, GroupEntity::getId));

    Map<String, String> storedIdentifierToUniqueIdMap =
        storedEntities.stream().collect(Collectors.toMap(GroupEntity::getIdentifier, GroupEntity::getUniqueId));

    List<String> saveCaseIdentifiers = new ArrayList<>();
    List<String> noOrderProvidedCaseIdentifiers = new ArrayList<>();

    for (GroupRequest groupRequest : groupRequests) {
      GroupEntity groupEntity = GroupsMapper.fromDTO(scopeInfo, groupRequest.getGroup());
      String storedId = storedIdentifierToIdMap.get(groupRequest.getGroup().getIdentifier());
      groupEntity.setId(storedId);
      groupEntity.setUniqueId(storedIdentifierToUniqueIdMap.get(groupRequest.getGroup().getIdentifier()));

      if (!isEmpty(groupEntity.getWorkflows())) {
        updateOrRemoveWorkflowsIfAny(groupEntity, savedTemplatesWrapper[0], isIDP2_0Enabled);
      }

      groupsEntitiesToUpdate.add(groupEntity);

      if (isEmpty(storedId)) {
        saveCaseIdentifiers.add(groupRequest.getGroup().getIdentifier());
      }

      if (groupRequest.getGroup().getOrder() == null) {
        noOrderProvidedCaseIdentifiers.add(groupRequest.getGroup().getIdentifier());
      }
    }

    if (!isEmpty(saveCaseIdentifiers)) {
      if (isIDP2_0Enabled) {
        throw new InvalidRequestException(String.format(
            INVALID_UPDATE_CASE_ERROR_MESSAGE_IDPV2, saveCaseIdentifiers, projectIdentifier, orgIdentifier));
      } else {
        throw new InvalidRequestException(String.format(INVALID_UPDATE_CASE_ERROR_MESSAGE, saveCaseIdentifiers));
      }
    }

    if (!isEmpty(noOrderProvidedCaseIdentifiers)) {
      throw new InvalidRequestException(
          String.format(INVALID_UPDATE_CASE_NO_ORDER_PROVIDED_ERROR_MESSAGE, noOrderProvidedCaseIdentifiers));
    }

    return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      List<GroupEntity> updateGroupEntities = updateGroupEntities(accountIdentifier, groupsEntitiesToUpdate);

      updateGroupEntities = sortGroupsEntityBasedOnOrder(updateGroupEntities);

      List<GroupResponse> groupResponses = new ArrayList<>();
      for (GroupEntity groupEntity : updateGroupEntities) {
        groupResponses.add(getGroupResponseForGroupEntity(groupEntity, savedTemplatesWrapper[0], isIDP2_0Enabled));
      }

      if (groupRequests.size() == 1) {
        Optional<GroupEntity> oldGroupEntity =
            storedEntities.stream()
                .filter(group -> groupRequests.get(0).getGroup().getIdentifier().equals(group.getIdentifier()))
                .findFirst();
        GroupResponse oldGroupResponse;

        oldGroupResponse =
            getGroupResponseForGroupEntity(oldGroupEntity.get(), savedTemplatesWrapper[0], isIDP2_0Enabled);

        outboxService.save(
            new GroupUpdateEvent(groupResponses.get(0).getGroup(), oldGroupResponse.getGroup(), accountIdentifier));
      }
      return groupResponses;
    }));
  }

  @Override
  public Page<BackstageCatalogEntity> getWorkflowsInfo(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, int page, int limit) {
    return backstageService.findAllByAccountIdentifierAndKind(
        accountIdentifier, BackstageCatalogEntityTypes.TEMPLATE.kind, page, limit);
  }

  @Override
  public Page<CatalogEntity> getCatalogEntitiesForWorkflowsInfo(String accountIdentifier, int page, int limit,
      String orgIdentifier, String projectIdentifier, String searchTerm) {
    ScopeInfo scopeInfo =
        NGRestUtils.getResponse(scopeInfoClient.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier));

    Criteria criteria = new Criteria();
    criteria.and(CatalogEntity.CatalogKeys.parentUniqueId)
        .is(scopeInfo.getUniqueId())
        .and(CatalogEntity.CatalogKeys.kind)
        .is(WORKFLOW_KIND);

    if (!isEmpty(searchTerm)) {
      criteria.andOperator(Criteria.where(CatalogEntity.CatalogKeys.name).regex(".*" + searchTerm + ".*", "i"));
    }
    return catalogEntityRepository.findAll(criteria, PageRequest.of(page, limit));
  }

  @Override
  public void modifyEntityIdentifierForIdpV2(String accountIdentifier, Set<String> conflictedEntityUids) {
    List<GroupEntity> groupEntities = groupRepository.findAllByAccountIdentifier(accountIdentifier);
    Set<String> conflictedEntityUidsSafe = conflictedEntityUids != null ? conflictedEntityUids : Collections.emptySet();

    if (isEmpty(groupEntities)) {
      return;
    }

    log.info("Totally {} records present in Group collection for account {}", groupEntities.size(), accountIdentifier);

    List<GroupEntity> entitiesToUpdate = new ArrayList<>();

    for (GroupEntity groupEntity : groupEntities) {
      List<String> entityUids = groupEntity.getWorkflows();

      if (!isEmpty(entityUids)) {
        List<String> updatedEntityUids = entityUids.stream()
                                             .map(entityUid -> transformEntityUid(entityUid, conflictedEntityUidsSafe))
                                             .collect(Collectors.toList());

        if (!isEmpty(updatedEntityUids)) {
          groupEntity.setWorkflows(updatedEntityUids);
          entitiesToUpdate.add(groupEntity);
        }
      }
    }

    if (!isEmpty(entitiesToUpdate)) {
      groupRepository.saveAll(entitiesToUpdate);
    }

    log.info("Migration for group catalog dependents completed for account - {}", accountIdentifier);
  }

  @Override
  public void modifyScopeForEntityIdentifier(List<GroupEntity> groupEntities, String accountIdentifier,
      String existingEntityIdentifier, String modifiedEntityIdentifier) {
    List<GroupEntity> entitiesToUpdate = new ArrayList<>();
    groupEntities.forEach(groupEntity -> {
      List<String> workflows = groupEntity.getWorkflows();
      if (!isEmpty(workflows)) {
        int index = workflows.indexOf(existingEntityIdentifier);
        if (index != -1) {
          List<String> mutableWorkflows = new ArrayList<>(workflows);
          mutableWorkflows.set(index, modifiedEntityIdentifier);
          groupEntity.setWorkflows(mutableWorkflows);
          entitiesToUpdate.add(groupEntity);
        }
      }
    });
    if (!isEmpty(entitiesToUpdate)) {
      groupRepository.saveAll(entitiesToUpdate);
      log.info("Totally {} records modified in Groups collection for IDP 2.0 MigrationAPI Operation for account {}, "
              + "identifier {}",
          entitiesToUpdate.size(), accountIdentifier, existingEntityIdentifier);
    }
  }

  @Override
  public void addUniqueIdAndParentUniqueIdInfo(String accountIdentifier) {
    List<GroupEntity> groupEntities = groupRepository.findAllByAccountIdentifier(accountIdentifier);
    ScopeInfo scopeInfo = NGRestUtils.getResponse(scopeInfoClient.getScopeInfo(accountIdentifier, null, null));
    for (GroupEntity groupEntity : groupEntities) {
      groupEntity.setUniqueId(UUIDGenerator.generateUuid());
      groupEntity.setParentUniqueId(scopeInfo.getUniqueId());
    }
    groupRepository.saveAll(groupEntities);
  }

  private String transformEntityUid(String entityUid, Set<String> conflictedEntityUidsSafe) {
    String[] parts = entityUid.split("[./]");

    if (parts.length == 3) {
      String first = parts[0].toLowerCase();
      String kind = parts[1].toLowerCase();
      String name = parts[2].toLowerCase();

      if (entityUid.contains(":")) {
        // Convert `string:string:string` -> `account/kind/name`
        return "account/" + kind + "/" + name;
      } else if (!first.equals("account") && !first.contains(".")) {
        // Convert `namespace/kind/name` -> `account/kind/name`, handling conflicts
        String modifiedEntityUid = "account/" + kind + "/" + name;
        String modifiedEntityUidForConflict = "account/" + kind + "/" + first + "_" + name;

        return conflictedEntityUidsSafe.contains(modifiedEntityUidForConflict) ? modifiedEntityUidForConflict
                                                                               : modifiedEntityUid;
      }
    }

    return entityUid;
  }

  private GroupEntity updateOrRemoveWorkflowsIfAny(
      GroupEntity groupEntity, List<Object> savedIDPWorkflows, Boolean isIDP2_0Enabled) {
    if (isEmpty(savedIDPWorkflows)) {
      return groupEntity;
    }

    if (isIDP2_0Enabled) {
      modifyWorkflowsIdBasedOnIDPV2(groupEntity);
      List<CatalogEntity> catalogEntities = castObjetListToCatalogEntities(savedIDPWorkflows);
      return updateOrRemoveWorkflowsIfAnyForIdpCatalogs(groupEntity, catalogEntities);
    }
    List<BackstageCatalogEntity> backstageCatalogEntities = castObjetListToBackstageCatalogEntities(savedIDPWorkflows);
    return updateOrRemoveWorkflowsIfAnyForBackstageCatalog(groupEntity, backstageCatalogEntities);
  }

  private GroupEntity updateOrRemoveWorkflowsIfAnyForBackstageCatalog(
      GroupEntity groupEntity, List<BackstageCatalogEntity> backstageCatalogEntities) {
    if (isEmpty(groupEntity.getWorkflows())) {
      return groupEntity;
    }

    List<String> workflowsIdsToUpdate =
        groupEntity.getWorkflows()
            .stream()
            .map(workFlowIds
                -> backstageCatalogEntities.stream()
                       .filter(backstageCatalogEntity
                           -> backstageCatalogEntity.getEntityUid().equalsIgnoreCase(workFlowIds))
                       .findFirst()
                       .map(BackstageCatalogEntity::getEntityUid)
                       .orElse(null))
            .filter(Objects::nonNull)
            .toList();

    groupEntity.setWorkflows(workflowsIdsToUpdate);
    return groupEntity;
  }

  private GroupEntity updateOrRemoveWorkflowsIfAnyForIdpCatalogs(
      GroupEntity groupEntity, List<CatalogEntity> catalogEntities) {
    if (isEmpty(groupEntity.getWorkflows())) {
      return groupEntity;
    }

    List<String> workflowsIdsToUpdate =
        groupEntity.getWorkflows()
            .stream()
            .map(workFlowIds
                -> catalogEntities.stream()
                       .filter(catalogEntity
                           -> CatalogUtils
                                  .getIdentifierForWorkflowsInGroup(
                                      CatalogUtils.getFullyQualifiedScopeRef(catalogEntity.getScope(),
                                          catalogEntity.getOrgIdentifier(), catalogEntity.getProjectIdentifier()),
                                      "template", catalogEntity.getIdentifier())
                                  .equalsIgnoreCase(workFlowIds))
                       .findFirst()
                       .map(catalogEntity
                           -> CatalogUtils.getIdentifierForWorkflowsInGroup(
                               CatalogUtils.getFullyQualifiedScopeRef(catalogEntity.getScope(),
                                   catalogEntity.getOrgIdentifier(), catalogEntity.getProjectIdentifier()),
                               "template", catalogEntity.getIdentifier()))
                       .orElse(null))
            .filter(Objects::nonNull)
            .toList();

    groupEntity.setWorkflows(workflowsIdsToUpdate);
    return groupEntity;
  }

  private GroupResponse getGroupResponseForGroupEntity(
      GroupEntity groupEntity, List<Object> savedTemplates, Boolean isIDP2_0Enabled) {
    if (isIDP2_0Enabled) {
      List<CatalogEntity> catalogEntities = castObjetListToCatalogEntities(savedTemplates);
      String orgName = null;
      String projectName = null;

      if (!isEmpty(groupEntity.getOrgIdentifier())) {
        orgName = idpCommonService.getOrgName(groupEntity.getAccountIdentifier(), groupEntity.getOrgIdentifier());
      }
      if (!isEmpty(groupEntity.getOrgIdentifier()) && !isEmpty(groupEntity.getProjectIdentifier())) {
        projectName = idpCommonService.getProjectName(
            groupEntity.getAccountIdentifier(), groupEntity.getOrgIdentifier(), groupEntity.getProjectIdentifier());
      }
      return GroupsMapper.toResponse(groupEntity,
          getWorkflowsForGroupEntity(groupEntity, getEntityUidWorkflowsMappingForIDPCatalogs(catalogEntities)), orgName,
          projectName);
    }
    List<BackstageCatalogEntity> backstageCatalogEntities = castObjetListToBackstageCatalogEntities(savedTemplates);
    return GroupsMapper.toResponse(groupEntity,
        getWorkflowsForGroupEntity(
            groupEntity, getEntityUidWorkflowsMappingForBackstageCatalog(backstageCatalogEntities)),
        null, null);
  }

  private List<WorkflowsInfo> getWorkflowsForGroupEntity(
      GroupEntity groupEntity, Map<String, WorkflowsInfo> entityUidWorkflowsMapping) {
    List<WorkflowsInfo> workflowsInfoList = new ArrayList<>();
    if (isEmpty(groupEntity.getWorkflows())) {
      return workflowsInfoList;
    }
    for (String workflowsEntityUId : groupEntity.getWorkflows()) {
      workflowsInfoList.add(entityUidWorkflowsMapping.get(workflowsEntityUId));
    }
    return workflowsInfoList;
  }

  private Map<String, WorkflowsInfo> getEntityUidWorkflowsMapping(
      List<Object> savedTemplates, Boolean isIDP2_0Enabled) {
    if (isIDP2_0Enabled) {
      List<CatalogEntity> catalogEntities = castObjetListToCatalogEntities(savedTemplates);
      return getEntityUidWorkflowsMappingForIDPCatalogs(catalogEntities);
    }
    List<BackstageCatalogEntity> backstageCatalogEntities = castObjetListToBackstageCatalogEntities(savedTemplates);
    return getEntityUidWorkflowsMappingForBackstageCatalog(backstageCatalogEntities);
  }

  private Map<String, WorkflowsInfo> getEntityUidWorkflowsMappingForBackstageCatalog(
      List<BackstageCatalogEntity> savedTemplates) {
    List<WorkflowsInfo> workflowsInfos =
        savedTemplates.stream().map(WorkflowsMapper::toDTO).collect(Collectors.toList());

    return workflowsInfos.stream().collect(Collectors.toMap(WorkflowsInfo::getUid, Function.identity()));
  }

  private Map<String, WorkflowsInfo> getEntityUidWorkflowsMappingForIDPCatalogs(List<CatalogEntity> savedWorkflows) {
    List<WorkflowsInfo> workflowsInfos =
        savedWorkflows.stream().map(WorkflowsMapper::toDTO).collect(Collectors.toList());

    return workflowsInfos.stream().collect(
        Collectors.toMap(wf -> modifyEntityUidToSupportWorkflowFromTemplate(wf.getUid()), Function.identity()));
  }

  private Integer getMaxGroupOrderValueForAccount(ScopeInfo scopeInfo) {
    List<GroupEntity> groupsEntities = groupRepository.findAllByParentUniqueId(scopeInfo.getUniqueId());
    if (isEmpty(groupsEntities)) {
      return 0;
    }
    return groupsEntities.stream().map(GroupEntity::getOrder).max(Integer::compareTo).get();
  }

  private List<GroupEntity> sortGroupsEntityBasedOnOrder(List<GroupEntity> groupsEntities) {
    return groupsEntities.stream().sorted(Comparator.comparingInt(GroupEntity::getOrder)).collect(Collectors.toList());
  }

  private List<GroupEntity> updateGroupEntities(String accountIdentifier, List<GroupEntity> groupsEntities) {
    List<GroupEntity> updatedGroupEntities;
    try {
      updatedGroupEntities = (List<GroupEntity>) groupRepository.saveAll(groupsEntities);
    } catch (DuplicateKeyException e) {
      throw new InvalidRequestException(String.format(DUPLICATE_KEY_EXCEPTION_ERROR_MESSAGE,
          CommonUtils.extractDuplicateValueFromDuplicateKeyException(e.getMessage()), accountIdentifier));
    }
    return updatedGroupEntities;
  }

  private List<String> getIDPCatalogIdentifiersOfWorkflows(List<String> idpCatalogEntityUid) {
    List<String> toReturnList = new ArrayList<>();
    for (String entityUid : idpCatalogEntityUid) {
      String[] scopeKindAndName = entityUid.split("/");
      toReturnList.add(scopeKindAndName[2]);
    }
    return toReturnList;
  }

  private List<Object> getSavedTemplates(ScopeInfo scopeInfo, Boolean isIDP2_0Enabled) {
    if (isIDP2_0Enabled) {
      return new ArrayList<>(
          catalogEntityRepository.findAllByParentUniqueIdAndKind(scopeInfo.getUniqueId(), WORKFLOW_KIND));
    }
    return new ArrayList<>(backstageService.findAllByAccountIdentifierAndKind(
        scopeInfo.getAccountIdentifier(), BackstageCatalogEntityTypes.TEMPLATE.kind));
  }

  private List<CatalogEntity> castObjetListToCatalogEntities(List<Object> objetList) {
    return objetList.stream().map(CatalogEntity.class ::cast).collect(Collectors.toList());
  }

  private List<BackstageCatalogEntity> castObjetListToBackstageCatalogEntities(List<Object> objetList) {
    return objetList.stream().map(BackstageCatalogEntity.class ::cast).collect(Collectors.toList());
  }

  private GroupEntity modifyWorkflowsIdBasedOnIDPV2(GroupEntity groupEntity) {
    List<String> workflows = groupEntity.getWorkflows();
    if (isEmpty(workflows)) {
      return groupEntity;
    }

    List<String> modifiedWorkflows =
        workflows.stream()
            .map(wf -> modifyEntityUidToSupportWorkflowFromTemplate(wf)) // use your function here
            .collect(Collectors.toList());

    groupEntity.setWorkflows(modifiedWorkflows);
    return groupEntity;
  }

  private String modifyEntityUidToSupportWorkflowFromTemplate(String entityUid) {
    if (entityUid.contains("workflow/")) {
      return entityUid.replace("workflow/", "template/");
    }
    return entityUid;
  }

  private void throwExceptionIfIDPV2NotEnabled(
      boolean isIDP2_0Enabled, String orgIdentifier, String projectIdentifier) {
    if (!isIDP2_0Enabled) {
      if (!isEmpty(projectIdentifier) || !isEmpty(orgIdentifier)) {
        throw new InvalidRequestException("Project or Org level groups are only supported in IDP V2");
      }
    }
  }

  private void throwExceptionIfDefaultToAccountMigrationNotCompleted(String accountIdentifier) {
    if (!isIdpV2MigrationCompletedForDefaultToAccount(accountIdentifier)) {
      throw new InvalidRequestException("Default to account migration not completed");
    }
  }

  private boolean isIdpV2MigrationCompletedForDefaultToAccount(String accountIdentifier) {
    Optional<NamespaceEntity> namespaceEntity = namespaceService.getEntityForAccountIdentifier(accountIdentifier);

    if (namespaceEntity.isPresent() && namespaceEntity.get().getMetadata() != null) {
      NamespaceEntity.Metadata metadata = namespaceEntity.get().getMetadata();

      // Proceed only if idpV2 feature flag is enabled
      if (metadata.isIdpV2FFState()) {
        NamespaceEntity.Metadata.IdpV2MigrationInfo migrationInfo = metadata.getIdpV2MigrationInfo();

        // If migration info is missing, treat as completed
        if (migrationInfo == null) {
          return true;
        }

        boolean backstageCompleted = migrationInfo.isMigrateDefaultToAccountNamespaceInBackstageCompleted();
        boolean dependentsCompleted = migrationInfo.isMigrateDefaultToAccountNamespaceInDependentsCompleted();

        // Migration is complete only if both are true
        return backstageCompleted && dependentsCompleted;
      }
    }

    // Default to true if entity not found, feature flag off, or other issue
    return true;
  }
}
