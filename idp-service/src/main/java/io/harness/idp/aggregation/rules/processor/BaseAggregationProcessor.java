/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.aggregation.rules.processor;

import static io.harness.idp.catalog.utils.Constants.METADATA;
import static io.harness.idp.common.CommonUtils.buildMap;
import static io.harness.idp.common.CommonUtils.findObjectByName;
import static io.harness.idp.common.Constants.DOT_SEPARATOR;
import static io.harness.idp.common.Constants.PROCESSED_DATA;
import static io.harness.idp.common.YamlUtils.mergeDecorator;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.idp.aggregation.rules.beans.AggregationRulesDTO;
import io.harness.idp.aggregation.rules.calculator.AggregationCalculator;
import io.harness.idp.aggregation.rules.calculator.AggregationRulesCalculatorFactory;
import io.harness.idp.aggregation.rules.entity.AggregationRuleEntity;
import io.harness.idp.aggregation.rules.helper.AggregationRulesHelper;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.GitReferencedCatalogEntity;
import io.harness.idp.catalog.entities.InlineCatalogEntity;
import io.harness.idp.catalog.utils.CatalogUtils;
import io.harness.idp.ccp.utils.CatalogCustomPropertiesUtils;
import io.harness.idp.scorecard.scores.entity.ScoreEntity;
import io.harness.idp.scorecard.scores.repositories.ScoreEntityByEntityIdentifier;
import io.harness.idp.scorecard.scores.repositories.ScoreRepository;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
public abstract class BaseAggregationProcessor implements AggregationProcessor {
  protected final AggregationCalculator calculator;
  protected final AggregationRulesHelper aggregationRulesHelper;
  protected final AggregationRuleEntity aggregationRuleEntity;
  protected final ScoreRepository scoreRepository;
  private static final Gson gson = new Gson();

  public BaseAggregationProcessor(AggregationRulesHelper aggregationRulesHelper,
      AggregationRuleEntity aggregationRuleEntity, ScoreRepository scoreRepository) {
    this.aggregationRulesHelper = aggregationRulesHelper;
    this.calculator = AggregationRulesCalculatorFactory.getCalculator(aggregationRuleEntity.getAggFormula());
    this.aggregationRuleEntity = aggregationRuleEntity;
    this.scoreRepository = scoreRepository;
  }

  public Map<String, Double> extractMetrics(Set<CatalogEntity> entities) {
    switch (this.aggregationRuleEntity.getAggregationType()) {
      case METRIC -> {
        return extractCatalogMetric(entities);
      }
      case SCORECARD -> {
        return extractScorecardMetric(entities);
      }
      default -> {
        return null;
      }
    }
  }

  public Map<String, Double> extractScorecardMetric(Set<CatalogEntity> entities){
    Map<String, Double> scorecardMetrics = new HashMap<>();
    if (entities.isEmpty()) {
      return scorecardMetrics;
    }
    String accountIdentifier = entities.iterator().next().getAccountIdentifier();
    String scorecardIdentifier = this.aggregationRuleEntity.getFieldForAgg();
    List<ScoreEntityByEntityIdentifier> scoreByEntityIdentifiers = scoreRepository.getLatestScorePerEntityForScorecard(accountIdentifier, scorecardIdentifier);
    Map<String,Double> scorecardMetricsByEntityIdentifier = new HashMap<>();

    for (ScoreEntityByEntityIdentifier scoreByEntity : scoreByEntityIdentifiers) {
          String entityIdentifier = scoreByEntity.getEntityIdentifier();
          ScoreEntity scoreEntity = scoreByEntity.getScoreEntity();
          scorecardMetricsByEntityIdentifier.put(entityIdentifier, 1.0 * scoreEntity.getScore());
        }

        for (CatalogEntity entity : entities) {
          String entityIdentifier = CatalogUtils.getEntityUUId(entity);
          try {
            Double scorecardMetric = scorecardMetricsByEntityIdentifier.get(entityIdentifier);
            if (scorecardMetric != null) {
              scorecardMetrics.put(entityIdentifier, scorecardMetric);
            }
          } catch (Exception e) {
            log.error("Error extracting scorecard metric for entity {}: {}", entity.getUniqueId(), e.getMessage());
          }
        }
        return scorecardMetrics;
    }

