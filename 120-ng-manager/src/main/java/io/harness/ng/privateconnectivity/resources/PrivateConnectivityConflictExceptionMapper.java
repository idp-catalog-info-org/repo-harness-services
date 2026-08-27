/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.privateconnectivity.resources;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ErrorCode;
import io.harness.ng.core.Status;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.privateconnectivity.services.PrivateConnectivityConflictException;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

@OwnedBy(CI)
public class PrivateConnectivityConflictExceptionMapper
    implements ExceptionMapper<PrivateConnectivityConflictException> {
  @Override
  public Response toResponse(PrivateConnectivityConflictException exception) {
    FailureDTO failureDTO = FailureDTO.toBody(Status.FAILURE, ErrorCode.INVALID_REQUEST, exception.getMessage(), null);
    return Response.status(Response.Status.CONFLICT).entity(failureDTO).type(MediaType.APPLICATION_JSON).build();
  }
}
