/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.config;

import static io.harness.rule.OwnerRule.KAPIL_GARG;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.ModuleType;
import io.harness.category.element.UnitTests;
import io.harness.licensing.Edition;
import io.harness.rule.Owner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class AutoProvisionLicenseConfigTest {
  @Test
  @Owner(developers = KAPIL_GARG)
  @Category(UnitTests.class)
  public void testGetModulesForEdition_nullEditionModuleConfig() {
    AutoProvisionLicenseConfig config = new AutoProvisionLicenseConfig(true, null);
    List<ModuleType> result = config.getModulesForEdition(Edition.ENTERPRISE);
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = KAPIL_GARG)
  @Category(UnitTests.class)
  public void testGetModulesForEdition_editionNotPresent() {
    Map<Edition, String> editionModuleConfig = new HashMap<>();
    editionModuleConfig.put(Edition.FREE, "CD,CI");
    AutoProvisionLicenseConfig config = new AutoProvisionLicenseConfig(true, editionModuleConfig);

    List<ModuleType> result = config.getModulesForEdition(Edition.ENTERPRISE);
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = KAPIL_GARG)
  @Category(UnitTests.class)
  public void testGetModulesForEdition_validModules() {
    Map<Edition, String> editionModuleConfig = new HashMap<>();
    editionModuleConfig.put(Edition.ENTERPRISE, "CD,CI");
    AutoProvisionLicenseConfig config = new AutoProvisionLicenseConfig(true, editionModuleConfig);

    List<ModuleType> result = config.getModulesForEdition(Edition.ENTERPRISE);
    assertThat(result).containsExactly(ModuleType.CD, ModuleType.CI);
  }

  @Test
  @Owner(developers = KAPIL_GARG)
  @Category(UnitTests.class)
  public void testGetModulesForEdition_blankValue() {
    Map<Edition, String> editionModuleConfig = new HashMap<>();
    editionModuleConfig.put(Edition.ENTERPRISE, "  ");
    AutoProvisionLicenseConfig config = new AutoProvisionLicenseConfig(true, editionModuleConfig);

    List<ModuleType> result = config.getModulesForEdition(Edition.ENTERPRISE);
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = KAPIL_GARG)
  @Category(UnitTests.class)
  public void testGetModulesForEdition_invalidModuleSkipped() {
    Map<Edition, String> editionModuleConfig = new HashMap<>();
    editionModuleConfig.put(Edition.ENTERPRISE, "CD,INVALID_MODULE,CI");
    AutoProvisionLicenseConfig config = new AutoProvisionLicenseConfig(true, editionModuleConfig);

    List<ModuleType> result = config.getModulesForEdition(Edition.ENTERPRISE);
    assertThat(result).containsExactly(ModuleType.CD, ModuleType.CI);
  }

  @Test
  @Owner(developers = KAPIL_GARG)
  @Category(UnitTests.class)
  public void testGetModulesForEdition_handlesWhitespace() {
    Map<Edition, String> editionModuleConfig = new HashMap<>();
    editionModuleConfig.put(Edition.ENTERPRISE, " CD , CI , CF ");
    AutoProvisionLicenseConfig config = new AutoProvisionLicenseConfig(true, editionModuleConfig);

    List<ModuleType> result = config.getModulesForEdition(Edition.ENTERPRISE);
    assertThat(result).containsExactly(ModuleType.CD, ModuleType.CI, ModuleType.CF);
  }
}
