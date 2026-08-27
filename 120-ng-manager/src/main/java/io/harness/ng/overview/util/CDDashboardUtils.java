/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.overview.util;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.ng.core.service.dto.ServiceDashboardResponseDTO;
import io.harness.ng.core.service.dto.ServiceResponseDTO;
import io.harness.ng.core.service.entity.ServiceFilterPropertiesDTO;
import io.harness.spec.server.ng.v1.model.Service;
import io.harness.spec.server.ng.v1.model.ServiceDashboardResponse;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@UtilityClass
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_DASHBOARD})
public class CDDashboardUtils {
  public ServiceFilterPropertiesDTO createFilterProperties(
      List<String> serviceIdentifiers, List<String> serviceNames, List<String> tags, List<String> serviceTypes) {
    if (isEmpty(serviceIdentifiers) && isEmpty(serviceNames) && isEmpty(tags) && isEmpty(serviceTypes)) {
      return null;
    }

    return ServiceFilterPropertiesDTO.builder()
        .serviceIdentifiers(serviceIdentifiers)
        .serviceNames(serviceNames)
        .serviceTypes(serviceTypes)
        .tags(getTags(tags))
        .build();
  }

  public List<ServiceDashboardResponse> mapToServiceDashaboardResponseList(
      List<ServiceDashboardResponseDTO> responseDTOList) {
    if (isEmpty(responseDTOList)) {
      return Collections.emptyList();
    }

    return responseDTOList.stream().map(CDDashboardUtils::mapToServiceDashboardResponse).collect(Collectors.toList());
  }

  public ServiceDashboardResponse mapToServiceDashboardResponse(ServiceDashboardResponseDTO responseDTO) {
    return new ServiceDashboardResponse()
        .service(mapToService(responseDTO.getService()))
        .createdAt(responseDTO.getCreatedAt())
        .lastModifiedAt(responseDTO.getLastModifiedAt())
        .deploymentTypes(responseDTO.getDeploymentTypeList().stream().toList());
  }

  public Service mapToService(ServiceResponseDTO responseDTO) {
    return new Service()
        .account(responseDTO.getAccountId())
        .org(responseDTO.getOrgIdentifier())
        .project(responseDTO.getProjectIdentifier())
        .identifier(responseDTO.getIdentifier())
        .name(responseDTO.getName())
        .description(responseDTO.getDescription())
        .yaml(responseDTO.getYaml())
        .tags(responseDTO.getTags());
  }

  private Map<String, String> getTags(List<String> tags) {
    if (isEmpty(tags)) {
      return null;
    }

    Map<String, String> map = new HashMap<>();
    for (String tag : tags) {
      String[] tagComps = tag.split(":");
      if (tagComps.length == 1) {
        map.put(tagComps[0], null);
      } else {
        map.put(tagComps[0], tagComps[1]);
      }
    }
    return map;
  }
}
