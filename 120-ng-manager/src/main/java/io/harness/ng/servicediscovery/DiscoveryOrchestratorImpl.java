/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.servicediscovery;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.infrastructure.dto.DiscoveryStatusDTO;
import io.harness.ng.core.infrastructure.dto.ServiceDiscoveryEnableResponseDTO;
import io.harness.ng.core.infrastructure.dto.ServiceDiscoveryEnableResponseDTO.ItemResult;
import io.harness.ng.core.infrastructure.dto.ServiceDiscoveryEnableResponseDTO.Summary;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity.InfrastructureEntityKeys;
import io.harness.ng.core.infrastructure.services.DiscoveryOrchestrator;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.persistence.HPersistence;
import io.harness.servicediscovery.client.beans.ServiceDiscoveryAgentRequest;
import io.harness.servicediscovery.client.beans.ServiceDiscoveryAgentResponse;
import io.harness.servicediscovery.client.beans.ServiceDiscoveryResponseDTO;
import io.harness.servicediscovery.client.remote.ServiceDiscoveryHttpClient;

import com.google.common.hash.Hashing;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Response;

@OwnedBy(HarnessTeam.CDP)
@Singleton
@Slf4j
public class DiscoveryOrchestratorImpl implements DiscoveryOrchestrator {
  private static final String AGENT_PREFIX = "harness-discovery-";

  @Inject private ServiceDiscoveryHttpClient serviceDiscoveryHttpClient;
  @Inject private InfrastructureEntityService infrastructureEntityService;
  @Inject private HPersistence hPersistence;

  @Override
  public DiscoveryStatusDTO enable(InfrastructureEntity infraEntity) {
    if (!Boolean.TRUE.equals(infraEntity.getServiceDiscoveryEnabled())) {
      return null;
    }
    String connectorRef = extractConnectorRef(infraEntity);
    String agentIdentity = buildAgentIdentity(connectorRef);

    ServiceDiscoveryAgentResponse existingAgent = lookupAgent(infraEntity, agentIdentity);
    if (existingAgent != null) {
      log.info("Agent [{}] already exists for connector [{}], skipping creation", agentIdentity, connectorRef);
      return toDiscoveryStatus(existingAgent);
    }

    return createDedicatedInfraAndAgent(infraEntity, agentIdentity, connectorRef);
  }

  @Override
  public void disable(InfrastructureEntity infraEntity) {
    String connectorRef = extractConnectorRef(infraEntity);
    String agentIdentity = buildAgentIdentity(connectorRef);

    if (hasSiblingsWithSameAgent(infraEntity, agentIdentity)) {
      log.info("Agent [{}] still has other infra defs sharing the same connector, skipping teardown", agentIdentity);
      return;
    }

    ServiceDiscoveryAgentResponse agent = lookupAgent(infraEntity, agentIdentity);
    if (agent == null) {
      log.info("No agent found for [{}], treating as already cleaned up", agentIdentity);
      return;
    }
    deleteAgent(infraEntity, agentIdentity);
    deleteDedicatedInfra(infraEntity, agentIdentity);
  }

  @Override
  public DiscoveryStatusDTO reconcile(InfrastructureEntity oldInfra, InfrastructureEntity newInfra) {
    boolean wasEnabled = Boolean.TRUE.equals(oldInfra.getServiceDiscoveryEnabled());
    boolean isEnabled = Boolean.TRUE.equals(newInfra.getServiceDiscoveryEnabled());

    if (!wasEnabled && isEnabled) {
      return enable(newInfra);
    } else if (wasEnabled && !isEnabled) {
      disable(oldInfra);
      return null;
    } else if (wasEnabled && isEnabled) {
      String oldConnector = extractConnectorRef(oldInfra);
      String newConnector = extractConnectorRef(newInfra);
      if (!oldConnector.equals(newConnector)) {
        disable(oldInfra);
        return enable(newInfra);
      }
      ServiceDiscoveryAgentResponse agent = lookupAgent(newInfra, buildAgentIdentity(newConnector));
      return agent != null ? toDiscoveryStatus(agent) : null;
    }
    return null;
  }

