/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline;

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
@ApiModel("RemotePipelinesDTO")
@Schema(
    name = "RemotePipelinesDTO", description = "Remote pipelines grouped by a unique repository (repoName, repoURL).")
@OwnedBy(PIPELINE)
public class RemotePipelinesDTO {
  @Schema(description = "Repository name.") String repoName;
  @Schema(description = "Repository URL.") String repoURL;
  @Schema(description = "Number of remote pipelines in this repository.") long count;
  @Schema(description = "Unique pipeline file paths in this repository, mapped to the owning scope "
          + "of the pipeline (account/org/project + parentUniqueId). Two projects may share the same repo; "
          + "the owning scope lets consumers decide which per-scope webhook governs each file.")
  Map<String, Scope> filePathsByOwningScope;
  @Schema(description = "Distinct fully-qualified connector references "
          + "(<accountId>/<orgId>/<projectId>/<connectorId>, with org/project segments omitted "
          + "for org- or account-scoped connectors) used by pipelines in this repository.")
  Set<String> connectorRefs;
}
