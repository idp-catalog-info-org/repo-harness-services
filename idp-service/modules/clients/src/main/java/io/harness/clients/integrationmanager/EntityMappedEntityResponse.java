/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.clients.integrationmanager;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.apache.commons.lang3.StringUtils;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class EntityMappedEntityResponse {
  private String uuid;
  private String kind;
  private String name;
  @JsonProperty("correlation_field") private String correlationField;
  @JsonProperty("correlation_mapping") private CorrelationMapping correlationMapping;
  private EntityEntityScope scope;
  @JsonProperty("entity_info") private EntityEntityIdentifierInfoType2 entityInfo;
  private Map<String, Object> data;
  @JsonProperty("detected_at") private long detectedAt;

  public boolean hasCorrelationMapping() {
    return correlationMapping != null && StringUtils.isNotBlank(correlationMapping.getSourcePath())
        && StringUtils.isNotBlank(correlationMapping.getDestinationPath());
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  @FieldDefaults(level = AccessLevel.PRIVATE)
  public static class CorrelationMapping {
    @JsonProperty("source_path") private String sourcePath;
    @JsonProperty("target_path") private String destinationPath;
    @JsonProperty("operator") private String operator;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @FieldDefaults(level = AccessLevel.PRIVATE)
  public static class EntityEntityScope {
    @JsonProperty("account_identifier") private String accountIdentifier;
    @JsonProperty("org_identifier") private String orgIdentifier;
    @JsonProperty("project_identifier") private String projectIdentifier;

    @Override
    public String toString() {
      return "account" + (!isEmpty(orgIdentifier) ? "." + orgIdentifier : "")
          + (!isEmpty(projectIdentifier) ? "." + projectIdentifier : "");
    }
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @FieldDefaults(level = AccessLevel.PRIVATE)
  public static class EntityEntityIdentifierInfoType2 {
    private String identifier;
    @JsonProperty("space_path") private String spacePath;
  }
}
