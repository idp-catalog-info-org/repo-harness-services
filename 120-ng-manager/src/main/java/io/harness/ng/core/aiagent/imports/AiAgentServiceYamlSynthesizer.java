/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.aiagent.imports;

import io.harness.cdng.service.beans.ServiceDefinition;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.cdng.service.beans.aiagent.AgentSourceSpec;
import io.harness.cdng.service.beans.aiagent.AwsAgentCoreServiceSpec;
import io.harness.cdng.service.beans.aiagent.AwsCoreAgentSource;
import io.harness.cdng.service.beans.aiagent.AwsCoreAgentSourceType;
import io.harness.cdng.service.beans.aiagent.ContainerAgentSource;
import io.harness.cdng.service.beans.aiagent.GoogleAgentRuntimeServiceSpec;
import io.harness.cdng.service.beans.aiagent.GoogleAgentSource;
import io.harness.cdng.service.beans.aiagent.GoogleAgentSourceType;
import io.harness.delegate.task.aiagent.AgentDescriptor;
import io.harness.delegate.task.aiagent.AgentPlatform;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ng.core.aiagent.dto.AgentConfigVariableDTO;
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.ng.core.service.yaml.NGServiceV2InfoConfig;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.validator.NGRegexValidatorConstants;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.core.variables.NGVariableType;
import io.harness.yaml.core.variables.StringNGVariable;

import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Synthesizes Harness AI agent service YAML from cloud agent descriptors.
 * Produces service definitions for the per-platform AI service types (AwsAgentCore / GoogleAgentRuntime).
 */
@Slf4j
@Singleton
public class AiAgentServiceYamlSynthesizer {
  /**
   * Synthesizes a complete AI agent service YAML from the discovered agent descriptor.
   *
   * @param descriptor agent metadata discovered from the cloud provider
   * @param platform the cloud platform (AWS_AGENT_CORE or GOOGLE_AGENT_RUNTIME)
   * @param serviceIdentifier Harness service identifier
   * @param serviceName Harness service name
   * @return YAML string representing the service definition
   */
  public String synthesize(@NotNull AgentDescriptor descriptor, @NotNull AgentPlatform platform,
      @NotNull String serviceIdentifier, @NotNull String serviceName) {
    // Build config variables (reconcile pin + config + env vars)
    List<NGVariable> configVariables = buildConfigVariables(descriptor);

    // Build the service definition for the platform's service type
    ServiceDefinition serviceDefinition = buildServiceDefinition(descriptor, platform, configVariables);

    // Build service config
    NGServiceV2InfoConfig serviceInfo = NGServiceV2InfoConfig.builder()
                                            .name(serviceName)
                                            .identifier(serviceIdentifier)
                                            .description(descriptor.getDescription())
                                            .serviceDefinition(serviceDefinition)
                                            .build();

    NGServiceConfig serviceConfig = NGServiceConfig.builder().ngServiceV2InfoConfig(serviceInfo).build();

    // Convert to YAML
    return YamlUtils.writeYamlString(serviceConfig);
  }

