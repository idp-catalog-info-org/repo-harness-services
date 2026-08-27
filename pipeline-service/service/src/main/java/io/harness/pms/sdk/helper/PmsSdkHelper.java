/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.helper;

import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.contracts.plan.PlanCreationServiceGrpc;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.plan.creation.info.PlanCreatorServiceInfo;
import io.harness.pms.sdk.PmsSdkInstance;
import io.harness.pms.sdk.PmsSdkInstance.PmsSdkInstanceKeys;
import io.harness.pms.sdk.PmsSdkInstanceService;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.utils.PmsFeatureFlagService;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class PmsSdkHelper {
  @Inject private Map<ModuleType, PlanCreationServiceGrpc.PlanCreationServiceBlockingStub> planCreatorServices;
  @Inject private PmsSdkInstanceService pmsSdkInstanceService;
  @Inject @Getter @Named("pipelineSdkPriority") Map<String, Integer> pipelineSdkPriority;
  @Inject PmsFeatureFlagService pmsFeatureFlagService;

  /**
   * Gets the list of registered services with their PlanCreatorServiceInfo object
   */
  public Map<String, PlanCreatorServiceInfo> getServices() {
    Map<String, Map<String, Set<String>>> sdkInstances = pmsSdkInstanceService.getInstanceNameToSupportedTypes();
    Map<String, PlanCreatorServiceInfo> services = new HashMap<>();
    if (EmptyPredicate.isNotEmpty(planCreatorServices) && EmptyPredicate.isNotEmpty(sdkInstances)) {
      sdkInstances.forEach((k, v) -> {
        if (planCreatorServices.containsKey(ModuleType.fromString(k))) {
          services.put(k,
              new PlanCreatorServiceInfo(
                  v, planCreatorServices.get(ModuleType.fromString(k)), pipelineSdkPriority.getOrDefault(k, 100)));
        }
      });
    }
    return services;
  }

  @VisibleForTesting
  protected Map<String, PlanCreatorServiceInfo> sortServicesBasedOnPriority(
      Map<String, PlanCreatorServiceInfo> services) {
    // sorting the services based on the priority coming from pipeline config.yml
    Map<String, PlanCreatorServiceInfo> serviceSortedBasedOnPriority = new LinkedHashMap<>();
    services.entrySet()
        .stream()
        .sorted(Map.Entry.comparingByValue(Comparator.comparingInt(PlanCreatorServiceInfo::getPriority)))
        .forEachOrdered(entry -> serviceSortedBasedOnPriority.put(entry.getKey(), entry.getValue()));

    return serviceSortedBasedOnPriority;
  }

  public Map<String, PlanCreatorServiceInfo> getServicesV2() {
    Map<String, PlanCreatorServiceInfo> services = getServices();
    /*
    Forces the executions to go to CI by removing "sto" from the services map if it exists.
    This will be removed when ADR-43 is complete.
    */
    services.remove(ModuleType.STO.name().toLowerCase());
    return sortServicesBasedOnPriority(services);
  }

  /**
   * Checks if the service supports any of the dependency mentioned.
   */
  public static boolean containsSupportedDependencyByYamlPath(
      PlanCreatorServiceInfo serviceInfo, Dependencies dependencies) {
    if (dependencies == null || EmptyPredicate.isEmpty(dependencies.getDependenciesMap())) {
      return false;
    }

    YamlField fullYamlField;
    try {
      fullYamlField = YamlUtils.readTree(dependencies.getYaml());
    } catch (IOException ex) {
      String message = "Invalid yaml during plan creation";
      log.error(message, ex);
      throw new InvalidRequestException(message);
    }

    return dependencies.getDependenciesMap()
        .entrySet()
        .stream()
        .filter(entry
            -> containsSupportedSingleDependencyByYamlPath(serviceInfo, fullYamlField, entry, HarnessYamlVersion.V0))
        .map(Map.Entry::getKey)
        .findFirst()
        .isPresent();
  }

  public boolean containsSupportedDependencyByYamlPath(
      Map.Entry<String, PlanCreatorServiceInfo> givenServiceInfo, Dependencies dependencies, String accountId) {
    return containsSupportedDependencyByYamlPath(givenServiceInfo, dependencies, accountId, HarnessYamlVersion.V0);
  }

  public boolean containsSupportedDependencyByYamlPath(Map.Entry<String, PlanCreatorServiceInfo> givenServiceInfo,
      Dependencies dependencies, String accountId, String yamlVersion) {
    if (dependencies == null || EmptyPredicate.isEmpty(dependencies.getDependenciesMap())) {
      return false;
    }

    YamlField fullYamlField;
    try {
      fullYamlField = YamlUtils.readTree(dependencies.getYaml());
    } catch (IOException ex) {
      String message = "Invalid yaml during plan creation";
      log.error(message, ex);
      throw new InvalidRequestException(message);
    }

    return dependencies.getDependenciesMap()
        .entrySet()
        .stream()
        .filter(entry
            -> containsSupportedSingleDependencyByYamlPath(
                givenServiceInfo, fullYamlField, entry, yamlVersion, accountId))
        .map(Map.Entry::getKey)
        .findFirst()
        .isPresent();
  }

  public static boolean containsSupportedSingleDependencyByYamlPath(PlanCreatorServiceInfo serviceInfo,
      YamlField fullYamlField, Map.Entry<String, String> dependencyEntry, String harnessVersion) {
    if (dependencyEntry == null) {
      return false;
    }
    Map<String, Set<String>> supportedTypes = serviceInfo.getSupportedTypes();
    try {
      YamlField field = fullYamlField.fromYamlPath(dependencyEntry.getValue());
      return PlanCreatorUtils.supportsField(supportedTypes, field, harnessVersion);
    } catch (Exception ex) {
      String message = "Invalid yaml during plan creation for dependency path - " + dependencyEntry.getValue();
      log.error(message, ex);
      throw new InvalidRequestException(message);
    }
  }

  /**
   * Checks if the service supports any of the dependency mentioned.
   */
  public boolean containsSupportedSingleDependencyByYamlPath(Map.Entry<String, PlanCreatorServiceInfo> givenServiceInfo,
      YamlField fullYamlField, Map.Entry<String, String> dependencyEntry, String harnessVersion, String accountId) {
    if (dependencyEntry == null) {
      return false;
    }

    PlanCreatorServiceInfo serviceInfo = givenServiceInfo.getValue();
    Map<String, Set<String>> supportedTypes = serviceInfo.getSupportedTypes();
    try {
      YamlField field = fullYamlField.fromYamlPath(dependencyEntry.getValue());
      if (pmsFeatureFlagService.isEnabled(accountId, FeatureName.PIPE_STORE_TEMPLATE_REFERENCE_SUMMARY_PER_EXECUTION)
          || HarnessYamlVersion.isV1(harnessVersion)) {
        return PlanCreatorUtils.supportsField(supportedTypes, field, harnessVersion, true);
      }
      return PlanCreatorUtils.supportsField(supportedTypes, field, harnessVersion);
    } catch (Exception ex) {
      String message = "Invalid yaml during plan creation for dependency path - " + dependencyEntry.getValue();
      log.error(message, ex);
      throw new InvalidRequestException(message);
    }
  }

  public static String getServiceAffinityForGivenDependency(
      Map<String, String> serviceAffinityMap, Map.Entry<String, String> dependencyEntry) {
    String affinityService = null;
    String serviceAffinity = serviceAffinityMap.get(dependencyEntry.getKey());
    if (EmptyPredicate.isNotEmpty(serviceAffinityMap)) {
      affinityService = serviceAffinity;
    }
    return affinityService;
  }

  public boolean checkIfGivenServiceSupportsPath(Map.Entry<String, PlanCreatorServiceInfo> givenServiceInfo,
      Map.Entry<String, String> dependencyEntry, YamlField fullYamlField, String harnessVersion) {
    return checkIfGivenServiceSupportsPath(givenServiceInfo, dependencyEntry, fullYamlField, harnessVersion, null);
  }

  public boolean checkIfGivenServiceSupportsPath(Map.Entry<String, PlanCreatorServiceInfo> givenServiceInfo,
      Map.Entry<String, String> dependencyEntry, YamlField fullYamlField, String harnessVersion, String accountId) {
    if (givenServiceInfo == null) {
      return false;
    }
    return containsSupportedSingleDependencyByYamlPath(
        givenServiceInfo, fullYamlField, dependencyEntry, harnessVersion, accountId);
  }

  public static Dependencies createBatchDependency(Dependencies dependencies, Map<String, String> dependencyMap) {
    return Dependencies.newBuilder()
        .putAllDependencies(dependencyMap)
        .putAllDependencyMetadata(dependencies.getDependencyMetadataMap())
        .setYaml(dependencies.getYaml())
        .build();
  }

  public static Map<String, String> createBatchServiceAffinityMap(
      Set<String> dependencyKeys, Map<String, String> allServiceAffinityMap) {
    return allServiceAffinityMap.entrySet()
        .stream()
        .filter(e -> dependencyKeys.contains(e.getKey()))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  public static boolean isPipelineService(Map.Entry<String, PlanCreatorServiceInfo> serviceInfo) {
    return serviceInfo.getKey().equals(ModuleType.PMS.name().toLowerCase());
  }

  public static boolean getServiceForGivenAffinity(
      Map.Entry<String, PlanCreatorServiceInfo> serviceInfo, String serviceName) {
    if (EmptyPredicate.isEmpty(serviceName)) {
      return false;
    }
    return serviceInfo.getKey().equals(serviceName.toLowerCase());
  }

  // This method is used for FilterCreator and VariableCreator
  public void addDependencyToServiceDependencyMapBasedOnPriority(Map<String, PlanCreatorServiceInfo> services,
      Map<String, String> dependencyMap, YamlField fullYamlField,
      Map<Map.Entry<String, PlanCreatorServiceInfo>, List<Map.Entry<String, String>>> serviceToDependencyMap,
      String harnessVersion, String accountId) {
    // Initializing the responseMap
    for (Map.Entry<String, PlanCreatorServiceInfo> serviceEntry : services.entrySet()) {
      serviceToDependencyMap.put(serviceEntry, new LinkedList<>());
    }

    for (Map.Entry<String, String> dependencyEntry : dependencyMap.entrySet()) {
      // 2. pipeline-service dependencies
      Map.Entry<String, PlanCreatorServiceInfo> pmsPlanCreatorService =
          services.entrySet()
              .stream()
              .filter(PmsSdkHelper::isPipelineService)
              .findFirst()
              .orElseThrow(
                  () -> new InvalidRequestException("Pipeline Service service provider information is missing."));

      if (checkIfGivenServiceSupportsPath(pmsPlanCreatorService, dependencyEntry, fullYamlField, harnessVersion)) {
        serviceToDependencyMap.get(pmsPlanCreatorService).add(dependencyEntry);
      } else {
        for (Map.Entry<String, PlanCreatorServiceInfo> serviceInfoEntry : services.entrySet()) {
          if (PmsSdkHelper.isPipelineService(serviceInfoEntry)) {
            continue;
          }
          if (checkIfGivenServiceSupportsPath(
                  serviceInfoEntry, dependencyEntry, fullYamlField, harnessVersion, accountId)) {
            serviceToDependencyMap.get(serviceInfoEntry).add(dependencyEntry);
            break;
          }
        }
      }
    }
  }

  public String getModulePath(String moduleName) {
    if (EmptyPredicate.isEmpty(moduleName)) {
      return "";
    }
    PmsSdkInstance pmsSdkInstance =
        pmsSdkInstanceService
            .getActiveSdkInstanceMapFromSecondary(Collections.singletonList(PmsSdkInstanceKeys.modulePath))
            .get(moduleName);
    String modulePath = "";
    if (pmsSdkInstance != null && EmptyPredicate.isNotEmpty(pmsSdkInstance.getModulePath())) {
      modulePath = pmsSdkInstance.getModulePath();
    }
    return modulePath;
  }
}
