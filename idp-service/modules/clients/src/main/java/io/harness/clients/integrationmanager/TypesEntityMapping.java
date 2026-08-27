/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.clients.integrationmanager;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class TypesEntityMapping {
  private String id;
  @JsonProperty("integration_id") private String integrationId;
  @JsonProperty("entity_schema_id") private String entitySchemaId;
  private String kind;
  @JsonProperty("mapping_id") private String mappingId;
  private String version;
  @JsonProperty("space_path") private String spacePath;

  @JsonProperty("mapping_config") private TypesMappingConfig mappingConfig;
  private TypesStorageMappingSpec mappings;

  private Long created;
  @JsonProperty("last_updated") private Long lastUpdated;

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @FieldDefaults(level = AccessLevel.PRIVATE)
  public static class TypesMappingConfig {
    @JsonProperty("auto_subscribe") private TypesFieldMapping autoSubscribe;
    @JsonProperty("correlation_id") private TypesFieldMapping correlationId;
    @JsonProperty("idp_kind") private String idpKind;
    @JsonProperty("idp_type") private String idpType;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class TypesFieldMapping {
      @JsonProperty("source_field") private String sourceField;
      @JsonProperty("target_field") private String targetField;
    }
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @FieldDefaults(level = AccessLevel.PRIVATE)
  public static class TypesStorageMappingSpec {
    private String identifier;
    private String name;
    private TypesIDPInfo idp;
    private TypesScopeInfo scope;
    private Map<String, Object> properties;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class TypesIDPInfo {
      private String kind;
      private String type;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class TypesScopeInfo {
      @JsonProperty("account_identifier") private String accountIdentifier;
      @JsonProperty("org_identifier") private String orgIdentifier;
      @JsonProperty("project_identifier") private String projectIdentifier;
    }
  }
}
