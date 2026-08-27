/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.clients.integrationmanager;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.codehaus.jackson.annotate.JsonProperty;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class EntitySubscribeEntitiesResponse {
  private List<EntitySubscriptionFailure> failed;
  private List<EntitySubscriptionSuccess> success;
  private EntitySubscriptionSummary summary;

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @FieldDefaults(level = AccessLevel.PRIVATE)
  public static class EntitySubscriptionSuccess {
    @JsonProperty("entity_info") private EntityEntityIdentifierInfo entityInfo;
    private String uuid;
    private String kind;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @FieldDefaults(level = AccessLevel.PRIVATE)
  public static class EntitySubscriptionFailure {
    @JsonProperty("entity_info") private EntityEntityIdentifierInfo entityInfo;
    private String uuid;
    private String kind;
    private String reason;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @FieldDefaults(level = AccessLevel.PRIVATE)
  public static class EntitySubscriptionSummary {
    @JsonProperty("partial_success") private Boolean partialSuccess;
    @JsonProperty("total_requested") private Integer totalRequested;
    @JsonProperty("total_success") private Integer totalSuccess;
    @JsonProperty("total_failed") private Integer totalFailed;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @FieldDefaults(level = AccessLevel.PRIVATE)
  public static class EntityEntityIdentifierInfo {
    private String identifier;
    @JsonProperty("space_path") private String spacePath;
  }
}
