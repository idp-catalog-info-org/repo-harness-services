/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.mapper;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.unified.cd.service.spec.SpotServiceSpec.SpotServiceSpecBuilder;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.bean.yaml.ArtifactListConfig;
import io.harness.cdng.configfile.ConfigFileWrapper;
import io.harness.cdng.hooks.ServiceHookWrapper;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.manifestConfigs.ManifestConfigurations;
import io.harness.cdng.service.ServiceSpec;
import io.harness.cdng.service.beans.AsgServiceSpec;
import io.harness.cdng.service.beans.AwsLambdaServiceSpec;
import io.harness.cdng.service.beans.AwsSamServiceSpec;
import io.harness.cdng.service.beans.AzureContainerAppsServiceSpec;
import io.harness.cdng.service.beans.AzureFunctionServiceSpec;
import io.harness.cdng.service.beans.AzureWebAppServiceSpec;
import io.harness.cdng.service.beans.EcsServiceSpec;
import io.harness.cdng.service.beans.ElastigroupServiceSpec;
import io.harness.cdng.service.beans.GoogleCloudRunServiceSpec;
import io.harness.cdng.service.beans.KubernetesServiceSpec;
import io.harness.cdng.service.beans.NativeHelmServiceSpec;
import io.harness.cdng.service.beans.ServerlessAwsLambdaServiceSpec;
import io.harness.cdng.service.beans.aiagent.AgentSourceSpec;
import io.harness.cdng.service.beans.aiagent.AgentSourceTypeConstants;
import io.harness.cdng.service.beans.aiagent.AwsAgentCoreServiceSpec;
import io.harness.cdng.service.beans.aiagent.AwsCoreAgentSource;
import io.harness.cdng.service.beans.aiagent.ContainerAgentSource;
import io.harness.cdng.service.beans.aiagent.GoogleAgentRuntimeServiceSpec;
import io.harness.cdng.service.beans.aiagent.GoogleAgentSource;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.k8s.ServiceSpecType;
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.pms.yaml.ParameterField;
import io.harness.unified.cd.service.agent.AwsAgentCoreSource;
import io.harness.unified.cd.service.agent.AwsAgentCoreSourceType;
import io.harness.unified.cd.service.agent.GoogleAgentRuntimeSource;
import io.harness.unified.cd.service.agent.GoogleAgentRuntimeSourceType;
import io.harness.unified.cd.service.artifacts.ArtifactWrapper;
import io.harness.unified.cd.service.configfiles.ConfigFile;
import io.harness.unified.cd.service.hooks.ServiceHookConfig;
import io.harness.unified.cd.service.manifests.ManifestConfig;
import io.harness.unified.cd.service.manifests.ManifestWrapper;
import io.harness.unified.cd.service.manifests.ManifestWrapper.ManifestWrapperBuilder;
import io.harness.unified.cd.service.spec.ServiceConfig;
import io.harness.unified.cd.service.spec.ServiceConfig.ServiceConfigBuilder;
import io.harness.unified.cd.service.spec.ServiceInfoConfig;
import io.harness.unified.cd.service.spec.ServiceType;
import io.harness.unified.cd.service.spec.SpotServiceSpec;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * NEW FRAMEWORK: Template-based Service Mapper.
 * This mapper converts NG services to template-compatible structure with inputs mapping.
 */
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.CI)
@Singleton
public class TemplateBasedServiceMapper {
  private final TemplateBasedServiceMapperValidator validator;
  private final TemplateBasedArtifactMapper artifactMapperTemplate;
  private final TemplateBasedManifestMapper manifestMapperTemplate;
  private final TemplateBasedConfigFileMapper configFileMapperTemplate;
  private final TemplateBasedStartupScriptMapper startupScriptMapperTemplate;
  private final TemplateBasedHookMapper hookMapperTemplate;

