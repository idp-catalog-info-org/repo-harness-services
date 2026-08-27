/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import static io.harness.NGCommonEntityConstants.FUNCTOR_BASE64_METHOD_NAME;
import static io.harness.NGCommonEntityConstants.FUNCTOR_STRING_METHOD_NAME;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.common.ParameterFieldHelper;
import io.harness.configFiles.ConfigFileOutcomeLite;
import io.harness.configFiles.ConfigGitFile;
import io.harness.data.encoding.EncodingUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.pms.data.OptionalOutcome;
import io.harness.engine.pms.data.outcome.PmsOutcomeService;
import io.harness.exception.EngineExpressionEvaluationException;
import io.harness.exception.EngineFunctorException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.expression.LateBindingMap;
import io.harness.expression.common.ExpressionMode;
import io.harness.expression.functors.ExpressionFunctor;
import io.harness.filestore.remote.FileStoreClient;
import io.harness.network.SafeHttpCall;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.utils.URLDecoderUtility;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.utils.FilePathUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_PIPELINE, HarnessModuleComponent.CDS_PIPELINE})
@AllArgsConstructor
@Slf4j
public class ConfigFileFunctorV2 extends LateBindingMap implements ExpressionFunctor {
  private static final String CONFIG_FILE_IDENTIFIER_REFERENCE_DELIMITER = ":";

  PmsOutcomeService outcomeService;
  FileStoreClient fileStoreClient;
  Ambiance ambiance;
  EngineExpressionEvaluator engineExpressionService;
  int depth;

  public Object getAsBase64(String configFileIdentifierWithReference) {
    return get(FUNCTOR_BASE64_METHOD_NAME, configFileIdentifierWithReference, null);
  }

  public Object getAsBase64(String configFileIdentifierWithReference, Integer configFilePathIndex) {
    return get(FUNCTOR_BASE64_METHOD_NAME, configFileIdentifierWithReference, configFilePathIndex);
  }

  public Object getAsString(String configFileIdentifierWithReference) {
    return get(FUNCTOR_STRING_METHOD_NAME, configFileIdentifierWithReference, null);
  }

  public Object getAsString(String configFileIdentifierWithReference, Integer configFilePathIndex) {
    return get(FUNCTOR_STRING_METHOD_NAME, configFileIdentifierWithReference, configFilePathIndex);
  }

  public Object getAsBase64Internal(String content) {
    if (EmptyPredicate.isEmpty(content)) {
      return null;
    }
    // We still need to support "${ngBase64Manager.encode(\"" + content + "\")}" because of backward compatibility.
    return EncodingUtils.encodeBase64(content);
  }

  public Object get(String methodName, String configFileIdentifierWithReference, Integer configFilePathIndex) {
    Pair<String, String> configFileIdentifierAndReference =
        getConfigFileIdentifierAndReference(configFileIdentifierWithReference);
    String configFileIdentifier = configFileIdentifierAndReference.getLeft();
    String reference = configFileIdentifierAndReference.getRight();

    ConfigFileOutcomeLite configFileOutcome = getConfigFileOutcome(ambiance, configFileIdentifier);
    return getConfigFileContent(ambiance, configFileOutcome, reference, methodName, configFilePathIndex);
  }

