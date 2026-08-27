/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.mapper.v1;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidRequestException;
import io.harness.ngtriggers.beans.source.artifact.AMIRegistrySpec;
import io.harness.ngtriggers.beans.source.artifact.AcrSpec;
import io.harness.ngtriggers.beans.source.artifact.AmazonS3RegistrySpec;
import io.harness.ngtriggers.beans.source.artifact.ArtifactType;
import io.harness.ngtriggers.beans.source.artifact.ArtifactTypeSpec;
import io.harness.ngtriggers.beans.source.artifact.ArtifactoryRegistrySpec;
import io.harness.ngtriggers.beans.source.artifact.AzureArtifactsRegistrySpec;
import io.harness.ngtriggers.beans.source.artifact.BambooRegistrySpec;
import io.harness.ngtriggers.beans.source.artifact.DockerRegistrySpec;
import io.harness.ngtriggers.beans.source.artifact.EcrSpec;
import io.harness.ngtriggers.beans.source.artifact.GarSpec;
import io.harness.ngtriggers.beans.source.artifact.GcrSpec;
import io.harness.ngtriggers.beans.source.artifact.GithubPackagesSpec;
import io.harness.ngtriggers.beans.source.artifact.GoolgeCloudStorageRegistrySpec;
import io.harness.ngtriggers.beans.source.artifact.JenkinsRegistrySpec;
import io.harness.ngtriggers.beans.source.artifact.Nexus2RegistrySpec;
import io.harness.ngtriggers.beans.source.artifact.NexusRegistrySpec;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.core.variables.NGVariableV1;
import io.harness.yaml.core.variables.NumberNGVariable;
import io.harness.yaml.core.variables.SecretNGVariable;
import io.harness.yaml.core.variables.StringNGVariable;
import io.harness.yaml.core.variables.v1.BooleanNGVariableV1;
import io.harness.yaml.core.variables.v1.NGVariableV1Wrapper;
import io.harness.yaml.core.variables.v1.NumberNGVariableV1;
import io.harness.yaml.core.variables.v1.SecretNGVariableV1;
import io.harness.yaml.core.variables.v1.StringNGVariableV1;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@OwnedBy(HarnessTeam.PIPELINE)
public class NGArtifactTriggerElementMapper {
  ArtifactType toArtifactTriggerType(io.harness.ngtriggers.beans.source.v1.artifact.ArtifactType typeEnum) {
    switch (typeEnum) {
      case ACR:
        return ArtifactType.ACR;
      case ECR:
        return ArtifactType.ECR;
      case GCR:
        return ArtifactType.GCR;
      case BAMBOO:
        return ArtifactType.BAMBOO;
      case JENKINS:
        return ArtifactType.JENKINS;
      case AMAZON_S3:
        return ArtifactType.AMAZON_S3;
      case AZURE_ARTIFACTS:
        return ArtifactType.AZURE_ARTIFACTS;
      case CUSTOM_ARTIFACT:
        return ArtifactType.CUSTOM_ARTIFACT;
      case DOCKER_REGISTRY:
        return ArtifactType.DOCKER_REGISTRY;
      case NEXUS2_REGISTRY:
        return ArtifactType.NEXUS2_REGISTRY;
      case NEXUS3_REGISTRY:
        return ArtifactType.NEXUS3_REGISTRY;
      case AMI:
        return ArtifactType.AMI;
      case GOOGLE_CLOUD_STORAGE:
        return ArtifactType.GOOGLE_CLOUD_STORAGE;
      case ARTIFACTORY_REGISTRY:
        return ArtifactType.ARTIFACTORY_REGISTRY;
      case GITHUB_PACKAGES:
        return ArtifactType.GITHUB_PACKAGES;
      case GoogleArtifactRegistry:
        return ArtifactType.GoogleArtifactRegistry;
      default:
        throw new InvalidRequestException("Artifact Trigger Type " + typeEnum + " is invalid");
    }
  }

  Pair<String, String> getImageAndTagFromLocation(String location) {
    String[] strings = location.split(":");
    return Pair.of(strings[0], strings[1]);
  }

