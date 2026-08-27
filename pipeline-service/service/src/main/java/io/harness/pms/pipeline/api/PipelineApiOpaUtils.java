/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.api;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.governance.PolicyMetadata;
import io.harness.governance.PolicySetMetadata;
import io.harness.spec.server.pipeline.v1.model.GovernanceStatus;
import io.harness.spec.server.pipeline.v1.model.PipelineGovernanceMetadata;
import io.harness.spec.server.pipeline.v1.model.PipelineGovernancePolicy;
import io.harness.spec.server.pipeline.v1.model.PipelineGovernancePolicySet;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility class for converting OPA/Governance metadata from protobuf to Pipeline V1 API models.
 * This class specifically handles governance-related conversions for Pipeline V1 API responses
 * (create/update/patch operations).
 */
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineApiOpaUtils {
  public static PipelineGovernanceMetadata buildGovernanceMetadataFromProto(
      io.harness.governance.GovernanceMetadata protoMetadata) {
    return new PipelineGovernanceMetadata()
        .deny(protoMetadata.getDeny())
        .message(protoMetadata.getMessage())
        .status(GovernanceStatus.fromValue(protoMetadata.getStatus().toUpperCase()))
        .policySets(buildPolicySetMetadata(protoMetadata.getDetailsList()));
  }

  private static List<PipelineGovernancePolicySet> buildPolicySetMetadata(List<PolicySetMetadata> detailsList) {
    if (EmptyPredicate.isEmpty(detailsList)) {
      return null;
    }
    return detailsList.stream()
        .map(policySet
            -> new PipelineGovernancePolicySet()
                   .identifier(policySet.getIdentifier())
                   .name(policySet.getPolicySetName())
                   .org(policySet.getOrgId())
                   .project(policySet.getProjectId())
                   .status(GovernanceStatus.fromValue(policySet.getStatus().toUpperCase()))
                   .policies(buildPoliciesMetadata(policySet.getPolicyMetadataList())))
        .collect(Collectors.toList());
  }

  private static List<PipelineGovernancePolicy> buildPoliciesMetadata(List<PolicyMetadata> policyMetadataList) {
    if (EmptyPredicate.isEmpty(policyMetadataList)) {
      return null;
    }
    return policyMetadataList.stream()
        .map(policy
            -> new PipelineGovernancePolicy()
                   .identifier(policy.getIdentifier())
                   .name(policy.getPolicyName())
                   .org(policy.getOrgId())
                   .project(policy.getProjectId())
                   .evaluationError(policy.getError())
                   .denyMessages(policy.getDenyMessagesList())
                   .status(GovernanceStatus.fromValue(policy.getStatus().toUpperCase())))
        .collect(Collectors.toList());
  }
}
