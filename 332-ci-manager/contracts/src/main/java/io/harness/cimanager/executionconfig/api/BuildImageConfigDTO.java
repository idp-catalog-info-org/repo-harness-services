/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.ci.pipeline.executions.beans;

import io.harness.annotations.StoreIn;
import io.harness.ng.DbAliases;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.morphia.annotations.Entity;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "BuildImageConfigKeys")
@StoreIn(DbAliases.CIMANAGER)
@Entity(value = "buildImageConfig", noClassnameStored = true)
@Document(collection = "buildImageConfig")
public class BuildImageConfigDTO {
  private String accountId;

  @NotNull(message = "Config is required, data cannot be null or empty") private ImageOS data;

  @Data
  @Builder
  public static class ImageOS {
    private EnvironmentType linux_amd64;
    private EnvironmentType linux_arm64;
    private EnvironmentType windows_amd64;
    private EnvironmentType mac_arm64;
  }

  @Data
  @Builder
  public static class EnvironmentType {
    private List<ImageDetailsConfig> primary;
    private List<ImageDetailsConfig> beta;
  }

  @Data
  @Builder
  public static class ImageDetailsConfig {
    @NotNull private String version;
    @NotNull private String image;
  }
}
