/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasources.resources;

import static io.harness.idp.common.Constants.SUCCESS_RESPONSE;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.annotations.IdpServiceAuthIfHasApiKey;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.scorecard.datapoints.service.DataPointService;
import io.harness.idp.scorecard.datasourcelocations.service.DataSourceLocationService;
import io.harness.idp.scorecard.datasources.service.DataSourceService;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.idp.v1.DataSourceApi;
import io.harness.spec.server.idp.v1.model.DataPoint;
import io.harness.spec.server.idp.v1.model.DataPointDetailsRequest;
import io.harness.spec.server.idp.v1.model.DataPointsResponse;
import io.harness.spec.server.idp.v1.model.DataSource;
import io.harness.spec.server.idp.v1.model.DataSourceDataPointsMap;
import io.harness.spec.server.idp.v1.model.DataSourceDataPointsMapResponse;
import io.harness.spec.server.idp.v1.model.DataSourceLocationDetailsRequest;
import io.harness.spec.server.idp.v1.model.DataSourcesResponse;
import io.harness.spec.server.idp.v1.model.DefaultSaveResponse;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import java.util.List;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;

@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(onConstructor = @__({ @com.google.inject.Inject }))
@NextGenManagerAuth
@Slf4j
@Timed
@ResponseMetered
public class DataSourceApiImpl implements DataSourceApi {
  DataSourceService dataSourceService;
  DataPointService dataPointService;
  DataSourceLocationService dataSourceLocationService;
  IdpCommonService idpCommonService;

  @Override
  @IdpServiceAuthIfHasApiKey
  public Response createGlobalDataSourceLocation(String dataSource, DataSourceLocationDetailsRequest body) {
    idpCommonService.checkUserAuthorization();
    try {
      dataSourceLocationService.createGlobalDataSourceLocation(dataSource, body.getDataSourceLocationDetails());
      return Response.status(Response.Status.CREATED)
          .entity(new DefaultSaveResponse().status(SUCCESS_RESPONSE))
          .build();
    } catch (DuplicateKeyException | InvalidRequestException e) {
      log.info("Could not create global data source location for data source {}", dataSource, e);
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    } catch (Exception e) {
      log.error("Could not create global data source location for data source {}", dataSource, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  @IdpServiceAuthIfHasApiKey
  public Response updateGlobalDataSourceLocation(
      String dataSource, String dataSourceLocationId, DataSourceLocationDetailsRequest body) {
    idpCommonService.checkUserAuthorization();
    try {
      dataSourceLocationService.updateGlobalDataSourceLocation(
          dataSource, dataSourceLocationId, body.getDataSourceLocationDetails());
      return Response.status(Response.Status.OK).entity(new DefaultSaveResponse().status(SUCCESS_RESPONSE)).build();
    } catch (InvalidRequestException e) {
      log.info(
          "Could not update global data source location {} for data source {}", dataSourceLocationId, dataSource, e);
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    } catch (Exception e) {
      log.error(
          "Could not update global data source location {} for data source {}", dataSourceLocationId, dataSource, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  @IdpServiceAuthIfHasApiKey
  public Response createGlobalDataPoint(String dataSource, DataPointDetailsRequest body) {
    idpCommonService.checkUserAuthorization();
    try {
      dataPointService.createGlobalDataPoint(dataSource, body.getDataPointDetails());
      return Response.status(Response.Status.CREATED)
          .entity(new DefaultSaveResponse().status(SUCCESS_RESPONSE))
          .build();
    } catch (DuplicateKeyException | InvalidRequestException e) {
      log.info("Could not create global data point for data source {}", dataSource, e);
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    } catch (Exception e) {
      log.error("Could not create global data point for data source {}", dataSource, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  @IdpServiceAuthIfHasApiKey
  public Response updateGlobalDataPoint(String dataSource, String dataPointId, DataPointDetailsRequest body) {
    idpCommonService.checkUserAuthorization();
    try {
      dataPointService.updateGlobalDataPoint(dataSource, dataPointId, body.getDataPointDetails());
      return Response.status(Response.Status.OK).entity(new DefaultSaveResponse().status(SUCCESS_RESPONSE)).build();
    } catch (InvalidRequestException e) {
      log.info("Could not update global data point {} for data source {}", dataPointId, dataSource, e);
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    } catch (Exception e) {
      log.error("Could not update global data point {} for data source {}", dataPointId, dataSource, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  public Response getAllDatasourcesForAccount(@AccountIdentifier String harnessAccount) {
    try {
      List<DataSource> dataSources = dataSourceService.getAllDataSourcesDetailsForAnAccount(harnessAccount);
      DataSourcesResponse dataSourcesResponse = new DataSourcesResponse();
      dataSourcesResponse.setDataSources(dataSources);
      return Response.status(Response.Status.OK).entity(dataSourcesResponse).build();
    } catch (Exception e) {
      log.error("Error in getting data sources details for account - {}", harnessAccount);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  public Response getDataPointsForDataSource(String dataSource, @AccountIdentifier String harnessAccount) {
    try {
      List<DataPoint> dataPoints = dataSourceService.getAllDataPointsDetailsForDataSource(harnessAccount, dataSource);
      DataPointsResponse dataPointsResponse = new DataPointsResponse();
      dataPointsResponse.dataPoints(dataPoints);
      return Response.status(Response.Status.OK).entity(dataPointsResponse).build();
    } catch (Exception e) {
      log.error(
          "Error in getting data points details for account - {} and datasource - {}", harnessAccount, dataSource);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  public Response getDataSourcesDataPointsMap(@AccountIdentifier String harnessAccount) {
    try {
      List<DataSourceDataPointsMap> dataSourceDataPointsMaps =
          dataSourceService.getDataPointsForDataSources(harnessAccount);
      DataSourceDataPointsMapResponse dataSourceDataPointsMapResponse = new DataSourceDataPointsMapResponse();
      dataSourceDataPointsMapResponse.dataSourceDataPointsMap(dataSourceDataPointsMaps);
      return Response.status(Response.Status.OK).entity(dataSourceDataPointsMapResponse).build();
    } catch (Exception e) {
      log.error("Error in getting data source data points map for account - {}", harnessAccount);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }
}