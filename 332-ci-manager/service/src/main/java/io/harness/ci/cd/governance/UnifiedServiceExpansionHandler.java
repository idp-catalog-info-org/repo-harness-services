/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.cd.governance;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.core.mapper.TagMapper.convertToMap;
import static io.harness.pms.yaml.YAMLFieldNameConstants.ID;
import static io.harness.pms.yaml.YAMLFieldNameConstants.ITEMS;
import static io.harness.remote.client.NGRestUtils.getResponse;

import io.harness.annotations.dev.OwnedBy;
import io.harness.app.beans.entities.ServiceBasicInfo;
import io.harness.app.beans.entities.ServiceEntity;
import io.harness.beans.IdentifierRef;
import io.harness.ci.cd.service.ServiceEntityService;
import io.harness.common.NGExpressionUtils;
import io.harness.pms.contracts.governance.ExpansionPlacementStrategy;
import io.harness.pms.contracts.governance.ExpansionRequestMetadata;
import io.harness.pms.sdk.core.governance.handler.ExpandedValue;
import io.harness.pms.sdk.core.governance.handler.ExpansionResponse;
import io.harness.pms.sdk.core.governance.handler.JsonExpansionHandler;
import io.harness.unified.service.NgServiceResourceClient;
import io.harness.unified.service.UnifiedServiceConverterRequestDTO;
import io.harness.unified.service.UnifiedServiceConverterResponse;
import io.harness.utils.IdentifierRefHelper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CI)
@Singleton
@Slf4j
public class UnifiedServiceExpansionHandler implements JsonExpansionHandler {
  @Inject private ServiceEntityService serviceEntityService;
  @Inject private NgServiceResourceClient ngServiceResourceClient;

  @Override
  public ExpansionResponse expand(JsonNode serviceNode, ExpansionRequestMetadata metadata, String fqn) {
    String accountIdentifier = metadata.getAccountId();
    String orgIdentifier = metadata.getOrgId();
    String projectIdentifier = metadata.getProjectId();
    List<String> serviceRefs = getServiceIdentifiers(serviceNode);

    if (isEmpty(serviceRefs)) {
      return sendErrorResponseForEmptyServices();
    }

    ServiceLookupResult serviceLookupResult =
        getServicesInfoAndMissingSvcIds(accountIdentifier, orgIdentifier, projectIdentifier, serviceRefs);

    List<String> serviceIdsAsExpression = serviceLookupResult.getServiceIdsAsExpression();
    if (isNotEmpty(serviceIdsAsExpression)) {
      return sendErrorResponseForServiceIdsAsExpression(serviceIdsAsExpression);
    }

    List<String> notFoundIds = serviceLookupResult.getMissingServiceIds();
    if (isNotEmpty(notFoundIds)) {
      return sendErrorResponseForNotFoundService(notFoundIds);
    }

    ExpandedValue value =
        UnifiedServiceExpandedValue.builder().servicesInfo(serviceLookupResult.getFoundServices()).build();
    return ExpansionResponse.builder()
        .success(true)
        .key(value.getKey())
        .value(value)
        .placement(ExpansionPlacementStrategy.REPLACE)
        .build();
  }

  private ExpansionResponse sendErrorResponseForEmptyServices() {
    return ExpansionResponse.builder().success(false).errorMessage("No unified services are present").build();
  }

  private ExpansionResponse sendErrorResponseForServiceIdsAsExpression(List<String> serviceIds) {
    return ExpansionResponse.builder()
        .success(false)
        .errorMessage("Following service ids are expression:  " + serviceIds.toString())
        .build();
  }

  private ExpansionResponse sendErrorResponseForNotFoundService(List<String> serviceIds) {
    return ExpansionResponse.builder()
        .success(false)
        .errorMessage("Could not find unified service: " + serviceIds.toString())
        .build();
  }

  private ServiceLookupResult getServicesInfoAndMissingSvcIds(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, List<String> serviceIdentifiers) {
    List<ServiceBasicInfo> foundServices = new ArrayList<>();
    List<String> notFoundServices = new ArrayList<>();
    List<String> serviceIdsAsExpression = new ArrayList<>();

    for (String serviceId : serviceIdentifiers) {
      if (NGExpressionUtils.matchesGenericJexlOrCelExpressionPattern(serviceId)) {
        log.warn(String.format("Service id %s is an expression", serviceId));
        serviceIdsAsExpression.add(serviceId);
        continue;
      }
      Optional<ServiceBasicInfo> serviceInfo =
          getServiceInfo(accountIdentifier, orgIdentifier, projectIdentifier, serviceId);
      if (serviceInfo.isPresent()) {
        foundServices.add(serviceInfo.get());
      } else {
        notFoundServices.add(serviceId);
      }
    }
    return ServiceLookupResult.builder()
        .foundServices(foundServices)
        .missingServiceIds(notFoundServices)
        .serviceIdsAsExpression(serviceIdsAsExpression)
        .build();
  }

