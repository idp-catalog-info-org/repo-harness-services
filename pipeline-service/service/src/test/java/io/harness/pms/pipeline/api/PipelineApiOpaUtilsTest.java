/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.pms.pipeline.api;

import static io.harness.rule.OwnerRule.RITEK_ROUNAK;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.governance.PolicyMetadata;
import io.harness.governance.PolicySetMetadata;
import io.harness.rule.Owner;
import io.harness.spec.server.pipeline.v1.model.GovernanceStatus;
import io.harness.spec.server.pipeline.v1.model.PipelineGovernanceMetadata;
import io.harness.spec.server.pipeline.v1.model.PipelineGovernancePolicy;
import io.harness.spec.server.pipeline.v1.model.PipelineGovernancePolicySet;

import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class PipelineApiOpaUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testBuildGovernanceMetadataFromProtoMapsProtoFields() {
    io.harness.governance.GovernanceMetadata proto =
        io.harness.governance.GovernanceMetadata.newBuilder()
            .setId("eval-123")
            .setDeny(true)
            .setMessage("denied")
            .setTimestamp(111L)
            .setStatus("error")
            .setAccountId("acc")
            .setOrgId("org")
            .setProjectId("proj")
            .setEntity("entity")
            .setType("pipeline")
            .setAction("onsave")
            .setCreated(222L)
            .addDetails(PolicySetMetadata.newBuilder()
                            .setPolicySetId("ps-uuid")
                            .setDeny(true)
                            .setPolicySetName("ps-name")
                            .setStatus("error")
                            .setIdentifier("ps-id")
                            .setCreated(333L)
                            .setAccountId("acc")
                            .setOrgId("org")
                            .setProjectId("proj")
                            .setDescription("ps-desc")
                            .addPolicyMetadata(PolicyMetadata.newBuilder()
                                                   .setPolicyId("pol-uuid")
                                                   .setPolicyName("policy-name")
                                                   .setSeverity("error")
                                                   .addDenyMessages("denied by policy")
                                                   .setStatus("error")
                                                   .setIdentifier("policy-id")
                                                   .setAccountId("acc")
                                                   .setOrgId("org")
                                                   .setProjectId("proj")
                                                   .setCreated(444L)
                                                   .setUpdated(555L)
                                                   .setError("eval failed")
                                                   .build())
                            .build())
            .build();

    PipelineGovernanceMetadata governanceMetadata = PipelineApiOpaUtils.buildGovernanceMetadataFromProto(proto);

    assertThat(governanceMetadata.getId()).isEqualTo("eval-123");
    assertThat(governanceMetadata.isDeny()).isTrue();
    assertThat(governanceMetadata.getMessage()).isEqualTo("denied");
    assertThat(governanceMetadata.getTimestamp()).isEqualTo(111L);
    assertThat(governanceMetadata.getStatus()).isEqualTo(GovernanceStatus.ERROR);
    assertThat(governanceMetadata.getAccountId()).isEqualTo("acc");
    assertThat(governanceMetadata.getOrgId()).isEqualTo("org");
    assertThat(governanceMetadata.getProjectId()).isEqualTo("proj");
    assertThat(governanceMetadata.getEntity()).isEqualTo("entity");
    assertThat(governanceMetadata.getType()).isEqualTo("pipeline");
    assertThat(governanceMetadata.getAction()).isEqualTo("onsave");
    assertThat(governanceMetadata.getCreated()).isEqualTo(222L);

    List<PipelineGovernancePolicySet> details = governanceMetadata.getDetails();
    assertThat(details).hasSize(1);
    PipelineGovernancePolicySet policySet = details.get(0);
    assertThat(policySet.getPolicySetId()).isEqualTo("ps-uuid");
    assertThat(policySet.isDeny()).isTrue();
    assertThat(policySet.getPolicySetName()).isEqualTo("ps-name");
    assertThat(policySet.getStatus()).isEqualTo(GovernanceStatus.ERROR);
    assertThat(policySet.getIdentifier()).isEqualTo("ps-id");
    assertThat(policySet.getCreated()).isEqualTo(333L);
    assertThat(policySet.getAccountId()).isEqualTo("acc");
    assertThat(policySet.getOrgId()).isEqualTo("org");
    assertThat(policySet.getProjectId()).isEqualTo("proj");
    assertThat(policySet.getDescription()).isEqualTo("ps-desc");

    assertThat(policySet.getPolicyMetadata()).hasSize(1);
    PipelineGovernancePolicy policy = policySet.getPolicyMetadata().get(0);
    assertThat(policy.getPolicyId()).isEqualTo("pol-uuid");
    assertThat(policy.getPolicyName()).isEqualTo("policy-name");
    assertThat(policy.getSeverity()).isEqualTo("error");
    assertThat(policy.getDenyMessages()).containsExactly("denied by policy");
    assertThat(policy.getStatus()).isEqualTo(GovernanceStatus.ERROR);
    assertThat(policy.getIdentifier()).isEqualTo("policy-id");
    assertThat(policy.getAccountId()).isEqualTo("acc");
    assertThat(policy.getOrgId()).isEqualTo("org");
    assertThat(policy.getProjectId()).isEqualTo("proj");
    assertThat(policy.getCreated()).isEqualTo(444L);
    assertThat(policy.getUpdated()).isEqualTo(555L);
    assertThat(policy.getError()).isEqualTo("eval failed");
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testBuildGovernanceMetadataFromProtoNullAndEmptyDefaults() {
    assertThat(PipelineApiOpaUtils.buildGovernanceMetadataFromProto(null)).isNull();

    io.harness.governance.GovernanceMetadata proto =
        io.harness.governance.GovernanceMetadata.newBuilder().setDeny(false).setStatus("pass").build();
    PipelineGovernanceMetadata governanceMetadata = PipelineApiOpaUtils.buildGovernanceMetadataFromProto(proto);
    assertThat(governanceMetadata.getId()).isNull();
    assertThat(governanceMetadata.getDetails()).isNull();
    assertThat(governanceMetadata.getTimestamp()).isNull();
    assertThat(governanceMetadata.getCreated()).isNull();
    assertThat(governanceMetadata.getStatus()).isEqualTo(GovernanceStatus.PASS);
  }
}
