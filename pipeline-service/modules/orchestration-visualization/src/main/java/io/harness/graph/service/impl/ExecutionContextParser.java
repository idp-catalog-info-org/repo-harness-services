/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.graph.service.impl;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.ambiance.ExecutionContext;

import java.util.Optional;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for parsing ExecutionContext protobuf from MongoDB CDC formats.
 * Delegates binary extraction to {@link ProtobufBinaryParser}.
 */
@UtilityClass
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class ExecutionContextParser {
  @Value
  @Builder
  public static class ExecutionContextResult {
    String accountId;
    ExecutionContext executionContext;

    public boolean hasAccountId() {
      return accountId != null && !accountId.isEmpty();
    }

    public boolean hasExecutionContext() {
      return executionContext != null;
    }
  }

  /**
   * Parse an ExecutionContext object from CDC event.
   */
  public static Optional<ExecutionContextResult> parse(Object executionContextObj) {
    if (executionContextObj == null) {
      return Optional.empty();
    }

    try {
      Optional<ExecutionContext> parsed =
          ProtobufBinaryParser.parseToObject(executionContextObj, ExecutionContext::parseFrom);
      if (parsed.isPresent()) {
        ExecutionContext ec = parsed.get();
        String accountId = null;
        if (ec.getSetupAbstractionsMap().containsKey("accountId")) {
          accountId = ec.getSetupAbstractionsMap().get("accountId");
        }
        return Optional.of(ExecutionContextResult.builder().accountId(accountId).executionContext(ec).build());
      }

      return Optional.empty();
    } catch (Exception e) {
      log.warn("[EXECUTION-CONTEXT-PARSER] Failed to parse ExecutionContext: {}", e.getMessage());
      return Optional.empty();
    }
  }
}
