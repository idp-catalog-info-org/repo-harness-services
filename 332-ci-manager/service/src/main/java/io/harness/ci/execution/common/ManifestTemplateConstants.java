/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.common;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

/**
 * Constants and utilities for template-based manifest processing.
 * Contains mappings from manifest types to their capabilities.
 */
@UtilityClass
@OwnedBy(HarnessTeam.CI)
public class ManifestTemplateConstants {
  // Manifest output keys
  public static final String PRIMARY = "primary";
  public static final String OVERRIDES = "overrides";
  public static final String TO_RENDER = "toRender";
  public static final String TO_TEMPLATE = "toTemplate";

  // Inputs keys for templatized manifests
  public static final String INPUTS_KEY_PATHS = "paths";
  public static final String INPUTS_KEY_FOLDER_PATH = "folderPath";
  public static final String INPUTS_KEY_FILE_PATH = "filePath";
  // Harness File Store manifest content references (file or folder scoped paths)
  public static final String INPUTS_KEY_FILES = "files";
  // Unified store discriminator present in templatized manifest inputs
  public static final String INPUTS_KEY_STORE_TYPE = "storeType";
  // Display name of the Harness File Store unified store type (see StoreType.HARNESS)
  public static final String STORE_TYPE_HARNESS = "harness";
  public static final String INPUTS_KEY_PLUGIN_PATH = "pluginPath";
  public static final String INPUTS_KEY_OVERRIDES = "overrides";
  public static final String INPUTS_KEY_VALUES = "values";
  public static final String INPUT_KEY_VALUES_PATHS = "valuesPaths";
  public static final String INPUTS_KEY_PARAMS = "params";
  public static final String INPUTS_KEY_PARAMS_PATHS = "paramsPaths";
  public static final String INPUTS_KEY_PATCHES = "patches";
  public static final String INPUTS_KEY_PATCHES_PATHS = "patchesPaths";
  public static final String INPUTS_KEY_OVERLAY_CONFIGURATION = "overlayConfiguration";
  public static final String INPUTS_KEY_KUSTOMIZE_YAML_FOLDER_PATH = "kustomizeYamlFolderPath";

  // Output keys for manifest map
  public static final String OUTPUT_KEY_PLUGIN = "plugin";
  public static final String OUTPUT_KEY_KUSTOMIZE_YAML_FOLDER_PATH = "kustomizeYamlFolderPath";
}