  @Override
  public ServiceDiscoveryEnableResponseDTO enableAll(String accountId, String orgIdentifier, String projectIdentifier,
      String envIdentifier, List<String> infraIdentifiers) {
    List<InfrastructureEntity> infras = hPersistence.createQuery(InfrastructureEntity.class)
                                            .filter(InfrastructureEntityKeys.accountId, accountId)
                                            .filter(InfrastructureEntityKeys.orgIdentifier, orgIdentifier)
                                            .filter(InfrastructureEntityKeys.projectIdentifier, projectIdentifier)
                                            .filter(InfrastructureEntityKeys.envIdentifier, envIdentifier)
                                            .field(InfrastructureEntityKeys.identifier)
                                            .in(infraIdentifiers)
                                            .asList();

    Set<String> foundIds = infras.stream().map(InfrastructureEntity::getIdentifier).collect(Collectors.toSet());

    List<ItemResult> results = new ArrayList<>();

    for (String id : infraIdentifiers) {
      if (!foundIds.contains(id)) {
        results.add(
            ItemResult.builder().infraIdentifier(id).status("FAILED").error("Infrastructure not found").build());
      }
    }

    for (InfrastructureEntity infra : infras) {
      try {
        if (!Boolean.TRUE.equals(infra.getServiceDiscoveryEnabled())) {
          hPersistence.update(hPersistence.createQuery(InfrastructureEntity.class)
                                  .filter(InfrastructureEntityKeys.accountId, accountId)
                                  .filter(InfrastructureEntityKeys.orgIdentifier, orgIdentifier)
                                  .filter(InfrastructureEntityKeys.projectIdentifier, projectIdentifier)
                                  .filter(InfrastructureEntityKeys.envIdentifier, envIdentifier)
                                  .filter(InfrastructureEntityKeys.identifier, infra.getIdentifier()),
              hPersistence.createUpdateOperations(InfrastructureEntity.class)
                  .set("serviceDiscoveryEnabled", Boolean.TRUE));
          infra.setServiceDiscoveryEnabled(Boolean.TRUE);
        }
        DiscoveryStatusDTO status = enable(infra);
        boolean agentFailed = status != null && "FAILED".equals(status.getState());
        results.add(ItemResult.builder()
                        .infraIdentifier(infra.getIdentifier())
                        .status(agentFailed ? "FAILED" : "SUCCESS")
                        .agent(status)
                        .error(agentFailed ? "Agent creation failed" : null)
                        .build());
      } catch (Exception e) {
        log.error("Failed to enable service discovery for infra [{}]", infra.getIdentifier(), e);
        results.add(
            ItemResult.builder().infraIdentifier(infra.getIdentifier()).status("FAILED").error(e.getMessage()).build());
      }
    }

    long succeeded = results.stream().filter(r -> "SUCCESS".equals(r.getStatus())).count();
    log.info(
        "Enabled service discovery for {}/{} infras in env [{}]", succeeded, infraIdentifiers.size(), envIdentifier);
    return ServiceDiscoveryEnableResponseDTO.builder()
        .results(results)
        .summary(Summary.builder()
                     .requested(infraIdentifiers.size())
                     .succeeded((int) succeeded)
                     .failed((int) (results.size() - succeeded))
                     .build())
        .build();
  }

