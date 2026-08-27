/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.source.v1.webhook.condition;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.ngtriggers.conditionchecker.ConditionOperator.EQUALS;
import static io.harness.rule.OwnerRule.AVEESHA_JINDAL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ngtriggers.beans.source.v1.webhook.github.action.GithubPRAction;
import io.harness.ngtriggers.beans.source.v1.webhook.github.event.GithubPRSpec;
import io.harness.ngtriggers.beans.source.v1.webhook.spec.CustomTriggerSpec;
import io.harness.rule.Owner;

import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class ConditionsTest extends CategoryTest {
  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void shouldReturnEmptyListsWhenPayloadAndHeaderAreNull() {
    Conditions conditions = new Conditions(null, null, null);

    assertThat(conditions.getPayload()).isEmpty();
    assertThat(conditions.getHeader()).isEmpty();
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void shouldMapPayloadAndHeaderConditionsWhenPresent() {
    TriggerEventDataCondition payloadCondition =
        new TriggerEventDataCondition("key1", ConditionOperator.EQUALS, "value1");
    TriggerEventDataCondition headerCondition =
        new TriggerEventDataCondition("header1", ConditionOperator.EQUALS, "value2");
    Conditions conditions = new Conditions(List.of(headerCondition), List.of(payloadCondition), "true");

    assertThat(conditions.getPayload()).hasSize(1);
    assertThat(conditions.getPayload().get(0).getKey()).isEqualTo("key1");
    assertThat(conditions.getPayload().get(0).getOperator()).isEqualTo(EQUALS);
    assertThat(conditions.getPayload().get(0).getValue()).isEqualTo("value1");
    assertThat(conditions.getHeader()).hasSize(1);
    assertThat(conditions.getHeader().get(0).getKey()).isEqualTo("header1");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void shouldAllowCustomTriggerSpecWithEmptyConditionsBlock() {
    Conditions conditions = new Conditions(null, null, "true");
    CustomTriggerSpec customTriggerSpec = new CustomTriggerSpec(conditions);

    assertThat(customTriggerSpec.fetchPayloadConditions()).isEmpty();
    assertThat(customTriggerSpec.fetchHeaderConditions()).isEmpty();
    assertThat(customTriggerSpec.fetchJexlCondition()).isEqualTo("true");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void shouldPreserveGithubPrSpecBehaviorWhenConditionsAreNull() {
    GithubPRSpec githubPRSpec = new GithubPRSpec("connector", "repo", List.of(GithubPRAction.OPEN), null, false);

    assertThat(githubPRSpec.fetchPayloadConditions()).isNull();
    assertThat(githubPRSpec.fetchHeaderConditions()).isNull();
    assertThat(githubPRSpec.fetchJexlCondition()).isNull();
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void shouldPreserveGithubPrSpecPayloadMappingWhenConditionsArePopulated() {
    TriggerEventDataCondition payloadCondition =
        new TriggerEventDataCondition("branch", ConditionOperator.EQUALS, "main");
    TriggerEventDataCondition headerCondition =
        new TriggerEventDataCondition("X-GitHub-Event", ConditionOperator.EQUALS, "pull_request");
    Conditions conditions = new Conditions(List.of(headerCondition), List.of(payloadCondition), "true");
    GithubPRSpec githubPRSpec = new GithubPRSpec("connector", "repo", List.of(GithubPRAction.OPEN), conditions, false);

    assertThat(githubPRSpec.fetchPayloadConditions()).hasSize(1);
    assertThat(githubPRSpec.fetchPayloadConditions().get(0).getKey()).isEqualTo("branch");
    assertThat(githubPRSpec.fetchPayloadConditions().get(0).getOperator()).isEqualTo(EQUALS);
    assertThat(githubPRSpec.fetchPayloadConditions().get(0).getValue()).isEqualTo("main");
    assertThat(githubPRSpec.fetchHeaderConditions()).hasSize(1);
    assertThat(githubPRSpec.fetchHeaderConditions().get(0).getKey()).isEqualTo("X-GitHub-Event");
    assertThat(githubPRSpec.fetchJexlCondition()).isEqualTo("true");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void shouldMapV1CustomTriggerWithEmptyConditionsBlockToV2Spec() {
    CustomTriggerSpec v1CustomTriggerSpec = new CustomTriggerSpec(new Conditions(null, null, null));

    io.harness.ngtriggers.beans.source.webhook.v2.spec.CustomTriggerSpec v2CustomTriggerSpec =
        io.harness.ngtriggers.beans.source.webhook.v2.spec.CustomTriggerSpec.builder()
            .payloadConditions(v1CustomTriggerSpec.fetchPayloadConditions())
            .headerConditions(v1CustomTriggerSpec.fetchHeaderConditions())
            .jexlCondition(v1CustomTriggerSpec.fetchJexlCondition())
            .build();

    assertThat(v2CustomTriggerSpec.fetchPayloadConditions()).isEmpty();
    assertThat(v2CustomTriggerSpec.fetchHeaderConditions()).isEmpty();
    assertThat(v2CustomTriggerSpec.fetchJexlCondition()).isNull();
  }
}
