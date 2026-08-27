/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.utils.configfile;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.serializer.JsonUtils;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Harness store deserialized from flattened {@code inputs}. Supports both Harness File Store scoped paths
 * ({@code files}) and inline encrypted/secret file refs ({@code secretFiles}), mirroring the NG Harness store.
 */
@Value
@Builder(toBuilder = true)
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
@OwnedBy(HarnessTeam.CI)
public class HarnessFileStoreSpec implements FileStoreSpec {
  @JsonProperty("files")
  @JsonDeserialize(using = FileStoreSpec.SingleOrListOfStringsDeserializer.class)
  @Builder.Default
  List<String> filePaths = Collections.emptyList();

  @JsonProperty("secretFiles")
  @JsonAlias("secret-files")
  @JsonDeserialize(using = FileStoreSpec.SingleOrListOfStringsDeserializer.class)
  @Builder.Default
  List<String> secretFilePaths = Collections.emptyList();

  /**
   * Builds spec from {@link io.harness.unified.cd.service.configfiles.ConfigFile#getInputs()} via Jackson.
   */
  public static HarnessFileStoreSpec fromInputs(Map<String, Object> in) {
    if (in == null) {
      return HarnessFileStoreSpec.builder()
          .filePaths(Collections.emptyList())
          .secretFilePaths(Collections.emptyList())
          .build();
    }
    try {
      HarnessFileStoreSpec spec = JsonUtils.convertValue(in, HarnessFileStoreSpec.class);
      List<String> filePaths = spec.getFilePaths() == null ? Collections.emptyList() : spec.getFilePaths();
      List<String> secretPaths =
          spec.getSecretFilePaths() == null ? Collections.emptyList() : spec.getSecretFilePaths();
      return spec.toBuilder().filePaths(filePaths).secretFilePaths(secretPaths).build();
    } catch (IllegalArgumentException e) {
      throw new InvalidRequestException("Invalid Harness config file inputs: " + e.getMessage(), e);
    }
  }
}