  private String getConfigFileContent(Ambiance ambiance, ConfigFileOutcomeLite configFileOutcome, String reference,
      String methodName, Integer configFilePathIndex) {
    String configFileIdentifier = configFileOutcome.getIdentifier();

    // If configFilePathIndex is null, use the first index to maintain consistency with the previous flow.
    int fileIndex = configFilePathIndex != null ? configFilePathIndex : 0;

    if (ParameterField.isNotNull(configFileOutcome.getFiles())
        || ParameterField.isNotNull(configFileOutcome.getSecretFiles())) {
      List<String> files = ParameterFieldHelper.getParameterFieldValue(configFileOutcome.getFiles());
      List<String> secretFiles = ParameterFieldHelper.getParameterFieldValue(configFileOutcome.getSecretFiles());
      validateHarnessStoreConfigFiles(configFileIdentifier, reference, files, secretFiles, configFilePathIndex);
      if (EmptyPredicate.isEmpty(reference)) {
        return isNotEmpty(files) ? getFileStoreFileContent(ambiance, methodName, files.get(fileIndex))
                                 : getSecretFileContent(ambiance, secretFiles.get(fileIndex), methodName);
      }

      return FilePathUtils.isScopedFilePath(reference) ? getFileStoreFileContent(ambiance, methodName, reference)
                                                       : getSecretFileContent(ambiance, reference, methodName);
    } else if (EmptyPredicate.isNotEmpty(configFileOutcome.getGitFiles())) {
      validateGitStoreConfigFiles(
          configFileOutcome.getIdentifier(), reference, configFileOutcome.getGitFiles(), configFilePathIndex);
      if (FUNCTOR_BASE64_METHOD_NAME.equals(methodName)) {
        return "<+configFile.getAsBase64Internal(<+configFile.getAsString('" + configFileIdentifier + "')>)>";
      }
      String gitFileContent =
          getGitFileContentOrThrow(configFileIdentifier, reference, configFileOutcome.getGitFiles(), fileIndex);
      return updateGitFileContentByMethodAndRenderExpressions(methodName, gitFileContent);
    } else {
      throw new InvalidRequestException(
          format("Invalid store kind for config file, configFileIdentifier: %s", configFileIdentifier));
    }
  }

  private String getSecretFileContent(Ambiance ambiance, final String secretRef, String methodName) {
    if (FUNCTOR_STRING_METHOD_NAME.equals(methodName)) {
      return getSecretFileContentAsString(ambiance, secretRef);
    } else if (FUNCTOR_BASE64_METHOD_NAME.equals(methodName)) {
      return getSecretFileContentAsBase64(ambiance, secretRef);
    } else {
      throw new InvalidArgumentsException(
          format("Unsupported configFile functor method: %s, secretRef: %s", methodName, secretRef));
    }
  }

  private String getSecretFileContentAsString(Ambiance ambiance, final String ref) {
    return "${ngSecretManager.obtainSecretFileAsString(\"" + ref + "\", " + ambiance.getExpressionFunctorToken() + ")}";
  }

  private String getSecretFileContentAsBase64(Ambiance ambiance, final String ref) {
    return "${ngSecretManager.obtainSecretFileAsBase64(\"" + ref + "\", " + ambiance.getExpressionFunctorToken() + ")}";
  }

  private void validateGitStoreConfigFiles(String configFileIdentifier, String reference,
      List<ConfigGitFile> configGitFileList, Integer configFilePathIndex) {
    if (EmptyPredicate.isEmpty(reference)) {
      validateGitStoreConfigFilesWithoutReference(configFileIdentifier, configGitFileList, configFilePathIndex);
    }
  }

  private void validateGitStoreConfigFilesWithoutReference(
      String configFileIdentifier, List<ConfigGitFile> configGitFileList, Integer configFilePathIndex) {
    if (isNotEmpty(configGitFileList) && configGitFileList.size() > 1 && configFilePathIndex == null) {
      throw new InvalidArgumentsException(
          format("Found more git files attached to config file, configFileIdentifier: %s", configFileIdentifier));
    }
    if (isNotEmpty(configGitFileList) && configFilePathIndex != null
        && (configGitFileList.size() <= configFilePathIndex || configFilePathIndex < 0)) {
      throw new InvalidArgumentsException(format(
          "Found config file Path reference out of bound for, configFileIdentifier: %s. Expected between 0 and %d. but found %d",
          configFileIdentifier, configGitFileList.size() - 1, configFilePathIndex));
    }
  }

