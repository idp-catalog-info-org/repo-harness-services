/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.common;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

import com.google.gson.Gson;
import org.json.JSONObject;

@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
public class GsonUtils {
  private GsonUtils() {}

  private static final Gson gson = new Gson();

  public static <T> T convertJsonStringToObject(String jsonString, Class<T> targetType) {
    return gson.fromJson(jsonString, targetType);
  }

  public static JSONObject getJSONObjectFromObject(Object object, String key) {
    String jsonInString = new Gson().toJson(object);
    JSONObject jsonObject = new JSONObject(jsonInString);
    return (JSONObject) jsonObject.get(key);
  }
}
