/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.rbac;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.IDP)
public class KindResourceTypeMapper {
  public static final String CATALOG_RESOURCE_TYPE = "IDP_CATALOG";
  public static final String WORKFLOW_RESOURCE_TYPE = "IDP_WORKFLOW";
  public static final String ENVIRONMENT_RESOURCE_TYPE = "IDP_ENVIRONMENT";
  public static final String BLUEPRINT_RESOURCE_TYPE = "IDP_ENVIRONMENT_BLUEPRINT";
  public static final String TEAM_RESOURCE_TYPE = "IDP_TEAM";

  public static final List<String> SPECIAL_KINDS = List.of("workflow", "environment", "environmentblueprint");

  public static String resourceTypeForKind(String kind) {
    return switch (kind.toLowerCase()) {
      case "workflow" -> WORKFLOW_RESOURCE_TYPE;
      case "environment" -> ENVIRONMENT_RESOURCE_TYPE;
      case "environmentblueprint" -> BLUEPRINT_RESOURCE_TYPE;
      case "group" -> TEAM_RESOURCE_TYPE;
      default -> CATALOG_RESOURCE_TYPE;
    };
  }

  public static String permissionForResourceType(String resourceType, String action) {
    return switch (resourceType) {
      case WORKFLOW_RESOURCE_TYPE -> "idp_workflow_" + action;
      case ENVIRONMENT_RESOURCE_TYPE -> "idp_idpenvironment_" + action;
      case BLUEPRINT_RESOURCE_TYPE -> "idp_environmentblueprint_" + action;
      case TEAM_RESOURCE_TYPE -> "idp_team_" + action;
      default -> "idp_catalog_" + action;
    };
  }

  public static Map<String, List<String>> groupKindsByResourceType(List<String> kinds) {
    return kinds.stream().collect(Collectors.groupingBy(KindResourceTypeMapper::resourceTypeForKind));
  }

  public static List<String> kindsForResourceType(String resourceType) {
    return switch (resourceType) {
      case WORKFLOW_RESOURCE_TYPE -> List.of("workflow");
      case ENVIRONMENT_RESOURCE_TYPE -> List.of("environment");
      case BLUEPRINT_RESOURCE_TYPE -> List.of("environmentblueprint");
      case TEAM_RESOURCE_TYPE -> List.of("group");
      default -> null;
    };
  }
}
