/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.layout.helpers;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.spec.server.idp.v1.model.LayoutRequest;

import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

@Slf4j
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
public class LayoutServiceHelper {
  private final static Set<String> VALID_COMPONENTS = Set.of("StatsCard", "AggregationCard", "EntitiesByScopeTable",
      "HierarchicalEntitiesTable", "CustomEntityTable", "AggregatedTable");

  private final static Set<String> PROPS_OPTIONAL_COMPONENTS =
      Set.of("EntitiesByScopeTable", "HierarchicalEntitiesTable");

  private final static Set<String> VALID_CATALOG_FILTER_KEYS =
      Set.of("kind", "scopes", "type", "owner", "lifecycle", "tags", "owned_by_me", "favorites", "sort_by",
          "orgIdentifier", "projectIdentifier", "account", "entity_refs", "customFilter");

  private final static Set<String> VALID_COLUMN_TYPES = Set.of("text", "url", "count");

  private final static Set<String> VALID_PRESET_COLUMN_KEYS = Set.of(
      "NAME_COLUMN", "KIND_COLUMN", "TYPE_COLUMN", "OWNER_COLUMN", "TAGS_COLUMN", "LIFECYCLE_COLUMN", "ACTIONS_COLUMN");

  private final static Set<String> VALID_CONTENT_KEYS = Set.of("component", "specs");

  private final static Set<String> VALID_CONTENT_SPEC_KEYS = Set.of("props", "gridProps", "contents", "children");

