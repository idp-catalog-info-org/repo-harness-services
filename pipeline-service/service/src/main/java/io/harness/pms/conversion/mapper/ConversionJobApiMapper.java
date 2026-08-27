/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.conversion.mapper;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.goconvert.EntityType;
import io.harness.pms.conversion.beans.ConversionActionType;
import io.harness.pms.conversion.beans.ConversionErrorDetail;
import io.harness.pms.conversion.beans.ConversionJobEntity;
import io.harness.pms.conversion.beans.ConversionJobMetricsDTO;
import io.harness.pms.conversion.beans.ConversionNodeSummary;
import io.harness.pms.conversion.beans.ConversionStatus;
import io.harness.pms.conversion.beans.EntityIdentifierDTO;
import io.harness.pms.conversion.beans.PipelineConversionMetricsDTO;
import io.harness.spec.server.pipeline.v1.model.ConversionJobRequestBody;
import io.harness.spec.server.pipeline.v1.model.ConversionJobResponseBody;
import io.harness.spec.server.pipeline.v1.model.EntityIdentifier;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

/**
 * Mapper for converting between OpenAPI-generated spec models and internal entities/DTOs.
 * Modern v1 API standard - works directly with entities, no intermediate DTOs.
 */
@OwnedBy(PIPELINE)
@UtilityClass
public class ConversionJobApiMapper {
  /**
   * Convert from API request body to entity (for creating new job).
   */
  public static ConversionJobEntity toEntity(String accountId, ConversionJobRequestBody requestBody) {
    ConversionActionType actionType = ConversionActionType.valueOf(requestBody.getActionType().toString());
    EntityType entityType = requestBody.getEntityType() != null
        ? EntityType.valueOf(requestBody.getEntityType().toString())
        : EntityType.PIPELINE;

    // For SINGLE actions, extract entity identifier and full reference from entityReference
    String entityIdentifier = null;
    EntityIdentifierDTO entityReference = null;
    if (actionType == ConversionActionType.SINGLE && requestBody.getEntityReference() != null) {
      entityIdentifier = requestBody.getEntityReference().getEntityId();
      entityReference = toEntityIdentifierDTO(requestBody.getEntityReference());
      entityType = entityReference.getEntityType();
    }

    return ConversionJobEntity.builder()
        .status(ConversionStatus.QUEUED)
        .accountId(accountId)
        .orgId(requestBody.getOrgId())
        .projectId(requestBody.getProjectId())
        .actionType(actionType)
        .entityType(entityType)
        .entityIdentifier(entityIdentifier)
        .entityReference(entityReference)
        .entityReferences(requestBody.getEntityReferences() != null
                ? requestBody.getEntityReferences()
                      .stream()
                      .map(ConversionJobApiMapper::toEntityIdentifierDTO)
                      .collect(Collectors.toList())
                : null)
        .forceReconvert(Boolean.TRUE.equals(requestBody.isForceReconvert()))
        .conversionMetrics(ConversionJobMetricsDTO.builder()
                               .totalEntities(0)
                               .processedEntities(0)
                               .convertedEntities(0)
                               .skippedEntities(0)
                               .failedEntities(0)
                               .progressPercentage(0)
                               .build())
        .nextIteration(System.currentTimeMillis())
        .createdAt(System.currentTimeMillis())
        .build();
  }

  /**
   * Convert from entity to API response body.
   */
  public static ConversionJobResponseBody toResponseBody(ConversionJobEntity entity) {
    ConversionJobResponseBody responseBody = new ConversionJobResponseBody();
    responseBody.setUuid(entity.getUuid());
    responseBody.setStatus(ConversionJobResponseBody.StatusEnum.fromValue(entity.getStatus().name()));
    responseBody.setActionType(ConversionJobResponseBody.ActionTypeEnum.fromValue(entity.getActionType().name()));
    responseBody.setEntityType(ConversionJobResponseBody.EntityTypeEnum.fromValue(entity.getEntityType().name()));
    responseBody.setAccountId(entity.getAccountId());
    responseBody.setOrgId(entity.getOrgId());
    responseBody.setProjectId(entity.getProjectId());
    responseBody.setRetryCount(entity.getRetryCount());
    responseBody.setErrorMessage(entity.getErrorMessage());
    responseBody.setCreatedAt(entity.getCreatedAt());
    responseBody.setStartTs(entity.getStartTs());
    responseBody.setEndTs(entity.getEndTs());

    if (entity.getConversionMetrics() != null) {
      responseBody.setConversionMetrics(toSpecMetrics(entity.getConversionMetrics()));
    }

    if (entity.getConversionResults() != null && !entity.getConversionResults().isEmpty()) {
      List<io.harness.spec.server.pipeline.v1.model.ConversionNodeSummary> results = new ArrayList<>();
      for (ConversionNodeSummary node : entity.getConversionResults()) {
        results.add(toSpecNodeSummary(node));
      }
      responseBody.setConversionResults(results);
    }

    return responseBody;
  }

  /**
   * Convert from spec EntityIdentifier to internal DTO (used in service layer).
   */
  private static EntityIdentifierDTO toEntityIdentifierDTO(EntityIdentifier entityIdentifier) {
    return EntityIdentifierDTO.builder()
        .entityId(entityIdentifier.getEntityId())
        .entityType(EntityType.valueOf(entityIdentifier.getEntityType().toString()))
        .branch(entityIdentifier.getBranch())
        .convertAllBranches(Boolean.TRUE.equals(entityIdentifier.isConvertAllBranches()))
        .build();
  }

