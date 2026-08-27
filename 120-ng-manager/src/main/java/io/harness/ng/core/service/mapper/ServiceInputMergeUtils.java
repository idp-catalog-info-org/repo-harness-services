/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.mapper;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.beans.ScopeInfo;
import io.harness.exception.InvalidYamlException;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.pms.merger.helpers.MergeHelper;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlUtils;
import io.harness.serializer.JsonUtils;
import io.harness.unified.service.UnifiedServiceConverterRequestDTO;
import io.harness.utils.YamlPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;

/**
 * Utility class for merging service inputs with service YAML.

 */
@UtilityClass
public class ServiceInputMergeUtils {
  /**
   * Merges service inputs YAML with the service entity's YAML.
   * This is a neutral utility function used by both POJO and template-based flows.
   *
   * @param requestDTO the unified service converter request containing service inputs
   * @param serviceEntity the service entity
   * @param scopeInfo the scope information (nullable)
   * @return merged service YAML string
   */
  public static String getMergedNgServiceYaml(
      UnifiedServiceConverterRequestDTO requestDTO, ServiceEntity serviceEntity, ScopeInfo scopeInfo) {
    String mergedNgServiceYaml = scopeInfo != null ? serviceEntity.getYaml(scopeInfo) : serviceEntity.getYaml();
    if (isNotEmpty(requestDTO.getServiceInputsYaml())) {
      JsonNode serviceInputsJsonNodeWrapper = YamlUtils.readAsJsonNode(requestDTO.getServiceInputsYaml());

      JsonNode serviceInputsJsonNode = serviceInputsJsonNodeWrapper.get("overlay");
      ObjectNode serviceObjectNode = (ObjectNode) serviceInputsJsonNode;

      if (serviceInputsJsonNode != null) {
        if (serviceObjectNode.get(YAMLFieldNameConstants.UUID) != null) {
          serviceObjectNode.remove(YAMLFieldNameConstants.UUID);
        }
        Map<String, Object> serviceJsonInputsMap = JsonUtils.asMap(serviceInputsJsonNode.toString());
        Map<String, Object> mergeableServiceJsonInputsMap = new HashMap<>();

        // V1 overlay drops the `serviceDefinition` wrapper (i.e. `spec` sits directly under
        // `overlay`). When `spec` is present, re-insert the wrapper here so the overlay's FQNs align with the stored
        // V0 service YAML (`service.serviceDefinition.*`) during the merge.
        // Any other shape (explicit `serviceDefinition` wrapper or unsupported fields) is deemed incorrect and would
        // fail later in the service step, so fail fast here instead of silently passing it through.
        if (serviceJsonInputsMap.containsKey(YAMLFieldNameConstants.SPEC)) {
          Map<String, Object> serviceDefinitionJsonInputsMap = new HashMap<>();
          serviceDefinitionJsonInputsMap.put(YAMLFieldNameConstants.SERVICE_DEFINITION, serviceJsonInputsMap);
          mergeableServiceJsonInputsMap.put(YAMLFieldNameConstants.SERVICE, serviceDefinitionJsonInputsMap);
        } else if (serviceJsonInputsMap.containsKey(YAMLFieldNameConstants.SERVICE_DEFINITION)) {
          throw new InvalidYamlException("'service.with.overlay' requires the 'spec' field to provide service inputs. "
              + "Remove the 'serviceDefinition' wrapper and put inputs directly under 'spec'.");
        } else {
          throw new InvalidYamlException("'service.with.overlay' requires the 'spec' field to provide service inputs.");
        }

        mergedNgServiceYaml = MergeHelper.mergeRuntimeInputValuesAndCheckForRuntimeInOriginalYaml(
            scopeInfo != null ? serviceEntity.getYaml(scopeInfo) : serviceEntity.getYaml(),
            YamlPipelineUtils.writeYamlString(mergeableServiceJsonInputsMap), true, true, false);
      }
    }
    return mergedNgServiceYaml;
  }
}
