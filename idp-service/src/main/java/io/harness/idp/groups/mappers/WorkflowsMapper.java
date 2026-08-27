/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.idp.groups.mappers;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.idp.backstage.beans.BackstageCatalogEntityTypes;
import io.harness.idp.backstage.entities.BackstageCatalogEntity;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.utils.CatalogUtils;
import io.harness.spec.server.idp.v1.model.WorkflowsInfo;
import io.harness.spec.server.idp.v1.model.WorkflowsInfoResponse;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@UtilityClass
public class WorkflowsMapper {
  public WorkflowsInfo toDTO(BackstageCatalogEntity backstageCatalogEntity) {
    WorkflowsInfo workflowsInfo = new WorkflowsInfo();
    if (backstageCatalogEntity.getMetadata() != null) {
      if (backstageCatalogEntity.getMetadata().get("name") != null) {
        workflowsInfo.setName(backstageCatalogEntity.getMetadata().get("name").toString());
      }
      if (backstageCatalogEntity.getMetadata().get("description") != null) {
        workflowsInfo.setDescription(backstageCatalogEntity.getMetadata().get("description").toString());
      }
      if (backstageCatalogEntity.getMetadata().get("title") != null) {
        workflowsInfo.setTitle(backstageCatalogEntity.getMetadata().get("title").toString());
      }
      if (backstageCatalogEntity.getMetadata().get("icon") != null) {
        workflowsInfo.setIcon(backstageCatalogEntity.getMetadata().get("icon").toString());
      }
    }
    workflowsInfo.setUid(backstageCatalogEntity.getEntityUid());
    workflowsInfo.setType(BackstageCatalogEntityTypes.getEntityType(backstageCatalogEntity));
    workflowsInfo.setKind(backstageCatalogEntity.getKind());
    workflowsInfo.setOwner(BackstageCatalogEntityTypes.getEntityOwner(backstageCatalogEntity));
    return workflowsInfo;
  }

  public WorkflowsInfo toDTO(CatalogEntity catalogEntity) {
    WorkflowsInfo workflowsInfo = new WorkflowsInfo();

    if (catalogEntity.getMetadata() != null) {
      if (catalogEntity.getMetadata().get("icon") != null) {
        workflowsInfo.setIcon(catalogEntity.getMetadata().get("icon").toString());
      }
    }
    workflowsInfo.setName(catalogEntity.getIdentifier());
    workflowsInfo.setTitle(catalogEntity.getName());
    workflowsInfo.description(catalogEntity.getDescription());

    workflowsInfo.setUid(CatalogUtils.getIdentifierForWorkflowsInGroup(
        CatalogUtils.getFullyQualifiedScopeRef(
            catalogEntity.getScope(), catalogEntity.getOrgIdentifier(), catalogEntity.getProjectIdentifier()),
        catalogEntity.getKind(), catalogEntity.getIdentifier()));
    workflowsInfo.setType(catalogEntity.getType());
    workflowsInfo.setKind(catalogEntity.getKind());
    workflowsInfo.setOwner(catalogEntity.getOwner());
    return workflowsInfo;
  }

  public WorkflowsInfoResponse toResponseFromCatalogEntities(List<CatalogEntity> catalogEntities) {
    List<WorkflowsInfo> workflowsInfos = new ArrayList<>();
    WorkflowsInfoResponse workflowsInfoResponse = new WorkflowsInfoResponse();
    for (CatalogEntity catalogEntity : catalogEntities) {
      workflowsInfos.add(WorkflowsMapper.toDTO(catalogEntity));
    }
    workflowsInfoResponse.workflows(workflowsInfos);
    return workflowsInfoResponse;
  }

  // Renamed it because of  Java's type erasure error for overloaded functions
  public WorkflowsInfoResponse toResponseFromBackstageCatalogEntities(
      List<BackstageCatalogEntity> backstageCatalogEntities) {
    List<WorkflowsInfo> workflowsInfos = new ArrayList<>();
    WorkflowsInfoResponse workflowsInfoResponse = new WorkflowsInfoResponse();
    for (BackstageCatalogEntity backstageCatalogEntity : backstageCatalogEntities) {
      workflowsInfos.add(WorkflowsMapper.toDTO(backstageCatalogEntity));
    }
    workflowsInfoResponse.workflows(workflowsInfos);
    return workflowsInfoResponse;
  }
}
