/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.governance.services;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.backstage.utils.BackstageUtils.getEntityUniqueId;
import static io.harness.idp.common.CommonUtils.truncateEntityName;
import static io.harness.idp.common.JacksonUtils.convert;
import static io.harness.mongo.MongoConfig.DOT_REPLACEMENT;
import static io.harness.remote.client.NGRestUtils.getGeneralResponse;
import static io.harness.remote.client.NGRestUtils.getResponse;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;
import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdstage.remote.CDStageConfigClient;
import io.harness.clients.BackstageResourceClient;
import io.harness.idp.backstage.beans.BackstageCatalogEntityTypes;
import io.harness.idp.backstage.entities.BackstageCatalogEntity;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.helpers.CatalogServiceHelper;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.catalog.utils.CatalogUtils;
import io.harness.idp.common.CommonUtils;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.governance.beans.ScorecardExpandedValue;
import io.harness.idp.governance.beans.ServiceScorecards;
import io.harness.idp.governance.beans.ServiceScorecardsMapper;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.idp.scorecard.scores.service.ScoreService;
import io.harness.ng.core.dto.CDStageMetaDataDTO;
import io.harness.ng.core.dto.CdDeployStageMetadataRequestDTO;
import io.harness.pms.contracts.governance.ExpansionPlacementStrategy;
import io.harness.pms.contracts.governance.ExpansionRequestMetadata;
import io.harness.pms.sdk.core.governance.handler.ExpandedValue;
import io.harness.pms.sdk.core.governance.handler.ExpansionResponse;
import io.harness.pms.sdk.core.governance.handler.JsonExpansionHandler;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlNode;
import io.harness.spec.server.idp.v1.model.ScorecardSummaryInfo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@Slf4j
public class ScorecardExpansionHandler implements JsonExpansionHandler {
  private static final String COMPONENT = "component";
  private static final ObjectMapper mapper =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  private static final String CATALOG_API = "%s/idp/api/catalog/entities?%s";
  private static final String CD_SERVICE_ID_ANNOTATION = "metadata.annotations.harness.io/cd-serviceId";
  @Inject BackstageResourceClient backstageResourceClient;
  @Inject ScoreService scoreService;
  @Inject CDStageConfigClient cdStageConfigClient;
  @Inject NamespaceService namespaceService;
  @Inject CatalogEntityRepository catalogEntityRepository;
  @Inject IdpCommonService idpCommonService;
  @Inject CatalogServiceHelper catalogServiceHelper;
  private static final ObjectMapper objectMapper = NG_DEFAULT_OBJECT_MAPPER;

  @Override
  public ExpansionResponse expand(JsonNode fieldValue, ExpansionRequestMetadata metadata, String fqn) {
    String accountId = metadata.getAccountId();
    try {
      namespaceService.getNamespaceForAccountIdentifier(accountId);
    } catch (Exception e) {
      log.info(e.getMessage());
      return ExpansionResponse.builder().success(false).errorMessage(e.getMessage()).build();
    }
    String orgId = metadata.getOrgId();
    String projectId = metadata.getProjectId();
    String stageIdentifier = fieldValue.get("identifier").asText();
    String pipeline = metadata.getYaml().toStringUtf8();
    log.info(format("Process started for IDP Scorecard expansion for stage: [%s], account: [%s], project:[%s]",
        stageIdentifier, accountId, projectId));
    CDStageMetaDataDTO cdStageMetaDataDTO = getCDStageResponse(stageIdentifier, pipeline);
    if (cdStageMetaDataDTO == null || isInvalidResponse(cdStageMetaDataDTO)) {
      String errorMessage =
          format("Could not fetch ServiceRef and EnvironmentRef for stage: [%s], account: [%s], project:[%s]",
              stageIdentifier, accountId, projectId);
      log.error(errorMessage);
      return ExpansionResponse.builder().success(false).errorMessage(errorMessage).build();
    }
    if (isEmpty(cdStageMetaDataDTO.getServiceEnvRefList())) {
      cdStageMetaDataDTO = setServiceEnvRef(cdStageMetaDataDTO);
    }

    boolean idpV2Enabled = idpCommonService.idpV2Enabled(accountId);

    Map<String, List<ServiceScorecards>> serviceScores = new HashMap<>();
    for (CDStageMetaDataDTO.ServiceEnvRef serviceEnvRef : cdStageMetaDataDTO.getServiceEnvRefList()) {
      String serviceId = serviceEnvRef.getServiceRef();
      try {
        String uuid = fetchMappingEntity(accountId, orgId, projectId, serviceId, idpV2Enabled);
        if (uuid == null) {
          continue;
        }
        log.info("Matching backstage entity: " + uuid);
        List<ScorecardSummaryInfo> scorecardSummaryInfos = scoreService.getScoresSummaryForAnEntity(accountId, uuid);
        serviceScores.put(serviceId, ServiceScorecardsMapper.toDTO(scorecardSummaryInfos));
      } catch (Exception e) {
        log.error(format("Error while fetch catalog details for account = [%s], serviceId = [%s], error = [%s]",
                      accountId, serviceId, e.getMessage()),
            e);
      }
    }

    if (isEmpty(serviceScores)) {
      String errorMessage = "Could not find matching backstage entity or scores for given service(s)";
      log.info(errorMessage);
      return ExpansionResponse.builder().success(false).errorMessage(errorMessage).build();
    }

    ExpandedValue value = ScorecardExpandedValue.builder().serviceScores(serviceScores).build();
    log.info("Scorecard Expand json value: " + value.toJson());
    return ExpansionResponse.builder()
        .success(true)
        .key(value.getKey())
        .value(value)
        .fqn(fqn + YamlNode.PATH_SEP + YAMLFieldNameConstants.SPEC)
        .placement(ExpansionPlacementStrategy.APPEND)
        .build();
  }

