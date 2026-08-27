/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.opa.entities.variable;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.governance.GovernanceMetadata;
import io.harness.ng.core.variable.dto.VariableDTO;
import io.harness.opa.OpaEvaluationContext;
import io.harness.opa.OpaService;
import io.harness.opaclient.OpaUtils;
import io.harness.opaclient.model.OpaConstants;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@Singleton
public class VariableOpaServiceImpl implements VariableOpaService {
  private OpaService opaService;
  @Override
  public GovernanceMetadata evaluatePoliciesWithEntity(
      ScopeInfo scopeInfo, VariableDTO variableDTO, String action, String identifier) {
    OpaEvaluationContext context;
    try {
      String expandedYaml = getVariableYaml(variableDTO);
      context = createEvaluationContext(expandedYaml, OpaConstants.OPA_EVALUATION_TYPE_VARIABLE);
      return opaService.evaluate(context, scopeInfo.getAccountIdentifier(), scopeInfo.getOrgIdentifier(),
          scopeInfo.getProjectIdentifier(), identifier, action, OpaConstants.OPA_EVALUATION_TYPE_VARIABLE);
    } catch (IOException ex) {
      return GovernanceMetadata.newBuilder()
          .setDeny(true)
          .setMessage(String.format("Could not create OPA context for Variable : [%s]", ex.getMessage()))
          .build();
    }
  }

  private String getVariableYaml(VariableDTO variableDTO) {
    String variableYaml = null;
    ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory()
                                                     .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
                                                     .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                                                     .disable(YAMLGenerator.Feature.USE_NATIVE_TYPE_ID));
    objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    try {
      variableYaml = objectMapper.writeValueAsString(variableDTO);
    } catch (Exception ex) {
      log.error("Failed while converting to variable yaml format", ex);
    }
    return variableYaml;
  }

  private VariableOpaEvaluationContext createEvaluationContext(String yaml, String key) throws IOException {
    return VariableOpaEvaluationContext.builder().variable(OpaUtils.extractObjectFromYamlString(yaml, key)).build();
  }
}
