/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasourcelocations.locations;

import static io.harness.idp.common.Constants.CATALOG_IDENTIFIER;
import static io.harness.idp.common.Constants.DSL_RESPONSE;
import static io.harness.idp.common.Constants.ERROR_MESSAGE_KEY;
import static io.harness.idp.common.Constants.EXPRESSION_PATTERN;

import io.harness.expression.common.ExpressionMode;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.scorecard.datasourcelocations.entity.DataSourceLocationEntity;
import io.harness.idp.scorecard.datasourcelocations.entity.HQLDataSourceLocationEntity;
import io.harness.idp.scorecard.datasourcelocations.service.IdpAnalyticsService;
import io.harness.idp.scorecard.expression.IdpExpressionEvaluator;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;
import io.harness.spec.server.idp.v1.model.InputValue;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class HQLDataSourceLocation implements DataSourceLocationV2 {
  private final IdpAnalyticsService analyticsService;

  @Override
  public Map<String, Object> fetchData(String accountIdentifier, Object entity,
      DataSourceLocationEntity dataSourceLocationEntity, List<DataFetchDTO> dataPointAndInputValues) {
    // Validate entity type
    if (!(entity instanceof CatalogEntity)) {
      log.error(
          "Entity must be of type CatalogEntity, but got: {}", entity != null ? entity.getClass().getName() : "null");
      return Map.of(ERROR_MESSAGE_KEY, "Entity must be of type CatalogEntity");
    }

    // Extract HQL template
    HQLDataSourceLocationEntity hqlDataSourceLocationEntity = (HQLDataSourceLocationEntity) dataSourceLocationEntity;
    String hqlTemplate = hqlDataSourceLocationEntity.getHqlTemplate();

    // Replace placeholders in HQL template
    String hql;
    try {
      hql = replacePlaceholders(hqlTemplate, (CatalogEntity) entity, dataPointAndInputValues);
      log.info("Resolved HQL query: {}", hql);
    } catch (Exception e) {
      log.error("Failed to replace placeholders in HQL template: {}", hqlTemplate, e);
      return Map.of(ERROR_MESSAGE_KEY, "Failed to resolve placeholders: " + e.getMessage());
    }

    // Execute HQL query
    List<Map<String, Object>> result;
    try {
      result = analyticsService.execute(hql, accountIdentifier);
    } catch (Exception e) {
      log.error("Failed to execute HQL query: {}", hql, e);
      return Map.of(ERROR_MESSAGE_KEY, "Failed to execute HQL query: " + e.getMessage());
    }

    // Validate and return results
    if (result == null || result.isEmpty()) {
      log.warn("HQL query returned no results: {}", hql);
      return Map.of(ERROR_MESSAGE_KEY, "HQL query returned no results");
    }

    return Map.of(DSL_RESPONSE, result.get(0));
  }

  /**
   * Replaces placeholders in HQL template with values from catalog entity.
   * Supports type-aware formatting for String, Number, Boolean, and List types.
   *
   * @param hqlTemplate HQL template with <+catalog.x.y> placeholders
   * @param catalogEntity CatalogEntity containing data for placeholder resolution
   * @return HQL query with all placeholders replaced
   * @throws IllegalArgumentException if placeholder resolution fails
   */
  private String replacePlaceholders(
      String hqlTemplate, CatalogEntity catalogEntity, List<DataFetchDTO> dataPointAndInputValues) {
    // Extract catalog entity as map, enriching with entity identity fields for HQL placeholders
    Map<String, Object> catalog = new HashMap<>(catalogEntity.getDecoratedEntityMap());
    catalog.put("parentUniqueId", catalogEntity.getParentUniqueId());
    catalog.put("uniqueId", catalogEntity.getUniqueId());

    // Build inputs context from datapoint input values
    Map<String, Object> inputs = buildInputsContext(dataPointAndInputValues);

    // Create JEXL evaluator with catalog and inputs context
    Map<String, Map<String, Object>> context = new HashMap<>();
    context.put(CATALOG_IDENTIFIER, catalog);
    if (!inputs.isEmpty()) {
      context.put("inputs", inputs);
    }
    IdpExpressionEvaluator evaluator = new IdpExpressionEvaluator(context);

    // Find and replace all <+...> expressions
    Matcher matcher = EXPRESSION_PATTERN.matcher(hqlTemplate);
    StringBuffer resolvedHql = new StringBuffer();

    while (matcher.find()) {
      String fullMatch = matcher.group(0); // e.g., "<+catalog.metadata.name>"
      String expression = matcher.group(1); // e.g., "catalog.metadata.name"

      try {
        // Resolve expression using JEXL
        Object resolvedValue = evaluator.evaluateExpression(expression, ExpressionMode.RETURN_NULL_IF_UNRESOLVED);

        if (resolvedValue == null) {
          throw new IllegalArgumentException("Failed to resolve placeholder: " + fullMatch);
        }

        // For input values, use as-is (already in HQL format); for catalog values, apply SQL formatting
        String formattedValue;
        if (expression.startsWith("inputs.")) {
          String raw = resolvedValue.toString();
          if (raw.length() >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
            raw = raw.substring(1, raw.length() - 1);
          }
          formattedValue = raw;
        } else {
          formattedValue = formatForSql(resolvedValue);
        }
        matcher.appendReplacement(resolvedHql, Matcher.quoteReplacement(formattedValue));

      } catch (Exception e) {
        log.error("Error resolving expression: {}", expression, e);
        throw new IllegalArgumentException("Failed to resolve placeholder: " + fullMatch + " - " + e.getMessage(), e);
      }
    }
    matcher.appendTail(resolvedHql);

    return resolvedHql.toString();
  }

  private Map<String, Object> buildInputsContext(List<DataFetchDTO> dataPointAndInputValues) {
    Map<String, Object> inputs = new HashMap<>();
    if (dataPointAndInputValues == null) {
      return inputs;
    }
    for (DataFetchDTO dto : dataPointAndInputValues) {
      if (dto.getInputValues() == null) {
        continue;
      }
      for (InputValue iv : dto.getInputValues()) {
        String value = iv.getValue();
        inputs.put(iv.getKey(), value);
      }
    }
    return inputs;
  }

  private String formatForSql(Object value) {
    if (value == null) {
      return "NULL";
    }

    // Handle String - quote and escape
    if (value instanceof String) {
      String str = (String) value;
      // SQL escape: ' -> '', \ -> \\
      String escaped = str.replace("\\", "\\\\").replace("'", "''");
      return "'" + escaped + "'";
    }

    // Handle Number - direct conversion (no quotes)
    if (value instanceof Number) {
      return value.toString();
    }

    // Handle Boolean - uppercase TRUE/FALSE (no quotes)
    if (value instanceof Boolean) {
      return ((Boolean) value) ? "TRUE" : "FALSE";
    }

    // Handle List - comma-separated with recursive type formatting
    if (value instanceof List) {
      List<?> list = (List<?>) value;
      if (list.isEmpty()) {
        // Empty lists cannot form a valid SQL IN (...) clause; caller must treat this datapoint as N/A.
        throw new IllegalArgumentException("HQL placeholder resolved to an empty list; the entity has no values for "
            + "this field, so the check cannot be evaluated.");
      }

      StringBuilder formatted = new StringBuilder();
      for (int i = 0; i < list.size(); i++) {
        if (i > 0) {
          formatted.append(", ");
        }
        formatted.append(formatForSql(list.get(i)));
      }
      return formatted.toString();
    }

    // Unsupported type - log warning and convert to string
    log.warn("Unsupported type for SQL formatting: {}, converting to string", value.getClass().getName());
    return formatForSql(value.toString());
  }
}
