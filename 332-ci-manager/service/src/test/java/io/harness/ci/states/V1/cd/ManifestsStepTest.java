/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.states.V1.cd;

import static io.harness.rule.OwnerRule.FERNANDOD;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;
import io.harness.unified.cd.service.manifests.ManifestConfig;
import io.harness.unified.cd.service.manifests.ManifestType;

import java.util.HashMap;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

public class ManifestsStepTest {
  @InjectMocks private ManifestsStep manifestsStep;
  private AutoCloseable mocks;

  @Before
  public void setup() {
    mocks = MockitoAnnotations.openMocks(this);
  }

  @After
  public void tearDown() throws Exception {
    mocks.close();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void testValidateManifestConfigMap_validConfig_doesNotThrow() {
    ManifestConfig manifest = ManifestConfig.builder().id("myManifest").uses(ManifestType.K8S).build();
    Map<String, ManifestConfig> configMap = new HashMap<>();
    configMap.put("myManifest", manifest);

    assertThatCode(() -> manifestsStep.validateManifestConfigMap(configMap)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void testValidateManifestConfigMap_emptyMap_doesNotThrow() {
    assertThatCode(() -> manifestsStep.validateManifestConfigMap(new HashMap<>())).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void testValidateManifestConfigMap_emptyId_throwsInvalidRequestException() {
    ManifestConfig manifest = ManifestConfig.builder().id("").uses(ManifestType.K8S).build();
    Map<String, ManifestConfig> configMap = new HashMap<>();
    configMap.put("entry", manifest);

    assertThatThrownBy(() -> manifestsStep.validateManifestConfigMap(configMap))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Manifest id is required");
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void testValidateManifestConfigMap_nullId_throwsInvalidRequestException() {
    ManifestConfig manifest = ManifestConfig.builder().id(null).uses(ManifestType.K8S).build();
    Map<String, ManifestConfig> configMap = new HashMap<>();
    configMap.put("entry", manifest);

    assertThatThrownBy(() -> manifestsStep.validateManifestConfigMap(configMap))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Manifest id is required");
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void testValidateManifestConfigMap_idStartsWithDigit_doesNotThrow() {
    ManifestConfig manifest = ManifestConfig.builder().id("1invalidId").uses(ManifestType.K8S).build();
    Map<String, ManifestConfig> configMap = new HashMap<>();
    configMap.put("entry", manifest);

    assertThatCode(() -> manifestsStep.validateManifestConfigMap(configMap)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void testValidateManifestConfigMap_idWithDash_doesNotThrow() {
    ManifestConfig manifest = ManifestConfig.builder().id("my-manifest").uses(ManifestType.K8S).build();
    Map<String, ManifestConfig> configMap = new HashMap<>();
    configMap.put("entry", manifest);

    assertThatCode(() -> manifestsStep.validateManifestConfigMap(configMap)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void testValidateManifestConfigMap_nullUses_throwsInvalidRequestException() {
    ManifestConfig manifest = ManifestConfig.builder().id("validId").uses(null).build();
    Map<String, ManifestConfig> configMap = new HashMap<>();
    configMap.put("validId", manifest);

    assertThatThrownBy(() -> manifestsStep.validateManifestConfigMap(configMap))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Manifest uses is required");
  }
}
