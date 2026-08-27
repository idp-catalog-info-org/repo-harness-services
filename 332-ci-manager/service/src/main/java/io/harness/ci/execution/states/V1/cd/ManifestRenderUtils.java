/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.states.V1.cd;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.SPACE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.encoding.EncodingUtils;
import io.harness.delegate.HarnessSecret;
import io.harness.delegate.beans.ci.vm.VmTaskExecutionResponse;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.runner.request.helpers.RunnerRequestBuilderHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import org.apache.commons.text.StringEscapeUtils;

@UtilityClass
@OwnedBy(HarnessTeam.CI)
public class ManifestRenderUtils {
  public static final Pattern SECRET_PATTERN =
      Pattern.compile("\\$\\{ngSecretManager\\.obtain\\(\"([^\"]+)\", -?\\d+\\)\\}");
  public static final String NEW_LINE_ENCODED_STRING = "Cg--";

  // Matches a doubled double-quote pair ("") and captures the content between the opening and closing pair on the
  // same line, allowing leading/trailing horizontal whitespace to be trimmed. e.g. ""   world   "" -> ""world""
  public static final Pattern DOUBLE_QUOTE_CONTENT_PATTERN = Pattern.compile("\"\"[ \\t]*(.*?)[ \\t]*\"\"");

  // Matches a doubled double-quote pair ("") only when it is adjacent to a non-whitespace character either before or
  // after it. Whitespace neighbors are ignored (interior whitespace is already trimmed by
  // DOUBLE_QUOTE_CONTENT_PATTERN), so a standalone "" or one surrounded only by whitespace (e.g. an empty value) is
  // intentionally left untouched.
  public static final Pattern ADJACENT_DOUBLE_QUOTE_PATTERN = Pattern.compile("(?<=\\S)\"\"|\"\"(?=\\S)");

  public Map<String, String> getDecodedContents(
      Set<String> filePaths, Map<String, VmTaskExecutionResponse> vmTaskResponses) {
    if (isEmpty(filePaths) || isEmpty(vmTaskResponses)) {
      return Collections.emptyMap();
    }

    Map<String, String> contentsToRender = new HashMap<>();
    for (Map.Entry<String, VmTaskExecutionResponse> responseDataEntry : vmTaskResponses.entrySet()) {
      VmTaskExecutionResponse vmTaskExecutionResponse = responseDataEntry.getValue();
      Map<String, String> decodedOverrideFilesContent =
          getDecodedFilesContentsFromResponse(filePaths, vmTaskExecutionResponse);
      if (isNotEmpty(decodedOverrideFilesContent)) {
        contentsToRender.putAll(decodedOverrideFilesContent);
      }
    }

    return contentsToRender;
  }

  public Map<String, String> getContentsOfFileToRender(
      List<String> filesToRender, Map<String, VmTaskExecutionResponse> vmTaskResponses, String defaultValuesYaml) {
    if (isEmpty(filesToRender) || isEmpty(vmTaskResponses)) {
      return Collections.emptyMap();
    }

    Map<String, String> filesContentsToRender = new HashMap<>();
    for (Map.Entry<String, VmTaskExecutionResponse> responseDataEntry : vmTaskResponses.entrySet()) {
      VmTaskExecutionResponse vmTaskExecutionResponse = responseDataEntry.getValue();
      Map<String, String> overridesFilesContent =
          getFileContentsFromResponse(filesToRender, vmTaskExecutionResponse, defaultValuesYaml);
      if (isNotEmpty(overridesFilesContent)) {
        filesContentsToRender.putAll(overridesFilesContent);
      }
    }

    return filesContentsToRender;
  }

  public boolean isDefaultValuesYamlPresent(
      String defaultValuesYaml, Map<String, VmTaskExecutionResponse> vmTaskResponses) {
    if (isEmpty(defaultValuesYaml) || isEmpty(vmTaskResponses)) {
      return false;
    }

    for (Map.Entry<String, VmTaskExecutionResponse> responseDataEntry : vmTaskResponses.entrySet()) {
      VmTaskExecutionResponse vmTaskExecutionResponse = responseDataEntry.getValue();
      Map<String, String> outputVars = vmTaskExecutionResponse.getOutputVars();
      if (isNotEmpty(outputVars) && outputVars.containsKey(defaultValuesYaml)) {
        String content = outputVars.get(defaultValuesYaml);
        if (!NEW_LINE_ENCODED_STRING.equals(content)) {
          return true;
        }
      }
    }

    return false;
  }

  private Map<String, String> getFileContentsFromResponse(
      List<String> filePath, VmTaskExecutionResponse vmTaskExecutionResponse, String defaultValuesYaml) {
    Map<String, String> fileContents = new HashMap<>();
    Map<String, String> outputVars = vmTaskExecutionResponse.getOutputVars();
    if (isNotEmpty(outputVars)) {
      filePath.forEach(path -> {
        if (outputVars.containsKey(path)) {
          String content = outputVars.get(path);
          // Todo: Plugin Team need to fix this in git clone plugin
          if (!NEW_LINE_ENCODED_STRING.equals(content)) {
            fileContents.put(path, content);
          }
        }
      });
    }
    return fileContents;
  }

  public Map<String, String> replaceSecretsRunnerCompatible(Map<String, String> renderedFilesContentMap) {
    Map<String, String> replacedSecretsRenderedFilesContentMap = new HashMap<>();
    if (isNotEmpty(renderedFilesContentMap)) {
      renderedFilesContentMap.forEach((path, content) -> {
        String renderedContentForRunner = replaceSecretsRunnerCompatible(content).replace("\n", "\\n");
        replacedSecretsRenderedFilesContentMap.put(path, renderedContentForRunner);
      });
    }
    return replacedSecretsRenderedFilesContentMap;
  }

