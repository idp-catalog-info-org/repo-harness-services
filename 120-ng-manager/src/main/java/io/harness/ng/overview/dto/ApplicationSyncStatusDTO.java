/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.overview.dto;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.gitops.models.ApplicationResource;
import io.harness.gitops.models.ApplicationSyncStatus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_DASHBOARD})
@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@OwnedBy(GITOPS)
public class ApplicationSyncStatusDTO {
  @JsonProperty("accountIdentifier") private String accountIdentifier;
  @JsonProperty("projectIdentifier") private String projectIdentifier;
  @JsonProperty("orgIdentifier") private String orgIdentifier;
  @JsonProperty("agentIdentifier") private String agentIdentifier;
  @JsonProperty("applicationName") private String applicationName;
  @JsonProperty("syncStatus") private ApplicationResource.SyncResult syncStatus;
  @JsonProperty("createdAt") private Integer createdAt;
  @JsonProperty("lastModifiedAt") private Integer lastModifiedAt;
  @JsonProperty("operationState") private ApplicationResource.OperationState operationState;
  @JsonProperty("reqIdentifier") private String reqIdentifier;
  @JsonProperty("lastKnownRevisionId") private Integer lastKnownRevisionId;
  @JsonProperty("syncedBy") private ApplicationSyncStatus.User syncedBy;
  @JsonProperty("autoSyncCount") private Integer autoSyncCount;
  @JsonProperty("serviceRef") private String serviceRef;
  @JsonProperty("envRef") private String envRef;
}
