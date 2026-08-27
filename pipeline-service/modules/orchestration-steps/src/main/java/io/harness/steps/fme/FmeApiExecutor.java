/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.fme;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.NestedExceptionUtils;
import io.harness.fme.FmeResponse;
import io.harness.fme.governance.FmeErrorResponse;
import io.harness.fme.governance.FmeGovernanceResult;
import io.harness.fme.governance.GovernanceStatus;
import io.harness.fme.governance.HasGovernance;
import io.harness.logging.LogLevel;
import io.harness.logstreaming.NGLogCallback;
import io.harness.serializer.JsonUtils;
import io.harness.steps.fme.exception.FmeFeatureFlagDefinitionNotFoundException;
import io.harness.steps.fme.exception.FmeFeatureFlagNotFoundException;
import io.harness.steps.fme.exception.FmeFlagsetNotFoundException;
import io.harness.steps.fme.exception.FmeInternalServerErrorException;
import io.harness.steps.fme.exception.FmePolicyDeniedException;
import io.harness.steps.fme.exception.FmeSegmentNotFoundException;

import java.io.IOException;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.StringUtils;
import retrofit2.Call;
import retrofit2.Response;

/**
 * Utility class for executing FME API calls with standardized error handling.
 * Centralizes:
 * - IOException handling during call execution
 * - Error body extraction from failed responses
 * - 404 handling (flag not found vs definition not found)
 * - 499 handling (policy denied)
 * - Governance warning logging on successful responses
 * - Logging of success/failure
 */
@OwnedBy(HarnessTeam.FME)
@UtilityClass
@Slf4j
public class FmeApiExecutor {
  private static final int POLICY_DENIED_STATUS_CODE = 499;

  /**
   * Context for API execution containing common fields for logging and error handling.
   */
  @Getter
  @Builder
  public static class ExecutionContext {
    private final NGLogCallback logCallback;
    private final String flagName;
    private final String environment; // nullable for flag-level operations without environment
    private final String operationName; // e.g., "archive", "create", "kill", "get definition"
  }

  /**
   * How to handle 404 responses.
   */
  public enum NotFoundBehavior {
    /** Throw FmeFeatureFlagNotFoundException */
    THROW_FLAG_NOT_FOUND,
    /** Throw FmeFeatureFlagDefinitionNotFoundException */
    THROW_DEFINITION_NOT_FOUND,
    /** Throw FmeSegmentNotFoundException */
    THROW_SEGMENT_NOT_FOUND,
    /** Throw FmeFlagsetNotFoundException */
    THROW_FLAGSET_NOT_FOUND,
    /** Return null (caller handles missing resource) */
    RETURN_NULL
  }

  /**
   * Functional interface for custom error message formatting.
   * Receives flagName, environment, and errorBody, returns formatted error message.
   */
  @FunctionalInterface
  public interface ErrorMessageFormatter {
    String format(String flagName, String environment, String errorBody);
  }

  // Default error message formatter
  private static final ErrorMessageFormatter DEFAULT_ERROR_FORMATTER = (flagName, environment, errorBody) -> {
    if (StringUtils.isNotBlank(environment)) {
      return String.format(
          "FME API request failed for flag '%s' in environment '%s'. Error: %s", flagName, environment, errorBody);
    }
    return String.format("FME API request failed for flag '%s'. Error: %s", flagName, errorBody);
  };

  /**
   * Executes an API call and returns the response body.
   *
   * @param call The Retrofit call to execute
   * @param context Execution context for logging and error messages
   * @param notFoundBehavior How to handle 404 responses
   * @param <T> Response body type
   * @return The response body on success, or null if 404 and RETURN_NULL behavior
   */
  public <T> T execute(Call<T> call, ExecutionContext context, NotFoundBehavior notFoundBehavior) {
    return execute(call, context, notFoundBehavior, DEFAULT_ERROR_FORMATTER);
  }

  /**
   * Executes an API call with custom error message formatting.
   *
   * @param call The Retrofit call to execute
   * @param context Execution context for logging and error messages
   * @param notFoundBehavior How to handle 404 responses
   * @param errorFormatter Custom formatter for error messages
   * @param <T> Response body type
   * @return The response body on success, or null if 404 and RETURN_NULL behavior
   */
  public <T> T execute(
      Call<T> call, ExecutionContext context, NotFoundBehavior notFoundBehavior, ErrorMessageFormatter errorFormatter) {
    Response<T> response = executeCall(call);

    if (response.isSuccessful()) {
      T body = response.body();
      log.info(
          "[FME API] {} - Success: statusCode={}, responseBody={}", context.getOperationName(), response.code(), body);
      logSuccess(context);
      logGovernanceWarningsIfPresent(body, context);
      return body;
    }

    return handleErrorResponse(response, context, notFoundBehavior, errorFormatter);
  }

