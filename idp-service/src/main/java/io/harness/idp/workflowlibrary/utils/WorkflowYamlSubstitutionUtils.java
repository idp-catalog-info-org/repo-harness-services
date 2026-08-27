/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.workflowlibrary.utils;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.IDP)
public class WorkflowYamlSubstitutionUtils {
  private static final Pattern SYMBOLIC_REF_PATTERN = Pattern.compile("OOTB_PIPELINE_REF:([\\w-]+)");
  private static final Pattern ADMIN_INPUT_PATTERN = Pattern.compile("OOTB_ADMIN:([\\w]+)");

  public static String substituteSymbolicRefs(String yaml, Map<String, String> refToRealId) {
    if (yaml == null || refToRealId == null) {
      return yaml;
    }
    Matcher matcher = SYMBOLIC_REF_PATTERN.matcher(yaml);
    StringBuilder sb = new StringBuilder();
    while (matcher.find()) {
      String refId = matcher.group(1);
      String replacement = refToRealId.getOrDefault(refId, matcher.group(0));
      matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
    }
    matcher.appendTail(sb);
    return sb.toString();
  }

  public static String substituteAdminInputs(String yaml, Map<String, String> adminInputValues) {
    if (yaml == null || adminInputValues == null || adminInputValues.isEmpty()) {
      return yaml;
    }
    Matcher matcher = ADMIN_INPUT_PATTERN.matcher(yaml);
    StringBuilder sb = new StringBuilder();
    while (matcher.find()) {
      String key = matcher.group(1);
      String replacement = adminInputValues.getOrDefault(key, matcher.group(0));
      matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
    }
    matcher.appendTail(sb);
    return sb.toString();
  }

  public static String substituteScope(String yaml, String org, String project) {
    if (yaml == null) {
      return yaml;
    }
    String result = yaml;
    result = result.replace("{{orgIdentifier}}", org != null ? org : "");
    result = result.replace("{{projectIdentifier}}", project != null ? project : "");
    return result;
  }

  public static String injectScopeFields(String yaml, String orgIdentifier, String projectIdentifier) {
    if (yaml == null) {
      return yaml;
    }
    StringBuilder sb = new StringBuilder();
    boolean injected = false;
    for (String line : yaml.split("\n")) {
      sb.append(line).append('\n');
      if (!injected && line.startsWith("identifier:")) {
        if (orgIdentifier != null && !orgIdentifier.isEmpty()) {
          sb.append("orgIdentifier: ").append(orgIdentifier).append('\n');
        }
        if (projectIdentifier != null && !projectIdentifier.isEmpty()) {
          sb.append("projectIdentifier: ").append(projectIdentifier).append('\n');
        }
        injected = true;
      }
    }
    return sb.toString();
  }
}
