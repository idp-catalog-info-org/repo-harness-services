/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.source.v1.artifact;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.ngtriggers.beans.source.v1.constants.YamlSimplConstants;

import com.fasterxml.jackson.annotation.JsonProperty;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@OwnedBy(PIPELINE)
public enum ArtifactType {
  @JsonProperty(YamlSimplConstants.GCR) GCR(YamlSimplConstants.GCR),
  @JsonProperty(YamlSimplConstants.ECR) ECR(YamlSimplConstants.ECR),
  @JsonProperty(YamlSimplConstants.DOCKER_REGISTRY) DOCKER_REGISTRY(YamlSimplConstants.DOCKER_REGISTRY),
  @JsonProperty(YamlSimplConstants.NEXUS3_REGISTRY) NEXUS3_REGISTRY(YamlSimplConstants.NEXUS3_REGISTRY),
  @JsonProperty(YamlSimplConstants.NEXUS2_REGISTRY) NEXUS2_REGISTRY(YamlSimplConstants.NEXUS2_REGISTRY),
  @JsonProperty(YamlSimplConstants.ARTIFACTORY_REGISTRY) ARTIFACTORY_REGISTRY(YamlSimplConstants.ARTIFACTORY_REGISTRY),
  @JsonProperty(YamlSimplConstants.ACR) ACR(YamlSimplConstants.ACR),
  @JsonProperty(YamlSimplConstants.AMAZON_S3) AMAZON_S3(YamlSimplConstants.AMAZON_S3),
  @JsonProperty(YamlSimplConstants.JENKINS) JENKINS(YamlSimplConstants.JENKINS),
  @JsonProperty(YamlSimplConstants.CUSTOM_ARTIFACT) CUSTOM_ARTIFACT(YamlSimplConstants.CUSTOM_ARTIFACT),
  @JsonProperty(YamlSimplConstants.GOOGLE_ARTIFACT_REGISTRY)
  GoogleArtifactRegistry(YamlSimplConstants.GOOGLE_ARTIFACT_REGISTRY),
  @JsonProperty(YamlSimplConstants.GITHUB_PACKAGES) GITHUB_PACKAGES(YamlSimplConstants.GITHUB_PACKAGES),
  @JsonProperty(YamlSimplConstants.AZURE_ARTIFACTS) AZURE_ARTIFACTS(YamlSimplConstants.AZURE_ARTIFACTS),
  @JsonProperty(YamlSimplConstants.AMI) AMI(YamlSimplConstants.AMI),
  @JsonProperty(YamlSimplConstants.GOOGLE_CLOUD_STORAGE) GOOGLE_CLOUD_STORAGE(YamlSimplConstants.GOOGLE_CLOUD_STORAGE),
  @JsonProperty(YamlSimplConstants.BAMBOO) BAMBOO(YamlSimplConstants.BAMBOO);

  private String value;

  ArtifactType(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }
}
