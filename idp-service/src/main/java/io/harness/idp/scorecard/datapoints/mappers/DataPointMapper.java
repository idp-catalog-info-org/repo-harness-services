/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.scorecard.datapoints.mappers;

import static io.harness.idp.common.Constants.GLOBAL_ACCOUNT_ID;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.spec.server.idp.v1.model.DataPoint;
import io.harness.spec.server.idp.v1.model.DataPointDetails;

import lombok.experimental.UtilityClass;
import org.bson.types.ObjectId;

@OwnedBy(HarnessTeam.IDP)
@UtilityClass
public class DataPointMapper {
  public DataPointEntity fromDto(DataPointDetails dataPointDetails, String dataSourceIdentifier) {
    return DataPointEntity.builder()
        .accountIdentifier(GLOBAL_ACCOUNT_ID)
        .identifier(dataPointDetails.getIdentifier())
        .name(dataPointDetails.getName())
        .type(DataPointEntity.Type.valueOf(dataPointDetails.getType().name()))
        .description(dataPointDetails.getDescription())
        .detailedDescription(dataPointDetails.getDetailedDescription())
        .isConditional(Boolean.TRUE.equals(dataPointDetails.isIsConditional()))
        .conditionalInputValueDescription(dataPointDetails.getConditionalInputValueDescription())
        .inputDetails(dataPointDetails.getInputDetails())
        .dataSourceLocationIdentifier(dataPointDetails.getDataSourceLocationIdentifier())
        .dataSourceIdentifier(dataSourceIdentifier)
        .outcomeExpression(dataPointDetails.getOutcomeExpression())
        .build();
  }

  public void updateEntity(DataPointEntity dataPointEntity, DataPointDetails dataPointDetails) {
    dataPointEntity.setName(dataPointDetails.getName());
    dataPointEntity.setType(DataPointEntity.Type.valueOf(dataPointDetails.getType().name()));
    dataPointEntity.setDescription(dataPointDetails.getDescription());
    dataPointEntity.setDetailedDescription(dataPointDetails.getDetailedDescription());
    dataPointEntity.setConditional(Boolean.TRUE.equals(dataPointDetails.isIsConditional()));
    dataPointEntity.setConditionalInputValueDescription(dataPointDetails.getConditionalInputValueDescription());
    dataPointEntity.setInputDetails(dataPointDetails.getInputDetails());
    dataPointEntity.setDataSourceLocationIdentifier(dataPointDetails.getDataSourceLocationIdentifier());
    dataPointEntity.setOutcomeExpression(dataPointDetails.getOutcomeExpression());
  }

  public DataPoint toDto(DataPointEntity dataPointEntity) {
    DataPoint dataPoint = new DataPoint();
    dataPoint.setName(dataPointEntity.getName());
    dataPoint.setDescription(dataPointEntity.getDescription());
    dataPoint.setType(dataPointEntity.getType().toString());
    dataPoint.setDataPointIdentifier(dataPointEntity.getIdentifier());
    dataPoint.setDetailedDescription(dataPointEntity.getDetailedDescription());
    dataPoint.setInputDetails(dataPointEntity.getInputDetails());
    return dataPoint;
  }
}
