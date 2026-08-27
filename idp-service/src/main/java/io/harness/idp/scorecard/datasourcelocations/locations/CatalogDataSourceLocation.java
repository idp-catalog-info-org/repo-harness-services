/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasourcelocations.locations;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.Constants.CATALOG_IDENTIFIER;
import static io.harness.idp.common.Constants.DSL_RESPONSE;
import static io.harness.idp.common.Constants.ERROR_MESSAGE_KEY;
import static io.harness.idp.common.Constants.MISSING_DATA;

import io.harness.expression.common.ExpressionMode;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.scorecard.datasourcelocations.entity.CatalogDataSourceLocationEntity;
import io.harness.idp.scorecard.datasourcelocations.entity.DataSourceLocationEntity;
import io.harness.idp.scorecard.expression.IdpExpressionEvaluator;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;
import io.harness.spec.server.idp.v1.model.InputValue;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class CatalogDataSourceLocation implements DataSourceLocationV2 {
  // MISSING_DATA-prefixed so ScoreComputerServiceImpl classifies an unresolved/unenriched entity as missing data
  // (treated like an empty result) rather than a hard evaluation error, matching DefaultHQLParser's convention.
  static final String NO_DATA_FOR_DATA_POINT_ERROR = MISSING_DATA + ": No data found for data point";
  static final String PATH_INPUT_KEY = "path";

  @Override
  public Map<String, Object> fetchData(String accountIdentifier, Object entity,
      DataSourceLocationEntity dataSourceLocationEntity, List<DataFetchDTO> dataPointAndInputValues) {
    // Validate entity type
    if (!(entity instanceof CatalogEntity catalogEntity)) {
      log.error(
          "Entity must be of type CatalogEntity, but got: {}", entity != null ? entity.getClass().getName() : "null");
      return Map.of(ERROR_MESSAGE_KEY, "Entity must be of type CatalogEntity");
    }

    CatalogDataSourceLocationEntity catalogDataSourceLocationEntity =
        (CatalogDataSourceLocationEntity) dataSourceLocationEntity;
    String jexl = resolveJexl(catalogDataSourceLocationEntity.getJexl(), dataPointAndInputValues);

    // Extract catalog entity as map, enriching with entity identity fields for JEXL expressions
    Map<String, Object> catalog = new HashMap<>(catalogEntity.getDecoratedEntityMap());
    catalog.put("parentUniqueId", catalogEntity.getParentUniqueId());
    catalog.put("uniqueId", catalogEntity.getUniqueId());

    // Create JEXL evaluator with catalog context
    IdpExpressionEvaluator evaluator = new IdpExpressionEvaluator(Map.of(CATALOG_IDENTIFIER, catalog));

    // Resolve expression using JEXL
    Object resolvedValue;
    try {
      resolvedValue = evaluator.evaluateExpression(jexl, ExpressionMode.RETURN_NULL_IF_UNRESOLVED);
    } catch (Exception e) {
      // A missing intermediate segment (e.g. an entity that was never enriched by this integration, so
      // integration_properties has no entry for the provider) makes JEXL throw an "undefined property" error rather
      // than resolving to null. Treat that the same as an unresolved expression so callers get a consistent
      // null-result signal instead of a hard evaluation error.
      if (isUnresolvedExpression(e)) {
        log.debug("CatalogDSL: {} could not resolve jexl {} for entity: {}; treating as missing data",
            catalogDataSourceLocationEntity.getIdentifier(), jexl, catalogEntity.getIdentifier());
        return Map.of(ERROR_MESSAGE_KEY, NO_DATA_FOR_DATA_POINT_ERROR);
      }
      log.error("Exception while evaluating jexl for catalogDSL {} for entity {}",
          catalogDataSourceLocationEntity.getIdentifier(), catalogEntity.getIdentifier(), e);
      return Map.of(ERROR_MESSAGE_KEY, String.format("Exception while evaluating jexl: %s", e.getMessage()));
    }

    if (resolvedValue == null) {
      log.debug("CatalogDSL: {} resolved to null for entity: {}; treating as missing data",
          dataSourceLocationEntity.getIdentifier(), catalogEntity.getIdentifier());
      return Map.of(ERROR_MESSAGE_KEY, NO_DATA_FOR_DATA_POINT_ERROR);
    }

    return Map.of(DSL_RESPONSE, resolvedValue);
  }

  /**
   * Prefer a non-empty optional {@code path} input value (full JEXL) when present; otherwise use the DSL default.
   */
  private String resolveJexl(String defaultJexl, List<DataFetchDTO> dataPointAndInputValues) {
    if (isEmpty(dataPointAndInputValues)) {
      return defaultJexl;
    }
    List<InputValue> inputValues = dataPointAndInputValues.get(0).getInputValues();
    if (isEmpty(inputValues)) {
      return defaultJexl;
    }
    for (InputValue inputValue : inputValues) {
      if (inputValue == null || !PATH_INPUT_KEY.equals(inputValue.getKey())) {
        continue;
      }
      String path = stripSurroundingQuotes(inputValue.getValue());
      if (StringUtils.isNotBlank(path)) {
        return path;
      }
    }
    return defaultJexl;
  }

  private static String stripSurroundingQuotes(String value) {
    if (value != null && value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
      return value.substring(1, value.length() - 1);
    }
    return value;
  }

  /**
   * JEXL surfaces a missing intermediate map segment as an "undefined property" error, which the expression evaluator
   * rethrows as a HintException carrying an "unresolved expressions" message (the original JexlException is not kept in
   * the cause chain). Matching that hint text lets us treat an unenriched entity as a null result rather than a hard
   * evaluation error.
   */
  private boolean isUnresolvedExpression(Throwable throwable) {
    while (throwable != null) {
      String message = throwable.getMessage();
      if (message != null && (message.contains("unresolved expressions") || message.contains("undefined property"))) {
        return true;
      }
      throwable = throwable.getCause();
    }
    return false;
  }
}