  public ServiceConfig toUnifiedServiceWithTemplate(@NonNull NGServiceConfig ngServiceConfig) {
    if (!isServiceSpecPresent(ngServiceConfig)) {
      throw new InvalidRequestException("Could not find service configuration for service "
          + ngServiceConfig.getNgServiceV2InfoConfig().getIdentifier());
    }

    ServiceSpec serviceSpecNG = ngServiceConfig.getNgServiceV2InfoConfig().getServiceDefinition().getServiceSpec();
    String serviceSpecNGType = serviceSpecNG.getType();

    ServiceConfigBuilder serviceConfigBuilderUnified = ServiceConfig.builder();
    io.harness.unified.cd.service.spec.ServiceSpec serviceSpecUnified = null;

    ServiceType serviceTypeUnified = ServiceTypeConversionUtils.SERVICE_TYPE_CONVERSION_MAP.get(serviceSpecNGType);
    if (serviceTypeUnified == null) {
      throw new InvalidRequestException(
          String.format("v0 service type [%s] is not supported in unified stage", serviceSpecNGType));
    }

    switch (serviceSpecNGType) {
      case ServiceSpecType.KUBERNETES:
        serviceSpecUnified = toUnifiedKubernetesServiceSpecTemplate(serviceSpecNG);
        break;
      case ServiceSpecType.NATIVE_HELM:
        serviceSpecUnified = toUnifiedNativeHelmServiceSpecTemplate(serviceSpecNG);
        break;
      case ServiceSpecType.AWS_SAM:
        serviceSpecUnified = toUnifiedAwsSamServiceSpecTemplate(serviceSpecNG);
        break;
      case ServiceSpecType.SERVERLESS_AWS_LAMBDA:
        serviceSpecUnified = toUnifiedServerlessServiceSpecTemplate(serviceSpecNG);
        break;
      case ServiceSpecType.GOOGLE_CLOUD_RUN:
        serviceSpecUnified = toUnifiedGoogleCloudRunServiceSpecTemplate(serviceSpecNG);
        break;
      case ServiceSpecType.AZURE_CONTAINER_APPS:
        serviceSpecUnified = toUnifiedAzureContainerAppsServiceSpecTemplate(serviceSpecNG);
        break;
      case ServiceSpecType.AZURE_FUNCTION:
        serviceSpecUnified = toUnifiedAzureFunctionServiceSpecTemplate(serviceSpecNG);
        break;
      case ServiceSpecType.AZURE_WEBAPP:
        serviceSpecUnified = toUnifiedAzureWebAppServiceSpecTemplate(serviceSpecNG);
        break;
      case ServiceSpecType.AWS_LAMBDA:
        serviceSpecUnified = toUnifiedAwsLambdaServiceSpecTemplate(serviceSpecNG);
        break;
      case ServiceSpecType.ECS:
        serviceSpecUnified = toUnifiedEcsServiceSpecTemplate(serviceSpecNG);
        break;
      case ServiceSpecType.ASG:
        serviceSpecUnified = toUnifiedAsgServiceSpecTemplate(serviceSpecNG);
        break;
      case ServiceSpecType.ELASTIGROUP:
        serviceSpecUnified = toUnifiedSpotServiceSpecTemplate(serviceSpecNG);
        break;
      case ServiceSpecType.AWS_AGENT_CORE:
        serviceSpecUnified = toUnifiedAwsAgentCoreServiceSpecTemplate(serviceSpecNG);
        break;
      case ServiceSpecType.GOOGLE_AGENT_RUNTIME:
        serviceSpecUnified = toUnifiedGoogleAgentRuntimeServiceSpecTemplate(serviceSpecNG);
        break;
      default:
        throw new InvalidRequestException(String.format(
            "Mapping from NG service type [%s] to unified service spec is not available", serviceSpecNGType));
    }

    if (serviceSpecUnified == null) {
      log.debug("All entities fell back to POJO path, falling back to POJO path for entire service");
      return null;
    }

    Map<String, Object> serviceInputsV1 = ServiceVariableConversionUtils.toUnifiedInputs(serviceSpecNG.getVariables());

    return serviceConfigBuilderUnified
        .serviceInfoConfig(ServiceInfoConfig.builder()
                               .uses(serviceTypeUnified)
                               .with(serviceSpecUnified)
                               .inputs(serviceInputsV1)
                               .build())
        .build();
  }

