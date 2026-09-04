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
 * Converts OPA/Governance protobuf metadata to Pipeline V1 OpenAPI models.
 * Field names on the generated models match GovernanceMetadata / PolicySetMetadata / PolicyMetadata proto.
 */
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineApiOpaUtils {
  public static PipelineGovernanceMetadata buildGovernanceMetadataFromProto(
      io.harness.governance.GovernanceMetadata protoMetadata) {
    if (protoMetadata == null) {
      return null;
    }
    return new PipelineGovernanceMetadata()
        .id(nullIfEmpty(protoMetadata.getId()))
        .deny(protoMetadata.getDeny())
        .details(buildPolicySetMetadata(protoMetadata.getDetailsList()))
        .message(nullIfEmpty(protoMetadata.getMessage()))
        .timestamp(nullIfZero(protoMetadata.getTimestamp()))
        .status(toGovernanceStatus(protoMetadata.getStatus()))
        .accountId(nullIfEmpty(protoMetadata.getAccountId()))
        .orgId(nullIfEmpty(protoMetadata.getOrgId()))
        .projectId(nullIfEmpty(protoMetadata.getProjectId()))
        .entity(nullIfEmpty(protoMetadata.getEntity()))
        .type(nullIfEmpty(protoMetadata.getType()))
        .action(nullIfEmpty(protoMetadata.getAction()))
        .created(nullIfZero(protoMetadata.getCreated()));
  }

  private static List<PipelineGovernancePolicySet> buildPolicySetMetadata(List<PolicySetMetadata> detailsList) {
    if (EmptyPredicate.isEmpty(detailsList)) {
      return null;
    }
    return detailsList.stream()
        .map(policySet
            -> new PipelineGovernancePolicySet()
                   .policySetId(nullIfEmpty(policySet.getPolicySetId()))
                   .deny(policySet.getDeny())
                   .policyMetadata(buildPoliciesMetadata(policySet.getPolicyMetadataList()))
                   .policySetName(nullIfEmpty(policySet.getPolicySetName()))
                   .status(toGovernanceStatus(policySet.getStatus()))
                   .identifier(nullIfEmpty(policySet.getIdentifier()))
                   .created(nullIfZero(policySet.getCreated()))
                   .accountId(nullIfEmpty(policySet.getAccountId()))
                   .orgId(nullIfEmpty(policySet.getOrgId()))
                   .projectId(nullIfEmpty(policySet.getProjectId()))
                   .description(nullIfEmpty(policySet.getDescription())))
        .collect(Collectors.toList());
  }

  private static List<PipelineGovernancePolicy> buildPoliciesMetadata(List<PolicyMetadata> policyMetadataList) {
    if (EmptyPredicate.isEmpty(policyMetadataList)) {
      return null;
    }
    return policyMetadataList.stream()
        .map(policy
            -> new PipelineGovernancePolicy()
                   .policyId(nullIfEmpty(policy.getPolicyId()))
                   .policyName(nullIfEmpty(policy.getPolicyName()))
                   .severity(nullIfEmpty(policy.getSeverity()))
                   .denyMessages(
                       EmptyPredicate.isEmpty(policy.getDenyMessagesList()) ? null : policy.getDenyMessagesList())
                   .status(toGovernanceStatus(policy.getStatus()))
                   .identifier(nullIfEmpty(policy.getIdentifier()))
                   .accountId(nullIfEmpty(policy.getAccountId()))
                   .orgId(nullIfEmpty(policy.getOrgId()))
                   .projectId(nullIfEmpty(policy.getProjectId()))
                   .created(nullIfZero(policy.getCreated()))
                   .updated(nullIfZero(policy.getUpdated()))
                   .error(nullIfEmpty(policy.getError())))
        .collect(Collectors.toList());
  }

  private static String nullIfEmpty(String value) {
    return EmptyPredicate.isEmpty(value) ? null : value;
  }

  private static Long nullIfZero(long value) {
    return value == 0 ? null : value;
  }

  private static GovernanceStatus toGovernanceStatus(String status) {
    if (EmptyPredicate.isEmpty(status)) {
      return null;
    }
    return GovernanceStatus.fromValue(status.toUpperCase());
  }
}
