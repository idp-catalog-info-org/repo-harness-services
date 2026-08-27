/*
 * Copyright 2021 Harness Inc. All rights reserved.
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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_DASHBOARD})
@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@OwnedBy(GITOPS)
public class RecentDeploymentsDetailsListDTO {
  @JsonProperty("content") private List<DeploymentsDetails> content;
  @JsonProperty("pageItemCount") private Integer pageItemCount;
  @JsonProperty("empty") private Boolean empty;

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class DeploymentsDetails {
    @JsonProperty("startedAt") private String startedAt;
    @JsonProperty("rollback") private Integer rollback;
    @JsonProperty("deploy") private Integer deploy;
    @JsonProperty("redeploy") private Integer redeploy;
    @JsonProperty("succeeded") private Integer succeeded;
    @JsonProperty("error") private Integer error;
    @JsonProperty("terminating") private Integer terminating;
    @JsonProperty("failed") private Integer failed;
    @JsonProperty("running") private Integer running;
    @JsonProperty("totalDeployments") private Integer totalDeployments;
    @JsonProperty("failureRate") private Double failureRate;
  }
}