  /**
   * Convert Kubernetes service spec with template approach.
   * Returns null if all entities (manifests/artifacts) fall back to POJO path.
   */
  private io.harness.unified.cd.service.spec.ServiceSpec toUnifiedKubernetesServiceSpecTemplate(
      ServiceSpec serviceSpecNG) {
    if (!(serviceSpecNG instanceof KubernetesServiceSpec kubernetesServiceSpec)) {
      throw new InvalidRequestException("Given service spec is not of type kubernetes");
    }

    TemplateManifestArtifactConfigFileConversionResult converted = convertServiceChildrenForTemplates(
        kubernetesServiceSpec.getManifests(), kubernetesServiceSpec.getManifestConfigurations(),
        kubernetesServiceSpec.getArtifacts(), kubernetesServiceSpec.getConfigFiles());

    io.harness.unified.cd.service.spec.KubernetesServiceSpec.KubernetesServiceSpecBuilder builder =
        io.harness.unified.cd.service.spec.KubernetesServiceSpec.builder();
    if (converted.manifestWrapper != null) {
      builder.manifests(converted.manifestWrapper);
    }
    if (converted.artifactWrapper != null) {
      builder.artifacts(converted.artifactWrapper);
    }
    if (isNotEmpty(converted.configFiles)) {
      builder.configFiles(converted.configFiles);
    }
    List<ServiceHookConfig> hooks = convertHooks(kubernetesServiceSpec.getHooks());
    if (isNotEmpty(hooks)) {
      builder.hooks(hooks);
    }
    return builder.build();
  }

  /**
   * Convert Native Helm service spec with template approach (same manifest/artifact rules as Kubernetes).
   * Returns null if all entities fall back to POJO path.
   */
  private io.harness.unified.cd.service.spec.ServiceSpec toUnifiedNativeHelmServiceSpecTemplate(
      ServiceSpec serviceSpecNG) {
    if (!(serviceSpecNG instanceof NativeHelmServiceSpec helmServiceSpec)) {
      throw new InvalidRequestException("Given service spec is not of type native helm");
    }

    TemplateManifestArtifactConfigFileConversionResult converted =
        convertServiceChildrenForTemplates(helmServiceSpec.getManifests(), helmServiceSpec.getManifestConfigurations(),
            helmServiceSpec.getArtifacts(), helmServiceSpec.getConfigFiles());

    io.harness.unified.cd.service.spec.NativeHelmServiceSpec.NativeHelmServiceSpecBuilder builder =
        io.harness.unified.cd.service.spec.NativeHelmServiceSpec.builder();
    if (converted.manifestWrapper != null) {
      builder.manifests(converted.manifestWrapper);
    }
    if (converted.artifactWrapper != null) {
      builder.artifacts(converted.artifactWrapper);
    }
    if (isNotEmpty(converted.configFiles)) {
      builder.configFiles(converted.configFiles);
    }
    List<ServiceHookConfig> hooks = convertHooks(helmServiceSpec.getHooks());
    if (isNotEmpty(hooks)) {
      builder.hooks(hooks);
    }
    return builder.build();
  }

  /**
   * Convert AWS SAM service spec with template approach.
   * Returns null if all entities fall back to POJO path.
   */
  private io.harness.unified.cd.service.spec.ServiceSpec toUnifiedAwsSamServiceSpecTemplate(ServiceSpec serviceSpecNG) {
    if (!(serviceSpecNG instanceof AwsSamServiceSpec awsSamServiceSpec)) {
      throw new InvalidRequestException("Given service spec is not of type AWS SAM");
    }

    TemplateManifestArtifactConfigFileConversionResult converted = convertServiceChildrenForTemplates(
        awsSamServiceSpec.getManifests(), null, awsSamServiceSpec.getArtifacts(), awsSamServiceSpec.getConfigFiles());

    io.harness.unified.cd.service.spec.AwsSamServiceSpec.AwsSamServiceSpecBuilder builder =
        io.harness.unified.cd.service.spec.AwsSamServiceSpec.builder();
    if (converted.manifestWrapper != null) {
      builder.manifests(converted.manifestWrapper);
    }
    if (converted.artifactWrapper != null) {
      builder.artifacts(converted.artifactWrapper);
    }
    if (isNotEmpty(converted.configFiles)) {
      builder.configFiles(converted.configFiles);
    }
    return builder.build();
  }

