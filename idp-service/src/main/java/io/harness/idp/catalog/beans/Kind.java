/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;

import lombok.Getter;

@Getter
@OwnedBy(HarnessTeam.IDP)
public enum Kind {
  user("Users", "", "User", "User"),
  group("User Groups", "", "Group", "Group"),
  component("Components", "", "Component", "Component"),
  api("APIs", "", "API", "API"),
  workflow("Workflows", "", "Template", "Workflow"),
  resource("Resources", "", "Resource", "Resource"),
  environmentblueprint("Environment Blueprints", "", "", "EnvironmentBlueprint"),
  environment("Environments", "", "", "Environment"),
  system("Systems", "", "System", "System"),
  hierarchy("Hierarchies", "", "Hierarchy", "Hierarchy"),
  aiasset("AI Assets", "Functional, reusable AI components", "AIAsset", "AIAsset");

  private final String displayName;
  private final String description;
  private final String backstageNaming;
  private final String harnessNaming;

  Kind(String displayName, String description, String backstageNaming, String harnessNaming) {
    this.displayName = displayName;
    this.description = description;
    this.backstageNaming = backstageNaming;
    this.harnessNaming = harnessNaming;
  }

  public static String getDisplayNameForKind(Kind kind) {
    return kind.getDisplayName();
  }

  public static String getDescriptionForKind(Kind kind) {
    return kind.getDescription();
  }

  public static String getBackstageNamingForKind(Kind kind) {
    return kind.getBackstageNaming().toLowerCase();
  }

  public static Kind fromHarnessNaming(String value) {
    for (Kind kind : Kind.values()) {
      if (kind.getHarnessNaming().equalsIgnoreCase(value)) {
        return kind;
      }
    }
    throw new InvalidRequestException(String.format("Kind %s is not supported in IDP2.0", value));
  }
}
