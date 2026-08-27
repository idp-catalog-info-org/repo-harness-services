/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.common;

import static io.harness.ci.execution.common.ManifestTemplateConstants.INPUTS_KEY_FILES;
import static io.harness.ci.execution.common.ManifestTemplateConstants.INPUTS_KEY_FILE_PATH;
import static io.harness.ci.execution.common.ManifestTemplateConstants.INPUTS_KEY_FOLDER_PATH;
import static io.harness.ci.execution.common.ManifestTemplateConstants.INPUTS_KEY_KUSTOMIZE_YAML_FOLDER_PATH;
import static io.harness.ci.execution.common.ManifestTemplateConstants.INPUTS_KEY_OVERLAY_CONFIGURATION;
import static io.harness.ci.execution.common.ManifestTemplateConstants.INPUTS_KEY_OVERRIDES;
import static io.harness.ci.execution.common.ManifestTemplateConstants.INPUTS_KEY_PARAMS;
import static io.harness.ci.execution.common.ManifestTemplateConstants.INPUTS_KEY_PARAMS_PATHS;
import static io.harness.ci.execution.common.ManifestTemplateConstants.INPUTS_KEY_PATCHES_PATHS;
import static io.harness.ci.execution.common.ManifestTemplateConstants.INPUTS_KEY_PATHS;
import static io.harness.ci.execution.common.ManifestTemplateConstants.INPUTS_KEY_VALUES;
import static io.harness.ci.execution.common.ManifestTemplateConstants.INPUT_KEY_VALUES_PATHS;
import static io.harness.ci.execution.common.ManifestTypesValidationUtils.FILES_TO_TEMPLATE_SUPPORTING_MANIFEST_TYPES;
import static io.harness.ci.execution.common.ManifestTypesValidationUtils.PARAMS_BASED_FILES_TO_TEMPLATE_TYPES;
import static io.harness.ci.execution.common.ManifestTypesValidationUtils.PATCHES_BASED_FILES_TO_TEMPLATE_TYPES;
import static io.harness.ci.execution.common.ManifestTypesValidationUtils.VALUES_BASED_FILES_TO_TEMPLATE_TYPES;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.pms.yaml.YamlUtils.NULL_STR;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.serializer.JsonUtils;
import io.harness.unified.cd.service.manifests.ManifestType;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Utilities for extracting paths, overrides, and files-to-template/render from template-based manifest inputs.
 */
@UtilityClass
@Slf4j
@OwnedBy(HarnessTeam.CI)
public class ManifestTemplatesPathsUtils {
  /**
   * Get paths from inputs map.
   * Supports both List and String values for paths/values/params keys.
   * String value can be a JSON array e.g. "[\"test/k8s/examples/simple/\"]" or a single path.
   * Falls back to folderPath (single directory) for manifest types like Kustomize and OpenShift
   * that use folderPath instead of paths in their NG CD store spec.
   */
  public static List<String> getPathsFromInputs(Map<String, Object> inputs) {
    if (inputs == null) {
      return new ArrayList<>();
    }
    for (String key : new String[] {INPUTS_KEY_PATHS}) {
      if (inputs.containsKey(key)) {
        List<String> result = getListFromInputsForKey(inputs, key);
        if (!isEmpty(result)) {
          return result;
        }
      }
    }
    // Harness File Store manifests expose their content references (file or folder scoped paths)
    // under the "files" key, mirroring the NG Harness store spec. Scope prefixes (account:/org:/project:)
    // are stripped so the resulting runner paths are clean filesystem paths.
    if (inputs.containsKey(INPUTS_KEY_FILES)) {
      List<String> result = stripScopePrefixes(getListFromInputsForKey(inputs, INPUTS_KEY_FILES));
      if (!isEmpty(result)) {
        return result;
      }
    }
    if (inputs.containsKey(INPUTS_KEY_FOLDER_PATH)) {
      Object folderPathValue = inputs.get(INPUTS_KEY_FOLDER_PATH);
      if (folderPathValue instanceof String folderPath) {
        List<String> result = parsePathsFromString(folderPath);
        if (!isEmpty(result)) {
          return result;
        }
      }
    }
    if (inputs.containsKey(INPUTS_KEY_FILE_PATH)) {
      Object filePathValue = inputs.get(INPUTS_KEY_FILE_PATH);
      if (filePathValue instanceof String filePath) {
        List<String> result = parsePathsFromString(filePath);
        if (!isEmpty(result)) {
          return result;
        }
      }
    }
    return new ArrayList<>();
  }

