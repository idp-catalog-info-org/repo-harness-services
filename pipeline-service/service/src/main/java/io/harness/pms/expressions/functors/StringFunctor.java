/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * Functor exposing string utility helpers for expressions.
 * Usage example in pipeline expressions:
 *   <+string.escapeDoubleQuotes(value)>
 *   <+string.escapeJson(value)>
 */

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class StringFunctor {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  /**
   * Minimally escape a string for safe embedding inside a JSON string literal by escaping only
   * unescaped double quotes ("). Already-escaped quotes (\") are preserved and other characters
   * (including backslashes and control characters) are left untouched to avoid over-escaping.
   */
  public String escapeDoubleQuotes(String input) {
    if (input == null) {
      return null;
    }

    StringBuilder out = new StringBuilder(input.length());
    int consecutiveBackslashes = 0;
    for (int i = 0; i < input.length(); i++) {
      char c = input.charAt(i);
      if (c == '"') {
        // Quote is considered escaped if preceded by an odd number of backslashes
        boolean isEscaped = (consecutiveBackslashes % 2) == 1;
        if (!isEscaped) {
          out.append('\\');
        }
        out.append('"');
        consecutiveBackslashes = 0; // reset after handling quote
      } else {
        out.append(c);
        if (c == '\\') {
          consecutiveBackslashes++;
        } else {
          consecutiveBackslashes = 0;
        }
      }
    }
    return out.toString();
  }

  /**
   * Escapes a string for safe embedding inside a JSON string literal.
   * Returns null when input is null.
   */
  public String escapeJson(String input) {
    if (input == null) {
      return null;
    }
    try {
      // Jackson will produce a valid JSON string literal including surrounding quotes.
      // We remove the surrounding quotes to return the escaped content only.
      String quoted = MAPPER.writeValueAsString(input);
      if (quoted.length() >= 2 && quoted.charAt(0) == '"' && quoted.charAt(quoted.length() - 1) == '"') {
        return quoted.substring(1, quoted.length() - 1);
      }
      return input; // Fallback (should not happen)
    } catch (Exception e) {
      // In case of unexpected failure, return the original string to avoid breaking evaluation.
      log.warn("Failed to escape JSON string: {}", input, e);
      return input;
    }
  }
}
