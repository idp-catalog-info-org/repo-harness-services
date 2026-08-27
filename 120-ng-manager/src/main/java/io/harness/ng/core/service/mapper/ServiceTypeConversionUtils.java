/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.mapper;

import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.unified.cd.service.spec.ServiceType;

import java.util.Map;
import lombok.experimental.UtilityClass;

/**
 * Utility class for service type conversions between NG and Unified formats.

 */
@UtilityClass
public class ServiceTypeConversionUtils {
  /**
   * Map of NG service type names to Unified service types.
   */
  public static final Map<String, ServiceType> SERVICE_TYPE_CONVERSION_MAP =
      Map.ofEntries(Map.entry(ServiceDefinitionType.KUBERNETES.getYamlName(), ServiceType.KUBERNETES),
          Map.entry(ServiceDefinitionType.NATIVE_HELM.getYamlName(), ServiceType.HELM),
          Map.entry(ServiceDefinitionType.AWS_SAM.getYamlName(), ServiceType.AWS_SAM),
          Map.entry(ServiceDefinitionType.ECS.getYamlName(), ServiceType.ECS),
          Map.entry(ServiceDefinitionType.ASG.getYamlName(), ServiceType.ASG),
          Map.entry(ServiceDefinitionType.AZURE_WEBAPP.getYamlName(), ServiceType.AZURE_WEB_APP),
          Map.entry(ServiceDefinitionType.AWS_LAMBDA.getYamlName(), ServiceType.AWS_LAMBDA),
          Map.entry(ServiceDefinitionType.SERVERLESS_AWS_LAMBDA.getYamlName(), ServiceType.SERVERLESS),
          Map.entry(ServiceDefinitionType.GOOGLE_CLOUD_RUN.getYamlName(), ServiceType.GOOGLE_CLOUD_RUN),
          Map.entry(ServiceDefinitionType.AZURE_CONTAINER_APPS.getYamlName(), ServiceType.AZURE_CONTAINER_APPS),
          Map.entry(ServiceDefinitionType.AZURE_FUNCTION.getYamlName(), ServiceType.AZURE_FUNCTION),
          Map.entry(ServiceDefinitionType.ELASTIGROUP.getYamlName(), ServiceType.SPOT),
          Map.entry(ServiceDefinitionType.GOOGLE_AGENT_RUNTIME.getYamlName(), ServiceType.GOOGLE_AGENT_RUNTIME),
          Map.entry(ServiceDefinitionType.AWS_AGENT_CORE.getYamlName(), ServiceType.AWS_AGENT_CORE));
}
