/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.common;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.pms.yaml.ParameterField.isNotNull;

import io.harness.cd.beans.outcomes.ServiceConfigOutcome;
import io.harness.exception.InvalidRequestException;
import io.harness.unified.cd.service.annotations.ObjectFlattener;
import io.harness.unified.cd.service.configfiles.ConfigFile;
import io.harness.unified.cd.service.spec.ServiceSpec;
import io.harness.unified.cd.service.spec.SpotServiceSpec;
import io.harness.unified.cd.service.startupscript.StartupScriptCodeStoreConfig;
import io.harness.unified.cd.service.startupscript.StartupScriptConfiguration;
import io.harness.unified.cd.service.startupscript.StartupScriptStoreType;
import io.harness.unified.cd.service.startupscript.StartupScriptStoreWrapper;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;

/** Resolves unified Spot {@code startup-script} into {@code service.startupScript.paths}. */
@UtilityClass
public class SpotStartupScriptHelper {
  public static final String PATHS_KEY = "paths";
  public static final String STORE_TYPE_KEY = "storeType";
  public static final String STARTUP_SCRIPT_CODE_ACTION = "startup-script-code";
  public static final String STARTUP_SCRIPT_UNIT_ID = "startupScript";

  public static boolean hasStartupScript(ServiceSpec spec) {
    return SpotServiceSpec.hasStartupScript(spec);
  }

  public static boolean requiresCodeFetch(StartupScriptConfiguration startupScript) {
    return startupScript != null && startupScript.getStore() != null
        && StartupScriptStoreType.CODE == startupScript.getStore().getUses();
  }

  public static Map<String, Object> buildInitialOutcome(StartupScriptConfiguration startupScript) {
    Map<String, Object> outcome = new HashMap<>();
    if (startupScript == null || startupScript.getStore() == null) {
      return outcome;
    }
    StartupScriptStoreWrapper store = startupScript.getStore();
    outcome.put(STORE_TYPE_KEY, store.getUses().getDisplayName());
    if (!(store.getWith() instanceof StartupScriptCodeStoreConfig codeStore)) {
      return outcome;
    }
    outcome.put("action", STARTUP_SCRIPT_CODE_ACTION);
    if (isNotNull(codeStore.getRepo()) && isNotEmpty((String) codeStore.getRepo().fetchFinalValue())) {
      outcome.put("repoName", codeStore.getRepo().fetchFinalValue());
    }
    if (isNotNull(codeStore.getBranch()) && isNotEmpty((String) codeStore.getBranch().fetchFinalValue())) {
      outcome.put("branch", codeStore.getBranch().fetchFinalValue());
    }
    if (isNotNull(codeStore.getCommitId()) && isNotEmpty((String) codeStore.getCommitId().fetchFinalValue())) {
      outcome.put("commitId", codeStore.getCommitId().fetchFinalValue());
    }
    if (isNotNull(codeStore.getPaths()) && isNotEmpty(codeStore.getPaths().obtainValue())) {
      outcome.put(PATHS_KEY, codeStore.getPaths().obtainValue());
    }
    return outcome;
  }

  public static Map<String, Object> buildCodeFetchInputs(StartupScriptConfiguration startupScript) {
    if (!requiresCodeFetch(startupScript)) {
      return Map.of();
    }
    StartupScriptCodeStoreConfig codeStore = (StartupScriptCodeStoreConfig) startupScript.getStore().getWith();
    Map<String, Object> inputs = new LinkedHashMap<>();
    inputs.put(STORE_TYPE_KEY, StartupScriptStoreType.CODE.getDisplayName());
    try {
      inputs.putAll(ObjectFlattener.flatten(codeStore));
      Object repo = inputs.remove("repo");
      if (repo != null) {
        inputs.put("repoName", repo);
      }
    } catch (IllegalAccessException e) {
      throw new InvalidRequestException("Failed to flatten startup script Harness Code store", e);
    }
    return inputs;
  }

  /**
   * Adapts the startup script to the config-file fetch contract so it follows the same content
   * fetch, expression rendering, and runner materialization flow as other config files.
   */
  public static ConfigFile buildConfigFile(StartupScriptConfiguration startupScript) {
    return ConfigFile.builder().id(STARTUP_SCRIPT_UNIT_ID).inputs(buildCodeFetchInputs(startupScript)).build();
  }

  public static void validateStoreType(StartupScriptStoreWrapper store) {
    if (store == null || store.getUses() == null) {
      return;
    }
    if (store.getUses() != StartupScriptStoreType.CODE) {
      throw new InvalidRequestException(String.format(
          "Unsupported startup script store type [%s]. Only Harness Code is supported.", store.getUses()));
    }
  }

  public static ServiceConfigOutcome patchStartupScriptPaths(ServiceConfigOutcome serviceConfig, List<String> paths) {
    if (serviceConfig == null || !isNotEmpty(paths)) {
      return serviceConfig;
    }
    Map<String, Object> startupScript = serviceConfig.getStartupScript();
    if (startupScript == null) {
      startupScript = new HashMap<>();
    } else {
      startupScript = new HashMap<>(startupScript);
    }
    startupScript.put(PATHS_KEY, String.join(",", paths));
    return ServiceConfigOutcome.builder()
        .manifests(serviceConfig.getManifests())
        .artifacts(serviceConfig.getArtifacts())
        .configFiles(serviceConfig.getConfigFiles())
        .startupScript(startupScript)
        .build();
  }
}
