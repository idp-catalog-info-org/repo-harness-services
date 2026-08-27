/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.infrastructure.resource;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.core.service.registries.UnifiedConversionRegistry.convertInfrastructure;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.infra.definition.config.InfrastructureConfig;
import io.harness.cdng.infra.yaml.Infrastructure;
import io.harness.cdng.infra.yaml.K8SDirectInfrastructure;
import io.harness.cdng.infra.yaml.K8sAwsInfrastructure;
import io.harness.cdng.infra.yaml.K8sAzureInfrastructure;
import io.harness.cdng.infra.yaml.K8sGcpInfrastructure;
import io.harness.cdng.infra.yaml.K8sRancherInfrastructure;
import io.harness.cdng.infra.yaml.WithReleaseName;
import io.harness.exception.InvalidRequestException;
import io.harness.infrastructure.unified.UnifiedInfraConverterRequestDTO;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.service.registries.UnifiedConversionRegistry;
import io.harness.pms.merger.helpers.MergeHelper;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlUtils;
import io.harness.serializer.JsonUtils;
import io.harness.unified.cd.infrastructure.InfraConfig;
import io.harness.unified.cd.infrastructure.InfraInfoConfig;
import io.harness.unified.cd.infrastructure.InfraType;
import io.harness.utils.YamlPipelineUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(HarnessTeam.CI)
@UtilityClass
@Slf4j
public class UnifiedInfrastructureConversionUtility {
  /**
   * Map of NG infrastructure kind to Unified infrastructure type.
   */
  private static final Map<String, InfraType> INFRA_TYPE_CONVERSION_MAP =
      Map.ofEntries(Map.entry(InfrastructureKind.KUBERNETES_DIRECT, InfraType.K8S_DIRECT),
          Map.entry(InfrastructureKind.KUBERNETES_GCP, InfraType.K8S_GCP),
          Map.entry(InfrastructureKind.AWS_SAM, InfraType.AWS_SAM),
          Map.entry(InfrastructureKind.SERVERLESS_AWS_LAMBDA, InfraType.SERVERLESS),
          Map.entry(InfrastructureKind.KUBERNETES_AWS, InfraType.K8S_AWS),
          Map.entry(InfrastructureKind.KUBERNETES_AZURE, InfraType.K8S_AZURE),
          Map.entry(InfrastructureKind.KUBERNETES_RANCHER, InfraType.K8S_RANCHER),
          Map.entry(InfrastructureKind.AZURE_FUNCTION, InfraType.AZURE_FUNCTION),
          Map.entry(InfrastructureKind.AZURE_WEB_APP, InfraType.AZURE_WEB_APP),
          Map.entry(InfrastructureKind.AWS_LAMBDA, InfraType.AWS_LAMBDA),
          Map.entry(InfrastructureKind.ECS, InfraType.ECS), Map.entry(InfrastructureKind.ASG, InfraType.ASG),
          Map.entry(InfrastructureKind.ELASTIGROUP, InfraType.ELASTIGROUP));

  /**
   * Map of infrastructure class to release name updater function.
   * Maps each infrastructure type to a lambda that applies withReleaseName.
   * This eliminates the need for if-else chains when updating release names.
   */
  private static final Map<Class<? extends Infrastructure>,
      BiFunction<Infrastructure, ParameterField<String>, Infrastructure>> RELEASE_NAME_UPDATERS =
      Map.of(K8SDirectInfrastructure.class,
          (infra, releaseName)
              -> ((K8SDirectInfrastructure) infra).withReleaseName(releaseName),
          K8sGcpInfrastructure.class,
          (infra, releaseName)
              -> ((K8sGcpInfrastructure) infra).withReleaseName(releaseName),
          K8sAwsInfrastructure.class,
          (infra, releaseName)
              -> ((K8sAwsInfrastructure) infra).withReleaseName(releaseName),
          K8sAzureInfrastructure.class,
          (infra, releaseName)
              -> ((K8sAzureInfrastructure) infra).withReleaseName(releaseName),
          K8sRancherInfrastructure.class,
          (infra, releaseName) -> ((K8sRancherInfrastructure) infra).withReleaseName(releaseName));

