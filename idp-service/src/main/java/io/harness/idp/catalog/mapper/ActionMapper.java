/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.idp.catalog.mapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.catalog.entities.Action;
import io.harness.idp.catalog.entities.ActionBuiltinConfig;
import io.harness.idp.catalog.entities.ActionHttpConfig;
import io.harness.idp.catalog.entities.ActionStatus;
import io.harness.idp.catalog.entities.ActionType;
import io.harness.spec.server.idp.v1.model.ActionBuiltinConfigDTO;
import io.harness.spec.server.idp.v1.model.ActionCreateRequest;
import io.harness.spec.server.idp.v1.model.ActionHttpConfigDTO;
import io.harness.spec.server.idp.v1.model.ActionHttpConfigDTO.MethodEnum;
import io.harness.spec.server.idp.v1.model.ActionResponse;
import io.harness.spec.server.idp.v1.model.ActionUpdateRequest;

import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.IDP)
public class ActionMapper {
  public static void applyUpdate(ActionUpdateRequest request, Action existing) {
    existing.setName(request.getName());
    existing.setDescription(request.getDescription());
    existing.setCategory(request.getCategory());
    existing.setConnectorRef(request.getConnectorRef());
    existing.setTags(request.getTags());
    existing.setDelegateSelectors(request.getDelegateSelectors());
    existing.setInputSchema(request.getInputSchema() != null ? (Map<String, Object>) request.getInputSchema() : null);
    existing.setOutputMapping(request.getOutputMapping());
    existing.setHttpConfig(request.getHttpConfig() != null ? fromHttpConfigDTO(request.getHttpConfig()) : null);
    existing.setBuiltinConfig(
        request.getBuiltinConfig() != null ? fromBuiltinConfigDTO(request.getBuiltinConfig()) : null);
  }

  public static Action fromCreateRequest(ActionCreateRequest request) {
    Action.ActionBuilder builder =
        Action.builder()
            .identifier(request.getIdentifier())
            .name(request.getName())
            .description(request.getDescription())
            .version(request.getVersion())
            .inputSchema(request.getInputSchema() != null ? (Map<String, Object>) request.getInputSchema() : null)
            .outputMapping(request.getOutputMapping())
            .connectorRef(request.getConnectorRef())
            .delegateSelectors(request.getDelegateSelectors())
            .category(request.getCategory())
            .tags(request.getTags());

    if (request.getType() == null) {
      throw new InvalidRequestException("'type' is required and must be one of: HTTP, BUILTIN");
    }
    builder.type(ActionType.valueOf(request.getType().name()));
    if (request.getHttpConfig() != null) {
      builder.httpConfig(fromHttpConfigDTO(request.getHttpConfig()));
    }
    if (request.getBuiltinConfig() != null) {
      builder.builtinConfig(fromBuiltinConfigDTO(request.getBuiltinConfig()));
    }
    return builder.build();
  }

  public static ActionResponse toResponse(Action action) {
    return toResponse(action, null);
  }

  public static ActionResponse toResponse(Action action, ScopeInfo scopeInfo) {
    ActionResponse response = new ActionResponse();
    response.setId(action.getId());
    response.setAccountIdentifier(action.getAccountIdentifier());
    response.setOrgIdentifier(scopeInfo != null ? scopeInfo.getOrgIdentifier() : null);
    response.setProjectIdentifier(scopeInfo != null ? scopeInfo.getProjectIdentifier() : null);
    response.setIdentifier(action.getIdentifier());
    response.setName(action.getName());
    response.setDescription(action.getDescription());
    response.setVersion(action.getVersion());
    response.setStatus(ActionResponse.StatusEnum.valueOf(action.getStatus().name()));
    response.setType(ActionResponse.TypeEnum.valueOf(action.getType().name()));
    response.setInputSchema(action.getInputSchema());
    response.setOutputMapping(action.getOutputMapping());
    response.setConnectorRef(action.getConnectorRef());
    response.setDelegateSelectors(action.getDelegateSelectors());
    response.setCategory(action.getCategory());
    response.setTags(action.getTags());
    response.setCreatedAt(action.getCreatedAt());
    response.setLastUpdatedAt(action.getLastUpdatedAt());
    response.setDeprecatedAt(action.getDeprecatedAt());
    if (action.getHttpConfig() != null) {
      response.setHttpConfig(toHttpConfigDTO(action.getHttpConfig()));
    }
    if (action.getBuiltinConfig() != null) {
      response.setBuiltinConfig(toBuiltinConfigDTO(action.getBuiltinConfig()));
    }
    return response;
  }

  private static ActionHttpConfig fromHttpConfigDTO(ActionHttpConfigDTO dto) {
    return ActionHttpConfig.builder()
        .method(dto.getMethod() != null ? dto.getMethod().name() : null)
        .path(dto.getPath())
        .url(dto.getUrl())
        .queryParams(dto.getQueryParams())
        .headers(dto.getHeaders())
        .body(dto.getBody())
        .timeoutMs(dto.getTimeoutMs())
        .expectedStatusCodes(dto.getExpectedStatusCodes())
        .build();
  }

  private static ActionHttpConfigDTO toHttpConfigDTO(ActionHttpConfig config) {
    ActionHttpConfigDTO dto = new ActionHttpConfigDTO();
    dto.setMethod(config.getMethod() != null ? MethodEnum.fromValue(config.getMethod()) : null);
    dto.setPath(config.getPath());
    dto.setUrl(config.getUrl());
    dto.setQueryParams(config.getQueryParams());
    dto.setHeaders(config.getHeaders());
    dto.setBody(config.getBody());
    dto.setTimeoutMs(config.getTimeoutMs());
    dto.setExpectedStatusCodes(config.getExpectedStatusCodes());
    return dto;
  }

  private static ActionBuiltinConfig fromBuiltinConfigDTO(ActionBuiltinConfigDTO dto) {
    return ActionBuiltinConfig.builder().handler(dto.getHandler()).build();
  }

  private static ActionBuiltinConfigDTO toBuiltinConfigDTO(ActionBuiltinConfig config) {
    ActionBuiltinConfigDTO dto = new ActionBuiltinConfigDTO();
    dto.setHandler(config.getHandler());
    return dto;
  }
}
