/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import static io.harness.connector.utils.ModuleConstants.CONNECTOR_DECORATOR_SERVICE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
import io.harness.beans.ScopeInfo;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.execution.expression.sdk.SdkFunctor;
import io.harness.serializer.JsonUtils;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
public class ConnectorFunctor implements SdkFunctor {
  public static final String CONNECTOR_KEY = "connector";
  @Inject @Named(CONNECTOR_DECORATOR_SERVICE) private ConnectorService connectorService;
  @Inject PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Inject ScopeInfoService scopeInfoService;

  @Override
  public Object get(Ambiance ambiance, String... args) {
    String connectorIdentifier = args[0];

    Optional<ConnectorResponseDTO> optional;
    boolean useScopeInfo = pmsFeatureFlagHelper.isEnabled(
        AmbianceUtils.getAccountId(ambiance), FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2);
    if (useScopeInfo) {
      IdentifierRef identifierRef =
          IdentifierRefHelper.getIdentifierRef(connectorIdentifier, AmbianceUtils.getAccountId(ambiance),
              AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance));
      ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(
          identifierRef.getAccountIdentifier(), identifierRef.getOrgIdentifier(), identifierRef.getProjectIdentifier());
      optional = connectorService.get(scopeInfo, identifierRef.getIdentifier());

    } else {
      optional = connectorService.getByRef(AmbianceUtils.getAccountId(ambiance),
          AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance), connectorIdentifier);
    }
    if (optional.isEmpty()) {
      return Collections.emptyMap();
    }
    return JsonUtils.convertValue(optional.get().getConnector(), Map.class);
  }
}