  /**
   * Executes an API call that returns FmeResponse wrapper and unwraps the entity.
   * Logs governance warnings if present on the wrapper.
   * Use this for v2 API endpoints that return wrapped responses.
   *
   * @param call The Retrofit call to execute (returns FmeResponse)
   * @param context Execution context for logging and error messages
   * @param notFoundBehavior How to handle 404 responses
   * @param <T> The entity type inside FmeResponse
   * @return The unwrapped entity on success, or null if 404 and RETURN_NULL behavior
   */
  public <T> T executeWrapped(Call<FmeResponse<T>> call, ExecutionContext context, NotFoundBehavior notFoundBehavior) {
    return executeWrapped(call, context, notFoundBehavior, DEFAULT_ERROR_FORMATTER);
  }

  /**
   * Executes an API call that returns FmeResponse wrapper with custom error formatting.
   * Logs governance warnings if present on the wrapper.
   * Use this for v2 API endpoints that return wrapped responses.
   *
   * @param call The Retrofit call to execute (returns FmeResponse)
   * @param context Execution context for logging and error messages
   * @param notFoundBehavior How to handle 404 responses
   * @param errorFormatter Custom formatter for error messages
   * @param <T> The entity type inside FmeResponse
   * @return The unwrapped entity on success, or null if 404 and RETURN_NULL behavior
   */
  public <T> T executeWrapped(Call<FmeResponse<T>> call, ExecutionContext context, NotFoundBehavior notFoundBehavior,
      ErrorMessageFormatter errorFormatter) {
    Response<FmeResponse<T>> response = executeCall(call);

    if (response.isSuccessful()) {
      FmeResponse<T> wrapper = response.body();
      logSuccess(context);
      logGovernanceWarningsIfPresent(wrapper, context);
      return wrapper != null ? wrapper.getEntity() : null;
    }

    return handleErrorResponse(response, context, notFoundBehavior, errorFormatter);
  }

  /**
   * Executes the Retrofit call, converting IOException to FmeInternalServerErrorException.
   */
  private <T> Response<T> executeCall(Call<T> call) {
    try {
      return call.execute();
    } catch (IOException e) {
      throw new FmeInternalServerErrorException(format("Failed to communicate with FME API: %s", e.getMessage()));
    }
  }

  /**
   * Handles non-successful responses. Uses Response<?> since we only need status code and error body.
   * The generic return type allows callers to use this for any response type.
   */
  private <T> T handleErrorResponse(Response<?> response, ExecutionContext context, NotFoundBehavior notFoundBehavior,
      ErrorMessageFormatter errorFormatter) {
    int statusCode = response.code();
    String errorBody = extractErrorBody(response);

    log.info("[FME API] {} - Error: statusCode={}, errorBody={}", context.getOperationName(), statusCode, errorBody);
    context.getLogCallback().saveExecutionLog(
        format("[FME API] Error Response -> statusCode: %d, body: %s", statusCode, errorBody), LogLevel.ERROR);

    // Handle 404
    if (statusCode == 404) {
      return handleNotFound(context, notFoundBehavior, errorBody);
    }

    // Handle 499 - Policy Denied
    if (statusCode == POLICY_DENIED_STATUS_CODE) {
      handlePolicyDenied(context, errorBody);
    }

    // Handle other errors
    String errorMessage = errorFormatter.format(context.getFlagName(), context.getEnvironment(), errorBody);
    logError(context, errorBody);
    throw new FmeInternalServerErrorException(errorMessage);
  }

  /**
   * Handles 499 policy denied responses by parsing the error body,
   * logging the governance details, and throwing a chained exception for nice UI display.
   */
  private void handlePolicyDenied(ExecutionContext context, String errorBody) {
    FmeGovernanceResult governanceResult = null;
    String policyMessage = "Policy denied";

    try {
      FmeErrorResponse errorResponse = JsonUtils.asObject(errorBody, FmeErrorResponse.class);
      if (errorResponse != null) {
        policyMessage = errorResponse.getMessage() != null ? errorResponse.getMessage() : policyMessage;
        governanceResult = extractGovernanceResult(errorResponse.getDetails());
      }
    } catch (Exception e) {
      log.warn("Failed to parse FME policy denied error response: {}", e.getMessage());
    }

    // Log the governance result
    if (governanceResult != null) {
      context.getLogCallback().saveExecutionLog("=== Policy Denied ===", LogLevel.ERROR);
      context.getLogCallback().saveExecutionLog(FmeGovernanceFormatter.formatForLog(governanceResult), LogLevel.ERROR);
    } else {
      context.getLogCallback().saveExecutionLog(format("Policy denied: %s", policyMessage), LogLevel.ERROR);
    }

    // Create the exception message
    String exceptionMessage =
        governanceResult != null ? FmeGovernanceFormatter.formatForExceptionMessage(governanceResult) : policyMessage;

    // Create chained exception for nice UI display
    FmePolicyDeniedException baseException = new FmePolicyDeniedException(exceptionMessage, governanceResult);

    String hint = FmeGovernanceFormatter.getHintMessage(governanceResult);
    String explanation = FmeGovernanceFormatter.getExplanationMessage(governanceResult);

    throw NestedExceptionUtils.hintWithExplanationException(hint, explanation, baseException);
  }

