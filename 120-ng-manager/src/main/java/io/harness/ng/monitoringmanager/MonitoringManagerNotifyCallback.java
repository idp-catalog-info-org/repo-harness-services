/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.monitoringmanager;

import io.harness.monitoringmanager.client.beans.MonitoringManagerApplyManifestResponse;
import io.harness.monitoringmanager.client.beans.MonitoringManagerApplyManifestResponse.MonitoringManagerApplyManifestResponseBuilder;
import io.harness.monitoringmanager.client.remote.MonitoringManagerHttpClient;
import io.harness.remote.client.NGRestUtils;
import io.harness.tasks.ResponseData;
import io.harness.waiter.NotifyCallbackWithErrorHandling;

import com.google.inject.Inject;
import java.util.Map;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MonitoringManagerNotifyCallback implements NotifyCallbackWithErrorHandling {
  @Inject MonitoringManagerHttpClient monitoringManagerHttpClient;

  String maUuid;

  public MonitoringManagerNotifyCallback(String maUuid) {
    this.maUuid = maUuid;
  }

  @Override
  public void notify(Map<String, Supplier<ResponseData>> response) {
    MonitoringManagerApplyManifestResponse applyManifestResponse = buildResponse(response);

    try {
      Boolean isSuccessful =
          NGRestUtils.getResponse(monitoringManagerHttpClient.pushTaskResponse(applyManifestResponse));
      if (Boolean.TRUE.equals(isSuccessful)) {
        log.info("Monitoring Manager informed successfully");
      } else {
        log.error("Monitoring Manager not informed");
      }
    } catch (Exception ex) {
      log.error("Monitoring Manager not informed", ex);
    }
  }

  private MonitoringManagerApplyManifestResponse buildResponse(Map<String, Supplier<ResponseData>> response) {
    String taskId = response.keySet().iterator().next();
    MonitoringManagerApplyManifestResponseBuilder responseBuilder =
        MonitoringManagerApplyManifestResponse.builder().taskId(taskId);
    try {
      responseBuilder = responseBuilder.status("SUCCESS");
      log.info("Monitoring Manager callback triggered with success response");
    } catch (Exception e) {
      log.error("Monitoring Manager callback triggered with error response", e);
      responseBuilder = responseBuilder.status("FAILED");
    }
    return responseBuilder.build();
  }
}