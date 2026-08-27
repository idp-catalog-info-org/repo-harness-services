/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.iro;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.apache.commons.lang3.StringUtils;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@OwnedBy(HarnessTeam.PL)
public class HttpDelegateTaskRequest {
  @NotBlank(message = "HTTP method cannot be blank") String method;

  @NotBlank(message = "URL cannot be blank") String url;

  @Builder.Default Map<String, String> headers = new HashMap<>();

  @Builder.Default Map<String, String> queryParams = new HashMap<>();

  JsonNode body;

  List<String> delegateSelectors;

  @NotBlank(message = "delegate account ID cannot be blank") String delegateAccountId;

  String delegateOrgId;

  String delegateProjectId;

  @Builder.Default List<ExpectedOutput> expectedOutputs = new ArrayList<>();

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @FieldDefaults(level = AccessLevel.PRIVATE)
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class ExpectedOutput {
    String name;
    String jmesPathSelector;
  }

  public void validate() {
    if (StringUtils.isBlank(method)) {
      throw new IllegalArgumentException("HTTP method is required");
    }
    if (StringUtils.isBlank(url)) {
      throw new IllegalArgumentException("URL is required");
    }
    // Empty delegateSelectors means use any delegate with matching account/org/project
    if (StringUtils.isBlank(delegateAccountId)) {
      throw new IllegalArgumentException("delegateAccountId is required");
    }
  }
}