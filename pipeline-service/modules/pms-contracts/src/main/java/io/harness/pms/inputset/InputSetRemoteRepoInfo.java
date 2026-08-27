/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.inputset;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@ApiModel("InputSetRemoteRepoInfo")
@Schema(name = "InputSetRemoteRepoInfo", description = "Aggregated remote input set information grouped by repository.")
@OwnedBy(PIPELINE)
public class InputSetRemoteRepoInfo {
  @Schema(description = "Repository name as stored on the input set entity.") String repoName;
  @Schema(description = "Repository URL as stored on the input set entity.") String repoURL;
  @Schema(description = "Number of remote input sets for this repository.") long count;
  @Schema(description = "Unique input set file paths within this repository, mapped to the owning scope "
          + "of the input set (account/org/project + parentUniqueId). Consumers use the owning scope to "
          + "evaluate whether a per-scope webhook governs the file — a webhook only covers a file when "
          + "the webhook's scope is an ancestor of the file's owning scope.")
  Map<String, Scope> filePathsByOwningScope;
  @Schema(description = "Set of distinct fully-qualified connector references "
          + "(<accountId>/<orgId>/<projectId>/<connectorId>, with org/project segments omitted "
          + "for org- or account-scoped connectors) used by input sets in this repository.")
  Set<String> connectorRefs;
}