  private DiscoveryStatusDTO createDedicatedInfraAndAgent(
      InfrastructureEntity infraEntity, String agentIdentity, String connectorRef) {
    String namespace = extractNamespace(infraEntity);

    // Step 1: Create a dedicated infra def for the agent
    String dedicatedInfraYaml = buildDedicatedInfraYaml(infraEntity, agentIdentity, connectorRef, namespace);
    InfrastructureEntity dedicatedInfra = InfrastructureEntity.builder()
                                              .accountId(infraEntity.getAccountId())
                                              .orgIdentifier(infraEntity.getOrgIdentifier())
                                              .projectIdentifier(infraEntity.getProjectIdentifier())
                                              .envIdentifier(infraEntity.getEnvIdentifier())
                                              .identifier(agentIdentity)
                                              .name(agentIdentity)
                                              .type(infraEntity.getType())
                                              .deploymentType(infraEntity.getDeploymentType())
                                              .yaml(dedicatedInfraYaml)
                                              .build();
    try {
      infrastructureEntityService.create(dedicatedInfra);
      log.info("Created dedicated infra [{}] for SD agent", agentIdentity);
    } catch (io.harness.exception.DuplicateFieldException e) {
      log.info("Dedicated infra [{}] already exists, proceeding to agent creation", agentIdentity);
    } catch (Exception e) {
      log.error("Failed to create dedicated infra [{}]", agentIdentity, e);
      return DiscoveryStatusDTO.builder().agentId(agentIdentity).state("FAILED").build();
    }

    // Step 2: Create the SD agent pointing to the dedicated infra
    ServiceDiscoveryAgentRequest request =
        ServiceDiscoveryAgentRequest.builder()
            .name(agentIdentity)
            .infraIdentifier(agentIdentity)
            .environmentIdentifier(infraEntity.getEnvIdentifier())
            .config(
                ServiceDiscoveryAgentRequest.AgentConfiguration.builder()
                    .kubernetes(ServiceDiscoveryAgentRequest.KubernetesConfig.builder().namespace(namespace).build())
                    .data(
                        ServiceDiscoveryAgentRequest.DataConfig.builder()
                            .cron(ServiceDiscoveryAgentRequest.CronConfig.builder().expression("*/15 * * * *").build())
                            .build())
                    .build())
            .build();
    try {
      Response<ServiceDiscoveryAgentResponse> response =
          serviceDiscoveryHttpClient
              .createAgent(infraEntity.getAccountId(), infraEntity.getOrgIdentifier(),
                  infraEntity.getProjectIdentifier(), true, request)
              .execute();
      if (response.isSuccessful() && response.body() != null) {
        log.info("Created SD agent [{}] for connector [{}]", agentIdentity, connectorRef);
        return toDiscoveryStatus(response.body());
      } else if (response.isSuccessful()) {
        log.info("Created SD agent [{}] for connector [{}]", agentIdentity, connectorRef);
        return DiscoveryStatusDTO.builder().agentId(agentIdentity).state("PROCESSING").build();
      } else {
        String errorBody = response.errorBody() != null ? response.errorBody().string() : "unknown";
        log.error("Failed to create SD agent [{}]: HTTP {} - {}", agentIdentity, response.code(), errorBody);
        return DiscoveryStatusDTO.builder().agentId(agentIdentity).state("FAILED").build();
      }
    } catch (IOException e) {
      log.error("Failed to create SD agent [{}] for connector [{}]", agentIdentity, connectorRef, e);
      return DiscoveryStatusDTO.builder().agentId(agentIdentity).state("FAILED").build();
    }
  }

  private void deleteDedicatedInfra(InfrastructureEntity infraEntity, String agentIdentity) {
    try {
      infrastructureEntityService.delete(infraEntity.getAccountId(), infraEntity.getOrgIdentifier(),
          infraEntity.getProjectIdentifier(), null, infraEntity.getEnvIdentifier(), agentIdentity, true);
      log.info("Deleted dedicated infra [{}]", agentIdentity);
    } catch (Exception e) {
      log.warn("Failed to delete dedicated infra [{}], may not exist", agentIdentity, e);
    }
  }

  private ServiceDiscoveryAgentResponse lookupAgent(InfrastructureEntity infraEntity, String agentIdentity) {
    try {
      Response<ServiceDiscoveryResponseDTO<ServiceDiscoveryAgentResponse>> response =
          serviceDiscoveryHttpClient
              .listAgents(infraEntity.getAccountId(), infraEntity.getOrgIdentifier(),
                  infraEntity.getProjectIdentifier(), agentIdentity)
              .execute();
      if (response.isSuccessful() && response.body() != null) {
        List<ServiceDiscoveryAgentResponse> items = response.body().getItems();
        if (items != null && !items.isEmpty()) {
          return items.get(0);
        }
      }
    } catch (IOException e) {
      log.error("Failed to lookup agent [{}] from Service Discovery", agentIdentity, e);
    }
    return null;
  }

  private void deleteAgent(InfrastructureEntity infraEntity, String agentId) {
    try {
      Response<Void> response = (Response<Void>) (Response<?>) serviceDiscoveryHttpClient
                                    .deleteAgent(agentId, infraEntity.getAccountId(), infraEntity.getOrgIdentifier(),
                                        infraEntity.getProjectIdentifier(), infraEntity.getEnvIdentifier())
                                    .execute();
      if (response.isSuccessful() || response.code() == 404) {
        log.info("Deleted SD agent [{}]", agentId);
      } else {
        log.error("Failed to delete SD agent [{}]: HTTP {}", agentId, response.code());
      }
    } catch (IOException e) {
      log.error("Failed to delete SD agent [{}]", agentId, e);
    }
  }

