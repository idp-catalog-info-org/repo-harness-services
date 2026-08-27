/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.states.V1.cd;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.unified.service.NGOutcomes.NG_OUTCOMES;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.common.VariablesSweepingOutput;
import io.harness.common.utils.YamlParsingUtils;
import io.harness.configFiles.ConfigGitFile;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.YamlUtils;
import io.harness.unified.service.NGOutcomes;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class ConfigFilesStepHelper {
  @Inject private ExecutionSweepingOutputService sweepingOutputService;

  /**
   * Patches the {@code ngOutcomes} sweeping output's config-files section with the supplied
   * git file content so that downstream expressions resolve to the latest file content.
   *
   * <p>Used by both {@link ConfigFilesStep} (after fetching files from SCM) and
   * {@link RenderingStep} (after rendering expressions in file content).
   */
  @SuppressWarnings("unchecked")
  public void updateNgConfigFilesOutcomeWithGitFiles(
      Ambiance ambiance, Map<String, List<ConfigGitFile>> gitFilesByConfigFileId) {
    if (isEmpty(gitFilesByConfigFileId)) {
      return;
    }
    try {
      OptionalSweepingOutput ngOutcomesSweepingOutput =
          sweepingOutputService.resolveOptional(ambiance, RefObjectUtils.getSweepingOutputRefObject(NG_OUTCOMES));
      if (!ngOutcomesSweepingOutput.isFound()) {
        return;
      }

      VariablesSweepingOutput ngOutcomes = (VariablesSweepingOutput) ngOutcomesSweepingOutput.getOutput();
      if (ngOutcomes == null || !ngOutcomes.containsKey(NGOutcomes.CONFIG_FILES.getName())) {
        return;
      }

      String configFilesYamlString = (String) ngOutcomes.get(NGOutcomes.CONFIG_FILES.getName());
      if (isEmpty(configFilesYamlString)) {
        return;
      }

      Map<String, Object> parsedYaml = YamlParsingUtils.parseYamlStringToMap(configFilesYamlString);
      if (isEmpty(parsedYaml)) {
        return;
      }

      // Some YAML serializations may wrap the map under a single key; handle common wrapper keys defensively.
      Map<String, Object> configFilesMap = parsedYaml;
      Object wrapper = parsedYaml.get("configFilesOutcome");
      if (wrapper instanceof Map) {
        configFilesMap = (Map<String, Object>) wrapper;
      } else {
        Object wrapper2 = parsedYaml.get("ConfigFilesOutcome");
        if (wrapper2 instanceof Map) {
          configFilesMap = (Map<String, Object>) wrapper2;
        }
      }

      boolean didUpdate = false;
      for (Map.Entry<String, List<ConfigGitFile>> entry : gitFilesByConfigFileId.entrySet()) {
        String configFileId = entry.getKey();
        List<ConfigGitFile> gitFiles = entry.getValue();
        if (isEmpty(configFileId) || isEmpty(gitFiles)) {
          continue;
        }

        List<Map<String, Object>> gitFilesAsMaps = new ArrayList<>();
        for (ConfigGitFile gitFile : gitFiles) {
          if (gitFile == null) {
            continue;
          }
          Map<String, Object> fileMap = new HashMap<>();
          fileMap.put("filePath", gitFile.getFilePath());
          fileMap.put("fileContent", gitFile.getFileContent());
          gitFilesAsMaps.add(fileMap);
        }
        if (isEmpty(gitFilesAsMaps)) {
          continue;
        }

        String mapKeyToUpdate = configFileId;
        Object existingObj = configFilesMap.get(mapKeyToUpdate);

        // Fallback: search by outcome.identifier (in case map key differs)
        if (!(existingObj instanceof Map)) {
          for (Map.Entry<String, Object> cfEntry : configFilesMap.entrySet()) {
            if (!(cfEntry.getValue() instanceof Map)) {
              continue;
            }
            Map<String, Object> candidate = (Map<String, Object>) cfEntry.getValue();
            Object identifier = candidate.get("identifier");
            if (identifier != null && configFileId.equals(identifier.toString())) {
              mapKeyToUpdate = cfEntry.getKey();
              existingObj = candidate;
              break;
            }
          }
        }

        if (!(existingObj instanceof Map)) {
          continue;
        }

        Map<String, Object> existingMap = (Map<String, Object>) existingObj;
        existingMap.put("gitFiles", gitFilesAsMaps);
        configFilesMap.put(mapKeyToUpdate, existingMap);
        didUpdate = true;
      }

      if (!didUpdate) {
        return;
      }

      ngOutcomes.put(NGOutcomes.CONFIG_FILES.getName(), YamlUtils.writeYamlString(parsedYaml));
      sweepingOutputService.consumeUpsert(ambiance, NG_OUTCOMES, ngOutcomes, StepCategory.STAGE.name());
    } catch (Exception e) {
      log.warn("Failed to update ngOutcomes configFiles outcome with git file contents", e);
    }
  }
}