  /**
   * Get overrides from inputs map.
   * Supports both List and String values (e.g. JSON array string or single path) for overrides/values/params.
   */
  public static List<String> getOverridesFromInputs(Map<String, Object> inputs) {
    if (inputs == null) {
      return new ArrayList<>();
    }
    // Harness File Store override paths may carry a scope prefix; strip it so override paths
    // resolve to clean runner filesystem paths. Non-Harness (e.g. git) paths are unaffected.
    return stripScopePrefixes(getRawOverridesFromInputs(inputs));
  }

  /**
   * Same as {@link #getOverridesFromInputs(Map)} but returns the raw values without stripping any
   * Harness File Store scope prefix. Used when the scoped reference must be preserved to fetch
   * content from the File Store.
   */
  public static List<String> getRawOverridesFromInputs(Map<String, Object> inputs) {
    if (inputs == null) {
      return new ArrayList<>();
    }
    for (String key : new String[] {INPUTS_KEY_OVERRIDES, INPUTS_KEY_VALUES, INPUTS_KEY_PARAMS, INPUT_KEY_VALUES_PATHS,
             INPUTS_KEY_PARAMS_PATHS, INPUTS_KEY_PATCHES_PATHS, INPUTS_KEY_FILE_PATH}) {
      if (inputs.containsKey(key)) {
        return getListFromInputsForKey(inputs, key);
      }
    }
    return new ArrayList<>();
  }

  /**
   * Returns the raw Harness File Store manifest content references ({@code files} key), preserving
   * any scope prefix so the scoped reference can be used to fetch content from the File Store.
   */
  public static List<String> getRawHarnessFilesFromInputs(Map<String, Object> inputs) {
    if (inputs == null || !inputs.containsKey(INPUTS_KEY_FILES)) {
      return new ArrayList<>();
    }
    return getListFromInputsForKey(inputs, INPUTS_KEY_FILES);
  }

  /**
   * Strips Harness File Store scope prefixes (account:/org:/project:) from each path so that
   * scoped references resolve to clean filesystem paths on the runner. Paths without a recognized
   * scope prefix (e.g. git repository paths) are returned unchanged.
   */
  private static List<String> stripScopePrefixes(List<String> paths) {
    if (isEmpty(paths)) {
      return paths;
    }
    return paths.stream().map(ManifestTemplatesPathsUtils::stripScopePrefix).collect(Collectors.toList());
  }

  private static String stripScopePrefix(String path) {
    if (path == null) {
      return null;
    }
    return path.replaceFirst("^(account|org|project):", "");
  }

  /**
   * Check if manifest type supports files to template.
   * Mimics the instanceof WithFilesToTemplate check.
   */
  public static boolean supportsFilesToTemplate(ManifestType manifestType) {
    return FILES_TO_TEMPLATE_SUPPORTING_MANIFEST_TYPES.contains(manifestType);
  }

  /**
   * Get files to template from inputs map based on manifest type.
   * This replaces the need for WithFilesToTemplate interface in templatized flow.
   * Supports both List and String values (e.g. JSON array string or single path).
   *
   * <p>Pattern:
   * <ul>
   *   <li>K8S, HELM_CHART, SERVERLESS, AWS_SAM, VALUES → get from "values"</li>
   *   <li>PARAMS, OPENSHIFT → get from "params"</li>
   *   <li>PATCHES, KUSTOMIZE → get from "patches"</li>
   * </ul>
   *
   * <p>This mimics the behavior of WithFilesToTemplate#getFilesToTemplate() method.
   */
  public static List<String> getFilesToTemplateFromInputs(ManifestType manifestType, Map<String, Object> inputs) {
    if (inputs == null) {
      return new ArrayList<>();
    }
    String key = getInputsKeyForFilesToTemplateOrRender(manifestType);
    return key != null ? getListFromInputsForKey(inputs, key) : new ArrayList<>();
  }