  private String buildDedicatedInfraYaml(
      InfrastructureEntity infraEntity, String agentIdentity, String connectorRef, String namespace) {
    return "infrastructureDefinition:\n"
        + "  name: " + agentIdentity + "\n"
        + "  identifier: " + agentIdentity + "\n"
        + "  orgIdentifier: " + infraEntity.getOrgIdentifier() + "\n"
        + "  projectIdentifier: " + infraEntity.getProjectIdentifier() + "\n"
        + "  environmentRef: " + infraEntity.getEnvIdentifier() + "\n"
        + "  deploymentType: Kubernetes\n"
        + "  type: KubernetesDirect\n"
        + "  spec:\n"
        + "    connectorRef: " + connectorRef + "\n"
        + "    namespace: " + namespace + "\n"
        + "    releaseName: release-<+INFRA_KEY_SHORT_ID>\n"
        + "  allowSimultaneousDeployments: false\n";
  }

  private String extractConnectorRef(InfrastructureEntity infraEntity) {
    if (infraEntity.getYaml() == null) {
      throw new io.harness.exception.InvalidRequestException(
          "Infrastructure YAML is required for service discovery operations");
    }
    io.harness.cdng.infra.definition.config.InfrastructureConfig infraConfig =
        io.harness.cdng.infra.mapper.InfrastructureEntityConfigMapper.toInfrastructureConfig(infraEntity.getYaml());
    io.harness.cdng.infra.yaml.Infrastructure spec = infraConfig.getInfrastructureDefinitionConfig().getSpec();
    if (spec == null || spec.getConnectorReference() == null || spec.getConnectorReference().getValue() == null) {
      throw new io.harness.exception.InvalidRequestException(
          "A resolved connectorRef is required for service discovery — runtime inputs are not supported");
    }
    return spec.getConnectorReference().getValue();
  }

  private String extractNamespace(InfrastructureEntity infraEntity) {
    if (infraEntity.getYaml() == null) {
      return "default";
    }
    try {
      io.harness.cdng.infra.definition.config.InfrastructureConfig infraConfig =
          io.harness.cdng.infra.mapper.InfrastructureEntityConfigMapper.toInfrastructureConfig(infraEntity.getYaml());
      io.harness.cdng.infra.yaml.Infrastructure spec = infraConfig.getInfrastructureDefinitionConfig().getSpec();
      if (spec instanceof io.harness.cdng.infra.yaml.K8SDirectInfrastructure) {
        io.harness.cdng.infra.yaml.K8SDirectInfrastructure k8sSpec =
            (io.harness.cdng.infra.yaml.K8SDirectInfrastructure) spec;
        String ns = k8sSpec.getNamespace() != null ? k8sSpec.getNamespace().getValue() : null;
        return ns != null ? ns : "default";
      }
    } catch (Exception e) {
      log.warn("Could not extract namespace from infra YAML, using default", e);
    }
    return "default";
  }

  private boolean hasSiblingsWithSameAgent(InfrastructureEntity infraEntity, String agentIdentity) {
    List<InfrastructureEntity> sdEnabledInfras =
        hPersistence.createQuery(InfrastructureEntity.class)
            .filter(InfrastructureEntityKeys.accountId, infraEntity.getAccountId())
            .filter(InfrastructureEntityKeys.orgIdentifier, infraEntity.getOrgIdentifier())
            .filter(InfrastructureEntityKeys.projectIdentifier, infraEntity.getProjectIdentifier())
            .filter("serviceDiscoveryEnabled", Boolean.TRUE)
            .field(InfrastructureEntityKeys.identifier)
            .notEqual(infraEntity.getIdentifier())
            .asList();

    return sdEnabledInfras.stream().filter(infra -> !infra.getIdentifier().startsWith(AGENT_PREFIX)).anyMatch(infra -> {
      try {
        String otherConnector = extractConnectorRef(infra);
        return agentIdentity.equals(buildAgentIdentity(otherConnector));
      } catch (Exception e) {
        return false;
      }
    });
  }

  static String buildAgentIdentity(String connectorRef) {
    String hash = Hashing.murmur3_128().hashString(connectorRef, StandardCharsets.UTF_8).toString().substring(0, 12);
    return AGENT_PREFIX + hash;
  }

  private DiscoveryStatusDTO toDiscoveryStatus(ServiceDiscoveryAgentResponse agent) {
    String state;
    if (agent.getStatus() != null && agent.getStatus().equalsIgnoreCase("HEALTHY")) {
      state = "SUCCESS";
    } else if (agent.getLastSyncedAt() != null) {
      state = "SUCCESS";
    } else {
      state = "PROCESSING";
    }
    return DiscoveryStatusDTO.builder()
        .agentId(agent.getIdentity() != null ? agent.getIdentity() : agent.getId())
        .state(state)
        .lastSyncedAt(agent.getLastSyncedAt())
        .build();
  }
}