  /**
   * Convert Serverless AWS Lambda service spec with template approach.
   * Returns null if all entities fall back to POJO path.
   */
  private io.harness.unified.cd.service.spec.ServiceSpec toUnifiedServerlessServiceSpecTemplate(
      ServiceSpec serviceSpecNG) {
    if (!(serviceSpecNG instanceof ServerlessAwsLambdaServiceSpec serverlessServiceSpec)) {
      throw new InvalidRequestException("Given service spec is not of type Serverless AWS Lambda");
    }

    TemplateManifestArtifactConfigFileConversionResult converted =
        convertServiceChildrenForTemplates(serverlessServiceSpec.getManifests(), null,
            serverlessServiceSpec.getArtifacts(), serverlessServiceSpec.getConfigFiles());

    io.harness.unified.cd.service.spec.ServerlessServiceSpec.ServerlessServiceSpecBuilder builder =
        io.harness.unified.cd.service.spec.ServerlessServiceSpec.builder();
    if (converted.manifestWrapper != null) {
      builder.manifests(converted.manifestWrapper);
    }
    if (converted.artifactWrapper != null) {
      builder.artifacts(converted.artifactWrapper);
    }
    if (isNotEmpty(converted.configFiles)) {
      builder.configFiles(converted.configFiles);
    }
    return builder.build();
  }

  /**
   * Convert Google Cloud Run service spec with template approach.
   * Returns null if all entities fall back to POJO path.
   */
  private io.harness.unified.cd.service.spec.ServiceSpec toUnifiedGoogleCloudRunServiceSpecTemplate(
      ServiceSpec serviceSpecNG) {
    if (!(serviceSpecNG instanceof GoogleCloudRunServiceSpec googleCloudRunServiceSpec)) {
      throw new InvalidRequestException("Given service spec is not of type Google Cloud Run");
    }

    TemplateManifestArtifactConfigFileConversionResult converted =
        convertServiceChildrenForTemplates(googleCloudRunServiceSpec.getManifests(), null,
            googleCloudRunServiceSpec.getArtifacts(), googleCloudRunServiceSpec.getConfigFiles());

    io.harness.unified.cd.service.spec.GoogleCloudRunServiceSpec.GoogleCloudRunServiceSpecBuilder builder =
        io.harness.unified.cd.service.spec.GoogleCloudRunServiceSpec.builder();
    if (converted.manifestWrapper != null) {
      builder.manifests(converted.manifestWrapper);
    }
    if (converted.artifactWrapper != null) {
      builder.artifacts(converted.artifactWrapper);
    }
    if (isNotEmpty(converted.configFiles)) {
      builder.configFiles(converted.configFiles);
    }
    return builder.build();
  }

  /**
   * Convert Azure Container Apps service spec with template approach.
   * Returns null if all entities fall back to POJO path.
   */
  private io.harness.unified.cd.service.spec.ServiceSpec toUnifiedAzureContainerAppsServiceSpecTemplate(
      ServiceSpec serviceSpecNG) {
    if (!(serviceSpecNG instanceof AzureContainerAppsServiceSpec azureContainerAppsServiceSpec)) {
      throw new InvalidRequestException("Given service spec is not of type Azure Container Apps");
    }

    TemplateManifestArtifactConfigFileConversionResult converted =
        convertServiceChildrenForTemplates(azureContainerAppsServiceSpec.getManifests(), null,
            azureContainerAppsServiceSpec.getArtifacts(), azureContainerAppsServiceSpec.getConfigFiles());

    io.harness.unified.cd.service.spec.AzureContainerAppsServiceSpec.AzureContainerAppsServiceSpecBuilder builder =
        io.harness.unified.cd.service.spec.AzureContainerAppsServiceSpec.builder();
    if (converted.manifestWrapper != null) {
      builder.manifests(converted.manifestWrapper);
    }
    if (converted.artifactWrapper != null) {
      builder.artifacts(converted.artifactWrapper);
    }
    if (isNotEmpty(converted.configFiles)) {
      builder.configFiles(converted.configFiles);
    }
    return builder.build();
  }

