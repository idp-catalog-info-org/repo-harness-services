/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.clients;

import static io.harness.idp.common.YamlUtils.yamlObject;

import static org.joda.time.Minutes.minutes;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.security.JWTTokenServiceUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import org.yaml.snakeyaml.Yaml;
import retrofit2.Call;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.IDP)
public class POServerClientUtils {
  private final POServerClient poServerClient;
  private final POServerConfig poServerConfig;
  private static final Yaml yaml = yamlObject();

  @Inject
  public POServerClientUtils(POServerClient poServerClient, POServerConfig poServerConfig) {
    this.poServerClient = poServerClient;
    this.poServerConfig = poServerConfig;
  }

  public Object compile(String environmentYaml, String accountId, String org, String projectId) {
    String jwt = generateJWTToken(accountId, org, projectId);

    CompileRequestBody compileRequestBody = CompileRequestBody.builder()
                                                .environmentYaml(environmentYaml)
                                                .orgIdentifier(org)
                                                .projectIdentifier(projectId)
                                                .build();

    try {
      retrofit2.Response<Object> response;
      try {
        response = poServerClient.getCompileEnvironmentYaml(compileRequestBody, jwt, accountId, accountId).execute();
      } catch (java.io.IOException ioEx) {
        log.error("IO error while calling PO server for account = {}", accountId, ioEx);
        throw new RuntimeException("Network error while connecting to PO server: " + ioEx.getMessage(), ioEx);
      }

      return handleResponse(response, accountId, "compile environment yaml");
    } catch (Exception ex) {
      handleException(ex, accountId, "Error in getting the compile environment yaml");
      throw ex;
    }
  }

  public Object execute(Object compileEnvironmentYamlResponse, String accountId, String org, String projectId) {
    ExecuteRequestBody executeRequestBody;
    String jwt = generateJWTToken(accountId, org, projectId);
    try {
      ObjectMapper mapper = new ObjectMapper();
      @SuppressWarnings("unchecked")
      Map<String, Object> workspaceMap = mapper.convertValue(compileEnvironmentYamlResponse, Map.class);

      Map<String, Object> infrastructure = (Map<String, Object>) workspaceMap.get("infrastructure");
      Map<String, Object> combinedOverrides = (Map<String, Object>) workspaceMap.get("combinedOverrides");

      infrastructure.put("metadata", Map.of("combinedOverrides", combinedOverrides));

      String infrastructureYaml = yaml.dump(infrastructure);

      executeRequestBody = ExecuteRequestBody.builder()
                               .infrastructureYaml(infrastructureYaml)
                               .orgIdentifier(org)
                               .projectIdentifier(projectId)
                               .build();

    } catch (Exception e) {
      throw new RuntimeException("Failed to convert compiled yaml response to execute request body", e);
    }

    try {
      retrofit2.Response<Object> response;
      try {
        response = poServerClient.executeEnvironmentYaml(executeRequestBody, jwt, accountId).execute();
      } catch (java.io.IOException ioEx) {
        log.error("IO error while calling PO server for account = {}", accountId, ioEx);
        throw new RuntimeException("Network error while connecting to PO server: " + ioEx.getMessage(), ioEx);
      }

      return handleResponse(response, accountId, "execute environment yaml");
    } catch (Exception ex) {
      handleException(ex, accountId, "Error in making the execute request for environment");
      throw ex;
    }
  }

  public void deleteEnvironment(String infrastructureId, String accountId, String orgId, String projectId) {
    String jwt = generateJWTToken(accountId, orgId, projectId);

    try {
      log.info("Sending delete request to PO server for infrastructure id: {}, account: {}, org: {}, project: {}",
          infrastructureId, accountId, orgId, projectId);

      Call<okhttp3.ResponseBody> call =
          poServerClient.deleteInfrastructure(jwt, accountId, infrastructureId, orgId, projectId);

      okhttp3.Request request = call.request();
      log.info("DELETE request URL: {}, Headers: {}", request.url(), request.headers());

      retrofit2.Response<okhttp3.ResponseBody> response;
      try {
        response = call.execute();
        log.info("Delete infrastructure response code: {}", response.code());
      } catch (java.io.IOException ioEx) {
        log.error("IO error while calling PO server for account = {}, Error: {}", accountId, ioEx.getMessage(), ioEx);
        throw new RuntimeException("Network error while connecting to PO server: " + ioEx.getMessage(), ioEx);
      }

      if (!response.isSuccessful()) {
        String errorMessage = "Failed to delete infrastructure";
        try {
          if (response.errorBody() != null) {
            String errorBody = response.errorBody().string();
            if (!errorBody.isEmpty()) {
              log.error("Error response body: {}", errorBody);

              try {
                ObjectMapper mapper = new ObjectMapper();
                Map<String, Object> errorMap = mapper.readValue(errorBody, Map.class);

                if (errorMap.containsKey("message")) {
                  errorMessage = (String) errorMap.get("message");
                } else if (errorMap.containsKey("error")) {
                  errorMessage = (String) errorMap.get("error");
                } else {
                  errorMessage = errorBody;
                }
              } catch (Exception ex) {
                errorMessage = errorBody;
              }
            }
          }
        } catch (Exception e) {
          log.warn("Failed to read error body", e);
        }
        throw new RuntimeException(errorMessage);
      }

      // Success case - no need to parse body
      log.info("Successfully deleted infrastructure with id: {}", infrastructureId);
    } catch (Exception ex) {
      handleException(ex, accountId, "Error deleting infrastructure");
      throw ex;
    }
  }

  private String generateJWTToken(String accountId, String orgId, String projectId) {
    return JWTTokenServiceUtils.generateJWTToken(Map.of("accountId", accountId, "orgId", orgId, "projectId", projectId),
        minutes(1440).toStandardDuration().getMillis(), poServerConfig.getGlobalToken()); // 24 Hours
  }

  private Object handleResponse(retrofit2.Response<Object> response, String accountId, String operationDescription) {
    if (!response.isSuccessful()) {
      String errorDetails = "";

      try {
        if (response.errorBody() != null) {
          String errorBody = response.errorBody().string();
          log.info("Error body content: {}", errorBody);

          try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> errorMap = mapper.readValue(errorBody, Map.class);

            if (errorMap.containsKey("message")) {
              errorDetails = (String) errorMap.get("message");
            } else if (errorMap.containsKey("error")) {
              errorDetails = (String) errorMap.get("error");
            } else {
              errorDetails = errorBody;
            }
          } catch (Exception ex) {
            errorDetails = errorBody;
          }
        }
      } catch (Exception e) {
        log.warn("Failed to read error body", e);
      }

      throw new RuntimeException(errorDetails);
    }

    Object result = response.body();
    log.info("Response - {}", result);
    return result;
  }

  private void handleException(Exception ex, String accountId, String errorMessage) {
    if (ex instanceof RuntimeException && ex.getCause() == null) {
      throw (RuntimeException) ex;
    }

    log.error("{} for account = {}, Error = {}", errorMessage, accountId, ex.getMessage(), ex);

    String errorMsg = ex.getMessage();
    if (errorMsg != null && errorMsg.contains("Details:")) {
      int detailsIndex = errorMsg.indexOf("Details:");
      if (detailsIndex >= 0) {
        errorMsg = errorMsg.substring(detailsIndex + 8).trim();
      }
    }

    throw new RuntimeException(errorMsg);
  }
}
