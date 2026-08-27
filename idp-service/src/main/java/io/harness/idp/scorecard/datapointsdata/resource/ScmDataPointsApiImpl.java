/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapointsdata.resource;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ResponseMessage;
import io.harness.idp.scorecard.datapointsdata.service.DataPointDataValueService;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.idp.v1.ScmDataPointsApi;
import io.harness.spec.server.idp.v1.model.ScmConfig;
import io.harness.spec.server.idp.v1.model.ScmRequest;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import java.util.Map;
import javax.validation.Valid;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(onConstructor = @__({ @com.google.inject.Inject }))
@NextGenManagerAuth
@Slf4j
@Timed
@ResponseMetered
public class ScmDataPointsApiImpl implements ScmDataPointsApi {
  DataPointDataValueService dataPointDataValueService;
  @Override
  public Response getScmDataPointValues(@Valid ScmRequest body, String harnessAccount) {
    try {
      ScmConfig config = body.getRequest();
      String dataSourceIdentifier = config.getDataSourceLocation().getDataPoints().get(0).getDataSourceIdentifier();
      Map<String, Object> returnData =
          dataPointDataValueService.getDataPointDataValues(harnessAccount, dataSourceIdentifier, config);
      return Response.status(Response.Status.OK).entity(returnData).build();
    } catch (BadRequestException e) {
      log.error("Error in getting data from scm data source for account - {}", harnessAccount, e);
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    } catch (Exception e) {
      log.error("Error in getting data from scm data source for account - {}", harnessAccount, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }
}