  private static io.harness.spec.server.pipeline.v1.model.ConversionJobMetrics toSpecMetrics(
      ConversionJobMetricsDTO metrics) {
    io.harness.spec.server.pipeline.v1.model.ConversionJobMetrics specMetrics =
        new io.harness.spec.server.pipeline.v1.model.ConversionJobMetrics();
    specMetrics.setTotalEntities(metrics.getTotalEntities());
    specMetrics.setProcessedEntities(metrics.getProcessedEntities());
    specMetrics.setConvertedEntities(metrics.getConvertedEntities());
    specMetrics.setSkippedEntities(metrics.getSkippedEntities());
    specMetrics.setFailedEntities(metrics.getFailedEntities());
    specMetrics.setProgressPercentage(metrics.getProgressPercentage());
    return specMetrics;
  }

  private static io.harness.spec.server.pipeline.v1.model.PipelineConversionMetrics toSpecPipelineMetrics(
      PipelineConversionMetricsDTO metrics) {
    io.harness.spec.server.pipeline.v1.model.PipelineConversionMetrics specMetrics =
        new io.harness.spec.server.pipeline.v1.model.PipelineConversionMetrics();
    specMetrics.setTotalInputSets(metrics.getTotalInputSets());
    specMetrics.setConvertedInputSets(metrics.getConvertedInputSets());
    specMetrics.setSkippedInputSets(metrics.getSkippedInputSets());
    specMetrics.setFailedInputSets(metrics.getFailedInputSets());
    specMetrics.setTotalTemplates(metrics.getTotalTemplates());
    specMetrics.setConvertedTemplates(metrics.getConvertedTemplates());
    specMetrics.setSkippedTemplates(metrics.getSkippedTemplates());
    specMetrics.setFailedTemplates(metrics.getFailedTemplates());
    specMetrics.setTotalTriggers(metrics.getTotalTriggers());
    specMetrics.setConvertedTriggers(metrics.getConvertedTriggers());
    specMetrics.setSkippedTriggers(metrics.getSkippedTriggers());
    specMetrics.setFailedTriggers(metrics.getFailedTriggers());
    return specMetrics;
  }

  private static io.harness.spec.server.pipeline.v1.model.ConversionErrorDetail toSpecErrorDetail(
      ConversionErrorDetail error) {
    io.harness.spec.server.pipeline.v1.model.ConversionErrorDetail specError =
        new io.harness.spec.server.pipeline.v1.model.ConversionErrorDetail();
    specError.setCode(error.getCode());
    specError.setMessage(error.getMessage());
    if (error.getSeverity() != null) {
      specError.setSeverity(io.harness.spec.server.pipeline.v1.model.ConversionErrorDetail.SeverityEnum.fromValue(
          error.getSeverity().name()));
    }
    specError.setEntityIdentifier(error.getEntityIdentifier());
    if (error.getEntityType() != null) {
      specError.setEntityType(io.harness.spec.server.pipeline.v1.model.ConversionErrorDetail.EntityTypeEnum.fromValue(
          error.getEntityType().name()));
    }
    specError.setContext(error.getContext());
    return specError;
  }

  private static io.harness.spec.server.pipeline.v1.model.ConversionNodeSummary toSpecNodeSummary(
      ConversionNodeSummary node) {
    io.harness.spec.server.pipeline.v1.model.ConversionNodeSummary specNode =
        new io.harness.spec.server.pipeline.v1.model.ConversionNodeSummary();
    specNode.setEntityIdentifier(node.getEntityIdentifier());
    specNode.setVersionLabel(node.getVersionLabel());
    specNode.setV1Identifier(node.getV1Identifier());
    if (node.getEntityType() != null) {
      specNode.setEntityType(io.harness.spec.server.pipeline.v1.model.ConversionNodeSummary.EntityTypeEnum.fromValue(
          node.getEntityType().name()));
    }
    if (node.getStatus() != null) {
      specNode.setStatus(
          io.harness.spec.server.pipeline.v1.model.ConversionNodeSummary.StatusEnum.fromValue(node.getStatus().name()));
    }
    specNode.setErrorMessage(node.getErrorMessage());

    if (node.getPipelineMetrics() != null) {
      specNode.setPipelineMetrics(toSpecPipelineMetrics(node.getPipelineMetrics()));
    }

    if (node.getErrors() != null && !node.getErrors().isEmpty()) {
      List<io.harness.spec.server.pipeline.v1.model.ConversionErrorDetail> specErrors = new ArrayList<>();
      for (ConversionErrorDetail error : node.getErrors()) {
        specErrors.add(toSpecErrorDetail(error));
      }
      specNode.setErrors(specErrors);
    }

    if (node.getChildren() != null && !node.getChildren().isEmpty()) {
      List<io.harness.spec.server.pipeline.v1.model.ConversionNodeSummary> specChildren = new ArrayList<>();
      for (ConversionNodeSummary child : node.getChildren()) {
        specChildren.add(toSpecNodeSummary(child));
      }
      specNode.setChildren(specChildren);
    }

    return specNode;
  }
}
