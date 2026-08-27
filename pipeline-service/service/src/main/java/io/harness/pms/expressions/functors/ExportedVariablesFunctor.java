/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import static java.util.Objects.isNull;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.engine.expressions.metadata.ExecutionSweepingOutputMetadata;
import io.harness.expression.celcustomfunctor.WithGetValue;
import io.harness.expression.functors.ExpressionFunctor;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.steps.shellscript.OutputAliasSweepingOutput;
import io.harness.steps.shellscript.OutputAliasUtils;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_COMMON_STEPS, HarnessModuleComponent.CDS_PIPELINE})
@RecasterAlias("io.harness.pms.expressions.functors.ExportedVariablesFunctor")
public class ExportedVariablesFunctor implements ExpressionFunctor, WithGetValue {
  private final Ambiance ambiance;
  private final ExecutionSweepingOutputService executionSweepingOutputService;
  private final ExecutionSweepingOutputMetadata outputMetadata;
  private static final String DOT = "\\.";
  public ExportedVariablesFunctor(Ambiance ambiance, ExecutionSweepingOutputService executionSweepingOutputService,
      ExecutionSweepingOutputMetadata outputMetadata) {
    this.ambiance = ambiance;
    this.executionSweepingOutputService = executionSweepingOutputService;
    this.outputMetadata = outputMetadata;
  }

  /**
   * Maps the input expression to corresponding sweeping output expression
   */
  @Override
  public Object getValue(String exportExpression) {
    if (StringUtils.isBlank(exportExpression) || !OutputAliasUtils.validateExpressionFormat(exportExpression)) {
      return null;
    }
    // getting the resolved expression in exportExpression in case of
    // <+exportVariables.getValue("<+pipeline.variables.exp1")>

    String[] exportExpressionSplit = exportExpression.split(DOT);
    String scope = exportExpressionSplit[0];
    String userAlias = exportExpressionSplit[1];
    String encodedAlias = OutputAliasUtils.generateSweepingOutputKeyUsingUserAlias(userAlias, ambiance);

    ExecutionSweepingOutput sweepingOutput;
    try {
      if (AmbianceUtils.checkIfFeatureFlagEnabled(ambiance, FeatureName.PIPE_OPTIMISE_EXPORTED_VARIABLES_FUNCTOR.name())
          && outputMetadata != null
          && (outputMetadata.getExistingOutputNames() != null
              || outputMetadata.getOutputNamesForRollbackMode() != null)) {
        if (outputMetadata.getExistingOutputNames().contains(encodedAlias)
            || outputMetadata.getOutputNamesForRollbackMode().contains(encodedAlias)) {
          sweepingOutput = executionSweepingOutputService.resolve(ambiance,
              RefObjectUtils.getSweepingOutputRefObjectUsingGroup(
                  encodedAlias, OutputAliasUtils.toStepOutcomeGroup(scope)));
        } else {
          log.warn("No sweeping output present for resolving outputAlias for the key [{}:{}] for scope {}", userAlias,
              encodedAlias, scope);
          return null;
        }
      } else {
        sweepingOutput = executionSweepingOutputService.resolve(ambiance,
            RefObjectUtils.getSweepingOutputRefObjectUsingGroup(
                encodedAlias, OutputAliasUtils.toStepOutcomeGroup(scope)));
      }
    } catch (Exception ex) {
      log.warn(
          "Error while resolving outputAlias for the key [{}:{}] for scope {}", userAlias, encodedAlias, scope, ex);
      return null;
    }
    if (isNull(sweepingOutput) || !(sweepingOutput instanceof OutputAliasSweepingOutput)) {
      log.warn("Absent or incorrect type of sweeping output obtained after resolving outputAlias for the key [{}:{}] "
              + "for scope {}",
          userAlias, encodedAlias, scope);
      return null;
    }
    OutputAliasSweepingOutput outputAliasSweepingOutput = (OutputAliasSweepingOutput) sweepingOutput;

    if (isNull(outputAliasSweepingOutput.getOutputVariables())) {
      log.warn("OutputVariables map not present in the sweeping output after resolving outputAlias for the key [{}:{}] "
              + "for scope {}",
          userAlias, encodedAlias, scope);
      return null;
    }
    Map<String, String> exportVariablesMap = outputAliasSweepingOutput.getOutputVariables();

    if (exportExpressionSplit.length == 2) {
      return exportVariablesMap;
    }
    return exportVariablesMap.get(exportExpressionSplit[2]);
  }
}
