/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import static io.harness.utils.FilePathUtils.FILE_PATH_PATTERN;
import static io.harness.utils.SecretUtils.containsSecret;

import static java.lang.String.format;

import io.harness.account.settings.service.PipelineSettingsService;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.encoding.EncodingUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.dataretention.PipelineRetentionService;
import io.harness.exception.EngineExpressionEvaluationException;
import io.harness.exception.EngineFunctorException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.expression.ExpressionEvaluatorUtils;
import io.harness.expression.LateBindingMap;
import io.harness.expression.common.ExpressionMode;
import io.harness.expression.functors.ExpressionFunctor;
import io.harness.filestore.remote.FileStoreClient;
import io.harness.network.SafeHttpCall;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.utils.URLDecoderUtility;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;

import java.nio.charset.StandardCharsets;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_PIPELINE, HarnessModuleComponent.CDS_PIPELINE})
@AllArgsConstructor
@Slf4j
public class FileStoreFunctorV2 extends LateBindingMap implements ExpressionFunctor {
  private static final long MAX_FILE_SIZE = 4 * ExpressionEvaluatorUtils.EXPANSION_LIMIT;

  FileStoreClient fileStoreClient;
  Ambiance ambiance;
  PipelineRetentionService pipelineRetentionService;
  PipelineSettingsService pipelineSettingsService;
  EngineExpressionEvaluator engineExpressionService;
  int depth;

  public Object getAsBase64Internal(String content) {
    if (EmptyPredicate.isEmpty(content)) {
      return null;
    }

    boolean fileContentContainSecret = containsSecret(content);
    String accountId = AmbianceUtils.getAccountId(ambiance);
    if (fileContentContainSecret) {
      log.warn("File content to be encoded as base64 contains secret, accountId: {}, scopedFilePath: {}", accountId);
    }

    // We still need to support "${ngBase64Manager.encode(\"" + content + "\")}" because of backward compatibility.
    return fileContentContainSecret ? "${ngBase64Manager.encode(\"" + content + "\")}"
                                    : EncodingUtils.encodeBase64(content);
  }

  /**
   * We have a use case where in we want to resolve the contents of the file first and then convert to base64. Inorder
   * to not call the expression engine again from functor, we are returning the modified expression so that the contents
   * get resolved
   *
   * This will also ensure that we do not run into loops because expression engine automatically takes care of depth
   *
   * @param scopedFilePath
   * @return
   */
  public Object getAsBase64(String scopedFilePath) {
    return "<+fileStore.getAsBase64Internal(<+fileStore.getAsString(\"" + scopedFilePath + "\")>)>";
  }

  public Object getAsString(String scopedFilePath) {
    if (!FILE_PATH_PATTERN.matcher(scopedFilePath).find()) {
      throw new InvalidArgumentsException(format("File path not valid: %s", scopedFilePath));
    }
    String accountIdentifier = AmbianceUtils.getAccountId(ambiance);
    String content = getContent(accountIdentifier, AmbianceUtils.getOrgIdentifier(ambiance),
        AmbianceUtils.getProjectIdentifier(ambiance), scopedFilePath);
    long contentInBytesLength = content.getBytes(StandardCharsets.UTF_8).length;
    if (!pipelineSettingsService.isFileSizeWithinLimit(accountIdentifier, contentInBytesLength)) {
      log.warn("[FILE_SIZE_LIMIT_EXCEEDED]: The File size limit is exceeded for the account {}.", accountIdentifier);
      try {
        pipelineRetentionService.updateMaxFileSizeLimit(accountIdentifier, contentInBytesLength);
      } catch (Exception ex) {
        log.warn(String.format(
                     "Can be ignored - Error in overriding the file size limit for account id: {%s}, to size: {%d}:",
                     accountIdentifier, content.getBytes(StandardCharsets.UTF_8).length),
            ex);
      }
    }
    if (contentInBytesLength > MAX_FILE_SIZE) {
      throw new InvalidRequestException(format("Too large file, scopedFilePath: %s", scopedFilePath));
    }
    return content;
  }

  public String getContent(String accountId, String orgId, String projectId, String scopedFilePath) {
    scopedFilePath = URLDecoderUtility.getEncodedString(scopedFilePath);
    try {
      ResponseDTO<String> ret =
          SafeHttpCall.executeWithExceptions(fileStoreClient.getContent(scopedFilePath, accountId, orgId, projectId));
      String content =
          engineExpressionService.renderExpression(ret.getData(), ExpressionMode.RETURN_NULL_IF_UNRESOLVED, depth--);
      // We are doing a depth+1 again because we want to check for loop but we want to allow many fileStore resolutions
      // in a given expression. This ensures that when a fileStore or configFile is called from inside a fileStore or
      // configFileFunctor, then only depth is reduced
      depth++;
      return content;
    } catch (EngineExpressionEvaluationException e) {
      throw new EngineFunctorException(e);
    } catch (Exception ex) {
      log.error(format("Failed to get File content from `%s`", scopedFilePath), ex);
      throw new InvalidRequestException(String.format("Failed to get file content from: %s", scopedFilePath));
    }
  }
}