  /**
   * Get files to render from inputs map based on manifest type.
   * This replaces the need for WithFilesToRender interface in templatized flow.
   * Supports both List and String values (e.g. JSON array string or single path).
   *
   * <p>Pattern:
   * <ul>
   *   <li>K8S, HELM_CHART, SERVERLESS, AWS_SAM, VALUES → get from "values"</li>
   *   <li>PARAMS, OPENSHIFT → get from "params"</li>
   *   <li>PATCHES, KUSTOMIZE → get from "patches"</li>
   * </ul>
   *
   * <p>This mimics the behavior of WithFilesToRender#getFilesToRender() method.
   * Note: Same pattern as getFilesToTemplateFromInputs since both interfaces use the same fields.
   */
  public static List<String> getFilesToRenderFromInputs(ManifestType manifestType, Map<String, Object> inputs) {
    if (inputs == null) {
      return new ArrayList<>();
    }
    String key = getInputsKeyForFilesToTemplateOrRender(manifestType);
    return key != null ? getListFromInputsForKey(inputs, key) : new ArrayList<>();
  }

  /**
   * Get list of strings from inputs for a given key.
   * Supports both List and String values (e.g. JSON array string or single path).
   */
  @SuppressWarnings("unchecked")
  private static List<String> getListFromInputsForKey(Map<String, Object> inputs, String key) {
    if (inputs == null || !inputs.containsKey(key)) {
      return new ArrayList<>();
    }
    Object value = inputs.get(key);
    if (value instanceof List) {
      return (List<String>) value;
    }
    if (value instanceof String) {
      return parsePathsFromString((String) value);
    }
    return new ArrayList<>();
  }

  /**
   * Get the inputs key (values/params/patches) for the given manifest type.
   * Used by both getFilesToTemplateFromInputs and getFilesToRenderFromInputs.
   * Returns null if manifest type is not in any of the files-to-template/render sets.
   */
  private static String getInputsKeyForFilesToTemplateOrRender(ManifestType manifestType) {
    if (VALUES_BASED_FILES_TO_TEMPLATE_TYPES.contains(manifestType)) {
      return INPUT_KEY_VALUES_PATHS;
    }
    if (PARAMS_BASED_FILES_TO_TEMPLATE_TYPES.contains(manifestType)) {
      return INPUTS_KEY_PARAMS_PATHS;
    }
    if (PATCHES_BASED_FILES_TO_TEMPLATE_TYPES.contains(manifestType)) {
      return INPUTS_KEY_PATCHES_PATHS;
    }
    return null;
  }

  /**
   * Parse a string value to list of paths.
   * Handles: (1) JSON array string e.g. "[\"path1\", \"path2\"]", (2) single path string.
   */
  private static List<String> parsePathsFromString(String value) {
    if (isEmpty(value) || NULL_STR.equals(value)) {
      return new ArrayList<>();
    }
    String trimmed = value.trim();
    if (trimmed.isEmpty()) {
      return new ArrayList<>();
    }
    if (trimmed.startsWith("[")) {
      try {
        List<String> parsed = JsonUtils.asObject(trimmed, new TypeReference<List<String>>() {});
        return parsed != null ? parsed : new ArrayList<>();
      } catch (Exception e) {
        log.debug("Failed to parse paths as JSON array, treating as single path: {}", trimmed, e);
      }
    }
    return new ArrayList<>(Collections.singletonList(trimmed));
  }

  /**
   * Extract Kustomize overlay folder path from inputs.
   * Returns null when overlayConfiguration is absent, malformed, or the folder value is blank.
   */
  public static String getKustomizeYamlFolderPathFromInputs(Map<String, Object> inputs) {
    if (isEmpty(inputs) || !inputs.containsKey(INPUTS_KEY_OVERLAY_CONFIGURATION)) {
      return null;
    }
    Object overlay = inputs.get(INPUTS_KEY_OVERLAY_CONFIGURATION);
    Map<?, ?> overlayMap;
    if (overlay instanceof Map<?, ?> map) {
      overlayMap = map;
    } else if (overlay instanceof String overlayStr && isNotEmpty(overlayStr)) {
      String trimmedOverlay = overlayStr.trim();
      if (!trimmedOverlay.startsWith("{")) {
        return null;
      }
      try {
        overlayMap = JsonUtils.asObject(trimmedOverlay, new TypeReference<Map<String, Object>>() {});
      } catch (Exception e) {
        log.debug("Failed to parse overlayConfiguration as JSON object: {}", trimmedOverlay, e);
        return null;
      }
      if (isEmpty(overlayMap)) {
        return null;
      }
    } else {
      return null;
    }
    Object folder = overlayMap.get(INPUTS_KEY_KUSTOMIZE_YAML_FOLDER_PATH);
    if (folder instanceof String folderStr && isNotEmpty(folderStr)) {
      return folderStr;
    }
    return null;
  }
}
