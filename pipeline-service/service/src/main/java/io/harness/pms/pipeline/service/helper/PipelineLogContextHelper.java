/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service.helper;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;

import java.util.HashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(PIPELINE)
public class PipelineLogContextHelper {
  public static final String ACCOUNT_KEY = "accountIdentifier";
  public static final String PLAN_EXECUTION_KEY = "planExecutionIdentifier";
  public static final String NODE_EXECUTION_KEY = "nodeExecutionIdentifier";

  public static final String FILE_NAME = "fileName";

  public static Map<String, String> getContextMap(
      String accountIdentifier, String planExecutionId, String nodeExecutionId, String fileName) {
    Map<String, String> logContextMap = new HashMap<>();
    setContextIfNotNull(logContextMap, ACCOUNT_KEY, accountIdentifier);
    setContextIfNotNull(logContextMap, PLAN_EXECUTION_KEY, planExecutionId);
    setContextIfNotNull(logContextMap, NODE_EXECUTION_KEY, nodeExecutionId);
    setContextIfNotNull(logContextMap, FILE_NAME, fileName);
    return logContextMap;
  }

  private static void setContextIfNotNull(Map<String, String> logContextMap, String key, String value) {
    if (isNotEmpty(value)) {
      logContextMap.putIfAbsent(key, value);
    }
  }
}
