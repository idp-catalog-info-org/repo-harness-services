/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.resource;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.pipeline.PipelineAnnotationsResource;
import io.harness.pms.pipeline.annotations.PipelineAnnotationsService;
import io.harness.security.annotations.AnnotationsAuth;

import com.google.inject.Inject;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CI)
@AnnotationsAuth
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class PipelineAnnotationsResourceImpl implements PipelineAnnotationsResource {
  private final PipelineAnnotationsService pipelineAnnotationsService;

  @Override
  public ResponseDTO<io.harness.pms.annotations.CreateAnnotationsResponse> createPipelineExecutionAnnotations(
      String accountId, String planExecutionId, io.harness.pms.annotations.CreateAnnotationsRequest request) {
    log.info("Creating/updating annotations for planExecutionId: {}, account: {}", planExecutionId, accountId);

    io.harness.pms.annotations.CreateAnnotationsResponse response =
        pipelineAnnotationsService.createAnnotations(planExecutionId, accountId, request);

    return ResponseDTO.newResponse(response);
  }
}