  public String getMergedInfrastructureYaml(
      UnifiedInfraConverterRequestDTO requestDTO, InfrastructureEntity infraEntity) throws JsonProcessingException {
    String mergedNgInfraYaml = infraEntity.getYaml();
    if (isNotEmpty(requestDTO.getInfraInputsYaml())) {
      ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
      JsonNode infraInputsJsonNode = yamlReader.readTree(requestDTO.getInfraInputsYaml());
      ObjectNode infraObjectNode = (ObjectNode) infraInputsJsonNode;

      if (infraObjectNode.get(YAMLFieldNameConstants.UUID) != null) {
        infraObjectNode.remove(YAMLFieldNameConstants.UUID);
      }
      Map<String, Object> infraJsonInputsMap = JsonUtils.asMap(infraInputsJsonNode.toString());
      Map<String, Object> mergeableInfraJsonInputsMap = new HashMap<>();
      mergeableInfraJsonInputsMap.put("infrastructureDefinition", infraJsonInputsMap);

      mergedNgInfraYaml = MergeHelper.mergeRuntimeInputValuesAndCheckForRuntimeInOriginalYaml(
          infraEntity.getYaml(), YamlPipelineUtils.writeYamlString(mergeableInfraJsonInputsMap), true, true, false);
    }
    return mergedNgInfraYaml;
  }

  public String toUnifiedInfrastructureYaml(String mergedNgInfrastructureYaml) throws IOException {
    InfrastructureConfig ngInfrastructureConfig =
        YamlUtils.read(mergedNgInfrastructureYaml, InfrastructureConfig.class);
    InfraConfig unifiedInfraConfig = toUnifiedInfra(ngInfrastructureConfig);
    return YamlPipelineUtils.writeYamlString(unifiedInfraConfig);
  }

  public String toUnifiedInfrastructureYaml(InfrastructureConfig ngInfrastructureConfig) throws IOException {
    InfraConfig unifiedInfraConfig = toUnifiedInfra(ngInfrastructureConfig);
    return YamlPipelineUtils.writeYamlString(unifiedInfraConfig);
  }

  public Pair<String, String> getUnifiedInfraAndInputSchemaYaml(
      InfrastructureConfig ngInfrastructureConfig, String infraInputYaml) {
    InfraConfig unifiedInfraConfig = toUnifiedInfra(ngInfrastructureConfig);
    String unifiedInfraYaml = YamlPipelineUtils.writeYamlString(unifiedInfraConfig);
    unifiedInfraYaml = getMergedUnifiedInfraYamlWithInput(unifiedInfraYaml, infraInputYaml);
    return Pair.of(
        unifiedInfraYaml, YamlUtils.generateInputsSectionYaml(unifiedInfraConfig.getGeneratedSchemaForInput()));
  }

  private String getMergedUnifiedInfraYamlWithInput(String unifiedInfraYaml, String infraInputYaml) {
    try {
      if (isNotEmpty(unifiedInfraYaml)) {
        ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
        JsonNode infraInputsJsonNodeWrapper = yamlReader.readTree(infraInputYaml);
        JsonNode infraInputsJsonNode = infraInputsJsonNodeWrapper.get("overlay");
        if (infraInputsJsonNode != null) {
          Map<String, Object> infraJsonInputsMap = JsonUtils.asMap(infraInputsJsonNode.toString());
          Map<String, Object> mergeableInfraJsonInputsMap = new HashMap<>();
          mergeableInfraJsonInputsMap.put("infrastructure", Map.of("with", infraJsonInputsMap));
          unifiedInfraYaml = MergeHelper.mergeRuntimeInputValuesAndCheckForRuntimeInOriginalYaml(
              unifiedInfraYaml, YamlPipelineUtils.writeYamlString(mergeableInfraJsonInputsMap), true, true, false);
        }
      }
    } catch (Exception ex) {
      log.warn("Failed to merge infra input yaml", ex);
    }
    return unifiedInfraYaml;
  }

  /**
   * Converts v0 expression format (<+...>) to v1 format (${{...}}) in YAML string.
   * Enables template default expressions like: ${{ngInfra.spec.connectorRef}}
   *
   * Pattern: <+EXPRESSION> -> ${{EXPRESSION}}
   *
   * @param yaml YAML string with v0 expressions
   * @return YAML string with v1 expressions
   */
  public static String convertExpressionsV0ToV1(String yaml) {
    if (isNotEmpty(yaml)) {
      // Convert <+...> pattern to ${{...}} pattern
      return yaml.replaceAll("<\\+(.*?)>", "\\$\\{\\{$1\\}\\}");
    }
    return yaml;
  }

