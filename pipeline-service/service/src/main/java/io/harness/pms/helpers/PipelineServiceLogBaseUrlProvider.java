/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.helpers;

import io.harness.account.services.AccountClient;
import io.harness.pipeline.service.PipelineServiceConfiguration;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.remote.client.CGRestUtils;
import io.harness.steps.executable.LogBaseUrlProvider;
import io.harness.utils.LogUrlBuilder;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class PipelineServiceLogBaseUrlProvider implements LogBaseUrlProvider {
  @Inject PipelineServiceConfiguration pipelineServiceConfiguration;
  @Inject AccountClient accountClient;

  @Override
  public String getBaseUrl(Ambiance ambiance) {
    String portalBase = pipelineServiceConfiguration.getPipelineServiceBaseUrl();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String vanity = "";
    try {
      vanity = CGRestUtils.getResponse(accountClient.getVanityUrl(accountId));
      if (vanity == null) {
        vanity = "";
      }
    } catch (Exception ignore) {
      vanity = "";
    }
    return LogUrlBuilder.buildExternalLogBaseUrl(portalBase, vanity);
  }
}
