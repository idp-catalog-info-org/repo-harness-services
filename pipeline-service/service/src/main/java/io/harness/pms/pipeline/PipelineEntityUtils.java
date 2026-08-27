/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline;

import io.harness.data.structure.EmptyPredicate;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;

@Singleton
public class PipelineEntityUtils {
  private static final String DEFAULT_MODULE = "cd";
  private static final int DEFAULT_PIPELINE_SDK_PRIORITY = 100;
  public static final String PIPELINE_YAML_SIZE_LIMIT_EXCEEDED_MESSAGE =
      "Pipeline YAML size exceeds the maximum allowed limit of 3 MB. "
      + "Please reduce the pipeline size by removing unnecessary stages/steps or use templates to modularize your "
      + "pipeline.";

  @Inject private PipelineSdkPrioritySupport pipelineSdkPrioritySupport;

  public String getModuleNameFromPipelineEntity(Collection<String> modules) {
    return getModuleNameFromPipelineEntity(modules, null);
  }

  public String getModuleNameFromPipelineEntity(Collection<String> modules, String accountIdentifier) {
    /* common will only come in a case where there is just pms in the modules list.
    // For reference: https://app.harness.io/ng/account/vpCkHKsDSxK9_KYfjCTMKA/module/code/orgs/
    HarnessHCRInternalUAT/projects/Harness_Code/repos/harness-core/pulls/3730/changes */
    if (modules.contains("common")) {
      return DEFAULT_MODULE;
    }
    if (EmptyPredicate.isEmpty(modules)) {
      return DEFAULT_MODULE;
    }

    if (pipelineSdkPrioritySupport.isHonorPipelineSdkPriorityEnabled(accountIdentifier)) {
      Map<String, Integer> pipelineSdkPriority = pipelineSdkPrioritySupport.getPipelineSdkPriority();
      if (EmptyPredicate.isNotEmpty(pipelineSdkPriority)) {
        return selectModuleBySdkPriority(modules, pipelineSdkPriority);
      }
    }

    for (String module : modules) {
      if (!module.equals("pms")) {
        return module;
      }
    }
    return DEFAULT_MODULE;
  }

  private String selectModuleBySdkPriority(Collection<String> modules, Map<String, Integer> pipelineSdkPriority) {
    return modules.stream()
        .filter(module -> !"pms".equals(module))
        .min(Comparator.comparingInt(module -> pipelineSdkPriority.getOrDefault(module, DEFAULT_PIPELINE_SDK_PRIORITY)))
        .orElse(DEFAULT_MODULE);
  }
}
