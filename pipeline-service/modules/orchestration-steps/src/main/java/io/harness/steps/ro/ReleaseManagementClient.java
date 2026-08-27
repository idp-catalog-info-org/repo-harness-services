/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.ro;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.ResponseDTO;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;

@OwnedBy(HarnessTeam.PIPELINE)
public interface ReleaseManagementClient {
  @POST("api/v1/webhook/signal")
  Call<ResponseDTO<Void>> notifyReleaseOrchestration(@Header("Harness-Account") String accountId,
      @Header("X-Idempotency-Key") String idempotencyKey, @Body RONotifyRequestBody request);
}