  /**
   * Extracts configuration variables for the API response echo.
   * Returns the same variables that are embedded in the service YAML.
   *
   * @param descriptor agent metadata from cloud discovery
   * @return list of config variable DTOs
   */
  public List<AgentConfigVariableDTO> configVariablesFor(@NotNull AgentDescriptor descriptor) {
    List<AgentConfigVariableDTO> result = new ArrayList<>();
    Set<String> usedVariableNames = new HashSet<>();

    // Reconcile pin variable
    if (descriptor.getReconcilePinKey() != null && descriptor.getReconcilePinValue() != null) {
      validateVariableName(descriptor.getReconcilePinKey(), "reconcile pin key", usedVariableNames);
      result.add(AgentConfigVariableDTO.builder()
                     .name(descriptor.getReconcilePinKey())
                     .value(descriptor.getReconcilePinValue())
                     .build());
    }

    // Protocol
    if (descriptor.getProtocol() != null && !descriptor.getProtocol().isEmpty()) {
      validateVariableName("protocol", "protocol field", usedVariableNames);
      result.add(AgentConfigVariableDTO.builder().name("protocol").value(descriptor.getProtocol()).build());
    }

    // Idle session timeout (seconds)
    if (descriptor.getIdleTimeoutSeconds() != null) {
      validateVariableName("idleSessionTimeout", "idle timeout field", usedVariableNames);
      result.add(AgentConfigVariableDTO.builder()
                     .name("idleSessionTimeout")
                     .value(String.valueOf(descriptor.getIdleTimeoutSeconds()))
                     .build());
    }

    // Max lifetime (seconds)
    if (descriptor.getMaxLifetimeSeconds() != null) {
      validateVariableName("maxLifetime", "max lifetime field", usedVariableNames);
      result.add(AgentConfigVariableDTO.builder()
                     .name("maxLifetime")
                     .value(String.valueOf(descriptor.getMaxLifetimeSeconds()))
                     .build());
    }

    // Tags (comma-separated key=value pairs)
    if (descriptor.getTags() != null && !descriptor.getTags().isEmpty()) {
      validateVariableName("tags", "tags field", usedVariableNames);
      result.add(AgentConfigVariableDTO.builder().name("tags").value(descriptor.getTags()).build());
    }

    // Config variables
    if (descriptor.getConfigVariables() != null) {
      descriptor.getConfigVariables().forEach((key, value) -> {
        if (value != null) {
          validateVariableName(key, "config variable key", usedVariableNames);
          result.add(AgentConfigVariableDTO.builder().name(key).value(value).build());
        }
      });
    }

    // Environment variables (parse KEY=VALUE format)
    if (descriptor.getEnvVars() != null) {
      descriptor.getEnvVars().forEach(envVar -> {
        if (envVar != null && !envVar.isEmpty()) {
          int equalsIndex = envVar.indexOf('=');
          if (equalsIndex > 0) {
            String key = envVar.substring(0, equalsIndex);
            String value = envVar.substring(equalsIndex + 1);
            validateVariableName(key, "environment variable key", usedVariableNames);
            result.add(AgentConfigVariableDTO.builder().name(key).value(value).build());
          } else {
            // No '=' found, use the entire string as key with empty value
            validateVariableName(envVar, "environment variable key", usedVariableNames);
            result.add(AgentConfigVariableDTO.builder().name(envVar).value("").build());
          }
        }
      });
    }

    return result;
  }

  // --- Private helpers ---

  private List<NGVariable> buildConfigVariables(AgentDescriptor descriptor) {
    List<NGVariable> variables = new ArrayList<>();
    Set<String> usedVariableNames = new HashSet<>();

    // Reconcile pin variable
    if (descriptor.getReconcilePinKey() != null && descriptor.getReconcilePinValue() != null) {
      validateVariableName(descriptor.getReconcilePinKey(), "reconcile pin key", usedVariableNames);
      variables.add(StringNGVariable.builder()
                        .name(descriptor.getReconcilePinKey())
                        .type(NGVariableType.STRING)
                        .value(ParameterField.createValueField(descriptor.getReconcilePinValue()))
                        .build());
    }

    // Protocol (PLUGIN_CONFIG_protocol)
    if (descriptor.getProtocol() != null && !descriptor.getProtocol().isEmpty()) {
      validateVariableName("protocol", "protocol field", usedVariableNames);
      variables.add(StringNGVariable.builder()
                        .name("protocol")
                        .type(NGVariableType.STRING)
                        .value(ParameterField.createValueField(descriptor.getProtocol()))
                        .build());
    }

    // Idle session timeout in seconds (PLUGIN_CONFIG_idleSessionTimeout)
    if (descriptor.getIdleTimeoutSeconds() != null) {
      validateVariableName("idleSessionTimeout", "idle timeout field", usedVariableNames);
      variables.add(StringNGVariable.builder()
                        .name("idleSessionTimeout")
                        .type(NGVariableType.STRING)
                        .value(ParameterField.createValueField(String.valueOf(descriptor.getIdleTimeoutSeconds())))
                        .build());
    }

    // Maximum lifetime in seconds (PLUGIN_CONFIG_maxLifetime)
    if (descriptor.getMaxLifetimeSeconds() != null) {
      validateVariableName("maxLifetime", "max lifetime field", usedVariableNames);
      variables.add(StringNGVariable.builder()
                        .name("maxLifetime")
                        .type(NGVariableType.STRING)
                        .value(ParameterField.createValueField(String.valueOf(descriptor.getMaxLifetimeSeconds())))
                        .build());
    }

    // Tags as a comma-separated key=value string (PLUGIN_CONFIG_tags)
    if (descriptor.getTags() != null && !descriptor.getTags().isEmpty()) {
      validateVariableName("tags", "tags field", usedVariableNames);
      variables.add(StringNGVariable.builder()
                        .name("tags")
                        .type(NGVariableType.STRING)
                        .value(ParameterField.createValueField(descriptor.getTags()))
                        .build());
    }

    // Config variables
    if (descriptor.getConfigVariables() != null) {
      for (Map.Entry<String, String> entry : descriptor.getConfigVariables().entrySet()) {
        if (entry.getValue() != null) {
          validateVariableName(entry.getKey(), "config variable key", usedVariableNames);
          variables.add(StringNGVariable.builder()
                            .name(entry.getKey())
                            .type(NGVariableType.STRING)
                            .value(ParameterField.createValueField(entry.getValue()))
                            .build());
        }
      }
    }

    // Environment variables (parse KEY=VALUE format)
    if (descriptor.getEnvVars() != null) {
      for (String envVar : descriptor.getEnvVars()) {
        if (envVar != null && !envVar.isEmpty()) {
          int equalsIndex = envVar.indexOf('=');
          if (equalsIndex > 0) {
            String key = envVar.substring(0, equalsIndex);
            String value = envVar.substring(equalsIndex + 1);
            validateVariableName(key, "environment variable key", usedVariableNames);
            variables.add(StringNGVariable.builder()
                              .name(key)
                              .type(NGVariableType.STRING)
                              .value(ParameterField.createValueField(value))
                              .build());
          } else {
            // No '=' found, treat entire string as key with empty value
            validateVariableName(envVar, "environment variable key", usedVariableNames);
            variables.add(StringNGVariable.builder()
                              .name(envVar)
                              .type(NGVariableType.STRING)
                              .value(ParameterField.createValueField(""))
                              .build());
          }
        }
      }
    }

    return variables;
  }

