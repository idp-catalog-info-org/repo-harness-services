/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.configmanager.service;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.Constants.GET_METHOD;
import static io.harness.idp.common.Constants.X_API_KEY;
import static io.harness.idp.common.HttpUtils.buildRequest;
import static io.harness.idp.common.HttpUtils.executeRequest;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.idp.common.CommonUtils;
import io.harness.idp.common.GsonUtils;
import io.harness.idp.common.PipelineTriggerUtils;
import io.harness.idp.configmanager.entities.CustomPluginInfoEntity;
import io.harness.idp.configmanager.entities.PluginInfoEntity;
import io.harness.idp.configmanager.repositories.PluginInfoRepository;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.idp.plugin.config.CustomPluginsConfig;
import io.harness.spec.server.idp.v1.model.CustomPluginStatus;
import io.harness.spec.server.idp.v1.model.NamespaceInfo;
import io.harness.spec.server.idp.v1.model.PluginInfo;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.github.resilience4j.retry.Retry;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;

@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class CustomPluginServiceImpl implements CustomPluginService {
  private static final Retry retry =
      PipelineTriggerUtils.buildRetryAndRegisterListeners(CustomPluginServiceImpl.class.getSimpleName());
  private static final List<String> steps = List.of("liteEngineTask", "harness-git-clone", "Download_Packages",
      "Install_Custom_Plugins", "Compile1", "Compile", "Build_and_Push", "GCR", "ShellScript_1");
  private final CustomPluginsConfig customPluginsConfig;
  private final String env;
  private final String xApiKey;
  private final NamespaceService namespaceService;
  private final PluginInfoRepository pluginInfoRepository;

  @Inject
  public CustomPluginServiceImpl(@Named("customPlugins") CustomPluginsConfig customPluginsConfig,
      @Named("env") String env, @Named("idpAutomationXApiKey") String xApiKey, NamespaceService namespaceService,
      PluginInfoRepository pluginInfoRepository) {
    this.customPluginsConfig = customPluginsConfig;
    this.env = env;
    this.xApiKey = xApiKey;
    this.namespaceService = namespaceService;
    this.pluginInfoRepository = pluginInfoRepository;
  }

  @Override
  public void triggerBuildPipeline(String accountIdentifier, String pluginId) {
    log.info("Plugin status: Trigger started for accountId: {}, pluginId: {}", accountIdentifier, pluginId);
    String apiURL = triggerBuildPipeline(accountIdentifier);
    log.info("Plugin status: Trigger Pipeline API URL: {}", apiURL);

    Optional<PluginInfoEntity> existingPluginInfoOptional =
        pluginInfoRepository.findByIdentifierAndAccountIdentifierAndType(
            pluginId, accountIdentifier, PluginInfo.PluginTypeEnum.CUSTOM);

    if (existingPluginInfoOptional.isPresent()) {
      CustomPluginInfoEntity customPluginInfoEntity = ((CustomPluginInfoEntity) existingPluginInfoOptional.get());
      customPluginInfoEntity.setStatusApiUrl(apiURL);
      pluginInfoRepository.update(pluginId, accountIdentifier, customPluginInfoEntity);
      log.info("Plugin status: API URL saved to db");
    }
  }

  @Override
  public CustomPluginStatus getCustomPluginStatus(String pluginId, String harnessAccount) {
    Optional<PluginInfoEntity> existingPluginInfoOptional =
        pluginInfoRepository.findByIdentifierAndAccountIdentifierAndType(
            pluginId, harnessAccount, PluginInfo.PluginTypeEnum.CUSTOM);
    if (existingPluginInfoOptional.isEmpty()) {
      throw new NotFoundException(
          format("Could not find plugin with identifier %s in account %s", pluginId, harnessAccount));
    }
    CustomPluginInfoEntity customPluginInfoEntity = ((CustomPluginInfoEntity) existingPluginInfoOptional.get());
    String url = customPluginInfoEntity.getStatusApiUrl();
    Request request = buildRequest(url, GET_METHOD, Map.of(X_API_KEY, xApiKey), null);

    CustomPluginStatus customPluginStatus = new CustomPluginStatus();

    String responseBodyString = executeRequest(request, retry);
    Map<String, Object> responseObject =
        (Map<String, Object>) GsonUtils.convertJsonStringToObject(responseBodyString, Map.class);
    Map<String, Object> webhookProcessingDetails =
        (Map<String, Object>) CommonUtils.findObjectByName(responseObject, "webhookProcessingDetails");
    Map<String, Object> pipelineExecutionSummary =
        (Map<String, Object>) CommonUtils.findObjectByName(responseObject, "pipelineExecutionSummary");
    customPluginStatus.status((String) pipelineExecutionSummary.get("status"));
    String pipelineExecutionId = (String) webhookProcessingDetails.get("pipelineExecutionId");
    String stageNodeId = (String) pipelineExecutionSummary.get("startingNodeId");

    if (Objects.equals(customPluginStatus.getStatus(), "Failed")) {
      Request executionRequest = buildRequest(customPluginsConfig.getPipelineExecutionUrl()
                                                  .replace("{planExecutionId}", pipelineExecutionId)
                                                  .replace("{stageNodeId}", stageNodeId),
          GET_METHOD, Map.of(X_API_KEY, xApiKey), null);
      String key = "";
      try {
        String executionResponseBodyString = executeRequest(executionRequest, retry);
        Map<String, Object> executionResponseObject =
            (Map<String, Object>) GsonUtils.convertJsonStringToObject(executionResponseBodyString, Map.class);
        Map<String, Object> nodeMap =
            (Map<String, Object>) CommonUtils.findObjectByName(executionResponseObject, "nodeMap");
        if (nodeMap != null) {
          for (Map.Entry<String, Object> entry : nodeMap.entrySet()) {
            Map<String, Object> value = (Map<String, Object>) entry.getValue();
            if (steps.contains(value.get("identifier")) && value.get("status").equals("Failed")) {
              customPluginStatus.error((String) CommonUtils.findObjectByName(value, "message"));
              List<Map<String, Object>> executableResponses =
                  (List<Map<String, Object>>) value.get("executableResponses");
              if (!isEmpty(executableResponses)) {
                String executableResponseType = executableResponses.get(0).keySet().stream().findFirst().orElse(null);
                if (!isEmpty(executableResponseType)) {
                  Map<String, Object> async =
                      (Map<String, Object>) executableResponses.get(0).get(executableResponseType);
                  List<String> logKeys = (List<String>) async.get("logKeys");
                  if (!isEmpty(logKeys)) {
                    key = logKeys.get(0);
                  }
                }
              }
              if (isEmpty(key)) {
                key = (String) value.get("logBaseKey");
              }
              log.info("Plugin status: Key fetched. {}", key);
            }
          }
        }
        customPluginStatus.key(key);
        log.info("Plugin status: Pipeline execution details fetched");
      } catch (Exception e) {
        log.error(format("Plugin status: PMS Request Failed. Exception: %s", e));
      }
    }
    return customPluginStatus;
  }

  @Override
  public String getCustomPluginStatusLogs(
      String accountId, String orgId, String projectId, String pipelineId, String logKey) {
    String logSummary = "";
    try {
      Request request = buildRequest(customPluginsConfig.getPipelineExecutionLogUrl().replace("{key}", logKey),
          GET_METHOD, Map.of(X_API_KEY, xApiKey), null);
      logSummary = executeRequest(request, retry);
      log.info("Plugin status: {}", logKey);
    } catch (Exception e) {
      log.error("Plugin status: Log Service Request Failed. Exception: {}", e.getMessage(), e);
    }
    return logSummary;
  }

  private String triggerBuildPipeline(String accountIdentifier) {
    NamespaceInfo namespaceInfo = namespaceService.getNamespaceForAccountIdentifier(accountIdentifier);
    String namespace = namespaceInfo.getNamespace();
    String url = customPluginsConfig.getTriggerPipelineUrl();
    return PipelineTriggerUtils.trigger(accountIdentifier, namespace, env, url, "", retry, xApiKey);
  }
}
