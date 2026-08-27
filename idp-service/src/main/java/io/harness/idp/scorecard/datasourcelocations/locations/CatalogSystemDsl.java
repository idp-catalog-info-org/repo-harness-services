/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasourcelocations.locations;

import static io.harness.idp.backstage.utils.BackstageUtils.getEntityUniqueId;
import static io.harness.idp.common.Constants.ERROR_MESSAGE_KEY;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.ENTITY_INCORRECT_KIND;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.SYSTEM_DOES_NOT_EXISTS;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.SYSTEM_NOT_DEFINED;

import io.harness.exception.InvalidRequestException;
import io.harness.idp.backstage.beans.MetadataFieldConstants;
import io.harness.idp.backstage.entities.BackstageCatalogEntity;
import io.harness.idp.backstage.service.impl.BackstageServiceImpl;
import io.harness.idp.catalog.beans.Kind;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.helpers.CatalogServiceHelper;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.scorecard.common.beans.DataSourceConfig;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datasourcelocations.entity.DataSourceLocationEntity;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;
import io.harness.spec.server.idp.v1.model.InputValue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.tuple.Triple;

public class CatalogSystemDsl extends DataSourceLocationNoLoop {
  @Inject BackstageServiceImpl backstageService;
  @Inject CatalogEntityRepository catalogEntityRepository;
  @Inject CatalogServiceHelper catalogServiceHelper;
  static final ObjectMapper mapper =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  @Override
  public Map<String, Object> fetchData(String accountIdentifier, Object entity,
      DataSourceLocationEntity dataSourceLocationEntity, List<DataFetchDTO> dataPointAndInputValues,
      Map<String, String> replaceableHeaders, Map<String, String> possibleReplaceableRequestBodyPairs,
      Map<String, String> possibleReplaceableUrlPairs, DataSourceConfig dataSourceConfig, boolean throughDelegate,
      Set<String> delegateSelectors) {
    Map<String, Object> ruleData = new HashMap<>();
    if (entity instanceof CatalogEntity) {
      return handleCatalogEntity((CatalogEntity) entity, ruleData);
    }
    Map<String, Object> backstageCatalogEntityObject = mapper.convertValue(entity, new TypeReference<>() {});
    Map<String, Object> specObject = (Map<String, Object>) backstageCatalogEntityObject.get("spec");

    boolean systemDefined = specObject != null && specObject.get("system") != null;
    boolean systemExists = false;

    if (systemDefined) {
      try {
        List<String> systemList = (List<String>) specObject.get("system");
        backstageService.findByAccountIdentifierAndEntityRef(accountIdentifier,
            getEntityUniqueId(BackstageCatalogEntity.getValue(((BackstageCatalogEntity) entity).getMetadata(),
                                  MetadataFieldConstants.NAMESPACE, String.class),
                "System", systemList.get(0)));
        systemExists = true;
      } catch (InvalidRequestException e) {
        systemExists = false;
      }
    }

    if (!systemDefined) {
      String entityKind = ((BackstageCatalogEntity) entity).getKind();
      Set<String> validEntityKinds = Set.of("Component", "API", "Resource");
      if (!validEntityKinds.contains(entityKind)) {
        String entityOfIncorrectKind =
            ENTITY_INCORRECT_KIND.replace("${kind}", ((BackstageCatalogEntity) entity).getKind());
        ruleData.put(ERROR_MESSAGE_KEY, entityOfIncorrectKind);
      } else {
        ruleData.put(ERROR_MESSAGE_KEY, SYSTEM_NOT_DEFINED);
      }
    } else if (!systemExists) {
      List<String> systemList = (List<String>) specObject.get("system");
      String systemDoesNotExists = SYSTEM_DOES_NOT_EXISTS.replace("${system}", systemList.get(0));
      ruleData.put(ERROR_MESSAGE_KEY, systemDoesNotExists);
    }

    return ruleData;
  }

  @Override
  protected String replaceInputValuePlaceholdersIfAnyInRequestBody(
      String requestBody, DataPointEntity dataPoint, List<InputValue> inputValues, Object entity) {
    return null;
  }

  @Override
  protected String replaceInputValuePlaceholdersIfAnyInRequestUrl(
      String url, DataPointEntity dataPoint, List<InputValue> inputValues) {
    return null;
  }

  @Override
  protected boolean validate(DataFetchDTO dataFetchDTO, Map<String, Object> data,
      Map<String, String> replaceableHeaders, Map<String, String> possibleReplaceableRequestBodyPairs,
      Map<String, String> possibleReplaceableUrlPairs) {
    return true;
  }

  @Override
  protected String getHost(Map<String, String> data) {
    return null;
  }

  @Override
  protected Map<String, Object> processResponse(Response response) {
    return Collections.emptyMap();
  }

  private Map<String, Object> handleCatalogEntity(CatalogEntity catalogEntity, Map<String, Object> ruleData) {
    List<String> systemList = (List<String>) catalogEntity.fromSpecification("system");

    boolean systemDefined = systemList != null && !systemList.isEmpty();
    boolean systemExists = false;
    List<String> missingSystems = new ArrayList<>();

    if (systemDefined) {
      systemExists = true;
      String parentUniqueId = catalogEntity.getParentUniqueId();

      for (String systemRef : systemList) {
        try {
          Triple<String, String, String> kindScopeIdentifier = catalogServiceHelper.getKindScopeIdentifier(systemRef);
          String systemIdentifier = kindScopeIdentifier.getRight();

          Optional<CatalogEntity> systemEntity = catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(
              parentUniqueId, Kind.system.name(), systemIdentifier);

          if (!systemEntity.isPresent()) {
            systemExists = false;
            missingSystems.add(systemRef);
          }
        } catch (Exception e) {
          systemExists = false;
          missingSystems.add(systemRef);
        }
      }
    }

    if (!systemDefined) {
      ruleData.put(ERROR_MESSAGE_KEY, SYSTEM_NOT_DEFINED);
    } else if (!systemExists) {
      String systemDoesNotExist = SYSTEM_DOES_NOT_EXISTS.replace("${system}", String.join(", ", missingSystems));
      ruleData.put(ERROR_MESSAGE_KEY, systemDoesNotExist);
    }

    return ruleData;
  }
}
