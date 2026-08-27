/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.iro;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.utils.DelegateOwner.getNGTaskSetupAbstractionsWithOwner;

import io.harness.account.services.AccountClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.IdentifierRef;
import io.harness.beans.KeyValuePair;
import io.harness.cdng.helpers.NgExpressionHelper;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.ConnectorServiceImpl;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.task.http.HttpStepResponse;
import io.harness.delegate.task.http.HttpTaskParameters;
import io.harness.delegate.task.iro.IrHttpTaskParams;
import io.harness.exception.UnexpectedException;
import io.harness.http.HttpHeaderConfig;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.NGAccessWithEncryptionConsumer;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.notification.NotificationTriggerRequest;
import io.harness.notification.entities.NotificationEntity;
import io.harness.notification.entities.NotificationEvent;
import io.harness.notification.notificationclient.NotificationClient;
import io.harness.remote.client.CGRestUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.secrets.remote.SecretNGManagerClient;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.spec.server.ng.v1.model.SLONotificationDTO;
import io.harness.utils.IdentifierRefHelper;

import software.wings.beans.HttpStateExecutionResponse;
import software.wings.beans.TaskType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.nimbusds.oauth2.sdk.util.StringUtils;
import io.burt.jmespath.Expression;
import io.burt.jmespath.JmesPath;
import io.burt.jmespath.jackson.JacksonRuntime;
import io.burt.jmespath.parser.ParseException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.UriBuilder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.utils.URIBuilder;

@Singleton
@OwnedBy(PL)
@NoArgsConstructor(force = true)
@AllArgsConstructor
@Slf4j
public class IRServiceImpl implements IRService {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  // Socket timeout for the delegate HTTP call (2 minutes). Avoids an infinite socket wait (timeout 0) on a hung target.
  private static final int HTTP_SOCKET_TIMEOUT_MILLIS = 120000;

  @Inject private final NotificationClient notificationClient;
  @Inject private final AccountClient accountClient;

  @Inject NgExpressionHelper ngExpressionHelper;
  @Inject DelegateGrpcClientWrapper delegateGrpcClientWrapper;

  @Inject ConnectorServiceImpl connectorService;

  @Inject @Named("PRIVILEGED") SecretNGManagerClient secretNGManagerClient;

