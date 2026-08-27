/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.iro;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class HttpDelegateTaskResponse {
  private int statusCode;
  private JsonNode body;
  private String errorMessage;

  public HttpDelegateTaskResponse(int statusCode, JsonNode body, String errorMessage) {
    this.statusCode = statusCode;
    this.body = body;
    this.errorMessage = errorMessage;
  }
}