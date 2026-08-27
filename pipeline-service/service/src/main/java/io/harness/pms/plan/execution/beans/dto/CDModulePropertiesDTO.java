/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.beans.dto;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidRequestException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModel;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_PIPELINE})
@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@ApiModel("CDModuleProperties")
@FieldNameConstants(innerTypeName = "CDModulePropertiesDTOKeys")
public class CDModulePropertiesDTO {
  Object artifactDisplayNames;
  Object envIdentifiers;
  Object serviceIdentifiers;
  Object serviceDefinitionTypes;
  Object helmChartVersions;
  Object gitOpsAppIdentifiers;

  public List<String> getArtifactDisplayNames() {
    return processField(this.artifactDisplayNames, CDModulePropertiesDTOKeys.artifactDisplayNames);
  }

  public List<String> getEnvIdentifiers() {
    return processField(this.envIdentifiers, CDModulePropertiesDTOKeys.envIdentifiers);
  }

  public List<String> getHelmChartVersions() {
    return processField(this.helmChartVersions, CDModulePropertiesDTOKeys.helmChartVersions);
  }

  public List<String> getServiceDefinitionTypes() {
    return processField(this.serviceDefinitionTypes, CDModulePropertiesDTOKeys.serviceDefinitionTypes);
  }

  public List<String> getServiceIdentifiers() {
    return processField(this.serviceIdentifiers, CDModulePropertiesDTOKeys.serviceIdentifiers);
  }

  public List<String> getGitOpsAppIdentifiers() {
    return processField(this.gitOpsAppIdentifiers, CDModulePropertiesDTOKeys.gitOpsAppIdentifiers);
  }

  private List<String> processField(Object value, String fieldName) {
    if (value == null) {
      return null;
    }
    if (value instanceof List<?> valueList) {
      boolean isListOfStrings = valueList.stream().allMatch(e -> e == null || e instanceof String);
      if (isListOfStrings) {
        return (List<String>) valueList;
      } else {
        throw new InvalidRequestException(String.format("Please verify the value for the filter key: moduleInfo.cd.%s, "
                + "only String/List<String> type of filter is supported",
            fieldName));
      }
    } else if (value instanceof String) {
      return List.of((String) value);
    } else {
      throw new InvalidRequestException(String.format("Please verify the value for the filter key: moduleInfo.cd.%s, "
              + "only String/List<String> type of filter is supported",
          fieldName));
    }
  }
}
