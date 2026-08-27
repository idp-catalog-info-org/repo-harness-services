/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.iro;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ZoomDeAuthorizationResult {
  private final boolean success;
  private final String message;
  private final List<String> successfulConnectors;
  private final List<String> failedConnectors;

  public static ZoomDeAuthorizationResult success(String message) {
    return ZoomDeAuthorizationResult.builder()
        .success(true)
        .message(message)
        .successfulConnectors(new ArrayList<>())
        .failedConnectors(new ArrayList<>())
        .build();
  }

  public static ZoomDeAuthorizationResult failed(String message) {
    return ZoomDeAuthorizationResult.builder()
        .success(false)
        .message(message)
        .successfulConnectors(new ArrayList<>())
        .failedConnectors(new ArrayList<>())
        .build();
  }

  public static ZoomDeAuthorizationResult partialSuccess(String message, List<String> successful, List<String> failed) {
    return ZoomDeAuthorizationResult.builder()
        .success(!successful.isEmpty())
        .message(message)
        .successfulConnectors(successful)
        .failedConnectors(failed)
        .build();
  }

  /**
   * Response classes for API consistency
   */
  @Data
  @AllArgsConstructor
  public static class ErrorResponse {
    private String error;
    private String message;
  }

  @Data
  @AllArgsConstructor
  public static class SuccessResponse {
    private String message;
  }
}