/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.utils.configfile;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.serializer.JsonUtils;
import io.harness.unified.cd.service.manifests.StoreType;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/** Git (SCM) store fields for unified config file, deserialized from flattened {@code inputs}. */
@Value
@Builder(toBuilder = true)
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
@OwnedBy(HarnessTeam.CI)
public class GitFileStoreSpec implements FileStoreSpec {
  @JsonAlias("uses") StoreType storeType;

  @JsonAlias("connector") @JsonProperty("connectorRef") String connectorRef;

  @JsonAlias("repo") @JsonProperty("repoName") String repoName;

  String branch;

  @JsonAlias("commit") @JsonProperty("commitId") String commitId;

  @JsonDeserialize(using = FileStoreSpec.SingleOrListOfStringsDeserializer.class)
  @Builder.Default
  List<String> paths = Collections.emptyList();

  /**
   * Deserializes git fields from inputs; {@code storeType} is taken from {@link
   * ResolvedConfigFileStoreSpecFactory#parseStoreType} so discriminator resolution stays consistent with {@code uses} /
   * {@code storeType} key precedence.
   */
  public static GitFileStoreSpec fromInputs(Map<String, Object> in, StoreType storeType) {
    if (in == null || in.isEmpty()) {
      throw new InvalidRequestException("Config file git inputs are required");
    }
    try {
      GitFileStoreSpec spec = JsonUtils.convertValue(in, GitFileStoreSpec.class);
      GitFileStoreSpec built = spec.toBuilder().storeType(storeType).build();
      if (isEmpty(built.getConnectorRef()) || "null".equals(built.getConnectorRef())) {
        throw new InvalidRequestException("Git connector reference is required in config file inputs");
      }
      List<String> pathsNorm = normalizePaths(built.getPaths());
      return built.toBuilder().paths(pathsNorm).build();
    } catch (IllegalArgumentException e) {
      throw new InvalidRequestException("Invalid git config file inputs: " + e.getMessage(), e);
    }
  }

  private static List<String> normalizePaths(List<String> rawPaths) {
    if (rawPaths == null || rawPaths.isEmpty()) {
      return Collections.emptyList();
    }

    List<String> normalizedPaths = new ArrayList<>();
    for (String path : rawPaths) {
      if (isEmpty(path)) {
        continue;
      }

      String trimmedPath = path.trim();
      if (!JsonUtils.isJsonList(trimmedPath)) {
        normalizedPaths.add(path);
        continue;
      }

      try {
        List<String> parsedList = JsonUtils.asList(trimmedPath, new TypeReference<List<String>>() {});
        if (parsedList == null || parsedList.isEmpty()) {
          normalizedPaths.add(path);
        } else {
          normalizedPaths.addAll(parsedList);
        }
      } catch (Exception ex) {
        // Keep original value if parsing fails so caller can surface a meaningful downstream error.
        normalizedPaths.add(path);
      }
    }
    return normalizedPaths;
  }
}