  ArtifactTypeSpec toArtifactTypeSpec(io.harness.ngtriggers.beans.source.v1.artifact.ArtifactTypeSpec spec,
      io.harness.ngtriggers.beans.source.v1.artifact.ArtifactType artifactType) {
    switch (artifactType) {
      case GCR:
        io.harness.ngtriggers.beans.source.v1.artifact.GcrSpec gcrArtifactSpec =
            (io.harness.ngtriggers.beans.source.v1.artifact.GcrSpec) spec;
        return GcrSpec.builder()
            .connectorRef(spec.fetchConnectorRef())
            .eventConditions(spec.fetchEventDataConditions())
            .imagePath(getImageAndTagFromLocation(gcrArtifactSpec.getLocation()).getLeft())
            .jexlCondition(spec.fetchJexlArtifactConditions())
            .metaDataConditions(spec.fetchMetaDataConditions())
            .registryHostname(gcrArtifactSpec.getHost())
            .tag(getImageAndTagFromLocation(gcrArtifactSpec.getLocation()).getRight())
            .build();
      case GoogleArtifactRegistry:
        io.harness.ngtriggers.beans.source.v1.artifact.GarSpec garArtifactSpec =
            (io.harness.ngtriggers.beans.source.v1.artifact.GarSpec) spec;
        return GarSpec.builder()
            .connectorRef(spec.fetchConnectorRef())
            .eventConditions(spec.fetchEventDataConditions())
            .jexlCondition(spec.fetchJexlArtifactConditions())
            .metaDataConditions(spec.fetchMetaDataConditions())
            .pkg(garArtifactSpec.getPkg())
            .project(garArtifactSpec.getProject())
            .region(garArtifactSpec.getRegion())
            .version(garArtifactSpec.getVersion())
            .repositoryName(garArtifactSpec.getRepo())
            .build();
      case GITHUB_PACKAGES:
        io.harness.ngtriggers.beans.source.v1.artifact.GithubPackagesSpec gprArtifactSpec =
            (io.harness.ngtriggers.beans.source.v1.artifact.GithubPackagesSpec) spec;
        return GithubPackagesSpec.builder()
            .connectorRef(spec.fetchConnectorRef())
            .eventConditions(spec.fetchEventDataConditions())
            .jexlCondition(spec.fetchJexlArtifactConditions())
            .metaDataConditions(spec.fetchMetaDataConditions())
            .org(gprArtifactSpec.getOrg())
            .packageType(gprArtifactSpec.getPkg().getType())
            .packageName(gprArtifactSpec.getPkg().getName())
            .build();
      case ARTIFACTORY_REGISTRY:
        io.harness.ngtriggers.beans.source.v1.artifact.ArtifactoryRegistrySpec artifactoryRegistryArtifactTriggerSpec =
            (io.harness.ngtriggers.beans.source.v1.artifact.ArtifactoryRegistrySpec) spec;
        return ArtifactoryRegistrySpec.builder()
            .connectorRef(spec.fetchConnectorRef())
            .eventConditions(spec.fetchEventDataConditions())
            .jexlCondition(spec.fetchJexlArtifactConditions())
            .metaDataConditions(spec.fetchMetaDataConditions())
            .artifactDirectory(artifactoryRegistryArtifactTriggerSpec.getDir())
            .artifactFilter(artifactoryRegistryArtifactTriggerSpec.getFilter())
            .artifactPath(artifactoryRegistryArtifactTriggerSpec.getPath())
            .repository(artifactoryRegistryArtifactTriggerSpec.getRepo().getName())
            .repositoryFormat(artifactoryRegistryArtifactTriggerSpec.getRepo().getFormat())
            .repositoryUrl(artifactoryRegistryArtifactTriggerSpec.getRepo().getUrl())
            .build();
      case GOOGLE_CLOUD_STORAGE:
        io.harness.ngtriggers.beans.source.v1.artifact.GoolgeCloudStorageRegistrySpec gcsArtifactTriggerSpec =
            (io.harness.ngtriggers.beans.source.v1.artifact.GoolgeCloudStorageRegistrySpec) spec;
        return GoolgeCloudStorageRegistrySpec.builder()
            .connectorRef(spec.fetchConnectorRef())
            .eventConditions(spec.fetchEventDataConditions())
            .jexlCondition(spec.fetchJexlArtifactConditions())
            .metaDataConditions(spec.fetchMetaDataConditions())
            .artifactPath(gcsArtifactTriggerSpec.getPath())
            .bucket(gcsArtifactTriggerSpec.getBucket())
            .project(gcsArtifactTriggerSpec.getProject())
            .build();
      case AMI:
        io.harness.ngtriggers.beans.source.v1.artifact.AMIRegistrySpec amiArtifactTriggerSpec =
            (io.harness.ngtriggers.beans.source.v1.artifact.AMIRegistrySpec) spec;
        return AMIRegistrySpec.builder()
            .connectorRef(spec.fetchConnectorRef())
            .eventConditions(spec.fetchEventDataConditions())
            .jexlCondition(spec.fetchJexlArtifactConditions())
            .metaDataConditions(spec.fetchMetaDataConditions())
            .filters(amiArtifactTriggerSpec.getFilters())
            .region(amiArtifactTriggerSpec.getRegion())
            .tags(amiArtifactTriggerSpec.getTags())
            .version(amiArtifactTriggerSpec.getVersion())
            .versionRegex(amiArtifactTriggerSpec.getVersion_regex())
            .build();
      case NEXUS3_REGISTRY:
        io.harness.ngtriggers.beans.source.v1.artifact.NexusRegistrySpec nexus3ArtifactTriggerSpec =
            (io.harness.ngtriggers.beans.source.v1.artifact.NexusRegistrySpec) spec;
        return NexusRegistrySpec.builder()
            .connectorRef(spec.fetchConnectorRef())
            .eventConditions(spec.fetchEventDataConditions())
            .jexlCondition(spec.fetchJexlArtifactConditions())
            .metaDataConditions(spec.fetchMetaDataConditions())
            .artifactId(nexus3ArtifactTriggerSpec.getArtifact())
            .classifier(nexus3ArtifactTriggerSpec.getClassifier())
            .extension(nexus3ArtifactTriggerSpec.getExtension())
            .group(nexus3ArtifactTriggerSpec.getGroup())
            .groupId(nexus3ArtifactTriggerSpec.getGroup_id())
            .imagePath(getImageAndTagFromLocation(nexus3ArtifactTriggerSpec.getLocation()).getLeft())
            .packageName(nexus3ArtifactTriggerSpec.getPkg())
            .repository(nexus3ArtifactTriggerSpec.getRepo().getName())
            .repositoryFormat(nexus3ArtifactTriggerSpec.getRepo().getFormat())
            .repositoryUrl(nexus3ArtifactTriggerSpec.getRepo().getUrl())
            .tag(getImageAndTagFromLocation(nexus3ArtifactTriggerSpec.getLocation()).getRight())
            .build();
      case DOCKER_REGISTRY:
        io.harness.ngtriggers.beans.source.v1.artifact.DockerRegistrySpec dockerRegistryArtifactTriggerSpec =
            (io.harness.ngtriggers.beans.source.v1.artifact.DockerRegistrySpec) spec;
        return DockerRegistrySpec.builder()
            .connectorRef(spec.fetchConnectorRef())
            .eventConditions(spec.fetchEventDataConditions())
            .jexlCondition(spec.fetchJexlArtifactConditions())
            .metaDataConditions(spec.fetchMetaDataConditions())
            .imagePath(getImageAndTagFromLocation(dockerRegistryArtifactTriggerSpec.getLocation()).getLeft())
            .tag(getImageAndTagFromLocation(dockerRegistryArtifactTriggerSpec.getLocation()).getRight())
            .build();
      case CUSTOM_ARTIFACT:
        io.harness.ngtriggers.beans.source.v1.artifact.CustomArtifactSpec customArtifactTriggerSpec =
            (io.harness.ngtriggers.beans.source.v1.artifact.CustomArtifactSpec) spec;
        return io.harness.ngtriggers.beans.source.artifact.CustomArtifactSpec.builder()
            .eventConditions(spec.fetchEventDataConditions())
            .jexlCondition(spec.fetchJexlArtifactConditions())
            .metaDataConditions(spec.fetchMetaDataConditions())
            .artifactsArrayPath(customArtifactTriggerSpec.getPath())
            .versionPath(customArtifactTriggerSpec.getVersion_path())
            .inputs(toNGVariableList(customArtifactTriggerSpec.getInputs()))
            .version(customArtifactTriggerSpec.getVersion())
            .metadata(customArtifactTriggerSpec.getMetadata())
            .script(customArtifactTriggerSpec.getScript())
            .build();
      case NEXUS2_REGISTRY:
        io.harness.ngtriggers.beans.source.v1.artifact.Nexus2RegistrySpec nexus2ArtifactTriggerSpec =
            (io.harness.ngtriggers.beans.source.v1.artifact.Nexus2RegistrySpec) spec;
        return Nexus2RegistrySpec.builder()
            .connectorRef(spec.fetchConnectorRef())
            .eventConditions(spec.fetchEventDataConditions())
            .jexlCondition(spec.fetchJexlArtifactConditions())
            .metaDataConditions(spec.fetchMetaDataConditions())
            .artifactId(nexus2ArtifactTriggerSpec.getArtifact())
            .classifier(nexus2ArtifactTriggerSpec.getClassifier())
            .extension(nexus2ArtifactTriggerSpec.getExtension())
            .groupId(nexus2ArtifactTriggerSpec.getGroup_id())
            .packageName(nexus2ArtifactTriggerSpec.getPkg())
            .repositoryFormat(nexus2ArtifactTriggerSpec.getRepo().getFormat())
            .repositoryUrl(nexus2ArtifactTriggerSpec.getRepo().getUrl())
            .tag(nexus2ArtifactTriggerSpec.getTag())
            .repositoryName(nexus2ArtifactTriggerSpec.getRepo().getName())
            .build();
      case AZURE_ARTIFACTS:
        io.harness.ngtriggers.beans.source.v1.artifact.AzureArtifactsRegistrySpec azureArtifactTriggerSpec =
            (io.harness.ngtriggers.beans.source.v1.artifact.AzureArtifactsRegistrySpec) spec;
        return AzureArtifactsRegistrySpec.builder()
            .connectorRef(spec.fetchConnectorRef())
            .eventConditions(spec.fetchEventDataConditions())
            .jexlCondition(spec.fetchJexlArtifactConditions())
            .metaDataConditions(spec.fetchMetaDataConditions())
            .packageName(azureArtifactTriggerSpec.getPkg().getName())
            .feed(azureArtifactTriggerSpec.getFeed())
            .packageType(azureArtifactTriggerSpec.getPkg().getType())
            .project(azureArtifactTriggerSpec.getProject())
            .version(azureArtifactTriggerSpec.getVersion())
            .versionRegex(azureArtifactTriggerSpec.getVersion_regex())
            .build();
      case AMAZON_S3:
        io.harness.ngtriggers.beans.source.v1.artifact.AmazonS3RegistrySpec amazonS3ArtifactTriggerSpec =
            (io.harness.ngtriggers.beans.source.v1.artifact.AmazonS3RegistrySpec) spec;
        return AmazonS3RegistrySpec.builder()
            .connectorRef(spec.fetchConnectorRef())
            .eventConditions(spec.fetchEventDataConditions())
            .jexlCondition(spec.fetchJexlArtifactConditions())
            .metaDataConditions(spec.fetchMetaDataConditions())
            .bucketName(amazonS3ArtifactTriggerSpec.getBucket())
            .filePathRegex(amazonS3ArtifactTriggerSpec.getPath_regex())
            .region(amazonS3ArtifactTriggerSpec.getRegion())
            .build();
      case JENKINS:
        io.harness.ngtriggers.beans.source.v1.artifact.JenkinsRegistrySpec jenkinsArtifactTriggerSpec =
            (io.harness.ngtriggers.beans.source.v1.artifact.JenkinsRegistrySpec) spec;
        return JenkinsRegistrySpec.builder()
            .connectorRef(spec.fetchConnectorRef())
            .eventConditions(spec.fetchEventDataConditions())
            .jexlCondition(spec.fetchJexlArtifactConditions())
            .metaDataConditions(spec.fetchMetaDataConditions())
            .artifactPath(jenkinsArtifactTriggerSpec.getPath())
            .jobName(jenkinsArtifactTriggerSpec.getJob())
            .build(jenkinsArtifactTriggerSpec.getBuild())
            .build();
      case BAMBOO:
        io.harness.ngtriggers.beans.source.v1.artifact.BambooRegistrySpec bambooArtifactTriggerSpec =
            (io.harness.ngtriggers.beans.source.v1.artifact.BambooRegistrySpec) spec;
        return BambooRegistrySpec.builder()
            .connectorRef(spec.fetchConnectorRef())
            .eventConditions(spec.fetchEventDataConditions())
            .jexlCondition(spec.fetchJexlArtifactConditions())
            .metaDataConditions(spec.fetchMetaDataConditions())
            .planKey(bambooArtifactTriggerSpec.getPlan_key())
            .artifactPaths(bambooArtifactTriggerSpec.getPaths())
            .build(bambooArtifactTriggerSpec.getBuild())
            .build();
      case ECR:
        io.harness.ngtriggers.beans.source.v1.artifact.EcrSpec ecrArtifactTriggerSpec =
            (io.harness.ngtriggers.beans.source.v1.artifact.EcrSpec) spec;
        return EcrSpec.builder()
            .connectorRef(spec.fetchConnectorRef())
            .eventConditions(spec.fetchEventDataConditions())
            .jexlCondition(spec.fetchJexlArtifactConditions())
            .metaDataConditions(spec.fetchMetaDataConditions())
            .imagePath(getImageAndTagFromLocation(ecrArtifactTriggerSpec.getLocation()).getLeft())
            .region(ecrArtifactTriggerSpec.getRegion())
            .tag(getImageAndTagFromLocation(ecrArtifactTriggerSpec.getLocation()).getRight())
            .registryId(ecrArtifactTriggerSpec.getRegistry())
            .build();
      case ACR:
        io.harness.ngtriggers.beans.source.v1.artifact.AcrSpec acrArtifactTriggerSpec =
            (io.harness.ngtriggers.beans.source.v1.artifact.AcrSpec) spec;
        return AcrSpec.builder()
            .connectorRef(spec.fetchConnectorRef())
            .eventConditions(spec.fetchEventDataConditions())
            .jexlCondition(spec.fetchJexlArtifactConditions())
            .metaDataConditions(spec.fetchMetaDataConditions())
            .tag(acrArtifactTriggerSpec.getTag())
            .registry(acrArtifactTriggerSpec.getRegistry())
            .repository(acrArtifactTriggerSpec.getRepo())
            .subscriptionId(acrArtifactTriggerSpec.getSubscription())
            .build();
      default:
        throw new InvalidRequestException("Artifact Trigger Type " + artifactType + " is invalid");
    }
  }

