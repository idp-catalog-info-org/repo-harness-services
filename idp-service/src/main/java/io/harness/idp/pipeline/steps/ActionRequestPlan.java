/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.idp.pipeline.steps;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.http.HttpHeaderConfig;

import java.util.List;
import java.util.Set;
import lombok.Builder;
import lombok.Value;

@OwnedBy(HarnessTeam.IDP)
@Value
@Builder
class ActionRequestPlan {
  String url;
  String method;
  String body;
  List<HttpHeaderConfig> headers;
  int timeoutMs;
  Set<String> delegateSelectors;
}
