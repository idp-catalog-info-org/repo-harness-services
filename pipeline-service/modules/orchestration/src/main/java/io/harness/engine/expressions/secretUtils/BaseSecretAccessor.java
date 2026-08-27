/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.secretUtils;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.expression.ConnectorInputsMapper.buildIdentifierWithScope;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.SecretRefData;
import io.harness.exception.EngineFunctorException;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.ConnectorInputsMapper;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.ng.core.dto.secrets.SecretSpecDTO;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.secrets.remote.SecretNGManagerClient;
import io.harness.serializer.MapperUtils;
import io.harness.utils.IdentifierRefHelper;

import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Slf4j
public abstract class BaseSecretAccessor {
  protected final SecretNGManagerClient secretNGManagerClient;
  protected final Ambiance ambiance;

  protected BaseSecretAccessor(Ambiance ambiance, SecretNGManagerClient secretNGManagerClient) {
    this.secretNGManagerClient = secretNGManagerClient;
    this.ambiance = ambiance;
  }

  public Object get(Object key) {
    String secretIdentifier = String.valueOf(key);

    if (EmptyPredicate.isEmpty(secretIdentifier)) {
      return null;
    }

    return getSecretByIdentifier(secretIdentifier);
  }

  protected abstract Map<String, Object> getSecretByIdentifier(String secretIdentifier);

  protected SecretSpecDTO getSecretSpec(IdentifierRef identifierRef, String secretIdentifier) {
    String errorMsg = "No secret configured with identifier: " + secretIdentifier;
    SecretResponseWrapper secretResponseWrapper = NGRestUtils.getResponse(
        secretNGManagerClient.getSecret(identifierRef.getIdentifier(), identifierRef.getAccountIdentifier(),
            identifierRef.getOrgIdentifier(), identifierRef.getProjectIdentifier()),
        errorMsg);
    if (secretResponseWrapper == null) {
      throw new InvalidRequestException(errorMsg);
    }

    return secretResponseWrapper.getSecret().getSpec();
  }

  protected IdentifierRef createIdentifierRef(String secretIdentifier) {
    return IdentifierRefHelper.getIdentifierRef(secretIdentifier, AmbianceUtils.getAccountId(ambiance),
        AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance));
  }

  protected Map<String, Object> buildSecretFieldMap(SecretInputDTO secretInputDTO) {
    String idWithScope = buildIdentifierWithScope(secretInputDTO.getId(), secretInputDTO.getScope());
    Map<String, Object> resultMap = new HashMap<>(MapperUtils.toMapViaJsonString(secretInputDTO));
    resultMap.put("id", idWithScope);
    return resultMap;
  }

  protected static String convertSecretRefDataToString(SecretRefData secretRefData) {
    return ConnectorInputsMapper.convertSecretRefDataToString(secretRefData);
  }

  protected void handleSecretRetrievalException(String secretIdentifier, String secretType, Exception ex) {
    log.error("Failed to retrieve {} secret: {}", secretType, secretIdentifier, ex);
    throw new EngineFunctorException("Failed to retrieve " + secretType + " secret: " + secretIdentifier, ex);
  }
}