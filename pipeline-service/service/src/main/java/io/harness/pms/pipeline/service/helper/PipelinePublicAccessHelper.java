/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service.helper;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.accesscontrol.publicaccess.PublicAccessClient;
import io.harness.accesscontrol.publicaccess.dto.PublicAccessResponse;
import io.harness.accesscontrol.publicaccess.utils.PublicAccessClientUtils;
import io.harness.annotations.dev.OwnedBy;
import io.harness.spec.server.accesscontrol.v1.model.PublicAccessRequest;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import retrofit2.Response;

@Singleton
@Slf4j
@OwnedBy(PIPELINE)
public class PipelinePublicAccessHelper {
  @Inject private PublicAccessClient publicAccessClient;
  private final String PIPELINE = "PIPELINE";

  public PublicAccessResponse markPipelinePublic(
      String accountId, String orgId, String projectId, String pipelineIdentifier, boolean isPublic) {
    PublicAccessResponse existingPublicStatus =
        getPublicContextResponse(accountId, orgId, projectId, pipelineIdentifier);
    if (isPublic == existingPublicStatus.isPublic()) {
      return createPublicAccessResponse(isPublic, null);
    }

    PublicAccessRequest publicAccessRequest =
        PublicAccessClientUtils.getPublicAccessRequest(accountId, orgId, projectId, PIPELINE, pipelineIdentifier);
    try {
      Response<Boolean> isPublicResponse;
      if (isPublic) {
        isPublicResponse = publicAccessClient.enable(publicAccessRequest, accountId).execute();
      } else {
        isPublicResponse =
            publicAccessClient.disable(accountId, orgId, projectId, PIPELINE, pipelineIdentifier, accountId).execute();
      }
      if (isPublicResponse.body() == null) {
        return createPublicAccessResponse(existingPublicStatus.isPublic(), getErrorMessage(isPublicResponse));
      } else {
        return createPublicAccessResponse(isPublic ? isPublicResponse.body() : !isPublicResponse.body(), null);
      }
    } catch (Exception e) {
      log.error("There was an error while marking resource type [{}] with resource identifier [{}] as public.",
          PIPELINE, pipelineIdentifier, e);
      return createPublicAccessResponse(false, e.getMessage());
    }
  }

  public PublicAccessResponse createPublicAccessResponse(boolean isPublic, String error) {
    return PublicAccessResponse.builder().isPublic(isPublic).errorMessage(error).build();
  }

  public PublicAccessResponse getPublicContextResponse(
      String accountId, String orgId, String projectId, String pipelineId) {
    PublicAccessRequest publicAccessRequest =
        PublicAccessClientUtils.getPublicAccessRequest(accountId, orgId, projectId, PIPELINE, pipelineId);
    try {
      Response<Boolean> isPublic = publicAccessClient.isResourcePublic(publicAccessRequest, accountId).execute();
      return createPublicAccessResponse(Boolean.TRUE.equals(isPublic.body()), getErrorMessage(isPublic));
    } catch (Exception e) {
      log.error("There was an error while marking resource type [{}] with resource identifier [{}] as public.",
          PIPELINE, pipelineId, e);
      return createPublicAccessResponse(false, e.getMessage());
    }
  }

  public String getErrorMessage(Response<Boolean> isPublic) throws IOException {
    if (isPublic.errorBody() != null) {
      JSONObject jsonObject = new JSONObject(isPublic.errorBody().string());
      return jsonObject.getString("message");
    }
    return null;
  }
}