  @Override
  public void sloNotificationTriggerHandler(SLONotificationDTO sloNotificationRequest, String harnessAccount) {
    String notificationTriggerRequestId = generateUuid();
    String orgIdentifier = sloNotificationRequest.getRuleOrgId();
    String projectIdentifier = sloNotificationRequest.getRuleProjectId();
    String sloOrgIdentifier = sloNotificationRequest.getSloOrgId();
    String sloProjectIdentifier = sloNotificationRequest.getSloProjectId();
    String baseUrl = ngExpressionHelper.getBaseUrl(harnessAccount);

    String eventIdentifier = sloNotificationRequest.getEventTemplateIdentifier();
    String entityIdentifier = sloNotificationRequest.getEntityIdentifier();
    String eventName = getEventName(eventIdentifier);
    String sloName = sloNotificationRequest.getSloName();
    AccountDTO accountDTO = null;
    if (accountClient != null) {
      accountDTO = CGRestUtils.getResponse(accountClient.getAccountDTO(harnessAccount));
    }

    String rawSloIdentifier = extractRawSloIdentifier(entityIdentifier);

    URI uri = buildUri(baseUrl, harnessAccount, sloOrgIdentifier, sloProjectIdentifier, rawSloIdentifier);

    String formattedIncidentTime = getFormattedIncidentTime();
    String incidentUnixTimestamp = String.valueOf(Instant.now().getEpochSecond());

    if (!isNotEmpty(eventName)) {
      log.error("Unknown event identifier {} received for slo notification, correlationId: {}", eventIdentifier,
          sloNotificationRequest.getCorrelationId());
      return;
    }

    Map<String, String> templateData = new HashMap<>();
    templateData.put("SLO_IDENTIFIER", rawSloIdentifier);
    templateData.put("SLO_NAME", sloName);
    templateData.put("SLO_PROJECT_NAME", sloProjectIdentifier);
    templateData.put("SLO_ORG_NAME", sloOrgIdentifier);
    templateData.put("TEMPLATE_IDENTIFIER", eventIdentifier);
    templateData.put("INCIDENT_TIME", formattedIncidentTime);
    templateData.put("INCIDENT_UNIX_TIMESTAMP", incidentUnixTimestamp);
    templateData.put("ACCOUNT_ID", harnessAccount);
    templateData.put("SLO_URL", uri.toString());
    if (accountDTO != null) {
      templateData.put("ACCOUNT_NAME", accountDTO.getName());
    } else {
      templateData.put("ACCOUNT_NAME", harnessAccount);
    }

    NotificationTriggerRequest.Builder notificationTriggerRequestBuilder =
        NotificationTriggerRequest.newBuilder()
            .setId(notificationTriggerRequestId)
            .setAccountId(harnessAccount)
            .setEventEntity(NotificationEntity.SERVICE_LEVEL_OBJECTIVE.name())
            .setEvent(eventName)
            .setEntityIdentifier(entityIdentifier)
            .putAllTemplateData(templateData);

    if (isNotEmpty(projectIdentifier)) {
      notificationTriggerRequestBuilder.setProjectId(projectIdentifier);
    }

    if (isNotEmpty(orgIdentifier)) {
      notificationTriggerRequestBuilder.setOrgId(orgIdentifier);
    }

    log.info("Sending {} notification for {}, correlationId: {}", eventIdentifier, entityIdentifier,
        sloNotificationRequest.getCorrelationId());
    notificationClient.sendNotificationTrigger(notificationTriggerRequestBuilder.build());
  }

  private static String getEventName(String eventTemplateIdentifier) {
    switch (eventTemplateIdentifier) {
      case "slo_error_budget_burn_rate":
        return NotificationEvent.SLO_ERROR_BUDGET_BURN_RATE.name();
      case "slo_error_budget_remaining_minutes":
        return NotificationEvent.SLO_ERROR_BUDGET_REMAINING_MINUTES.name();
      case "slo_error_budget_remaining_percentage":
        return NotificationEvent.SLO_ERROR_BUDGET_REMAINING_PERCENTAGE.name();
      default:
        return null;
    }
  }

  private String extractRawSloIdentifier(String entityIdentifier) {
    if (entityIdentifier == null || !entityIdentifier.contains(":")) {
      return entityIdentifier;
    }
    String[] entityIdentifierParts = entityIdentifier.split(":");
    return entityIdentifierParts[entityIdentifierParts.length - 1];
  }

  private URI buildUri(String baseUrl, String harnessAccount, String sloOrgIdentifier, String sloProjectIdentifier,
      String rawSloIdentifier) {
    return UriBuilder.fromUri(baseUrl.replace("#", ""))
        .path("account/{harnessAccount}")
        .path("module/ir/orgs/{sloOrgIdentifier}")
        .path("projects/{sloProjectIdentifier}")
        .path("slos/{rawSloIdentifier}")
        .resolveTemplate("harnessAccount", harnessAccount)
        .resolveTemplate("sloOrgIdentifier", sloOrgIdentifier)
        .resolveTemplate("sloProjectIdentifier", sloProjectIdentifier)
        .resolveTemplate("rawSloIdentifier", rawSloIdentifier)
        .build();
  }

  private String getFormattedIncidentTime() {
    Instant incidentTime = Instant.now();
    return DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss z yyyy").withZone(ZoneOffset.UTC).format(incidentTime);
  }

