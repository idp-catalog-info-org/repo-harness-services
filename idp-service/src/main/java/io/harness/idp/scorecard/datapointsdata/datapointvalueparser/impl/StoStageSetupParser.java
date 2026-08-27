/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.scorecard.datapointsdata.datapointvalueparser.impl;

import io.harness.idp.scorecard.datapointsdata.datapointvalueparser.ValueParserConstants;
import io.harness.idp.scorecard.datapointsdata.datapointvalueparser.ValueParserUtils;
import io.harness.idp.scorecard.datapointsdata.datapointvalueparser.base.PipelineInfo;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;

@Slf4j
public class StoStageSetupParser implements PipelineInfo {
  private static final String STO_SCAN_STAGE_KEY = "\"nodeType\":\"SecurityTests\"";
  public Map<String, Object> getParsedValue(
      Object responseCI, Object responseCD, String dataPointIdentifier, String ciPipelineUrl, String cdPipelineUrl) {
    ArrayList<String> errorMessagePipelines = new ArrayList<>();

    Map<String, Object> map = new HashMap<>();

    boolean stoCheckForCiPipeline = false;
    if (responseCI != null) {
      stoCheckForCiPipeline = isSTOStageAddedForLatestPipelineExecution(responseCI);
      log.info("STO Stage added for CI Pipeline - {}, pipeline link - {}", stoCheckForCiPipeline, ciPipelineUrl);
      if (!stoCheckForCiPipeline) {
        errorMessagePipelines.add(ciPipelineUrl);
      }
    }

    boolean stoCheckForCdPipeline = true;
    if (responseCD != null) {
      stoCheckForCdPipeline = isSTOStageAddedForLatestPipelineExecution(responseCD);
      log.info("STO Stage added for CD Pipeline - {}, pipeline link - {}", stoCheckForCdPipeline, cdPipelineUrl);
      if (!stoCheckForCdPipeline) {
        errorMessagePipelines.add(cdPipelineUrl);
      }
    }

    Map<String, Object> dataPointInfo =
        ValueParserUtils.getDataPointsInfoMap(stoCheckForCiPipeline && stoCheckForCdPipeline, errorMessagePipelines);
    map.put(dataPointIdentifier, dataPointInfo);
    log.info("Harness Data Source -> StoStageSetupParser returned value {}", map);
    return map;
  }

  private boolean isSTOStageAddedForLatestPipelineExecution(Object response) {
    String jsonInString = new Gson().toJson(response);
    JSONObject listOfPipelineExecutions = new JSONObject(jsonInString);
    JSONArray pipelineExecutions = listOfPipelineExecutions.getJSONArray(ValueParserConstants.CONTENT_KEY);
    if (pipelineExecutions.length() > 0) {
      JSONObject latestPipelineExecution = pipelineExecutions.getJSONObject(0);
      log.info("result - {}", latestPipelineExecution.toString().contains(STO_SCAN_STAGE_KEY));
      return latestPipelineExecution.toString().contains(STO_SCAN_STAGE_KEY);
    }
    return false;
  }
}
