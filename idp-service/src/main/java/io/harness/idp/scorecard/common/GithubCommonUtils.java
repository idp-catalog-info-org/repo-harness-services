/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.common;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.common.CommonUtils;

import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.IDP)
public class GithubCommonUtils {
  public String fetchErrorMessageFromGraphQLResponse(Map<String, Object> data) {
    StringBuilder errorMessage = new StringBuilder();
    List<Map<String, Object>> errors = (List<Map<String, Object>>) CommonUtils.findObjectByName(data, "errors");
    if (!isEmpty(errors)) {
      for (Map<String, Object> error : errors) {
        errorMessage.append(error.get("message"));
      }
    }
    return errorMessage.toString();
  }

  public String constructGraphQLUrl(String host) {
    return (host.equals("github.com") ? String.format("https://api.%s", host) : String.format("https://%s/api", host))
        + "/graphql";
  }
}
