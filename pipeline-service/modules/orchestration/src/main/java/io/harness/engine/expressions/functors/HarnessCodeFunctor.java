/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.functors;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.ci.execution.buildstate.ConnectorUtils;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.HarnessConnectorDTO;
import io.harness.expression.ConnectorInputsMapper;
import io.harness.expression.HarnessConnectorInputDTO;
import io.harness.expression.LateBindingMap;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.serializer.MapperUtils;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.HashMap;
import lombok.Builder;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
public class HarnessCodeFunctor extends LateBindingMap implements RuntimeAbstractFunctor {
  @Inject private ConnectorUtils connectorUtils;
  @Inject private ConnectorInputsMapper connectorInputsMapper;
  @Inject @Named("harnessCodeServiceSecret") private String harnessCodeServiceSecret;
  @Inject @Named("harnessCodeClientConfig") ServiceHttpClientConfig harnessCodeClientConfig;
  private final Ambiance ambiance;
  private static final String codeKey = "code";

  @Builder
  public HarnessCodeFunctor(Ambiance ambiance) {
    this.ambiance = ambiance;
  }

  @Override
  public boolean supportsKey(String key) {
    return key.equals(codeKey);
  }

  @Override
  public synchronized Object get(Object key) {
    if (!(key instanceof String repoName)) {
      return null;
    }

    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    String gitnessBaseUrl = harnessCodeClientConfig.getBaseUrl();
    ConnectorDetails connectorDetails = connectorUtils.getHarnessCodeConnectorDetails(
        ngAccess, true, ambiance, repoName, harnessCodeServiceSecret, gitnessBaseUrl);

    if (connectorDetails == null) {
      return null;
    }

    HarnessConnectorInputDTO harnessConnectorDTO =
        toHarnessConnectorDTO(connectorDetails, AmbianceUtils.getAccountId(ambiance), repoName);
    return new HashMap<>(MapperUtils.toMapViaJsonString(harnessConnectorDTO));
  }

  private HarnessConnectorInputDTO toHarnessConnectorDTO(
      ConnectorDetails connectorDetails, String accountId, String repoName) {
    if (connectorDetails == null || connectorDetails.getConnectorConfig() == null) {
      return null;
    }
    HarnessConnectorDTO harnessConnectorDTO = (HarnessConnectorDTO) connectorDetails.getConnectorConfig();
    return ConnectorInputsMapper.toHarnessConnectorInputDTO(harnessConnectorDTO, connectorDetails.getConnectorType(),
        connectorDetails.getIdentifier(), connectorDetails.getIdentifier(), connectorDetails.getProjectIdentifier(),
        connectorDetails.getOrgIdentifier(), accountId, repoName);
  }

  // This is required for CEL because CEL first calls the containsKey method and only if is true does it call get method
  // where we have our logic. That's why we are returning true here so that it can go to the get method.
  @Override
  public boolean containsKey(Object key) {
    if (HarnessYamlVersion.isV1(ambiance.getMetadata().getHarnessVersion())) {
      return true;
    } else {
      return super.containsKey(key);
    }
  }
}