  private CDStageMetaDataDTO getCDStageResponse(String stageIdentifier, String pipeline) {
    try {
      return getResponse(cdStageConfigClient.getCDStageMetaData(
          CdDeployStageMetadataRequestDTO.builder().stageIdentifier(stageIdentifier).pipelineYaml(pipeline).build()));
    } catch (Exception e) {
      String errorMessage = format(
          "Exception occurred while fetching service and environment reference for stage: [%s]", stageIdentifier);
      log.error(errorMessage, e);
      return null;
    }
  }

  private boolean isInvalidResponse(CDStageMetaDataDTO cdStageMetaDataDTO) {
    return isEmpty(cdStageMetaDataDTO.getServiceEnvRefList())
        && (Objects.isNull(cdStageMetaDataDTO.getServiceRef())
            || Objects.isNull(cdStageMetaDataDTO.getEnvironmentRef()));
  }

  private CDStageMetaDataDTO setServiceEnvRef(CDStageMetaDataDTO cdStageMetaDataDTO) {
    return CDStageMetaDataDTO.builder()
        .environmentRef(cdStageMetaDataDTO.getEnvironmentRef())
        .serviceRef(cdStageMetaDataDTO.getServiceRef())
        .serviceEnvRef(CDStageMetaDataDTO.ServiceEnvRef.builder()
                           .environmentRef(cdStageMetaDataDTO.getEnvironmentRef())
                           .serviceRef(cdStageMetaDataDTO.getServiceRef())
                           .build())
        .build();
  }

  private String fetchMappingEntity(
      String accountId, String orgId, String projectId, String serviceId, boolean idpV2Enabled) {
    if (idpV2Enabled) {
      List<CatalogEntity> entities = catalogEntityRepository.getEntitiesForArbitraryFields(
          accountId, Map.of(CD_SERVICE_ID_ANNOTATION.replace(".", DOT_REPLACEMENT), serviceId), null);
      return getUUIdForHarnessCatalogs(entities, orgId, projectId);
    } else {
      String filter = "filter=kind=" + COMPONENT + "," + CD_SERVICE_ID_ANNOTATION + "=" + serviceId;
      String url = String.format(CATALOG_API, accountId, filter);
      log.info("Making backstage API request {}: ", url);
      Object entitiesResponse = getGeneralResponse(backstageResourceClient.getCatalogEntities(url));
      List<Map<String, Object>> entities =
          objectMapper.convertValue(entitiesResponse, new TypeReference<List<Map<String, Object>>>() {});

      for (Map<String, Object> entity : entities) {
        CommonUtils.normalizeSystemField(entity);
      }

      List<BackstageCatalogEntity> backstageEntities = convert(mapper, entities, BackstageCatalogEntity.class);
      return getUUIdForBackstageCatalogs(backstageEntities, orgId, projectId);
    }
  }

  private String getUUIdForBackstageCatalogs(List<BackstageCatalogEntity> entities, String orgId, String projectId) {
    if (entities.size() == 1) {
      return getEntityUniqueId(entities.get(0));
    } else if (entities.size() > 1) {
      BackstageCatalogEntity catalogEntity = entities.stream()
                                                 .filter(entity
                                                     -> (BackstageCatalogEntityTypes.getEntityDomain(entity) != null
                                                            && Objects.equals(truncateEntityName(orgId),
                                                                BackstageCatalogEntityTypes.getEntityDomain(entity)))
                                                         && Objects.equals(truncateEntityName(projectId),
                                                             BackstageCatalogEntityTypes.getEntitySystem(entity)))
                                                 .findFirst()
                                                 .orElse(null);
      if (catalogEntity != null) {
        return getEntityUniqueId(catalogEntity);
      }
    }
    return null;
  }

  private String getUUIdForHarnessCatalogs(List<CatalogEntity> entities, String orgId, String projectId) {
    if (entities.size() == 1) {
      return CatalogUtils.getEntityUUId(entities.get(0));
    } else if (entities.size() > 1) {
      CatalogEntity catalogEntity =
          entities.stream()
              .filter(entity
                  -> (entity.getOrgIdentifier() != null && entity.getIdentifier().equals(orgId))
                      && (entity.getProjectIdentifier() != null && entity.getProjectIdentifier().equals(projectId)))
              .findFirst()
              .orElse(null);
      if (catalogEntity != null) {
        return CatalogUtils.getEntityUUId(catalogEntity);
      }
    }
    return null;
  }
}
