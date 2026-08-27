/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps.internal;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.encryption.SecretRefData;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.filters.FilterCreatorHelper;
import io.harness.filters.GenericStepPMSFilterJsonCreatorV2;
import io.harness.filters.SecretRefExtractorHelper;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.pms.filter.creation.FilterCreationResponse;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.utils.PmsFeatureFlagService;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_MIGRATOR, HarnessModuleComponent.CDS_COMMON_STEPS})
@OwnedBy(PIPELINE)
public class ShellScriptStepFilterJsonCreatorV2 extends GenericStepPMSFilterJsonCreatorV2 {
  @Inject PmsFeatureFlagService pmsFeatureFlagService;
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.SHELL_SCRIPT);
  }

  @Override
  public FilterCreationResponse handleNode(FilterCreationContext filterCreationContext, AbstractStepNode yamlField) {
    FilterCreationResponse filterCreationResponse = super.handleNode(filterCreationContext, yamlField);
    if (pmsFeatureFlagService.isEnabled(filterCreationContext.getSetupMetadata().getAccountId(),
            FeatureName.PIE_SEND_SECRET_REF_FOR_SHELLSCRIPT_VARIABLES)) {
      List<EntityDetailProtoDTO> entityDetailProtoDTOS = extractSecretrefFromEnvVariables(filterCreationContext);
      filterCreationResponse.addReferredEntities(entityDetailProtoDTOS);
    }
    return filterCreationResponse;
  }

  private List<EntityDetailProtoDTO> extractSecretrefFromEnvVariables(FilterCreationContext filterCreationContext) {
    // Fetch the secrets referred in the variables
    List<EntityDetailProtoDTO> entityDetailProtoDTOS = new ArrayList<>();
    String accountIdentifier = filterCreationContext.getSetupMetadata().getAccountId();
    String orgIdentifier = filterCreationContext.getSetupMetadata().getOrgId();
    String projectIdentifier = filterCreationContext.getSetupMetadata().getProjectId();
    YamlField variablesField = null;
    if (filterCreationContext.getCurrentField() != null && filterCreationContext.getCurrentField().getNode() != null
        && filterCreationContext.getCurrentField().getNode().getField(YAMLFieldNameConstants.SPEC) != null
        && filterCreationContext.getCurrentField().getNode().getField(YAMLFieldNameConstants.SPEC).getNode() != null) {
      variablesField = filterCreationContext.getCurrentField()
                           .getNode()
                           .getField(YAMLFieldNameConstants.SPEC)
                           .getNode()
                           .getField(YAMLFieldNameConstants.ENVIRONMENT_VARIABLES);
    }
    if (variablesField != null) {
      Map<String, ParameterField<SecretRefData>> fqnToSecretRefs =
          SecretRefExtractorHelper.extractSecretRefsFromVariables(variablesField);
      for (Map.Entry<String, ParameterField<SecretRefData>> entry : fqnToSecretRefs.entrySet()) {
        entityDetailProtoDTOS.add(FilterCreatorHelper.convertSecretToEntityDetailProtoDTO(
            accountIdentifier, orgIdentifier, projectIdentifier, entry.getKey(), entry.getValue()));
      }
    }
    return entityDetailProtoDTOS;
  }
}
