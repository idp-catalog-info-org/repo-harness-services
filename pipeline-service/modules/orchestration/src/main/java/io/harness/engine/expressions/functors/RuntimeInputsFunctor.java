/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.functors;

import io.harness.beans.IdentifierRef;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.delegate.beans.connector.utils.ConnectorType;
import io.harness.expression.LateBindingMap;
import io.harness.ngsettings.SettingIdentifiers;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.remote.client.NGRestUtils;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.PmsFeatureFlagService;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.Builder;
import org.apache.commons.lang3.StringUtils;

public class RuntimeInputsFunctor extends LateBindingMap implements RuntimeAbstractFunctor {
  @Inject PmsFeatureFlagService featureFlagService;
  @Inject NGSettingsClient settingsClient;
  @Inject private ConnectorResourceClient connectorResourceClient;
  Ambiance ambiance;
  private static final String inputsKey = "inputs";
  public static final String BACKEND = "PLUGIN_BACKEND"; // Backend for Save and Restore Step.
  private final Map<String, String> inputs = new HashMap<>();

  // This can be used to set any values for any variable we want. It will help us making our template and pipeline yaml
  // lightweight. We don't need to evaluate all those params in pipeline or template yaml thus less crowding in
  // pipeline/template yaml.
  private void processInputVariables() {
    setUpInputsForCacheIntelligence(ambiance);
  }

  @Override
  public boolean supportsKey(String key) {
    return key.equals(inputsKey);
  }

  @Builder
  public RuntimeInputsFunctor(Ambiance ambiance) {
    this.ambiance = ambiance;
  }

  @Override
  public synchronized Object get(Object key) {
    if (!(key instanceof String)) {
      return null;
    }
    processInputVariables();
    return inputs.get((String) key);
  }

  private String getConnectorType(String accountId, String projectId, String orgId, String connectorRef) {
    ConnectorType connectorType = null;
    try {
      IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(connectorRef, accountId, orgId, projectId);
      Optional<ConnectorDTO> connectorDTO = NGRestUtils.getResponse(
          connectorResourceClient.get(identifierRef.getIdentifier(), identifierRef.getAccountIdentifier(),
              identifierRef.getOrgIdentifier(), identifierRef.getProjectIdentifier()));
      if (connectorDTO.isEmpty()) {
        return null;
      }
      ConnectorInfoDTO connectorInfoDTO = connectorDTO.get().getConnectorInfo();
      if (connectorInfoDTO == null) {
        return null;
      }
      connectorType = connectorInfoDTO.getConnectorType();

      return connectorType.getDisplayName();
    } catch (Exception ex) {
      return null;
    }
  }

  // This will be removed later, we are planning to migrate this to Plugin side. For now we have workaround but this
  // will be changed later.
  private void setUpInputsForCacheIntelligence(Ambiance ambiance) {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectId = AmbianceUtils.getProjectIdentifier(ambiance);
    String settingsConnector =
        NGRestUtils
            .getResponse(settingsClient.getSetting(SettingIdentifiers.CI_CACHE_CONNECTOR, accountId, orgId, projectId))
            .getValue();
    String backend = "s3";

    if (StringUtils.isNotEmpty(settingsConnector)) {
      String type = getConnectorType(accountId, projectId, orgId, settingsConnector);
      if ("Gcp".equals(type)) {
        backend = "gcs";
      } else if ("Azure".equals(type)) {
        backend = "azure";
      }
    }
    inputs.put(BACKEND, backend);
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