    public Map<String, Double> extractCatalogMetric(Set<CatalogEntity> entities) {
      Map<String, Double> catalogMetrics = new HashMap<>();
      for (CatalogEntity catalogEntity : entities) {
        String entityIdentifier = CatalogUtils.getEntityUUId(catalogEntity);
        try {
          if (catalogEntity.getDecoratedEntityMap() != null && catalogEntity.getDecoratedEntityMap() != null) {
            Optional<Double> metric =
                getMetric(catalogEntity.getDecoratedEntityMap(), this.aggregationRuleEntity.getFieldForAgg());
            if (metric.isPresent()) {
              catalogMetrics.put(entityIdentifier, metric.get());
            }
          }
        } catch (Exception e) {
          log.error("Error extracting metric for entity {}: {}", catalogEntity.getUniqueId(), e.getMessage());
        }
      }
      return catalogMetrics;
    }

    @SuppressWarnings("unchecked")
    public Optional<Double> getMetric(Map<String, Object> entityMap, String field) {
      String[] keys = field.split("\\.");
      if (entityMap == null || keys.length == 0) {
        return Optional.empty();
      }

      Object current = entityMap;
      for (String key : keys) {
        if (current instanceof Map) {
          Map<String, Object> currentMap = (Map<String, Object>) current;
          current = currentMap.get(key);
          if (current == null) {
            return Optional.empty();
          }
        } else {
          return Optional.empty();
        }
      }

      try {
        return parseMetric(current);
      } catch (ClassCastException e) {
        return Optional.empty();
      }
    }

    private Optional<Double> parseMetric(Object metric) {
      if (metric == null)
        return Optional.empty();

      try {
        if (metric instanceof Number) {
          double value = ((Number) metric).doubleValue();
          return value >= 0 ? Optional.of(value) : Optional.empty();
        }

        if (metric instanceof String) {
          String str = ((String) metric).trim();
          if (!str.isEmpty()) {
            double value = Double.parseDouble(str);
            return value >= 0 ? Optional.of(value) : Optional.empty();
          }
        }

        if (metric instanceof Boolean) {
          return Optional.of(((Boolean) metric) ? 1.0 : 0.0);
        }
      } catch (NumberFormatException e) {
        // Invalid number format, skip
      }

      return Optional.empty();
    }

    public CatalogEntity modifyCatalogEntity(CatalogEntity catalogEntity, AggregationRulesDTO aggregationRulesDTO) {
      CatalogEntity deepCopyCatalogEntity = deepCopy(catalogEntity);
      Map<String, Object> decorator = deepCopyCatalogEntity.getFailSafeDecorator();
      Map<String, Object> processedData = deepCopyCatalogEntity.getFailSafeProcessedData(decorator);
      String metadataKey = getMetadataKey(aggregationRuleEntity);
      if (aggregationRulesDTO.getOperation().equals(AggregationRulesDTO.UpdateOperation.INGEST)) {
        Map<String, Object> constructMap = buildMap(metadataKey, aggregationRulesDTO.getAggregationValue());
        CatalogCustomPropertiesUtils.removeProperties(processedData, Collections.singletonList(metadataKey));
        Map<String, Object> merged = mergeDecorator(constructMap, processedData);
        decorator.put(PROCESSED_DATA, merged);
      } else if (aggregationRulesDTO.getOperation().equals(AggregationRulesDTO.UpdateOperation.DELETE)) {
        CatalogCustomPropertiesUtils.removeProperties(processedData, Collections.singletonList(metadataKey));
        decorator.put(PROCESSED_DATA, processedData);
      } else if (aggregationRulesDTO.getOperation().equals(AggregationRulesDTO.UpdateOperation.RENAME)) {
        String oldMetadataKey = getMetadataKey(aggregationRulesDTO.getOldName());
        Double value = (Double) findObjectByName(processedData, aggregationRulesDTO.getOldName());
        CatalogCustomPropertiesUtils.removeProperties(processedData, Collections.singletonList(oldMetadataKey));
        if (value != null) {
          Map<String, Object> constructMap = buildMap(metadataKey, value);
          Map<String, Object> merged = mergeDecorator(constructMap, processedData);
          decorator.put(PROCESSED_DATA, merged);
        } else {
          decorator.put(PROCESSED_DATA, processedData);
        }
      }
      deepCopyCatalogEntity.setDecorator(decorator);
      return deepCopyCatalogEntity;
    }

    private CatalogEntity deepCopy(CatalogEntity catalogEntity) {
      Type mapType;
      if (catalogEntity instanceof InlineCatalogEntity) {
        mapType = new TypeToken<InlineCatalogEntity>() {}.getType();
      } else {
        mapType = new TypeToken<GitReferencedCatalogEntity>() {}.getType();
      }
      return gson.fromJson(gson.toJson(catalogEntity), mapType);
    }

    private String getMetadataKey(AggregationRuleEntity aggregationRuleEntity) {
      return getMetadataKey(aggregationRuleEntity.getName());
    }

    private String getMetadataKey(String name) {
      return METADATA + DOT_SEPARATOR + name;
    }
  }