  @Override
  public HttpDelegateTaskResponse httpDelegateTaskHandler(HttpDelegateTaskRequest httpDelegateTaskRequest) {
    if (httpDelegateTaskRequest == null) {
      throw new IllegalArgumentException("HTTP delegate task request cannot be null");
    }

    final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    try {
      List<KeyValuePair> headers = new ArrayList<>();
      Map<String, String> requestHeaders = httpDelegateTaskRequest.getHeaders();
      if (requestHeaders != null) {
        requestHeaders.forEach((key, value) -> headers.add(KeyValuePair.builder().key(key).value(value).build()));
      }

      Object body = httpDelegateTaskRequest.getBody();
      String serializedBody;

      if (body == null) {
        serializedBody = null;
      } else if (body instanceof TextNode textNode) {
        serializedBody = textNode.asText();
      } else {
        serializedBody = OBJECT_MAPPER.writeValueAsString(body);
      }

      String fullUrl = appendQueryParams(httpDelegateTaskRequest.getUrl(), httpDelegateTaskRequest.getQueryParams());

      HttpTaskParameters httpTaskParameters = HttpTaskParameters.builder()
                                                  .method(httpDelegateTaskRequest.getMethod())
                                                  .url(fullUrl)
                                                  .headers(headers)
                                                  .body(serializedBody)
                                                  .build();

      Long timeout = TaskData.DEFAULT_SYNC_CALL_TIMEOUT;

      List<TaskSelector> delegateSelectors = new ArrayList<>();
      for (String selector : httpDelegateTaskRequest.getDelegateSelectors()) {
        delegateSelectors.add(TaskSelector.newBuilder().setSelector(selector).build());
      }

      Map<String, String> owner = getNGTaskSetupAbstractionsWithOwner(httpDelegateTaskRequest.getDelegateAccountId(),
          httpDelegateTaskRequest.getDelegateOrgId(), httpDelegateTaskRequest.getDelegateProjectId());
      Map<String, String> abstractions = new HashMap<>(owner);

      DelegateTaskRequest delegateTaskRequest = DelegateTaskRequest.builder()
                                                    .accountId(httpDelegateTaskRequest.getDelegateAccountId())
                                                    .taskParameters(httpTaskParameters)
                                                    .taskType(TaskType.HTTP.name())
                                                    .selectionLogsTrackingEnabled(true)
                                                    .taskSetupAbstractions(abstractions)
                                                    .selectors(delegateSelectors)
                                                    .executionTimeout(Duration.ofMillis(timeout))
                                                    .build();

      if (delegateGrpcClientWrapper == null) {
        throw new IllegalStateException("Delegate gRPC client is not available");
      }

      try {
        DelegateResponseData response = delegateGrpcClientWrapper.executeSyncTaskV2(delegateTaskRequest);
        if (response instanceof HttpStateExecutionResponse httpResponse) {
          int statusCode = httpResponse.getHttpResponseCode();
          String responseBody = httpResponse.getHttpResponseBody();
          ObjectMapper objectMapper = new ObjectMapper();
          JsonNode responseJson = StringUtils.isNotBlank(responseBody) ? objectMapper.readTree(responseBody) : null;

          if (statusCode < 200 || statusCode >= 300) {
            String errorMessage = "HTTP call failed with status " + statusCode;
            if (responseJson != null && responseJson.has("message")) {
              errorMessage = responseJson.get("message").asText();
            }
            return new HttpDelegateTaskResponse(statusCode, responseJson, errorMessage);
          }

          if (httpDelegateTaskRequest.getExpectedOutputs().isEmpty()) {
            return new HttpDelegateTaskResponse(statusCode, responseJson, null);
          } else {
            try {
              JmesPath<JsonNode> jmespath = new JacksonRuntime();
              ObjectNode outputMap = JsonNodeFactory.instance.objectNode();

              for (HttpDelegateTaskRequest.ExpectedOutput expectedOutput :
                  httpDelegateTaskRequest.getExpectedOutputs()) {
                String outputName = StringUtils.isNotBlank(expectedOutput.getName())
                    ? expectedOutput.getName()
                    : "output_" + (httpDelegateTaskRequest.getExpectedOutputs().indexOf(expectedOutput) + 1);

                try {
                  Expression<JsonNode> expression = jmespath.compile(expectedOutput.getJmesPathSelector());
                  JsonNode result = expression.search(responseJson);
                  outputMap.set(outputName, result);
                } catch (ParseException e) {
                  log.error(
                      "Invalid JMESPath expression '{}': {}", expectedOutput.getJmesPathSelector(), e.getMessage(), e);
                  throw e;
                }
              }

              return new HttpDelegateTaskResponse(statusCode, outputMap, null);
            } catch (Exception e) {
              log.error("JMESPath processing failed", e);
              return new HttpDelegateTaskResponse(
                  statusCode, responseJson, "JMESPath evaluation failed: " + e.getMessage());
            }
          }
        }
      } catch (Exception e) {
        log.error("Failed to execute HTTP delegate task for URL: {}", httpDelegateTaskRequest.getUrl(), e);
        return new HttpDelegateTaskResponse(500, null, "Failed to process HTTP request: " + e.getMessage());
      }

      return new HttpDelegateTaskResponse(500, null, "Unexpected response from delegate");

    } catch (Exception e) {
      log.error("Failed to execute HTTP delegate task for URL: {}", httpDelegateTaskRequest.getUrl(), e);
      return new HttpDelegateTaskResponse(500, null, "Failed to process HTTP request: " + e.getMessage());
    }
  }

