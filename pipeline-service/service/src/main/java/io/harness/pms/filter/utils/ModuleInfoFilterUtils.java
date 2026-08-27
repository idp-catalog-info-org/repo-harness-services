/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.filter.utils;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.pms.plan.execution.beans.dto.CDModulePropertiesDTO;
import io.harness.pms.plan.execution.beans.dto.CDModulePropertiesDTO.CDModulePropertiesDTOKeys;
import io.harness.pms.plan.execution.beans.dto.CIExecutionInfoDTO;
import io.harness.pms.plan.execution.beans.dto.CIModulePropertiesDTO;
import io.harness.pms.plan.execution.beans.dto.CIModulePropertiesDTO.CIModulePropertiesDTOKeys;
import io.harness.pms.plan.execution.beans.dto.CIPullRequestDTO;
import io.harness.pms.plan.execution.beans.dto.ModulePropertiesDTO;
import io.harness.pms.plan.execution.beans.dto.ModulePropertiesDTO.ModulePropertiesDTOKeys;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import org.springframework.data.mongodb.core.query.Criteria;

@UtilityClass
public class ModuleInfoFilterUtils {
  public void processModuleProperties(ModulePropertiesDTO modulePropertiesDTO, String parentPath, Criteria criteria) {
    if (modulePropertiesDTO == null) {
      return;
    }
    if (modulePropertiesDTO.getCd() != null) {
      processCDModuleInfoFilter(modulePropertiesDTO.getCd(), parentPath, criteria);
    }
    if (modulePropertiesDTO.getCi() != null) {
      processCIModuleInfoFilter(modulePropertiesDTO.getCi(), parentPath, criteria);
    }
  }

  // This function is created to apply OR conditions on different modules in filterProperties.
  public void processModulePropertiesOROperator(
      ModulePropertiesDTO modulePropertiesDTO, String parentPath, List<Criteria> criteriaList) {
    if (modulePropertiesDTO == null) {
      return;
    }
    if (modulePropertiesDTO.getCd() != null) {
      Criteria criteriaModules = new Criteria();
      processCDModuleInfoFilter(modulePropertiesDTO.getCd(), parentPath, criteriaModules);
      if (checkIfCriteriaIsPopulated(criteriaModules)) {
        criteriaList.add(criteriaModules);
      }
    }
    if (modulePropertiesDTO.getCi() != null) {
      Criteria criteriaModules = new Criteria();
      processCIModuleInfoFilter(modulePropertiesDTO.getCi(), parentPath, criteriaModules);
      if (checkIfCriteriaIsPopulated(criteriaModules)) {
        criteriaList.add(criteriaModules);
      }
    }
  }

  private boolean checkIfCriteriaIsPopulated(Criteria criteria) {
    return !criteria.equals(new Criteria());
  }

  private void processCDModuleInfoFilter(CDModulePropertiesDTO moduleProperties, String parentPath, Criteria criteria) {
    if (moduleProperties == null) {
      return;
    }
    String modulePath = String.format("%s.%s", parentPath, ModulePropertiesDTOKeys.cd);
    addCriteriaForCDModuleProperties(moduleProperties.getEnvIdentifiers(),
        String.format("%s.%s", modulePath, CDModulePropertiesDTOKeys.envIdentifiers), criteria);
    addCriteriaForCDModuleProperties(moduleProperties.getArtifactDisplayNames(),
        String.format("%s.%s", modulePath, CDModulePropertiesDTOKeys.artifactDisplayNames), criteria);
    addCriteriaForCDModuleProperties(moduleProperties.getServiceIdentifiers(),
        String.format("%s.%s", modulePath, CDModulePropertiesDTOKeys.serviceIdentifiers), criteria);
    addCriteriaForCDModuleProperties(moduleProperties.getServiceDefinitionTypes(),
        String.format("%s.%s", modulePath, CDModulePropertiesDTOKeys.serviceDefinitionTypes), criteria);
    addCriteriaForCDModuleProperties(moduleProperties.getHelmChartVersions(),
        String.format("%s.%s", modulePath, CDModulePropertiesDTOKeys.helmChartVersions), criteria);
  }

  private void addCriteriaForCDModuleProperties(List<String> value, String path, Criteria criteria) {
    if (value == null) {
      return;
    }
    criteria.and(path).in(value);
  }

  private void processCIModuleInfoFilter(CIModulePropertiesDTO moduleProperties, String parentPath, Criteria criteria) {
    if (moduleProperties == null) {
      return;
    }
    String modulePath = String.format("%s.%s", parentPath, ModulePropertiesDTOKeys.ci);
    if (moduleProperties.getBranch() != null) {
      criteria.and(String.format("%s.%s", modulePath, CIModulePropertiesDTOKeys.branch))
          .is(moduleProperties.getBranch());
    }
    if (moduleProperties.getBuildType() != null) {
      criteria.and(String.format("%s.%s", modulePath, CIModulePropertiesDTOKeys.buildType))
          .is(moduleProperties.getBuildType());
    }
    if (moduleProperties.getTag() != null) {
      criteria.and(String.format("%s.%s", modulePath, CIModulePropertiesDTOKeys.tag)).is(moduleProperties.getTag());
    }
    if (moduleProperties.getRepoName() != null) {
      criteria.and(String.format("%s.%s", modulePath, CIModulePropertiesDTOKeys.repoName))
          .is(moduleProperties.getRepoName());
    }
    if (moduleProperties.getCiExecutionInfoDTO() != null) {
      CIExecutionInfoDTO ciExecutionInfoDTO = moduleProperties.getCiExecutionInfoDTO();
      if (ciExecutionInfoDTO.getEvent() != null) {
        criteria.and(String.format("%s.%s", modulePath, CIModulePropertiesDTOKeys.event))
            .is(ciExecutionInfoDTO.getEvent());
      }
      if (ciExecutionInfoDTO.getPullRequest() != null) {
        CIPullRequestDTO ciPullRequestDTO = ciExecutionInfoDTO.getPullRequest();
        if (ciPullRequestDTO.getSourceBranch() != null) {
          criteria.and(String.format("%s.%s", modulePath, CIModulePropertiesDTOKeys.sourceBranch))
              .is(ciPullRequestDTO.getSourceBranch());
        }
        if (ciPullRequestDTO.getTargetBranch() != null) {
          criteria.and(String.format("%s.%s", modulePath, CIModulePropertiesDTOKeys.targetBranch))
              .is(ciPullRequestDTO.getTargetBranch());
        }
      }
    }
  }

  public void processNode(JsonNode jsonNode, String parentPath, Criteria criteria) {
    if (jsonNode.isValueNode()) {
      if (jsonNode.isInt()) {
        criteria.and(parentPath).is(jsonNode.asInt());
      } else {
        criteria.and(parentPath).is(jsonNode.asText());
      }
    } else if (jsonNode.isArray()) {
      List<String> valueList = new ArrayList<>();
      for (JsonNode arrayItem : jsonNode) {
        valueList.add(arrayItem.textValue());
      }
      if (isNotEmpty(valueList)) {
        criteria.and(parentPath).in(valueList);
      }
    } else if (jsonNode.isObject()) {
      Iterator<Map.Entry<String, JsonNode>> fields = jsonNode.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> jsonField = fields.next();
        processNode(jsonField.getValue(), String.join(".", parentPath, jsonField.getKey()), criteria);
      }
    }
  }
}
