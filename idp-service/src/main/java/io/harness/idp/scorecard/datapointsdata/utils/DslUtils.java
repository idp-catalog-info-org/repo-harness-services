/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.scorecard.datapointsdata.utils;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.common.Constants;
import io.harness.idp.scorecard.datapointsdata.dsldataprovider.DslConstants;
import io.harness.spec.server.idp.v1.model.DataPointInputValues;
import io.harness.spec.server.idp.v1.model.DataSourceDataPointInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

@OwnedBy(HarnessTeam.IDP)
@UtilityClass
@Slf4j
public class DslUtils {
  private static final String ciPipelineUrlNewAnnotationMissingError =
      "Please add new annotation for harness ci/cd plugin, harness.io/pipelines is missing in catalog yaml";

  public Map<String, String> getCiPipelineUrlIdentifiers(String ciPipelineUrl) {
    String[] splitText = ciPipelineUrl.split("/");
    Map<String, String> returnMap = new HashMap<>();
    returnMap.put(DslConstants.CI_ACCOUNT_IDENTIFIER_KEY, splitText[5]);
    if (splitText[6].equals("module")) {
      returnMap.put(DslConstants.CI_ORG_IDENTIFIER_KEY, splitText[9]);
      returnMap.put(DslConstants.CI_PROJECT_IDENTIFIER_KEY, splitText[11]);
      returnMap.put(DslConstants.CI_PIPELINE_IDENTIFIER_KEY, splitText[13]);
    } else {
      returnMap.put(DslConstants.CI_ORG_IDENTIFIER_KEY, splitText[8]);
      returnMap.put(DslConstants.CI_PROJECT_IDENTIFIER_KEY, splitText[10]);
      returnMap.put(DslConstants.CI_PIPELINE_IDENTIFIER_KEY, splitText[12]);
    }
    log.info("CI Identifiers - {}", returnMap);
    return returnMap;
  }

  public Map<String, String> getCdServiceUrlIdentifiers(String cdServiceUrl) {
    String[] splitText = cdServiceUrl.split("/");
    Map<String, String> returnMap = new HashMap<>();
    returnMap.put(DslConstants.CD_ACCOUNT_IDENTIFIER_KEY, splitText[5]);
    returnMap.put(DslConstants.CD_SERVICE_HOST, splitText[2]);

    log.info("Account - {} and String used - {}", splitText[5], cdServiceUrl);

    switch (splitText[6]) {
      case "all" -> {
        if (splitText[7].equals("orgs")) {
          returnMap.put(DslConstants.CD_ORG_IDENTIFIER_KEY, splitText[8]);
          returnMap.put(DslConstants.CD_PROJECT_IDENTIFIER_KEY, splitText[10]);
        } else {
          returnMap.put(DslConstants.CD_ORG_IDENTIFIER_KEY, splitText[9]);
          returnMap.put(DslConstants.CD_PROJECT_IDENTIFIER_KEY, splitText[11]);
        }
        returnMap.put(DslConstants.CD_SERVICE_IDENTIFIER_KEY, setCDServiceIdentifier(splitText[13]));
      }
      case "module" -> {
        returnMap.put(DslConstants.CD_ORG_IDENTIFIER_KEY, splitText[9]);
        returnMap.put(DslConstants.CD_PROJECT_IDENTIFIER_KEY, splitText[11]);
        if (splitText[12].equals("settings")) {
          returnMap.put(DslConstants.CD_SERVICE_IDENTIFIER_KEY, setCDServiceIdentifier(splitText[14]));
        } else {
          returnMap.put(DslConstants.CD_SERVICE_IDENTIFIER_KEY, setCDServiceIdentifier(splitText[13]));
        }
      }
      case "admin" -> returnMap.put(DslConstants.CD_SERVICE_IDENTIFIER_KEY, setCDServiceIdentifier(splitText[9]));
    }

    log.info("CD Identifiers - {}", returnMap);
    return returnMap;
  }

  private String setCDServiceIdentifier(String serviceIdentifier) {
    String[] splitForServiceId = serviceIdentifier.split("\\?");
    return splitForServiceId[0];
  }

  public String getCiUrlFromCatalogInfoYaml(String catalogInfoYaml) {
    Map<String, Object> annotationObject = getAnnotationsFromCatalogInfoYaml(catalogInfoYaml);
    String ciPipelineUrls = (String) annotationObject.get("harness.io/pipelines");
    if (ciPipelineUrls == null) {
      return null;
    }
    String[] ciPipelineUrlsAsList = ciPipelineUrls.split("\n");
    return ciPipelineUrlsAsList[0].split(":", 2)[1];
  }

  public String getServiceUrlFromCatalogInfoYaml(String catalogInfoYaml) {
    Map<String, Object> annotationObject = getAnnotationsFromCatalogInfoYaml(catalogInfoYaml);
    String serviceUrls = (String) annotationObject.get("harness.io/services");
    if (serviceUrls == null) {
      return null;
    }
    String[] serviceUrlsAsList = serviceUrls.split("\n");
    return serviceUrlsAsList[0].split(":", 2)[1];
  }

  public Map<String, Object> getAnnotationsFromCatalogInfoYaml(String catalogInfoYaml) {
    Yaml yaml = new Yaml();
    Map<String, Object> catalogInfoYamlObject = yaml.load(catalogInfoYaml);
    Map<String, Object> metadataObject = (Map<String, Object>) catalogInfoYamlObject.get("metadata");
    return (Map<String, Object>) metadataObject.get("annotations");
  }

  public Map<String, Object> checkAndGetMissingNewAnnotationErrorMessage(String ciPipelineUrl,
      Boolean isCiPipelineUrlNeeded,
      DataSourceDataPointInfo dataSourceDataPointInfo) {
    String errorMessage = "";
    if (isCiPipelineUrlNeeded && ciPipelineUrl == null) {
      errorMessage = errorMessage + ciPipelineUrlNewAnnotationMissingError + "\n";
    }

    if (errorMessage.isEmpty()) {
      return null;
    }
    List<DataPointInputValues> dataPointInputValuesList =
        dataSourceDataPointInfo.getDataSourceLocation().getDataPoints();
    Map<String, Object> returnedMap = new HashMap<>();

    Map<String, Object> errorMessageInfo = new HashMap<>();
    errorMessageInfo.put(Constants.DATA_POINT_VALUE_KEY, null);
    errorMessageInfo.put(Constants.ERROR_MESSAGE_KEY, errorMessage);

    for (DataPointInputValues dataPointInputValues : dataPointInputValuesList) {
          returnedMap.put(dataPointInputValues.getDataPointIdentifier(), errorMessageInfo);
        }
        return returnedMap;
    }
  }