  @Override
  public HttpDelegateTaskResponse connectorHttpTaskHandler(IrConnectorHttpRequest request) {
    if (request == null) {
      throw new IllegalArgumentException("Connector HTTP request cannot be null");
    }
    try {
      String accountId = request.getDelegateAccountId();

      // Connector scope is independent of the delegate/task scope: an org-scoped connector can be executed by an
      // account-level delegate. Resolve the connector (and its encryption details) at the connector's own scope.
      IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(request.getConnectorIdentifier(), accountId,
          request.resolveConnectorOrgId(), request.resolveConnectorProjectId());
      ConnectorResponseDTO connectorResponse =
          connectorService
              .get(connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(),
                  connectorRef.getProjectIdentifier(), connectorRef.getIdentifier())
              .orElseThrow(
                  () -> new IllegalArgumentException("Connector not found: " + request.getConnectorIdentifier()));
      ConnectorConfigDTO connectorConfig = connectorResponse.getConnector().getConnectorConfig();

      NGAccess ngAccess = BaseNGAccess.builder()
                              .accountIdentifier(connectorRef.getAccountIdentifier())
                              .orgIdentifier(connectorRef.getOrgIdentifier())
                              .projectIdentifier(connectorRef.getProjectIdentifier())
                              .identifier(connectorRef.getIdentifier())
                              .build();
      List<List<EncryptedDataDetail>> encryptedDataDetails = new ArrayList<>();
      List<DecryptableEntity> decryptableEntities = connectorConfig.getDecryptableEntities();
      if (decryptableEntities != null) {
        for (DecryptableEntity decryptableEntity : decryptableEntities) {
          encryptedDataDetails.add(getEncryptionDetails(ngAccess, decryptableEntity));
        }
      }

      List<HttpHeaderConfig> headers = new ArrayList<>();
      if (request.getHeaders() != null) {
        request.getHeaders().forEach(
            (key, value) -> headers.add(HttpHeaderConfig.builder().key(key).value(value).build()));
      }

      JsonNode bodyNode = request.getBody();
      String serializedBody;
      if (bodyNode == null) {
        serializedBody = null;
      } else if (bodyNode instanceof TextNode textNode) {
        serializedBody = textNode.asText();
      } else {
        serializedBody = OBJECT_MAPPER.writeValueAsString(bodyNode);
      }

      String fullUrl = appendQueryParams(request.getUrl(), request.getQueryParams());

      IrHttpTaskParams taskParams = IrHttpTaskParams.builder()
                                        .method(request.getMethod())
                                        .url(fullUrl)
                                        .requestHeader(headers)
                                        .body(serializedBody)
                                        .socketTimeoutMillis(HTTP_SOCKET_TIMEOUT_MILLIS)
                                        .connectorConfig(connectorConfig)
                                        .encryptedDataDetails(encryptedDataDetails)
                                        .build();

      List<TaskSelector> delegateSelectors = new ArrayList<>();
      if (request.getDelegateSelectors() != null) {
        for (String selector : request.getDelegateSelectors()) {
          delegateSelectors.add(TaskSelector.newBuilder().setSelector(selector).build());
        }
      }

      // Task owner/routing uses the DELEGATE scope (where the delegate lives), not the connector scope.
      Map<String, String> abstractions = new HashMap<>(
          getNGTaskSetupAbstractionsWithOwner(accountId, request.getDelegateOrgId(), request.getDelegateProjectId()));

      DelegateTaskRequest delegateTaskRequest =
          DelegateTaskRequest.builder()
              .accountId(accountId)
              .taskParameters(taskParams)
              .taskType(TaskType.IR_HTTP_TASK_NG.name())
              .selectionLogsTrackingEnabled(true)
              .taskSetupAbstractions(abstractions)
              .selectors(delegateSelectors)
              .executionTimeout(Duration.ofMillis(TaskData.DEFAULT_SYNC_CALL_TIMEOUT))
              .build();

      DelegateResponseData response = delegateGrpcClientWrapper.executeSyncTaskV2(delegateTaskRequest);
      if (response instanceof HttpStepResponse httpResponse) {
        return parseHttpStepResponse(httpResponse);
      }
      // Not an HttpStepResponse (e.g. delegate task failed / error response) - a server-side fault, not a client error.
      throw new UnexpectedException("Unexpected response from delegate for URL: " + request.getUrl());
    } catch (UnexpectedException e) {
      throw e;
    } catch (Exception e) {
      log.error("Failed to execute connector HTTP task for URL: {}", request.getUrl(), e);
      throw new UnexpectedException("Failed to process HTTP request: " + e.getMessage(), e);
    }
  }