  /**
   * Logs governance warnings if present on the response body.
   * Called for successful responses that may contain governance warning info.
   */
  private <T> void logGovernanceWarningsIfPresent(T body, ExecutionContext context) {
    if (body == null) {
      return;
    }

    FmeGovernanceResult governance = null;
    if (body instanceof HasGovernance) {
      governance = ((HasGovernance) body).getGovernance();
    }

    if (governance != null && governance.getStatus() == GovernanceStatus.WARNING) {
      context.getLogCallback().saveExecutionLog("=== Governance Warning ===", LogLevel.WARN);
      context.getLogCallback().saveExecutionLog(FmeGovernanceFormatter.formatForLog(governance), LogLevel.WARN);
    }
  }

  /**
   * Handles 404 responses based on configured behavior.
   */
  private <T> T handleNotFound(ExecutionContext context, NotFoundBehavior behavior, String errorBody) {
    switch (behavior) {
      case THROW_FLAG_NOT_FOUND:
        logNotFound(context, "Flag", errorBody);
        throw new FmeFeatureFlagNotFoundException(context.getFlagName(), context.getEnvironment());

      case THROW_DEFINITION_NOT_FOUND:
        logNotFound(context, "Definition", errorBody);
        throw new FmeFeatureFlagDefinitionNotFoundException(context.getFlagName(), context.getEnvironment());

      case THROW_SEGMENT_NOT_FOUND:
        context.getLogCallback().saveExecutionLog(
            format("Segment '%s' not found (404). API response: %s", context.getFlagName(), errorBody), LogLevel.ERROR);
        throw new FmeSegmentNotFoundException(context.getFlagName());

      case THROW_FLAGSET_NOT_FOUND:
        context.getLogCallback().saveExecutionLog(
            format("Flagset '%s' not found (404). API response: %s", context.getFlagName(), errorBody), LogLevel.ERROR);
        throw new FmeFlagsetNotFoundException(context.getFlagName());

      case RETURN_NULL:
      default:
        context.getLogCallback().saveExecutionLog(
            format("No existing %s found for flag '%s'%s, will use default", context.getOperationName(),
                context.getFlagName(), formatEnvironmentSuffix(context.getEnvironment())),
            LogLevel.INFO);
        return null;
    }
  }

  /**
   * Extracts FmeGovernanceResult from the details field which can be either
   * a JSON String or an already-parsed object (Map).
   */
  private FmeGovernanceResult extractGovernanceResult(Object details) {
    if (details == null) {
      return null;
    }

    try {
      if (details instanceof String) {
        // Details is a JSON string, parse it
        return JsonUtils.asObject((String) details, FmeGovernanceResult.class);
      } else {
        // Details is already parsed (likely a Map), convert to JSON then parse
        return JsonUtils.asObject(JsonUtils.asJson(details), FmeGovernanceResult.class);
      }
    } catch (Exception e) {
      log.warn("Failed to extract governance result from details: {}", e.getMessage());
      return null;
    }
  }

  /**
   * Extracts error body from response, handling IOExceptions gracefully.
   */
  private String extractErrorBody(Response<?> response) {
    try (ResponseBody body = response.errorBody()) {
      if (body != null) {
        return body.string();
      }
    } catch (IOException e) {
      return "Failed to read error response: " + e.getMessage();
    }
    return "Unknown error";
  }

  private void logSuccess(ExecutionContext context) {
    context.getLogCallback().saveExecutionLog(
        format("%s for flag '%s'%s completed successfully", capitalize(context.getOperationName()),
            context.getFlagName(), formatEnvironmentSuffix(context.getEnvironment())),
        LogLevel.INFO);
  }

  private void logError(ExecutionContext context, String errorDetails) {
    context.getLogCallback().saveExecutionLog(
        format("Error in %s for flag '%s'%s: %s", context.getOperationName(), context.getFlagName(),
            formatEnvironmentSuffix(context.getEnvironment()), errorDetails),
        LogLevel.ERROR);
  }

  private void logNotFound(ExecutionContext context, String entityType, String errorBody) {
    context.getLogCallback().saveExecutionLog(
        format("%s not found for flag '%s'%s (404). API response: %s", entityType, context.getFlagName(),
            formatEnvironmentSuffix(context.getEnvironment()), errorBody),
        LogLevel.ERROR);
  }

  private String formatEnvironmentSuffix(String environment) {
    return environment != null ? format(" in environment '%s'", environment) : "";
  }

  private String capitalize(String s) {
    if (s == null || s.isEmpty()) {
      return s;
    }
    return Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }
}
