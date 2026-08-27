/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.beans;

import static io.harness.rule.OwnerRule.KOTA_KARTHIK;
import static io.harness.rule.OwnerRule.SATHISH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class KindTest extends CategoryTest {
  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetDisplayNameForKind() {
    assertThat(Kind.getDisplayNameForKind(Kind.user)).isEqualTo("Users");
    assertThat(Kind.getDisplayNameForKind(Kind.group)).isEqualTo("User Groups");
    assertThat(Kind.getDisplayNameForKind(Kind.component)).isEqualTo("Components");
    assertThat(Kind.getDisplayNameForKind(Kind.api)).isEqualTo("APIs");
    assertThat(Kind.getDisplayNameForKind(Kind.workflow)).isEqualTo("Workflows");
    assertThat(Kind.getDisplayNameForKind(Kind.resource)).isEqualTo("Resources");
    assertThat(Kind.getDisplayNameForKind(Kind.environmentblueprint)).isEqualTo("Environment Blueprints");
    assertThat(Kind.getDisplayNameForKind(Kind.environment)).isEqualTo("Environments");
    assertThat(Kind.getDisplayNameForKind(Kind.system)).isEqualTo("Systems");
    assertThat(Kind.getDisplayNameForKind(Kind.aiasset)).isEqualTo("AI Assets");
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetDescriptionForKind() {
    assertThat(Kind.getDescriptionForKind(Kind.user)).isEqualTo("");
    assertThat(Kind.getDescriptionForKind(Kind.group)).isEqualTo("");
    assertThat(Kind.getDescriptionForKind(Kind.component)).isEqualTo("");
    assertThat(Kind.getDescriptionForKind(Kind.api)).isEqualTo("");
    assertThat(Kind.getDescriptionForKind(Kind.workflow)).isEqualTo("");
    assertThat(Kind.getDescriptionForKind(Kind.resource)).isEqualTo("");
    assertThat(Kind.getDescriptionForKind(Kind.environmentblueprint)).isEqualTo("");
    assertThat(Kind.getDescriptionForKind(Kind.environment)).isEqualTo("");
    assertThat(Kind.getDescriptionForKind(Kind.system)).isEqualTo("");
    assertThat(Kind.getDescriptionForKind(Kind.aiasset)).isEqualTo("Functional, reusable AI components");
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetBackstageNamingForKind() {
    assertThat(Kind.getBackstageNamingForKind(Kind.user)).isEqualTo("user");
    assertThat(Kind.getBackstageNamingForKind(Kind.group)).isEqualTo("group");
    assertThat(Kind.getBackstageNamingForKind(Kind.component)).isEqualTo("component");
    assertThat(Kind.getBackstageNamingForKind(Kind.api)).isEqualTo("api");
    assertThat(Kind.getBackstageNamingForKind(Kind.workflow)).isEqualTo("template");
    assertThat(Kind.getBackstageNamingForKind(Kind.resource)).isEqualTo("resource");
    assertThat(Kind.getBackstageNamingForKind(Kind.environmentblueprint)).isEqualTo("");
    assertThat(Kind.getBackstageNamingForKind(Kind.environment)).isEqualTo("");
    assertThat(Kind.getBackstageNamingForKind(Kind.system)).isEqualTo("system");
    assertThat(Kind.getBackstageNamingForKind(Kind.aiasset)).isEqualTo("aiasset");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testFromHarnessNaming() {
    assertThat(Kind.fromHarnessNaming("User")).isEqualTo(Kind.user);
    assertThat(Kind.fromHarnessNaming("user")).isEqualTo(Kind.user);
    assertThat(Kind.fromHarnessNaming("Group")).isEqualTo(Kind.group);
    assertThat(Kind.fromHarnessNaming("group")).isEqualTo(Kind.group);
    assertThat(Kind.fromHarnessNaming("Component")).isEqualTo(Kind.component);
    assertThat(Kind.fromHarnessNaming("component")).isEqualTo(Kind.component);
    assertThat(Kind.fromHarnessNaming("API")).isEqualTo(Kind.api);
    assertThat(Kind.fromHarnessNaming("api")).isEqualTo(Kind.api);
    assertThat(Kind.fromHarnessNaming("Workflow")).isEqualTo(Kind.workflow);
    assertThat(Kind.fromHarnessNaming("workflow")).isEqualTo(Kind.workflow);
    assertThat(Kind.fromHarnessNaming("Resource")).isEqualTo(Kind.resource);
    assertThat(Kind.fromHarnessNaming("resource")).isEqualTo(Kind.resource);
    assertThat(Kind.fromHarnessNaming("EnvironmentBlueprint")).isEqualTo(Kind.environmentblueprint);
    assertThat(Kind.fromHarnessNaming("environmentblueprint")).isEqualTo(Kind.environmentblueprint);
    assertThat(Kind.fromHarnessNaming("Environment")).isEqualTo(Kind.environment);
    assertThat(Kind.fromHarnessNaming("environment")).isEqualTo(Kind.environment);
    assertThat(Kind.fromHarnessNaming("System")).isEqualTo(Kind.system);
    assertThat(Kind.fromHarnessNaming("system")).isEqualTo(Kind.system);
    assertThat(Kind.fromHarnessNaming("AIAsset")).isEqualTo(Kind.aiasset);
    assertThat(Kind.fromHarnessNaming("aiasset")).isEqualTo(Kind.aiasset);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testFromHarnessNamingInvalidKind() {
    assertThatThrownBy(() -> Kind.fromHarnessNaming("InvalidKind"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Kind InvalidKind is not supported in IDP2.0");

    assertThatThrownBy(() -> Kind.fromHarnessNaming(""))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Kind  is not supported in IDP2.0");

    assertThatThrownBy(() -> Kind.fromHarnessNaming(null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Kind null is not supported in IDP2.0");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testKindEnumValues() {
    assertThat(Kind.values()).hasSize(11);
    assertThat(Kind.values())
        .containsExactly(Kind.user, Kind.group, Kind.component, Kind.api, Kind.workflow, Kind.resource,
            Kind.environmentblueprint, Kind.environment, Kind.system, Kind.hierarchy, Kind.aiasset);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testKindGetters() {
    assertThat(Kind.user.getDisplayName()).isEqualTo("Users");
    assertThat(Kind.user.getDescription()).isEqualTo("");
    assertThat(Kind.user.getBackstageNaming()).isEqualTo("User");
    assertThat(Kind.user.getHarnessNaming()).isEqualTo("User");

    assertThat(Kind.workflow.getDisplayName()).isEqualTo("Workflows");
    assertThat(Kind.workflow.getDescription()).isEqualTo("");
    assertThat(Kind.workflow.getBackstageNaming()).isEqualTo("Template");
    assertThat(Kind.workflow.getHarnessNaming()).isEqualTo("Workflow");
  }
}
