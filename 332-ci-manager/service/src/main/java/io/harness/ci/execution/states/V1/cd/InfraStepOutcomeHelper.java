/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.states.V1.cd;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cd.beans.outcomes.EnvironmentOutcome;
import io.harness.cd.beans.outcomes.InfraStepOutcome;
import io.harness.ci.execution.common.InfraConfigOutput;
import io.harness.common.utils.InfrastructureKeyGeneratorUtils;
import io.harness.common.utils.InfrastructureKeyGeneratorUtils.InfraKey;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.ParameterField;
import io.harness.unified.cd.infrastructure.InfraConfig;
import io.harness.unified.cd.infrastructure.InfraInfoConfig;
import io.harness.unified.cd.infrastructure.InfrastructureMetadata;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CI)
@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class InfraStepOutcomeHelper {
  public static final String INFRA_OUTPUT = "infraOutput";
  ExecutionSweepingOutputService sweepingOutputService;

  public InfraStepOutcome getInfraStepOutcome(Ambiance ambiance, InfrastructureMetadata infraMetadata,
      EnvironmentOutcome environmentOutcome, InfraConfig infraConfig, String serviceRef, String[] infraKeyValues) {
    InfraInfoConfig infraInfoConfig = infraConfig.getInfraInfoConfig();
    InfraKey infraKey =
        InfrastructureKeyGeneratorUtils.createInfraKey(serviceRef, environmentOutcome.getRef(), infraKeyValues);

    InfraStepOutcome stepOutcome = InfraStepOutcome.builder()
                                       .name(infraMetadata.getName())
                                       .identifier(infraMetadata.getIdentifier())
                                       .kind(infraInfoConfig.getUses().getDisplayName())
                                       .description(infraMetadata.getDescription())
                                       .tags(infraMetadata.getTags())
                                       .infrastructureKey(infraKey.getKey())
                                       .infrastructureKeyShort(infraKey.getShortKey())
                                       .releaseId(infraKey.getKey())
                                       .addRcStep(shouldAddRcStep(infraInfoConfig.getAllowSimultaneousDeployments()))
                                       .environment(environmentOutcome)
                                       .build();

    // Populate the HashMap with all the properties
    stepOutcome.populateMap();

    OptionalSweepingOutput optionalSweepingOutput =
        sweepingOutputService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(INFRA_OUTPUT));

    if (optionalSweepingOutput.isFound()) {
      InfraConfigOutput infraConfigOutput = (InfraConfigOutput) optionalSweepingOutput.getOutput();
      stepOutcome.putAll(infraConfigOutput);
    }

    return stepOutcome;
  }

  /**
   * Resource-constraint (RC) step should be added unless {@code allowSimultaneousDeployments} is explicitly
   * resolved to {@code true}. The field can be null, an unresolved expression with no concrete value, or have
   * a default value, so this method is null-safe and avoids unboxing a potentially-null Boolean.
   */
  private boolean shouldAddRcStep(ParameterField<Boolean> allowSimultaneousDeployments) {
    if (ParameterField.isNull(allowSimultaneousDeployments)) {
      return true;
    }
    return !Boolean.TRUE.equals(allowSimultaneousDeployments.getValue());
  }
}
