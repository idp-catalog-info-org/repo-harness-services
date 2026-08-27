/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.proxy.environments.service;

import io.harness.spec.server.idp.v1.model.EnvironmentProxyCreateRequest;
import io.harness.spec.server.idp.v1.model.EnvironmentProxyResponse;
import io.harness.spec.server.idp.v1.model.EnvironmentProxyUpdateRequest;

public interface EnvironmentProxyService {
  EnvironmentProxyResponse createCompileAndExecuteEnvironment(EnvironmentProxyCreateRequest body,
      String accountIdentifier, String orgIdentifier, String projectIdentifier, Boolean dryRun);

  EnvironmentProxyResponse updateCompileAndExecuteEnvironment(String environmentId, EnvironmentProxyUpdateRequest body,
      String accountIdentifier, String orgIdentifier, String projectIdentifier) throws Exception;

  void deleteEnvironment(
      String environmentId, String accountIdentifier, String orgIdentifier, String projectIdentifier);
}