  private HttpDelegateTaskResponse parseHttpStepResponse(HttpStepResponse httpResponse) throws Exception {
    // Thin pass-through: return the target API's status code and raw body unchanged for all statuses. ng-manager does
    // not interpret the status or transform the body, and does not apply output selectors — the caller (ai-sre) owns
    // error handling and any response extraction (e.g. JMESPath).
    int statusCode = httpResponse.getHttpResponseCode();
    String responseBody = httpResponse.getHttpResponseBody();
    JsonNode responseJson = StringUtils.isNotBlank(responseBody) ? OBJECT_MAPPER.readTree(responseBody) : null;
    return new HttpDelegateTaskResponse(statusCode, responseJson, null);
  }

  private List<EncryptedDataDetail> getEncryptionDetails(NGAccess ngAccess, DecryptableEntity decryptableEntity) {
    return NGRestUtils.getResponse(secretNGManagerClient.getEncryptionDetails(ngAccess.getAccountIdentifier(),
        NGAccessWithEncryptionConsumer.builder().ngAccess(ngAccess).decryptableEntity(decryptableEntity).build()));
  }

  private String appendQueryParams(String baseUrl, Map<String, String> queryParams) {
    if (queryParams == null || queryParams.isEmpty()) {
      return baseUrl;
    }

    try {
      URIBuilder uriBuilder = new URIBuilder(baseUrl);
      for (Map.Entry<String, String> entry : queryParams.entrySet()) {
        uriBuilder.addParameter(entry.getKey(), entry.getValue());
      }
      return uriBuilder.build().toString();
    } catch (URISyntaxException e) {
      log.error("Failed to append query parameters to URL: {}", baseUrl, e);
      throw new IllegalArgumentException("Invalid URL or query parameters", e);
    }
  }
}