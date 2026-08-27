/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.states.V1.cd;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@UtilityClass
public class RenderingStepUtils {
  public final Map<String, String> MANIFEST_TYPE_TO_RENDERING_TEMPLATE = Map.of("k8s", "k8s-rendering", "helm-chart",
      "helm-rendering", "openshift", "openshift-rendering", "kustomize", "kustomize-rendering");

  public final String PLUGIN_EXECUTION_STATUS_ENV = "PLUGIN_EXECUTION_STATUS";
  public final String PLUGIN_EXECUTION_ERROR_ENV = "PLUGIN_EXECUTION_ERROR";
  public final String PLUGIN_EXECUTION_FAILURE_TYPE_ENV = "PLUGIN_EXECUTION_FAILURE_TYPE";
  public final String RENDERING_PLUGIN_TEMPLATE_FOLDER_PATH = "templates/render/";
  public final String SERVICE_OUTPUT_MANIFESTS_PRIMARY_TYPE_EXP = "${{serviceOutput.manifests.primary.uses}}";

  public void sanitizeOutputVars(Map<String, String> outputVars) {
    outputVars.remove(PLUGIN_EXECUTION_STATUS_ENV);
    outputVars.remove(PLUGIN_EXECUTION_ERROR_ENV);
    outputVars.remove(PLUGIN_EXECUTION_FAILURE_TYPE_ENV);
  }

  public void sanitizeFilePaths(Set<String> filePaths) {
    filePaths.remove(PLUGIN_EXECUTION_STATUS_ENV);
    filePaths.remove(PLUGIN_EXECUTION_ERROR_ENV);
    filePaths.remove(PLUGIN_EXECUTION_FAILURE_TYPE_ENV);
  }

  public List<String> filterPathsByFetchOutput(Map<String, Object> manifests, String key, Set<String> fetchedPaths) {
    Object value = manifests.get(key);
    if (value instanceof List) {
      return ((List<String>) value).stream().filter(fetchedPaths::contains).collect(Collectors.toList());
    }
    return new ArrayList<>();
  }

  /**
   * Appends the fetched file paths to the existing paths under the given key, preserving insertion
   * order and removing duplicates. Existing paths come first, followed by any fetched paths not
   * already present.
   */
  public List<String> mergePathsWithManifestOutput(
      Map<String, Object> manifests, String key, Set<String> fetchedPaths) {
    Set<String> merged = new LinkedHashSet<>();
    Object value = manifests.get(key);
    if (value instanceof List) {
      merged.addAll((List<String>) value);
    }
    if (isNotEmpty(fetchedPaths)) {
      merged.addAll(fetchedPaths);
    }
    return new ArrayList<>(merged);
  }
}