  List<NGVariable> toNGVariableList(NGVariableV1Wrapper ngVariableV1Wrapper) {
    List<NGVariable> variablesList = new ArrayList<>();
    if (ngVariableV1Wrapper == null || isEmpty(ngVariableV1Wrapper.getMap())) {
      return variablesList;
    }
    Map<String, NGVariableV1> variables = ngVariableV1Wrapper.getMap();
    for (Map.Entry<String, NGVariableV1> entry : variables.entrySet()) {
      switch (entry.getValue().getType()) {
        case STRING:
          StringNGVariableV1 stringNGVariableV1 = (StringNGVariableV1) entry.getValue();
          variablesList.add(StringNGVariable.builder()
                                .defaultValue(stringNGVariableV1.getDefaultValue())
                                .value(stringNGVariableV1.getValue())
                                .name(entry.getKey())
                                .required(stringNGVariableV1.isRequired())
                                .description(stringNGVariableV1.getDescription())
                                .build());
          break;
        case NUMBER:
          NumberNGVariableV1 numberNGVariableV1 = (NumberNGVariableV1) entry.getValue();
          variablesList.add(NumberNGVariable.builder()
                                .defaultValue(numberNGVariableV1.getDefaultValue())
                                .value(numberNGVariableV1.getValue())
                                .name(entry.getKey())
                                .required(numberNGVariableV1.isRequired())
                                .description(numberNGVariableV1.getDescription())
                                .build());
          break;
        case SECRET:
          SecretNGVariableV1 secretNGVariableV1 = (SecretNGVariableV1) entry.getValue();
          variablesList.add(SecretNGVariable.builder()
                                .defaultValue(secretNGVariableV1.getDefaultValue())
                                .value(secretNGVariableV1.getValue())
                                .name(entry.getKey())
                                .required(secretNGVariableV1.isRequired())
                                .description(secretNGVariableV1.getDescription())
                                .build());
          break;
        case BOOLEAN:
          BooleanNGVariableV1 booleanNGVariableV1 = (BooleanNGVariableV1) entry.getValue();
          ParameterField<String> boolStringValue = ParameterField.isNull(booleanNGVariableV1.getValue())
              ? ParameterField.ofNull()
              : booleanNGVariableV1.getValue().isExpression()
              ? ParameterField.createExpressionField(
                    true, booleanNGVariableV1.getValue().getExpressionValue(), null, true)
              : ParameterField.createValueField(String.valueOf(booleanNGVariableV1.getValue().getValue()));
          variablesList.add(StringNGVariable.builder()
                                .defaultValue(booleanNGVariableV1.getDefaultValue() != null
                                        ? String.valueOf(booleanNGVariableV1.getDefaultValue())
                                        : null)
                                .value(boolStringValue)
                                .name(entry.getKey())
                                .required(booleanNGVariableV1.isRequired())
                                .description(booleanNGVariableV1.getDescription())
                                .build());
          break;
        default:
          throw new InvalidRequestException("Variable type " + entry.getValue().getType() + "is invalid");
      }
    }
    return variablesList;
  }
}
