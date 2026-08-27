/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.opa.entities.apiKey;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.governance.GovernanceMetadata;
import io.harness.ng.core.dto.ApiKeyDTO;
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
public class ApiKeyOpaServiceImpl implements ApiKeyOpaService {
  private OpaService opaService;
  @Override
  public GovernanceMetadata evaluatePoliciesWithEntity(
      ScopeInfo scopeInfo, ApiKeyDTO apiKeyDTO, String action, String identifier) {
    OpaEvaluationContext context;
    try {
      String expandedYaml = getApiKeyYaml(apiKeyDTO);
      context = createEvaluationContext(expandedYaml, OpaConstants.OPA_EVALUATION_TYPE_API_KEY);
      return opaService.evaluate(context, scopeInfo.getAccountIdentifier(), scopeInfo.getOrgIdentifier(),
          scopeInfo.getProjectIdentifier(), identifier, action, OpaConstants.OPA_EVALUATION_TYPE_API_KEY);
    } catch (IOException ex) {
      return GovernanceMetadata.newBuilder()
          .setDeny(true)
          .setMessage(String.format("Could not create OPA context for ApiKey : [%s]", ex.getMessage()))
          .build();
    }
  }

  private String getApiKeyYaml(ApiKeyDTO apiKeyDTO) {
    String apiTokenYaml = null;
    ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory()
                                                     .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
                                                     .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                                                     .disable(YAMLGenerator.Feature.USE_NATIVE_TYPE_ID));
    objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    try {
      apiTokenYaml = objectMapper.writeValueAsString(apiKeyDTO);
    } catch (Exception ex) {
      log.error("Failed while converting to connector yaml format", ex);
    }
    return apiTokenYaml;
  }

  private ApiKeyOpaEvaluationContext createEvaluationContext(String yaml, String key) throws IOException {
    return ApiKeyOpaEvaluationContext.builder().apiKey(OpaUtils.extractObjectFromYamlString(yaml, key)).build();
  }
}
