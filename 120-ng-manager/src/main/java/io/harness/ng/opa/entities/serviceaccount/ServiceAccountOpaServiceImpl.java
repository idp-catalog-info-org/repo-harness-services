/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.opa.entities.serviceaccount;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.governance.GovernanceMetadata;
import io.harness.opa.OpaEvaluationContext;
import io.harness.opa.OpaService;
import io.harness.opaclient.OpaUtils;
import io.harness.opaclient.model.OpaConstants;
import io.harness.serviceaccount.ServiceAccountDTO;

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
public class ServiceAccountOpaServiceImpl implements ServiceAccountOpaService {
  private OpaService opaService;

  private ServiceAccountOpaEvaluationContext createEvaluationContext(String yaml, String key) throws IOException {
    return ServiceAccountOpaEvaluationContext.builder()
        .serviceAccount(OpaUtils.extractObjectFromYamlString(yaml, key))
        .build();
  }

  public GovernanceMetadata evaluatePoliciesWithEntity(String accountId, ServiceAccountDTO serviceAccountDTO,
      String orgIdentifier, String projectIdentifier, String action, String identifier) {
    OpaEvaluationContext context;

    try {
      String expandedYaml = getServiceAccountYaml(serviceAccountDTO);
      context = createEvaluationContext(expandedYaml, OpaConstants.OPA_EVALUATION_TYPE_SERVICE_ACCOUNT);
      return opaService.evaluate(context, accountId, orgIdentifier, projectIdentifier, identifier, action,
          OpaConstants.OPA_EVALUATION_TYPE_SERVICE_ACCOUNT);
    } catch (IOException ex) {
      return GovernanceMetadata.newBuilder()
          .setDeny(true)
          .setMessage(String.format("Could not create OPA context: [%s]", ex.getMessage()))
          .build();
    }
  }

  private String getServiceAccountYaml(ServiceAccountDTO serviceAccountDTO) {
    String serviceAccountYaml = null;
    ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory()
                                                     .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
                                                     .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                                                     .disable(YAMLGenerator.Feature.USE_NATIVE_TYPE_ID));
    objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    try {
      serviceAccountYaml = objectMapper.writeValueAsString(serviceAccountDTO);
    } catch (Exception ex) {
      log.error("Failed while converting to connector yaml format", ex);
    }
    return serviceAccountYaml;
  }
}