  /**
   * Converts NG infrastructure to unified infrastructure with minimal structure.
   * Returns InfraInfoConfig with only 'uses' field populated for template-based flow.
   * CI Manager will fetch template and resolve inputs from saved infraV0Yaml.
   *
   * @param ngInfraConfig NG infrastructure configuration
   * @return InfraConfig with minimal InfraInfoConfig (only uses field populated)
   */
  public static InfraConfig toUnifiedInfra(@NonNull InfrastructureConfig ngInfraConfig) {
    if (!isInfraSpecPresent(ngInfraConfig)) {
      throw new InvalidRequestException("Could not find infrastructure configuration for ng infrastructure "
          + ngInfraConfig.getInfrastructureDefinitionConfig().getIdentifier());
    }

    Infrastructure ngInfraSpec = ngInfraConfig.getInfrastructureDefinitionConfig().getSpec();
    String ngInfraSpecKind = ngInfraSpec.getKind();

    // Single conversion call gets both unified type and template action
    UnifiedConversionRegistry.ConversionResult<InfraType> result = convertInfrastructure(ngInfraSpecKind);

    if (result == null) {
      throw new InvalidRequestException(
          String.format("Given NG Infrastructure type [%s], is not supported to be used in Unified Pipeline",
              ngInfraConfig.getInfrastructureDefinitionConfig().getType().getDisplayName()));
    }

    // Return minimal infra with 'uses' and 'action' fields - template will be fetched by CI Manager
    return InfraConfig.builder()
        .infraInfoConfig(InfraInfoConfig.builder()
                             .uses(result.getUnifiedType())
                             .action(result.getTemplateAction())
                             .infraKey(ngInfraSpec.getInfrastructureKeyValues())
                             .allowSimultaneousDeployments(
                                 ngInfraConfig.getInfrastructureDefinitionConfig().getAllowSimultaneousDeployments())
                             .build())
        .build();
  }

  /**
   * Checks if infrastructure spec is present in the configuration.
   */
  private static boolean isInfraSpecPresent(InfrastructureConfig ngInfraConfig) {
    return ngInfraConfig.getInfrastructureDefinitionConfig() != null
        && ngInfraConfig.getInfrastructureDefinitionConfig().getSpec() != null;
  }

  /**
   * Converts releaseName from V0 to V1 format for Infrastructure objects that have the releaseName property.
   * Uses a Map-based lookup approach to avoid if-else chains.
   * Caller should check if infrastructure implements WithReleaseName before calling this method.
   *
   * @param infrastructure the infrastructure object to convert (must implement WithReleaseName)
   * @return infrastructure with converted release name, or original if no conversion needed
   */
  public static Infrastructure convertInfrastructureReleaseNameFromV0ToV1(Infrastructure infrastructure) {
    if (!(infrastructure instanceof WithReleaseName withReleaseName)) {
      return infrastructure;
    }

    // Convert the release name from V0 to V1 format
    ParameterField<String> convertedReleaseName = convertReleaseNameFromV0ToV1(withReleaseName.getReleaseName());

    // If no conversion happened, return original
    if (convertedReleaseName == withReleaseName.getReleaseName()) {
      return infrastructure;
    }

    // Look up the updater function for this infrastructure type and apply it
    BiFunction<Infrastructure, ParameterField<String>, Infrastructure> updater =
        RELEASE_NAME_UPDATERS.get(infrastructure.getClass());

    if (updater != null) {
      return updater.apply(infrastructure, convertedReleaseName);
    }

    // If no updater found, return original (should not happen if WithReleaseName is implemented correctly)
    log.warn("No release name updater found for infrastructure type: {}", infrastructure.getClass().getName());
    return infrastructure;
  }

  /**
   * Converts v0 release name expressions to v1 format.
   * Converts expressions like &lt;+INFRA_KEY_SHORT_ID&gt; to ${{INFRA_KEY_SHORT_ID}}
   *
   * @param releaseNameField the release name parameter field
   * @return converted release name parameter field
   */
  public static ParameterField<String> convertReleaseNameFromV0ToV1(ParameterField<String> releaseNameField) {
    if (ParameterField.isNull(releaseNameField)) {
      return releaseNameField;
    }

    if (!releaseNameField.isExpression()) {
      return releaseNameField;
    }

    String expressionValue = releaseNameField.getExpressionValue();
    if (expressionValue == null) {
      return releaseNameField;
    }

    String convertedExpression = convertV0ExpressionToV1(expressionValue);

    if (!convertedExpression.equals(expressionValue)) {
      return ParameterField.createExpressionField(
          true, convertedExpression, releaseNameField.getInputSetValidator(), releaseNameField.isTypeString());
    }

    return releaseNameField;
  }

  /**
   * Converts v0 expression format to v1 format.
   * Pattern: &lt;+EXPRESSION&gt; -> ${{EXPRESSION}}
   *
   * @param expressionValue the expression value to convert
   * @return converted expression value
   */
  private static String convertV0ExpressionToV1(String expressionValue) {
    if (expressionValue == null) {
      return null;
    }

    // Convert <+...> pattern to ${{...}} pattern
    return expressionValue.replaceAll("<\\+(.*?)>", "\\$\\{\\{$1\\}\\}");
  }
}