  private static final Pattern VARIABLE_NAME_PATTERN = Pattern.compile(NGRegexValidatorConstants.VARIABLE_NAME_PATTERN);

  /**
   * Fails fast with a clear, actionable message when a cloud-derived key cannot be used as a Harness
   * variable name, or when it collides with another variable already produced for this descriptor.
   * {@link StringNGVariable#getName()} is {@code @Pattern}-constrained and the service YAML requires
   * unique names within {@code configVariables}; without this check an invalid or duplicate key
   * surfaces as an opaque validation error only at service-persist time.
   *
   * @param name the cloud-derived variable name
   * @param origin human-readable description of where the name came from (for the error message)
   * @param usedVariableNames names already produced for this descriptor; {@code name} is added to it on success
   */
  private void validateVariableName(String name, String origin, Set<String> usedVariableNames) {
    if (name == null || !VARIABLE_NAME_PATTERN.matcher(name).matches()) {
      throw new InvalidRequestException(
          String.format(
              "Cannot import agent: %s '%s' is not a valid Harness variable name. Names must start with a letter or "
                  + "underscore and contain only letters, digits, '_', '.', '$' or '-' (max 128 characters). Rename it "
                  + "on the cloud resource and retry the import.",
              origin, name),
          WingsException.USER);
    }
    if (!usedVariableNames.add(name)) {
      throw new InvalidRequestException(
          String.format("Cannot import agent: %s '%s' collides with another config variable, environment variable, or "
                  + "reserved field of the same name. Rename one of them on the cloud resource and retry the import.",
              origin, name),
          WingsException.USER);
    }
  }

  // Each agent platform has its own service type, so the platform configuration sits at the top level of the service
  // spec and the service definition type identifies the platform.
  private ServiceDefinition buildServiceDefinition(
      AgentDescriptor descriptor, AgentPlatform platform, List<NGVariable> configVariables) {
    // Build container source
    ContainerAgentSource containerSource =
        ContainerAgentSource.builder().image(ParameterField.createValueField(descriptor.getImage())).build();

    if (platform == AgentPlatform.AWS_AGENT_CORE) {
      // AWS: set executionRoleArn from identity
      AwsCoreAgentSource awsSource = AwsCoreAgentSource.builder()
                                         .type(AwsCoreAgentSourceType.CONTAINER)
                                         .spec((AgentSourceSpec) containerSource)
                                         .build();

      return ServiceDefinition.builder()
          .type(ServiceDefinitionType.AWS_AGENT_CORE)
          .serviceSpec(AwsAgentCoreServiceSpec.builder()
                           .configVariables(configVariables)
                           .source(awsSource)
                           .executionRoleArn(ParameterField.createValueField(descriptor.getIdentity()))
                           .build())
          .build();
    } else {
      // GCP: no executionRoleArn
      GoogleAgentSource googleSource = GoogleAgentSource.builder()
                                           .type(GoogleAgentSourceType.CONTAINER)
                                           .spec((AgentSourceSpec) containerSource)
                                           .build();

      return ServiceDefinition.builder()
          .type(ServiceDefinitionType.GOOGLE_AGENT_RUNTIME)
          .serviceSpec(
              GoogleAgentRuntimeServiceSpec.builder().configVariables(configVariables).source(googleSource).build())
          .build();
    }
  }
}
