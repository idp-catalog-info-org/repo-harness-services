/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.states.V1.cd;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.filestore.remote.FileStoreClient;
import io.harness.network.SafeHttpCall;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.utils.URLDecoderUtility;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Fetches Harness File Store config-file content via {@link FileStoreClient}. Kept separate from
 * {@link ConfigFilesStep} so the file-store fetch can be unit tested in isolation and reused.
 *
 * <p>Mirrors the NG content fetch in
 * {@code io.harness.pms.expressions.functors.ConfigFileFunctorV2#getFileStoreFileContent} but materializes
 * content eagerly at step execution time (parallel to how git config files are fetched in the unified flow).
 */
@OwnedBy(HarnessTeam.CI)
@Singleton
@Slf4j
public class HarnessConfigFileStoreFetcher {
  @Inject private FileStoreClient fileStoreClient;

  /**
   * Resolves each scoped file path to its content. Returns an ordered map keyed by scoped file path,
   * preserving the input order so callers can build deterministic outcomes.
   */
  public Map<String, String> fetchFileStoreContents(Ambiance ambiance, List<String> scopedFilePaths) {
    Map<String, String> contents = new LinkedHashMap<>();
    if (isEmpty(scopedFilePaths)) {
      return contents;
    }
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectId = AmbianceUtils.getProjectIdentifier(ambiance);
    for (String scopedFilePath : scopedFilePaths) {
      if (isEmpty(scopedFilePath)) {
        continue;
      }
      contents.put(scopedFilePath, getContent(accountId, orgId, projectId, scopedFilePath));
    }
    return contents;
  }

  public String getContent(String accountId, String orgId, String projectId, String scopedFilePath) {
    String encodedFilePath = URLDecoderUtility.getEncodedString(scopedFilePath);
    try {
      ResponseDTO<String> ret =
          SafeHttpCall.executeWithExceptions(fileStoreClient.getContent(encodedFilePath, accountId, orgId, projectId));
      return ret.getData();
    } catch (Exception ex) {
      log.error(format("Failed to get File content from `%s`", scopedFilePath), ex);
      throw new InvalidRequestException(format("Failed to get file content from: %s", scopedFilePath));
    }
  }
}