  public boolean validateYaml(String yaml) {
    try {
      new Yaml().load(yaml);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  @SuppressWarnings("unchecked")
  public boolean hierarchyKindValidation(String entityKind, String yamlString) {
    if (!"hierarchy".equalsIgnoreCase(entityKind)) {
      return true;
    }
    try {
      Map<String, Object> root = new Yaml().load(yamlString);
      if (root == null || !(root.get("page") instanceof Map)) {
        return false;
      }
      Map<String, Object> page = (Map<String, Object>) root.get("page");
      if (!(page.get("name") instanceof String) || !(page.get("tabs") instanceof List)) {
        return false;
      }
      for (Object tabObj : (List<Object>) page.get("tabs")) {
        if (!validateTab(tabObj)) {
          return false;
        }
      }
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  @SuppressWarnings("unchecked")
  public boolean entityLayoutTypeValidation(String type, String yamlString) {
    if (!type.equals("EntityLayout")) {
      return true;
    }
    try {
      Map<String, Object> root = new Yaml().load(yamlString);
      if (root == null || !(root.get("page") instanceof Map)) {
        return false;
      }
      Map<String, Object> page = (Map<String, Object>) root.get("page");
      if (!(page.get("tabs") instanceof List)) {
        return false;
      }
      List<Object> tabs = (List<Object>) page.get("tabs");
      if (tabs.isEmpty()) {
        return false;
      }
      if (page.containsKey("name") && !(page.get("name") instanceof String)) {
        return false;
      }
      for (Object tabObj : tabs) {
        if (!validateEntityTab(tabObj)) {
          return false;
        }
      }
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  @SuppressWarnings("unchecked")
  private boolean validateTab(Object tabObj) {
    if (!(tabObj instanceof Map)) {
      return false;
    }
    Map<String, Object> tab = (Map<String, Object>) tabObj;
    if (!(tab.get("name") instanceof String) || !(tab.get("path") instanceof String)
        || !(tab.get("title") instanceof String) || !(tab.get("contents") instanceof List)) {
      return false;
    }
    for (Object contentObj : (List<Object>) tab.get("contents")) {
      if (!validateComponent(contentObj)) {
        return false;
      }
    }
    return true;
  }

  @SuppressWarnings("unchecked")
  private boolean validateComponent(Object contentObj) {
    if (!(contentObj instanceof Map)) {
      return false;
    }
    Map<String, Object> content = (Map<String, Object>) contentObj;
    Object component = content.get("component");
    if (!(component instanceof String) || !VALID_COMPONENTS.contains(component)) {
      return false;
    }
    String componentName = (String) component;
    boolean propsOptional = PROPS_OPTIONAL_COMPONENTS.contains(componentName);

    Object specsObj = content.get("specs");
    if (specsObj == null) {
      return propsOptional;
    }
    if (!(specsObj instanceof Map)) {
      return false;
    }
    Map<String, Object> specs = (Map<String, Object>) specsObj;

    if (specs.get("gridProps") != null && !(specs.get("gridProps") instanceof Map)) {
      return false;
    }

    Object propsObj = specs.get("props");
    if (propsObj == null) {
      return propsOptional;
    }
    if (!(propsObj instanceof Map)) {
      return false;
    }
    return validateComponentProps(componentName, (Map<String, Object>) propsObj);
  }

  private boolean validateComponentProps(String componentName, Map<String, Object> props) {
    return switch (componentName) {
            case "StatsCard" -> validateStatsCardProps(props);
            case "AggregationCard" -> props.get("ruleId") instanceof String;
            case "EntitiesByScopeTable" -> validateEntitiesByScopeTableProps(props);
            case "HierarchicalEntitiesTable", "AggregatedTable" -> validateHierarchicalTableProps(props);
            case "CustomEntityTable" -> validateCustomEntityTableProps(props);
            default -> false;
        };
    }

    private boolean validateStatsCardProps(Map<String, Object> props) {
        if (!(props.get("title") instanceof String) || !(props.get("value") instanceof String)) {
            return false;
        }
        return !props.containsKey("subtitle") || props.get("subtitle") instanceof String;
    }

    private boolean validateEntitiesByScopeTableProps(Map<String, Object> props) {
        if (props.containsKey("visibleFilters") && !isValidFilterKeysArray(props.get("visibleFilters"))) {
            return false;
        }
        return !props.containsKey("entityFilters") || props.get("entityFilters") instanceof Map;
    }

    private boolean validateHierarchicalTableProps(Map<String, Object> props) {
        if (props.containsKey("tableTitle") && !(props.get("tableTitle") instanceof String)) {
            return false;
        }
        if (props.containsKey("visibleFilters") && !isValidFilterKeysArray(props.get("visibleFilters"))) {
            return false;
        }
        return !props.containsKey("tableProps") || isValidTableProps(props.get("tableProps"));
    }

    private boolean validateCustomEntityTableProps(Map<String, Object> props) {
        if (!(props.get("tableTitle") instanceof String)) {
            return false;
        }
        if (props.containsKey("tableProps") && !isValidTableProps(props.get("tableProps"))) {
            return false;
        }
        if (props.containsKey("visibleFilters") && !isValidFilterKeysArray(props.get("visibleFilters"))) {
            return false;
        }
        if (props.containsKey("entityFilters") && !(props.get("entityFilters") instanceof Map)) {
            return false;
        }
        if (props.containsKey("showSearch") && !(props.get("showSearch") instanceof Boolean)) {
            return false;
        }
        return !props.containsKey("tableKey") || props.get("tableKey") instanceof String;
    }

    private boolean isValidFilterKeysArray(Object obj) {
        if (!(obj instanceof List)) {
            return false;
        }
        for (Object item : (List<?>) obj) {
                if (!(item instanceof String) || !VALID_CATALOG_FILTER_KEYS.contains(item)) {
                  return false;
                }
              }
              return true;
    }

    @SuppressWarnings("unchecked")
    private boolean isValidTableProps(Object obj) {
      if (!(obj instanceof Map)) {
        return false;
      }
      Map<String, Object> tableProps = (Map<String, Object>) obj;
      if (!tableProps.containsKey("columns")) {
        return true;
      }
      Object columns = tableProps.get("columns");
      if (!(columns instanceof List)) {
        return false;
      }
      for (Object col : (List<?>) columns) {
        if (!isValidColumnInput(col)) {
          return false;
        }
      }
      return true;
    }

    @SuppressWarnings("unchecked")
    private boolean isValidColumnInput(Object col) {
      if (col instanceof String) {
        return VALID_PRESET_COLUMN_KEYS.contains(col);
      }
      if (col instanceof Map) {
        Map<String, Object> colMap = (Map<String, Object>) col;
        if (!(colMap.get("name") instanceof String) || !(colMap.get("accessorKey") instanceof String)
            || !(colMap.get("type") instanceof String) || !VALID_COLUMN_TYPES.contains(colMap.get("type"))) {
          return false;
        }
        return !colMap.containsKey("width") || colMap.get("width") instanceof Number;
      }
      return false;
    }

    @SuppressWarnings("unchecked")
    private boolean validateEntityTab(Object tabObj) {
      if (!(tabObj instanceof Map)) {
        return false;
      }
      Map<String, Object> tab = (Map<String, Object>) tabObj;
      if (!(tab.get("title") instanceof String) || !(tab.get("path") instanceof String)) {
        return false;
      }
      if (tab.containsKey("name") && !(tab.get("name") instanceof String)) {
        return false;
      }
      if (tab.containsKey("contents")) {
        if (!(tab.get("contents") instanceof List)) {
          return false;
        }
        for (Object contentObj : (List<Object>) tab.get("contents")) {
          if (!validateEntityContent(contentObj)) {
            return false;
          }
        }
      }
      return true;
    }

    @SuppressWarnings("unchecked")
    private boolean validateEntityContent(Object contentObj) {
      if (!(contentObj instanceof Map)) {
        return false;
      }
      Map<String, Object> content = (Map<String, Object>) contentObj;
      for (String key : content.keySet()) {
        if (!VALID_CONTENT_KEYS.contains(key)) {
          return false;
        }
      }
      if (content.containsKey("component") && !(content.get("component") instanceof String)) {
        return false;
      }
      Object specsObj = content.get("specs");
      if (specsObj == null) {
        return true;
      }
      if (!(specsObj instanceof Map)) {
        return false;
      }
      String component = content.get("component") instanceof String ? (String) content.get("component") : null;
      if ("EntitySwitch".equals(component)) {
        return validateSwitchSpecs((Map<String, Object>) specsObj);
      }
      return validateContentSpecs((Map<String, Object>) specsObj);
    }

    @SuppressWarnings("unchecked")
    private boolean validateContentSpecs(Map<String, Object> specs) {
      for (String key : specs.keySet()) {
        if (!VALID_CONTENT_SPEC_KEYS.contains(key)) {
          return false;
        }
      }
      if (specs.containsKey("props") && !(specs.get("props") instanceof Map)) {
        return false;
      }
      if (specs.containsKey("gridProps") && !(specs.get("gridProps") instanceof Map)) {
        return false;
      }
      if (specs.containsKey("contents")) {
        if (!(specs.get("contents") instanceof List)) {
          return false;
        }
        for (Object item : (List<Object>) specs.get("contents")) {
          if (!validateEntityContent(item)) {
            return false;
          }
        }
      }
      if (specs.containsKey("children")) {
        if (!(specs.get("children") instanceof List)) {
          return false;
        }
        for (Object item : (List<Object>) specs.get("children")) {
          if (!validateEntityContent(item)) {
            return false;
          }
        }
      }
      return true;
    }

    @SuppressWarnings("unchecked")
    private boolean validateSwitchSpecs(Map<String, Object> specs) {
      if (!specs.containsKey("cases")) {
        return true;
      }
      Object casesObj = specs.get("cases");
      if (!(casesObj instanceof List)) {
        return false;
      }
      for (Object caseObj : (List<Object>) casesObj) {
        if (!(caseObj instanceof Map)) {
          return false;
        }
        Map<String, Object> caseMap = (Map<String, Object>) caseObj;
        if (!caseMap.containsKey("content") || !(caseMap.get("content") instanceof Map)) {
          return false;
        }
        if (caseMap.containsKey("if") && !(caseMap.get("if") instanceof String)) {
          return false;
        }
      }
      return true;
    }
  }