  /**
   * Convert Azure Function service spec with template approach.
   * Returns null if all entities fall back to POJO path.
   */
  private io.harness.unified.cd.service.spec.ServiceSpec toUnifiedAzureFunctionServiceSpecTemplate(
      ServiceSpec serviceSpecNG) {
    if (!(serviceSpecNG instanceof AzureFunctionServiceSpec azureFunctionServiceSpec)) {
      throw new InvalidRequestException("Given service spec is not of type Azure Function");
    }

    TemplateManifestArtifactConfigFileConversionResult converted =
        convertServiceChildrenForTemplates(azureFunctionServiceSpec.getManifests(), null,
            azureFunctionServiceSpec.getArtifacts(), azureFunctionServiceSpec.getConfigFiles());

    io.harness.unified.cd.service.spec.AzureFunctionServiceSpec.AzureFunctionServiceSpecBuilder builder =
        io.harness.unified.cd.service.spec.AzureFunctionServiceSpec.builder();
    if (converted.manifestWrapper != null) {
      builder.manifests(converted.manifestWrapper);
    }
    if (converted.artifactWrapper != null) {
      builder.artifacts(converted.artifactWrapper);
    }
    if (isNotEmpty(converted.configFiles)) {
      builder.configFiles(converted.configFiles);
    }
    return builder.build();
  }

  /**
   * Convert Azure Web App service spec with template approach.
   * Returns null if all entities fall back to POJO path.
   */
  private io.harness.unified.cd.service.spec.ServiceSpec toUnifiedAzureWebAppServiceSpecTemplate(
      ServiceSpec serviceSpecNG) {
    if (!(serviceSpecNG instanceof AzureWebAppServiceSpec azureWebAppServiceSpec)) {
      throw new InvalidRequestException("Given service spec is not of type Azure Web App");
    }

    TemplateManifestArtifactConfigFileConversionResult converted =
        convertServiceChildrenForTemplates(azureWebAppServiceSpec.getManifests(), null,
            azureWebAppServiceSpec.getArtifacts(), azureWebAppServiceSpec.getConfigFiles());

    io.harness.unified.cd.service.spec.AzureWebAppServiceSpec.AzureWebAppServiceSpecBuilder builder =
        io.harness.unified.cd.service.spec.AzureWebAppServiceSpec.builder();
    if (converted.manifestWrapper != null) {
      builder.manifests(converted.manifestWrapper);
    }
    if (converted.artifactWrapper != null) {
      builder.artifacts(converted.artifactWrapper);
    }
    if (isNotEmpty(converted.configFiles)) {
      builder.configFiles(converted.configFiles);
    }
    return builder.build();
  }

  /**
   * Convert AWS Lambda service spec with template approach.
   * Returns null if all entities fall back to POJO path.
   */
  private io.harness.unified.cd.service.spec.ServiceSpec toUnifiedAwsLambdaServiceSpecTemplate(
      ServiceSpec serviceSpecNG) {
    if (!(serviceSpecNG instanceof AwsLambdaServiceSpec awsLambdaServiceSpec)) {
      throw new InvalidRequestException("Given service spec is not of type AWS Lambda");
    }

    TemplateManifestArtifactConfigFileConversionResult converted =
        convertServiceChildrenForTemplates(awsLambdaServiceSpec.getManifests(), null,
            awsLambdaServiceSpec.getArtifacts(), awsLambdaServiceSpec.getConfigFiles());

    io.harness.unified.cd.service.spec.AwsLambdaServiceSpec.AwsLambdaServiceSpecBuilder builder =
        io.harness.unified.cd.service.spec.AwsLambdaServiceSpec.builder();
    if (converted.manifestWrapper != null) {
      builder.manifests(converted.manifestWrapper);
    }
    if (converted.artifactWrapper != null) {
      builder.artifacts(converted.artifactWrapper);
    }
    if (isNotEmpty(converted.configFiles)) {
      builder.configFiles(converted.configFiles);
    }
    return builder.build();
  }

