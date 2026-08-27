/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.idp.catalog.entities;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@OwnedBy(HarnessTeam.IDP)
public class ActionHttpConfig {
  private String method;
  private String path;
  private String url;
  private Map<String, String> queryParams;
  private Map<String, String> headers;
  private String body;
  @Builder.Default private Integer timeoutMs = 30000;
  private List<String> expectedStatusCodes;
  @Builder.Default private Boolean suppressResponseBody = false;
}
