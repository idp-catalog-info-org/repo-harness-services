/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapointsdata.dsldataprovider.utils;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.CommonUtils.removeLeadingSlash;
import static io.harness.idp.common.Constants.MESSAGE_KEY;
import static io.harness.idp.common.Constants.TEXT;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.RATE_LIMIT_EXCEEDED;
import static io.harness.idp.scorecard.datapoints.constants.Inputs.BRANCH_NAME;
import static io.harness.idp.scorecard.datapoints.constants.Inputs.FILE_PATH;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.ACCOUNT_IDENTIFIER;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.ORG_IDENTIFIER;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.PROJECT_IDENTIFIER;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.REPOSITORY_BRANCH;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.REPOSITORY_NAME;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.REPOSITORY_OWNER;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.REPOSITORY_SUB_FOLDER;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.common.CommonUtils;
import io.harness.idp.common.GsonUtils;
import io.harness.spec.server.idp.v1.model.InputValue;
import io.harness.spec.server.idp.v1.model.ScmConfig;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.core.Response;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
@UtilityClass
public class DslCommons {
  private static final String FILE_PATH_REPLACER = "{FILE_PATH_REPLACER}";

  public String replacePlaceholdersFromInputValues(String requestBody, List<InputValue> inputValues, String fileName) {
    Optional<InputValue> inputValueOpt =
        inputValues.stream().filter(inputValue -> inputValue.getKey().equals(FILE_PATH)).findFirst();
    if (inputValueOpt.isPresent()) {
      String inputValue = inputValueOpt.get().getValue();
      inputValue = inputValue.replace("\"", "");
      int lastSlash = inputValue.lastIndexOf("/");
      String path = (lastSlash != -1) ? removeLeadingSlash(inputValue.substring(0, lastSlash)) : "";
      if (!isEmpty(fileName)) {
        path = isEmpty(path) ? path : path + "/";
        requestBody = requestBody.replace(FILE_PATH_REPLACER, path + removeLeadingSlash(fileName));
      } else {
        requestBody = requestBody.replace(FILE_PATH_REPLACER, path);
      }
    }

    inputValueOpt = inputValues.stream().filter(inputValue -> inputValue.getKey().equals(BRANCH_NAME)).findFirst();
    if (inputValueOpt.isPresent()) {
      String inputValue = inputValueOpt.get().getValue();
      inputValue = inputValue.replace("\"", "");
      if (!inputValue.isEmpty()) {
        requestBody = requestBody.replace(REPOSITORY_BRANCH, inputValue);
      }
    }
    return requestBody;
  }

  public String replacePlaceholdersFromSourceLocationAnnotation(String urlOrRequestBody, ScmConfig scmConfig) {
    if (scmConfig.getRepoOwner() != null) {
      urlOrRequestBody = urlOrRequestBody.replace(REPOSITORY_OWNER, scmConfig.getRepoOwner());
    }
    urlOrRequestBody = urlOrRequestBody.replace(REPOSITORY_NAME, scmConfig.getRepoName());
    if (scmConfig.getAccountIdentifier() != null) {
      urlOrRequestBody = urlOrRequestBody.replace(ACCOUNT_IDENTIFIER, scmConfig.getAccountIdentifier());
    }
    if (scmConfig.getOrgIdentifier() != null) {
      urlOrRequestBody = urlOrRequestBody.replace(ORG_IDENTIFIER, scmConfig.getOrgIdentifier());
    }
    if (scmConfig.getProjectIdentifier() != null) {
      urlOrRequestBody = urlOrRequestBody.replace(PROJECT_IDENTIFIER, scmConfig.getProjectIdentifier());
    }
    if (scmConfig.getRepoBranch() != null) {
      urlOrRequestBody = urlOrRequestBody.replace(REPOSITORY_BRANCH, scmConfig.getRepoBranch());
    }
    if (scmConfig.getRepoSubFolder() != null) {
      urlOrRequestBody = urlOrRequestBody.replace(REPOSITORY_SUB_FOLDER, scmConfig.getRepoSubFolder());
    }
    return urlOrRequestBody;
  }

  public String getBranchFromBitbucketResponse(Response response) {
    if (response.getStatus() == 200) {
      Map<String, Object> data = GsonUtils.convertJsonStringToObject(response.getEntity().toString(), Map.class);
      return (String) CommonUtils.findObjectByName(data, "name");
    } else {
      throwExceptionForBitbucket(response);
    }
    return null;
  }

  public Map<String, Object> throwExceptionForBitbucket(Response response) {
    if (response.getStatus() == 429) {
      throw new InvalidRequestException(RATE_LIMIT_EXCEEDED);
    } else if (response.getStatus() == 500) {
      throw new InvalidRequestException(((ResponseMessage) response.getEntity()).getMessage());
    } else {
      Map<String, Object> error =
          (Map<String, Object>) GsonUtils.convertJsonStringToObject(response.getEntity().toString(), Map.class)
              .get("error");
      throw new BadRequestException((String) error.get(MESSAGE_KEY));
    }
  }

  public Map<String, Object> processResponse(Response response) {
    if (response.getStatus() == 200) {
      return Map.of(TEXT, response.getEntity());
    }
    return throwExceptionForBitbucket(response);
  }
}
