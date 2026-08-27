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
import io.harness.spec.server.idp.v1.model.ExportDetails;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

@Slf4j
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
public class LayoutManager {
  private final Map<String, Object> currentCopy;

  public LayoutManager(String layoutYaml) {
    Map<String, Object> parsed;
    try {
      parsed = new Yaml().load(layoutYaml);
    } catch (Exception e) {
      parsed = Map.of();
    }
    this.currentCopy = parsed;
  }

  public Map<String, Object> getCurrentLayout() {
    return currentCopy;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> findTab(String path) {
    if (path == null) {
      return null;
    }
    Map<String, Object> page = (Map<String, Object>) currentCopy.get("page");
    if (page == null) {
      return null;
    }
    List<Map<String, Object>> tabs = (List<Map<String, Object>>) page.get("tabs");
    if (tabs == null) {
      return null;
    }
    return tabs.stream().filter(tab -> path.equals(tab.get("path"))).findFirst().orElse(null);
  }

  private boolean isTabAdditive(String path) {
    return "/ci-cd".equals(path);
  }

  @SuppressWarnings("unchecked")
  public List<String> addCardToOverviewPage(ExportDetails cardDetail, String layout) {
    List<String> errors = new ArrayList<>();
    String cardName = cardDetail.getName();

    Map<String, Object> overviewTab = findTab("/");
    if (overviewTab == null) {
      errors.add("Overview tab not found, skipping auto removal");
      return errors;
    }

    List<Map<String, Object>> contents = (List<Map<String, Object>>) overviewTab.get("contents");
    if (contents == null) {
      errors.add("Overview tab has no contents, skipping auto add");
      return errors;
    }

    Map<String, Object> componentInOverview =
        contents.stream().filter(el -> cardName.equals(el.get("component"))).findFirst().orElse(null);

    if (componentInOverview != null) {
      errors.add(String.format("Card %s already present in layout %s, skipping auto add", cardName, layout));
    } else {
      boolean foundInsideEntitySwitch = false;
      for (Map<String, Object> el : contents) {
        if ("EntitySwitch".equals(el.get("component"))) {
          Map<String, Object> specs = (Map<String, Object>) el.get("specs");
          List<Map<String, Object>> cases = specs != null ? (List<Map<String, Object>>) specs.get("cases") : null;
          if (cases != null) {
            boolean found = cases.stream().anyMatch(c -> {
              Object content = c.get("content");
              return content instanceof Map && cardName.equals(((Map<String, Object>) content).get("component"));
            });
            if (found) {
              foundInsideEntitySwitch = true;
            }
          }
        }
      }
      if (!foundInsideEntitySwitch) {
        Object layoutSchemaSpecs = cardDetail.getLayoutSchemaSpecs();
        if (layoutSchemaSpecs instanceof Map) {
          contents.add((Map<String, Object>) layoutSchemaSpecs);
        }
      } else {
        errors.add(String.format("Card %s already present in layout %s, skipping auto add", cardName, layout));
      }
    }
    return errors;
  }

  @SuppressWarnings("unchecked")
  public List<String> removeCardFromOverviewPage(ExportDetails cardDetail, String layout) {
    List<String> errors = new ArrayList<>();
    String cardName = cardDetail.getName();

    Map<String, Object> overviewTab = findTab("/");
    if (overviewTab == null) {
      errors.add("Overview tab not found, skipping auto removal");
      return errors;
    }

    List<Map<String, Object>> contents = (List<Map<String, Object>>) overviewTab.get("contents");
    if (contents == null) {
      return errors;
    }

    Map<String, Object> componentInOverview =
        contents.stream().filter(el -> cardName.equals(el.get("component"))).findFirst().orElse(null);

    if (componentInOverview != null) {
      contents.remove(componentInOverview);
    } else {
      boolean foundInsideEntitySwitch = false;
      for (Map<String, Object> el : contents) {
        if ("EntitySwitch".equals(el.get("component"))) {
          Map<String, Object> specs = (Map<String, Object>) el.get("specs");
          List<Map<String, Object>> cases = specs != null ? (List<Map<String, Object>>) specs.get("cases") : null;
          if (cases != null) {
            Iterator<Map<String, Object>> it = cases.iterator();
            while (it.hasNext()) {
              Map<String, Object> c = it.next();
              Object content = c.get("content");
              if (content instanceof Map && cardName.equals(((Map<String, Object>) content).get("component"))) {
                it.remove();
                foundInsideEntitySwitch = true;
              }
            }
          }
        }
      }
      if (!foundInsideEntitySwitch) {
        errors.add(String.format("Card %s not present in layout %s, skipping auto removal", cardName, layout));
      }
    }
    return errors;
  }

  @SuppressWarnings("unchecked")
  public List<String> addTabToLayout(ExportDetails tabDetail, String layout) {
    List<String> errors = new ArrayList<>();
    String tabName = tabDetail.getName();
    String defaultRoute = tabDetail.getDefaultRoute();

    Map<String, Object> foundTab = findTab(defaultRoute);
    boolean isTabContentAdditive = isTabAdditive(defaultRoute);

    if (foundTab != null && !isTabContentAdditive) {
      errors.add(String.format("Tab %s already present in layout %s, skipping auto add", tabName, layout));
      return errors;
    }

    Map<String, Object> page = (Map<String, Object>) currentCopy.get("page");
    List<Map<String, Object>> tabs = (List<Map<String, Object>>) page.get("tabs");

    if (foundTab == null) {
      Map<String, Object> layoutSchemaSpecs = (Map<String, Object>) tabDetail.getLayoutSchemaSpecs();
      if (layoutSchemaSpecs != null) {
        Map<String, Object> newTab = new java.util.LinkedHashMap<>();
        newTab.put("name", tabName);
        newTab.put("path", defaultRoute != null ? defaultRoute : "");
        newTab.put("title", layoutSchemaSpecs.get("title"));
        newTab.put("contents", layoutSchemaSpecs.get("contents"));
        tabs.add(newTab);
      }
      return errors;
    }

    // Tab exists and is additive — merge EntitySwitch cases
    List<Map<String, Object>> foundTabContents = (List<Map<String, Object>>) foundTab.get("contents");
    if (foundTabContents == null) {
      return errors;
    }

    Map<String, Object> entitySwitch =
        foundTabContents.stream().filter(e -> "EntitySwitch".equals(e.get("component"))).findFirst().orElse(null);

    Map<String, Object> layoutSchemaSpecs = (Map<String, Object>) tabDetail.getLayoutSchemaSpecs();
    if (layoutSchemaSpecs == null || entitySwitch == null) {
      return errors;
    }

    List<Map<String, Object>> schemaContents = (List<Map<String, Object>>) layoutSchemaSpecs.get("contents");
    if (schemaContents == null || schemaContents.isEmpty()) {
      return errors;
    }

    Map<String, Object> firstContent = schemaContents.get(0);
    Map<String, Object> specsFromSchema = (Map<String, Object>) firstContent.get("specs");
    List<Map<String, Object>> casesFromSchema =
        specsFromSchema != null ? (List<Map<String, Object>>) specsFromSchema.get("cases") : List.of();

    Map<String, Object> switchSpecs = (Map<String, Object>) entitySwitch.get("specs");
    List<Map<String, Object>> existingCases =
        switchSpecs != null ? (List<Map<String, Object>>) switchSpecs.get("cases") : null;

    if (existingCases == null) {
      return errors;
    }

    for (Map<String, Object> schema : casesFromSchema) {
      boolean found = existingCases.stream().anyMatch(c -> {
        Object cIf = c.get("if");
        Object sIf = schema.get("if");
        return cIf != null && cIf.equals(sIf);
      });
      if (!found) {
        existingCases.addAll(0, casesFromSchema);
      } else {
        errors.add(String.format("%s case already exists, not adding it", schema.get("if")));
      }
    }

    return errors;
  }

  @SuppressWarnings("unchecked")
  public List<String> removeTabFromLayout(ExportDetails tabDetail, String layout) {
    List<String> errors = new ArrayList<>();
    String tabName = tabDetail.getName();
    String defaultRoute = tabDetail.getDefaultRoute();

    Map<String, Object> foundTab = findTab(defaultRoute);
    boolean isTabContentAdditive = isTabAdditive(defaultRoute);

    if (foundTab == null) {
      errors.add(String.format("Tab %s not found in layout %s, skipping removal", tabName, layout));
      return errors;
    }

    if (!isTabContentAdditive) {
      // Remove the entire tab
      Map<String, Object> page = (Map<String, Object>) currentCopy.get("page");
      List<Map<String, Object>> tabs = (List<Map<String, Object>>) page.get("tabs");
      tabs.removeIf(tab -> defaultRoute.equals(tab.get("path")));
      return errors;
    }

    // Additive tab — remove matching EntitySwitch cases
    List<Map<String, Object>> foundTabContents = (List<Map<String, Object>>) foundTab.get("contents");
    if (foundTabContents == null) {
      return errors;
    }

    Map<String, Object> entitySwitch =
        foundTabContents.stream().filter(e -> "EntitySwitch".equals(e.get("component"))).findFirst().orElse(null);

    Map<String, Object> layoutSchemaSpecs = (Map<String, Object>) tabDetail.getLayoutSchemaSpecs();
    if (layoutSchemaSpecs == null || entitySwitch == null) {
      return errors;
    }

    List<Map<String, Object>> schemaContents = (List<Map<String, Object>>) layoutSchemaSpecs.get("contents");
    if (schemaContents == null || schemaContents.isEmpty()) {
      return errors;
    }

    Map<String, Object> firstContent = schemaContents.get(0);
    Map<String, Object> specsFromSchema = (Map<String, Object>) firstContent.get("specs");
    List<Map<String, Object>> casesFromSchema =
        specsFromSchema != null ? (List<Map<String, Object>>) specsFromSchema.get("cases") : List.of();

    Map<String, Object> switchSpecs = (Map<String, Object>) entitySwitch.get("specs");
    List<Map<String, Object>> existingCases =
        switchSpecs != null ? (List<Map<String, Object>>) switchSpecs.get("cases") : null;

    if (existingCases != null) {
      existingCases.removeIf(c -> casesFromSchema.stream().anyMatch(s -> {
        Object sIf = s.get("if");
        Object cIf = c.get("if");
        return sIf != null && sIf.equals(cIf);
      }));
    }

    return errors;
  }
}
