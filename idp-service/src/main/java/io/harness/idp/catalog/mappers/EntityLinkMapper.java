/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.mappers;

import static io.harness.idp.common.CommonUtils.buildSpacePath;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.catalog.entities.EntityLinks;
import io.harness.spec.server.idp.v1.model.EntityLinkRequest;
import io.harness.spec.server.idp.v1.model.EntityLinkResponse;
import io.harness.spec.server.idp.v1.model.FieldMapping;
import io.harness.spec.server.idp.v1.model.IntegrationReference;

import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.IDP)
public class EntityLinkMapper {
  public EntityLinks toEntity(String accountIdentifier, EntityLinkRequest request) {
    return EntityLinks.builder()
        .accountIdentifier(accountIdentifier)
        .entityRef(request.getEntityLink().getEntityRef())
        .scopes(request.getEntityLink().getScopes())
        .targets(mapTargets(request.getEntityLink().getTargets()))
        .fieldMappings(mapFieldMappings(request.getEntityLink().getFieldMappings()))
        .integrations(mapIntegrations(accountIdentifier, request.getEntityLink().getIntegrations()))
        .build();
  }

  public EntityLinkResponse toDTO(EntityLinks entity) {
    EntityLinkResponse response = new EntityLinkResponse();
    io.harness.spec.server.idp.v1.model.EntityLink link = new io.harness.spec.server.idp.v1.model.EntityLink();
    link.setEntityRef(entity.getEntityRef());
    link.setScopes(entity.getScopes());

    if (entity.getTargets() != null) {
      link.setTargets(entity.getTargets()
                          .stream()
                          .map(t -> {
                            io.harness.spec.server.idp.v1.model.LinkTarget lt =
                                new io.harness.spec.server.idp.v1.model.LinkTarget();
                            lt.setEntityKind(t.getEntityKind());
                            lt.setEntityType(t.getEntityType());
                            return lt;
                          })
                          .collect(Collectors.toList()));
    }

    if (entity.getFieldMappings() != null) {
      link.setFieldMappings(entity.getFieldMappings()
                                .stream()
                                .map(m -> {
                                  FieldMapping fm = new FieldMapping();
                                  fm.setInput(m.getInput());
                                  fm.setEntityFieldSource(m.getEntityFieldSource());
                                  return fm;
                                })
                                .collect(Collectors.toList()));
    }

    if (entity.getIntegrations() != null) {
      link.setIntegrations(entity.getIntegrations()
                               .stream()
                               .map(ref -> {
                                 IntegrationReference ir = new IntegrationReference();
                                 ir.setIdentifier(ref.getIdentifier());
                                 String[] parts =
                                     ref.getSpacePath() != null ? ref.getSpacePath().split("/") : new String[0];
                                 if (parts.length >= 2) {
                                   ir.setOrgIdentifier(parts[1]);
                                 }
                                 if (parts.length >= 3) {
                                   ir.setProjectIdentifier(parts[2]);
                                 }
                                 return ir;
                               })
                               .collect(Collectors.toList()));
    }

    response.setEntityLink(link);
    return response;
  }

  private List<EntityLinks.LinkTarget> mapTargets(List<io.harness.spec.server.idp.v1.model.LinkTarget> targets) {
    if (targets == null) {
      return List.of();
    }
    return targets.stream()
        .map(t -> EntityLinks.LinkTarget.builder().entityKind(t.getEntityKind()).entityType(t.getEntityType()).build())
        .collect(Collectors.toList());
  }

  private List<EntityLinks.FieldMapping> mapFieldMappings(List<FieldMapping> mappings) {
    if (mappings == null) {
      return List.of();
    }
    return mappings.stream()
        .map(m
            -> EntityLinks.FieldMapping.builder()
                   .input(m.getInput())
                   .entityFieldSource(m.getEntityFieldSource())
                   .build())
        .collect(Collectors.toList());
  }

  private List<EntityLinks.IntegrationReference> mapIntegrations(
      String accountIdentifier, List<IntegrationReference> integrations) {
    if (integrations == null) {
      return null;
    }
    return integrations.stream()
        .map(ref
            -> EntityLinks.IntegrationReference.builder()
                   .identifier(ref.getIdentifier())
                   .spacePath(buildSpacePath(accountIdentifier, ref.getOrgIdentifier(), ref.getProjectIdentifier()))
                   .build())
        .collect(Collectors.toList());
  }
}
