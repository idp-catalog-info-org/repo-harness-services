/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.common;

import static io.harness.rule.OwnerRule.CHIRAG_S;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.unified.cd.service.manifests.ManifestType;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ManifestTypesValidationUtilsTest {
  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testAllowsMultipleManifests() {
    assertThat(ManifestTypesValidationUtils.allowsMultipleManifests(ManifestType.PARAMS)).isTrue();
    assertThat(ManifestTypesValidationUtils.allowsMultipleManifests(ManifestType.VALUES)).isTrue();
    assertThat(ManifestTypesValidationUtils.allowsMultipleManifests(ManifestType.PATCHES)).isTrue();
    assertThat(ManifestTypesValidationUtils.allowsMultipleManifests(ManifestType.K8S)).isFalse();
    assertThat(ManifestTypesValidationUtils.allowsMultipleManifests(ManifestType.HELM_CHART)).isFalse();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHasCollectivePaths() {
    assertThat(ManifestTypesValidationUtils.hasCollectivePaths(ManifestType.PARAMS)).isTrue();
    assertThat(ManifestTypesValidationUtils.hasCollectivePaths(ManifestType.VALUES)).isTrue();
    assertThat(ManifestTypesValidationUtils.hasCollectivePaths(ManifestType.PATCHES)).isTrue();
    assertThat(ManifestTypesValidationUtils.hasCollectivePaths(ManifestType.K8S)).isFalse();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testFilesToTemplateSupportingTypes() {
    assertThat(ManifestTypesValidationUtils.FILES_TO_TEMPLATE_SUPPORTING_MANIFEST_TYPES)
        .contains(ManifestType.K8S, ManifestType.HELM_CHART, ManifestType.KUSTOMIZE);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValuesBasedFilesToTemplateTypes() {
    assertThat(ManifestTypesValidationUtils.VALUES_BASED_FILES_TO_TEMPLATE_TYPES)
        .contains(ManifestType.K8S, ManifestType.HELM_CHART, ManifestType.VALUES);
    assertThat(ManifestTypesValidationUtils.PARAMS_BASED_FILES_TO_TEMPLATE_TYPES)
        .contains(ManifestType.PARAMS, ManifestType.OPENSHIFT);
    assertThat(ManifestTypesValidationUtils.PATCHES_BASED_FILES_TO_TEMPLATE_TYPES)
        .contains(ManifestType.PATCHES, ManifestType.KUSTOMIZE);
  }
}
