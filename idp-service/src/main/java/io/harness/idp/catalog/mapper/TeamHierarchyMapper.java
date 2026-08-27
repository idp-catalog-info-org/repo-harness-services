/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.mapper;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.YamlUtils.writeObjectAsYaml;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.GitReferencedCatalogEntity;
import io.harness.idp.catalog.utils.CatalogUtils;
import io.harness.spec.server.idp.v1.model.TeamHierarchyNode;

import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
public class TeamHierarchyMapper {
  public TeamHierarchyNode toNode(CatalogEntity catalogEntity, String orgName, String projectName, String kindIcon,
      List<TeamHierarchyNode> children) {
    TeamHierarchyNode node = new TeamHierarchyNode();
    node.setIdentifier(catalogEntity.getIdentifier());
    node.setEntityRef(CatalogUtils.entityRef(catalogEntity));
    node.setOrgIdentifier(catalogEntity.getOrgIdentifier());
    node.setOrgName(orgName);
    node.setProjectIdentifier(catalogEntity.getProjectIdentifier());
    node.setProjectName(projectName);
    node.setScope(TeamHierarchyNode.ScopeEnum.valueOf(catalogEntity.getScope()));
    node.setReferenceType(TeamHierarchyNode.ReferenceTypeEnum.valueOf(catalogEntity.getReferenceType().name()));
    node.setKindIdentifier(catalogEntity.getKind());
    node.setKindIcon(kindIcon);
    node.setType(catalogEntity.getType());
    node.setName(catalogEntity.getName());
    node.setDescription(catalogEntity.getDescription());
    node.setOwner(catalogEntity.getOwner());
    node.setTags(catalogEntity.getTags());
    node.setMetadata(catalogEntity.getDecoratedMetadata());
    node.setSpec(catalogEntity.getSpec());
    node.setRelations(catalogEntity.getRelations());
    node.setCreated(catalogEntity.getCreatedAt());
    node.setUpdated(catalogEntity.getLastUpdatedAt());

    Map<String, Object> processedData = catalogEntity.getFailSafeProcessedData();
    if (!isEmpty(processedData)) {
      node.setDecorator(writeObjectAsYaml(processedData));
    }

    if (catalogEntity instanceof GitReferencedCatalogEntity) {
      node.setGitDetails(IDPGitXMapper.getEntityGitDetails());
    }

    node.setChildren(children);
    return node;
  }
}
