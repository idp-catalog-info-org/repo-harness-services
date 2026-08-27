/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.shellscript;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.encoding.EncodingUtils;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.k8s.K8sInfraDelegateConfig;
import io.harness.delegate.task.shell.ShellScriptTaskParametersNG.ShellScriptTaskParametersNGBuilder;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.ParameterField;
import io.harness.security.SimpleEncryption;
import io.harness.shell.ScriptType;
import io.harness.steps.shellscript.v1.ShellScriptOutcome;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
@OwnedBy(CDC)
public interface ShellScriptHelperService {
  Pattern SHELL_VAR_PATTERN = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");

  // Handles ParameterField and String type Objects, else throws Exception
  Map<String, String> getEnvironmentVariables(Map<String, Object> inputVariables, Ambiance ambiance);

  List<String> getOutputVars(Map<String, Object> outputVariables, Set<String> secretOutputVariables);
  List<String> getSecretOutputVars(Map<String, Object> outputVariables, Set<String> secretOutputVariables);

  K8sInfraDelegateConfig getK8sInfraDelegateConfig(
      @Nonnull Ambiance ambiance, @Nonnull String shellScript, Boolean includeInfraSelectors);

  void prepareTaskParametersForExecutionTarget(@Nonnull Ambiance ambiance,
      @Nonnull ShellScriptStepParametersV0 shellScriptStepParameters,
      @Nonnull ShellScriptTaskParametersNGBuilder taskParametersNGBuilder, boolean executeOnDelegate);

  boolean toExecuteOnDelegate(ParameterField<ExecutionTarget> executionTargetParameterField);

  String getShellScript(@Nonnull ShellScriptStepParametersV0 stepParameters, Ambiance ambiance);

  String getWorkingDirectory(
      ParameterField<ExecutionTarget> executionTarget, @Nonnull ScriptType scriptType, boolean executeOnDelegate);

  TaskParameters buildShellScriptTaskParametersNG(@Nonnull Ambiance ambiance,
      @Nonnull ShellScriptStepParametersV0 shellScriptStepParameters, String sessionTimeout, String commandUnit);
  TaskParameters buildShellScriptTaskParametersNG(@Nonnull Ambiance ambiance,
      @Nonnull ShellScriptStepParametersV0 shellScriptStepParameters, String sessionTimeout, String commandUnit,
      Map<String, String> identityTokens);
  TaskParameters buildShellScriptTaskParametersNG(
      @Nonnull Ambiance ambiance, @Nonnull ShellScriptStepParametersV0 shellScriptStepParameters);

  ShellScriptBaseOutcome prepareShellScriptOutcome(
      Map<String, String> sweepingOutputEnvVariables, Map<String, Object> outputVariables);

  void exportOutputVariablesUsingAlias(@Nonnull Ambiance ambiance,
      @Nonnull ShellScriptStepParametersV0 shellScriptStepParametersV0,
      @Nonnull ShellScriptBaseOutcome shellScriptOutcome);

  static ShellScriptBaseOutcome prepareShellScriptOutcome(Map<String, String> sweepingOutputEnvVariables,
      Map<String, Object> outputVariables, Set<String> secretOutputVariables) {
    return prepareShellScriptOutcome(
        sweepingOutputEnvVariables, outputVariables, secretOutputVariables, HarnessYamlVersion.V0);
  }

  static ShellScriptBaseOutcome prepareShellScriptOutcome(Map<String, String> sweepingOutputEnvVariables,
      Map<String, Object> outputVariables, Set<String> secretOutputVariables, String version) {
    SimpleEncryption encryption = new SimpleEncryption();

    if (outputVariables == null || sweepingOutputEnvVariables == null) {
      return null;
    }
    Map<String, String> resolvedOutputVariables = new HashMap<>();
    outputVariables.keySet().forEach(name -> {
      Object value = ((ParameterField<?>) outputVariables.get(name)).getValue();
      if (isNotEmpty(secretOutputVariables) && secretOutputVariables.contains(name)
          && isNotEmpty(sweepingOutputEnvVariables.get(value.toString()))) {
        String encodedValue = EncodingUtils.encodeBase64(
            encryption.encrypt(sweepingOutputEnvVariables.get(value).getBytes(StandardCharsets.UTF_8)));
        String finalValue = "${sweepingOutputSecrets.obtain(\"" + name + "\",\"" + encodedValue + "\")}";
        resolvedOutputVariables.put(name, finalValue);
      } else {
        resolvedOutputVariables.put(name, sweepingOutputEnvVariables.get(value));
      }
    });
    return getShellScriptOutcome(resolvedOutputVariables, version);
  }

  /**
   * FF-ON post-processor (PIPE_OUTPUT_VARIABLE_RESOLUTION_FALLBACK, PIPE-35042). Called AFTER
   * {@link #prepareShellScriptOutcome} — mirrors CI's {@code resolveOutputVariablesWithFallback}
   * pattern.
   * <p>
   * For each non-secret output variable: if the shell did NOT export the referenced var
   * (containsKey == false) or the resolved YAML value isn't a valid shell identifier, overwrite
   * the map entry with the resolved YAML value. Secrets and exported values (even empty) are
   * left untouched.
   */
  static void applyResolutionFallback(ShellScriptBaseOutcome outcome, Map<String, String> sweepingOutputEnvVariables,
      Map<String, Object> outputVariables, Set<String> secretOutputVariables) {
    if (outcome == null || outputVariables == null || sweepingOutputEnvVariables == null) {
      return;
    }
    Map<String, String> resolvedOutputVariables = outcome.getOutputVariables();
    if (resolvedOutputVariables == null) {
      return;
    }
    outputVariables.forEach((name, val) -> {
      if (isNotEmpty(secretOutputVariables) && secretOutputVariables.contains(name)) {
        return;
      }
      Object value = ((ParameterField<?>) val).getValue();
      if (value == null) {
        return;
      }
      String valueStr = value.toString();
      if (!isValidShellVariableName(valueStr) || !sweepingOutputEnvVariables.containsKey(valueStr)) {
        resolvedOutputVariables.put(name, valueStr);
      }
    });
  }

  static boolean isValidShellVariableName(String value) {
    return value != null && SHELL_VAR_PATTERN.matcher(value).matches();
  }

  // We have separate POJO for step outcome for v1 because we also need to support expressions of outcomes following v1
  // rfc
  static ShellScriptBaseOutcome getShellScriptOutcome(Map<String, String> resolvedOutputVariables, String version) {
    switch (version) {
      case HarnessYamlVersion.V1:
        return ShellScriptOutcome.builder().output_vars(resolvedOutputVariables).build();
      case HarnessYamlVersion.V0:
        return io.harness.steps.shellscript.ShellScriptOutcome.builder()
            .outputVariables(resolvedOutputVariables)
            .build();
      default:
        throw new InvalidRequestException(String.format("Version %s not supported", version));
    }
  }

  // Convert v1 step parameters to v0, we could not do this during plan creation because we also need to support
  // expressions following v1 rfc
  static io.harness.steps.shellscript.ShellScriptStepParametersV0 getShellScriptStepParameters(
      StepBaseParameters stepParameters) {
    String version = stepParameters.getSpec().getVersion();
    switch (version) {
      case HarnessYamlVersion.V0:
        return (io.harness.steps.shellscript.ShellScriptStepParametersV0) stepParameters.getSpec();
      case HarnessYamlVersion.V1:
        return ((io.harness.steps.shellscript.v1.ShellScriptStepParameters) stepParameters.getSpec())
            .toShellScriptParametersV0();
      default:
        throw new InvalidRequestException(String.format("Version %s not supported", version));
    }
  }
}
