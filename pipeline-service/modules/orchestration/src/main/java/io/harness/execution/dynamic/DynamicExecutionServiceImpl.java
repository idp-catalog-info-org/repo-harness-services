/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.execution.dynamic;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.EntityNotFoundException;
import io.harness.execution.DynamicExecutionInstance;
import io.harness.execution.dynamic.dtos.DynamicExecutionInstanceRequestDTO;
import io.harness.execution.dynamic.dtos.DynamicExecutionInstanceResponseDTO;
import io.harness.repositories.dynamic.DynamicExecutionInstanceRepository;

import com.google.inject.Inject;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
@OwnedBy(PIPELINE)
public class DynamicExecutionServiceImpl implements DynamicExecutionService {
  @Inject DynamicExecutionInstanceRepository dynamicExecutionInstanceRepository;
  @Override
  public void create(DynamicExecutionInstanceRequestDTO instanceDTO) {
    dynamicExecutionInstanceRepository.save(DynamicExecutionInstance.builder()
                                                .planExecutionId(instanceDTO.getPlanExecutionId())
                                                .nodeExecutionId(instanceDTO.getNodeExecutionId())
                                                .yaml(instanceDTO.getYaml())
                                                .processedYaml(instanceDTO.getProcessedYaml())
                                                .identifier(instanceDTO.getIdentifier())
                                                .build());
  }

  @Override
  public DynamicExecutionInstanceResponseDTO getByNodeExecutionId(String nodeExecutionId) {
    Optional<DynamicExecutionInstance> optional =
        dynamicExecutionInstanceRepository.findByNodeExecutionId(nodeExecutionId);
    if (optional.isEmpty()) {
      throw new EntityNotFoundException(
          String.format("DynamicExecution Instance could not be found for the nodeExecutionId %s", nodeExecutionId));
    }
    DynamicExecutionInstance dynamicExecutionInstance = optional.get();
    return DynamicExecutionInstanceResponseDTO.builder()
        .planExecutionId(dynamicExecutionInstance.getPlanExecutionId())
        .nodeExecutionId(dynamicExecutionInstance.getNodeExecutionId())
        .yaml(dynamicExecutionInstance.getYaml())
        .build();
  }

  @Override
  public Optional<DynamicExecutionInstanceResponseDTO> getByPlanExecutionIdAndIdentifier(
      String planExecutionId, String identifier) {
    Optional<DynamicExecutionInstance> optional =
        dynamicExecutionInstanceRepository.findByPlanExecutionIdAndIdentifier(planExecutionId, identifier);
    if (optional.isEmpty()) {
      return Optional.empty();
    }
    DynamicExecutionInstance dynamicExecutionInstance = optional.get();
    return Optional.of(DynamicExecutionInstanceResponseDTO.builder()
                           .planExecutionId(dynamicExecutionInstance.getPlanExecutionId())
                           .nodeExecutionId(dynamicExecutionInstance.getNodeExecutionId())
                           .yaml(dynamicExecutionInstance.getYaml())
                           .processedYaml(dynamicExecutionInstance.getProcessedYaml())
                           .build());
  }
}
