/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.dto;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "TriggerExecutor",
    description = "Details of a user or service account eligible to execute a trigger. "
        + "For create/update requests, 'uuid' and 'type' are required. For SERVICE_ACCOUNT executors, "
        + "accountIdentifier, orgIdentifier, and projectIdentifier are required and must match the service "
        + "account's defining scope (from listManageableServiceAccounts). Name and email are fetched from "
        + "user/service-account services. "
        + "For responses, all fields are populated.")
@OwnedBy(PIPELINE)
public class TriggerExecutorDTO {
  @JsonProperty("uuid")
  @JsonAlias("identifier")
  @Schema(description = "Unique identifier of the executor (user UUID or service account identifier)", required = true)
  String identifier;

  @Schema(description = "Display name of the executor (auto-populated from user/service-account service on save)",
      required = false)
  String name;

  @Schema(description = "Email address (auto-populated from user/service-account service on save)", required = false)
  String email;

  @Schema(description = "Account identifier of the service account's defining scope. Required in request for "
          + "SERVICE_ACCOUNT. Not used for USER executors.",
      required = false)
  String accountIdentifier;

  @Schema(description = "Org identifier of the service account's defining scope. Required in request for "
          + "SERVICE_ACCOUNT (null for account-level service accounts). Not used for USER executors.",
      required = false)
  String orgIdentifier;

  @Schema(description = "Project identifier of the service account's defining scope. Required in request for "
          + "SERVICE_ACCOUNT (null for account- or org-level service accounts). Not used for USER executors.",
      required = false)
  String projectIdentifier;

  @Schema(description = "Globally unique ID of the executor. For SERVICE_ACCOUNT this is the internal uniqueId "
          + "that disambiguates identically-named service accounts across scopes. For USER this equals the uuid.",
      required = false)
  String uniqueId;

  @Schema(description = "Type of executor - USER or SERVICE_ACCOUNT", required = true) ExecutorType type;

  public enum ExecutorType {
    @Schema(description = "Regular user") USER,
    @Schema(description = "Service account") SERVICE_ACCOUNT
  }
}