  /**
   * Convert ECS service spec with template approach.
   * Returns null if all entities fall back to POJO path.
   */
  private io.harness.unified.cd.service.spec.ServiceSpec toUnifiedEcsServiceSpecTemplate(ServiceSpec serviceSpecNG) {
    if (!(serviceSpecNG instanceof EcsServiceSpec ecsServiceSpec)) {
      throw new InvalidRequestException("Given service spec is not of type ECS");
    }

    TemplateManifestArtifactConfigFileConversionResult converted = convertServiceChildrenForTemplates(
        ecsServiceSpec.getManifests(), null, ecsServiceSpec.getArtifacts(), ecsServiceSpec.getConfigFiles());

    io.harness.unified.cd.service.spec.EcsServiceSpec.EcsServiceSpecBuilder builder =
        io.harness.unified.cd.service.spec.EcsServiceSpec.builder();
    if (converted.manifestWrapper != null) {
      builder.manifests(converted.manifestWrapper);
    }
    if (converted.artifactWrapper != null) {
      builder.artifacts(converted.artifactWrapper);
    }
    if (isNotEmpty(converted.configFiles)) {
      builder.configFiles(converted.configFiles);
    }
    if (ecsServiceSpec.getEcsTaskDefinitionArn() != null) {
      builder.ecsTaskDefinitionArn(ecsServiceSpec.getEcsTaskDefinitionArn());
    }
    return builder.build();
  }

  /**
   * Convert ASG service spec with template approach.
   * Returns null if all entities fall back to POJO path.
   */
  private io.harness.unified.cd.service.spec.ServiceSpec toUnifiedAsgServiceSpecTemplate(ServiceSpec serviceSpecNG) {
    if (!(serviceSpecNG instanceof AsgServiceSpec asgServiceSpec)) {
      throw new InvalidRequestException("Given service spec is not of type ASG");
    }

    TemplateManifestArtifactConfigFileConversionResult converted = convertServiceChildrenForTemplates(
        asgServiceSpec.getManifests(), null, asgServiceSpec.getArtifacts(), asgServiceSpec.getConfigFiles());

    io.harness.unified.cd.service.spec.AsgServiceSpec.AsgServiceSpecBuilder builder =
        io.harness.unified.cd.service.spec.AsgServiceSpec.builder();
    if (converted.manifestWrapper != null) {
      builder.manifests(converted.manifestWrapper);
    }
    if (converted.artifactWrapper != null) {
      builder.artifacts(converted.artifactWrapper);
    }
    if (isNotEmpty(converted.configFiles)) {
      builder.configFiles(converted.configFiles);
    }
    if (converted.manifestWrapper == null && converted.artifactWrapper == null && isEmpty(converted.configFiles)) {
      return null;
    }
    return builder.build();
  }

  /**
   * Convert Spot (NG Elastigroup) service spec with template approach.
   * Returns null if all entities fall back to POJO path.
   *
   * <p>Spot only supports config files for the Elastigroup JSON (config file id {@code elastigroup});
   * manifests are intentionally not converted here since they are config files.
   */
  private io.harness.unified.cd.service.spec.ServiceSpec toUnifiedSpotServiceSpecTemplate(ServiceSpec serviceSpecNG) {
    if (!(serviceSpecNG instanceof ElastigroupServiceSpec elastigroupServiceSpec)) {
      throw new InvalidRequestException("Given service spec is not of type Elastigroup");
    }

    TemplateManifestArtifactConfigFileConversionResult converted = convertServiceChildrenForTemplates(
        null, null, elastigroupServiceSpec.getArtifacts(), elastigroupServiceSpec.getConfigFiles());

    io.harness.unified.cd.service.startupscript.StartupScriptConfiguration startupScriptUnified =
        startupScriptMapperTemplate.toUnifiedStartupScriptWithInputs(elastigroupServiceSpec.getStartupScript());
    if (elastigroupServiceSpec.getStartupScript() != null && startupScriptUnified == null) {
      log.debug("Spot startup script is not supported by unified conversion; falling back to POJO path");
      return null;
    }

    SpotServiceSpecBuilder builder = SpotServiceSpec.builder();
    if (converted.artifactWrapper != null) {
      builder.artifacts(converted.artifactWrapper);
    }
    if (isNotEmpty(converted.configFiles)) {
      builder.configFiles(converted.configFiles);
    }
    if (startupScriptUnified != null) {
      builder.startupScript(startupScriptUnified);
    }
    if (converted.artifactWrapper == null && isEmpty(converted.configFiles) && startupScriptUnified == null) {
      return null;
    }
    return builder.build();
  }

