/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.onboarding.provisioners.infra;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesAuthDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesAuthType;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterDetailsDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialType;
import io.harness.delegate.beans.connector.k8Connector.KubernetesServiceAccountDTO;
import io.harness.delegate.beans.connector.utils.ConnectorType;
import io.harness.encryption.SecretRefHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.infrastructure.InfrastructureType;
import io.harness.ng.core.onboarding.dto.OnboardingContextDTO;
import io.harness.ng.core.onboarding.mapper.OnboardingContextNormalizer;
import io.harness.ng.core.onboarding.mapper.OnboardingInfraYamlBuilder;
import io.harness.ng.core.onboarding.provisioners.spec.InfraProvisioner;
import io.harness.ng.core.onboarding.support.OnboardingIdentifiers;
import io.harness.ng.core.onboarding.support.OnboardingProvisionContext;
import io.harness.ng.core.onboarding.support.OnboardingSecretCreation;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

/**
 * KubernetesDirect infrastructure. Validates the K8s cluster/manual-config/service-account request fields, provisions
 * the K8s cluster connector (creating its service-account-token credential secret under {@code connectorId}) and emits
 * the {@code infrastructureDefinition:}-rooted YAML by delegating to {@link OnboardingInfraYamlBuilder}. The
 * surrounding environment create/reuse and infrastructure upsert stay in the coordinator.
 */
@OwnedBy(HarnessTeam.CDC)
@Singleton
public class K8sDirectInfraProvisioner implements InfraProvisioner {
  private final OnboardingSecretCreation secretCreation;

  @Inject
  public K8sDirectInfraProvisioner(OnboardingSecretCreation secretCreation) {
    this.secretCreation = secretCreation;
  }

  @Override
  public InfrastructureType type() {
    return InfrastructureType.KUBERNETES_DIRECT;
  }

  @Override
  public void validate(OnboardingContextDTO context) {
    OnboardingContextNormalizer.validateInfraType(context.getInfraType());
    OnboardingContextNormalizer.validateInfraConnectorType(context.getInfraConnectorType());
    OnboardingContextNormalizer.validateInfraCredentialType(context.getInfraCredentialType());
    OnboardingContextNormalizer.validateInfraAuthType(context.getInfraAuthType());
    if (StringUtils.isBlank(context.getInfraClusterUrl())) {
      throw new InvalidRequestException("infra_clusterUrl is required for a KubernetesDirect infrastructure");
    }
    if (StringUtils.isBlank(context.getInfraNamespace())) {
      throw new InvalidRequestException("infra_namespace is required for a KubernetesDirect infrastructure");
    }
    if (StringUtils.isBlank(context.getInfraServiceAccountToken())) {
      throw new InvalidRequestException(
          "infra_serviceAccountToken is required when the cluster auth type is 'ServiceAccount'");
    }
  }

  @Override
  public String connectorIdentifier(String infraId) {
    return OnboardingIdentifiers.k8sConnectorIdentifier(infraId);
  }

  @Override
  public ConnectorInfoDTO buildConnector(OnboardingProvisionContext provisionContext, String connectorId) {
    OnboardingContextDTO context = provisionContext.getRequest();
    String secretRef = secretCreation.upsertSecret(provisionContext.getScopeInfo(), provisionContext.getOrgIdentifier(),
        provisionContext.getProjectIdentifier(), connectorId + "_credential", context.getInfraServiceAccountToken(),
        provisionContext.getCreatedSecrets());
    return buildK8sClusterConnector(
        context, provisionContext.getOrgIdentifier(), provisionContext.getProjectIdentifier(), connectorId, secretRef);
  }

  @Override
  public String buildInfraYaml(OnboardingContextDTO context, String infraId, String infraName, String orgIdentifier,
      String projectIdentifier, String environmentRef, String connectorRef, String releaseName) {
    return OnboardingInfraYamlBuilder.buildInfrastructureYaml(infraId, infraName, orgIdentifier, projectIdentifier,
        environmentRef, connectorRef, context.getInfraNamespace(), releaseName);
  }

  private ConnectorInfoDTO buildK8sClusterConnector(OnboardingContextDTO context, String orgIdentifier,
      String projectIdentifier, String connectorId, String secretRef) {
    KubernetesAuthDTO auth = buildServiceAccountAuth(secretRef);
    KubernetesClusterDetailsDTO clusterDetails =
        KubernetesClusterDetailsDTO.builder().masterUrl(context.getInfraClusterUrl()).auth(auth).build();
    KubernetesCredentialDTO credential = KubernetesCredentialDTO.builder()
                                             .kubernetesCredentialType(KubernetesCredentialType.MANUAL_CREDENTIALS)
                                             .config(clusterDetails)
                                             .build();
    KubernetesClusterConfigDTO k8sConfig = KubernetesClusterConfigDTO.builder().credential(credential).build();

    return ConnectorInfoDTO.builder()
        .identifier(connectorId)
        .name(connectorId)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .connectorType(ConnectorType.KUBERNETES_CLUSTER)
        .connectorConfig(k8sConfig)
        .build();
  }

  private KubernetesAuthDTO buildServiceAccountAuth(String secretRef) {
    KubernetesServiceAccountDTO serviceAccount = KubernetesServiceAccountDTO.builder()
                                                     .serviceAccountTokenRef(SecretRefHelper.createSecretRef(secretRef))
                                                     .build();
    return KubernetesAuthDTO.builder().authType(KubernetesAuthType.SERVICE_ACCOUNT).credentials(serviceAccount).build();
  }
}