  private List<String> getServiceIdentifiers(JsonNode serviceNode) {
    List<String> serviceIdentifiers = new ArrayList<>();
    if (serviceNode == null) {
      return serviceIdentifiers;
    }

    String singleServiceId = getServiceId(serviceNode);
    if (isNotEmpty(singleServiceId)) {
      serviceIdentifiers.add(singleServiceId);
      return serviceIdentifiers;
    }

    JsonNode serviceItems = serviceNode.get(ITEMS);
    if (serviceItems != null && serviceItems.isArray()) {
      ArrayNode serviceNodes = (ArrayNode) serviceItems;
      for (JsonNode svcNode : serviceNodes) {
        String serviceId = getServiceId(svcNode);
        if (isNotEmpty(serviceId)) {
          serviceIdentifiers.add(serviceId);
        }
      }
    }
    return serviceIdentifiers;
  }

  private String getServiceId(JsonNode serviceNode) {
    if (serviceNode.isTextual()) {
      return serviceNode.asText();
    }

    JsonNode serviceId = serviceNode.get(ID);
    if (serviceId != null) {
      return serviceId.asText();
    }
    return null;
  }

  Optional<ServiceBasicInfo> getServiceInfo(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId) {
    Optional<ServiceEntity> serviceEntityOpt =
        serviceEntityService.get(accountIdentifier, orgIdentifier, projectIdentifier, serviceId, false);
    if (serviceEntityOpt.isPresent()) {
      return Optional.of(toServiceBasicInfo(serviceEntityOpt.get()));
    } else {
      IdentifierRef serviceIdentifierRef =
          IdentifierRefHelper.getIdentifierRef(serviceId, accountIdentifier, orgIdentifier, projectIdentifier);
      UnifiedServiceConverterResponse serviceEntityNGResponse = getResponse(ngServiceResourceClient.convertToUnified(
          serviceIdentifierRef.getIdentifier(), serviceIdentifierRef.getAccountIdentifier(),
          serviceIdentifierRef.getOrgIdentifier(), serviceIdentifierRef.getProjectIdentifier(), null, null,
          UnifiedServiceConverterRequestDTO.builder().serviceInputsYaml("").build()));
      // Expansion handlers must not fail the flow: on NG error, log and treat the service as not found.
      if (serviceEntityNGResponse != null && serviceEntityNGResponse.getError() != null) {
        log.warn("Failed to convert service {} to unified service: {}", serviceId,
            serviceEntityNGResponse.getError().getErrorMessage());
        return Optional.empty();
      }
      if (serviceEntityNGResponse != null && serviceEntityNGResponse.getResponseDTO() != null) {
        return Optional.of(toServiceBasicInfo(serviceEntityNGResponse, serviceIdentifierRef));
      }
    }
    return Optional.empty();
  }

  ServiceBasicInfo toServiceBasicInfo(
      UnifiedServiceConverterResponse unifiedServiceConverterResponse, IdentifierRef serviceIdentifierRef) {
    return ServiceBasicInfo.builder()
        .id(serviceIdentifierRef.getIdentifier())
        .name(unifiedServiceConverterResponse.getResponseDTO().getName())
        .description(unifiedServiceConverterResponse.getResponseDTO().getDescription())
        .accountIdentifier(serviceIdentifierRef.getAccountIdentifier())
        .orgIdentifier(serviceIdentifierRef.getOrgIdentifier())
        .projectIdentifier(serviceIdentifierRef.getProjectIdentifier())
        .tags(unifiedServiceConverterResponse.getResponseDTO().getTags())
        .build();
  }

  ServiceBasicInfo toServiceBasicInfo(ServiceEntity serviceEntity) {
    return ServiceBasicInfo.builder()
        .id(serviceEntity.getIdentifier())
        .name(serviceEntity.getName())
        .description(serviceEntity.getDescription())
        .accountIdentifier(serviceEntity.getAccountId())
        .orgIdentifier(serviceEntity.getOrgIdentifier())
        .projectIdentifier(serviceEntity.getProjectIdentifier())
        .tags(convertToMap(serviceEntity.getTags()))
        .build();
  }

  @Value
  @Builder
  public static class ServiceLookupResult {
    List<ServiceBasicInfo> foundServices;
    List<String> missingServiceIds;
    List<String> serviceIdsAsExpression;
  }
}
