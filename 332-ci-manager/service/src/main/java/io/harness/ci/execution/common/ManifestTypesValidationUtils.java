/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.common;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.unified.cd.service.manifests.ManifestType;

import java.util.Set;
import lombok.experimental.UtilityClass;

/**
 * Validation utilities for manifest types.
 * Contains manifest type sets and predicates for multiple manifests, collective paths, overrides, and files to
 * template.
 */
@UtilityClass
@OwnedBy(HarnessTeam.CI)
public class ManifestTypesValidationUtils {
  /**
   * Manifest types that allow multiple manifests of the same type.
   * These implement AllowMultipleManifests interface.
   */
  public static final Set<ManifestType> ALLOW_MULTIPLE_MANIFEST_TYPES =
      Set.of(ManifestType.PARAMS, ManifestType.VALUES, ManifestType.PATCHES);

  /**
   * Manifest types that have collective paths.
   * These implement WithCollectivePaths interface (which extends AllowMultipleManifests).
   */
  public static final Set<ManifestType> COLLECTIVE_PATHS_MANIFEST_TYPES =
      Set.of(ManifestType.PARAMS, ManifestType.VALUES, ManifestType.PATCHES);

  /**
   * Manifest types that support files to template.
   * These implement WithFilesToTemplate interface.
   */
  public static final Set<ManifestType> FILES_TO_TEMPLATE_SUPPORTING_MANIFEST_TYPES = Set.of(ManifestType.K8S,
      ManifestType.HELM_CHART, ManifestType.SERVERLESS, ManifestType.AWS_SAM, ManifestType.OPENSHIFT,
      ManifestType.KUSTOMIZE, ManifestType.PARAMS, ManifestType.VALUES, ManifestType.PATCHES);

  /**
   * Manifest types that use "values" field for files to template.
   */
  public static final Set<ManifestType> VALUES_BASED_FILES_TO_TEMPLATE_TYPES = Set.of(
      ManifestType.K8S, ManifestType.HELM_CHART, ManifestType.SERVERLESS, ManifestType.AWS_SAM, ManifestType.VALUES);

  /**
   * Manifest types that use "params" field for files to template.
   */
  public static final Set<ManifestType> PARAMS_BASED_FILES_TO_TEMPLATE_TYPES =
      Set.of(ManifestType.PARAMS, ManifestType.OPENSHIFT);

  /**
   * Manifest types that use "patches" field for files to template.
   */
  public static final Set<ManifestType> PATCHES_BASED_FILES_TO_TEMPLATE_TYPES =
      Set.of(ManifestType.PATCHES, ManifestType.KUSTOMIZE);

  /**
   * Check if manifest type allows multiple manifests.
   */
  public static boolean allowsMultipleManifests(ManifestType manifestType) {
    return ALLOW_MULTIPLE_MANIFEST_TYPES.contains(manifestType);
  }

  /**
   * Check if manifest type has collective paths.
   */
  public static boolean hasCollectivePaths(ManifestType manifestType) {
    return COLLECTIVE_PATHS_MANIFEST_TYPES.contains(manifestType);
  }
}
