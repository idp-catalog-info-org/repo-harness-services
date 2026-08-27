/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.container.utils;

import static io.harness.beans.FeatureName.HAR_ENABLED;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.HarnessCodeServiceConfig;
import io.harness.beans.environment.ConnectorConversionInfo;
import io.harness.ci.utils.BaseConnectorUtils;
import io.harness.ci.utils.HarnessRegistryConnectorUtils;
import io.harness.connector.utils.HarnessCodeConnectorUtils;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.exception.ConnectorNotFoundException;
import io.harness.ng.core.NGAccess;
import io.harness.steps.container.execution.ContainerExecutionConfig;
import io.harness.utils.PmsFeatureFlagService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class ConnectorUtils extends BaseConnectorUtils {
  @Inject private PmsFeatureFlagService featureFlagService;
  @Inject private HarnessRegistryConnectorUtils harnessRegistryConnectorUtils;
  private static String HAR_CONNECTOR = "HARNESS_ARTIFACT_REGISTRY";
  // expecting no one to have this connector name - will make it as reserved connector name later
  private static String CODE_API_CONNECTOR = "##HARNESS_CODE_REPOSITORY##";
  @Inject private HarnessCodeConnectorUtils harnessCodeConnectorUtils;
  @Inject private HarnessCodeServiceConfig harnessCodeServiceConfig;
  @Inject
  public ConnectorUtils(ContainerExecutionConfig containerExecutionConfig) {
    this.containerExecutionConfig = containerExecutionConfig;
  }

  private final ContainerExecutionConfig containerExecutionConfig;

  public ConnectorDetails getDefaultInternalConnector(NGAccess ngAccess) {
    ConnectorDetails connectorDetails = null;
    try {
      connectorDetails = getConnectorDetails(ngAccess, containerExecutionConfig.getDefaultInternalImageConnector());
    } catch (ConnectorNotFoundException e) {
      log.info("Default harness image connector does not exist: {}", e.getMessage());
      connectorDetails = null;
    }
    return connectorDetails;
  }

  public ConnectorDetails getConnectorDetailsWithConversionInfo(
      NGAccess ngAccess, ConnectorConversionInfo connectorConversionInfo) {
    ConnectorDetails connectorDetails;
    if (isNotEmpty(connectorConversionInfo.getRegistryRef())
        && featureFlagService.isEnabled(ngAccess.getAccountIdentifier(), HAR_ENABLED)) {
      connectorDetails = harnessRegistryConnectorUtils.getConnectorDetailsForHarnessArtifactRegistry(ngAccess);
      connectorConversionInfo.setConnectorRef(HAR_CONNECTOR);
    } else if (connectorConversionInfo.isHarnessCodeRepo()) {
      connectorDetails = getHarnessConnectorDetails(ngAccess, harnessCodeServiceConfig.getGitUrl(), null,
          connectorConversionInfo.getHarnessCodeToken(), harnessCodeServiceConfig.getApiUrl());
      connectorConversionInfo.setConnectorRef(CODE_API_CONNECTOR);
    } else {
      connectorDetails = getConnectorDetails(ngAccess, connectorConversionInfo.getConnectorRef());
    }
    connectorDetails.setEnvToSecretsMap(connectorConversionInfo.getEnvToSecretsMap());
    return connectorDetails;
  }
}
