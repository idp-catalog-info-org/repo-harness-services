/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.processor.api;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.google.inject.Singleton;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Wraps {@code swagger-parser-v3} to parse OpenAPI 3.x specs. All {@code $ref} resolution is
 * disabled ({@code setResolve(false)}) — otherwise a spec with {@code $ref: https://attacker/...}
 * would make HTTP calls bypassing {@link SpecFetcher}'s SSRF protections.
 */
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class OpenApiSpecParser {
  private static final ParseOptions PARSE_OPTIONS = buildParseOptions();

  public OpenAPI parse(String content) {
    if (content == null || content.isBlank()) {
      throw new OpenApiParseException("Spec content is empty.");
    }

    SwaggerParseResult result;
    try {
      result = new OpenAPIV3Parser().readContents(content, null, PARSE_OPTIONS);
    } catch (Exception ex) {
      throw new OpenApiParseException("Spec content could not be parsed as OpenAPI: " + ex.getMessage(), ex);
    }

    if (result == null || result.getOpenAPI() == null) {
      String details = describeMessages(result == null ? null : result.getMessages());
      throw new OpenApiParseException("Content is not a valid OpenAPI specification." + details);
    }

    List<String> messages = result.getMessages();
    if (messages != null && !messages.isEmpty()) {
      log.info("OpenAPI parser reported {} warning(s) while parsing: {}", messages.size(), messages);
    }

    return result.getOpenAPI();
  }

  private static ParseOptions buildParseOptions() {
    ParseOptions options = new ParseOptions();
    // SECURITY: master gate — blocks all $ref resolution incl. remote URL refs (SSRF egress).
    options.setResolve(false);
    return options;
  }

  private static String describeMessages(List<String> messages) {
    if (messages == null || messages.isEmpty()) {
      return "";
    }
    return " Parser messages: " + String.join("; ", messages);
  }
}