  private String getGitFileContentOrThrow(
      String configFileIdentifier, String reference, List<ConfigGitFile> gitFiles, int fileIndex) {
    return EmptyPredicate.isEmpty(reference)
        ? gitFiles.get(fileIndex).getFileContent()
        : gitFiles.stream()
              .filter(configGitFile -> configGitFile != null && reference.equals(configGitFile.getFilePath()))
              .map(ConfigGitFile::getFileContent)
              .findFirst()
              .orElseThrow(()
                               -> new InvalidArgumentsException(
                                   format("Not found Git file with reference: [%s], configFileIdentifier: %s",
                                       reference, configFileIdentifier)));
  }

  private String updateGitFileContentByMethodAndRenderExpressions(final String methodName, String content) {
    if (FUNCTOR_STRING_METHOD_NAME.equals(methodName)) {
      try {
        String content1 =
            engineExpressionService.renderExpression(content, ExpressionMode.RETURN_NULL_IF_UNRESOLVED, depth--);
        // We are doing a depth+1 again because we want to check for loop but we want to allow many fileStore
        // resolutions in a given expression. This ensures that when a fileStore or configFile is called from inside a
        // fileStore or configFileFunctor, then only depth is reduced
        depth++;
        return content1;
      } catch (EngineExpressionEvaluationException e) {
        throw new EngineFunctorException(e);
      }
    }
    throw new InvalidArgumentsException(format("Unsupported configFile functor method: %s", methodName));
  }

  private void validateHarnessStoreConfigFiles(String configFileIdentifier, String reference, List<String> files,
      List<String> secretFiles, Integer configFilePathIndex) {
    if (EmptyPredicate.isEmpty(files) && EmptyPredicate.isEmpty(secretFiles)) {
      throw new InvalidArgumentsException(
          format("Not added Harness Store file or encrypted file to config file, configFileIdentifier: %s",
              configFileIdentifier));
    }

    if (EmptyPredicate.isEmpty(reference)) {
      validateHarnessStoreConfigFilesWithoutReference(configFileIdentifier, files, secretFiles, configFilePathIndex);
    }
  }

  private void validateHarnessStoreConfigFilesWithoutReference(
      String configFileIdentifier, List<String> files, List<String> secretFiles, Integer configFilePathIndex) {
    if (isNotEmpty(files) && isNotEmpty(secretFiles)) {
      throw new InvalidArgumentsException(
          format("Found file and encrypted file both attached to config file, configFileIdentifier: %s",
              configFileIdentifier));
    }

    if (isNotEmpty(files)) {
      validateFileList(files, configFileIdentifier, configFilePathIndex, "file");
    }

    if (isNotEmpty(secretFiles)) {
      validateFileList(secretFiles, configFileIdentifier, configFilePathIndex, "encrypted file");
    }
  }

  private void validateFileList(
      List<String> fileList, String configFileIdentifier, Integer configFilePathIndex, String fileType) {
    if (fileList.size() > 1 && configFilePathIndex == null) {
      throw new InvalidArgumentsException(
          format("Found more %ss attached to config file, configFileIdentifier: %s", fileType, configFileIdentifier));
    }

    if (configFilePathIndex != null) {
      if (configFilePathIndex < 0 || configFilePathIndex >= fileList.size()) {
        throw new InvalidArgumentsException(format(
            "%s path reference is out of bounds for configFileIdentifier: %s. Expected between 0 and %d, but found %d",
            fileType, configFileIdentifier, fileList.size() - 1, configFilePathIndex));
      }
    }
  }