  private String replaceSecretsRunnerCompatible(String input) {
    Matcher matcher = SECRET_PATTERN.matcher(input);
    StringBuilder result = new StringBuilder();
    while (matcher.find()) {
      String secretName = matcher.group(1);
      String replacement = String.format("\\$\\{\\{secrets.%s\\}\\}", secretName);
      matcher.appendReplacement(result, replacement);
    }
    matcher.appendTail(result);
    return result.toString();
  }

  private Map<String, String> getDecodedFilesContentsFromResponse(
      Set<String> filePaths, VmTaskExecutionResponse vmTaskExecutionResponse) {
    Map<String, String> overridesDecoded = new HashMap<>();
    Map<String, String> outputVars = vmTaskExecutionResponse.getOutputVars();
    if (isNotEmpty(outputVars)) {
      RenderingStepUtils.sanitizeOutputVars(outputVars);
      filePaths.forEach(path -> {
        if (outputVars.containsKey(path)) {
          String fileContent = outputVars.get(path).replace("-", "=").replace(SPACE, EMPTY);
          String decodedFileContent = EncodingUtils.decodeBase64ToString(fileContent);
          overridesDecoded.put(path, decodedFileContent);
        }
      });
    }
    return overridesDecoded;
  }

  public Map<String, String> getDecodedFilesContent(Map<String, String> filesContent) {
    Map<String, String> filesContentDecoded = new HashMap<>();
    if (isNotEmpty(filesContent)) {
      filesContent.forEach((path, content) -> {
        String fileContent = content.replace("-", "=").replace(SPACE, EMPTY);
        String decodedFileContent = EncodingUtils.decodeBase64ToString(fileContent);
        filesContentDecoded.put(path, decodedFileContent);
      });
    }
    return filesContentDecoded;
  }

  public static Map<String, String> unescapeJavaStringsInMap(
      Map<String, String> replacedSecretsRenderedFilesContentMap) {
    if (replacedSecretsRenderedFilesContentMap != null) {
      Map<String, String> encodedFileContent = new HashMap<>();
      for (Map.Entry<String, String> entry : replacedSecretsRenderedFilesContentMap.entrySet()) {
        encodedFileContent.put(entry.getKey(), StringEscapeUtils.unescapeJava(entry.getValue()));
      }
      return encodedFileContent;
    }

    return Collections.emptyMap();
  }

  /**
   * Trims leading and trailing horizontal whitespace of the content wrapped inside a doubled double-quote pair
   * ({@code ""}) on the same line. e.g. {@code ""   world   ""} becomes {@code ""world""}.
   */
  public Map<String, String> trimWhitespaceInsideDoubleQuotesInMap(Map<String, String> content) {
    if (isNotEmpty(content)) {
      Map<String, String> updatedMap = new HashMap<>();
      for (Map.Entry<String, String> entry : content.entrySet()) {
        updatedMap.put(entry.getKey(), trimWhitespaceInsideDoubleQuotes(entry.getValue()));
      }
      return updatedMap;
    }
    return Collections.emptyMap();
  }

  private String trimWhitespaceInsideDoubleQuotes(String content) {
    if (content == null) {
      return null;
    }
    Matcher matcher = DOUBLE_QUOTE_CONTENT_PATTERN.matcher(content);
    StringBuilder result = new StringBuilder();
    while (matcher.find()) {
      matcher.appendReplacement(result, Matcher.quoteReplacement("\"\"" + matcher.group(1) + "\"\""));
    }
    matcher.appendTail(result);
    return result.toString();
  }

  /**
   * Collapses a doubled double-quote pair ({@code ""}) into a single double-quote ({@code "}) only when it is adjacent
   * to a non-whitespace character (not restricted to alphabets) either before or after it. Whitespace neighbors are
   * ignored — interior whitespace is already trimmed by {@link #trimWhitespaceInsideDoubleQuotesInMap} — so a
   * standalone {@code ""} or one surrounded only by whitespace (such as an empty value) is left untouched.
   */
  public Map<String, String> replaceDoubleQuotesInMap(Map<String, String> content) {
    if (isNotEmpty(content)) {
      Map<String, String> updatedMap = new HashMap<>();
      for (Map.Entry<String, String> entry : content.entrySet()) {
        updatedMap.put(entry.getKey(), replaceAdjacentDoubleQuotes(entry.getValue()));
      }
      return updatedMap;
    }
    return Collections.emptyMap();
  }

  private String replaceAdjacentDoubleQuotes(String content) {
    if (content == null) {
      return null;
    }
    return ADJACENT_DOUBLE_QUOTE_PATTERN.matcher(content).replaceAll("\"");
  }

  public List<HarnessSecret> getHarnessSecrets(Ambiance ambiance, Map<String, String> renderedFilesContentMap) {
    List<HarnessSecret> collectedSecrets = new ArrayList<>();
    renderedFilesContentMap.values().forEach(content -> {
      List<HarnessSecret> secrets =
          RunnerRequestBuilderHelper.updateSecretExprAndGetSecrets(ambiance, content, new HashSet<>());
      if (isNotEmpty(secrets)) {
        collectedSecrets.addAll(secrets);
      }
    });
    return collectedSecrets;
  }
}
