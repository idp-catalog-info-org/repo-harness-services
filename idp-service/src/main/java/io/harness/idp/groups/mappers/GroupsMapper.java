/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.idp.groups.mappers;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.data.structure.UUIDGenerator;
import io.harness.idp.groups.entities.GroupEntity;
import io.harness.spec.server.idp.v1.model.Group;
import io.harness.spec.server.idp.v1.model.GroupResponse;
import io.harness.spec.server.idp.v1.model.WorkflowsInfo;

import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@UtilityClass
public class GroupsMapper {
  public GroupEntity fromDTO(ScopeInfo scopeInfo, Group group) {
    GroupEntity groupsEntity = GroupEntity.builder()
                                   .accountIdentifier(scopeInfo.getAccountIdentifier())
                                   .name(group.getName())
                                   .description(group.getDescription())
                                   .icon(group.getIcon())
                                   .orgIdentifier(scopeInfo.getOrgIdentifier())
                                   .projectIdentifier(scopeInfo.getProjectIdentifier())
                                   .parentUniqueId(scopeInfo.getUniqueId())
                                   .uniqueId(UUIDGenerator.generateUuid())
                                   .order(group.getOrder())
                                   .identifier(group.getIdentifier())
                                   .build();
    if (!isEmpty(group.getWorkflows())) {
      groupsEntity.setWorkflows(group.getWorkflows().stream().map(WorkflowsInfo::getUid).collect(Collectors.toList()));
    }
    return groupsEntity;
  }

  public Group toDTO(GroupEntity groupsEntity, List<WorkflowsInfo> workflows, String orgName, String projectName) {
    Group group = new Group();
    group.identifier(groupsEntity.getIdentifier());
    group.projectIdentifier(groupsEntity.getProjectIdentifier());
    group.orgIdentifier(groupsEntity.getOrgIdentifier());
    group.name(groupsEntity.getName());
    group.orgName(orgName);
    group.projectName(projectName);
    group.description(groupsEntity.getDescription());
    group.icon(groupsEntity.getIcon());
    group.workflows(workflows);
    group.order(groupsEntity.getOrder());
    return group;
  }

  public Group toYamlGroupDTO(GroupEntity groupsEntity, List<WorkflowsInfo> workflowsInfos) {
    Group group = new Group();
    group.name(groupsEntity.getName());
    group.description(groupsEntity.getDescription());
    group.icon(groupsEntity.getIcon());
    group.workflows(workflowsInfos);
    return group;
  }

  public GroupResponse toResponse(
      GroupEntity groupsEntity, List<WorkflowsInfo> workflowsInfos, String orgName, String projectName) {
    GroupResponse groupResponse = new GroupResponse();
    groupResponse.setGroup(toDTO(groupsEntity, workflowsInfos, orgName, projectName));
    return groupResponse;
  }
}
