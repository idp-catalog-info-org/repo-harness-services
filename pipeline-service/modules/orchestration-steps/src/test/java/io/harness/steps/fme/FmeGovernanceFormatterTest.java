/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.fme;

import static io.harness.rule.OwnerRule.CAMERON;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.fme.governance.FmeGovernancePolicyDetails;
import io.harness.fme.governance.FmeGovernancePolicySetDetails;
import io.harness.fme.governance.FmeGovernanceResult;
import io.harness.fme.governance.GovernanceAction;
import io.harness.fme.governance.GovernanceStatus;
import io.harness.fme.governance.GovernanceType;
import io.harness.rule.Owner;

import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.FME)
@RunWith(MockitoJUnitRunner.class)
public class FmeGovernanceFormatterTest extends CategoryTest {
  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testFormatForLog_withValidResult() {
    FmeGovernanceResult result = createTestGovernanceResult(GovernanceStatus.WARNING);

    String formatted = FmeGovernanceFormatter.formatForLog(result);

    assertThat(formatted).contains("Governance Evaluation Result");
    assertThat(formatted).contains("Status: warning");
    assertThat(formatted).contains("Action: onstep");
    assertThat(formatted).contains("Type: featureFlag");
    assertThat(formatted).contains("Policy Set: Test Policy Set");
    assertThat(formatted).contains("Policy: Test Policy");
    assertThat(formatted).contains("flag name is invalid");
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testFormatForLog_withNullResult() {
    String formatted = FmeGovernanceFormatter.formatForLog(null);

    assertThat(formatted).isEqualTo("No governance details available");
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testFormatForLog_withNullDetails() {
    FmeGovernanceResult result = FmeGovernanceResult.builder()
                                     .status(GovernanceStatus.WARNING)
                                     .action(GovernanceAction.ON_STEP)
                                     .type(GovernanceType.FEATURE_FLAG)
                                     .details(null)
                                     .build();

    String formatted = FmeGovernanceFormatter.formatForLog(result);

    assertThat(formatted).isEqualTo("No governance details available");
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testFormatForExceptionMessage_withFailedPolicies() {
    FmeGovernanceResult result = createTestGovernanceResult(GovernanceStatus.ERROR);

    String message = FmeGovernanceFormatter.formatForExceptionMessage(result);

    assertThat(message).contains("Policy denied:");
    assertThat(message).contains("Test Policy");
    assertThat(message).contains("flag name is invalid");
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testFormatForExceptionMessage_withNullResult() {
    String message = FmeGovernanceFormatter.formatForExceptionMessage(null);

    assertThat(message).isEqualTo("Policy evaluation failed");
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testFormatForExceptionMessage_withNoFailedPolicies() {
    FmeGovernanceResult result = createTestGovernanceResult(GovernanceStatus.PASS);

    String message = FmeGovernanceFormatter.formatForExceptionMessage(result);

    assertThat(message).isEqualTo("Policy evaluation denied the operation");
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testGetHintMessage() {
    FmeGovernanceResult result = createTestGovernanceResult(GovernanceStatus.ERROR);

    String hint = FmeGovernanceFormatter.getHintMessage(result);

    assertThat(hint).contains("Review the policy requirements");
    assertThat(hint).contains("governance administrator");
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testGetExplanationMessage_withResult() {
    FmeGovernanceResult result = createTestGovernanceResult(GovernanceStatus.ERROR);

    String explanation = FmeGovernanceFormatter.getExplanationMessage(result);

    assertThat(explanation).contains("FME governance evaluated 1 policy set(s)");
    assertThat(explanation).contains("1 policy set(s) denied the operation");
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testGetExplanationMessage_withNullResult() {
    String explanation = FmeGovernanceFormatter.getExplanationMessage(null);

    assertThat(explanation).contains("governance policies prevented this operation");
  }

  private FmeGovernanceResult createTestGovernanceResult(GovernanceStatus status) {
    FmeGovernancePolicyDetails policyDetails = FmeGovernancePolicyDetails.builder()
                                                   .id("policy-1")
                                                   .name("Test Policy")
                                                   .status(status)
                                                   .severity("high")
                                                   .denyMessages(List.of("flag name is invalid"))
                                                   .build();

    FmeGovernancePolicySetDetails policySetDetails = FmeGovernancePolicySetDetails.builder()
                                                         .id("policy-set-1")
                                                         .name("Test Policy Set")
                                                         .status(status)
                                                         .description("Test policy set description")
                                                         .details(List.of(policyDetails))
                                                         .build();

    return FmeGovernanceResult.builder()
        .id("governance-1")
        .status(status)
        .action(GovernanceAction.ON_STEP)
        .type(GovernanceType.FEATURE_FLAG)
        .details(List.of(policySetDetails))
        .build();
  }
}
