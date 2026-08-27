/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasourcelocations.locations.scm.bitbucket;

import static io.harness.idp.common.Constants.DSL_RESPONSE;
import static io.harness.idp.common.Constants.ERROR_MESSAGE_KEY;
import static io.harness.idp.common.Constants.MESSAGE_KEY;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.RATE_LIMIT_EXCEEDED;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ResponseMessage;
import io.harness.idp.common.GsonUtils;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datasourcelocations.locations.scm.ScmBaseDslNoLoop;
import io.harness.spec.server.idp.v1.model.InputValue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.Response;

@OwnedBy(HarnessTeam.IDP)
public abstract class BitbucketBaseDsl extends ScmBaseDslNoLoop {
  @Override
  public String replaceInputValuePlaceholdersIfAnyInRequestBody(
      String requestBody, DataPointEntity dataPoint, List<InputValue> inputValues, Object entity) {
    return requestBody;
  }

  @Override
  protected Map<String, Object> processResponse(Response response) {
    Map<String, Object> ruleData = new HashMap<>();
    if (response.getStatus() == 200) {
      ruleData.put(DSL_RESPONSE, GsonUtils.convertJsonStringToObject(response.getEntity().toString(), Map.class));
    } else if (response.getStatus() == 429) {
      ruleData.put(ERROR_MESSAGE_KEY, RATE_LIMIT_EXCEEDED);
    } else if (response.getStatus() == 500) {
      ruleData.put(ERROR_MESSAGE_KEY, ((ResponseMessage) response.getEntity()).getMessage());
    } else {
      Map<String, Object> error =
          (Map<String, Object>) GsonUtils.convertJsonStringToObject(response.getEntity().toString(), Map.class)
              .get("error");
      ruleData.put(ERROR_MESSAGE_KEY, error.get(MESSAGE_KEY));
    }
    return ruleData;
  }
}
