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
import io.harness.ng.core.filestore.dto.FileStoreFetchedFileDTO;
import io.harness.ng.core.utils.URLDecoderUtility;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Fetches Harness File Store manifest content (files and folders) via {@link FileStoreClient}.
 *
 * <p>Mirrors {@code io.harness.cdng.CDStepHelper#validateAndFetchFileFromHarnessStore} /
 * {@code getFileContentsFromManifest}: each scoped reference is resolved server-side, where a file
 * yields a single entry and a folder is expanded recursively into all of its files. Kept separate
 * from {@link ManifestsStep} so the fetch can be unit tested in isolation and reused.
 */
@OwnedBy(HarnessTeam.CI)
@Singleton
@Slf4j
public class HarnessManifestFileStoreFetcher {
  @Inject private FileStoreClient fileStoreClient;

  /**
   * Resolves each scoped manifest reference (file or folder path) to its files-with-content,
   * preserving input order. Folder references are expanded recursively server-side.
   */
  public List<FileStoreFetchedFileDTO> fetchManifestFiles(Ambiance ambiance, List<String> scopedFilePaths) {
    List<FileStoreFetchedFileDTO> fetched = new ArrayList<>();
    if (isEmpty(scopedFilePaths)) {
      return fetched;
    }
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectId = AmbianceUtils.getProjectIdentifier(ambiance);
    for (String scopedFilePath : scopedFilePaths) {
      if (isEmpty(scopedFilePath)) {
        continue;
      }
      fetched.addAll(getFilesWithContent(accountId, orgId, projectId, scopedFilePath));
    }
    return fetched;
  }

  public List<FileStoreFetchedFileDTO> getFilesWithContent(
      String accountId, String orgId, String projectId, String scopedFilePath) {
    String encodedFilePath = URLDecoderUtility.getEncodedString(scopedFilePath);
    try {
      ResponseDTO<List<FileStoreFetchedFileDTO>> ret = SafeHttpCall.executeWithExceptions(
          fileStoreClient.getFilesWithContent(encodedFilePath, accountId, orgId, projectId));
      List<FileStoreFetchedFileDTO> data = ret.getData();
      return data != null ? data : new ArrayList<>();
    } catch (Exception ex) {
      log.error(format("Failed to fetch manifest file(s) from Harness File Store `%s`", scopedFilePath), ex);
      throw new InvalidRequestException(
          format("Failed to fetch manifest file(s) from Harness File Store: %s", scopedFilePath));
    }
  }
}
