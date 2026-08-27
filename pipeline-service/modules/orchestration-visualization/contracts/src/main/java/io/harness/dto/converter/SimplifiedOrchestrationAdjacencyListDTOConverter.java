/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.dto.converter;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.OrchestrationAdjacencyListInternal;
import io.harness.beans.converter.EdgeListConverter;
import io.harness.dto.SimplifiedOrchestrationAdjacencyListDTO;

import java.util.Map;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.IDP)
@UtilityClass
public class SimplifiedOrchestrationAdjacencyListDTOConverter {
  public SimplifiedOrchestrationAdjacencyListDTO convertFrom(OrchestrationAdjacencyListInternal adjacencyListInternal) {
    return SimplifiedOrchestrationAdjacencyListDTO.builder()
        .graphVertexMap(adjacencyListInternal.getGraphVertexMap().entrySet().stream().collect(Collectors.toMap(
            Map.Entry::getKey, m -> GraphVertexDTOConverter.toSimplifiedGraphVertexDTO.apply(m.getValue()))))
        .adjacencyMap(adjacencyListInternal.getAdjacencyMap().entrySet().stream().collect(Collectors.toMap(
            Map.Entry::getKey, edgeListInternal -> EdgeListConverter.convertFrom(edgeListInternal.getValue()))))
        .build();
  }
}
