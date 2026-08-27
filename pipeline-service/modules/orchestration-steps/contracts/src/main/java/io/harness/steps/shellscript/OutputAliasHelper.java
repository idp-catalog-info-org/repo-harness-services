/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.shellscript;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InternalServerErrorException;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;

import com.google.inject.Inject;
import java.util.Map;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
@OwnedBy(CDC)
@Slf4j
public class OutputAliasHelper {
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;

  public void exportOutputVariablesUsingAlias(
      @Nonnull Ambiance ambiance, @Nonnull OutputAlias outputAlias, @Nonnull Map<String, String> outputVariables) {
    if (EmptyPredicate.isEmpty(outputVariables)) {
      return;
    }
    String userAlias = (String) outputAlias.getKey().fetchFinalValue();
    String uuid = OutputAliasUtils.generateSweepingOutputKeyUsingUserAlias(userAlias, ambiance);
    try {
      executionSweepingOutputService.consume(ambiance, uuid,
          OutputAliasSweepingOutput.builder().outputVariables(outputVariables).build(),
          outputAlias.getScope().toStepOutcomeGroup());
    } catch (Exception ex) {
      if (OutputAliasUtils.isDuplicateKeyException(ex, uuid)) {
        log.error("Error while publishing outputAlias due to the output already saved for the key [{}:{}] for scope {}",
            userAlias, uuid, outputAlias.getScope(), ex);
        throw new InvalidRequestException(
            String.format("Output alias with key %s, already saved in %s scope. Please ensure that there are no "
                    + "duplicate output alias keys within the same scope",
                userAlias, outputAlias.getScope()));
      }
      log.error("Error while publishing outputAlias for the key [{}:{}] for scope {}", userAlias, uuid,
          outputAlias.getScope(), ex);
      throw new InternalServerErrorException(
          String.format(
              "Error while publishing outputAlias for the key %s for scope %s", userAlias, outputAlias.getScope()),
          ex);
    }
  }
}
