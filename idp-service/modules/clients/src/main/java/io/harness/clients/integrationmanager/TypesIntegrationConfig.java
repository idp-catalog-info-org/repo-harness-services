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
import java.util.List;
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
public class TypesIntegrationConfig {
  private Map<String, Object> configuration;
  private long created;
  private boolean enabled;
  private Map<String, Object> error;
  private String identifier;
  @JsonProperty("integration_mode") private IntegrationMode integrationMode;
  @JsonProperty("integration_type") private EnumIntegrationType integrationType;
  private List<String> kinds;
  @JsonProperty("last_sync") private long lastSync;
  @JsonProperty("last_updated") private long lastUpdated;
  private String name;
  @JsonProperty("space_path") private String spacePath;
  private String status;
  private TypesSyncState sync;
  private String version;
  @JsonProperty("action_per_kind") private Map<String, String> actionPerKind;

  public enum IntegrationMode { airbyte, kubernetes, webhook, platform }

  public enum EnumIntegrationType {
    HarnessCD,
    HarnessCI,
    ServiceNow,
    HarnessScope,
    HarnessK8s,
    PagerDuty,
    GCP,
    AIAssetDiscovery,
    GitHub,
    SonarQube,
    DataDog,
    BitbucketCloud,
    DynaTrace,
    CatalogInfo,
    HarnessTraceable
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @FieldDefaults(level = AccessLevel.PRIVATE)
  public static class TypesSyncState {
    private Map<String, Object> cursor;
    private long created;
    private boolean pending;
    @JsonProperty("total_entities") private long totalEntities;
  }
}
