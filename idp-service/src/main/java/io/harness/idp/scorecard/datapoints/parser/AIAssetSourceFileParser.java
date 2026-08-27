/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.parser;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;

import java.util.Map;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class AIAssetSourceFileParser extends AbstractAIAssetParser {
  private static final String SOURCE_FILE = "source_file";
  private static final String RELATIONSHIP = "relationship";

  private static final Map<String, Pattern> TYPE_PATTERNS =
      Map.of("skill", Pattern.compile(".*/skills/[^/]+/SKILL\\.md$"), "command",
          Pattern.compile(".*/commands/[^/]+\\.md$"), "agent", Pattern.compile(".*/agents/[^/]+\\.md$"));
  private static final Pattern PLUGIN_PATTERN = Pattern.compile(".*/plugin\\.json$");

  @Override
  protected Object evaluate(Map<String, Object> providerProperties, DataFetchDTO dataFetchDTO) {
    String sourceFile = (String) providerProperties.get(SOURCE_FILE);
    String type = (String) providerProperties.get(TYPE);
    String relationship = (String) providerProperties.get(RELATIONSHIP);

    if (sourceFile == null || type == null) {
      return buildResponse(dataFetchDTO, false, "source_file or type is missing in provider properties");
    }

    boolean matches = matchesExpectedPattern(sourceFile, type, relationship);
    return buildResponse(dataFetchDTO, matches, null);
  }

  @Override
  protected String getErrorMessage() {
    return "Failed to evaluate source file pattern";
  }

  private boolean matchesExpectedPattern(String sourceFile, String type, String relationship) {
    if ("plugin".equalsIgnoreCase(type) && "definition".equalsIgnoreCase(relationship)) {
      return PLUGIN_PATTERN.matcher(sourceFile).matches();
    }

    Pattern pattern = TYPE_PATTERNS.get(type.toLowerCase());
    if (pattern == null) {
      log.warn("Unknown AI asset type: {}", type);
      return false;
    }
    return pattern.matcher(sourceFile).matches();
  }
}
