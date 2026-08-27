/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.container.utils.yaml;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.common.utils.YamlParsingUtils;
import io.harness.exception.ngexception.CIStageExecutionException;

import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;

/**
 * Validates that a podSpecOverlay YAML does not contain any Kubernetes secret references.
 *
 * Customers share a Kubernetes namespace across builds. Allowing secret references in the
 * overlay YAML would let a build reference secrets created by a concurrent build, leaking
 * credentials across isolation boundaries.
 *
 * Blocked paths (all locations a PodSpec can reference a secret):
 *   spec.containers[*].env[*].valueFrom.secretKeyRef
 *   spec.initContainers[*].env[*].valueFrom.secretKeyRef
 *   spec.containers[*].envFrom[*].secretRef
 *   spec.initContainers[*].envFrom[*].secretRef
 *   spec.volumes[*].secret
 *   spec.volumes[*].projected.sources[*].secret
 */
@UtilityClass
@OwnedBy(HarnessTeam.CI)
public class OverlayYamlSecurityValidator {
  private static final String ERROR_MESSAGE =
      "podSpecOverlay contains a Kubernetes secret reference which is not permitted. "
      + "Secret references (secretKeyRef / secretRef / secret volumes) are not allowed in podSpecOverlay. "
      + "Configure secret-backed environment variables directly in the pipeline YAML.";

  public void validateNoSecretReferences(String overlayYaml) {
    if (isEmpty(overlayYaml)) {
      return;
    }

    Map<String, Object> overlayMap = YamlParsingUtils.parseYamlStringToMap(overlayYaml);
    if (isEmpty(overlayMap)) {
      return;
    }

    Map<String, Object> spec = getMap(overlayMap, "spec");
    if (isEmpty(spec)) {
      return;
    }

    checkContainersForSecretRefs(getList(spec, "containers"));
    checkContainersForSecretRefs(getList(spec, "initContainers"));
    checkContainersForSecretRefs(getList(spec, "ephemeralContainers"));
    checkVolumesForSecretRefs(getList(spec, "volumes"));
  }

  private void checkContainersForSecretRefs(List<Object> containers) {
    if (isEmpty(containers)) {
      return;
    }
    for (Object containerObj : containers) {
      Map<String, Object> container = asMap(containerObj);
      if (isEmpty(container)) {
        continue;
      }
      checkEnvForSecretKeyRef(getList(container, "env"));
      checkEnvFromForSecretRef(getList(container, "envFrom"));
    }
  }

  private void checkEnvForSecretKeyRef(List<Object> envList) {
    if (isEmpty(envList)) {
      return;
    }
    for (Object envObj : envList) {
      Map<String, Object> env = asMap(envObj);
      if (isEmpty(env)) {
        continue;
      }
      Map<String, Object> valueFrom = getMap(env, "valueFrom");
      if (!isEmpty(valueFrom) && valueFrom.containsKey("secretKeyRef")) {
        throw new CIStageExecutionException(ERROR_MESSAGE);
      }
    }
  }

  private void checkEnvFromForSecretRef(List<Object> envFromList) {
    if (isEmpty(envFromList)) {
      return;
    }
    for (Object envFromObj : envFromList) {
      Map<String, Object> envFrom = asMap(envFromObj);
      if (!isEmpty(envFrom) && envFrom.containsKey("secretRef")) {
        throw new CIStageExecutionException(ERROR_MESSAGE);
      }
    }
  }

  private void checkVolumesForSecretRefs(List<Object> volumes) {
    if (isEmpty(volumes)) {
      return;
    }
    for (Object volumeObj : volumes) {
      Map<String, Object> volume = asMap(volumeObj);
      if (isEmpty(volume)) {
        continue;
      }
      if (volume.containsKey("secret")) {
        throw new CIStageExecutionException(ERROR_MESSAGE);
      }
      Map<String, Object> projected = getMap(volume, "projected");
      if (!isEmpty(projected)) {
        checkProjectedSourcesForSecretRefs(getList(projected, "sources"));
      }
    }
  }

  private void checkProjectedSourcesForSecretRefs(List<Object> sources) {
    if (isEmpty(sources)) {
      return;
    }
    for (Object sourceObj : sources) {
      Map<String, Object> source = asMap(sourceObj);
      if (!isEmpty(source) && source.containsKey("secret")) {
        throw new CIStageExecutionException(ERROR_MESSAGE);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> getMap(Map<String, Object> parent, String key) {
    Object value = parent.get(key);
    return value instanceof Map ? (Map<String, Object>) value : null;
  }

  @SuppressWarnings("unchecked")
  private List<Object> getList(Map<String, Object> parent, String key) {
    Object value = parent.get(key);
    return value instanceof List ? (List<Object>) value : null;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> asMap(Object obj) {
    return obj instanceof Map ? (Map<String, Object>) obj : null;
  }
}