  /**
   * Convert AWS Agent Core service spec with template approach.
   * Returns null if the source or the agent deploy config cannot be represented in unified format.
   */
  private io.harness.unified.cd.service.spec.ServiceSpec toUnifiedAwsAgentCoreServiceSpecTemplate(
      ServiceSpec serviceSpecNG) {
    if (!(serviceSpecNG instanceof AwsAgentCoreServiceSpec awsAgentCoreServiceSpec)) {
      throw new InvalidRequestException("Given service spec is not of type AwsAgentCore");
    }

    if (isNotEmpty(awsAgentCoreServiceSpec.getManifests())) {
      log.debug("AWS Agent Core deploy config is not supported by unified conversion; falling back to POJO path");
      return null;
    }

    AwsCoreAgentSource sourceNG = awsAgentCoreServiceSpec.getSource();
    io.harness.unified.cd.service.agent.AgentSourceSpec sourceSpecUnified =
        sourceNG == null ? null : toUnifiedAgentSourceSpec(sourceNG.getSpec());
    if (sourceSpecUnified == null || !AgentSourceTypeConstants.CONTAINER.equals(sourceNG.getType())) {
      log.debug("AWS Agent Core source is not supported by unified conversion; falling back to POJO path");
      return null;
    }

    io.harness.unified.cd.service.spec.AwsAgentCoreServiceSpec.AwsAgentCoreServiceSpecBuilder builder =
        io.harness.unified.cd.service.spec.AwsAgentCoreServiceSpec.builder()
            .source(AwsAgentCoreSource.builder().uses(AwsAgentCoreSourceType.CONTAINER).with(sourceSpecUnified).build())
            .executionRoleArn(awsAgentCoreServiceSpec.getExecutionRoleArn());

    Map<String, Object> configVariablesUnified =
        ServiceVariableConversionUtils.toUnifiedInputs(awsAgentCoreServiceSpec.getConfigVariables());
    if (isNotEmpty(configVariablesUnified)) {
      builder.configVariables(configVariablesUnified);
    }
    return builder.build();
  }

  /**
   * Convert Google Agent Runtime service spec with template approach.
   * Returns null if the source cannot be represented in unified format.
   */
  private io.harness.unified.cd.service.spec.ServiceSpec toUnifiedGoogleAgentRuntimeServiceSpecTemplate(
      ServiceSpec serviceSpecNG) {
    if (!(serviceSpecNG instanceof GoogleAgentRuntimeServiceSpec googleAgentRuntimeServiceSpec)) {
      throw new InvalidRequestException("Given service spec is not of type GoogleAgentRuntime");
    }

    GoogleAgentSource sourceNG = googleAgentRuntimeServiceSpec.getSource();
    io.harness.unified.cd.service.agent.AgentSourceSpec sourceSpecUnified =
        sourceNG == null ? null : toUnifiedAgentSourceSpec(sourceNG.getSpec());
    if (sourceSpecUnified == null || !AgentSourceTypeConstants.CONTAINER.equals(sourceNG.getType())) {
      log.debug("Google Agent Runtime source is not supported by unified conversion; falling back to POJO path");
      return null;
    }

    io.harness.unified.cd.service.spec.GoogleAgentRuntimeServiceSpec.GoogleAgentRuntimeServiceSpecBuilder builder =
        io.harness.unified.cd.service.spec.GoogleAgentRuntimeServiceSpec.builder().source(
            GoogleAgentRuntimeSource.builder()
                .uses(GoogleAgentRuntimeSourceType.CONTAINER)
                .with(sourceSpecUnified)
                .build());

    Map<String, Object> configVariablesUnified =
        ServiceVariableConversionUtils.toUnifiedInputs(googleAgentRuntimeServiceSpec.getConfigVariables());
    if (isNotEmpty(configVariablesUnified)) {
      builder.configVariables(configVariablesUnified);
    }
    return builder.build();
  }