  private ConfigFileOutcomeLite getConfigFileOutcome(Ambiance ambiance, String configFileIdentifier) {
    Optional<String> configFilesOutcomeOpt = getConfigFilesOutcome(ambiance);
    if (configFilesOutcomeOpt.isEmpty()) {
      throw new InvalidArgumentsException("Not found config files");
    }
    JsonNode jsonNode = YamlUtils.readAsJsonNode(configFilesOutcomeOpt.get());

    if (!jsonNode.isObject()) {
      throw new InvalidArgumentsException(format("Not found config file with identifier: %s", configFileIdentifier));
    }
    ObjectNode objectNode = (ObjectNode) jsonNode;
    if (objectNode.get(configFileIdentifier) == null) {
      throw new InvalidArgumentsException(format("Not found config file with identifier: %s", configFileIdentifier));
    }
    return ConfigFileOutcomeLite.convertToLite(objectNode.get(configFileIdentifier));
  }

  public Optional<String> getConfigFilesOutcome(Ambiance ambiance) {
    OptionalOutcome configFilesOutcome =
        outcomeService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject("configFiles"));

    if (!configFilesOutcome.isFound()) {
      return Optional.empty();
    }

    return Optional.of(configFilesOutcome.getOutcome());
  }

  @VisibleForTesting
  Pair<String, String> getConfigFileIdentifierAndReference(String configFileIdentifierWithReference) {
    if (EmptyPredicate.isEmpty(configFileIdentifierWithReference)) {
      throw new InvalidArgumentsException("Config file identifier cannot be null or empty");
    }
    if (configFileIdentifierWithReference.startsWith(CONFIG_FILE_IDENTIFIER_REFERENCE_DELIMITER)
        || configFileIdentifierWithReference.endsWith(CONFIG_FILE_IDENTIFIER_REFERENCE_DELIMITER)) {
      throw new InvalidArgumentsException(
          format("Found invalid config file identifier, %s", configFileIdentifierWithReference));
    }

    if (!configFileIdentifierWithReference.contains(CONFIG_FILE_IDENTIFIER_REFERENCE_DELIMITER)) {
      return Pair.of(configFileIdentifierWithReference, null);
    }

    String[] configFileIdentifierAndReference =
        configFileIdentifierWithReference.split(CONFIG_FILE_IDENTIFIER_REFERENCE_DELIMITER, 2);
    return Pair.of(configFileIdentifierAndReference[0], configFileIdentifierAndReference[1]);
  }

  private String getFileStoreFileContent(Ambiance ambiance, final String methodName, final String scopedFilePath) {
    if (FUNCTOR_STRING_METHOD_NAME.equals(methodName)) {
      try {
        String content = engineExpressionService.renderExpression(
            getContent(AmbianceUtils.getAccountId(ambiance), AmbianceUtils.getOrgIdentifier(ambiance),
                AmbianceUtils.getProjectIdentifier(ambiance), scopedFilePath),
            ExpressionMode.RETURN_NULL_IF_UNRESOLVED, depth--);
        // We are doing a depth+1 again because we want to check for loop but we want to allow many fileStore
        // resolutions in a given expression. This ensures that when a fileStore or configFile is called from inside a
        // fileStore or configFileFunctor, then only depth is reduced
        depth++;
        return content;
      } catch (EngineExpressionEvaluationException e) {
        throw new EngineFunctorException(e);
      }
    } else if (FUNCTOR_BASE64_METHOD_NAME.equals(methodName)) {
      return "<+fileStore.getAsBase64Internal(<+fileStore.getAsString('" + scopedFilePath + "')>)>";
    } else {
      throw new InvalidArgumentsException(
          format("Unsupported configFile functor method: %s, scopedFilePath: %s", methodName, scopedFilePath));
    }
  }

  public String getContent(String accountId, String orgId, String projectId, String scopedFilePath) {
    scopedFilePath = URLDecoderUtility.getEncodedString(scopedFilePath);
    try {
      ResponseDTO<String> ret =
          SafeHttpCall.executeWithExceptions(fileStoreClient.getContent(scopedFilePath, accountId, orgId, projectId));
      return ret.getData();
    } catch (Exception ex) {
      log.error(format("Failed to get File content from `%s`", scopedFilePath), ex);
      throw new InvalidRequestException(String.format("Failed to get file content from: %s", scopedFilePath));
    }
  }
}
