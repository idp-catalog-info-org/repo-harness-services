/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.helpers;

import io.harness.account.services.AccountClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pipeline.service.PipelineServiceConfiguration;
import io.harness.remote.client.CGRestUtils;
import io.harness.service.LogServiceUrlProvider;
import io.harness.utils.LogUrlBuilder;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineServiceLogServiceUrlProvider implements LogServiceUrlProvider {
  @Inject private PipelineServiceConfiguration pipelineServiceConfiguration;
  @Inject private AccountClient accountClient;

  @Override
  public String getLogServiceBaseUrl(String accountId) {
    String portalBase = pipelineServiceConfiguration.getPipelineServiceBaseUrl();
    String vanity = "";
    try {
      vanity = CGRestUtils.getResponse(accountClient.getVanityUrl(accountId));
      if (vanity == null) {
        vanity = "";
      }
    } catch (Exception e) {
      log.warn("Failed to fetch vanity URL for account {}", accountId, e);
      vanity = "";
    }
    return LogUrlBuilder.buildExternalLogBaseUrl(portalBase, vanity);
  }
}