  private io.harness.unified.cd.service.agent.AgentSourceSpec toUnifiedAgentSourceSpec(AgentSourceSpec sourceSpecNG) {
    if (!(sourceSpecNG instanceof ContainerAgentSource containerAgentSource)) {
      return null;
    }
    return io.harness.unified.cd.service.agent.ContainerAgentSource.builder()
        .image(containerAgentSource.getImage())
        .build();
  }

  private List<ServiceHookConfig> convertHooks(List<ServiceHookWrapper> hookWrappers) {
    return hookMapperTemplate.toUnifiedHooks(hookWrappers);
  }

  private TemplateManifestArtifactConfigFileConversionResult convertServiceChildrenForTemplates(
      List<ManifestConfigWrapper> manifestsRaw, ManifestConfigurations manifestConfigurationsOrNull,
      ArtifactListConfig artifactsOrNull, List<ConfigFileWrapper> configFilesNG) {
    TemplateManifestArtifactConfigFileConversionResult result =
        new TemplateManifestArtifactConfigFileConversionResult();

    if (isNotEmpty(manifestsRaw)) {
      List<ManifestConfigWrapper> manifestsToConvert = manifestsRaw;
      if (manifestConfigurationsOrNull != null
          && ParameterField.isNotNull(manifestConfigurationsOrNull.getPrimaryManifestRef())) {
        manifestsToConvert = PrimaryManifestFilterUtils.filterManifestWrappersForPrimary(
            manifestsToConvert, manifestConfigurationsOrNull.getPrimaryManifestRef());
      }
      List<ManifestConfig> unifiedServiceManifests =
          manifestMapperTemplate.toUnifiedManifestsWithInputs(manifestsToConvert);

      if (isNotEmpty(unifiedServiceManifests)) {
        ManifestWrapperBuilder manifestWrapperBuilder = ManifestWrapper.builder().sources(unifiedServiceManifests);

        if (manifestConfigurationsOrNull != null
            && ParameterField.isNotNull(manifestConfigurationsOrNull.getPrimaryManifestRef())) {
          PrimaryManifestFilterUtils.setPrimaryManifestRef(
              manifestWrapperBuilder, manifestConfigurationsOrNull.getPrimaryManifestRef());
        }

        result.manifestWrapper = manifestWrapperBuilder.build();
      } else {
        log.debug("All manifests fell back to POJO path, skipping template-based manifest conversion");
      }
    }

    if (artifactsOrNull != null) {
      ArtifactWrapper artifactWrapperUnified =
          artifactMapperTemplate.toUnifiedArtifactWrapperWithInputs(artifactsOrNull);

      if (artifactWrapperUnified != null && artifactWrapperUnified.getSources() != null
          && isNotEmpty(artifactWrapperUnified.getSources())) {
        result.artifactWrapper = artifactWrapperUnified;
      } else {
        log.debug("All artifacts fell back to POJO path, skipping template-based artifact conversion");
      }
    }

    if (isNotEmpty(configFilesNG)) {
      List<ConfigFile> unifiedConfigFiles = configFileMapperTemplate.toUnifiedConfigFilesWithInputs(configFilesNG);
      if (isNotEmpty(unifiedConfigFiles)) {
        result.configFiles = unifiedConfigFiles;
      } else {
        log.debug("All config files fell back to POJO path, skipping template-based config file conversion");
      }
    }

    return result;
  }

  private static final class TemplateManifestArtifactConfigFileConversionResult {
    private ManifestWrapper manifestWrapper;
    private ArtifactWrapper artifactWrapper;
    private List<ConfigFile> configFiles;

    private TemplateManifestArtifactConfigFileConversionResult() {}
  }

  /**
   * Check if service spec is present.
   */
  private boolean isServiceSpecPresent(NGServiceConfig ngServiceConfig) {
    return ngServiceConfig.getNgServiceV2InfoConfig() != null
        && ngServiceConfig.getNgServiceV2InfoConfig().getServiceDefinition() != null
        && ngServiceConfig.getNgServiceV2InfoConfig().getServiceDefinition().getServiceSpec() != null;
  }
}
