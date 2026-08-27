/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.salesforce.defaultpipelines;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

import com.google.inject.Singleton;
import java.util.Map;

@Singleton
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.SALESFORCE})
@OwnedBy(HarnessTeam.CDP)
public class SalesforcePipelineMappingsService {
  private static final Map<String, String> DEFAULT_PIPELINE_MAPPINGS = Map.of("DEPLOY", "salesforce_dx_deploy",
      "VALIDATE", "salesforce_dx_validate", "QUICK_DEPLOY", "salesforce_quick_deploy", "EVALUATE_DIFF",
      "salesforce_evaluate_diff", "SOURCE_BACKUP", "salesforce_source_backup");

  public Map<String, String> getPipelineMappings() {
    return DEFAULT_PIPELINE_MAPPINGS;
  }
}
