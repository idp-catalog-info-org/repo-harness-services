/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.logger;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.logging.AutoLogContext;

import java.util.HashMap;
import java.util.Map;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_DATA_RETENTION})
@OwnedBy(PIPELINE)
public class ExecutionRetentionLogContext extends AutoLogContext {
  public static final String MESSAGE_SCOPE = "messageScope";
  public static final String CONTEXT_KEY = "contextKey";

  public static final String ACCOUNT_IDENTIFIER = "accountIdentifier";

  public ExecutionRetentionLogContext(String messageScope) {
    super(setContextMap(messageScope, null), OverrideBehavior.OVERRIDE_NESTS);
  }

  public ExecutionRetentionLogContext(String messageScope, String accountIdentifier) {
    super(setContextMap(messageScope, accountIdentifier), OverrideBehavior.OVERRIDE_NESTS);
  }

  private static Map<String, String> setContextMap(String messageScope, String accountIdentifier) {
    Map<String, String> logContextMap = new HashMap<>();
    setContextIfNotNull(logContextMap, MESSAGE_SCOPE, messageScope);
    setContextIfNotNull(logContextMap, ACCOUNT_IDENTIFIER, accountIdentifier);
    return logContextMap;
  }

  private static void setContextIfNotNull(Map<String, String> logContextMap, String key, String value) {
    if (isNotEmpty(value)) {
      logContextMap.putIfAbsent(key, value);
    }
  }
}
