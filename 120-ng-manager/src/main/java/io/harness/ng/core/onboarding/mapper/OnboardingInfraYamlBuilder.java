/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.onboarding.mapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.infra.definition.config.InfrastructureConfig;
import io.harness.cdng.infra.definition.config.InfrastructureDefinitionConfig;
import io.harness.cdng.infra.yaml.K8SDirectInfrastructure;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.infrastructure.InfrastructureType;
import io.harness.pms.yaml.ParameterField;

import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * Assembles the in-memory {@link InfrastructureConfig} bean graph for a {@code KubernetesDirect}
 * infrastructure and serializes it to the {@code infrastructureDefinition:}-rooted YAML expected by
 * {@code InfrastructureResource}. The infra references the environment and Kubernetes cluster connector
 * provisioned earlier in the same onboarding request.
 */
@OwnedBy(HarnessTeam.CDC)
public final class OnboardingInfraYamlBuilder {
  private OnboardingInfraYamlBuilder() {}

  /**
   * Builds and serializes the {@code KubernetesDirect} infrastructure YAML.
   *
   * @param infraId identifier of the infrastructure (must match the {@code InfrastructureRequestDTO})
   * @param infraName name of the infrastructure
   * @param orgIdentifier org scope
   * @param projectIdentifier project scope
   * @param environmentRef identifier of the environment created/reused for this infra
   * @param connectorRef ref of the K8s cluster connector created for this infra
   * @param namespace Kubernetes namespace to deploy into
   * @param releaseName Harness release name expression
   */
  public static String buildInfrastructureYaml(String infraId, String infraName, String orgIdentifier,
      String projectIdentifier, String environmentRef, String connectorRef, String namespace, String releaseName) {
    K8SDirectInfrastructure spec = K8SDirectInfrastructure.builder()
                                       .connectorRef(ParameterField.createValueField(connectorRef))
                                       .namespace(ParameterField.createValueField(namespace))
                                       .releaseName(ParameterField.createValueField(releaseName))
                                       .build();

    InfrastructureDefinitionConfig definitionConfig =
        InfrastructureDefinitionConfig.builder()
            .identifier(infraId)
            .name(infraName)
            .orgIdentifier(orgIdentifier)
            .projectIdentifier(projectIdentifier)
            .environmentRef(environmentRef)
            .deploymentType(ServiceDefinitionType.KUBERNETES)
            .type(InfrastructureType.KUBERNETES_DIRECT)
            .spec(spec)
            .allowSimultaneousDeployments(ParameterField.createValueField(false))
            .build();

    InfrastructureConfig infrastructureConfig =
        InfrastructureConfig.builder().infrastructureDefinitionConfig(definitionConfig).build();

    try {
      // Prune null-valued keys (e.g. unset provisioner) that the shared mapper would otherwise emit; keeps the
      // persisted infra YAML limited to the fields onboarding actually sets. See OnboardingYamlUtils.
      return OnboardingYamlUtils.toPrunedYaml(infrastructureConfig);
    } catch (JsonProcessingException e) {
      throw new InvalidRequestException("Failed to serialize the onboarding infrastructure definition to YAML", e);
    }
  }
}
